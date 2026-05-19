<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <div class="login-brand">
      <div class="brand-content">
        <img src="/lightbot-logo.svg" alt="LightBot" class="brand-logo" />
        <h1 class="brand-title">LightBot</h1>
        <p class="brand-desc">轻量级 AI Agent 开发平台</p>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-form-area">
      <div class="form-wrapper">
        <div class="form-header">
          <img src="/lightbot-logo.svg" alt="LightBot" class="form-logo" />
          <h2>欢迎登录</h2>
          <p>登录你的 LightBot 账号</p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label>用户名</label>
            <input
              v-model="form.username"
              type="text"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </div>
          <button class="btn-submit" type="submit" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <p class="form-footer">
          没有账号？<router-link to="/register">注册</router-link>
        </p>
      </div>
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
.login-page {
  display: flex;
  height: 100vh;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* 左侧品牌区 */
.login-brand {
  flex: 1;
  background: #171717;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  position: relative;
  overflow: hidden;
}

.login-brand::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 50%, rgba(0, 112, 243, 0.08) 0%, transparent 50%);
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
}

.brand-logo {
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.brand-desc {
  font-size: 16px;
  color: #a1a1aa;
  margin-bottom: 48px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  text-align: left;
  max-width: 280px;
  margin: 0 auto;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #d4d4d8;
}

.feature-icon {
  font-size: 20px;
  width: 32px;
  text-align: center;
}

/* 右侧表单区 */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 60px;
}

.form-wrapper {
  width: 100%;
  max-width: 380px;
}

.form-header {
  margin-bottom: 36px;
}

.form-logo {
  width: 40px;
  height: 40px;
  margin-bottom: 20px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}

.form-header p {
  font-size: 14px;
  color: #71717a;
}

.login-form {
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
  height: 44px;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
  background: #fafafa;
}

.form-group input:focus {
  border-color: #0070f3;
  background: #fff;
}

.btn-submit {
  height: 44px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
  margin-top: 4px;
}

.btn-submit:hover:not(:disabled) {
  background: #27272a;
}

.btn-submit:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

.form-footer {
  text-align: center;
  font-size: 14px;
  color: #71717a;
  margin-top: 24px;
}

.form-footer a {
  color: #0070f3;
  text-decoration: none;
}

.form-footer a:hover {
  text-decoration: underline;
}

/* 响应式：小屏幕隐藏左侧品牌区 */
@media (max-width: 768px) {
  .login-brand {
    display: none;
  }
  .login-form-area {
    padding: 40px 24px;
  }
}
</style>
