<template>
  <div class="config-panel">
    <div class="panel-header">
      <div class="node-type-badge">
        <div class="type-icon edge-icon">
          <BranchesOutlined />
        </div>
        <span class="type-name">连线详情</span>
      </div>
      <button class="btn-close" @click="$emit('close')">
        <CloseOutlined />
      </button>
    </div>
    <div class="panel-body">
      <div class="edge-detail-card">
        <div class="edge-detail-row">
          <span class="edge-detail-label">连线 ID</span>
          <span class="edge-detail-value mono">{{ edge.id }}</span>
        </div>
        <div class="edge-connection-flow">
          <div class="edge-node-box source">
            <span class="edge-node-role">源节点</span>
            <span class="edge-node-name">{{ getEdgeSourceLabel(edge) }}</span>
            <span class="edge-node-type">{{ getEdgeSourceType(edge) }}</span>
            <span v-if="edge.sourceHandle" class="edge-handle-tag">出口: {{ getHandleDisplayName(edge.sourceHandle) }}</span>
          </div>
          <div class="edge-arrow"><ArrowRightOutlined /></div>
          <div class="edge-node-box target">
            <span class="edge-node-role">目标节点</span>
            <span class="edge-node-name">{{ getEdgeTargetLabel(edge) }}</span>
            <span class="edge-node-type">{{ getEdgeTargetType(edge) }}</span>
            <span class="edge-handle-tag">入口: {{ getHandleDisplayName(edge.targetHandle || handleIn) }}</span>
          </div>
        </div>
      </div>
      <div class="edge-retarget-form">
        <div class="edge-retarget-title">修改连线（可拖拽连线端点，或在此选择）</div>
        <div class="edge-retarget-row">
          <label>目标节点</label>
          <a-select
            :value="edge.target"
            show-search
            option-filter-prop="label"
            style="width: 100%"
            :disabled="isVersionPreview"
            @change="val => $emit('target-change', val)"
          >
            <a-select-option
              v-for="n in edgeTargetCandidates"
              :key="n.id"
              :value="n.id"
              :label="n.data?.label || getNodeTitle(n.type)"
            >
              {{ n.data?.label || getNodeTitle(n.type) }} ({{ getNodeTitle(n.type) }})
            </a-select-option>
          </a-select>
        </div>
        <div v-if="edgeSourceHandleOptions.length > 1" class="edge-retarget-row">
          <label>源出口</label>
          <a-select
            :value="edge.sourceHandle || handleOut"
            style="width: 100%"
            :disabled="isVersionPreview"
            @change="val => $emit('source-handle-change', val)"
          >
            <a-select-option v-for="h in edgeSourceHandleOptions" :key="h.id" :value="h.id">
              {{ h.label }}
            </a-select-option>
          </a-select>
        </div>
        <p class="edge-retarget-hint">从节点右侧「出」拖到下游节点左侧「入」；禁止接入开始节点或从结束节点连出。</p>
      </div>
      <div class="panel-footer edge-delete-footer">
        <a-button type="primary" danger block @click="$emit('delete')">
          <DeleteOutlined /> 删除连线
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { BranchesOutlined, ArrowRightOutlined, CloseOutlined, DeleteOutlined } from '@ant-design/icons-vue'

defineProps({
  edge: { type: Object, required: true },
  isVersionPreview: Boolean,
  edgeTargetCandidates: { type: Array, default: () => [] },
  edgeSourceHandleOptions: { type: Array, default: () => [] },
  handleIn: { type: String, required: true },
  handleOut: { type: String, required: true },
  getEdgeSourceLabel: { type: Function, required: true },
  getEdgeSourceType: { type: Function, required: true },
  getEdgeTargetLabel: { type: Function, required: true },
  getEdgeTargetType: { type: Function, required: true },
  getHandleDisplayName: { type: Function, required: true },
  getNodeTitle: { type: Function, required: true },
})

defineEmits(['close', 'target-change', 'source-handle-change', 'delete'])
</script>

<style scoped>
.config-panel {
  width: 480px;
  min-width: 480px;
  max-width: 42vw;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  gap: 12px;
}
.node-type-badge { display: flex; align-items: center; gap: 8px; }
.type-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.type-name { font-weight: 600; color: #1f2937; }
.edge-icon { background: #f3e8ff !important; color: #7c3aed !important; }
.btn-close {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}
.panel-body {
  position: relative;
  flex: 1;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
}
.edge-detail-card {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
}
.edge-detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}
.edge-detail-label { font-size: 12px; color: #6b7280; }
.edge-detail-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
.edge-connection-flow {
  display: flex;
  align-items: stretch;
  gap: 10px;
}
.edge-node-box {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.edge-node-box.source { border-color: #c4b5fd; }
.edge-node-box.target { border-color: #86efac; }
.edge-node-role {
  font-size: 11px;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}
.edge-node-name { font-size: 14px; font-weight: 600; color: #1f2937; }
.edge-node-type { font-size: 12px; color: #6b7280; }
.edge-handle-tag {
  font-size: 11px;
  color: #7c3aed;
  background: #f5f3ff;
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
}
.edge-arrow {
  display: flex;
  align-items: center;
  color: #9ca3af;
  font-size: 16px;
  flex-shrink: 0;
}
.edge-retarget-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e5e7eb;
}
.edge-retarget-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}
.edge-retarget-row { margin-bottom: 12px; }
.edge-retarget-row label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.edge-retarget-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}
.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
}
.edge-delete-footer {
  margin-top: 16px;
  border-top: none;
  padding: 0;
}
</style>
