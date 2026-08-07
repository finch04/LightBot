<template>
  <div class="form-designer">
    <aside class="field-panel">
      <div class="field-panel__title">拖拽添加字段</div>
      <div
        v-for="item in FIELD_TYPES"
        :key="item.type"
        class="field-item"
        draggable="true"
        @dragstart="onDragStart($event, item)"
      >
        {{ item.label }}
      </div>
    </aside>

    <div
      class="canvas"
      :class="{ 'canvas--drag-over': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave="dragOver = false"
      @drop.prevent="onDrop"
    >
      <div v-if="customFields.length === 0" class="canvas__hint">
        从左侧拖拽字段到此处；底部系统字段固定且不可修改
      </div>
      <div class="canvas__list">
        <div
          v-for="(field, idx) in fields"
          :key="field._id"
          :class="['field-row', field.system ? 'field-row--system' : 'field-row--custom']"
        >
          <span class="field-row__index">{{ idx + 1 }}.</span>

          <template v-if="field.system">
            <span class="type-badge type-badge--system">系统</span>
            <span class="type-badge">日期时间</span>
            <span class="field-row__label-text">{{ field.label }}</span>
            <span class="field-row__hint">精确到时分秒 · 自动写入 · 不可调整</span>
            <LockOutlined class="field-row__lock" />
          </template>

          <template v-else>
            <div class="field-row__actions">
              <button
                type="button"
                class="btn-link"
                :disabled="!canMoveUp(idx)"
                @click="moveField(idx, -1)"
              >上移</button>
              <button
                type="button"
                class="btn-link"
                :disabled="!canMoveDown(idx)"
                @click="moveField(idx, 1)"
              >下移</button>
            </div>
            <span class="type-badge">{{ getTypeLabel(field.type) }}</span>
            <a-input
              :value="field.label"
              placeholder="中文名（≤20）"
              :maxlength="20"
              show-count
              class="field-row__label"
              @update:value="(v) => (field.label = filterLabel(v ?? ''))"
            />
            <div class="field-row__key-wrap">
              <a-input
                :value="field._key"
                placeholder="英文名（数据库列名）"
                :maxlength="64"
                class="field-row__key"
                :disabled="field._keyLocked"
                :status="keyErrorOf(field) ? 'error' : undefined"
                @update:value="(v) => onKeyInput(field, v)"
                @blur="field._keyTouched = true"
              />
              <span v-if="keyErrorOf(field)" class="field-row__key-error">{{ keyErrorOf(field) }}</span>
              <span
                v-else-if="field._key"
                class="field-row__key-hint"
                :title="'物理列: ' + toColumnPreview(field._key)"
              >
                → {{ toColumnPreview(field._key) }}
              </span>
            </div>
            <a-input
              v-model:value="field.description"
              placeholder="字段描述（大模型 / DDL 备注）"
              :maxlength="100"
              show-count
              class="field-row__description"
              allow-clear
            />
            <a-checkbox v-model:checked="field.required" class="field-row__required">必填</a-checkbox>
            <div class="field-row__extra">
              <template v-if="['select', 'radio', 'checkbox'].includes(field.type)">
                <button type="button" class="lb-btn lb-btn--sm" @click="openOptionsDialog(field)">
                  {{ field.options.length ? `已配置 ${field.options.length} 项` : '配置选项' }}
                </button>
              </template>
              <template v-else-if="field.type === 'number'">
                <a-input-number v-model:value="field.props.min" placeholder="最小值" class="field-row__num" />
                <a-input-number v-model:value="field.props.max" placeholder="最大值" class="field-row__num" />
              </template>
              <template v-else-if="field.type === 'upload'">
                <span class="field-row__hint">最多</span>
                <a-input-number v-model:value="field.props.limit" :min="1" :max="50" class="field-row__num" />
                <span class="field-row__hint">个文件</span>
              </template>
            </div>
            <button type="button" class="btn-link btn-link--danger field-row__delete" @click="removeField(idx)">删除</button>
          </template>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="optionsDialog.visible"
      title="配置选项"
      :width="480"
      destroy-on-close
      @ok="confirmOptions"
      @cancel="optionsDialog.visible = false"
    >
      <div class="options-list">
        <div v-for="(_, optionIndex) in optionsDialog.list" :key="optionIndex" class="options-list__row">
          <a-input v-model:value="optionsDialog.list[optionIndex]" placeholder="请输入选项内容" />
          <button type="button" class="btn-link btn-link--danger options-list__remove" @click="removeOption(optionIndex)">删除</button>
        </div>
      </div>
      <button type="button" class="lb-btn options-list__add" @click="addOption">新增选项</button>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { LockOutlined } from '@ant-design/icons-vue'

