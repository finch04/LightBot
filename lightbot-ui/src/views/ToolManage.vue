<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">工具管理</h1>
        <p class="page-desc">管理 Agent 可使用的工具（Tool）</p>
      </div>
      <button class="btn-primary" @click="openDialog()">
        <PlusOutlined /> 新增工具
      </button>
    </div>

    <div class="provider-grid">
      <div v-for="t in list" :key="t.id" class="provider-card">
        <div class="card-top">
          <div class="card-icon" :style="{ background: typeColors[t.toolType?.code || t.toolType] || '#171717' }">
            {{ (t.displayName || t.name || '?')[0].toUpperCase() }}
          </div>
          <div class="card-info">
            <h3>{{ t.displayName || t.name }}</h3>
            <span class="card-type">{{ toolTypeLabels[t.toolType?.code || t.toolType] || t.toolType }}</span>
          </div>
          <div class="card-actions">
            <button class="btn-icon" @click="openDialog(t)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(t.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <div class="card-detail">
          <span v-if="t.name" class="detail-tag">标识: {{ t.name }}</span>
          <span v-if="t.description">{{ t.description }}</span>
          <span v-if="t.endpointUrl">端点: {{ t.endpointUrl }}</span>
        </div>
      </div>
      <div v-if="list.length === 0" class="empty-tip">暂无工具，点击右上角新增</div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑工具' : '新增工具'" :width="640" :footer="null" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="工具标识" required>
          <a-input v-model:value="form.name" placeholder="如：http_request（英文，唯一标识）" />
        </a-form-item>
        <a-form-item label="显示名称">
          <a-input v-model:value="form.displayName" placeholder="如：HTTP 请求" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="工具用途说明，供 Agent 理解" />
        </a-form-item>
        <a-form-item label="工具类型" required>
          <a-select v-model:value="form.toolType" style="width: 100%">
            <a-select-option value="builtin">内置</a-select-option>
            <a-select-option value="custom">自定义</a-select-option>
            <a-select-option value="api">API调用</a-select-option>
            <a-select-option value="mcp">MCP协议</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="端点地址">
          <a-input v-model:value="form.endpointUrl" placeholder="API 端点 URL（API 类型必填）" />
        </a-form-item>
        <a-form-item label="认证类型">
          <a-select v-model:value="form.authType" style="width: 100%">
            <a-select-option value="none">无认证</a-select-option>
            <a-select-option value="api_key">API Key</a-select-option>
            <a-select-option value="oauth">OAuth</a-select-option>
            <a-select-option value="bearer">Bearer Token</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="输入Schema">
          <a-textarea v-model:value="form.inputSchema" :rows="4" placeholder='JSON Schema，如：{"type":"object","properties":{...}}' />
        </a-form-item>
        <a-form-item label="输出Schema">
          <a-textarea v-model:value="form.outputSchema" :rows="3" placeholder="输出参数 JSON Schema（可选）" />
        </a-form-item>
        <a-form-item label="认证配置">
          <a-textarea v-model:value="form.authConfig" :rows="2" placeholder='JSON 格式，如：{"apiKey":"xxx"}' />
        </a-form-item>
        <a-form-item label="扩展配置">
          <a-textarea v-model:value="form.config" :rows="2" placeholder="JSON 格式的扩展配置（可选）" />
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
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getTools, createTool, updateTool, deleteTool } from '../api/tool'

const toolTypeLabels = { builtin: '内置', custom: '自定义', api: 'API调用', mcp: 'MCP协议' }
const typeColors = { builtin: '#171717', custom: '#0070f3', api: '#10b981', mcp: '#8b5cf6' }

const list = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  id: null, name: '', displayName: '', description: '',
  toolType: 'custom', endpointUrl: '', authType: 'none',
  inputSchema: '', outputSchema: '', authConfig: '', config: '',
})

async function loadData() {
  try {
    const res = await getTools({ pageNum: 1, pageSize: 50 })
    list.value = res.data.records || []
  } catch (e) {
    // interceptor handles error
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      ...row,
      toolType: row.toolType?.code || row.toolType || 'custom',
      authType: row.authType?.code || row.authType || 'none',
    })
  } else {
    Object.assign(form, {
      id: null, name: '', displayName: '', description: '',
      toolType: 'custom', endpointUrl: '', authType: 'none',
      inputSchema: '', outputSchema: '', authConfig: '', config: '',
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入工具标识')
  submitting.value = true
  try {
    const data = { ...form }
    if (form.id) {
      await updateTool(data)
      message.success('更新成功')
    } else {
      await createTool(data)
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
    content: '删除后该工具将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteTool(id)
      message.success('删除成功')
      loadData()
    },
  })
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
  flex-shrink: 0;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.detail-tag {
  display: inline-block;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #71717a;
}
.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  color: #a1a1aa;
  font-size: 14px;
}

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
</style>
