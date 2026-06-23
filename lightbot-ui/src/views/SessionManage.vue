<template>
  <div class="session-manage">
    <div class="page-header">
      <h2>会话管理</h2>
      <div class="page-header-right">
        <a-input
          v-model:value="searchText"
          placeholder="搜索会话名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="handleRefresh" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <a-popconfirm
          v-if="selectedRowKeys.length > 0"
          :title="`确认删除选中的 ${selectedRowKeys.length} 个会话？`"
          ok-text="确认删除"
          cancel-text="取消"
          ok-type="danger"
          @confirm="handleBatchDelete"
        >
          <button class="btn-danger-outline">
            <DeleteOutlined /> 删除 ({{ selectedRowKeys.length }})
          </button>
        </a-popconfirm>
      </div>
    </div>

    <a-spin :spinning="loading">
    <a-table
      :columns="columns"
      :data-source="sessions"
      :pagination="pagination"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      @change="handleTableChange"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <a-tooltip :title="record.title">
            <span class="session-title-cell">{{ record.title || '新对话' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'messageCount'">
          {{ record.messageCount || 0 }}
        </template>
        <template v-else-if="column.key === 'totalTokens'">
          {{ formatTokens(record.totalTokens) }}
        </template>
        <template v-else-if="column.key === 'lastMessageAt'">
          {{ formatTime(record.lastMessageAt) }}
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openDetail(record)">
              <EyeOutlined /> 详情
            </a-button>
            <a-button type="link" size="small" @click="goToChat(record)">
              <MessageOutlined /> 前往对话
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    </a-spin>

    <!-- 会话详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="detailSession?.title || '会话详情'"
      :width="640"
      :footer="null"
    >
      <template v-if="detailSession">
        <a-descriptions :column="1" bordered size="small" class="detail-desc">
          <a-descriptions-item label="会话ID">
            <span class="detail-mono">{{ detailSession.id }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="会话名称">{{ detailSession.title || '新对话' }}</a-descriptions-item>
          <a-descriptions-item label="消息数">{{ detailSession.messageCount || 0 }}</a-descriptions-item>
          <a-descriptions-item label="Token 消耗">{{ formatTokens(detailSession.totalTokens) }}</a-descriptions-item>
          <a-descriptions-item label="最后消息">{{ formatTime(detailSession.lastMessageAt) }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailSession.createTime) }}</a-descriptions-item>
        </a-descriptions>

        <div class="detail-messages-header">
          <span class="detail-messages-title">消息记录</span>
          <button v-if="selectedMsgKeys.length > 0" class="btn-msg-delete" @click="confirmDeleteMessages">
            <DeleteOutlined /> 删除 ({{ selectedMsgKeys.length }})
          </button>
        </div>
        <a-spin :spinning="messagesLoading">
          <div v-if="detailMessages.length === 0 && !messagesLoading" class="detail-messages-empty">暂无消息</div>
          <div v-else class="detail-messages-list">
            <a-checkbox-group v-model:value="selectedMsgKeys" class="msg-checkbox-group">
              <div v-for="(msg, i) in detailMessages" :key="msg.id || i" class="detail-msg" :class="msg.role">
                <a-checkbox :value="msg.id" class="msg-checkbox" />
                <div class="msg-body">
                  <div class="detail-msg-role">{{ roleLabels[msg.role] || msg.role }}</div>
                  <div class="detail-msg-content">{{ msg.content }}</div>
                </div>
              </div>
            </a-checkbox-group>
            <div v-if="hasMoreMessages" class="detail-load-more">
              <a-button size="small" :loading="loadingOlder" @click="loadOlderMessages">
                加载更早的消息
              </a-button>
            </div>
          </div>
        </a-spin>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ReloadOutlined, SearchOutlined, DeleteOutlined, EyeOutlined, MessageOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getSessions, getSessionMessages, deleteSessionsBatch, deleteMessage } from '../api/chatSession'

const router = useRouter()

const loading = ref(false)
const sessions = ref([])
const searchText = ref('')
const selectedRowKeys = ref([])

const detailVisible = ref(false)
const detailSession = ref(null)
const detailMessages = ref([])
const messagesLoading = ref(false)
const hasMoreMessages = ref(false)
const loadingOlder = ref(false)
let detailPageNum = 1

// 消息批量选择
const selectedMsgKeys = ref([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '会话名称', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '消息数', dataIndex: 'messageCount', key: 'messageCount', width: 80 },
  { title: 'Token', dataIndex: 'totalTokens', key: 'totalTokens', width: 100 },
  { title: '最后消息', dataIndex: 'lastMessageAt', key: 'lastMessageAt', width: 170 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 180 },
]

const roleLabels = { user: '用户', assistant: '助手', system: '系统', tool: '工具' }