const FIELD_TYPES = [
  { type: 'input', label: '单行文本' },
  { type: 'textarea', label: '多行文本' },
  { type: 'number', label: '数字' },
  { type: 'date', label: '日期' },
  { type: 'datetime', label: '日期时间' },
  { type: 'select', label: '下拉选择' },
  { type: 'radio', label: '单选' },
  { type: 'checkbox', label: '多选' },
  { type: 'upload', label: '附件' },
]

/** 系统固定字段：始终存在、精确到秒、不可调整 */
const SYSTEM_FIELD_DEFS = [
  {
    key: 'createTime',
    label: '创建时间',
    type: 'datetime',
    required: true,
    system: true,
    props: { precision: 'second', format: 'YYYY-MM-DD HH:mm:ss', readonly: true },
  },
  {
    key: 'updateTime',
    label: '更新时间',
    type: 'datetime',
    required: true,
    system: true,
    props: { precision: 'second', format: 'YYYY-MM-DD HH:mm:ss', readonly: true },
  },
]

const SYSTEM_KEYS = new Set(SYSTEM_FIELD_DEFS.map((f) => f.key))

const props = defineProps({
  modelValue: { type: Object, default: () => ({ fields: [] }) },
})

const dragOver = ref(false)
const fields = ref([])
let dragType = null
let idSeed = 0

const optionsDialog = reactive({
  visible: false,
  field: null,
  list: [],
})

const customFields = computed(() => fields.value.filter((f) => !f.system))

const LABEL_REG = /[\u4e00-\u9fa5a-zA-Z0-9_]/g
/** 英文名：字母开头，字母数字下划线（与后端列名规则对齐） */
const KEY_REG = /[a-zA-Z0-9_]/g
const RESERVED_KEYS = new Set([
  'id', 'deleted', 'createTime', 'updateTime',
  'create_time', 'update_time', 'createtime', 'updatetime',
])

function filterLabel(value) {
  if (value == null || typeof value !== 'string') return ''
  return value.match(LABEL_REG)?.join('')?.slice(0, 20) ?? ''
}

function filterKey(value) {
  if (value == null || typeof value !== 'string') return ''
  // 仅过滤非法字符，保留数字开头以便页面即时提示「必须以字母开头」
  return value.match(KEY_REG)?.join('')?.slice(0, 64) ?? ''
}

function onKeyInput(field, value) {
  field._key = filterKey(value ?? '')
  field._keyTouched = true
}

/**
 * 英文名校验错误（用于输入框下方提示）
 * @param {object} field
 * @returns {string}
 */
function keyErrorOf(field) {
  if (!field || field.system || field._keyLocked) return ''
  const key = String(field._key || '').trim()
  if (!key) {
    return field._keyTouched ? '请填写英文名（将作为数据库列名）' : ''
  }
  if (/^[0-9]/.test(key)) {
    return '英文名必须以字母开头，不能以数字开头'
  }
  if (key.startsWith('_')) {
    return '英文名必须以字母开头，不能以下划线开头'
  }
  if (!/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(key)) {
    return '仅允许字母、数字、下划线'
  }
  if (RESERVED_KEYS.has(key) || RESERVED_KEYS.has(key.toLowerCase())) {
    return `与系统字段冲突：${key}`
  }
  const col = toColumnPreview(key)
  if (!/^[a-z][a-z0-9_]{0,62}$/.test(col)) {
    return '无法生成合法数据库列名'
  }
  const dup = fields.value.some(
    (f) => !f.system && f._id !== field._id && String(f._key || '').trim() === key,
  )
  if (dup) {
    return '英文名与其它字段重复'
  }
  const colDup = fields.value.some((f) => {
    if (f.system || f._id === field._id) return false
    const other = String(f._key || '').trim()
    return other && toColumnPreview(other) === col
  })
  if (colDup) {
    return `列名 ${col} 与其它字段冲突`
  }
  return ''
}

