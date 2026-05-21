<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">模型提供商</h1>
        <p class="page-desc">管理 AI 模型提供商的 API 配置</p>
      </div>
      <button class="btn-primary" @click="openDialog()">
        <PlusOutlined /> 新增提供商
      </button>
    </div>

    <div class="provider-grid">
      <div v-for="p in list" :key="p.id" class="provider-card">
        <div class="card-top">
          <div class="card-icon">{{ p.name[0] }}</div>
          <div class="card-info">
            <h3>{{ p.name }}</h3>
            <span class="card-type">{{ p.type?.code || p.type }}</span>
          </div>
          <div class="card-actions">
            <button class="btn-icon" @click="openDialog(p)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(p.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <div class="card-detail">
          <span>API Key: {{ maskKey(p.apiKey) }}</span>
          <span v-if="p.baseUrl">URL: {{ p.baseUrl }}</span>
        </div>
        <div class="card-footer">
          <button class="btn-link" @click="openModelModal(p)">管理模型</button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑提供商' : '新增提供商'" :width="480" :footer="null" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：通义千问" />
        </a-form-item>
        <a-form-item label="类型" required>
          <a-select v-model:value="form.type" style="width: 100%">
            <a-select-option value="DASHSCOPE">通义千问 (DashScope)</a-select-option>
            <a-select-option value="OPENAI">OpenAI</a-select-option>
            <a-select-option value="DEEPSEEK">DeepSeek</a-select-option>
            <a-select-option value="OLLAMA">Ollama</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="API Key">
          <a-input-password v-model:value="form.apiKey" placeholder="sk-..." />
        </a-form-item>
        <a-form-item label="Base URL">
          <a-input v-model:value="form.baseUrl" placeholder="可选" />
        </a-form-item>

        <!-- 高级选项 -->
        <div class="advanced-toggle" @click="showAdvanced = !showAdvanced">
          <span>高级选项</span>
          <DownOutlined :class="['toggle-icon', { expanded: showAdvanced }]" />
        </div>
        <template v-if="showAdvanced">
          <a-form-item label="模型列表URL">
            <a-input v-model:value="form.modelsEndpoint" placeholder="为空时使用默认地址" />
            <div class="form-hint">自定义获取模型列表的接口地址</div>
          </a-form-item>
          <a-form-item label="额外请求头">
            <a-textarea v-model:value="form.headersJson" placeholder='{"Authorization": "Bearer xxx"}' :rows="3" />
            <div class="form-hint">JSON 格式，为空时不添加额外请求头</div>
            <div v-if="form.headersJson && !isValidJson(form.headersJson)" class="form-error">JSON 格式不正确</div>
          </a-form-item>
          <a-form-item label="扩展配置">
            <a-textarea v-model:value="form.extraJson" placeholder='{"timeout": 30000}' :rows="3" />
            <div class="form-hint">JSON 格式的扩展配置</div>
            <div v-if="form.extraJson && !isValidJson(form.extraJson)" class="form-error">JSON 格式不正确</div>
          </a-form-item>
        </template>
      </a-form>
      <div class="dialog-footer">
        <button class="btn-check" :disabled="checking" @click="handleCheck">
          {{ checking ? '检查中...' : '检查连通性' }}
        </button>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="dialogVisible = false">取消</button>
          <button class="btn-primary-sm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </a-modal>

    <!-- 模型管理弹窗 -->
    <a-modal v-model:open="modelModalVisible" :title="`${currentProvider?.name || ''} - 模型管理`" :width="640" :footer="null" :maskClosable="false">
      <div class="model-modal-header">
        <span class="model-count">共 {{ modelList.length }} 个模型</span>
        <div class="model-modal-actions">
          <button class="btn-fetch" :disabled="fetching" @click="handleFetchModels">
            {{ fetching ? '拉取中...' : '联网拉取' }}
          </button>
          <button class="btn-primary-sm" @click="showAddModel = true; showFetchPanel = false">
            <PlusOutlined /> 手动添加
          </button>
        </div>
      </div>

      <!-- 联网拉取面板 -->
      <div v-if="showFetchPanel" class="fetch-panel">
        <div class="fetch-panel-header">
          <span>从 {{ currentProvider?.name }} 拉取到 {{ fetchedModels.length }} 个模型，请勾选需要添加的：</span>
        </div>
        <!-- 搜索 + 类型筛选 -->
        <div class="fetch-filter-bar">
          <a-input v-model:value="fetchSearchText" placeholder="搜索模型..." allow-clear size="small" style="width: 200px">
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <div class="fetch-type-tabs">
            <button
              v-for="t in fetchTypeTabs"
              :key="t.value"
              :class="['type-tab', { active: fetchTypeFilter === t.value }]"
              @click="fetchTypeFilter = t.value"
            >{{ t.label }}{{ t.count > 0 ? ` (${t.count})` : '' }}</button>
          </div>
        </div>
        <a-checkbox-group v-model:value="selectedFetchedModels" class="fetch-model-grid">
          <div v-for="m in filteredFetchedModels" :key="m.modelId" class="fetch-model-item">
            <a-checkbox :value="m.modelId">
              <span class="fetch-model-id">{{ m.modelId }}</span>
              <span class="fetch-model-type">{{ modelTypeText(m.type) }}</span>
            </a-checkbox>
          </div>
          <div v-if="filteredFetchedModels.length === 0" class="fetch-empty">无匹配模型</div>
        </a-checkbox-group>
        <div class="add-model-actions">
          <button class="btn-cancel" @click="showFetchPanel = false">取消</button>
          <button class="btn-link" @click="selectAllFiltered">全选当前筛选</button>
          <button class="btn-primary-sm" :disabled="selectedFetchedModels.length === 0 || modelSubmitting" @click="handleBatchAddModels">
            {{ modelSubmitting ? '添加中...' : `确认添加 (${selectedFetchedModels.length})` }}
          </button>
        </div>
      </div>

      <!-- 手动添加模型表单 -->
      <div v-if="showAddModel" class="add-model-form">
        <a-form :model="modelForm" :label-col="{ span: 6 }" size="small">
          <a-form-item label="模型标识" required>
            <a-input v-model:value="modelForm.modelId" placeholder="如：qwen-max" />
          </a-form-item>
          <a-form-item label="显示名称" required>
            <a-input v-model:value="modelForm.name" placeholder="如：通义千问 Max" />
          </a-form-item>
          <a-form-item label="模型类型" required>
            <a-select v-model:value="modelForm.type" style="width: 100%">
              <a-select-option value="llm">对话模型</a-select-option>
              <a-select-option value="embedding">嵌入模型</a-select-option>
              <a-select-option value="rerank">重排模型</a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
        <div class="add-model-actions">
          <button class="btn-cancel" @click="showAddModel = false">取消</button>
          <button class="btn-primary-sm" :disabled="modelSubmitting" @click="handleAddModel">
            {{ modelSubmitting ? '添加中...' : '确认添加' }}
          </button>
        </div>
      </div>

      <!-- 模型列表 -->
      <div class="model-list" v-if="modelList.length > 0">
        <div v-for="m in modelList" :key="m.id" class="model-item">
          <div class="model-info">
            <span class="model-id">{{ m.modelId }}</span>
            <span class="model-name">{{ m.name }}</span>
            <span class="model-type-tag">{{ modelTypeText(m.type?.code || m.type) }}</span>
          </div>
          <button class="btn-icon danger" @click="handleDeleteModel(m.id)"><DeleteOutlined /></button>
        </div>
      </div>
      <div v-else-if="!showFetchPanel && !showAddModel" class="model-empty">暂无模型，点击上方按钮添加</div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, DownOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getModelProviders, createModelProvider, updateModelProvider, deleteModelProvider, checkModelProviderByForm, fetchProviderModels } from '../api/modelProvider'
