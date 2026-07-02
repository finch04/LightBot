<template>
  <div class="canvas-area" ref="canvasAreaRef" @dragover.prevent>
    <div v-if="isVersionPreview" class="version-preview-banner">
      正在预览历史版本 v{{ selectedVersion }}（只读），点击右上角「回到当前版本」返回草稿
    </div>

    <div
      v-show="isNodeDragging"
      ref="trashRef"
      class="workflow-trash"
      :class="{ 'is-over': dragOverTrash, 'is-disabled': !canDeleteDraggedNode }"
    >
      <DeleteOutlined class="trash-icon" />
      <span class="trash-label">{{ canDeleteDraggedNode ? '拖到此处删除' : '开始/结束节点不可删除' }}</span>
    </div>

    <WorkflowCanvasCore
      :flow-id="flowId"
      :nodes="nodes"
      v-model:edges="edgesModel"
      :edge-types="edgeTypes"
      :default-edge-options="defaultEdgeOptions"
      :is-valid-connection="isValidConnection"
      :nodes-draggable="!isVersionPreview"
      :edges-selectable="!isVersionPreview"
      :edges-updatable="!isVersionPreview"
      :nodes-connectable="!isVersionPreview"
      :elements-selectable="!isVersionPreview"
      :readonly="isVersionPreview"
      :get-node-color="getNodeColor"
      :show-connection-line="true"
      @edges-change="$emit('edges-change', $event)"
      @connect="$emit('connect', $event)"
      @edge-update="$emit('edge-update', $event)"
      @nodes-change="$emit('nodes-change', $event)"
      @node-drag-start="$emit('node-drag-start', $event)"
      @node-drag="$emit('node-drag', $event)"
      @node-drag-stop="$emit('node-drag-stop', $event)"
      @drop="$emit('drop', $event)"
      @node-click="$emit('node-click', $event)"
      @edge-click="$emit('edge-click', $event)"
      @edge-mouse-enter="$emit('edge-mouse-enter', $event)"
      @edge-mouse-move="$emit('edge-mouse-move', $event)"
      @edge-mouse-leave="$emit('edge-mouse-leave', $event)"
      @pane-click="$emit('pane-click', $event)"
    >
      <template #overlay>
        <EdgeLabelRenderer>
          <div
            v-if="edgeInsertAnchorEdge && !isVersionPreview && edgeInsertLabelStyle"
            :style="edgeInsertLabelStyle"
            class="edge-insert-label-layer"
            @mouseenter="$emit('edge-insert-pointer-enter')"
            @mouseleave="$emit('edge-insert-pointer-leave')"
          >
            <WorkflowEdgeInsert
              :visible="true"
              @select="type => $emit('insert-node-on-edge', type)"
              @menu-open="$emit('edge-insert-menu-open')"
              @menu-close="$emit('edge-insert-menu-close')"
            />
          </div>
        </EdgeLabelRenderer>
      </template>
    </WorkflowCanvasCore>

    <div v-if="nodes.length === 0" class="canvas-empty">
      <p>从左侧拖拽节点到画布开始构建工作流</p>
    </div>

        <div v-if="versionVisible" class="canvas-overlay-top">
          <div class="version-panel-float" :style="versionPanelStyle">
            <div class="version-panel-header">
              <span
                class="version-panel-drag-handle"
                title="按住拖动面板"
                @mousedown.prevent.stop="$emit('version-panel-drag-start', $event)"
              >
            <HolderOutlined />
          </span>
          <span class="version-panel-title">历史版本</span>
          <button type="button" class="version-panel-close" @click="$emit('close-version-panel')">
            <CloseOutlined />
          </button>
        </div>
        <div class="version-panel-body scroll-area-y">
          <div
            class="version-item draft"
            :class="{ active: selectedVersion === 'draft' }"
            @click="$emit('select-version', 'draft')"
          >
            <div class="version-item-title">当前草稿</div>
            <div class="version-item-desc">继续编辑未发布的修改</div>
          </div>
          <a-divider style="margin: 12px 0" />
          <a-spin :spinning="versionLoading">
            <a-timeline>
              <a-timeline-item
                v-for="item in versionList"
                :key="item.version"
                :color="selectedVersion === item.version ? '#6366f1' : '#d1d5db'"
              >
                <div
                  class="version-item"
                  :class="{ active: selectedVersion === item.version }"
                  @click="$emit('select-version', item.version)"
                >
                  <div class="version-item-header">
                    <span class="version-item-title">{{ item.current ? '线上版本' : `v${item.version}` }}</span>
                    <a-tag v-if="item.current" color="green" size="small">最新</a-tag>
                  </div>
                  <div v-if="item.description" class="version-item-note">{{ item.description }}</div>
                  <div class="version-item-desc">{{ formatVersionDesc(item) }}</div>
                </div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-if="!versionLoading && versionList.length === 0" description="暂无发布版本" />
          </a-spin>
        </div>
        <div v-if="selectedVersion !== 'draft'" class="version-panel-footer">
          <a-button type="primary" block @click="$emit('overwrite-draft')">覆盖当前草稿</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { EdgeLabelRenderer } from '@vue-flow/core'
