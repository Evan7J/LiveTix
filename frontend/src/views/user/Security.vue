<template>
  <div>
    <!-- Change Password -->
    <div class="card-dark section-card">
      <h3>修改密码</h3>
      <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="100px" class="edit-form">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" @click="handleChangePwd" :loading="changing">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Bind Phone -->
    <div class="card-dark section-card">
      <h3>绑定手机</h3>
      <el-form :model="phoneForm" label-width="100px" class="edit-form">
        <el-form-item label="手机号">
          <el-input v-model="phoneForm.phone" placeholder="请输入新手机号" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="phoneForm.code" placeholder="验证码" style="flex: 1;" />
            <el-button class="btn-gold-outline" @click="sendCode('phone')" :loading="sendingPhone">发送验证码</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" @click="handleBindPhone" :loading="bindingPhone">绑定手机</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Bind Email -->
    <div class="card-dark section-card">
      <h3>绑定邮箱</h3>
      <el-form :model="emailForm" label-width="100px" class="edit-form">
        <el-form-item label="邮箱">
          <el-input v-model="emailForm.email" placeholder="请输入新邮箱" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="emailForm.code" placeholder="验证码" style="flex: 1;" />
            <el-button class="btn-gold-outline" @click="sendCode('email')" :loading="sendingEmail">发送验证码</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" @click="handleBindEmail" :loading="bindingEmail">绑定邮箱</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { toast } from '@/utils/message'
import { changePassword, sendVerifyCode, bindPhone, bindEmail } from '@/api'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const userStore = useUserStore()
const pwdFormRef = ref(null)
const changing = ref(false)
const sendingPhone = ref(false)
const sendingEmail = ref(false)
const bindingPhone = ref(false)
const bindingEmail = ref(false)

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (rule, value, callback) => { if (value !== pwdForm.newPassword) callback(new Error('两次密码不一致')); else callback() }, trigger: 'blur' }],
}

const phoneForm = reactive({ phone: '', code: '' })
const emailForm = reactive({ email: '', code: '' })

async function handleChangePwd() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  changing.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, password: pwdForm.newPassword })
    toast.success('密码修改成功，请重新登录')
    pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdForm.confirmPassword = ''
    userStore.logout()
    setTimeout(() => window.location.href = '/login', 1000)
  } finally { changing.value = false }
}

async function sendCode(type) {
  const loading = type === 'phone' ? 'sendingPhone' : 'sendingEmail'
  try {
    await sendVerifyCode({ target: type === 'phone' ? phoneForm.phone : emailForm.email, type })
    toast.success('验证码已发送（模拟）')
  } catch { /* ignore */ }
}

async function handleBindPhone() {
  bindingPhone.value = true
  try {
    await bindPhone({ phone: phoneForm.phone, code: phoneForm.code })
    toast.success('手机号绑定成功')
  } finally { bindingPhone.value = false }
}

async function handleBindEmail() {
  bindingEmail.value = true
  try {
    await bindEmail({ email: emailForm.email, code: emailForm.code })
    toast.success('邮箱绑定成功')
  } finally { bindingEmail.value = false }
}
</script>

<style scoped>
.section-card { padding: 24px; margin-bottom: 20px; }
.section-card h3 { font-size: 16px; margin-bottom: 16px; }
.edit-form { max-width: 460px; }
.code-row { display: flex; gap: 12px; width: 100%; }
</style>
