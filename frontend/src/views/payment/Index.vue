<template>
  <div class="payment-page" v-if="order.id">
    <div class="container">
      <!-- Payment Steps -->
      <div class="pay-steps">
        <div class="step done"><span class="step-num">✓</span>选择演出</div>
        <div class="step-line done"></div>
        <div class="step done"><span class="step-num">✓</span>确认订单</div>
        <div class="step-line" :class="{ done: payStatus === 'paid' }"></div>
        <div class="step" :class="{ done: payStatus === 'paid', active: payStatus === 'pending' }">
          <span class="step-num">{{ payStatus === 'paid' ? '✓' : '3' }}</span>支付
        </div>
        <div class="step-line" :class="{ done: payStatus === 'paid' }"></div>
        <div class="step" :class="{ done: payStatus === 'paid' }">
          <span class="step-num">4</span>电子票
        </div>
      </div>

      <!-- Countdown -->
      <div class="countdown-bar card-dark" v-if="payStatus === 'pending' && remaining > 0">
        <div class="countdown-content">
          <span class="countdown-icon">⏱</span>
          <span>支付剩余时间：</span>
          <span class="countdown-time text-gold">{{ formatTime(remaining) }}</span>
          <span>，超时订单将自动取消</span>
        </div>
      </div>
      <div class="countdown-bar expired" v-else-if="payStatus === 'pending'">
        <span>⏰ 订单已超时，请重新下单</span>
        <el-button class="btn-gold" size="small" @click="$router.push('/')" style="margin-left: 16px;">返回首页</el-button>
      </div>

      <div class="pay-layout" v-if="payStatus === 'pending'">
        <!-- Left: Order Summary -->
        <div class="pay-left">
          <div class="order-card card-dark">
            <h2>订单<span class="text-gold">确认</span></h2>
            <div class="order-detail">
              <div class="order-show-header">
                <img :src="order.showCover || '/logo.svg'" class="order-show-img" @error="e => e.target.src='/logo.svg'" />
                <div>
                  <h3>{{ order.showTitle }}</h3>
                  <p>{{ formatDate(order.showTime) }}</p>
                  <p class="venue-text">{{ order.venueName || '' }}</p>
                </div>
              </div>
              <div class="detail-grid">
                <div class="detail-row"><span>订单编号</span><span class="order-no">{{ order.orderNo }}</span></div>
                <div class="detail-row"><span>票种</span><span>{{ order.ticketType }}</span></div>
                <div class="detail-row"><span>数量</span><span>{{ order.quantity }} 张</span></div>
                <div class="detail-row"><span>单价</span><span>¥{{ order.unitPrice || order.ticketPrice }}</span></div>
                <div class="detail-row" v-if="order.couponDiscount > 0">
                  <span>优惠券</span><span class="discount-text">-¥{{ order.couponDiscount }}</span>
                </div>
                <div class="detail-row total"><span>应付金额</span><span class="text-gold total-amount">¥{{ order.payAmount }}</span></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Payment Methods -->
        <div class="pay-right">
          <div class="pay-methods-card card-dark">
            <h2>选择<span class="text-gold">支付方式</span></h2>

            <div class="pay-methods">
              <div
                :class="['pay-method', { selected: payMethod === 'wechat' }]"
                @click="payMethod = 'wechat'"
              >
                <span class="pay-icon">💚</span>
                <div class="pay-info">
                  <span class="pay-name">微信支付</span>
                  <span class="pay-desc">微信安全支付</span>
                </div>
                <el-icon v-if="payMethod === 'wechat'" color="var(--gold-primary)" :size="20"><CircleCheckFilled /></el-icon>
              </div>

              <div
                :class="['pay-method', { selected: payMethod === 'alipay' }]"
                @click="payMethod = 'alipay'"
              >
                <span class="pay-icon">💙</span>
                <div class="pay-info">
                  <span class="pay-name">支付宝</span>
                  <span class="pay-desc">支付宝安全支付</span>
                </div>
                <el-icon v-if="payMethod === 'alipay'" color="var(--gold-primary)" :size="20"><CircleCheckFilled /></el-icon>
              </div>

              <div
                :class="['pay-method', { selected: payMethod === 'wallet' }]"
                @click="payMethod = 'wallet'"
              >
                <span class="pay-icon">💰</span>
                <div class="pay-info">
                  <span class="pay-name">钱包余额</span>
                  <span class="pay-desc">余额支付（当前: ¥{{ walletBalance }}）</span>
                </div>
                <el-icon v-if="payMethod === 'wallet'" color="var(--gold-primary)" :size="20"><CircleCheckFilled /></el-icon>
              </div>
            </div>

            <button
              class="btn-gold pay-submit-btn"
              :disabled="!payMethod || remaining <= 0 || paying"
              @click="handlePay"
            >
              <template v-if="paying">
                <el-icon class="is-loading"><Loading /></el-icon> 支付中...
              </template>
              <template v-else>
                确认支付 ¥{{ order.payAmount }}
              </template>
            </button>
          </div>
        </div>
      </div>

      <!-- Payment Success / E-Ticket -->
      <div class="eticket-section" v-if="payStatus === 'paid'">
        <div class="success-header">
          <div class="success-icon">✅</div>
          <h2>支付成功！</h2>
          <p>您的电子票已生成，请妥善保管</p>
        </div>

        <div class="eticket-card">
          <div class="eticket-header">
            <span class="text-gold eticket-logo">LiveTix</span>
            <span class="eticket-label">电子票</span>
          </div>

          <div class="eticket-body">
            <div class="eticket-show-info">
              <h2>{{ order.showTitle }}</h2>
              <div class="eticket-meta">
                <div class="eticket-meta-item">
                  <span class="meta-label">演出时间</span>
                  <span class="meta-value">{{ formatDate(order.showTime) }}</span>
                </div>
                <div class="eticket-meta-item">
                  <span class="meta-label">场馆</span>
                  <span class="meta-value">{{ order.venueName || '--' }}</span>
                </div>
                <div class="eticket-meta-item">
                  <span class="meta-label">票种</span>
                  <span class="meta-value">{{ order.ticketType }} × {{ order.quantity }}张</span>
                </div>
                <div class="eticket-meta-item">
                  <span class="meta-label">座位</span>
                  <!-- 动态渲染：有选定座位 → 展示具体座位；无 → 随机分配 -->
                  <span class="meta-value seat-value" v-if="hasSelectedSeat">
                    {{ order.ticketType }} {{ seatLabel(order.seats) }}
                  </span>
                  <span class="meta-value seat-random" v-else>
                    {{ order.ticketType }}（座位现场分配）
                  </span>
                </div>
              </div>
            </div>

            <div class="eticket-qr-section">
              <div class="qr-code">
                <div class="qr-pattern">
                  <div class="qr-row" v-for="row in 7" :key="row">
                    <span v-for="col in 7" :key="col" :class="['qr-cell', { black: qrPattern[(row-1)*7+(col-1)] }]"></span>
                  </div>
                </div>
                <p class="qr-hint">{{ order.orderNo?.slice(-8) }}</p>
              </div>
            </div>
          </div>

          <div class="eticket-footer">
            <div class="eticket-order-info">
              <span>订单号: {{ order.orderNo }}</span>
              <span>验证码: {{ verifyCode }}</span>
            </div>
            <div class="eticket-barcode">
              <div class="barcode-lines">
                <span v-for="n in 30" :key="n" class="barcode-line" :style="{ height: (10 + Math.sin(n * 1.7) * 18 + Math.sin(n * 0.7) * 8) + 'px' }"></span>
              </div>
            </div>
          </div>

          <div class="eticket-watermark">LiveTix 电子票 · 入场时出示此页面</div>
        </div>

        <div class="eticket-actions">
          <el-button class="btn-gold-outline" size="large" @click="$router.push('/user/orders')">查看我的订单</el-button>
          <el-button class="btn-gold" size="large" @click="$router.push('/')">继续浏览演出</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '@/utils/message'
