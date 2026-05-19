<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Agent 管理</h1>
        <p class="page-desc">创建和管理 AI Agent，配置系统提示词和行为</p>
      </div>
      <button class="btn-primary" @click="openDialog()">
        <PlusOutlined /> 新建 Agent
      </button>
    </div>

    <div class="agent-grid">
      <div v-for="a in list" :key="a.id" class="agent-card" @click="router.push(`/agents/${a.id}`)">
        <div class="card-top">
          <div class="card-icon">{{ (a.name || 'A')[0] }}</div>
          <div class="card-info">
            <h3>{{ a.name }}</h3>
            <span class="card-type">{{ a.agentType?.code || a.agentType || 'CHAT' }}</span>
          </div>
          <div class="card-actions" @click.stop>
            <button class="btn-icon" @click="openDialog(a)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(a.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <p class="card-desc">{{ a.description || '暂无描述' }}</p>
        <div class="card-meta">
          <span class="card-status" :class="(a.status?.code || a.status || 'draft').toLowerCase()">
            {{ statusText(a.status?.code || a.status) }}
          </span>
          <span class="card-time">{{ formatTime(a.createTime) }}</span>
        </div>
      </div>

      <div v-if="list.length === 0" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p>还没有 Agent，点击右上角创建一个吧</p>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑 Agent' : '新建 Agent'"
      :width="560"
      @ok="handleSubmit"
      :confirm-loading="submitting"
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
            <a-select-option value="CHAT">对话型</a-select-option>
            <a-select-option value="TASK">任务型</a-select-option>
            <a-select-option value="WORKFLOW">工作流</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="系统提示词">
          <a-textarea v-model:value="form.systemPrompt" :rows="4" placeholder="定义 Agent 的行为和角色..." />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, EditOutlined, DeleteOutlined, RobotOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAgents, createAgent, updateAgent, deleteAgent } from '../api/agent'

const router = useRouter()
const list = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, name: '', description: '', agentType: 'CHAT', systemPrompt: '' })

async function loadData() {
  const res = await getAgents({ pageNum: 1, pageSize: 50 })
  list.value = res.data.records || []
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      agentType: row.agentType?.code || row.agentType || 'CHAT',
      systemPrompt: row.systemPrompt || '',
    })
  } else {
    Object.assign(form, { id: null, name: '', description: '', agentType: 'CHAT', systemPrompt: '' })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) return message.warning('请输入名称')
  submitting.value = true
  try {
    if (form.id) {
      await updateAgent(form)
      message.success('更新成功')
    } else {
      await createAgent(form)
      message.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  await deleteAgent(id)
  message.success('删除成功')
  loadData()
}

function statusText(s) {
  const map = { draft: '草稿', published: '已发布', archived: '已归档' }
  return map[s] || s || '草稿'
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
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
