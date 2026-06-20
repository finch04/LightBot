<template>
  <div class="entity-avatar" :class="[`entity-avatar--${size}`, badge && `entity-avatar--has-badge`]" :style="{ background: gradient }">
    <span v-if="badge === 'builtin'" class="entity-badge entity-badge--builtin">内置</span>
    <span v-else-if="badge === 'knowledge'" class="entity-badge entity-badge--knowledge">知识库</span>
    <span v-else-if="badge === 'status-active'" class="entity-status-dot entity-status-active"></span>
    <span v-else-if="badge === 'status-disabled'" class="entity-status-dot entity-status-disabled"></span>
    {{ letter }}
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BINDING_GRADIENTS } from '../utils/bindingTheme'

const props = defineProps({
  type: { type: String, required: true },
  name: { type: String, default: '' },
  size: { type: String, default: 'md' },
  badge: { type: String, default: '' },
})

const gradient = computed(() => BINDING_GRADIENTS[props.type] || BINDING_GRADIENTS.tool)
const letter = computed(() => {
  const n = props.name || '?'
  return n[0].toUpperCase()
})
</script>

<style scoped>
.entity-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.entity-avatar--md {
  width: 40px;
  height: 40px;
  font-size: 16px;
}

.entity-avatar--sm {
  width: 24px;
  height: 24px;
  font-size: 12px;
  border-radius: 5px;
}

.entity-badge {
  position: absolute;
  top: -1px;
  right: -1px;
  font-size: 9px;
  font-weight: 600;
  padding: 0 4px;
  border-radius: 0 6px 0 4px;
  line-height: 16px;
  z-index: 1;
}

.entity-badge--builtin {
  background: #3b82f6;
  color: #fff;
}

.entity-badge--knowledge {
  background: #7c3aed;
  color: #fff;
}

.entity-status-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid #fff;
}

.entity-status-active {
  background: #22c55e;
}

.entity-status-disabled {
  background: #d4d4d8;
}
</style>
