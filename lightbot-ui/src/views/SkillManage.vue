<template>
  <div class="page">
    <div v-if="!hideHeader" class="page-header">
      <div>
        <h1 class="page-title">Skill 库</h1>
        <p class="page-desc">全局可复用的 Skill：编排提示词 + 依赖工具/MCP，可在 Agent 详情中按需启用</p>
      </div>
      <div class="page-header-actions">
        <a-input
          v-model:value="searchText"
          placeholder="搜索 Skill 名称 / slug..."
          allow-clear
          style="width: 240px"
          @pressEnter="loadData"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-outline" @click="loadData" :disabled="loading">
          <ReloadOutlined :spin="loading" /> 刷新
        </button>
        <button class="btn-primary" @click="openDialog()">
          <PlusOutlined /> 新增 Skill
        </button>
        <button class="btn-primary" style="background: #7c3aed" @click="importModalVisible = true">
          <UploadOutlined /> ZIP 导入
        </button>
        <button class="btn-primary" style="background: #0369a1" @click="remoteInstallVisible = true">
          <CloudDownloadOutlined /> 远程安装
        </button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="provider-grid">
      <div v-for="s in list" :key="s.id" class="provider-card">
        <div class="card-top">
          <div class="card-icon card-icon--skill">
            <span v-if="s.isBuiltin === 1" class="builtin-badge">内置</span>
            <span class="status-dot" :class="s.status === 'disabled' ? 'status-disabled' : 'status-active'"></span>
            <ThunderboltOutlined />
          </div>
          <div class="card-info">
            <h3>{{ s.displayName || s.name }}</h3>
          </div>
          <div class="card-actions">
            <a-tooltip title="查看详情">
              <button class="btn-icon" @click="openDetail(s)"><EyeOutlined /></button>
            </a-tooltip>
            <button v-if="s.isBuiltin !== 1" class="btn-icon danger" @click="handleDelete(s)"><DeleteOutlined /></button>
            <a-dropdown :trigger="['click']">
              <button class="btn-icon" @click.prevent><MoreOutlined /></button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="toggleEnabled(s)">
                    <CheckCircleOutlined v-if="s.status !== 'disabled'" style="color: #16a34a; margin-right: 6px" />
                    <CloseCircleOutlined v-else style="color: #a3a3a3; margin-right: 6px" />
                    {{ s.status === 'disabled' ? '启用' : '禁用' }}
                  </a-menu-item>
                  <a-menu-item @click="handleExport(s)">
                    <ExportOutlined style="margin-right: 6px" /> 导出 ZIP
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>
        <div class="card-detail">
          <div class="card-tags">
            <span v-if="s.slug" class="tag tag-slug">{{ s.slug }}</span>
            <span v-if="s.version" class="tag tag-version">v{{ s.version }}</span>
            <span v-if="s.sourceType === 'builtin'" class="tag tag-builtin">内置</span>
            <span v-else-if="s.sourceType === 'upload'" class="tag tag-upload">上传</span>
            <span v-else-if="s.sourceType === 'remote'" class="tag tag-remote">远程</span>
          </div>
          <a-tooltip v-if="s.description" :title="s.description" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
            <span class="card-desc">{{ truncateText(s.description, 50) }}</span>
          </a-tooltip>
        </div>
      </div>
      <div v-if="list.length === 0 && !loading" class="empty-tip">
        {{ searchText ? '没有匹配的 Skill' : '暂无 Skill，点击右上角新增' }}
      </div>
    </div>
    </a-spin>

    <!-- 查看详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="Skill 详情" :width="720" :footer="null" :maskClosable="false">
      <template v-if="detailRow">
        <a-descriptions bordered :column="1" size="small">
          <a-descriptions-item label="显示名称">{{ detailRow.displayName || detailRow.name }}</a-descriptions-item>
          <a-descriptions-item label="技能名称">{{ detailRow.name }}</a-descriptions-item>
          <a-descriptions-item label="slug">{{ detailRow.slug || '—' }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ detailRow.version || '1.0.0' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="detailRow.status === 'disabled' ? 'default' : 'success'">
              {{ detailRow.status === 'disabled' ? '已禁用' : '已启用' }}
            </a-tag>
            <a-tag v-if="detailRow.isBuiltin === 1" color="blue">内置</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="描述">{{ detailRow.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="排序">{{ detailRow.sortOrder ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="依赖工具">
            {{ formatIdLabels(detailRow.toolIds, toolOptions) || '无' }}
          </a-descriptions-item>
          <a-descriptions-item label="依赖 MCP">
            {{ formatIdLabels(detailRow.mcpServerIds, mcpOptions) || '无' }}
          </a-descriptions-item>
        </a-descriptions>
        <div class="detail-section">
          <div class="detail-section-title">提示词模板</div>
          <pre class="detail-pre">{{ detailRow.promptTemplate || '—' }}</pre>
        </div>
        <div v-if="detailRow.config && detailRow.config !== '{}'" class="detail-section">
          <div class="detail-section-title">扩展配置</div>
          <pre class="detail-pre">{{ detailRow.config }}</pre>
        </div>
      </template>
      <div class="dialog-footer">
        <div class="dialog-footer-left">
          <button v-if="detailRow && detailRow.isBuiltin !== 1" class="btn-cancel" @click="detailVisible = false; openDialog(detailRow)">
            <EditOutlined /> 编辑
          </button>
        </div>
        <div class="dialog-footer-right">
          <button class="btn-cancel" @click="detailVisible = false">关闭</button>
        </div>
      </div>
    </a-modal>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="dialogVisible" :width="720" :footer="null" :maskClosable="false">
      <template #title>
        <span>{{ form.id ? '编辑 Skill' : '新增 Skill' }}</span>
        <QuestionCircleOutlined class="help-icon" @click.stop="guideVisible = true" />
      </template>
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="slug" required v-if="!form.id || form.scope === 'global'">
          <a-input v-model:value="form.slug" placeholder="英文-小写-短横线，如 deep-research（不超过30字）" :maxlength="30" show-count :disabled="form.id && form.isBuiltin === 1" />
        </a-form-item>
        <a-form-item label="技能名称" required>
          <a-input v-model:value="form.name" placeholder="英文短名，对模型可读，如 deep_research（不超过30字）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="显示名称">
          <a-input v-model:value="form.displayName" placeholder="中文，如 深度研究（不超过30字）" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="什么场景启用这个技能（不超过50字）" :maxlength="50" show-count />
        </a-form-item>
        <a-form-item label="依赖工具">
          <a-select v-model:value="form.toolIds" mode="multiple" placeholder="选择该 Skill 启用时附带的工具" style="width: 100%" :options="toolOptions" />
        </a-form-item>
        <a-form-item label="依赖 MCP Server">
          <a-select v-model:value="form.mcpServerIds" mode="multiple" placeholder="选择该 Skill 启用时附带的 MCP Server" style="width: 100%" :options="mcpOptions" />
        </a-form-item>
        <a-form-item label="提示词模板" required>
          <a-textarea v-model:value="form.promptTemplate" :rows="8" placeholder="### 技能：xxx\n**触发条件**：...\n**执行流程**：...（不超过5000字）" :maxlength="5000" show-count />
        </a-form-item>
        <a-form-item label="排序序号">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="扩展配置">
          <JsonInput v-model="form.config" :rows="2" placeholder="JSON 格式的扩展配置（可选）" />
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

    <!-- ZIP 导入弹窗 -->
    <SkillImportModal
      v-model:open="importModalVisible"
      @imported="loadData"
    />

    <!-- 远程安装弹窗 -->
    <SkillRemoteInstallModal
      v-model:open="remoteInstallVisible"
      @installed="loadData"
    />

    <!-- Skill 说明弹窗 -->
    <a-modal v-model:open="guideVisible" title="Skill 说明" :width="640" :footer="null">
      <div class="guide">
        <div class="guide-section">
          <div class="guide-h3">Skill 在本项目中的作用</div>
          <p>Skill 是<strong>可复用的能力包</strong>：把「何时启用、如何执行」写成提示词模板，并可附带依赖的工具与 MCP Server。在 Agent 详情中启用后，对话时会把这些 Skill 注入系统上下文，引导主模型按场景选用对应能力（与 Yuxi 的 Skill 中间件思路一致）。</p>
          <p>与 SubAgent 的区别：Skill 是<strong>提示词 + 工具扩展</strong>，由主 Agent 在同一轮对话中执行；SubAgent 是<strong>独立子智能体</strong>，通过委派工具异步完成子任务。</p>
        </div>
        <div class="guide-section">
          <div class="guide-h3">如何新建 Skill</div>
          <div class="guide-step">
            <span class="guide-num">1</span>
            <div><b>填写 slug 与名称</b><p>slug 为全局唯一英文短横线标识；name 为模型可读英文名；displayName 为界面展示名。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">2</span>
            <div><b>编写提示词模板</b><p>建议包含「触发条件」与「执行规则」，例如深度研究、知识库问答等场景说明。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">3</span>
            <div><b>（可选）绑定依赖</b><p>选择该 Skill 启用时需要一并开放的工具或 MCP Server。</p></div>
          </div>
          <div class="guide-step">
            <span class="guide-num">4</span>
            <div><b>在 Agent 中启用</b><p>进入智能体详情 → Skill Tab，从列表勾选启用（最多 10 个）。发布版本后绑定关系会写入版本快照。</p></div>
          </div>
        </div>
        <div class="guide-section">
          <div class="guide-h3">内置 Skill</div>
          <p>系统启动时会注册若干内置 Skill（如深度研究、知识库问答等），可启用但不可删除或修改 slug。</p>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
defineProps({ hideHeader: Boolean })
import { ref, reactive, watch, onMounted } from 'vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined, ThunderboltOutlined, CheckCircleOutlined, CloseCircleOutlined, EyeOutlined, QuestionCircleOutlined, UploadOutlined, ExportOutlined, CloudDownloadOutlined, MoreOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getSkills, createSkill, updateSkill, deleteSkill, setSkillEnabled, exportSkillZip } from '../api/skill'
import { getTools } from '../api/tool'
import { getMcpServers } from '../api/mcp'
import JsonInput from '../components/JsonInput.vue'
import SkillImportModal from '../components/SkillImportModal.vue'
import SkillRemoteInstallModal from '../components/SkillRemoteInstallModal.vue'
import { truncateText } from '../utils/format'

const list = ref([])
const loading = ref(false)
const searchText = ref('')
const toolOptions = ref([])
const mcpOptions = ref([])
const dialogVisible = ref(false)
const guideVisible = ref(false)
const importModalVisible = ref(false)
const remoteInstallVisible = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)
const submitting = ref(false)
const form = reactive({
  id: null, slug: '', name: '', displayName: '',
  description: '', promptTemplate: '', config: '{}', sortOrder: 0,
  toolIds: [], mcpServerIds: [], scope: 'global', isBuiltin: 0,
})

watch(searchText, () => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: 1, pageSize: 50 }
    if (searchText.value) params.keyword = searchText.value
    const res = await getSkills(params)
    const data = res.data || {}
    list.value = data.records || data || []
  } catch (e) {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [toolRes, mcpRes] = await Promise.all([
      getTools({ pageNum: 1, pageSize: 200 }),
      getMcpServers({ pageNum: 1, pageSize: 100 }),
    ])
    toolOptions.value = (toolRes.data.records || []).map(t => ({
      label: t.displayName || t.name,
      value: String(t.id),
    }))
    mcpOptions.value = (mcpRes.data.records || []).map(m => ({
      label: m.name,
      value: String(m.id),
    }))
  } catch (e) {
    // ignore
  }
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function formatIdLabels(raw, options) {
  const ids = parseIdArray(raw)
  if (!ids.length) return ''
  const labelMap = Object.fromEntries((options || []).map(o => [String(o.value), o.label]))
  return ids.map(id => labelMap[id] || id).join('、')
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      ...row,
      toolIds: parseIdArray(row.toolIds),
      mcpServerIds: parseIdArray(row.mcpServerIds),
      config: row.config || '{}',
    })
  } else {
    Object.assign(form, {
      id: null, slug: '', name: '', displayName: '',
      description: '', promptTemplate: '', config: '{}', sortOrder: 0,
      toolIds: [], mcpServerIds: [], scope: 'global', isBuiltin: 0,
    })
  }
  dialogVisible.value = true
}

