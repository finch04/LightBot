<template>
  <div class="model-select-wrapper" :style="wrapperStyle">
    <a-select
      :value="displayValue"
      @update:value="handleUpdate"
      :placeholder="placeholder || '选择模型'"
      :disabled="disabled"
      style="width: 100%"
      show-search
      :filter-option="filterOption"
      :loading="loading"
      allow-clear
      :dropdownMatchSelectWidth="false"
    >
      <a-select-option v-for="opt in options" :key="opt.value" :value="opt.value">
        <a-tooltip :title="opt.modelId" placement="topLeft" :overlayStyle="{ maxWidth: '360px' }">
          <span class="model-option-label">{{ opt.label }}</span>
        </a-tooltip>
      </a-select-option>
    </a-select>
    <div class="model-select-suffix-icons">
      <template v-if="modelValue">
        <a-tooltip title="检查连通性">
          <span class="suffix-icon-btn" @click.stop="handleCheck">
            <LoadingOutlined v-if="checking" spin />
            <CheckCircleOutlined v-else-if="checkStatus === 'success'" style="color: #52c41a" />
            <CloseCircleOutlined v-else-if="checkStatus === 'error'" style="color: #ff4d4f" />
            <ApiOutlined v-else />
          </span>
        </a-tooltip>
      </template>
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

function filterOption(input, option) {
  const label = option.children?.[0]?.children?.[0]?.children || ''
  return label.toLowerCase().includes(input.toLowerCase())
}

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
    emit('change', providerId, modelId)
  } else {
    emit('change', null, null)
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
  color: #a1a1aa;
  font-size: 13px;
  border-radius: 3px;
  transition: color 0.15s, background 0.15s;
}
.suffix-icon-btn:hover {
  color: #171717;
  background: #f4f4f5;
}
.model-option-label {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
