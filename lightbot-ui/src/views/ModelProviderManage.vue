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
      </div>
    </div>

    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑提供商' : '新增提供商'" :width="480" @ok="handleSubmit" :confirm-loading="submitting">
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
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getModelProviders, createModelProvider, updateModelProvider, deleteModelProvider } from '../api/modelProvider'

const list = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', type: 'DASHSCOPE', apiKey: '', baseUrl: '', config: '' })

async function loadData() {
  const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
  list.value = res.data.records || []
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { ...row, type: row.type?.code || row.type })
  } else {
    Object.assign(form, { id: null, name: '', type: 'DASHSCOPE', apiKey: '', baseUrl: '', config: '' })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
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

async function handleDelete(id) {
  await deleteModelProvider(id)
  message.success('删除成功')
  loadData()
}

function maskKey(key) {
  if (!key || key.length < 10) return '***'
  return key.substring(0, 6) + '****' + key.substring(key.length - 4)
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
}
</style>
