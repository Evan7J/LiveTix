import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userId = ref(null)
  const username = ref('')
  const nickname = ref('')
  const avatar = ref('')
  const role = ref('')
  const memberLevel = ref(0)
  const permissions = ref([])
  const unreadCount = ref(0)

  const isLoggedIn = () => !!token.value
  const isAdmin = () => role.value === 'admin' || role.value === 'super_admin' || role.value === 'operator' || role.value === 'finance' || role.value === 'cs'

  function hasPermission(perm) {
    if (role.value === 'admin') return true
    return permissions.value.includes(perm) || permissions.value.includes('*')
  }

  function setLogin(data) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname
    avatar.value = data.avatar
    role.value = data.role
    memberLevel.value = data.memberLevel
    if (data.permissions) {
      permissions.value = data.permissions
    }
  }

  function logout() {
    token.value = ''
    userId.value = null
    username.value = ''
    nickname.value = ''
    avatar.value = ''
    role.value = ''
    memberLevel.value = 0
    permissions.value = []
    unreadCount.value = 0
  }

  function setUnreadCount(count) {
    unreadCount.value = count
  }

  return {
    token, userId, username, nickname, avatar, role, memberLevel,
    permissions, unreadCount,
    isLoggedIn, isAdmin, hasPermission, setLogin, logout, setUnreadCount,
  }
}, {
  // 40 修复: sessionStorage 替代 localStorage — 关闭标签页即清除，XSS 无法持久窃取 token
  persist: { storage: sessionStorage },
})
