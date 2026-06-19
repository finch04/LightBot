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
        <button class="btn-outline" @click="handleRefresh" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
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
      <div v-show="activeTab === 'datasets'">
        <a-spin :spinning="loading">
          <div class="card-grid">
            <div
              v-for="item in datasets"
              :key="item.id"
              class="card-item"
              @click="router.push(`/app/eval/datasets/${item.id}`)"
            >
              <div class="card-top">
                <div class="card-icon dataset-icon">D</div>
                <div class="card-info">
                  <h3>{{ item.name }}</h3>
                  <span class="card-version" v-if="item.latestVersion">v{{ item.latestVersion }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <button class="btn-icon" @click="openDatasetDialog(item)"><EditOutlined /></button>
                  <button class="btn-icon danger" @click="handleDeleteDataset(item.id)"><DeleteOutlined /></button>
                </div>
              </div>
              <p class="card-desc">{{ item.description || '暂无描述' }}</p>
              <div class="card-meta">
                <span class="card-count" v-if="item.itemCount !== undefined">{{ item.itemCount }} 条数据</span>
                <span class="card-time">{{ formatTime(item.createTime) }}</span>
              </div>
            </div>
            <div v-if="datasets.length === 0 && !loading" class="empty-state">
              <DatabaseOutlined class="empty-icon" />
              <p v-if="searchText">没有匹配的评测集</p>
              <p v-else>还没有评测集，点击右上角创建一个吧</p>
            </div>
          </div>
        </a-spin>
      </div>

      <!-- 评估器 Tab -->
      <div v-show="activeTab === 'evaluators'">
        <a-spin :spinning="loading">
          <div class="card-grid">
            <div
              v-for="item in evaluators"
              :key="item.id"
              class="card-item"
              @click="router.push(`/app/eval/evaluators/${item.id}`)"
            >
              <div class="card-top">
                <div class="card-icon evaluator-icon">E</div>
                <div class="card-info">
                  <h3>{{ item.name }}</h3>
                  <span class="card-version" v-if="item.latestVersion">{{ item.latestVersion }}</span>
                </div>
                <div class="card-actions" @click.stop>
                  <button class="btn-icon" @click="openEvaluatorDialog(item)"><EditOutlined /></button>
                  <button class="btn-icon danger" @click="handleDeleteEvaluator(item.id)"><DeleteOutlined /></button>
                </div>
              </div>
              <p class="card-desc">{{ item.description || '暂无描述' }}</p>
              <div class="card-tags" v-if="item.tags">
                <a-tag v-for="tag in item.tags.split(',')" :key="tag" color="purple">{{ tag.trim() }}</a-tag>
              </div>
            </div>
            <div v-if="evaluators.length === 0 && !loading" class="empty-state">
              <AuditOutlined class="empty-icon" />
              <p v-if="searchText">没有匹配的评估器</p>
              <p v-else>还没有评估器，点击右上角创建一个吧</p>
            </div>
          </div>
        </a-spin>
      </div>

      <!-- 实验 Tab -->
      <div v-show="activeTab === 'experiments'">
        <a-table
          :dataSource="experiments"
          :columns="experimentColumns"
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
            <template v-if="column.key === 'action'">
              <div class="table-actions">
                <button
                  v-if="(record.status?.code || record.status) === 'running'"
                  class="btn-icon"
                  title="停止"
                  @click="handleStopExperiment(record.id)"
                >
                  <PauseCircleOutlined />
                </button>
                <button
                  v-if="(record.status?.code || record.status) === 'stopped'"
                  class="btn-icon"
                  title="重启"
                  @click="handleRestartExperiment(record.id)"
                >
                  <PlayCircleOutlined />
                </button>
                <button
                  class="btn-icon danger"
                  title="删除"
                  @click="handleDeleteExperiment(record.id)"
                >
                  <DeleteOutlined />
                </button>
              </div>
            </template>
          </template>
        </a-table>
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
          <a-input v-model:value="datasetForm.name" :maxlength="50" show-count placeholder="如：客服问答评测集 (不超过50字)" />
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
          <a-input v-model:value="evaluatorForm.name" placeholder="如：准确性评估器" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="evaluatorForm.description" :rows="3" placeholder="评估器的用途描述" />
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
          <a-input v-model:value="experimentForm.name" placeholder="如：客服 Prompt v1 vs v2 对比" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="experimentForm.description" :rows="3" placeholder="实验目的说明" />
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
      <a-form v-show="experimentStep === 3" :model="experimentForm" :label-col="{ span: 5 }">
        <a-form-item label="评估器" required>
          <a-select
            v-model:value="experimentForm.evaluatorId"
            placeholder="选择评估器"
            style="width: 100%"
            @change="onExpEvaluatorChange"
          >
            <a-select-option v-for="e in expEvaluatorList" :key="e.id" :value="e.id">
              {{ e.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="评估器版本" required>
          <a-select
            v-model:value="experimentForm.evaluatorVersion"
            placeholder="选择版本"
            style="width: 100%"
          >
            <a-select-option v-for="v in expEvaluatorVersions" :key="v.version" :value="v.version">
              {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="参数映射">
          <a-textarea
            v-model:value="experimentForm.evaluatorParamMapping"
            :rows="3"
            placeholder='评估器变量映射，JSON 格式：{"actual_output":"output","expected_output":"reference"}'
          />
        </a-form-item>
      </a-form>

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
const loading = ref(false)
const submitting = ref(false)
const exampleCreating = ref(null)

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
  evaluatorId: null,
  evaluatorVersion: '',
  evaluatorParamMapping: '',
})
const expDatasetList = ref([])
const expDatasetVersions = ref([])
const expPromptList = ref([])
const expPromptVersions = ref([])
const expEvaluatorList = ref([])
const expEvaluatorVersions = ref([])

const experimentColumns = [
  { title: '实验名称', key: 'name', dataIndex: 'name', width: 240 },
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
onMounted(() => loadData())

watch(activeTab, (tab) => {
  router.replace({ query: { ...route.query, tab } })
  searchText.value = ''
  loadData()
})

// ========== 数据加载 ==========
function handleSearch() {
  loadData()
}

function handleRefresh() {
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (searchText.value) params.keyword = searchText.value
    if (activeTab.value === 'datasets') {
      const res = await getEvalDatasets(params)
      datasets.value = res.data?.records || []
    } else if (activeTab.value === 'evaluators') {
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
    loadData()
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
    loadData()
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
    evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '',
  })
  expDatasetVersions.value = []
  expPromptVersions.value = []
  expEvaluatorVersions.value = []
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

async function onExpEvaluatorChange(id) {
  experimentForm.evaluatorVersion = ''
  try { const res = await getEvaluatorVersions(id); expEvaluatorVersions.value = res.data || [] }
  catch { expEvaluatorVersions.value = [] }
}

function nextExperimentStep() {
  if (experimentStep.value === 0 && !experimentForm.name.trim()) return message.warning('请输入实验名称')
  if (experimentStep.value === 1 && (!experimentForm.datasetId || !experimentForm.datasetVersion)) return message.warning('请选择评测集和版本')
  if (experimentStep.value === 2 && (!experimentForm.promptKey || !experimentForm.promptVersion)) return message.warning('请选择 Prompt 和版本')
  experimentStep.value++
}

async function handleExperimentSubmit() {
  if (!experimentForm.evaluatorId || !experimentForm.evaluatorVersion) return message.warning('请选择评估器和版本')
  submitting.value = true
  try {
    let variableMap = []
    if (experimentForm.variableMapping.trim()) {
      try {
        const parsed = JSON.parse(experimentForm.variableMapping)
        variableMap = Object.entries(parsed).map(([promptVariable, datasetColumn]) => ({ promptVariable, datasetColumn }))
      } catch { return message.warning('变量映射 JSON 格式不正确') }
    }
    let evaluatorParamMap = []
    if (experimentForm.evaluatorParamMapping.trim()) {
      try {
        const parsed = JSON.parse(experimentForm.evaluatorParamMapping)
        evaluatorParamMap = Object.entries(parsed).map(([evaluatorVariable, source]) => ({ evaluatorVariable, source }))
      } catch { return message.warning('参数映射 JSON 格式不正确') }
    }

    const dsVersion = expDatasetVersions.value.find(v => v.version === experimentForm.datasetVersion)
    const datasetVersionId = dsVersion?.id || null
    const evVersion = expEvaluatorVersions.value.find(v => v.version === experimentForm.evaluatorVersion)
    const evaluatorVersionId = evVersion?.id || null

    const evaluationObjectConfig = JSON.stringify({
      type: 'prompt',
      config: { promptKey: experimentForm.promptKey, version: experimentForm.promptVersion, variableMap },
    })
    const evaluatorConfig = JSON.stringify([{
      evaluatorVersionId: evaluatorVersionId ? String(evaluatorVersionId) : '',
      variableMap: evaluatorParamMap,
    }])

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
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.eval-page {
  height: 100vh;
  overflow: hidden;
  background: #fafafa;
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
  color: #171717;
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
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
  white-space: nowrap;
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
  white-space: nowrap;
}
.btn-outline:hover { border-color: #0070f3; color: #0070f3; }
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

/* 卡片网格 */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.card-item {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.card-item:hover {
  border-color: #0070f3;
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
.dataset-icon { background: linear-gradient(135deg, #10b981, #059669); }
.evaluator-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
.card-info { flex: 1; min-width: 0; }
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-version {
  font-size: 12px;
  color: #0070f3;
  background: #e8f4ff;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-actions { display: flex; gap: 4px; }
.card-desc {
  font-size: 13px;
  color: #71717a;
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
  color: #71717a;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-time {
  font-size: 12px;
  color: #a1a1aa;
}
.card-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.table-actions { display: flex; gap: 4px; }

/* 空状态 */
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: #a1a1aa;
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
  color: #71717a;
  margin-bottom: 16px;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.example-card {
  background: #fafafa;
  border: 1px solid #ebebeb;
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
  color: #171717;
}
.example-desc-text {
  font-size: 13px;
  color: #71717a;
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
  color: #a1a1aa;
  margin-left: 4px;
}
</style>
