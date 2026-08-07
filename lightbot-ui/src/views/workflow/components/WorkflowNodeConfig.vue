<template>
  <div :class="readonlyRootClass">
  <div v-if="readonly && !readonlyScrollable" class="readonly-overlay" aria-hidden="true" />
  <div v-if="readonly && readonlyScrollable" class="readonly-overlay readonly-overlay--scrollable" aria-hidden="true" />
  <a-form
    layout="vertical"
    :disabled="readonly && !readonlyScrollable"
    class="workflow-node-config-form"
    @keydown.capture="onReadonlyViewEvent"
    @beforeinput.capture="onReadonlyViewEvent"
    @paste.capture="onReadonlyViewEvent"
    @cut.capture="onReadonlyViewEvent"
  >
    <a-form-item label="节点 ID">
      <span class="node-id-display mono">{{ node.id }}</span>
    </a-form-item>
    <a-form-item>
      <template #label>
        <ConfigFieldLabel label="节点名称" :tip="hint('common', 'label')" />
      </template>
      <a-input v-model:value="node.data.label" placeholder="请输入节点名称（不超过 50 字）" :maxlength="50" show-count @change="emitSync" />
    </a-form-item>

    <a-form-item v-if="showBuiltinVars" class="builtin-vars-form-item">
      <template #label>
        <ConfigFieldLabel label="内置变量" tip="点击复制变量占位符，粘贴到输入框或脚本中" />
      </template>
      <div class="builtin-vars-inline">
        <button
          v-for="v in BUILTIN_VARIABLES"
          :key="v.key"
          type="button"
          class="builtin-var-tag"
          :disabled="readonly"
          @click="copyBuiltinVar(v.example)"
        >
          <code>{{ v.example }}</code>
          <CopyOutlined class="copy-icon" />
        </button>
      </div>
    </a-form-item>

    <!-- LLM -->
    <template v-if="node.type === 'llm'">
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="模型" :tip="hint('llm', 'modelId')" />
        </template>
        <ConfigReadonlyValue v-if="readonly" :text="modelDisplayText" />
        <ModelSelect
          v-else
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          @change="onModelChange"
        />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="系统提示词" :tip="hint('llm', 'sysPrompt')" />
        </template>
        <a-textarea v-model:value="node.data.sysPrompt" :rows="2" placeholder="定义 AI 角色、行为约束（不超过 5000 字）" :maxlength="5000" show-count @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="用户提示词模板" :tip="hint('llm', 'promptTemplate')" />
        </template>
        <a-textarea
          v-model:value="node.data.promptTemplate"
          placeholder="{{query}} 或 {{history_list}}"
          :rows="4"
          :maxlength="5000"
          show-count
          @change="emitSync"
        />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="温度" :tip="hint('llm', 'temperature')" />
        </template>
        <a-slider v-model:value="node.data.temperature" :min="0" :max="2" :step="0.1" @change="emitSync" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="流式输出" :tip="hint('llm', 'enableStreaming')" />
        </template>
        <a-switch v-model:checked="node.data.enableStreaming" @change="emitSync" />
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" :disabled="readonly" @update:model-value="emitSync" />
    </template>

    <!-- 意图分类 -->
    <template v-if="node.type === 'classifier'">
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="输入变量" :tip="hint('classifier', 'inputVariable')" />
        </template>
        <VariablePickerInput v-model="node.data.inputVariable" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{query}}" :disabled="readonly" @change="emitSync" />
      </a-form-item>
      <a-form-item label="模型" required>
        <ConfigReadonlyValue v-if="readonly" :text="modelDisplayText" />
        <ModelSelect
          v-else
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          @change="onModelChange"
        />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="意图分类" />
        </template>
        <div v-for="(cond, idx) in node.data.conditions" :key="cond.id" class="intent-item">
          <a-textarea
            v-model:value="cond.subject"
            placeholder="描述该意图，如：用户咨询产品价格"
            :rows="2"
            :maxlength="100"
            show-count
            @change="emitSync"
          />
          <a-button type="text" danger size="small" @click="removeIntent(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" @click="addIntent">
          <PlusOutlined /> 添加意图（{{ node.data.conditions?.length || 0 }}/10）
        </a-button>
      </a-form-item>
      <a-form-item label="其他意图">
        <div class="field-hint">当所有意图均不匹配时，走「其他意图」出口分支</div>
      </a-form-item>
      <a-form-item label="思考模式">
        <a-select v-model:value="node.data.mode_switch" @change="emitSync">
          <a-select-option value="efficient">快速模式 — 避免输出思考过程，速度更快</a-select-option>
          <a-select-option value="advanced">效果模式 — 逐步思考，匹配更精准</a-select-option>
        </a-select>
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" :disabled="readonly" @update:model-value="emitSync" />
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="提示词（额外约束）" />
        </template>
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="为意图识别提供额外要求（不超过 2000 字）" :maxlength="2000" show-count @change="emitSync" />
      </a-form-item>
      <a-form-item label="输出">
        <div class="output-desc">subject（命中主题）、thought（思考过程，效果模式下输出）</div>
      </a-form-item>
    </template>

    <!-- 条件 -->
    <template v-if="node.type === 'condition'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="条件组" :tip="hint('condition', 'conditionGroups')" />
        </template>
        <ConditionGroupForm
          v-model="node.data.conditionGroups"
          :disabled="readonly"
          @change="onConditionGroupsChange"
          @remove-group="onRemoveConditionGroup"
        />
      </a-form-item>
    </template>

    <!-- 知识检索 -->
    <template v-if="node.type === 'retrieval'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输入变量" :tip="hint('retrieval', 'inputVariable')" />
        </template>
        <VariablePickerInput v-model="node.data.inputVariable" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{query}}" :disabled="readonly" @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="知识库" :tip="hint('retrieval', 'knowledgeId')" />
        </template>
        <ConfigReadonlyValue v-if="readonly" :text="knowledgeDisplayText" />
        <a-select
          v-else
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
                <EntitySelectOption type="knowledge" :name="k.name" />
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

      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="覆盖知识库配置" />
        </template>
        <a-switch v-model:checked="node.data.overrideConfig" @change="onOverrideToggle" />
        <div class="field-hint">关闭时沿用知识库默认 TopK / 阈值；开启后可在此节点单独调整</div>
      </a-form-item>
    </template>

    <!-- 工具 -->
    <template v-if="node.type === 'tool'">
      <a-form-item label="工具" required>
        <ConfigReadonlyValue v-if="readonly" :text="toolDisplayText" />
        <a-select
          v-else
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
            :key="String(t.id)"
            :value="String(t.id)"
            :label="t.displayName || t.name"
          >
            <EntitySelectOption type="tool" :name="t.displayName || t.name" :icon="t.icon" :tag="getToolTypeLabel(t.toolType)" :desc="t.description" />
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输入参数映射" :tip="hint('tool', 'inputMappings')" />
        </template>
        <div v-for="(row, idx) in toolInputMappings" :key="'tool-in-' + idx" class="param-row">
          <a-input v-model:value="row.key" placeholder="工具参数名（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="row.value" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{query}}" :max-length="500" :disabled="readonly" @update:model-value="emitSync" />
          <a-button v-if="!readonly" type="text" danger @click="removeToolInputMapping(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button v-if="!readonly" type="dashed" block size="small" class="param-add-btn" @click="addToolInputMapping">
          <PlusOutlined /> 添加入参
        </a-button>
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输出参数映射" :tip="hint('tool', 'outputMappings')" />
        </template>
        <div v-for="(row, idx) in toolOutputMappings" :key="'tool-out-' + idx" class="param-row">
          <a-input v-model:value="row.key" placeholder="写入流程变量名（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="row.value" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{answer}}" :max-length="500" :disabled="readonly" @update:model-value="emitSync" />
          <a-button v-if="!readonly" type="text" danger @click="removeToolOutputMapping(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button v-if="!readonly" type="dashed" block size="small" class="param-add-btn" @click="addToolOutputMapping">
          <PlusOutlined /> 添加出参
        </a-button>
      </a-form-item>
    </template>

    <!-- 流程输入 -->
    <template v-if="node.type === 'input'">
      <div class="config-section">
        <div class="config-section-title">输出参数</div>
        <div v-for="(param, idx) in node.data.outputParams" :key="(param.key || 'param') + '-' + idx" class="param-row">
          <a-input v-model:value="param.key" placeholder="参数名（不超过 64 字）" :maxlength="64" @change="emitSync" />
          <a-select v-model:value="param.type" style="width: 100px" @change="emitSync">
            <a-select-option value="String">String</a-select-option>
            <a-select-option value="Number">Number</a-select-option>
            <a-select-option value="Boolean">Boolean</a-select-option>
          </a-select>
          <a-input v-model:value="param.defaultValue" placeholder="默认值（不超过 500 字）" :maxlength="500" @change="emitSync" />
          <a-button type="text" danger @click="removeOutputParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" @click="addOutputParam">
          <PlusOutlined /> 添加参数
        </a-button>
      </div>
    </template>

    <!-- 人工确认 -->
    <template v-if="node.type === 'confirm'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="确认提示语" :tip="hint('confirm', 'message')" />
        </template>
        <a-textarea v-model:value="node.data.message" :rows="2" placeholder="展示给操作者的说明文字（不超过 5000 字）" :maxlength="5000" show-count @change="emitSync" />
      </a-form-item>
      <div class="config-section">
        <div class="config-section-title">
          <ConfigFieldLabel label="确认表单字段" :tip="hint('confirm', 'formFields')" />
        </div>
        <WorkflowConfirmFieldsEditor
          :fields="node.data.formFields || (node.data.formFields = [])"
          @change="emitSync"
        />
      </div>
    </template>

    <!-- 流程输出 -->
    <template v-if="node.type === 'output'">
      <a-form-item label="输出内容" required>
        <a-textarea v-model:value="node.data.output" :rows="4" placeholder="{{input}} 或 {{llmOutput}}（不超过 5000 字）" :maxlength="5000" show-count @change="emitSync" />
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
        <a-textarea v-model:value="node.data.templateContent" :rows="4" placeholder="支持 {{变量名}}（不超过 5000 字）" :maxlength="5000" show-count @change="emitSync" />
      </a-form-item>
      <template v-if="node.data.handleType === 'group'">
        <a-form-item label="分组策略">
          <a-select v-model:value="node.data.groupStrategy" @change="emitSync">
            <a-select-option value="firstNotNull">取第一个非空</a-select-option>
            <a-select-option value="lastNotNull">取最后一个非空</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变量列表">
          <div v-for="(v, idx) in groupVariables" :key="(v.value || 'var') + '-' + idx" class="param-row">
            <a-input v-model:value="v.value" placeholder="{{变量引用}}（不超过 500 字）" :maxlength="500" @change="emitSync" />
            <a-button type="text" danger @click="removeGroupVar(idx)"><DeleteOutlined /></a-button>
          </div>
          <a-button type="dashed" block size="small" class="param-add-btn" @click="addGroupVar"><PlusOutlined /> 添加变量</a-button>
        </a-form-item>
      </template>
    </template>

    <!-- 参数提取 -->
    <template v-if="node.type === 'parameter_extractor'">
      <a-form-item label="输入变量" required>
        <a-input v-model:value="node.data.inputVariable" placeholder="{{input}}（不超过 500 字）" :maxlength="500" @change="emitSync" />
      </a-form-item>
      <a-form-item label="模型" required>
        <ConfigReadonlyValue v-if="readonly" :text="modelDisplayText" />
        <ModelSelect
          v-else
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          @change="onModelChange"
        />
      </a-form-item>
      <a-form-item label="提取指令">
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="补充提取规则说明（不超过 2000 字）" :maxlength="2000" show-count @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="提取参数定义" />
        </template>
        <div v-for="(p, idx) in node.data.extractParams" :key="(p.key || 'extract') + '-' + idx" class="extract-param-row">
          <a-input v-model:value="p.key" placeholder="参数 key（不超过 64 字）" :maxlength="64" @change="emitSync" />
          <a-input v-model:value="p.desc" placeholder="描述（不超过 200 字）" :maxlength="200" @change="emitSync" />
          <a-switch v-model:checked="p.required" checked-children="必填" un-checked-children="可选" @change="emitSync" />
          <a-button type="text" danger @click="removeExtractParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" @click="addExtractParam"><PlusOutlined /> 添加参数</a-button>
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" :disabled="readonly" @update:model-value="emitSync" />
    </template>

    <!-- 应用组件 / 子工作流 -->
    <template v-if="node.type === 'app_component'">
      <a-form-item label="组件类型">
        <a-select v-model:value="node.data.componentType" :disabled="readonly" @change="emitSync">
          <a-select-option value="workflow">工作流组件</a-select-option>
          <a-select-option value="agent" disabled>智能体组件（暂未支持）</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="子工作流" required>
        <ConfigReadonlyValue v-if="readonly" :text="subWorkflowDisplayText" />
        <a-select
          v-else
          v-model:value="node.data.componentCode"
          show-search
          allow-clear
          placeholder="选择已发布的 Workflow Agent"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterWorkflowAgentOption"
          :loading="subWorkflowAgentsLoading"
          @change="onSubWorkflowAgentChange"
        >
          <a-select-option
            v-for="agent in publishedWorkflowAgents"
            :key="agent.id"
            :value="String(agent.id)"
            :label="agent.name"
          >
            <div class="resource-option">
              <div class="resource-option-header">
                <EntitySelectOption
                  type="workflow"
                  :name="agent.name"
                  :avatar-url="agent.avatar"
                  :tag="agent.version ? `v${agent.version}` : ''"
                />
              </div>
              <div v-if="agent.description" class="resource-option-desc">{{ agent.description }}</div>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="node.data.componentName" label="显示名称">
        <ConfigReadonlyValue v-if="readonly" :value="node.data.componentName" />
        <a-input v-else v-model:value="node.data.componentName" placeholder="自动填充" @change="emitSync" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输入参数映射" :tip="hint('app_component', 'inputMappings')" />
        </template>
        <div v-for="(row, idx) in appComponentInputMappings" :key="'sub-in-' + idx" class="param-row">
          <a-input v-model:value="row.key" placeholder="参数名（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="row.value" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{query}}" :max-length="500" :disabled="readonly" @update:model-value="emitSync" />
          <a-button v-if="!readonly" type="text" danger @click="removeAppInputMapping(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button v-if="!readonly" type="dashed" block size="small" class="param-add-btn" @click="addAppInputMapping">
          <PlusOutlined /> 添加入参
        </a-button>
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输出参数映射" :tip="hint('app_component', 'outputMappings')" />
        </template>
        <div v-for="(row, idx) in appComponentOutputMappings" :key="'sub-out-' + idx" class="param-row">
          <a-input v-model:value="row.key" placeholder="写入父流程变量名（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="row.value" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{result}}" :max-length="500" :disabled="readonly" @update:model-value="emitSync" />
          <a-button v-if="!readonly" type="text" danger @click="removeAppOutputMapping(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button v-if="!readonly" type="dashed" block size="small" class="param-add-btn" @click="addAppOutputMapping">
          <PlusOutlined /> 添加出参
        </a-button>
      </a-form-item>
      <a-form-item label="流式输出">
        <a-switch v-model:checked="node.data.streamSwitch" :disabled="readonly" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- SubAgent -->
    <template v-if="node.type === 'sub_agent'">
      <a-form-item label="SubAgent" required>
        <ConfigReadonlyValue v-if="readonly" :text="subAgentDisplayText" />
        <a-select
          v-else
          v-model:value="node.data.subAgentId"
          show-search
          allow-clear
          placeholder="选择已启用的 SubAgent"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterSubAgentOption"
          :loading="subAgentsLoading"
          @change="onSubAgentChange"
        >
          <a-select-option
            v-for="sa in enabledSubAgents"
            :key="String(sa.id)"
            :value="String(sa.id)"
            :label="sa.displayName || sa.name"
          >
            <EntitySelectOption
              type="subagent"
              :name="sa.displayName || sa.name"
              :icon="sa.icon"
              :tag="sa.name"
              :desc="sa.description"
            />
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="任务描述" :tip="hint('sub_agent', 'task')" />
        </template>
        <VariablePickerInput
          v-model="node.data.task"
          :node-id="node.id"
          :nodes="nodes"
          :edges="edges"
          placeholder="请处理：{{query}}"
          :disabled="readonly"
          @update:model-value="emitSync"
        />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输出参数映射" :tip="hint('sub_agent', 'outputMappings')" />
        </template>
        <div v-for="(row, idx) in subAgentOutputMappings" :key="'sa-out-' + idx" class="param-row">
          <a-input v-model:value="row.key" placeholder="写入流程变量名" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="row.value" :node-id="node.id" :nodes="nodes" :edges="edges" placeholder="{{reply}}" :disabled="readonly" @update:model-value="emitSync" />
          <a-button v-if="!readonly" type="text" danger @click="removeSubAgentOutputMapping(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button v-if="!readonly" type="dashed" block size="small" class="param-add-btn" @click="addSubAgentOutputMapping">
          <PlusOutlined /> 添加出参
        </a-button>
      </a-form-item>
      <a-form-item label="流式输出">
        <a-switch v-model:checked="node.data.streamSwitch" :disabled="readonly" @change="emitSync" />
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
      <a-form-item label="Headers (JSON)">
        <JsonInput v-model="node.data.headers" :rows="3" :max-length="16000" :readonly="scrollableReadonly" placeholder='{"Content-Type":"application/json"}' @update:model-value="emitSync" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="Body (JSON)" />
        </template>
        <JsonInput v-model="node.data.body" :rows="4" :max-length="16000" :readonly="scrollableReadonly" placeholder='{"key":"value"}' @update:model-value="emitSync" />
      </a-form-item>
    </template>

    <!-- 循环（对齐 spring-ai-alibaba-admin Iterator） -->
    <template v-if="node.type === 'loop'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="循环类型" :tip="hint('loop', 'iteratorType')" />
        </template>
        <a-select :value="loopIteratorType" @change="onLoopIteratorTypeChange">
          <a-select-option value="byArray">按数组循环</a-select-option>
          <a-select-option value="byCount">按次数循环</a-select-option>
        </a-select>
      </a-form-item>
      <template v-if="loopIteratorType === 'byArray'">
        <a-form-item>
          <template #label>
            <ConfigFieldLabel label="循环数组" :tip="hint('loop', 'arrayVariable')" />
          </template>
          <VariablePickerInput
            :model-value="loopArrayVariable"
            :node-id="node.id"
            :nodes="nodes"
            :edges="edges"
            placeholder="{{input}} 或数组变量"
            @update:model-value="onLoopArrayVariableChange"
          />
        </a-form-item>
      </template>
      <a-form-item v-else>
        <template #label>
          <ConfigFieldLabel label="循环次数上限" :tip="hint('loop', 'countLimit')" />
        </template>
        <a-input-number :value="loopCountLimit" :min="1" :max="500" @change="onLoopCountLimitChange" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输出变量" :tip="hint('loop', 'outputParams')" />
        </template>
        <div v-for="(p, idx) in node.data.output_params || node.data.outputParams || []" :key="'loop-out-' + idx" class="param-row">
          <a-input v-model:value="p.key" placeholder="变量名（不超过 64 字）" :maxlength="64" @change="syncLoopOutputParams" />
          <a-select v-model:value="p.type" style="width: 100px" @change="syncLoopOutputParams">
            <a-select-option value="String">String</a-select-option>
            <a-select-option value="Object">Object</a-select-option>
            <a-select-option value="Array">Array</a-select-option>
          </a-select>
          <a-button type="text" danger :disabled="readonly" @click="removeLoopOutputParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block class="param-add-btn" :disabled="readonly" @click="addLoopOutputParam">+ 添加输出</a-button>
      </a-form-item>
    </template>

    <!-- 批处理（对齐 spring-ai-alibaba-admin Parallel） -->
    <template v-if="node.type === 'batch'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="批处理数组" :tip="hint('batch', 'arrayVariable')" />
        </template>
        <VariablePickerInput
          :model-value="batchArrayVariable"
          :node-id="node.id"
          :nodes="nodes"
          :edges="edges"
          placeholder="{{input}} 或 Array 类型变量"
          @update:model-value="onBatchArrayVariableChange"
        />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="批处理上限" :tip="hint('batch', 'batchSize')" />
        </template>
        <a-input-number :value="batchSizeVal" :min="1" :max="500" @change="onBatchSizeChange" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="并行数量" :tip="hint('batch', 'concurrentSize')" />
        </template>
        <a-input-number :value="batchConcurrentVal" :min="1" :max="50" @change="onBatchConcurrentChange" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="错误响应方法" :tip="hint('batch', 'errorStrategy')" />
        </template>
        <a-select :value="batchErrorStrategy" @change="onBatchErrorStrategyChange">
          <a-select-option value="terminated">错误时终止</a-select-option>
          <a-select-option value="continueOnError">忽略错误并继续</a-select-option>
          <a-select-option value="removeErrorOutput">移除错误输出</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输出变量" :tip="hint('batch', 'outputParams')" />
        </template>
        <div v-for="(p, idx) in node.data.output_params || node.data.outputParams || []" :key="'batch-out-' + idx" class="param-row">
          <a-input v-model:value="p.key" placeholder="变量名（不超过 64 字）" :maxlength="64" @change="syncBatchOutputParams" />
          <a-select v-model:value="p.type" style="width: 100px" @change="syncBatchOutputParams">
            <a-select-option value="Array">Array</a-select-option>
            <a-select-option value="Object">Object</a-select-option>
            <a-select-option value="String">String</a-select-option>
          </a-select>
          <a-button type="text" danger :disabled="readonly" @click="removeBatchOutputParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block class="param-add-btn" :disabled="readonly" @click="addBatchOutputParam">+ 添加输出</a-button>
      </a-form-item>
    </template>

    <!-- 变量 -->
    <template v-if="node.type === 'variable'">
      <a-form-item label="变量名"><a-input v-model:value="node.data.variableName" @change="emitSync" /></a-form-item>
      <a-form-item label="变量值"><a-input v-model:value="node.data.variableValue" @change="emitSync" /></a-form-item>
    </template>

    <!-- 脚本 -->
    <template v-if="node.type === 'script'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="脚本代码" :tip="hint('script', 'scriptContent')" />
        </template>
        <CodeEditor
          v-model="node.data.scriptContent"
          :language="node.data.scriptLanguage"
          :disabled="readonly && !readonlyScrollable"
          :read-only="scrollableReadonly"
          :rows="14"
          fullscreen-title="脚本编辑"
          @update:language="onScriptLanguageChange"
          @change="emitSync"
        />
      </a-form-item>
      <div class="config-section">
        <div class="config-section-title">
          <ConfigFieldLabel label="输入变量" :tip="hint('script', 'inputParams')" />
        </div>
        <div v-for="(p, idx) in node.data.inputParams" :key="'in-' + idx" class="param-row">
          <a-input v-model:value="p.key" placeholder="参数名（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="p.value" :node-id="node.id" :nodes="nodes" :edges="edges" :max-length="500" :disabled="readonly" @change="emitSync" />
          <a-button type="text" danger :disabled="readonly" @click="removeScriptInput(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" :disabled="readonly" @click="addScriptInput">
          <PlusOutlined /> 添加入参
        </a-button>
      </div>
      <div class="config-section">
        <div class="config-section-title">
          <ConfigFieldLabel label="输出变量" :tip="hint('script', 'outputParams')" />
        </div>
        <div v-for="(p, idx) in node.data.outputParams" :key="'out-' + idx" class="param-row">
          <a-input v-model:value="p.key" placeholder="输出字段（不超过 64 字）" :maxlength="64" :disabled="readonly" @change="emitSync" />
          <a-select v-model:value="p.type" style="width: 100px" :disabled="readonly" @change="emitSync">
            <a-select-option value="String">String</a-select-option>
            <a-select-option value="Number">Number</a-select-option>
            <a-select-option value="Boolean">Boolean</a-select-option>
            <a-select-option value="Object">Object</a-select-option>
          </a-select>
          <a-button type="text" danger :disabled="readonly" @click="removeScriptOutput(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" :disabled="readonly" @click="addScriptOutput">
          <PlusOutlined /> 添加出参
        </a-button>
      </div>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="失败策略" :tip="hint('script', 'errorStrategy')" />
        </template>
        <a-select v-model:value="node.data.errorStrategy" :disabled="readonly" @change="emitSync">
          <a-select-option value="defaultValue">使用默认值继续</a-select-option>
          <a-select-option value="abort">终止流程</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="node.data.errorStrategy === 'defaultValue'">
        <template #label>
          <ConfigFieldLabel label="默认输出 JSON" :tip="hint('script', 'defaultOutput')" />
        </template>
        <JsonInput
          v-model="node.data.defaultOutput"
          :rows="4"
          :max-length="16000"
          :readonly="scrollableReadonly"
          placeholder='{"result":""}'
          @update:model-value="emitSync"
        />
      </a-form-item>
    </template>
    <!-- MCP -->
    <template v-if="node.type === 'mcp'">
      <a-form-item label="MCP 服务" required>
        <ConfigReadonlyValue v-if="readonly" :text="mcpServerDisplayText" />
        <a-select
          v-else
          v-model:value="node.data.mcpServerId"
          show-search
          placeholder="选择 MCP 服务"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterMcpOption"
          :loading="mcpServersLoading"
          @change="onMcpServerChange"
        >
          <a-select-option
            v-for="s in mcpServers"
            :key="s.id"
            :value="s.id"
            :label="s.name"
          >
            <div class="resource-option">
              <div class="resource-option-header">
                <EntitySelectOption type="mcp" :name="s.name" :icon="s.icon" :builtin="s.isBuiltin === 1" :tag="mcpInstallTypeLabel(s)" />
              </div>
              <div v-if="s.description" class="resource-option-desc">{{ s.description }}</div>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="工具" required>
        <div class="mcp-tool-picker">
          <ConfigReadonlyValue v-if="readonly" :text="mcpToolDisplayText" />
          <a-select
            v-else
            v-model:value="node.data.toolName"
            show-search
            placeholder="选择 MCP 工具"
            option-label-prop="label"
            dropdown-class-name="workflow-resource-dropdown"
            :filter-option="filterMcpToolOption"
            :disabled="!node.data.mcpServerId"
            :loading="mcpToolsLoading"
            @change="emitSync"
          >
            <a-select-option
              v-for="t in enabledMcpTools"
              :key="t.name"
              :value="t.name"
              :label="t.name"
            >
              <div class="resource-option">
                <div class="resource-option-header">
                  <EntitySelectOption type="tool" :name="t.name" :tag-muted="t.enabled === false ? '已禁用' : ''" />
                </div>
                <div v-if="t.description" class="resource-option-desc">{{ truncateText(t.description, 50) }}</div>
              </div>
            </a-select-option>
          </a-select>
          <a-button
            class="mcp-refresh-btn"
            :disabled="readonly || !node.data.mcpServerId"
            :loading="mcpToolsRefreshing"
            @click="handleRefreshMcpTools"
          >
            <SyncOutlined /> 刷新工具
          </a-button>
        </div>
        <div v-if="node.data.mcpServerId && !mcpToolsLoading && enabledMcpTools.length === 0" class="mcp-tools-hint">
          暂无可用工具，请先测试 MCP 连接或点击「刷新工具」
        </div>
      </a-form-item>
      <a-form-item label="输入参数 JSON">
        <JsonInput v-model="mcpInputParamsJson" :rows="4" :max-length="16000" :readonly="scrollableReadonly" placeholder='{"chat_id":"oc_xxx","text":"{{query}}"}' />
      </a-form-item>
    </template>

    <NodeResilienceConfig
      v-if="supportsNodeResilience(node.type)"
      :node-id="node.id"
      :node-type="node.type"
      :node-data="node.data"
      :readonly="readonly"
      @change="emitSync"
    />
  </a-form>
  </div>
