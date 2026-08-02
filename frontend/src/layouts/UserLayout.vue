<template>
  <div class="user-layout">
    <header class="header">
      <div class="header-inner">
        <router-link to="/" class="logo">
          <img src="/logo.svg" alt="LiveTix" class="logo-img" />
          <span class="logo-text text-gold">LiveTix</span>
        </router-link>

        <nav class="nav-links">
          <router-link to="/" class="nav-item" active-class="nav-active">首页</router-link>

          <!-- 分类按钮：hover 展开下拉，点击跳转分类搜索页 -->
          <div class="category-dropdown" @mouseenter="showCatDropdown = true" @mouseleave="showCatDropdown = false">
            <router-link to="/category" class="nav-item nav-cat-btn" :class="{ 'nav-active': $route.path.startsWith('/category') }">
              分类
              <span class="arrow" :class="{ up: showCatDropdown }">▾</span>
            </router-link>
            <transition name="cat-fade">
              <div v-if="showCatDropdown" class="cat-dropdown-panel card-dark">
                <div class="cat-grid">
                  <div
                    v-for="cat in categories"
                    :key="cat.id"
                    class="cat-item"
                    @click="$router.push(`/category/${cat.id}`)"
                  >
                    <span class="cat-icon">{{ catIcons[cat.id] || '🎭' }}</span>
                    <span class="cat-name">{{ cat.name }}</span>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </nav>

        <div class="header-actions">
          <!-- Global Search - positioned left of user area -->
          <div class="search-box" @mouseleave="showSuggestions = false">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索演出、艺人、场馆"
              class="search-input"
              @keydown.enter="doSearch"
              @focus="onSearchFocus"
              @input="onSearchInput"
            />
            <span class="search-icon" @click="doSearch">
              <el-icon><Search /></el-icon>
            </span>
            <!-- Search suggestions dropdown -->
            <transition name="cat-fade">
              <div v-if="showSuggestions && suggestions.length > 0" class="suggestions-panel card-dark">
                <div
                  v-for="(item, idx) in suggestions"
                  :key="idx"
                  class="suggestion-item"
                  @mousedown.prevent="$router.push(`/show/${item.id}`)"
                >
                  <el-icon><Search /></el-icon>
                  <span>{{ item.title }}</span>
                  <span class="sug-type">{{ item.categoryName }}</span>
                </div>
              </div>
            </transition>
          </div>

          <!-- Notification Bell with popover -->
          <template v-if="userStore.isLoggedIn()">
            <div class="notif-wrapper" @mouseenter="loadNotifications" @mouseleave="hideNotifPopover">
              <div class="notif-bell" @click="showNotifPopover = !showNotifPopover">
                <span style="font-size: 20px;">🔔</span>
                <span v-if="userStore.unreadCount > 0" class="notif-badge">{{ userStore.unreadCount > 99 ? '99+' : userStore.unreadCount }}</span>
              </div>
              <transition name="cat-fade">
                <div v-if="showNotifPopover" class="notif-popover card-dark">
                  <div class="notif-popover-header">
                    <span>消息中心</span>
                    <span class="notif-all-read" @click="markAllRead">全部已读</span>
                  </div>
                  <div class="notif-list" v-if="notifications.length > 0">
                    <div
                      v-for="msg in notifications.slice(0, 6)"
                      :key="msg.id"
                      :class="['notif-item', { unread: msg.isRead === 0 }]"
                      @click="goNotification(msg)"
                    >
                      <div class="notif-dot" v-if="msg.isRead === 0"></div>
                      <div class="notif-content">
                        <p class="notif-title">{{ msg.title }}</p>
                        <p class="notif-desc">{{ msg.content }}</p>
                        <p class="notif-time">{{ formatNotifTime(msg.createTime) }}</p>
                      </div>
                    </div>
                  </div>
                  <el-empty v-else description="暂无消息" :image-size="48" />
                  <div class="notif-popover-footer" @click="$router.push('/user/notifications')">
                    查看全部消息 →
                  </div>
                </div>
              </transition>
            </div>

            <el-dropdown trigger="click" popper-class="user-dropdown-popper">
              <span class="user-info">
                <el-avatar :size="32" :src="userStore.avatar || undefined" class="user-avatar">
                  {{ (userStore.nickname || userStore.username || 'U').charAt(0) }}
                </el-avatar>
                <span class="username" :title="userStore.nickname || userStore.username">
                  {{ userStore.nickname || userStore.username || '用户' }}
                </span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/user/profile')">
                    <el-icon><User /></el-icon> 个人中心
                  </el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin()" @click="$router.push('/admin')" divided>
                    <el-icon><Setting /></el-icon> 管理后台
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleLogout" divided>
                    <el-icon><SwitchButton /></el-icon> 退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button class="btn-gold-outline" @click="$router.push('/login')">登录</el-button>
            <el-button class="btn-gold" @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>

    <!-- Customer Service Float Button -->
    <div class="cs-float" @click="showCs = !showCs">
      <span>💬</span>
    </div>
    <transition name="cat-fade">
      <div v-if="showCs" class="cs-panel card-dark">
        <div class="cs-header">
          <h4>在线客服</h4>
          <span class="cs-close" @click="showCs = false">✕</span>
        </div>
        <p style="color: var(--text-secondary); font-size: 13px; margin-bottom: 12px;">您好，有什么可以帮您？</p>
        <div class="cs-faq">
          <p class="cs-q" @click="$router.push('/user/orders')">如何退票？</p>
          <p class="cs-a">请在"我的订单"中申请退票，审核通过后款项将退回您的账户。</p>
          <p class="cs-q">支付后多久出票？</p>
          <p class="cs-a">支付成功后，电子票将立即发送到您的账户。</p>
        </div>
      </div>
    </transition>

    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="text-gold" style="font-size: 20px; font-weight: bold;">LiveTix</span>
          <p style="color: var(--text-secondary); margin-top: 8px;">您的专属票务平台</p>
        </div>
        <div class="footer-links">
          <div class="footer-col">
            <h4>演出分类</h4>
            <a href="#" @click.prevent="$router.push('/category/1')">演唱会</a>
            <a href="#" @click.prevent="$router.push('/category/2')">音乐节</a>
            <a href="#" @click.prevent="$router.push('/category/3')">话剧歌剧</a>
            <a href="#" @click.prevent="$router.push('/category/4')">体育赛事</a>
          </div>
          <div class="footer-col">
            <h4>客户服务</h4>
            <a href="#">帮助中心</a>
            <a href="#">退票规则</a>
            <a href="#">联系我们</a>
          </div>
          <div class="footer-col">
            <h4>关于我们</h4>
            <a href="#">公司介绍</a>
            <a href="#">加入我们</a>
            <a href="#">隐私政策</a>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <p>&copy; 2026 LiveTix. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { toast } from '@/utils/message'
