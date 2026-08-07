<template>
  <a-drawer
    :open="open"
    :width="720"
    placement="right"
    destroy-on-close
    :body-style="{ paddingBottom: '80px' }"
    @update:open="onOpenChange"
  >
    <template #title>
      <div class="ask-drawer__title">
        <span>问数增强</span>
        <span v-if="model?.name" class="ask-drawer__model">{{ model.name }}</span>
        <span v-if="dataset" class="ask-drawer__meta">
          {{ (dataset.dimensions || []).length }} 维 ·
          {{ autoMetrics.length }} 自动指标 ·
          {{ form.customMetrics.length }} 业务指标
        </span>
      </div>
    </template>

    <div v-if="loading" class="ask-drawer__loading">加载中…</div>

    <template v-else-if="!dataset">
      <div class="ask-empty-card">
        <p>尚未启用问数配置。启用后将自动同步字段语义与默认指标（数量 / 合计 / 平均）。</p>
        <button type="button" class="lb-btn lb-btn--primary" :disabled="busy" @click="onEnsure">
          启用问数增强
        </button>
      </div>
    </template>

    <div v-else class="ask-body">
      <p class="ask-drawer__hint">
        字段中文名与说明以数据模型为准；打开时自动同步维度与自动指标。此处配置业务说明、默认时间、敏感字段、数据隔离、默认过滤与业务指标。
      </p>

      <a-form
        layout="horizontal"
        :label-col="{ flex: '0 0 72px' }"
        class="ask-form"
        @submit.prevent
      >
        <div class="ask-form__row">
          <a-form-item label="默认时间" class="ask-form__half">
            <a-select
              v-model:value="form.defaultTimeField"
              allow-clear
              placeholder="默认 createTime"
              style="width: 100%"
              @change="markDirty"
            >
              <a-select-option
                v-for="opt in timeFieldOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="敏感字段" class="ask-form__half">
            <a-select
              v-model:value="form.sensitiveFields"
              mode="multiple"
              allow-clear
              max-tag-count="responsive"
              placeholder="结果中脱敏或隐藏"
              style="width: 100%"
              :options="fieldOptions"
              @change="markDirty"
            />
          </a-form-item>
        </div>
        <a-form-item label="业务说明" class="ask-form__full">
          <a-textarea
            v-model:value="form.description"
            :rows="2"
            :maxlength="512"
            show-count
            placeholder="补充业务口径，例如：默认只统计已激活客户"
            @update:value="markDirty"
          />
        </a-form-item>
      </a-form>

      <section class="ask-section">
        <div class="ask-section__head">
          <div>
            <h4>数据隔离</h4>
            <p>
              将对话 <code>callerContext</code> 映射到本表字段并强制过滤。
              <strong>有 enterpriseId</strong> 为企业用户（只按企业精确过滤，忽略地区）；
              <strong>无 enterpriseId</strong> 为行业用户（只按地区过滤，忽略企业；地区默认包含下级）。
              两边都可配置，运行时按角色互斥生效。未映射则该角色不限制。
            </p>
          </div>
        </div>
        <div class="ask-tenant-rows">
          <div
            v-for="row in tenantDimensionRows"
            :key="row.callerKey"
            class="ask-row ask-row--tenant"
          >
            <span class="ask-tenant-label">
              <span class="ask-tenant-name">{{ row.label }}</span>
              <code class="ask-tenant-key">{{ row.callerKey }}</code>
            </span>
            <a-select
              v-model:value="form.tenantDimensions[row.callerKey].field"
              class="ask-tenant-field"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="映射到表字段（可选）"
              :options="fieldOptions"
              @change="markDirty"
            />
            <a-checkbox
              v-if="row.callerKey === 'regionId'"
              v-model:checked="form.tenantDimensions.regionId.includeSubtree"
              class="ask-tenant-subtree"
              @change="markDirty"
            >
              包含下级
            </a-checkbox>
          </div>
        </div>
      </section>

      <section class="ask-section">
        <div class="ask-section__head">
          <div>
            <h4>默认过滤</h4>
            <p>每次问数自动带上的条件（支持等于、包含、区间等）</p>
          </div>
          <div class="ask-section__actions">
            <button
              type="button"
              class="lb-btn lb-btn--sm"
              :disabled="testing === 'filters'"
              @click="testDefaultFilters"
            >
              {{ testing === 'filters' ? '测试中…' : '测试过滤' }}
            </button>
            <button type="button" class="lb-btn lb-btn--sm" @click="addDefaultFilter">
              <PlusOutlined /> 添加
            </button>
          </div>
        </div>
        <div v-if="form.defaultFilters.length === 0" class="ask-section__empty">暂无，可选（无条件时测试将抽样全表）</div>
        <div v-else class="ask-rows">
          <div v-for="(row, idx) in form.defaultFilters" :key="'df-' + idx" class="ask-row">
            <a-select
              v-model:value="row.field"
              placeholder="字段"
              style="width: 160px"
              :options="fieldOptions"
              @change="markDirty"
            />
            <a-select
              v-model:value="row.op"
              style="width: 110px"
              :options="filterOpOptions"
              @change="markDirty"
            />
            <a-input
              v-if="needsFilterValue(row.op)"
              v-model:value="row.value"
              :placeholder="filterValuePlaceholder(row.op)"
              style="width: 160px"
              @update:value="markDirty"
            />
            <button type="button" class="btn-link btn-link--danger" @click="removeDefaultFilter(idx)">
              删除
            </button>
          </div>
        </div>
      </section>

      <section class="ask-section">
        <div class="ask-section__head">
          <div>
            <h4>业务指标</h4>
            <p>自定义命名口径；可带固化过滤。系统自动指标无需配置。</p>
          </div>
          <button type="button" class="lb-btn lb-btn--sm" @click="addMetric">
            <PlusOutlined /> 添加指标
          </button>
        </div>

        <div v-if="autoMetrics.length" class="ask-auto-metrics">
          <span class="ask-auto-metrics__label">系统自动：</span>
          <span
            v-for="m in autoMetrics"
            :key="m.code"
            class="ask-chip"
            :title="m.code"
          >{{ m.name }}</span>
        </div>

        <div v-if="form.customMetrics.length === 0" class="ask-section__empty">
          暂无业务指标。例如：活跃客户 = count，过滤 status=active
        </div>
        <div v-else class="ask-metric-list">
          <div
            v-for="(m, idx) in form.customMetrics"
            :key="'m-' + idx"
            class="ask-metric-card"
          >
            <div class="ask-metric-card__row">
              <a-input
                v-model:value="m.name"
                placeholder="名称，如 活跃客户"
                style="flex: 1.2; min-width: 100px"
                @update:value="onMetricName(m)"
              />
              <a-input
                v-model:value="m.code"
                placeholder="编码 active_cnt"
                style="flex: 1; min-width: 100px"
                @update:value="() => { m._codeTouched = true; markDirty() }"
              />
              <a-select
                v-model:value="m.op"
                style="width: 110px"
                :options="opOptions"
                @change="onMetricOpChange(m)"
              />
              <a-select
                v-if="m.op !== 'count'"
                v-model:value="m.field"
                allow-clear
                show-search
                option-filter-prop="label"
                placeholder="选择字段"
                style="flex: 1; min-width: 120px"
                :options="metricFieldOptions"
                @change="markDirty"
              />
              <span v-else class="ask-metric-card__no-field">计数无需字段</span>
              <button
                type="button"
                class="btn-link"
                :disabled="testing === 'metric-' + idx"
                @click="testMetric(m, idx)"
              >
                {{ testing === 'metric-' + idx ? '测试中…' : '测试' }}
              </button>
              <button type="button" class="btn-link btn-link--danger" @click="removeMetric(idx)">
                删除
              </button>
            </div>
            <div class="ask-metric-card__row ask-metric-card__row--sub">
              <span class="ask-metric-card__label">固化过滤</span>
              <a-select
                v-model:value="m.filterField"
                allow-clear
                placeholder="字段（可选）"
                style="width: 150px"
                :options="fieldOptions"
                @change="markDirty"
              />
              <a-select
                v-model:value="m.filterOp"
                style="width: 110px"
                :options="filterOpOptions"
                :disabled="!m.filterField"
                @change="markDirty"
              />
              <a-input
                v-if="m.filterField && needsFilterValue(m.filterOp)"
                v-model:value="m.filterValue"
                :placeholder="filterValuePlaceholder(m.filterOp)"
                style="width: 140px"
                @update:value="markDirty"
              />
            </div>
          </div>
        </div>
      </section>
    </div>

    <template v-if="dataset" #footer>
      <div class="ask-drawer__footer">
        <button type="button" class="lb-btn" :disabled="busy" @click="close">取消</button>
        <button
          type="button"
          class="lb-btn lb-btn--primary"
          :disabled="busy || !dirty"
          @click="onSave"
        >
          保存
        </button>
      </div>
    </template>
  </a-drawer>

  <!-- 测试结果弹窗：不撑开配置抽屉 -->
  <a-modal
    v-model:open="testModalOpen"
    :title="testResult?.title || '测试结果'"
    :width="720"
    :footer="null"
    destroy-on-close
    centered
  >
    <div v-if="testResult" class="ask-test-modal">
      <div v-if="testResult.error" class="ask-test-modal__error">{{ testResult.error }}</div>
      <template v-else>
        <p class="ask-test-modal__summary">{{ testResult.summary }}</p>
        <div class="ask-test-modal__meta">
          <template v-if="testResult.mode === 'metric' && testResult.kpi != null">
            结果值：<b>{{ testResult.kpi }}</b>
          </template>
          <template v-else>
            命中 {{ testResult.total }} 条 · 展示 {{ testResult.rows.length }} 条
          </template>
          <span v-if="testResult.elapsedMs != null"> · {{ testResult.elapsedMs }}ms</span>
          <button
            v-if="testResult.hiddenColCount > 0"
            type="button"
            class="btn-link ask-test-modal__cols-toggle"
            @click="testShowAllColumns = !testShowAllColumns"
          >
            {{ testShowAllColumns ? '收起列' : `展开全部列（+${testResult.hiddenColCount}）` }}
          </button>
        </div>
        <div
          v-if="testResult.rows.length && !(testResult.mode === 'metric' && testResult.kpi != null)"
          class="ask-test-modal__table"
        >
          <a-table
            size="small"
            :columns="visibleTestColumns"
            :data-source="testResult.rows"
            :pagination="false"
            :scroll="{ x: 'max-content', y: 280 }"
            row-key="__idx"
          />
        </div>
        <div
          v-else-if="testResult.mode === 'metric' && testResult.kpi != null"
          class="ask-test-modal__kpi"
        >
          {{ testResult.kpi }}
        </div>
        <div v-else-if="!testResult.error" class="ask-test-modal__empty">无匹配数据</div>
      </template>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  ensureAskDatasetFromModel,
  getAskDatasetByModel,
  previewAskDatasetEnhancement,
  syncAskDatasetFromModel,
  updateAskDatasetEnhancement,
} from '../../api/askData'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 数据模型：{ id, name, schema } */
  model: { type: Object, default: null },
})

