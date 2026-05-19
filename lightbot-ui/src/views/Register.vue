<template>
  <div class="register-page">
    <!-- 左侧品牌区 -->
    <div class="register-brand">
      <div class="brand-content">
        <img src="/lightbot-logo.svg" alt="LightBot" class="brand-logo" />
        <h1 class="brand-title">LightBot</h1>
        <p class="brand-desc">轻量级 AI Agent 开发平台</p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-icon">🤖</span>
            <span>智能对话 Agent</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">📚</span>
            <span>RAG 知识库管理</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">⚡</span>
            <span>Workflow 工作流编排</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧注册表单 -->
    <div class="register-form-area">
      <div class="form-wrapper">
        <div class="form-header">
          <img src="/lightbot-logo.svg" alt="LightBot" class="form-logo" />
          <h2>创建新账号</h2>
          <p>注册你的 LightBot 账号</p>
        </div>

        <form class="register-form" @submit.prevent="handleRegister">
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

        <p class="form-footer">
          已有账号？<router-link to="/login">登录</router-link>
        </p>
      </div>
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
.register-page {
  display: flex;
  height: 100vh;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* 左侧品牌区 */
.register-brand {
  flex: 1;
  background: #171717;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  position: relative;
  overflow: hidden;
}

.register-brand::before {
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
.register-form-area {
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

.register-form {
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

@media (max-width: 768px) {
  .register-brand {
    display: none;
  }
  .register-form-area {
    padding: 40px 24px;
  }
}
</style>
