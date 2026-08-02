<template>
  <div class="card-dark section-card">
    <h3>我的收藏</h3>
    <div v-if="shows.length > 0" class="show-grid">
      <div v-for="show in shows" :key="show.id" class="show-card card-dark" @click="$router.push(`/show/${show.id}`)">
        <div class="show-card-img">
          <img :src="show.coverImage || '/logo.svg'" class="show-cover-img" @error="e => e.target.src='/logo.svg'" />
        </div>
        <div class="show-card-body">
          <h3 class="show-title">{{ show.title }}</h3>
          <p class="show-info">{{ show.artists || '精彩演出' }}</p>
          <p class="show-info">{{ formatDate(show.showTime) }}</p>
          <div class="show-price-row">
            <span class="show-price text-gold">¥{{ show.priceMin }}起</span>
          </div>
          <div class="fav-actions">
            <el-button size="small" class="btn-gold-outline" @click.stop="$router.push(`/show/${show.id}`)">立即购买</el-button>
            <el-button size="small" text type="danger" @click.stop="handleRemove(show.id)">取消收藏</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无收藏的演出" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { toast } from '@/utils/message'
import { getFavorites, removeFavorite } from '@/api'
import dayjs from 'dayjs'

const shows = ref([])

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadList() {
  try { const res = await getFavorites(); shows.value = res.data || [] } catch { /* ignore */ }
}

async function handleRemove(showId) {
  try {
    await removeFavorite(showId)
    toast.success('已取消收藏')
    loadList()
  } catch { /* ignore */ }
}

onMounted(() => loadList())
</script>

<style scoped>
.section-card { padding: 24px; }
.section-card h3 { font-size: 16px; margin-bottom: 16px; }
.show-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.show-card { cursor: pointer; overflow: hidden; }
.show-card-img { height: 160px; background: linear-gradient(135deg, #1A1A1A, #2A2A2A); }
.show-cover-img { width: 100%; height: 100%; object-fit: cover; }
.show-card-body { padding: 12px; }
.show-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }
.show-info { font-size: 12px; color: var(--text-secondary); }
.show-price { font-size: 18px; font-weight: 700; }
.fav-actions { display: flex; gap: 8px; margin-top: 8px; }
</style>
