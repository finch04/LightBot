<template>
  <div class="eval-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">评测</h1>
        <p class="page-desc">数据集、评估器和实验管理</p>
      </div>
    </div>

    <div class="tab-toolbar">
      <a-tabs v-model:activeKey="activeTab" class="eval-tabs">
        <a-tab-pane key="datasets" tab="评测集" />
        <a-tab-pane key="evaluators" tab="评估器" />
        <a-tab-pane key="experiments" tab="实验" />
      </a-tabs>
      <div class="toolbar-actions">
        <a-input
          v-model:value="searchText"
          :placeholder="searchPlaceholder"
          allow-clear
          style="width: 220px"
          @change="handleSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="handleRefresh" :disabled="getTabLoading(activeTab).value">
          <ReloadOutlined :spin="getTabLoading(activeTab).value" /> 刷新
        </button>
        <a-tooltip v-if="activeTab === 'datasets'" title="示例评测集">
          <button class="btn-outline" @click="openDatasetExampleModal">
            <SnippetsOutlined />
          </button>
        </a-tooltip>
        <a-tooltip v-if="activeTab === 'evaluators'" title="示例评估器">
          <button class="btn-outline" @click="openEvaluatorExampleModal">
            <SnippetsOutlined />
          </button>
        </a-tooltip>
        <button class="btn-primary" @click="handleAdd">
          <PlusOutlined /> {{ addBtnText }}
        </button>
      </div>
    </div>

    <div class="tab-content">
      <!-- 评测集 Tab -->
      <div v-show="activeTab === 'datasets'" style="min-height: 400px;">
        <a-spin :spinning="datasetsLoading" style="min-height: 400px; display: block;">
          <div class="card-grid">
            <div
              v-for="item in datasets"
              :key="item.id"
              class="card-item"
              @click="router.push(`/app/eval/datasets/${item.id}`)"
            >
              <div class="card-top">
                <div class="card-icon dataset-icon">{{ (item.name || 'D')[0].toUpperCase() }}</div>
                <div class="card-info">
                  <h3>{{ item.name }}</h3>
                  <span class="card-version" v-if="item.latestVersion">v{{ item.latestVersion }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <a-tooltip title="编辑">
                    <button class="btn-icon" @click="openDatasetDialog(item)"><EditOutlined /></button>
                  </a-tooltip>
                  <a-tooltip title="删除">
                    <button class="btn-icon danger" @click="handleDeleteDataset(item.id)"><DeleteOutlined /></button>
                  </a-tooltip>
                </div>
              </div>
              <p class="card-desc">{{ item.description || '暂无描述' }}</p>
              <div class="card-meta">
                <span class="card-count" v-if="item.itemCount !== undefined">{{ item.itemCount }} 条数据</span>
                <span class="card-time">{{ formatTime(item.createTime) }}</span>
              </div>
            </div>
            <div v-if="datasets.length === 0 && !datasetsLoading" class="empty-state">
              <DatabaseOutlined class="empty-icon" />
              <p v-if="searchText">没有匹配的评测集</p>
              <p v-else>还没有评测集，点击右上角创建一个吧</p>
            </div>
          </div>
        </a-spin>
      </div>

      <!-- 评估器 Tab -->
      <div v-show="activeTab === 'evaluators'" style="min-height: 400px;">
        <a-spin :spinning="evaluatorsLoading" style="min-height: 400px; display: block;">
          <div class="card-grid">
            <div
              v-for="item in evaluators"
              :key="item.id"
              class="card-item"
              @click="router.push(`/app/eval/evaluators/${item.id}`)"
            >
              <div class="card-top">
                <div class="card-icon evaluator-icon">{{ (item.name || 'E')[0].toUpperCase() }}</div>
                <div class="card-info">
                  <h3>{{ item.name }}</h3>
                  <span class="card-version" v-if="item.latestVersion">{{ item.latestVersion }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <a-tooltip title="编辑">
                    <button class="btn-icon" @click="openEvaluatorDialog(item)"><EditOutlined /></button>
                  </a-tooltip>
                  <a-tooltip title="删除">
                    <button class="btn-icon danger" @click="handleDeleteEvaluator(item.id)"><DeleteOutlined /></button>
                  </a-tooltip>
                </div>
              </div>
              <p class="card-desc">{{ item.description || '暂无描述' }}</p>
              <div class="card-tags" v-if="item.tags">
                <a-tag v-for="tag in item.tags.split(',')" :key="tag" color="purple">{{ tag.trim() }}</a-tag>
              </div>
            </div>
            <div v-if="evaluators.length === 0 && !evaluatorsLoading" class="empty-state">
              <AuditOutlined class="empty-icon" />
              <p v-if="searchText">没有匹配的评估器</p>
              <p v-else>还没有评估器，点击右上角创建一个吧</p>
            </div>
          </div>
        </a-spin>
      </div>

      <!-- 实验 Tab -->
      <div v-show="activeTab === 'experiments'" style="min-height: 400px;">
        <a-spin :spinning="experimentsLoading" style="min-height: 400px; display: block;">
        <a-table
          :dataSource="experiments"
          :columns="experimentColumns"
          :pagination="{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }"
          rowKey="id"
          size="middle"
          :loading="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a @click.stop="router.push(`/app/eval/experiments/${record.id}`)">{{ record.name }}</a>
            </template>
            <template v-if="column.key === 'datasetName'">
              <a v-if="record.datasetId" @click.stop="router.push(`/app/eval/datasets/${record.datasetId}`)">{{ record.datasetName || '-' }}</a>
              <span v-else>{{ record.datasetName || '-' }}</span>
            </template>
            <template v-if="column.key === 'promptKey'">
              <a v-if="record.promptKey" @click.stop="router.push(`/app/prompts/${record.promptKey}`)">{{ record.promptKey }}</a>
              <span v-else>-</span>
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
              <template v-if="record.evaluatorNameList?.length">
                <template v-for="(name, i) in record.evaluatorNameList.slice(0, 2)" :key="i">
                  <a v-if="record.evaluatorIdList?.[i]" @click.stop="router.push(`/app/eval/evaluators/${record.evaluatorIdList[i]}`)">{{ name }}</a>
                  <span v-else>{{ name }}</span>
                  <span v-if="i < Math.min(1, record.evaluatorNameList.length - 1)" style="color: var(--color-mute);">、</span>
                </template>
                <a-tooltip v-if="record.evaluatorNameList.length > 2" :title="record.evaluatorNameList.join('、')">
                  <span style="color: var(--color-mute); cursor: pointer;">...等{{ record.evaluatorNameList.length }}个</span>
                </a-tooltip>
              </template>
              <span v-else>{{ record.evaluatorName || '-' }}</span>
            </template>
            <template v-if="column.key === 'action'">
              <div class="table-actions">
                <a-tooltip v-if="(record.status?.code || record.status) === 'running'" title="停止">
                  <button
                    class="btn-icon"
                    @click="handleStopExperiment(record.id)"
                  >
                    <PauseCircleOutlined />
                  </button>
                </a-tooltip>
                <a-tooltip v-if="(record.status?.code || record.status) === 'stopped'" title="重启">
                  <button
                    class="btn-icon"
                    @click="handleRestartExperiment(record.id)"
                  >
                    <PlayCircleOutlined />
                  </button>
                </a-tooltip>
                <a-tooltip title="删除">
                  <button
                    class="btn-icon danger"
                    @click="handleDeleteExperiment(record.id)"
                  >
                    <DeleteOutlined />
                  </button>
                </a-tooltip>
              </div>
            </template>
          </template>
        </a-table>
        </a-spin>
      </div>
    </div>

    <!-- 评测集 创建/编辑弹窗 -->
    <a-modal
      v-model:open="datasetDialogVisible"
      :title="datasetForm.id ? '编辑评测集' : '新建评测集'"
      :width="560"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="datasetForm" :label-col="{ span: 5 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="datasetForm.name" :maxlength="30" show-count placeholder="如：客服问答评测集 (不超过30字)" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="datasetForm.description" :rows="3" :maxlength="50" show-count placeholder="评测集的用途描述 (不超过50字)" />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="datasetDialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleDatasetSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 评估器 创建/编辑弹窗 -->
    <a-modal
      v-model:open="evaluatorDialogVisible"
      :title="evaluatorForm.id ? '编辑评估器' : '新建评估器'"
      :width="560"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="evaluatorForm" :label-col="{ span: 5 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="evaluatorForm.name" :maxlength="30" show-count placeholder="如：准确性评估器 (不超过30字)" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="evaluatorForm.description" :rows="3" :maxlength="50" show-count placeholder="评估器的用途描述 (不超过50字)" />
        </a-form-item>
        <a-form-item label="标签">
          <TagInput v-model="evaluatorForm.tags" />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="evaluatorDialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleEvaluatorSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 示例评测集弹窗 -->
    <a-modal
      v-model:open="datasetExampleVisible"
      title="示例评测集"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <div class="example-intro">选择一个内置示例，快速创建评测集并学习评测数据的组织方式</div>
      <div class="example-list">
        <div v-for="ex in datasetExamples" :key="ex.key" class="example-card">
          <div class="example-card-header">
            <span class="example-name">{{ ex.name }}</span>
            <a-button type="primary" size="small" :loading="exampleCreating === ex.key" @click="handleCreateDatasetExample(ex.key)">
              生成
            </a-button>
          </div>
          <div class="example-desc-text">{{ ex.description }}</div>
          <div class="example-tags">
            <a-tag v-for="tag in ex.tags" :key="tag" color="blue">{{ tag }}</a-tag>
            <span class="example-count">{{ ex.itemCount }} 条示例数据</span>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 示例评估器弹窗 -->
    <a-modal
      v-model:open="evaluatorExampleVisible"
      title="示例评估器"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <div class="example-intro">选择一个内置示例，快速创建评估器并学习评估 Prompt 的编写方式</div>
      <div class="example-list">
        <div v-for="ex in evaluatorExamples" :key="ex.key" class="example-card">
          <div class="example-card-header">
            <span class="example-name">{{ ex.name }}</span>
            <a-button type="primary" size="small" :loading="exampleCreating === ex.key" @click="handleCreateEvaluatorExample(ex.key)">
              生成
            </a-button>
          </div>
          <div class="example-desc-text">{{ ex.description }}</div>
          <div class="example-tags">
            <a-tag v-for="tag in ex.tags" :key="tag" color="purple">{{ tag }}</a-tag>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 创建实验弹窗 -->
    <a-modal
      v-model:open="experimentDialogVisible"
      title="创建实验"
      :width="680"
      :footer="null"
      :maskClosable="false"
      @close="resetExperimentDialog"
    >
      <a-steps :current="experimentStep" size="small" style="margin-bottom: 24px">
        <a-step title="基本信息" />
        <a-step title="评测集" />
        <a-step title="评测对象" />
        <a-step title="评估器" />
      </a-steps>

      <!-- Step 1: 基本信息 -->
      <a-form v-show="experimentStep === 0" :model="experimentForm" :label-col="{ span: 5 }">
        <a-form-item label="实验名称" required>
          <a-input v-model:value="experimentForm.name" :maxlength="30" show-count placeholder="如：客服 Prompt v1 vs v2 对比 (不超过30字)" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="experimentForm.description" :rows="3" :maxlength="50" show-count placeholder="实验目的说明 (不超过50字)" />
        </a-form-item>
      </a-form>

      <!-- Step 2: 选择评测集 -->
      <a-form v-show="experimentStep === 1" :model="experimentForm" :label-col="{ span: 5 }">
        <a-form-item label="评测集" required>
          <a-select
            v-model:value="experimentForm.datasetId"
            placeholder="选择评测集"
            style="width: 100%"
            @change="onExpDatasetChange"
          >
            <a-select-option v-for="d in expDatasetList" :key="d.id" :value="d.id">
              {{ d.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据版本" required>
          <a-select
            v-model:value="experimentForm.datasetVersion"
            placeholder="选择数据版本"
            style="width: 100%"
          >
            <a-select-option v-for="v in expDatasetVersions" :key="v.version" :value="v.version">
              {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>

      <!-- Step 3: 配置评测对象 -->
      <a-form v-show="experimentStep === 2" :model="experimentForm" :label-col="{ span: 5 }">
        <a-form-item label="Prompt Key" required>
          <a-select
            v-model:value="experimentForm.promptKey"
            placeholder="选择 Prompt"
            style="width: 100%"
            @change="onExpPromptChange"
          >
            <a-select-option v-for="p in expPromptList" :key="p.promptKey" :value="p.promptKey">
              {{ p.promptKey }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Prompt 版本" required>
          <a-select
            v-model:value="experimentForm.promptVersion"
            placeholder="选择版本"
            style="width: 100%"
          >
            <a-select-option v-for="v in expPromptVersions" :key="v.version" :value="v.version">
              {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变量映射">
          <a-textarea
            v-model:value="experimentForm.variableMapping"
            :rows="3"
            placeholder='将评测集字段映射到 Prompt 变量，JSON 格式：{"input":"user_input","expected_output":"reference"}'
          />
        </a-form-item>
      </a-form>

      <!-- Step 4: 配置评估器 -->
      <div v-show="experimentStep === 3">
        <div v-for="(ev, idx) in experimentForm.evaluators" :key="idx" class="evaluator-config-block">
          <div class="evaluator-config-header">
            <span class="evaluator-config-title">评估器 {{ idx + 1 }}</span>
            <a-tooltip v-if="experimentForm.evaluators.length > 1" title="移除">
              <DeleteOutlined class="evaluator-remove-btn" @click="removeExpEvaluator(idx)" />
            </a-tooltip>
          </div>
          <a-form :model="ev" :label-col="{ span: 5 }">
            <a-form-item label="评估器" required>
              <a-select v-model:value="ev.evaluatorId" placeholder="选择评估器" style="width: 100%" @change="(id) => onExpEvaluatorChange(idx, id)">
                <a-select-option v-for="e in expEvaluatorList" :key="e.id" :value="e.id">{{ e.name }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="评估器版本" required>
              <a-select v-model:value="ev.evaluatorVersion" placeholder="选择版本" style="width: 100%">
                <a-select-option v-for="v in ev.versions" :key="v.version" :value="v.version">{{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="参数映射">
              <a-textarea v-model:value="ev.evaluatorParamMapping" :rows="2" placeholder='JSON: {"actual_output":"output","expected_output":"reference"}' />
            </a-form-item>
          </a-form>
        </div>
        <a-button v-if="experimentForm.evaluators.length < 5" type="dashed" size="small" block @click="addExpEvaluator" style="margin-top: 4px;">
          <PlusOutlined /> 添加评估器
        </a-button>
        <div v-if="experimentForm.evaluators.length >= 5" style="margin-top: 4px; font-size: 12px; color: var(--color-mute); text-align: center;">最多添加5个评估器</div>
      </div>

      <div class="dialog-footer">
        <div>
          <button v-if="experimentStep > 0" class="btn-cancel" @click="experimentStep--">上一步</button>
        </div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="experimentDialogVisible = false">取消</button>
          <button v-if="experimentStep < 3" class="btn-primary-sm" @click="nextExperimentStep">下一步</button>
          <button v-else class="btn-primary-sm" :disabled="submitting" @click="handleExperimentSubmit">
            {{ submitting ? '创建中...' : '创建实验' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined,
  DatabaseOutlined, AuditOutlined, SnippetsOutlined,
  PauseCircleOutlined, PlayCircleOutlined, ExperimentOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import TagInput from '../components/TagInput.vue'
import {
  getEvalDatasets, createEvalDataset, updateEvalDataset, deleteEvalDataset,
  listEvalDatasetExamples, createFromEvalDatasetExample,
} from '../api/evalDataset'
import {
  getEvaluators, createEvaluator, updateEvaluator, deleteEvaluator,
  listEvaluatorExamples, createFromEvaluatorExample,
  getEvaluatorVersions,
} from '../api/evaluator'
import {
  getExperiments, stopExperiment, deleteExperiment, restartExperiment, createExperiment,
} from '../api/experiment'
import { getEvalDatasetVersions } from '../api/evalDataset'
import { getPrompts, getPromptVersions } from '../api/prompt'

const router = useRouter()
const route = useRoute()
const activeTab = ref(route.query.tab || 'datasets')
const searchText = ref('')
const datasetsLoading = ref(false)
const evaluatorsLoading = ref(false)
const experimentsLoading = ref(false)
const submitting = ref(false)
const exampleCreating = ref(null)
const loadedTabs = new Set()

function getTabLoading(tab) {
  if (tab === 'datasets') return datasetsLoading
  if (tab === 'evaluators') return evaluatorsLoading
  return experimentsLoading
}

// ========== 数据 ==========
const datasets = ref([])
const evaluators = ref([])
const experiments = ref([])

// ========== 评测集 ==========
const datasetDialogVisible = ref(false)
const datasetForm = reactive({ id: null, name: '', description: '' })
const datasetExampleVisible = ref(false)
const datasetExamples = ref([])

// ========== 评估器 ==========
const evaluatorDialogVisible = ref(false)
const evaluatorForm = reactive({ id: null, name: '', description: '', tags: '' })
const evaluatorExampleVisible = ref(false)
const evaluatorExamples = ref([])

// ========== 实验 ==========
const experimentDialogVisible = ref(false)
const experimentStep = ref(0)
const experimentForm = reactive({
  name: '',
  description: '',
  datasetId: null,
  datasetVersion: '',
  promptKey: '',
  promptVersion: '',
  variableMapping: '',
  evaluators: [{ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] }],
})
const expDatasetList = ref([])
const expDatasetVersions = ref([])
const expPromptList = ref([])
const expPromptVersions = ref([])
const expEvaluatorList = ref([])

const experimentColumns = [
  { title: '实验名称', key: 'name', dataIndex: 'name', width: 200, ellipsis: true },
  { title: '评测集', key: 'datasetName', dataIndex: 'datasetName', width: 140, ellipsis: true },
  { title: 'Prompt', key: 'promptKey', dataIndex: 'promptKey', width: 140, ellipsis: true },
  { title: '评估器', key: 'evaluatorName', dataIndex: 'evaluatorName', width: 140, ellipsis: true },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '进度', key: 'progress', dataIndex: 'progress', width: 160 },
  { title: '创建时间', key: 'createTime', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 120 },
]

// ========== 计算属性 ==========
const addBtnText = computed(() => {
  const map = { datasets: '新建评测集', evaluators: '新建评估器', experiments: '创建实验' }
  return map[activeTab.value]
})

const searchPlaceholder = computed(() => {
  const map = { datasets: '搜索评测集名称...', evaluators: '搜索评估器名称...', experiments: '搜索实验名称...' }
  return map[activeTab.value]
})

// ========== 初始化 ==========
onMounted(() => loadData(activeTab.value, true))

watch(activeTab, (tab) => {
  router.replace({ query: { ...route.query, tab } })
  searchText.value = ''
  loadData(tab)
})

// ========== 数据加载 ==========
function handleSearch() {
  loadData(activeTab.value, true)
}

function handleRefresh() {
  loadData(activeTab.value, true)
}

async function loadData(tab = activeTab.value, force = false) {
  if (!force && loadedTabs.has(tab)) return
  loadedTabs.add(tab)
  const loading = getTabLoading(tab)
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    if (tab === 'datasets') {
      const res = await getEvalDatasets(params)
      datasets.value = res.data?.records || []
    } else if (tab === 'evaluators') {
      const res = await getEvaluators(params)
      evaluators.value = res.data?.records || []
    } else {
      const res = await getExperiments(params)
      experiments.value = res.data?.records || []
    }
  } finally {
    loading.value = false
  }
}

// ========== 评测集操作 ==========
function handleAdd() {
  if (activeTab.value === 'datasets') openDatasetDialog()
  else if (activeTab.value === 'evaluators') openEvaluatorDialog()
  else openExperimentDialog()
}

function openDatasetDialog(row) {
  if (row) {
    Object.assign(datasetForm, { id: row.id, name: row.name || '', description: row.description || '' })
  } else {
    Object.assign(datasetForm, { id: null, name: '', description: '' })
  }
  datasetDialogVisible.value = true
}

async function handleDatasetSubmit() {
  if (!datasetForm.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    if (datasetForm.id) {
      await updateEvalDataset(datasetForm.id, { name: datasetForm.name, description: datasetForm.description })
      message.success('更新成功')
    } else {
      await createEvalDataset({ name: datasetForm.name, description: datasetForm.description })
      message.success('创建成功')
    }
    datasetDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDeleteDataset(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除', okType: 'danger', cancelText: '取消',
    async onOk() { await deleteEvalDataset(id); message.success('删除成功'); loadData() },
  })
}

async function openDatasetExampleModal() {
  datasetExampleVisible.value = true
  try { const res = await listEvalDatasetExamples(); datasetExamples.value = res.data || [] }
  catch { datasetExamples.value = [] }
}

async function handleCreateDatasetExample(key) {
  exampleCreating.value = key
  try {
    await createFromEvalDatasetExample(key)
    message.success('示例评测集创建成功')
    datasetExampleVisible.value = false
    loadData(activeTab.value, true)
  } finally { exampleCreating.value = null }
}

// ========== 评估器操作 ==========
function openEvaluatorDialog(row) {
  if (row) {
    Object.assign(evaluatorForm, { id: row.id, name: row.name || '', description: row.description || '', tags: row.tags || '' })
  } else {
    Object.assign(evaluatorForm, { id: null, name: '', description: '', tags: '' })
  }
  evaluatorDialogVisible.value = true
}

async function handleEvaluatorSubmit() {
  if (!evaluatorForm.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    if (evaluatorForm.id) {
      await updateEvaluator(evaluatorForm.id, { name: evaluatorForm.name, description: evaluatorForm.description, tags: evaluatorForm.tags })
      message.success('更新成功')
    } else {
      await createEvaluator({ name: evaluatorForm.name, description: evaluatorForm.description, tags: evaluatorForm.tags })
      message.success('创建成功')
    }
    evaluatorDialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

function handleDeleteEvaluator(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除', okType: 'danger', cancelText: '取消',
    async onOk() { await deleteEvaluator(id); message.success('删除成功'); loadData() },
  })
}

async function openEvaluatorExampleModal() {
  evaluatorExampleVisible.value = true
  try { const res = await listEvaluatorExamples(); evaluatorExamples.value = res.data || [] }
  catch { evaluatorExamples.value = [] }
}

async function handleCreateEvaluatorExample(key) {
  exampleCreating.value = key
  try {
    await createFromEvaluatorExample(key)
    message.success('示例评估器创建成功')
    evaluatorExampleVisible.value = false
    loadData(activeTab.value, true)
  } finally { exampleCreating.value = null }
}

// ========== 实验创建 ==========
async function openExperimentDialog() {
  resetExperimentDialog()
  experimentDialogVisible.value = true
  try {
    const [dsRes, pRes, eRes] = await Promise.all([
      getEvalDatasets({ pageNum: 1, pageSize: 100 }),
      getPrompts({ pageNum: 1, pageSize: 100 }),
      getEvaluators({ pageNum: 1, pageSize: 100 }),
    ])
    expDatasetList.value = dsRes.data?.records || []
    expPromptList.value = pRes.data?.records || []
    expEvaluatorList.value = eRes.data?.records || []
  } catch { /* ignore */ }
}

function resetExperimentDialog() {
  experimentStep.value = 0
  Object.assign(experimentForm, {
    name: '', description: '', datasetId: null, datasetVersion: '',
    promptKey: '', promptVersion: '', variableMapping: '',
    evaluators: [{ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] }],
  })
  expDatasetVersions.value = []
  expPromptVersions.value = []
}

async function onExpDatasetChange(id) {
  experimentForm.datasetVersion = ''
  try { const res = await getEvalDatasetVersions(id); expDatasetVersions.value = res.data || [] }
  catch { expDatasetVersions.value = [] }
}

async function onExpPromptChange(key) {
  experimentForm.promptVersion = ''
  try { const res = await getPromptVersions(key); expPromptVersions.value = res.data || [] }
  catch { expPromptVersions.value = [] }
}

async function onExpEvaluatorChange(idx, id) {
  experimentForm.evaluators[idx].evaluatorVersion = ''
  try { const res = await getEvaluatorVersions(id); experimentForm.evaluators[idx].versions = res.data || [] }
  catch { experimentForm.evaluators[idx].versions = [] }
}

function addExpEvaluator() {
  if (experimentForm.evaluators.length >= 5) return message.warning('每个实验最多添加5个评估器')
  experimentForm.evaluators.push({ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] })
}

function removeExpEvaluator(idx) {
  experimentForm.evaluators.splice(idx, 1)
}

function nextExperimentStep() {
  if (experimentStep.value === 0 && !experimentForm.name.trim()) return message.warning('请输入实验名称')
  if (experimentStep.value === 1 && (!experimentForm.datasetId || !experimentForm.datasetVersion)) return message.warning('请选择评测集和版本')
  if (experimentStep.value === 2 && (!experimentForm.promptKey || !experimentForm.promptVersion)) return message.warning('请选择 Prompt 和版本')
  experimentStep.value++
}

async function handleExperimentSubmit() {
  if (experimentForm.evaluators.length === 0) return message.warning('请至少添加一个评估器')
  for (let i = 0; i < experimentForm.evaluators.length; i++) {
    const ev = experimentForm.evaluators[i]
    if (!ev.evaluatorId || !ev.evaluatorVersion) return message.warning(`请选择评估器 ${i + 1} 的评估器和版本`)
  }
  submitting.value = true
  try {
    let variableMap = []
    if (experimentForm.variableMapping.trim()) {
      try {
        const parsed = JSON.parse(experimentForm.variableMapping)
        variableMap = Object.entries(parsed).map(([promptVariable, datasetColumn]) => ({ promptVariable, datasetColumn }))
      } catch { return message.warning('变量映射 JSON 格式不正确') }
    }

    const dsVersion = expDatasetVersions.value.find(v => v.version === experimentForm.datasetVersion)
    if (!dsVersion) return message.warning('评测集版本无效，请重新选择')
    const datasetVersionId = dsVersion.id

    const evaluationObjectConfig = JSON.stringify({
      type: 'prompt',
      config: { promptKey: experimentForm.promptKey, version: experimentForm.promptVersion, variableMap },
    })
    const evaluatorConfigArr = experimentForm.evaluators.map(ev => {
      let evaluatorParamMap = []
      if (ev.evaluatorParamMapping?.trim()) {
        try {
          const parsed = JSON.parse(ev.evaluatorParamMapping)
          evaluatorParamMap = Object.entries(parsed).map(([evaluatorVariable, source]) => ({ evaluatorVariable, source }))
        } catch { throw new Error('评估器参数映射 JSON 格式不正确') }
      }
      const evVersion = ev.versions?.find(v => v.version === ev.evaluatorVersion)
      return {
        evaluatorVersionId: evVersion?.id ? String(evVersion.id) : '',
        variableMap: evaluatorParamMap,
      }
    })
    const evaluatorConfig = JSON.stringify(evaluatorConfigArr)

    await createExperiment({
      name: experimentForm.name,
      description: experimentForm.description,
      datasetId: experimentForm.datasetId,
      datasetVersionId,
      datasetVersion: experimentForm.datasetVersion,
      evaluationObjectConfig,
      evaluatorConfig,
    })
    message.success('实验创建成功')
    experimentDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ========== 实验操作 ==========
function handleStopExperiment(id) {
  Modal.confirm({
    title: '确认停止', content: '停止后实验将不再继续运行，是否继续？',
    okText: '确认停止', cancelText: '取消',
    async onOk() { await stopExperiment(id); message.success('实验已停止'); loadData() },
  })
}

function handleRestartExperiment(id) {
  Modal.confirm({
    title: '确认重启', content: '将重新运行该实验，是否继续？',
    okText: '确认重启', cancelText: '取消',
    async onOk() { await restartExperiment(id); message.success('实验已重启'); loadData() },
  })
}

function handleDeleteExperiment(id) {
  Modal.confirm({
    title: '确认删除', content: '删除后将无法恢复，是否继续？',
    okText: '确认删除', okType: 'danger', cancelText: '取消',
    async onOk() { await deleteExperiment(id); message.success('删除成功'); loadData() },
  })
}

// ========== 工具函数 ==========
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
.eval-page {
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  padding: 32px;
  display: flex;
  flex-direction: column;
}
.page-header {
  margin-bottom: 20px;
  flex-shrink: 0;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin: 0;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.eval-tabs {
  flex: 1;
}
.eval-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.tab-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* 按钮 */
.btn-primary, .btn-outline { white-space: nowrap; }
.btn-primary-sm {
  padding: 6px 16px;
  background: var(--color-primary);
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

/* 卡片网格 */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.card-item {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.card-item:hover {
  border-color: var(--color-link);
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}
.dataset-icon { background: linear-gradient(135deg, #0891b2, #0e7490); }
.evaluator-icon { background: linear-gradient(135deg, #f97316, #ea580c); }
.card-info { flex: 1; min-width: 0; }
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-version {
  font-size: 12px;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 2px 8px;
  border-radius: 100px;
}
.card-actions { display: flex; gap: 4px; }
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card-count {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 100px;
}
.card-time {
  font-size: 12px;
  color: var(--color-mute);
}
.card-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.table-actions { display: flex; gap: 4px; }

/* 空状态 */
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--color-mute);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: block;
}

/* 弹窗 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }

/* 示例弹窗 */
.example-intro {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 16px;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.example-card {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 16px;
}
.example-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.example-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.example-desc-text {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 10px;
  line-height: 1.5;
}
.example-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.example-count {
  font-size: 12px;
  color: var(--color-mute);
  margin-left: 4px;
}
.evaluator-config-block { border: 1px solid var(--color-hairline); border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; }
.evaluator-config-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.evaluator-config-title { font-size: 13px; font-weight: 600; color: var(--color-ink); }
.evaluator-remove-btn { color: var(--color-mute); cursor: pointer; font-size: 14px; }
.evaluator-remove-btn:hover { color: #ef4444; }
</style>
