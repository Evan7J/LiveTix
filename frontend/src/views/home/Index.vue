<template>
  <div class="home-page">
    <!-- Hero Banner / Carousel -->
    <section class="banner-section">
      <el-carousel :interval="5000" arrow="always" height="440px" indicator-position="outside">
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <div class="banner-slide" @click="goShow(banner.showId)" :style="{ backgroundImage: `url(${banner.imageUrl})` }">
            <div class="banner-overlay">
              <div class="banner-content">
                <h1 class="banner-title">{{ banner.title }}</h1>
                <p class="banner-sub">立即抢购 →</p>
              </div>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </section>

    <!-- Category Pills -->
    <section class="section cat-section">
      <div class="container">
        <div class="category-filters">
          <button
            v-for="cat in categories"
            :key="cat.id"
            :class="['filter-btn', { active: activeCat === cat.id }]"
            @click="activeCat = activeCat === cat.id ? null : cat.id"
          >
            <span class="cat-emoji">{{ catIcons[cat.id] || '🎭' }}</span>
            {{ cat.name }}
          </button>
        </div>
      </div>
    </section>

    <!-- Hot Shows with Countdown -->
    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">
            🔥 <span class="text-gold">热门</span>演出
          </h2>
          <router-link to="/category/1" class="see-all">查看全部 →</router-link>
        </div>

        <!-- Loading skeleton -->
        <div v-if="hotShowsLoading" class="show-grid">
          <div v-for="n in 4" :key="'hs'+n" class="show-card card-dark skeleton-card">
            <div class="skeleton-img"></div>
            <div class="skeleton-body">
              <div class="skeleton-line w-80"></div>
              <div class="skeleton-line w-60"></div>
              <div class="skeleton-line w-40"></div>
            </div>
          </div>
        </div>

        <!-- Error -->
        <div v-else-if="hotShowsError" class="error-state">
          <p>加载热门演出失败</p>
          <button class="btn-gold" @click="loadHotShows">点击重试</button>
        </div>

        <!-- Hot shows grid -->
        <div v-else-if="hotShows.length > 0" class="show-grid">
          <div
            v-for="show in hotShows"
            :key="show.id"
            class="show-card card-dark"
            @click="$router.push(`/show/${show.id}`)"
          >
            <div class="show-card-img">
              <img :src="show.coverImage || '/logo.svg'" class="show-cover-img" @error="e => e.target.src='/logo.svg'" />
              <span class="hot-tag">🔥 热门</span>
              <!-- Countdown for upcoming sale -->
              <div v-if="isUpcoming(show.saleStartTime)" class="countdown-badge">
                <span class="countdown-icon">⏰</span>
                <span>{{ countdownText(show.saleStartTime) }}</span>
              </div>
              <!-- Low stock warning -->
              <div v-else-if="show.availableStock > 0 && show.availableStock <= 20" class="stock-badge">
                ⚡ 仅剩 {{ show.availableStock }} 张
              </div>
            </div>
            <div class="show-card-body">
              <h3 class="show-title">{{ show.title }}</h3>
              <p class="show-artist">{{ show.artists || '精彩演出' }}</p>
              <p class="show-meta">
                <span>{{ formatDateShort(show.showTime) }}</span>
              </p>
              <div class="show-card-footer">
                <div class="show-price-row">
                  <span class="show-price text-gold">¥{{ show.priceMin }}</span>
                  <span class="price-from" v-if="show.priceMax > show.priceMin">- ¥{{ show.priceMax }}</span>
                  <span class="price-label">起</span>
                </div>
                <button class="buy-btn-small" @click.stop="$router.push(`/show/${show.id}`)">
                  抢票
                </button>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无热门演出" />
      </div>
    </section>

    <!-- All Shows -->
    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">
            全部<span class="text-gold">演出</span>
          </h2>
          <span class="result-count" v-if="!allShowsLoading">{{ total }} 场演出</span>
        </div>

        <!-- Loading skeleton -->
        <div v-if="allShowsLoading" class="show-grid">
          <div v-for="n in 8" :key="'as'+n" class="show-card card-dark skeleton-card">
            <div class="skeleton-img"></div>
            <div class="skeleton-body">
              <div class="skeleton-line w-80"></div>
              <div class="skeleton-line w-60"></div>
              <div class="skeleton-line w-40"></div>
            </div>
          </div>
        </div>

        <!-- Error -->
        <div v-else-if="allShowsError" class="error-state">
          <p>加载演出列表失败</p>
          <button class="btn-gold" @click="loadShows">点击重试</button>
        </div>

        <!-- All shows grid -->
        <div v-else-if="allShows.length > 0" class="show-grid">
          <div
            v-for="show in allShows"
            :key="show.id"
            class="show-card card-dark"
            @click="$router.push(`/show/${show.id}`)"
          >
            <div class="show-card-img">
              <img :src="show.coverImage || '/logo.svg'" class="show-cover-img" @error="e => e.target.src='/logo.svg'" />
              <span v-if="show.isHot" class="hot-tag">热门</span>
              <span v-if="show.availableStock === 0" class="sold-out-overlay">售罄</span>
            </div>
            <div class="show-card-body">
              <h3 class="show-title">{{ show.title }}</h3>
              <p class="show-artist">{{ show.artists || '精彩演出' }}</p>
              <p class="show-meta">
                <span>{{ formatDateShort(show.showTime) }}</span>
                <span class="venue-short" v-if="show.venueCity">· {{ show.venueCity }}</span>
              </p>
              <div class="show-card-footer">
                <span class="show-price text-gold">¥{{ show.priceMin }}起</span>
                <span class="stock-info" :class="{ low: show.availableStock <= 20 && show.availableStock > 0 }">
                  {{ show.availableStock <= 0 ? '已售罄' : show.availableStock <= 20 ? '少量' : '有票' }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无演出" />

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
    </section>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBanners, getCategories, getHotShows, getShows } from '@/api'
