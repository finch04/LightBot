<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Skill 管理</h1>
        <p class="page-desc">管理 Agent 的技能（Skill），每个 Skill 绑定一个工具并定义提示词模板</p>
      </div>
      <button class="btn-primary" @click="openDialog()">
        <PlusOutlined /> 新增 Skill
      </button>
    </div>

    <!-- Agent 筛选 -->
    <div class="filter-bar">
      <span class="filter-label">按 Agent 筛选：</span>
      <a-select v-model:value="filterAgentId" placeholder="全部 Agent" allow-clear style="width: 240px" @change="loadData">
        <a-select-option v-for="a in agentList" :key="a.id" :value="a.id">{{ a.name }}</a-select-option>
      </a-select>
    </div>

    <div class="provider-grid">
      <div v-for="s in list" :key="s.id" class="provider-card">
        <div class="card-top">
          <div class="card-icon">
            {{ (s.name || '?')[0].toUpperCase() }}
          </div>
          <div class="card-info">
            <h3>{{ s.name }}</h3>
            <span class="card-type">排序: {{ s.sortOrder ?? 0 }}</span>
          </div>
          <div class="card-actions">
            <button class="btn-icon" @click="openDialog(s)"><EditOutlined /></button>
            <button class="btn-icon danger" @click="handleDelete(s.id)"><DeleteOutlined /></button>
          </div>
        </div>
        <div class="card-detail">
          <span v-if="s.description">{{ s.description }}</span>
          <span v-if="s.toolId" class="detail-tag">关联工具ID: {{ s.toolId }}</span>
          <span v-if="s.promptTemplate" class="prompt-preview">{{ s.promptTemplate }}</span>
        </div>
      </div>
      <div v-if="list.length === 0" class="empty-tip">暂无 Skill，点击右上角新增</div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :title="form.id ? '编辑 Skill' : '新增 Skill'" :width="600" :footer="null">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="所属 Agent" required>
          <a-select v-model:value="form.agentId" placeholder="选择 Agent" style="width: 100%">
            <a-select-option v-for="a in agentList" :key="a.id" :value="a.id">{{ a.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Skill名称" required>
          <a-input v-model:value="form.name" placeholder="如：web_search" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="技能描述" />
        </a-form-item>
        <a-form-item label="关联工具">
          <a-select v-model:value="form.toolId" placeholder="选择工具（可选）" allow-clear style="width: 100%">
            <a-select-option v-for="t in toolList" :key="t.id" :value="t.id">
              {{ t.displayName || t.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="提示词模板">
          <a-textarea v-model:value="form.promptTemplate" :rows="4" placeholder="提示词模板，支持 {{variable}} 变量" />
        </a-form-item>
        <a-form-item label="排序序号">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
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
import { getSkillsByAgent, createSkill, updateSkill, deleteSkill } from '../api/skill'
import { getAgents } from '../api/agent'
import { getTools } from '../api/tool'

const list = ref([])
const agentList = ref([])
const toolList = ref([])
const filterAgentId = ref(null)
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  id: null, agentId: null, toolId: null, name: '',
  description: '', promptTemplate: '', config: '', sortOrder: 0,
})

async function loadAgents() {
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 100 })
    agentList.value = res.data.records || res.data || []
  } catch (e) {
    // ignore
  }
}

async function loadTools() {
  try {
    const res = await getTools({ pageNum: 1, pageSize: 100 })
    toolList.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

async function loadData() {
  if (!filterAgentId.value) {
    // 无筛选时加载第一个 Agent 的 Skills
    if (agentList.value.length > 0) {
      filterAgentId.value = agentList.value[0].id
    } else {
      list.value = []
      return
    }
  }
  try {
    const res = await getSkillsByAgent(filterAgentId.value)
    list.value = res.data || []
  } catch (e) {
    // interceptor handles error
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { ...row })
  } else {
    Object.assign(form, {
      id: null, agentId: filterAgentId.value, toolId: null, name: '',
      description: '', promptTemplate: '', config: '', sortOrder: 0,
    })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.agentId) return message.warning('请选择 Agent')
  if (!form.name.trim()) return message.warning('请输入 Skill 名称')
  submitting.value = true
  try {
    const data = { ...form }
    if (form.id) {
      await updateSkill(data)
      message.success('更新成功')
    } else {
      await createSkill(data)
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
    content: '删除后该 Skill 将无法恢复，是否继续？',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteSkill(id)
      message.success('删除成功')
      loadData()
    },
  })
}

onMounted(async () => {
  await loadAgents()
  await loadTools()
  loadData()
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
  margin-bottom: 24px;
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}
.filter-label {
  font-size: 14px;
  color: #52525b;
  white-space: nowrap;
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
  background: linear-gradient(135deg, #f59e0b, #ef4444);
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
.prompt-preview {
  font-size: 12px;
  color: #a1a1aa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
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
