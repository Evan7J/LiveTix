<template>
  <div class="auth-page">
    <div class="auth-card card-dark">
      <div class="auth-header">
        <span class="text-gold" style="font-size: 28px; font-weight: 700;">LiveTix</span>
        <p>欢迎回来，请登录您的账号</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" class="auth-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button class="btn-gold" size="large" style="width: 100%;" @click="handleLogin" :loading="loading">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
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
import { login } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    userStore.setLogin(res.data)
    toast.success('登录成功')
    if (res.data.role === 'admin') {
      router.push('/admin')
    } else {
      router.push('/')
    }
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