import { DeleteOutlined, CloseOutlined, HolderOutlined } from '@ant-design/icons-vue'
import WorkflowCanvasCore from '../WorkflowCanvasCore.vue'
import WorkflowEdgeInsert from '../WorkflowEdgeInsert.vue'

const edgesModel = defineModel('edges', { type: Array, default: () => [] })

defineProps({
  flowId: { type: String, default: 'lightbot-workflow-edit' },
  nodes: { type: Array, default: () => [] },
  edgeTypes: { type: Object, default: () => ({}) },
  defaultEdgeOptions: { type: Object, required: true },
  isValidConnection: { type: Function, required: true },
  isVersionPreview: Boolean,
  isNodeDragging: Boolean,
  dragOverTrash: Boolean,
  canDeleteDraggedNode: Boolean,
  getNodeColor: { type: Function, required: true },
  edgeInsertAnchorEdge: { type: Object, default: null },
  edgeInsertLabelStyle: { type: Object, default: null },
  versionVisible: Boolean,
  versionPanelStyle: { type: Object, default: () => ({}) },
  versionList: { type: Array, default: () => [] },
  versionLoading: Boolean,
  selectedVersion: [String, Number],
  formatVersionDesc: { type: Function, required: true },
})

defineEmits([
  'edges-change', 'connect', 'edge-update', 'nodes-change',
  'node-drag-start', 'node-drag', 'node-drag-stop', 'drop',
  'node-click', 'edge-click', 'edge-mouse-enter', 'edge-mouse-move', 'edge-mouse-leave', 'pane-click',
  'edge-insert-pointer-enter', 'edge-insert-pointer-leave',
  'insert-node-on-edge', 'edge-insert-menu-open', 'edge-insert-menu-close',
  'version-panel-drag-start', 'close-version-panel', 'select-version', 'overwrite-draft',
])

const canvasAreaRef = ref(null)
const trashRef = ref(null)

defineExpose({
  canvasAreaEl: canvasAreaRef,
  trashEl: trashRef,
})
</script>

<style scoped>
.canvas-area {
  flex: 1;
  position: relative;
  background: var(--color-canvas);
  isolation: isolate;
}
.canvas-area :deep(.vue-flow__controls),
.canvas-area :deep(.vue-flow__minimap) {
  z-index: 5 !important;
}
.canvas-area :deep(.vue-flow__handle) {
  width: 16px !important;
  height: 16px !important;
  border: 2px solid #6366f1 !important;
  background: var(--color-canvas) !important;
  border-radius: 50% !important;
  transition: width 0.15s, height 0.15s, background 0.15s;
}
.canvas-area :deep(.vue-flow__handle:hover) {
  width: 22px !important;
  height: 22px !important;
  background: #6366f1 !important;
}
.workflow-trash {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 120px;
  padding: 14px 24px;
  border-radius: 12px;
  border: 2px dashed #d4d4d8;
  background: rgba(255, 255, 255, 0.95);
  color: var(--color-mute);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: border-color 0.15s, background 0.15s, color 0.15s, transform 0.15s;
  pointer-events: none;
}
.workflow-trash .trash-icon { font-size: 28px; }
.workflow-trash .trash-label { font-size: 12px; white-space: nowrap; }
.workflow-trash.is-over:not(.is-disabled) {
  border-color: #ef4444;
  border-style: solid;
  background: var(--color-error-bg);
  color: #dc2626;
  transform: translateX(-50%) scale(1.05);
}
.workflow-trash.is-disabled { opacity: 0.65; }
.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--color-mute);
  font-size: 14px;
}
.edge-insert-label-layer { pointer-events: none; }
.edge-insert-label-layer > * { pointer-events: auto; }
.canvas-overlay-top {
  position: absolute;
  inset: 0;
  z-index: 1000;
  pointer-events: none;
}
.version-panel-float {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border: 1px solid var(--color-border-slate);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  pointer-events: auto;
}
.version-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
  cursor: default;
}
.version-panel-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--color-mute);
  border-radius: 4px;
  cursor: grab;
  flex-shrink: 0;
  user-select: none;
}
.version-panel-drag-handle:hover { color: var(--color-mute); background: var(--color-canvas-soft); }
.version-panel-drag-handle:active { cursor: grabbing; }
.version-panel-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-code);
}
.version-panel-close {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-mute);
  padding: 4px;
  line-height: 1;
}
.version-panel-close:hover { color: var(--color-body); }
.version-panel-body {
  flex: 1;
  min-height: 0;
  padding: 12px;
}
.version-panel-footer {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid var(--color-hairline);
}
.version-preview-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  padding: 8px 16px;
  background: var(--color-warn-bg);
  border: 1px solid #fcd34d;
  border-radius: 8px;
  font-size: 13px;
  color: #92400e;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  pointer-events: none;
}
.version-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.version-item:hover { background: var(--color-canvas-soft-2); }
.version-item.active { background: var(--color-info-bg); border: 1px solid var(--color-purple-border); }
.version-item.draft { margin-bottom: 4px; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-item-title { font-weight: 600; font-size: 14px; color: var(--color-ink); }
.version-item-note {
  font-size: 13px;
  color: var(--color-text-dark);
  line-height: 1.45;
  margin-bottom: 4px;
  word-break: break-word;
}
.version-item-desc { font-size: 12px; color: var(--color-mute); }
</style>
