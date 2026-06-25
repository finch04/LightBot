<template>
  <div class="entity-select-option">
    <span class="entity-select-avatar" :style="{ background: gradient }">
      {{ letter }}
    </span>
    <a-tooltip v-if="name" :title="name" placement="topLeft">
      <span class="entity-select-name">{{ name }}</span>
    </a-tooltip>
    <span v-if="tag" class="entity-select-tag">{{ tag }}</span>
    <span v-if="tagMuted" class="entity-select-tag entity-select-tag--muted">{{ tagMuted }}</span>
    <a-tooltip v-if="desc" :title="desc" placement="topLeft" :overlay-style="{ maxWidth: '400px' }">
      <span class="entity-select-desc">{{ desc }}</span>
    </a-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BINDING_GRADIENTS } from '../utils/bindingTheme'

const props = defineProps({
  type: { type: String, required: true },
  name: { type: String, default: '' },
  tag: { type: String, default: '' },
  tagMuted: { type: String, default: '' },
  desc: { type: String, default: '' },
})

const gradient = computed(() => BINDING_GRADIENTS[props.type] || BINDING_GRADIENTS.tool)
const letter = computed(() => (props.name || '?')[0].toUpperCase())
</script>

<style scoped>
.entity-select-option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.entity-select-avatar {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.entity-select-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.entity-select-tag {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 4px;
  background: #f4f4f5;
  color: var(--color-mute);
  white-space: nowrap;
  flex-shrink: 0;
}
.entity-select-tag--muted {
  background: #fef2f2;
  color: #dc2626;
}
.entity-select-desc {
  font-size: 12px;
  color: #a1a1aa;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  text-align: right;
}
</style>
