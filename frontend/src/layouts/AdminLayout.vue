<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <router-link to="/" class="admin-logo">
          <span class="text-gold" style="font-size: 22px; font-weight: 700;">LiveTix</span>
        </router-link>
        <span class="admin-badge">管理后台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :router="true"
        class="sidebar-menu"
        background-color="transparent"
        text-color="#B0B0B0"
        active-text-color="#D4A853"
      >
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>

        <el-sub-menu index="shows-group">
          <template #title>
            <el-icon><VideoPlay /></el-icon>
            <span>演出管理</span>
          </template>
          <el-menu-item index="/admin/shows">演出列表</el-menu-item>
          <el-menu-item index="/admin/categories">演出分类</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="orders-group">
          <template #title>
            <el-icon><Tickets /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/admin/orders">订单列表</el-menu-item>
          <el-menu-item index="/admin/refunds">退票审核</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/admin/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>

        <el-sub-menu index="finance-group" v-if="userStore.hasPermission('finance')">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>财务管理</span>
          </template>
          <el-menu-item index="/admin/finance/transactions">交易流水</el-menu-item>
          <el-menu-item index="/admin/finance/refunds">退款记录</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/admin/banners">
          <el-icon><Picture /></el-icon>
          <span>页面管理</span>
        </el-menu-item>

        <el-sub-menu index="settings-group">
          <template #title>
            <el-icon><Tools /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/admin/config">基础配置</el-menu-item>
          <el-menu-item index="/admin/logs">操作日志</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-footer">
        <el-button text @click="$router.push('/')" style="color: var(--text-secondary); width: 100%;">
          <el-icon><ArrowLeft /></el-icon> 返回前台
        </el-button>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <h2>{{ pageTitle }}</h2>
        <div class="admin-actions">
          <el-avatar :size="32" :src="userStore.avatar || undefined">
            {{ (userStore.nickname || userStore.username || 'A').charAt(0) }}
          </el-avatar>
          <span>{{ userStore.nickname || userStore.username || '管理员' }}</span>
          <el-button text @click="handleLogout" style="color: var(--text-secondary);">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="admin-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const titles = {
    '/admin': '数据概览',
    '/admin/shows': '演出列表',
    '/admin/categories': '演出分类',
    '/admin/orders': '订单列表',
    '/admin/refunds': '退票审核',
    '/admin/users': '用户管理',
    '/admin/coupons': '优惠券管理',
    '/admin/finance/transactions': '交易流水',
    '/admin/finance/refunds': '退款记录',
    '/admin/banners': '页面管理',
    '/admin/config': '基础配置',
    '/admin/roles': '权限管理',
    '/admin/logs': '操作日志',
  }
  return titles[route.path] || '管理后台'
})

function handleLogout() {
  userStore.logout()
  router.push('/')
}
</script>

<style scoped>
.admin-layout { display: flex; height: 100vh; background: var(--bg-primary); }
.sidebar { width: 240px; background: var(--bg-secondary); border-right: 1px solid var(--border-color); display: flex; flex-direction: column; flex-shrink: 0; overflow-y: auto; }
.sidebar-header { padding: 20px; border-bottom: 1px solid var(--border-color); }
.admin-logo { text-decoration: none; display: block; }
.admin-badge { display: inline-block; margin-top: 4px; font-size: 12px; color: var(--text-muted); background: rgba(212, 168, 83, 0.15); padding: 2px 8px; border-radius: 4px; }
.sidebar-menu { flex: 1; border-right: none !important; padding-top: 8px; }
.sidebar-menu .el-menu-item { font-size: 14px; height: 48px; line-height: 48px; margin: 2px 8px; border-radius: var(--radius-sm); }
.sidebar-menu .el-menu-item:hover { background: rgba(212, 168, 83, 0.1) !important; }
.sidebar-menu .el-menu-item.is-active { background: rgba(212, 168, 83, 0.15) !important; border-right: 3px solid var(--gold-primary); }
:deep(.el-sub-menu .el-menu-item) { min-width: auto; padding-left: 52px !important; }
:deep(.el-sub-menu__title) { font-size: 14px; height: 48px; line-height: 48px; margin: 2px 8px; border-radius: var(--radius-sm); }
:deep(.el-sub-menu__title:hover) { background: rgba(212, 168, 83, 0.1) !important; }
.sidebar-footer { padding: 16px; border-top: 1px solid var(--border-color); }
.admin-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.admin-topbar { height: 64px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; background: var(--bg-secondary); border-bottom: 1px solid var(--border-color); flex-shrink: 0; }
.admin-topbar h2 { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.admin-actions { display: flex; align-items: center; gap: 12px; color: var(--text-secondary); }
.admin-content { flex: 1; overflow-y: auto; padding: 24px; }
</style>
