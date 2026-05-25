<template>
  <div class="node-panel" :class="{ collapsed: panelCollapsed }">
    <div class="panel-header">
      <span v-if="!panelCollapsed">{{ leftPanelTab === 'library' ? '节点库' : '画布节点' }}</span>
      <div v-if="!panelCollapsed" class="panel-header-actions">
        <WorkflowTooltip title="如何新增节点" placement="bottom">
          <button type="button" class="btn-help" @click="$emit('open-node-help')">
            <QuestionCircleOutlined />
          </button>
        </WorkflowTooltip>
      </div>
      <button class="btn-collapse" @click="$emit('update:panelCollapsed', !panelCollapsed)">
        <LeftOutlined v-if="!panelCollapsed" />
        <RightOutlined v-else />
      </button>
    </div>
    <div v-if="panelCollapsed" class="panel-collapsed-rail">
      <WorkflowTooltip title="节点库" placement="right">
        <button type="button" class="rail-btn" :class="{ active: leftPanelTab === 'library' }" @click="$emit('open-tab', 'library')">
          <AppstoreOutlined />
        </button>
      </WorkflowTooltip>
      <WorkflowTooltip title="画布节点" placement="right">
        <button type="button" class="rail-btn" :class="{ active: leftPanelTab === 'canvas' }" @click="$emit('open-tab', 'canvas')">
          <UnorderedListOutlined />
        </button>
      </WorkflowTooltip>
      <WorkflowTooltip title="如何新增节点" placement="right">
        <button type="button" class="rail-btn" @click="$emit('open-node-help')">
          <QuestionCircleOutlined />
        </button>
      </WorkflowTooltip>
    </div>
    <div v-if="!panelCollapsed" class="panel-body">
      <a-segmented
        :value="leftPanelTab"
        :options="[
          { label: '节点库', value: 'library' },
          { label: '画布节点', value: 'canvas' }
        ]"
        block
        size="small"
        style="margin-bottom: 10px"
        @change="val => $emit('update:leftPanelTab', val)"
      />

      <template v-if="leftPanelTab === 'canvas'">
        <a-input
          :value="canvasNodeSearch"
          placeholder="搜索画布节点..."
          allow-clear
          size="small"
          @update:value="val => $emit('update:canvasNodeSearch', val)"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div class="canvas-node-list">
          <div
            v-for="n in filteredCanvasNodes"
            :key="n.id"
            class="canvas-node-item"
            :class="{ active: selectedNodeId === n.id }"
            @click="$emit('focus-node', n)"
          >
            <span class="canvas-node-dot" :style="{ background: getNodeColor(n.type) }" />
            <span class="canvas-node-name">{{ n.data?.label || getNodeTitle(n.type) }}</span>
            <span class="canvas-node-type">{{ getNodeTitle(n.type) }}</span>
          </div>
        </div>
      </template>

      <template v-else>
        <a-input
          :value="nodeSearch"
          placeholder="搜索节点..."
          allow-clear
          size="small"
          @update:value="val => $emit('update:nodeSearch', val)"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <div v-for="group in filteredNodeGroups" :key="group.key" class="node-group">
          <div class="group-title">{{ group.title }}</div>
          <NodeItem
            v-for="type in group.items"
            :key="type"
            :type="type"
            :title="getNodeMeta(type).title"
            :desc="getNodeMeta(type).desc"
            :color="getNodeMeta(type).color"
            draggable="true"
            @dragstart="(e, t) => $emit('drag-start', e, t)"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import {
  SearchOutlined, LeftOutlined, RightOutlined, QuestionCircleOutlined,
  AppstoreOutlined, UnorderedListOutlined,
} from '@ant-design/icons-vue'
import WorkflowTooltip from '../WorkflowTooltip.vue'
import NodeItem from '../NodeItem.vue'

defineProps({
  panelCollapsed: Boolean,
  leftPanelTab: String,
  canvasNodeSearch: String,
  nodeSearch: String,
  filteredCanvasNodes: { type: Array, default: () => [] },
  filteredNodeGroups: { type: Array, default: () => [] },
  selectedNodeId: [String, Number],
  getNodeColor: { type: Function, required: true },
  getNodeTitle: { type: Function, required: true },
  getNodeMeta: { type: Function, required: true },
})

defineEmits([
  'update:panelCollapsed', 'update:leftPanelTab', 'update:canvasNodeSearch', 'update:nodeSearch',
  'open-node-help', 'open-tab', 'focus-node', 'drag-start',
])
</script>

<style scoped>
.node-panel {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}
.node-panel.collapsed { width: 40px; }
.node-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}
.node-panel.collapsed .panel-header {
  padding: 12px 8px;
  justify-content: center;
}
.panel-collapsed-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  flex: 1;
}
.rail-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.rail-btn:hover { background: #eef2ff; color: #6366f1; }
.rail-btn.active { background: #6366f1; color: #fff; }
.btn-collapse {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;
  margin-left: 8px;
}
.btn-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  font-size: 16px;
}
.btn-help:hover { background: #f3f4f6; color: #7c3aed; }
.node-panel .panel-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}
.node-group { margin-top: 12px; }
.group-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  padding: 4px 0;
}
.canvas-node-list {
  margin-top: 10px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}
.canvas-node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.canvas-node-item:hover,
.canvas-node-item.active { background: #f3f4f6; }
.canvas-node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.canvas-node-name {
  flex: 1;
  color: #1f2937;
  font-weight: 500;
}
.canvas-node-type {
  color: #9ca3af;
  font-size: 11px;
}
</style>
