<template>
  <g>
    <path
      :d="pathD"
      fill="none"
      class="workflow-connection-line-path"
      :class="{ snapped: pathSnapped }"
    />
    <circle
      :cx="endX"
      :cy="endY"
      r="4"
      class="workflow-connection-line-dot"
    />
  </g>
</template>

<script setup>
import { computed } from 'vue'
import { getSnappedConnectionPath } from '../workflowSnap'

const props = defineProps({
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
})

const path = computed(() =>
  getSnappedConnectionPath(props.sourceX, props.sourceY, props.targetX, props.targetY),
)

const pathD = computed(() => path.value.d)
const endX = computed(() => path.value.endX)
const endY = computed(() => path.value.endY)
const pathSnapped = computed(() => path.value.snapped)
</script>

<style scoped>
.workflow-connection-line-path {
  stroke: #7c3aed;
  stroke-width: 2;
  stroke-dasharray: 6 4;
  pointer-events: none;
}
.workflow-connection-line-path.snapped {
  stroke: #5b21b6;
  stroke-width: 2.5;
  stroke-dasharray: none;
}
.workflow-connection-line-dot {
  fill: #fff;
  stroke: #7c3aed;
  stroke-width: 2;
}
</style>
