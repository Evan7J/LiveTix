<template>
  <div>
    <div class="card-dark section-card">
      <h3>账户余额</h3>
      <div class="balance-display">
        <span class="balance-amount text-gold">¥{{ balance }}</span>
        <div class="balance-actions">
          <el-button class="btn-gold" @click="showRecharge = true">充值</el-button>
        </div>
      </div>
      <p class="hint">充值金额不可提现，购票后退款原路返回</p>
    </div>

    <div class="card-dark section-card">
      <h3>交易记录</h3>
      <el-table :data="transactions" style="width: 100%;">
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'recharge' ? 'success' : row.type === 'refund' ? 'warning' : 'info'" size="small">
              {{ row.type === 'recharge' ? '充值' : row.type === 'refund' ? '退款' : row.type === 'purchase' ? '购票' : row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.amount > 0 ? '#67C23A' : '#F56C6C' }">
              {{ row.amount > 0 ? '+' : '' }}¥{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </div>

    <!-- Recharge Dialog -->
    <el-dialog v-model="showRecharge" title="余额充值" width="400px">
      <div style="text-align: center; padding: 20px;">
        <p style="margin-bottom: 16px;">选择充值金额</p>
        <div class="amount-options">
          <button v-for="a in amounts" :key="a" :class="['amount-btn', { active: selectedAmount === a }]" @click="selectedAmount = a">¥{{ a }}</button>
        </div>
        <p class="hint" style="margin-top: 12px;">单笔充值 50-5000 元</p>
        <el-button class="btn-gold" style="margin-top: 16px; width: 100%;" @click="handleRecharge" :loading="recharging">确认充值（模拟）</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { toast } from '@/utils/message'
import { getWallet, getWalletTransactions, rechargeWallet } from '@/api'
import dayjs from 'dayjs'

const balance = ref('0.00')
const transactions = ref([])
const showRecharge = ref(false)
const recharging = ref(false)
const selectedAmount = ref(100)
const amounts = [50, 100, 200, 500, 1000, 2000]

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadData() {
  try {
    const [bRes, tRes] = await Promise.all([getWallet(), getWalletTransactions({ page: 1, pageSize: 50 })])
    balance.value = (bRes.data || 0).toFixed(2)
    transactions.value = tRes.data?.records || []
  } catch { /* ignore */ }
}

async function handleRecharge() {
  recharging.value = true
  try {
    await rechargeWallet({ amount: selectedAmount.value })
    toast.success('充值成功')
    showRecharge.value = false
    loadData()
  } finally { recharging.value = false }
}

onMounted(() => loadData())
</script>

<style scoped>
.section-card { padding: 24px; margin-bottom: 20px; }
.section-card h3 { font-size: 16px; margin-bottom: 16px; }
.balance-display { display: flex; justify-content: space-between; align-items: center; }
.balance-amount { font-size: 36px; font-weight: 700; }
.hint { color: var(--text-muted); font-size: 12px; margin-top: 8px; }
.amount-options { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.amount-btn { padding: 10px 20px; border: 1px solid var(--border-color); background: transparent; color: var(--text-primary); border-radius: 8px; cursor: pointer; font-size: 16px; transition: all 0.2s; }
.amount-btn:hover { border-color: var(--gold-primary); }
.amount-btn.active { border-color: var(--gold-primary); background: rgba(212,168,83,0.2); color: var(--gold-primary); font-weight: 700; }
</style>
