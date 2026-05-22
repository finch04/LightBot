<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/agents')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">{{ agent.name || 'Agent 详情' }}</h1>
        <p class="page-desc">{{ agent.description || '暂无描述' }}</p>
      </div>
      <div class="header-actions">
        <button class="btn-outline" @click="startChat">
          <MessageOutlined /> 对话
        </button>
        <button class="btn-primary" @click="handleSave" :disabled="saving">
          <SaveOutlined /> 保存配置
        </button>
      </div>
    </div>

    <div class="content-grid">
      <!-- 基本信息 -->
      <div class="panel">
        <div class="panel-header">
          <h3>基本信息</h3>
        </div>
        <a-form :model="agent" :label-col="{ span: 6 }">
          <a-form-item label="名称">
            <a-input v-model:value="agent.name" placeholder="Agent 名称" />
          </a-form-item>
          <a-form-item label="头像">
            <div class="avatar-upload">
              <div class="avatar-preview" :class="{ 'has-avatar': avatarUrl }">
                <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-img" @error="agent.avatar = ''" />
                <span v-else class="avatar-placeholder">{{ (agent.name || 'A')[0] }}</span>
                <div class="avatar-overlay" @click="triggerAvatarUpload">
                  <UploadOutlined />
                </div>
              </div>
              <input ref="avatarInputRef" type="file" accept="image/*" style="display: none" @change="onAvatarFileChange" />
              <span class="avatar-tip">支持 jpg/png，建议 200x200</span>
            </div>
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea v-model:value="agent.description" :rows="2" placeholder="Agent 描述" />
          </a-form-item>
          <a-form-item label="系统提示词">
            <div class="prompt-wrapper">
              <a-textarea v-model:value="agent.systemPrompt" :rows="6" placeholder="定义 Agent 的行为和角色..." />
              <a-tooltip :title="generatingPrompt ? '生成中...' : 'AI生成提示词'">
                <button class="btn-ai-icon" :disabled="generatingPrompt" @click="handleGeneratePrompt">
                  <ThunderboltOutlined :spin="generatingPrompt" />
                </button>
              </a-tooltip>
            </div>
          </a-form-item>
          <a-form-item label="类型">
            <a-select v-model:value="agent.agentType" style="width: 100%">
              <a-select-option value="chat">对话型</a-select-option>
              <a-select-option value="assistant">助手型</a-select-option>
              <a-select-option value="workflow">工作流型</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="欢迎语">
            <a-textarea v-model:value="agent.welcomeMessage" :rows="2" placeholder="对话时显示的欢迎语（可选）" />
          </a-form-item>
          <a-form-item label="推荐问题">
            <div class="questions-header">
              <button class="btn-ai-sm" :disabled="generatingQuestions" @click="handleGenerateQuestions">
                <ThunderboltOutlined :spin="generatingQuestions" />
                {{ generatingQuestions ? '生成中...' : 'AI生成推荐问题' }}
              </button>
            </div>
            <div class="recommended-questions">
              <div v-for="(q, i) in recommendedQuestions" :key="i" class="question-row">
                <a-input v-model:value="recommendedQuestions[i]" placeholder="推荐问题" size="small" />
                <button class="btn-icon-sm danger" @click="recommendedQuestions.splice(i, 1)">
                  <CloseOutlined />
                </button>
              </div>
              <button v-if="recommendedQuestions.length < 3" class="btn-add-question" @click="recommendedQuestions.push('')">
                <PlusOutlined /> 添加推荐问题
              </button>
            </div>
          </a-form-item>
        </a-form>
      </div>

      <!-- 模型参数调优 -->
      <div class="panel">
        <div class="panel-header">
          <h3>模型参数调优</h3>
          <div style="display: flex; align-items: center; gap: 8px;">
            <button class="btn-ai-sm" @click="restoreDefaults" :disabled="!agentConfig.providerId">
              <UndoOutlined /> 恢复默认
            </button>
            <span class="panel-tip">根据提供商动态显示可用配置</span>
          </div>
        </div>
        <a-form :model="agentConfig" :label-col="{ span: 6 }">
          <a-form-item label="提供商">
            <a-select v-model:value="agentConfig.providerId" placeholder="选择提供商" style="width: 100%" @change="onProviderChange">
              <a-select-option v-for="p in providerList" :key="p.id" :value="p.id">
                {{ p.name }} ({{ p.type?.code || p.type }})
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="模型">
            <a-select
              v-model:value="agentConfig.modelId"
              placeholder="选择模型"
              show-search
              :filter-option="false"
              @search="val => modelSearchText = val"
              style="width: 100%"
              allow-clear
            >
              <a-select-option v-for="m in filteredModels" :key="m.modelId" :value="m.modelId">
                {{ m.name || m.modelId }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item v-for="field in configFields.filter(f => f.key !== 'modelId')" :key="field.key">
            <template #label>
              <div class="config-label-wrap">
                <span class="config-label">{{ field.label }}</span>
                <span class="config-key">{{ field.key }}</span>
              </div>
            </template>
            <!-- select -->
            <a-select v-if="field.type === 'select'" v-model:value="agentConfig[field.key]" placeholder="请选择" style="width: 100%">
              <a-select-option v-for="opt in field.options" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </a-select-option>
            </a-select>
            <!-- slider -->
            <div v-else-if="field.type === 'slider'" class="param-row">
              <a-slider v-model:value="agentConfig[field.key]" :min="field.min" :max="field.max" :step="field.step" style="flex: 1" />
              <span class="param-value">{{ agentConfig[field.key] }}</span>
            </div>
            <!-- number -->
            <a-input-number v-else-if="field.type === 'number'" v-model:value="agentConfig[field.key]" :min="field.min" :max="field.max" :step="field.step" style="width: 100%" />
            <!-- text -->
            <a-input v-else v-model:value="agentConfig[field.key]" placeholder="请输入" />
            <div v-if="field.hint" class="param-hint">{{ field.hint }}</div>
          </a-form-item>
        </a-form>
      </div>

      <!-- 知识库绑定 -->
      <div class="panel full-width">
        <div class="panel-header">
          <h3>知识库绑定</h3>
          <span class="panel-tip">每个 Agent 最多绑定 3 个知识库，绑定后可基于知识库内容回答问题</span>
        </div>
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div v-if="selectedKnowledge.length === 0" class="empty-tip">
              暂未绑定知识库，请从下方列表选择
            </div>
            <div v-for="k in selectedKnowledge" :key="k.id" class="knowledge-tag">
              <span>{{ k.name }}</span>
              <button class="tag-remove" @click="removeKnowledge(k.id)">
                <CloseOutlined />
              </button>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可用知识库</span>
              <a-input
                v-model:value="searchText"
                placeholder="搜索知识库..."
                size="small"
                style="width: 200px"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="k in filteredKnowledgeList"
                :key="k.id"
                class="knowledge-item"
                :class="{ selected: selectedKnowledgeIds.has(k.id) }"
                @click="toggleKnowledge(k)"
              >
                <div class="item-icon">K</div>
                <div class="item-info">
                  <div class="item-name">{{ k.name }}</div>
                  <div class="item-desc">{{ k.description || '暂无描述' }}</div>
                </div>
                <div class="item-check" v-if="selectedKnowledgeIds.has(k.id)">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredKnowledgeList.length === 0" class="empty-tip">
                暂无可用知识库
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, SaveOutlined, CloseOutlined, SearchOutlined, CheckOutlined, MessageOutlined, PlusOutlined, ThunderboltOutlined, UploadOutlined, LoadingOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAgentDetail, updateAgent, updateAgentKnowledge, generateAgentPrompt, generateAgentQuestions, uploadAgentAvatar } from '../api/agent'
import { getModelProviders, getProviderConfigFields } from '../api/modelProvider'
import { getModelsByProvider } from '../api/model'
import { getKnowledgeList } from '../api/knowledge'
const route = useRoute()
const router = useRouter()
const agentId = route.params.id
const avatarInputRef = ref(null)

