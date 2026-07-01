<template>
  <div ref="wrapperRef" class="model-select-wrapper" :style="wrapperStyle">
    <a-tooltip
      :title="selectedTooltipText || undefined"
      placement="topLeft"
      :overlay-style="{ maxWidth: '420px' }"
      overlay-class-name="model-select-tooltip"
      :open="selectorTooltipOpen"
      :mouse-enter-delay="0.35"
    >
      <div
        class="model-select-inner"
        @mouseenter="selectorHovered = true"
        @mouseleave="selectorHovered = false"
      >
        <a-select
          :value="displayValue"
          @update:value="handleUpdate"
          :placeholder="placeholder || '选择模型'"
          :disabled="disabled"
          style="width: 100%"
          show-search
          :filter-option="false"
          :loading="loading"
          allow-clear
          :dropdownMatchSelectWidth="false"
          option-label-prop="label"
          :popup-class-name="modelSelectDropdownClass"
          @search="onSearch"
          @dropdown-visible-change="onDropdownVisibleChange"
        >
          <template #dropdownRender="{ menuNode }">
            <div v-if="searching" class="search-progress-bar">
              <div class="progress-inner" :style="{ width: progress + '%', transition: progress >= 100 ? 'width 0.15s ease' : 'none' }"></div>
            </div>
            <component :is="menuNode" />
          </template>
          <a-select-option
            v-for="opt in filteredOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
            :title="NO_NATIVE_TITLE"
          >
            <span class="model-option-label">{{ opt.label }}</span>
          </a-select-option>
        </a-select>
      </div>
    </a-tooltip>
    <a-tooltip
      v-if="dropdownOpen"
      :title="dropdownHoverText || undefined"
      :open="dropdownTooltipVisible && !!dropdownHoverText"
      placement="topLeft"
      :overlay-style="{ maxWidth: '420px' }"
      overlay-class-name="model-select-tooltip"
      :mouse-enter-delay="0.35"
    >
      <span class="model-select-dropdown-anchor" :style="dropdownAnchorStyle" />
    </a-tooltip>
    <div class="model-select-suffix-icons">
      <a-tooltip :title="displayValue ? '检查连通性' : '请先选择模型'">
        <span class="suffix-icon-btn" :class="{ disabled: !displayValue }" @click.stop="handleCheck">
          <LoadingOutlined v-if="checking" spin />
          <CheckCircleOutlined v-else-if="checkStatus === 'success'" style="color: #52c41a" />
          <CloseCircleOutlined v-else-if="checkStatus === 'error'" style="color: #ff4d4f" />
          <ApiOutlined v-else />
        </span>
      </a-tooltip>
      <a-tooltip title="刷新缓存">
        <span class="suffix-icon-btn" @click.stop="handleRefresh">
          <LoadingOutlined v-if="refreshing" spin />
          <CheckCircleOutlined v-else-if="refreshStatus === 'success'" style="color: #52c41a" />
          <CloseCircleOutlined v-else-if="refreshStatus === 'error'" style="color: #ff4d4f" />
          <ReloadOutlined v-else />
        </span>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ApiOutlined, ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getProvidersWithModels, checkModelProvider, refreshModelProviderCache } from '../api/modelProvider'
import {
  formatModelSelectValue,
  normalizeModelSelectValue,
  parseModelSelectValue,
} from '../utils/modelSelect'

const props = defineProps({
  /** 复合值 providerId|modelId（兼容历史 `:` 分隔） */
  modelValue: { type: String, default: null },
  /** 可选：与 modelId 双绑定时无需手动拼接复合值 */
  providerId: { type: [String, Number], default: null },
  modelId: { type: String, default: null },
  modelType: { type: String, default: 'llm' },
  placeholder: String,
  disabled: Boolean,
  style: [String, Object],
})

const emit = defineEmits(['update:modelValue', 'update:providerId', 'update:modelId', 'change'])

/** 禁用 Select 原生 title，仅使用 a-tooltip */
const NO_NATIVE_TITLE = ''

const wrapperRef = ref(null)
const modelSelectDropdownClass = `model-select-dropdown-${Math.random().toString(36).slice(2, 10)}`
let selectionTitleObserver = null
let dropdownTitleObserver = null
let dropdownEl = null

const loading = ref(false)
const checking = ref(false)
const refreshing = ref(false)
const checkStatus = ref(null)
const refreshStatus = ref(null)
const options = ref([])
const dropdownOpen = ref(false)
const selectorHovered = ref(false)
const dropdownHoverText = ref('')
const dropdownTooltipVisible = ref(false)
const dropdownAnchorStyle = ref({
  position: 'fixed',
  top: '0px',
  left: '0px',
  width: '1px',
  height: '1px',
  pointerEvents: 'none',
})

function formatOptionTooltip(opt) {
  if (!opt) return ''
  return `${opt.providerName} / ${opt.modelId}`
}

