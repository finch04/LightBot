<template>
  <div class="node-config-panel">
    <div class="panel-header">
      <div class="panel-title">{{ nodeTitle }}</div>
      <a-button type="text" size="small" @click="$emit('close')">
        <CloseOutlined />
      </a-button>
    </div>

    <div class="panel-body">
      <!-- LLM节点配置 -->
      <template v-if="node.type === 'llm'">
        <a-form layout="vertical">
          <a-form-item label="模型">
            <a-select
              v-model:value="localData.modelId"
              placeholder="选择模型"
              @change="onModelChange"
            >
              <a-select-option v-for="m in models" :key="m.id" :value="m.id">
                {{ m.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="Prompt模板">
            <a-textarea
              v-model:value="localData.promptTemplate"
              placeholder="使用 {{input}} 表示用户输入"
              :rows="4"
              @change="emitUpdate"
            />
          </a-form-item>
        </a-form>
      </template>

      <!-- 条件节点配置 -->
      <template v-if="node.type === 'condition'">
        <div class="branches-config">
          <div class="branch-list">
            <div v-for="(branch, index) in localData.branches" :key="index" class="branch-item">
              <a-input
                v-model:value="branch.condition"
                placeholder="条件表达式（如 input == 'yes'）"
                @change="emitUpdate"
              />
              <a-select
                v-model:value="branch.targetNodeId"
                placeholder="目标节点"
                @change="emitUpdate"
              >
                <a-select-option v-for="n in targetNodes" :key="n.id" :value="n.id">
                  {{ getNodeLabel(n) }}
                </a-select-option>
              </a-select>
              <a-button type="text" danger size="small" @click="removeBranch(index)">
                <DeleteOutlined />
              </a-button>
            </div>
          </div>
          <a-button type="dashed" block @click="addBranch">
            添加分支
          </a-button>
        </div>
      </template>

      <!-- 知识检索节点配置 -->
      <template v-if="node.type === 'retrieval'">
        <a-form layout="vertical">
          <a-form-item label="知识库">
            <a-select
              v-model:value="localData.knowledgeId"
              placeholder="选择知识库"
              @change="onKnowledgeChange"
            >
              <a-select-option v-for="k in knowledgeList" :key="k.id" :value="k.id">
                {{ k.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </template>

      <!-- 工具节点配置 -->
      <template v-if="node.type === 'tool'">
        <a-form layout="vertical">
          <a-form-item label="工具">
            <a-select
              v-model:value="localData.toolId"
              placeholder="选择工具"
              @change="onToolChange"
            >
              <a-select-option v-for="t in tools" :key="t.id" :value="t.id">
                {{ t.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { CloseOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore } from '../../stores/workflow'
import { getModelProviders } from '../../api/modelProvider'
import { getKnowledgeList } from '../../api/knowledge'
import { getTools } from '../../api/tool'

const props = defineProps({
  node: Object
})

const emit = defineEmits(['close', 'update'])

const workflowStore = useWorkflowStore()
const localData = ref({ ...props.node.data })

const models = ref([])
const knowledgeList = ref([])
const tools = ref([])

// 节点标题
const nodeTitle = computed(() => {
  const titles = {
    llm: 'LLM节点配置',
    condition: '条件分支配置',
    retrieval: '知识检索配置',
    tool: '工具调用配置'
  }
  return titles[props.node.type] || '节点配置'
})

// 可选的目标节点（排除自身和 start节点）
const targetNodes = computed(() => {
  return workflowStore.nodes.filter(n => n.id !== props.node.id && n.type !== 'start')
})

// 获取节点标签
function getNodeLabel(n) {
  const labels = {
    start: '开始',
    end: '结束',
    llm: 'LLM',
    condition: '条件',
    retrieval: '知识检索',
    tool: '工具'
  }
  return labels[n.type] || n.type
}

// 监听 node.data 变化
watch(
  () => props.node.data,
  (newData) => {
    localData.value = { ...newData }
  },
  { deep: true }
)

// 加载资源列表
onMounted(async () => {
  try {
    // 加载模型提供商
    const modelRes = await getModelProviders({ pageNum: 1, pageSize: 100 })
    models.value = modelRes.data.records || []

    // 加载知识库
    const knowledgeRes = await getKnowledgeList({ pageNum: 1, pageSize: 100 })
    knowledgeList.value = knowledgeRes.data.records || []

    // 加载工具
    const toolRes = await getTools({ pageNum: 1, pageSize: 100 })
    tools.value = toolRes.data.records || []
  } catch (e) {
    console.error('加载资源失败:', e)
  }
})

// 模型选择变化
function onModelChange(value) {
  const model = models.value.find(m => m.id === value)
  localData.value.modelName = model?.name || ''
  emitUpdate()
}

// 知识库选择变化
function onKnowledgeChange(value) {
  const knowledge = knowledgeList.value.find(k => k.id === value)
  localData.value.knowledgeName = knowledge?.name || ''
  emitUpdate()
}

// 工具选择变化
function onToolChange(value) {
  const tool = tools.value.find(t => t.id === value)
  localData.value.toolName = tool?.name || ''
  emitUpdate()
}

// 添加分支
function addBranch() {
  if (!localData.value.branches) {
    localData.value.branches = []
  }
  localData.value.branches.push({
    condition: '',
    targetNodeId: ''
  })
  emitUpdate()
}

// 删除分支
function removeBranch(index) {
  localData.value.branches.splice(index, 1)
  emitUpdate()
}

// 发送更新事件
function emitUpdate() {
  emit('update', props.node.id, localData.value)
}
</script>

<style scoped>
.node-config-panel {
  width: 280px;
  background: #fff;
  border-left: 1px solid #e8e8e8;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
}

.panel-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.branches-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.branch-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.branch-item .a-input {
  flex: 1;
}
</style>