</template>

<script setup>
import { computed, watch, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { copyToClipboard } from '../../../utils/clipboard'
import { DeleteOutlined, PlusOutlined, CopyOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { getAgents } from '../../../api/agent'
import { getToolIoSchema } from '../../../api/tool'
import { getWorkflowIoSchema } from '../../../api/workflow'
import { getEnabledSubAgents } from '../../../api/subagent'
import { getMcpServers, getMcpServerTools, refreshMcpServerTools } from '../../../api/mcp'
import { getProvidersWithModels } from '../../../api/modelProvider'
import ShortMemoryForm from './ShortMemoryForm.vue'
import ConfigFieldLabel from './ConfigFieldLabel.vue'
import VariablePickerInput from './VariablePickerInput.vue'
import CodeEditor from './CodeEditor.vue'
import ConditionGroupForm from './ConditionGroupForm.vue'
import JsonInput from '../../../components/JsonInput.vue'
import ModelSelect from '../../../components/ModelSelect.vue'
import EntitySelectOption from '../../../components/EntitySelectOption.vue'
import ConfigReadonlyValue from './ConfigReadonlyValue.vue'
import WorkflowConfirmFieldsEditor from '../../../components/workflow/confirm/WorkflowConfirmFieldsEditor.vue'
import {
  formatModelDisplay,
  resolveKnowledgeName,
  resolveToolName,
  resolveMcpServerName,
  resolveMcpToolName,
  resolveSubWorkflowName,
  resolveSubAgentName,
} from '../workflowNodeDisplayLabels.js'
import { getToolTypeLabel } from '../../../utils/bindingTheme'
import { createConditionId } from '../nodeMeta'
import { BUILTIN_VARIABLES, getFieldHint, getScriptExampleConfig } from '../nodeConfigMeta'
import { supportsNodeResilience } from '../nodeResilienceMeta.js'
import { truncateText } from '../../../utils/format'
import { syncConditionBranches, ensureConditionGroups, conditionGroupHandleId } from '../conditionUtils'
import NodeResilienceConfig from './NodeResilienceConfig.vue'

const props = defineProps({
  node: { type: Object, required: true },
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
  /** 只读但允许滚动查看长文本（可观测性 trace 等） */
  readonlyScrollable: { type: Boolean, default: false },
  knowledgeList: { type: Array, default: () => [] },
  tools: { type: Array, default: () => [] },
  targetNodes: { type: Array, default: () => [] },
  filterKnowledgeOption: { type: Function, default: () => true },
  filterToolOption: { type: Function, default: () => true },
  getToolTypeLabel: { type: Function, default: () => '' }
})

const emit = defineEmits([
  'sync',
  'knowledge-change',
  'tool-change',
  'remove-source-handles',
])

const route = useRoute()
const publishedWorkflowAgents = ref([])
const subWorkflowAgentsLoading = ref(false)
const enabledSubAgents = ref([])
const subAgentsLoading = ref(false)

function ensureAppComponentMappings() {
  if (props.node?.type !== 'app_component' || !props.node?.data) return
  if (!Array.isArray(props.node.data.inputMappings) || !props.node.data.inputMappings.length) {
    props.node.data.inputMappings = [
      { key: 'query', value: '{{query}}' },
      { key: 'input', value: '{{input}}' },
    ]
  }
  if (!Array.isArray(props.node.data.outputMappings) || !props.node.data.outputMappings.length) {
    props.node.data.outputMappings = [{ key: 'result', value: '{{result}}' }]
  }
}

function ensureToolMappings() {
  if (props.node?.type !== 'tool' || !props.node?.data) return
  if (!Array.isArray(props.node.data.inputMappings)) {
    props.node.data.inputMappings = [{ key: 'query', value: '{{query}}' }]
  }
  if (!Array.isArray(props.node.data.outputMappings)) {
    props.node.data.outputMappings = [{ key: 'toolResult', value: '{{output}}' }]
  }
}

const toolInputMappings = computed(() => {
  ensureToolMappings()
  return props.node?.data?.inputMappings || []
})

const toolOutputMappings = computed(() => {
  ensureToolMappings()
  return props.node?.data?.outputMappings || []
})

function buildDefaultToolInputValue(key, inputDef) {
  if (key === 'query') return '{{query}}'
  if (key === 'input') return '{{input}}'
  if (inputDef?.type === 'Number' && key === 'maxResults') return '5'
  return `{{${key}}}`
}

async function applyToolIoSchema(toolId) {
  if (!toolId || props.node?.type !== 'tool') return
  try {
    const res = await getToolIoSchema(toolId)
    const schema = res.data || {}
    const inputs = schema.inputs || []
    const outputs = schema.outputs || []
    props.node.data.inputMappings = inputs.length
      ? inputs.map(i => ({
          key: i.key,
          value: buildDefaultToolInputValue(i.key, i),
        }))
      : [{ key: 'query', value: '{{query}}' }]
    props.node.data.outputMappings = outputs.length
      ? outputs.map(o => ({
          key: o.key,
          value: `{{${o.key}}}`,
        }))
      : [{ key: 'toolResult', value: '{{output}}' }]
    if (!props.node.data.outputMappings.some(r => r.key === 'toolResult')) {
      props.node.data.outputMappings.push({ key: 'toolResult', value: '{{output}}' })
    }
    emitSync()
  } catch (e) {
    message.warning(e.message || '加载工具参数 Schema 失败')
  }
}

function addToolInputMapping() {
  ensureToolMappings()
  props.node.data.inputMappings.push({ key: '', value: '' })
  emitSync()
}

function removeToolInputMapping(idx) {
  props.node.data.inputMappings.splice(idx, 1)
  emitSync()
}

function addToolOutputMapping() {
  ensureToolMappings()
  props.node.data.outputMappings.push({ key: '', value: '{{output}}' })
  emitSync()
}

function removeToolOutputMapping(idx) {
  props.node.data.outputMappings.splice(idx, 1)
  emitSync()
}

const appComponentInputMappings = computed(() => {
  ensureAppComponentMappings()
  return props.node?.data?.inputMappings || []
})

const appComponentOutputMappings = computed(() => {
  ensureAppComponentMappings()
  return props.node?.data?.outputMappings || []
})

async function loadPublishedWorkflowAgents() {
  subWorkflowAgentsLoading.value = true
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 100, agentType: 'workflow' })
    const records = res.data?.records || res.data || []
    const currentId = String(route.params.agentId || '')
    publishedWorkflowAgents.value = records.filter(
      a => String(a.id) !== currentId && Number(a.version) > 0,
    )
  } catch {
    publishedWorkflowAgents.value = []
  } finally {
    subWorkflowAgentsLoading.value = false
  }
}