const emit = defineEmits(['update:open'])

const OP_OPTIONS = [
  { value: 'count', label: '计数' },
  { value: 'count_distinct', label: '去重计数' },
  { value: 'sum', label: '合计' },
  { value: 'avg', label: '平均' },
  { value: 'min', label: '最小' },
  { value: 'max', label: '最大' },
]

const FILTER_OP_OPTIONS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'like', label: '包含' },
  { value: 'not_like', label: '不包含' },
  { value: 'starts_with', label: '开头是' },
  { value: 'not_starts_with', label: '开头不是' },
  { value: 'in', label: '属于' },
  { value: 'not_in', label: '不属于' },
  { value: 'between', label: '区间' },
  { value: 'is_null', label: '为空' },
  { value: 'is_not_null', label: '非空' },
]

const opOptions = OP_OPTIONS
const filterOpOptions = FILTER_OP_OPTIONS

function needsFilterValue(op) {
  return op !== 'is_null' && op !== 'is_not_null'
}

function filterValuePlaceholder(op) {
  if (op === 'in' || op === 'not_in') return '多值用逗号分隔'
  if (op === 'between') return '最小值,最大值'
  if (op === 'like' || op === 'not_like') return '关键字'
  if (op === 'starts_with' || op === 'not_starts_with') return '前缀'
  return '值'
}

