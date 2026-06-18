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
        <a-select
          v-model:value="filterAgentType"
          placeholder="全部类型"
          allow-clear
          style="width: 130px"
          @change="loadData"
        >
          <a-select-option value="chat">对话型</a-select-option>
          <a-select-option value="workflow">工作流型</a-select-option>
        </a-select>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <a-tooltip title="示例工作流">
          <button class="btn-outline" @click="openExampleModal">
            <ExperimentOutlined />
          </button>
        </a-tooltip>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新建 Agent
        </button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="agent-grid">
      <div v-for="a in list" :key="a.id" class="agent-card" @click="router.push(`/app/agents/${a.id}`)">
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

      <div v-if="list.length === 0 && !loading" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p v-if="searchText">没有匹配的 Agent</p>
        <p v-else>还没有 Agent，点击右上角创建一个吧</p>
      </div>
    </div>
    </a-spin>

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
          <a-input v-model:value="form.name" placeholder="如：客服助手（不超过50字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="Agent 描述（不超过200字）" :maxlength="200" show-count />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="form.agentType" style="width: 100%">
            <a-select-option value="chat">对话型</a-select-option>
            <a-select-option value="workflow">工作流型</a-select-option>
          </a-select>
        </a-form-item>
        <!-- 模型：仅新建且非工作流类型显示 -->
        <a-form-item v-if="!form.id && form.agentType !== 'workflow'" label="模型" required>
          <ModelSelect v-model="form.model" placeholder="选择模型" @change="onModelChange" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 示例工作流弹窗 -->
    <a-modal
      v-model:open="exampleModalVisible"
      title="示例工作流 Agent"
      :width="640"
      :footer="null"
      :maskClosable="false"
    >
      <div class="example-desc">选择一个内置示例，快速创建工作流 Agent 并学习各节点的使用方式</div>
      <div class="example-list">
        <div v-for="ex in workflowExamples" :key="ex.key" class="example-card">
          <div class="example-card-header">
            <span class="example-name">{{ ex.name }}</span>
            <a-button type="primary" size="small" :loading="exampleCreating === ex.key" @click="handleCreateExample(ex.key)">
              生成
            </a-button>
          </div>
          <div class="example-desc-text">{{ ex.description }}</div>
          <div class="example-tags">
            <a-tag v-for="tag in ex.nodeTypeTags" :key="tag" color="blue">{{ tag }}</a-tag>
          </div>
        </div>
      </div>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, EditOutlined, DeleteOutlined, RobotOutlined, SearchOutlined, ReloadOutlined, StarOutlined, ExperimentOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getAgents, createAgent, updateAgent, deleteAgent, setDefaultAgent, listWorkflowExamples, createFromWorkflowExample } from '../api/agent'
import { loadAgentStatusLabels, formatAgentStatus } from '../utils/agentStatus'
import ModelSelect from '../components/ModelSelect.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const agentStatusLabels = ref(null)
const searchText = ref('')
const filterAgentType = ref(undefined)
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', agentType: 'chat', model: null })
const selectedProviderId = ref(null)
const exampleModalVisible = ref(false)
const workflowExamples = ref([])
const exampleCreating = ref(null)

function onModelChange({ providerId }) {
  selectedProviderId.value = providerId
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.name = searchText.value
    if (filterAgentType.value) params.agentType = filterAgentType.value
    const res = await getAgents(params)
    list.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

watch(searchText, () => loadData())

function openDialog(row) {
  selectedProviderId.value = null
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      agentType: row.agentType?.code || row.agentType || 'chat',
      model: null,
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', agentType: 'chat', model: null })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  // 工作流类型不需要模型，在LLM节点中配置
  if (!form.id && form.agentType !== 'workflow' && !form.model) return message.warning('请选择模型')
  submitting.value = true
  try {
    if (form.id) {
      await updateAgent(form)
      message.success('更新成功')
    } else {
      // form.model 格式为 providerId:modelId，拆分后存入 config
      const config = form.agentType === 'workflow' ? '{}' : JSON.stringify({ providerId: selectedProviderId.value })
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

function handleSetDefault(id) {
  Modal.confirm({
    title: '设为默认智能体',
    content: '设置为默认智能体后，该智能体将对所有用户公开访问。确认继续？',
    okText: '确认',
    cancelText: '取消',
    async onOk() {
      await setDefaultAgent(id)
      message.success('已设为默认')
      loadData()
    },
  })
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

async function openExampleModal() {
  try {
    const res = await listWorkflowExamples()
    workflowExamples.value = res.data || []
    exampleModalVisible.value = true
  } catch {
    message.error('加载示例列表失败')
  }
}

function handleCreateExample(key) {
  const ex = workflowExamples.value.find(e => e.key === key)
  Modal.confirm({
    title: '生成示例工作流 Agent',
    content: `即将生成「${ex?.name || key}」。\n\n注意：示例中的部分节点需要手动配置实际内容（如绑定知识库、选择工具、选择模型等），生成后请进入工作流编辑器逐一完善。`,
    okText: '确认生成',
    cancelText: '取消',
    async onOk() {
      exampleCreating.value = key
      try {
        const res = await createFromWorkflowExample(key)
        message.success('示例 Agent 创建成功')
        exampleModalVisible.value = false
        loadData()
        router.push(`/app/agents/${res.data.id}`)
      } catch {
        message.error('创建失败')
      } finally {
        exampleCreating.value = null
      }
    },
  })
}

onMounted(async () => {
  agentStatusLabels.value = await loadAgentStatusLabels()
  loadData()
})
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
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
.example-desc {
  color: #71717a;
  font-size: 13px;
  margin-bottom: 16px;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.example-card {
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 14px 16px;
  transition: border-color 0.2s;
}
.example-card:hover {
  border-color: #1677ff;
}
.example-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.example-name {
  font-weight: 500;
  font-size: 14px;
  color: #171717;
}
.example-desc-text {
  font-size: 12px;
  color: #71717a;
  margin-bottom: 10px;
  line-height: 1.6;
}
.example-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

</style>