function filterWorkflowAgentOption(input, option) {
  const label = option.label ?? option.children ?? ''
  return String(label).toLowerCase().includes(String(input).toLowerCase())
}

async function onSubWorkflowAgentChange(agentId) {
  if (!agentId) {
    props.node.data.componentName = ''
    emitSync()
    return
  }
  const agent = publishedWorkflowAgents.value.find(a => String(a.id) === String(agentId))
  if (agent) {
    props.node.data.componentName = agent.name
  }
  try {
    const res = await getWorkflowIoSchema(agentId)
    const schema = res.data || {}
    props.node.data.inputMappings = (schema.inputs || []).map(i => ({
      key: i.key,
      value: i.key === 'query' ? '{{query}}' : (i.key === 'input' ? '{{input}}' : `{{${i.key}}}`),
    }))
    props.node.data.outputMappings = [{ key: 'result', value: '{{result}}' }]
    emitSync()
  } catch (e) {
    message.warning(e.message || '加载子工作流参数失败')
  }
}

function addAppInputMapping() {
  ensureAppComponentMappings()
  props.node.data.inputMappings.push({ key: '', value: '' })
  emitSync()
}

function removeAppInputMapping(idx) {
  props.node.data.inputMappings.splice(idx, 1)
  emitSync()
}

