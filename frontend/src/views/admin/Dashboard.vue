<template>
  <div class="dashboard">
    <!-- KPI Cards -->
    <div class="kpi-grid">
      <div class="kpi-card card-dark">
        <div class="kpi-label">总票房收入</div>
        <div class="kpi-value">
          <span class="text-gold">¥{{ formatNum(data.totalRevenue) }}</span>
        </div>
        <div class="kpi-sub" v-if="data.todayRevenue">今日 +¥{{ formatNum(data.todayRevenue) }}</div>
        <div class="kpi-icon">💰</div>
      </div>
      <div class="kpi-card card-dark">
        <div class="kpi-label">总订单量</div>
        <div class="kpi-value">{{ formatNum(data.totalOrderCount) }}</div>
        <div class="kpi-sub" v-if="data.todayOrderCount">今日 +{{ formatNum(data.todayOrderCount) }}</div>
        <div class="kpi-icon">📋</div>
      </div>
      <div class="kpi-card card-dark">
        <div class="kpi-label">用户总数</div>
        <div class="kpi-value">{{ formatNum(data.totalUserCount) }}</div>
        <div class="kpi-sub" v-if="data.newUserCount">新增 {{ formatNum(data.newUserCount) }}</div>
        <div class="kpi-icon">👥</div>
      </div>
      <div class="kpi-card card-dark">
        <div class="kpi-label">在售演出</div>
        <div class="kpi-value">{{ formatNum(data.activeShowCount) }}</div>
        <div class="kpi-sub" v-if="data.totalShowCount">共 {{ data.totalShowCount }} 场</div>
        <div class="kpi-icon">🎭</div>
      </div>
    </div>

    <!-- Charts Row 1: Revenue + Hot Shows -->
    <div class="charts-row">
      <div class="chart-card card-dark">
        <h3>📈 近30天票房趋势</h3>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card card-dark">
        <h3>🏆 热门演出排行</h3>
        <div ref="hotChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- Charts Row 2: Order Status + User Growth -->
    <div class="charts-row">
      <div class="chart-card card-dark">
        <h3>📊 订单状态分布</h3>
        <div ref="orderPieRef" class="chart-container"></div>
      </div>
      <div class="chart-card card-dark">
        <h3>👥 用户增长趋势</h3>
        <div ref="userGrowthRef" class="chart-container"></div>
      </div>
    </div>

    <!-- Latest Orders -->
    <div class="card-dark table-card">
      <div class="table-header">
        <h3>最新订单</h3>
        <el-button text size="small" @click="$router.push('/admin/orders')" style="color: var(--gold-primary);">
          查看全部 →
        </el-button>
      </div>
      <el-table :data="data.latestOrders || []" style="width: 100%;" :empty-text="'暂无订单数据'">
        <el-table-column prop="order_no" label="订单编号" width="180">
          <template #default="{ row }">
            <span style="font-family: monospace; font-size: 12px;">{{ row.order_no }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="show_title" label="演出名称" min-width="200">
          <template #default="{ row }">
            <span style="display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;">{{ row.show_title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="user_nickname" label="用户" width="100" />
        <el-table-column prop="pay_amount" label="金额" width="100">
          <template #default="{ row }">
            <span class="text-gold" style="font-weight: 600;">¥{{ row.pay_amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'paid' ? 'success' : row.status === 'pending' ? 'warning' : row.status === 'cancelled' ? 'info' : ''"
              size="small"
            >
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="时间" width="160">
          <template #default="{ row }">{{ formatDate(row.create_time) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import { getDashboard } from '@/api'
import * as echarts from 'echarts'
import dayjs from 'dayjs'

const data = ref({})
const trendChartRef = ref(null)
const hotChartRef = ref(null)
const orderPieRef = ref(null)
const userGrowthRef = ref(null)

const chartInstances = []

const statusMap = {
  pending: '待支付',
  paid: '已支付',
  completed: '已完成',
  cancelled: '已取消',
  refunding: '退款中',
  refunded: '已退款',
}

function formatNum(val) {
  if (val == null) return '0'
  return Number(val).toLocaleString()
}

function formatDate(date) {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '--'
}

function initTrendChart(trendData) {
  if (!trendChartRef.value) return
  const chart = echarts.init(trendChartRef.value, 'dark')
  chartInstances.push(chart)
  const dates = (trendData || []).map(d => d.date)
  const amounts = (trendData || []).map(d => d.amount)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,20,20,0.95)',
      borderColor: 'var(--border-color)',
      textStyle: { color: '#fff', fontSize: 12 },
    },
    grid: { left: 55, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#2A2A2A' } },
      axisLabel: { fontSize: 11, color: '#6B6B6B' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1A1A1A' } },
      axisLabel: { fontSize: 11, color: '#6B6B6B', formatter: '¥{value}' },
    },
    series: [{
      data: amounts,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { color: '#D4A853', width: 2 },
      itemStyle: { color: '#D4A853' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(212, 168, 83, 0.3)' },
          { offset: 1, color: 'rgba(212, 168, 83, 0.02)' },
        ]),
      },
    }],
  })
}

