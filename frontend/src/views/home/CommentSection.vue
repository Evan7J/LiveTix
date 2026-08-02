<template>
  <div class="comment-section">
    <h3>观众<span class="text-gold">评价</span> ({{ total }})</h3>

    <!-- 评分统计 -->
    <div class="rating-summary">
      <div class="avg-score">
        <span class="score-num">{{ avgRating }}</span>
        <span class="score-unit">/5</span>
      </div>
      <div class="star-bars">
        <div v-for="s in 5" :key="s" class="star-bar-row">
          <span>{{ 6 - s }}星</span>
          <div class="bar-track"><div class="bar-fill" :style="{ width: getBarWidth(6 - s) + '%' }"></div></div>
          <span class="bar-count">{{ ratingCounts[6 - s] || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- 发表评论 -->
    <div v-if="userStore.isLoggedIn()" class="write-comment card-dark">
      <div class="write-header">
        <span>写评价</span>
        <div class="star-select">
          <span v-for="s in 5" :key="s" :class="['star', { active: myRating >= s }]" @click="myRating = s">
            {{ myRating >= s ? '★' : '☆' }}
          </span>
        </div>
      </div>
      <el-input v-model="myContent" type="textarea" :rows="3" placeholder="说说你的观演感受..." maxlength="500" show-word-limit />
      <el-button class="btn-gold" style="margin-top: 12px;" @click="submitComment" :loading="submitting">
        发表评价
      </el-button>
    </div>
    <p v-else class="login-hint">请 <router-link to="/login">登录</router-link> 后发表评价</p>

    <!-- 评论列表 -->
    <div v-if="comments.length > 0" class="comment-list">
      <div v-for="c in comments" :key="c.id" class="comment-item card-dark">
        <div class="comment-header">
          <div class="comment-user">
            <el-avatar :size="36">{{ c.userNickname?.[0] || 'U' }}</el-avatar>
            <div>
              <span class="user-name">{{ c.userNickname || '匿名用户' }}</span>
              <div class="user-stars">{{ '★'.repeat(c.rating) }}{{ '☆'.repeat(5 - c.rating) }}</div>
            </div>
          </div>
          <span class="comment-time">{{ formatDate(c.createTime) }}</span>
        </div>
        <p class="comment-content">{{ c.content }}</p>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="暂无评价，看完演出后来说两句吧" />

    <!-- 加载更多 -->
    <div v-if="hasMore" class="load-more">
      <el-button text :loading="loading" @click="loadComments">加载更多</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/message'
import request from '@/utils/request'
import dayjs from 'dayjs'

const props = defineProps({ showId: { type: [Number, String], required: true } })
const userStore = useUserStore()

const comments = ref([])
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)
const page = ref(1)
const avgRating = ref('--')
const ratingCounts = ref({})

const myRating = ref(0)
const myContent = ref('')
const submitting = ref(false)

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD') : '' }
function getBarWidth(stars) {
  const max = Math.max(...Object.values(ratingCounts.value), 1)
  return Math.round(((ratingCounts.value[stars] || 0) / max) * 100)
}

async function loadComments() {
  loading.value = true
  try {
    const res = await request.get('/public/shows/' + props.showId + '/comments', {
      params: { page: page.value, pageSize: 10 }
    })
    const data = res.data
    if (page.value === 1) {
      comments.value = data.records || []
    } else {
      comments.value.push(...(data.records || []))
    }
    total.value = data.total || 0
    hasMore.value = comments.value.length < total.value
    // 评分统计
    if (data.avgRating != null) avgRating.value = Number(data.avgRating).toFixed(1)
    if (data.ratingCounts) ratingCounts.value = data.ratingCounts
  } catch { /* ignore */ } finally { loading.value = false }
}

async function submitComment() {
  if (myRating.value === 0) { toast.warning('请先评分'); return }
  if (!myContent.value.trim()) { toast.warning('请输入评价内容'); return }
  submitting.value = true
  try {
    await request.post('/user/comments', {
      showId: props.showId,
      rating: myRating.value,
      content: myContent.value.trim(),
    })
    toast.success('评价发表成功')
    myRating.value = 0
    myContent.value = ''
    page.value = 1
    loadComments()
  } catch { /* ignore */ } finally { submitting.value = false }
}

onMounted(() => loadComments())
</script>

<style scoped>
.comment-section { margin-top: 40px; }
.comment-section h3 { font-size: 18px; margin-bottom: 20px; }

.rating-summary { display: flex; gap: 32px; padding: 20px; background: var(--bg-secondary); border-radius: 8px; margin-bottom: 24px; align-items: center; }
.avg-score { text-align: center; }
.score-num { font-size: 42px; font-weight: 700; color: var(--gold-primary); }
.score-unit { font-size: 14px; color: var(--text-secondary); display: block; }
.star-bars { flex: 1; }
.star-bar-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; }
.bar-track { flex: 1; height: 6px; background: #2A2A2A; border-radius: 3px; overflow: hidden; }
.bar-fill { height: 100%; background: var(--gold-gradient); border-radius: 3px; transition: width 0.4s; }
.bar-count { width: 20px; text-align: right; }

.write-comment { padding: 16px; margin-bottom: 24px; }
.write-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.star-select { font-size: 24px; }
.star { cursor: pointer; color: #555; margin-right: 2px; transition: color 0.2s; }
.star.active { color: var(--gold-primary); }
.star:hover { color: var(--gold-primary); }

.login-hint { text-align: center; padding: 20px; color: var(--text-muted); font-size: 14px; }
.login-hint a { color: var(--gold-primary); }

.comment-item { padding: 16px; margin-bottom: 12px; }
.comment-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.comment-user { display: flex; align-items: center; gap: 10px; }
.user-name { font-size: 14px; font-weight: 600; }
.user-stars { font-size: 12px; color: var(--gold-primary); margin-top: 2px; }
.comment-time { font-size: 12px; color: var(--text-muted); }
.comment-content { font-size: 14px; color: var(--text-secondary); line-height: 1.6; }

.load-more { text-align: center; padding: 16px; }
</style>
