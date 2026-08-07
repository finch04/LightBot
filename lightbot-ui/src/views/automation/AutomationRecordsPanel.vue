<template>
  <div class="records-panel">
    <div class="panel-toolbar">
      <a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索任务名称 / 指令..."
        style="width: 240px"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-select
        v-model:value="agentFilter"
        allow-clear
        show-search
        placeholder="智能体"
        style="width: 180px"
        :options="agentOptions"
        :filter-option="filterAgent"
        :loading="agentsLoading"
      />
      <a-select
        v-model:value="statusFilter"
        allow-clear
        placeholder="执行状态"
        style="width: 140px"
        :options="statusOptions"
      />
      <button type="button" class="lb-btn" :disabled="loading" @click="refresh">
        <ReloadOutlined /> 刷新
      </button>
    </div>

    <div class="panel-table-wrap">
      <a-table
        :columns="columns"
        :data-source="records"
        :pagination="pagination"
        :loading="loading"
        row-key="id"
        size="middle"
        :scroll="{ x: 960 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-badge :status="statusBadge[record.status]" :text="statusLabel[record.status] || record.status" />
          </template>
          <template v-else-if="column.key === 'instruction'">
            <a-tooltip :title="record.instruction" placement="topLeft">
              <span class="cell-ellipsis">{{ record.instruction }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'triggerTime'">
            {{ formatTime(record.triggerTime) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <button type="button" class="btn-link" @click="openDetail(record)">详情</button>
          </template>
        </template>
        <template #emptyText>
          <LbEmptyState
            :icon="ThunderboltOutlined"
            title="暂无执行记录"
            desc="配置定时任务后，每次触发会在此展示结果"
          />
        </template>
      </a-table>
    </div>

    <a-drawer
      v-model:open="detailOpen"
      title="执行详情"
      :width="560"
      destroy-on-close
    >
      <template v-if="detailRecord">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="任务名称">{{ detailRecord.jobName }}</a-descriptions-item>
          <a-descriptions-item label="智能体">{{ detailRecord.agentName }}</a-descriptions-item>
          <a-descriptions-item label="文字指令">{{ detailRecord.instruction }}</a-descriptions-item>
          <a-descriptions-item label="触发时间">{{ formatTime(detailRecord.triggerTime) }}</a-descriptions-item>
          <a-descriptions-item label="触发方式">
            {{ detailRecord.triggerType === 'manual' ? '立即执行' : '定时触发' }}
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-badge :status="statusBadge[detailRecord.status]" :text="statusLabel[detailRecord.status] || detailRecord.status" />
          </a-descriptions-item>
          <a-descriptions-item label="耗时">{{ detailRecord.duration || '—' }}</a-descriptions-item>
          <a-descriptions-item label="会话ID">{{ detailRecord.sessionId || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="detailRecord.error" label="错误信息">{{ detailRecord.error }}</a-descriptions-item>
        </a-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">执行过程</div>
          <div v-if="detailMsg" class="detail-message">
            <ChatMessageBody
              :msg="detailMsg"
              :index="0"
              :get-att-thumb-url="noopThumb"
              @reasoning-toggle="toggleReasoning"
            />
          </div>
          <div v-else class="detail-fallback">
            <div class="detail-fallback__label">结果摘要</div>
            <div class="detail-fallback__body">{{ detailRecord.summary || '—' }}</div>
            <div class="detail-fallback__tip">该记录无完整执行快照（可能为升级前数据），可打开对应会话查看</div>
          </div>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ReloadOutlined, SearchOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import LbEmptyState from '../../components/common/LbEmptyState.vue'
import ChatMessageBody from '../../components/chat/message/ChatMessageBody.vue'
import { parseMessage } from '../../composables/chat/useChatMessageModel.js'
import { getAgents } from '../../api/agent'
import { getAutomationRun, pageAutomationRuns } from '../../api/automation'

const statusLabel = {
  success: '成功',
  failed: '失败',
  running: '执行中',
  skipped: '已跳过',
}
const statusBadge = {
  success: 'success',
  failed: 'error',
  running: 'processing',
  skipped: 'default',
}
const statusOptions = [
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
  { value: 'running', label: '执行中' },
  { value: 'skipped', label: '已跳过' },
]

const columns = [
  { title: '任务名称', dataIndex: 'jobName', key: 'jobName', width: 160, ellipsis: true },
  { title: '智能体', dataIndex: 'agentName', key: 'agentName', width: 120, ellipsis: true },
  { title: '文字指令', dataIndex: 'instruction', key: 'instruction', ellipsis: true },
  { title: '触发时间', dataIndex: 'triggerTime', key: 'triggerTime', width: 170 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '耗时', dataIndex: 'duration', key: 'duration', width: 90 },
  { title: '操作', key: 'actions', width: 80, fixed: 'right' },
]

const records = ref([])
const loading = ref(false)
const keyword = ref('')
const agentFilter = ref(undefined)
const statusFilter = ref(undefined)
const agentsLoading = ref(false)
const agentOptions = ref([])
const detailOpen = ref(false)
const detailRecord = ref(null)
const detailMsg = ref(null)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
})

function formatTime(v) {
  if (!v) return '—'
  return dayjs(v).isValid() ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : String(v)
}

function noopThumb() {
  return ''
}

function buildDetailMsg(detail) {
  if (!detail || typeof detail !== 'object') return null
  try {
    const msg = parseMessage(detail)
    msg._reasoningExpanded = true
    msg._toolsDone = true
    msg._streaming = false
    return msg
  } catch {
    return null
  }
}

function toggleReasoning() {
  if (detailMsg.value) {
    detailMsg.value._reasoningExpanded = !detailMsg.value._reasoningExpanded
  }
}

function filterAgent(input, option) {
  return String(option?.label || '').toLowerCase().includes(String(input || '').toLowerCase())
}

async function loadAgents() {
  agentsLoading.value = true
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 200, includeDefault: false })
    const list = res?.data?.records || []
    agentOptions.value = list.map((a) => ({
      value: String(a.id),
      label: a.name || `智能体 ${a.id}`,
    }))
  } catch {
    agentOptions.value = []
  } finally {
    agentsLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await pageAutomationRuns({
      keyword: keyword.value?.trim() || undefined,
      status: statusFilter.value || undefined,
      agentId: agentFilter.value || undefined,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    const page = res?.data || {}
    records.value = page.records || []
    pagination.total = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

function onTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadRecords()
}

async function refresh() {
  await loadRecords()
  message.success('已刷新')
}

async function openDetail(record) {
  detailOpen.value = true
  detailRecord.value = record
  detailMsg.value = buildDetailMsg(record.detail)
  try {
    const res = await getAutomationRun(record.id)
    if (res?.data) {
      detailRecord.value = res.data
      detailMsg.value = buildDetailMsg(res.data.detail)
    }
  } catch {
    // 保留列表行数据
  }
}

let filterTimer
watch([keyword, statusFilter, agentFilter], () => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(() => {
    pagination.current = 1
    loadRecords()
  }, 250)
})

onMounted(async () => {
  await Promise.all([loadAgents(), loadRecords()])
})
</script>

<style scoped>
.records-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
}
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.panel-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 4px 8px 8px;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.btn-link {
  border: none;
  background: none;
  padding: 0;
  color: var(--color-link);
  cursor: pointer;
  font-size: 13px;
}
.btn-link:hover {
  opacity: 0.8;
}
.detail-section {
  margin-top: 16px;
}
.detail-section__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 10px;
}
.detail-message {
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  background: var(--color-canvas);
}
.detail-fallback__label {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 6px;
}
.detail-fallback__body {
  font-size: 14px;
  color: var(--color-body);
  white-space: pre-wrap;
  line-height: 1.55;
}
.detail-fallback__tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-mute);
}
</style>
