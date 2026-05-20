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
      <button class="btn-primary" @click="handleSave" :disabled="saving">
        <SaveOutlined /> 保存配置
      </button>
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
          <a-form-item label="描述">
            <a-textarea v-model:value="agent.description" :rows="2" placeholder="Agent 描述" />
          </a-form-item>
          <a-form-item label="系统提示词">
            <a-textarea v-model:value="agent.systemPrompt" :rows="6" placeholder="定义 Agent 的行为和角色..." />
          </a-form-item>
          <a-form-item label="类型">
            <a-select v-model:value="agent.agentType" style="width: 100%">
              <a-select-option value="CHAT">对话型</a-select-option>
              <a-select-option value="TASK">任务型</a-select-option>
              <a-select-option value="WORKFLOW">工作流</a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>

      <!-- 模型参数调优 -->
      <div class="panel">
        <div class="panel-header">
          <h3>模型参数调优</h3>
          <span class="panel-tip">根据提供商动态显示可用配置</span>
        </div>
        <a-form :model="agentConfig" :label-col="{ span: 8 }">
          <a-form-item label="提供商">
            <a-select v-model:value="agentConfig.providerId" placeholder="选择提供商" style="width: 100%" @change="onProviderChange">
              <a-select-option v-for="p in providerList" :key="p.id" :value="p.id">
                {{ p.name }} ({{ p.type?.code || p.type }})
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item v-for="field in configFields" :key="field.key" :label="field.label">
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
          <span class="panel-tip">绑定知识库后，Agent 可基于知识库内容回答问题</span>
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
import { ArrowLeftOutlined, SaveOutlined, CloseOutlined, SearchOutlined, CheckOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAgentDetail, updateAgent, updateAgentKnowledge } from '../api/agent'
import { getModelProviders, getProviderConfigFields } from '../api/modelProvider'
import { getKnowledgeList } from '../api/knowledge'

const route = useRoute()
const router = useRouter()
const agentId = route.params.id

const agent = reactive({
  id: null,
  name: '',
  description: '',
  systemPrompt: '',
  agentType: 'CHAT',
})

// 模型配置（存储在 config JSONB 中）
const agentConfig = reactive({
  providerId: null,
})

const providerList = ref([])
const configFields = ref([])
const selectedKnowledgeIds = ref(new Set())
const knowledgeList = ref([])
const searchText = ref('')
const saving = ref(false)

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

async function onProviderChange(providerId) {
  // 切换提供商时，清除非 providerId 的配置项
  const keepKeys = new Set(['providerId', ...configFields.value.map(f => f.key)])
  for (const key of Object.keys(agentConfig)) {
    if (!keepKeys.has(key) && key !== 'providerId') {
      delete agentConfig[key]
    }
  }
  await loadConfigFields(providerId)
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

    // 加载提供商列表和配置字段
    await loadProviders()
    await loadConfigFields(agentConfig.providerId)

    selectedKnowledgeIds.value = new Set(knowledgeIds || [])
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
    ids.add(k.id)
  }
  selectedKnowledgeIds.value = ids
}

function removeKnowledge(id) {
  const ids = new Set(selectedKnowledgeIds.value)
  ids.delete(id)
  selectedKnowledgeIds.value = ids
}

async function handleSave() {
  if (!agent.name?.trim()) {
    message.warning('请输入 Agent 名称')
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
</style>
