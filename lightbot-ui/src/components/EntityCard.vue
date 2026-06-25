<template>
  <div
    class="entity-card"
    :class="{ 'entity-card--clickable': clickable }"
    @click="$emit('click', $event)"
  >
    <div class="card-top">
      <div class="card-icon" :style="iconStyle">
        <slot name="icon">{{ (name || '?')[0].toUpperCase() }}</slot>
      </div>
      <div class="card-info">
        <slot name="info">
          <a-tooltip :title="name"><h3>{{ name }}</h3></a-tooltip>
        </slot>
      </div>
      <div v-if="$slots.actions" class="card-actions" @click.stop>
        <slot name="actions" />
      </div>
    </div>
    <slot />
    <div v-if="$slots.meta" class="card-meta">
      <slot name="meta" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BINDING_GRADIENTS } from '../utils/bindingTheme'

const props = defineProps({
  type: { type: String, default: '' },
  gradient: { type: String, default: '' },
  name: { type: String, default: '' },
  clickable: { type: Boolean, default: true },
})

defineEmits(['click'])

const iconStyle = computed(() => {
  if (props.type && BINDING_GRADIENTS[props.type]) {
    return { background: BINDING_GRADIENTS[props.type] }
  }
  if (props.gradient) {
    return { background: props.gradient }
  }
  return { background: 'linear-gradient(135deg, #71717a, #52525b)' }
})
</script>

<style scoped>
.entity-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.15s, box-shadow 0.15s;
  display: flex;
  flex-direction: column;
}
.entity-card--clickable {
  cursor: pointer;
}
.entity-card--clickable:hover {
  border-color: var(--color-link);
  box-shadow: var(--shadow-3);
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
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
  position: relative;
}

.card-info {
  flex: 1;
  min-width: 0;
}
.card-info :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: fit-content;
  max-width: 100%;
}

.card-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
