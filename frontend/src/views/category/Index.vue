<template>
  <div class="category-page">
    <!-- Search Box -->
    <div class="search-bar-wrap">
      <div class="search-bar">
        <div class="search-input-box">
          <el-icon :size="20"><Search /></el-icon>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索演出、艺人、场馆"
            class="search-input"
            @keydown.enter="doSearch"
            @input="onSearchInput"
            @focus="showSuggestions = suggestions.length > 0"
            @blur="hideSuggestions"
          />
        </div>
        <button class="search-btn" @click="doSearch">搜索</button>
      </div>
      <!-- Search Suggestions -->
      <div v-if="showSuggestions && suggestions.length > 0" class="search-suggestions">
        <div
          v-for="item in suggestions"
          :key="item.id"
          class="suggestion-row"
          @mousedown.prevent="$router.push(`/show/${item.id}`)"
        >
          <span>{{ item.title }}</span>
          <span class="sug-type">{{ item.categoryName || '演出' }}</span>
        </div>
      </div>
    </div>

    <!-- Filter Section -->
    <div class="filter-section">
      <!-- City Filter -->
      <div class="filter-row">
        <span class="filter-label">城　市：</span>
        <div class="filter-tags">
          <span
            :class="['filter-tag', { active: selectedCity === '' }]"
            @click="selectCity('')"
          >全部</span>
          <span
            v-for="city in displayCities"
            :key="city"
            :class="['filter-tag', { active: selectedCity === city }]"
            @click="selectCity(city)"
          >{{ city }}</span>
          <span
            v-if="allCities.length > 8"
            class="filter-tag more-btn"
            @click="showMoreCities = !showMoreCities"
          >
            {{ showMoreCities ? '收起▲' : '更多▼' }}
          </span>
        </div>
      </div>

      <!-- Category Filter -->
      <div class="filter-row">
        <span class="filter-label">分　类：</span>
        <div class="filter-tags">
          <span
            :class="['filter-tag', { active: selectedCategory === null }]"
            @click="selectCategory(null)"
          >全部</span>
          <span
            v-for="cat in categories"
            :key="cat.id"
            :class="['filter-tag', { active: selectedCategory === cat.id }]"
            @click="selectCategory(cat.id)"
          >{{ cat.name }}</span>
        </div>
      </div>

      <!-- Time Filter -->
      <div class="filter-row">
        <span class="filter-label">时　间：</span>
        <div class="filter-tags">
          <span
            :class="['filter-tag', { active: selectedTimeRange === '' }]"
            @click="selectTimeRange('')"
          >全部</span>
          <span
            v-for="opt in timeOptions"
            :key="opt.value"
            :class="['filter-tag', { active: selectedTimeRange === opt.value }]"
            @click="selectTimeRange(opt.value)"
          >{{ opt.label }}</span>
          <span style="position: relative;">
            <span
              :class="['filter-tag', { active: selectedTimeRange === 'calendar' }]"
              @click="openCalendar"
            >📅 {{ calendarDate || '按日历' }}</span>
            <input
              type="date"
              ref="dateInputRef"
              v-model="calendarDate"
              class="hidden-date-input"
              @change="onCalendarPick"
            />
          </span>
        </div>
      </div>

      <!-- Sort & Results -->
      <div class="filter-row sort-row">
        <span class="filter-label">排　序：</span>
        <div class="filter-tags">
          <span
            :class="['filter-tag', { active: sortType === 'recommend' }]"
            @click="selectSort('recommend')"
          >推荐排序</span>
          <span
            :class="['filter-tag', { active: sortType === 'soonest' }]"
            @click="selectSort('soonest')"
          >最近开场</span>
          <span
            :class="['filter-tag', { active: sortType === 'latest' }]"
            @click="selectSort('latest')"
          >最新上架</span>
        </div>
        <span class="result-count" v-if="!loading">共 <strong>{{ total }}</strong> 场演出</span>
      </div>
    </div>

    <!-- Show Grid - Light Theme Cards -->
    <div class="show-section">
      <!-- Loading -->
      <div v-if="loading" class="show-grid">
        <div v-for="n in 8" :key="'sk'+n" class="show-card skeleton-card">
          <div class="skeleton-img"></div>
          <div class="skeleton-body">
            <div class="skeleton-line w-80"></div>
            <div class="skeleton-line w-60"></div>
            <div class="skeleton-line w-40"></div>
          </div>
        </div>
      </div>

      <!-- Empty -->
      <el-empty v-else-if="shows.length === 0" description="暂无符合条件的演出">
        <el-button class="search-btn" @click="resetFilters">重置筛选</el-button>
      </el-empty>

      <!-- Grid -->
      <div v-else class="show-grid">
        <div
          v-for="show in shows"
          :key="show.id"
          class="show-card"
          @click="$router.push(`/show/${show.id}`)"
        >
          <div class="show-card-img">
            <img :src="show.coverImage || '/logo.svg'" class="show-cover-img" @error="e => e.target.src='/logo.svg'" />
            <span v-if="show.isHot" class="card-tag hot-tag">热门</span>
            <span v-else-if="show.availableStock === 0" class="card-tag sold-tag">售罄</span>
            <span v-else-if="show.availableStock <= 20" class="card-tag few-tag">少量</span>
          </div>
          <div class="show-card-body">
            <h3 class="show-title">{{ show.title }}</h3>
            <p class="show-artist" v-if="show.artists">{{ show.artists }}</p>
            <p class="show-meta">
              <span>{{ formatDate(show.showTime) }}</span>
              <span v-if="show.venueCity" class="show-city">{{ show.venueCity }}</span>
            </p>
            <div class="show-footer">
              <span class="show-price">¥{{ show.priceMin }}<span class="price-suffix">起</span></span>
              <button class="card-buy-btn" @click.stop="$router.push(`/show/${show.id}`)">
                {{ show.availableStock === 0 ? '售罄' : '选座购票' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadShows"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShows, getCategories, searchShows } from '@/api'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const dateInputRef = ref(null)

// Search
const searchKeyword = ref('')
const showSuggestions = ref(false)
const suggestions = ref([])
let searchTimer = null

// Filters
const selectedCity = ref('')
const selectedCategory = ref(null)
const selectedTimeRange = ref('')
const sortType = ref('recommend')
const showMoreCities = ref(false)
const calendarDate = ref('')

// Data
const shows = ref([])
const categories = ref([])
const loading = ref(true)
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)

// All major cities
const allCities = [
  '北京', '上海', '广州', '深圳', '成都', '杭州', '西安', '重庆',
  '武汉', '南京', '天津', '长沙', '郑州', '苏州', '厦门', '青岛',
  '大连', '昆明', '沈阳', '宁波', '福州', '合肥', '哈尔滨', '济南',
]

const displayCities = computed(() => {
  return showMoreCities.value ? allCities : allCities.slice(0, 8)
})

const timeOptions = [
  { label: '今天', value: 'today' },
  { label: '明天', value: 'tomorrow' },
  { label: '本周末', value: 'weekend' },
  { label: '一个月内', value: 'month' },
]

// Watch route param changes
const routeCatId = computed(() => {
  const id = Number(route.params.id)
  return id > 0 ? id : null
})

watch(routeCatId, (newId) => {
  selectedCategory.value = newId
  page.value = 1
  loadShows()
})

function formatDate(date) { return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '--' }

// Selection handlers
function selectCity(city) {
  selectedCity.value = city
  page.value = 1
  loadShows()
}

function selectCategory(catId) {
  selectedCategory.value = catId
  page.value = 1
  // Update URL to reflect category
  if (catId) {
    router.replace('/category/' + catId)
  } else {
    router.replace('/category')
  }
  loadShows()
}

function selectTimeRange(range) {
  if (range === 'calendar') return
  selectedTimeRange.value = range
  calendarDate.value = ''
  page.value = 1
  loadShows()
}

function onCalendarPick() {
  if (calendarDate.value) {
    selectedTimeRange.value = 'calendar'
    page.value = 1
    loadShows()
  }
}

function openCalendar() {
  if (dateInputRef.value) {
    dateInputRef.value.showPicker()
  }
}

function selectSort(sort) {
  sortType.value = sort
  page.value = 1
  loadShows()
}

function resetFilters() {
  selectedCity.value = ''
  selectedCategory.value = null
  selectedTimeRange.value = ''
  sortType.value = 'recommend'
  calendarDate.value = ''
  searchKeyword.value = ''
  page.value = 1
  loadShows()
}

// Search
function doSearch() {
  if (searchKeyword.value.trim()) {
    router.push('/search?keyword=' + encodeURIComponent(searchKeyword.value.trim()))
  }
}

function onSearchInput() {
  clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (kw.length < 1) { suggestions.value = []; showSuggestions.value = false; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchShows({ keyword: kw, page: 1, pageSize: 6 })
      suggestions.value = res.data?.records || []
      showSuggestions.value = suggestions.value.length > 0
    } catch { suggestions.value = [] }
  }, 300)
}

