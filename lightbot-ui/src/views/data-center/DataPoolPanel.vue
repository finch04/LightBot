<template>
  <div class="pool-layout">
    <!-- 左侧：分类手风琴 + 模型列表 -->
    <aside class="pool-rail">
      <div class="pool-rail__header">
        <span>数据模型</span>
        <a-input
          v-model:value="filterText"
          allow-clear
          size="small"
          placeholder="筛选..."
          class="pool-rail__search"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
      </div>

      <a-collapse
        v-model:activeKey="activeKey"
        accordion
        :bordered="false"
        expand-icon-position="start"
        class="pool-accordion"
      >
        <a-collapse-panel
          v-for="cat in filteredCategories"
          :key="cat.id"
          :header="undefined"
        >
          <template #header>
            <div class="accordion-header">
              <span class="accordion-title">{{ cat.name }}</span>
              <span class="accordion-count">{{ modelsOf(cat.id).length }}</span>
            </div>
          </template>
          <button
            v-for="model in modelsOf(cat.id)"
            :key="model.id"
            type="button"
            :class="['model-item', { active: selectedModelId === model.id }]"
            @click="selectModel(model.id)"
          >
            <span class="model-item__name">{{ model.name }}</span>
            <span class="model-item__meta">{{ model.recordCount != null ? `${model.recordCount} 条` : '—' }}</span>
          </button>
          <div v-if="modelsOf(cat.id).length === 0" class="accordion-empty">暂无模型</div>
        </a-collapse-panel>
      </a-collapse>
    </aside>

    <!-- 右侧：选中模型的数据池内容 -->
    <div class="pool-main">
      <template v-if="selectedModel">
        <div class="pool-main__header">
          <div class="pool-main__title">
            <div class="pool-main__title-row">
              <h3>{{ selectedModel.name }}</h3>
              <span class="pool-main__cat">{{ categoryName(selectedModel.categoryId) }}</span>
              <span class="pool-main__count">{{ pagination.total }} 条</span>
            </div>
          </div>
          <div class="pool-main__actions">
            <button type="button" class="lb-btn" title="刷新" :disabled="recordsLoading" @click="loadRecords">
              <ReloadOutlined /> 刷新
            </button>
            <button type="button" class="lb-btn" :disabled="!canImportExport" @click="openImportDialog">
              <ImportOutlined /> 导入
            </button>
            <a-dropdown :trigger="['click']" :disabled="!canImportExport">
              <button type="button" class="lb-btn" :disabled="!canImportExport">
                <ExportOutlined /> 导出
              </button>
              <template #overlay>
                <a-menu @click="({ key }) => exportRecords(key)">
                  <a-menu-item key="filtered">导出当前筛选（CSV）</a-menu-item>
                  <a-menu-item key="all">导出全部数据（CSV）</a-menu-item>
                  <a-menu-item key="json">导出全部数据（JSON）</a-menu-item>
                  <a-menu-item key="template">下载导入模板（CSV）</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <button type="button" class="lb-btn lb-btn--primary" @click="openRecordCreate">
              <PlusOutlined /> 新增数据
            </button>
          </div>
        </div>

        <input
          ref="importInputRef"
          type="file"
          accept=".csv,.json,text/csv,application/json"
          class="pool-import-input"
          @change="onImportFileChange"
        />

        <!-- 按模型配置的模糊搜索 + 筛选项 -->
        <div v-if="hasSearchBar" class="pool-filters">
          <a-input
            v-if="fuzzyFields.length"
            v-model:value="fuzzyKeyword"
            allow-clear
            placeholder="关键词模糊搜索..."
            class="pool-filters__fuzzy"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <template v-for="field in filterFields" :key="field.key">
            <a-select
              v-if="['select', 'radio'].includes(field.type)"
              v-model:value="filterValues[field.key]"
              allow-clear
              :placeholder="field.label"
              style="width: 140px"
              :options="(field.props?.options || []).map((o) => ({ value: o.value, label: o.label }))"
            />
            <a-input
              v-else
              v-model:value="filterValues[field.key]"
              allow-clear
              :placeholder="field.label"
              style="width: 140px"
            />
          </template>
          <button
            v-if="hasActiveFilters"
            type="button"
            class="btn-link"
            @click="resetFilters"
          >清空筛选</button>
        </div>

        <div class="pool-table-wrap">
          <a-table
            :columns="tableColumns"
            :data-source="records"
            :loading="recordsLoading"
            :pagination="tablePagination"
            :scroll="{ x: tableScrollX }"
            row-key="id"
            size="middle"
            @change="onTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'actions'">
                <button type="button" class="btn-link" @click="openDetail(record)">详情</button>
                <button type="button" class="btn-link" @click="openRecordEdit(record)">编辑</button>
                <button type="button" class="btn-link btn-link--danger" @click="removeRecord(record)">删除</button>
              </template>
              <template v-else-if="column.key === firstFieldKey">
                <a-tooltip
                  placement="topLeft"
                  :title="cellTooltipText(record, column.key)"
                  :overlay-style="{ maxWidth: '480px' }"
                >
                  <button
                    type="button"
                    class="btn-link cell-ellipsis cell-primary-link"
                    @click="openDetail(record)"
                  >{{ formatCellValue(record, column.key) || '—' }}</button>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'createTime' || column.key === 'updateTime'">
                <a-tooltip placement="topLeft" :title="formatDateTime(record[column.key]) || undefined">
                  <span class="cell-ellipsis">{{ formatDateTime(record[column.key]) || '—' }}</span>
                </a-tooltip>
              </template>
              <template v-else>
                <a-tooltip
                  placement="topLeft"
                  :title="cellTooltipText(record, column.key)"
                  :overlay-style="{ maxWidth: '480px' }"
                >
                  <span class="cell-ellipsis">{{ formatCellValue(record, column.key) || '—' }}</span>
                </a-tooltip>
              </template>
            </template>
            <template #emptyText>
              <LbEmptyState
                :icon="InboxOutlined"
                :title="hasActiveFilters ? '没有匹配的数据' : '暂无数据'"
                :desc="hasActiveFilters ? '试试调整筛选条件' : '点击右上角新增第一条数据'"
              />
            </template>
          </a-table>
        </div>
      </template>

      <LbEmptyState
        v-else
        :icon="InboxOutlined"
        title="选择数据模型"
        desc="从左侧选择一个模型，查看并管理其数据池"
      />
    </div>

    <a-modal
      v-model:open="importDialogVisible"
      title="导入数据"
      :width="560"
      :footer="null"
      destroy-on-close
      centered
      @cancel="closeImportDialog"
    >
      <div class="import-dialog">
        <div class="import-dialog__top">
          <span class="import-dialog__top-text">CSV / JSON · 结果追加到源文件末尾</span>
          <button type="button" class="btn-link" :disabled="importing" @click="exportRecords('template')">
            下载模板
          </button>
          <a-tooltip placement="bottomRight" :overlay-style="{ maxWidth: '280px' }">
            <template #title>
              <div class="import-tip">
                <p>CSV 首行为字段中文名或 key</p>
                <p>无需填写创建/更新时间</p>
                <p>结果文件末尾含「成功/失败」「失败原因」</p>
              </div>
            </template>
            <button type="button" class="btn-link">说明</button>
          </a-tooltip>
        </div>

        <div class="import-mode-cards" role="radiogroup" aria-label="导入方式">
          <button
            type="button"
            class="import-mode-card"
            :class="{ 'import-mode-card--active': importMode === 'append' }"
            :disabled="importing"
            role="radio"
            :aria-checked="importMode === 'append'"
            @click="importMode = 'append'"
          >
            <span class="import-mode-card__radio" aria-hidden="true" />
            <span class="import-mode-card__body">
              <span class="import-mode-card__title">
                追加导入
                <span class="import-mode-card__badge">推荐</span>
                <a-tooltip placement="top" :overlay-style="{ maxWidth: '280px' }">
                  <template #title>
                    保留现有数据并逐条新增。唯一约束冲突仅导致该行失败，其余行仍可成功。
                  </template>
                  <QuestionCircleOutlined class="import-mode-card__help" @click.stop />
                </a-tooltip>
              </span>
              <span class="import-mode-card__desc">保留旧数据，仅新增</span>
            </span>
          </button>
          <button
            type="button"
            class="import-mode-card import-mode-card--danger"
            :class="{ 'import-mode-card--active': importMode === 'replace' }"
            :disabled="importing"
            role="radio"
            :aria-checked="importMode === 'replace'"
            @click="importMode = 'replace'"
          >
            <span class="import-mode-card__radio" aria-hidden="true" />
            <span class="import-mode-card__body">
              <span class="import-mode-card__title">
                覆盖导入
                <span class="import-mode-card__badge import-mode-card__badge--warn">危险</span>
                <a-tooltip placement="top" :overlay-style="{ maxWidth: '280px' }">
                  <template #title>
                    先清空本模型全部数据再写入。不是按唯一键更新；清空后不可恢复，失败行也不会回滚。
                  </template>
                  <QuestionCircleOutlined class="import-mode-card__help" @click.stop />
                </a-tooltip>
              </span>
              <span class="import-mode-card__desc">先清空再写入</span>
            </span>
          </button>
        </div>

        <div v-if="importMode === 'replace'" class="import-dialog__warn" role="alert">
          <ExclamationCircleOutlined />
          <span>
            将清空全部数据<span v-if="pagination.total > 0">（约 {{ pagination.total }} 条）</span>，不可撤销
          </span>
        </div>

        <div class="import-action-row">
          <button type="button" class="lb-btn" :disabled="importing" @click="pickImportFile">
            {{ importPendingFile ? '重选文件' : '选择文件' }}
          </button>
          <div class="import-file-meta">
            <template v-if="importPendingFile">
              <span class="import-file-name" :title="importPendingFile.name">{{ importPendingFile.name }}</span>
              <span class="import-file-size">{{ formatFileSize(importPendingFile.size) }}</span>
            </template>
            <span v-else class="import-file-placeholder">未选择 · .csv / .json</span>
          </div>
          <button
            type="button"
            class="lb-btn lb-btn--primary import-dialog__submit"
            :class="{ 'import-dialog__submit--danger': importMode === 'replace' }"
            :disabled="importing || !importPendingFile"
            @click="confirmAndImport"
          >
            {{ importing ? '导入中…' : (importMode === 'replace' ? '覆盖导入' : '开始导入') }}
          </button>
        </div>

        <div v-if="importResult" class="import-result">
          <div class="import-result__stats">
            <span>总计 <b>{{ importResult.total }}</b></span>
            <span class="import-stat--ok">成功 <b>{{ importResult.successCount }}</b></span>
            <span class="import-stat--fail">失败 <b>{{ importResult.failCount }}</b></span>
          </div>
          <button
            type="button"
            class="lb-btn import-result__download"
            :disabled="!importResult.resultFileBase64"
            @click="downloadImportResult"
          >
            下载结果
          </button>
        </div>
      </div>
    </a-modal>

    <a-drawer
      v-model:open="detailOpen"
      title="数据详情"
      :width="480"
      destroy-on-close
    >
      <a-descriptions v-if="detailRecord" :column="1" bordered size="small" class="detail-desc">
        <a-descriptions-item
          v-for="field in editableFields"
          :key="field.key"
          :label="field.label"
        >
          {{ formatCellValue(detailRecord, field.key) || '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">
          {{ formatDateTime(detailRecord.createTime) || '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ formatDateTime(detailRecord.updateTime) || '—' }}
        </a-descriptions-item>
      </a-descriptions>
      <div class="detail-drawer__footer">
        <button type="button" class="lb-btn" @click="detailOpen = false">关闭</button>
        <button type="button" class="lb-btn lb-btn--primary" @click="editFromDetail">编辑</button>
      </div>
    </a-drawer>

    <a-modal
      v-model:open="recordDialogVisible"
      :title="recordForm.id ? '编辑数据' : '新增数据'"
      :width="560"
      @ok="saveRecord"
      @cancel="recordDialogVisible = false"
    >
      <a-form :label-col="{ flex: '0 0 100px' }">
        <a-form-item
          v-for="field in editableFields"
          :key="field.key"
          :label="field.label"
          :required="field.required"
        >
          <DataPoolFieldInput
            v-model="recordForm.values[field.key]"
            :field="field"
          />
        </a-form-item>
        <a-empty
          v-if="!editableFields.length"
          description="该模型尚未配置表单字段，请先在「数据模型」中配置"
        />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  SearchOutlined, ReloadOutlined, PlusOutlined, InboxOutlined,
  ImportOutlined, ExportOutlined, ExclamationCircleOutlined, QuestionCircleOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import LbEmptyState from '../../components/common/LbEmptyState.vue'
import DataPoolFieldInput from '../../components/data-center/DataPoolFieldInput.vue'
import { validatePoolRecord } from '../../utils/dataPoolFieldValidate'
import {
  listDataModelCategories,
  listDataModels,
  pageDataPoolRecords,
  createDataPoolRecord,
  updateDataPoolRecord,
  deleteDataPoolRecord,
  importDataPoolRecords,
  exportDataPoolRecords,
} from '../../api/dataCenter'

const categories = ref([])
const models = ref([])
const records = ref([])
const recordsLoading = ref(false)
const filterText = ref('')
const fuzzyKeyword = ref('')
const filterValues = reactive({})
const activeKey = ref(undefined)
const selectedModelId = ref(null)
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

const recordDialogVisible = ref(false)
const recordForm = reactive({ id: null, values: {} })
const importDialogVisible = ref(false)
const importMode = ref('append')
const importInputRef = ref(null)
const importPendingFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const detailOpen = ref(false)
const detailRecord = ref(null)

const SYSTEM_FILTER_LABELS = {
  createTime: '创建时间',
  updateTime: '更新时间',
}

function emptySchema() {
  return { fields: [], fuzzySearchFields: [], searchConditions: [], uniqueKeys: [], indexes: [] }
}

async function loadMeta() {
  const [catsRes, listRes] = await Promise.all([
    listDataModelCategories(),
    listDataModels(),
  ])
  categories.value = (catsRes?.data || []).map((c) => ({ ...c, id: String(c.id) }))
  models.value = (listRes?.data || []).map((m) => ({
    ...m,
    id: String(m.id),
    categoryId: m.categoryId != null ? String(m.categoryId) : m.categoryId,
    schema: m.schema || emptySchema(),
    recordCount: m.recordCount,
  }))
  if (!activeKey.value && categories.value.length) {
    activeKey.value = categories.value[0].id
  }
  if (!selectedModelId.value && models.value.length) {
    selectedModelId.value = models.value[0].id
    const cat = models.value[0].categoryId
    if (cat) activeKey.value = cat
  }
  if (selectedModelId.value) {
    await loadRecords()
  }
}

onMounted(() => {
  loadMeta()
})

const filteredCategories = computed(() => {
  const kw = filterText.value.trim().toLowerCase()
  if (!kw) return categories.value
  return categories.value.filter((cat) => {
    if (cat.name.toLowerCase().includes(kw)) return true
    return models.value.some((m) =>
      m.categoryId === cat.id && m.name.toLowerCase().includes(kw))
  })
})

function modelsOf(categoryId) {
  const kw = filterText.value.trim().toLowerCase()
  return models.value.filter((m) => {
    if (m.categoryId !== categoryId) return false
    if (!kw) return true
    return m.name.toLowerCase().includes(kw)
  })
}

function categoryName(categoryId) {
  return categories.value.find((c) => c.id === categoryId)?.name || '未分类'
}

const SYSTEM_FIELD_KEYS = new Set(['id', 'createTime', 'updateTime', 'deleted'])

const selectedModel = computed(() => models.value.find((m) => m.id === selectedModelId.value) || null)

/** 业务字段（排除创建/更新时间等系统字段，写入由后端生成） */
const editableFields = computed(() =>
  (selectedModel.value?.schema?.fields || []).filter(
    (f) => f?.key && !f.system && !SYSTEM_FIELD_KEYS.has(f.key),
  ),
)

const canImportExport = computed(() => editableFields.value.length > 0)

const fieldByKey = computed(() => {
  const map = new Map()
  for (const f of selectedModel.value?.schema?.fields || []) {
    map.set(f.key, f)
  }
  return map
})

const fuzzyFields = computed(() => {
  const keys = selectedModel.value?.schema?.fuzzySearchFields || []
  return keys.map((k) => fieldByKey.value.get(k)).filter(Boolean)
})

const filterFields = computed(() => {
  const keys = selectedModel.value?.schema?.searchConditions || []
  return keys.map((k) => {
    const field = fieldByKey.value.get(k)
    if (field) return field
    if (SYSTEM_FILTER_LABELS[k]) {
      return { key: k, label: SYSTEM_FILTER_LABELS[k], type: 'datetime', system: true }
    }
    return null
  }).filter(Boolean)
})

const hasSearchBar = computed(() => fuzzyFields.value.length > 0 || filterFields.value.length > 0)

const hasActiveFilters = computed(() => {
  if (fuzzyKeyword.value.trim()) return true
  return Object.values(filterValues).some((v) => v !== undefined && v !== null && v !== '')
})

/** 第一列业务字段 key，用于固定列与点击打开详情 */
const firstFieldKey = computed(() => editableFields.value[0]?.key || '')

/** 关闭原生 title，改用 a-tooltip，避免双提示 */
const ELLIPSIS = { showTitle: false }

const tableColumns = computed(() => {
  const cols = editableFields.value.map((f, index) => {
    const col = {
      title: f.label,
      dataIndex: f.key,
      key: f.key,
      ellipsis: ELLIPSIS,
      width: index === 0 ? 168 : 140,
    }
    if (index === 0) col.fixed = 'left'
    return col
  })
  cols.push(
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170, ellipsis: ELLIPSIS },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 170, ellipsis: ELLIPSIS },
    { title: '操作', key: 'actions', width: 168, fixed: 'right' },
  )
  return cols
})

const tableScrollX = computed(() =>
  tableColumns.value.reduce((sum, col) => sum + (Number(col.width) || 140), 0),
)

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
}))