const agent = reactive({
  id: null,
  name: '',
  description: '',
  systemPrompt: '',
  agentType: 'CHAT',
  icon: '',
})

// 模型配置（存储在 config JSONB 中）
const agentConfig = reactive({
  providerId: null,
})

const providerList = ref([])
const configFields = ref([])
const modelList = ref([])
const modelSearchText = ref('')
const selectedKnowledgeIds = ref(new Set())
const knowledgeList = ref([])
const searchText = ref('')
const saving = ref(false)
const recommendedQuestions = ref([])
const generatingPrompt = ref(false)
const generatingQuestions = ref(false)
const avatarUploading = ref(false)

const avatarUrl = computed(() => {
  if (!agent.avatar) return ''
  return `http://localhost:9000/lightbot/${agent.avatar}`
})

const filteredModels = computed(() => {
  if (!modelSearchText.value) return modelList.value
  const keyword = modelSearchText.value.toLowerCase()
  return modelList.value.filter(m =>
    m.modelId?.toLowerCase().includes(keyword) ||
    m.name?.toLowerCase().includes(keyword)
  )
})

const selectedKnowledge = computed(() => {
  return knowledgeList.value.filter(k => selectedKnowledgeIds.value.has(k.id))
})

const filteredKnowledgeList = computed(() => {
  if (!searchText.value) return knowledgeList.value
  const keyword = searchText.value.toLowerCase()
  return knowledgeList.value.filter(k =>
    k.name?.toLowerCase().includes(keyword) ||
    k.description?.toLowerCase().includes(keyword)
  )
})

