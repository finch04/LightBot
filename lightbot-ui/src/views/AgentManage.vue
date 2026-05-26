<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Agent</h1>
        <p class="page-desc">创建和管理 AI Agent，配置系统提示词和行为</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索 Agent 名称..."
          allow-clear
          style="width: 220px"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData">
          <ReloadOutlined /> 刷新
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新建 Agent
        </button>
      </div>
    </div>

    <div class="agent-grid">
      <div v-for="a in list" :key="a.id" class="agent-card" @click="router.push(`/agents/${a.id}`)">
        <div class="card-top">
          <div class="card-icon" :class="{ 'has-avatar': a.avatar }">
            <img v-if="a.avatar" :src="a.avatar" alt="" class="card-avatar-img" @error="a.avatar = ''" />
            <span v-else>{{ (a.name || 'A')[0] }}</span>
          </div>
          <div class="card-info">
            <h3>{{ a.name }} <span v-if="a.isDefault" class="card-default-tag">默认</span></h3>
            <span class="card-type">{{ agentTypeLabel(a.agentType) }}</span>
          </div>
          <div class="card-actions" @click.stop>
            <button v-if="!a.isDefault" class="btn-icon" title="设为默认" @click="handleSetDefault(a.id)"><StarOutlined /></button>
            <button class="btn-icon" @click="openDialog(a)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(a.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <p class="card-desc">{{ a.description || '暂无描述' }}</p>
        <div class="card-meta">
          <span class="card-status" :class="(a.status?.code || a.status || 'draft').toLowerCase()">
            {{ statusText(a.status?.code || a.status, a.version) }}
          </span>
          <span class="card-time">{{ formatTime(a.createTime) }}</span>
        </div>
      </div>

      <div v-if="list.length === 0" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p v-if="searchText">没有匹配的 Agent</p>
        <p v-else>还没有 Agent，点击右上角创建一个吧</p>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑 Agent' : '新建 Agent'"
      :width="560"
      @ok="handleSubmit"
      :confirm-loading="submitting"
      :maskClosable="false"
    >
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：客服助手" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="Agent 描述（可选）" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="form.agentType" style="width: 100%">
            <a-select-option value="chat">对话型</a-select-option>
            <a-select-option value="workflow">工作流型</a-select-option>
          </a-select>
        </a-form-item>
        <!-- 系统提示词：仅非工作流类型显示 -->
        <a-form-item v-if="form.agentType !== 'workflow'" label="系统提示词">
          <a-textarea v-model:value="form.systemPrompt" :rows="4" placeholder="定义 Agent 的行为和角色..." />
        </a-form-item>
        <!-- 模型提供商：仅新建且非工作流类型显示 -->
        <a-form-item v-if="!form.id && form.agentType !== 'workflow'" label="模型提供商" required>
          <a-select v-model:value="form.providerId" placeholder="选择模型提供商" style="width: 100%">
            <a-select-option v-for="p in providerList" :key="p.id" :value="p.id">
              {{ p.name }} ({{ p.type?.code || p.type }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, EditOutlined, DeleteOutlined, RobotOutlined, SearchOutlined, ReloadOutlined, StarOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getAgents, createAgent, updateAgent, deleteAgent, setDefaultAgent } from '../api/agent'
import { getModelProviders } from '../api/modelProvider'
import { loadAgentStatusLabels, formatAgentStatus } from '../utils/agentStatus'

const router = useRouter()
const list = ref([])
const agentStatusLabels = ref(null)
const searchText = ref('')
const providerList = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', agentType: 'chat', systemPrompt: '', providerId: null })

async function loadData() {
  const params = { pageNum: 1, pageSize: 50 }
  if (searchText.value) params.name = searchText.value
  const res = await getAgents(params)
  list.value = res.data.records || []
}

watch(searchText, () => loadData())

async function loadProviders() {
  try {
    const res = await getModelProviders({ pageNum: 1, pageSize: 50 })
    providerList.value = res.data.records || []
  } catch { /* ignore */ }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      agentType: row.agentType?.code || row.agentType || 'chat',
      systemPrompt: row.systemPrompt || '',
      providerId: null,
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', agentType: 'chat', systemPrompt: '', providerId: null })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  // 工作流类型不需要模型提供商，在LLM节点中配置
  if (!form.id && form.agentType !== 'workflow' && !form.providerId) return message.warning('请选择模型提供商')
  submitting.value = true
  try {
    if (form.id) {
      await updateAgent(form)
      message.success('更新成功')
    } else {
      const config = form.agentType === 'workflow' ? '{}' : JSON.stringify({ providerId: form.providerId })
      await createAgent({ ...form, config })
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
    content: '删除后该 Agent 将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteAgent(id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function handleSetDefault(id) {
  try {
    await setDefaultAgent(id)
    message.success('已设为默认')
    loadData()
  } catch {
    // ignore
  }
}

function agentTypeLabel(t) {
  const code = t?.code || t || ''
  const map = { chat: '对话型', assistant: '对话型', workflow: '工作流型' }
  return map[code] || code || '对话型'
}

function statusText(s, version) {
  return formatAgentStatus(s, version || 0, agentStatusLabels.value)
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
}

onMounted(async () => {
  agentStatusLabels.value = await loadAgentStatusLabels()
  loadData()
  loadProviders()
})
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

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.agent-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.agent-card:hover {
  border-color: #0070f3;
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
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
  background: linear-gradient(135deg, #7928ca, #ff0080);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  overflow: hidden;
  flex-shrink: 0;
}
.card-icon.has-avatar {
  background: #f4f4f5;
}
.card-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
.card-default-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 100px;
  font-weight: 500;
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
.card-desc {
  font-size: 13px;
  color: #71717a;
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-status.draft {
  background: #f5f5f5;
  color: #71717a;
}
.card-status.published {
  background: #dcfce7;
  color: #16a34a;
}
.card-status.published_editing {
  background: #fef3c7;
  color: #d97706;
}
.card-status.archived {
  background: #fef3c7;
  color: #d97706;
}
.card-time {
  font-size: 12px;
  color: #a1a1aa;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: #a1a1aa;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: block;
}
</style>
