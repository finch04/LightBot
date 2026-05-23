<template>
  <div class="extensions-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">扩展管理</h1>
        <p class="page-desc">管理 MCP 服务、Skill 技能和工具</p>
      </div>
    </div>
    <div class="tab-toolbar">
      <a-tabs v-model:activeKey="activeTab" class="ext-tabs">
        <a-tab-pane key="mcp" tab="MCP Server" />
        <a-tab-pane key="skills" tab="Skill" />
        <a-tab-pane key="tools" tab="工具" />
      </a-tabs>
      <div class="toolbar-actions">
        <a-input
          v-model:value="searchText"
          :placeholder="searchPlaceholder"
          allow-clear
          style="width: 220px"
          @change="handleSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <button class="btn-primary" @click="handleAdd">
          <PlusOutlined /> {{ addBtnText }}
        </button>
      </div>
    </div>
    <div class="tab-content">
      <McpManage v-show="activeTab === 'mcp'" ref="mcpRef" hide-header />
      <SkillManage v-show="activeTab === 'skills'" ref="skillRef" hide-header />
      <ToolManage v-show="activeTab === 'tools'" ref="toolRef" hide-header />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import McpManage from './McpManage.vue'
import SkillManage from './SkillManage.vue'
import ToolManage from './ToolManage.vue'

const activeTab = ref('mcp')
const searchText = ref('')
const mcpRef = ref(null)
const skillRef = ref(null)
const toolRef = ref(null)

const addBtnText = computed(() => {
  const map = { mcp: '新增 MCP Server', skills: '新增 Skill', tools: '新增工具' }
  return map[activeTab.value] || '新增'
})

const searchPlaceholder = computed(() => {
  const map = { mcp: '搜索 MCP Server...', skills: '搜索 Skill...', tools: '搜索工具...' }
  return map[activeTab.value] || '搜索...'
})

function handleAdd() {
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : toolRef.value
  target?.openDialog()
}

function handleSearch() {
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : toolRef.value
  target?.search(searchText.value)
}

// 切换Tab时清空搜索
watch(activeTab, () => {
  searchText.value = ''
  handleSearch()
})
</script>

<style scoped>
.extensions-page {
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
  padding: 32px;
}
.page-header {
  margin-bottom: 20px;
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
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.ext-tabs {
  flex: 1;
}
.ext-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #171717;
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}
.btn-primary:hover {
  background: #27272a;
}
.tab-content {
  height: calc(100vh - 180px);
  overflow-y: auto;
}
</style>
