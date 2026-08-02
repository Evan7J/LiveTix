<template>
  <div class="user-layout-page">
    <div class="container">
      <h1 class="page-title">个人<span class="text-gold">中心</span></h1>
      <div class="user-layout-grid">
        <!-- Sidebar -->
        <aside class="user-sidebar card-dark">
          <div class="sidebar-user">
            <el-avatar :size="56" :src="userStore.avatar" />
            <h4>{{ userStore.nickname || userStore.username }}</h4>
          </div>
          <nav class="sidebar-nav">
            <router-link
              v-for="item in menuItems"
              :key="item.path"
              :to="item.path"
              :class="['nav-item', { active: isActive(item.path) }]"
            >
              <span class="nav-icon">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </router-link>
          </nav>
        </aside>

        <!-- Content -->
        <div class="user-content">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const menuItems = [
  { path: '/user/profile', icon: '👤', label: '个人信息' },
  { path: '/user/orders', icon: '📋', label: '我的订单' },
  { path: '/user/favorites', icon: '❤️', label: '我的收藏' },
  { path: '/user/real-name', icon: '🪪', label: '实名管理' },
  { path: '/user/wallet', icon: '💰', label: '我的钱包' },
  { path: '/user/notifications', icon: '🔔', label: '消息中心' },
  { path: '/user/security', icon: '🔒', label: '账号安全' },
]

function isActive(path) {
  return route.path === path || (path === '/user/profile' && route.path === '/user')
}
</script>

<style scoped>
.container { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
.page-title { font-size: 24px; font-weight: 700; margin-bottom: 32px; }
.user-layout-grid { display: grid; grid-template-columns: 240px 1fr; gap: 24px; }
@media (max-width: 768px) { .user-layout-grid { grid-template-columns: 1fr; } }

.user-sidebar { padding: 0; height: fit-content; overflow: hidden; }
.sidebar-user { padding: 24px; text-align: center; border-bottom: 1px solid var(--border-color); }
.sidebar-user h4 { margin-top: 10px; font-size: 16px; }

.sidebar-nav { padding: 8px 0; }
.nav-item { display: flex; align-items: center; gap: 10px; padding: 12px 24px; color: var(--text-secondary); text-decoration: none; font-size: 14px; transition: all 0.2s; }
.nav-item:hover { color: var(--gold-primary); background: rgba(212, 168, 83, 0.06); }
.nav-item.active { color: var(--gold-primary); background: rgba(212, 168, 83, 0.12); border-right: 3px solid var(--gold-primary); font-weight: 600; }
.nav-icon { font-size: 18px; width: 24px; text-align: center; }

.user-content { min-height: 400px; }
</style>
