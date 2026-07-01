<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/app/prompts')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">Prompt 模板</h1>
        <p class="page-desc">管理 Prompt 构建模板，方便快速创建和调试</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索模板..."
          allow-clear
          style="width: 180px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData" :disabled="loading"><ReloadOutlined :spin="loading" /> 刷新</button>
        <button class="btn-primary" @click="openDialog()"><PlusOutlined /> 新建模板</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="template-grid">
      <EntityCard
        v-for="t in filteredTemplates"
        :key="t.id"
        type="template"
        :name="t.promptTemplateKey"
        @click="previewTemplate(t)"
      >
        <template #info>
          <a-tooltip :title="t.promptTemplateKey"><h3>{{ t.promptTemplateKey }}</h3></a-tooltip>
          <span class="card-desc">{{ t.templateDesc || '暂无描述' }}</span>
        </template>
        <template #actions>
          <a-tooltip title="编辑">
            <button class="btn-icon" @click="openDialog(t)"><EditOutlined /></button>
          </a-tooltip>
          <a-tooltip title="删除">
            <button class="btn-icon danger" @click="handleDelete(t.id)"><DeleteOutlined /></button>
          </a-tooltip>
        </template>
        <div class="card-tags" v-if="t.tags">
          <a-tag v-for="tag in t.tags.split(',').slice(0, 3)" :key="tag" color="blue" size="small">{{ tag.trim() }}</a-tag>
        </div>
      </EntityCard>
      <div v-if="filteredTemplates.length === 0 && !loading" class="empty-state">
        <FileTextOutlined class="empty-icon" />
        <p>{{ searchText ? '没有匹配模板' : '暂无模板，点击右上角创建' }}</p>
      </div>
    </div>
    </a-spin>

    <!-- 模板预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      title="模板预览"
      :width="640"
      :footer="null"
      :maskClosable="true"
    >
      <div v-if="previewingTemplate" class="preview-content">
        <div class="preview-header">
          <h4>{{ previewingTemplate.promptTemplateKey }}</h4>
          <p>{{ previewingTemplate.templateDesc || '暂无描述' }}</p>
        </div>
        <a-divider />
        <div class="preview-section">
          <div class="preview-label">模板内容</div>
          <a-textarea :value="previewingTemplate.template" :rows="10" readonly class="preview-editor" />
        </div>
        <div class="preview-section" v-if="previewingTemplate.variables">
          <div class="preview-label">变量定义</div>
          <div class="preview-vars">
            <a-tag v-for="v in previewingTemplate.variables.split(',')" :key="v" color="blue">{{ v.trim() }}</a-tag>
          </div>
        </div>
        <div class="preview-section" v-if="previewingTemplate.modelConfig">
          <div class="preview-label">模型配置</div>
          <pre class="preview-config">{{ formatConfig(previewingTemplate.modelConfig) }}</pre>
        </div>
      </div>
    </a-modal>

    <!-- 新建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑模板' : '新建模板'"
      :width="800"
      :footer="null"
      :maskClosable="false"
    >
      <a-form :model="form" :label-col="{ span: 4 }">
        <a-form-item label="模板标识" required>
          <a-input v-model:value="form.promptTemplateKey" :maxlength="30" show-count placeholder="如：customer_service (不超过30字)" :disabled="!!form.id" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.templateDesc" :rows="2" :maxlength="50" show-count placeholder="模板用途描述 (不超过50字)" />
        </a-form-item>
        <a-form-item label="模板内容" required>
          <a-textarea v-model:value="form.template" :rows="10" :maxlength="5000" show-count placeholder="使用 {{变量名}} 定义变量 (不超过5000字)" class="template-editor" @input="onTemplateInput" />
        </a-form-item>
        <a-form-item label="变量定义">
          <div v-if="detectedVars.length > 0" class="detected-vars-hint">
            <span class="hint-label">自动识别：</span>
            <a-tag v-for="v in detectedVars" :key="v" color="blue" size="small">{{ v }}</a-tag>
          </div>
          <div v-else class="detected-vars-empty">
            <span class="hint-label">自动识别：</span>
            <span class="hint-text">在模板内容中使用 {{'\{\{变量名\}\}'}} 定义变量</span>
          </div>
        </a-form-item>
        <a-form-item label="模型配置">
          <JsonInput v-model="form.modelConfig" :rows="4" placeholder='{"providerId":1,"modelId":"gpt-4"}' />
        </a-form-item>
        <a-form-item label="标签">
          <TagInput v-model="form.tags" />
        </a-form-item>
      </a-form>
      <div class="dialog-footer">
        <div></div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftOutlined, PlusOutlined, EditOutlined, DeleteOutlined,
  SearchOutlined, ReloadOutlined, FileTextOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import TagInput from '../components/TagInput.vue'