import { getModelsByProvider, createModel, deleteModel } from '../api/model'

const list = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const checking = ref(false)
const form = reactive({ id: null, name: '', type: 'DASHSCOPE', apiKey: '', baseUrl: '', modelsEndpoint: '', headersJson: '', extraJson: '' })
const showAdvanced = ref(false)

// 模型管理
const modelModalVisible = ref(false)
const currentProvider = ref(null)
const modelList = ref([])
const showAddModel = ref(false)
const modelSubmitting = ref(false)
const modelForm = reactive({ modelId: '', name: '', type: 'llm' })

// 联网拉取
const fetching = ref(false)
const showFetchPanel = ref(false)
const fetchedModels = ref([])
const selectedFetchedModels = ref([])
const fetchSearchText = ref('')
const fetchTypeFilter = ref('all')

const filteredFetchedModels = computed(() => {
  let result = fetchedModels.value
  if (fetchTypeFilter.value !== 'all') {
    result = result.filter(m => m.type === fetchTypeFilter.value)
  }
  if (fetchSearchText.value) {
    const keyword = fetchSearchText.value.toLowerCase()
    result = result.filter(m => m.modelId.toLowerCase().includes(keyword))
  }
  return result
})

const fetchTypeTabs = computed(() => {
  const counts = { all: fetchedModels.value.length }
  for (const m of fetchedModels.value) {
    counts[m.type] = (counts[m.type] || 0) + 1
  }
  return [
    { value: 'all', label: '全部', count: counts.all },
    { value: 'llm', label: '对话', count: counts.llm || 0 },
    { value: 'embedding', label: '嵌入', count: counts.embedding || 0 },
    { value: 'rerank', label: '重排', count: counts.rerank || 0 },
    { value: 'tts', label: '语音合成', count: counts.tts || 0 },
    { value: 'stt', label: '语音识别', count: counts.stt || 0 },
  ].filter(t => t.count > 0 || t.value === 'all')
})

