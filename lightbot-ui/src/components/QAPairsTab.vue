<template>
  <div class="qa-pairs-tab">
    <div class="qa-toolbar">
      <a-input
        v-model:value="searchText"
        placeholder="搜索问答对..."
        allow-clear
        size="small"
        class="qa-search"
        @press-enter="loadData"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-tooltip title="刷新">
        <a-button size="small" @click="loadData" :disabled="loading">
          <template #icon><ReloadOutlined :spin="loading" /></template>
        </a-button>
      </a-tooltip>
      <div class="qa-toolbar-right">
        <a-button
          v-if="selectedRowKeys.length > 0"
          size="small"
          @click="handleBatchVectorize"
          :disabled="vectorizing"
        >
          <template #icon><ApiOutlined /></template> 向量化 ({{ selectedRowKeys.length }})
        </a-button>
        <a-button size="small" @click="showCreateModal = true">
          <template #icon><PlusOutlined /></template> 新增
        </a-button>
        <a-button size="small" :loading="generating" @click="onGenerateClick">
          <template #icon><RobotOutlined /></template> AI 生成
        </a-button>
      </div>
    </div>

    <a-table
      :data-source="qaPairs"
      :columns="columns"
      :pagination="pagination"
      :loading="loading"
      size="small"
      row-key="id"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      :custom-row="(record) => ({ onClick: () => handleViewDetail(record), style: { cursor: 'pointer' } })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'question'">
          <a-tooltip :title="record.question">
            <span class="qa-cell-text">
              <a-tooltip :title="record.status">
                <CheckCircleOutlined v-if="record.status === '生效'" class="qa-status-icon status-active" />
                <SyncOutlined v-else-if="record.status === '向量化中'" class="qa-status-icon status-vectorizing" spin />
                <ClockCircleOutlined v-else-if="record.status === '待向量化'" class="qa-status-icon status-pending" />
                <CloseCircleOutlined v-else-if="record.status === '失败'" class="qa-status-icon status-failed" />
                <ClockCircleOutlined v-else class="qa-status-icon" />
              </a-tooltip>
              {{ truncate(record.question, 50) }}
            </span>
          </a-tooltip>
        </template>
        <template v-if="column.key === 'answer'">
          <a-tooltip :title="record.answer">
            <span class="qa-cell-text">{{ truncate(record.answer, 80) }}</span>
          </a-tooltip>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-tooltip v-if="record.status === '待向量化' || record.status === '失败'" title="向量化">
              <a-button type="text" size="small" @click.stop="handleVectorize(record)" :disabled="vectorizing">
                <template #icon><ApiOutlined /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip title="查看详情">
              <a-button type="text" size="small" @click="handleViewDetail(record)">
                <template #icon><EyeOutlined /></template>
              </a-button>
            </a-tooltip>
            <a-popconfirm title="确定删除？" @confirm="handleDelete(record.id)" @click.stop>
              <a-tooltip title="删除">
                <a-button type="text" size="small" danger>
                  <template #icon><DeleteOutlined /></template>
                </a-button>
              </a-tooltip>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增弹窗 -->
    <a-modal
      v-model:open="showCreateModal"
      title="新增问答对"
      :maskClosable="false"
      @ok="handleCreate"
      :confirm-loading="saving"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="问题" required>
          <a-textarea v-model:value="form.question" placeholder="输入问题内容（不超过2000字）" :rows="3" :maxlength="2000" show-count />
        </a-form-item>
        <a-form-item label="标准答案" required>
          <a-textarea v-model:value="form.answer" placeholder="输入标准答案（不超过2000字）" :rows="5" :maxlength="2000" show-count />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="showDetailModal"
      title="问答对详情"
      :maskClosable="false"
      :width="640"
      @cancel="detailEditing = false"
    >
      <template v-if="detailRecord">
        <a-descriptions :column="2" size="small" bordered class="qa-detail-info">
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(detailRecord.status)">{{ detailRecord.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="来源">
            <a-tag>{{ sourceLabel(detailRecord.source) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间" :span="2">{{ formatTime(detailRecord.createTime) }}</a-descriptions-item>
        </a-descriptions>
        <a-divider style="margin: 12px 0" />
        <a-form v-if="detailEditing" :model="detailForm" layout="vertical">
          <a-form-item label="问题" required>
            <a-textarea v-model:value="detailForm.question" :rows="3" placeholder="输入问题内容（不超过2000字）" :maxlength="2000" show-count />
          </a-form-item>
          <a-form-item label="标准答案" required>
            <a-textarea v-model:value="detailForm.answer" :rows="5" placeholder="输入标准答案（不超过2000字）" :maxlength="2000" show-count />
          </a-form-item>
        </a-form>
        <template v-else>
          <div class="qa-detail-section">
            <div class="qa-detail-label">
              问题
              <a-tooltip title="复制">
                <CopyOutlined class="qa-copy-btn" @click="copyText(detailRecord.question)" />
              </a-tooltip>
            </div>
            <div class="qa-detail-content">{{ detailRecord.question }}</div>
          </div>
          <div class="qa-detail-section">
            <div class="qa-detail-label">
              标准答案
              <a-tooltip title="复制">
                <CopyOutlined class="qa-copy-btn" @click="copyText(detailRecord.answer)" />
              </a-tooltip>
            </div>
            <div class="qa-detail-content">{{ detailRecord.answer }}</div>
          </div>
        </template>
      </template>
      <template #footer>
        <a-button @click="detailEditing = false" v-if="detailEditing">取消</a-button>
        <a-button v-if="!detailEditing" @click="startDetailEdit">
          <template #icon><EditOutlined /></template> 编辑
        </a-button>
        <a-button type="primary" v-if="detailEditing" :loading="saving" @click="handleDetailSave">保存</a-button>
      </template>
    </a-modal>

    <!-- AI 生成弹窗 -->
    <a-modal
      v-model:open="showGenerateModal"
      title="AI 生成问答对"
      :maskClosable="false"
      @ok="handleGenerate"
      :confirm-loading="generating"
    >
      <a-form layout="vertical">
        <a-form-item label="生成条数">
          <a-slider v-model:value="generateCount" :min="1" :max="20" :marks="{ 1: '1', 10: '10', 20: '20' }" />
        </a-form-item>
        <a-form-item label="生成模型">
          <ModelSelect v-model="generateProviderId" placeholder="不选择则使用系统内置模型" @change="onGenerateModelChange" />
        </a-form-item>
        <a-form-item>
          <a-alert message="AI 将从知识库文档中自动提取问答对，单次最多生成 20 条" type="info" show-icon />
        </a-form-item>
      </a-form>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { SearchOutlined, PlusOutlined, RobotOutlined, EditOutlined, DeleteOutlined, EyeOutlined, CheckCircleOutlined, SyncOutlined, ClockCircleOutlined, CloseCircleOutlined, CopyOutlined, ReloadOutlined, ApiOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getQAPairs, createQAPair, updateQAPair, deleteQAPair, generateQAPairs, vectorizeQAPair, batchVectorizeQAPairs } from '../api/knowledge'
import ModelSelect from './ModelSelect.vue'

const props = defineProps({
  knowledgeId: { type: [String, Number], required: true },
  docTotal: { type: Number, default: 0 },
  qaEnabled: { type: Boolean, default: true },
})

const searchText = ref('')
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const vectorizing = ref(false)
const qaPairs = ref([])
const pagination = ref({ current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` })
const selectedRowKeys = ref([])

const showCreateModal = ref(false)
const showGenerateModal = ref(false)
const showDetailModal = ref(false)
const detailRecord = ref(null)
const detailEditing = ref(false)
const generateCount = ref(10)
const generateProviderId = ref(null)
const selectedGenerateModel = ref({ providerId: null, modelId: null })

function onGenerateModelChange({ providerId, modelId }) {
  selectedGenerateModel.value = { providerId, modelId }
}

const form = reactive({ question: '', answer: '' })
const detailForm = reactive({ question: '', answer: '' })

const columns = [
  { title: '问题', key: 'question', dataIndex: 'question', ellipsis: true, width: '40%' },
  { title: '答案', key: 'answer', dataIndex: 'answer', ellipsis: true, width: '45%' },
  { title: '操作', key: 'action', width: 80, align: 'center' },
]

async function loadData() {
  loading.value = true
  try {
    const res = await getQAPairs(props.knowledgeId, {
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      keyword: searchText.value || undefined,
    })
    qaPairs.value = res.data?.records || []
    pagination.value.total = res.data?.total || 0
  } catch (e) {
    console.error('加载问答对失败', e)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.question?.trim() || !form.answer?.trim()) {
    message.warning('请填写问题和答案')
    return
  }
  saving.value = true
  try {
    await createQAPair(props.knowledgeId, { question: form.question, answer: form.answer })
    message.success('创建成功')
    showCreateModal.value = false
    form.question = ''
    form.answer = ''
    loadData()
  } catch (e) {
    message.error('创建失败')
  } finally {
    saving.value = false
  }
}

function handleViewDetail(record) {
  detailRecord.value = record
  detailEditing.value = false
  showDetailModal.value = true
}

function startDetailEdit() {
  detailForm.question = detailRecord.value.question
  detailForm.answer = detailRecord.value.answer
  detailEditing.value = true
}

async function handleDetailSave() {
  if (!detailForm.question?.trim() || !detailForm.answer?.trim()) {
    message.warning('请填写问题和答案')
    return
  }
  saving.value = true
  try {
    await updateQAPair(detailRecord.value.id, { question: detailForm.question, answer: detailForm.answer })
    message.success('更新成功')
    detailRecord.value.question = detailForm.question
    detailRecord.value.answer = detailForm.answer
    detailEditing.value = false
    loadData()
  } catch (e) {
    message.error('更新失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    await deleteQAPair(id)
    message.success('删除成功')
    loadData()
  } catch (e) {
    message.error('删除失败')
  }
}

function onSelectChange(keys) {
  selectedRowKeys.value = keys
}

async function handleVectorize(record) {
  vectorizing.value = true
  try {
    await vectorizeQAPair(record.id)
    message.success('已提交向量化')
    loadData()
  } catch (e) {
    message.error('向量化失败')
  } finally {
    vectorizing.value = false
  }
}

async function handleBatchVectorize() {
  if (selectedRowKeys.value.length === 0) return
  vectorizing.value = true
  try {
    const res = await batchVectorizeQAPairs(selectedRowKeys.value)
    message.success(`已提交 ${res.data} 条向量化`)
    selectedRowKeys.value = []
    loadData()
  } catch (e) {
    message.error('批量向量化失败')
  } finally {
    vectorizing.value = false
  }
}

function onGenerateClick() {
  if (props.docTotal === 0) {
    Modal.warning({ title: '暂无文档', content: '知识库中暂无文档，请先上传文档后再生成问答对。' })
    return
  }
  showGenerateModal.value = true
}

async function handleGenerate() {
  generating.value = true
  try {
    const params = { count: generateCount.value }
    if (selectedGenerateModel.value.providerId) {
      params.providerId = selectedGenerateModel.value.providerId
      params.modelId = selectedGenerateModel.value.modelId
    }
    await generateQAPairs(props.knowledgeId, params)
    message.success('已提交到任务中心，可在任务中心查看进度')
    showGenerateModal.value = false
    setTimeout(() => loadData(), 2000)
  } catch (e) {
    message.error('生成失败')
  } finally {
    generating.value = false
  }
}


function handleTableChange(pag) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  loadData()
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text || '')
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '...' : str
}

function sourceLabel(source) {
  const map = { manual: '手动', import: '导入', ai: 'AI' }
  return map[source] || source
}

function statusColor(status) {
  const map = { '待向量化': 'gold', '向量化中': 'processing', '生效': 'success', '失败': 'error' }
  return map[status] || 'default'
}

// 暴露给父组件调用
defineExpose({ loadData })

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.qa-pairs-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.qa-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.qa-search {
  width: 220px;
}
.qa-toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.qa-cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.config-desc {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
}
.qa-status-icon {
  margin-right: 6px;
  font-size: 14px;
}
.status-active {
  color: #52c41a;
}
.status-vectorizing {
  color: #1890ff;
}
.status-pending {
  color: #faad14;
}
.status-failed {
  color: #ff4d4f;
}
.qa-detail-info {
  margin-bottom: 0;
}
.qa-detail-section {
  margin-bottom: 12px;
}
.qa-detail-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.qa-copy-btn {
  font-size: 12px;
  color: #a1a1aa;
  cursor: pointer;
  transition: color 0.15s;
}
.qa-copy-btn:hover {
  color: #0070f3;
}
.qa-detail-content {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
</style>