function formatFilterValue(value) {
  if (value == null) return ''
  if (Array.isArray(value)) return value.join(',')
  return String(value)
}
const dataset = ref(null)
const autoMetrics = ref([])
const loading = ref(false)
const busy = ref(false)
const dirty = ref(false)
const testing = ref('')
const testModalOpen = ref(false)
const testResult = ref(null)
const testShowAllColumns = ref(false)

/** 明细预览默认展示列数（不含系统字段优先） */
const PREVIEW_COL_LIMIT = 6
const SYSTEM_COL_ORDER = ['id', 'createTime', 'updateTime', 'create_time', 'update_time']

const TENANT_DIMENSION_DEFS = [
  { callerKey: 'regionId', label: '地区 ID' },
  { callerKey: 'enterpriseId', label: '企业 ID' },
]

const tenantDimensionRows = TENANT_DIMENSION_DEFS

function emptyTenantDimensions() {
  return {
    regionId: { field: undefined, includeSubtree: true },
    enterpriseId: { field: undefined },
  }
}

const form = reactive({
  description: '',
  defaultTimeField: 'createTime',
  sensitiveFields: [],
  defaultFilters: [],
  customMetrics: [],
  tenantDimensions: emptyTenantDimensions(),
})

const modelId = computed(() => (props.model?.id != null ? String(props.model.id) : null))

