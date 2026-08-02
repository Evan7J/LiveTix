<template>
  <div class="auth-page">
    <div class="auth-card card-dark">
      <div class="auth-header">
        <span class="text-gold" style="font-size: 28px; font-weight: 700;">LiveTix</span>
        <p>创建您的账号</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" class="auth-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-32位）" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少8位，含大小写字母和数字）" size="large" show-password />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称" size="large" />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" size="large" style="width: 100%;" @click="handleRegister" :loading="loading">
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
        <br/>
        <router-link to="/" style="font-size: 13px;">← 返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from '@/utils/message'
import { register } from '@/api'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
})

// 密码强度校验：至少8位，含大小写字母和数字
const validatePassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 8) {
    callback(new Error('密码至少8位'))
  } else if (!/[a-z]/.test(value)) {
    callback(new Error('密码需包含小写字母'))
  } else if (!/[A-Z]/.test(value)) {
    callback(new Error('密码需包含大写字母'))
  } else if (!/[0-9]/.test(value)) {
    callback(new Error('密码需包含数字'))
  } else if (value.length > 64) {
    callback(new Error('密码最多64位'))
  } else {
    callback()
  }
}

// 手机号格式校验
const validatePhone = (rule, value, callback) => {
  if (!value) {
    callback() // 手机号可留空
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度3-32位', trigger: 'blur' },
  ],
  password: [
    { required: true, validator: validatePassword, trigger: 'blur' },
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' },
  ],
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register(form)
    toast.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
}

.auth-card {
  width: 420px;
  padding: 40px;
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-header p {
  color: var(--text-secondary);
  margin-top: 8px;
  font-size: 14px;
}

.auth-footer {
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
