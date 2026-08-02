import axios from 'axios'
import { toast } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = userStore.token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      toast.error(res.message || '请求失败')
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/login'
        return Promise.reject(error)
      } else if (status === 403) {
        toast.error('权限不足，请联系管理员')
      } else if (status === 404) {
        toast.error('请求的资源不存在')
      } else if (status === 429) {
        toast.error('操作过于频繁，请稍后再试')
      } else if (status >= 500) {
        toast.error('服务器繁忙，请稍后重试')
      } else {
        toast.error(error.response.data?.message || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED') {
      toast.error('请求超时，请检查网络连接')
    } else if (error.message === 'Network Error') {
      toast.error('网络连接失败，请检查网络')
    } else {
      toast.error('网络错误，请重试')
    }
    return Promise.reject(error)
  }
)

export default request
