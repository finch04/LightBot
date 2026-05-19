<template>
  <div class="layout">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="sidebar-logo" @click="router.push('/chat')">
        <img src="/lightbot-logo.svg" alt="LightBot" class="logo-img" />
        <span class="logo-text">LightBot</span>
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
        <div class="section-title">最近对话</div>
        <div class="session-list" ref="sessionListRef">
          <div
            v-for="s in sessions"
            :key="s.id"
            :class="['session-item', { active: currentSessionId === s.id }]"
            @click="switchSession(s)"
          >
            <span class="session-title" @dblclick="startRename(s)">{{ s.title || '新对话' }}</span>
            <CloseOutlined class="session-delete" @click.stop="archiveSession(s.id)" />
          </div>
          <div v-if="sessions.length === 0" class="session-empty">暂无对话</div>
        </div>
      </div>

      <!-- 用户信息 -->
      <div class="sidebar-footer">
        <a-dropdown :trigger="['click']">
          <div class="user-info">
            <div class="user-avatar">{{ (userStore.user?.nickname || userStore.user?.username || 'U')[0] }}</div>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username || '用户' }}</span>
            <DownOutlined />
          </div>
          <template #overlay>
            <a-menu @click="handleCommand">
              <a-menu-item key="model-providers">模型管理</a-menu-item>
              <a-menu-item key="logout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view :key="route.fullPath" />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  PlusOutlined,
  CloseOutlined,
  DownOutlined,
  RobotOutlined,
  DatabaseOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  DashboardOutlined,
  FileTextOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user'
import { getSessions, createSession, archiveSession as archiveSessionApi, updateSessionTitle } from '../api/chatSession'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sessions = ref([])
const currentSessionId = ref(null)
const sessionListRef = ref(null)

const navItems = [
  { path: '/agents', label: 'Agent', icon: markRaw(RobotOutlined) },
  { path: '/knowledge', label: '知识库', icon: markRaw(DatabaseOutlined) },
  { path: '/mcp', label: 'MCP', icon: markRaw(ApiOutlined) },
  { path: '/skills', label: 'Skill', icon: markRaw(ThunderboltOutlined) },
  { path: '/tools', label: '工具', icon: markRaw(ToolOutlined) },
  { path: '/dashboard', label: 'Dashboard', icon: markRaw(DashboardOutlined) },
  { path: '/logs', label: '日志监控', icon: markRaw(FileTextOutlined) },
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

function newChat() {
  currentSessionId.value = null
  router.push('/chat')
}

function switchSession(session) {
  currentSessionId.value = session.id
  router.push(`/chat/${session.id}`)
}

async function archiveSession(id) {
  try {
    await archiveSessionApi(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      router.push('/chat')
    }
  } catch (e) {
    // ignore
  }
}

function startRename(session) {
  const newTitle = prompt('请输入新名称', session.title)
  if (newTitle && newTitle.trim()) {
    updateSessionTitle(session.id, newTitle.trim())
    session.title = newTitle.trim()
  }
}

function handleCommand({ key }) {
  if (key === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (key === 'model-providers') {
    router.push('/model-providers')
  }
}

onMounted(() => {
  if (!userStore.user) {
    userStore.fetchUser().catch(() => router.push('/login'))
  }
  loadSessions()
})

watch(() => route.path, (path) => {
  if (path.startsWith('/chat')) {
    const match = path.match(/\/chat\/(\d+)/)
    const newId = match ? Number(match[1]) : null
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
  gap: 10px;
  padding: 16px 16px 12px;
  cursor: pointer;
}
.logo-img {
  width: 28px;
  height: 28px;
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #fafafa;
  letter-spacing: -0.3px;
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
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 8px;
}
.section-title {
  font-size: 12px;
  font-weight: 500;
  color: #71717a;
  padding: 8px 12px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.session-list::-webkit-scrollbar {
  width: 4px;
}
.session-list::-webkit-scrollbar-thumb {
  background: #3f3f46;
  border-radius: 2px;
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
.session-delete {
  opacity: 0;
  color: #71717a;
  font-size: 14px;
  transition: opacity 0.15s;
  cursor: pointer;
}
.session-item:hover .session-delete {
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

/* ===== 主内容区 ===== */
.main-content {
  flex: 1;
  overflow: hidden;
  background: #ffffff;
}
</style>
