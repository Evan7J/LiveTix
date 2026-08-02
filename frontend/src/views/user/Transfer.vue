<template>
  <div class="card-dark section-card">
    <h3>票券转赠</h3>
    <p class="hint">将已购买的演出票券转赠给他人，转赠后票券将归属于对方。</p>

    <!-- Step 1: 选择要转赠的票券 -->
    <div v-if="step === 1">
      <h4>选择要转赠的票券</h4>
      <div v-if="paidOrders.length > 0">
        <div v-for="order in paidOrders" :key="order.id" :class="['ticket-card', { selected: selectedOrder?.id === order.id }]" @click="selectedOrder = order">
          <div class="ticket-info">
            <span class="ticket-show">{{ order.showTitle }}</span>
            <span class="ticket-detail">{{ order.ticketType }} × {{ order.quantity }}张</span>
            <span class="ticket-detail">{{ formatDate(order.showTime) }}</span>
          </div>
          <div class="ticket-amount text-gold">¥{{ order.payAmount }}</div>
        </div>
        <el-button class="btn-gold" style="margin-top: 16px;" @click="step = 2" :disabled="!selectedOrder">
          下一步：填写接收人信息
        </el-button>
      </div>
      <el-empty v-else description="没有可转赠的票券" />
    </div>

    <!-- Step 2: 填写接收人信息 -->
    <div v-if="step === 2">
      <h4>填写接收人信息</h4>
      <el-form :model="transferForm" label-width="100px" class="transfer-form">
        <el-form-item label="接收人手机号">
          <el-input v-model="transferForm.phone" placeholder="请输入接收人手机号" />
        </el-form-item>
        <el-form-item label="接收人实名">
          <el-input v-model="transferForm.realName" placeholder="请输入接收人真实姓名（与实名信息一致）" />
        </el-form-item>
        <el-form-item label="转赠留言">
          <el-input v-model="transferForm.message" type="textarea" :rows="2" placeholder="写一段话给接收人..." />
        </el-form-item>
      </el-form>
      <div class="transfer-actions">
        <el-button @click="step = 1">返回上一步</el-button>
        <el-button class="btn-gold" @click="confirmTransfer" :loading="transferring">确认转赠</el-button>
      </div>
    </div>

    <!-- Step 3: 转赠成功 -->
    <div v-if="step === 3" class="transfer-success">
      <div class="success-icon">🎉</div>
      <h4>转赠成功！</h4>
      <p>票券已成功转赠给 {{ transferForm.realName }} ({{ transferForm.phone }})</p>
      <el-button class="btn-gold" @click="resetTransfer">返回票券列表</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { getMyOrders } from '@/api'
import request from '@/utils/request'
import dayjs from 'dayjs'

const step = ref(1)
const paidOrders = ref([])
const selectedOrder = ref(null)
const transferring = ref(false)
const transferForm = ref({ phone: '', realName: '', message: '' })

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadOrders() {
  try {
    const res = await getMyOrders({ page: 1, pageSize: 50, status: 'paid' })
    paidOrders.value = res.data?.records || []
  } catch { /* ignore */ }
}

async function confirmTransfer() {
  if (!transferForm.value.phone) { toast.warning('请输入接收人手机号'); return }
  if (!transferForm.value.realName) { toast.warning('请输入接收人姓名'); return }

  try {
    await ElMessageBox.confirm(
      `确认将「${selectedOrder.value.showTitle}」转赠给 ${transferForm.value.realName}？\n转赠后票券将归属对方，无法撤回。`,
      '确认转赠', { type: 'warning' }
    )
  } catch { return }

  transferring.value = true
  try {
    await request.post(`/user/orders/${selectedOrder.value.id}/transfer`, transferForm.value)
    toast.success('转赠成功')
    step.value = 3
  } catch { /* ignore */ } finally { transferring.value = false }
}

function resetTransfer() {
  step.value = 1
  selectedOrder.value = null
  transferForm.value = { phone: '', realName: '', message: '' }
  loadOrders()
}

onMounted(() => loadOrders())
</script>

<style scoped>
.section-card { padding: 24px; }
.section-card h3 { font-size: 16px; margin-bottom: 8px; }
.hint { color: var(--text-muted); font-size: 13px; margin-bottom: 20px; }
h4 { font-size: 15px; margin-bottom: 12px; }

.ticket-card { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; background: var(--bg-secondary); border-radius: 8px; margin-bottom: 8px; cursor: pointer; border: 2px solid transparent; transition: all 0.2s; }
.ticket-card:hover { border-color: rgba(212,168,83,0.3); }
.ticket-card.selected { border-color: var(--gold-primary); background: rgba(212,168,83,0.08); }
.ticket-info { display: flex; flex-direction: column; gap: 2px; }
.ticket-show { font-size: 14px; font-weight: 600; }
.ticket-detail { font-size: 12px; color: var(--text-secondary); }
.ticket-amount { font-size: 20px; font-weight: 700; }

.transfer-form { max-width: 420px; margin-top: 16px; }
.transfer-actions { display: flex; gap: 12px; margin-top: 16px; }

.transfer-success { text-align: center; padding: 40px 0; }
.success-icon { font-size: 64px; margin-bottom: 16px; }
.transfer-success h4 { font-size: 20px; margin-bottom: 8px; }
.transfer-success p { color: var(--text-secondary); margin-bottom: 20px; }
</style>