function hideSuggestions() {
  setTimeout(() => { showSuggestions.value = false }, 200)
}

// Build params for API
function buildParams() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (selectedCategory.value) params.categoryId = selectedCategory.value
  if (selectedCity.value) params.city = selectedCity.value
  if (selectedTimeRange.value) {
    params.timeRange = selectedTimeRange.value
    if (selectedTimeRange.value === 'calendar' && calendarDate.value) {
      params.date = calendarDate.value
    }
  }
  if (sortType.value) params.sort = sortType.value
  return params
}

// Load shows
async function loadShows() {
  loading.value = true
  try {
    const params = buildParams()
    const res = await getShows(params)
    shows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    shows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch { /* ignore */ }
}

onMounted(() => {
  loadCategories()
  selectedCategory.value = routeCatId.value
  loadShows()
})
</script>

<style scoped>
/* ========== Page: Light Theme ========== */
.category-page { background: #F5F5F5; min-height: 100vh; }

/* ========== Search Bar ========== */
.search-bar-wrap { background: #fff; padding: 20px 24px; border-bottom: 1px solid #E8E8E8; position: relative; }
.search-bar { max-width: 700px; margin: 0 auto; display: flex; gap: 12px; }
.search-input-box { flex: 1; display: flex; align-items: center; gap: 10px; padding: 0 16px; border: 2px solid #E0E0E0; border-radius: 8px; background: #FAFAFA; transition: border-color 0.2s; }
.search-input-box:focus-within { border-color: #FF4D4F; background: #fff; }
.search-input-box .el-icon { color: #999; }
.search-input { flex: 1; height: 42px; border: none; background: transparent; outline: none; font-size: 14px; color: #333; }
.search-input::placeholder { color: #BBB; }
.search-btn { padding: 0 32px; background: linear-gradient(135deg, #FF4D4F, #FF7875); color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; transition: opacity 0.2s; }
.search-btn:hover { opacity: 0.9; }

/* Search Suggestions */
.search-suggestions { position: absolute; top: 68px; left: 50%; transform: translateX(-50%); width: 676px; max-height: 300px; overflow-y: auto; background: #fff; border: 1px solid #E8E8E8; border-radius: 8px; box-shadow: 0 8px 24px rgba(0,0,0,0.1); z-index: 100; padding: 8px 0; }
.suggestion-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 20px; cursor: pointer; font-size: 14px; color: #333; transition: background 0.15s; }
.suggestion-row:hover { background: #FFF1F0; }
.sug-type { font-size: 11px; color: #999; background: #F5F5F5; padding: 2px 8px; border-radius: 4px; }

/* ========== Filter Section ========== */
.filter-section { background: #fff; margin: 0; border-bottom: 1px solid #E8E8E8; }
.filter-row { display: flex; align-items: flex-start; padding: 12px 24px; border-bottom: 1px solid #F0F0F0; }
.filter-row:last-child { border-bottom: none; }
.sort-row { justify-content: flex-start; }

.filter-label { width: 56px; flex-shrink: 0; color: #999; font-size: 13px; line-height: 30px; text-align: right; margin-right: 12px; }
.filter-tags { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }

.filter-tag {
  display: inline-flex; align-items: center; padding: 5px 14px;
  border: 1px solid #E0E0E0; border-radius: 4px;
  background: #fff; color: #666; font-size: 13px; cursor: pointer;
  transition: all 0.2s; user-select: none; white-space: nowrap;
  line-height: 1.4;
}
.filter-tag:hover { border-color: #FF7875; color: #FF4D4F; }
.filter-tag.active { background: #FF4D4F; color: #fff; border-color: #FF4D4F; font-weight: 500; }
.filter-tag.more-btn { color: #FF4D4F; border-color: #FF7875; font-size: 12px; }
.filter-tag.more-btn:hover { background: #FFF1F0; }

.result-count { margin-left: auto; color: #999; font-size: 13px; white-space: nowrap; }
.result-count strong { color: #FF4D4F; }

/* Calendar Picker (hidden native input, triggered by tag) */
.hidden-date-input { position: absolute; top: 0; left: 0; width: 0; height: 0; opacity: 0; pointer-events: none; }
.hidden-date-input::-webkit-calendar-picker-indicator { position: absolute; inset: 0; width: auto; height: auto; }

/* ========== Show Section ========== */
.show-section { max-width: 1400px; margin: 0 auto; padding: 24px; }

/* Show Grid */
.show-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
@media (max-width: 1200px) { .show-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .show-grid { grid-template-columns: repeat(2, 1fr); } }

/* Card - Light Theme */
.show-card { background: #fff; border-radius: 8px; overflow: hidden; cursor: pointer; transition: all 0.3s; border: 1px solid #F0F0F0; }
.show-card:hover { transform: translateY(-6px); box-shadow: 0 12px 36px rgba(0,0,0,0.1); }
.show-card-img { height: 210px; position: relative; overflow: hidden; background: #F5F5F5; }
.show-cover-img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s; }
.show-card:hover .show-cover-img { transform: scale(1.06); }

.card-tag { position: absolute; top: 10px; right: 10px; font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; }
.hot-tag { background: linear-gradient(135deg, #FF4D4F, #FF7875); color: #fff; }
.few-tag { background: rgba(255, 77, 79, 0.85); color: #fff; animation: pulse 2s ease-in-out infinite; }
.sold-tag { background: rgba(0,0,0,0.5); color: #fff; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }

.show-card-body { padding: 16px; }
.show-title { font-size: 15px; font-weight: 600; color: #222; margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.4; }
.show-artist { font-size: 13px; color: #888; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.show-meta { font-size: 12px; color: #BBB; margin-bottom: 14px; display: flex; gap: 8px; align-items: center; }
.show-city { background: #F5F5F5; padding: 2px 10px; border-radius: 10px; color: #999; }
.show-footer { display: flex; justify-content: space-between; align-items: center; }
.show-price { font-size: 22px; font-weight: 700; color: #FF4D4F; }
.price-suffix { font-size: 13px; font-weight: 400; color: #999; margin-left: 2px; }
.card-buy-btn { padding: 7px 20px; background: linear-gradient(135deg, #FF4D4F, #FF7875); color: #fff; border: none; border-radius: 20px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.card-buy-btn:hover { box-shadow: 0 4px 12px rgba(255, 77, 79, 0.3); transform: scale(1.05); }

/* Pagination */
.pagination-wrap { display: flex; justify-content: center; margin-top: 40px; }

/* Skeleton */
.skeleton-card { pointer-events: none; }
.skeleton-img { height: 210px; background: linear-gradient(90deg, #F0F0F0 25%, #E8E8E8 50%, #F0F0F0 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-body { padding: 16px; }
.skeleton-line { height: 14px; background: linear-gradient(90deg, #F0F0F0 25%, #E8E8E8 50%, #F0F0F0 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; border-radius: 4px; margin-bottom: 8px; }
.skeleton-line.w-80 { width: 80%; }
.skeleton-line.w-60 { width: 60%; }
.skeleton-line.w-40 { width: 40%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