function buildFilters() {
  const filters = {}
  for (const field of filterFields.value) {
    const val = filterValues[field.key]
    if (val !== undefined && val !== null && val !== '') {
      filters[field.key] = val
    }
  }
  return filters
}

async function loadRecords() {
  if (!selectedModelId.value) {
    records.value = []
    pagination.total = 0
    return
  }
  recordsLoading.value = true
  try {
    const filters = buildFilters()
    const pageRes = await pageDataPoolRecords(selectedModelId.value, {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: fuzzyKeyword.value.trim() || undefined,
      filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
    })
    const page = pageRes?.data || {}
    records.value = (page.records || []).map((r) => ({
      ...r,
      id: r.id != null ? String(r.id) : r.id,
    }))
    pagination.total = Number(page.total || 0)
    const model = models.value.find((m) => m.id === selectedModelId.value)
    if (model) model.recordCount = pagination.total
  } finally {
    recordsLoading.value = false
  }
}

function resetFilters() {
  fuzzyKeyword.value = ''
  Object.keys(filterValues).forEach((k) => { delete filterValues[k] })
  pagination.current = 1
  loadRecords()
}

function selectModel(id) {
  selectedModelId.value = id
  fuzzyKeyword.value = ''
  Object.keys(filterValues).forEach((k) => { delete filterValues[k] })
  pagination.current = 1
  loadRecords()
}