function selectAllFiltered() {
  selectedFetchedModels.value = filteredFetchedModels.value.map(m => m.modelId)
}

async function loadData() {
  const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
  list.value = res.data.records || []
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      type: row.type?.code || row.type,
      apiKey: row.apiKey || '',
      baseUrl: row.baseUrl || '',
      modelsEndpoint: row.modelsEndpoint || '',
      headersJson: row.headersJson || '',
      extraJson: row.extraJson || '',
    })
    showAdvanced.value = false
  } else {
    Object.assign(form, { id: null, name: '', type: 'DASHSCOPE', apiKey: '', baseUrl: '', modelsEndpoint: '', headersJson: '', extraJson: '' })
    showAdvanced.value = false
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  if (form.headersJson && !isValidJson(form.headersJson)) return message.warning('额外请求头 JSON 格式不正确')
  if (form.extraJson && !isValidJson(form.extraJson)) return message.warning('扩展配置 JSON 格式不正确')
  submitting.value = true
  try {
    if (form.id) {
      await updateModelProvider(form)
      message.success('更新成功')
    } else {
      await createModelProvider(form)
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
    content: '删除后该模型提供商将无法恢复，关联的 Agent 将无法使用，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteModelProvider(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function handleCheck() {
  if (!form.apiKey?.trim() && form.type !== 'ollama') {
    message.warning('请先填写 API Key')
    return
  }
  checking.value = true
  try {
    const res = await checkModelProviderByForm({
      type: form.type,
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
    })
    message.success(res.data || '连接成功')
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    checking.value = false
  }
}

function maskKey(key) {
  if (!key || key.length < 10) return '***'
  return key.substring(0, 6) + '****' + key.substring(key.length - 4)
}

function isValidJson(str) {
  if (!str || !str.trim()) return true
  try {
    const parsed = JSON.parse(str)
    return typeof parsed === 'object' && parsed !== null
  } catch {
    return false
  }
}

// ========== 模型管理 ==========

async function openModelModal(provider) {
  currentProvider.value = provider
  showAddModel.value = false
  showFetchPanel.value = false
  Object.assign(modelForm, { modelId: '', name: '', type: 'llm' })
  modelModalVisible.value = true
  await loadModels(provider.id)
}

async function loadModels(providerId) {
  const res = await getModelsByProvider(providerId)
  modelList.value = res.data || []
}

async function handleAddModel() {
  if (!modelForm.modelId.trim()) return message.warning('请输入模型标识')
  if (!modelForm.name.trim()) return message.warning('请输入显示名称')
  modelSubmitting.value = true
  try {
    await createModel({
      providerId: currentProvider.value.id,
      modelId: modelForm.modelId,
      name: modelForm.name,
      type: modelForm.type,
    })
    message.success('添加成功')
    showAddModel.value = false
    Object.assign(modelForm, { modelId: '', name: '', type: 'llm' })
    await loadModels(currentProvider.value.id)
  } finally {
    modelSubmitting.value = false
  }
}

function handleDeleteModel(id) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后该模型将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteModel(id)
      message.success('删除成功')
      await loadModels(currentProvider.value.id)
    },
  })
}

async function handleFetchModels() {
  fetching.value = true
  showAddModel.value = false
  try {
    const res = await fetchProviderModels(currentProvider.value.id)
    fetchedModels.value = res.data || []
    selectedFetchedModels.value = []
    showFetchPanel.value = true
    if (fetchedModels.value.length === 0) {
      message.info('该提供商下未找到可用模型')
    }
  } catch (e) {
    // interceptor已处理错误提示
  } finally {
    fetching.value = false
  }
}