async function loadProviders() {
  try {
    const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
    providerList.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

async function loadConfigFields(providerId) {
  if (!providerId) return
  try {
    const res = await getProviderConfigFields(providerId)
    configFields.value = res.data || []
    // 为缺失的字段设置默认值
    for (const field of configFields.value) {
      if (agentConfig[field.key] === undefined && field.defaultValue !== undefined) {
        agentConfig[field.key] = field.defaultValue
      }
    }
  } catch (e) {
    configFields.value = []
  }
}

async function loadModels(providerId) {
  if (!providerId) {
    modelList.value = []
    return
  }
  try {
    const res = await getModelsByProvider(providerId)
    // 只显示对话模型
    modelList.value = (res.data || []).filter(m => {
      const type = m.type?.code || m.type
      return type === 'llm'
    })
  } catch (e) {
    modelList.value = []
  }
}

async function onProviderChange(providerId) {
  // 切换提供商时，清空模型和旧配置项
  agentConfig.modelId = undefined
  for (const key of Object.keys(agentConfig)) {
    if (key !== 'providerId' && key !== 'modelId') {
      delete agentConfig[key]
    }
  }
  modelSearchText.value = ''
  await Promise.all([loadConfigFields(providerId), loadModels(providerId)])
}

function restoreDefaults() {
  for (const field of configFields.value) {
    // 恢复默认不切换模型
    if (field.key === 'modelId') continue
    if (field.defaultValue !== undefined) {
      agentConfig[field.key] = field.defaultValue
    }
  }
  message.success('已恢复默认配置')
}

async function loadAgent() {
  try {
    const res = await getAgentDetail(agentId)
    const { agent: agentData, knowledgeIds } = res.data

    // 分离基本信息和配置
    const { config, agentType, ...basicInfo } = agentData
    Object.assign(agent, basicInfo)
    if (agentType?.code) {
      agent.agentType = agentType.code
    } else if (agentType) {
      agent.agentType = agentType
    }

    // 解析 config JSONB
    if (config) {
      try {
        const parsed = typeof config === 'string' ? JSON.parse(config) : config
        Object.assign(agentConfig, parsed)
      } catch (e) {
        // ignore
      }
    }

    // 解析推荐问题
    if (agentData.recommendedQuestions) {
      try {
        recommendedQuestions.value = typeof agentData.recommendedQuestions === 'string'
          ? JSON.parse(agentData.recommendedQuestions)
          : agentData.recommendedQuestions
      } catch { recommendedQuestions.value = [] }
    }

    // 加载提供商列表、配置字段和模型列表
    await loadProviders()
    await Promise.all([loadConfigFields(agentConfig.providerId), loadModels(agentConfig.providerId)])

    selectedKnowledgeIds.value = new Set((knowledgeIds || []).map(String))
  } catch (e) {
    // interceptor已处理错误提示
  }
}

async function loadKnowledgeList() {
  try {
    const res = await getKnowledgeList({ pageNum: 1, pageSize: 100 })
    knowledgeList.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

function toggleKnowledge(k) {
  const ids = new Set(selectedKnowledgeIds.value)
  if (ids.has(k.id)) {
    ids.delete(k.id)
  } else {
    if (ids.size >= 3) {
      message.warning('每个 Agent 最多绑定 3 个知识库')
      return
    }
    ids.add(k.id)
  }
  selectedKnowledgeIds.value = ids
}

function removeKnowledge(id) {
  const ids = new Set(selectedKnowledgeIds.value)
  ids.delete(id)
  selectedKnowledgeIds.value = ids
}

async function handleGeneratePrompt() {
  generatingPrompt.value = true
  try {
    const res = await generateAgentPrompt(agentId)
    agent.systemPrompt = res.data
    message.success('提示词生成成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    generatingPrompt.value = false
  }
}

async function handleGenerateQuestions() {
  generatingQuestions.value = true
  try {
    const res = await generateAgentQuestions(agentId)
    const questions = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    recommendedQuestions.value = questions
    message.success('推荐问题生成成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    generatingQuestions.value = false
  }
}

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

async function onAvatarFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  avatarUploading.value = true
  try {
    const res = await uploadAgentAvatar(agentId, file)
    agent.avatar = res.data
    message.success('头像上传成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    avatarUploading.value = false
    // 清空 input 以允许重新选择同一文件
    if (avatarInputRef.value) avatarInputRef.value.value = ''
  }
}

async function handleSave() {
  if (!agent.name?.trim()) {
    message.warning('请输入 Agent 名称')
    return
  }
  if (!agent.agentType) {
    message.warning('请选择类型')
    return
  }
  if (!agentConfig.providerId) {
    message.warning('请选择模型提供商')
    return
  }
  if (!agentConfig.modelId) {
    message.warning('请选择模型')
    return
  }

  // 2. 过滤空的推荐问题并校验
  const questions = recommendedQuestions.value.filter(q => q && q.trim())
  if (questions.length > 3) {
    message.warning('推荐问题最多3个')
    return
  }
  for (const q of questions) {
    if (q.length > 30) {
      message.warning('每个推荐问题不超过30字')
      return
    }
  }

  // 4. 校验知识库数量
  if (selectedKnowledgeIds.value.size > 3) {
    message.warning('每个 Agent 最多绑定 3 个知识库')
    return
  }

  saving.value = true
  try {
    // 1. 构建 config JSONB（包含 provider + 所有配置项）
    const configObj = { ...agentConfig }
    const configStr = JSON.stringify(configObj)

    // 2. 更新 Agent
    await updateAgent({
      ...agent,
      agentType: agent.agentType?.code || agent.agentType,
      config: configStr,
      recommendedQuestions: JSON.stringify(questions),
    })

    // 3. 更新知识库绑定
    await updateAgentKnowledge(agentId, Array.from(selectedKnowledgeIds.value))

    message.success('保存成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    saving.value = false
  }
}

function startChat() {
  router.push({ path: '/chat', query: { agentId: agentId } })
}

onMounted(() => {
  loadAgent()
  loadKnowledgeList()
})
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
.btn-back:hover {
  color: #0070f3;
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
.btn-primary:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #fff;
  color: #171717;
  border: 1px solid #d4d4d8;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-icon-sm {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  font-size: 12px;
}
.btn-icon-sm:hover {
  background: #f5f5f5;
}
.btn-icon-sm.danger:hover {
  color: #ee0000;
  background: #f7d4d6;
}
.prompt-wrapper {
  position: relative;
}
.btn-ai-icon {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(0, 112, 243, 0.1);
  border-radius: 6px;
  color: #0070f3;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.15s;
  z-index: 1;
}
.btn-ai-icon:hover:not(:disabled) {
  background: rgba(0, 112, 243, 0.2);
}
.btn-ai-icon:disabled {
  color: #a1a1aa;
  background: #f5f5f5;
  cursor: not-allowed;
}
.questions-header {
  margin-bottom: 8px;
}
.btn-ai-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  font-size: 12px;
  color: #0070f3;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-ai-sm:hover:not(:disabled) {
  border-color: #0070f3;
  background: #f0f7ff;
}
.btn-ai-sm:disabled {
  color: #a1a1aa;
  border-color: #e4e4e7;
  cursor: not-allowed;
}
.config-label-wrap {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.config-label {
  font-size: 14px;
}
.config-key {
  font-size: 11px;
  color: #a1a1aa;
  font-weight: normal;
}
.recommended-questions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.question-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.btn-add-question {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: none;
  border: 1px dashed #d4d4d8;
  border-radius: 6px;
  color: #71717a;
  font-size: 13px;
  cursor: pointer;
  align-self: flex-start;
}
.btn-add-question:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-primary:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
}
.panel.full-width {
  grid-column: 1 / -1;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.panel-tip {
  font-size: 12px;
  color: #a1a1aa;
}

.param-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.param-value {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
  min-width: 40px;
  text-align: right;
}
.param-hint {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
}

.knowledge-bind {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.selected-knowledge {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 40px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}
.knowledge-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 100px;
  font-size: 13px;
  color: #1e40af;
}
.tag-remove {
  background: none;
  border: none;
  color: #60a5fa;
  cursor: pointer;
  padding: 0;
  font-size: 12px;
  display: flex;
  align-items: center;
}
.tag-remove:hover {
  color: #ef4444;
}
.knowledge-list {
  border: 1px solid #ebebeb;
  border-radius: 8px;
  overflow: hidden;
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #ebebeb;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
.list-body {
  max-height: 300px;
  overflow-y: auto;
}
.knowledge-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.knowledge-item:hover {
  background: #f9fafb;
}
.knowledge-item.selected {
  background: #eff6ff;
}
.item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #007cf0, #00dfd8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}
.item-info {
  flex: 1;
  min-width: 0;
}
.item-name {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
}
.item-desc {
  font-size: 12px;
  color: #71717a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-check {
  color: #0070f3;
  font-size: 16px;
}
.empty-tip {
  text-align: center;
  padding: 24px;
  color: #a1a1aa;
  font-size: 13px;
}

/* 头像上传 */
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}
.avatar-preview.has-avatar {
  background: #f4f4f5;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  font-size: 28px;
  font-weight: 700;
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: #fff;
  font-size: 20px;
}
.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}
.avatar-tip {
  font-size: 12px;
  color: #a1a1aa;
}
</style>
