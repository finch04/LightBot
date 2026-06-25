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

      <!-- Step 4: 配置评估器（支持多个） -->
      <div v-show="currentStep === 3">
        <div v-for="(ev, idx) in createForm.evaluators" :key="idx" class="evaluator-config-block">
          <div class="evaluator-config-header">
            <span class="evaluator-config-title">评估器 {{ idx + 1 }}</span>
            <a-tooltip v-if="createForm.evaluators.length > 1" title="移除">
              <button class="btn-icon danger" @click="removeEvaluator(idx)"><DeleteOutlined /></button>
            </a-tooltip>
          </div>
          <a-form :label-col="{ span: 5 }">
            <a-form-item label="评估器" required>
              <a-select
                v-model:value="ev.evaluatorId"
                placeholder="选择评估器"
                style="width: 100%"
                @change="(id) => onEvaluatorChange(idx, id)"
              >
                <a-select-option v-for="e in evaluatorList" :key="e.id" :value="e.id">
                  {{ e.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="评估器版本" required>
              <a-select
                v-model:value="ev.evaluatorVersion"
                placeholder="选择版本"
                style="width: 100%"
              >
                <a-select-option v-for="v in ev.versions" :key="v.version" :value="v.version">
                  {{ v.version }} {{ v.versionDesc ? '- ' + v.versionDesc : '' }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="参数映射">
              <a-textarea
                v-model:value="ev.evaluatorParamMapping"
                :rows="2"
                placeholder='评估器变量映射，JSON 格式：{"actual_output":"output","expected_output":"reference"}'
              />
            </a-form-item>
          </a-form>
        </div>
        <button v-if="createForm.evaluators.length < 5" class="btn-outline" style="margin-top: 8px" @click="addEvaluator">
          <PlusOutlined /> 添加评估器
        </button>
        <div v-if="createForm.evaluators.length >= 5" style="margin-top: 8px; font-size: 12px; color: var(--color-mute);">最多添加5个评估器</div>
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
import { ArrowLeftOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
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

const createForm = reactive({
  name: '',
  description: '',
  datasetId: null,
  datasetVersion: '',
  promptKey: '',
  promptVersion: '',
  variableMapping: '',
  evaluators: [{ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] }],
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

async function onEvaluatorChange(idx, id) {
  createForm.evaluators[idx].evaluatorVersion = ''
  try {
    const res = await getEvaluatorVersions(id)
    createForm.evaluators[idx].versions = res.data || []
  } catch { createForm.evaluators[idx].versions = [] }
}

function addEvaluator() {
  if (createForm.evaluators.length >= 5) return message.warning('每个实验最多添加5个评估器')
  createForm.evaluators.push({ evaluatorId: null, evaluatorVersion: '', evaluatorParamMapping: '', versions: [] })
}

function removeEvaluator(idx) {
  createForm.evaluators.splice(idx, 1)
}

function nextStep() {
  if (currentStep.value === 0 && !createForm.name.trim()) return message.warning('请输入实验名称')
  if (currentStep.value === 1 && (!createForm.datasetId || !createForm.datasetVersion)) return message.warning('请选择评测集和版本')
  if (currentStep.value === 2 && (!createForm.promptKey || !createForm.promptVersion)) return message.warning('请选择 Prompt 和版本')
  currentStep.value++
}

async function handleCreate() {
  if (createForm.evaluators.length === 0) return message.warning('请至少添加一个评估器')
  for (let i = 0; i < createForm.evaluators.length; i++) {
    const ev = createForm.evaluators[i]
    if (!ev.evaluatorId || !ev.evaluatorVersion) return message.warning(`请选择评估器 ${i + 1} 的评估器和版本`)
  }

  submitting.value = true
  try {
    let variableMap = []
    if (createForm.variableMapping.trim()) {
      try {
        const parsed = JSON.parse(createForm.variableMapping)
        variableMap = Object.entries(parsed).map(([promptVariable, datasetColumn]) => ({ promptVariable, datasetColumn }))
      } catch { return message.warning('变量映射 JSON 格式不正确') }
    }

    const dsVersion = datasetVersions.value.find(v => v.version === createForm.datasetVersion)
    const datasetVersionId = dsVersion?.id || null

    const evaluationObjectConfig = JSON.stringify({
      type: 'prompt',
      config: {
        promptKey: createForm.promptKey,
        version: createForm.promptVersion,
        variableMap,
      },
    })

    const evaluatorConfigArr = createForm.evaluators.map(ev => {
      let evaluatorParamMap = []
      if (ev.evaluatorParamMapping.trim()) {
        try {
          const parsed = JSON.parse(ev.evaluatorParamMapping)
          evaluatorParamMap = Object.entries(parsed).map(([evaluatorVariable, source]) => ({ evaluatorVariable, source }))
        } catch { throw new Error('评估器参数映射 JSON 格式不正确') }
      }
      const evVersion = ev.versions.find(v => v.version === ev.evaluatorVersion)
      return {
        evaluatorVersionId: evVersion?.id ? String(evVersion.id) : '',
        variableMap: evaluatorParamMap,
      }
    })

    await createExperiment({
      name: createForm.name,
      description: createForm.description,
      datasetId: createForm.datasetId,
      datasetVersionId,
      datasetVersion: createForm.datasetVersion,
      evaluationObjectConfig,
      evaluatorConfig: JSON.stringify(evaluatorConfigArr),
    })
    message.success('实验创建成功')
    router.push('/app/eval?tab=experiments')
  } catch (e) {
    if (e.message) message.warning(e.message)
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
  background: var(--color-canvas-soft);
}
.page-header {
  margin-bottom: 24px;
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
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin: 0;
}
.create-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 32px;
  max-width: 800px;
}
.step-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--color-hairline);
}
.step-footer-right { display: flex; gap: 12px; }
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
.btn-primary:hover:not(:disabled) { background: #27272a; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-cancel {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 14px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover { border-color: var(--color-link); color: var(--color-link); }
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
  margin-bottom: 12px;
}
.evaluator-config-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
</style>