const fieldOptions = computed(() => {
  const fields = props.model?.schema?.fields || []
  const opts = fields.map((f) => ({
    value: f.key,
    label: f.label ? `${f.label}（${f.key}）` : f.key,
  }))
  opts.unshift(
    { value: 'createTime', label: '创建时间（createTime）' },
    { value: 'updateTime', label: '更新时间（updateTime）' },
  )
  return opts
})

const metricFieldOptions = computed(() => {
  const fields = props.model?.schema?.fields || []
  const opts = fields.map((f) => ({
    value: f.key,
    label: f.label ? `${f.label}（${f.key}）` : f.key,
  }))
  const system = [
    { value: 'id', label: '主键（id）' },
    { value: 'createTime', label: '创建时间（createTime）' },
    { value: 'updateTime', label: '更新时间（updateTime）' },
  ]
  const seen = new Set(opts.map((o) => o.value))
  for (const s of system) {
    if (!seen.has(s.value)) opts.push(s)
  }
  return opts
})

const timeFieldOptions = computed(() => {
  const fields = props.model?.schema?.fields || []
  const fromSchema = fields
    .filter((f) => ['date', 'datetime', 'time'].includes(String(f.type || '').toLowerCase()))
    .map((f) => ({
      value: f.key,
      label: f.label ? `${f.label}（${f.key}）` : f.key,
    }))
  const base = [
    { value: 'createTime', label: '创建时间（createTime）' },
    { value: 'updateTime', label: '更新时间（updateTime）' },
  ]
  const seen = new Set(base.map((o) => o.value))
  for (const o of fromSchema) {
    if (!seen.has(o.value)) {
      base.push(o)
      seen.add(o.value)
    }
  }
  return base
})

function isAutoMetricCode(code) {
  if (!code) return false
  const c = String(code).toLowerCase()
  return c === 'cnt' || c.startsWith('sum_') || c.startsWith('avg_')
}

function markDirty() {
  dirty.value = true
}

function slugCode(name) {
  const s = String(name || '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '_')
    .replace(/[^a-z0-9_]/g, '')
  if (s && /^[a-z]/.test(s)) return s.slice(0, 64)
  return ''
}

function onMetricName(m) {
  if (!m._codeTouched) {
    const next = slugCode(m.name)
    if (next) m.code = next
  }
  markDirty()
}

function onMetricOpChange(m) {
  if (m.op === 'count') m.field = undefined
  markDirty()
}

function mapFiltersToRows(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) {
    return raw.map((f) => ({
      field: f.field,
      op: f.op || 'eq',
      value: formatFilterValue(f.value),
    }))
  }
  if (typeof raw === 'object') {
    return Object.entries(raw).map(([field, value]) => ({
      field,
      op: 'eq',
      value: formatFilterValue(value),
    }))
  }
  return []
}

function rowsToFilterList(rows) {
  return (rows || [])
    .filter((row) => row?.field)
    .map((row) => ({
      field: row.field,
      op: row.op || 'eq',
      value: needsFilterValue(row.op) ? (row.value ?? '') : null,
    }))
}

function mapCustomMetrics(metrics) {
  return (metrics || [])
    .filter((m) => m?.code && !isAutoMetricCode(m.code))
    .map((m) => {
      const filters = mapFiltersToRows(m.filters)
      const first = filters[0] || {}
      return {
        name: m.name || '',
        code: m.code || '',
        op: m.op || 'count',
        field: m.field || undefined,
        filterField: first.field || undefined,
        filterOp: first.op || 'eq',
        filterValue: first.value ?? '',
        _codeTouched: true,
      }
    })
}