async function handleBatchAddModels() {
  if (selectedFetchedModels.value.length === 0) return
  modelSubmitting.value = true
  try {
    // 建立 modelId → type 映射
    const typeMap = {}
    for (const m of fetchedModels.value) {
      typeMap[m.modelId] = m.type
    }
    let successCount = 0
    for (const modelId of selectedFetchedModels.value) {
      try {
        await createModel({
          providerId: currentProvider.value.id,
          modelId,
          name: modelId,
          type: typeMap[modelId] || 'llm',
        })
        successCount++
      } catch {
        // 跳过已存在的模型
      }
    }
    message.success(`成功添加 ${successCount} 个模型`)
    showFetchPanel.value = false
    fetchSearchText.value = ''
    fetchTypeFilter.value = 'all'
    await loadModels(currentProvider.value.id)
  } finally {
    modelSubmitting.value = false
  }
}

function modelTypeText(type) {
  const map = { llm: '对话', embedding: '嵌入', rerank: '重排', tts: '语音合成', stt: '语音识别' }
  return map[type] || type
}

onMounted(loadData)
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
  margin-bottom: 32px;
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
.btn-primary:hover {
  background: #27272a;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.provider-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #171717;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.card-info {
  flex: 1;
}
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
}
.card-type {
  font-size: 12px;
  color: #71717a;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-actions {
  display: flex;
  gap: 4px;
}
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
}
.btn-icon:hover {
  background: #f5f5f5;
}
.btn-icon.danger:hover {
  color: #ee0000;
  background: #f7d4d6;
}
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #a1a1aa;
  margin-bottom: 12px;
  flex: 1;
}
.card-footer {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}
.btn-link {
  background: none;
  border: none;
  color: #0070f3;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.btn-link:hover {
  text-decoration: underline;
}

/* 模型管理弹窗 */
.model-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.model-count {
  font-size: 13px;
  color: #71717a;
}
.add-model-form {
  background: #f9f9f9;
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.add-model-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}
.model-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #ebebeb;
  border-radius: 8px;
}
.model-item:hover {
  border-color: #d4d4d8;
}
.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.model-id {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  font-family: monospace;
}
.model-name {
  font-size: 13px;
  color: #71717a;
}
.model-type-tag {
  font-size: 11px;
  color: #71717a;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 4px;
}
.model-empty {
  text-align: center;
  padding: 32px;
  color: #a1a1aa;
  font-size: 13px;
}
.model-modal-actions {
  display: flex;
  gap: 8px;
}
.btn-fetch {
  padding: 6px 14px;
  background: #fff;
  color: #171717;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-fetch:hover:not(:disabled) {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-fetch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 联网拉取面板 */
.fetch-panel {
  background: #f9f9f9;
  border: 1px solid #ebebeb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
.fetch-panel-header {
  font-size: 13px;
  color: #52525b;
  margin-bottom: 12px;
}
.fetch-filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.fetch-type-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.type-tab {
  padding: 3px 10px;
  border: 1px solid #d4d4d8;
  border-radius: 100px;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  color: #71717a;
  transition: all 0.15s;
}
.type-tab:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.type-tab.active {
  background: #171717;
  border-color: #171717;
  color: #fff;
}
.fetch-model-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 12px;
}
.fetch-model-item {
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 6px;
}
.fetch-model-item:hover {
  border-color: #d4d4d8;
}
.fetch-model-id {
  font-family: monospace;
  font-size: 13px;
  color: #171717;
}
.fetch-model-type {
  font-size: 11px;
  color: #71717a;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 8px;
}
.fetch-empty {
  text-align: center;
  padding: 24px;
  color: #a1a1aa;
  font-size: 13px;
}

/* 弹窗底部 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}
.dialog-footer-right {
  display: flex;
  gap: 8px;
}
.btn-check {
  padding: 6px 14px;
  background: #fff;
  color: #171717;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.btn-check:hover:not(:disabled) {
  border-color: #0070f3;
  color: #0070f3;
}
.btn-check:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-cancel {
  padding: 6px 14px;
  background: #fff;
  color: #71717a;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: #171717;
  color: #171717;
}
.btn-primary-sm {
  padding: 6px 14px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}

/* 高级选项 */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  cursor: pointer;
  font-size: 13px;
  color: #71717a;
  user-select: none;
  border-top: 1px dashed #ebebeb;
  margin-top: 4px;
}
.advanced-toggle:hover {
  color: #0070f3;
}
.toggle-icon {
  font-size: 10px;
  transition: transform 0.2s;
}
.toggle-icon.expanded {
  transform: rotate(180deg);
}
.form-hint {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 2px;
}
.form-error {
  font-size: 12px;
  color: #dc2626;
  margin-top: 2px;
}
</style>