import dayjs from 'dayjs'

const router = useRouter()

// Loading & error states
const bannersLoading = ref(true)
const hotShowsLoading = ref(true)
const allShowsLoading = ref(true)
const bannersError = ref(false)
const hotShowsError = ref(false)
const allShowsError = ref(false)

const banners = ref([])
const categories = ref([])
const hotShows = ref([])
const allShows = ref([])
const activeCat = ref(null)
const page = ref(1)
const pageSize = ref(8)
const total = ref(0)

const catIcons = {
  1: '🎤', 2: '🎸', 3: '🎭', 4: '⚽', 5: '👶', 6: '🎨',
}

function formatDateShort(date) {
  if (!date) return '--'
  return dayjs(date).format('MM/DD HH:mm')
}

function isUpcoming(saleStartTime) {
  if (!saleStartTime) return false
  return dayjs(saleStartTime).isAfter(dayjs())
}

function countdownText(saleStartTime) {
  if (!saleStartTime) return ''
  const now = dayjs()
  const sale = dayjs(saleStartTime)
  const diff = sale.diff(now, 'second')
  if (diff <= 0) return '已开售'
  const days = Math.floor(diff / 86400)
  const hours = Math.floor((diff % 86400) / 3600)
  const mins = Math.floor((diff % 3600) / 60)
  if (days > 0) return `${days}天后开售`
  return `${hours}时${mins}分开售`
}

function goShow(id) { if (id) router.push(`/show/${id}`) }

async function loadBanners() {
  bannersLoading.value = true
  bannersError.value = false
  try {
    const res = await getBanners()
    banners.value = res.data || []
  } catch {
    bannersError.value = true
  } finally {
    bannersLoading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch { /* ignore */ }
}

async function loadHotShows() {
  hotShowsLoading.value = true
  hotShowsError.value = false
  try {
    const res = await getHotShows()
    hotShows.value = res.data || []
  } catch {
    hotShowsError.value = true
  } finally {
    hotShowsLoading.value = false
  }
}

async function loadShows() {
  allShowsLoading.value = true
  allShowsError.value = false
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (activeCat.value) params.categoryId = activeCat.value
    const res = await getShows(params)
    allShows.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    allShowsError.value = true
  } finally {
    allShowsLoading.value = false
  }
}

watch(activeCat, () => {
  page.value = 1
  loadShows()
})

onMounted(() => {
  loadBanners()
  loadCategories()
  loadHotShows()
  loadShows()
})
</script>

<style scoped>
.home-page { background: var(--bg-primary); }
.container { max-width: 1400px; margin: 0 auto; padding: 0 24px; }
.section { padding: 40px 0 0; }
.cat-section { padding-top: 24px; }

.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.section-title { font-size: 22px; font-weight: 700; color: var(--text-primary); }
.see-all { color: var(--text-secondary); font-size: 14px; text-decoration: none; transition: color 0.2s; }
.see-all:hover { color: var(--gold-primary); }
.result-count { font-size: 13px; color: var(--text-muted); }

