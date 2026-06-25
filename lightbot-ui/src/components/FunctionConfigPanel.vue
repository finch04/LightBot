<template>
  <div class="function-config-panel">
    <div class="panel-header">
      <span class="panel-title">函数配置</span>
      <a-switch v-model:checked="enabled" size="small" @change="onEnableChange" />
    </div>

    <div v-if="enabled" class="panel-content">
      <div class="tool-select-area">
        <a-select
          v-model:value="selectedToolIds"
          mode="multiple"
          placeholder="选择可用函数"
          style="width: 100%"
          :options="toolOptions"
          :loading="loading"
          @change="onToolSelectChange"
        />
      </div>

      <!-- 已选工具列表 -->
      <div v-if="selectedTools.length > 0" class="tool-config-list">
        <div v-for="tool in selectedTools" :key="tool.id" class="tool-config-item">
          <div class="tool-config-header">
            <div class="tool-info">
              <span class="tool-name">{{ tool.name }}</span>
              <span class="tool-desc" v-if="tool.description">{{ tool.description }}</span>
            </div>
            <button class="btn-icon-sm" @click="removeTool(tool.id)">
              <CloseOutlined />
            </button>
          </div>
          <div class="tool-config-body" v-if="tool.inputSchema">
            <div class="config-label">参数配置（JSON）</div>
            <JsonInput
              v-model="toolConfigs[tool.id]"
              :rows="3"
              placeholder="{}"
            />
          </div>
        </div>
      </div>

      <div v-if="toolList.length === 0 && !loading" class="tool-empty">
        <p>暂无可用函数，请先在「工具管理」中创建</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import JsonInput from './JsonInput.vue'
import { getTools } from '../api/tool'

const props = defineProps({
  toolConfig: { type: String, default: '{}' }
})

const emit = defineEmits(['update:toolConfig'])

const enabled = ref(false)
const loading = ref(false)
const toolList = ref([])
const selectedToolIds = ref([])
const toolConfigs = ref({})

// 工具选项
const toolOptions = computed(() => {
  return toolList.value.map(t => ({
    value: String(t.id),
    label: t.name
  }))
})

// 已选工具详情
const selectedTools = computed(() => {
  return toolList.value.filter(t => selectedToolIds.value.includes(String(t.id)))
})

// 加载工具列表
async function loadTools() {
  loading.value = true
  try {
    const res = await getTools({ pageNum: 1, pageSize: 100 })
    toolList.value = res.data?.records || []
  } catch {
    toolList.value = []
  } finally {
    loading.value = false
  }
}

// 启用状态变化
function onEnableChange(val) {
  if (val && toolList.value.length === 0) {
    loadTools()
  }
  updateConfig()
}

// 工具选择变化
function onToolSelectChange() {
  // 为新选中的工具初始化配置
  for (const id of selectedToolIds.value) {
    if (!toolConfigs.value[id]) {
      toolConfigs.value[id] = '{}'
    }
  }
  updateConfig()
}

// 移除工具
function removeTool(id) {
  selectedToolIds.value = selectedToolIds.value.filter(i => i !== String(id))
  delete toolConfigs.value[id]
  updateConfig()
}

// 更新配置JSON
function updateConfig() {
  const config = {
    enabled: enabled.value,
    tools: selectedToolIds.value.map(id => ({
      id: id,
      config: toolConfigs.value[id] || '{}'
    }))
  }
  emit('update:toolConfig', JSON.stringify(config))
}

// 解析外部配置
function parseConfig(configStr) {
  if (!configStr) return
  try {
    const config = JSON.parse(configStr)
    enabled.value = config.enabled || false
    selectedToolIds.value = (config.tools || []).map(t => String(t.id))
    toolConfigs.value = {}
    for (const t of (config.tools || [])) {
      toolConfigs.value[t.id] = t.config || '{}'
    }
    if (enabled.value && toolList.value.length === 0) {
      loadTools()
    }
  } catch {
    // 配置解析失败，使用默认值
  }
}

// 监听外部配置变化
watch(() => props.toolConfig, (val) => {
  parseConfig(val)
}, { immediate: true })

onMounted(() => {
  if (enabled.value) {
    loadTools()
  }
})
</script>

<style scoped>
.function-config-panel {
  padding: 12px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  margin-top: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.panel-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-select-area {
  display: flex;
  gap: 8px;
}

.tool-config-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-config-item {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 12px;
}

.tool-config-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.tool-info {
  flex: 1;
  min-width: 0;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  display: block;
}

.tool-desc {
  font-size: 12px;
  color: var(--color-mute);
  display: block;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-config-body {
  margin-top: 8px;
}

.config-label {
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 4px;
}

.btn-icon-sm {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 12px;
}

.btn-icon-sm:hover { background: var(--color-canvas-soft-2); color: var(--color-error); }

.tool-empty {
  text-align: center;
  padding: 20px;
  color: var(--color-mute);
  font-size: 13px;
}
</style>