<template>
  <div class="tool-call-log-page">
    <div class="page-header">
      <h3><ToolOutlined /> 工具调用日志</h3>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <a-input v-model:value="filter.toolName" placeholder="工具名称" style="width: 180px" allow-clear @pressEnter="loadData(1)" />
      <a-select v-model:value="filter.status" placeholder="状态" style="width: 120px" allowClear>
        <a-select-option value="success">成功</a-select-option>
        <a-select-option value="error">失败</a-select-option>
        <a-select-option value="pending">执行中</a-select-option>
      </a-select>
      <a-range-picker
        v-model:value="filter.timeRange"
        :show-time="{ format: 'HH:mm' }"
        format="YYYY-MM-DD HH:mm"
        style="width: 380px"
      />
      <a-button type="primary" @click="loadData(1)"><SearchOutlined /> 查询</a-button>
    </div>

    <!-- 列表 -->
    <a-table
      :dataSource="records"
      :columns="columns"
      :loading="loading"
      :pagination="pagination"
      rowKey="id"
      size="small"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'toolName'">
          <span class="tool-name-cell"><ToolOutlined /> {{ record.toolName }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'success' ? 'success' : record.status === 'error' ? 'error' : 'processing'">
            {{ record.status === 'success' ? '成功' : record.status === 'error' ? '失败' : '执行中' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'toolInput'">
          <span class="cell-truncate">{{ formatJsonPreview(record.toolInput) }}</span>
        </template>
        <template v-else-if="column.key === 'toolOutput'">
          <span class="cell-truncate">{{ truncate(record.toolOutput, 80) }}</span>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="工具调用详情"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <template v-if="detailRecord">
        <a-descriptions :column="2" size="small" bordered style="margin-bottom: 16px">
          <a-descriptions-item label="工具名称">
            <ToolOutlined /> {{ detailRecord.toolName }}
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="detailRecord.status === 'success' ? 'success' : detailRecord.status === 'error' ? 'error' : 'processing'">
              {{ detailRecord.status === 'success' ? '成功' : detailRecord.status === 'error' ? '失败' : '执行中' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="消息ID" :span="2">{{ detailRecord.messageId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="调用时间" :span="2">{{ formatTime(detailRecord.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="错误信息" :span="2" v-if="detailRecord.errorMessage">
            <span style="color: #ff4d4f">{{ detailRecord.errorMessage }}</span>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider style="margin: 12px 0" />

        <div class="detail-section">
          <div class="detail-label">输入参数</div>
          <pre class="detail-pre">{{ formatJson(detailRecord.toolInput) }}</pre>
        </div>

        <div class="detail-section">
          <div class="detail-label">输出结果</div>
          <pre class="detail-pre">{{ detailRecord.toolOutput || '-' }}</pre>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ToolOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getToolCalls } from '../api/toolCall'

const loading = ref(false)
const records = ref([])
const detailVisible = ref(false)
const detailRecord = ref(null)

const filter = reactive({
  toolName: '',
  status: undefined,
  timeRange: null,
})

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '工具名称', key: 'toolName', width: 150 },
  { title: '状态', key: 'status', width: 80, align: 'center' },
  { title: '输入参数', key: 'toolInput', width: 220, ellipsis: true },
  { title: '输出结果', key: 'toolOutput', width: 300, ellipsis: true },
  { title: '时间', key: 'createdAt', width: 140 },
  { title: '操作', key: 'action', width: 70, align: 'center' },
]

async function loadData(page) {
  loading.value = true
  try {
    pagination.current = page || 1
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    if (filter.toolName?.trim()) params.toolName = filter.toolName.trim()
    if (filter.status) params.status = filter.status
    if (filter.timeRange?.length === 2) {
      params.startTime = filter.timeRange[0].format('YYYY-MM-DD HH:mm:ss')
      params.endTime = filter.timeRange[1].format('YYYY-MM-DD HH:mm:ss')
    }
    const res = await getToolCalls(params)
    records.value = res.data.records || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag) {
  loadData(pag.current)
}

function openDetail(record) {
  detailRecord.value = record
  detailVisible.value = true
}

function formatTime(t) {
  if (!t) return '-'
  if (Array.isArray(t)) {
    return `${t[0]}-${String(t[1]).padStart(2, '0')}-${String(t[2]).padStart(2, '0')} ${String(t[3]).padStart(2, '0')}:${String(t[4]).padStart(2, '0')}:${String(t[5]).padStart(2, '0')}`
  }
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function truncate(str, len) {
  if (!str) return '-'
  return str.length > len ? str.substring(0, len) + '...' : str
}

function formatJsonPreview(jsonStr) {
  if (!jsonStr) return '-'
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    const str = JSON.stringify(obj)
    return str.length > 60 ? str.substring(0, 60) + '...' : str
  } catch {
    return jsonStr.length > 60 ? jsonStr.substring(0, 60) + '...' : jsonStr
  }
}

function formatJson(jsonStr) {
  if (!jsonStr) return '-'
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch {
    return jsonStr
  }
}

onMounted(() => {
  loadData(1)
})
</script>

<style scoped>
.tool-call-log-page {
  padding: 24px;
  height: 100%;
  overflow: auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h3 {
  margin: 0;
  font-size: 18px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.tool-name-cell {
  font-weight: 500;
}
.cell-truncate {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-section {
  margin-bottom: 16px;
}
.detail-label {
  font-size: 13px;
  color: #888;
  margin-bottom: 6px;
  font-weight: 500;
}
.detail-pre {
  background: #f5f5f5;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  margin: 0;
}
</style>
