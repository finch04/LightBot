<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.back()">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">{{ experiment?.name || '实验详情' }}</h1>
        <p class="page-desc">{{ experiment?.description || '' }}</p>
      </div>
      <div class="page-header-actions">
        <a-tag v-if="experimentStatus" :color="statusColor" style="font-size: 14px; padding: 4px 12px;">
          {{ statusLabel }}
        </a-tag>
        <button
          v-if="experimentStatus === 'running'"
          class="btn-outline"
          @click="handleStop"
        >
          <PauseCircleOutlined /> 停止
        </button>
        <button
          v-if="experimentStatus === 'stopped' || experimentStatus === 'failed' || experimentStatus === 'completed'"
          class="btn-outline"
          @click="handleRestart"
        >
          <ReloadOutlined /> 重新评测
        </button>
      </div>
    </div>

    <!-- 实验信息卡片 -->
    <div class="info-cards">
      <div class="info-card">
        <div class="info-label">评测集</div>
        <div class="info-value">
          <a-tag v-if="experiment?.datasetVersion" color="blue" size="small">{{ experiment.datasetVersion }}</a-tag>
          <a v-if="experiment?.datasetId" @click="router.push(`/app/eval/datasets/${experiment.datasetId}`)">{{ experiment?.datasetName || '-' }}</a>
          <span v-else>{{ experiment?.datasetName || '-' }}</span>
        </div>
      </div>
      <div class="info-card">
        <div class="info-label">Prompt</div>
        <div class="info-value">
          <a-tag v-if="experiment?.promptVersion" color="blue" size="small">{{ experiment.promptVersion }}</a-tag>
          <a v-if="experiment?.promptKey" @click="router.push(`/app/prompts/${experiment.promptKey}`)">{{ experiment.promptKey }}</a>
          <span v-else>-</span>
        </div>
      </div>
      <div class="info-card">
        <div class="info-label">评估器</div>
        <div class="info-value" v-if="experiment?.evaluatorNameList?.length">
          <div v-for="(name, i) in experiment.evaluatorNameList" :key="i" class="evaluator-info-item">
            <a-tag v-if="experiment.evaluatorVersionList?.[i]" color="blue" size="small">{{ experiment.evaluatorVersionList[i] }}</a-tag>
            <a @click="router.push(`/app/eval/evaluators/${experiment.evaluatorIdList?.[i]}`)">{{ name }}</a>
          </div>
        </div>
        <div class="info-value" v-else>-</div>
      </div>
      <div class="info-card">
        <div class="info-label">创建时间</div>
        <div class="info-value">{{ formatTime(experiment?.createTime) }}</div>
      </div>
    </div>

    <!-- 失败原因 -->
    <a-alert
      v-if="experimentStatus === 'failed' && errorMessage"
      type="error"
      show-icon
      :message="'实验执行失败'"
      :description="errorMessage"
      style="margin-bottom: 16px; border-radius: 8px;"
    />

    <!-- Tab 切换 -->
    <div class="tabs-container">
    <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
      <!-- 概览 Tab -->
      <a-tab-pane key="overview" tab="概览">
        <a-spin :spinning="overviewLoading" style="min-height: 200px; display: block;">
        <div class="evaluator-cards" style="min-height: 200px;">
          <div
            v-for="ev in evaluatorResults"
            :key="ev.evaluatorName"
            class="evaluator-card"
          >
            <div class="evaluator-card-header">
              <span class="evaluator-name">{{ ev.evaluatorName }}</span>
              <span class="evaluator-version" v-if="ev.evaluatorVersion">{{ ev.evaluatorVersion }}</span>
            </div>
            <div class="evaluator-score" :class="scoreClass(ev.avgScore)">
              <span class="score-value">{{ ev.avgScore?.toFixed(2) ?? '-' }}</span>
              <span class="score-label">平均分</span>
            </div>
            <a-progress
              :percent="Math.round((ev.avgScore || 0) * 100)"
              :stroke-color="progressColor(ev.avgScore)"
              :show-info="false"
            />
            <div class="evaluator-meta">
              <span>已评测 {{ ev.evaluatedCount ?? 0 }} / {{ ev.totalCount ?? 0 }}</span>
            </div>
          </div>
        </div>
        <div v-if="evaluatorResults.length === 0 && !overviewLoading" class="empty-state">
          暂无评测结果
        </div>
        </a-spin>
      </a-tab-pane>

      <!-- 评测结果 Tab -->
      <a-tab-pane key="results" tab="评测结果">
        <a-tabs v-if="evaluatorResults.length" v-model:activeKey="resultEvaluatorTab" type="card" size="small">
          <a-tab-pane
            v-for="ev in evaluatorResults"
            :key="ev.evaluatorName"
            :tab="ev.evaluatorName"
          >
            <a-table
              :dataSource="getResultsForEvaluator(ev.evaluatorName)"
              :columns="resultColumns"
              :pagination="{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }"
              :loading="resultsLoading"
              rowKey="id"
              size="middle"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'input'">
                  <a-tooltip :title="record.input">
                    <span class="cell-preview">{{ truncate(record.input, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'actualOutput'">
                  <a-tooltip :title="record.actualOutput">
                    <span class="cell-preview">{{ truncate(record.actualOutput, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'referenceOutput'">
                  <a-tooltip :title="record.referenceOutput">
                    <span class="cell-preview">{{ truncate(record.referenceOutput, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'score'">
                  <span class="score-tag" :class="scoreClass(record.score)">
                    {{ record.score?.toFixed(2) ?? '-' }}
                  </span>
                </template>
                <template v-if="column.key === 'reason'">
                  <a-tooltip :title="record.reason">
                    <span class="cell-preview">{{ truncate(record.reason, 60) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'detailAction'">
                  <a-button type="link" size="small" @click="openDetailModal(record)">详情</a-button>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
        <div v-else class="empty-state">暂无评测结果</div>
      </a-tab-pane>
    </a-tabs>
    <!-- 运行中遮罩 -->
    <div v-if="experimentStatus === 'running'" class="running-mask">
      <div class="running-mask-content">
        <a-spin size="large" />
        <p class="running-mask-text">实验正在评测中，当前进度 {{ experiment?.progress || 0 }}%</p>
        <button class="btn-outline-sm" @click="handleRefreshResults">
          <ReloadOutlined /> 刷新结果
        </button>
      </div>
    </div>
    </div>

    <!-- 重新评测弹窗 -->
    <a-modal
      v-model:open="restartDialogVisible"
      title="重新评测"
      :width="restartStep === 0 ? 480 : 720"
      :footer="null"
      :maskClosable="false"
    >
      <div v-if="restartStep === 0">
        <p style="color: var(--color-mute); margin-bottom: 16px;">选择重新评测方式：</p>
        <a-radio-group v-model:value="restartMode" style="display: flex; flex-direction: column; gap: 12px;">
          <a-radio value="direct">
            <div>
              <div style="font-weight: 500;">直接重新评测</div>
              <div style="font-size: 12px; color: var(--color-mute);">使用当前配置立即重新运行</div>
            </div>
          </a-radio>
          <a-radio value="modify">
            <div>
              <div style="font-weight: 500;">修改配置后重新评测</div>
              <div style="font-size: 12px; color: var(--color-mute);">修改实验名称、评测集版本、Prompt 版本、评估器版本等</div>
            </div>
          </a-radio>
        </a-radio-group>
        <div class="dialog-footer">
          <div></div>
          <div class="dialog-footer-right">
            <button class="btn-cancel" @click="restartDialogVisible = false">取消</button>
            <button class="btn-primary-sm" :disabled="restartSubmitting" @click="handleRestartConfirm">
              {{ restartMode === 'direct' ? (restartSubmitting ? '重启中...' : '确认重启') : '下一步' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="restartStep === 1">
        <a-spin :spinning="restartFormLoading" tip="加载配置中...">
        <div class="restart-form-scroll">
        <a-form :model="editForm" :label-col="{ span: 5 }" :style="{ opacity: restartFormLoading ? 0.4 : 1, transition: 'opacity 0.2s' }">
          <a-form-item label="实验名称" required>
            <a-input v-model:value="editForm.name" :maxlength="30" show-count placeholder="实验名称 (不超过30字)" />
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="editForm.description" :rows="2" :maxlength="50" show-count placeholder="实验描述 (不超过50字)" />
          </a-form-item>
          <a-form-item label="评测集" required>
            <a-select v-model:value="editForm.datasetId" placeholder="选择评测集" @change="onEditDatasetChange">
              <a-select-option v-for="d in datasetList" :key="d.id" :value="d.id">{{ d.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="数据版本" required>
            <a-select v-model:value="editForm.datasetVersion" placeholder="选择数据版本">
              <a-select-option v-for="v in datasetVersions" :key="v.version" :value="v.version">{{ v.version }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="Prompt" required>
            <a-select v-model:value="editForm.promptKey" placeholder="选择 Prompt" @change="onEditPromptChange">
              <a-select-option v-for="p in promptList" :key="p.promptKey" :value="p.promptKey">{{ p.promptKey }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="Prompt 版本" required>
            <a-select v-model:value="editForm.promptVersion" placeholder="选择版本">
              <a-select-option v-for="v in promptVersions" :key="v.version" :value="v.version">{{ v.version }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="变量映射">
            <a-textarea v-model:value="editForm.variableMapping" :rows="2" placeholder='JSON: {"input":"user_input"}' />
          </a-form-item>
          <div v-for="(ev, idx) in editForm.evaluators" :key="idx" class="evaluator-config-block">
            <div class="evaluator-config-header">
              <span class="evaluator-config-title">评估器 {{ idx + 1 }}</span>
              <a-tooltip v-if="editForm.evaluators.length > 1" title="移除">
                <button class="btn-icon danger" @click="removeEditEvaluator(idx)"><DeleteOutlined /></button>
              </a-tooltip>
            </div>
            <a-form-item label="评估器" required>
              <a-select v-model:value="ev.evaluatorId" placeholder="选择评估器" @change="(id) => onEditEvaluatorChange(idx, id)">
                <a-select-option v-for="e in evaluatorList" :key="e.id" :value="e.id">{{ e.name }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="评估器版本" required>
              <a-select v-model:value="ev.evaluatorVersion" placeholder="选择版本">
                <a-select-option v-for="v in ev.versions" :key="v.version" :value="v.version">{{ v.version }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="参数映射">
              <a-textarea v-model:value="ev.evaluatorParamMapping" :rows="2" placeholder='JSON: {"actual_output":"output"}' />
            </a-form-item>
          </div>
          <a-button v-if="editForm.evaluators.length < 5" type="dashed" size="small" block @click="addEditEvaluator" style="margin-top: 4px;">
            <PlusOutlined /> 添加评估器
          </a-button>
          <div v-if="editForm.evaluators.length >= 5" style="margin-top: 4px; font-size: 12px; color: var(--color-mute); text-align: center;">最多添加5个评估器</div>
        </a-form>
        </div>
        </a-spin>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="restartStep = 0">上一步</button>
          <div class="dialog-footer-right">
            <button class="btn-cancel" @click="restartDialogVisible = false">取消</button>
            <button class="btn-primary-sm" :disabled="restartSubmitting" @click="handleEditSubmit">
              {{ restartSubmitting ? '提交中...' : '更新并重新评测' }}
            </button>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 结果详情弹窗 -->
    <a-modal
      v-model:open="detailModalVisible"
      title="评测结果详情"
      :width="720"
      :footer="null"
      :maskClosable="false"
    >
      <div class="result-detail-scroll" v-if="detailRecord">
        <div class="result-detail-item">
          <div class="result-detail-label">输入</div>
          <div class="result-detail-value">{{ detailRecord.input || '-' }}</div>
        </div>
        <div class="result-detail-item">
          <div class="result-detail-label">实际输出</div>
          <div class="result-detail-value">{{ detailRecord.actualOutput || '-' }}</div>
        </div>
        <div class="result-detail-item">
          <div class="result-detail-label">期望输出</div>
          <div class="result-detail-value">{{ detailRecord.referenceOutput || '-' }}</div>
        </div>
        <div class="result-detail-item">
          <div class="result-detail-label">评分</div>
          <div class="result-detail-value">
            <span class="score-tag" :class="scoreClass(detailRecord.score)">
              {{ detailRecord.score?.toFixed(2) ?? '-' }}
            </span>
          </div>
        </div>
        <div class="result-detail-item">
          <div class="result-detail-label">评分理由</div>
          <div class="result-detail-value">{{ detailRecord.reason || '-' }}</div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, PauseCircleOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getExperiment, updateExperiment, stopExperiment, restartExperiment,
  getExperimentResults, getExperimentDetailResults,
} from '../api/experiment'
import { getTask } from '../api/task'
import { getEvalDatasets, getEvalDatasetVersions } from '../api/evalDataset'
import { getPrompts, getPromptVersions } from '../api/prompt'
import { getEvaluators, getEvaluatorVersions } from '../api/evaluator'
import { formatTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const experimentId = route.params.id
const experiment = ref(null)
const activeTab = ref('overview')
const resultEvaluatorTab = ref('')
const evaluatorResults = ref([])
const detailResults = ref([])
const overviewLoading = ref(false)
const resultsLoading = ref(false)
const errorMessage = ref('')

// 重新评测弹窗
const restartDialogVisible = ref(false)
const restartMode = ref('direct')
const restartSubmitting = ref(false)
const restartFormLoading = ref(false)
const restartStep = ref(0)
const editForm = reactive({
  name: '', description: '',
  datasetId: null, datasetVersion: '',
  promptKey: '', promptVersion: '',
  variableMapping: '',
  evaluators: [],
})
const datasetList = ref([])
const datasetVersions = ref([])
const promptList = ref([])
const promptVersions = ref([])
const evaluatorList = ref([])

const resultColumns = [
  { title: '输入', dataIndex: 'input', key: 'input', width: 200, ellipsis: true },
  { title: '实际输出', dataIndex: 'actualOutput', key: 'actualOutput', width: 200, ellipsis: true },
  { title: '期望输出', dataIndex: 'referenceOutput', key: 'referenceOutput', width: 200, ellipsis: true },
  { title: '评分', dataIndex: 'score', key: 'score', width: 80 },
  { title: '评分理由', dataIndex: 'reason', key: 'reason' },
  { title: '操作', key: 'detailAction', width: 80 },
]

const detailModalVisible = ref(false)
const detailRecord = ref(null)

const experimentStatus = computed(() => experiment.value?.status?.code || experiment.value?.status || '')

const statusColor = computed(() => {
  const map = { created: '#d9d9d9', running: '#1890ff', completed: '#52c41a', failed: '#ff4d4f', stopped: '#d9d9d9' }
  return map[experimentStatus.value] || '#d9d9d9'
})

const statusLabel = computed(() => {
  const map = { created: '已创建', running: '运行中', completed: '已完成', failed: '失败', stopped: '已停止' }
  return map[experimentStatus.value] || '未知'
})

onMounted(async () => {
  await loadExperiment()
  await loadResults()
})

async function loadExperiment() {
  const res = await getExperiment(experimentId)
  experiment.value = res.data || {}
  // 加载失败原因
  if (experimentStatus.value === 'failed' && experiment.value?.taskId) {
    try {
      const taskRes = await getTask(experiment.value.taskId)
      errorMessage.value = taskRes.data?.error || ''
    } catch { /* ignore */ }
  }
}

async function loadResults() {
  overviewLoading.value = true
  try {
    const res = await getExperimentResults(experimentId)
    evaluatorResults.value = res.data || []
    if (evaluatorResults.value.length > 0) {
      resultEvaluatorTab.value = evaluatorResults.value[0].evaluatorName
    }
  } catch { /* ignore */ } finally {
    overviewLoading.value = false
  }
}

async function handleRefreshResults() {
  const promises = [loadExperiment(), loadResults()]
  if (activeTab.value === 'results' && detailResults.value.length > 0) {
    resultsLoading.value = true
    promises.push(
      getExperimentDetailResults(experimentId)
        .then(res => { detailResults.value = res.data?.records || [] })
        .catch(() => {})
        .finally(() => { resultsLoading.value = false })
    )
  }
  await Promise.all(promises)
}

async function onTabChange(tab) {
  if (tab === 'results' && detailResults.value.length === 0) {
    resultsLoading.value = true
    try {
      const res = await getExperimentDetailResults(experimentId)
      detailResults.value = res.data?.records || []
    } catch { /* ignore */ } finally {
      resultsLoading.value = false
    }
  }
}

function getResultsForEvaluator(evaluatorName) {
  return detailResults.value.filter(r => r.evaluatorName === evaluatorName) || []
}

function handleStop() {
  Modal.confirm({
    title: '确认停止',
    content: '停止后实验将不再继续运行，是否继续？',
    okText: '确认停止',
    cancelText: '取消',
    async onOk() {
      await stopExperiment(experimentId)
      message.success('实验已停止')
      loadExperiment()
    },
  })
}

function handleRestart() {
  restartMode.value = 'direct'
  restartStep.value = 0
  restartDialogVisible.value = true
}

async function handleRestartConfirm() {
  if (restartMode.value === 'direct') {
    restartSubmitting.value = true
    try {
      await restartExperiment(experimentId)
      message.success('实验已重启')
      restartDialogVisible.value = false
      loadExperiment()
      loadResults()
    } finally {
      restartSubmitting.value = false
    }
  } else {
    restartStep.value = 1
    restartFormLoading.value = true
    try {
      initEditForm()
      await loadDropdowns()
    } finally {
      restartFormLoading.value = false
    }
  }
}

function initEditForm() {
  const exp = experiment.value
  editForm.name = exp.name || ''
  editForm.description = exp.description || ''
  editForm.datasetId = exp.datasetId
  editForm.datasetVersion = exp.datasetVersion || ''
  editForm.promptKey = ''
  editForm.promptVersion = ''
  editForm.variableMapping = ''
  editForm.evaluators = []
  // 解析 evaluationObjectConfig
  try {
    const objConfig = JSON.parse(exp.evaluationObjectConfig)
    const config = objConfig.config || {}
    editForm.promptKey = config.promptKey || ''
    editForm.promptVersion = config.version || ''
    const varMap = config.variableMap || []
    if (varMap.length > 0) {
      const mapped = {}
      varMap.forEach(m => { mapped[m.promptVariable] = m.datasetColumn })
      editForm.variableMapping = JSON.stringify(mapped)
    }
  } catch { /* ignore */ }
  // 解析 evaluatorConfig（支持多个评估器）
  try {
    const evalConfigs = JSON.parse(exp.evaluatorConfig)
    editForm.evaluators = evalConfigs.map(cfg => {
      const eVarMap = cfg.variableMap || []
      let paramMapping = ''
      if (eVarMap.length > 0) {
        const mapped = {}
        eVarMap.forEach(m => { mapped[m.evaluatorVariable] = m.source })
        paramMapping = JSON.stringify(mapped)
      }
      return {
        evaluatorId: null,
        evaluatorVersion: '',
        evaluatorParamMapping: paramMapping,
        evaluatorVersionId: cfg.evaluatorVersionId || '',
        versions: [],
      }
    })
  } catch { /* ignore */ }
}

async function loadDropdowns() {
  const [dsRes, pRes, eRes] = await Promise.all([
    getEvalDatasets({ pageNum: 1, pageSize: 100 }),
    getPrompts({ pageNum: 1, pageSize: 100 }),
    getEvaluators({ pageNum: 1, pageSize: 100 }),
  ])
  datasetList.value = dsRes.data?.records || []
  promptList.value = pRes.data?.records || []
  evaluatorList.value = eRes.data?.records || []
  // 级联加载已有值的版本列表
  if (editForm.datasetId) {
    const res = await getEvalDatasetVersions(editForm.datasetId)
    datasetVersions.value = res.data || []
  }
  if (editForm.promptKey) {
    const res = await getPromptVersions(editForm.promptKey)
    promptVersions.value = res.data || []
  }
  // 从 evaluatorConfig 中的 evaluatorVersionId 反查 evaluatorId（支持多个评估器）
  for (const evCfg of editForm.evaluators) {
    const evVersionId = evCfg.evaluatorVersionId
    if (!evVersionId) continue
    for (const ev of evaluatorList.value) {
      const vers = await getEvaluatorVersions(ev.id)
      const match = (vers.data || []).find(v => String(v.id) === String(evVersionId))
      if (match) {
        evCfg.evaluatorId = ev.id
        evCfg.versions = vers.data || []
        evCfg.evaluatorVersion = match.version
        break
      }
    }
  }
}

async function onEditDatasetChange(id) {
  editForm.datasetVersion = ''
  const res = await getEvalDatasetVersions(id)
  datasetVersions.value = res.data || []
}

async function onEditPromptChange(key) {
  editForm.promptVersion = ''
  const res = await getPromptVersions(key)
  promptVersions.value = res.data || []
}

async function onEditEvaluatorChange(idx, id) {
  editForm.evaluators[idx].evaluatorVersion = ''
  try {
    const res = await getEvaluatorVersions(id)
    editForm.evaluators[idx].versions = res.data || []
  } catch { editForm.evaluators[idx].versions = [] }
}

function addEditEvaluator() {
  if (editForm.evaluators.length >= 5) return message.warning('每个实验最多添加5个评估器')
  editForm.evaluators.push({ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] })
}

function removeEditEvaluator(idx) {
  editForm.evaluators.splice(idx, 1)
}

async function handleEditSubmit() {
  if (!editForm.name.trim()) return message.warning('请输入实验名称')
  if (!editForm.datasetId || !editForm.datasetVersion) return message.warning('请选择评测集和版本')
  if (!editForm.promptKey || !editForm.promptVersion) return message.warning('请选择 Prompt 和版本')
  if (editForm.evaluators.length === 0) return message.warning('请至少添加一个评估器')
  for (let i = 0; i < editForm.evaluators.length; i++) {
    const ev = editForm.evaluators[i]
    if (!ev.evaluatorId || !ev.evaluatorVersion) return message.warning(`请选择评估器 ${i + 1} 的评估器和版本`)
  }

  restartSubmitting.value = true
  try {
    let variableMap = []
    if (editForm.variableMapping.trim()) {
      try {
        const parsed = JSON.parse(editForm.variableMapping)
        variableMap = Object.entries(parsed).map(([promptVariable, datasetColumn]) => ({ promptVariable, datasetColumn }))
      } catch { return message.warning('变量映射 JSON 格式不正确') }
    }

    const dsVersion = datasetVersions.value.find(v => v.version === editForm.datasetVersion)
    if (!dsVersion) return message.warning('评测集版本无效')

    const evaluatorConfigArr = editForm.evaluators.map(ev => {
      let evaluatorParamMap = []
      if (ev.evaluatorParamMapping.trim()) {
        try {
          const parsed = JSON.parse(ev.evaluatorParamMapping)
          evaluatorParamMap = Object.entries(parsed).map(([evaluatorVariable, source]) => ({ evaluatorVariable, source }))
        } catch { throw new Error('评估器参数映射 JSON 格式不正确') }
      }
      const evVersion = ev.versions.find(v => v.version === ev.evaluatorVersion)
      return {
        evaluatorVersionId: evVersion?.id ? String(evVersion.id) : (ev.evaluatorVersionId || ''),
        variableMap: evaluatorParamMap,
      }
    })

    const evaluationObjectConfig = JSON.stringify({
      type: 'prompt',
      config: { promptKey: editForm.promptKey, version: editForm.promptVersion, variableMap },
    })

    await updateExperiment(experimentId, {
      name: editForm.name,
      description: editForm.description,
      datasetId: editForm.datasetId,
      datasetVersionId: dsVersion.id,
      datasetVersion: editForm.datasetVersion,
      evaluationObjectConfig,
      evaluatorConfig: JSON.stringify(evaluatorConfigArr),
    })
    await restartExperiment(experimentId)
    message.success('实验已更新并重启')
    restartDialogVisible.value = false
    loadExperiment()
    loadResults()
  } catch (e) {
    if (e.message) message.warning(e.message)
  } finally {
    restartSubmitting.value = false
  }
}

function scoreClass(score) {
  if (score == null) return ''
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-mid'
  return 'score-low'
}

function progressColor(score) {
  if (score == null) return '#d9d9d9'
  if (score >= 0.8) return '#52c41a'
  if (score >= 0.5) return '#faad14'
  return '#ff4d4f'
}

function openDetailModal(record) {
  detailRecord.value = record
  detailModalVisible.value = true
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '...' : str
}

</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.page-header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.btn-back {
  background: none;
  border: none;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: var(--color-link); }
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

/* 信息卡片 */
.info-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 0 auto 32px;
  max-width: 1200px;
}
.info-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px 24px;
}
.info-label {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 4px;
}
.info-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.info-value a {
  color: var(--color-link);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.15s;
}
.info-value a:hover {
  color: #005bb5;
  text-decoration: underline;
}

/* 评估器卡片 */
.evaluator-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.evaluator-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
}
.evaluator-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.evaluator-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.evaluator-version {
  font-size: 12px;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 2px 8px;
  border-radius: 100px;
}
.evaluator-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}
.evaluator-score .score-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-ink);
}
.evaluator-score .score-label {
  font-size: 13px;
  color: var(--color-mute);
}
.evaluator-score.score-high .score-value { color: #52c41a; }
.evaluator-score.score-mid .score-value { color: #faad14; }
.evaluator-score.score-low .score-value { color: #ff4d4f; }
.evaluator-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-mute);
}

/* 评分标签 */
.score-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 600;
}
.score-tag.score-high { background: var(--color-success-bg); color: #16a34a; }
.score-tag.score-mid { background: var(--color-warn-bg-deep); color: #d97706; }
.score-tag.score-low { background: var(--color-error-bg); color: #dc2626; }

.cell-preview {
  font-size: 13px;
  color: var(--color-mute);
}
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-mute);
}
.tabs-container {
  position: relative;
  margin-top: 16px;
}
.tabs-container :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}
.running-mask {
  position: absolute;
  inset: 0;
  background: var(--color-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: 8px;
}
.running-mask-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.running-mask-text {
  font-size: 14px;
  color: var(--color-body);
  margin: 0;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline-sm:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }
.btn-primary-sm {
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: var(--color-hairline-strong); color: var(--color-mute); cursor: not-allowed; }
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
  width: 28px;
  height: 28px;
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
.evaluator-info-item {
  font-size: 14px;
  line-height: 1.8;
}
.restart-form-scroll {
  max-height: 55vh;
  overflow-y: auto;
  padding-right: 8px;
}
.evaluator-config-block {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 12px;
}
.evaluator-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.evaluator-config-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.result-detail-scroll {
  max-height: 65vh;
  overflow-y: auto;
  padding-right: 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.result-detail-item {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px 16px;
}
.result-detail-label {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 6px;
}
.result-detail-value {
  font-size: 14px;
  color: var(--color-ink);
  line-height: 1.8;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
