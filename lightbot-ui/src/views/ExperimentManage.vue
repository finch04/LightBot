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
          <a @click.stop="router.push(`/eval/experiments/${record.id}`)">{{ record.name }}</a>
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
        <template v-if="column.key === 'action'">
          <div class="table-actions">
            <button
              v-if="(record.status?.code || record.status) === 'running'"
              class="btn-icon"
              title="停止"
              @click="handleStop(record.id)"
            >
              <PauseCircleOutlined />
            </button>
            <button
              v-if="(record.status?.code || record.status) === 'stopped'"
              class="btn-icon"
              title="重启"
              @click="handleRestart(record.id)"
            >
              <PlayCircleOutlined />
            </button>
            <button
              class="btn-icon danger"
              title="删除"
              @click="handleDelete(record.id)"
            >
              <DeleteOutlined />
            </button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 创建实验弹窗（多步表单） -->
    <a-modal
      v-model:open="createDialogVisible"
      title="创建实验"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <!-- 步骤条 -->
      <a-steps :current="currentStep" size="small" style="margin-bottom: 24px">
        <a-step title="基本信息" />
        <a-step title="评测集" />
        <a-step title="评测对象" />
        <a-step title="评估器" />
      </a-steps>

      <!-- Step 1: 基本信息 -->
      <div v-show="currentStep === 0">
        <a-form :model="createForm" :label-col="{ span: 5 }">
          <a-form-item label="实验名称" required>
            <a-input v-model:value="createForm.name" placeholder="如：客服 Prompt v1 vs v2 对比" />
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="createForm.description" :rows="3" placeholder="实验目的说明" />
          </a-form-item>
        </a-form>
      </div>

      <!-- Step 2: 选择评测集 -->
      <div v-show="currentStep === 1">
        <a-form :model="createForm" :label-col="{ span: 5 }">
          <a-form-item label="评测集" required>
            <a-select
              v-model:value="createForm.datasetId"
              placeholder="选择评测集"
              style="width: 100%"
              @change="onDatasetChange"
            >
              <a-select-option v-for="d in datasetList" :key="d.id" :value="d.id">
                {{ d.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="数据版本" required>
            <a-select
              v-model:value="createForm.datasetVersion"
              placeholder="选择数据版本"
              style="width: 100%"
            >
              <a-select-option v-for="v in datasetVersions" :key="v.version" :value="v.version">
                {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>

      <!-- Step 3: 配置评测对象 -->
      <div v-show="currentStep === 2">
        <a-form :model="createForm" :label-col="{ span: 5 }">
          <a-form-item label="Prompt Key" required>
            <a-select
              v-model:value="createForm.promptKey"
              placeholder="选择 Prompt"
              style="width: 100%"
              @change="onPromptChange"
            >
              <a-select-option v-for="p in promptList" :key="p.promptKey" :value="p.promptKey">
                {{ p.promptKey }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="Prompt 版本" required>
            <a-select
              v-model:value="createForm.promptVersion"
              placeholder="选择版本"
              style="width: 100%"
            >
              <a-select-option v-for="v in promptVersions" :key="v.version" :value="v.version">
                {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="变量映射">
            <a-textarea
              v-model:value="createForm.variableMapping"
              :rows="3"
              placeholder='将评测集字段映射到 Prompt 变量，JSON 格式：{"input":"user_input","expected_output":"reference"}'
            />
          </a-form-item>
        </a-form>
      </div>

      <!-- Step 4: 配置评估器 -->
      <div v-show="currentStep === 3">
        <a-form :model="createForm" :label-col="{ span: 5 }">
          <a-form-item label="评估器" required>
            <a-select
              v-model:value="createForm.evaluatorId"
              placeholder="选择评估器"
              style="width: 100%"
              @change="onEvaluatorChange"
            >
              <a-select-option v-for="e in evaluatorList" :key="e.id" :value="e.id">
                {{ e.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="评估器版本" required>
            <a-select
              v-model:value="createForm.evaluatorVersion"
              placeholder="选择版本"
              style="width: 100%"
            >
              <a-select-option v-for="v in evaluatorVersions" :key="v.version" :value="v.version">
                {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="参数映射">
            <a-textarea
              v-model:value="createForm.evaluatorParamMapping"
              :rows="3"
              placeholder='评估器变量映射，JSON 格式：{"actual_output":"output","expected_output":"reference"}'
            />
          </a-form-item>
        </a-form>
      </div>

      <!-- 底部按钮 -->
      <div class="dialog-footer">
        <div>
          <button v-if="currentStep > 0" class="btn-cancel" @click="currentStep--">上一步</button>
        </div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="createDialogVisible = false">取消</button>
          <button v-if="currentStep < 3" class="btn-primary-sm" @click="nextStep">下一步</button>
          <button v-else class="btn-primary-sm" :disabled="submitting" @click="handleCreate">
            {{ submitting ? '创建中...' : '创建实验' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined, SearchOutlined, ReloadOutlined,
  DeleteOutlined, PauseCircleOutlined, PlayCircleOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getExperiments, createExperiment, stopExperiment, deleteExperiment, restartExperiment,
} from '../api/experiment'
import { getEvalDatasets, getEvalDatasetVersions } from '../api/evalDataset'
import { getPrompts, getPromptVersions } from '../api/prompt'
import { getEvaluators, getEvaluatorVersions } from '../api/evaluator'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const createDialogVisible = ref(false)
const submitting = ref(false)
const currentStep = ref(0)

// 下拉数据
const datasetList = ref([])
const datasetVersions = ref([])
const promptList = ref([])
const promptVersions = ref([])
const evaluatorList = ref([])
const evaluatorVersions = ref([])

const createForm = reactive({
  name: '',
  description: '',
  datasetId: null,
  datasetVersion: '',
  promptKey: '',
  promptVersion: '',
  variableMapping: '',
  evaluatorId: null,
  evaluatorVersion: '',
  evaluatorParamMapping: '',
})

const columns = [
  { title: '实验名称', key: 'name', dataIndex: 'name', width: 240 },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '进度', key: 'progress', dataIndex: 'progress', width: 160 },
  { title: '创建时间', key: 'createTime', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 120 },
]

onMounted(() => {
  loadData()
  loadDropdowns()
})

watch(searchText, () => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    const res = await getExperiments(params)
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

async function loadDropdowns() {
  try {
    const [dsRes, pRes, eRes] = await Promise.all([
      getEvalDatasets({ pageNum: 1, pageSize: 100 }),
      getPrompts({ pageNum: 1, pageSize: 100 }),
      getEvaluators({ pageNum: 1, pageSize: 100 }),
    ])
    datasetList.value = dsRes.data?.records || []
    promptList.value = pRes.data?.records || []
    evaluatorList.value = eRes.data?.records || []
  } catch { /* ignore */ }
}

async function onDatasetChange(id) {
  createForm.datasetVersion = ''
  try {
    const res = await getEvalDatasetVersions(id)
    datasetVersions.value = res.data || []
  } catch { datasetVersions.value = [] }
}

async function onPromptChange(key) {
  createForm.promptVersion = ''
  try {
    const res = await getPromptVersions(key)
    promptVersions.value = res.data || []
  } catch { promptVersions.value = [] }
}

async function onEvaluatorChange(id) {
  createForm.evaluatorVersion = ''
  try {
    const res = await getEvaluatorVersions(id)
    evaluatorVersions.value = res.data || []
  } catch { evaluatorVersions.value = [] }
}

function openCreateDialog() {
  currentStep.value = 0
  Object.assign(createForm, {
    name: '', description: '',
    datasetId: null, datasetVersion: '',
    promptKey: '', promptVersion: '', variableMapping: '',
    evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '',
  })
  datasetVersions.value = []
  promptVersions.value = []
  evaluatorVersions.value = []
  createDialogVisible.value = true
}

function nextStep() {
  if (currentStep.value === 0 && !createForm.name.trim()) return message.warning('请输入实验名称')
  if (currentStep.value === 1 && (!createForm.datasetId || !createForm.datasetVersion)) return message.warning('请选择评测集和版本')
  if (currentStep.value === 2 && (!createForm.promptKey || !createForm.promptVersion)) return message.warning('请选择 Prompt 和版本')
  currentStep.value++
}

async function handleCreate() {
  if (!createForm.evaluatorId || !createForm.evaluatorVersion) return message.warning('请选择评估器和版本')
  submitting.value = true
  try {
    let variableMap = []
    if (createForm.variableMapping.trim()) {
      try {
        const parsed = JSON.parse(createForm.variableMapping)
        variableMap = Object.entries(parsed).map(([promptVariable, datasetColumn]) => ({ promptVariable, datasetColumn }))
      } catch { return message.warning('变量映射 JSON 格式不正确') }
    }
    let evaluatorParamMap = []
    if (createForm.evaluatorParamMapping.trim()) {
      try {
        const parsed = JSON.parse(createForm.evaluatorParamMapping)
        evaluatorParamMap = Object.entries(parsed).map(([evaluatorVariable, source]) => ({ evaluatorVariable, source }))
      } catch { return message.warning('参数映射 JSON 格式不正确') }
    }

    // 查找 datasetVersionId
    const dsVersion = datasetVersions.value.find(v => v.version === createForm.datasetVersion)
    const datasetVersionId = dsVersion?.id || null

    // 查找 evaluatorVersionId
    const evVersion = evaluatorVersions.value.find(v => v.version === createForm.evaluatorVersion)
    const evaluatorVersionId = evVersion?.id || null

    const evaluationObjectConfig = JSON.stringify({
      type: 'prompt',
      config: {
        promptKey: createForm.promptKey,
        version: createForm.promptVersion,
        variableMap,
      },
    })
    const evaluatorConfig = JSON.stringify([{
      evaluatorVersionId: evaluatorVersionId ? String(evaluatorVersionId) : '',
      variableMap: evaluatorParamMap,
    }])

    await createExperiment({
      name: createForm.name,
      description: createForm.description,
      datasetId: createForm.datasetId,
      datasetVersionId,
      datasetVersion: createForm.datasetVersion,
      evaluationObjectConfig,
      evaluatorConfig,
    })
    message.success('实验创建成功')
    createDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
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

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
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
  color: #171717;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
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
  background: #171717;
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
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-primary-sm {
  padding: 6px 16px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-primary-sm:hover { background: #27272a; }
.btn-primary-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: #0070f3; color: #0070f3; }
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
  color: #71717a;
}
.btn-icon:hover { background: #f5f5f5; }
.btn-icon.danger:hover { color: #ee0000; background: #f7d4d6; }
.table-actions {
  display: flex;
  gap: 4px;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
}
.dialog-footer-right { display: flex; gap: 8px; }
</style>
