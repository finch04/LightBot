<template>
  <div class="login-wrapper">
    <!-- 后端不可用提示 -->
    <div v-if="isBackendDown" class="health-banner">
      <span>后端服务未就绪，请稍后刷新页面重试</span>
    </div>

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

    <!-- 底部链接 -->
    <div class="page-footer">
      <div class="footer-links">
        <a href="https://github.com/finch04" target="_blank" rel="noopener">联系我们</a>
        <span class="divider">|</span>
        <a href="https://github.com/finch04/LightBot" target="_blank" rel="noopener">使用帮助</a>
      </div>
      <div class="footer-copyright">© 2026 LightBot. All Rights Reserved.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { checkHealth } from '../api/systemConfig'
import { UserOutlined, LockOutlined, LoadingOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const isBackendDown = ref(false)

const form = reactive({ username: '', password: '' })

onMounted(async () => {
  try {
    await checkHealth()
  } catch {
    isBackendDown.value = true
  }
})

async function handleLogin() {
  if (!form.username || !form.password) {
    message.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    message.success('登录成功')
    router.push('/app')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  position: relative;
}

.health-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: var(--color-error-bg);
  border-bottom: 1px solid #ffccc7;
  padding: 10px 16px;
  text-align: center;
  font-size: 14px;
  color: #cf1322;
}

/* 登录卡片 */
.login-card {
  display: flex;
  width: 900px;
  height: 560px;
  background: var(--color-canvas);
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
  color: var(--color-mute);
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
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 0 10px;
  transition: all 0.2s;
  background: var(--color-canvas);
}

.input-wrapper:hover {
  border-color: var(--color-mute);
}

.input-wrapper:focus-within {
  border-color: var(--color-ink);
  box-shadow: 0 0 0 2px rgba(23, 23, 23, 0.08);
}

.input-icon {
  color: var(--color-mute);
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
  color: var(--color-mute);
}

/* 登录按钮 */
.btn-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  background: var(--color-primary);
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
  color: var(--color-mute);
}

.form-footer .link {
  color: var(--color-link);
  text-decoration: none;
  margin-left: 4px;
}

.form-footer .link:hover {
  color: #0761d1;
}

/* 底部链接 */
.page-footer {
  position: fixed;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 13px;
  color: var(--color-mute);
}

.page-footer a {
  color: var(--color-mute);
  text-decoration: none;
  transition: color 0.15s;
}

.page-footer a:hover {
  color: var(--color-ink);
}

.page-footer .divider {
  margin: 0 8px;
  color: #d9d9d9;
}

.footer-links {
  margin-bottom: 4px;
}

.footer-copyright {
  color: var(--color-mute);
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
