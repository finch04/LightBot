<template>
  <div class="trace-steps">
    <div v-if="!nodeSteps.length" class="trace-empty">暂无节点轨迹</div>
    <template v-for="(step, i) in nodeSteps" :key="i">
      <div
        class="trace-step"
        :class="{ 'trace-active': step.nodeId === activeNodeId }"
        @click="emitSelect(step)"
      >
        <div class="trace-step-head" @click.stop="toggleStep(i)">
          <span class="trace-step-icon">
            <span v-if="step.status === 'done'" class="trace-icon-done">✓</span>
            <span v-else-if="step.status === 'failed'" class="trace-icon-fail">✗</span>
            <span v-else-if="step.status === 'suspended'" class="trace-icon-wait">⏸</span>
            <span v-else class="trace-icon-run">▶</span>
          </span>
          <span class="trace-step-label">
            <strong>{{ step.nodeLabel || step.nodeId }}</strong>
            <span class="trace-step-type">{{ getNodeTypeName(step.nodeType) }}</span>
            <span v-if="step.isContainer && step.children?.length" class="trace-child-count">{{ step.children.length }} 个子节点</span>
          </span>
          <span v-if="step.durationMs != null" class="trace-step-duration">{{ step.durationMs }}ms</span>
          <span v-else-if="step.status === 'running'" class="trace-step-duration trace-running">执行中</span>
          <span v-else-if="step.status === 'suspended'" class="trace-step-duration trace-suspended">等待确认</span>
          <span class="trace-toggle" :class="{ expanded: expandedSteps.has(i) }">›</span>
        </div>
        <div v-show="expandedSteps.has(i)" class="trace-step-body">
          <div v-if="step.status === 'failed'" class="trace-msg trace-fail">{{ step.message || '执行失败' }}</div>
          <div v-else-if="step.status === 'suspended'" class="trace-msg trace-suspended-msg">{{ step.message || '等待人工确认' }}</div>
          <div v-else-if="step.status === 'done' && step.message" class="trace-msg">{{ step.message }}</div>
          <div v-if="step.detail" class="trace-detail">
            <pre>{{ step.detail }}</pre>
          </div>
          <div v-if="hasKvData(step.input)" class="trace-kv">
            <div class="trace-kv-title">入参</div>
            <pre>{{ formatKv(step.input) }}</pre>
          </div>
          <div v-if="hasKvData(step.outputs)" class="trace-kv">
            <div class="trace-kv-title">出参</div>
            <pre>{{ formatKv(step.outputs) }}</pre>
          </div>
          <div v-if="step.nextNodeId" class="trace-meta">下一节点: {{ step.nextNodeId }}</div>
        </div>
        <div v-if="step.isContainer && step.children?.length && expandedSteps.has(i)" class="trace-container-children">
          <div
            v-for="(child, ci) in step.children"
            :key="ci"
            class="trace-step trace-child-step"
            :class="{ 'trace-active': child.nodeId === activeNodeId }"
            @click.stop="emitSelect(child)"
          >
            <div class="trace-step-head" @click.stop="toggleStep(`child_${i}_${ci}`)">
              <span class="trace-step-icon">
                <span v-if="child.status === 'done'" class="trace-icon-done">✓</span>
                <span v-else-if="child.status === 'failed'" class="trace-icon-fail">✗</span>
                <span v-else class="trace-icon-run">▶</span>
              </span>
              <span class="trace-step-label">
                <strong>{{ child.nodeLabel || child.nodeId }}</strong>
                <span class="trace-step-type">{{ getNodeTypeName(child.nodeType) }}</span>
                <span v-if="child.iterationIndex != null" class="trace-iteration-tag">#{{ child.iterationIndex + 1 }}</span>
              </span>
              <span v-if="child.durationMs != null" class="trace-step-duration">{{ child.durationMs }}ms</span>
              <span class="trace-toggle" :class="{ expanded: expandedSteps.has(`child_${i}_${ci}`) }">›</span>
            </div>
            <div v-show="expandedSteps.has(`child_${i}_${ci}`)" class="trace-step-body">
              <div v-if="child.status === 'failed'" class="trace-msg trace-fail">{{ child.message || '执行失败' }}</div>
              <div v-else-if="child.status === 'done' && child.message" class="trace-msg">{{ child.message }}</div>
              <div v-if="hasKvData(child.outputs)" class="trace-kv">
                <div class="trace-kv-title">出参</div>
                <pre>{{ formatKv(child.outputs) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useWorkflowNodeSteps, getNodeTypeName } from '../composables/useWorkflowNodeSteps.js'

const props = defineProps({
  nodeEvents: { type: Array, default: () => [] },
  activeNodeId: { type: [String, Number], default: null },
  /** 完成后默认展开全部步骤 */
  autoExpand: { type: Boolean, default: true },
})

const emit = defineEmits(['select-node'])

const expandedSteps = ref(new Set())
const { nodeSteps } = useWorkflowNodeSteps(() => props.nodeEvents)

watch(() => props.nodeEvents, (val) => {
  if (props.autoExpand && val?.length) {
    expandedSteps.value = new Set(Array.from({ length: nodeSteps.value.length }, (_, i) => i))
  }
}, { immediate: true })

function toggleStep(index) {
  const next = new Set(expandedSteps.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedSteps.value = next
}

function emitSelect(step) {
  if (step?.nodeId) emit('select-node', step.nodeId)
}

function hasKvData(value) {
  return value && typeof value === 'object' && Object.keys(value).length > 0
}

function formatKv(value) {
  if (!value) return ''
  try { return JSON.stringify(value, null, 2) } catch { return String(value) }
}
</script>

<style scoped>
.trace-steps { display: flex; flex-direction: column; gap: 6px; }
.trace-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 20px 0; }
.trace-step { border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); cursor: pointer; }
.trace-step.trace-active { border-color: #818cf8; background: var(--color-info-bg); }
.trace-step-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer; user-select: none;
}
.trace-step-head:hover { background: var(--color-canvas-soft); }
.trace-step-icon { flex-shrink: 0; font-size: 13px; }
.trace-icon-done { color: #22c55e; }
.trace-icon-fail { color: #dc2626; }
.trace-icon-wait { color: #f97316; }
.trace-icon-run { color: var(--color-link); }
.trace-step-label { flex: 1; min-width: 0; font-size: 13px; color: var(--color-text-dark); }
.trace-step-label strong { font-weight: 600; }
.trace-step-type { margin-left: 6px; font-size: 11px; font-weight: normal; color: var(--color-mute); }
.trace-step-duration { flex-shrink: 0; font-size: 11px; color: var(--color-mute); font-variant-numeric: tabular-nums; }
.trace-running { color: var(--color-link); }
.trace-suspended { color: #f97316; }
.trace-suspended-msg { color: #f97316; }
.trace-toggle {
  flex-shrink: 0; font-size: 12px; color: var(--color-mute);
  transition: transform 0.2s; display: inline-block;
}
.trace-toggle.expanded { transform: rotate(90deg); }
.trace-step-body { padding: 0 10px 10px; }
.trace-msg { font-size: 12px; color: var(--color-mute); margin-top: 4px; }
.trace-msg.trace-fail { color: #dc2626; }
.trace-detail {
  margin-top: 6px; padding: 8px; background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate); border-radius: 6px; max-height: 160px; overflow: auto;
}
.trace-detail pre { margin: 0; font-size: 12px; line-height: 1.45; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, monospace; }
.trace-kv {
  margin-top: 6px; padding: 8px; border-radius: 6px;
  border: 1px solid #ede9fe; background: #faf5ff;
}
.trace-kv-title { margin-bottom: 4px; font-size: 12px; font-weight: 600; color: #6d28d9; }
.trace-kv pre { margin: 0; font-size: 12px; line-height: 1.45; color: #4c1d95; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, monospace; }
.trace-meta { margin-top: 4px; font-size: 11px; color: var(--color-mute); font-family: ui-monospace, monospace; }
.trace-container-children {
  margin-left: 18px; padding-left: 10px; border-left: 2px solid #e9d5ff;
  margin-top: 4px; display: flex; flex-direction: column; gap: 4px; padding-bottom: 6px;
}
.trace-child-step { background: #faf5ff; border-color: #f3e8ff; cursor: pointer; }
.trace-child-step .trace-step-head { padding: 5px 8px; }
.trace-child-step .trace-step-body { padding: 0 8px 8px; }
.trace-child-count {
  margin-left: 6px; font-size: 11px; font-weight: normal; color: #7c3aed;
  background: var(--color-purple-bg); padding: 1px 6px; border-radius: 8px;
}
.trace-iteration-tag {
  margin-left: 4px; font-size: 10px; font-weight: normal; color: var(--color-link);
  background: var(--color-info-bg); padding: 1px 5px; border-radius: 6px;
}
</style>