function addAppOutputMapping() {
  ensureAppComponentMappings()
  props.node.data.outputMappings.push({ key: '', value: '{{result}}' })
  emitSync()
}

function removeAppOutputMapping(idx) {
  props.node.data.outputMappings.splice(idx, 1)
  emitSync()
}

function ensureSubAgentMappings() {
  if (props.node?.type !== 'sub_agent' || !props.node?.data) return
  if (!props.node.data.task) {
    props.node.data.task = '{{query}}'
  }
  if (!Array.isArray(props.node.data.outputMappings) || !props.node.data.outputMappings.length) {
    props.node.data.outputMappings = [{ key: 'reply', value: '{{reply}}' }]
  }
}

const subAgentOutputMappings = computed(() => {
  ensureSubAgentMappings()
  return props.node?.data?.outputMappings || []
})

async function loadEnabledSubAgents() {
  subAgentsLoading.value = true
  try {
    const res = await getEnabledSubAgents()
    enabledSubAgents.value = res.data || []
  } catch {
    enabledSubAgents.value = []
  } finally {
    subAgentsLoading.value = false
  }
}

function filterSubAgentOption(input, option) {
  const label = option.label ?? option.children ?? ''
  return String(label).toLowerCase().includes(String(input).toLowerCase())
}

function onSubAgentChange(subAgentId) {
  if (!subAgentId) {
    props.node.data.subAgentName = ''
    emitSync()
    return
  }
  const sa = enabledSubAgents.value.find(x => String(x.id) === String(subAgentId))
  if (sa) {
    props.node.data.subAgentName = sa.displayName || sa.name || ''
  }
  ensureSubAgentMappings()
  emitSync()
}