function applyDataset(ds) {
  dataset.value = ds
  form.description = ds?.description || ''
  form.defaultTimeField = ds?.defaultTimeField || 'createTime'
  form.sensitiveFields = [...(ds?.sensitiveFields || [])]
  form.defaultFilters = mapFiltersToRows(ds?.defaultFilters)
  form.customMetrics = mapCustomMetrics(ds?.metrics)
  const td = ds?.tenantDimensions || {}
  const regionRaw = td.regionId
  if (typeof regionRaw === 'string' && regionRaw) {
    // 旧格式纯字符串等价 match=eq
    form.tenantDimensions.regionId = { field: regionRaw, includeSubtree: false }
  } else if (regionRaw && typeof regionRaw === 'object' && regionRaw.field) {
    form.tenantDimensions.regionId = {
      field: regionRaw.field,
      includeSubtree: regionRaw.match === 'subtree',
    }
  } else {
    form.tenantDimensions.regionId = { field: undefined, includeSubtree: true }
  }
  const entRaw = td.enterpriseId
  if (typeof entRaw === 'string' && entRaw) {
    form.tenantDimensions.enterpriseId = { field: entRaw }
  } else if (entRaw && typeof entRaw === 'object' && entRaw.field) {
    form.tenantDimensions.enterpriseId = { field: entRaw.field }
  } else {
    form.tenantDimensions.enterpriseId = { field: undefined }
  }
  autoMetrics.value = (ds?.metrics || []).filter((m) => isAutoMetricCode(m.code))
  dirty.value = false
  testModalOpen.value = false
  testResult.value = null
  testShowAllColumns.value = false
}

/** 组装 tenantDimensions：仅提交已选择映射的键 */
function buildTenantDimensionsPayload() {
  const out = {}
  const region = form.tenantDimensions.regionId
  if (region?.field) {
    out.regionId = {
      field: region.field,
      match: region.includeSubtree !== false ? 'subtree' : 'eq',
    }
  }
  const enterprise = form.tenantDimensions.enterpriseId
  if (enterprise?.field) {
    out.enterpriseId = { field: enterprise.field, match: 'eq' }
  }
  return out
}

const fieldLabelMap = computed(() => {
  const map = {
    id: '主键',
    createTime: '创建时间',
    updateTime: '更新时间',
    create_time: '创建时间',
    update_time: '更新时间',
  }
  for (const f of props.model?.schema?.fields || []) {
    if (f.key) map[f.key] = f.label || f.key
  }
  return map
})

const visibleTestColumns = computed(() => {
  const all = testResult.value?.allColumns || []
  if (testShowAllColumns.value || all.length <= PREVIEW_COL_LIMIT) return all
  return all.slice(0, PREVIEW_COL_LIMIT)
})

function columnTitle(key) {
  const label = fieldLabelMap.value[key]
  return label && label !== key ? `${label}` : key
}

function pickPreviewColumnKeys(keys) {
  const list = keys || []
  // 业务字段按返回顺序优先，系统字段置后
  const preferred = list.filter((k) => !SYSTEM_COL_ORDER.includes(k))
  for (const k of SYSTEM_COL_ORDER) {
    if (list.includes(k) && !preferred.includes(k)) preferred.push(k)
  }
  return preferred
}

function openTestModal(result) {
  testResult.value = result
  testShowAllColumns.value = false
  testModalOpen.value = true
}

async function loadDataset() {
  if (!modelId.value) {
    applyDataset(null)
    return
  }
  loading.value = true
  try {
    const res = await getAskDatasetByModel(modelId.value)
    let ds = res?.data || null
    if (ds?.id) {
      try {
        const synced = await syncAskDatasetFromModel(ds.id)
        ds = synced?.data || ds
      } catch {
        /* ignore */
      }
    }
    applyDataset(ds)
  } catch {
    applyDataset(null)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, modelId.value],
  ([isOpen]) => {
    if (isOpen && modelId.value) {
      loadDataset()
    }
  },
)

function onOpenChange(v) {
  emit('update:open', v)
}

function close() {
  emit('update:open', false)
}

function addDefaultFilter() {
  form.defaultFilters.push({ field: undefined, op: 'eq', value: '' })
  markDirty()
}

function removeDefaultFilter(idx) {
  form.defaultFilters.splice(idx, 1)
  markDirty()
}

