<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">MCP Server</h1>
        <p class="page-desc">管理 MCP (Model Context Protocol) 服务</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索 Server 名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData">
          <ReloadOutlined /> 刷新
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新增 Server
        </button>
      </div>
    </div>

    <div class="provider-grid">
      <div v-for="s in list" :key="s.id" class="provider-card">
        <div class="card-top">
          <div class="card-icon">{{ s.name[0] }}</div>
          <div class="card-info">
            <h3>{{ s.name }}</h3>
            <span class="card-type">{{ s.installType?.code || s.installType }}</span>
          </div>
          <div class="card-actions">
            <button class="btn-icon" @click="openDialog(s)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(s.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <div class="card-detail">
          <span v-if="s.description">{{ s.description }}</span>
          <span v-if="s.host">地址: {{ s.host }}</span>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑 MCP Server' : '新增 MCP Server'" :width="560" :footer="null" :maskClosable="false">
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：filesystem-server" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="form.description" placeholder="服务用途说明" />
        </a-form-item>
        <a-form-item label="安装类型" required>
          <a-select v-model:value="form.installType" style="width: 100%" @change="onInstallTypeChange">
            <a-select-option value="npx">NPX (Node.js)</a-select-option>
            <a-select-option value="uvx">UVX (Python)</a-select-option>
            <a-select-option value="sse">SSE (远程服务)</a-select-option>
          </a-select>
        </a-form-item>

        <!-- npx / uvx 配置 -->
        <template v-if="form.installType === 'npx' || form.installType === 'uvx'">
          <a-form-item label="包名">
            <a-input v-model:value="deployForm.packageName" placeholder="如：@modelcontextprotocol/server-filesystem" />
          </a-form-item>
          <a-form-item label="命令参数">
            <a-textarea v-model:value="deployForm.args" :rows="2" placeholder="每行一个参数，如：--allow-read&#10;/tmp" />
          </a-form-item>
          <a-form-item label="环境变量">
            <a-textarea v-model:value="deployForm.env" :rows="2" placeholder="KEY=VALUE，每行一个" />
          </a-form-item>
        </template>

        <!-- sse 配置 -->
        <template v-if="form.installType === 'sse'">
          <a-form-item label="服务地址" required>
            <a-input v-model:value="form.host" placeholder="http://localhost:3001/sse" />
          </a-form-item>
          <a-form-item label="请求头">
            <a-textarea v-model:value="deployForm.headers" :rows="2" placeholder='{"Authorization": "Bearer xxx"}' />
          </a-form-item>
        </template>
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
import { ref, reactive, watch, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getMcpServers, createMcpServer, updateMcpServer, deleteMcpServer } from '../api/mcp'

const list = ref([])
const searchText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', installType: 'npx', host: '' })
const deployForm = reactive({ packageName: '', args: '', env: '', headers: '' })

async function loadData() {
  const params = { pageNum: 1, pageSize: 50 }
  if (searchText.value) params.name = searchText.value
  const res = await getMcpServers(params)
  list.value = res.data.records || []
}

watch(searchText, () => loadData())

function parseDeployConfig(configStr) {
  if (!configStr) return
  try {
    const cfg = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    deployForm.packageName = cfg.packageName || ''
    deployForm.args = Array.isArray(cfg.args) ? cfg.args.join('\n') : (cfg.args || '')
    deployForm.env = cfg.env ? Object.entries(cfg.env).map(([k, v]) => `${k}=${v}`).join('\n') : ''
    deployForm.headers = cfg.headers ? JSON.stringify(cfg.headers, null, 2) : ''
  } catch { /* ignore */ }
}

function buildDeployConfig() {
  const cfg = {}
  if (form.installType === 'npx' || form.installType === 'uvx') {
    if (deployForm.packageName) cfg.packageName = deployForm.packageName
    if (deployForm.args) cfg.args = deployForm.args.split('\n').map(s => s.trim()).filter(Boolean)
    if (deployForm.env) {
      cfg.env = {}
      deployForm.env.split('\n').forEach(line => {
        const idx = line.indexOf('=')
        if (idx > 0) cfg.env[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
      })
    }
  } else if (form.installType === 'sse') {
    if (deployForm.headers) {
      try { cfg.headers = JSON.parse(deployForm.headers) } catch { /* ignore */ }
    }
  }
  return Object.keys(cfg).length > 0 ? JSON.stringify(cfg) : null
}

function onInstallTypeChange() {
  deployForm.packageName = ''
  deployForm.args = ''
  deployForm.env = ''
  deployForm.headers = ''
  form.host = ''
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { ...row, installType: row.installType?.code || row.installType })
    parseDeployConfig(row.deployConfig)
  } else {
    Object.assign(form, { id: null, name: '', description: '', installType: 'npx', host: '' })
    onInstallTypeChange()
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    const data = { ...form, deployConfig: buildDeployConfig() }
    if (form.id) {
      await updateMcpServer(data)
      message.success('更新成功')
    } else {
      await createMcpServer(data)
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
    content: '删除后该 MCP Server 将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteMcpServer(id)
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
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
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
