<template>
  <div ref="rootRef" class="workflow-node classifier-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <div class="node-header">
      <div class="node-icon">
        <NodeTypeIcon type="classifier" />
      </div>
      <div class="node-title">{{ data.label || '意图分类' }}</div>
    </div>
    <div class="node-body">
      <div
        v-for="(item, idx) in intentConditions"
        :key="item.id"
        class="branch-row"
        :ref="el => setBranchRowRef(el, idx)"
      >
        <span class="branch-label">{{ item.subject || '暂未配置意图' }}</span>
        <WorkflowHandle
          type="source"
          position="right"
          :id="`${id}_${item.id}`"
          :style="handleStyleAt(idx)"
        />
      </div>
      <div
        class="branch-row default-row"
        :ref="el => setBranchRowRef(el, intentConditions.length)"
      >
        <span class="branch-label">其他意图</span>
        <WorkflowHandle
          type="source"
          position="right"
          :id="`${id}_default`"
          :style="handleStyleAt(intentConditions.length)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onBeforeUpdate, onMounted, ref, watch } from 'vue'
import WorkflowHandle from '../components/WorkflowHandle.vue'
import NodeTypeIcon from '../components/NodeTypeIcon.vue'
import { useGroupDragMask } from '../useGroupDragMask'

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
/** 各分支出口相对节点高度的 top 百分比，避免多个右侧 Handle 叠在中线 */
const handleTops = ref([])
let resizeObserver = null

const intentConditions = computed(() => {
  const list = props.data?.conditions || []
  return list.filter(c => c.id !== 'default')
})

const nodeClass = computed(() => ({
  selected: props.selected,
  'wf-group-child-mask': isGroupChildDragMasked.value,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus
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
  const count = intentConditions.value.length + 1
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
  // 值未变则不写回，避免 onUpdated/响应式形成无限重渲染把页面卡在加载遮罩
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

watch(intentConditions, () => nextTick(measureHandleTops), { deep: true })
</script>

<style scoped>
.classifier-node {
  background: var(--color-canvas);
  border: 2px solid #f59e0b;
  border-radius: 12px;
  min-width: 200px;
  position: relative;
}
.classifier-node.selected { box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.25); }
.classifier-node.debug-executing { animation: wf-executing 1.2s linear infinite; border-color: var(--color-link); }
.classifier-node.debug-success { border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25); }
.classifier-node.debug-fail { border-color: #ef4444; box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.25); }
.node-header {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  background: var(--color-warn-bg); border-bottom: 1px solid var(--color-hairline); border-radius: 10px 10px 0 0;
}
.node-icon { color: #f59e0b; font-size: 16px; }
.node-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 600; color: var(--color-ink); }
.branch-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px 6px 14px; min-height: 36px;
}
.branch-row + .branch-row { border-top: 1px dashed var(--color-hairline); }
.branch-label { font-size: 12px; color: var(--color-text-dark); max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.default-row .branch-label { color: var(--color-mute); }
@keyframes wf-executing {
  0% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(99, 102, 241, 0.15); }
  100% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.4); }
}
</style>