/** 预览物理列名（camelCase → snake_case，与后端 toColumnName 一致） */
function toColumnPreview(key) {
  const raw = String(key || '').trim()
  if (!raw) return ''
  if (raw === 'createTime') return 'create_time'
  if (raw === 'updateTime') return 'update_time'
  let out = ''
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i]
    if (c >= 'A' && c <= 'Z') {
      if (i > 0) out += '_'
      out += c.toLowerCase()
    } else {
      out += c.toLowerCase()
    }
  }
  return out
}

function getTypeLabel(type) {
  return FIELD_TYPES.find((item) => item.type === type)?.label || type || '-'
}

function genId() {
  idSeed += 1
  return `f_${idSeed}_${Date.now()}`
}

function buildSystemFields() {
  return SYSTEM_FIELD_DEFS.map((def) => ({
    _id: `sys_${def.key}`,
    _key: def.key,
    label: def.label,
    type: def.type,
    required: def.required,
    system: true,
    options: [],
    props: { ...def.props },
  }))
}

function ensureSystemFields(list) {
  const custom = list.filter((f) => !f.system && !SYSTEM_KEYS.has(f._key))
  return [...custom, ...buildSystemFields()]
}

function onDragStart(event, item) {
  dragType = item
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', item.type)
}

function onDrop() {
  dragOver.value = false
  if (!dragType) return
  const hasOptions = ['select', 'radio', 'checkbox'].includes(dragType.type)
  let initProps = {}
  if (dragType.type === 'number') initProps = { min: undefined, max: undefined }
  if (dragType.type === 'upload') initProps = { limit: 10 }
  const custom = fields.value.filter((f) => !f.system)
  custom.push({
    _id: genId(),
    _key: '',
    _keyLocked: false,
    _keyTouched: false,
    label: dragType.label,
    description: '',
    type: dragType.type,
    required: false,
    system: false,
    options: hasOptions ? [] : [],
    props: initProps,
  })
  fields.value = ensureSystemFields(custom)
  dragType = null
}

function removeField(index) {
  const field = fields.value[index]
  if (!field || field.system) return
  const next = fields.value.filter((_, i) => i !== index)
  fields.value = ensureSystemFields(next)
}

function canMoveUp(index) {
  const field = fields.value[index]
  if (!field || field.system) return false
  return index > 0 && !fields.value[index - 1]?.system
}

function canMoveDown(index) {
  const field = fields.value[index]
  if (!field || field.system) return false
  const next = fields.value[index + 1]
  return next && !next.system
}

function moveField(index, delta) {
  if (delta < 0 && !canMoveUp(index)) return
  if (delta > 0 && !canMoveDown(index)) return
  const next = index + delta
  const list = [...fields.value]
  const current = list[index]
  list[index] = list[next]
  list[next] = current
  fields.value = ensureSystemFields(list)
}

function openOptionsDialog(field) {
  if (field.system) return
  optionsDialog.field = field
  const options = field.options || []
  optionsDialog.list = options.length
    ? options.map((item) => String(item.label ?? item.value ?? ''))
    : ['']
  optionsDialog.visible = true
}

function addOption() {
  optionsDialog.list.push('')
}

function removeOption(index) {
  optionsDialog.list.splice(index, 1)
}

function confirmOptions() {
  if (!optionsDialog.field || optionsDialog.field.system) return
  optionsDialog.field.options = optionsDialog.list
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .map((text) => ({ value: text, label: text }))
  optionsDialog.visible = false
}

function resolveFieldKey(field) {
  return String(field._key || '').trim()
}

/**
 * 保存前校验自定义字段英文名
 * @returns {string|null} 错误信息，null 表示通过
 */
function validate() {
  const customs = fields.value.filter((f) => !f.system)
  for (const field of customs) {
    field._keyTouched = true
  }
  for (let i = 0; i < customs.length; i++) {
    const field = customs[i]
    const err = keyErrorOf(field)
    if (err) {
      const label = field.label || `第 ${i + 1} 个字段`
      return `「${label}」${err}`
    }
  }
  return null
}

