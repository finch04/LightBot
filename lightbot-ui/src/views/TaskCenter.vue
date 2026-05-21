<template>
  <div class="task-center">
    <div class="page-header">
      <h2>任务中心</h2>
      <button class="btn-outline" @click="loadTasks">
        <ReloadOutlined />
        刷新
      </button>
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
        <template v-else-if="column.key === 'status'">
          <a-badge :status="statusBadge[record.status]" :text="statusMap[record.status] || record.status" />
        </template>
        <template v-else-if="column.key === 'progress'">
          <a-progress
            :percent="record.progress"
            :status="record.status === 'failed' ? 'exception' : record.status === 'success' ? 'success' : 'active'"
            size="small"
          />
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
      :width="480"
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
            <a-progress
              :percent="detailTask.progress"
              :status="detailTask.status === 'failed' ? 'exception' : detailTask.status === 'success' ? 'success' : 'active'"
              size="small"
            />
          </a-descriptions-item>
          <a-descriptions-item label="进度信息">{{ detailTask.message || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailTask.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatTime(detailTask.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatTime(detailTask.completedAt) }}</a-descriptions-item>
          <a-descriptions-item v-if="detailTask.result" label="执行结果">{{ detailTask.result }}</a-descriptions-item>
          <a-descriptions-item v-if="detailTask.error" label="错误信息">
            <span class="error-text">{{ detailTask.error }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTaskList, cancelTask } from '../api/task'

const loading = ref(false)
const tasks = ref([])
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
  { title: '任务名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '进度', dataIndex: 'progress', key: 'progress', width: 160 },
  { title: '操作', key: 'action', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
]

const typeMap = {
  document_upload: '文档上传',
  document_ingest: '文档入库',
  document_ocr: '文档OCR',
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
    const res = await getTaskList({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    tasks.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) {
    // interceptor handled
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadTasks()
}

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

onMounted(() => {
  loadTasks()
  pollTimer = setInterval(loadTasks, 5000)
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
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
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
</style>
