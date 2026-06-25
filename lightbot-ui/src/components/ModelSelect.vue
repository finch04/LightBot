<template>
  <div class="model-select-wrapper" :style="wrapperStyle">
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
      @search="onSearch"
    >
      <template #dropdownRender="{ menuNode }">
        <div v-if="searching" class="search-progress-bar">
          <div class="progress-inner" :style="{ width: progress + '%', transition: progress >= 100 ? 'width 0.15s ease' : 'none' }"></div>
        </div>
        <component :is="menuNode" />
      </template>
      <a-select-option v-for="opt in filteredOptions" :key="opt.value" :value="opt.value">
        <a-tooltip :title="opt.modelId" placement="topLeft" :overlayStyle="{ maxWidth: '360px' }">
          <span class="model-option-label">{{ opt.label }}</span>
        </a-tooltip>
      </a-select-option>
    </a-select>
    <div class="model-select-suffix-icons">
      <a-tooltip :title="modelValue ? '检查连通性' : '请先选择模型'">
        <span class="suffix-icon-btn" :class="{ disabled: !modelValue }" @click.stop="handleCheck">
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
import { ref, computed, onMounted, watch } from 'vue'
import { ApiOutlined, ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getProvidersWithModels, checkModelProvider, refreshModelProviderCache } from '../api/modelProvider'

const props = defineProps({
  modelValue: String,
  modelType: { type: String, default: 'llm' },
  placeholder: String,
  disabled: Boolean,
  style: [String, Object],
})

const emit = defineEmits(['update:modelValue', 'change'])

const loading = ref(false)
const checking = ref(false)
const refreshing = ref(false)
const checkStatus = ref(null)    // null | 'success' | 'error'
const refreshStatus = ref(null)  // null | 'success' | 'error'
const options = ref([])

/** 外层容器样式：将用户传入的 style 应用到 wrapper，select 自动撑满 */
const wrapperStyle = computed(() => {
  if (!props.style) return { width: '100%' }
  if (typeof props.style === 'string') return { width: props.style }
  return { width: '100%', ...props.style }
})

const displayValue = computed(() => {
  if (!props.modelValue) return undefined
  // 确保 value 中的 providerId 和 modelId 都是字符串，与 options 格式一致
  const parts = props.modelValue.split(':')
  if (parts.length === 2) {
    return `${String(parts[0])}:${String(parts[1])}`
  }
  return props.modelValue
})

// 防抖搜索：支持模型提供商名称、模型名称
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
  // 防抖期间不显示进度条
  searching.value = false
  progress.value = 0
  searchTimer = setTimeout(() => {
    debouncedSearch.value = val
    // 防抖结束后开始进度条动画
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
          value: `${String(p.id)}:${String(m.modelId)}`,
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

function handleUpdate(val) {
  checkStatus.value = null
  emit('update:modelValue', val)
  if (val) {
    const [providerId, modelId] = val.split(':')
    emit('change', { providerId, modelId })
  } else {
    emit('change', { providerId: null, modelId: null })
  }
}

async function handleCheck() {
  if (!props.modelValue) return
  const [providerId] = props.modelValue.split(':')
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

onMounted(loadOptions)
watch(() => props.modelType, loadOptions)
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
.model-select-wrapper :deep(.ant-select-selector) {
  padding-right: 52px !important;
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
