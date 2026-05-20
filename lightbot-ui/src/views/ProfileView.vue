<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">个人信息</h1>
      <p class="page-desc">管理你的账户信息和安全设置</p>
    </div>

    <div class="content-grid">
      <!-- 个人信息 -->
      <div class="panel">
        <div class="panel-header">
          <h3>基本信息</h3>
        </div>
        <a-form :model="profileForm" :label-col="{ span: 6 }">
          <a-form-item label="用户名">
            <a-input :value="profileForm.username" disabled />
          </a-form-item>
          <a-form-item label="昵称">
            <a-input v-model:value="profileForm.nickname" placeholder="设置昵称" />
          </a-form-item>
          <a-form-item label="邮箱">
            <a-input v-model:value="profileForm.email" placeholder="设置邮箱" />
          </a-form-item>
          <a-form-item label="手机号">
            <a-input v-model:value="profileForm.phone" placeholder="设置手机号" />
          </a-form-item>
          <a-form-item label="角色">
            <a-tag :color="roleColor">{{ roleText }}</a-tag>
          </a-form-item>
          <a-form-item label="注册时间">
            <span class="info-text">{{ formatTime(profileForm.createTime) }}</span>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 6 }">
            <button class="btn-primary" :disabled="saving" @click="handleSaveProfile">
              <SaveOutlined /> 保存修改
            </button>
          </a-form-item>
        </a-form>
      </div>

      <!-- 修改密码 -->
      <div class="panel">
        <div class="panel-header">
          <h3>修改密码</h3>
        </div>
        <a-form :model="passwordForm" :label-col="{ span: 6 }">
          <a-form-item label="原密码" required>
            <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入原密码" />
          </a-form-item>
          <a-form-item label="新密码" required>
            <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码（6-64位）" />
          </a-form-item>
          <a-form-item label="确认密码" required>
            <a-input-password v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 6 }">
            <button class="btn-primary" :disabled="changingPwd" @click="handleChangePassword">
              <LockOutlined /> 修改密码
            </button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { SaveOutlined, LockOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getMe, updateProfile, changePassword } from '../api/auth'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const saving = ref(false)
const changingPwd = ref(false)

const profileForm = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  role: '',
  createTime: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const roleText = computed(() => {
  const map = { ADMIN: '管理员', USER: '普通用户' }
  return map[profileForm.role] || profileForm.role || '普通用户'
})

const roleColor = computed(() => {
  return profileForm.role === 'ADMIN' ? 'red' : 'blue'
})

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

async function loadProfile() {
  try {
    const res = await getMe()
    const user = res.data
    Object.assign(profileForm, {
      username: user.username || '',
      nickname: user.nickname || '',
      email: user.email || '',
      phone: user.phone || '',
      role: user.role?.code || user.role || '',
      createTime: user.createTime || '',
    })
  } catch { /* ignore */ }
}

async function handleSaveProfile() {
  saving.value = true
  try {
    const res = await updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone,
    })
    // 更新 store 中的用户信息
    userStore.user.nickname = res.data.nickname
    userStore.user.email = res.data.email
    userStore.user.phone = res.data.phone
    message.success('个人信息已更新')
  } catch { /* interceptor已处理 */ } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword) return message.warning('请输入原密码')
  if (!passwordForm.newPassword) return message.warning('请输入新密码')
  if (passwordForm.newPassword.length < 6) return message.warning('新密码至少6位')
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return message.warning('两次密码不一致')

  changingPwd.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    message.success('密码修改成功，请重新登录')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch { /* interceptor已处理 */ } finally {
    changingPwd.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  margin-bottom: 24px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
}
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
}
.panel-header {
  margin-bottom: 16px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.info-text {
  font-size: 14px;
  color: #71717a;
}
</style>