import { getUnreadCount, getCategories, searchShows, getNotifications, markAllNotificationsRead, markNotificationRead, getProfile } from '@/api'
import dayjs from 'dayjs'

const userStore = useUserStore()
const router = useRouter()

const searchKeyword = ref('')
const showCatDropdown = ref(false)
const showCs = ref(false)
const showSuggestions = ref(false)
const suggestions = ref([])
const categories = ref([])

// Notification popover
const showNotifPopover = ref(false)
const notifications = ref([])
let notifHideTimer = null

const catIcons = {
  1: '🎤', 2: '🎸', 3: '🎭', 4: '⚽', 5: '👶', 6: '🎨',
}

let searchTimer = null

function formatNotifTime(time) {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()
  if (d.isSame(now, 'day')) return d.format('HH:mm')
  if (d.isSame(now.subtract(1, 'day'), 'day')) return '昨天'
  return d.format('MM-DD')
}

function doSearch() {
  if (searchKeyword.value.trim()) {
    showSuggestions.value = false
    router.push(`/search?keyword=${encodeURIComponent(searchKeyword.value.trim())}`)
  }
}

function onSearchFocus() {
  if (suggestions.value.length > 0) {
    showSuggestions.value = true
  }
}

async function onSearchInput() {
  clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (kw.length < 1) {
    suggestions.value = []
    showSuggestions.value = false
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchShows({ keyword: kw, page: 1, pageSize: 6 })
      suggestions.value = res.data?.records || []
      showSuggestions.value = suggestions.value.length > 0
    } catch {
      suggestions.value = []
    }
  }, 300)
}

async function loadCategories() {
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch { /* ignore */ }
}

// Notification functions
async function loadNotifications() {
  clearTimeout(notifHideTimer)
  showNotifPopover.value = true
  try {
    const res = await getNotifications({ page: 1, pageSize: 6 })
    notifications.value = res.data?.records || []
  } catch { /* ignore */ }
}

