<template>
  <div class="layout">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="sidebar-logo" @click="router.push('/chat')">
        <img src="/lightbot-logo.png" alt="LightBot" class="logo-img" />
      </div>

      <!-- 新建对话按钮 -->
      <button class="btn-new-chat" @click="newChat">
        <PlusOutlined />
        新建对话
      </button>

      <!-- 导航菜单 -->
      <nav class="nav-menu">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="['nav-item', { active: isActive(item.path) }]"
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 对话历史 -->
      <div class="session-section">
        <div class="section-title" @click="sessionsCollapsed = !sessionsCollapsed">
          <span>最近对话</span>
          <DownOutlined v-if="sessionsCollapsed" class="collapse-icon" />
          <UpOutlined v-else class="collapse-icon" />
        </div>
        <div v-show="!sessionsCollapsed" class="session-list" ref="sessionListRef">
          <div
            v-for="s in sessions"
            :key="s.id"
            :class="['session-item', { active: currentSessionId === s.id }]"
            @click="switchSession(s)"
          >
            <span v-if="s.pinned" class="session-pin">📌</span>
            <span class="session-title">{{ s.title || '新对话' }}</span>
            <a-dropdown :trigger="['click']" placement="bottomRight">
              <EllipsisOutlined class="session-more" @click.stop />
              <template #overlay>
                <a-menu @click="({ key }) => handleSessionMenu(key, s)" >
                  <a-menu-item key="pin">{{ s.pinned ? '取消置顶' : '置顶' }}</a-menu-item>
                  <a-menu-item key="rename">重命名</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="delete" class="menu-danger">删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-if="sessions.length === 0" class="session-empty">暂无对话</div>
        </div>
      </div>

      <!-- 重命名弹窗 -->
      <a-modal
        v-model:open="renameVisible"
        title="重命名对话"
        :width="400"
        @ok="confirmRename"
        @cancel="renameVisible = false"
      >
        <a-input
          v-model:value="renameValue"
          placeholder="请输入新名称"
          @press-enter="confirmRename"
          :maxlength="50"
        />
      </a-modal>

      <!-- 用户信息 -->
      <div class="sidebar-footer">
        <a-dropdown v-model:open="userDropdownOpen" :trigger="['click']">
          <div class="user-info">
            <div class="user-avatar">{{ (userStore.user?.nickname || userStore.user?.username || 'U')[0] }}</div>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username || '用户' }}</span>
            <a-badge v-if="taskBadgeCount" :count="taskBadgeCount" :number-style="{ fontSize: '10px', boxShadow: 'none', backgroundColor: '#f5222d' }" />
            <UpOutlined v-if="userDropdownOpen" />
            <DownOutlined v-else />
          </div>
          <template #overlay>
            <a-menu @click="handleCommand">
              <a-menu-item key="profile">个人信息</a-menu-item>
              <a-menu-item key="tasks">
                <div class="menu-item-with-badge">
                  <span>任务中心</span>
                  <a-badge v-if="taskBadgeCount" :count="taskBadgeCount" :number-style="{ fontSize: '10px', boxShadow: 'none', backgroundColor: '#f5222d' }" />
                </div>
              </a-menu-item>
              <a-menu-item key="model-providers">模型管理</a-menu-item>
              <a-menu-item key="logs">日志</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view :key="route.path.startsWith('/chat') ? '/chat' : route.path" />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  PlusOutlined,
  DownOutlined,
  UpOutlined,
  EllipsisOutlined,
  RobotOutlined,
  DatabaseOutlined,
  ToolOutlined,
  DashboardOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user'
import { Modal } from 'ant-design-vue'
import { getSessions, updateSessionTitle, deleteSession, togglePinSession } from '../api/chatSession'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sessions = ref([])
const currentSessionId = ref(null)
const sessionListRef = ref(null)
const renameVisible = ref(false)
const renameValue = ref('')
const renameTarget = ref(null)
const userDropdownOpen = ref(false)
const sessionsCollapsed = ref(false)
const runningTaskCount = ref(0)
let taskSSE = null

const taskBadgeCount = computed(() => {
  if (runningTaskCount.value <= 0) return 0
  return runningTaskCount.value > 10 ? '10+' : runningTaskCount.value
})

const navItems = [
  { path: '/agents', label: 'Agent', icon: markRaw(RobotOutlined) },
  { path: '/knowledge', label: '知识库', icon: markRaw(DatabaseOutlined) },
  { path: '/extensions', label: '扩展', icon: markRaw(ToolOutlined) },
  { path: '/dashboard', label: '监控', icon: markRaw(DashboardOutlined) },
]

function isActive(path) {
  return route.path.startsWith(path)
}

async function loadSessions() {
  try {
    const res = await getSessions({ pageNum: 1, pageSize: 50 })
    sessions.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

// 标题异步生成完成后刷新侧边栏（重试3次，间隔2秒，覆盖AI生成标题的延迟）
function refreshSessionsWithRetry() {
  let count = 0
  const maxRetries = 3
  const interval = 2000
  const timer = setInterval(() => {
    loadSessions()
    count++
    if (count >= maxRetries) clearInterval(timer)
  }, interval)
  // 立即刷新一次
  loadSessions()
}

function newChat() {
  currentSessionId.value = null
  router.push('/chat')
}

function switchSession(session) {
  currentSessionId.value = session.id
  router.push(`/chat/${session.id}`)
}

function handleSessionMenu(key, session) {
  if (key === 'pin') {
    handleTogglePin(session)
  } else if (key === 'rename') {
    startRename(session)
  } else if (key === 'delete') {
    handleDeleteSession(session)
  }
}

async function handleTogglePin(session) {
  try {
    await togglePinSession(session.id)
    session.pinned = !session.pinned
    // 重新排序：置顶的排前面
    sessions.value.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0))
  } catch {
    // interceptor已处理错误提示
  }
}

