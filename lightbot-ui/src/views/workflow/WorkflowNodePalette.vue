<template>
  <div class="node-palette">
    <div class="palette-title">节点列表</div>
    <div class="node-list">
      <div
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, 'llm')"
      >
        <div class="node-icon">
          <RobotOutlined />
        </div>
        <div class="node-info">
          <div class="node-name">LLM节点</div>
          <div class="node-desc">调用大模型生成响应</div>
        </div>
      </div>

      <div
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, 'condition')"
      >
        <div class="node-icon">
          <ForkOutlined />
        </div>
        <div class="node-info">
          <div class="node-name">条件分支</div>
          <div class="node-desc">根据条件选择分支</div>
        </div>
      </div>

      <div
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, 'retrieval')"
      >
        <div class="node-icon">
          <BookOutlined />
        </div>
        <div class="node-info">
          <div class="node-name">知识检索</div>
          <div class="node-desc">从知识库检索内容</div>
        </div>
      </div>

      <div
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, 'tool')"
      >
        <div class="node-icon">
          <ToolOutlined />
        </div>
        <div class="node-info">
          <div class="node-name">工具调用</div>
          <div class="node-desc">执行工具获取结果</div>
        </div>
      </div>

      <div
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, 'script')"
      >
        <div class="node-icon">
          <CodeOutlined />
        </div>
        <div class="node-info">
          <div class="node-name">脚本节点</div>
          <div class="node-desc">执行自定义脚本</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { RobotOutlined, ForkOutlined, BookOutlined, ToolOutlined, CodeOutlined } from '@ant-design/icons-vue'

const emit = defineEmits(['drag-node'])

function onDragStart(event, nodeType) {
  emit('drag-node', event, nodeType)
}
</script>

<style scoped>
.node-palette {
  width: 200px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  padding: 16px;
}

.palette-title {
  font-weight: 600;
  font-size: 14px;
  color: #333;
  margin-bottom: 12px;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  cursor: grab;
  transition: all 0.2s;
}

.node-item:hover {
  border-color: #1890ff;
  background: #f0f7ff;
}

.node-item:active {
  cursor: grabbing;
}

.node-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 4px;
  margin-right: 8px;
}

.node-info {
  flex: 1;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.node-desc {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}
</style>