function findOptionByLabel(labelText) {
  if (!labelText) return null
  const keyword = debouncedSearch.value.toLowerCase().trim()
  const list = !keyword
    ? options.value
    : options.value.filter(opt =>
      opt.providerName.toLowerCase().includes(keyword) ||
      opt.modelId.toLowerCase().includes(keyword)
    )
  return list.find(o => o.label === labelText) || null
}

function updateDropdownTooltip(event) {
  const optionEl = event.target.closest?.('.ant-select-item-option')
  if (!optionEl || optionEl.classList.contains('ant-select-item-option-disabled')) {
    dropdownTooltipVisible.value = false
    return
  }
  const labelText = optionEl.querySelector('.ant-select-item-option-content')?.textContent?.trim()
  const opt = findOptionByLabel(labelText)
  if (!opt) {
    dropdownTooltipVisible.value = false
    return
  }
  const rect = optionEl.getBoundingClientRect()
  dropdownAnchorStyle.value = {
    position: 'fixed',
    top: `${rect.top}px`,
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 1)}px`,
    height: `${Math.max(rect.height, 1)}px`,
    pointerEvents: 'none',
  }
  dropdownHoverText.value = formatOptionTooltip(opt)
  dropdownTooltipVisible.value = true
}

function hideDropdownTooltip() {
  dropdownTooltipVisible.value = false
  dropdownHoverText.value = ''
}

function bindDropdownTooltipEvents() {
  nextTick(() => {
    dropdownEl = document.querySelector(`.${modelSelectDropdownClass}`)
    if (!dropdownEl) return
    stripDropdownNativeTitle()
    dropdownEl.addEventListener('mousemove', updateDropdownTooltip)
    dropdownEl.addEventListener('mouseleave', hideDropdownTooltip)
    setupDropdownTitleObserver()
  })
}

function unbindDropdownTooltipEvents() {
  dropdownEl?.removeEventListener('mousemove', updateDropdownTooltip)
  dropdownEl?.removeEventListener('mouseleave', hideDropdownTooltip)
  dropdownTitleObserver?.disconnect()
  dropdownTitleObserver = null
  dropdownEl = null
  hideDropdownTooltip()
}

function setupDropdownTitleObserver() {
  if (!dropdownEl) return
  dropdownTitleObserver?.disconnect()
  dropdownTitleObserver = new MutationObserver(() => {
    stripDropdownNativeTitle()
  })
  dropdownTitleObserver.observe(dropdownEl, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['title'],
  })
}

/** 移除选中项上的原生 title，避免与 a-tooltip 重复 */
function stripSelectionNativeTitle() {
  nextTick(() => {
    const root = wrapperRef.value
    if (!root) return
    root.querySelectorAll('.ant-select-selection-item[title]').forEach(el => {
      el.removeAttribute('title')
    })
  })
}

/** 移除下拉项上的原生 title（下拉层挂载在 body） */
function stripDropdownNativeTitle() {
  nextTick(() => {
    document.querySelectorAll(`.${modelSelectDropdownClass} .ant-select-item-option[title]`).forEach(el => {
      el.removeAttribute('title')
    })
  })
}

function setupSelectionTitleObserver() {
  const selector = wrapperRef.value?.querySelector('.ant-select-selector')
  if (!selector) return
  selectionTitleObserver?.disconnect()
  selectionTitleObserver = new MutationObserver(() => {
    stripSelectionNativeTitle()
  })
  selectionTitleObserver.observe(selector, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['title'],
  })
}

function onDropdownVisibleChange(open) {
  dropdownOpen.value = open
  if (open) {
    bindDropdownTooltipEvents()
  } else {
    unbindDropdownTooltipEvents()
    stripSelectionNativeTitle()
  }
}

const wrapperStyle = computed(() => {
  if (!props.style) return { width: '100%' }
  if (typeof props.style === 'string') return { width: props.style }
  return { width: '100%', ...props.style }
})

/** 下拉展示值：优先 providerId+modelId 双绑，否则归一化 modelValue */
const displayValue = computed(() => {
  if (props.providerId != null && props.providerId !== '' && props.modelId) {
    return formatModelSelectValue(props.providerId, props.modelId)
  }
  return normalizeModelSelectValue(props.modelValue)
})

/** 选中项完整 tooltip（提供商 / 模型） */
const selectedOption = computed(() => {
  if (!displayValue.value) return null
  return options.value.find(o => o.value === displayValue.value) || null
})

const selectedTooltipText = computed(() => {
  if (selectedOption.value) return formatOptionTooltip(selectedOption.value)
  if (!displayValue.value) return ''
  const parsed = parseModelSelectValue(displayValue.value)
  if (parsed.providerId && parsed.modelId) {
    return `${parsed.providerId} / ${parsed.modelId}`
  }
  return parsed.modelId || ''
})

/** 下拉展开时不显示选中区 tooltip，避免遮挡 */
const selectorTooltipOpen = computed(() =>
  selectorHovered.value && !dropdownOpen.value && !!selectedTooltipText.value
)

const searchText = ref('')
const debouncedSearch = ref('')
const searching = ref(false)
const progress = ref(0)
let searchTimer = null
let progressTimer = null

function simulateProgress() {
  clearInterval(progressTimer)
  const startTime = Date.now()
  const duration = 300
  progressTimer = setInterval(() => {
    const elapsed = Date.now() - startTime
    progress.value = Math.min((elapsed / duration) * 100, 100)
    if (progress.value >= 100) clearInterval(progressTimer)
  }, 16)
}

function onSearch(val) {
  searchText.value = val
  clearTimeout(searchTimer)
  clearInterval(progressTimer)
  if (!val) {
    debouncedSearch.value = ''
    searching.value = false
    progress.value = 0
    return
  }
  searching.value = false
  progress.value = 0
  searchTimer = setTimeout(() => {
    debouncedSearch.value = val
    searching.value = true
    progress.value = 0
    simulateProgress()
    setTimeout(() => {
      searching.value = false
      progress.value = 0
    }, 300)
  }, 300)
}

const filteredOptions = computed(() => {
  const keyword = debouncedSearch.value.toLowerCase().trim()
  if (!keyword) return options.value
  return options.value.filter(opt =>
    opt.providerName.toLowerCase().includes(keyword) ||
    opt.modelId.toLowerCase().includes(keyword)
  )
})

async function loadOptions() {
  loading.value = true
  try {
    const res = await getProvidersWithModels(props.modelType)
    const providers = res.data || []
    const opts = []
    for (const p of providers) {
      for (const m of (p.models || [])) {
        opts.push({
          value: formatModelSelectValue(p.id, m.modelId),
          label: `${p.name}:${m.modelId}`,
          providerId: String(p.id),
          modelId: String(m.modelId),
          providerName: p.name,
        })
      }
    }
    options.value = opts
  } catch (e) {
    console.error('[ModelSelect] 加载模型列表失败:', e)
  } finally {
    loading.value = false
  }
}

function emitSelection(parsed) {
  // 先 change 后 v-model，便于父组件在 @change 中读取切换前的 providerId
  emit('change', parsed)
  const normalized = formatModelSelectValue(parsed.providerId, parsed.modelId)
  emit('update:modelValue', normalized)
  emit('update:providerId', parsed.providerId)
  emit('update:modelId', parsed.modelId)
}

function handleUpdate(val) {
  checkStatus.value = null
  if (!val) {
    emitSelection({ providerId: null, modelId: null })
    return
  }
  emitSelection(parseModelSelectValue(val))
}

async function handleCheck() {
  if (!displayValue.value) return
  const { providerId } = parseModelSelectValue(displayValue.value)
  if (!providerId) return
  checking.value = true
  checkStatus.value = null
  try {
    await checkModelProvider(providerId, { silent: true })
    checkStatus.value = 'success'
  } catch {
    checkStatus.value = 'error'
  } finally {
    checking.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  checkStatus.value = null
  refreshStatus.value = null
  try {
    await refreshModelProviderCache({ silent: true })
    refreshStatus.value = 'success'
    await loadOptions()
  } catch {
    refreshStatus.value = 'error'
  } finally {
    refreshing.value = false
    setTimeout(() => { refreshStatus.value = null }, 1000)
  }
}

onMounted(() => {
  loadOptions()
  setupSelectionTitleObserver()
  stripSelectionNativeTitle()
})

onBeforeUnmount(() => {
  selectionTitleObserver?.disconnect()
  unbindDropdownTooltipEvents()
})

watch(() => props.modelType, loadOptions)
watch(displayValue, () => {
  stripSelectionNativeTitle()
})
watch(filteredOptions, () => {
  if (dropdownOpen.value) stripDropdownNativeTitle()
})
</script>

<style scoped>
.model-select-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 100%;
}
.model-select-wrapper :deep(.ant-select) {
  flex: 1;
  min-width: 0;
}
.model-select-inner {
  width: 100%;
  min-width: 0;
}
.model-select-wrapper :deep(.ant-select-selector) {
  padding-right: 52px !important;
}
.model-select-dropdown-anchor {
  display: block;
  z-index: -1;
}
.model-select-suffix-icons {
  position: absolute;
  right: 30px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 2px;
  z-index: 2;
}
.suffix-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
  color: var(--color-mute);
  font-size: 13px;
  border-radius: 3px;
  transition: color 0.15s, background 0.15s;
}
.suffix-icon-btn:hover {
  color: var(--color-ink);
  background: var(--color-canvas-soft-2);
}
.suffix-icon-btn.disabled {
  color: #d4d4d8;
  cursor: not-allowed;
}
.suffix-icon-btn.disabled:hover {
  color: #d4d4d8;
  background: transparent;
}
.model-option-label {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-progress-bar {
  height: 2px;
  position: relative;
  overflow: hidden;
}
.progress-inner {
  height: 100%;
  background: #1890ff;
  border-radius: 1px;
}
</style>

<style>
.model-select-tooltip .ant-tooltip-inner {
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  font-size: 13px;
  line-height: 1.5;
}
</style>
