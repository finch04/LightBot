<template>
  <div class="short-memory-form">
    <div class="sm-header">
      <span class="sm-title">记忆</span>
      <a-switch v-model:checked="local.enabled" :disabled="disabled" @change="emitChange" />
    </div>
    <template v-if="local.enabled">
      <a-form-item label="记忆类型">
        <a-select v-model:value="local.type" :disabled="disabled" @change="emitChange">
          <a-select-option value="self">本节点缓存 — 仅记住本节点内上下文</a-select-option>
          <a-select-option value="custom">自定义缓存 — 使用全局上下文变量</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="local.type === 'self'" label="记忆轮次">
        <a-slider v-model:value="local.round" :min="1" :max="50" :step="1" :disabled="disabled" @change="emitChange" />
        <span class="param-value">{{ local.round }}</span>
      </a-form-item>
      <a-form-item v-else label="上下文变量">
        <a-input v-model:value="local.paramKey" :disabled="disabled" placeholder="变量名，如 history" @change="emitChange" />
      </a-form-item>
    </template>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { SHORT_MEMORY_DEFAULT } from '../nodeMeta'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ ...SHORT_MEMORY_DEFAULT }) },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const local = reactive({ ...SHORT_MEMORY_DEFAULT, ...props.modelValue })

watch(() => props.modelValue, (val) => {
  Object.assign(local, SHORT_MEMORY_DEFAULT, val || {})
}, { deep: true })

function emitChange() {
  if (props.disabled) return
  emit('update:modelValue', { ...local })
}
</script>

<style scoped>
.short-memory-form {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: #fafafa;
}
.sm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sm-title {
  font-weight: 600;
  font-size: 13px;
}
.param-value {
  margin-left: 8px;
  font-size: 12px;
  color: #6b7280;
}
</style>