function onTableChange(pag) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadRecords()
}

let filterTimer = null
watch([fuzzyKeyword, filterValues], () => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(() => {
    pagination.current = 1
    loadRecords()
  }, 300)
}, { deep: true })

function openRecordCreate() {
  if (!editableFields.value.length) {
    message.warning('该模型尚未配置表单字段')
    return
  }
  recordForm.id = null
  recordForm.values = {}
  for (const f of editableFields.value) {
    recordForm.values[f.key] = undefined
  }
  recordDialogVisible.value = true
}

function openRecordEdit(record) {
  recordForm.id = record.id
  recordForm.values = {}
  for (const f of editableFields.value) {
    recordForm.values[f.key] = record[f.key]
  }
  recordDialogVisible.value = true
}

async function saveRecord() {
  const modelId = selectedModelId.value
  if (!modelId) return
  // 仅提交业务字段；创建/更新时间由后端生成
  const data = {}
  for (const f of editableFields.value) {
    data[f.key] = recordForm.values[f.key]
  }
  const err = validatePoolRecord(editableFields.value, data)
  if (err) {
    message.warning(err)
    return
  }
  if (recordForm.id) {
    await updateDataPoolRecord(modelId, recordForm.id, data)
  } else {
    await createDataPoolRecord(modelId, data)
  }
  recordDialogVisible.value = false
  message.success('已保存')
  await loadRecords()
}

