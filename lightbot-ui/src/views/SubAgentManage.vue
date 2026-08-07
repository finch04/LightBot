<template>
  <div class="subagent-manage">
    <!-- 卡片列表 -->
    <a-spin :spinning="loading" style="min-height: 300px; display: block;">
    <div class="card-grid">
      <EntityCard
        v-for="s in list"
        :key="s.id"
        type="subagent"
        :name="s.displayName"
        @click="openDetail(s)"
      >
        <template #icon>
          <DynamicIcon :name="s.icon" :fallback="s.displayName || s.name" />
          <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
          <span class="status-dot" :class="s.enabled === 1 ? 'status-active' : 'status-disabled'"></span>
        </template>
        <template #info>
          <a-tooltip :title="s.displayName"><h3>{{ s.displayName }}</h3></a-tooltip>
          <a-tooltip :title="s.name"><span class="card-name">{{ s.name }}</span></a-tooltip>
        </template>
        <template #actions>
          <a-tooltip v-if="s.isBuiltin !== 1" title="删除">
            <button class="btn-icon danger" @click="handleDelete(s)"><DeleteOutlined /></button>
          </a-tooltip>
          <a-dropdown :trigger="['click']">
            <button class="btn-icon" @click.prevent><MoreOutlined /></button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="handleToggleEnabled(s, s.enabled !== 1)">
                  <CheckCircleOutlined v-if="s.enabled === 1" style="color: #16a34a; margin-right: 6px" />
                  <CloseCircleOutlined v-else style="color: #a3a3a3; margin-right: 6px" />
                  {{ s.enabled === 1 ? '禁用' : '启用' }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
          <p class="card-desc">{{ truncateText(s.description, 50) }}</p>
        </a-tooltip>
        <p v-else class="card-desc">暂无描述</p>
        <template #meta>
          <span class="card-tools" v-if="formatToolIds(s.toolIds)">
            <ToolOutlined /> {{ formatToolIds(s.toolIds) }}
          </span>
          <span class="card-tools" v-else>
            <ToolOutlined /> 无工具
          </span>
        </template>
      </EntityCard>

      <div v-if="list.length === 0 && !loading" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p v-if="searchText">没有匹配的 SubAgent</p>
        <p v-else>还没有 SubAgent，点击右上角创建一个吧</p>
      </div>
    </div>
    </a-spin>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :width="720"
      :maskClosable="false"
      @ok="handleSave"
      @cancel="dialogVisible = false"
    >
      <template #title>
        <span>{{ editingId ? '编辑 SubAgent' : '新增 SubAgent' }}</span>
        <QuestionCircleOutlined class="help-icon" @click.stop="guideVisible = true" />
      </template>
      <div class="dialog-scroll-body">
      <a-form :label-col="{ flex: '0 0 100px' }">
        <div v-if="editingBuiltin" class="builtin-edit-banner">
          内置 SubAgent 仅可调整「模型配置」，其余字段保持系统默认不可修改。
        </div>
        <a-form-item label="标识名称" required>
          <a-input v-model:value="form.name" placeholder="请输入标识名称（英文，不超过 50 字）" :maxlength="50" show-count :disabled="editingBuiltin" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="form.displayName" placeholder="请输入显示名称（不超过 50 字）" :maxlength="50" show-count :disabled="editingBuiltin" />
        </a-form-item>
        <a-form-item label="图标">
          <IconPicker v-model:value="form.icon" :disabled="editingBuiltin" />
        </a-form-item>
        <a-form-item label="描述" required>
          <a-textarea v-model:value="form.description" placeholder="请输入 SubAgent 描述（不超过 200 字）" :rows="2" :maxlength="200" show-count :disabled="editingBuiltin" />
        </a-form-item>
        <a-form-item label="系统提示词" required>
          <a-textarea v-model:value="form.systemPrompt" placeholder="SubAgent 的系统提示词（不超过2000字）" :rows="6" :maxlength="2000" show-count :disabled="editingBuiltin" />
        </a-form-item>
        <a-form-item label="绑定工具">
          <a-select
            v-model:value="form.toolIds"
            mode="multiple"
            placeholder="选择工具（可选）"
            :options="allToolOptions"
            allow-clear
            option-label-prop="label"
            :disabled="editingBuiltin"
          >
            <template #option="{ value, label, toolType, description, icon }">
              <EntitySelectOption type="tool" :name="label" :icon="icon" :tag="toolType" :desc="description" />
            </template>
          </a-select>
        </a-form-item>
        <a-form-item label="模型配置">
          <a-switch v-model:checked="inheritModel" style="margin-right: 8px" />
          <span style="font-size: 13px; color: var(--color-mute);">{{ inheritModel ? '继承主 Agent 模型（含版本快照）' : '使用独立模型' }}</span>
          <ModelSelect
            v-if="!inheritModel"
            v-model:provider-id="form.providerId"
            v-model:model-id="form.llmModel"
            placeholder="选择模型"
            style="margin-top: 8px"
          />
        </a-form-item>
        <a-form-item label="连接超时">
          <a-input-number
            v-model:value="form.connectTimeoutSeconds"
            :min="1"
            :max="60"
            :step="1"
            addon-after="秒"
            style="width: 160px"
            :disabled="editingBuiltin"
          />
          <div class="form-hint">建立模型连接的最长等待，默认 10 秒</div>
        </a-form-item>
        <a-form-item label="响应超时">
          <a-input-number
            v-model:value="form.readTimeoutSeconds"
            :min="10"
            :max="300"
            :step="5"
            addon-after="秒"
            style="width: 160px"
            :disabled="editingBuiltin"
          />
          <div class="form-hint">SubAgent 整体执行上限，默认 120 秒</div>
        </a-form-item>
        <a-form-item label="失败重试">
          <a-input-number
            v-model:value="form.modelRetryTimes"
            :min="0"
            :max="10"
            :step="1"
            addon-after="次"
            style="width: 160px"
            :disabled="editingBuiltin"
          />
          <div class="form-hint">模型连接失败时的重试次数，默认 1 次</div>
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="form.enabled" :disabled="editingBuiltin" />
        </a-form-item>
      </a-form>
      </div>
    </a-modal>

    <!-- SubAgent 说明弹窗 -->
    <a-modal v-model:open="guideVisible" title="SubAgent 说明" :width="640" :footer="null">
      <div class="guide">
        <div class="guide-section">
          <div class="guide-h3">SubAgent 在本项目中的作用</div>
          <p>SubAgent 是<strong>专职子智能体</strong>：拥有独立的系统提示词与可选工具集。主 Agent 在对话中可通过内置工具 <code>delegate_to_subagent</code> 将子任务委派给指定 SubAgent，子智能体在隔离上下文中完成推理与工具调用后，将结果返回主 Agent 继续回复。</p>
          <p>适用场景：代码审查、深度调研、专项写作等需要<strong>独立人设与工具边界</strong>的任务，避免主对话上下文被拉长。</p>
        </div>
        <div class="guide-section">
          <div class="guide-h3">如何新建 SubAgent</div>
          <div class="guide-step">
            <span class="guide-num">1</span>
            <div><b>填写标识与显示名</b><p>name 为英文标识（委派时引用）；displayName 为界面展示名称。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">2</span>
            <div><b>编写描述与系统提示词</b><p>描述帮助主模型判断何时委派；系统提示词定义子智能体的角色与输出规范。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">3</span>
            <div><b>（可选）绑定工具</b><p>SubAgent 仅能使用此处绑定的工具；不绑定则运行时无工具可用（不会继承主 Agent 的工具）。部分工具（如产物交付）禁止 SubAgent 调用。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">4</span>
            <div><b>在 Agent 中绑定</b><p>进入智能体详情 → SubAgents Tab，勾选要委派的子智能体（有数量上限）。保存并发布后，主 Agent 对话即可委派。</p></div>
          </div>
        </div>
        <div class="guide-section">
          <div class="guide-h3">内置 SubAgent</div>
          <p>系统预置 SubAgent 仅可调整「模型配置」（继承主 Agent 或自选模型）与启用状态；其余字段保持系统默认，不可删除。</p>
        </div>
      </div>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="currentDetail?.displayName || 'SubAgent 详情'"
      :width="640"
      :maskClosable="false"
    >
      <div class="dialog-scroll-body">
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
          <span class="detail-value">{{ formatToolIds(currentDetail?.toolIds) || '无' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">模型配置</span>
          <span class="detail-value">
            <a-tag v-if="currentDetail?.modelId" color="blue">
              {{ formatModelLabel(currentDetail) }}
            </a-tag>
            <span v-else style="color: #999;">继承主 Agent（含版本快照）</span>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">连接超时</span>
          <span class="detail-value">{{ currentDetail?.connectTimeoutSeconds ?? 10 }} 秒</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">响应超时</span>
          <span class="detail-value">{{ currentDetail?.readTimeoutSeconds ?? 120 }} 秒</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">失败重试</span>
          <span class="detail-value">{{ currentDetail?.modelRetryTimes ?? 1 }} 次</span>
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
      </div>
      <template #footer>
        <LbDialogFooter
          cancel-text="关闭"
          hide-confirm
          @cancel="detailVisible = false"
        >
          <template #left>
            <button v-if="currentDetail" class="lb-btn" @click="detailVisible = false; openEditDialog(currentDetail)">
              <EditOutlined /> 编辑
            </button>
          </template>
        </LbDialogFooter>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { RobotOutlined, EditOutlined, DeleteOutlined, ToolOutlined, QuestionCircleOutlined, MoreOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import EntitySelectOption from '../components/EntitySelectOption.vue'
import EntityCard from '../components/EntityCard.vue'
import DynamicIcon from '../components/DynamicIcon.vue'
import IconPicker from '../components/IconPicker.vue'
import { getSubAgents, createSubAgent, updateSubAgent, deleteSubAgent, setSubAgentEnabled } from '../api/subagent'
import { getTools } from '../api/tool'
import { getProvidersWithModels } from '../api/modelProvider'
import ModelSelect from '../components/ModelSelect.vue'
import LbDialogFooter from '../components/common/LbDialogFooter.vue'
import { truncateText } from '../utils/format'
import { getToolTypeLabel } from '../utils/bindingTheme'

const props = defineProps({
  hideHeader: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh'])

const loading = ref(false)
const list = ref([])
const searchText = ref('')

const dialogVisible = ref(false)
const guideVisible = ref(false)
const editingId = ref(null)
const inheritModel = ref(true)
const form = reactive({
  name: '',
  displayName: '',
  icon: '',
  description: '',
  systemPrompt: '',
  toolIds: [],
  providerId: null,
  llmModel: null,
  connectTimeoutSeconds: 10,
  readTimeoutSeconds: 120,
  modelRetryTimes: 1,
  enabled: true
})

const detailVisible = ref(false)
const currentDetail = ref(null)

// 编辑内置 SubAgent 时，仅模型配置可改，其余字段全部禁用
const editingBuiltin = computed(() => !!editingId.value
  && list.value.some(s => s.id === editingId.value && s.isBuiltin === 1))

const toolList = ref([])
const staleToolOptions = ref([])
const providerList = ref([])

const toolOptions = computed(() => {
  return toolList.value.map(t => ({
    value: String(t.id),
    label: t.displayName || t.name,
    icon: t.icon,
    toolType: getToolTypeLabel(t.toolType),
    description: t.description,
  }))
})

const allToolOptions = computed(() => [...toolOptions.value, ...staleToolOptions.value])

// 提供商ID到名称的映射
const providerNameMap = computed(() => {
  const map = {}
  for (const p of providerList.value) {
    map[String(p.id)] = p.name
  }
  return map
})

// 下拉选项懒加载标记：首次打开新增/编辑弹窗时才加载提供商列表，避免进入页面就发请求
let optionsLoaded = false

onMounted(() => {
  // 卡片和详情弹窗的工具绑定展示都依赖 toolList（formatToolIds 按 id 查工具名），必须与列表并行预加载，否则会全部回退显示为「[已删除]」
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

async function loadProviders() {
  try {
    const res = await getProvidersWithModels('llm')
    providerList.value = res.data || []
  } catch (e) {
    console.error('[SubAgentManage] 加载提供商列表失败:', e)
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

async function openDialog() {
  if (!optionsLoaded) {
    await Promise.all([loadToolList(), loadProviders()])
    optionsLoaded = true
  }
  editingId.value = null
  inheritModel.value = true
  staleToolOptions.value = []
  Object.assign(form, { name: '', displayName: '', icon: '', description: '', systemPrompt: '', toolIds: [], providerId: null, llmModel: null, connectTimeoutSeconds: 10, readTimeoutSeconds: 120, modelRetryTimes: 1, enabled: true })
  dialogVisible.value = true
}

async function openEditDialog(record) {
  if (!optionsLoaded) {
    await Promise.all([loadToolList(), loadProviders()])
    optionsLoaded = true
  }
  editingId.value = record.id
  inheritModel.value = !record.modelId
  staleToolOptions.value = []

  // 解析已绑定的工具ID
  const selectedIds = parseIdArray(record.toolIds)

  // 检测悬空引用（JSON中有但工具列表中不存在的ID）
  const existingIds = new Set(toolList.value.map(t => String(t.id)))
  selectedIds.filter(id => !existingIds.has(id)).forEach(id => {
    staleToolOptions.value.push({ value: id, label: `[已删除] ${id}`, disabled: true })
  })

  Object.assign(form, {
    name: record.name,
    displayName: record.displayName,
    icon: record.icon || '',
    description: record.description,
    systemPrompt: record.systemPrompt,
    toolIds: selectedIds,
    providerId: record.modelId ? String(record.modelId) : null,
    llmModel: record.llmModel || null,
    connectTimeoutSeconds: record.connectTimeoutSeconds ?? 10,
    readTimeoutSeconds: record.readTimeoutSeconds ?? 120,
    modelRetryTimes: record.modelRetryTimes ?? 1,
    enabled: record.enabled === 1
  })
  dialogVisible.value = true
}

function formatModelLabel(record) {
  if (!record?.modelId) return '继承主 Agent'
  const pid = String(record.modelId)
  const providerName = providerNameMap.value[pid] || pid
  const model = record.llmModel || '默认模型'
  return `${providerName}:${model}`
}

/** 解析JSON数组为字符串列表，兼容数组和JSON字符串输入 */
function parseIdArray(json) {
  if (!json) return []
  if (Array.isArray(json)) return json.map(String)
  try { return JSON.parse(json).map(String) } catch { return [] }
}

async function handleSave() {
  if (!form.name || !form.displayName || !form.description || !form.systemPrompt) {
    message.warning('请填写必填字段')
    return
  }

  // 检查是否有悬空工具引用
  const existingIds = new Set(toolList.value.map(t => String(t.id)))
  const staleCount = form.toolIds.filter(id => !existingIds.has(id)).length

  if (staleCount > 0) {
    Modal.confirm({
      title: '存在已删除的工具',
      content: `${staleCount} 个工具已被删除，保存时将自动移除。是否继续？`,
      okText: '继续保存',
      cancelText: '取消',
      onOk: () => doSave(),
    })
  } else {
    doSave()
  }
}

async function doSave() {
  if (!inheritModel.value && !form.providerId) {
    message.warning('请选择独立模型，或开启继承主 Agent 模型')
    return
  }
  try {
    const data = {
      name: form.name,
      displayName: form.displayName,
      icon: form.icon,
      description: form.description,
      systemPrompt: form.systemPrompt,
      toolIds: form.toolIds,
      providerId: inheritModel.value ? null : (form.providerId || null),
      llmModel: inheritModel.value ? null : (form.llmModel || null),
      connectTimeoutSeconds: form.connectTimeoutSeconds ?? 10,
      readTimeoutSeconds: form.readTimeoutSeconds ?? 120,
      modelRetryTimes: form.modelRetryTimes ?? 1,
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

async function openDetail(record) {
  currentDetail.value = record
  // 确保提供商列表已加载，以便正确显示模型提供商名称
  if (providerList.value.length === 0) {
    await loadProviders()
  }
  detailVisible.value = true
}

function formatToolIds(toolIdsJson) {
  if (!toolIdsJson) return ''
  try {
    const ids = JSON.parse(toolIdsJson)
    if (!ids.length) return ''
    return ids.map(id => {
      const tool = toolList.value.find(t => String(t.id) === String(id))
      return tool ? (tool.displayName || tool.name) : '[已删除]'
    }).join('、')
  } catch {
    return ''
  }
}

defineExpose({ openDialog, search, refresh, loading })
</script>

<style scoped>
.subagent-manage {
  background: var(--color-canvas);
  border-radius: 12px;
  padding: 20px;
}
.card-grid {
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}
/* SubAgent 专用 hover 色（琥珀色） */
:deep(.entity-card:hover) {
  border-color: #f59e0b !important;
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
  z-index: 1;
}
.status-dot {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  z-index: 1;
}
.status-active {
  background: #16a34a;
}
.status-disabled {
  background: #a3a3a3;
}
.card-name {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 2px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: fit-content;
  max-width: 100%;
}
.card-desc {
  font-size: 13px;
  color: var(--color-mute);
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-tools {
  font-size: 12px;
  color: var(--color-link);
  display: flex;
  align-items: center;
  gap: 4px;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  color: var(--color-mute);
}
.empty-icon {
  font-size: 48px;
  color: #d4d4d8;
  margin-bottom: 12px;
  display: block;
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
  min-width: 100px;
  font-size: 13px;
  color: var(--color-mute);
  flex-shrink: 0;
  white-space: nowrap;
}
.detail-value {
  font-size: 14px;
  color: var(--color-ink);
}
.detail-prompt {
  margin-top: 8px;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--color-ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.help-icon {
  margin-left: 8px;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 16px;
  vertical-align: middle;
}
.help-icon:hover {
  color: #d97706;
}
.dialog-scroll-body {
  /* 滚动由 .ant-modal-body 全局接管，避免双滚动条 */
}
.form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.builtin-edit-banner {
  margin-bottom: 16px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.55;
  color: #1d4ed8;
  background: var(--color-link-bg-soft, #eff6ff);
  border: 1px solid color-mix(in srgb, var(--color-link) 25%, transparent);
  border-radius: 6px;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--color-hairline);
  margin-top: 8px;
}
.dialog-footer-left {
  display: flex;
  gap: 8px;
}
.dialog-footer-right {
  display: flex;
  gap: 8px;
}
.btn-cancel {
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-mute);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover {
  border-color: var(--color-ink);
  color: var(--color-ink);
}
.guide-section {
  margin-bottom: 20px;
}
.guide-section:last-child {
  margin-bottom: 0;
}
.guide-h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.guide-section p {
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.6;
  margin: 0 0 8px;
}
.guide-section code {
  font-size: 12px;
  background: var(--color-canvas-soft-2);
  padding: 1px 4px;
  border-radius: 4px;
}
.guide-step {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.guide-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-warn-bg);
  color: #b45309;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.guide-step b {
  display: block;
  font-size: 13px;
  margin-bottom: 4px;
}
.guide-step p {
  margin: 0;
  font-size: 12px;
  color: var(--color-mute);
}
/* 工具下拉选项样式 */
.tool-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.tool-option-icon-wrap {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.tool-option-name {
  font-size: 13px;
  color: var(--color-ink);
  flex-shrink: 0;
}
.tool-option-tag {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.tool-option-desc {
  font-size: 12px;
  color: var(--color-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
</style>
