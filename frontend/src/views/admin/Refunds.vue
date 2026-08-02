<template>
  <div>
    <h3>退票审核</h3>
    <div class="status-tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="['tab-btn', { active: activeTab === tab.value }]" @click="activeTab = tab.value; loadList()">{{ tab.label }}</button>
      <span v-if="activeTab==='pending' && list.length" style="margin-left:auto;font-size:13px;color:var(--text-muted)">共 {{ list.length }} 条待审核</span>
    </div>

    <el-table :data="list" style="width: 100%;" v-loading="loading">
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column label="用户/订单" min-width="180">
        <template #default="{ row }">
          <div style="font-size:13px">{{ row.username || '用户'+row.userId }}</div>
          <div style="font-size:11px;color:var(--text-muted)">{{ row.showTitle || '' }}</div>
          <div v-if="row.ticketType" style="font-size:10px;color:var(--text-muted)">{{ row.ticketType }}×{{ row.quantity }}</div>
        </template>
      </el-table-column>
      <el-table-column label="退款金额" width="140">
        <template #default="{ row }">
          <span class="text-gold" style="font-weight:700">¥{{ row.refundAmount }}</span>
          <span v-if="row.feeAmount > 0" style="color:var(--text-muted);font-size:11px;margin-left:2px">(费¥{{ row.feeAmount }})</span>
        </template>
      </el-table-column>
      <el-table-column label="原因" min-width="150">
        <template #default="{ row }"><span style="font-size:12px">{{ row.reason }}</span></template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="160">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column v-if="activeTab !== 'pending'" label="审核结果" width="280">
        <template #default="{ row }">
          <span :style="{color: row.status==='approved'?'#67C23A':'#F56C6C'}">{{ row.status === 'approved' ? '✓ 已通过' : '✕ 已驳回' }}</span>
          <span v-if="row.reviewComment" style="color:var(--text-muted);font-size:12px;margin-left:6px">— {{ row.reviewComment }}</span>
          <div style="font-size:11px;color:var(--text-muted)">{{ fmt(row.reviewTime) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" v-if="activeTab === 'pending'">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
          <el-button size="small" type="danger" @click="handleReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectDialog" title="驳回退款申请" width="480px" :close-on-click-modal="false">
      <div class="rd-section">
        <div class="rd-row"><span>用户</span><span>{{ cur?.username || '用户'+cur?.userId }}</span></div>
        <div class="rd-row"><span>演出</span><span>{{ cur?.showTitle || '--' }}</span></div>
        <div class="rd-row"><span>退款金额</span><span class="text-gold" style="font-weight:700">¥{{ cur?.refundAmount }}</span></div>
        <div class="rd-row"><span>申请原因</span><span>{{ cur?.reason }}</span></div>
      </div>
      <div class="rd-section">
        <h4>🔗 驳回后关联变化</h4>
        <div class="rd-chain">
          <span class="rd-step err">驳回申请</span><span class="rd-arrow">→</span>
          <span class="rd-step">订单恢复已支付</span><span class="rd-arrow">→</span>
          <span class="rd-step">通知用户</span>
        </div>
      </div>
      <div style="margin-top:12px">
        <p style="color:var(--text-muted);font-size:13px;margin-bottom:6px">请填写驳回原因：</p>
        <el-input v-model="rejectComment" type="textarea" placeholder="如：超过退票时限 / 不符合退票条件..." :rows="3" />
      </div>
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="submitting" :disabled="!rejectComment.trim()">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, h } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { adminGetRefunds, adminApproveRefund, adminRejectRefund } from '@/api'
import dayjs from 'dayjs'

const list = ref([])
const loading = ref(false)
const submitting = ref(false)
const activeTab = ref('pending')
const tabs = [
  { label: '待审核', value: 'pending' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' },
]

const rejectDialog = ref(false)
const rejectComment = ref('')
const cur = ref(null)

function fmt(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadList() {
  loading.value = true
  try {
    const res = await adminGetRefunds({ page: 1, pageSize: 100, status: activeTab.value })
    list.value = res.data?.records || []
  } finally { loading.value = false }
}

// ===== 通过：ElMessageBox 二次确认（可靠弹窗） =====
function handleApprove(row) {
  const fee = row.feeAmount || 0
  const net = (row.refundAmount || 0) - fee
  const msg = h('div', { style: 'line-height:2;font-size:14px' }, [
    h('p', [h('strong', '用户：'), row.username || ('用户' + row.userId)]),
    h('p', [h('strong', '演出：'), row.showTitle || '--']),
    row.ticketType ? h('p', [h('strong', '票种：'), row.ticketType + ' ×' + row.quantity + '张']) : null,
    h('p', [h('strong', '退款金额：'), h('span', { style: 'color:#C8A45A;font-size:22px;font-weight:700' }, '¥' + row.refundAmount)]),
    fee > 0 ? h('p', [h('strong', '手续费：'), '¥' + fee]) : null,
    fee > 0 ? h('p', [h('strong', '实退：'), h('span', { style: 'color:#C8A45A;font-weight:600' }, '¥' + net)]) : null,
    h('p', [h('strong', '原因：'), row.reason]),
    h('div', { style: 'margin-top:12px;padding:10px;background:rgba(103,194,58,.08);border-radius:6px;font-size:12px;color:#67C23A' }, [
      '🔗 通过审核 → 订单已退款 → 恢复库存 → 退款到余额 → 通知用户',
    ]),
  ])

  ElMessageBox.confirm(msg, '退款审核 — 确认通过？', {
    confirmButtonText: '确认通过 · 退款 ¥' + row.refundAmount,
    cancelButtonText: '取消',
    type: 'warning',
    distinguishCancelAndClose: true,
    confirmButtonClass: 'el-button--success',
  }).then(() => {
    doApprove(row.id)
  }).catch(() => {})
}

async function doApprove(id) {
  submitting.value = true
  try {
    await adminApproveRefund(id, { comment: '审核通过' })
    toast.success('退款已通过！库存已恢复，金额已退回用户钱包')
    loadList()
  } catch { /* handled */ }
  finally { submitting.value = false }
}

// ===== 驳回：先确认 → 弹窗填原因 =====
function handleReject(row) {
  cur.value = row
  rejectComment.value = ''
  rejectDialog.value = true
}

async function confirmReject() {
  if (!rejectComment.value.trim()) return
  submitting.value = true
  try {
    await adminRejectRefund(cur.value.id, { comment: rejectComment.value })
    toast.success('已驳回，订单恢复为已支付状态，用户将收到通知')
    rejectDialog.value = false
    loadList()
  } catch { /* handled */ }
  finally { submitting.value = false }
}

onMounted(() => loadList())
</script>

<style scoped>
h3 { font-size: 18px; margin-bottom: 16px; }
.status-tabs { display: flex; gap: 8px; margin-bottom: 16px; align-items: center; }
.tab-btn { padding: 6px 16px; border: 1px solid var(--border-color); background: transparent; color: var(--text-secondary); border-radius: 16px; cursor: pointer; font-size: 13px; transition: all 0.3s; }
.tab-btn.active { background: var(--gold-gradient); border-color: transparent; color: #000; font-weight: 600; }
.rd-section { margin-bottom: 12px; }
.rd-section h4 { font-size: 14px; color: var(--text-primary); margin-bottom: 6px; }
.rd-row { display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid rgba(255,255,255,.04); font-size: 14px; color: var(--text-secondary); }
.rd-chain { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; font-size: 12px; padding: 10px; background: rgba(255,255,255,.03); border-radius: 6px; }
.rd-step { padding: 3px 8px; background: rgba(255,255,255,.05); border-radius: 4px; white-space: nowrap; }
.rd-step.err { color: #F56C6C; border: 1px solid rgba(245,108,108,.3); }
.rd-arrow { color: #555; font-size: 11px; }
</style>
