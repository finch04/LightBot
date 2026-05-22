<template>
  <div class="extensions-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">扩展管理</h1>
        <p class="page-desc">管理 MCP 服务、Skill 技能和工具</p>
      </div>
    </div>
    <a-tabs v-model:activeKey="activeTab" class="ext-tabs">
      <a-tab-pane key="mcp" tab="MCP Server">
        <McpManage hide-header />
      </a-tab-pane>
      <a-tab-pane key="skills" tab="Skill">
        <SkillManage hide-header />
      </a-tab-pane>
      <a-tab-pane key="tools" tab="工具">
        <ToolManage hide-header />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import McpManage from './McpManage.vue'
import SkillManage from './SkillManage.vue'
import ToolManage from './ToolManage.vue'

const activeTab = ref('mcp')
</script>

<style scoped>
.extensions-page {
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
  padding: 32px;
}
.page-header {
  margin-bottom: 24px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}
.ext-tabs :deep(.ant-tabs-content) {
  height: 100%;
}
.ext-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
  overflow-y: auto;
}
</style>
