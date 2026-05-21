<template>
  <div class="login-wrapper">
    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- 左侧背景图 -->
      <div class="card-left">
        <img src="/login-bg.png" alt="Login Background" class="bg-image" />
      </div>

      <!-- 右侧表单 -->
      <div class="card-right">
        <div class="form-header">
          <img src="/lightbot-logo-single.png" alt="LightBot" class="form-logo" />
          <h2>欢迎回来</h2>
          <p>登录你的 LightBot 账号</p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-item">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <input
                v-model="form.username"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="form-item">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
            </div>
          </div>

          <button class="btn-login" type="submit" :disabled="loading">
            <LoadingOutlined v-if="loading" />
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </form>

        <div class="form-footer">
          <span>没有账号？</span>
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { UserOutlined, LockOutlined, LoadingOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    message.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    message.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  overflow: hidden;
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 登录卡片 */
.login-card {
  display: flex;
  width: 900px;
  height: 500px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0px 1px 1px rgba(0,0,0,0.02), 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 16px -4px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
  overflow: hidden;
}

/* 左侧背景图 */
.card-left {
  width: 380px;
  flex-shrink: 0;
  overflow: hidden;
}

.bg-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 右侧表单 */
.card-right {
  flex: 1;
  padding: 40px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header {
  margin-bottom: 28px;
  text-align: center;
}

.form-logo {
  height: 56px;
  object-fit: contain;
  margin-bottom: 16px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #141414;
  margin: 0 0 6px;
}

.form-header p {
  font-size: 14px;
  color: #8c8c8c;
  margin: 0;
}

/* 表单 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #141414;
}

.input-wrapper {
  display: flex;
  align-items: center;
  height: 36px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 0 10px;
  transition: all 0.2s;
  background: #ffffff;
}

.input-wrapper:hover {
  border-color: #a1a1aa;
}

.input-wrapper:focus-within {
  border-color: #171717;
  box-shadow: 0 0 0 2px rgba(23, 23, 23, 0.08);
}

.input-icon {
  color: #bfbfbf;
  font-size: 14px;
  margin-right: 8px;
}

.input-wrapper input {
  flex: 1;
  height: 100%;
  border: none;
  outline: none;
  font-size: 14px;
  color: #141414;
  background: transparent;
}

.input-wrapper input::placeholder {
  color: #bfbfbf;
}

/* 登录按钮 */
.btn-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  background: #171717;
  color: #ffffff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 4px;
}

.btn-login:hover:not(:disabled) {
  background: #27272a;
}

.btn-login:active:not(:disabled) {
  background: #0a0a0a;
}

.btn-login:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

/* 底部链接 */
.form-footer {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
  color: #8c8c8c;
}

.form-footer .link {
  color: #0070f3;
  text-decoration: none;
  margin-left: 4px;
}

.form-footer .link:hover {
  color: #0761d1;
}

/* 响应式 */
@media (max-width: 960px) {
  .login-card {
    width: 90%;
    height: auto;
    min-height: 400px;
  }
  .card-left {
    display: none;
  }
  .card-right {
    padding: 32px 24px;
  }
}
</style>