import { getOrderDetail, getPayStatus, getWallet } from '@/api'
import request from '@/utils/request'
import dayjs from 'dayjs'

// 从 seats 字段提取人类可读的标签（格式: "A排4座|1-4" → "A排4座"）
function seatLabel(seats) {
  if (!seats) return '座位现场分配'
  // 如果有 | 分隔符，取前半部分作为显示标签
  const idx = seats.indexOf('|')
  return idx > 0 ? seats.substring(0, idx).trim() : seats
}
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const order = ref({})
const remaining = ref(0)
const paying = ref(false)
const payMethod = ref('wechat')
const payStatus = ref('pending')
const walletBalance = ref('0.00')
let timer = null
let syncTimer = null

// 是否已选定具体座位（非随机分配）
const hasSelectedSeat = computed(() => {
  const seats = order.value.seats
  return seats && seats.trim() && !seats.includes('随机') && !seats.includes('现场')
})

// 6位数字验证码
const verifyCode = computed(() => {
  return order.value.verifyCode || (order.value.orderNo || '').slice(-6).toUpperCase() || '******'
})

const qrPattern = reactive(Array.from({ length: 49 }, () => Math.random() > 0.5))

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }
function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function loadOrder() {
  try {
    const res = await getOrderDetail(route.params.orderId)
    order.value = res.data
    // Check if already paid
    if (order.value.status === 'paid' || order.value.status === 'completed') {
      payStatus.value = 'paid'
      return
    }
    // Get pay status
    const ps = await getPayStatus(route.params.orderId)
    remaining.value = ps.data?.remainingSeconds || 0
    if (remaining.value > 0) startTimer()
    // Load wallet balance
    try {
      const wallet = await getWallet()
      walletBalance.value = ((typeof wallet.data === 'number' ? wallet.data : wallet.data?.balance) || 0).toFixed(2)
    } catch { /* ignore */ }
  } catch {
    toast.error('加载订单失败')
    router.push('/user/orders')
  }
}

