<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">模型提供商</h1>
        <p class="page-desc">管理 AI 模型提供商的 API 配置</p>
      </div>
      <button class="btn-primary" @click="openDialog()">
        <el-icon><Plus /></el-icon> 新增提供商
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
            <button class="btn-icon" @click="openDialog(p)"><el-icon><Edit /></el-icon></button>
            <button class="btn-icon danger" @click="handleDelete(p.id)"><el-icon><Delete /></el-icon></button>
          </div>
        </div>
        <div class="card-detail">
          <span>API Key: {{ maskKey(p.apiKey) }}</span>
          <span v-if="p.baseUrl">URL: {{ p.baseUrl }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑提供商' : '新增提供商'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：通义千问" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="通义千问 (DashScope)" value="DASHSCOPE" />
            <el-option label="OpenAI" value="OPENAI" />
            <el-option label="DeepSeek" value="DEEPSEEK" />
            <el-option label="Ollama" value="OLLAMA" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" placeholder="sk-..." show-password />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
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
  if (!form.name.trim()) return ElMessage.warning('请输入名称')
  submitting.value = true
  try {
    if (form.id) {
      await updateModelProvider(form)
      ElMessage.success('更新成功')
    } else {
      await createModelProvider(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  await deleteModelProvider(id)
  ElMessage.success('删除成功')
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
  border-radius: 8px;
  font-size: 14px;
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
  border: 1px solid #e4e4e7;
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
  background: #f4f4f5;
  padding: 2px 8px;
  border-radius: 4px;
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
  background: #f4f4f5;
}
.btn-icon.danger:hover {
  color: #dc2626;
  background: #fee2e2;
}
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #a1a1aa;
}
</style>
