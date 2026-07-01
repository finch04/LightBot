<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/app/prompts')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">Playground
          <a-popover trigger="click" overlay-class-name="playground-help-popover">
            <template #content>
              <div class="playground-help">
                <p><strong>Playground 是什么？</strong></p>
                <p>Playground 是 Prompt 的实时调试环境，用于验证模板效果和模型表现。</p>
                <p><strong>使用方式：</strong></p>
                <ul>
                  <li>在编辑器中编写 Prompt 模板，使用 <code v-pre>{{变量名}}</code> 定义变量</li>
                  <li>可选择已发布的 Prompt 版本快速加载</li>
                  <li>在下方输入框发送消息，实时查看模型输出</li>
                </ul>
                <p><strong>适用场景：</strong></p>
                <ul>
                  <li>调试 Prompt 模板的指令清晰度和输出质量</li>
                  <li>对比不同模型配置下的生成效果</li>
                  <li>验证变量替换是否符合预期</li>
                </ul>
              </div>
            </template>
            <QuestionCircleOutlined class="playground-help-icon" />
          </a-popover>
        </h1>
        <p class="page-desc">测试和调试你的 AI 提示词</p>
      </div>
      <div class="header-actions">
        <button class="btn-outline" @click="openCreatePromptModal" title="从当前配置创建新Prompt">
          <PlusOutlined /> 快速创建 Prompt
        </button>
        <button class="btn-outline" @click="addInstance()" :disabled="instances.length >= 3" title="最多同时对比3个配置">
          <CopyOutlined /> 添加配置
        </button>
      </div>
    </div>

    <!-- 配置实例网格 -->
    <div class="instances-grid" :class="'cols-' + instances.length">
      <div v-for="(inst, idx) in instances" :key="inst.id" class="instance-card">
        <!-- 实例头部 -->
        <div class="instance-header">
          <span class="instance-title">配置 {{ idx + 1 }}
            <span v-if="inst.promptKey" class="instance-subtitle">({{ inst.promptKey }})</span>
          </span>
          <div class="instance-actions">
            <a-tooltip title="清空对话" :getPopupContainer="t => t.parentElement" v-if="inst.messages.length > 0">
              <button class="btn-icon-sm" @click="clearChat(inst)">
                <ClearOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="复制配置" :getPopupContainer="t => t.parentElement">
              <button class="btn-icon-sm" @click="addInstance(inst)" :disabled="instances.length >= 3">
                <CopyOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="删除配置" :getPopupContainer="t => t.parentElement" v-if="instances.length > 1">
              <button class="btn-icon-sm" @click="removeInstance(inst.id)">
                <DeleteOutlined />
              </button>
            </a-tooltip>
          </div>
        </div>

        <!-- Prompt 选择 -->
        <div class="select-row">
          <a-select
            v-model:value="inst.selectedPromptKey"
            placeholder="选择 Prompt（可选）"
            allow-clear
            size="small"
            style="flex: 1"
            @change="onPromptChange(inst, $event)"
          >
            <a-select-option v-for="p in promptList" :key="p.promptKey" :value="p.promptKey">
              {{ p.promptKey }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="inst.selectedVersion"
            placeholder="选择版本"
            allow-clear
            size="small"
            style="flex: 1"
            :disabled="!inst.selectedPromptKey"
            @change="onVersionChange(inst, $event)"
          >
            <a-select-option v-for="v in inst.versionList" :key="v.version" :value="v.version">
              {{ v.version }}{{ v.versionDesc ? ' - ' + v.versionDesc : '' }}
            </a-select-option>
          </a-select>
        </div>

        <!-- Prompt 内容编辑器 -->
        <a-textarea
          v-model:value="inst.content"
          :rows="6"
          placeholder="输入 Prompt 内容，使用 {{变量名}} 定义变量"
          class="template-editor"
          @change="onContentChange(inst)"
        />

        <!-- 模型配置 -->
        <div class="config-section">
          <div class="config-section-title">模型配置</div>
          <div class="model-select-row">
            <ModelSelect
              :model-value="getInstModelValue(inst)"
              size="small"
              @change="(m) => onInstModelChange(inst, m)"
            />
          </div>
          <div class="model-params" v-if="inst.configFields.length > 0">
            <div class="param-row" v-for="field in inst.configFields" :key="field.key">
              <span class="param-label">{{ field.label || field.key }}</span>
              <a-slider
                v-if="field.type === 'slider'"
                v-model:value="inst.modelConfig[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step || 0.01"
                style="flex: 1"
                size="small"
              />
              <a-input-number
                v-else-if="field.type === 'number'"
                v-model:value="inst.modelConfig[field.key]"
                :min="field.min"
                :max="field.max"
                size="small"
                style="width: 100px"
              />
              <a-select
                v-else-if="field.type === 'select'"
                v-model:value="inst.modelConfig[field.key]"
                size="small"
                style="width: 120px"
              >
                <a-select-option v-for="opt in field.options" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
              <a-input
                v-else
                v-model:value="inst.modelConfig[field.key]"
                size="small"
                style="width: 120px"
              />
              <span class="param-value" v-if="field.type === 'slider'">
                {{ inst.modelConfig[field.key] ?? field.defaultValue }}
              </span>
            </div>
          </div>
</div>

        <!-- 参数配置 -->
        <div class="config-section" v-if="inst.variables.length > 0">
          <div class="config-section-title">参数配置</div>
          <div class="variable-row" v-for="v in inst.variables" :key="v.key">
            <span class="var-key">&lt;{{ v.key }}&gt;</span>
            <a-input
              v-model:value="v.defaultValue"
              size="small"
              :placeholder="'默认值'"
              style="flex: 1"
            />
          </div>
        </div>

        <a-divider style="margin: 12px 0" />

        <!-- 对话测试区域 -->
        <div class="chat-area">
          <div class="chat-header">
            <span class="chat-title">对话测试</span>
            <span class="chat-meta" v-if="inst.messages.length > 0">{{ inst.messages.length }} 条消息</span>
          </div>
          <div class="chat-messages" :ref="el => { if (el) inst.messagesRef = el }">
            <div v-if="inst.messages.length === 0" class="chat-empty">
              <RobotOutlined class="chat-empty-icon" />
              <p>在下方输入框中发送消息开始测试</p>
            </div>
            <div v-for="(msg, i) in inst.messages" :key="i" :class="['chat-msg', msg.role]">
              <div class="msg-avatar" v-if="msg.role === 'assistant'">AI</div>
              <div class="msg-body">
                <MarkdownPreview v-if="msg.role === 'assistant' && msg._md" :content="msg.content" :finalized="true" />
                <div v-else class="msg-content">{{ msg.content }}</div>
                <div class="msg-actions" v-if="msg.role === 'assistant' && !inst.streaming">
                  <a-tooltip title="Markdown 渲染">
                    <button class="btn-text-xs" :class="{ active: msg._md }" @click="msg._md = !msg._md">
                      <FileMarkdownOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip title="复制">
                    <button class="btn-text-xs" @click="copyText(msg.content)">
                      <CopyOutlined />
                    </button>
                  </a-tooltip>
                </div>
              </div>
            </div>
            <div v-if="inst.streaming" class="chat-msg assistant">
              <div class="msg-avatar">AI</div>
              <div class="msg-body">
                <div class="msg-content">{{ inst.streamContent }}<span class="cursor">|</span></div>
              </div>
            </div>
          </div>
          <div class="chat-input">
            <a-textarea
              v-model:value="inst.userInput"
              :rows="2"
              :auto-size="{ minRows: 2, maxRows: 4 }"
              placeholder="输入消息... (Ctrl+Enter 发送)"
              @keydown.enter.ctrl="handleSend(inst)"
            />
            <div class="chat-input-actions">
              <span class="chat-hint">Ctrl+Enter 发送</span>
              <button
                class="btn-primary-sm"
                :disabled="inst.streaming || !inst.content.trim()"
                @click="handleSend(inst)"
              >
                <SendOutlined /> {{ inst.streaming ? '生成中...' : '发送' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快速创建Prompt弹窗 -->
    <CreatePromptModal
      v-model:open="createPromptModalVisible"
      :current-config="createPromptConfig"
      @success="handleCreatePromptSuccess"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, CopyOutlined, DeleteOutlined, ClearOutlined, RobotOutlined, SendOutlined,
  QuestionCircleOutlined, PlusOutlined, FileMarkdownOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getPrompts, getPromptVersions, getPromptVersionDetail, runPromptStream } from '../api/prompt'
import { getProviderConfigFields } from '../api/modelProvider'
import ModelSelect from '../components/ModelSelect.vue'
import CreatePromptModal from '../components/CreatePromptModal.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { copyToClipboard } from '../utils/clipboard'

const router = useRouter()
const promptList = ref([])
let instanceIdCounter = 1
const instances = ref([])

function createInstance() {
  return {
    id: instanceIdCounter++,
    promptKey: null,
    selectedPromptKey: null,
    selectedVersion: null,
    versionList: [],
    content: '',
    providerId: null,
    modelId: null,
    modelConfig: {},
    configFields: [],
    modelList: [],
    variables: [],
    toolConfig: '{}',
    messages: [],
    userInput: '',
    streaming: false,
    streamContent: '',
    messagesRef: null,
    abortController: null,
  }
}

function addInstance(src) {
  if (instances.value.length >= 3) return
  const inst = createInstance()
  if (src) {
    inst.content = src.content || ''
    inst.providerId = src.providerId
    inst.modelId = src.modelId
    inst.modelConfig = { ...(src.modelConfig || {}) }
    inst.variables = (src.variables || []).map(v => ({ ...v }))
    inst.toolConfig = src.toolConfig || '{}'
    if (inst.providerId) {
      loadConfigFieldsForInstance(inst, inst.providerId)
    }
  }
  instances.value.push(inst)
}

function removeInstance(id) {
  instances.value = instances.value.filter(i => i.id !== id)
}

async function copyText(text) {
  await copyToClipboard(text)
  message.success('已复制到剪贴板')
}

// Prompt 选择
async function onPromptChange(inst, promptKey) {
  inst.selectedVersion = null
  inst.versionList = []
  inst.content = ''
  inst.variables = []
  inst.promptKey = promptKey
  if (!promptKey) return
  try {
    const res = await getPromptVersions(promptKey)
    inst.versionList = res.data || []
  } catch { inst.versionList = [] }
}

// 版本选择
async function onVersionChange(inst, version) {
  if (!version || !inst.selectedPromptKey) {
    inst.content = ''
    inst.variables = []
    inst.toolConfig = '{}'
    return
  }
  try {
    const res = await getPromptVersionDetail(inst.selectedPromptKey, version)
    const detail = res.data
    if (detail) {
      inst.content = detail.template || ''
      if (detail.modelConfig) {
        const cfg = typeof detail.modelConfig === 'string' ? JSON.parse(detail.modelConfig) : detail.modelConfig
        if (cfg.providerId) {
          inst.providerId = cfg.providerId
          await loadConfigFieldsForInstance(inst, cfg.providerId)
          if (cfg.modelId) inst.modelId = cfg.modelId
          for (const [k, v] of Object.entries(cfg)) {
            if (k !== 'providerId' && k !== 'modelId') {
              inst.modelConfig[k] = v
            }
          }
        }
      }
      if (detail.variables) {
        const vars = typeof detail.variables === 'string' ? JSON.parse(detail.variables) : detail.variables
        if (Array.isArray(vars)) {
          inst.variables = vars
        } else if (typeof vars === 'object') {
          inst.variables = Object.entries(vars).map(([key, defaultValue]) => ({ key, defaultValue: defaultValue || '' }))
        }
      }
      // 加载工具配置
      if (detail.toolConfig) {
        inst.toolConfig = detail.toolConfig
      }
      onContentChange(inst)
    }
  } catch { /* ignore */ }
}

function getInstModelValue(inst) {
  if (inst.providerId && inst.modelId) return `${String(inst.providerId)}|${String(inst.modelId)}`
  return undefined
}

async function onInstModelChange(inst, { providerId, modelId }) {
  const prevProviderId = inst.providerId
  inst.providerId = providerId ? String(providerId) : providerId
  inst.modelId = modelId ? String(modelId) : modelId
  if (providerId && String(prevProviderId) !== String(providerId)) {
    inst.modelConfig = {}
    await loadConfigFieldsForInstance(inst, providerId)
  }
}

async function loadConfigFieldsForInstance(inst, providerId) {
  try {
    const res = await getProviderConfigFields(providerId)
    const fields = (res.data || []).filter(f => f.key !== 'modelId')
    inst.configFields = fields
    for (const f of fields) {
      if (inst.modelConfig[f.key] === undefined && f.defaultValue !== undefined) {
        inst.modelConfig[f.key] = f.defaultValue
      }
    }
  } catch { inst.configFields = [] }
}

// 变量检测
function onContentChange(inst) {
  const matches = [...(inst.content || '').matchAll(/\{\{(\w+)\}\}/g)]
  const keys = [...new Set(matches.map(m => m[1]))]
  inst.variables = keys.map(k => {
    const existing = inst.variables.find(v => v.key === k)
    return { key, defaultValue: existing?.defaultValue || '' }
  })
}

function buildModelConfigJson(inst) {
  const cfg = { providerId: inst.providerId, modelId: inst.modelId, ...inst.modelConfig }
  return JSON.stringify(cfg)
}

// 发送消息
async function handleSend(inst) {
  if (inst.streaming || !inst.content.trim() || !inst.userInput.trim()) return

  const userMsg = inst.userInput.trim()
  inst.messages.push({ role: 'user', content: userMsg })
  inst.userInput = ''
  inst.streaming = true
  inst.streamContent = ''

  await nextTick()
  scrollToBottom(inst)

  // 合并变量
  let variables = '{}'
  if (inst.variables.length > 0) {
    const vars = {}
    for (const v of inst.variables) {
      if (v.key) vars[v.key] = v.defaultValue || ''
    }
    variables = JSON.stringify(vars)
  }

  inst.abortController = new AbortController()
  try {
    await runPromptStream(
      {
        promptKey: inst.promptKey || 'playground',
        template: inst.content,
        variables,
        modelConfig: buildModelConfigJson(inst),
      },
      {
        onChunk(chunk) {
          inst.streamContent += chunk
          nextTick(() => scrollToBottom(inst))
        },
        onDone() {
          if (inst.streamContent) {
            inst.messages.push({ role: 'assistant', content: inst.streamContent })
          }
          inst.streaming = false
          inst.streamContent = ''
        },
        onError(err) {
          inst.messages.push({ role: 'assistant', content: '[错误] ' + err })
          inst.streaming = false
          inst.streamContent = ''
        },
      },
      inst.abortController.signal,
    )
  } catch (e) {
    if (e.name !== 'AbortError') {
      inst.messages.push({ role: 'assistant', content: '[错误] 请求失败' })
    }
    inst.streaming = false
    inst.streamContent = ''
  }
}

function clearChat(inst) {
  inst.messages = []
  if (inst.abortController) inst.abortController.abort()
  inst.streaming = false
  inst.streamContent = ''
}

function scrollToBottom(inst) {
  if (inst.messagesRef) {
    inst.messagesRef.scrollTop = inst.messagesRef.scrollHeight
  }
}

// 初始化
onMounted(async () => {
  const inst = createInstance()
  instances.value.push(inst)

  const promptRes = await getPrompts({ pageNum: 1, pageSize: 100 }).catch(() => ({ data: { records: [] } }))
  promptList.value = promptRes.data?.records || []
})

// 快速创建Prompt
const createPromptModalVisible = ref(false)
const createPromptConfig = ref({})

function openCreatePromptModal() {
  const inst = instances.value[0]
  if (!inst?.content.trim()) {
    message.warning('请先编辑模板内容')
    return
  }
  createPromptConfig.value = {
    template: inst.content,
    modelId: inst.modelId,
    variables: inst.variables,
    modelConfig: {
      providerId: inst.providerId,
      modelId: inst.modelId,
      ...inst.modelConfig
    },
    toolConfig: inst.toolConfig
  }
  createPromptModalVisible.value = true
}

function handleCreatePromptSuccess({ promptKey }) {
  loadPromptsData()
  router.push(`/app/prompts/${promptKey}`)
}

async function loadPromptsData() {
  try {
    const res = await getPrompts({ pageNum: 1, pageSize: 100 })
    promptList.value = res.data?.records || []
  } catch { /* ignore */ }
}
</script>

<style scoped>
.page {
  padding: 20px calc(24px + var(--scroll-content-gap)) 20px 24px;
  min-height: 100vh;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
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
.playground-help-icon {
  margin-left: 6px;
  font-size: 18px;
  color: var(--color-mute);
  cursor: pointer;
  transition: color 0.2s;
  vertical-align: middle;
}
.playground-help-icon:hover {
  color: #3b82f6;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.header-actions {
  display: flex;
  gap: 8px;
}
.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover:not(:disabled) { border-color: var(--color-link); color: var(--color-link); }
.btn-outline:disabled { opacity: 0.5; cursor: not-allowed; border-color: #d9d9d9; color: var(--color-mute); }
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: var(--color-hairline-strong); color: var(--color-mute); cursor: not-allowed; }
.btn-icon-sm {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 12px;
}
.btn-icon-sm:hover:not(:disabled) { background: var(--color-canvas-soft-2); }
.btn-icon-sm:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-text-xs {
  background: none;
  border: none;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 4px;
}
.btn-text-xs:hover { color: var(--color-link); }

/* 实例网格 */
.instances-grid {
  display: grid;
  gap: 16px;
}
.instances-grid.cols-1 { grid-template-columns: 1fr; }
.instances-grid.cols-2 { grid-template-columns: repeat(2, 1fr); }
.instances-grid.cols-3 { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 1400px) {
  .instances-grid.cols-3 { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 1100px) {
  .instances-grid.cols-2,
  .instances-grid.cols-3 { grid-template-columns: 1fr; }
}

.instance-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
}
.instance-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.instance-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}
.instance-subtitle {
  font-size: 13px;
  font-weight: 400;
  color: var(--color-mute);
  margin-left: 4px;
}
.instance-actions {
  display: flex;
  gap: 4px;
}

/* 选择行 */
.select-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

/* 编辑器 */
.template-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}

/* 配置区块 */
.config-section {
  margin-top: 10px;
  padding: 10px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.config-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.model-select-row {
  display: flex;
  gap: 8px;
}
.model-params {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.param-label {
  font-size: 12px;
  color: var(--color-mute);
  min-width: 80px;
  flex-shrink: 0;
}
.param-value {
  font-size: 12px;
  color: var(--color-ink);
  min-width: 40px;
  text-align: right;
}
.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.var-key {
  font-size: 12px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: var(--color-link);
  background: var(--color-info-bg);
  padding: 2px 6px;
  border-radius: 4px;
  min-width: 100px;
  flex-shrink: 0;
}

/* 对话区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.chat-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}
.chat-meta {
  font-size: 12px;
  color: var(--color-mute);
}
.chat-messages {
  height: 350px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-mute);
}
.chat-empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.chat-msg {
  display: flex;
  gap: 8px;
  max-width: 90%;
}
.chat-msg.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.chat-msg.user .msg-body {
  background: var(--color-primary);
  color: #fff;
  border-radius: 12px 12px 2px 12px;
  padding: 10px 14px;
}
.chat-msg.assistant {
  align-self: flex-start;
}
.chat-msg.assistant .msg-body {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
  border-radius: 2px 12px 12px 12px;
  padding: 10px 14px;
}
.msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 2px;
}
.msg-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-actions {
  margin-top: 4px;
  display: flex;
  gap: 4px;
}
.msg-actions .btn-text-xs.active {
  color: #2563eb;
}
.btn-text-xs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border: none;
  background: transparent;
  color: var(--color-mute);
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}
.btn-text-xs:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}
.cursor {
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.chat-input {
  border-top: 1px solid var(--color-hairline);
  padding-top: 12px;
}
.chat-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.chat-hint {
  font-size: 12px;
  color: var(--color-mute);
}
</style>

<style>
.playground-help-popover .ant-popover-inner-content {
  padding: 12px 16px;
}
.playground-help {
  max-width: 360px;
  font-size: 13px;
  color: #333;
  line-height: 1.6;
}
.playground-help p {
  margin: 0 0 8px;
}
.playground-help p:last-child {
  margin-bottom: 0;
}
.playground-help ul {
  margin: 4px 0 8px;
  padding-left: 18px;
}
.playground-help li {
  margin-bottom: 2px;
}
.playground-help code {
  background: var(--color-canvas-soft-2);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
  color: var(--color-link);
}
</style>
