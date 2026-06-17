<template>
  <div class="task-center">
    <div class="page-header">
      <div class="page-header-left">
        <h2>任务中心</h2>
        <a-radio-group v-model:value="statusFilter" size="small" button-style="solid">
          <a-radio-button value="running">进行中 {{ runningCount }}</a-radio-button>
          <a-radio-button value="pending">等待中 {{ pendingCount }}</a-radio-button>
          <a-radio-button value="">全部</a-radio-button>
        </a-radio-group>
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
        <button class="btn-outline" @click="loadTasks" :disabled="loading">
          <ReloadOutlined :spin="loading" />
          刷新
        </button>
      </div>
    </div>
    <div class="type-filter-bar">
      <span
        class="type-filter-item"
        :class="{ active: typeFilter === '' }"
        @click="typeFilter = ''"
      >全部</span>
      <span
        v-for="(color, typeName) in typeColor"
        :key="typeName"
        class="type-filter-item"
        :class="{ active: typeFilter === typeName }"
        @click="typeFilter = typeFilter === typeName ? '' : typeName"
      >
        <a-tag :color="color" :class="{ 'type-tag-selected': typeFilter === typeName }" size="small">{{ typeName }}</a-tag>
        <span class="type-count">{{ typeCounts[typeName] || 0 }}</span>
      </span>
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
          <a-tooltip>
            <template #title>{{ record.name }}</template>
            <a class="task-name-link" @click="openDetail(record)">{{ record.name }}</a>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'type'">
          <a-tag :color="typeColor[record.type] || 'default'">{{ record.type }}</a-tag>
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
          <a-tooltip v-if="record.status === 'pending' || record.status === 'running'" title="取消任务" :getPopupContainer="t => t.parentElement">
            <a-button
              type="link"
              size="small"
              danger
              @click.stop="handleCancel(record)"
            >
              <StopOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip v-else title="删除" :getPopupContainer="t => t.parentElement">
            <a-button
              type="link"
              size="small"
              danger
              @click.stop="handleDelete(record)"
            >
              <DeleteOutlined />
            </a-button>
          </a-tooltip>
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
        <a-descriptions :column="1" bordered size="small" class="detail-descriptions">
          <a-descriptions-item label="任务名称">
            <span class="detail-content">{{ detailTask.name }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="任务类型">{{ detailTask.type }}</a-descriptions-item>
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
          <a-descriptions-item label="进度信息">
            <span class="detail-content">{{ detailTask.message || '-' }}</span>
          </a-descriptions-item>
          <a-descriptions-item v-if="detailTask.payload" label="请求参数">
            <pre class="json-text">{{ formatJson(detailTask.payload) }}</pre>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailTask.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatTime(detailTask.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatTime(detailTask.completedAt) }}</a-descriptions-item>
          <a-descriptions-item v-if="detailTask.result" label="执行结果">
            <pre class="json-text">{{ formatJson(detailTask.result) }}</pre>
          </a-descriptions-item>
          <a-descriptions-item v-if="detailTask.error" label="错误信息">
            <span class="error-text detail-content">{{ detailTask.error }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined, SearchOutlined, DeleteOutlined, StopOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTaskList, cancelTask, deleteTask, getTaskTypeCounts } from '../api/task'
import { taskCounts } from '../stores/task'

const loading = ref(false)
const tasks = ref([])
const searchText = ref('')
const statusFilter = ref('')
const typeFilter = ref('')
const pendingCount = computed(() => taskCounts.pending)
const runningCount = computed(() => taskCounts.running)
const typeCounts = ref({})

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
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 80 },
]

const typeColor = {
  '文档上传': 'blue',
  '文档入库': 'green',
  '文档OCR': 'orange',
  '实验执行': 'cyan',
  '基准生成': 'purple',
  '基准导入': 'volcano',
  'RAG评估': 'magenta',
  '图谱抽取': 'geekblue',
  '问答对生成': 'gold',
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
    if (statusFilter.value) params.status = statusFilter.value
    if (typeFilter.value) params.type = typeFilter.value
    const res = await getTaskList(params)
    tasks.value = res.data.records || []
    pagination.total = res.data.total || 0
  } catch (e) {
    // interceptor handled
  } finally {
    loading.value = false
  }
}

async function loadTypeCounts() {
  try {
    const res = await getTaskTypeCounts()
    typeCounts.value = res.data || {}
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

watch(statusFilter, () => {
  pagination.current = 1
  typeFilter.value = ''
  loadTasks()
})

watch(typeFilter, () => {
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
    try {
      // 雪花算法 Long ID（16+位数字）超过 JS Number 精度，先转为字符串再解析
      const safe = val.replace(/:\s*(-?\d{16,})/g, ':"$1"')
      return JSON.stringify(JSON.parse(safe), null, 2)
    } catch { return val }
  }
  return JSON.stringify(val, null, 2)
}

onMounted(() => {
  loadTasks()
  loadTypeCounts()
  pollTimer = setInterval(() => {
    loadTasks()
    loadTypeCounts()
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
.page-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.type-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.type-filter-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.15s;
  font-size: 13px;
}
.type-filter-item:hover {
  background: #f4f4f5;
}
.type-filter-item.active {
  background: #f0f0f0;
  outline: 2px solid #1677ff;
  outline-offset: -1px;
}
.type-filter-item :deep(.ant-tag) {
  margin: 0;
  cursor: pointer;
}
.type-tag-selected {
  outline: 2px solid #1677ff;
  outline-offset: -1px;
}
.type-count {
  font-size: 12px;
  color: #8b8b8b;
  font-weight: 500;
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
.detail-descriptions :deep(.ant-descriptions-view) {
  table-layout: fixed;
}
.detail-descriptions :deep(.ant-descriptions-item-label) {
  width: 100px;
  min-width: 100px;
  white-space: nowrap;
}
.detail-descriptions :deep(.ant-descriptions-item-content) {
  word-break: break-all;
  overflow-wrap: break-word;
}
.detail-content {
  display: inline-block;
  max-width: 100%;
  word-break: break-all;
  overflow-wrap: break-word;
}
.json-text {
  margin: 0;
  padding: 8px 10px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-family: 'Geist Mono', 'Menlo', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
.error-text {
  color: #dc2626;
  word-break: break-all;
}
</style>