function addSubAgentOutputMapping() {
  ensureSubAgentMappings()
  props.node.data.outputMappings.push({ key: '', value: '{{reply}}' })
  emitSync()
}

function removeSubAgentOutputMapping(idx) {
  props.node.data.outputMappings.splice(idx, 1)
  emitSync()
}

const mcpServers = ref([])
const mcpServersLoading = ref(false)
const mcpTools = ref([])
const mcpToolsLoading = ref(false)
const mcpToolsRefreshing = ref(false)
const modelOptions = ref([])
const enabledMcpTools = computed(() =>
  (mcpTools.value || []).filter(t => t.enabled !== false)
)

const modelDisplayText = computed(() => formatModelDisplay(props.node?.data, modelOptions.value))
const knowledgeDisplayText = computed(() => resolveKnowledgeName(props.node?.data, props.knowledgeList))
const toolDisplayText = computed(() => resolveToolName(props.node?.data, props.tools))
const mcpServerDisplayText = computed(() => resolveMcpServerName(props.node?.data, mcpServers.value))
const mcpToolDisplayText = computed(() => resolveMcpToolName(props.node?.data))
const subWorkflowDisplayText = computed(() =>
  resolveSubWorkflowName(props.node?.data, publishedWorkflowAgents.value),
)
const subAgentDisplayText = computed(() =>
  resolveSubAgentName(props.node?.data, enabledSubAgents.value),
)

