import request from '@/utils/request'

// ==================== Auth ====================
export const login = (data) => request.post('/public/login', data)
export const register = (data) => request.post('/public/register', data)

// ==================== Public ====================
export const getBanners = () => request.get('/public/banners')
export const getCategories = () => request.get('/public/categories')
export const getHotShows = () => request.get('/public/shows/hot')
export const getShows = (params) => request.get('/public/shows', { params })
export const getShowDetail = (id) => request.get(`/public/shows/${id}`)
export const searchShows = (params) => request.get('/public/shows/search', { params })

// ==================== User Profile ====================
export const getProfile = () => request.get('/user/profile')
export const updateProfile = (data) => request.put('/user/profile', data)

// ==================== User Orders ====================
export const createOrder = (data) => request.post('/user/orders', data)
export const getMyOrders = (params) => request.get('/user/orders', { params })
export const getOrderDetail = (id) => request.get(`/user/orders/${id}`)
export const cancelOrder = (id) => request.put(`/user/orders/${id}/cancel`)
export const getPayStatus = (id) => request.get(`/user/orders/${id}/pay-status`)
export const getOrderCreateStatus = (requestId) => request.get('/user/orders/create-status', { params: { requestId } })
export const applyRefund = (id, data) => request.put(`/user/orders/${id}/refund`, data)
export const getBuyQuota = (showId) => request.get(`/user/shows/${showId}/buy-quota`)

// ==================== User Real Name ====================
export const getRealNames = () => request.get('/user/real-names')
export const addRealName = (data) => request.post('/user/real-names', data)
export const updateRealName = (id, data) => request.put(`/user/real-names/${id}`, data)
export const deleteRealName = (id) => request.delete(`/user/real-names/${id}`)

// ==================== User Favorites ====================
export const getFavorites = () => request.get('/user/favorites')
export const addFavorite = (showId) => request.post(`/user/favorites/${showId}`)
export const removeFavorite = (showId) => request.delete(`/user/favorites/${showId}`)

// ==================== User Reminders ====================
export const getMyReminders = () => request.get('/user/reminders')
export const setReminder = (showId) => request.post(`/user/reminders/${showId}`)
export const cancelReminder = (showId) => request.delete(`/user/reminders/${showId}`)

// ==================== User Notifications ====================
export const getNotifications = (params) => request.get('/user/notifications', { params })
export const markNotificationRead = (id) => request.put(`/user/notifications/${id}/read`)
export const markAllNotificationsRead = () => request.put('/user/notifications/read-all')
export const getUnreadCount = () => request.get('/user/notifications/unread-count')

// ==================== User Wallet ====================
export const getWallet = () => request.get('/user/wallet')
export const getWalletTransactions = (params) => request.get('/user/wallet/transactions', { params })
export const rechargeWallet = (data) => request.post('/user/wallet/recharge', data)

// ==================== User Security ====================
export const changePassword = (data) => request.post('/user/change-password', data)
export const sendVerifyCode = (data) => request.post('/user/send-verify-code', data)
export const bindPhone = (data) => request.put('/user/bind-phone', data)
export const bindEmail = (data) => request.put('/user/bind-email', data)

// ==================== Admin Dashboard ====================
export const getDashboard = (params) => request.get('/admin/dashboard', { params })

// ==================== Admin Shows ====================
export const adminGetShows = (params) => request.get('/admin/shows', { params })
export const adminGetShow = (id) => request.get(`/admin/shows/${id}`)
export const adminCreateShow = (data) => request.post('/admin/shows', data)
export const adminUpdateShow = (id, data) => request.put(`/admin/shows/${id}`, data)
export const adminDeleteShow = (id) => request.delete(`/admin/shows/${id}`)

// ==================== Admin Categories ====================
export const adminGetCategories = () => request.get('/admin/categories')
export const adminCreateCategory = (data) => request.post('/admin/categories', data)
export const adminUpdateCategory = (id, data) => request.put(`/admin/categories/${id}`, data)
export const adminDeleteCategory = (id) => request.delete(`/admin/categories/${id}`)

// ==================== Admin Orders ====================
export const adminGetOrders = (params) => request.get('/admin/orders', { params })
export const adminGetOrder = (id) => request.get(`/admin/orders/${id}`)
export const adminRefundOrder = (id) => request.put(`/admin/orders/${id}/refund`)

// ==================== Admin Refunds ====================
export const adminGetRefunds = (params) => request.get('/admin/refunds', { params })
export const adminGetRefund = (id) => request.get(`/admin/refunds/${id}`)
export const adminApproveRefund = (id, data) => request.put(`/admin/refunds/${id}/approve`, data)
export const adminRejectRefund = (id, data) => request.put(`/admin/refunds/${id}/reject`, data)

// ==================== Admin Finance ====================
export const adminGetTransactions = (params) => request.get('/admin/finance/transactions', { params })
export const adminGetFinanceRefunds = (params) => request.get('/admin/finance/refunds', { params })

// ==================== Admin Users ====================
export const adminGetUsers = (params) => request.get('/admin/users', { params })
export const adminGetUser = (id) => request.get(`/admin/users/${id}`)
export const adminToggleUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, null, { params: { status } })

// ==================== Admin Banners ====================
export const adminGetBanners = () => request.get('/admin/banners')
export const adminCreateBanner = (data) => request.post('/admin/banners', data)
export const adminUpdateBanner = (id, data) => request.put(`/admin/banners/${id}`, data)
export const adminDeleteBanner = (id) => request.delete(`/admin/banners/${id}`)

// ==================== Admin Config ====================
export const adminGetConfigs = () => request.get('/admin/config')
export const adminUpdateConfig = (id, data) => request.put(`/admin/config/${id}`, data)

// ==================== Admin Roles ====================
export const adminGetRoles = () => request.get('/admin/roles')
export const adminCreateRole = (data) => request.post('/admin/roles', data)
export const adminUpdateRole = (id, data) => request.put(`/admin/roles/${id}`, data)
export const adminDeleteRole = (id) => request.delete(`/admin/roles/${id}`)
export const adminGetPermissions = () => request.get('/admin/permissions')
export const adminGetRolePermissions = (id) => request.get(`/admin/roles/${id}/permissions`)
export const adminUpdateRolePermissions = (id, data) => request.put(`/admin/roles/${id}/permissions`, data)

// ==================== Upload ====================
export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000,
  })
}

// ==================== Admin Logs ====================
export const adminGetLogs = (params) => request.get('/admin/logs', { params })
