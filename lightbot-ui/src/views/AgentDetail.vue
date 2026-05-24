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
          <a-form-item label="智能体ID">
            <div class="id-field">
              <span class="id-value">{{ agent.id || '新建后生成' }}</span>
            </div>
          </a-form-item>
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
          <a-form-item label="上下文条数">
            <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
              <a-input-number
                v-model:value="agentConfig.maxContextMessages"
                :min="1"
                :max="50"
                :step="5"
                placeholder="默认20"
                style="flex: 1"
              />
              <a-tooltip
                title="与模型对话时最多携带的历史消息条数。条数越多上下文越完整，但消耗的Token也越多"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: #a1a1aa; cursor: help;" />
              </a-tooltip>
            </div>
            <div class="param-hint">与模型对话时最多携带的历史消息条数，默认20条</div>
          </a-form-item>
          <a-form-item label="上下文摘要">
            <div style="display: flex; align-items: center; gap: 8px;">
              <a-switch v-model:checked="agentConfig.enableSummary" />
              <span class="tool-option-value">{{ agentConfig.enableSummary ? '已启用' : '未启用' }}</span>
              <a-tooltip
                title="当上下文大小超过阈值时，自动对早期对话进行摘要，以优化Token使用"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: #a1a1aa; cursor: help;" />
              </a-tooltip>
            </div>
          </a-form-item>
          <a-form-item v-if="agentConfig.enableSummary" label="摘要触发阈值">
            <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
              <a-input-number
                v-model:value="agentConfig.summaryThresholdKb"
                :min="10"
                :max="1000"
                :step="10"
                placeholder="100"
                style="flex: 1"
              />
              <span style="font-size: 13px; color: #71717a; white-space: nowrap;">KB</span>
              <a-tooltip
                title="当上下文大小超过该值时，启用摘要功能以优化上下文使用。单位为 KB，默认值为 100KB"
                overlay-class-name="no-flip-tooltip"
                :overlay-style="{ maxWidth: '320px' }"
                placement="topLeft"
              >
                <QuestionCircleOutlined style="font-size: 14px; color: #a1a1aa; cursor: help;" />
              </a-tooltip>
            </div>
            <div class="param-hint">上下文超过该大小时自动摘要，默认 100KB</div>
          </a-form-item>
        </a-form>
      </div>
    </div>

    <!-- 绑定配置 Tabs -->
    <a-tabs v-model:activeKey="activeTab" @change="onTabChange" class="binding-tabs">
      <!-- 工具绑定 -->
      <a-tab-pane key="tools" tab="工具绑定">
        <div class="tool-options-bar">
          <div class="tool-option-item">
            <span class="tool-option-label">工具调用模式</span>
            <a-switch v-model:checked="agentConfig.asyncToolCalls" size="default" />
            <span class="tool-option-value">{{ agentConfig.asyncToolCalls ? '异步（并行）' : '串行（逐个）' }}</span>
            <a-tooltip
              title="串行模式：每次只调用一个工具，等待结果后再决定是否继续调用；异步模式：AI可同时调用多个工具，提升效率但可能消耗更多Token"
              overlay-class-name="no-flip-tooltip"
              :overlay-style="{ maxWidth: '320px' }"
              placement="topLeft"
            >
              <QuestionCircleOutlined class="tool-option-help" />
            </a-tooltip>
          </div>
        </div>
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedTools.length }} 个工具</span>
              <button v-if="selectedTools.length > 0" class="btn-clear" @click="clearSelectedTools">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div v-if="selectedTools.length === 0" class="empty-tip">
              暂未绑定工具，请从下方列表选择
            </div>
            <div v-for="t in selectedTools" :key="t.id" class="knowledge-tag tool-tag">
              <ToolOutlined />
              <span>{{ t.displayName || t.name }}</span>
              <span class="tool-type-badge">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
              <button class="tag-remove" @click="removeTool(t.id)">
                <CloseOutlined />
              </button>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可选工具</span>
              <div class="list-header-actions">
                <SystemToolDrawer placement="bottomRight" />
                <a-input
                  v-model:value="toolSearchText"
                  placeholder="搜索工具..."
                  size="small"
                  style="width: 200px"
                >
                  <template #prefix><SearchOutlined /></template>
                </a-input>
              </div>
            </div>
            <div class="type-filter-bar">
              <button
                v-for="opt in toolTypeOptions"
                :key="opt.value"
                class="type-filter-btn"
                :class="{ active: toolTypeFilter === opt.value }"
                @click="toolTypeFilter = opt.value; loadToolList(opt.value || undefined)"
              >{{ opt.label }}</button>
            </div>
            <div class="list-body">
              <div
                v-for="t in filteredToolList"
                :key="t.name"
                class="knowledge-item"
                :class="{ selected: selectedToolIds.has(t.id) }"
                @click="toggleTool(t)"
              >
                <div class="item-icon tool-icon-bg">
                  <ToolOutlined />
                </div>
                <div class="item-info">
                  <div class="item-name">
                    {{ t.displayName || t.name }}
                    <span class="tool-type-badge">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
                  </div>
                  <div class="item-desc">{{ t.description || '暂无描述' }}</div>
                </div>
                <div class="item-check" v-if="selectedToolIds.has(t.id)">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredToolList.length === 0" class="empty-tip">
                暂无可用工具
              </div>
            </div>
          </div>
        </div>
      </a-tab-pane>

      <!-- MCP 工具 -->
      <a-tab-pane key="mcp" tab="MCP Server">
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedMcpServers.length }} 个 MCP Server</span>
              <button v-if="selectedMcpServers.length > 0" class="btn-clear" @click="clearSelectedMcpServers">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div v-if="selectedMcpServers.length === 0" class="empty-tip">
              暂未绑定 MCP Server，请从下方列表选择
            </div>
            <div v-for="s in selectedMcpServers" :key="s.id" class="knowledge-tag mcp-tag">
              <ApiOutlined />
              <span>{{ s.name }}</span>
              <button class="tag-remove" @click="removeMcpServer(s.id)">
                <CloseOutlined />
              </button>
            </div>
          </div>
          <div class="knowledge-list">
            <div class="list-header">
              <span>可用 MCP Server</span>
              <a-input
                v-model:value="mcpSearchText"
                placeholder="搜索 MCP Server..."
                size="small"
                style="width: 200px"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="s in filteredMcpServerList"
                :key="s.id"
                class="knowledge-item"
                :class="{ selected: selectedMcpServerIds.has(s.id) }"
                @click="toggleMcpServer(s)"
              >
                <div class="item-icon mcp-icon-bg">
                  <ApiOutlined />
                </div>
                <div class="item-info">
                  <div class="item-name">{{ s.name }}</div>
                  <div class="item-desc">{{ s.description || '暂无描述' }}</div>
                </div>
                <div class="item-check" v-if="selectedMcpServerIds.has(s.id)">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredMcpServerList.length === 0" class="empty-tip">
                暂无可用 MCP Server
              </div>
            </div>
          </div>
        </div>
      </a-tab-pane>

      <!-- 知识库绑定 -->
      <a-tab-pane key="knowledge" tab="知识库">
        <div class="knowledge-bind">
          <div class="selected-knowledge">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedKnowledge.length }} 个知识库</span>
              <button v-if="selectedKnowledge.length > 0" class="btn-clear" @click="clearSelectedKnowledge">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-knowledge-tags">
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
                <div class="item-icon knowledge-icon">
                  <BookOutlined />
                </div>
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
      </a-tab-pane>

      <!-- SubAgent 绑定 -->
      <a-tab-pane key="subagents" tab="SubAgents">
        <div class="subagent-bind">
          <div class="selected-subagents">
            <div class="selected-header">
              <span class="selected-label">已绑定 {{ selectedSubAgents.length }} 个 SubAgent</span>
              <button v-if="selectedSubAgents.length > 0" class="btn-clear" @click="clearSelectedSubAgents">
                <DeleteOutlined /> 清空
              </button>
            </div>
            <div class="selected-subagents-tags">
              <div v-if="selectedSubAgents.length === 0" class="empty-tip">
                暂未绑定 SubAgent，从下方列表选择
              </div>
              <div v-for="s in selectedSubAgents" :key="s.id" class="subagent-tag">
                <div class="tag-info">
                  <span class="tag-name">{{ s.displayName }}</span>
                  <span class="tag-desc">{{ s.description || '暂无描述' }}</span>
                </div>
                <button class="tag-remove" @click="removeSubAgent(s.id)">
                  <CloseOutlined />
                </button>
              </div>
            </div>
          </div>
          <div class="subagent-list">
            <div class="list-header">
              <span>可用的 SubAgent</span>
              <a-input
                v-model:value="subAgentSearchText"
                placeholder="搜索 SubAgent..."
                size="small"
                style="width: 200px"
              >
                <template #prefix><SearchOutlined /></template>
              </a-input>
            </div>
            <div class="list-body">
              <div
                v-for="s in filteredSubAgentList"
                :key="s.id"
                class="subagent-item"
                :class="{ selected: selectedSubAgentIds.has(s.id) }"
                @click="toggleSubAgent(s)"
              >
                <div class="item-icon subagent-icon">
                  <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
                  <RobotOutlined />
                </div>
                <div class="item-info">
                  <div class="item-name">{{ s.displayName }}</div>
                  <div class="item-desc">{{ s.description || '暂无描述' }}</div>
                  <div class="item-tools" v-if="s.tools && JSON.parse(s.tools).length > 0">
                    工具: {{ JSON.parse(s.tools).join(', ') }}
                  </div>
                </div>
                <div class="item-check" v-if="selectedSubAgentIds.has(s.id)">
                  <CheckOutlined />
                </div>
              </div>
              <div v-if="filteredSubAgentList.length === 0" class="empty-tip">
                暂无可用 SubAgent
              </div>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, SaveOutlined, CloseOutlined, SearchOutlined, CheckOutlined, MessageOutlined, PlusOutlined, ThunderboltOutlined, UploadOutlined, LoadingOutlined, UndoOutlined, ToolOutlined, QuestionCircleOutlined, ApiOutlined, DeleteOutlined, BookOutlined, RobotOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAgentDetail, updateAgent, updateAgentKnowledge, updateAgentTools, getAgentToolDetails, generateAgentPrompt, generateAgentQuestions, uploadAgentAvatar, updateAgentMcpServers, updateAgentSubAgents } from '../api/agent'