function addMetric() {
  form.customMetrics.push({
    name: '',
    code: '',
    op: 'count',
    field: undefined,
    filterField: undefined,
    filterOp: 'eq',
    filterValue: '',
    _codeTouched: false,
  })
  markDirty()
}

function removeMetric(idx) {
  form.customMetrics.splice(idx, 1)
  markDirty()
}

function buildMetricPayload(m) {
  const filters = m.filterField
    ? [{
        field: m.filterField,
        op: m.filterOp || 'eq',
        value: needsFilterValue(m.filterOp) ? (m.filterValue ?? '') : null,
      }]
    : []
  return {
    code: (m.code || '').trim().toLowerCase(),
    name: (m.name || '').trim(),
    op: m.op || 'count',
    field: m.op === 'count' ? undefined : m.field,
    filters,
    format: 'number',
    synonyms: [],
  }
}

function buildCustomMetricsPayload() {
  return form.customMetrics.map((m) => buildMetricPayload(m))
}

function parsePreviewResult(data, { mode, title }) {
  const table = data?.table || {}
  const rawKeys = table.columns || []
  const orderedKeys = pickPreviewColumnKeys(rawKeys)
  const allColumns = orderedKeys.map((key) => ({
    title: columnTitle(key),
    dataIndex: key,
    key,
    ellipsis: true,
    width: 128,
  }))
  const rows = (table.rows || []).map((row, i) => ({ ...row, __idx: i }))
  let kpi = null
  if (mode === 'metric' && rows.length === 1 && orderedKeys.length) {
    const last = orderedKeys[orderedKeys.length - 1]
    kpi = rows[0][last]
  }
  const hiddenColCount = Math.max(0, allColumns.length - PREVIEW_COL_LIMIT)
  return {
    mode,
    title,
    summary: data?.summary || '',
    total: table.total != null ? table.total : rows.length,
    allColumns,
    hiddenColCount,
    rows,
    kpi,
    elapsedMs: data?.elapsedMs,
    error: null,
  }
}

async function testDefaultFilters() {
  if (!dataset.value?.id) return
  for (const row of form.defaultFilters) {
    if (!row.field) {
      message.warning('请完善过滤字段')
      return
    }
    if (needsFilterValue(row.op) && (row.value == null || String(row.value).trim() === '')) {
      message.warning('请完善过滤值')
      return
    }
  }
  testing.value = 'filters'
  try {
    const res = await previewAskDatasetEnhancement(dataset.value.id, {
      mode: 'default_filters',
      defaultFilters: rowsToFilterList(form.defaultFilters),
      limit: 5,
    })
    openTestModal(parsePreviewResult(res.data, {
      mode: 'default_filters',
      title: `过滤测试 · ${props.model?.name || ''}`,
    }))
  } catch (e) {
    openTestModal({
      mode: 'default_filters',
      title: `过滤测试 · ${props.model?.name || ''}`,
      error: e?.message || '测试失败',
      summary: '',
      rows: [],
      allColumns: [],
      hiddenColCount: 0,
      total: 0,
    })
  } finally {
    testing.value = ''
  }
}

async function testMetric(m, idx) {
  if (!dataset.value?.id) return
  if (!m.name?.trim() || !m.code?.trim()) {
    message.warning('请先填写指标名称与编码')
    return
  }
  if (m.op !== 'count' && !m.field) {
    message.warning('请选择聚合字段')
    return
  }
  testing.value = 'metric-' + idx
  try {
    const res = await previewAskDatasetEnhancement(dataset.value.id, {
      mode: 'metric',
      defaultFilters: rowsToFilterList(form.defaultFilters),
      metric: buildMetricPayload(m),
      limit: 20,
    })
    openTestModal(parsePreviewResult(res.data, {
      mode: 'metric',
      title: `指标测试 · ${m.name || m.code}`,
    }))
  } catch (e) {
    openTestModal({
      mode: 'metric',
      title: `指标测试 · ${m.name || m.code}`,
      error: e?.message || '测试失败',
      summary: '',
      rows: [],
      allColumns: [],
      hiddenColCount: 0,
      total: 0,
    })
  } finally {
    testing.value = ''
  }
}

async function onEnsure() {
  if (!modelId.value) return
  busy.value = true
  try {
    const res = await ensureAskDatasetFromModel(modelId.value)
    applyDataset(res.data)
    message.success('已启用问数增强')
  } catch (e) {
    message.error(e?.message || '启用失败')
  } finally {
    busy.value = false
  }
}