function schemaFromFields() {
  return fields.value.map((field) => {
    const result = {
      key: field.system ? field._key : resolveFieldKey(field),
      label: field.label || '未命名',
      type: field.type,
      required: !!field.required,
    }
    if (field.system) {
      result.system = true
      result.props = {
        precision: 'second',
        format: 'YYYY-MM-DD HH:mm:ss',
        readonly: true,
      }
      return result
    }
    const desc = String(field.description || '').trim()
    if (desc) {
      result.description = desc.slice(0, 100)
    }
    if (field.props && Object.keys(field.props).length) {
      result.props = { ...field.props }
    }
    if (field.type === 'upload' && (!result.props || result.props.limit == null)) {
      result.props = { ...(result.props || {}), limit: 10 }
    }
    if (['select', 'radio', 'checkbox'].includes(field.type) && Array.isArray(field.options) && field.options.length) {
      result.props = {
        ...(result.props || {}),
        options: field.options.map((item) => ({ value: item.value, label: item.label ?? item.value })),
      }
    }
    return result
  })
}

function mapRawField(field, index) {
  const options = Array.isArray(field.props?.options)
    ? field.props.options.map((item) => ({ value: item.value ?? '', label: item.label ?? item.value ?? '' }))
    : []
  let fieldProps = {}
  if (field.type === 'number' && field.props) fieldProps = { min: field.props.min, max: field.props.max }
  if (field.type === 'upload' && field.props) fieldProps = { limit: field.props.limit ?? 10 }
  if (field.type === 'datetime' && field.props) fieldProps = { ...field.props }
  const key = field.key || ''
  const isSystem = !!field.system || SYSTEM_KEYS.has(key)
  // 自动生成的 field_N 允许改成可读英文名；已手工命名的锁定，避免改 key 产生孤儿列
  const autoKey = /^field_\d+$/.test(key)
  return {
    _id: key || `field_${index}`,
    _key: key,
    _keyLocked: !isSystem && !!key && !autoKey,
    _keyTouched: false,
    label: field.label || '',
    description: field.description || '',
    type: field.type || 'input',
    required: !!field.required,
    system: isSystem,
    options,
    props: fieldProps,
  }
}

watch(
  () => props.modelValue,
  (value) => {
    const raw = (value?.fields || []).filter((f) => !SYSTEM_KEYS.has(f.key) && !f.system)
    const mapped = raw.map((field, index) => mapRawField(field, index))
    fields.value = ensureSystemFields(mapped)
  },
  { immediate: true, deep: true },
)

/**
 * 收集英文名为空、可编辑的自定义字段（供 AI 补全）
 * @returns {{ targets: object[], occupiedKeys: string[] }}
 */
function collectEmptyKeyTargets() {
  const targets = []
  const occupiedKeys = []
  for (const field of fields.value) {
    const key = String(field._key || '').trim()
    if (field.system) {
      if (key) occupiedKeys.push(key)
      continue
    }
    if (key) {
      occupiedKeys.push(key)
      continue
    }
    // 已锁定且为空极少见；仍跳过，避免覆盖
    if (field._keyLocked) continue
    targets.push(field)
  }
  return { targets, occupiedKeys }
}

/**
 * 将 AI 返回的英文名写入目标字段（仅写入仍为空的项）
 * @param {object[]} targets
 * @param {string[]} keys
 * @returns {number} 实际写入数量
 */
function applySuggestedKeys(targets, keys) {
  if (!Array.isArray(targets) || !Array.isArray(keys)) return 0
  let filled = 0
  for (let i = 0; i < targets.length; i++) {
    const field = targets[i]
    if (!field || field.system || field._keyLocked) continue
    if (String(field._key || '').trim()) continue
    const next = filterKey(keys[i] ?? '')
    if (!next) continue
    field._key = next
    field._keyTouched = true
    filled += 1
  }
  return filled
}

defineExpose({
  getSchema() {
    return { fields: schemaFromFields() }
  },
  /** 供「索引与检索」读取当前字段快照（含系统字段） */
  getFields() {
    return schemaFromFields()
  },
  validate,
  collectEmptyKeyTargets,
  applySuggestedKeys,
})
</script>