let searchDebounceTimer = null

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    if (searchText.value) params.keyword = searchText.value
    const res = await getSessions(params)
    sessions.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch {
    // interceptor handled
  } finally {
    loading.value = false
  }
}

function handleRefresh() {
  searchText.value = ''
  pagination.current = 1
  loadData()
}

function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadData()
}

function onSelectChange(keys) {
  selectedRowKeys.value = keys
}

watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    pagination.current = 1
    loadData()
  }, 300)
})

async function handleBatchDelete() {
  try {
    await deleteSessionsBatch(selectedRowKeys.value)
    message.success(`已删除 ${selectedRowKeys.value.length} 个会话`)
    selectedRowKeys.value = []
    loadData()
  } catch {
    // interceptor handled
  }
}

function openDetail(record) {
  detailSession.value = record
  detailMessages.value = []
  detailPageNum = 1
  hasMoreMessages.value = false
  selectedMsgKeys.value = []
  detailVisible.value = true
  loadMessages()
}

async function loadMessages() {
  messagesLoading.value = true
  try {
    const res = await getSessionMessages(detailSession.value.id, { pageNum: detailPageNum, pageSize: 20 })
    const records = res.data?.records || []
    // API 按创建时间倒序返回，前端正序显示
    detailMessages.value = records.reverse()
    hasMoreMessages.value = records.length === 20
  } catch {
    // interceptor handled
  } finally {
    messagesLoading.value = false
  }
}

async function loadOlderMessages() {
  loadingOlder.value = true
  try {
    detailPageNum++
    const res = await getSessionMessages(detailSession.value.id, { pageNum: detailPageNum, pageSize: 20 })
    const records = res.data?.records || []
    detailMessages.value = [...records.reverse(), ...detailMessages.value]
    hasMoreMessages.value = records.length === 20
  } catch {
    detailPageNum--
  } finally {
    loadingOlder.value = false
  }
}

async function handleBatchDeleteMessages() {
  const count = selectedMsgKeys.value.length
  if (count === 0) return
  try {
    await Promise.all(
      selectedMsgKeys.value.map(id => deleteMessage(detailSession.value.id, id).catch(() => null))
    )
    message.success(`已删除 ${count} 条消息`)
    selectedMsgKeys.value = []
    // 刷新消息列表
    detailPageNum = 1
    await loadMessages()
    // 刷新会话列表（消息数/token 可能变化）
    loadData()
  } catch {
    // interceptor handled
  }
}

function confirmDeleteMessages() {
  const count = selectedMsgKeys.value.length
  Modal.confirm({
    title: '确认删除',
    content: `确认删除选中的 ${count} 条消息？删除后不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: handleBatchDeleteMessages,
  })
}

function goToChat(record) {
  router.push(`/app/chat/${record.id}`)
}

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function formatTokens(tokens) {
  if (!tokens) return '0'
  if (tokens >= 10000) return (tokens / 10000).toFixed(1) + '万'
  return String(tokens)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.session-manage {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.page-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: border-color 0.2s;
}
.btn-outline:hover:not(:disabled) {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-outline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-danger-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: transparent;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #dc2626;
  transition: all 0.2s;
}
.btn-danger-outline:hover {
  border-color: #dc2626;
  background: #fef2f2;
}
.session-title-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-desc {
  margin-bottom: 20px;
}
.detail-desc :deep(.ant-descriptions-view) {
  table-layout: fixed;
}
.detail-desc :deep(.ant-descriptions-item-label) {
  width: 100px;
  min-width: 100px;
  white-space: nowrap;
}
.detail-mono {
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 12px;
}
.detail-messages-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 10px 24px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: -24px;
  background: #fff;
  z-index: 1;
  margin-left: -24px;
  margin-right: -24px;
}
.detail-messages-title {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
}
.btn-msg-delete {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: transparent;
  color: #dc2626;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-msg-delete:hover {
  background: #fef2f2;
  border-color: #dc2626;
}
.detail-messages-empty {
  text-align: center;
  color: #a1a1aa;
  padding: 24px 0;
  font-size: 13px;
}
.detail-messages-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.detail-msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f5f5f5;
}
.detail-msg .msg-checkbox {
  margin-top: 2px;
  flex-shrink: 0;
}
.detail-msg .msg-body {
  flex: 1;
  min-width: 0;
}
.detail-msg.user {
  background: #eff6ff;
}
.detail-msg.assistant {
  background: #f0fdf4;
}
.detail-msg-role {
  font-size: 11px;
  font-weight: 600;
  color: #71717a;
  margin-bottom: 4px;
}
.detail-msg-content {
  font-size: 13px;
  color: #171717;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}
.detail-load-more {
  text-align: center;
  padding: 8px 0;
}
</style>
