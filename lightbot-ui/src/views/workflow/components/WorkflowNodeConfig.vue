<template>
  <a-form layout="vertical">
    <a-form-item label="节点 ID">
      <span class="node-id-display mono">{{ node.id }}</span>
    </a-form-item>
    <a-form-item label="节点名称">
      <a-input v-model:value="node.data.label" placeholder="输入节点名称" @change="emitSync" />
    </a-form-item>

    <!-- LLM -->
    <template v-if="node.type === 'llm'">
      <a-form-item label="模型提供商" required>
        <a-select v-model:value="node.data.providerId" placeholder="选择模型提供商" @change="onLlmProviderChange">
          <a-select-option v-for="p in providers" :key="p.id" :value="p.id">
            {{ p.name }} ({{ p.type?.code || p.type }})
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="模型" required>
        <a-select
          v-model:value="node.data.modelId"
          placeholder="选择具体模型"
          show-search
          :disabled="!node.data.providerId"
          @change="onLlmModelChange"
        >
          <a-select-option v-for="m in llmModelList" :key="m.modelId" :value="m.modelId">
            {{ m.name || m.modelId }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="系统提示词">
        <a-textarea v-model:value="node.data.sysPrompt" :rows="2" placeholder="定义 AI 角色、行为约束（对应 SystemMessage）" @change="emitSync" />
        <div class="field-hint">系统提示词设定 AI 的身份与全局规则，不会随用户输入变化；留空则仅使用用户提示词。</div>
      </a-form-item>
      <a-form-item label="用户提示词模板" required>
        <a-textarea
          v-model:value="node.data.promptTemplate"
          placeholder="使用 {{input}} 表示用户输入"
          :rows="4"
          @change="emitSync"
        />
        <div class="field-hint">用户提示词为每轮具体任务内容（对应 UserMessage），支持 {{input}} 等变量引用。</div>
      </a-form-item>
      <a-form-item label="温度">
        <a-slider v-model:value="node.data.temperature" :min="0" :max="2" :step="0.1" @change="emitSync" />
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" @update:model-value="emitSync" />
    </template>

    <!-- 意图分类 -->
    <template v-if="node.type === 'classifier'">
      <a-form-item label="输入变量" required>
        <a-input v-model:value="node.data.inputVariable" placeholder="{{input}}" @change="emitSync" />
        <div class="field-hint">用于意图判断的文本内容，支持 {{input}} 等变量</div>
      </a-form-item>
      <a-form-item label="模型提供商" required>
        <a-select v-model:value="node.data.providerId" placeholder="选择模型" @change="onLlmProviderChange">
          <a-select-option v-for="p in providers" :key="p.id" :value="p.id">{{ p.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="模型" required>
        <a-select
          v-model:value="node.data.modelId"
          :disabled="!node.data.providerId"
          placeholder="选择具体模型"
          @change="onLlmModelChange"
        >
          <a-select-option v-for="m in llmModelList" :key="m.modelId" :value="m.modelId">
            {{ m.name || m.modelId }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-divider>意图分类</a-divider>
      <div v-for="(cond, idx) in node.data.conditions" :key="cond.id" class="intent-item">
        <a-textarea
          v-model:value="cond.subject"
          placeholder="描述该意图，如：用户咨询产品价格"
          :rows="2"
          @change="emitSync"
        />
        <a-button type="text" danger size="small" @click="removeIntent(idx)"><DeleteOutlined /></a-button>
      </div>
      <a-button type="dashed" block size="small" @click="addIntent">
        <PlusOutlined /> 添加意图（{{ node.data.conditions?.length || 0 }}/10）
      </a-button>
      <a-form-item label="其他意图">
        <div class="field-hint">当所有意图均不匹配时，走「其他意图」出口分支</div>
      </a-form-item>
      <a-form-item label="思考模式">
        <a-select v-model:value="node.data.mode_switch" @change="emitSync">
          <a-select-option value="efficient">快速模式 — 避免输出思考过程，速度更快</a-select-option>
          <a-select-option value="advanced">效果模式 — 逐步思考，匹配更精准</a-select-option>
        </a-select>
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" @update:model-value="emitSync" />
      <a-form-item label="提示词（额外约束）">
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="为意图识别提供额外要求" @change="emitSync" />
      </a-form-item>
      <a-form-item label="输出">
        <div class="output-desc">subject（命中主题）、thought（思考过程，效果模式下输出）</div>
      </a-form-item>
    </template>

    <!-- 条件 -->
    <template v-if="node.type === 'condition'">
      <a-form-item label="条件分支">
        <div v-for="(branch, index) in node.data.branches" :key="index" class="branch-item">
          <a-input v-model:value="branch.condition" placeholder="条件表达式" size="small" @change="emitSync" />
          <a-select v-model:value="branch.targetNodeId" placeholder="目标节点" size="small" @change="emitSync">
            <a-select-option v-for="n in targetNodes" :key="n.id" :value="n.id">
              {{ n.data?.label || n.type }}
            </a-select-option>
          </a-select>
          <a-button type="text" danger size="small" @click="removeBranch(index)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" @click="addBranch"><PlusOutlined /> 添加分支</a-button>
      </a-form-item>
    </template>

    <!-- 知识检索 -->
    <template v-if="node.type === 'retrieval'">
      <a-form-item label="输入变量">
        <a-input v-model:value="node.data.inputVariable" placeholder="{{input}}" @change="emitSync" />
      </a-form-item>
      <a-form-item label="知识库" required>
        <a-select
          v-model:value="node.data.knowledgeId"
          show-search
          placeholder="选择知识库"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterKnowledgeOption"
          @change="onKnowledgeChange"
        >
          <a-select-option v-for="k in knowledgeList" :key="k.id" :value="k.id" :label="k.name">
            <div class="resource-option">
              <div class="resource-option-header">
                <BookOutlined class="resource-option-icon knowledge" />
                <span class="resource-option-title">{{ k.name }}</span>
              </div>
              <div v-if="k.description" class="resource-option-desc">{{ k.description }}</div>
              <div class="resource-option-meta">
                <span v-if="k.embeddingModel">向量模型: {{ k.embeddingModel }}</span>
                <span v-if="k.documentCount != null">文档: {{ k.documentCount }}</span>
              </div>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>

      <div class="kb-config-card">
        <div class="kb-config-card-header">
          <span class="kb-config-title">检索参数</span>
          <span v-if="node.data.knowledgeName" class="kb-config-source">来自「{{ node.data.knowledgeName }}」</span>
        </div>
        <div class="kb-config-fields">
          <div class="kb-config-field">
            <label>TopK</label>
            <a-input-number
              :value="displayTopK"
              :min="1"
              :max="20"
              :disabled="!node.data.overrideConfig"
              @change="v => { node.data.topK = v; emitSync() }"
            />
          </div>
          <div class="kb-config-field">
            <label>相似度阈值</label>
            <a-input-number
              :value="displayThreshold"
              :min="0"
              :max="1"
              :step="0.05"
              :disabled="!node.data.overrideConfig"
              @change="v => { node.data.threshold = v; emitSync() }"
            />
          </div>
        </div>
        <div v-if="!node.data.overrideConfig" class="kb-config-readonly-hint">
          当前使用知识库默认配置，开启下方开关后可自定义
        </div>
      </div>

      <a-form-item label="覆盖知识库配置">
        <a-switch v-model:checked="node.data.overrideConfig" @change="onOverrideToggle" />
        <div class="field-hint">关闭时沿用知识库默认 TopK / 阈值；开启后可在此节点单独调整</div>
      </a-form-item>
    </template>

    <!-- 工具 -->
    <template v-if="node.type === 'tool'">
      <a-form-item label="工具" required>
        <a-select
          v-model:value="node.data.toolId"
          show-search
          placeholder="选择工具"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterToolOption"
          @change="onToolChange"
        >
          <a-select-option
            v-for="t in tools"
            :key="t.id"
            :value="t.id"
            :label="t.displayName || t.name"
          >
            <div class="resource-option">
              <div class="resource-option-header">
                <ToolOutlined class="resource-option-icon tool" />
                <span class="resource-option-title">{{ t.displayName || t.name }}</span>
                <span v-if="t.type" class="resource-tag">{{ getToolTypeLabel(t.type) }}</span>
              </div>
              <div v-if="t.description" class="resource-option-desc">{{ t.description }}</div>
              <div class="resource-option-meta">
                <span v-if="t.name && t.displayName">标识: {{ t.name }}</span>
              </div>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>
    </template>

    <!-- 流程输入 -->
    <template v-if="node.type === 'input'">
      <a-form-item label="输出参数">
        <div v-for="(param, idx) in node.data.outputParams" :key="idx" class="param-row">
          <a-input v-model:value="param.key" placeholder="参数名" @change="emitSync" />
          <a-select v-model:value="param.type" style="width: 100px" @change="emitSync">
            <a-select-option value="String">String</a-select-option>
            <a-select-option value="Number">Number</a-select-option>
            <a-select-option value="Boolean">Boolean</a-select-option>
          </a-select>
          <a-input v-model:value="param.defaultValue" placeholder="默认值" @change="emitSync" />
          <a-button type="text" danger @click="removeOutputParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" @click="addOutputParam"><PlusOutlined /> 添加参数</a-button>
      </a-form-item>
    </template>

    <!-- 流程输出 -->
    <template v-if="node.type === 'output'">
      <a-form-item label="输出内容" required>
        <a-textarea v-model:value="node.data.output" :rows="4" placeholder="{{input}} 或 {{llmOutput}}" @change="emitSync" />
      </a-form-item>
      <a-form-item label="流式输出">
        <a-switch v-model:checked="node.data.streamSwitch" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- 变量处理 -->
    <template v-if="node.type === 'variable_handle'">
      <a-form-item label="处理方式">
        <a-select v-model:value="node.data.handleType" @change="emitSync">
          <a-select-option value="template">模板拼接</a-select-option>
          <a-select-option value="group">分组取值</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="node.data.handleType === 'template'" label="模板内容" required>
        <a-textarea v-model:value="node.data.templateContent" :rows="4" placeholder="支持 {{变量名}}" @change="emitSync" />
      </a-form-item>
      <template v-if="node.data.handleType === 'group'">
        <a-form-item label="分组策略">
          <a-select v-model:value="node.data.groupStrategy" @change="emitSync">
            <a-select-option value="firstNotNull">取第一个非空</a-select-option>
            <a-select-option value="lastNotNull">取最后一个非空</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变量列表">
          <div v-for="(v, idx) in groupVariables" :key="idx" class="param-row">
            <a-input v-model:value="v.value" placeholder="{{变量引用}}" @change="emitSync" />
            <a-button type="text" danger @click="removeGroupVar(idx)"><DeleteOutlined /></a-button>
          </div>
          <a-button type="dashed" block size="small" @click="addGroupVar"><PlusOutlined /> 添加变量</a-button>
        </a-form-item>
      </template>
    </template>

    <!-- 参数提取 -->
    <template v-if="node.type === 'parameter_extractor'">
      <a-form-item label="输入变量" required>
        <a-input v-model:value="node.data.inputVariable" placeholder="{{input}}" @change="emitSync" />
      </a-form-item>
      <a-form-item label="模型提供商" required>
        <a-select v-model:value="node.data.providerId" @change="onLlmProviderChange">
          <a-select-option v-for="p in providers" :key="p.id" :value="p.id">{{ p.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="模型" required>
        <a-select v-model:value="node.data.modelId" :disabled="!node.data.providerId" @change="onLlmModelChange">
          <a-select-option v-for="m in llmModelList" :key="m.modelId" :value="m.modelId">{{ m.name || m.modelId }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="提取指令">
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="补充提取规则说明" @change="emitSync" />
      </a-form-item>
      <a-divider>提取参数定义</a-divider>
      <div v-for="(p, idx) in node.data.extractParams" :key="idx" class="extract-param-row">
        <a-input v-model:value="p.key" placeholder="参数 key" @change="emitSync" />
        <a-input v-model:value="p.desc" placeholder="描述" @change="emitSync" />
        <a-switch v-model:checked="p.required" checked-children="必填" un-checked-children="可选" @change="emitSync" />
        <a-button type="text" danger @click="removeExtractParam(idx)"><DeleteOutlined /></a-button>
      </div>
      <a-button type="dashed" block size="small" @click="addExtractParam"><PlusOutlined /> 添加参数</a-button>
      <ShortMemoryForm v-model="node.data.short_memory" @update:model-value="emitSync" />
    </template>

    <!-- 应用组件 -->
    <template v-if="node.type === 'app_component'">
      <a-form-item label="组件类型">
        <a-select v-model:value="node.data.componentType" @change="emitSync">
          <a-select-option value="workflow">工作流组件</a-select-option>
          <a-select-option value="agent">智能体组件</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="组件标识" required>
        <a-input v-model:value="node.data.componentCode" placeholder="已发布组件的 code" @change="emitSync" />
      </a-form-item>
      <a-form-item label="组件名称">
        <a-input v-model:value="node.data.componentName" placeholder="显示名称" @change="emitSync" />
      </a-form-item>
      <a-form-item label="流式输出">
        <a-switch v-model:checked="node.data.streamSwitch" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- API -->
    <template v-if="node.type === 'api'">
      <a-form-item label="URL" required><a-input v-model:value="node.data.url" @change="emitSync" /></a-form-item>
      <a-form-item label="Method">
        <a-select v-model:value="node.data.method" @change="emitSync">
          <a-select-option value="GET">GET</a-select-option>
          <a-select-option value="POST">POST</a-select-option>
          <a-select-option value="PUT">PUT</a-select-option>
          <a-select-option value="DELETE">DELETE</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="Headers (JSON)"><a-textarea v-model:value="node.data.headers" :rows="2" @change="emitSync" /></a-form-item>
      <a-form-item label="Body (JSON)"><a-textarea v-model:value="node.data.body" :rows="3" @change="emitSync" /></a-form-item>
      <a-form-item label="超时(秒)"><a-input-number v-model:value="node.data.timeout" :min="1" :max="120" @change="emitSync" /></a-form-item>
    </template>

    <!-- 循环 -->
    <template v-if="node.type === 'loop'">
      <a-form-item label="循环类型">
        <a-select v-model:value="node.data.iteratorType" @change="emitSync">
          <a-select-option value="byArray">按数组循环</a-select-option>
          <a-select-option value="byCount">按次数循环</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="node.data.iteratorType === 'byArray'" label="循环数组变量">
        <a-input v-model:value="node.data.arrayVariable" placeholder="{{items}}" @change="emitSync" />
      </a-form-item>
      <a-form-item v-else label="循环次数">
        <a-input-number v-model:value="node.data.countLimit" :min="1" :max="100" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- 批处理 -->
    <template v-if="node.type === 'batch'">
      <a-form-item label="批处理数组">
        <a-input v-model:value="node.data.arrayVariable" placeholder="{{input}}" @change="emitSync" />
      </a-form-item>
      <a-form-item label="批处理上限">
        <a-input-number v-model:value="node.data.batchSize" :min="1" :max="100" @change="emitSync" />
      </a-form-item>
      <a-form-item label="并行数量">
        <a-input-number v-model:value="node.data.concurrentSize" :min="1" :max="20" @change="emitSync" />
      </a-form-item>
      <a-form-item label="错误策略">
        <a-select v-model:value="node.data.errorStrategy" @change="emitSync">
          <a-select-option value="terminated">遇错终止</a-select-option>
          <a-select-option value="continueOnError">跳过继续</a-select-option>
          <a-select-option value="removeErrorOutput">移除错误输出</a-select-option>
        </a-select>
      </a-form-item>
    </template>

    <!-- 变量 -->
    <template v-if="node.type === 'variable'">
      <a-form-item label="变量名"><a-input v-model:value="node.data.variableName" @change="emitSync" /></a-form-item>
      <a-form-item label="变量值"><a-input v-model:value="node.data.variableValue" @change="emitSync" /></a-form-item>
    </template>

    <!-- 脚本 / MCP -->
    <template v-if="node.type === 'script'">
      <a-form-item label="脚本内容"><a-textarea v-model:value="node.data.scriptContent" :rows="8" @change="emitSync" /></a-form-item>
    </template>
    <template v-if="node.type === 'mcp'">
      <a-form-item label="MCP 服务"><a-input v-model:value="node.data.mcpServerName" @change="emitSync" /></a-form-item>
      <a-form-item label="工具名称"><a-input v-model:value="node.data.toolName" @change="emitSync" /></a-form-item>
      <a-form-item label="输入参数 JSON"><a-textarea v-model:value="node.data.inputParams" :rows="4" @change="emitSync" /></a-form-item>
    </template>
  </a-form>
</template>

<script setup>
import { computed } from 'vue'
import { DeleteOutlined, PlusOutlined, BookOutlined, ToolOutlined } from '@ant-design/icons-vue'
import ShortMemoryForm from './ShortMemoryForm.vue'
import { createConditionId } from '../nodeMeta'

const props = defineProps({
  node: { type: Object, required: true },
  providers: { type: Array, default: () => [] },
  llmModelList: { type: Array, default: () => [] },
  knowledgeList: { type: Array, default: () => [] },
  tools: { type: Array, default: () => [] },
  targetNodes: { type: Array, default: () => [] },
  filterKnowledgeOption: { type: Function, default: () => true },
  filterToolOption: { type: Function, default: () => true },
  getToolTypeLabel: { type: Function, default: () => '' }
})

const emit = defineEmits([
  'sync',
  'llm-provider-change',
  'llm-model-change',
  'knowledge-change',
  'tool-change'
])

const displayTopK = computed(() => {
  const d = props.node.data
  if (d.overrideConfig) return d.topK
  return d.knowledgeBaseTopK ?? d.topK ?? 5
})

const displayThreshold = computed(() => {
  const d = props.node.data
  if (d.overrideConfig) return d.threshold
  return d.knowledgeBaseThreshold ?? d.threshold ?? 0.5
})

const groupVariables = computed(() => {
  const groups = props.node.data.groups
  if (!groups?.length) return []
  return groups[0]?.variables || []
})

function emitSync() {
  emit('sync')
}

function onLlmProviderChange(v) {
  emit('llm-provider-change', v)
}

function onLlmModelChange(v) {
  emit('llm-model-change', v)
}

function onKnowledgeChange(v) {
  emit('knowledge-change', v)
}

function onToolChange(v) {
  emit('tool-change', v)
}

function onOverrideToggle(checked) {
  if (!checked && props.node.data.knowledgeBaseTopK != null) {
    props.node.data.topK = props.node.data.knowledgeBaseTopK
    props.node.data.threshold = props.node.data.knowledgeBaseThreshold
  }
  emitSync()
}

function addIntent() {
  if (!props.node.data.conditions) props.node.data.conditions = []
  if (props.node.data.conditions.length >= 10) return
  props.node.data.conditions.push({ id: createConditionId(), subject: '' })
  emitSync()
}

function removeIntent(idx) {
  props.node.data.conditions.splice(idx, 1)
  emitSync()
}

function addBranch() {
  if (!props.node.data.branches) props.node.data.branches = []
  props.node.data.branches.push({ condition: '', targetNodeId: '' })
  emitSync()
}

function removeBranch(idx) {
  props.node.data.branches.splice(idx, 1)
  emitSync()
}

function addOutputParam() {
  if (!props.node.data.outputParams) props.node.data.outputParams = []
  props.node.data.outputParams.push({ key: '', type: 'String', defaultValue: '' })
  emitSync()
}

function removeOutputParam(idx) {
  props.node.data.outputParams.splice(idx, 1)
  emitSync()
}

function addExtractParam() {
  if (!props.node.data.extractParams) props.node.data.extractParams = []
  props.node.data.extractParams.push({ key: '', type: 'String', required: true, desc: '' })
  emitSync()
}

function removeExtractParam(idx) {
  props.node.data.extractParams.splice(idx, 1)
  emitSync()
}

function addGroupVar() {
  if (!props.node.data.groups?.length) {
    props.node.data.groups = [{ variables: [] }]
  }
  if (!props.node.data.groups[0].variables) {
    props.node.data.groups[0].variables = []
  }
  props.node.data.groups[0].variables.push({ value: '' })
  emitSync()
}

function removeGroupVar(idx) {
  if (props.node.data.groups?.[0]?.variables) {
    props.node.data.groups[0].variables.splice(idx, 1)
  }
  emitSync()
}
</script>

<style scoped>
.field-hint { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.intent-item { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 8px; }
.intent-item .ant-input { flex: 1; }
.branch-item { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; padding: 8px; background: #f9fafb; border-radius: 6px; }
.output-desc { font-size: 12px; color: #6b7280; padding: 8px; background: #f3f4f6; border-radius: 6px; }
.node-id-display { font-size: 12px; color: #6b7280; }

.resource-option { display: flex; flex-direction: column; gap: 4px; padding: 2px 0; }
.resource-option-header { display: flex; align-items: center; gap: 8px; }
.resource-option-icon { font-size: 14px; flex-shrink: 0; }
.resource-option-icon.knowledge { color: #4f46e5; }
.resource-option-icon.tool { color: #059669; }
.resource-option-title { font-weight: 600; font-size: 13px; color: #1f2937; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-option-desc { font-size: 12px; color: #6b7280; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.resource-option-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: #9ca3af; }
.resource-tag { flex-shrink: 0; font-size: 11px; padding: 0 6px; border-radius: 4px; background: #ecfdf5; color: #059669; }

.kb-config-card {
  margin-bottom: 16px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.kb-config-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.kb-config-title { font-weight: 600; font-size: 13px; color: #334155; }
.kb-config-source { font-size: 11px; color: #94a3b8; }
.kb-config-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.kb-config-field label { display: block; font-size: 12px; color: #64748b; margin-bottom: 4px; }
.kb-config-field :deep(.ant-input-number) { width: 100%; }
.kb-config-readonly-hint { margin-top: 10px; font-size: 11px; color: #94a3b8; }

.param-row, .extract-param-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.param-row .ant-input, .extract-param-row .ant-input { flex: 1; min-width: 80px; }
</style>