function parseIdArray(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(String)
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return []
  }
}

async function handleSubmit() {
  if (!form.name?.trim()) return message.warning('请输入 Skill 名称')
  if (!form.slug?.trim()) return message.warning('请填写 slug（英文-小写-短横线）')
  if (!form.promptTemplate?.trim()) return message.warning('请填写提示词模板')
  submitting.value = true
  try {
    const data = {
      ...form,
      toolIds: (form.toolIds || []).map(String),
      mcpServerIds: (form.mcpServerIds || []).map(String),
    }
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

function handleDelete(row) {
  if (row.isBuiltin === 1) {
    message.warning('内置 Skill 不可删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `删除 Skill「${row.displayName || row.name}」后，已启用此 Skill 的 Agent 将自动忽略，是否继续？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteSkill(row.id)
      message.success('删除成功')
      loadData()
    },
  })
}

async function toggleEnabled(row) {
  const next = row.status === 'disabled'
  await setSkillEnabled(row.id, next)
  message.success(next ? '已启用' : '已禁用')
  loadData()
}

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

async function handleExport(row) {
  try {
    const res = await exportSkillZip(row.id)
    const blob = new Blob([res.data], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `skill-${row.slug || row.id}.zip`
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e) {
    // interceptor handles error
  }
}

onMounted(async () => {
  await loadOptions()
  loadData()
})

function search(text) {
  const next = text || ''
  if (searchText.value === next) return
  searchText.value = next
  loadData()
}

function refresh() {
  searchText.value = ''
  loadData()
}

function openImportModal() {
  importModalVisible.value = true
}

function openRemoteInstallModal() {
  remoteInstallVisible.value = true
}

defineExpose({ openDialog, search, refresh, openImportModal, openRemoteInstallModal })
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
.btn-primary:hover { background: #27272a; }
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
}
.btn-outline:hover { border-color: #0070f3; color: #0070f3; }

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
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
  background: linear-gradient(135deg, #ec4899, #db2777);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  position: relative;
}
.card-icon--skill {
  background: linear-gradient(135deg, #ec4899, #db2777);
}
.detail-section {
  margin-top: 16px;
}
.detail-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}
.detail-pre {
  margin: 0;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}
.card-info { flex: 1; min-width: 0; }
.card-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  display: flex;
  align-items: center;
  gap: 6px;
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
.card-actions { display: flex; gap: 4px; }
.btn-icon {
  width: 32px; height: 32px;
  border: none; background: transparent;
  border-radius: 6px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #71717a;
}
.btn-icon:hover { background: #f5f5f5; }
.btn-icon:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-icon.danger:hover:not(:disabled) { color: #ee0000; background: #f7d4d6; }
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.card-tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
  line-height: 1.4;
}
.tag-slug {
  background: #fdf2f8;
  color: #be185d;
  border: 1px solid #fbcfe8;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}
.tag-version {
  background: #f0f9ff;
  color: #0369a1;
  border: 1px solid #bae6fd;
}
.tag-builtin {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}
.tag-upload {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.tag-remote {
  background: #f0f9ff;
  color: #0369a1;
  border: 1px solid #bae6fd;
}
.help-icon {
  margin-left: 8px;
  color: #a1a1aa;
  cursor: pointer;
  font-size: 16px;
  vertical-align: middle;
}
.help-icon:hover {
  color: #db2777;
}
.guide {
  max-height: 60vh;
  overflow-y: auto;
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
  color: #171717;
  margin-bottom: 8px;
}
.guide-section p {
  font-size: 13px;
  color: #52525b;
  line-height: 1.6;
  margin: 0 0 8px;
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
  background: #fdf2f8;
  color: #be185d;
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
  color: #71717a;
}

.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
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
.dialog-footer-left { display: flex; gap: 8px; }
.dialog-footer-right { display: flex; gap: 8px; }
.btn-cancel {
  padding: 6px 14px;
  background: #fff;
  color: #71717a;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: #171717; color: #171717; }
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
.btn-primary-sm:hover:not(:disabled) { background: #27272a; }
.btn-primary-sm:disabled { background: #d4d4d8; cursor: not-allowed; }
</style>
