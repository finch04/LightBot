<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <img src="/lightbot-logo.svg" alt="LightBot" />
        <h1>LightBot</h1>
      </div>
      <p class="auth-subtitle">创建新账号</p>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="3-32个字符" autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="6-64个字符" autocomplete="new-password" />
        </div>
        <div class="form-group">
          <label>昵称 <span class="optional">可选</span></label>
          <input v-model="form.nickname" type="text" placeholder="你的昵称" />
        </div>
        <div class="form-group">
          <label>邮箱 <span class="optional">可选</span></label>
          <input v-model="form.email" type="email" placeholder="your@email.com" />
        </div>
        <button class="btn-submit" type="submit" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <p class="auth-footer">
        已有账号？<router-link to="/login">登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const loading = ref(false)

const form = reactive({ username: '', password: '', nickname: '', email: '' })

async function handleRegister() {
  if (!form.username || form.username.length < 3) {
    ElMessage.warning('用户名至少3个字符')
    return
  }
  if (!form.password || form.password.length < 6) {
    ElMessage.warning('密码至少6个字符')
    return
  }
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
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
.optional {
  font-weight: 400;
  color: #a1a1aa;
  font-size: 12px;
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
