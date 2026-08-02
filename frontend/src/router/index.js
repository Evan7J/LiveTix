import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  // ========== C端页面 ==========
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('@/views/home/Index.vue') },
      { path: 'category/:id?', name: 'Category', component: () => import('@/views/category/Index.vue') },
      { path: 'show/:id', name: 'ShowDetail', component: () => import('@/views/home/Detail.vue') },
      { path: 'search', name: 'Search', component: () => import('@/views/search/Index.vue') },
      { path: 'pay/:orderId', name: 'Payment', component: () => import('@/views/payment/Index.vue'), meta: { auth: true } },

      // Personal center with sidebar layout
      {
        path: 'user',
        component: () => import('@/views/user/Layout.vue'),
        meta: { auth: true },
        children: [
          { path: '', redirect: '/user/profile' },
          { path: 'profile', name: 'UserProfile', component: () => import('@/views/user/Profile.vue') },
          { path: 'orders', name: 'UserOrders', component: () => import('@/views/order/Index.vue') },
          { path: 'favorites', name: 'UserFavorites', component: () => import('@/views/user/Favorites.vue') },
          { path: 'real-name', name: 'UserRealName', component: () => import('@/views/user/RealName.vue') },
          { path: 'wallet', name: 'UserWallet', component: () => import('@/views/user/Wallet.vue') },
          { path: 'notifications', name: 'UserNotifications', component: () => import('@/views/user/Notifications.vue') },
          { path: 'security', name: 'UserSecurity', component: () => import('@/views/user/Security.vue') },
        ],
      },
    ],
  },

  // ========== 认证页面 ==========
  { path: '/login', name: 'Login', component: () => import('@/views/auth/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('@/views/auth/Register.vue') },

  // ========== 管理后台 ==========
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { auth: true, admin: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/admin/Dashboard.vue') },
      { path: 'shows', name: 'AdminShows', component: () => import('@/views/admin/Shows.vue') },
      // 场次和座位管理已合并到演出管理页面中
      { path: 'categories', name: 'AdminCategories', component: () => import('@/views/admin/Categories.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/Orders.vue') },
      { path: 'refunds', name: 'AdminRefunds', component: () => import('@/views/admin/Refunds.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/Users.vue') },
      { path: 'finance', name: 'AdminFinance', component: () => import('@/views/admin/Finance.vue') },
      { path: 'banners', name: 'AdminBanners', component: () => import('@/views/admin/Banners.vue') },
      { path: 'config', name: 'AdminConfig', component: () => import('@/views/admin/SysConfig.vue') },
      { path: 'logs', name: 'AdminLogs', component: () => import('@/views/admin/Logs.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  if (to.meta.auth && !userStore.isLoggedIn()) {
    next('/login')
    return
  }

  if (to.meta.admin && !userStore.isAdmin()) {
    next('/')
    return
  }

  next()
})

export default router
