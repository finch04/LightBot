<template>
  <div class="workflow-node classifier-node" :class="nodeClass" @dblclick="$emit('edit')">
    <WorkflowHandle type="target" position="left" />
    <div class="node-header">
      <div class="node-icon">
        <NodeTypeIcon type="classifier" />
      </div>
      <div class="node-title">{{ data.label || '意图分类' }}</div>
    </div>
    <div class="node-body">
      <div v-for="item in intentConditions" :key="item.id" class="branch-row">
        <span class="branch-label">{{ item.subject || '暂未配置意图' }}</span>
        <WorkflowHandle type="source" position="right" :id="`${id}_${item.id}`" />
      </div>
      <div class="branch-row default-row">
        <span class="branch-label">其他意图</span>
        <WorkflowHandle type="source" position="right" :id="`${id}_default`" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
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

const intentConditions = computed(() => {
  const list = props.data?.conditions || []
  return list.filter(c => c.id !== 'default')
})

const nodeClass = computed(() => ({
  selected: props.selected,
  'wf-group-child-mask': isGroupChildDragMasked.value,
  [`debug-${props.data?.debugStatus}`]: !!props.data?.debugStatus
}))
</script>

<style scoped>
.classifier-node {
  background: var(--color-canvas);
  border: 2px solid #f59e0b;
  border-radius: 12px;
  min-width: 200px;
}
.classifier-node.selected { box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.25); }
.classifier-node.debug-executing { animation: wf-executing 1.2s linear infinite; border-color: var(--color-link); }
.classifier-node.debug-success { border-color: #22c55e; box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25); }
.classifier-node.debug-fail { border-color: #ef4444; box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.25); }
.node-header {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  background: var(--color-warn-bg); border-bottom: 1px solid #fde68a; border-radius: 10px 10px 0 0;
}
.node-icon { color: #f59e0b; font-size: 16px; }
.node-title { font-size: 14px; font-weight: 600; }
.branch-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px 6px 14px; min-height: 36px;
}
.branch-row + .branch-row { border-top: 1px dashed #f3f4f6; }
.branch-label { font-size: 12px; color: var(--color-text-dark); max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.default-row .branch-label { color: var(--color-mute); }
@keyframes wf-executing {
  0% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(99, 102, 241, 0.15); }
  100% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.4); }
}
</style>
