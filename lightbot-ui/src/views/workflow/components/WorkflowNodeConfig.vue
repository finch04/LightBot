<template>
  <div :class="{ 'config-readonly': readonly }">
  <div v-if="readonly" class="readonly-overlay" aria-hidden="true" />
  <a-form layout="vertical" :disabled="readonly" class="workflow-node-config-form">
    <a-form-item label="节点 ID">
      <span class="node-id-display mono">{{ node.id }}</span>
    </a-form-item>
    <a-form-item>
      <template #label>
        <ConfigFieldLabel label="节点名称" :tip="hint('common', 'label')" />
      </template>
      <a-input v-model:value="node.data.label" placeholder="输入节点名称" @change="emitSync" />
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
          :title="`复制 ${v.example}`"
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
        <ModelSelect
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          :disabled="readonly"
          @change="emitSync"
        />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="系统提示词" :tip="hint('llm', 'sysPrompt')" />
        </template>
        <a-textarea v-model:value="node.data.sysPrompt" :rows="2" placeholder="定义 AI 角色、行为约束（对应 SystemMessage）" @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="用户提示词模板" :tip="hint('llm', 'promptTemplate')" />
        </template>
        <a-textarea
          v-model:value="node.data.promptTemplate"
          placeholder="{{query}} 或 {{history_list}}"
          :rows="4"
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
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="300" :placeholder="'默认120'" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- 意图分类 -->
    <template v-if="node.type === 'classifier'">
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="输入变量" :tip="hint('classifier', 'inputVariable')" />
        </template>
        <VariablePickerInput v-model="node.data.inputVariable" placeholder="{{query}}" :disabled="readonly" @change="emitSync" />
      </a-form-item>
      <a-form-item label="模型" required>
        <ModelSelect
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          :disabled="readonly"
          @change="emitSync"
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
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="300" :placeholder="'默认60'" @change="emitSync" />
      </a-form-item>
      <a-form-item label="提示词（额外约束）">
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="为意图识别提供额外要求" @change="emitSync" />
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
        />
      </a-form-item>
    </template>

    <!-- 知识检索 -->
    <template v-if="node.type === 'retrieval'">
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="输入变量" :tip="hint('retrieval', 'inputVariable')" />
        </template>
        <VariablePickerInput v-model="node.data.inputVariable" placeholder="{{query}}" :disabled="readonly" @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="知识库" :tip="hint('retrieval', 'knowledgeId')" />
        </template>
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

      <a-form-item label="覆盖知识库配置">
        <a-switch v-model:checked="node.data.overrideConfig" @change="onOverrideToggle" />
        <div class="field-hint">关闭时沿用知识库默认 TopK / 阈值；开启后可在此节点单独调整</div>
      </a-form-item>
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="120" :placeholder="'默认30'" @change="emitSync" />
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
            <EntitySelectOption type="tool" :name="t.displayName || t.name" :tag="getToolTypeLabel(t.toolType)" :desc="t.description" />
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="120" :placeholder="'默认30'" @change="emitSync" />
      </a-form-item>
    </template>

    <!-- 流程输入 -->
    <template v-if="node.type === 'input'">
      <div class="config-section">
        <div class="config-section-title">输出参数</div>
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
        <a-textarea v-model:value="node.data.message" :rows="2" placeholder="展示给操作者的说明文字" @change="emitSync" />
      </a-form-item>
      <div class="config-section">
        <div class="config-section-title">
          <ConfigFieldLabel label="确认表单字段" :tip="hint('confirm', 'formFields')" />
        </div>
        <div v-for="(field, idx) in node.data.formFields" :key="idx" class="param-row confirm-field-row">
          <a-input v-model:value="field.key" placeholder="变量 key" @change="emitSync" />
          <a-input v-model:value="field.label" placeholder="显示标签" @change="emitSync" />
          <a-select v-model:value="field.type" style="width: 110px" @change="emitSync">
            <a-select-option value="text">文本</a-select-option>
            <a-select-option value="textarea">多行</a-select-option>
            <a-select-option value="number">数字</a-select-option>
            <a-select-option value="select">选项</a-select-option>
          </a-select>
          <a-switch v-model:checked="field.required" checked-children="必填" un-checked-children="选填" @change="emitSync" />
          <a-button type="text" danger @click="removeConfirmField(idx)"><DeleteOutlined /></a-button>
        </div>
        <div v-for="(field, idx) in node.data.formFields" :key="'opt-' + idx">
          <a-input
            v-if="field.type === 'select'"
            :value="formatSelectOptions(field.options)"
            placeholder="选项，逗号分隔，如：是,否"
            class="confirm-options-input"
            @update:value="val => setSelectOptions(field, val)"
          />
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" @click="addConfirmField">
          <PlusOutlined /> 添加字段
        </a-button>
      </div>
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
          <a-button type="dashed" block size="small" class="param-add-btn" @click="addGroupVar"><PlusOutlined /> 添加变量</a-button>
        </a-form-item>
      </template>
    </template>

    <!-- 参数提取 -->
    <template v-if="node.type === 'parameter_extractor'">
      <a-form-item label="输入变量" required>
        <a-input v-model:value="node.data.inputVariable" placeholder="{{input}}" @change="emitSync" />
      </a-form-item>
      <a-form-item label="模型" required>
        <ModelSelect
          v-model:provider-id="node.data.providerId"
          v-model:model-id="node.data.modelId"
          :disabled="readonly"
          @change="emitSync"
        />
      </a-form-item>
      <a-form-item label="提取指令">
        <a-textarea v-model:value="node.data.instruction" :rows="3" placeholder="补充提取规则说明" @change="emitSync" />
      </a-form-item>
      <a-form-item required>
        <template #label>
          <ConfigFieldLabel label="提取参数定义" />
        </template>
        <div v-for="(p, idx) in node.data.extractParams" :key="idx" class="extract-param-row">
          <a-input v-model:value="p.key" placeholder="参数 key" @change="emitSync" />
          <a-input v-model:value="p.desc" placeholder="描述" @change="emitSync" />
          <a-switch v-model:checked="p.required" checked-children="必填" un-checked-children="可选" @change="emitSync" />
          <a-button type="text" danger @click="removeExtractParam(idx)"><DeleteOutlined /></a-button>
        </div>
        <a-button type="dashed" block size="small" class="param-add-btn" @click="addExtractParam"><PlusOutlined /> 添加参数</a-button>
      </a-form-item>
      <ShortMemoryForm v-model="node.data.short_memory" :disabled="readonly" @update:model-value="emitSync" />
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="120" :placeholder="'默认30'" @change="emitSync" />
      </a-form-item>
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
      <a-form-item label="Headers (JSON)">
        <JsonInput v-model="node.data.headers" :rows="3" placeholder='{"Content-Type":"application/json"}' @update:model-value="emitSync" />
      </a-form-item>
      <a-form-item label="Body (JSON)">
        <JsonInput v-model="node.data.body" :rows="4" placeholder='{"key":"value"}' @update:model-value="emitSync" />
      </a-form-item>
      <a-form-item label="超时(秒)"><a-input-number v-model:value="node.data.timeout" :min="1" :max="120" @change="emitSync" /></a-form-item>
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
          <a-input v-model:value="p.key" placeholder="变量名" @change="syncLoopOutputParams" />
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
          <a-input v-model:value="p.key" placeholder="变量名" @change="syncBatchOutputParams" />
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
          v-model:language="node.data.scriptLanguage"
          :disabled="readonly"
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
          <a-input v-model:value="p.key" placeholder="参数名" :disabled="readonly" @change="emitSync" />
          <VariablePickerInput v-model="p.value" :disabled="readonly" @change="emitSync" />
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
          <a-input v-model:value="p.key" placeholder="输出字段" :disabled="readonly" @change="emitSync" />
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
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="60" :placeholder="'默认15'" @change="emitSync" />
      </a-form-item>
      <a-form-item>
        <template #label>
          <ConfigFieldLabel label="启用重试" :tip="hint('script', 'retryEnabled')" />
        </template>
        <a-switch v-model:checked="node.data.retryConfig.enabled" :disabled="readonly" @change="emitSync" />
      </a-form-item>
      <template v-if="node.data.retryConfig?.enabled">
        <a-form-item>
          <template #label>
            <ConfigFieldLabel label="最大次数" :tip="hint('script', 'maxAttempts')" />
          </template>
          <a-input-number v-model:value="node.data.retryConfig.maxAttempts" :min="1" :max="10" :disabled="readonly" @change="emitSync" />
        </a-form-item>
        <a-form-item>
          <template #label>
            <ConfigFieldLabel label="重试间隔(ms)" :tip="hint('script', 'retryDelayMs')" />
          </template>
          <a-input-number v-model:value="node.data.retryConfig.delayMs" :min="0" :max="60000" :step="500" :disabled="readonly" @change="emitSync" />
        </a-form-item>
      </template>
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
          placeholder='{"result":""}'
          @update:model-value="emitSync"
        />
      </a-form-item>
    </template>
    <!-- MCP -->
    <template v-if="node.type === 'mcp'">
      <a-form-item label="MCP 服务" required>
        <a-select
          v-model:value="node.data.mcpServerId"
          show-search
          placeholder="选择 MCP 服务"
          option-label-prop="label"
          dropdown-class-name="workflow-resource-dropdown"
          :filter-option="filterMcpOption"
          :disabled="readonly"
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
                <EntitySelectOption type="mcp" :name="s.name" :tag="mcpInstallTypeLabel(s)" />
              </div>
              <div v-if="s.description" class="resource-option-desc">{{ s.description }}</div>
            </div>
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="工具" required>
        <div class="mcp-tool-picker">
          <a-select
            v-model:value="node.data.toolName"
            show-search
            placeholder="选择 MCP 工具"
            option-label-prop="label"
            dropdown-class-name="workflow-resource-dropdown"
            :filter-option="filterMcpToolOption"
            :disabled="readonly || !node.data.mcpServerId"
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
                <div v-if="t.description" class="resource-option-desc" :title="t.description">{{ truncateText(t.description, 50) }}</div>
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
        <JsonInput v-model="mcpInputParamsJson" :rows="4" placeholder='{"chat_id":"oc_xxx","text":"{{query}}"}' />
      </a-form-item>
      <a-form-item label="超时(秒)">
        <a-input-number v-model:value="node.data.timeout" :min="1" :max="120" :placeholder="'默认60'" @change="emitSync" />
      </a-form-item>
    </template>
  </a-form>
  </div>
