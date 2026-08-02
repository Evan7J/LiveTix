<template>
  <div class="container">
    <div class="search-header">
      <h1>搜索<span class="text-gold">演出</span></h1>
      <div class="search-box">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索演出名称、艺人、场馆..."
          class="search-input"
          @keydown.enter="doSearch"
          @input="onInput"
        />
        <button class="btn-gold" @click="doSearch">
          <el-icon><Search /></el-icon> 搜索
        </button>
      </div>
      <!-- Search suggestions while typing -->
      <div v-if="suggestions.length > 0 && keyword.trim()" class="search-suggestions card-dark">
        <div
          v-for="item in suggestions"
          :key="item.id"
          class="suggestion-row"
          @click="goDetail(item.id)"
        >
          <el-icon><Search /></el-icon>
          <span>{{ item.title }}</span>
          <span class="sug-cat">{{ item.categoryName || '演出' }}</span>
          <span class="sug-artist" v-if="item.artists">{{ item.artists }}</span>
        </div>
      </div>
    </div>

    <!-- Category filter tags -->
    <div class="filter-tags" v-if="categories.length > 0">
      <span
        v-for="cat in categories"
        :key="cat.id"
        :class="['filter-tag', { active: activeCat === cat.id }]"
        @click="activeCat = activeCat === cat.id ? null : cat.id; doSearch()"
      >
        {{ cat.name }}
      </span>
    </div>

    <!-- Results -->
    <div class="results-info" v-if="searched">
      <span>找到 <strong class="text-gold">{{ total }}</strong> 个相关演出</span>
      <span class="sort-hint" v-if="keyword.trim()">关键词: "{{ keyword }}"</span>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="show-grid">
      <div v-for="n in 4" :key="'sk'+n" class="show-card card-dark skeleton-card">
        <div class="skeleton-img"></div>
        <div class="skeleton-body">
          <div class="skeleton-line w-80"></div>
          <div class="skeleton-line w-60"></div>
          <div class="skeleton-line w-40"></div>
        </div>
      </div>
    </div>

    <!-- Results grid -->
    <div v-else-if="shows.length > 0" class="show-grid">
      <div
        v-for="show in shows"
        :key="show.id"
        class="show-card card-dark"
        @click="$router.push(`/show/${show.id}`)"
      >
        <div class="show-card-img">
          <img :src="show.coverImage || '/logo.svg'" class="show-cover-img" @error="e => e.target.src='/logo.svg'" />
          <span v-if="show.isHot" class="hot-tag">🔥 热门</span>
          <span v-if="show.availableStock <= 20 && show.availableStock > 0" class="stock-warning-tag">⚡ 少量</span>
          <span v-if="show.availableStock === 0" class="sold-out-tag">售罄</span>
        </div>
        <div class="show-card-body">
          <h3 class="show-title">{{ show.title }}</h3>
          <p class="show-info" v-if="show.artists">{{ show.artists }}</p>
          <p class="show-info">
            <el-icon style="vertical-align: -2px;"><Clock /></el-icon>
            {{ formatDate(show.showTime) }}
          </p>
          <div class="show-price-row">
            <span class="show-price text-gold">¥{{ show.priceMin }}起</span>
            <span class="show-city" v-if="show.venueCity">{{ show.venueCity }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else-if="searched" description="未找到相关演出，试试其他关键词">
      <div class="no-results-suggestions">
        <p class="hot-search-title">🔥 热门搜索</p>
        <div class="hot-search-tags">
          <span v-for="kw in hotKeywords" :key="kw" class="hot-search-tag" @click="keyword = kw; doSearch()">{{ kw }}</span>
        </div>
        <el-button class="btn-gold" @click="$router.push('/')" style="margin-top: 16px;">浏览全部演出</el-button>
      </div>
    </el-empty>

    <el-empty v-else description="输入关键词搜索您想看的演出" :image-size="120">
      <template #image>🔍</template>
    </el-empty>

    <!-- Pagination -->
    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="doSearch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchShows, getCategories } from '@/api'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.keyword || '')
const shows = ref([])
const suggestions = ref([])
const categories = ref([])
const searched = ref(false)
const loading = ref(false)
const total = ref(0)
const activeCat = ref(null)
const page = ref(1)
const pageSize = ref(12)

const hotKeywords = ['演唱会', '周杰伦', '音乐节', '话剧', '体育赛事', '五月天', '摇滚', '古典音乐']

let suggestionTimer = null

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }
function goDetail(id) { router.push(`/show/${id}`) }

