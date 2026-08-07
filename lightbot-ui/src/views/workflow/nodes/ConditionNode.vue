<template>
  <div ref="rootRef" class="workflow-node condition-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <div class="node-header">
      <div class="node-icon">
        <ForkOutlined />
      </div>
      <div class="node-title">{{ data.label || '条件判断' }}</div>
    </div>
    <div class="node-body">
      <div
        v-for="(group, idx) in ruleGroups"
        :key="group.id"
        class="branch-row"
        :ref="el => setBranchRowRef(el, idx)"
      >
        <span class="branch-label">{{ group.label || `条件 ${idx + 1}` }}</span>
        <WorkflowHandle
          type="source"
          position="right"
          :id="groupHandleId(group.id)"
          :style="handleStyleAt(idx)"
        />
      </div>
      <div
        class="branch-row default-row"
        :ref="el => setBranchRowRef(el, ruleGroups.length)"
      >
        <span class="branch-label">都未命中</span>
        <WorkflowHandle
          type="source"
          position="right"
          :id="defaultHandleId"
          :style="handleStyleAt(ruleGroups.length)"
        />
      </div>
      <div v-if="!hasDefaultEdge" class="node-warning">⚠ 未连接「都未命中」出口</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onBeforeUpdate, onMounted, ref, watch } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import { ForkOutlined } from '@ant-design/icons-vue'
import { useGroupDragMask } from '../useGroupDragMask'
import { useHandleConnections } from '@vue-flow/core'
import { conditionDefaultHandleId, conditionGroupHandleId } from '../conditionUtils'

const props = defineProps({
  id: String,
  data: Object,
  selected: Boolean,
  parentNode: String,
})

defineEmits(['edit'])

const { isGroupChildDragMasked } = useGroupDragMask(props)

const rootRef = ref(null)
const branchRowEls = ref([])
const handleTops = ref([])
let resizeObserver = null

/** 仅展示带规则的匹配组；默认口单独渲染，不调用 ensure 以免 computed 内生成新 id */
const ruleGroups = computed(() =>
  (props.data?.conditionGroups || []).filter(g => g?.id && Array.isArray(g.rules) && g.rules.length > 0)
)

const defaultHandleId = computed(() => conditionDefaultHandleId(props.id))

function groupHandleId(groupId) {
  return conditionGroupHandleId(props.id, groupId)
}

const defaultHandleConnections = useHandleConnections({
  id: defaultHandleId,
  type: 'source',
  nodeId: props.id,
})
const hasDefaultEdge = computed(() => defaultHandleConnections.value.length > 0)

const nodeClass = computed(() => ({
  selected: props.selected,
  'wf-group-child-mask': isGroupChildDragMasked.value,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus,
}))

function setBranchRowRef(el, idx) {
  if (el) branchRowEls.value[idx] = el
}

function handleStyleAt(idx) {
  const top = handleTops.value[idx]
  if (top == null) return undefined
  return { top: `${top}%` }
}

function measureHandleTops() {
  const root = rootRef.value
  if (!root) return
  const rootRect = root.getBoundingClientRect()
  const h = rootRect.height || 1
  const count = ruleGroups.value.length + 1
  const tops = []
  for (let i = 0; i < count; i++) {
    const row = branchRowEls.value[i]
    if (!row) {
      tops.push(((i + 1) / (count + 1)) * 100)
      continue
    }
    const rowRect = row.getBoundingClientRect()
    const midY = rowRect.top + rowRect.height / 2 - rootRect.top
    tops.push(Math.min(95, Math.max(5, (midY / h) * 100)))
  }
  const prev = handleTops.value
  if (prev.length === tops.length && prev.every((v, i) => Math.abs(v - tops[i]) < 0.05)) return
  handleTops.value = tops
}

onBeforeUpdate(() => {
  branchRowEls.value = []
})

onMounted(() => {
  nextTick(measureHandleTops)
  if (typeof ResizeObserver !== 'undefined' && rootRef.value) {
    resizeObserver = new ResizeObserver(() => measureHandleTops())
    resizeObserver.observe(rootRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(ruleGroups, () => nextTick(measureHandleTops), { deep: true })
</script>

<style scoped>
.condition-node {
  background: var(--color-canvas);
  border: 2px solid #d97706;
  border-radius: 12px;
  min-width: 200px;
  position: relative;
  transition: all 0.2s ease;
}
.condition-node:hover {
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.15);
}
.condition-node.selected {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.2);
}
.condition-node.debug-executing { animation: wf-exec 1.2s linear infinite; border-color: var(--color-link); }
.condition-node.debug-success { border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25); }
.condition-node.debug-fail { border-color: #ef4444; box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.25); }
@keyframes wf-exec {
  0% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.35); }
  50% { box-shadow: 0 0 0 8px rgba(99, 102, 241, 0.12); }
  100% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.35); }
}
.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--color-warn-bg-deep);
  border-bottom: 1px solid var(--color-hairline);
  border-radius: 10px 10px 0 0;
}
.node-icon { color: #d97706; font-size: 16px; }
.node-title { font-size: 14px; font-weight: 600; color: var(--color-ink); }
.branch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 6px 14px;
  min-height: 36px;
}
.branch-row + .branch-row { border-top: 1px dashed var(--color-hairline); }
.branch-label {
  font-size: 12px;
  color: var(--color-text-dark);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.default-row .branch-label { color: var(--color-mute); }
.node-warning {
  font-size: 11px;
  color: #d97706;
  padding: 4px 14px 8px;
}
</style>
