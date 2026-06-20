<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/app/eval')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">创建实验</h1>
        <p class="page-desc">选择评测集、配置评测对象和评估器，创建评测实验</p>
      </div>
    </div>

    <div class="create-card">
      <a-steps :current="currentStep" size="small" style="margin-bottom: 32px">
        <a-step title="基本信息" />
        <a-step title="评测集" />
        <a-step title="评测对象" />
        <a-step title="评估器" />
      </a-steps>

      <!-- Step 1: 基本信息 -->
      <div v-show="currentStep === 0">
        <a-form :model="createForm" :label-col="{ span: 4 }">
          <a-form-item label="实验名称" required>
            <a-input v-model:value="createForm.name" placeholder="如：客服 Prompt v1 vs v2 对比" :maxlength="30" show-count />
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="createForm.description" :rows="3" placeholder="实验目的说明" />
          </a-form-item>
        </a-form>
      </div>

      <!-- Step 2: 选择评测集 -->
      <div v-show="currentStep === 1">
        <a-form :model="createForm" :label-col="{ span: 4 }">
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
        <a-form :model="createForm" :label-col="{ span: 4 }">
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
        <a-form :model="createForm" :label-col="{ span: 4 }">
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
      <div class="step-footer">
        <div>
          <button v-if="currentStep > 0" class="btn-cancel" @click="currentStep--">上一步</button>
        </div>
        <div class="step-footer-right">
          <button class="btn-cancel" @click="router.push('/app/eval')">取消</button>
          <button v-if="currentStep < 3" class="btn-primary" @click="nextStep">下一步</button>
          <button v-else class="btn-primary" :disabled="submitting" @click="handleCreate">
            {{ submitting ? '创建中...' : '创建实验' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { createExperiment } from '../api/experiment'
import { getEvalDatasets, getEvalDatasetVersions } from '../api/evalDataset'
import { getPrompts, getPromptVersions } from '../api/prompt'
import { getEvaluators, getEvaluatorVersions } from '../api/evaluator'

const router = useRouter()
const submitting = ref(false)
const currentStep = ref(0)

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

onMounted(() => loadDropdowns())

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

    const dsVersion = datasetVersions.value.find(v => v.version === createForm.datasetVersion)
    const datasetVersionId = dsVersion?.id || null

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
    router.push('/app/eval')
  } finally {
    submitting.value = false
  }
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
  margin-bottom: 24px;
}
.btn-back {
  background: none;
  border: none;
  color: #71717a;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: #0070f3; }
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
.create-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 32px;
  max-width: 800px;
}
.step-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}
.step-footer-right { display: flex; gap: 12px; }
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
.btn-primary:hover:not(:disabled) { background: #27272a; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-cancel {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  cursor: pointer;
  font-size: 14px;
}
.btn-cancel:hover { border-color: #0070f3; color: #0070f3; }
</style>