function handleDeleteSession(session) {
  Modal.confirm({
    title: '确定删除对话？',
    content: '删除后，聊天记录将不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSession(session.id)
        sessions.value = sessions.value.filter(s => s.id !== session.id)
        if (currentSessionId.value === session.id) {
          router.push('/chat')
        }
      } catch {
        // interceptor已处理错误提示
      }
    },
  })
}

function startRename(session) {
  renameTarget.value = session
  renameValue.value = session.title || ''
  renameVisible.value = true
}

function confirmRename() {
  const val = renameValue.value.trim()
  if (!val) return
  if (renameTarget.value) {
    updateSessionTitle(renameTarget.value.id, val)
    renameTarget.value.title = val
  }
  renameVisible.value = false
}

function handleCommand({ key }) {
  if (key === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (key === 'profile') {
    router.push('/profile')
  } else if (key === 'tasks') {
    router.push('/tasks')
  } else if (key === 'model-providers') {
    router.push('/model-providers')
  } else if (key === 'logs') {
    router.push('/logs')
  }
}

function connectTaskSSE() {
  if (taskSSE) return
  const userId = userStore.user?.id
  if (!userId) return

  taskSSE = new EventSource(`/api/tasks/stream?userId=${userId}`)

  taskSSE.addEventListener('count', (e) => {
    runningTaskCount.value = Number(e.data) || 0
  })

  taskSSE.onerror = () => {
    taskSSE?.close()
    taskSSE = null
    // 断线重连
    setTimeout(connectTaskSSE, 3000)
  }
}

function disconnectTaskSSE() {
  taskSSE?.close()
  taskSSE = null
}

onMounted(async () => {
  if (!userStore.user) {
    try {
      await userStore.fetchUser()
    } catch {
      router.push('/login')
      return
    }
  }
  loadSessions()
  // 监听对话标题更新事件（异步生成完成后刷新侧边栏，带重试）
  window.addEventListener('session-title-updated', refreshSessionsWithRetry)
  // SSE 实时监听任务计数（需等 user 加载完成后才有 userId）
  connectTaskSSE()
})

onUnmounted(() => {
  window.removeEventListener('session-title-updated', refreshSessionsWithRetry)
  disconnectTaskSSE()
})

watch(() => route.path, (path) => {
  if (path.startsWith('/chat')) {
    const match = path.match(/\/chat\/(\d+)/)
    const newId = match ? match[1] : null
    if (newId && newId !== currentSessionId.value) {
      // 新会话创建后刷新侧边栏列表
      loadSessions()
    }
    currentSessionId.value = newId
  }
})
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px;
  background: #171717;
  color: #a1a1aa;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 16px 12px;
  cursor: pointer;
}
.logo-img {
  height: 56px;
  object-fit: contain;
}
.btn-new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 12px 12px;
  padding: 10px 0;
  background: transparent;
  border: 1px solid #3f3f46;
  border-radius: 8px;
  color: #fafafa;
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-new-chat:hover {
  border-color: #0070f3;
}

/* 导航 */
.nav-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 8px;
  margin-bottom: 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  color: #a1a1aa;
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: #27272a;
  color: #fafafa;
}
.nav-item.active {
  background: #27272a;
  color: #fafafa;
}

/* 对话历史 */
.session-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}
.session-section::-webkit-scrollbar {
  width: 4px;
}
.session-section::-webkit-scrollbar-thumb {
  background: #3f3f46;
  border-radius: 2px;
}
.section-title {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #171717;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 500;
  color: #71717a;
  padding: 8px 12px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  cursor: pointer;
  user-select: none;
}
.section-title:hover {
  color: #a1a1aa;
}
.collapse-icon {
  font-size: 10px;
  transition: transform 0.2s;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 8px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.session-item:hover {
  background: #27272a;
}
.session-item.active {
  background: #27272a;
}
.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #d4d4d8;
}
.session-pin {
  font-size: 12px;
  margin-right: 4px;
  flex-shrink: 0;
}
.session-more {
  opacity: 0;
  color: #71717a;
  font-size: 14px;
  transition: opacity 0.15s;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.session-more:hover {
  background: #3f3f46;
  color: #d4d4d8;
}
.session-item:hover .session-more {
  opacity: 1;
}
.session-empty {
  padding: 12px;
  font-size: 13px;
  color: #52525b;
  text-align: center;
}

/* 用户信息 */
.sidebar-footer {
  flex-shrink: 0;
  background: #171717;
  padding: 12px;
  border-top: 1px solid #27272a;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.user-info:hover {
  background: #27272a;
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #0070f3;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}
.user-name {
  flex: 1;
  font-size: 13px;
  color: #d4d4d8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.menu-item-with-badge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
:deep(.menu-danger) {
  color: #ee0000 !important;
}
:deep(.menu-danger:hover) {
  background: #f7d4d6 !important;
}

/* ===== 主内容区 ===== */
.main-content {
  flex: 1;
  overflow: hidden;
  background: #ffffff;
}
</style>