const scrollableReadonly = computed(() => props.readonly && props.readonlyScrollable)
const readonlyRootClass = computed(() => ({
  'config-readonly': props.readonly && !props.readonlyScrollable,
  'config-readonly-scrollable': scrollableReadonly.value,
}))

const READONLY_VIEW_NAV_KEYS = new Set([
  'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight',
  'PageUp', 'PageDown', 'Home', 'End', 'Tab', 'Escape',
])

function onReadonlyViewEvent(e) {
  if (!scrollableReadonly.value) return
  const tag = e.target?.tagName?.toLowerCase()
  if (tag !== 'textarea' && tag !== 'input') return
  if (e.type === 'keydown') {
    if (READONLY_VIEW_NAV_KEYS.has(e.key)) return
    if ((e.ctrlKey || e.metaKey) && ['a', 'c'].includes(e.key.toLowerCase())) return
    e.preventDefault()
    return
  }
  if (['beforeinput', 'paste', 'cut', 'input'].includes(e.type)) {
    e.preventDefault()
  }
}

const mcpInputParamsJson = computed({
  get() {
    const v = props.node.data?.inputParams
    if (v == null || v === '') return '{}'
    if (typeof v === 'string') return v
    try {
      return JSON.stringify(v, null, 2)
    } catch {
      return '{}'
    }
  },
  set(val) {
    props.node.data.inputParams = val
    emitSync()
  },
})

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

const showBuiltinVars = computed(() => {
  const t = props.node.type
  return ['llm', 'retrieval', 'classifier', 'script', 'output', 'variable', 'parameter_extractor', 'loop', 'batch', 'api'].includes(t)
})

const loopIteratorType = computed(() => props.node.data?.iterator_type || props.node.data?.iteratorType || 'byArray')
const loopArrayVariable = computed(() => {
  const params = props.node.data?.input_params || props.node.data?.inputParams
  if (params?.[0]?.value) return params[0].value
  return props.node.data?.arrayVariable || '{{input}}'
})
const loopCountLimit = computed(() => props.node.data?.count_limit ?? props.node.data?.countLimit ?? 100)
const batchArrayVariable = computed(() => {
  const params = props.node.data?.input_params || props.node.data?.inputParams
  if (params?.[0]?.value) return params[0].value
  return props.node.data?.arrayVariable || '{{input}}'
})
const batchSizeVal = computed(() => props.node.data?.batch_size ?? props.node.data?.batchSize ?? 100)
const batchConcurrentVal = computed(() => props.node.data?.concurrent_size ?? props.node.data?.concurrentSize ?? 5)
const batchErrorStrategy = computed(() => props.node.data?.error_strategy || props.node.data?.errorStrategy || 'continueOnError')

function ensureLoopOutputParams() {
  if (!props.node.data.output_params && !props.node.data.outputParams) {
    props.node.data.output_params = [{ key: 'result', type: 'Object' }]
  } else if (!props.node.data.output_params) {
    props.node.data.output_params = [...(props.node.data.outputParams || [])]
  }
}

function ensureBatchOutputParams() {
  if (!props.node.data.output_params && !props.node.data.outputParams) {
    props.node.data.output_params = [{ key: 'result', type: 'Array' }]
  } else if (!props.node.data.output_params) {
    props.node.data.output_params = [...(props.node.data.outputParams || [])]
  }
}

function onLoopIteratorTypeChange(v) {
  if (props.readonly) return
  props.node.data.iterator_type = v
  props.node.data.iteratorType = v
  ensureLoopOutputParams()
  emitSync()
}

function onLoopArrayVariableChange(v) {
  if (props.readonly) return
  props.node.data.arrayVariable = v
  if (!props.node.data.input_params) props.node.data.input_params = [{ key: 'item', type: 'Object', value_from: 'refer', value: v }]
  else props.node.data.input_params[0] = { ...props.node.data.input_params[0], value: v }
  emitSync()
}

function onLoopCountLimitChange(v) {
  if (props.readonly) return
  props.node.data.count_limit = v
  props.node.data.countLimit = v
  emitSync()
}

function syncLoopOutputParams() {
  props.node.data.outputParams = props.node.data.output_params
  emitSync()
}

function addLoopOutputParam() {
  ensureLoopOutputParams()
  props.node.data.output_params.push({ key: '', type: 'Object' })
  syncLoopOutputParams()
}

function removeLoopOutputParam(idx) {
  props.node.data.output_params.splice(idx, 1)
  syncLoopOutputParams()
}

function onBatchArrayVariableChange(v) {
  if (props.readonly) return
  props.node.data.arrayVariable = v
  if (!props.node.data.input_params) props.node.data.input_params = [{ key: 'item', type: 'Object', value_from: 'refer', value: v }]
  else props.node.data.input_params[0] = { ...props.node.data.input_params[0], value: v }
  emitSync()
}

function onBatchSizeChange(v) {
  if (props.readonly) return
  props.node.data.batch_size = v
  props.node.data.batchSize = v
  emitSync()
}

function onBatchConcurrentChange(v) {
  if (props.readonly) return
  props.node.data.concurrent_size = v
  props.node.data.concurrentSize = v
  emitSync()
}

function onBatchErrorStrategyChange(v) {
  if (props.readonly) return
  props.node.data.error_strategy = v
  props.node.data.errorStrategy = v
  emitSync()
}

function syncBatchOutputParams() {
  props.node.data.outputParams = props.node.data.output_params
  emitSync()
}

function addBatchOutputParam() {
  ensureBatchOutputParams()
  props.node.data.output_params.push({ key: '', type: 'Array' })
  syncBatchOutputParams()
}

