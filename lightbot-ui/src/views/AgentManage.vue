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
        <a-tooltip title="消息反馈记录">
          <button class="btn-outline" @click="feedbackOpen = true">
            <LikeOutlined />
          </button>
        </a-tooltip>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新建 Agent
        </button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="agent-grid">
      <EntityCard
        v-for="a in list"
        :key="a.id"
        type="agent"
        :name="a.name"
        @click="router.push(`/app/agents/${a.id}`)"
      >
        <template #icon>
          <img v-if="a.avatar" :src="a.avatar" alt="" class="card-avatar-img" @error="a.avatar = ''" />
          <span v-else>{{ (a.name || 'A')[0] }}</span>
        </template>
        <template #info>
          <a-tooltip :title="a.name">
            <h3>{{ a.name }} <span v-if="a.isDefault" class="card-default-tag">默认</span></h3>
          </a-tooltip>
          <span class="card-type" :class="'card-type--' + (a.agentType?.code || a.agentType || 'chat')">{{ agentTypeLabel(a.agentType) }}</span>
        </template>
        <template #actions>
          <a-tooltip title="编辑">
            <button class="btn-icon" @click.stop="openDialog(a)"><EditOutlined /></button>
          </a-tooltip>
          <a-dropdown :trigger="['click']">
            <button class="btn-icon" @click.stop.prevent><MoreOutlined /></button>
            <template #overlay>
              <a-menu>
                <a-menu-item v-if="!a.isDefault" @click="handleSetDefault(a.id)">
                  <StarOutlined style="margin-right: 6px" /> 设为默认
                </a-menu-item>
                <a-menu-item @click="handleClone(a.id)">
                  <CopyOutlined style="margin-right: 6px" /> 复制
                </a-menu-item>
                <a-menu-item @click="handleDelete(a.id)" class="menu-danger">
                  <DeleteOutlined style="margin-right: 6px" /> 删除
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <a-tooltip v-if="a.description" :title="a.description" placement="top" :overlay-style="{ maxWidth: '400px' }">
          <p class="card-desc">{{ a.description }}</p>
        </a-tooltip>
        <p v-else class="card-desc">暂无描述</p>
        <template #meta>
          <span class="card-status" :class="(a.status?.code || a.status || 'draft').toLowerCase()">
            {{ statusText(a.status?.code || a.status, a.version) }}
          </span>
          <span class="card-time">{{ formatTime(a.createTime) }}</span>
        </template>
      </EntityCard>

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
          <a-input v-model:value="form.name" placeholder="如：客服助手（不超过30字）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="Agent 描述（不超过50字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="form.agentType" style="width: 100%">
            <a-select-option value="chat">对话型</a-select-option>
            <a-select-option value="workflow">工作流型</a-select-option>
          </a-select>
        </a-form-item>
        <!-- 模型：仅新建且非工作流类型显示 -->
        <a-form-item v-if="!form.id && form.agentType !== 'workflow'" label="模型" required>
          <ModelSelect
            v-model:provider-id="createProviderId"
            v-model:model-id="createModelId"
            placeholder="选择模型"
          />
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
      class="example-workflow-modal"
      :body-style="{ padding: 0, overflow: 'hidden' }"
    >
      <div class="example-modal-body">
        <div class="example-desc">选择一个内置示例，快速创建工作流 Agent 并学习各节点的使用方式</div>
        <div class="example-list-scroll">
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
        </div>
      </div>
    </a-modal>

    <FeedbackHistory v-model:open="feedbackOpen" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, EditOutlined, DeleteOutlined, CopyOutlined, RobotOutlined, SearchOutlined, ReloadOutlined, StarOutlined, ExperimentOutlined, MoreOutlined, LikeOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getAgents, createAgent, updateAgent, deleteAgent, cloneAgent, setDefaultAgent, listWorkflowExamples, createFromWorkflowExample } from '../api/agent'
import { formatDate as formatTime } from '../utils/format'
import FeedbackHistory from './FeedbackHistory.vue'
import { loadAgentStatusLabels, formatAgentStatus } from '../utils/agentStatus'
import ModelSelect from '../components/ModelSelect.vue'
import EntityCard from '../components/EntityCard.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const agentStatusLabels = ref(null)
const searchText = ref('')
const filterAgentType = ref(undefined)
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', agentType: 'chat' })
const createProviderId = ref(null)
const createModelId = ref(null)
const exampleModalVisible = ref(false)
const workflowExamples = ref([])
const exampleCreating = ref(null)
const feedbackOpen = ref(false)

function openDialog(row) {
  createProviderId.value = null
  createModelId.value = null
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      agentType: row.agentType?.code || row.agentType || 'chat',
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', agentType: 'chat' })
  }
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50, includeDefault: false }
    if (searchText.value) params.name = searchText.value
    if (filterAgentType.value) params.agentType = filterAgentType.value
    const res = await getAgents(params)
    list.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

let searchDebounceTimer = null
watch(searchText, () => {
  clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => loadData(), 300)
})

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  // 工作流类型不需要模型，在LLM节点中配置
  if (!form.id && form.agentType !== 'workflow' && (!createProviderId.value || !createModelId.value)) {
    return message.warning('请选择模型')
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateAgent(form)
      message.success('更新成功')
    } else {
      const config = form.agentType === 'workflow'
        ? '{}'
        : JSON.stringify({ providerId: createProviderId.value, modelId: createModelId.value })
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

async function handleClone(id) {
  try {
    const res = await cloneAgent(id)
    message.success(`已复制: ${res.data?.name || '新 Agent'}`)
    loadData()
  } catch {
    // interceptor handled
  }
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
.page { overflow-x: hidden; }

:deep(.menu-danger) {
  color: var(--color-error) !important;
}
:deep(.menu-danger:hover) {
  background: var(--color-error-soft) !important;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
/* 头像需要 overflow: hidden 裁剪图片 */
:deep(.card-icon) {
  overflow: hidden;
}
.card-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-type {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 4px;
  letter-spacing: 0.3px;
}
.card-type--chat,
.card-type--assistant {
  color: #1d4ed8;
  background: var(--color-info-bg);
}
.card-type--workflow {
  color: #7c3aed;
  background: var(--color-purple-bg);
}
.card-default-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: var(--color-info-bg);
  color: #2563eb;
  border-radius: 100px;
  font-weight: 500;
}
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: fit-content;
  max-width: 100%;
}
.card-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 100px;
}
.card-status.draft {
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
}
.card-status.published {
  background: var(--color-success-bg);
  color: #16a34a;
}
.card-status.published_editing {
  background: var(--color-warn-bg-deep);
  color: #d97706;
}
.card-status.archived {
  background: var(--color-warn-bg-deep);
  color: #d97706;
}
.card-time {
  font-size: 12px;
  color: var(--color-mute);
}
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
.example-desc {
  flex-shrink: 0;
  color: var(--color-mute);
  font-size: 13px;
  padding: 0 24px 16px;
  margin-bottom: 0;
}
.example-modal-body {
  display: flex;
  flex-direction: column;
  max-height: 65vh;
}
.example-list-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 20px 24px;
  margin-right: 8px;
  scrollbar-gutter: stable;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 8px;
}
.example-card {
  border: 1px solid var(--color-hairline);
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
  color: var(--color-ink);
}
.example-desc-text {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 10px;
  line-height: 1.6;
}
.example-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

</style>
