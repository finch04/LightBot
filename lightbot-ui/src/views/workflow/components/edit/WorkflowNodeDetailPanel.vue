<template>
  <div class="config-panel">
    <div class="panel-header config-panel-header">
      <div class="node-type-badge">
        <div class="type-icon" :style="{ background: getNodeColor(node.type) + '20', color: getNodeColor(node.type) }">
          <NodeTypeIcon :type="node.type" />
        </div>
        <span class="type-name">{{ getNodeTitle(node.type) }}</span>
        <WorkflowTooltip
          v-if="node.type !== 'start' && node.type !== 'end' && !isGroupBuiltinNode(node)"
          title="查看节点说明与示例配置"
          placement="topLeft"
        >
          <button type="button" class="btn-node-example" @click="$emit('open-example')">
            <QuestionCircleOutlined />
          </button>
        </WorkflowTooltip>
      </div>
      <div class="panel-header-right">
        <div
          v-if="node.type !== 'start' && node.type !== 'end' && !isGroupBuiltinNode(node)"
          class="panel-header-actions node-detail-actions"
        >
          <WorkflowTooltip v-if="canTestSelectedNode" title="测试运行此节点" placement="top">
            <button type="button" class="btn-node-action" :disabled="isVersionPreview" @click="$emit('open-test')">
              <PlayCircleOutlined />
            </button>
          </WorkflowTooltip>
          <WorkflowTooltip title="复制节点" placement="top">
            <button type="button" class="btn-node-action" :disabled="isVersionPreview" @click="$emit('copy')">
              <CopyOutlined />
            </button>
          </WorkflowTooltip>
        </div>
        <button class="btn-close" @click="$emit('close')">
          <CloseOutlined />
        </button>
      </div>
    </div>
    <div class="panel-body" :class="{ 'panel-body-readonly': isVersionPreview }">
      <div v-if="nodeErrors.length > 0" class="node-errors">
        <div v-for="err in nodeErrors" :key="err.field" class="error-item">
          <ExclamationCircleOutlined /> {{ err.message }}
        </div>
      </div>

      <a-alert v-if="isVersionPreview" type="info" show-icon message="历史版本预览（只读）" class="preview-readonly-alert" />

      <a-alert
        v-if="isGroupBuiltinNode(node)"
        type="info"
        show-icon
        message="容器内置节点"
        description="该节点随循环/批处理容器自动创建，不可单独删除；删除容器时会一并移除。"
      />

      <WorkflowNodeConfig
        v-else-if="node.type !== 'start' && node.type !== 'end'"
        :readonly="isVersionPreview"
        :node="node"
        :edges="edges"
        :providers="providers"
        :llm-model-list="llmModelList"
        :knowledge-list="knowledgeList"
        :tools="tools"
        :target-nodes="targetNodes"
        :filter-knowledge-option="filterKnowledgeOption"
        :filter-tool-option="filterToolOption"
        :get-tool-type-label="getToolTypeLabel"
        @sync="$emit('sync')"
        @llm-provider-change="$emit('llm-provider-change', $event)"
        @llm-model-change="$emit('llm-model-change', $event)"
        @knowledge-change="$emit('knowledge-change', $event)"
        @tool-change="$emit('tool-change', $event)"
      />
      <a-form v-else layout="vertical" :disabled="isVersionPreview">
        <a-form-item label="节点 ID"><span class="node-id-display mono">{{ node.id }}</span></a-form-item>
        <a-form-item label="节点名称">
          <a-input v-model:value="node.data.label" :disabled="isVersionPreview" @change="$emit('sync')" />
        </a-form-item>
      </a-form>

      <div
        v-if="!isVersionPreview && node.type !== 'start' && node.type !== 'end' && !isGroupBuiltinNode(node)"
        class="panel-footer"
      >
        <a-button type="text" danger @click="$emit('delete')">
          <DeleteOutlined /> 删除节点
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  CloseOutlined, DeleteOutlined, ExclamationCircleOutlined,
  QuestionCircleOutlined, PlayCircleOutlined, CopyOutlined,
} from '@ant-design/icons-vue'
import WorkflowTooltip from '../WorkflowTooltip.vue'
import WorkflowNodeConfig from '../WorkflowNodeConfig.vue'
import NodeTypeIcon from '../NodeTypeIcon.vue'

defineProps({
  node: { type: Object, required: true },
  edges: { type: Array, default: () => [] },
  isVersionPreview: Boolean,
  canTestSelectedNode: Boolean,
  nodeErrors: { type: Array, default: () => [] },
  providers: { type: Array, default: () => [] },
  llmModelList: { type: Array, default: () => [] },
  knowledgeList: { type: Array, default: () => [] },
  tools: { type: Array, default: () => [] },
  targetNodes: { type: Array, default: () => [] },
  filterKnowledgeOption: { type: Function, required: true },
  filterToolOption: { type: Function, required: true },
  getToolTypeLabel: { type: Function, required: true },
  getNodeColor: { type: Function, required: true },
  getNodeTitle: { type: Function, required: true },
  isGroupBuiltinNode: { type: Function, required: true },
})

defineEmits([
  'close', 'open-example', 'open-test', 'copy', 'sync',
  'llm-provider-change', 'llm-model-change', 'knowledge-change', 'tool-change', 'delete',
])
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
.config-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  gap: 12px;
}
.node-type-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.type-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.type-name { font-weight: 600; color: #1f2937; }
.panel-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-left: auto;
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.btn-node-example {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #eef2ff;
  color: #6366f1;
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-node-example:hover { background: #6366f1; color: #fff; }
.node-detail-actions { display: inline-flex; align-items: center; gap: 4px; }
.btn-node-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: #f1f5f9;
  color: #475569;
  cursor: pointer;
}
.btn-node-action:hover:not(:disabled) { background: #6366f1; color: #fff; }
.btn-node-action:disabled { opacity: 0.45; cursor: not-allowed; }
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
.panel-body-readonly { position: relative; }
.panel-body-readonly::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 15;
  cursor: not-allowed;
  background: transparent;
}
.panel-body-readonly .preview-readonly-alert {
  position: relative;
  z-index: 16;
}
.preview-readonly-alert { margin-bottom: 12px; }
.node-errors { margin-bottom: 12px; }
.error-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 4px;
  color: #dc2626;
  font-size: 12px;
  margin-bottom: 4px;
}
.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
