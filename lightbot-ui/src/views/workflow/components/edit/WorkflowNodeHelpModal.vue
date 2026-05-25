<template>
  <a-modal v-model:open="open" title="如何新增工作流节点" :width="760" :footer="null" destroy-on-close>
    <div class="node-help-content">
      <p class="node-help-intro">
        工作流采用「前端画板定义 DAG + 后端 NodeProcessor 执行」架构。新增一种节点需要<strong>前后端同时扩展</strong>，后端通过 Spring 自动注册处理器。
      </p>
      <h4>一、后端：定义节点类型（NodeType）</h4>
      <p>在 <code>lightbot-server/.../enums/NodeType.java</code> 增加枚举项，例如：</p>
      <pre class="node-help-code code-block-scroll code-block-scroll--dark">RETRIEVAL("retrieval", "知识检索"),</pre>
      <p><code>code</code> 必须与前端节点 <code>type</code> 字符串一致（如 <code>llm</code>、<code>retrieval</code>）。</p>
      <h4>二、后端：实现节点处理器（NodeProcessor）</h4>
      <p>在 <code>lightbot-server/.../workflow/processor/</code> 新建类，实现接口 <code>NodeProcessor</code>：</p>
      <ul>
        <li><code>getType()</code>：返回对应的 <code>NodeType</code></li>
        <li><code>execute(NodeExecutionContext context)</code>：读取 <code>context.getCurrentNodeData()</code> 中的配置，执行业务逻辑，通过 <code>NodeExecutionResult</code> 返回下一节点 ID 与输出变量</li>
      </ul>
      <p>参考现有实现：</p>
      <ul>
        <li><code>StartNodeProcessor</code> — 将用户输入写入变量 <code>input</code>，沿出边进入下一节点</li>
        <li><code>LlmNodeProcessor</code> — 读取 <code>modelId</code>、<code>promptTemplate</code>，调用 Spring AI 生成内容</li>
        <li><code>ConditionNodeProcessor</code> — 评估 <code>branches</code> 条件表达式，选择分支目标</li>
        <li><code>EndNodeProcessor</code> — 结束工作流</li>
      </ul>
      <p>类上添加 <code>@Component</code>，无需手动注册。</p>
      <h4>三、后端：自动注册（NodeProcessorRegistry）</h4>
      <p><code>NodeProcessorRegistry</code> 在启动时注入所有 <code>NodeProcessor</code> 实现，按 <code>NodeType</code> 建立映射。执行时由 <code>WorkflowExecutorService</code> 从 START 节点开始，根据边的连接关系依次调用对应处理器。</p>
      <h4>四、前端：节点库与画布（WorkflowEdit.vue）</h4>
      <ol>
        <li>在左侧节点库增加 <code>NodeItem</code>，<code>type</code> 与后端 <code>code</code> 一致</li>
        <li>在 <code>getDefaultNodeData(type)</code> 中补充默认 <code>data</code> 字段</li>
        <li>在 <code>getNodeColor</code> / <code>getNodeTitle</code> 中补充展示配置</li>
        <li>在 <code>VueFlow</code> 内增加 <code>#node-xxx</code> 模板槽，引用 <code>workflow/nodes/XxxNode.vue</code> 自定义节点组件</li>
        <li>在右侧配置面板增加该类型的表单项，并在 <code>validateWorkflow()</code> 中校验必填项</li>
      </ol>
      <h4>五、前端：自定义节点组件</h4>
      <p>在 <code>lightbot-ui/src/views/workflow/nodes/</code> 创建 Vue 组件，使用 <code>Handle</code> 定义连接点（通常左侧 target、右侧 source；条件节点可多出口）。节点 <code>data</code> 中的配置会原样保存到 <code>agent.config.workflow</code> JSON。</p>
      <h4>六、数据结构与执行流程</h4>
      <ul>
        <li><code>WorkflowDefinition</code>：包含 <code>nodes</code>（id、type、position、data）与 <code>edges</code>（source、target、sourceHandle 等）</li>
        <li>保存：调用 <code>updateAgent</code>，将 workflow 写入 Agent 的 <code>config</code> 字段</li>
        <li>运行：对话时 <code>WorkflowExecutorService.execute()</code> 解析 config，沿 DAG 执行直至 END</li>
      </ul>
      <p class="node-help-tip">
        提示：当前画板已支持 start / end / llm / condition / retrieval / tool。枚举中还有 script、code 类型，需按上述步骤补齐前后端后方可使用。
      </p>
    </div>
  </a-modal>
</template>

<script setup>
const open = defineModel('open', { type: Boolean, default: false })
</script>

<style scoped>
.node-help-content {
  max-height: 65vh;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
}
.node-help-content h4 {
  margin: 16px 0 8px;
  font-size: 15px;
  color: #1f2937;
}
.node-help-content ul,
.node-help-content ol {
  padding-left: 20px;
  margin: 8px 0;
}
.node-help-content li { margin-bottom: 4px; }
.node-help-intro {
  margin: 0 0 12px;
  padding: 10px 12px;
  background: #f5f3ff;
  border-radius: 8px;
  color: #5b21b6;
}
.node-help-code {
  background: #1f2937;
  color: #e5e7eb;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
}
.node-help-content code {
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.node-help-tip {
  margin-top: 16px;
  padding: 10px 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #92400e;
  font-size: 13px;
}
</style>
