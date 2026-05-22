<template>
  <div class="task-center">
    <div class="page-header">
      <div class="page-header-left">
        <h2>任务中心</h2>
        <span
          :class="['pending-badge', { active: onlyPending }]"
          @click="onlyPending = !onlyPending"
        >
          待处理 {{ pendingCount }}
        </span>
      </div>
      <div class="page-header-right">
        <a-input
          v-model:value="searchText"
          placeholder="搜索任务名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadTasks">
          <ReloadOutlined />
          刷新
        </button>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data-source="tasks"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a class="task-name-link" @click="openDetail(record)">{{ record.name }}</a>
        </template>
        <template v-else-if="column.key === 'type'">
          <a-tag :color="typeColor[record.type]">{{ typeMap[record.type] || record.type }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-badge :status="statusBadge[record.status]" :text="statusMap[record.status] || record.status" />
        </template>
        <template v-else-if="column.key === 'progress'">
          <div class="progress-cell">
            <a-progress
              :percent="record.progress"
              :status="record.status === 'failed' ? 'exception' : record.status === 'success' ? 'success' : 'active'"
              :show-info="false"
              size="small"
            />
            <span class="progress-text">{{ record.progress || 0 }}%</span>
          </div>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button
            v-if="record.status === 'pending' || record.status === 'running'"
            type="link"
            size="small"
            danger
            @click.stop="handleCancel(record)"
          >
            取消
          </a-button>
          <a-button
            v-else
            type="link"
            size="small"
            danger
            @click.stop="handleDelete(record)"
          >
            删除
          </a-button>
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
      </template>
    </a-table>

    <!-- 任务详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      title="任务详情"
      :width="560"
      :footer="null"
    >
      <template v-if="detailTask">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="任务名称">{{ detailTask.name }}</a-descriptions-item>
          <a-descriptions-item label="任务类型">{{ typeMap[detailTask.type] || detailTask.type }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-badge :status="statusBadge[detailTask.status]" :text="statusMap[detailTask.status] || detailTask.status" />
          </a-descriptions-item>
          <a-descriptions-item label="进度">
            <div class="progress-cell">
              <a-progress
                :percent="detailTask.progress"
                :status="detailTask.status === 'failed' ? 'exception' : detailTask.status === 'success' ? 'success' : 'active'"
                :show-info="false"
                size="small"
              />
              <span class="progress-text">{{ detailTask.progress || 0 }}%</span>
            </div>
          </a-descriptions-item>
          <a-descriptions-item label="进度信息">{{ detailTask.message || '-' }}</a-descriptions-item>
          <a-descriptions-item v-if="detailTask.payload" label="请求参数">
            <span class="json-text">{{ formatJson(detailTask.payload) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailTask.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatTime(detailTask.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatTime(detailTask.completedAt) }}</a-descriptions-item>
          <a-descriptions-item v-if="detailTask.result" label="执行结果">
            <span class="json-text">{{ formatJson(detailTask.result) }}</span>
          </a-descriptions-item>
          <a-descriptions-item v-if="detailTask.error" label="错误信息">
            <span class="error-text">{{ detailTask.error }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTaskList, cancelTask, deleteTask } from '../api/task'

const loading = ref(false)
const tasks = ref([])
const searchText = ref('')
const onlyPending = ref(false)
const pendingCount = ref(0)

const detailVisible = ref(false)
const detailTask = ref(null)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '任务名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 240 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '进度', dataIndex: 'progress', key: 'progress', width: 160 },
  { title: '操作', key: 'action', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
]

const typeMap = {
  document_upload: '文档上传',
  document_ingest: '文档入库',
  document_ocr: '文档OCR',
}

const typeColor = {
  document_upload: 'blue',
  document_ingest: 'green',
  document_ocr: 'orange',
}

const statusMap = {
  pending: '等待中',
  running: '执行中',
  success: '已完成',
  failed: '失败',
  cancelled: '已取消',
}

const statusBadge = {
  pending: 'processing',
  running: 'processing',
  success: 'success',
  failed: 'error',
  cancelled: 'default',
}

let pollTimer = null

async function loadTasks() {
  loading.value = true
  try {
    const params = { pageNum: pagination.current, pageSize: pagination.pageSize }
    if (searchText.value) params.name = searchText.value
    if (onlyPending.value) params.status = 'pending'
    const res = await getTaskList(params)
    tasks.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) {
    // interceptor handled
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  try {
    const res = await getTaskList({ pageNum: 1, pageSize: 1, status: 'pending' })
    pendingCount.value = res.data.total || 0
  } catch { /* ignore */ }
}

function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadTasks()
}

watch(searchText, () => {
  pagination.current = 1
  loadTasks()
})

watch(onlyPending, () => {
  pagination.current = 1
  loadTasks()
})

function openDetail(record) {
  detailTask.value = record
  detailVisible.value = true
}

function handleCancel(record) {
  Modal.confirm({
    title: '确认取消',
    content: `确定取消任务「${record.name}」？`,
    okText: '确认',
    cancelText: '取消',
    async onOk() {
      try {
        await cancelTask(record.id)
        message.success('取消请求已提交')
        loadTasks()
      } catch (e) {
        // interceptor handled
      }
    },
  })
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除任务「${record.name}」？删除后不可恢复。`,
    okText: '确认',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteTask(record.id)
        message.success('删除成功')
        loadTasks()
      } catch (e) {
        // interceptor handled
      }
    },
  })
}

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatJson(val) {
  if (!val) return '-'
  if (typeof val === 'string') {
    try { return JSON.stringify(JSON.parse(val), null, 2) } catch { return val }
  }
  return JSON.stringify(val, null, 2)
}

onMounted(() => {
  loadTasks()
  loadPendingCount()
  pollTimer = setInterval(() => {
    loadTasks()
    loadPendingCount()
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.task-center {
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
.page-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.pending-label {
  font-size: 13px;
  color: #8c8c8c;
}
.pending-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 12px;
  border-radius: 10px;
  background: #f5f5f5;
  color: #8c8c8c;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}
.pending-badge:hover {
  background: #e6e6e6;
}
.pending-badge.active {
  background: #e6f4ff;
  color: #1677ff;
  border-color: #91caff;
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
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.task-name-link {
  color: #1677ff;
  cursor: pointer;
}
.task-name-link:hover {
  text-decoration: underline;
}
.error-text {
  color: #dc2626;
}
.json-text {
  font-family: 'Geist Mono', 'Menlo', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-cell :deep(.ant-progress) {
  flex: 1;
  min-width: 0;
}
.progress-text {
  font-size: 12px;
  color: #52525b;
  min-width: 36px;
  text-align: right;
  flex-shrink: 0;
}
.task-center :deep(.ant-descriptions-view) {
  table-layout: fixed;
}
.task-center :deep(.ant-descriptions-item-content) {
  word-break: break-word;
  white-space: normal;
}
.task-center :deep(.ant-descriptions-item-label) {
  width: 90px;
  min-width: 90px;
  white-space: nowrap;
}
</style>