function removeRecord(record) {
  Modal.confirm({
    title: '确认删除？',
    content: '删除后不可恢复。',
    okType: 'danger',
    async onOk() {
      await deleteDataPoolRecord(selectedModelId.value, record.id)
      message.success('已删除')
      await loadRecords()
    },
  })
}

function formatDateTime(value) {
  if (value == null || value === '') return ''
  const text = String(value).trim().replace('T', ' ')
  return text.length >= 19 ? text.slice(0, 19) : text
}

/** 空值不显示 tooltip */
function cellTooltipText(record, fieldKey) {
  const text = formatCellValue(record, fieldKey)
  return text || undefined
}

function parseJsonArray(value) {
  if (value == null || value === '') return []
  if (Array.isArray(value)) return value
  // 兼容后端偶发返回的 PGobject 形态 { type: 'jsonb', value: '...' }
  if (typeof value === 'object' && typeof value.value === 'string') {
    try {
      const parsed = JSON.parse(value.value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : [value]
    } catch {
      return value.includes('、') ? value.split('、').map((s) => s.trim()).filter(Boolean) : [value]
    }
  }
  return []
}

function formatCellValue(record, fieldKey) {
  if (!record) return ''
  const field = fieldByKey.value.get(fieldKey)
  const value = record[fieldKey]
  if (value == null || value === '') return ''
  if (fieldKey === 'createTime' || fieldKey === 'updateTime' || field?.type === 'datetime') {
    return formatDateTime(value)
  }
  if (field?.type === 'date') {
    return String(value).slice(0, 10)
  }
  if (['select', 'radio'].includes(field?.type)) {
    const opt = (field.props?.options || []).find((o) => String(o.value) === String(value))
    return opt?.label ?? String(value)
  }
  if (field?.type === 'checkbox') {
    return parseJsonArray(value).map((v) => {
      const raw = (v != null && typeof v === 'object') ? (v.value ?? v.label ?? '') : v
      const opt = (field.props?.options || []).find((o) => String(o.value) === String(raw))
      return opt?.label ?? String(raw)
    }).filter(Boolean).join('、')
  }
  if (field?.type === 'upload') {
    return parseJsonArray(value).map((f) => f?.name || f?.url).filter(Boolean).join('、')
  }
  if (typeof value === 'object') {
    try { return JSON.stringify(value) } catch { return String(value) }
  }
  return String(value)
}

function openDetail(record) {
  detailRecord.value = record
  detailOpen.value = true
}

function editFromDetail() {
  const record = detailRecord.value
  detailOpen.value = false
  if (record) openRecordEdit(record)
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function safeFileName(name) {
  return String(name || 'data').replace(/[\\/:*?"<>|]/g, '_')
}

async function exportRecords(mode) {
  const model = selectedModel.value
  const fields = editableFields.value
  if (!fields.length) {
    message.warning('该模型尚未配置表单字段')
    return
  }
  const base = safeFileName(model.name)
  if (mode === 'template') {
    // 模板表头与导出口径一致：中文展示名（导入同时兼容 key）
    const header = fields.map((f) => f.label || f.key).join(',')
    const blob = new Blob([`\uFEFF${header}\n`], { type: 'text/csv;charset=utf-8' })
    downloadBlob(blob, `${base}_template.csv`)
    message.success('已下载导入模板')
    return
  }
  const filters = mode === 'filtered' ? buildFilters() : {}
  const keyword = mode === 'filtered' ? (fuzzyKeyword.value.trim() || undefined) : undefined
  const format = mode === 'json' ? 'json' : 'csv'
  const blob = await exportDataPoolRecords(model.id, {
    format,
    keyword,
    filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
  })
  downloadBlob(blob, `${base}.${format === 'json' ? 'json' : 'csv'}`)
  message.success('导出成功')
}

function openImportDialog() {
  if (!canImportExport.value) {
    message.warning('该模型尚未配置表单字段')
    return
  }
  // 默认追加：避免误选覆盖导致清库
  importMode.value = 'append'
  importPendingFile.value = null
  importResult.value = null
  importing.value = false
  importDialogVisible.value = true
}

function closeImportDialog() {
  importDialogVisible.value = false
  importing.value = false
  importPendingFile.value = null
}

function pickImportFile() {
  if (importing.value) return
  nextTick(() => {
    if (importInputRef.value) {
      importInputRef.value.value = ''
      importInputRef.value.click()
    }
  })
}

function formatFileSize(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n < 0) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function downloadImportResult() {
  const result = importResult.value
  if (!result?.resultFileBase64) return
  const binary = atob(result.resultFileBase64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  const filename = result.resultFileName || 'import-result.csv'
  const isCsv = String(filename).toLowerCase().endsWith('.csv')
  const blob = new Blob([bytes], {
    type: isCsv
      ? 'text/csv;charset=utf-8'
      : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })
  downloadBlob(blob, filename)
}

function onImportFileChange(event) {
  const file = event.target?.files?.[0]
  if (!file) return
  importPendingFile.value = file
  importResult.value = null
  if (importInputRef.value) importInputRef.value.value = ''
}

function confirmAndImport() {
  const file = importPendingFile.value
  if (!file || !selectedModelId.value || importing.value) return
  if (importMode.value === 'replace') {
    const totalHint = pagination.total > 0 ? `当前约 ${pagination.total} 条记录将被清空，` : ''
    Modal.confirm({
      title: '确认覆盖导入？',
      content: `${totalHint}清空后无法恢复。确定使用「${file.name}」覆盖现有数据吗？`,
      okText: '确认覆盖并导入',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => runImport(file),
    })
    return
  }
  runImport(file)
}

async function runImport(file) {
  if (!selectedModelId.value) return
  importing.value = true
  importResult.value = null
  try {
    const res = await importDataPoolRecords(selectedModelId.value, file, importMode.value)
    importResult.value = res?.data || null
    const ok = Number(importResult.value?.successCount || 0)
    const fail = Number(importResult.value?.failCount || 0)
    if (fail === 0) {
      message.success(`导入完成：成功 ${ok} 条`)
    } else {
      message.warning(`导入完成：成功 ${ok} 条，失败 ${fail} 条，请下载结果文件查看原因`)
    }
    pagination.current = 1
    await loadRecords()
  } catch {
    // request 拦截器已提示
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.pool-layout {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
}
.pool-rail {
  width: 260px;
  flex-shrink: 0;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.pool-rail__header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 4px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  flex-shrink: 0;
}
.pool-rail__search {
  text-transform: none;
  letter-spacing: normal;
  font-weight: 400;
}
.pool-accordion {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: transparent;
}
.pool-accordion :deep(.ant-collapse-item) {
  border: none;
  margin-bottom: 4px;
}
.pool-accordion :deep(.ant-collapse-header) {
  padding: 8px 10px !important;
  border-radius: 8px !important;
  align-items: center !important;
  color: var(--color-ink);
  font-weight: 500;
  transition: background-color 0.15s ease;
}
.pool-accordion :deep(.ant-collapse-header:hover) {
  background: var(--color-canvas-soft-2);
}
.pool-accordion :deep(.ant-collapse-content) {
  border: none;
  background: transparent;
}
.pool-accordion :deep(.ant-collapse-content-box) {
  padding: 4px 0 8px 8px !important;
}
.accordion-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding-right: 4px;
}
.accordion-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.accordion-count {
  font-size: 11px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 0 6px;
  border-radius: 999px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}
.accordion-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--color-mute);
}
.model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 2px;
  border: 1px solid transparent;
  border-radius: 8px;
  border-left: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--color-ink);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.model-item:hover {
  background: var(--color-canvas-soft-2);
}
.model-item.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  border-left-color: var(--color-ink);
  font-weight: 600;
}
.model-item:active {
  transform: scale(0.98);
}
.model-item__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.model-item__meta {
  font-size: 11px;
  color: var(--color-mute);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.pool-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pool-main__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.pool-main__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.pool-main__title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
  line-height: 1.3;
}
.pool-main__cat,
.pool-main__count {
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}
.pool-main__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.pool-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  flex-shrink: 0;
}
.pool-filters__fuzzy {
  width: 220px;
}
.pool-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.pool-table-wrap :deep(.ant-table-placeholder .ant-table-cell) {
  padding: 48px 16px;
}
.pool-table-wrap :deep(.ant-table) {
  font-variant-numeric: tabular-nums;
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  padding: 0 6px 0 0;
  transition: color 0.15s ease, opacity 0.15s ease;
}
.btn-link:hover {
  opacity: 0.8;
}
.btn-link--danger {
  color: var(--color-error);
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.cell-primary-link {
  text-align: left;
  font-weight: 500;
}
.detail-desc {
  margin-bottom: 24px;
}
.detail-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-hairline);
}
.pool-main > .lb-empty {
  flex: 1;
}
.pool-import-input {
  display: none;
}
.import-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.import-dialog__top {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
}
.import-dialog__top-text {
  flex: 1;
  min-width: 160px;
  color: var(--color-mute);
}
.import-tip p {
  margin: 0 0 4px;
  line-height: 1.45;
}
.import-tip p:last-child {
  margin-bottom: 0;
}
.import-mode-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.import-mode-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;
}
.import-mode-card:hover:not(:disabled) {
  border-color: var(--color-hairline-strong, #a1a1a1);
}
.import-mode-card:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.import-mode-card--active {
  border-color: var(--color-ink);
  background: var(--color-canvas-soft);
  box-shadow: 0 0 0 1px var(--color-ink);
}
.import-mode-card--danger.import-mode-card--active {
  border-color: var(--color-error);
  box-shadow: 0 0 0 1px var(--color-error);
  background: color-mix(in srgb, var(--color-error) 6%, var(--color-canvas));
}
.import-mode-card__radio {
  width: 14px;
  height: 14px;
  margin-top: 2px;
  border-radius: 50%;
  border: 1.5px solid var(--color-hairline-strong, #a1a1a1);
  flex-shrink: 0;
  position: relative;
}
.import-mode-card--active .import-mode-card__radio {
  border-color: var(--color-ink);
}
.import-mode-card--active .import-mode-card__radio::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: var(--color-ink);
}
.import-mode-card--danger.import-mode-card--active .import-mode-card__radio {
  border-color: var(--color-error);
}
.import-mode-card--danger.import-mode-card--active .import-mode-card__radio::after {
  background: var(--color-error);
}
.import-mode-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.import-mode-card__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  line-height: 1.3;
}
.import-mode-card__badge {
  font-size: 11px;
  font-weight: 500;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
}
.import-mode-card__badge--warn {
  background: color-mix(in srgb, var(--color-error) 12%, transparent);
  color: var(--color-error);
}
.import-mode-card__desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-mute);
}
.import-mode-card__help {
  margin-left: 2px;
  font-size: 12px;
  color: var(--color-mute);
}
.import-mode-card__help:hover {
  color: var(--color-ink);
}
.import-dialog__warn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--color-error) 35%, transparent);
  background: color-mix(in srgb, var(--color-error) 8%, var(--color-canvas));
  color: var(--color-error);
  font-size: 12px;
  line-height: 1.4;
}
.import-dialog__warn :deep(.anticon) {
  flex-shrink: 0;
}
.import-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.import-file-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.import-file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.import-file-size,
.import-file-placeholder {
  font-size: 12px;
  color: var(--color-mute);
  flex-shrink: 0;
}
.import-dialog__submit {
  flex-shrink: 0;
  min-width: 96px;
}
.import-dialog__submit--danger {
  background: var(--color-error) !important;
  border-color: var(--color-error) !important;
  color: #fff !important;
}
.import-dialog__submit--danger:hover:not(:disabled) {
  filter: brightness(1.05);
}
.import-result {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
}
.import-result__stats {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--color-mute);
}
.import-result__stats b {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-ink);
  margin-left: 2px;
}
.import-stat--ok b {
  color: #1f8a4c;
}
.import-stat--fail b {
  color: var(--color-error);
}
.import-result__download {
  flex-shrink: 0;
}
</style>
