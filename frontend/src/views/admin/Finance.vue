<template>
  <div>
    <h3>财务管理</h3>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="交易流水" name="transactions">
        <el-table :data="txList" style="width: 100%;" v-loading="txLoading">
          <el-table-column prop="id" label="#" width="60" />
          <el-table-column prop="userId" label="用户ID" width="80" />
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.type === 'recharge' ? 'success' : row.type === 'refund' ? 'warning' : 'info'" size="small">
                {{ row.type === 'recharge' ? '充值' : row.type === 'refund' ? '退款' : row.type === 'purchase' ? '购票' : row.type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="120">
            <template #default="{ row }">
              <span :style="{ color: row.amount > 0 ? '#67C23A' : '#F56C6C' }">¥{{ row.amount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="balanceAfter" label="交易后余额" width="120">
            <template #default="{ row }">¥{{ row.balanceAfter }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" />
          <el-table-column prop="createTime" label="时间" width="160">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="退款记录" name="refunds">
        <el-table :data="refundList" style="width: 100%;" v-loading="refundLoading">
          <el-table-column prop="orderNo" label="订单编号" width="200" />
          <el-table-column prop="showTitle" label="演出名称" min-width="200" />
          <el-table-column prop="payAmount" label="退款金额" width="120">
            <template #default="{ row }"><span class="text-gold">¥{{ row.payAmount }}</span></template>
          </el-table-column>
          <el-table-column prop="refundTime" label="退款时间" width="160">
            <template #default="{ row }">{{ formatDate(row.refundTime) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminGetTransactions, adminGetFinanceRefunds } from '@/api'
import dayjs from 'dayjs'

const activeTab = ref('transactions')
const txList = ref([])
const txLoading = ref(false)
const refundList = ref([])
const refundLoading = ref(false)

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadTx() {
  txLoading.value = true
  try {
    const res = await adminGetTransactions({ page: 1, pageSize: 50 })
    txList.value = res.data?.records || []
  } finally { txLoading.value = false }
}

async function loadRefunds() {
  refundLoading.value = true
  try {
    const res = await adminGetFinanceRefunds({ page: 1, pageSize: 50 })
    refundList.value = res.data?.records || []
  } finally { refundLoading.value = false }
}

onMounted(() => { loadTx(); loadRefunds() })
</script>

<style scoped>
h3 { font-size: 18px; margin-bottom: 16px; }
</style>