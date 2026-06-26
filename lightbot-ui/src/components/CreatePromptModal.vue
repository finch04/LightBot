<template>
  <a-modal
    v-model:open="visible"
    title="快速创建 Prompt"
    :width="560"
    :footer="null"
    :maskClosable="false"
  >
    <div class="create-prompt-info">
      <p>将当前配置创建为新的 Prompt，包含模板内容、模型配置。</p>
    </div>

    <a-form :model="form" :label-col="{ span: 5 }">
      <a-form-item label="Prompt Key" required>
        <a-input v-model:value="form.promptKey" :maxlength="30" show-count placeholder="如：my_new_prompt (不超过30字)" @input="sanitizePromptKey" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" :rows="2" :maxlength="200" show-count placeholder="Prompt用途描述 (不超过200字)" />
      </a-form-item>
      <a-form-item label="标签">
        <TagInput v-model="form.tags" />
      </a-form-item>
      <a-form-item label="初始版本">
        <a-input v-model:value="form.version" placeholder="如：v1.0" />
      </a-form-item>
      <a-form-item label="版本状态">
        <a-radio-group v-model:value="form.status">
          <a-radio value="pre">草稿（pre）</a-radio>
          <a-radio value="release">正式发布（release）</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>

    <!-- 配置预览 -->
    <div class="config-preview">
      <div class="preview-section">
        <span class="preview-label">模板内容预览：</span>
        <pre class="preview-content">{{ truncateContent(currentConfig.template) }}</pre>
      </div>
      <div class="preview-section">
        <span class="preview-label">模型配置：</span>
        <span class="preview-value">{{ currentConfig.modelId || '未选择' }}</span>
      </div>
      <div class="preview-section" v-if="currentConfig.variables?.length">
        <span class="preview-label">检测到参数：</span>
        <div class="preview-vars">
          <a-tag v-for="v in currentConfig.variables" :key="v.key" color="blue" size="small">{{ v.key }}</a-tag>
        </div>
      </div>
    </div>

    <div class="dialog-footer">
      <div></div>
      <div class="dialog-footer-right">
        <button class="btn-cancel" @click="visible = false">取消</button>
        <button class="btn-primary-sm" :disabled="submitting || !form.promptKey" @click="handleSubmit">
          {{ submitting ? '创建中...' : '创建' }}
        </button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import TagInput from './TagInput.vue'
import { createPrompt, createPromptVersion } from '../api/prompt'

const props = defineProps({
  open: { type: Boolean, default: false },
  currentConfig: {
    type: Object,
    default: () => ({ template: '', modelId: '', variables: [], modelConfig: {} })
  }
})

const emit = defineEmits(['update:open', 'success'])

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val)
})

const submitting = ref(false)
const form = reactive({
  promptKey: '',
  description: '',
  tags: '',
  version: 'v1.0',
  status: 'pre'
})

// 清理Prompt Key（只允许英文、数字、下划线、横杠）
function sanitizePromptKey() {
  form.promptKey = form.promptKey.replace(/[^a-zA-Z0-9_-]/g, '_')
}

// 截断内容预览
function truncateContent(content) {
  if (!content) return '（空）'
  const maxLen = 200
  if (content.length > maxLen) {
    return content.substring(0, maxLen) + '...'
  }
  return content
}

// 提交创建
async function handleSubmit() {
  if (!form.promptKey.trim()) {
    message.warning('请输入 Prompt Key')
    return
  }

  submitting.value = true
  try {
    // 1. 创建Prompt
    await createPrompt({
      promptKey: form.promptKey.trim(),
      description: form.description,
      tags: form.tags
    })

    // 2. 创建初始版本
    if (form.version.trim() && props.currentConfig.template) {
      const variables = props.currentConfig.variables?.length > 0
        ? JSON.stringify(props.currentConfig.variables.map(v => ({ key: v.key, defaultValue: v.defaultValue || '' })))
        : '[]'

      const modelConfig = props.currentConfig.modelConfig
        ? JSON.stringify(props.currentConfig.modelConfig)
        : '{}'

      const toolConfig = props.currentConfig.toolConfig || '{}'

      await createPromptVersion({
        promptKey: form.promptKey.trim(),
        version: form.version.trim(),
        versionDesc: '初始版本',
        template: props.currentConfig.template,
        variables,
        modelConfig,
        toolConfig,
        status: form.status
      })
    }

    message.success('Prompt 创建成功')
    emit('success', { promptKey: form.promptKey.trim() })
    visible.value = false

    // 重置表单
    Object.assign(form, { promptKey: '', description: '', tags: '', version: 'v1.0', status: 'pre' })
  } catch (e) {
    message.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

// 监听弹窗打开，初始化表单
watch(visible, (val) => {
  if (val) {
    Object.assign(form, { promptKey: '', description: '', tags: '', version: 'v1.0', status: 'pre' })
  }
})
</script>

<style scoped>
.create-prompt-info {
  background: var(--color-canvas-soft-2);
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.create-prompt-info p {
  font-size: 13px;
  color: var(--color-mute);
  margin: 0;
}

.config-preview {
  background: var(--color-canvas-soft);
  padding: 12px;
  border-radius: 8px;
  margin-top: 12px;
}

.preview-section {
  margin-bottom: 8px;
}

.preview-section:last-child {
  margin-bottom: 0;
}

.preview-label {
  font-size: 12px;
  color: var(--color-mute);
  display: block;
  margin-bottom: 4px;
}

.preview-value {
  font-size: 13px;
  color: var(--color-ink);
}

.preview-content {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: var(--color-canvas);
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--color-hairline);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 100px;
  overflow-y: auto;
}

.preview-vars {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}

.dialog-footer-right {
  display: flex;
  gap: 8px;
}

.btn-cancel {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}

.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }

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
</style>