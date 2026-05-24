<template>
  <div class="subagent-manage">
    <!-- 卡片列表 -->
    <div class="subagent-grid">
      <div v-for="s in list" :key="s.id" class="subagent-card" @click="openDetail(s)">
        <div class="card-top">
          <div class="card-icon">
            <RobotOutlined />
            <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
          </div>
          <div class="card-info">
            <h3>{{ s.displayName }}</h3>
            <span class="card-name">{{ s.name }}</span>
          </div>
          <div class="card-actions" @click.stop>
            <a-switch
              :checked="s.enabled === 1"
              size="small"
              :disabled="s.isBuiltin === 1"
              @change="(val) => handleToggleEnabled(s, val)"
            />
            <button v-if="s.isBuiltin !== 1" class="btn-icon" @click="openEditDialog(s)">
              <EditOutlined />
            </button>
            <button v-if="s.isBuiltin !== 1" class="btn-icon danger" @click="handleDelete(s)">
              <DeleteOutlined />
            </button>
          </div>
        </div>
        <p class="card-desc">{{ s.description || '暂无描述' }}</p>
        <div class="card-meta">
          <span class="card-tools" v-if="formatTools(s.tools)">
            <ToolOutlined /> {{ formatTools(s.tools) }}
          </span>
          <span class="card-tools" v-else>
            <ToolOutlined /> 无工具
          </span>
        </div>
      </div>

      <div v-if="list.length === 0" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p>暂无 SubAgent，点击右上角创建</p>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="editingId ? '编辑 SubAgent' : '新增 SubAgent'"
      :width="720"
      :maskClosable="false"
      @ok="handleSave"
      @cancel="dialogVisible = false"
    >
      <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="标识名称" required>
          <a-input v-model:value="form.name" placeholder="英文标识，如 research-agent" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="form.displayName" placeholder="中文显示名称" />
        </a-form-item>
        <a-form-item label="描述" required>
          <a-textarea v-model:value="form.description" placeholder="SubAgent 描述" :rows="2" />
        </a-form-item>
        <a-form-item label="系统提示词" required>
          <a-textarea v-model:value="form.systemPrompt" placeholder="SubAgent 的系统提示词" :rows="6" />
        </a-form-item>
        <a-form-item label="绑定工具">
          <a-select
            v-model:value="form.tools"
            mode="multiple"
            placeholder="选择工具（可选）"
            :options="toolOptions"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="currentDetail?.displayName || 'SubAgent 详情'"
      :width="640"
      :footer="null"
    >
      <div class="detail-section">
        <div class="detail-row">
          <span class="detail-label">标识名称</span>
          <span class="detail-value">{{ currentDetail?.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">显示名称</span>
          <span class="detail-value">{{ currentDetail?.displayName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">描述</span>
          <span class="detail-value">{{ currentDetail?.description || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">绑定工具</span>
          <span class="detail-value">{{ formatTools(currentDetail?.tools) || '无' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">
            <a-tag :color="currentDetail?.enabled === 1 ? 'green' : 'red'">
              {{ currentDetail?.enabled === 1 ? '启用' : '禁用' }}
            </a-tag>
            <a-tag v-if="currentDetail?.isBuiltin === 1" color="blue">内置</a-tag>
          </span>
        </div>
        <div class="detail-row full">
          <span class="detail-label">系统提示词</span>
          <pre class="detail-prompt">{{ currentDetail?.systemPrompt }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { RobotOutlined, EditOutlined, DeleteOutlined, ToolOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getSubAgents, createSubAgent, updateSubAgent, deleteSubAgent, setSubAgentEnabled } from '../api/subagent'
import { getTools } from '../api/tool'

const props = defineProps({
  hideHeader: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh'])

const loading = ref(false)
const list = ref([])
const searchText = ref('')

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '',
  displayName: '',
  description: '',
  systemPrompt: '',
  tools: [],
  enabled: true
})

const detailVisible = ref(false)
const currentDetail = ref(null)

const toolList = ref([])
const toolOptions = computed(() => {
  return toolList.value.map(t => ({ value: t.name, label: t.displayName || t.name }))
})

onMounted(() => {
  loadList()
  loadToolList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getSubAgents({ pageNum: 1, pageSize: 100, keyword: searchText.value })
    list.value = res.data?.records || []
  } catch (e) {
    console.error('[SubAgentManage] 加载列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function loadToolList() {
  try {
    const res = await getTools({ pageNum: 1, pageSize: 100 })
    toolList.value = res.data?.records || []
  } catch (e) {
    console.error('[SubAgentManage] 加载工具列表失败:', e)
  }
}

function search(text) {
  searchText.value = text
  loadList()
}

function refresh() {
  searchText.value = ''
  loadList()
}

function openDialog() {
  editingId.value = null
  Object.assign(form, { name: '', displayName: '', description: '', systemPrompt: '', tools: [], enabled: true })
  dialogVisible.value = true
}

function openEditDialog(record) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    displayName: record.displayName,
    description: record.description,
    systemPrompt: record.systemPrompt,
    tools: JSON.parse(record.tools || '[]'),
    enabled: record.enabled === 1
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name || !form.displayName || !form.description || !form.systemPrompt) {
    message.warning('请填写必填字段')
    return
  }
  try {
    const data = {
      name: form.name,
      displayName: form.displayName,
      description: form.description,
      systemPrompt: form.systemPrompt,
      tools: form.tools,
      enabled: form.enabled
    }
    if (editingId.value) {
      data.id = editingId.value
      await updateSubAgent(data)
      message.success('更新成功')
    } else {
      await createSubAgent(data)
      message.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
    emit('refresh')
  } catch (e) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除 SubAgent "${record.displayName}" 吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSubAgent(record.id)
        message.success('删除成功')
        loadList()
        emit('refresh')
      } catch (e) {
        message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

async function handleToggleEnabled(record, enabled) {
  try {
    await setSubAgentEnabled(record.id, enabled)
    message.success(enabled ? '已启用' : '已禁用')
    loadList()
  } catch (e) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

function openDetail(record) {
  currentDetail.value = record
  detailVisible.value = true
}

function formatTools(toolsJson) {
  if (!toolsJson) return ''
  try {
    const tools = JSON.parse(toolsJson)
    if (!tools.length) return ''
    return tools.join(', ')
  } catch {
    return ''
  }
}

defineExpose({ openDialog, search, refresh })
</script>

<style scoped>
.subagent-manage {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
}
.subagent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.subagent-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.subagent-card:hover {
  border-color: #f59e0b;
  box-shadow: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04);
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  position: relative;
}
.builtin-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 10px;
  padding: 1px 4px;
  background: #0070f3;
  color: #fff;
  border-radius: 4px;
}
.card-info {
  flex: 1;
  min-width: 0;
}
.card-info h3 {
  font-size: 14px;
  font-weight: 600;
  color: #171717;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-name {
  font-size: 12px;
  color: #71717a;
  margin-top: 2px;
  display: block;
}
.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-icon {
  padding: 4px;
  background: transparent;
  border: none;
  color: #71717a;
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
}
.btn-icon:hover {
  background: #f4f4f5;
  color: #171717;
}
.btn-icon.danger:hover {
  background: #fee2e2;
  color: #ef4444;
}
.card-desc {
  font-size: 13px;
  color: #71717a;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-tools {
  font-size: 12px;
  color: #0070f3;
  display: flex;
  align-items: center;
  gap: 4px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #71717a;
}
.empty-icon {
  font-size: 48px;
  color: #d4d4d8;
  margin-bottom: 12px;
}
.empty-state p {
  margin: 0;
  font-size: 14px;
}

/* 详情弹窗样式 */
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-row {
  display: flex;
  align-items: flex-start;
}
.detail-row.full {
  flex-direction: column;
}
.detail-label {
  width: 100px;
  font-size: 13px;
  color: #71717a;
  flex-shrink: 0;
}
.detail-value {
  font-size: 14px;
  color: #171717;
}
.detail-prompt {
  margin-top: 8px;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #171717;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}
</style>