<template>
  <slot name="trigger">
    <a-tooltip title="查看运行时自动注入的工具" :placement="placement">
      <button type="button" class="lb-btn lb-btn--accent lb-btn--accent--tool" @click="open">
        <SettingOutlined /> 自动注入工具
      </button>
    </a-tooltip>
  </slot>

  <a-drawer
    v-model:open="drawerVisible"
      title="自动注入工具"
    :width="560"
    :bodyStyle="{ padding: '16px' }"
  >
    <div class="drawer-desc">
      这些能力由 Agent 绑定资源、开放 API 运行时条件或模型能力注入，不占用手动绑定工具名额。
    </div>
    <a-spin :spinning="loading">
      <div class="group-list">
        <section v-for="group in groups" :key="group.key" class="tool-group" :class="{ disabled: showStatus && !group.enabled }">
          <div class="group-head">
            <div>
              <h4>{{ group.title }}</h4>
              <p>
                {{ group.triggerText || group.trigger }}
                <span v-if="showCount && group.triggerCount !== undefined" class="trigger-count">（当前 {{ group.triggerCount }} 个）</span>
              </p>
            </div>
            <span
              v-if="showStatus"
              class="status-pill"
              :class="{ enabled: group.enabled }"
            >
              {{ group.enabled ? '已启用' : '未启用' }}
            </span>
          </div>
          <div v-if="showStatus && group.reason" class="group-reason">{{ group.reason }}</div>
          <div v-if="group.tools.length" class="tool-list">
            <div v-for="tool in group.tools" :key="`${group.key}-${tool.name}`" class="tool-item">
              <span class="tool-name">{{ tool.displayName }}</span>
              <code>{{ tool.name }}</code>
            </div>
          </div>
          <div v-else class="tool-empty">暂无可展示工具</div>
        </section>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { SettingOutlined } from '@ant-design/icons-vue'
import { getTools } from '../api/tool'

const props = defineProps({
  placement: { type: String, default: 'top' },
  showStatus: { type: Boolean, default: true },
  showCount: { type: Boolean, default: true },
  selectedKnowledge: { type: Array, default: () => [] },
  selectedMcpServers: { type: Array, default: () => [] },
  selectedSubAgents: { type: Array, default: () => [] },
  agentConfig: { type: Object, default: () => ({}) },
})

const drawerVisible = ref(false)
const loading = ref(false)
const knowledgeTools = ref([])

defineExpose({ open })

async function open() {
  drawerVisible.value = true
  await loadDynamicToolMeta()
}

async function loadDynamicToolMeta() {
  loading.value = true
  try {
    const toolRes = await getTools({ pageNum: 1, pageSize: 100, toolType: 'knowledge' })
    knowledgeTools.value = toolRes.data?.records || []
  } finally {
    loading.value = false
  }
}

const groups = computed(() => {
  const knowledgeEnabled = props.selectedKnowledge.length > 0
  const mcpEnabled = props.selectedMcpServers.length > 0
  const subAgentEnabled = props.selectedSubAgents.length > 0
  const fileReadEnabled = !!props.agentConfig.enableFileRead
  const webSearchEnabled = !!props.agentConfig.enableWebSearch

  return [
    {
      key: 'session-collaboration',
      title: '会话协作工具',
      enabled: true,
      triggerText: '触发条件：主 Agent 会话运行时自动注入',
      triggerCount: 2,
      reason: '无需手动绑定，不占用 Agent 工具配额；文件交付仅允许主 Agent 执行。',
      tools: [
        { name: 'write_todos', displayName: '更新待办' },
        { name: 'present_artifacts', displayName: '文件交付' },
      ],
    },
    {
      key: 'knowledge',
      title: '知识库工具',
      enabled: knowledgeEnabled,
      triggerText: '触发条件：Agent 已绑定知识库',
      triggerCount: props.selectedKnowledge.length,
      reason: knowledgeEnabled ? '' : '绑定知识库后，运行时会自动注入知识库检索相关工具。',
      tools: knowledgeTools.value.map(t => ({
        name: t.name,
        displayName: t.displayName || t.name,
      })),
    },
    {
      key: 'memory',
      title: '长期记忆工具',
      enabled: true,
      triggerText: '触发条件：策略启用后，控制台用 debug_user_{用户ID}；开放 API 传 externalUserId',
      reason: '策略在「系统管理 → 长期记忆」配置企业默认，各 Key 可单独覆盖；正式记忆与控制台调试命名空间互不共享。',
      tools: [
        { name: 'memory_save', displayName: '保存长期记忆' },
        { name: 'memory_search', displayName: '查询长期记忆' },
        { name: 'memory_delete', displayName: '停用长期记忆' },
      ],
    },
    {
      key: 'subagent',
      title: 'SubAgent 批次委派',
      enabled: subAgentEnabled,
      triggerText: '触发条件：Agent 已绑定 SubAgent',
      triggerCount: props.selectedSubAgents.length,
      reason: subAgentEnabled ? '统一入口支持同步、并行与后台模式；查询和取消会回填同一个任务批次。' : '绑定子智能体后，将自动注入统一的批次委派能力。',
      tools: [
        { name: 'delegate_to_subagent', displayName: '统一批次委派入口' },
      ],
    },
    {
      key: 'mcp',
      title: 'MCP 工具',
      enabled: mcpEnabled,
      triggerText: '触发条件：Agent 已绑定 MCP Server',
      triggerCount: props.selectedMcpServers.length,
      reason: mcpEnabled ? '具体工具由 MCP Server 运行时返回，并受 Server 工具启停配置影响。' : '绑定 MCP Server 后，运行时会加载该 Server 暴露的工具。',
      tools: props.selectedMcpServers.map(server => ({
        name: server.name || String(server.id),
        displayName: server.displayName || server.name || `MCP Server ${server.id}`,
      })),
    },
    {
      key: 'capability',
      title: '模型与 Agent 能力工具',
      enabled: fileReadEnabled || webSearchEnabled,
      trigger: '触发条件：模型能力或 Agent 对话配置开启',
      reason: fileReadEnabled || webSearchEnabled ? '' : '开启文件读取、联网搜索等能力后，相关系统工具会按需参与运行时链路。',
      tools: [
        ...(fileReadEnabled ? [{ name: 'file_read_context', displayName: '文件读取上下文' }] : []),
        ...(webSearchEnabled ? [{ name: 'web_search', displayName: '联网搜索能力' }] : []),
      ],
    },
  ]
})
</script>

<style scoped>
/* trigger 按钮已迁移到 .lb-btn.lb-btn--accent.lb-btn--accent--tool（绿色渐变），
   与 Extensions tools tab、AgentDetail tools/knowledge tab 三处嵌入的视觉一致 */
.drawer-desc {
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-mute);
}
.group-list {
  display: grid;
  gap: 12px;
}
.tool-group {
  padding: 14px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
}
.tool-group.disabled {
  opacity: 0.78;
}
.group-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
  color: var(--color-mute);
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  flex-shrink: 0;
}
.status-pill.enabled {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}
.group-head h4 {
  margin: 0;
  font-size: 15px;
  color: var(--color-ink);
}
.group-head p,
.group-reason {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-mute);
}
.trigger-count {
  color: var(--color-primary);
  font-weight: 500;
}
.tool-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.tool-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-canvas-soft);
}
.tool-name {
  min-width: 0;
  color: var(--color-ink);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-item code {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-mute);
}
.tool-empty {
  margin-top: 12px;
  color: var(--color-mute);
  font-size: 12px;
}
</style>