/* ========== Banner ========== */
.banner-slide { height: 440px; cursor: pointer; background-size: cover; background-position: center; position: relative; overflow: hidden; }
.banner-slide::before { content: ''; position: absolute; inset: 0; background: rgba(0,0,0,0.4); }
.banner-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }
.banner-content { text-align: center; z-index: 1; }
.banner-title { font-size: 44px; font-weight: 800; color: #fff; margin-bottom: 16px; text-shadow: 0 4px 20px rgba(0,0,0,0.5); }
.banner-sub { color: var(--gold-light); font-size: 18px; font-weight: 500; }

/* ========== Category Filters ========== */
.category-filters { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.filter-btn { padding: 10px 22px; border: 1px solid var(--border-color); background: transparent; color: var(--text-secondary); border-radius: 24px; cursor: pointer; font-size: 14px; transition: all 0.3s; display: flex; align-items: center; gap: 6px; }
.filter-btn:hover { border-color: var(--gold-primary); color: var(--gold-primary); transform: translateY(-2px); }
.filter-btn.active { background: var(--gold-gradient); color: #000; border-color: transparent; font-weight: 600; box-shadow: 0 4px 16px rgba(212, 168, 83, 0.3); }
.cat-emoji { font-size: 16px; }

/* ========== Show Grid ========== */
.show-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
@media (max-width: 1200px) { .show-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .show-grid { grid-template-columns: repeat(2, 1fr); } }

.show-card { cursor: pointer; overflow: hidden; transition: all 0.3s; }
.show-card:hover { transform: translateY(-6px); box-shadow: 0 12px 40px rgba(212, 168, 83, 0.12); }

.show-card-img { height: 210px; position: relative; overflow: hidden; background: linear-gradient(135deg, #1A1A1A, #2A2A2A); }
.show-cover-img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s; }
.show-card:hover .show-cover-img { transform: scale(1.08); }

.hot-tag { position: absolute; top: 10px; right: 10px; background: var(--gold-gradient); color: #000; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 4px; }

/* Countdown badge */
.countdown-badge { position: absolute; bottom: 10px; left: 10px; right: 10px; background: rgba(0,0,0,0.8); backdrop-filter: blur(8px); color: var(--gold-light); font-size: 12px; font-weight: 600; padding: 6px 10px; border-radius: 6px; text-align: center; display: flex; align-items: center; justify-content: center; gap: 6px; }
.countdown-icon { font-size: 14px; }

/* Stock badge */
.stock-badge { position: absolute; bottom: 10px; left: 10px; right: 10px; background: rgba(231, 76, 60, 0.9); color: #fff; font-size: 12px; font-weight: 700; padding: 6px 10px; border-radius: 6px; text-align: center; animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }

.sold-out-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; color: #F56C6C; font-size: 18px; font-weight: 700; letter-spacing: 4px; }

.show-card-body { padding: 16px; }
.show-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.4; }
.show-artist { font-size: 13px; color: var(--text-secondary); margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.show-meta { font-size: 12px; color: var(--text-muted); margin-bottom: 12px; display: flex; gap: 4px; }
.venue-short { color: var(--text-muted); }

.show-card-footer { display: flex; justify-content: space-between; align-items: center; }
.show-price-row { display: flex; align-items: baseline; gap: 2px; }
.show-price { font-size: 20px; font-weight: 700; }
.price-from { font-size: 13px; color: var(--gold-primary); }
.price-label { font-size: 12px; color: var(--text-muted); }

.buy-btn-small { padding: 6px 16px; background: var(--gold-gradient); color: #000; border: none; border-radius: 16px; font-size: 12px; font-weight: 700; cursor: pointer; transition: all 0.2s; }
.buy-btn-small:hover { box-shadow: 0 4px 12px rgba(212, 168, 83, 0.4); transform: scale(1.05); }

.stock-info { font-size: 11px; color: #67C23A; background: rgba(103, 194, 58, 0.1); padding: 2px 10px; border-radius: 10px; }
.stock-info.low { color: #E6A23C; background: rgba(230, 162, 60, 0.1); }

/* Skeleton */
.skeleton-card { pointer-events: none; }
.skeleton-img { height: 210px; background: linear-gradient(90deg, #1A1A1A 25%, #2A2A2A 50%, #1A1A1A 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-body { padding: 16px; }
.skeleton-line { height: 14px; background: linear-gradient(90deg, #1A1A1A 25%, #2A2A2A 50%, #1A1A1A 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; border-radius: 4px; margin-bottom: 8px; }
.skeleton-line.w-80 { width: 80%; }
.skeleton-line.w-60 { width: 60%; }
.skeleton-line.w-40 { width: 40%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.error-state { text-align: center; padding: 48px 0; color: var(--text-secondary); }
.error-state p { font-size: 14px; margin-bottom: 12px; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 40px; padding-bottom: 40px; }
</style>