</template>

<script setup>
import { computed, watch, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined, CopyOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { getMcpServers, getMcpServerTools, refreshMcpServerTools } from '../../../api/mcp'
import ShortMemoryForm from './ShortMemoryForm.vue'
import ConfigFieldLabel from './ConfigFieldLabel.vue'
import VariablePickerInput from './VariablePickerInput.vue'
import CodeEditor from './CodeEditor.vue'
import ConditionGroupForm from './ConditionGroupForm.vue'
import JsonInput from '../../../components/JsonInput.vue'
import ModelSelect from '../../../components/ModelSelect.vue'
import EntitySelectOption from '../../../components/EntitySelectOption.vue'
import { getToolTypeLabel } from '../../../utils/bindingTheme'
import { createConditionId } from '../nodeMeta'
import { BUILTIN_VARIABLES, getFieldHint, getScriptExampleConfig } from '../nodeConfigMeta'
import { truncateText } from '../../../utils/format'
import { syncConditionBranches, ensureConditionGroups } from '../conditionUtils'

const props = defineProps({
  node: { type: Object, required: true },
  edges: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
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
])

const mcpServers = ref([])
const mcpServersLoading = ref(false)
const mcpTools = ref([])
const mcpToolsLoading = ref(false)
const mcpToolsRefreshing = ref(false)
const enabledMcpTools = computed(() =>
  (mcpTools.value || []).filter(t => t.enabled !== false)
)

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

function copyBuiltinVar(example) {
  if (props.readonly) return
  navigator.clipboard?.writeText(example).then(() => {
    message.success(`已复制 ${example}`)
  }).catch(() => {
    message.info(example)
  })
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

/** 切换脚本语言：直接切换为对应语言的示例模板 */
function onScriptLanguageChange(lang) {
  if (props.readonly || props.node.type !== 'script') return
  const example = getScriptExampleConfig(lang)
  props.node.data.scriptLanguage = lang
  props.node.data.scriptContent = example.scriptContent
  if (example.inputParams?.length) {
    props.node.data.inputParams = JSON.parse(JSON.stringify(example.inputParams))
  }
  if (example.outputParams?.length) {
    props.node.data.outputParams = JSON.parse(JSON.stringify(example.outputParams))
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

async function loadMcpServers() {
  mcpServersLoading.value = true
  try {
    const res = await getMcpServers({ pageNum: 1, pageSize: 200 })
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
  if (props.node?.type === 'mcp') {
    await loadMcpServers()
    resolveMcpServerIdFromName()
    if (props.node.data?.mcpServerId) {
      await loadMcpTools(props.node.data.mcpServerId, true)
    }
  }
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

function addConfirmField() {
  if (!props.node.data.formFields) props.node.data.formFields = []
  props.node.data.formFields.push({ key: '', label: '', type: 'text', required: false, defaultValue: '' })
  emitSync()
}

function removeConfirmField(idx) {
  props.node.data.formFields?.splice(idx, 1)
  emitSync()
}

function formatSelectOptions(options) {
  if (!Array.isArray(options)) return ''
  return options.join(',')
}

function setSelectOptions(field, val) {
  field.options = (val || '').split(',').map(s => s.trim()).filter(Boolean)
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
.kb-config-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.kb-config-title { font-weight: 600; font-size: 13px; color: var(--color-text-dark); }
.kb-config-source { font-size: 11px; color: var(--color-mute); }
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
.confirm-field-row {
  flex-wrap: wrap;
}
.confirm-options-input {
  margin: -4px 0 10px 0;
}
.config-section-title { margin-bottom: 8px; font-size: 13px; font-weight: 500; color: rgba(0, 0, 0, 0.88); }
.param-add-btn { margin-top: 8px; margin-bottom: 16px; }
.config-readonly {
  position: relative;
}
.readonly-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  cursor: not-allowed;
  background: transparent;
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
</style>