async function onSave() {
  if (!dataset.value?.id) return
  for (const m of form.customMetrics) {
    if (!m.name?.trim() || !m.code?.trim()) {
      message.warning('请完善业务指标的名称与编码')
      return
    }
    if (m.op !== 'count' && !m.field) {
      message.warning(`指标「${m.name}」需要选择聚合字段`)
      return
    }
  }
  busy.value = true
  try {
    const res = await updateAskDatasetEnhancement(dataset.value.id, {
      description: form.description || '',
      defaultTimeField: form.defaultTimeField || 'createTime',
      sensitiveFields: form.sensitiveFields || [],
      defaultFilters: rowsToFilterList(form.defaultFilters),
      customMetrics: buildCustomMetricsPayload(),
      tenantDimensions: buildTenantDimensionsPayload(),
    })
    applyDataset(res.data)
    message.success('已保存')
    close()
  } catch (e) {
    message.error(e?.message || '保存失败')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.ask-drawer__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ask-drawer__model {
  font-weight: 500;
  color: var(--color-ink);
}
.ask-drawer__meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 8px;
  border-radius: 999px;
}
.ask-drawer__hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.ask-drawer__loading {
  color: var(--color-mute);
  font-size: 13px;
  padding: 24px 0;
}
.ask-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.ask-empty-card {
  padding: 24px;
  border-radius: 12px;
  border: 1px dashed var(--color-hairline);
  background: var(--color-canvas-soft);
}
.ask-empty-card p {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-mute);
  line-height: 1.55;
}
.ask-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ask-tenant-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ask-row--tenant {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
}
.ask-tenant-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 168px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-ink);
  white-space: nowrap;
}
.ask-tenant-name {
  flex-shrink: 0;
}
.ask-tenant-field {
  width: 220px;
  flex: 0 0 220px;
}
.ask-tenant-subtree {
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-ink);
}
.ask-tenant-key {
  font-size: 11px;
  font-weight: 400;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 0 5px;
  border-radius: 4px;
}
.ask-drawer__hint code,
.ask-section__head p code {
  font-size: 11px;
  background: var(--color-canvas-soft-2);
  padding: 0 4px;
  border-radius: 3px;
}
.ask-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.ask-form__half,
.ask-form__full {
  margin-bottom: 12px !important;
}
.ask-form :deep(.ant-form-item) {
  margin-bottom: 12px;
}
.ask-form :deep(.ant-form-item-label > label) {
  font-size: 13px;
  height: 32px;
}
.ask-section {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--color-hairline);
}
.ask-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.ask-section__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.ask-test-modal__summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--color-ink);
  line-height: 1.5;
}
.ask-test-modal__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 12px;
}
.ask-test-modal__meta b {
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.ask-test-modal__cols-toggle {
  margin-left: auto;
}
.ask-test-modal__table {
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  overflow: hidden;
}
.ask-test-modal__table :deep(.ant-table-cell) {
  max-width: 160px;
}
.ask-test-modal__kpi {
  padding: 28px 16px;
  text-align: center;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-ink);
  background: var(--color-canvas-soft);
  border-radius: 10px;
}
.ask-test-modal__empty,
.ask-test-modal__error {
  font-size: 13px;
  line-height: 1.5;
  padding: 16px 0;
}
.ask-test-modal__error {
  color: var(--color-error);
}
.ask-section__head h4 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}
.ask-section__head p {
  margin: 0;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.45;
}
.ask-section__empty {
  font-size: 12px;
  color: var(--color-mute);
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-canvas-soft);
}
.ask-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ask-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ask-auto-metrics {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}
.ask-auto-metrics__label {
  font-size: 12px;
  color: var(--color-mute);
}
.ask-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}
.ask-metric-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ask-metric-card {
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  background: var(--color-canvas-soft);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ask-metric-card__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ask-metric-card__label {
  font-size: 12px;
  color: var(--color-mute);
  width: 56px;
  flex-shrink: 0;
}
.ask-metric-card__no-field {
  flex: 1;
  min-width: 88px;
  font-size: 12px;
  color: var(--color-mute);
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  padding: 0 4px;
}
.btn-link--danger {
  color: var(--color-error);
}
@media (max-width: 640px) {
  .ask-form__row {
    grid-template-columns: 1fr;
  }
}
</style>
