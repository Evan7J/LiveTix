<template>
  <div class="detail-page">
    <!-- Loading -->
    <div v-if="loading" class="state-wrap">
      <div class="spinner"></div>
      <p>加载演出详情...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="state-wrap">
      <div class="err-icon">😕</div>
      <h2>加载失败</h2>
      <p>{{ error }}</p>
      <div class="err-btns">
        <el-button class="btn-primary" @click="loadDetail">重试</el-button>
        <el-button @click="$router.push('/')">回首页</el-button>
      </div>
    </div>

    <template v-else-if="show.id">
      <!-- Hero -->
      <div class="hero">
        <div class="hero-bg" :style="{ backgroundImage: `url(${show.coverImage || '/logo.svg'})` }"></div>
        <div class="hero-mask"></div>
        <div class="hero-body">
          <div class="w">
            <h1 class="hero-title">{{ show.title }}</h1>
            <div class="hero-meta">
              <span><Clock /> {{ fmt(show.showTime) }}</span>
              <span><Location /> {{ show.venueName || '场馆待定' }}{{ show.venueCity ? ' · '+show.venueCity : '' }}</span>
              <span><View /> {{ show.viewCount }} 浏览</span>
            </div>
          </div>
        </div>
      </div>

      <div class="w">
        <div class="dl">
          <!-- LEFT: Seat Map -->
          <div class="dl-left">
            <div class="seat-card">
              <div class="seat-head">
                <h3>{{ show.venueName || '场馆' }} · 座位图</h3>
                <span class="stage-dir">舞台 ↑</span>
              </div>
              <div class="stage-box">演 出 舞 台</div>
              <div class="seat-sections">
                <div v-for="s in seatSections" :key="s.name" class="seat-sec">
                  <div class="sec-bar" :style="{ borderLeftColor: s.color }">
                    <span class="sec-name" :style="{ color: s.color }">{{ s.name }}</span>
                    <span class="sec-price">¥{{ s.price }}</span>
                  </div>
                  <div class="sec-grid">
                    <div v-for="row in s.rows" :key="row.label" class="sr">
                      <span class="rl">{{ row.label }}</span>
                      <span
                        v-for="seat in row.seats" :key="seat.id"
                        :class="['st', { sold: seat.sold, sel: selectedSeats.includes(seat.id) }]"
                        :style="selectedSeats.includes(seat.id) ? { background: s.color, borderColor: s.color } : {}"
                        @click="toggleSeat(s, seat)"
                        :title="seat.sold ? '已售' : s.name+' '+row.label+'排'+seat.col+'座 ¥'+s.price"
                      >{{ selectedSeats.includes(seat.id) ? '✓' : '' }}</span>
                      <span class="rl rlr">{{ row.label }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="legend">
                <span v-for="s in seatSections" :key="s.name" class="lg"><i style="background:{{ s.color }}"></i>{{ s.name }}</span>
                <span class="lg"><i class="sd"></i>已售</span>
              </div>
            </div>
          </div>

          <!-- RIGHT: Purchase -->
          <div class="dl-right">
            <div class="tags">
              <span v-if="show.isRealName === 1" class="tag warn">实名制购票</span>
              <span v-if="show.allowRefund === 1" class="tag ok">支持退票</span>
              <span v-else class="tag warn">不支持退票</span>
              <span v-if="show.buyLimit > 0" class="tag">限购{{ show.buyLimit }}张</span>
            </div>

            <!-- Stock -->
            <div class="stock-card">
              <div class="stock-bar"><div class="stock-fill" :style="{ width: stockPct + '%' }" :class="{ lo: stockPct < 20 }"></div></div>
              <div class="stock-num"><span>已售 {{ soldCount }}</span><span class="gold">剩余 {{ show.availableStock }} / {{ show.totalStock }} 张</span></div>
            </div>

            <!-- Ticket cards -->
            <div class="ticket-box">
              <h4>选择票档</h4>
              <div class="tickets">
                <div v-for="(t, i) in ticketTypes" :key="i"
                  :class="['tc', { sel: selectedTicket === i, dis: (ticketRemaining[i] || 0) <= 0 }]"
                  @click="selectTicketType(i)">
                  <div>
                    <div class="tcn">{{ t.name }}</div>
                    <div class="tcs"><span v-if="(ticketRemaining[i]||0)<=0" class="so">已售罄</span><span v-else-if="(ticketRemaining[i]||0)<=20" class="ls">仅剩{{ ticketRemaining[i] }}张</span><span v-else>余{{ ticketRemaining[i] }}张</span></div>
                  </div>
                  <div>
                    <span class="tcp gold">¥{{ t.price }}</span>
                    <span v-if="selectedTicket===i" class="ck">✓</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Buy -->
            <div class="buy-card">
              <div class="buy-qty">
              <span>数量</span>
              <span class="qty-val">
                1 张
                <template v-if="show.buyLimit > 0">
                  <span v-if="!userStore.isLoggedIn()">（每人限购{{ show.buyLimit }}张）</span>
                  <span v-else-if="buyQuota.remaining <= 0" class="quota-exhausted">（已满{{ show.buyLimit }}张，无法再购）</span>
                  <span v-else>（限购{{ show.buyLimit }}张，还可购{{ buyQuota.remaining }}张）</span>
                </template>
              </span>
            </div>
              <div v-if="selectedSeats.length>0" class="seat-info">已选：{{ selectedSeatLabels[0] }}</div>
              <!-- 实名观演人选择 -->
              <div v-if="show.isRealName === 1" class="real-name-pick">
                <span class="rnp-label">观演人</span>
                <div v-if="myRealNames.length" class="rnp-list">
                  <span v-for="rn in myRealNames" :key="rn.id"
                    :class="['rnp-item', { sel: activeRealNameId === rn.id }]"
                    @click="activeRealNameId = rn.id">
                    {{ rn.realName }} <em>{{ maskIdCard(rn.idCardNumber) }}</em>
                  </span>
                </div>
                <span v-else class="rnp-none" @click="router.push('/user/real-name')">+ 添加观演人</span>
              </div>
              <div class="buy-total"><span>合计</span><span class="gold buy-price">¥{{ totalPrice }}</span></div>
              <div class="buy-btns">
                <button class="buy-btn" :disabled="buyQuota.remaining <= 0 && show.buyLimit > 0" @click="handleBuy">
                  <span v-if="buying" class="btn-spin"></span>
                  <span v-else-if="show.availableStock<=0">已售罄</span>
                  <span v-else-if="buyQuota.remaining <= 0 && show.buyLimit > 0">已达购买上限</span>
                  <span v-else-if="selectedTicket<0">请先选择票档</span>
                  <span v-else>立即购买</span>
                </button>
                <button class="fav-btn" :class="{ on: isFavorited }" @click="handleFavorite" :disabled="favLoading">
                  {{ isFavorited ? '❤️' : '🤍' }}
                </button>
              </div>
              <div class="tips">
                <p v-if="show.isRealName === 1" class="tp warn">⚠ 实名制购票 · 一人一证一票</p>
                <p v-else class="tp">无需实名</p>
                <p v-if="show.allowRefund === 1" class="tp ok">✅ 支持退票（开演前{{show.refundDeadlineHours}}h）<template v-if="show.refundFeePercent>0">· 手续费{{show.refundFeePercent}}%</template></p>
                <p v-else class="tp">❌ 不支持退票</p>
                <p class="tp">⏱ 下单后{{show.payTimeoutMinutes||15}}分钟内支付 · 超时取消</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Tabs -->
        <div class="tabs-card">
          <div class="tab-btns">
            <button :class="{ on: activeTab==='desc' }" @click="activeTab='desc'">演出介绍</button>
            <button v-if="show.rules" :class="{ on: activeTab==='rules' }" @click="activeTab='rules'">票务规则</button>
            <button v-if="show.notice" :class="{ on: activeTab==='notice' }" @click="activeTab='notice'">观演须知</button>
            <button v-if="show.refundPolicy" :class="{ on: activeTab==='refund' }" @click="activeTab='refund'">退票政策</button>
          </div>
          <div class="tab-body">
            <!-- 35 修复: v-html 改为 sanitizeHtml() 净化渲染，防止 XSS -->
            <div v-if="activeTab==='desc'" v-html="sanitizeHtml(show.description)||'<p style=color:#666>暂无详细介绍</p>'"></div>
            <div v-else-if="activeTab==='rules'" v-html="sanitizeHtml(show.rules)||'<p style=color:#666>暂无</p>'"></div>
            <div v-else-if="activeTab==='notice'" v-html="sanitizeHtml(show.notice)||'<p style=color:#666>暂无</p>'"></div>
            <div v-else-if="activeTab==='refund'" v-html="sanitizeHtml(show.refundPolicy)||'<p style=color:#666>暂无</p>'"></div>
          </div>
        </div>
      </div>
    </template>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '@/utils/message'
import { getShowDetail, createOrder, addFavorite, removeFavorite, getRealNames, getFavorites, getBuyQuota, getOrderCreateStatus } from '@/api'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { sanitizeHtml } from '@/utils/sanitize'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const show = ref({})
const ticketTypes = ref([])
const selectedTicket = ref(-1)
const activeTab = ref('desc')
const isFavorited = ref(false)
const buying = ref(false)
const favLoading = ref(false)
const loading = ref(true)
const error = ref('')
const realNameDialog = ref(false)
const realNames = ref([])
const myRealNames = ref([])
const activeRealNameId = ref(null)
const selectedRealNames = ref([])
const selectedSeats = ref([])
const selectedSeatLabels = ref([])
const soldCellSet = ref(new Set())  // 已售座位坐标集合
const buyQuota = ref({ buyLimit: 0, alreadyBought: 0, remaining: 99, canBuy: true })  // 用户购买配额

function maskIdCard(idCard) {
  if (!idCard) return ''
  return idCard.substring(0, 3) + '****' + idCard.substring(idCard.length - 4)
}

// Seat colors per section
const sc = ['#C8A45A', '#D4836A', '#6A9FB5', '#8B9E6B', '#9B7EB5', '#B5896E', '#E85D75', '#5B8FF9']

// 从 cells 数据重建与管理员设置一致的座位图
// cells 格式: ["1-3", "1-4", ...] 表示 第1行第3列
function buildSeatSectionsFromCells(ticketTypes, soldSet) {
  // 1. 为每个票档分配颜色，收集所有 cells
  const sections = ticketTypes.map((t, i) => ({
    name: t.name,
    price: t.price,
    color: sc[i % sc.length],
    cells: new Set(t.cells || []),
    rows: [] // 稍后填充
  }))

  // 2. 找出所有出现的行号，按顺序排列
  const allCells = ticketTypes.flatMap(t => t.cells || [])
  const rowSet = new Set()
  const colSet = new Set()
  const cellMap = new Map() // "r-c" → sectionIndex

  allCells.forEach(cell => {
    const parts = cell.split('-')
    if (parts.length === 2) {
      const r = parseInt(parts[0]), c = parseInt(parts[1])
      if (!isNaN(r) && !isNaN(c)) {
        rowSet.add(r)
        colSet.add(c)
        // 确定这个 cell 属于哪个票档
        for (let i = 0; i < ticketTypes.length; i++) {
          if ((ticketTypes[i].cells || []).includes(cell)) {
            cellMap.set(cell, i)
            break
          }
        }
      }
    }
  })

  if (rowSet.size === 0) return null // 没有 cells 数据

  const rows = [...rowSet].sort((a, b) => a - b)
  const cols = [...colSet].sort((a, b) => a - b)
  const minCol = cols[0]
  const maxCol = cols[cols.length - 1]

  // 3. 构建全局行号→字母映射（所有看区从上到下统一按 A,B,C... 排列）
  const allUsedRows = [...new Set(sections.flatMap(s =>
    [...s.cells].map(c => parseInt(c.split('-')[0])).filter(r => !isNaN(r))
  ))].sort((a, b) => a - b)
  const rowLetterMap = {}
  allUsedRows.forEach((r, i) => { rowLetterMap[r] = String.fromCharCode(65 + i) })
  // 兜底：任何未映射的行号直接用 A+序号 转换
  const getRowLabel = (r) => rowLetterMap[r] || String.fromCharCode(64 + r)

  // 4. 为每个 section 构建行数据
  sections.forEach((section, si) => {
    const rowData = []
    rows.forEach(r => {
      const rawSeats = []
      for (let c = minCol; c <= maxCol; c++) {
        const cellKey = `${r}-${c}`
        if (section.cells.has(cellKey)) {
          rawSeats.push({ id: `${si}-${r}-${c}`, absCol: c, sold: soldSet.has(cellKey) })
        } else if (cellMap.has(cellKey)) {
          // 这个座位属于其他票档，不显示在此 section 中
        }
      }
      if (rawSeats.length > 0) {
        rawSeats.sort((a, b) => a.absCol - b.absCol)
        rawSeats.forEach((seat, idx) => { seat.col = idx + 1 })
        rowData.push({ label: getRowLabel(r), seats: rawSeats })
      }
    })
    section.rows = rowData
  })

  return sections.filter(s => s.rows.length > 0)
}

const seatSections = computed(() => {
  if (!ticketTypes.value.length) return []

  // 优先使用管理员设置的 cells 数据还原真实座位图
  const hasCells = ticketTypes.value.some(t => (t.cells || []).length > 0)
  if (hasCells) {
    const built = buildSeatSectionsFromCells(ticketTypes.value, soldCellSet.value)
    if (built && built.length > 0) return built
  }

  // 降级：没有 cells 数据时，根据库存数量生成模拟座位图
  return ticketTypes.value.map((t, i) => {
    const rows = []; const rc = t.stock > 5000 ? 7 : t.stock > 2000 ? 5 : 3
    const sp = Math.min(14, Math.max(8, Math.floor((t.stock || 0) / (rc * 2))))
    const sd = (t.price || 0) * 7 + i * 13
    for (let r = 0; r < rc; r++) {
      const label = String.fromCharCode(65 + r)
      const seats = []; const rs = sp + (r % 3 === 0 ? 2 : 0) - (r === rc - 1 ? 2 : 0)
      for (let c = 0; c < rs; c++) {
        const h = (r * 31 + c * 17 + sd) % 100
        seats.push({ id: `${i}-${r}-${c}`, col: c + 1, sold: h < 7 })
      }
      rows.push({ label, seats })
    }
    return { name: t.name, price: t.price, color: sc[i % sc.length], rows }
  })
})

const soldCount = computed(() => (show.value.totalStock || 0) - (show.value.availableStock || 0))

// 每个票档的实时剩余数量 = 总座位数 - 该票档已售座位数
const ticketRemaining = computed(() => {
  return ticketTypes.value.map(t => {
    const totalCells = (t.cells || []).length
    const soldInThisType = (t.cells || []).filter(c => soldCellSet.value.has(c)).length
    return Math.max(0, totalCells - soldInThisType)
  })
})
const stockPct = computed(() => show.value.totalStock ? Math.round((show.value.availableStock / show.value.totalStock) * 100) : 0)
const totalPrice = computed(() => {
  if (ticketTypes.value.length === 0 || selectedTicket.value < 0) return '--'
  const t = ticketTypes.value[selectedTicket.value]
  return t ? Number(t.price).toFixed(2) : '--'
})

function fmt(d) { return d ? dayjs(d).format('YYYY年MM月DD日 HH:mm') : '--' }
function parseTickets(data) {
  try {
    let t = typeof data.ticketTypes === 'string' ? JSON.parse(data.ticketTypes) : (data.ticketTypes || [])
    t.sort((a, b) => (b.price || 0) - (a.price || 0))
    return t
  } catch { return [] }
}

function selectTicketType(i) {
  if ((ticketRemaining.value[i] || 0) <= 0) return
  selectedTicket.value = i; selectedSeats.value = []; selectedSeatLabels.value = []
}
function toggleSeat(section, seat) {
  if (seat.sold) return
  const si = seatSections.value.findIndex(s => s.name === section.name)
  if (si >= 0 && selectedTicket.value !== si) selectTicketType(si)
  const idx = selectedSeats.value.indexOf(seat.id)
  if (idx >= 0) { selectedSeats.value.splice(idx, 1); selectedSeatLabels.value.splice(idx, 1) }
  else {
    const sdata = seatSections.value[si]
    const row = sdata?.rows.find(r => r.seats.find(s => s.id === seat.id))
    selectedSeats.value = [seat.id]
    selectedSeatLabels.value = [`${row?.label||''}排${seat.col}座`]
  }
}

async function loadSoldCells() {
  try {
    const res = await request.get('/public/shows/' + route.params.id + '/sold-cells')
    const cells = res.data || []
    soldCellSet.value = new Set(cells)
  } catch { soldCellSet.value = new Set() }
}

async function loadDetail() {
  loading.value = true; error.value = ''
  try {
    const res = await getShowDetail(route.params.id)
    if (res.data) { show.value = res.data; ticketTypes.value = parseTickets(res.data); await checkFav() }
    else error.value = '演出不存在'
  } catch { error.value = '加载失败' }
  finally { loading.value = false }

  // 加载已售座位
  await loadSoldCells()

  // 预加载用户实名信息
  if (userStore.isLoggedIn() && show.value.isRealName === 1) {
    try {
      const r = await getRealNames()
      myRealNames.value = r.data || []
      if (myRealNames.value.length) activeRealNameId.value = myRealNames.value[0].id
    } catch { /* ignore */ }
  }

  // 查询用户购买配额（已登录且有购买限制时）
  if (userStore.isLoggedIn() && show.value.buyLimit > 0) {
    try {
      const q = await getBuyQuota(show.value.id)
      buyQuota.value = q.data || buyQuota.value
    } catch { /* ignore */ }
  }
}

async function checkFav() {
  if (!userStore.isLoggedIn()) return
  try {
    const res = await getFavorites()
    const favs = res.data || []
    isFavorited.value = favs.some(f => (f.id || f.showId) === show.value.id)
  } catch { /* ignore */ }
}

async function handleBuy() {
  // 未登录 → 跳登录
  if (!userStore.isLoggedIn()) { router.push('/login'); return }

  if (selectedTicket.value < 0) { toast.warning('请先选择票档'); return }
  const ticket = ticketTypes.value[selectedTicket.value]
  if (!ticket || (ticketRemaining.value[selectedTicket.value] || 0) <= 0) { toast.warning('该票档已售罄，请选择其他票档'); return }
  if (show.value.isRealName === 1 && !activeRealNameId.value) { toast.warning('请先选择观演人'); return }
  // 前端限购检查（后端也会严格校验）
  if (show.value.buyLimit > 0 && buyQuota.value.remaining <= 0) {
    toast.warning('您已达到购买上限（每人限购' + show.value.buyLimit + '张）'); return
  }

  // 直接下单（实名信息已在页面选择，无需弹窗）
  buying.value = true
  try {
    // requestId：请求级幂等标识；MQ 异步下单时凭它轮询落库结果
    const requestId = window.crypto?.randomUUID
      ? window.crypto.randomUUID()
      : Date.now() + '-' + Math.random().toString(36).slice(2)
    const payload = { showId: show.value.id, ticketType: ticket.name, ticketPrice: ticket.price, quantity: 1, realNameId: activeRealNameId.value, requestId }
    // 座位信息：发送 r-c 坐标用于标记已售，同时发送人类可读标签
    if (selectedSeats.value.length) {
      const cellKeys = selectedSeats.value.map(sid => {
        const parts = sid.split('-')  // "si-r-c"
        return parts.slice(1).join('-')  // "r-c"
      })
      payload.seatCells = cellKeys.join(',')
      payload.seats = selectedSeatLabels.value.join('，')
    } else {
      payload.seats = null
    }
    const res = await createOrder(payload)
    if (res.data?.pending) {
      // MQ 异步下单：后端已在 Redis 预扣库存并投递 MQ，轮询等待订单落库
      toast.info('抢票请求已提交，排队处理中...')
      await pollOrderCreated(res.data.requestId || requestId)
      return
    }
    const orderId = res.data?.id
    if (orderId) {
      toast.success('下单成功！正在跳转支付页面...')
      setTimeout(() => router.push('/pay/' + orderId), 500)
    } else {
      toast.warning('下单成功，请在订单列表查看')
      router.push('/user/orders')
    }
  } catch {
    // interceptor 已提示错误
  } finally {
    buying.value = false
  }
}

// MQ 异步下单后轮询落库结果（每秒 1 次，最多 15 秒）
async function pollOrderCreated(requestId) {
  for (let i = 0; i < 15; i++) {
    await new Promise(r => setTimeout(r, 1000))
    try {
      const res = await getOrderCreateStatus(requestId)
      const d = res.data || {}
      if (d.status === 'success') {
        toast.success('下单成功！正在跳转支付页面...')
        setTimeout(() => router.push('/pay/' + d.orderId), 500)
        return
      }
      if (d.status === 'fail') {
        toast.error(d.reason || '下单失败，请重试')
        return
      }
    } catch { /* 网络抖动，继续轮询 */ }
  }
  toast.warning('下单处理中，请稍后在订单列表查看')
  router.push('/user/orders')
}

async function handleFavorite() {
  if (!userStore.isLoggedIn()) { router.push('/login'); return }
  favLoading.value = true
  try {
    if (isFavorited.value) {
      await removeFavorite(show.value.id)
      isFavorited.value = false
      toast.success('已取消收藏')
    } else {
      await addFavorite(show.value.id)
      isFavorited.value = true
      toast.success('已收藏 ❤️')
    }
  } catch { /* ignore */ }
  finally { favLoading.value = false }
}

onMounted(() => loadDetail())
</script>

<style scoped>
/* ========== States ========== */
.state-wrap { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 50vh; color: var(--text-secondary); }
.spinner { width: 36px; height: 36px; border: 2px solid #1a1a1a; border-top-color: var(--gold-primary); border-radius: 50%; animation: spin .8s linear infinite; margin-bottom: 16px; }
@keyframes spin { to { transform: rotate(360deg); } }
.err-icon { font-size: 48px; margin-bottom: 12px; }
.state-wrap h2 { font-size: 18px; color: var(--text-primary); margin-bottom: 6px; }
.err-btns { display: flex; gap: 12px; margin-top: 16px; }

/* ========== Buttons ========== */
.btn-primary { background: var(--gold-gradient) !important; border: none !important; color: #000 !important; font-weight: 600 !important; border-radius: var(--radius-sm) !important; }
.btn-primary:hover { box-shadow: 0 8px 30px rgba(200,164,90,.35) !important; }
.btn-primary:disabled { opacity: .35; cursor: not-allowed; }

.w { max-width: 1160px; margin: 0 auto; padding: 0 24px 24px; }
.gold { background: var(--gold-gradient); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }

/* ========== Hero ========== */
.hero { position: relative; height: 320px; overflow: hidden; margin-bottom: 28px; }
.hero-bg { position: absolute; inset: 0; background-size: cover; background-position: center; filter: blur(24px); transform: scale(1.15); }
.hero-mask { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,.5) 0%, rgba(0,0,0,.7) 40%, var(--bg-primary) 100%); }
.hero-body { position: relative; z-index: 2; height: 100%; display: flex; align-items: flex-end; padding-bottom: 28px; }
.hero-title { font-size: 36px; font-weight: 800; color: #fff; text-shadow: 0 2px 20px rgba(0,0,0,.6); letter-spacing: 1px; }
.hero-meta { display: flex; gap: 28px; flex-wrap: wrap; color: rgba(255,255,255,.7); font-size: 14px; margin-top: 10px; }
.hero-meta span { display: flex; align-items: center; gap: 6px; }

/* ========== Layout ========== */
.dl { display: grid; grid-template-columns: 1fr 388px; gap: 24px; }
@media (max-width: 960px) { .dl { grid-template-columns: 1fr; } }

/* ========== Seat Card ========== */
.seat-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); padding: 24px; }
.seat-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.seat-head h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.stage-dir { font-size: 11px; color: var(--text-muted); }

