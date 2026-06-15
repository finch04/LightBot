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
          <a-form-item label="头像">
            <div class="avatar-upload">
              <div class="avatar-preview" :class="{ 'has-avatar': avatarUrl }">
                <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-img" @error="avatarUrl = ''" />
                <span v-else class="avatar-placeholder">{{ initialLetter }}</span>
                <div class="avatar-overlay" @click="triggerAvatarUpload">
                  <UploadOutlined />
                </div>
              </div>
              <input ref="avatarInputRef" type="file" accept=".jpg,.jpeg,.png,.gif,.webp,.bmp" style="display: none" @change="onAvatarFileChange" />
              <span class="avatar-tip">支持 jpg/jpeg/png/gif/webp，建议 200x200</span>
            </div>
          </a-form-item>
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

    <!-- 头像框选择 -->
    <div class="panel frame-panel">
      <div class="panel-header">
        <h3>头像框</h3>
        <p class="panel-subtitle">选择一个动态头像框展示你的个性</p>
      </div>
      <div class="frame-content">
        <div class="frame-preview">
          <AvatarFrame :frame="selectedFrame" :size="80">
            <div class="preview-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" class="preview-avatar-img" @error="avatarUrl = ''" />
              <span v-else>{{ initialLetter }}</span>
            </div>
          </AvatarFrame>
          <span class="frame-preview-label">{{ frameLabelMap[selectedFrame] || '无' }}</span>
        </div>
        <div class="frame-options">
          <div
            v-for="opt in frameOptions"
            :key="opt.value"
            class="frame-option"
            :class="{ active: selectedFrame === opt.value }"
            @click="selectedFrame = opt.value"
          >
            <AvatarFrame :frame="opt.value" :size="48">
              <div class="option-avatar">
                <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" class="option-avatar-img" @error="avatarUrl = ''" />
                <span v-else>{{ initialLetter }}</span>
              </div>
            </AvatarFrame>
            <span class="frame-option-label">{{ opt.label }}</span>
          </div>
        </div>
        <button class="btn-primary" :disabled="savingFrame" @click="handleSaveFrame">
          <SaveOutlined /> 保存头像框
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { SaveOutlined, LockOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getMe, updateProfile, changePassword, updateAvatarFrame, uploadAvatar } from '../api/auth'
import { useUserStore } from '../stores/user'
import AvatarFrame from '../components/AvatarFrame.vue'

const userStore = useUserStore()
const saving = ref(false)
const changingPwd = ref(false)
const savingFrame = ref(false)
const selectedFrame = ref('')
const avatarUrl = ref('')
const avatarUploading = ref(false)
const avatarInputRef = ref(null)

const frameOptions = [
  { value: '', label: '无' },
  { value: 'lightning', label: '巅峰闪电' },
  { value: 'flame', label: '烈焰之环' },
  { value: 'stars', label: '星辰轨迹' },
]

const frameLabelMap = { '': '无', lightning: '巅峰闪电', flame: '烈焰之环', stars: '星辰轨迹' }

const initialLetter = computed(() => {
  return (profileForm.nickname || profileForm.username || 'U')[0]
})

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
    selectedFrame.value = user.avatarFrame || ''
    avatarUrl.value = user.avatar || ''
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

async function handleSaveFrame() {
  savingFrame.value = true
  try {
    const val = selectedFrame.value || 'none'
    await updateAvatarFrame(val)
    userStore.user.avatarFrame = selectedFrame.value || null
    message.success('头像框已更新')
  } catch { /* interceptor已处理 */ } finally {
    savingFrame.value = false
  }
}

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

async function onAvatarFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  avatarUploading.value = true
  try {
    const res = await uploadAvatar(file)
    avatarUrl.value = res.data
    userStore.user.avatar = res.data
    message.success('头像上传成功')
  } catch { /* interceptor已处理 */ } finally {
    avatarUploading.value = false
    if (avatarInputRef.value) avatarInputRef.value.value = ''
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
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}
.avatar-preview.has-avatar {
  background: #f4f4f5;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  font-size: 28px;
  font-weight: 700;
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: #fff;
  font-size: 20px;
}
.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}
.avatar-tip {
  font-size: 12px;
  color: #a1a1aa;
}
.frame-panel {
  grid-column: 1 / -1;
  margin-top: 4px;
}
.panel-subtitle {
  font-size: 13px;
  color: #71717a;
  margin: 4px 0 0;
}
.frame-content {
  display: flex;
  align-items: center;
  gap: 32px;
}
.frame-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.preview-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 600;
  overflow: hidden;
}
.preview-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.frame-preview-label {
  font-size: 13px;
  color: #71717a;
}
.frame-options {
  display: flex;
  gap: 16px;
  flex: 1;
}
.frame-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px;
  border: 2px solid #ebebeb;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.frame-option:hover {
  border-color: #0070f3;
}
.frame-option.active {
  border-color: #0070f3;
  box-shadow: 0 0 0 2px rgba(0, 112, 243, 0.15);
}
.option-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  overflow: hidden;
}
.option-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.frame-option-label {
  font-size: 12px;
  color: #52525b;
}
</style>