function hideNotifPopover() {
  notifHideTimer = setTimeout(() => {
    showNotifPopover.value = false
  }, 300)
}

async function goNotification(msg) {
  if (msg.isRead === 0) {
    try {
      await markNotificationRead(msg.id)
      userStore.setUnreadCount(Math.max(0, userStore.unreadCount - 1))
    } catch { /* ignore */ }
  }
  showNotifPopover.value = false
  router.push('/user/notifications')
}

async function markAllRead() {
  try {
    await markAllNotificationsRead()
    userStore.setUnreadCount(0)
    notifications.value = notifications.value.map(n => ({ ...n, isRead: 1 }))
  } catch { /* ignore */ }
}

function handleLogout() {
  userStore.logout()
  toast.success('已退出登录')
  router.push('/')
}

onMounted(async () => {
  loadCategories()
  if (userStore.isLoggedIn()) {
    try {
      const res = await getUnreadCount()
      userStore.setUnreadCount(res.data || 0)
    } catch { /* ignore */ }
    // 同步拉取最新用户信息，修正持久化 store 中的旧数据
    try {
      const p = await getProfile()
      if (p.data) {
        userStore.setLogin({
          token: userStore.token,
          userId: p.data.id,
          username: p.data.username,
          nickname: p.data.nickname,
          avatar: p.data.avatar,
          role: p.data.role,
          memberLevel: p.data.memberLevel,
        })
      }
    } catch { /* ignore */ }
  }
})
</script>

<style scoped>
.user-layout { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-primary); }

.header { position: sticky; top: 0; z-index: 100; background: rgba(10, 10, 10, 0.95); backdrop-filter: blur(20px); border-bottom: 1px solid var(--border-color); }
.header-inner { max-width: 1400px; margin: 0 auto; padding: 0 24px; height: 64px; display: flex; align-items: center; gap: 16px; }
.logo { display: flex; align-items: center; gap: 10px; text-decoration: none; flex-shrink: 0; }
.logo-img { width: 36px; height: 36px; }
.logo-text { font-size: 22px; font-weight: 700; letter-spacing: 1px; }
.nav-links { display: flex; gap: 4px; flex: 1; }
.nav-item { color: var(--text-secondary); text-decoration: none; padding: 8px 14px; border-radius: var(--radius-sm); font-size: 14px; transition: all 0.2s; white-space: nowrap; cursor: pointer; display: inline-flex; align-items: center; gap: 4px; }
.nav-item:hover { color: var(--gold-primary); background: rgba(212, 168, 83, 0.1); }
.nav-active { color: var(--gold-primary) !important; background: rgba(212, 168, 83, 0.15) !important; }
.nav-cat-btn { user-select: none; }
.arrow { font-size: 10px; transition: transform 0.3s; display: inline-block; }
.arrow.up { transform: rotate(180deg); }

/* Category Dropdown */
.category-dropdown { position: relative; }
.cat-dropdown-panel { position: absolute; top: 48px; left: 0; width: 420px; padding: 16px; z-index: 200; }
.cat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
.cat-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: var(--radius-sm); cursor: pointer; transition: all 0.2s; color: var(--text-secondary); font-size: 14px; }
.cat-item:hover { background: rgba(212, 168, 83, 0.1); color: var(--gold-primary); }
.cat-icon { font-size: 18px; }
.cat-name { font-weight: 500; }

.cat-fade-enter-active, .cat-fade-leave-active { transition: opacity 0.2s, transform 0.2s; }
.cat-fade-enter-from, .cat-fade-leave-to { opacity: 0; transform: translateY(-4px); }

/* Search */
.search-box { position: relative; }
.search-input { width: 220px; height: 36px; padding: 0 36px 0 14px; border: 1px solid var(--border-color); border-radius: 20px; background: rgba(255,255,255,0.05); color: var(--text-primary); font-size: 13px; outline: none; transition: all 0.3s; }
.search-input:focus { border-color: var(--gold-primary); width: 280px; }
.search-icon { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); cursor: pointer; font-size: 14px; color: var(--text-muted); }
.search-icon:hover { color: var(--gold-primary); }