async function doSearch() {
  loading.value = true
  searched.value = true
  suggestions.value = []
  try {
    const params = { keyword: keyword.value, page: page.value, pageSize: pageSize.value }
    if (activeCat.value) params.categoryId = activeCat.value
    const res = await searchShows(params)
    shows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    shows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onInput() {
  clearTimeout(suggestionTimer)
  const kw = keyword.value.trim()
  if (kw.length < 1) { suggestions.value = []; return }
  suggestionTimer = setTimeout(async () => {
    try {
      const res = await searchShows({ keyword: kw, page: 1, pageSize: 6 })
      suggestions.value = res.data?.records || []
    } catch { suggestions.value = [] }
  }, 300)
}

onMounted(async () => {
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch { /* ignore */ }
  if (keyword.value) doSearch()
})
</script>

<style scoped>
.container { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
.search-header { margin-bottom: 24px; }
.search-header h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; }
.search-box { display: flex; gap: 12px; max-width: 600px; position: relative; }
.search-input { flex: 1; height: 48px; padding: 0 20px; border: 2px solid var(--border-color); border-radius: 24px; background: rgba(255,255,255,0.05); color: var(--text-primary); font-size: 15px; outline: none; transition: all 0.3s; }
.search-input:focus { border-color: var(--gold-primary); box-shadow: 0 0 20px rgba(212, 168, 83, 0.1); }

/* Suggestions */
.search-suggestions { position: absolute; top: 56px; left: 0; right: 80px; z-index: 50; max-height: 300px; overflow-y: auto; padding: 8px 0; }
.suggestion-row { display: flex; align-items: center; gap: 10px; padding: 10px 20px; cursor: pointer; font-size: 13px; color: var(--text-primary); transition: all 0.2s; }
.suggestion-row:hover { background: rgba(212, 168, 83, 0.1); color: var(--gold-primary); }
.sug-cat { font-size: 11px; color: var(--text-muted); margin-left: auto; background: rgba(255,255,255,0.05); padding: 2px 8px; border-radius: 4px; }
.sug-artist { font-size: 12px; color: var(--text-muted); }

/* Filter Tags */
.filter-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 24px; }
.filter-tag { padding: 8px 20px; border: 1px solid var(--border-color); border-radius: 20px; cursor: pointer; font-size: 13px; color: var(--text-secondary); transition: all 0.2s; user-select: none; }
.filter-tag:hover { border-color: var(--gold-primary); color: var(--gold-primary); }
.filter-tag.active { background: var(--gold-gradient); color: #000; border-color: transparent; font-weight: 600; }

.results-info { margin-bottom: 16px; font-size: 14px; color: var(--text-secondary); display: flex; justify-content: space-between; align-items: center; }
.sort-hint { font-size: 12px; color: var(--text-muted); }

/* Grid */
.show-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
@media (max-width: 1200px) { .show-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .show-grid { grid-template-columns: repeat(2, 1fr); } }

.show-card { cursor: pointer; overflow: hidden; }
.show-card:hover { transform: translateY(-4px); }
.show-card-img { height: 200px; position: relative; overflow: hidden; background: linear-gradient(135deg, #1A1A1A, #2A2A2A); }
.show-cover-img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; }
.show-card:hover .show-cover-img { transform: scale(1.05); }
.hot-tag, .stock-warning-tag, .sold-out-tag { position: absolute; top: 10px; right: 10px; font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; }
.hot-tag { background: var(--gold-gradient); color: #000; }
.stock-warning-tag { background: rgba(230, 162, 60, 0.9); color: #000; }
.sold-out-tag { background: rgba(245, 108, 108, 0.9); color: #fff; }

.show-card-body { padding: 16px; }
.show-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.show-info { font-size: 13px; color: var(--text-secondary); margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
.show-price-row { margin-top: 12px; display: flex; justify-content: space-between; align-items: center; }
.show-price { font-size: 20px; font-weight: 700; }
.show-city { font-size: 11px; color: var(--text-muted); background: rgba(255,255,255,0.05); padding: 2px 10px; border-radius: 10px; }

.no-results-suggestions { text-align: center; }
.hot-search-title { font-size: 14px; color: var(--text-secondary); margin-bottom: 12px; }
.hot-search-tags { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.hot-search-tag { padding: 6px 16px; border: 1px solid var(--border-color); border-radius: 16px; cursor: pointer; font-size: 12px; color: var(--text-secondary); transition: all 0.2s; }
.hot-search-tag:hover { border-color: var(--gold-primary); color: var(--gold-primary); background: rgba(200,164,90,.08); }

.pagination-wrap { display: flex; justify-content: center; margin-top: 40px; }

/* Skeleton */
.skeleton-card { pointer-events: none; }
.skeleton-img { height: 200px; background: linear-gradient(90deg, #1A1A1A 25%, #2A2A2A 50%, #1A1A1A 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-body { padding: 16px; }
.skeleton-line { height: 14px; background: linear-gradient(90deg, #1A1A1A 25%, #2A2A2A 50%, #1A1A1A 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; border-radius: 4px; margin-bottom: 8px; }
.skeleton-line.w-80 { width: 80%; }
.skeleton-line.w-60 { width: 60%; }
.skeleton-line.w-40 { width: 40%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