import { getTools } from '../api/tool'
import { getToolTypes } from '../api/enum'
import { getModelProviders, getProviderConfigFields } from '../api/modelProvider'
import { getModelsByProvider } from '../api/model'
import { getKnowledgeList } from '../api/knowledge'
import { getMcpServers } from '../api/mcp'
import { getEnabledSubAgents } from '../api/subagent'
import SystemToolDrawer from '../components/SystemToolDrawer.vue'
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
const selectedToolIds = ref(new Set())
const toolList = ref([])
const toolSearchText = ref('')
const toolTypeFilter = ref('')
const toolTypeList = ref([])
const toolTypeLabels = { builtin: '内置', custom: '自定义', api: 'API调用', mcp: 'MCP协议' }
const toolTypeOptions = computed(() => {
  const options = [{ value: '', label: '全部' }]
  for (const t of toolTypeList.value) {
    options.push({ value: t.value, label: t.label })
  }
  return options
})
const saving = ref(false)
const activeTab = ref('tools')

// MCP Server 绑定
const selectedMcpServerIds = ref(new Set())
const mcpServerList = ref([])
const mcpSearchText = ref('')

// SubAgent 绑定
const selectedSubAgentIds = ref(new Set())
const subAgentList = ref([])
const subAgentSearchText = ref('')
const recommendedQuestions = ref([])
const generatingPrompt = ref(false)
const generatingQuestions = ref(false)
const avatarUploading = ref(false)