<style scoped>
.form-designer {
  display: flex;
  gap: 16px;
  min-height: 0;
  height: 100%;
  flex: 1;
}
.field-panel {
  width: 168px;
  flex-shrink: 0;
  align-self: stretch;
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 12px;
  background: var(--color-canvas-soft);
  overflow-y: auto;
}
.field-panel__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.field-item {
  margin-bottom: 8px;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
  font-size: 13px;
  color: var(--color-ink);
  cursor: grab;
  transition: border-color 0.15s ease, background-color 0.15s ease, transform 0.15s ease;
}
.field-item:hover {
  border-color: var(--color-ink);
  background: var(--color-canvas-soft-2);
}
.field-item:active {
  cursor: grabbing;
  transform: scale(0.98);
}
.canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  align-self: stretch;
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 16px;
  background: var(--color-canvas);
  overflow: auto;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.canvas--drag-over {
  border-style: dashed;
  border-color: var(--color-link);
  background: var(--color-info-bg);
}
.canvas__hint {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-canvas-soft);
  color: var(--color-mute);
  font-size: 13px;
}
.canvas__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field-row {
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  background: var(--color-canvas-soft);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.field-row:hover {
  border-color: var(--color-hairline-strong);
}
/* 自定义字段：固定列宽；顶部对齐以便英文名错误提示换行时不错位 */
.field-row--custom {
  display: grid;
  align-items: start;
  grid-template-columns:
    28px
    72px
    88px
    minmax(120px, 150px)
    minmax(140px, 180px)
    minmax(140px, 1fr)
    56px
    minmax(0, 180px)
    40px;
}
.field-row--custom > .field-row__index,
.field-row--custom > .field-row__actions,
.field-row--custom > .type-badge,
.field-row--custom > .field-row__required,
.field-row--custom > .field-row__extra,
.field-row--custom > .field-row__delete {
  margin-top: 6px;
}
.field-row--system {
  display: flex;
  flex-wrap: wrap;
  background: var(--color-canvas-soft-2);
  border-style: dashed;
  opacity: 0.95;
}
.field-row__index {
  color: var(--color-mute);
  font-size: 13px;
  width: 28px;
  text-align: right;
}
.field-row__actions {
  display: flex;
  gap: 2px;
  width: 72px;
  flex-shrink: 0;
}
.field-row__label {
  width: 100% !important;
  min-width: 0;
}
.field-row__label :deep(.ant-input) {
  width: 100%;
}
.field-row__key-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  width: 100%;
}
.field-row__key {
  width: 100% !important;
  min-width: 0;
}
.field-row__key :deep(.ant-input) {
  width: 100%;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
.field-row__key-hint {
  font-size: 11px;
  color: var(--color-mute);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-row__key-error {
  font-size: 11px;
  color: var(--color-error, #ff4d4f);
  line-height: 1.25;
  word-break: break-all;
}
.field-row__description {
  width: 100% !important;
  min-width: 0;
}
.field-row__description :deep(.ant-input) {
  width: 100%;
}
.field-row__required {
  white-space: nowrap;
  justify-self: start;
}
.field-row__extra {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.field-row__num {
  width: 100px !important;
}
.field-row__label-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  min-width: 72px;
}
.field-row__hint {
  font-size: 12px;
  color: var(--color-mute);
  white-space: nowrap;
}
.field-row__lock {
  margin-left: auto;
  color: var(--color-mute);
  font-size: 14px;
}
.field-row__delete {
  justify-self: end;
  white-space: nowrap;
}
.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  box-sizing: border-box;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  color: var(--color-body);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.type-badge--system {
  background: var(--color-canvas-soft-3);
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
.btn-link:disabled {
  color: var(--color-mute);
  cursor: not-allowed;
  opacity: 0.5;
}
.btn-link--danger {
  color: var(--color-error);
}
.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}
.options-list__row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.options-list__row :deep(.ant-input) {
  flex: 1;
  min-width: 0;
}
.options-list__remove {
  flex-shrink: 0;
  white-space: nowrap;
}
.options-list__add {
  width: 100%;
  margin-top: 12px;
}
</style>
