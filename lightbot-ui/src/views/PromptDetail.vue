<template>
  <div class="page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.back()">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title"><span v-if="latestVersion" class="version-badge">{{ latestVersion }}</span>{{ promptKey }}</h1>
        <p class="page-desc" v-if="prompt?.description">{{ prompt.description }}</p>
        <div class="prompt-tags" v-if="prompt?.tags">
          <a-tag v-for="tag in prompt.tags.split(',')" :key="tag" color="blue" size="small">{{ tag.trim() }}</a-tag>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn-outline-sm" @click="router.push(`/app/prompts/${promptKey}/versions`)">
          <HistoryOutlined /> 版本记录
        </button>
        <button class="btn-primary-sm" @click="openVersionDialog()">
          <CloudUploadOutlined /> 发布版本
        </button>
      </div>
    </div>

    <!-- 配置实例网格 -->
    <a-spin :spinning="pageLoading">
    <div class="instances-grid" :class="'cols-' + instances.length">
      <div v-for="(inst, idx) in instances" :key="inst.id" class="instance-card">
        <!-- 实例头部 -->
        <div class="instance-header">
          <span class="instance-title">配置 {{ idx + 1 }}</span>
          <div class="instance-actions">
            <a-tooltip title="从模板导入">
              <button class="btn-icon-sm" @click="openTemplateImportFor(inst)">
                <ImportOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="复制此配置">
              <button class="btn-icon-sm" @click="addInstance()" :disabled="instances.length >= 3">
                <CopyOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="删除配置">
              <button class="btn-icon-sm" @click="removeInstance(inst.id)" v-if="instances.length > 1">
                <DeleteOutlined />
              </button>
            </a-tooltip>
          </div>
        </div>

        <!-- Prompt 内容编辑器 -->
        <a-textarea
          v-model:value="inst.content"
          :rows="10"
          placeholder="输入 Prompt 模板内容，使用 {{变量名}} 定义变量"
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
          <!-- 动态模型参数 -->
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
              <a-switch
                v-else-if="field.type === 'switch'"
                v-model:checked="inst.modelConfig[field.key]"
                size="small"
              />
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

        <!-- 调试区域 -->
        <div class="debug-area">
          <div class="debug-header">
            <span class="debug-title">对话测试
              <a-tooltip placement="topLeft">
                <template #title>
                  <div style="max-width: 280px; line-height: 1.8">
                    <div>1. 在上方<b>参数配置</b>中填写变量默认值</div>
                    <div>2. 下方输入框可输入内容覆盖单变量值（可选）</div>
                    <div>3. 点击<b>运行</b>或 <b>Ctrl+Enter</b> 发送</div>
                    <div>4. AI 回复后可点击 <b>Markdown</b> 图标切换渲染</div>
                  </div>
                </template>
                <QuestionCircleOutlined class="debug-help-icon" />
              </a-tooltip>
            </span>
            <span class="debug-meta" v-if="inst.messages.length > 0">{{ inst.messages.length }} 条消息</span>
          </div>
          <div class="debug-messages" :ref="el => { if (el) inst.messagesRef = el }">
            <div v-if="inst.messages.length === 0" class="debug-empty">
              填写参数配置后点击运行，调试 Prompt 效果
            </div>
            <div v-for="(msg, i) in inst.messages" :key="i" :class="['debug-msg', msg.role]">
              <MarkdownPreview v-if="msg.role === 'assistant' && msg._md" :content="msg.content" :finalized="true" />
              <div v-else class="msg-content">{{ msg.content }}</div>
              <div class="msg-actions" v-if="msg.role === 'assistant' && !inst.streaming">
                <a-tooltip title="Markdown 渲染">
                  <button class="btn-text-xs" :class="{ active: msg._md }" @click="msg._md = !msg._md">
                    <FileMarkdownOutlined />
                  </button>
                </a-tooltip>
              </div>
            </div>
            <div v-if="inst.streaming" class="debug-msg assistant">
              <div class="msg-content">{{ inst.streamContent }}<span class="cursor">|</span></div>
            </div>
          </div>
          <div class="debug-input">
            <a-textarea
              v-model:value="inst.userInput"
              :rows="2"
              :auto-size="{ minRows: 2, maxRows: 6 }"
              :placeholder="getPlaceholder(inst)"
              @keydown.enter.ctrl="handleRun(inst)"
            />
            <div class="debug-input-actions">
              <span class="debug-hint">Ctrl+Enter 发送</span>
              <div class="debug-input-btns">
                <a-tooltip title="复制输入内容">
                  <button v-if="inst.userInput.trim()" class="btn-icon-sm" @click="copyInput(inst)">
                    <CopyOutlined />
                  </button>
                </a-tooltip>
                <button
                  class="btn-primary-sm"
                  :disabled="inst.streaming || !inst.content.trim()"
                  @click="handleRun(inst)"
                >
                  <ThunderboltOutlined /> {{ inst.streaming ? '生成中...' : '运行' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    </a-spin>

    <!-- 从模板导入弹窗 -->
    <TemplateImportModalLR
      v-model:open="templateImportVisible"
      @import="handleTemplateImport"
    />

    <!-- 发布版本弹窗 -->
    <a-modal
      v-model:open="versionDialogVisible"
      title="发布版本"
      :width="480"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="versionForm" :label-col="{ span: 5 }">
        <a-form-item label="版本号" required>
          <a-input v-model:value="versionForm.version" placeholder="如: v1.0" />
        </a-form-item>
        <a-form-item label="版本描述">
          <a-input v-model:value="versionForm.versionDesc" placeholder="版本说明" />
        </a-form-item>
        <a-form-item label="发布状态">
          <a-radio-group v-model:value="versionForm.status">
            <a-radio value="pre">草稿（pre）</a-radio>
            <a-radio value="release">正式发布（release）</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="versionDialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handlePublishVersion">
            {{ submitting ? '提交中...' : '发布' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, HistoryOutlined, ThunderboltOutlined,
  ImportOutlined, CloudUploadOutlined, CopyOutlined, DeleteOutlined,
  FileMarkdownOutlined, QuestionCircleOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getPrompts, getPromptVersions, getPromptVersionDetail, createPromptVersion, runPromptStream,
} from '../api/prompt'
import { getProviderConfigFields } from '../api/modelProvider'
import ModelSelect from '../components/ModelSelect.vue'
import TemplateImportModalLR from '../components/TemplateImportModalLR.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import { copyToClipboard } from '../utils/clipboard'

const route = useRoute()
const router = useRouter()
const promptKey = route.params.id
const prompt = ref(null)
const versions = ref([])
const pageLoading = ref(true)
const submitting = ref(false)

const latestVersion = computed(() => versions.value.length > 0 ? versions.value[0].version : '')

// 配置实例
let instanceIdCounter = 1
const instances = ref([])

function createInstance(template = '', modelCfg = {}) {
  return {
    id: instanceIdCounter++,
    content: template,
    providerId: modelCfg.providerId || null,
    modelId: modelCfg.modelId || null,
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

function addInstance() {
  if (instances.value.length >= 3) return
  const src = instances.value[0]
  const inst = createInstance(src?.content || '', {
    providerId: src?.providerId,
    modelId: src?.modelId,
  })
  // 复制变量
  inst.variables = (src?.variables || []).map(v => ({ ...v }))
  inst.modelConfig = { ...(src?.modelConfig || {}) }
  inst.toolConfig = src?.toolConfig || '{}'
  instances.value.push(inst)
  // 加载配置字段
  if (inst.providerId) {
    loadConfigFieldsForInstance(inst, inst.providerId)
  }
}

function removeInstance(id) {
  instances.value = instances.value.filter(i => i.id !== id)
}

// 版本记录
const versionDialogVisible = ref(false)
const versionForm = reactive({ version: '', versionDesc: '', status: 'pre' })

// 模板导入
const templateImportVisible = ref(false)

onMounted(async () => {
  try {
    await loadPrompt()
    await loadVersions()
    // 初始化一个配置实例
    const inst = createInstance()
    instances.value.push(inst)

    // 优先恢复指定版本，否则加载最新版本（通过详情API获取完整modelConfig）
    const restoreVersion = route.query.restoreVersion
    if (restoreVersion) {
      await restoreByVersion(restoreVersion)
    } else if (versions.value.length > 0) {
      const latest = versions.value[0]
      await restoreByVersion(latest.version)
    }
  } finally {
    pageLoading.value = false
  }
})

// 监听版本恢复（组件复用时 query 变化不会触发 onMounted）
watch(() => route.query.restoreVersion, async (val) => {
  if (val) await restoreByVersion(val)
})

async function restoreByVersion(version) {
  const inst = instances.value[0]
  if (!inst) return
  try {
    const res = await getPromptVersionDetail(promptKey, version)
    if (res.data) {
      await applyVersionToInstance(inst, res.data)
    }
  } catch { /* ignore */ }
}

async function loadPrompt() {
  const res = await getPrompts({ keyword: promptKey, pageNum: 1, pageSize: 1 })
  const records = res.data?.records || []
  prompt.value = records.find(p => p.promptKey === promptKey) || { promptKey }
}

async function loadVersions() {
  const res = await getPromptVersions(promptKey)
  versions.value = res.data || []
}

async function applyVersionToInstance(inst, versionData) {
  inst.content = versionData.template || ''
  // 模型配置
  if (versionData.modelConfig) {
    try {
      const cfg = typeof versionData.modelConfig === 'string' ? JSON.parse(versionData.modelConfig) : versionData.modelConfig
      const pid = cfg.providerId != null ? String(cfg.providerId) : null
      if (pid) {
        inst.providerId = pid
        await loadConfigFieldsForInstance(inst, pid)
        if (cfg.modelId) inst.modelId = String(cfg.modelId)
        applyModelConfigToInstance(inst, cfg)
      }
    } catch { /* ignore */ }
  }
  // 变量
  if (versionData.variables) {
    try {
      inst.variables = JSON.parse(versionData.variables)
    } catch { /* ignore */ }
  }
  // 工具配置
  if (versionData.toolConfig) {
    inst.toolConfig = versionData.toolConfig
  }
  onContentChange(inst)
}

function getInstModelValue(inst) {
  if (inst.providerId && inst.modelId) return `${String(inst.providerId)}:${String(inst.modelId)}`
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

function getPlaceholder(inst) {
  if (inst.variables.length > 1) return '输入对话内容（可选），留空则直接使用上方参数配置'
  if (inst.variables.length === 1) return `输入 <${inst.variables[0].key}> 的值，留空使用上方默认值`
  return '输入对话内容'
}

function onContentChange(inst) {
  const matches = [...(inst.content || '').matchAll(/\{\{(\w+)\}\}/g)]
  const keys = [...new Set(matches.map(m => m[1]))]
  inst.variables = keys.map(key => {
    const existing = inst.variables.find(v => v.key === key)
    return { key, defaultValue: existing?.defaultValue || '' }
  })
}

/**
 * 将导入/恢复的模型配置应用到实例，只覆盖提供商支持的字段
 */
/**
 * @param {boolean} filterByFields - 是否按 configFields 白名单过滤（版本恢复时过滤，模板导入时不过滤）
 */
function applyModelConfigToInstance(inst, cfg, filterByFields = true) {
  if (!cfg) return
  if (!inst.modelConfig) inst.modelConfig = {}
  const fields = inst.configFields || []
  const supportedKeys = filterByFields && fields.length > 0 ? new Set(fields.map(f => f.key)) : null
  for (const [k, v] of Object.entries(cfg)) {
    if (k !== 'providerId' && k !== 'modelId' && (supportedKeys === null || supportedKeys.has(k))) {
      inst.modelConfig[k] = v
    }
  }
}

function buildModelConfigJson(inst) {
  const cfg = { providerId: inst.providerId, modelId: inst.modelId, ...inst.modelConfig }
  return JSON.stringify(cfg)
}

// 模板导入
let importTargetInst = null

function openTemplateImportFor(inst) {
  importTargetInst = inst
  templateImportVisible.value = true
}

async function handleTemplateImport(t) {
  if (!t) return
  const inst = importTargetInst || instances.value[0]
  inst.content = t.template || ''
  onContentChange(inst)
  message.success('模板导入成功')
}

// 发布版本
function openVersionDialog() {
  const inst = instances.value[0]
  if (!inst?.content.trim()) return message.warning('请先编辑模板内容')
  Object.assign(versionForm, { version: '', versionDesc: '', status: 'pre' })
  versionDialogVisible.value = true
}

async function handlePublishVersion() {
  if (!versionForm.version.trim()) return message.warning('请输入版本号')
  const inst = instances.value[0]
  submitting.value = true
  try {
    await createPromptVersion({
      promptKey,
      version: versionForm.version,
      versionDesc: versionForm.versionDesc,
      template: inst.content,
      variables: JSON.stringify(inst.variables),
      modelConfig: buildModelConfigJson(inst),
      toolConfig: inst.toolConfig,
      status: versionForm.status,
    })
    message.success('版本发布成功')
    versionDialogVisible.value = false
    loadVersions()
  } finally {
    submitting.value = false
  }
}

// 复制输入内容
async function copyInput(inst) {
  await copyToClipboard(inst.userInput)
  message.success('已复制到剪贴板')
}

// 调试运行
async function handleRun(inst) {
  if (inst.streaming || !inst.content.trim()) return

  // 从参数配置区构建变量 JSON
  const vars = {}
  for (const v of inst.variables) {
    if (v.key) vars[v.key] = v.defaultValue || ''
  }
  // 单变量时，用户输入框的内容作为该变量值
  if (inst.variables.length === 1 && inst.userInput.trim()) {
    vars[inst.variables[0].key] = inst.userInput.trim()
  }
  const variables = JSON.stringify(vars)

  inst.messages.push({ role: 'user', content: inst.userInput || variables })
  inst.userInput = ''
  inst.streaming = true
  inst.streamContent = ''

  await nextTick()
  scrollToBottom(inst)

  inst.abortController = new AbortController()
  try {
    await runPromptStream(
      {
        promptKey,
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

function scrollToBottom(inst) {
  if (inst.messagesRef) {
    inst.messagesRef.scrollTop = inst.messagesRef.scrollHeight
  }
}
</script>

<style scoped>
.page {
  padding: 20px 24px;
  min-height: 100vh;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
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
  color: var(--color-primary);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.version-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #7c3aed;
  background: linear-gradient(135deg, #ede9fe, #e0e7ff);
  border: 1px solid #c4b5fd;
  border-radius: 100px;
  padding: 2px 10px;
  line-height: 18px;
  white-space: nowrap;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin-bottom: 12px;
}
.prompt-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: #fff;
  color: var(--color-primary);
  border: 1px solid #d4d4d8;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-sm:hover:not(:disabled) { border-color: var(--color-link); color: var(--color-link); }
.btn-outline-sm:disabled { opacity: 0.5; cursor: not-allowed; border-color: #d4d4d8; color: var(--color-mute); }
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
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
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

/* 实例网格 */
.instances-grid {
  display: grid;
  gap: 16px;
  min-height: 200px;
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
  background: #fff;
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
  color: var(--color-primary);
}
.instance-actions {
  display: flex;
  gap: 4px;
}

/* 编辑器 */
.template-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}

/* 配置区块 */
.config-section {
  margin-top: 12px;
  padding: 12px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
}
.config-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
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
  color: var(--color-primary);
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
  color: #6366f1;
  background: #eef2ff;
  padding: 2px 6px;
  border-radius: 4px;
  min-width: 100px;
  flex-shrink: 0;
}

/* 调试区域 */
.debug-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.debug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.debug-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}
.debug-help-icon {
  margin-left: 4px;
  font-size: 14px;
  color: #a1a1aa;
  cursor: help;
  vertical-align: middle;
}
.debug-help-icon:hover {
  color: var(--color-mute);
}
.debug-meta {
  font-size: 12px;
  color: #a1a1aa;
}
.debug-messages {
  height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
  border: 1px solid #f5f5f5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.debug-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #a1a1aa;
  font-size: 13px;
}
.debug-msg {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.debug-msg.user {
  align-self: flex-end;
  background: var(--color-canvas-soft-2);
  color: var(--color-primary);
}
.debug-msg.assistant {
  align-self: flex-start;
  background: #eff6ff;
  color: var(--color-primary);
}
.debug-footer {
  display: flex;
  justify-content: flex-end;
  padding: 0 4px;
}
.msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  justify-content: flex-end;
  opacity: 0;
  transition: opacity 0.2s;
}
.debug-msg:hover .msg-actions {
  opacity: 1;
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
  background: #f4f4f5;
  color: var(--color-primary);
}
.debug-footer .btn-text-xs {
  font-size: 12px;
  color: #a1a1aa;
}
.debug-footer .btn-text-xs:hover {
  color: var(--color-primary);
}
.cursor {
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.debug-input {
  border-top: 1px solid #ebebeb;
  padding-top: 12px;
}
.debug-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.debug-input-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}
.debug-hint {
  font-size: 12px;
  color: #a1a1aa;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}
.dialog-footer-right { display: flex; gap: 8px; }
</style>
