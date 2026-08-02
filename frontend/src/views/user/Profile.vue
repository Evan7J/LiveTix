<template>
  <div>
    <!-- Profile Edit -->
    <div class="card-dark section-card">
      <h3>编辑资料</h3>
      <el-form :model="editForm" label-width="80px" class="edit-form">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" @click="handleUpdateProfile" :loading="saving">保存修改</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Account Info -->
    <div class="card-dark section-card">
      <h3>账户信息</h3>
      <div class="info-grid">
        <div class="info-item"><label>账户余额</label><span class="text-gold" style="font-size: 22px; font-weight: 700;">¥{{ profile.balance || '0.00' }}</span></div>
        <div class="info-item"><label>注册时间</label><span>{{ formatDate(profile.createTime) }}</span></div>
        <div class="info-item"><label>角色</label><span>{{ userStore.role === 'admin' ? '管理员' : '普通用户' }}</span></div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getProfile, updateProfile } from '@/api'
import { toast } from '@/utils/message'
import dayjs from 'dayjs'

const userStore = useUserStore()
const profile = ref({})
const saving = ref(false)

const editForm = reactive({ nickname: '', phone: '', email: '' })

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '--' }

async function loadProfile() {
  try {
    const res = await getProfile()
    profile.value = res.data
    editForm.nickname = res.data.nickname || ''
    editForm.phone = res.data.phone || ''
    editForm.email = res.data.email || ''
  } catch { /* ignore */ }
}

async function handleUpdateProfile() {
  saving.value = true
  try {
    await updateProfile({ nickname: editForm.nickname, phone: editForm.phone, email: editForm.email })
    userStore.nickname = editForm.nickname
    toast.success('资料更新成功')
    loadProfile()
  } finally { saving.value = false }
}

onMounted(() => loadProfile())
</script>

<style scoped>
.section-card { padding: 24px; margin-bottom: 20px; }
.section-card h3 { font-size: 16px; margin-bottom: 16px; }
.edit-form { max-width: 420px; }
.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.info-item label { display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.info-item span { font-size: 16px; color: var(--text-primary); }
</style>
