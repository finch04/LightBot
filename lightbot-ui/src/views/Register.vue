<template>
  <div class="register-wrapper">
    <!-- 顶部 Logo -->
    <div class="top-bar">
      <div class="top-logo">
        <img src="/lightbot-logo.svg" alt="LightBot" class="top-logo-img" />
        <span class="top-logo-text">LightBot</span>
      </div>
    </div>

    <!-- 注册卡片 -->
    <div class="register-card">
      <!-- 左侧背景图 -->
      <div class="card-left">
        <img src="/login-bg.png" alt="Register Background" class="bg-image" />
      </div>

      <!-- 右侧表单 -->
      <div class="card-right">
        <div class="form-header">
          <h2>创建账号</h2>
          <p>注册你的 LightBot 账号</p>
        </div>

        <form class="register-form" @submit.prevent="handleRegister">
          <div class="form-item">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <input
                v-model="form.username"
                type="text"
                placeholder="3-32个字符"
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
                placeholder="6-64个字符"
                autocomplete="new-password"
              />
            </div>
          </div>

          <div class="form-item">
            <label class="form-label">昵称 <span class="optional">可选</span></label>
            <div class="input-wrapper">
              <SmileOutlined class="input-icon" />
              <input
                v-model="form.nickname"
                type="text"
                placeholder="你的昵称"
              />
            </div>
          </div>

          <div class="form-item">
            <label class="form-label">邮箱 <span class="optional">可选</span></label>
            <div class="input-wrapper">
              <MailOutlined class="input-icon" />
              <input
                v-model="form.email"
                type="email"
                placeholder="your@email.com"
              />
            </div>
          </div>

          <button class="btn-register" type="submit" :disabled="loading">
            <LoadingOutlined v-if="loading" />
            <span>{{ loading ? '注册中...' : '注册' }}</span>
          </button>
        </form>

        <div class="form-footer">
          <span>已有账号？</span>
          <router-link to="/login" class="link">立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'
import { UserOutlined, LockOutlined, SmileOutlined, MailOutlined, LoadingOutlined } from '@ant-design/icons-vue'

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
.register-wrapper {
  height: 100vh;
  overflow: hidden;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 顶部栏 */
.top-bar {
  width: 100%;
  padding: 20px 0;
  display: flex;
  justify-content: center;
}

.top-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-logo-img {
  width: 32px;
  height: 32px;
}

.top-logo-text {
  font-size: 20px;
  font-weight: 600;
  color: #141414;
}

/* 注册卡片 */
.register-card {
  display: flex;
  width: 800px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  margin-top: 20px;
}

/* 左侧背景图 */
.card-left {
  width: 360px;
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
  padding: 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header {
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #141414;
  margin: 0 0 8px;
}

.form-header p {
  font-size: 14px;
  color: #8c8c8c;
  margin: 0;
}

/* 表单 */
.register-form {
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

.optional {
  font-weight: 400;
  color: #8c8c8c;
  font-size: 12px;
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
  border-color: #4096ff;
}

.input-wrapper:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.1);
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

/* 注册按钮 */
.btn-register {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 36px;
  background: #1677ff;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 4px;
}

.btn-register:hover:not(:disabled) {
  background: #4096ff;
}

.btn-register:active:not(:disabled) {
  background: #0958d9;
}

.btn-register:disabled {
  background: #bae0ff;
  cursor: not-allowed;
}

/* 底部链接 */
.form-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #8c8c8c;
}

.form-footer .link {
  color: #1677ff;
  text-decoration: none;
  margin-left: 4px;
}

.form-footer .link:hover {
  color: #4096ff;
}

/* 响应式 */
@media (max-width: 860px) {
  .register-card {
    width: 90%;
    margin: 20px;
  }
  .card-left {
    display: none;
  }
  .card-right {
    padding: 24px;
  }
}
</style>