.stage-box { background: linear-gradient(180deg, #111, #1a1a1a); border: 1px solid #222; border-radius: 30px; text-align: center; padding: 14px; color: #555; font-size: 13px; letter-spacing: 12px; margin-bottom: 24px; }

.seat-sections { display: flex; flex-direction: column; gap: 18px; }
.seat-sec { border: 1px solid rgba(255,255,255,.04); border-radius: 10px; padding: 10px; }
.sec-bar { border-left: 3px solid; padding-left: 10px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center; }
.sec-name { font-size: 12px; font-weight: 700; }
.sec-price { font-size: 11px; color: var(--text-muted); }

.sec-grid { display: flex; flex-direction: column; gap: 3px; align-items: center; }
.sr { display: flex; align-items: center; gap: 2px; }
.rl { font-size: 9px; color: #444; width: 14px; text-align: center; flex-shrink: 0; }
.rlr { margin-left: 2px; }

.st { width: 20px; height: 18px; border-radius: 2px 2px 0 0; border: 1px solid rgba(200,164,90,.15); background: rgba(200,164,90,.06); cursor: pointer; transition: all .15s; display: flex; align-items: center; justify-content: center; font-size: 8px; color: #fff; }
.st:hover:not(.sold) { transform: scale(1.25); z-index: 2; box-shadow: 0 2px 8px rgba(200,164,90,.3); }
.st.sold { border-color: transparent; background: rgba(255,255,255,.02); cursor: not-allowed; opacity: .25; }
.st.sel { transform: scale(1.2); z-index: 2; }

.legend { display: flex; gap: 18px; margin-top: 18px; justify-content: center; flex-wrap: wrap; }
.lg { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--text-muted); }
.lg i { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.lg i.sd { background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.08); }

/* ========== Right Panel ========== */
.dl-right { display: flex; flex-direction: column; gap: 14px; }

.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag { font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; background: rgba(200,164,90,.1); color: var(--gold-primary); border: 1px solid rgba(200,164,90,.2); }
.tag.warn { background: rgba(230,162,60,.1); color: #E6A23C; border-color: rgba(230,162,60,.2); }
.tag.ok { background: rgba(103,194,58,.1); color: #67C23A; border-color: rgba(103,194,58,.2); }

.stock-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 10px 14px; }
.stock-bar { height: 4px; background: #1a1a1a; border-radius: 2px; overflow: hidden; margin-bottom: 6px; }
.stock-fill { height: 100%; background: var(--gold-gradient); border-radius: 2px; transition: width .5s; }
.stock-fill.lo { background: linear-gradient(90deg, #E74C3C, #E6A23C); }
.stock-num { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); }

/* Ticket Cards */
.ticket-box { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 16px; }
.ticket-box h4 { font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 10px; }
.tickets { display: flex; flex-direction: column; gap: 7px; }
.tc { display: flex; justify-content: space-between; align-items: center; padding: 11px 14px; border: 1px solid #1a1a1a; border-radius: var(--radius-sm); cursor: pointer; transition: all .2s; background: rgba(255,255,255,.01); }
.tc:hover:not(.dis) { border-color: rgba(200,164,90,.4); background: rgba(200,164,90,.04); }
.tc.sel { border-color: var(--gold-primary); background: rgba(200,164,90,.08); box-shadow: 0 0 16px rgba(200,164,90,.08); }
.tc.dis { opacity: .35; cursor: not-allowed; }
.tcn { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.tcs { font-size: 10px; color: var(--text-muted); margin-top: 2px; }
.tcp { font-size: 18px; font-weight: 700; }
.so { color: #F56C6C; }
.ls { color: #E6A23C; }
.ck { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; background: var(--gold-gradient); border-radius: 50%; font-size: 10px; color: #000; margin-left: 8px; }

/* Buy */
.buy-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 20px; }
.buy-qty { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--text-secondary); margin-bottom: 10px; }
.qty-val { color: #E6A23C; font-weight: 500; }
.seat-info { font-size: 12px; color: var(--gold-primary); margin-bottom: 10px; padding: 6px 10px; background: rgba(200,164,90,.06); border-radius: 6px; }
/* 实名观演人选择 */
.real-name-pick { margin-bottom: 10px; padding: 10px 12px; background: rgba(255,255,255,.02); border-radius: 8px; border: 1px solid rgba(255,255,255,.04); }
.rnp-label { font-size: 11px; color: var(--text-muted); margin-right: 8px; }
.rnp-list { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 4px; }
.rnp-item { padding: 4px 10px; border: 1px solid var(--border-color); border-radius: 16px; cursor: pointer; font-size: 12px; color: var(--text-secondary); transition: all .15s; }
.rnp-item:hover { border-color: var(--gold-primary); }
.rnp-item.sel { background: rgba(200,164,90,.15); border-color: var(--gold-primary); color: var(--gold-primary); }
.rnp-item em { font-style: normal; font-size: 10px; color: var(--text-muted); margin-left: 4px; }
.rnp-none { font-size: 12px; color: #E6A23C; cursor: pointer; }
.buy-total { display: flex; justify-content: space-between; align-items: center; font-size: 14px; color: var(--text-secondary); margin-bottom: 16px; }
.buy-price { font-size: 28px; font-weight: 700; }
.buy-btns { display: flex; gap: 10px; }

.buy-btn { flex: 1; height: 46px; background: var(--gold-gradient); border: none; border-radius: var(--radius-sm); color: #000; font-size: 15px; font-weight: 700; cursor: pointer; letter-spacing: 2px; transition: all .3s; display: flex; align-items: center; justify-content: center; gap: 8px; }
.buy-btn:hover { box-shadow: 0 8px 30px rgba(200,164,90,.4); transform: translateY(-1px); }
.buy-btn:active { transform: scale(0.98); }
.buy-btn:disabled { opacity: .35; cursor: not-allowed; transform: none !important; box-shadow: none !important; }
.btn-spin { width: 14px; height: 14px; border: 2px solid rgba(0,0,0,.3); border-top-color: #000; border-radius: 50%; animation: spin .6s linear infinite; }

.quota-exhausted { color: #F56C6C !important; }

.fav-btn { width: 46px; height: 46px; background: transparent; border: 1px solid #222; border-radius: var(--radius-sm); font-size: 18px; cursor: pointer; transition: all .2s; display: flex; align-items: center; justify-content: center; }
.fav-btn:hover { border-color: #E74C3C; background: rgba(231,76,60,.08); }
.fav-btn.on { border-color: #E74C3C; background: rgba(231,76,60,.12); }
.fav-btn:disabled { opacity: .5; }

.tips { margin-top: 14px; padding-top: 12px; border-top: 1px solid #1a1a1a; }
.tp { font-size: 11px; color: var(--text-muted); margin: 3px 0; }
.tp.warn { color: #E6A23C; }
.tp.ok { color: #67C23A; }

/* ========== Tabs ========== */
.tabs-card { margin-top: 36px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); overflow: hidden; margin-bottom: 60px; }
.tab-btns { display: flex; border-bottom: 1px solid var(--border-color); overflow-x: auto; }
.tab-btns button { padding: 14px 24px; border: none; background: transparent; color: var(--text-muted); font-size: 13px; cursor: pointer; border-bottom: 2px solid transparent; transition: all .2s; white-space: nowrap; }
.tab-btns button:hover { color: var(--gold-light); }
.tab-btns button.on { color: var(--gold-primary); border-bottom-color: var(--gold-primary); font-weight: 600; }
.tab-body { padding: 28px; color: var(--text-secondary); line-height: 1.9; font-size: 14px; min-height: 180px; }

/* ========== Dialog ========== */
.dh { color: #E6A23C; font-size: 12px; margin-bottom: 14px; }
.rn-list { display: flex; flex-direction: column; gap: 6px; max-height: 260px; overflow-y: auto; }
.rn-i { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: var(--bg-secondary); border-radius: 8px; cursor: pointer; border: 1px solid transparent; transition: all .2s; }
.rn-i:hover { border-color: rgba(200,164,90,.3); }
.rn-i.sel { border-color: var(--gold-primary); background: rgba(200,164,90,.08); }
.rnn { font-weight: 600; font-size: 13px; color: var(--text-primary); }
.rni { font-size: 11px; color: var(--text-muted); margin-left: auto; }
</style>
