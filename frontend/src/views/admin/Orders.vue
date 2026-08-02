<template>
  <div class="admin-page">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索订单号/演出..." style="width: 240px;" clearable @clear="search" @keyup.enter="search" />
      <el-select v-model="statusFilter" placeholder="状态筛选" style="width: 140px;" clearable @change="load">
        <el-option label="待支付" value="pending" />
        <el-option label="已支付" value="paid" />
        <el-option label="已取消" value="cancelled" />
        <el-option label="已退款" value="refunded" />
      </el-select>
    </div>

    <el-table :data="orders" style="width: 100%;">
      <el-table-column prop="orderNo" label="订单编号" width="200" />
      <el-table-column prop="showTitle" label="演出名称" min-width="180" />
      <el-table-column prop="ticketType" label="票种" width="100" />
      <el-table-column prop="quantity" label="数量" width="60" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }"><span class="text-gold">¥{{ row.payAmount }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="160">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 'paid'" size="small" text type="danger" @click="handleRefund(row)">退款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" background @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { toast } from '@/utils/message'
import { adminGetOrders, adminRefundOrder } from '@/api'
import dayjs from 'dayjs'

const orders = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref('')

function formatDate(d) { return dayjs(d).format('YYYY-MM-DD HH:mm') }
function statusText(s) { const m = { pending: '待支付', paid: '已支付', cancelled: '已取消', refunded: '已退款' }; return m[s] || s }
function statusTag(s) { const m = { pending: 'warning', paid: 'success', cancelled: 'info', refunded: 'danger' }; return m[s] || 'info' }

async function load() {
  const res = await adminGetOrders({ page: page.value, pageSize: pageSize.value, keyword: keyword.value, status: statusFilter.value || undefined })
  orders.value = res.data.records
  total.value = res.data.total
}

function search() { page.value = 1; load() }

async function handleRefund(row) {
  await adminRefundOrder(row.id)
  toast.success('退款成功')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.admin-page { min-height: 100%; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