const avatarUrl = computed(() => {
  if (!agent.avatar) return ''
  return agent.avatar
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

const selectedTools = computed(() => {
  return toolList.value.filter(t => selectedToolIds.value.has(t.id))
})

const filteredToolList = computed(() => {
  if (!toolSearchText.value) return toolList.value
  const keyword = toolSearchText.value.toLowerCase()
  return toolList.value.filter(t =>
    t.name?.toLowerCase().includes(keyword) ||
    t.displayName?.toLowerCase().includes(keyword) ||
    t.description?.toLowerCase().includes(keyword)
  )
})

const selectedMcpServers = computed(() => {
  return mcpServerList.value.filter(s => selectedMcpServerIds.value.has(s.id))
})

const filteredMcpServerList = computed(() => {
  if (!mcpSearchText.value) return mcpServerList.value
  const keyword = mcpSearchText.value.toLowerCase()
  return mcpServerList.value.filter(s =>
    s.name?.toLowerCase().includes(keyword) ||
    s.description?.toLowerCase().includes(keyword)
  )
})

const selectedSubAgents = computed(() => {
  return subAgentList.value.filter(s => selectedSubAgentIds.value.has(s.id))
})

const filteredSubAgentList = computed(() => {
  if (!subAgentSearchText.value) return subAgentList.value
  const keyword = subAgentSearchText.value.toLowerCase()
  return subAgentList.value.filter(s =>
    s.name?.toLowerCase().includes(keyword) ||
    s.displayName?.toLowerCase().includes(keyword) ||
    s.description?.toLowerCase().includes(keyword)
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
    const { agent: agentData, knowledgeIds, mcpServerIds, subAgentIds } = res.data

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
    selectedSubAgentIds.value = new Set((subAgentIds || []).map(String))

    // 加载绑定的工具详情
    const toolRes = await getAgentToolDetails(agentId)
    const boundTools = toolRes.data || []
    selectedToolIds.value = new Set(boundTools.map(t => t.id))

    // MCP Server IDs（从 detail 接口获取）
    selectedMcpServerIds.value = new Set((mcpServerIds || []).map(String))
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

async function loadToolTypes() {
  try {
    const res = await getToolTypes()
    toolTypeList.value = res.data || []
  } catch (e) {
    console.error('[AgentDetail] 加载工具类型枚举失败:', e)
  }
}

async function loadToolList(toolType) {
  try {
    const params = { pageNum: 1, pageSize: 100, isSystem: false }
    if (toolType) params.toolType = toolType
    const res = await getTools(params)
    toolList.value = res.data?.records || []
  } catch (e) {
    console.error('[AgentDetail] 加载工具列表失败:', e)
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

function toggleTool(t) {
  const ids = new Set(selectedToolIds.value)
  if (ids.has(t.id)) {
    ids.delete(t.id)
  } else {
    ids.add(t.id)
  }
  selectedToolIds.value = ids
}

function removeTool(id) {
  const ids = new Set(selectedToolIds.value)
  ids.delete(id)
  selectedToolIds.value = ids
}

function toggleMcpServer(s) {
  const ids = new Set(selectedMcpServerIds.value)
  if (ids.has(s.id)) {
    ids.delete(s.id)
  } else {
    ids.add(s.id)
  }
  selectedMcpServerIds.value = ids
}

function removeMcpServer(id) {
  const ids = new Set(selectedMcpServerIds.value)
  ids.delete(id)
  selectedMcpServerIds.value = ids
}

// SubAgent 操作
function toggleSubAgent(s) {
  const ids = new Set(selectedSubAgentIds.value)
  if (ids.has(s.id)) {
    ids.delete(s.id)
  } else {
    ids.add(s.id)
  }
  selectedSubAgentIds.value = ids
}

function removeSubAgent(id) {
  const ids = new Set(selectedSubAgentIds.value)
  ids.delete(id)
  selectedSubAgentIds.value = ids
}

function clearSelectedSubAgents() {
  selectedSubAgentIds.value = new Set()
}

async function loadSubAgentList() {
  try {
    const res = await getEnabledSubAgents()
    subAgentList.value = res.data || []
  } catch (e) {
    console.error('[AgentDetail] 加载SubAgent列表失败:', e)
  }
}

// Tab 切换刷新
async function onTabChange(tab) {
  if (tab === 'tools') {
    await Promise.all([loadToolTypes(), loadToolList(toolTypeFilter.value || undefined)])
  } else if (tab === 'mcp') {
    await loadMcpServerList()
  } else if (tab === 'knowledge') {
    await loadKnowledgeList()
  } else if (tab === 'subagents') {
    await loadSubAgentList()
  }
}

async function loadMcpServerList() {
  try {
    const res = await getMcpServers({ pageNum: 1, pageSize: 100 })
    mcpServerList.value = res.data?.records || []
  } catch (e) {
    console.error('[AgentDetail] 加载MCP Server列表失败:', e)
  }
}

function clearSelectedTools() {
  selectedToolIds.value = new Set()
}

function clearSelectedMcpServers() {
  selectedMcpServerIds.value = new Set()
}

function clearSelectedKnowledge() {
  selectedKnowledgeIds.value = new Set()
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

    // 4. 更新工具绑定
    await updateAgentTools(agentId, Array.from(selectedToolIds.value))

    // 5. 更新 MCP Server 绑定
    await updateAgentMcpServers(agentId, Array.from(selectedMcpServerIds.value))

    // 6. 更新 SubAgent 绑定
    await updateAgentSubAgents(agentId, Array.from(selectedSubAgentIds.value))

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

onMounted(async () => {
  await loadAgent()
  // 加载初始 tab（工具绑定）的数据
  await Promise.all([loadToolTypes(), loadToolList()])
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
.id-field {
  display: flex;
  align-items: center;
  gap: 12px;
}
.id-value {
  font-size: 14px;
  color: #171717;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  background: #f5f5f5;
  padding: 4px 12px;
  border-radius: 4px;
}
.id-hint {
  font-size: 12px;
  color: #a1a1aa;
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
/* tab 内容不再需要 grid，panel 宽度自适应 */
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

.tool-options-bar {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
}
.tool-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-option-label {
  font-size: 13px;
  color: #52525b;
  font-weight: 500;
}
.tool-option-value {
  font-size: 13px;
  color: #171717;
}
.tool-option-help {
  font-size: 14px;
  color: #a1a1aa;
  cursor: help;
  transition: color 0.2s;
}
.tool-option-help:hover {
  color: #1890ff;
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
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.selected-knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
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
.list-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  position: relative;
}
.knowledge-icon {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
}
.subagent-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}
.builtin-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: #0070f3;
  color: #fff;
  border-radius: 4px;
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
  margin-top: 4px;
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

/* 工具绑定样式 */
.tool-tag {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}
.tool-type-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 100px;
  background: rgba(0, 0, 0, 0.06);
  color: #71717a;
}
.tool-icon-bg {
  background: linear-gradient(135deg, #f59e0b, #ef4444) !important;
}
.mcp-tag {
  background: #fdf4ff;
  border-color: #e9d5ff;
  color: #7c3aed;
}
.mcp-icon-bg {
  background: linear-gradient(135deg, #7c3aed, #a855f7) !important;
}
.type-filter-bar {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.type-filter-btn {
  padding: 2px 10px;
  border: 1px solid #e4e4e7;
  border-radius: 100px;
  background: #fff;
  font-size: 12px;
  color: #71717a;
  cursor: pointer;
  transition: all 0.15s;
}
.type-filter-btn:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.type-filter-btn.active {
  background: #0070f3;
  border-color: #0070f3;
  color: #fff;
}
.agent-tabs {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 0 20px 20px;
}
.binding-tabs {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 16px 20px 20px;
  margin-top: 16px;
}

/* 清空按钮样式 */
.selected-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.selected-label {
  font-size: 13px;
  color: #71717a;
  font-weight: 500;
}
.btn-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: transparent;
  border: 1px solid #e4e4e7;
  border-radius: 4px;
  font-size: 12px;
  color: #a1a1aa;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-clear:hover {
  border-color: #ef4444;
  color: #ef4444;
  background: #fef2f2;
}

/* SubAgent 绑定 */
.subagent-bind {
  display: flex;
  gap: 24px;
}
.selected-subagents {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.selected-subagents-tags {
  margin-top: 12px;
}
.subagent-tag {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  margin-bottom: 8px;
}
.subagent-tag .tag-info {
  flex: 1;
  min-width: 0;
}
.subagent-tag .tag-name {
  font-size: 14px;
  font-weight: 500;
  color: #171717;
}
.subagent-tag .tag-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #71717a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.subagent-tag .tag-remove {
  padding: 4px;
  background: transparent;
  border: none;
  color: #a1a1aa;
  cursor: pointer;
  border-radius: 4px;
}
.subagent-tag .tag-remove:hover {
  background: #fee2e2;
  color: #ef4444;
}
.subagent-list {
  flex: 1;
  min-width: 0;
}
.subagent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.subagent-item:hover {
  background: #f9fafb;
}
.subagent-item.selected {
  background: #eff6ff;
}
.subagent-item .builtin-badge {
  font-size: 10px;
  padding: 1px 4px;
  background: #0070f3;
  color: #fff;
  border-radius: 3px;
  margin-right: 4px;
}
.subagent-item .item-tools {
  margin-top: 4px;
  font-size: 11px;
  color: #0070f3;
}
</style>
