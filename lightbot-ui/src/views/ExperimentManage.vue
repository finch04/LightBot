<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">实验管理</h1>
        <p class="page-desc">创建和管理 Prompt 评测实验</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索实验名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <button class="btn-outline" @click="router.push('/app/eval/datasets')">
          <ArrowLeftOutlined /> 返回评测
        </button>
        <button class="btn-primary" @click="openCreateDialog()">
          <PlusOutlined /> 创建实验
        </button>
      </div>
    </div>

    <a-table
      :dataSource="list"
      :columns="columns"
      :pagination="{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }"
      rowKey="id"
      size="middle"
      :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a @click.stop="router.push(`/app/eval/experiments/${record.id}`)">{{ record.name }}</a>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status?.code || record.status)">
            {{ statusLabel(record.status?.code || record.status) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'progress'">
          <a-progress
            :percent="record.progress || 0"
            :size="'small'"
            :status="progressStatus(record.status?.code || record.status)"
          />
        </template>
        <template v-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-if="column.key === 'evaluatorName'">
          <span v-if="record.evaluatorNameList?.length">{{ record.evaluatorNameList.join('、') }}</span>
          <span v-else>{{ record.evaluatorName || '-' }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <div class="table-actions">
            <a-tooltip v-if="(record.status?.code || record.status) === 'running'" title="停止">
              <button
                class="btn-icon"
                @click="handleStop(record.id)"
              >
                <PauseCircleOutlined />
              </button>
            </a-tooltip>
            <a-tooltip v-if="(record.status?.code || record.status) === 'stopped'" title="重启">
              <button
                class="btn-icon"
                @click="handleRestart(record.id)"
              >
                <PlayCircleOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="删除">
              <button
                class="btn-icon danger"
                @click="handleDelete(record.id)"
              >
                <DeleteOutlined />
            </button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 创建实验弹窗 -->
    <a-modal
      v-model:open="createDialogVisible"
      title="创建实验"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <ExperimentCreateForm
        ref="createFormRef"
        @success="onCreateSuccess"
      >
        <template #cancel>
          <button class="btn-cancel" @click="createDialogVisible = false">取消</button>
        </template>
      </ExperimentCreateForm>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  DeleteOutlined, PauseCircleOutlined, PlayCircleOutlined, ArrowLeftOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getExperiments, stopExperiment, deleteExperiment, restartExperiment,
} from '../api/experiment'
import { useDebouncedWatch } from '../composables/useDebounce'
import { formatTime } from '../utils/format'
import ExperimentCreateForm from '../components/eval/ExperimentCreateForm.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const createDialogVisible = ref(false)
const createFormRef = ref(null)
let loadAbortController = null

onUnmounted(() => {
  loadAbortController?.abort()
})

const columns = [
  { title: '实验名称', key: 'name', dataIndex: 'name', width: 200, ellipsis: true },
  { title: '评测集', key: 'datasetName', dataIndex: 'datasetName', width: 140, ellipsis: true },
  { title: 'Prompt', key: 'promptKey', dataIndex: 'promptKey', width: 140, ellipsis: true },
  { title: '评估器', key: 'evaluatorName', dataIndex: 'evaluatorName', width: 140, ellipsis: true },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '进度', key: 'progress', dataIndex: 'progress', width: 160 },
  { title: '创建时间', key: 'createTime', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 120 },
]

onMounted(() => {
  loadData()
})

useDebouncedWatch(searchText, () => loadData())

async function loadData() {
  loadAbortController?.abort()
  const controller = new AbortController()
  loadAbortController = controller
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    const res = await getExperiments(params)
    if (!controller.signal.aborted) {
      list.value = res.data?.records || []
    }
  } finally {
    if (!controller.signal.aborted) {
      loading.value = false
    }
  }
}

function openCreateDialog() {
  createFormRef.value?.resetForm()
  createDialogVisible.value = true
}

function onCreateSuccess() {
  createDialogVisible.value = false
  loadData()
}

function handleStop(id) {
  Modal.confirm({
    title: '确认停止',
    content: '停止后实验将不再继续运行，是否继续？',
    okText: '确认停止',
    cancelText: '取消',
    async onOk() {
      await stopExperiment(id)
      message.success('实验已停止')
      loadData()
    },
  })
}

function handleRestart(id) {
  Modal.confirm({
    title: '确认重启',
    content: '将重新运行该实验，是否继续？',
    okText: '确认重启',
    cancelText: '取消',
    async onOk() {
      await restartExperiment(id)
      message.success('实验已重启')
      loadData()
    },
  })
}

function handleDelete(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteExperiment(id)
      message.success('删除成功')
      loadData()
    },
  })
}

function statusColor(s) {
  const map = { created: 'default', running: 'blue', completed: 'green', failed: 'red', stopped: 'default' }
  return map[s] || 'default'
}

function statusLabel(s) {
  const map = { created: '已创建', running: '运行中', completed: '已完成', failed: '失败', stopped: '已停止' }
  return map[s] || s || '未知'
}

function progressStatus(s) {
  if (s === 'failed') return 'exception'
  if (s === 'completed') return 'success'
  return 'active'
}

</script>

<style scoped>
.page {
  padding: var(--space-xl);
  padding-right: calc(var(--space-xl) + var(--scroll-content-gap));
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:hover { background: #27272a; }
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
}
.btn-icon:hover { background: var(--color-canvas-soft-2); }
.btn-icon.danger:hover { color: var(--color-error); background: var(--color-error-soft); }
.table-actions {
  display: flex;
  gap: 4px;
}
</style>
