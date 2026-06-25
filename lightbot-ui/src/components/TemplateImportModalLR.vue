<template>
  <a-modal
    v-model:open="visible"
    title="从模板导入"
    :width="960"
    :footer="null"
    :maskClosable="true"
  >
    <div class="template-import-lr">
      <!-- 左侧：模板列表 -->
      <div class="template-left">
        <a-input
          v-model:value="searchText"
          placeholder="搜索模板名称或描述..."
          allow-clear
          style="margin-bottom: 12px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div class="template-list">
          <div
            v-for="t in filteredTemplates"
            :key="t.id"
            class="template-item"
            :class="{ selected: selectedTemplate?.id === t.id }"
            @click="selectTemplate(t)"
          >
            <div class="template-item-name">{{ t.promptTemplateKey }}</div>
            <div class="template-item-desc">{{ t.templateDesc || '暂无描述' }}</div>
            <div class="template-item-tags" v-if="t.tags">
              <a-tag v-for="tag in t.tags.split(',').slice(0, 2)" :key="tag" color="blue" size="small">{{ tag.trim() }}</a-tag>
            </div>
          </div>
          <div v-if="filteredTemplates.length === 0" class="template-list-empty">
            {{ searchText ? '没有匹配模板' : '暂无可用模板' }}
          </div>
        </div>
        <div class="template-manage-link">
          <a @click="openTemplateManage"><SettingOutlined /> 管理模板</a>
        </div>
      </div>

      <!-- 右侧：模板预览 -->
      <div class="template-right">
        <div v-if="selectedTemplate" class="template-preview-content">
          <div class="preview-header">
            <h4>{{ selectedTemplate.promptTemplateKey }}</h4>
            <p>{{ selectedTemplate.templateDesc || '暂无描述' }}</p>
          </div>
          <a-divider />
          <div class="preview-section">
            <div class="preview-label">模板内容</div>
            <a-textarea :value="selectedTemplate.template" :rows="12" readonly class="preview-editor" />
          </div>
          <div class="preview-section" v-if="selectedTemplate.variables">
            <div class="preview-label">变量定义</div>
            <div class="preview-vars">
              <a-tag v-for="v in selectedTemplate.variables.split(',')" :key="v" color="blue">{{ v.trim() }}</a-tag>
            </div>
          </div>
          <div class="preview-section" v-if="selectedTemplate.modelConfig">
            <div class="preview-label">模型配置</div>
            <pre class="preview-config">{{ formatConfig(selectedTemplate.modelConfig) }}</pre>
          </div>
          <div class="preview-section" v-if="selectedTemplate.tags">
            <div class="preview-label">标签</div>
            <div class="preview-tags">
              <a-tag v-for="tag in selectedTemplate.tags.split(',')" :key="tag" color="blue">{{ tag.trim() }}</a-tag>
            </div>
          </div>
        </div>
        <div v-else class="template-preview-empty">
          <FileTextOutlined class="empty-icon" />
          <p>请从左侧选择模板查看详情</p>
        </div>
      </div>
    </div>

    <div class="dialog-footer">
      <div class="dialog-footer-left">
        <span class="template-count">共 {{ templates.length }} 个模板</span>
        <span class="template-tip">仅导入提示词内容，不导入模型配置</span>
      </div>
      <div class="dialog-footer-right">
        <button class="btn-cancel" @click="visible = false">取消</button>
        <button class="btn-primary-sm" :disabled="!selectedTemplate" @click="handleImport">
          导入模板
        </button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutlined, SettingOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getPromptTemplates, getPromptTemplate } from '../api/prompt'

const router = useRouter()
const props = defineProps({
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['update:open', 'import'])

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val)
})

const searchText = ref('')
const templates = ref([])
const selectedTemplate = ref(null)
const loading = ref(false)

const filteredTemplates = computed(() => {
  if (!searchText.value) return templates.value
  const kw = searchText.value.toLowerCase()
  return templates.value.filter(t =>
    (t.promptTemplateKey || '').toLowerCase().includes(kw) ||
    (t.templateDesc || '').toLowerCase().includes(kw) ||
    (t.tags || '').toLowerCase().includes(kw)
  )
})

// 加载模板列表
async function loadTemplates() {
  loading.value = true
  try {
    const res = await getPromptTemplates()
    templates.value = res.data || []
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
}

// 选择模板
async function selectTemplate(t) {
  try {
    const res = await getPromptTemplate(t.promptTemplateKey)
    selectedTemplate.value = res.data || t
  } catch {
    selectedTemplate.value = t
  }
}

// 格式化配置JSON
function formatConfig(config) {
  if (!config) return ''
  try {
    const obj = typeof config === 'string' ? JSON.parse(config) : config
    return JSON.stringify(obj, null, 2)
  } catch {
    return config
  }
}

// 导入模板
function handleImport() {
  if (!selectedTemplate.value) return
  emit('import', selectedTemplate.value)
  visible.value = false
  selectedTemplate.value = null
}

// 打开模板管理页面
function openTemplateManage() {
  router.push('/app/prompt-templates')
}

// 监听弹窗打开时加载模板
watch(visible, (val) => {
  if (val && templates.value.length === 0) {
    loadTemplates()
  }
})

onMounted(() => {
  if (visible.value) {
    loadTemplates()
  }
})
</script>

<style scoped>
.template-import-lr {
  display: flex;
  gap: 24px;
  max-height: 500px;
}

/* 左侧模板列表 */
.template-left {
  flex: 0 0 360px;
  display: flex;
  flex-direction: column;
}

.template-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 8px;
}

.template-item {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.template-item:hover {
  border-color: var(--color-link);
}

.template-item.selected {
  border-color: var(--color-link);
  background: #f0f7ff;
}

.template-item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 4px;
}

.template-item-desc {
  font-size: 12px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}

.template-item-tags {
  display: flex;
  gap: 4px;
}

.template-list-empty {
  text-align: center;
  padding: 40px 20px;
  color: #a1a1aa;
  font-size: 13px;
}

.template-manage-link {
  padding-top: 12px;
  border-top: 1px solid #ebebeb;
  margin-top: 12px;
}

.template-manage-link a {
  font-size: 13px;
  color: var(--color-mute);
  display: flex;
  align-items: center;
  gap: 4px;
}

.template-manage-link a:hover {
  color: var(--color-link);
}

/* 右侧模板预览 */
.template-right {
  flex: 1;
  min-width: 0;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
}

.template-preview-content {
  display: flex;
  flex-direction: column;
}

.preview-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
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

.preview-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 8px;
}

.preview-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #fff;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
}

.preview-vars,
.preview-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.preview-config {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: #fff;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid var(--color-hairline);
  overflow-x: auto;
}

.template-preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
  color: #a1a1aa;
}

.template-preview-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.template-preview-empty p {
  font-size: 13px;
}

/* 底部按钮区 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebebeb;
}

.dialog-footer-left {
  display: flex;
  align-items: center;
}

.template-count {
  font-size: 13px;
  color: var(--color-mute);
}
.template-tip {
  font-size: 12px;
  color: #a1a1aa;
  margin-left: 12px;
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
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
</style>