function removeBatchOutputParam(idx) {
  props.node.data.output_params.splice(idx, 1)
  syncBatchOutputParams()
}

function hint(nodeType, fieldKey) {
  return getFieldHint(nodeType, fieldKey)
}

async function copyBuiltinVar(example) {
  if (props.readonly) return
  const ok = await copyToClipboard(example)
  if (ok) message.success(`已复制 ${example}`)
  else message.info(example)
}

watch(
  () => [props.node?.id, props.node?.type, props.edges?.length],
  () => {
    if (props.node?.type !== 'condition' || !props.node?.data) return
    if (!props.node.data.conditionGroups?.length) {
      props.node.data.conditionGroups = ensureConditionGroups(props.node.data)
    }
    syncConditionBranches(props.node.data, props.edges, props.node.id)
  },
  { immediate: true }
)

function onConditionGroupsChange() {
  syncConditionBranches(props.node.data, props.edges, props.node.id)
  emitSync()
}

/** 删除条件组时同步摘掉对应出口的连线，避免幽灵边 */
function onRemoveConditionGroup(groupId) {
  if (!groupId || !props.node?.id) return
  emit('remove-source-handles', {
    nodeId: props.node.id,
    handles: [conditionGroupHandleId(props.node.id, groupId)],
  })
}

const SCRIPT_CONTENT_CACHE_KEY = '__scriptContentCache'

function getScriptContentCache(data) {
  if (!data) return {}
  if (!Object.prototype.hasOwnProperty.call(data, SCRIPT_CONTENT_CACHE_KEY)) {
    Object.defineProperty(data, SCRIPT_CONTENT_CACHE_KEY, {
      value: {},
      enumerable: false,
      configurable: true,
      writable: true,
    })
  }
  return data[SCRIPT_CONTENT_CACHE_KEY] || {}
}

/** 切换脚本语言：缓存每种语言的脚本内容，避免来回切换覆盖已编辑代码 */
function onScriptLanguageChange(lang) {
  if (props.readonly || props.node.type !== 'script') return
  const data = props.node.data
  const previousLang = data.scriptLanguage || 'javascript'
  const cache = getScriptContentCache(data)
  cache[previousLang] = data.scriptContent || ''

  const example = getScriptExampleConfig(lang)
  const cachedContent = cache[lang]
  data.scriptLanguage = lang
  data.scriptContent = cachedContent != null ? cachedContent : example.scriptContent
  if (cachedContent == null && example.inputParams?.length && !(data.inputParams?.length)) {
    data.inputParams = JSON.parse(JSON.stringify(example.inputParams))
  }
  if (cachedContent == null && example.outputParams?.length && !(data.outputParams?.length)) {
    data.outputParams = JSON.parse(JSON.stringify(example.outputParams))
  }
  emitSync()
}

function emitSync() {
  if (props.readonly) return
  emit('sync')
}

function onKnowledgeChange(v) {
  if (props.readonly) return
  emit('knowledge-change', v)
}

function onToolChange(v) {
  if (props.readonly) return
  emit('tool-change', v)
  if (!v) {
    props.node.data.inputMappings = []
    props.node.data.outputMappings = [{ key: 'toolResult', value: '{{output}}' }]
    emitSync()
    return
  }
  applyToolIoSchema(v)
}

function onModelChange(payload) {
  if (props.readonly) return
  if (payload?.providerName) props.node.data.providerName = payload.providerName
  if (payload?.modelId) props.node.data.modelName = payload.modelId
  emitSync()
}

function filterMcpOption(input, option) {
  const label = (option?.label ?? '').toString().toLowerCase()
  const kw = (input || '').toLowerCase()
  return !kw || label.includes(kw)
}

function filterMcpToolOption(input, option) {
  const label = (option?.label ?? '').toString().toLowerCase()
  const kw = (input || '').toLowerCase()
  return !kw || label.includes(kw)
}

function mcpInstallTypeLabel(server) {
  const t = server?.installType?.code || server?.installType
  const map = { npx: 'NPX', uvx: 'UVX', sse: 'SSE' }
  return map[t] || t || ''
}

async function loadModelOptionsForDisplay() {
  if (!props.readonly) return
  try {
    const res = await getProvidersWithModels('llm')
    const providers = res.data || []
    const opts = []
    for (const p of providers) {
      for (const m of (p.models || [])) {
        opts.push({
          providerId: String(p.id),
          modelId: String(m.modelId),
          providerName: p.name,
        })
      }
    }
    modelOptions.value = opts
  } catch {
    modelOptions.value = []
  }
}

async function loadMcpServers() {
  mcpServersLoading.value = true
  try {
    const res = await getMcpServers({ pageNum: 1, pageSize: 100 })
    mcpServers.value = res.data?.records || res.data || []
  } catch (e) {
    mcpServers.value = []
  } finally {
    mcpServersLoading.value = false
  }
}

async function loadMcpTools(serverId, silent = false) {
  if (!serverId) {
    mcpTools.value = []
    return
  }
  if (!silent) mcpToolsLoading.value = true
  try {
    const res = await getMcpServerTools(serverId)
    mcpTools.value = res.data || []
  } catch {
    mcpTools.value = []
  } finally {
    mcpToolsLoading.value = false
  }
}

function onMcpServerChange(serverId) {
  if (props.readonly) return
  const server = mcpServers.value.find(s => String(s.id) === String(serverId))
  props.node.data.mcpServerId = serverId
  props.node.data.mcpServerName = server?.name || ''
  props.node.data.toolName = ''
  loadMcpTools(serverId)
  emitSync()
}

async function handleRefreshMcpTools() {
  const serverId = props.node.data?.mcpServerId
  if (!serverId || props.readonly) return
  mcpToolsRefreshing.value = true
  try {
    await refreshMcpServerTools(serverId)
    message.success('工具列表已刷新')
    await loadMcpTools(serverId, true)
  } catch {
    // 错误由后端统一返回「MCP获取工具失败」，request 拦截器已 toast 一次
  } finally {
    mcpToolsRefreshing.value = false
  }
}

function resolveMcpServerIdFromName() {
  const name = props.node.data?.mcpServerName
  if (!name || props.node.data?.mcpServerId) return
  const server = mcpServers.value.find(s => s.name === name)
  if (server) {
    props.node.data.mcpServerId = server.id
    loadMcpTools(server.id)
    emitSync()
  }
}

onMounted(async () => {
  if (props.readonly) await loadModelOptionsForDisplay()
  if (props.node?.type === 'app_component') {
    await loadPublishedWorkflowAgents()
    ensureAppComponentMappings()
  }
  if (props.node?.type === 'sub_agent') {
    await loadEnabledSubAgents()
    ensureSubAgentMappings()
  }
  if (props.node?.type === 'tool') {
    ensureToolMappings()
  }
  if (props.node?.type === 'mcp') {
    await loadMcpServers()
    resolveMcpServerIdFromName()
    if (props.node.data?.mcpServerId) {
      await loadMcpTools(props.node.data.mcpServerId, true)
    }
  }
})

watch(() => props.readonly, (v) => {
  if (v) loadModelOptionsForDisplay()
})

watch(
  () => props.node?.data?.mcpServerId,
  (id, prev) => {
    if (props.node?.type !== 'mcp') return
    if (id && String(id) !== String(prev)) {
      loadMcpTools(id, true)
    }
  }
)