import JsonInput from '../components/JsonInput.vue'
import EntityCard from '../components/EntityCard.vue'
import {
  getPromptTemplates, createPromptTemplate, updatePromptTemplate, deletePromptTemplate
} from '../api/prompt'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const previewVisible = ref(false)
const submitting = ref(false)
const previewingTemplate = ref(null)
const form = reactive({
  id: null,
  promptTemplateKey: '',
  templateDesc: '',
  template: '',
  variables: '',
  modelConfig: '{}',
  tags: ''
})

// 自动识别模板中的变量
const detectedVars = computed(() => {
  const matches = [...(form.template || '').matchAll(/\{\{(\w+)\}\}/g)]
  return [...new Set(matches.map(m => m[1]))]
})

// 模板内容输入时自动更新变量定义
function onTemplateInput() {
  if (detectedVars.value.length > 0 && !form.variables) {
    form.variables = detectedVars.value.join(',')
  }
}

const filteredTemplates = computed(() => {
  if (!searchText.value) return list.value
  const kw = searchText.value.toLowerCase()
  return list.value.filter(t =>
    (t.promptTemplateKey || '').toLowerCase().includes(kw) ||
    (t.templateDesc || '').toLowerCase().includes(kw) ||
    (t.tags || '').toLowerCase().includes(kw)
  )
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getPromptTemplates()
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      promptTemplateKey: row.promptTemplateKey || '',
      templateDesc: row.templateDesc || '',
      template: row.template || '',
      variables: row.variables || '',
      modelConfig: row.modelConfig || '{}',
      tags: row.tags || ''
    })
  } else {
    Object.assign(form, {
      id: null,
      promptTemplateKey: '',
      templateDesc: '',
      template: '',
      variables: '',
      modelConfig: '{}',
      tags: ''
    })
  }
  dialogVisible.value = true
}

function previewTemplate(t) {
  previewingTemplate.value = t
  previewVisible.value = true
}

function formatConfig(config) {
  if (!config) return ''
  try {
    const obj = typeof config === 'string' ? JSON.parse(config) : config
    return JSON.stringify(obj, null, 2)
  } catch {
    return config
  }
}

async function handleSubmit() {
  if (!form.promptTemplateKey.trim()) return message.warning('请输入模板标识')
  if (!form.template.trim()) return message.warning('请输入模板内容')

  // 使用自动识别的变量
  const variables = detectedVars.value.join(',')

  submitting.value = true
  try {
    if (form.id) {
      await updatePromptTemplate(form.id, {
        promptTemplateKey: form.promptTemplateKey,
        templateDesc: form.templateDesc,
        template: form.template,
        variables,
        modelConfig: form.modelConfig,
        tags: form.tags
      })
      message.success('更新成功')
    } else {
      await createPromptTemplate({
        promptTemplateKey: form.promptTemplateKey,
        templateDesc: form.templateDesc,
        template: form.template,
        variables,
        modelConfig: form.modelConfig,
        tags: form.tags
      })
      message.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function handleDelete(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deletePromptTemplate(id)
      message.success('删除成功')
      loadData()
    }
  })
}
</script>

<style scoped>
.page {
  padding: var(--space-xl);
  padding-right: calc(var(--space-xl) + var(--scroll-content-gap));
  min-height: 100vh;
  background: var(--color-canvas-soft);
  scrollbar-gutter: stable;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
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

.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

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

.btn-primary:hover { background: #27272a; }

.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-outline:hover { border-color: var(--color-link); color: var(--color-link); }

.btn-primary-sm {
  padding: 6px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  font-size: 13px;
}

.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
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

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-tags { display: flex; gap: 4px; flex-wrap: wrap; }

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

.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}

.dialog-footer-right { display: flex; gap: 8px; }

.template-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}

.preview-content {
  padding: 8px;
}

.preview-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 4px 0;
}

.preview-header p {
  font-size: 13px;
  color: var(--color-mute);
  margin: 0;
}

.preview-section {
  margin-bottom: 16px;
}

.preview-section:last-child { margin-bottom: 0; }

.preview-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}

.preview-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  background: var(--color-canvas-soft);
  border-radius: 6px;
}

.preview-vars {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.preview-config {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: var(--color-canvas-soft);
  padding: 10px;
  border-radius: 6px;
  border: 1px solid var(--color-hairline);
  overflow-x: auto;
}

.detected-vars-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-info-bg);
  border-radius: 6px;
}

.detected-vars-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-canvas-soft);
  border-radius: 6px;
  border: 1px dashed #d9d9d9;
}

.hint-label {
  font-size: 12px;
  color: var(--color-mute);
  flex-shrink: 0;
}

.hint-text {
  font-size: 12px;
  color: var(--color-mute);
}
</style>