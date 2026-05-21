<template>
  <div class="register-wrapper">
    <!-- 注册卡片 -->
    <div class="register-card">
      <!-- 左侧背景图 -->
      <div class="card-left">
        <img src="/login-bg.png" alt="Register Background" class="bg-image" />
      </div>

      <!-- 右侧表单 -->
      <div class="card-right">
        <div class="form-header">
          <img src="/lightbot-logo-single.png" alt="LightBot" class="form-logo" />
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
import { message } from 'ant-design-vue'
import { register } from '../api/auth'
import { UserOutlined, LockOutlined, SmileOutlined, LoadingOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loading = ref(false)

const form = reactive({ username: '', password: '', nickname: '' })

async function handleRegister() {
  if (!form.username || form.username.length < 3) {
    message.warning('用户名至少3个字符')
    return
  }
  if (!form.password || form.password.length < 6) {
    message.warning('密码至少6个字符')
    return
  }
  loading.value = true
  try {
    await register(form)
    message.success('注册成功，请登录')
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
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 注册卡片 */
.register-card {
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
  margin-bottom: 20px;
  text-align: center;
}

.form-logo {
  height: 56px;
  object-fit: contain;
  margin-bottom: 12px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin: 0 0 6px;
}

.form-header p {
  font-size: 14px;
  color: #888888;
  margin: 0;
}

/* 表单 */
.register-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
}

.optional {
  font-weight: 400;
  color: #888888;
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
  border-color: #a1a1aa;
}

.input-wrapper:focus-within {
  border-color: #171717;
  box-shadow: 0 0 0 2px rgba(23, 23, 23, 0.08);
}

.input-icon {
  color: #a1a1aa;
  font-size: 14px;
  margin-right: 8px;
}

.input-wrapper input {
  flex: 1;
  height: 100%;
  border: none;
  outline: none;
  font-size: 14px;
  color: #171717;
  background: transparent;
}

.input-wrapper input::placeholder {
  color: #a1a1aa;
}

/* 注册按钮 */
.btn-register {
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

.btn-register:hover:not(:disabled) {
  background: #27272a;
}

.btn-register:active:not(:disabled) {
  background: #0a0a0a;
}

.btn-register:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

/* 底部链接 */
.form-footer {
  margin-top: 14px;
  text-align: center;
  font-size: 14px;
  color: #888888;
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
  .register-card {
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