function initHotChart(hotData) {
  if (!hotChartRef.value) return
  const chart = echarts.init(hotChartRef.value, 'dark')
  chartInstances.push(chart)
  const names = (hotData || []).map(d => d.name).reverse()
  const values = (hotData || []).map(d => d.sales).reverse()

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(20,20,20,0.95)',
      borderColor: '#2A2A2A',
    },
    grid: { left: 120, right: 40, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1A1A1A' } },
      axisLabel: { fontSize: 11, color: '#6B6B6B' },
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLabel: { fontSize: 11, color: '#B0B0B0', width: 100, overflow: 'truncate' },
    },
    series: [{
      data: values,
      type: 'bar',
      barWidth: 18,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#D4A853' },
          { offset: 1, color: '#F5D78C' },
        ]),
        borderRadius: [0, 4, 4, 0],
      },
      label: { show: true, position: 'right', fontSize: 11, color: '#6B6B6B' },
    }],
  })
}

function initOrderPie(orderStatus) {
  if (!orderPieRef.value) return
  const chart = echarts.init(orderPieRef.value, 'dark')
  chartInstances.push(chart)

  const pieData = (orderStatus || []).length > 0 ? orderStatus : [
    { name: '已支付', value: 45 },
    { name: '待支付', value: 20 },
    { name: '已完成', value: 25 },
    { name: '已取消', value: 10 },
  ]

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(20,20,20,0.95)',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      bottom: 10,
      textStyle: { color: '#B0B0B0', fontSize: 11 },
    },
    series: [{
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 4, borderColor: '#0A0A0A', borderWidth: 2 },
      label: { show: true, position: 'outside', fontSize: 11 },
      data: pieData,
      color: ['#67C23A', '#E6A23C', '#D4A853', '#909399', '#F56C6C'],
    }],
  })
}

function initUserGrowth(userData) {
  if (!userGrowthRef.value) return
  const chart = echarts.init(userGrowthRef.value, 'dark')
  chartInstances.push(chart)

  const dates = (userData || []).map(d => d.date)
  const counts = (userData || []).map(d => d.count)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,20,20,0.95)',
    },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { fontSize: 11, color: '#6B6B6B' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1A1A1A' } },
      axisLabel: { fontSize: 11, color: '#6B6B6B' },
    },
    series: [{
      data: counts,
      type: 'bar',
      barWidth: 14,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#67C23A' },
          { offset: 1, color: 'rgba(103, 194, 58, 0.2)' },
        ]),
        borderRadius: [4, 4, 0, 0],
      },
    }],
  })
}

function handleResize() {
  chartInstances.forEach(c => { try { c.resize() } catch { /* ignore */ } })
}

onMounted(async () => {
  try {
    const res = await getDashboard()
    data.value = res.data || {}
  } catch { /* ignore */ }

  await nextTick()
  initTrendChart(data.value.revenueTrend || [])
  initHotChart(data.value.hotShows || [])
  initOrderPie(data.value.orderStatus || [])
  initUserGrowth(data.value.userGrowth || [])

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => { try { c.dispose() } catch { /* ignore */ } })
})
</script>

<style scoped>
.dashboard { min-height: 100%; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 24px; }
@media (max-width: 1100px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }

.kpi-card { padding: 24px; position: relative; overflow: hidden; }
.kpi-label { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.kpi-value { font-size: 32px; font-weight: 700; }
.kpi-sub { font-size: 12px; color: #67C23A; margin-top: 4px; }
.kpi-icon { position: absolute; right: 20px; top: 20px; font-size: 36px; opacity: 0.25; transition: all 0.3s; }
.kpi-card:hover .kpi-icon { opacity: 0.5; transform: scale(1.1); }

.charts-row { display: grid; grid-template-columns: 1.5fr 1fr; gap: 20px; margin-bottom: 24px; }
@media (max-width: 900px) { .charts-row { grid-template-columns: 1fr; } }

.chart-card { padding: 24px; }
.chart-card h3 { font-size: 15px; margin-bottom: 16px; color: var(--text-primary); font-weight: 600; }
.chart-container { height: 280px; width: 100%; }

.table-card { padding: 24px; }
.table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.table-header h3 { font-size: 15px; color: var(--text-primary); font-weight: 600; }
</style>