/* Search Suggestions */
.suggestions-panel { position: absolute; top: 44px; left: 0; right: 0; z-index: 200; max-height: 300px; overflow-y: auto; padding: 8px 0; }
.suggestion-item { display: flex; align-items: center; gap: 10px; padding: 10px 16px; cursor: pointer; font-size: 13px; color: var(--text-primary); transition: all 0.2s; }
.suggestion-item:hover { background: rgba(212, 168, 83, 0.1); color: var(--gold-primary); }
.sug-type { font-size: 11px; color: var(--text-muted); margin-left: auto; background: rgba(255,255,255,0.05); padding: 2px 8px; border-radius: 4px; }

.header-actions { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }

/* Notif */
.notif-wrapper { position: relative; }
.notif-bell { position: relative; cursor: pointer; }
.notif-badge { position: absolute; top: -6px; right: -8px; background: #E74C3C; color: #fff; font-size: 10px; padding: 2px 6px; border-radius: 10px; min-width: 18px; text-align: center; }

/* Notification Popover */
.notif-popover { position: absolute; top: 44px; right: -80px; width: 340px; z-index: 200; overflow: hidden; }
.notif-popover-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--border-color); font-size: 14px; font-weight: 600; color: var(--text-primary); }
.notif-all-read { font-size: 12px; color: var(--gold-primary); cursor: pointer; font-weight: 400; }
.notif-all-read:hover { text-decoration: underline; }
.notif-list { max-height: 340px; overflow-y: auto; }
.notif-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 16px; cursor: pointer; transition: all 0.2s; border-bottom: 1px solid rgba(255,255,255,0.03); }
.notif-item:hover { background: rgba(212, 168, 83, 0.06); }
.notif-item.unread { background: rgba(212, 168, 83, 0.03); }
.notif-dot { width: 6px; height: 6px; background: #E74C3C; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.notif-content { flex: 1; min-width: 0; }
.notif-title { font-size: 13px; font-weight: 500; color: var(--text-primary); margin-bottom: 2px; }
.notif-desc { font-size: 12px; color: var(--text-secondary); margin-bottom: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.notif-time { font-size: 11px; color: var(--text-muted); }
.notif-popover-footer { padding: 12px 16px; text-align: center; font-size: 13px; color: var(--gold-primary); cursor: pointer; border-top: 1px solid var(--border-color); }
.notif-popover-footer:hover { text-decoration: underline; }

.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--text-primary); }
.username { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }

/* CS Float */
.cs-float { position: fixed; right: 24px; bottom: 80px; width: 48px; height: 48px; border-radius: 50%; background: var(--gold-gradient); display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 22px; z-index: 99; box-shadow: 0 4px 16px rgba(212,168,83,0.3); transition: transform 0.2s; }
.cs-float:hover { transform: scale(1.1); }
.cs-panel { position: fixed; right: 24px; bottom: 140px; width: 320px; padding: 20px; z-index: 99; }
.cs-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.cs-header h4 { font-size: 16px; color: var(--text-primary); }
.cs-close { cursor: pointer; color: var(--text-muted); font-size: 16px; }
.cs-close:hover { color: var(--text-primary); }
.cs-faq { margin-top: 12px; }
.cs-q { color: var(--gold-primary); font-size: 13px; margin-top: 10px; cursor: pointer; }
.cs-q:hover { text-decoration: underline; }
.cs-a { color: var(--text-secondary); font-size: 12px; margin-left: 8px; }

.main-content { flex: 1; }

.footer { background: var(--bg-secondary); border-top: 1px solid var(--border-color); margin-top: 60px; }
.footer-inner { max-width: 1400px; margin: 0 auto; padding: 48px 24px; display: flex; justify-content: space-between; gap: 60px; }
.footer-links { display: flex; gap: 80px; }
.footer-col h4 { color: var(--text-primary); margin-bottom: 16px; font-size: 14px; }
.footer-col a { display: block; color: var(--text-secondary); font-size: 13px; margin-bottom: 10px; text-decoration: none; transition: color 0.2s; cursor: pointer; }
.footer-col a:hover { color: var(--gold-primary); }
.footer-bottom { border-top: 1px solid var(--border-color); padding: 20px 24px; text-align: center; color: var(--text-muted); font-size: 13px; }

/* Responsive */
@media (max-width: 1100px) {
  .nav-links { gap: 0; }
  .nav-item { padding: 8px 8px; font-size: 13px; }
  .search-input { width: 160px; }
  .search-input:focus { width: 200px; }
}
@media (max-width: 768px) {
  .nav-links { display: none; }
  .search-input { width: 140px; }
  .search-input:focus { width: 160px; }
  .username { display: none; }
}
</style>
