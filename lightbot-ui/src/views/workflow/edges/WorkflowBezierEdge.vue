<template>
  <BaseEdge
    :id="id"
    :path="path"
    :style="edgeStyle"
    :marker-end="markerEnd"
    :interaction-width="20"
  />
</template>

<script setup>
import { computed } from 'vue'
import { BaseEdge } from '@vue-flow/core'

const props = defineProps({
  id: { type: String, required: true },
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, default: 'right' },
  targetPosition: { type: String, default: 'left' },
  style: { type: Object, default: () => ({}) },
  markerEnd: { type: String, default: undefined },
  selected: { type: Boolean, default: false },
})

/** 水平贝塞尔（与 admin FlowBaseEdge 一致） */
function buildHorizontalBezierPath(sourceX, sourceY, targetX, targetY) {
  const c1x = sourceX + (targetX - sourceX) * 0.5
  const c1y = sourceY
  const c2x = sourceX + (targetX - sourceX) * 0.5
  const c2y = targetY
  return `M ${sourceX},${sourceY} C ${c1x} ${c1y} ${c2x} ${c2y} ${targetX},${targetY}`
}

const path = computed(() =>
  buildHorizontalBezierPath(props.sourceX, props.sourceY, props.targetX, props.targetY),
)

const edgeStyle = computed(() => ({
  strokeWidth: 2,
  stroke: props.selected ? '#6366f1' : '#94a3b8',
  ...(props.style || {}),
}))
</script>
