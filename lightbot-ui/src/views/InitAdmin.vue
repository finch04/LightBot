<template>
  <div class="init-wrapper">
    <div class="init-card">
      <!-- 左侧背景图 -->
      <div class="card-left">
        <img src="/login-bg.png" alt="Background" class="bg-image" />
      </div>

      <!-- 右侧表单 -->
      <div class="card-right">
        <div class="form-header">
          <img src="/lightbot-logo-single.png" alt="LightBot" class="form-logo" />
          <h2>系统初始化</h2>
          <p>创建管理员账号以开始使用 LightBot</p>
        </div>

        <form class="init-form" @submit.prevent="handleInit">
          <div class="form-item">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <input
                v-model="form.username"
                type="text"
                placeholder="请输入管理员用户名"
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
                placeholder="请输入密码（至少6位）"
                autocomplete="new-password"
              />
            </div>
          </div>

          <div class="form-item">
            <label class="form-label">确认密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                autocomplete="new-password"
              />
            </div>
          </div>

          <div class="form-item">
            <label class="form-label">昵称 <span class="optional">（可选）</span></label>
            <div class="input-wrapper">
              <SmileOutlined class="input-icon" />
              <input
                v-model="form.nickname"
                type="text"
                placeholder="请输入昵称"
              />
            </div>
          </div>

          <button class="btn-init" type="submit" :disabled="loading">
            <LoadingOutlined v-if="loading" />
            <span>{{ loading ? '创建中...' : '创建管理员' }}</span>
          </button>
        </form>
      </div>
    </div>

    <!-- 底部链接 -->
    <div class="page-footer">
      <div class="footer-copyright">© 2026 LightBot. All Rights Reserved.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { UserOutlined, LockOutlined, SmileOutlined, LoadingOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

async function handleInit() {
  if (!form.username || form.username.length < 3) {
    message.warning('用户名至少3个字符')
    return
  }
  if (!form.password || form.password.length < 6) {
    message.warning('密码至少6个字符')
    return
  }
  if (form.password !== form.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await userStore.initAdmin({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
    })
    message.success('管理员创建成功')
    router.push('/app')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.init-wrapper {
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  position: relative;
}

.init-card {
  display: flex;
  width: 900px;
  height: 600px;
  background: var(--color-canvas);
  border-radius: 12px;
  box-shadow: 0px 1px 1px rgba(0,0,0,0.02), 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 16px -4px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
  overflow: hidden;
}

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

.card-right {
  flex: 1;
  padding: 40px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header {
  margin-bottom: 24px;
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

.init-form {
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
  color: #141414;
}

.form-label .optional {
  font-weight: 400;
  color: var(--color-mute);
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

.btn-init {
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

.btn-init:hover:not(:disabled) {
  background: #27272a;
}

.btn-init:active:not(:disabled) {
  background: #0a0a0a;
}

.btn-init:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

.page-footer {
  position: fixed;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 13px;
  color: var(--color-mute);
}

.footer-copyright {
  color: var(--color-mute);
}

@media (max-width: 960px) {
  .init-card {
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