function startTimer() {
  clearInterval(timer)
  // 每秒倒计时
  timer = setInterval(() => {
    if (remaining.value > 0) remaining.value--
    else { clearInterval(timer); timer = null }
  }, 1000)
  // 每30秒从后端同步一次状态（确保倒计时准确性）
  syncTimer = setInterval(async () => {
    try {
      const ps = await getPayStatus(route.params.orderId)
      const serverRemaining = ps.data?.remainingSeconds || 0
      const serverStatus = ps.data?.status
      // 如果后端状态已变（如已支付），直接更新
      if (serverStatus === 'paid' || serverStatus === 'completed') {
        payStatus.value = 'paid'
        clearInterval(timer)
        clearInterval(syncTimer)
        order.value.status = serverStatus
        return
      }
      // 如果差异超过3秒，以后端为准
      if (Math.abs(remaining.value - serverRemaining) > 3) {
        remaining.value = serverRemaining
      }
    } catch { /* ignore sync errors */ }
  }, 30000)
}

async function handlePay() {
  if (!payMethod.value) { toast.warning('请选择支付方式'); return }
  paying.value = true
  try {
    const res = await request.post('/user/pay/' + order.value.id + '/execute', {
      method: payMethod.value,
    })
    toast.success('支付成功！')
    payStatus.value = 'paid'
    clearInterval(timer)
    // Update order status
    order.value.status = 'paid'
    order.value.orderNo = res.data?.orderNo || order.value.orderNo
    order.value.verifyCode = res.data?.verifyCode || generateVerifyCode()
  } catch (e) {
    // 余额不足 → 弹窗提示并引导充值
    const msg = e?.message || ''
    if (msg.includes('余额不足')) {
      ElMessageBox.confirm(
        '您的钱包余额不足，无法完成支付。是否前往充值？',
        '余额不足',
        {
          confirmButtonText: '去充值',
          cancelButtonText: '取消',
          type: 'warning',
          confirmButtonClass: 'btn-gold el-button',
        }
      ).then(() => {
        router.push('/user/wallet')
      }).catch(() => { /* 取消 */ })
    }
    // 其他错误由拦截器统一处理（已展示 toast）
  } finally {
    paying.value = false
  }
}

function generateVerifyCode() {
  return Math.random().toString(36).substring(2, 8).toUpperCase()
}

onMounted(() => loadOrder())
onUnmounted(() => { clearInterval(timer); clearInterval(syncTimer) })
</script>

<style scoped>
.container { max-width: 900px; margin: 0 auto; padding: 32px 24px; }

