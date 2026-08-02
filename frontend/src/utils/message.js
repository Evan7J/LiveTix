/**
 * 全局消息提示工具 — 统一风格、统一位置
 *
 * 使用方式：
 *   import { toast } from '@/utils/message'
 *   toast.error('库存不足')
 *   toast.success('下单成功')
 *   toast.warning('请先选择票档')
 *   toast.info('加载中...')
 */
import { ElMessage } from 'element-plus'

const BASE = {
  center: true,
  offset: 0,
  showClose: true,
  duration: 1800,
  zIndex: 99999,
  grouping: true,
  customClass: 'livetix-toast',
}

export const toast = {
  error(msg) {
    ElMessage({ ...BASE, message: msg, type: 'error', duration: 1800 })
  },
  success(msg) {
    ElMessage({ ...BASE, message: msg, type: 'success', duration: 1800 })
  },
  warning(msg) {
    ElMessage({ ...BASE, message: msg, type: 'warning', duration: 1800 })
  },
  info(msg) {
    ElMessage({ ...BASE, message: msg, type: 'info', duration: 1800 })
  },
}

// 一分钟后自动消失的持久消息（如重要通知）
export function sticky(msg, type = 'warning') {
  ElMessage({ ...BASE, message: msg, type, duration: 60000, showClose: true })
}
