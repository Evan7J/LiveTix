<template>
  <div class="card-dark section-card">
    <div class="section-header">
      <h3>消息中心</h3>
      <el-button text @click="markAllRead">全部标为已读</el-button>
    </div>
    <div v-if="list.length > 0">
      <div v-for="n in list" :key="n.id" :class="['notif-item', { unread: n.isRead === 0 }]">
        <span class="notif-icon">{{ typeIcon(n.type) }}</span>
        <div class="notif-body">
          <h4>{{ n.title }}</h4>
          <p>{{ n.content }}</p>
          <span class="notif-time">{{ formatDate(n.createTime) }}</span>
        </div>
        <el-button v-if="n.isRead === 0" text size="small" @click="markRead(n.id)">标为已读</el-button>
      </div>
    </div>
    <el-empty v-else description="暂无消息" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '@/api'
import dayjs from 'dayjs'

const list = ref([])

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '' }
function typeIcon(type) {
  return { order_status: '📋', refund_result: '💰', show_remind: '🎵', system: '📢' }[type] || '📌'
}

async function loadList() {
  try { const res = await getNotifications({ page: 1, pageSize: 50 }); list.value = res.data?.records || [] } catch { /* ignore */ }
}

async function markRead(id) {
  await markNotificationRead(id)
  loadList()
}

async function markAllRead() {
  await markAllNotificationsRead()
  loadList()
}

onMounted(() => loadList())
</script>

<style scoped>
.section-card { padding: 24px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { font-size: 16px; }
.notif-item { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border-color); }
.notif-item.unread { background: rgba(212,168,83,0.03); margin: 0 -12px; padding: 14px 12px; border-radius: 6px; }
.notif-icon { font-size: 20px; }
.notif-body { flex: 1; }
.notif-body h4 { font-size: 14px; margin-bottom: 4px; }
.notif-body p { font-size: 13px; color: var(--text-secondary); }
.notif-time { font-size: 11px; color: var(--text-muted); }
</style>
