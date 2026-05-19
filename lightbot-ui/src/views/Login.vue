<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <img src="/lightbot-logo.svg" alt="LightBot" />
        <h1>LightBot</h1>
      </div>
      <p class="auth-subtitle">登录你的账号</p>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <button class="btn-submit" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="auth-footer">
        没有账号？<router-link to="/register">注册</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  font-family: 'Inter', -apple-system, sans-serif;
}
.auth-card {
  width: 380px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 40px 32px;
}
.auth-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}
.auth-logo img {
  width: 36px;
  height: 36px;
}
.auth-logo h1 {
  font-size: 22px;
  font-weight: 600;
  color: #171717;
}
.auth-subtitle {
  text-align: center;
  font-size: 14px;
  color: #71717a;
  margin-bottom: 32px;
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
}
.form-group input {
  height: 40px;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
}
.form-group input:focus {
  border-color: #0070f3;
}
.btn-submit {
  height: 40px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-submit:hover:not(:disabled) {
  background: #27272a;
}
.btn-submit:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.auth-footer {
  text-align: center;
  font-size: 14px;
  color: #71717a;
  margin-top: 24px;
}
.auth-footer a {
  color: #0070f3;
  text-decoration: none;
}
.auth-footer a:hover {
  text-decoration: underline;
}
</style>