/* Steps */
.pay-steps { display: flex; align-items: center; justify-content: center; margin-bottom: 32px; gap: 0; }
.step { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-muted); }
.step.done { color: var(--gold-primary); }
.step.active { color: var(--text-primary); font-weight: 600; }
.step-num { width: 28px; height: 28px; border-radius: 50%; border: 2px solid var(--border-color); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.step.done .step-num { background: var(--gold-gradient); border-color: transparent; color: #000; }
.step.active .step-num { border-color: var(--gold-primary); color: var(--gold-primary); }
.step-line { width: 40px; height: 2px; background: var(--border-color); margin: 0 8px; }
.step-line.done { background: var(--gold-primary); }

/* Countdown */
.countdown-bar { padding: 14px 20px; margin-bottom: 24px; text-align: center; }
.countdown-content { font-size: 14px; color: var(--text-secondary); display: flex; align-items: center; justify-content: center; gap: 4px; flex-wrap: wrap; }
.countdown-icon { font-size: 18px; }
.countdown-time { font-size: 24px; font-weight: 700; margin: 0 4px; }
.countdown-bar.expired { padding: 14px 20px; margin-bottom: 24px; text-align: center; background: rgba(231, 76, 60, 0.15); border: 1px solid rgba(231, 76, 60, 0.3); border-radius: var(--radius-md); color: #E74C3C; font-size: 14px; }

/* Pay Layout */
.pay-layout { display: grid; grid-template-columns: 1fr 360px; gap: 24px; }
@media (max-width: 768px) { .pay-layout { grid-template-columns: 1fr; } }

/* Order Card */
.order-card { padding: 24px; }
.order-card h2 { font-size: 18px; font-weight: 700; margin-bottom: 20px; }
.order-show-header { display: flex; gap: 16px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border-color); }
.order-show-img { width: 80px; height: 100px; object-fit: cover; border-radius: var(--radius-sm); background: var(--bg-secondary); }
.order-show-header h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; }
.order-show-header p { font-size: 13px; color: var(--text-secondary); margin: 4px 0; }
.venue-text { color: var(--text-muted); font-size: 12px; }
.detail-grid { display: flex; flex-direction: column; gap: 0; }
.detail-row { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,0.04); font-size: 14px; color: var(--text-secondary); }
.detail-row.total { border-bottom: none; padding-top: 16px; font-size: 16px; font-weight: 600; }
.order-no { font-family: monospace; font-size: 12px; color: var(--text-muted); }
.discount-text { color: #67C23A; }
.total-amount { font-size: 28px; font-weight: 700; }

/* Payment Methods */
.pay-methods-card { padding: 24px; }
.pay-methods-card h2 { font-size: 18px; font-weight: 700; margin-bottom: 20px; }
.pay-methods { display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }
.pay-method { display: flex; align-items: center; gap: 14px; padding: 16px; border: 2px solid var(--border-color); border-radius: var(--radius-md); cursor: pointer; transition: all 0.25s; }
.pay-method:hover { border-color: rgba(212, 168, 83, 0.5); background: rgba(212, 168, 83, 0.05); }
.pay-method.selected { border-color: var(--gold-primary); background: rgba(212, 168, 83, 0.1); }
.pay-icon { font-size: 28px; }
.pay-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pay-name { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.pay-desc { font-size: 12px; color: var(--text-muted); }
.pay-submit-btn { width: 100%; padding: 16px; font-size: 16px; font-weight: 700; border: none; border-radius: var(--radius-sm); cursor: pointer; letter-spacing: 1px; }
.pay-submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ========== E-Ticket ========== */
.eticket-section { margin-top: 24px; }
.success-header { text-align: center; margin-bottom: 32px; }
.success-icon { font-size: 56px; margin-bottom: 16px; }
.success-header h2 { font-size: 28px; font-weight: 700; margin-bottom: 8px; }
.success-header p { color: var(--text-secondary); font-size: 15px; }

.eticket-card { background: linear-gradient(145deg, #1a1a1a 0%, #222 100%); border: 1px solid var(--border-color); border-radius: var(--radius-lg); overflow: hidden; max-width: 500px; margin: 0 auto; }
.eticket-header { background: linear-gradient(145deg, #111, #1a1a1a); padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); }
.eticket-logo { font-size: 20px; font-weight: 800; letter-spacing: 2px; }
.eticket-label { font-size: 12px; color: var(--text-muted); background: rgba(212, 168, 83, 0.15); padding: 4px 12px; border-radius: 4px; }

.eticket-body { display: flex; gap: 24px; padding: 24px; border-bottom: 1px dashed var(--border-color); }
.eticket-show-info { flex: 1; }
.eticket-show-info h2 { font-size: 18px; font-weight: 700; margin-bottom: 16px; }
.eticket-meta { display: flex; flex-direction: column; gap: 10px; }
.eticket-meta-item { display: flex; flex-direction: column; gap: 2px; }
.meta-label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; }
.meta-value { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.seat-value { color: var(--gold-primary); font-weight: 700; font-size: 15px; }
.seat-random { color: var(--text-secondary); font-style: italic; font-size: 13px; }

.eticket-qr-section { flex-shrink: 0; display: flex; flex-direction: column; align-items: center; }
.qr-code { text-align: center; }
.qr-pattern { display: inline-grid; grid-template-columns: repeat(7, 1fr); gap: 2px; padding: 8px; background: #fff; border-radius: 4px; }
.qr-cell { width: 14px; height: 14px; border-radius: 2px; }
.qr-cell.black { background: #000; }
.qr-hint { font-size: 10px; color: var(--text-muted); margin-top: 6px; font-family: monospace; }

.eticket-footer { padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; }
.eticket-order-info { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
.barcode-lines { display: flex; align-items: flex-end; gap: 1px; }
.barcode-line { width: 2px; background: var(--text-primary); border-radius: 1px; }

.eticket-watermark { padding: 12px 24px; text-align: center; font-size: 11px; color: var(--text-muted); background: rgba(0,0,0,0.3); }

.eticket-actions { display: flex; justify-content: center; gap: 16px; margin-top: 32px; }

.is-loading { animation: rotating 2s linear infinite; }
@keyframes rotating { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
