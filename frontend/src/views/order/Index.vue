<template>
  <div class="order-page">
    <div class="container">
      <h1 class="page-title">我的<span class="text-gold">订单</span></h1>

      <!-- Status tabs -->
      <div class="status-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          :class="['tab-btn', { active: activeTab === tab.value }]"
          @click="activeTab = tab.value; loadOrders()"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- Order list -->
      <div v-if="orders.length > 0">
        <div v-for="order in orders" :key="order.id" class="order-card card-dark">
          <div class="order-header">
            <span class="order-no">订单号: {{ order.orderNo }}</span>
            <el-tag :type="statusTag(order.status)" size="small">
              {{ statusText(order.status) }}
            </el-tag>
          </div>
          <div class="order-body">
            <div class="order-show">
              <div class="order-img-placeholder">
                <span class="text-gold">🎵</span>
              </div>
              <div class="order-info">
                <h4>{{ order.showTitle }}</h4>
                <p>{{ order.ticketType }} × {{ order.quantity }}张</p>
                <p v-if="order.seats" class="order-seat">🎯 {{ seatLabel(order.seats) }}</p>
                <p>{{ formatDate(order.showTime) }}</p>
                <p v-if="order.venueName" class="order-venue">📍 {{ order.venueName }}</p>
              </div>
            </div>
            <div class="order-amount">
              <span class="text-gold" style="font-size: 20px; font-weight: 700;">
                ¥{{ order.payAmount }}
              </span>
            </div>
          </div>
          <div class="order-footer">
            <span class="order-time">下单时间: {{ formatDate(order.createTime) }}</span>
            <div class="order-actions">
              <el-button
                v-if="order.status === 'pending'"
                size="small"
                type="primary"
                text
                @click="$router.push('/pay/' + order.id)"
              >
                去支付
              </el-button>
              <el-button
                size="small"
                text
                @click="handleViewDetail(order)"
              >
                查看详情
              </el-button>
              <el-button
                v-if="order.status === 'paid' && order.allowRefund === 1"
                size="small"
                type="warning"
                text
                @click="handleRefund(order)"
              >
                申请退票
              </el-button>
              <span v-else-if="order.status === 'paid' && order.allowRefund !== 1" class="no-refund-tag" title="该演出不支持退票">
                不可退票
              </span>
              <el-button
                v-if="order.status === 'pending'"
                size="small"
                type="danger"
                text
                @click="handleCancel(order)"
              >
                取消订单
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty -->
      <el-empty v-else description="暂无订单" style="margin-top: 80px;" />

      <!-- Pagination -->
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadOrders"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { getMyOrders, cancelOrder, applyRefund } from '@/api'
import dayjs from 'dayjs'

function seatLabel(seats) {
  if (!seats) return ''
  const idx = seats.indexOf('|')
  return idx > 0 ? seats.substring(0, idx).trim() : seats
}

const router = useRouter()

const orders = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const activeTab = ref('')

const tabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'pending' },
  { label: '已支付', value: 'paid' },
  { label: '退款中', value: 'refunding' },
  { label: '已取消', value: 'cancelled' },
]

function formatDate(date) {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

function statusText(status) {
  const map = { pending: '待支付', paid: '已支付', refunding: '退款中', cancelled: '已取消', refunded: '已退款' }
  return map[status] || status
}

function statusTag(status) {
  const map = { pending: 'warning', paid: 'success', refunding: 'warning', cancelled: 'info', refunded: 'danger' }
  return map[status] || 'info'
}

async function loadOrders() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (activeTab.value) params.status = activeTab.value
  const res = await getMyOrders(params)
  orders.value = res.data.records
  total.value = res.data.total
}

function handleViewDetail(order) {
  if (order.status === 'paid' || order.status === 'completed') {
    // 已支付订单 → 跳转到支付页查看电子票
    router.push('/pay/' + order.id)
  } else if (order.status === 'pending') {
    // 待支付订单 → 跳转到支付页继续支付
    router.push('/pay/' + order.id)
  }
}

function handleRefund(order) {
  // 计算预计退款金额
  const feeRate = (order.refundFeePercent || 0) / 100
  const fee = (order.payAmount * feeRate).toFixed(2)
  const refundAmount = (order.payAmount - fee).toFixed(2)
  const deadlineText = order.refundDeadlineHours
    ? `（开演前${order.refundDeadlineHours}小时）` : ''

  const msg = h('div', { style: 'line-height:2;font-size:14px' }, [
    h('p', [h('strong', '演出：'), order.showTitle]),
    h('p', [h('strong', '票种：'), order.ticketType + ' ×' + order.quantity + '张']),
    h('p', [h('strong', '支付金额：'), h('span', { style: 'color:#C8A45A;font-size:20px;font-weight:700' }, '¥' + order.payAmount)]),
    feeRate > 0 ? h('p', [h('strong', '退票手续费：'), h('span', { style: 'color:#E6A23C' }, '¥' + fee + '（' + (order.refundFeePercent || 0) + '%）')]) : null,
    feeRate > 0 ? h('p', [h('strong', '预计退款：'), h('span', { style: 'color:#67C23A;font-size:18px;font-weight:700' }, '¥' + refundAmount)]) : null,
    h('p', [h('strong', '订单号：'), order.orderNo]),
    h('div', { style: 'margin-top:10px;padding:8px 12px;background:rgba(230,162,60,.08);border-radius:6px;font-size:12px;color:#E6A23C' }, [
      '⚠ 退票申请提交后需管理员审核，通过后金额退回余额' + deadlineText,
    ]),
  ])

  ElMessageBox.confirm(msg, '确认申请退款？', {
    confirmButtonText: '确认退款 · ¥' + order.payAmount,
    cancelButtonText: '我再想想',
    type: 'warning',
  }).then(async () => {
    try {
      await applyRefund(order.id, { reason: '用户申请退款' })
      toast.success('退票申请已提交，请等待管理员审核')
      loadOrders()
    } catch { /* handled */ }
  }).catch(() => {})
}

async function handleCancel(order) {
  try {
    await ElMessageBox.confirm('确定要取消此订单吗？', '提示', { type: 'warning' })
    await cancelOrder(order.id)
    toast.success('订单已取消')
    loadOrders()
  } catch { /* cancelled */ }
}

onMounted(() => loadOrders())
</script>

<style scoped>
.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 24px;
}

.status-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}

.tab-btn {
  padding: 8px 20px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.tab-btn:hover {
  border-color: var(--gold-primary);
  color: var(--gold-primary);
}

.tab-btn.active {
  background: var(--gold-gradient);
  border-color: transparent;
  color: #000;
  font-weight: 600;
}

.order-card {
  padding: 20px;
  margin-bottom: 16px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.order-no {
  font-size: 13px;
  color: var(--text-muted);
}

.order-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
}

.order-show {
  display: flex;
  gap: 16px;
  align-items: center;
}

.order-img-placeholder {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
}

.order-info h4 {
  font-size: 15px;
  margin-bottom: 4px;
}

.order-info p {
  font-size: 13px;
  color: var(--text-secondary);
}
.order-seat {
  color: var(--gold-primary) !important;
  font-weight: 600;
}
.order-venue {
  font-size: 12px !important;
  color: var(--text-muted) !important;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.order-time {
  font-size: 12px;
  color: var(--text-muted);
}

.no-refund-tag {
  font-size: 11px;
  color: var(--text-muted);
  background: rgba(255,255,255,0.04);
  padding: 2px 10px;
  border-radius: 4px;
  cursor: default;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
