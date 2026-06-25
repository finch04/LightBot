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
            <div class="input-wrapper" :class="{ 'input-error': errors.username }">
              <UserOutlined class="input-icon" />
              <input
                v-model="form.username"
                type="text"
                placeholder="3-32个字符"
                autocomplete="username"
                @blur="validateUsername"
                @input="clearError('username')"
              />
            </div>
            <span v-if="errors.username" class="field-error">{{ errors.username }}</span>
          </div>

          <div class="form-item">
            <label class="form-label">密码</label>
            <div class="input-wrapper" :class="{ 'input-error': errors.password }">
              <LockOutlined class="input-icon" />
              <input
                v-model="form.password"
                type="password"
                placeholder="6-64个字符"
                autocomplete="new-password"
                @blur="validatePassword"
                @input="clearError('password')"
              />
            </div>
            <span v-if="errors.password" class="field-error">{{ errors.password }}</span>
          </div>

          <div class="form-item">
            <label class="form-label">确认密码</label>
            <div class="input-wrapper" :class="{ 'input-error': errors.confirmPassword }">
              <LockOutlined class="input-icon" />
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                autocomplete="new-password"
                @blur="validateConfirmPassword"
                @input="clearError('confirmPassword')"
              />
            </div>
            <span v-if="errors.confirmPassword" class="field-error">{{ errors.confirmPassword }}</span>
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
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { register } from '../api/auth'
import { UserOutlined, LockOutlined, LoadingOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loading = ref(false)

const form = reactive({ username: '', password: '', confirmPassword: '' })
const errors = reactive({ username: '', password: '', confirmPassword: '' })

function clearError(field) {
  errors[field] = ''
}

function validateUsername() {
  const v = form.username
  if (!v) { errors.username = '请输入用户名'; return false }
  if (v.length < 3) { errors.username = '用户名至少3个字符'; return false }
  if (v.length > 32) { errors.username = '用户名最多32个字符'; return false }
  errors.username = ''
  return true
}

function validatePassword() {
  const v = form.password
  if (!v) { errors.password = '请输入密码'; return false }
  if (v.length < 6) { errors.password = '密码至少6个字符'; return false }
  if (v.length > 64) { errors.password = '密码最多64个字符'; return false }
  errors.password = ''
  // 如果确认密码已输入，联动校验
  if (form.confirmPassword) validateConfirmPassword()
  return true
}

function validateConfirmPassword() {
  const v = form.confirmPassword
  if (!v) { errors.confirmPassword = '请再次输入密码'; return false }
  if (v !== form.password) { errors.confirmPassword = '两次输入的密码不一致'; return false }
  errors.confirmPassword = ''
  return true
}

async function handleRegister() {
  if (!validateUsername() || !validatePassword() || !validateConfirmPassword()) return
  loading.value = true
  try {
    const { confirmPassword, ...submitData } = form
    await register(submitData)
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
  background: var(--color-canvas-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 注册卡片 */
.register-card {
  display: flex;
  width: 900px;
  height: 560px;
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
  color: var(--color-primary);
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
  color: var(--color-primary);
}

.input-error {
  border-color: #ef4444 !important;
}

.input-error:focus-within {
  border-color: #ef4444 !important;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.15) !important;
}

.field-error {
  font-size: 12px;
  color: #ef4444;
  line-height: 1;
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
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  padding: 0 10px;
  transition: all 0.2s;
  background: #ffffff;
}

.input-wrapper:hover {
  border-color: #a1a1aa;
}

.input-wrapper:focus-within {
  border-color: var(--color-primary);
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
  color: var(--color-primary);
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
  color: #8c8c8c;
}

.page-footer a {
  color: #8c8c8c;
  text-decoration: none;
  transition: color 0.15s;
}

.page-footer a:hover {
  color: var(--color-primary);
}

.page-footer .divider {
  margin: 0 8px;
  color: #d9d9d9;
}

.footer-links {
  margin-bottom: 4px;
}

.footer-copyright {
  color: #bfbfbf;
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