watch(
  () => props.node?.id,
  async () => {
    if (props.node?.type === 'app_component') {
      await loadPublishedWorkflowAgents()
      ensureAppComponentMappings()
    }
    if (props.node?.type === 'sub_agent') {
      await loadEnabledSubAgents()
      ensureSubAgentMappings()
    }
    if (props.node?.type === 'tool') {
      ensureToolMappings()
    }
    if (props.node?.type !== 'mcp') return
    if (!mcpServers.value.length) await loadMcpServers()
    resolveMcpServerIdFromName()
    if (props.node.data?.mcpServerId) {
      await loadMcpTools(props.node.data.mcpServerId, true)
    }
  }
)

function onOverrideToggle(checked) {
  if (props.readonly) return
  if (!checked && props.node.data.knowledgeBaseTopK != null) {
    props.node.data.topK = props.node.data.knowledgeBaseTopK
    props.node.data.threshold = props.node.data.knowledgeBaseThreshold
  }
  emitSync()
}

function addIntent() {
  if (props.readonly) return
  if (!props.node.data.conditions) props.node.data.conditions = []
  if (props.node.data.conditions.length >= 10) return
  props.node.data.conditions.push({ id: createConditionId(), subject: '' })
  emitSync()
}

function removeIntent(idx) {
  if (props.readonly) return
  props.node.data.conditions.splice(idx, 1)
  emitSync()
}

function addScriptInput() {
  if (!props.node.data.inputParams) props.node.data.inputParams = []
  props.node.data.inputParams.push({ key: '', value: '{{query}}' })
  emitSync()
}

function removeScriptInput(idx) {
  props.node.data.inputParams.splice(idx, 1)
  emitSync()
}

function addScriptOutput() {
  if (!props.node.data.outputParams) props.node.data.outputParams = []
  props.node.data.outputParams.push({ key: 'result', type: 'String' })
  emitSync()
}

function removeScriptOutput(idx) {
  props.node.data.outputParams.splice(idx, 1)
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
.field-hint { font-size: 12px; color: var(--color-mute); margin-top: 4px; }
.intent-item { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 8px; }
.intent-item .ant-input { flex: 1; }
.branch-item { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; padding: 8px; background: var(--color-canvas-soft); border-radius: 6px; }
.output-desc { font-size: 12px; color: var(--color-mute); padding: 8px; background: var(--color-canvas-soft-2); border-radius: 6px; }
.node-id-display { font-size: 12px; color: var(--color-mute); }

.resource-option { display: flex; flex-direction: column; gap: 4px; padding: 2px 0; }
.resource-option-header { display: flex; align-items: center; gap: 8px; }
.kb-type-mini-tag { font-size: 10px; line-height: 16px; padding: 0 4px; flex-shrink: 0; }
.kb-type-icon { font-size: 13px; flex-shrink: 0; cursor: help; }
.kb-type-icon.pg { color: #3b82f6; }
.kb-type-icon.milvus { color: #8b5cf6; }
.mcp-tool-picker {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.mcp-tool-picker :deep(.ant-select) {
  flex: 1;
  min-width: 0;
}
.mcp-refresh-btn {
  flex-shrink: 0;
  white-space: nowrap;
}
.mcp-tools-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 6px;
}
.resource-tag--muted {
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
}
.resource-option-title { font-weight: 600; font-size: 13px; color: var(--color-ink); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-option-desc { font-size: 12px; color: var(--color-mute); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.resource-option-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: var(--color-mute); }
.resource-tag { flex-shrink: 0; font-size: 11px; padding: 0 6px; border-radius: 4px; background: #ecfdf5; color: #059669; }

.kb-config-card {
  margin-bottom: 16px;
  padding: 12px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate);
  border-radius: 8px;
}
.kb-config-card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; min-width: 0; margin-bottom: 12px; }
.kb-config-title { font-weight: 600; font-size: 13px; color: var(--color-text-dark); }
.kb-config-source { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: right; font-size: 11px; color: var(--color-mute); }
.kb-config-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.kb-config-field label { display: block; font-size: 12px; color: var(--color-mute); margin-bottom: 4px; }
.kb-config-field :deep(.ant-input-number) { width: 100%; }
.kb-config-readonly-hint { margin-top: 10px; font-size: 11px; color: var(--color-mute); }

.param-row,
.extract-param-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: nowrap;
  width: 100%;
}
.param-row .ant-input,
.extract-param-row .ant-input {
  flex: 1 1 120px;
  min-width: 72px;
}
.param-row :deep(.variable-picker-input),
.extract-param-row :deep(.variable-picker-input) {
  flex: 2 1 160px;
  min-width: 120px;
  flex-wrap: nowrap;
}
.param-row :deep(.variable-picker-input .ant-input) {
  font-size: 12px;
}
.param-row :deep(.ant-select) {
  flex: 0 0 96px;
  width: 96px !important;
}
.param-row :deep(.ant-btn) {
  flex-shrink: 0;
}
.workflow-node-config-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}
.builtin-vars-form-item { margin-bottom: 8px !important; }
.builtin-vars-form-item :deep(.ant-form-item-label) { padding-bottom: 4px; }
.builtin-vars-inline { display: flex; flex-wrap: wrap; gap: 6px; }
.builtin-var-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid var(--color-hairline);
  border-radius: 4px;
  background: var(--color-canvas-soft);
  cursor: pointer;
  font-size: 11px;
  line-height: 1.4;
}
.builtin-var-tag:hover:not(:disabled) {
  border-color: var(--color-link);
  background: var(--color-info-bg);
}
.builtin-var-tag code { font-size: 11px; color: var(--color-link); }
.builtin-var-tag .copy-icon { font-size: 10px; color: var(--color-mute); }
.config-section-title { margin-bottom: 8px; font-size: 13px; font-weight: 500; color: rgba(0, 0, 0, 0.88); }
.param-add-btn { margin-top: 8px; margin-bottom: 16px; }
.config-readonly {
  position: relative;
}
.config-readonly-scrollable {
  position: relative;
}
.readonly-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  cursor: not-allowed;
  background: transparent;
}
.readonly-overlay--scrollable {
  cursor: default;
}
.config-readonly-scrollable :deep(textarea.ant-input),
.config-readonly-scrollable :deep(.json-input-wrapper),
.config-readonly-scrollable :deep(.code-editor) {
  position: relative;
  z-index: 21;
  pointer-events: auto;
}
.config-readonly-scrollable :deep(.ant-btn-dashed),
.config-readonly-scrollable :deep(.ant-btn-text.ant-btn-dangerous) {
  display: none;
}
.config-readonly :deep(.ant-btn-dashed),
.config-readonly :deep(.ant-btn-text.ant-btn-dangerous) {
  display: none;
}
.config-readonly :deep(.ant-select-selector),
.config-readonly :deep(.ant-slider),
.config-readonly :deep(.ant-input),
.config-readonly :deep(.ant-input-number),
.config-readonly :deep(.ant-switch) {
  pointer-events: none;
}
/* 禁止 label / disabled 控件触发浏览器原生 title tooltip，问号 icon 仍可用 */
.workflow-node-config-form :deep(.ant-form-item-label > label) {
  pointer-events: none;
}
.workflow-node-config-form :deep(.config-field-label),
.workflow-node-config-form :deep(.config-field-help-icon) {
  pointer-events: auto;
}
.workflow-node-config-form :deep(input[disabled]),
.workflow-node-config-form :deep(.ant-input-disabled),
.workflow-node-config-form :deep(.ant-input-number-disabled),
.workflow-node-config-form :deep(.ant-select-disabled .ant-select-selector) {
  pointer-events: none;
}
</style>
