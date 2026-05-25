<template>
  <div class="workflow-edit-page">
    <!-- 顶部工具栏 -->
    <div class="workflow-toolbar">
      <button class="btn-back" @click="goBack">
        <ArrowLeftOutlined /> 返回
      </button>
      <a-tag v-if="workflowStatus === 'draft'" color="orange" class="publish-tag">未发布</a-tag>
      <a-tag v-else-if="workflowStatus === 'published_editing'" color="gold" class="publish-tag">已发布编辑中</a-tag>
      <a-tag v-else color="green" class="publish-tag">已发布 v{{ publishedVersion }}</a-tag>
      <h1 class="workflow-title">{{ agent?.name || '工作流配置' }}</h1>
      <div class="toolbar-status">
        <a-dropdown v-if="validationErrors.length > 0" :trigger="['click']">
          <span class="status-error clickable">
            <ExclamationCircleOutlined /> {{ validationErrors.length }} 个配置错误
            <DownOutlined style="margin-left: 4px; font-size: 10px;" />
          </span>
          <template #overlay>
            <div class="error-dropdown">
              <div class="error-header">配置错误详情</div>
              <div class="error-list">
                <div v-for="err in validationErrors" :key="err.nodeId + err.field" class="error-item">
                  <span class="error-node">{{ getNodeTitleById(err.nodeId) || '工作流全局' }}</span>
                  <span class="error-field">{{ err.field }}</span>
                  <span class="error-msg">{{ err.message }}</span>
                </div>
              </div>
            </div>
          </template>
        </a-dropdown>
        <span v-else-if="validationErrors.length === 0 && nodes.length >= 2" class="status-valid">
          <CheckCircleOutlined /> 配置完整
        </span>
        <span v-else class="status-empty">
          请添加节点并配置
        </span>
        <a-tooltip title="验证配置">
          <a-button type="text" size="small" class="btn-validate" @click="validateWorkflow">
            <AuditOutlined />
          </a-button>
        </a-tooltip>
        <span v-if="autoSaving" class="auto-save-hint saving">保存中...</span>
        <span v-else-if="lastAutoSaveTime" class="auto-save-hint">{{ formatAutoSaveTime(lastAutoSaveTime) }} 已自动保存</span>
      </div>
      <div class="toolbar-actions">
        <template v-if="isVersionPreview">
          <a-button type="primary" @click="backToCurrentDraft">
            <RollbackOutlined /> 回到当前版本
          </a-button>
          <a-button type="default" @click="openVersionDrawer">版本管理</a-button>
        </template>
        <template v-else>
        <a-button v-if="canUndo" type="default" @click="undoAction">
          <UndoOutlined /> 撤回
        </a-button>
        <a-button type="default" @click="globalConfigVisible = true">全局设置</a-button>
        <a-button type="default" @click="testVisible = true">测试运行</a-button>
        <a-button type="default" @click="openVersionDrawer">版本管理</a-button>
        <a-button type="default" @click="saveDraft" :disabled="saving" :loading="saving">
          <SaveOutlined /> 暂存
        </a-button>
        <a-button type="primary" @click="openPublishModal" :disabled="saving">
          发布
        </a-button>
        </template>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="workflow-content">
      <!-- 左侧节点面板 -->
      <div class="node-panel" :class="{ collapsed: panelCollapsed }">
        <div class="panel-header">
          <span v-if="!panelCollapsed">{{ leftPanelTab === 'library' ? '节点库' : '画布节点' }}</span>
          <div v-if="!panelCollapsed" class="panel-header-actions">
            <a-tooltip title="如何新增节点">
              <button type="button" class="btn-help" @click="nodeHelpVisible = true">
                <QuestionCircleOutlined />
              </button>
            </a-tooltip>
          </div>
          <button class="btn-collapse" @click="panelCollapsed = !panelCollapsed">
            <LeftOutlined v-if="!panelCollapsed" />
            <RightOutlined v-else />
          </button>
        </div>
        <div class="panel-body" v-if="!panelCollapsed">
          <a-segmented
            v-model:value="leftPanelTab"
            :options="[
              { label: '节点库', value: 'library' },
              { label: '画布节点', value: 'canvas' }
            ]"
            block
            size="small"
            style="margin-bottom: 10px"
          />

          <template v-if="leftPanelTab === 'canvas'">
            <a-input
              v-model:value="canvasNodeSearch"
              placeholder="搜索画布节点..."
              allow-clear
              size="small"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input>
            <div class="canvas-node-list">
              <div
                v-for="n in filteredCanvasNodes"
                :key="n.id"
                class="canvas-node-item"
                :class="{ active: selectedNode?.id === n.id }"
                @click="focusNode(n)"
              >
                <span class="canvas-node-dot" :style="{ background: getNodeColor(n.type) }" />
                <span class="canvas-node-name">{{ n.data?.label || getNodeTitle(n.type) }}</span>
                <span class="canvas-node-type">{{ getNodeTitle(n.type) }}</span>
              </div>
            </div>
          </template>

          <template v-else>
          <a-input
            v-model:value="nodeSearch"
            placeholder="搜索节点..."
            allow-clear
            size="small"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>

          <div class="node-group" v-for="group in filteredNodeGroups" :key="group.key">
            <div class="group-title">{{ group.title }}</div>
            <NodeItem
              v-for="type in group.items"
              :key="type"
              :type="type"
              :title="getNodeMeta(type).title"
              :desc="getNodeMeta(type).desc"
              :color="getNodeMeta(type).color"
              draggable="true"
              @dragstart="onDragStart($event, type)"
            />
          </div>
          </template>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-area" ref="canvasAreaRef" @dragover.prevent @drop="onDrop">
        <div v-if="isVersionPreview" class="version-preview-banner">
          正在预览历史版本 v{{ selectedVersion }}（只读），点击右上角「回到当前版本」返回草稿
        </div>

        <!-- 画布内版本列表（不遮挡顶部工具栏） -->
        <div
          v-if="versionVisible"
          class="version-panel-float"
          :style="versionPanelStyle"
        >
          <div class="version-panel-header">
            <span
              class="version-panel-drag-handle"
              title="按住拖动面板"
              @mousedown.prevent="onVersionPanelDragStart"
            >
              <HolderOutlined />
            </span>
            <span class="version-panel-title">历史版本</span>
            <button type="button" class="version-panel-close" @click="versionVisible = false">
              <CloseOutlined />
            </button>
          </div>
          <div class="version-panel-body">
            <div
              class="version-item draft"
              :class="{ active: selectedVersion === 'draft' }"
              @click="selectVersion('draft')"
            >
              <div class="version-item-title">当前草稿</div>
              <div class="version-item-desc">继续编辑未发布的修改</div>
            </div>
            <a-divider style="margin: 12px 0" />
            <a-spin :spinning="versionLoading">
              <a-timeline>
                <a-timeline-item
                  v-for="(item, idx) in versionList"
                  :key="item.version"
                  :color="selectedVersion === item.version ? '#6366f1' : '#d1d5db'"
                >
                  <div
                    class="version-item"
                    :class="{ active: selectedVersion === item.version }"
                    @click="selectVersion(item.version)"
                  >
                    <div class="version-item-header">
                      <span class="version-item-title">
                        {{ idx === 0 ? '线上版本' : `v${item.version}` }}
                      </span>
                      <a-tag v-if="idx === 0" color="green" size="small">最新</a-tag>
                    </div>
                    <div v-if="item.description" class="version-item-note">{{ item.description }}</div>
                    <div class="version-item-desc">{{ formatVersionDesc(item) }}</div>
                  </div>
                </a-timeline-item>
              </a-timeline>
              <a-empty v-if="!versionLoading && versionList.length === 0" description="暂无发布版本" />
            </a-spin>
          </div>
          <div v-if="selectedVersion !== 'draft'" class="version-panel-footer">
            <a-button type="primary" block @click="overwriteDraftFromVersion">
              覆盖当前草稿
            </a-button>
          </div>
        </div>
        <!-- 拖动节点时显示删除区 -->
        <div
          v-show="isNodeDragging"
          ref="trashRef"
          class="workflow-trash"
          :class="{ 'is-over': dragOverTrash, 'is-disabled': !canDeleteDraggedNode }"
        >
          <DeleteOutlined class="trash-icon" />
          <span class="trash-label">{{ canDeleteDraggedNode ? '拖到此处删除' : '开始/结束节点不可删除' }}</span>
        </div>

        <VueFlow
          v-if="nodes.length > 0"
          :nodes="nodes"
          :edges="edges"
          :nodes-draggable="!isVersionPreview"
          :edges-selectable="!isVersionPreview"
          :nodes-connectable="!isVersionPreview"
          :elements-selectable="!isVersionPreview"
          :default-edge-options="defaultEdgeOptions"
          @connect="onConnect"
          @nodes-change="onNodesChange"
          @node-drag-start="onNodeDragStart"
          @node-drag="onNodeDrag"
          @node-drag-stop="onNodeDragStop"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
          :default-viewport="{ zoom: 0.8, x: 0, y: 0 }"
          :min-zoom="0.1"
          :max-zoom="4"
        >
          <Background :gap="[20, 20]" pattern-color="#e5e7eb" />
          <Controls position="bottom-right" show-zoom show-fit-view />
          <MiniMap
            position="bottom-left"
            class="workflow-minimap"
            :offset-scale="4"
            pannable
            zoomable
            :node-color="getNodeColor"
            :node-stroke-width="3"
          />

          <!-- 自定义节点模板 -->
          <template #node-start="props"><StartNode v-bind="props" /></template>
          <template #node-end="props"><EndNode v-bind="props" /></template>
          <template #node-llm="props"><LlmNode v-bind="props" /></template>
          <template #node-condition="props"><ConditionNode v-bind="props" /></template>
          <template #node-retrieval="props"><RetrievalNode v-bind="props" /></template>
          <template #node-tool="props"><ToolNode v-bind="props" /></template>
          <template #node-classifier="props"><ClassifierNode v-bind="props" /></template>
          <template #node-api="props"><GenericWorkflowNode v-bind="props" node-type="api" summary-key="url" /></template>
          <template #node-loop="props"><GenericWorkflowNode v-bind="props" node-type="loop" /></template>
          <template #node-variable="props"><GenericWorkflowNode v-bind="props" node-type="variable" summary-key="variableName" /></template>
          <template #node-batch="props"><GenericWorkflowNode v-bind="props" node-type="batch" /></template>
          <template #node-script="props"><GenericWorkflowNode v-bind="props" node-type="script" /></template>
          <template #node-mcp="props"><GenericWorkflowNode v-bind="props" node-type="mcp" summary-key="mcpServerName" /></template>
          <template #node-input="props"><GenericWorkflowNode v-bind="props" node-type="input" /></template>
          <template #node-output="props"><GenericWorkflowNode v-bind="props" node-type="output" summary-key="output" /></template>
          <template #node-variable_handle="props"><GenericWorkflowNode v-bind="props" node-type="variable_handle" /></template>
          <template #node-parameter_extractor="props"><GenericWorkflowNode v-bind="props" node-type="parameter_extractor" /></template>
          <template #node-app_component="props"><GenericWorkflowNode v-bind="props" node-type="app_component" summary-key="componentName" /></template>
        </VueFlow>

        <!-- 空状态提示 -->
        <div v-if="nodes.length === 0" class="canvas-empty">
          <p>从左侧拖拽节点到画布开始构建工作流</p>
        </div>
      </div>

      <!-- 右侧配置面板：连线详情 -->
      <div class="config-panel" v-if="selectedEdge">
        <div class="panel-header">
          <div class="node-type-badge">
            <div class="type-icon edge-icon">
              <BranchesOutlined />
            </div>
            <span class="type-name">连线详情</span>
          </div>
          <button class="btn-close" @click="clearEdgeSelection">
            <CloseOutlined />
          </button>
        </div>
        <div class="panel-body">
          <div class="edge-detail-card">
            <div class="edge-detail-row">
              <span class="edge-detail-label">连线 ID</span>
              <span class="edge-detail-value mono">{{ selectedEdge.id }}</span>
            </div>
            <div class="edge-connection-flow">
              <div class="edge-node-box source">
                <span class="edge-node-role">源节点</span>
                <span class="edge-node-name">{{ getEdgeSourceLabel(selectedEdge) }}</span>
                <span class="edge-node-type">{{ getEdgeSourceType(selectedEdge) }}</span>
                <span v-if="selectedEdge.sourceHandle" class="edge-handle-tag">出口: {{ selectedEdge.sourceHandle }}</span>
              </div>
              <div class="edge-arrow">
                <ArrowRightOutlined />
              </div>
              <div class="edge-node-box target">
                <span class="edge-node-role">目标节点</span>
                <span class="edge-node-name">{{ getEdgeTargetLabel(selectedEdge) }}</span>
                <span class="edge-node-type">{{ getEdgeTargetType(selectedEdge) }}</span>
                <span v-if="selectedEdge.targetHandle" class="edge-handle-tag">入口: {{ selectedEdge.targetHandle }}</span>
              </div>
            </div>
          </div>
          <div class="panel-footer edge-delete-footer">
            <a-button type="primary" danger block @click="deleteSelectedEdge">
              <DeleteOutlined /> 删除连线
            </a-button>
          </div>
        </div>
      </div>

      <!-- 右侧配置面板：节点配置 -->
      <div class="config-panel" v-else-if="selectedNode">
        <div class="panel-header">
          <div class="node-type-badge">
            <div class="type-icon" :style="{ background: getNodeColor(selectedNode.type) + '20', color: getNodeColor(selectedNode.type) }">
              <NodeTypeIcon :type="selectedNode.type" />
            </div>
            <span class="type-name">{{ getNodeTitle(selectedNode.type) }}</span>
          </div>
          <button class="btn-close" @click="closeNodePanel">
            <CloseOutlined />
          </button>
        </div>
        <div class="panel-body" :class="{ 'panel-body-readonly': isVersionPreview }">
          <!-- 节点错误提示 -->
          <div v-if="getNodeErrors(selectedNode.id).length > 0" class="node-errors">
            <div v-for="err in getNodeErrors(selectedNode.id)" :key="err.field" class="error-item">
              <ExclamationCircleOutlined /> {{ err.message }}
            </div>
          </div>

          <a-alert v-if="isVersionPreview" type="info" show-icon message="历史版本预览（只读）" class="preview-readonly-alert" />

          <WorkflowNodeConfig
            v-if="selectedNode.type !== 'start' && selectedNode.type !== 'end'"
            :readonly="isVersionPreview"
            :node="selectedNode"
            :providers="providers"
            :llm-model-list="llmModelList"
            :knowledge-list="knowledgeList"
            :tools="tools"
            :target-nodes="getTargetNodes()"
            :filter-knowledge-option="filterKnowledgeOption"
            :filter-tool-option="filterToolOption"
            :get-tool-type-label="getToolTypeLabel"
            @sync="syncNodes"
            @llm-provider-change="onLlmProviderChange"
            @llm-model-change="onLlmModelChange"
            @knowledge-change="onKnowledgeChange"
            @tool-change="onToolChange"
          />
          <a-form v-else layout="vertical" :disabled="isVersionPreview">
            <a-form-item label="节点 ID"><span class="node-id-display mono">{{ selectedNode.id }}</span></a-form-item>
            <a-form-item label="节点名称">
              <a-input v-model:value="selectedNode.data.label" :disabled="isVersionPreview" @change="syncNodes" />
            </a-form-item>
          </a-form>

          <!-- 删除节点按钮 -->
          <div class="panel-footer" v-if="!isVersionPreview && selectedNode.type !== 'start' && selectedNode.type !== 'end'">
            <a-button type="text" danger @click="deleteSelectedNode">
              <DeleteOutlined /> 删除节点
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 如何新增节点说明 -->
  <a-modal
    v-model:open="nodeHelpVisible"
    title="如何新增工作流节点"
    :width="760"
    :footer="null"
    destroy-on-close
  >
    <div class="node-help-content">
      <p class="node-help-intro">
        工作流采用「前端画板定义 DAG + 后端 NodeProcessor 执行」架构。新增一种节点需要<strong>前后端同时扩展</strong>，后端通过 Spring 自动注册处理器。
      </p>

      <h4>一、后端：定义节点类型（NodeType）</h4>
      <p>在 <code>lightbot-server/.../enums/NodeType.java</code> 增加枚举项，例如：</p>
      <pre class="node-help-code">RETRIEVAL("retrieval", "知识检索"),</pre>
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

  <a-modal v-model:open="globalConfigVisible" title="全局设置" :width="640" @ok="globalConfigVisible = false">
    <a-form layout="vertical">
      <a-form-item label="上下文轮次（history_max_round）">
        <a-input-number v-model:value="globalConfig.history_config.history_max_round" :min="0" :max="50" style="width: 100%" />
      </a-form-item>
      <a-form-item label="启用对话历史">
        <a-switch v-model:checked="globalConfig.history_config.history_switch" />
      </a-form-item>
      <a-divider>会话变量（conversation_params）</a-divider>
      <div v-for="(param, idx) in globalConfig.variable_config.conversation_params" :key="idx" class="conv-param-row">
        <a-input v-model:value="param.key" placeholder="变量名" style="flex:1" />
        <a-input v-model:value="param.default_value" placeholder="默认值" style="flex:1" />
        <a-button type="text" danger @click="removeConversationParam(idx)"><DeleteOutlined /></a-button>
      </div>
      <a-button type="dashed" block @click="addConversationParam"><PlusOutlined /> 添加会话变量</a-button>
    </a-form>
  </a-modal>

  <a-drawer
    v-model:open="testVisible"
    title="测试运行"
    :width="560"
    :mask-closable="!testRunning && !testAnimating"
    :keyboard="!testRunning && !testAnimating"
    @close="clearNodeDebugStatus"
  >
    <a-alert v-if="testAnimating" type="info" show-icon message="正在执行工作流..." description="画布上当前节点会高亮显示执行状态" class="test-alert" />
    <a-form layout="vertical">
      <a-form-item label="测试问题" required>
        <a-textarea v-model:value="testInput" :rows="4" placeholder="输入要测试的问题或内容" />
      </a-form-item>
      <a-form-item label="使用草稿配置">
        <a-switch v-model:checked="testUseDraft" />
      </a-form-item>
      <a-button type="primary" :loading="testRunning || testAnimating" @click="runWorkflowTest">
        {{ testAnimating ? '执行中...' : '开始测试' }}
      </a-button>
    </a-form>
    <a-divider v-if="testResult || testAnimating" />
    <div v-if="testAnimating && testCurrentNodeId" class="test-current-node">
      当前节点：<strong>{{ getNodeTitleById(testCurrentNodeId) }}</strong>
    </div>
    <div v-if="testResult">
      <h4>输出结果</h4>
      <pre class="test-output">{{ testResult.output || '（无输出）' }}</pre>
      <h4>节点轨迹</h4>
      <div
        v-for="(ev, i) in testResult.nodeEvents"
        :key="i"
        class="test-event"
        :class="{ active: ev.nodeId === testCurrentNodeId }"
      >
        <span class="test-event-type">{{ ev.type === 'workflow_node_start' ? '▶' : ev.type === 'workflow_node_complete' ? '✓' : '•' }}</span>
        {{ ev.nodeLabel || ev.nodeId }} — {{ ev.message || ev.nodeType }}
      </div>
    </div>
  </a-drawer>

  <a-modal
    v-model:open="publishModalVisible"
    title="发布工作流"
    ok-text="确认发布"
    cancel-text="取消"
    :confirm-loading="saving"
    @ok="confirmPublishWorkflow"
  >
    <p class="publish-modal-tip">选填发布说明（最多 50 字），可在版本历史中查看。</p>
    <a-textarea
      v-model:value="publishDescription"
      :maxlength="50"
      show-count
      :rows="2"
      placeholder="例如：新增检索节点、调整 LLM 提示词"
    />
  </a-modal>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, shallowRef, triggerRef, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, useVueFlow, applyNodeChanges } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import {
  ArrowLeftOutlined, SaveOutlined, CheckCircleOutlined, ExclamationCircleOutlined,
  SearchOutlined, LeftOutlined, RightOutlined, CloseOutlined, DeleteOutlined,
  RobotOutlined, ForkOutlined, BookOutlined, ToolOutlined, PlayCircleOutlined,
  StopOutlined, PlusOutlined, DownOutlined, UndoOutlined, QuestionCircleOutlined,
  BranchesOutlined, ArrowRightOutlined, AuditOutlined, RollbackOutlined,
  HolderOutlined
} from '@ant-design/icons-vue'
import { message, notification, Modal } from 'ant-design-vue'
import { getAgentDetail } from '../api/agent'
import { getKnowledge, getKnowledgeList } from '../api/knowledge'
import { getModelProviders } from '../api/modelProvider'
import { getModelsByProvider } from '../api/model'
import { getTools } from '../api/tool'
import {
  getWorkflowConfig,
  saveWorkflowDraft,
  publishWorkflow as publishWorkflowApi,
  listWorkflowVersions,
  getWorkflowVersionDetail,
  restoreWorkflowVersion,
  testWorkflow
} from '../api/workflow'
import NodeItem from '../views/workflow/components/NodeItem.vue'
import StartNode from '../views/workflow/nodes/StartNode.vue'
import EndNode from '../views/workflow/nodes/EndNode.vue'
import LlmNode from '../views/workflow/nodes/LlmNode.vue'
import ConditionNode from '../views/workflow/nodes/ConditionNode.vue'
import RetrievalNode from '../views/workflow/nodes/RetrievalNode.vue'
import ToolNode from '../views/workflow/nodes/ToolNode.vue'
import GenericWorkflowNode from '../views/workflow/nodes/GenericWorkflowNode.vue'
import ClassifierNode from '../views/workflow/nodes/ClassifierNode.vue'
import NodeTypeIcon from '../views/workflow/components/NodeTypeIcon.vue'
import WorkflowNodeConfig from '../views/workflow/components/WorkflowNodeConfig.vue'
import { getDefaultNodeData as buildDefaultNodeData, getNodeTitle as metaGetNodeTitle, getNodeColor as metaGetNodeColor, getNodeMeta, getNodeLibraryGroups, createConditionId } from '../views/workflow/nodeMeta'

const route = useRoute()
const router = useRouter()
const agentId = route.params.agentId

// VueFlow hooks（getNodes 为画布真实坐标来源）
const { fitView, addSelectedEdges, removeSelectedEdges, getSelectedEdges, getNodes, setCenter, setViewport } = useVueFlow()

const defaultEdgeOptions = {
  selectable: true,
  style: { strokeWidth: 2, stroke: '#94a3b8' }
}

// 状态
const agent = ref(null)
const saving = ref(false)
const panelCollapsed = ref(false)
const nodeSearch = ref('')
const nodeHelpVisible = ref(false)
const leftPanelTab = ref('library')
const canvasNodeSearch = ref('')
const workflowStatus = ref('draft')
const publishedVersion = ref(0)
const globalConfigVisible = ref(false)
const testVisible = ref(false)
const testInput = ref('')
const testUseDraft = ref(true)
const testRunning = ref(false)
const testAnimating = ref(false)
const testCurrentNodeId = ref(null)
const testResult = ref(null)
const versionVisible = ref(false)
const publishModalVisible = ref(false)
const publishDescription = ref('')
const lastAutoSaveTime = ref(null)
const autoSaving = ref(false)
const workflowLoaded = ref(false)
let autoSaveTimer = null
const versionList = ref([])
const versionLoading = ref(false)
const selectedVersion = ref('draft')
const VERSION_PANEL_WIDTH = 300
const versionPanelPos = ref({ x: 0, y: 48 })
let versionPanelDragState = null
let versionPanelLayoutReady = false

const versionPanelStyle = computed(() => {
  const canvas = canvasAreaRef.value
  const h = canvas
    ? Math.max(280, canvas.clientHeight - versionPanelPos.value.y - 72)
    : 400
  return {
    left: `${versionPanelPos.value.x}px`,
    top: `${versionPanelPos.value.y}px`,
    height: `${h}px`,
    width: `${VERSION_PANEL_WIDTH}px`,
  }
})
const globalConfig = ref({
  history_config: { history_switch: true, history_max_round: 5 },
  variable_config: { conversation_params: [] }
})
const selectedNode = ref(null)
const selectedEdge = ref(null)
const validationErrors = ref([])
const canvasAreaRef = ref(null)
const trashRef = ref(null)
const savedWorkflowSnapshot = ref('')
const isNodeDragging = ref(false)
const dragOverTrash = ref(false)
const draggingNode = ref(null)

const isDirty = computed(() => {
  if (!savedWorkflowSnapshot.value) return false
  return savedWorkflowSnapshot.value !== getWorkflowSnapshot()
})

const canDeleteDraggedNode = computed(() => {
  const node = draggingNode.value
  return node && node.type !== 'start' && node.type !== 'end'
})

// 资源列表
const providers = ref([])
const llmModelList = ref([])
const knowledgeList = ref([])
const tools = ref([])

// 节点和边数据（使用 shallowRef 避免递归更新）
const nodes = shallowRef([])
const edges = shallowRef([])

function normalizePosition(pos) {
  if (!pos) return { x: 100, y: 100 }
  return {
    x: Number(pos.x ?? 100),
    y: Number(pos.y ?? 100)
  }
}

function buildWorkflowPayload() {
  const flowNodes = getNodes.value?.length ? getNodes.value : nodes.value
  return {
    nodes: flowNodes.map(n => {
      const local = nodes.value.find(item => item.id === n.id)
      return {
        id: n.id,
        type: n.type,
        position: normalizePosition(n.position),
        data: local?.data ?? n.data
      }
    }),
    edges: edges.value.map(e => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle,
      targetHandle: e.targetHandle
    })),
    globalConfig: globalConfig.value
  }
}

function getWorkflowSnapshot() {
  return JSON.stringify(buildWorkflowPayload())
}

const filteredCanvasNodes = computed(() => {
  const keyword = (canvasNodeSearch.value || '').toLowerCase()
  return nodes.value.filter(n => {
    const label = (n.data?.label || getNodeTitle(n.type) || '').toLowerCase()
    return !keyword || label.includes(keyword) || n.type.includes(keyword)
  })
})

const filteredNodeGroups = computed(() => getNodeLibraryGroups(nodeSearch.value))

const isVersionPreview = computed(() => selectedVersion.value !== 'draft')

function hasSavedLayout(nodeList) {
  return nodeList.some(n => {
    const p = n.position
    if (!p) return false
    const x = Number(p.x)
    const y = Number(p.y)
    return (x !== 100 && x !== 0) || (y !== 200 && y !== 0)
  })
}

function markWorkflowSaved() {
  savedWorkflowSnapshot.value = getWorkflowSnapshot()
}

function formatAutoSaveTime(d) {
  if (!d) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function scheduleAutoSave() {
  if (!workflowLoaded.value || isVersionPreview.value) return
  if (!isDirty.value) return
  clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => doAutoSave(true), 600)
}

async function doAutoSave(silent = true) {
  if (autoSaving.value || !isDirty.value || isVersionPreview.value) return
  autoSaving.value = true
  try {
    await saveWorkflowDraft(agentId, buildWorkflowPayload())
    markWorkflowSaved()
    lastAutoSaveTime.value = new Date()
    if (workflowStatus.value === 'published') {
      workflowStatus.value = 'published_editing'
    }
  } catch (e) {
    if (!silent) {
      notification.error({ message: '自动保存失败', description: e.message })
    }
  } finally {
    autoSaving.value = false
  }
}

async function flushAutoSave() {
  clearTimeout(autoSaveTimer)
  if (isDirty.value) {
    await doAutoSave(true)
  }
}

async function closeNodePanel() {
  const node = selectedNode.value
  if (!node) return
  if (isVersionPreview.value) {
    selectedNode.value = null
    return
  }
  const wasDirty = isDirty.value
  clearTimeout(autoSaveTimer)
  if (wasDirty) {
    await doAutoSave(true)
    message.success(`「${node.data?.label || getNodeTitle(node.type)}」已自动保存`)
  }
  selectedNode.value = null
}

function scheduleFitView(force = false) {
  if (!force && hasSavedLayout(nodes.value)) {
    return
  }
  nextTick(() => {
    setTimeout(() => {
      if (nodes.value.length > 0) {
        try {
          fitView({ padding: 0.2, includeHiddenNodes: true, duration: force ? 300 : 0 })
        } catch (e) {
          console.warn('fitView error:', e)
        }
      }
    }, 120)
  })
}

function applyWorkflowGraph(graph) {
  if (!graph) return
  if (graph.globalConfig) {
    globalConfig.value = {
      history_config: {
        history_switch: graph.globalConfig.history_config?.history_switch ?? true,
        history_max_round: graph.globalConfig.history_config?.history_max_round ?? 5
      },
      variable_config: {
        conversation_params: graph.globalConfig.variable_config?.conversation_params || []
      }
    }
  }
  nodes.value = (graph.nodes || []).map(migrateWorkflowNode).map(n => ({
    ...n,
    position: normalizePosition(n.position)
  }))
  edges.value = graph.edges || []
  triggerRef(nodes)
  triggerRef(edges)
}

function focusNode(node) {
  selectedNode.value = node
  clearEdgeSelection()
  const pos = normalizePosition(node.position)
  try {
    setCenter(pos.x + 90, pos.y + 40, { zoom: 1, duration: 300 })
  } catch (_) {
    setViewport({ x: -pos.x + 200, y: -pos.y + 120, zoom: 1 })
  }
}

function addConversationParam() {
  globalConfig.value.variable_config.conversation_params.push({ key: '', default_value: '' })
}

function removeConversationParam(index) {
  globalConfig.value.variable_config.conversation_params.splice(index, 1)
}

// 操作历史记录（用于撤回）
const history = ref([])
const canUndo = computed(() => history.value.length > 0)

// 记录操作历史
function recordHistory() {
  history.value.push({
    nodes: JSON.parse(JSON.stringify(nodes.value)),
    edges: JSON.parse(JSON.stringify(edges.value))
  })
  // 限制历史记录数量
  if (history.value.length > 20) {
    history.value.shift()
  }
}

// 撤回操作
function undoAction() {
  if (history.value.length === 0) return
  const lastState = history.value.pop()
  nodes.value = lastState.nodes
  edges.value = lastState.edges
  triggerRef(nodes)
  triggerRef(edges)
  selectedNode.value = null
  selectedEdge.value = null
  clearEdgeSelection()
}

// 初始化加载
onMounted(async () => {
  try {
    // 加载 Agent 数据
    const res = await getAgentDetail(agentId)
    agent.value = res.data.agent

    const wfRes = await getWorkflowConfig(agentId)
    workflowStatus.value = wfRes.data.status || 'draft'
    publishedVersion.value = wfRes.data.publishedVersion || 0
    const draftGraph = wfRes.data.draft
    if (draftGraph) {
      applyWorkflowGraph(draftGraph)
    } else if (res.data.agent.config) {
      const config = JSON.parse(res.data.agent.config)
      if (config.workflow) {
        applyWorkflowGraph(config.workflow)
      }
    }

    // 如果没有节点，添加默认的 start 和 end
    if (nodes.value.length === 0) {
      nodes.value = [
        { id: 'start_1', type: 'start', position: { x: 100, y: 200 }, data: { label: '开始' } },
        { id: 'end_1', type: 'end', position: { x: 600, y: 200 }, data: { label: '结束' } }
      ]
      triggerRef(nodes)
    }

    // 加载资源列表
    const [providerRes, knowledgeRes, toolRes] = await Promise.all([
      getModelProviders({ pageNum: 1, pageSize: 100 }),
      getKnowledgeList({ pageNum: 1, pageSize: 100 }),
      getTools({ pageNum: 1, pageSize: 100 })
    ])
    providers.value = providerRes.data.records || []
    knowledgeList.value = knowledgeRes.data.records || []
    tools.value = toolRes.data.records || []

    markWorkflowSaved()
    nextTick(() => { workflowLoaded.value = true })
    scheduleFitView(!hasSavedLayout(nodes.value))
    validateWorkflow(false)
  } catch (e) {
    notification.error({ message: '加载失败', description: e.message })
  }
  window.addEventListener('keydown', onKeyDown)
})

function onKeyDown(event) {
  if (isVersionPreview.value) return
  if (event.key !== 'Delete' && event.key !== 'Backspace') return
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || document.activeElement?.isContentEditable) return

  event.preventDefault()
  if (selectedEdge.value) {
    deleteSelectedEdge()
  } else if (selectedNode.value && selectedNode.value.type !== 'start' && selectedNode.value.type !== 'end') {
    deleteSelectedNode()
  }
}

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mousemove', onVersionPanelDragMove)
  document.removeEventListener('mouseup', onVersionPanelDragEnd)
  clearTimeout(autoSaveTimer)
})

/** 兼容旧数据并补齐默认字段 */
function migrateWorkflowNode(node) {
  const defaults = buildDefaultNodeData(node.type)
  const data = { ...(defaults || {}), ...(node.data || {}) }
  if (node.type === 'llm') {
    if (!data.providerId && data.modelId != null && typeof data.modelId === 'number') {
      data.providerId = data.modelId
      data.modelId = data.modelName || null
    }
    if (!data.short_memory) data.short_memory = defaults.short_memory
  }
  if (node.type === 'classifier') {
    if (!data.conditions?.length) {
      data.conditions = [{ id: createConditionId(), subject: '' }]
    }
    if (!data.mode_switch) data.mode_switch = 'efficient'
    if (!data.short_memory) data.short_memory = defaults.short_memory
  }
  return { ...node, data }
}

function getDefaultNodeData(type) {
  return buildDefaultNodeData(type)
}

// 选中 LLM 节点时加载模型列表
watch(
  () => {
    const type = selectedNode.value?.type
    if (type === 'llm' || type === 'classifier' || type === 'parameter_extractor') {
      return selectedNode.value?.data?.providerId
    }
    return null
  },
  async (providerId) => {
    if (providerId) {
      await loadLlmModels(providerId)
    } else {
      llmModelList.value = []
    }
  }
)

// 实时校验工作流配置
watch(
  () => getWorkflowSnapshot(),
  () => {
    validateWorkflow(false)
    scheduleAutoSave()
  }
)

// 获取节点颜色
function getNodeColor(nodeOrType) {
  const type = typeof nodeOrType === 'string' ? nodeOrType : nodeOrType?.type
  return metaGetNodeColor(type)
}

function getNodeTitle(type) {
  return metaGetNodeTitle(type)
}

// 根据节点ID获取节点标题
function getNodeTitleById(nodeId) {
  if (!nodeId) return null
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return null
  return node.data?.label || getNodeTitle(node.type)
}

// 获取节点错误
function getNodeErrors(nodeId) {
  return validationErrors.value.filter(e => e.nodeId === nodeId)
}

// 获取可选的目标节点
function getTargetNodes() {
  return nodes.value.filter(n => n.id !== selectedNode.value?.id)
}

// 根据节点 ID 获取展示信息
function getNodeById(nodeId) {
  return nodes.value.find(n => n.id === nodeId)
}

function getEdgeSourceLabel(edge) {
  const node = getNodeById(edge.source)
  return node?.data?.label || getNodeTitle(node?.type) || edge.source
}

function getEdgeTargetLabel(edge) {
  const node = getNodeById(edge.target)
  return node?.data?.label || getNodeTitle(node?.type) || edge.target
}

function getEdgeSourceType(edge) {
  const node = getNodeById(edge.source)
  return node ? getNodeTitle(node.type) : '未知'
}

function getEdgeTargetType(edge) {
  const node = getNodeById(edge.target)
  return node ? getNodeTitle(node.type) : '未知'
}

function clearEdgeSelection() {
  selectedEdge.value = null
  try {
    removeSelectedEdges(getSelectedEdges())
  } catch (_) { /* VueFlow 未就绪时忽略 */ }
}

function filterKnowledgeOption(input, option) {
  const k = knowledgeList.value.find(item => String(item.id) === String(option.value))
  if (!k) return false
  const keyword = (input || '').toLowerCase()
  const text = `${k.name} ${k.description || ''} ${k.embeddingModel || ''}`.toLowerCase()
  return text.includes(keyword)
}

function filterToolOption(input, option) {
  const t = tools.value.find(item => String(item.id) === String(option.value))
  if (!t) return false
  const keyword = (input || '').toLowerCase()
  const text = `${t.displayName || ''} ${t.name || ''} ${t.description || ''}`.toLowerCase()
  return text.includes(keyword)
}

function getToolTypeLabel(toolType) {
  const code = toolType?.code || toolType
  const labels = {
    builtin: '内置',
    http: 'HTTP',
    mcp: 'MCP',
    script: '脚本'
  }
  return labels[code] || code || '工具'
}

// 拖拽开始
function onDragStart(event, nodeType) {
  event.dataTransfer.setData('nodeType', nodeType)
}

// 拖拽放置
function onDrop(event) {
  if (isVersionPreview.value) return
  const nodeType = event.dataTransfer.getData('nodeType')
  if (!nodeType) return

  // 记录历史用于撤回
  recordHistory()

  const rect = event.currentTarget.getBoundingClientRect()
  const position = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  }

  const newNode = {
    id: `node_${Date.now()}`,
    type: nodeType,
    position,
    data: getDefaultNodeData(nodeType)
  }

  nodes.value = [...nodes.value, newNode]
  triggerRef(nodes)
  scheduleFitView()
}

// 同步节点变更（含拖动后的 position），保证未保存检测与保存能带上坐标
function onNodesChange(changes) {
  if (isVersionPreview.value || !changes?.length) return
  const nextNodes = applyNodeChanges(changes, nodes.value)
  nodes.value = nextNodes
  triggerRef(nodes)
}

function onNodeDragStart({ node }) {
  draggingNode.value = node
  isNodeDragging.value = true
  dragOverTrash.value = false
  recordHistory()
}

function onNodeDrag({ event }) {
  if (!isNodeDragging.value || !event || !trashRef.value) return
  const rect = trashRef.value.getBoundingClientRect()
  dragOverTrash.value =
    event.clientX >= rect.left &&
    event.clientX <= rect.right &&
    event.clientY >= rect.top &&
    event.clientY <= rect.bottom
}

function onNodeDragStop({ node }) {
  if (dragOverTrash.value && node && node.type !== 'start' && node.type !== 'end') {
    removeNodeById(node.id, { skipHistory: true })
    message.success('节点已删除')
  }
  isNodeDragging.value = false
  dragOverTrash.value = false
  draggingNode.value = null
}

function removeNodeById(nodeId, { skipHistory = false } = {}) {
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node || node.type === 'start' || node.type === 'end') return
  if (!skipHistory) recordHistory()

  nodes.value = nodes.value.filter(n => n.id !== nodeId)
  edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  triggerRef(nodes)
  triggerRef(edges)
  if (selectedNode.value?.id === nodeId) {
    selectedNode.value = null
  }
  clearEdgeSelection()
  scheduleFitView()
}

// 连接节点
function onConnect(params) {
  if (isVersionPreview.value) return
  recordHistory()
  const newEdge = {
    id: `edge_${params.source}_${params.target}_${params.sourceHandle || 'default'}_${Date.now()}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    selectable: true,
    style: { strokeWidth: 2, stroke: '#94a3b8' }
  }
  edges.value = [...edges.value, newEdge]
  triggerRef(edges)
}

// 点击节点
function onNodeClick(event) {
  clearEdgeSelection()
  selectedEdge.value = null
  selectedNode.value = event.node
}

// 点击连线
function onEdgeClick(event) {
  selectedNode.value = null
  selectedEdge.value = event.edge
  try {
    removeSelectedEdges(getSelectedEdges())
    addSelectedEdges([event.edge])
  } catch (_) { /* ignore */ }
}

// 点击空白区域
function onPaneClick() {
  closeNodePanel()
  clearEdgeSelection()
}

// 同步节点数据
function syncNodes() {
  triggerRef(nodes)
  scheduleAutoSave()
}

// LLM 提供商变更
async function onLlmProviderChange(providerId) {
  if (isVersionPreview.value) return
  const provider = providers.value.find(p => p.id === providerId)
  selectedNode.value.data.providerName = provider?.name || ''
  selectedNode.value.data.modelId = null
  selectedNode.value.data.modelName = ''
  await loadLlmModels(providerId)
  syncNodes()
}

async function loadLlmModels(providerId) {
  if (!providerId) {
    llmModelList.value = []
    return
  }
  try {
    const res = await getModelsByProvider(providerId)
    llmModelList.value = (res.data || []).filter(m => {
      const type = m.type?.code || m.type
      return type === 'llm'
    })
  } catch {
    llmModelList.value = []
  }
}

function onLlmModelChange(modelId) {
  if (isVersionPreview.value) return
  const model = llmModelList.value.find(m => m.modelId === modelId)
  selectedNode.value.data.modelName = model?.name || modelId || ''
  syncNodes()
}

// 知识库选择变化：回显知识库 RAG 配置
async function onKnowledgeChange(value) {
  const knowledge = knowledgeList.value.find(k => String(k.id) === String(value))
  selectedNode.value.data.knowledgeName = knowledge?.name || ''
  selectedNode.value.data.knowledgeBaseTopK = null
  selectedNode.value.data.knowledgeBaseThreshold = null
  if (value) {
    try {
      const detail = await getKnowledge(value)
      const kb = detail.data
      let cfg = {}
      if (kb?.config) {
        cfg = typeof kb.config === 'string' ? JSON.parse(kb.config) : kb.config
      }
      const topK = cfg.ragTopK ?? 5
      const threshold = cfg.ragThreshold ?? 0.5
      selectedNode.value.data.knowledgeBaseTopK = topK
      selectedNode.value.data.knowledgeBaseThreshold = threshold
      if (!selectedNode.value.data.overrideConfig) {
        selectedNode.value.data.topK = topK
        selectedNode.value.data.threshold = threshold
      }
    } catch (e) {
      console.warn('加载知识库配置失败', e)
    }
  }
  syncNodes()
}

// 工具选择变化
function onToolChange(value) {
  const tool = tools.value.find(t => t.id === value)
  selectedNode.value.data.toolName = tool?.displayName || tool?.name || ''
  syncNodes()
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function clearNodeDebugStatus() {
  nodes.value = nodes.value.map(n => ({
    ...n,
    data: { ...n.data, debugStatus: null }
  }))
  triggerRef(nodes)
}

function setNodeDebugStatus(nodeId, status) {
  nodes.value = nodes.value.map(n => {
    if (n.id !== nodeId) return n
    return { ...n, data: { ...n.data, debugStatus: status } }
  })
  triggerRef(nodes)
  const node = nodes.value.find(n => n.id === nodeId)
  if (node) focusNode(node)
}

async function animateWorkflowTest(events) {
  clearNodeDebugStatus()
  testAnimating.value = true
  for (const ev of events || []) {
    if (ev.type === 'workflow_node_start' && ev.nodeId) {
      setNodeDebugStatus(ev.nodeId, 'executing')
      testCurrentNodeId.value = ev.nodeId
      await sleep(700)
    }
    if (ev.type === 'workflow_node_complete' && ev.nodeId) {
      setNodeDebugStatus(ev.nodeId, ev.success === false ? 'fail' : 'success')
      await sleep(400)
    }
  }
  testAnimating.value = false
}

// 删除选中节点
function deleteSelectedNode() {
  if (!selectedNode.value) return
  removeNodeById(selectedNode.value.id)
}

// 删除选中连线
function deleteSelectedEdge() {
  if (!selectedEdge.value) return
  recordHistory()

  const edgeId = selectedEdge.value.id
  edges.value = edges.value.filter(e => e.id !== edgeId)
  triggerRef(edges)
  selectedEdge.value = null
}

// 验证工作流
function validateWorkflow(showToast = true) {
  const errors = []

  // 1. 检查 START 节点
  const startNodes = nodes.value.filter(n => n.type === 'start')
  if (startNodes.length === 0) {
    errors.push({ nodeId: null, field: 'start', message: '缺少开始节点' })
  } else if (startNodes.length > 1) {
    errors.push({ nodeId: null, field: 'start', message: '只能有一个开始节点' })
  }

  // 2. 检查 END 节点
  const endNodes = nodes.value.filter(n => n.type === 'end')
  if (endNodes.length === 0) {
    errors.push({ nodeId: null, field: 'end', message: '缺少结束节点' })
  }

  // 3. 检查节点连接
  const connectedIds = new Set()
  edges.value.forEach(e => {
    connectedIds.add(e.source)
    connectedIds.add(e.target)
  })
  nodes.value.forEach(n => {
    if (n.type !== 'start' && !connectedIds.has(n.id)) {
      errors.push({ nodeId: n.id, field: 'connection', message: '节点未连接到工作流' })
    }
  })

  // 4. 检查节点配置
  nodes.value.forEach(n => {
    if (n.type === 'llm') {
      if (!n.data.providerId) errors.push({ nodeId: n.id, field: 'providerId', message: '请选择模型提供商' })
      if (!n.data.modelId) errors.push({ nodeId: n.id, field: 'modelId', message: '请选择模型' })
      if (!n.data.promptTemplate) errors.push({ nodeId: n.id, field: 'promptTemplate', message: '请填写提示词' })
    }
    if (n.type === 'retrieval') {
      if (!n.data.knowledgeId) errors.push({ nodeId: n.id, field: 'knowledgeId', message: '请选择知识库' })
    }
    if (n.type === 'tool') {
      if (!n.data.toolId) errors.push({ nodeId: n.id, field: 'toolId', message: '请选择工具' })
    }
    if (n.type === 'classifier') {
      if (!n.data.providerId) errors.push({ nodeId: n.id, field: 'providerId', message: '请选择模型提供商' })
      if (!n.data.modelId) errors.push({ nodeId: n.id, field: 'modelId', message: '请选择模型' })
      if (!n.data.inputVariable) errors.push({ nodeId: n.id, field: 'inputVariable', message: '请配置输入变量' })
      const emptyIntent = (n.data.conditions || []).some(c => c.id !== 'default' && !c.subject?.trim())
      if (emptyIntent || !(n.data.conditions || []).length) {
        errors.push({ nodeId: n.id, field: 'conditions', message: '请配置至少一个意图分类' })
      }
    }
    if (n.type === 'api' && !n.data.url) {
      errors.push({ nodeId: n.id, field: 'url', message: '请填写 API URL' })
    }
    if (n.type === 'output' && !n.data.output?.trim()) {
      errors.push({ nodeId: n.id, field: 'output', message: '请填写输出内容' })
    }
    if (n.type === 'parameter_extractor') {
      if (!n.data.providerId) errors.push({ nodeId: n.id, field: 'providerId', message: '请选择模型提供商' })
      if (!n.data.modelId) errors.push({ nodeId: n.id, field: 'modelId', message: '请选择模型' })
      if (!n.data.inputVariable) errors.push({ nodeId: n.id, field: 'inputVariable', message: '请配置输入变量' })
    }
    if (n.type === 'app_component' && !n.data.componentCode?.trim()) {
      errors.push({ nodeId: n.id, field: 'componentCode', message: '请填写组件标识' })
    }
  })

  validationErrors.value = errors

  if (!showToast) return errors

  if (errors.length === 0) {
    message.success('工作流配置验证通过')
  } else {
    notification.warning({
      message: '工作流配置不完整',
      description: `发现 ${errors.length} 个配置错误，请完善后保存`
    })
  }

  return errors
}

async function saveDraft() {
  saving.value = true
  try {
    await saveWorkflowDraft(agentId, buildWorkflowPayload())
    markWorkflowSaved()
    if (workflowStatus.value === 'published') {
      workflowStatus.value = 'published_editing'
    }
    message.success('工作流已暂存（草稿）')
  } catch (e) {
    notification.error({ message: '暂存失败', description: e.message })
  } finally {
    saving.value = false
  }
}

function openPublishModal() {
  const errors = validateWorkflow()
  if (errors.length > 0) return
  publishDescription.value = ''
  publishModalVisible.value = true
}

async function confirmPublishWorkflow() {
  saving.value = true
  try {
    const payload = buildWorkflowPayload()
    const desc = publishDescription.value?.trim()
    if (desc) {
      payload.publishDescription = desc
    }
    const res = await publishWorkflowApi(agentId, payload)
    workflowStatus.value = 'published'
    publishedVersion.value = res.data?.version || publishedVersion.value + 1
    selectedVersion.value = 'draft'
    markWorkflowSaved()
    publishModalVisible.value = false
    message.success(`工作流已发布（v${publishedVersion.value}）`)
  } catch (e) {
    notification.error({ message: '发布失败', description: e.message || e.response?.data?.message })
    return Promise.reject(e)
  } finally {
    saving.value = false
  }
}

async function openVersionDrawer() {
  versionVisible.value = true
  await nextTick()
  ensureVersionPanelPosition()
  versionLoading.value = true
  try {
    const res = await listWorkflowVersions(agentId)
    versionList.value = res.data || []
  } catch (e) {
    notification.error({ message: '加载版本失败', description: e.message })
  } finally {
    versionLoading.value = false
  }
}

async function selectVersion(version) {
  selectedVersion.value = version
  selectedNode.value = null
  clearEdgeSelection()
  testVisible.value = false

  if (version === 'draft') {
    try {
      const wfRes = await getWorkflowConfig(agentId)
      workflowStatus.value = wfRes.data.status || 'draft'
      applyWorkflowGraph(wfRes.data.draft)
      markWorkflowSaved()
      scheduleFitView(false)
    } catch (e) {
      notification.error({ message: '加载草稿失败', description: e.message })
    }
    return
  }

  try {
    const res = await getWorkflowVersionDetail(agentId, version)
    applyWorkflowGraph(res.data)
    markWorkflowSaved()
    versionVisible.value = true
    await nextTick()
    ensureVersionPanelPosition()
    scheduleFitView(false)
  } catch (e) {
    notification.error({ message: '加载版本失败', description: e.message })
  }
}

async function backToCurrentDraft() {
  await selectVersion('draft')
  message.success('已回到当前版本')
}

function ensureVersionPanelPosition() {
  if (versionPanelLayoutReady) return
  const canvas = canvasAreaRef.value
  if (!canvas) return
  const x = Math.max(12, canvas.clientWidth - VERSION_PANEL_WIDTH - 12)
  versionPanelPos.value = { x, y: 48 }
  versionPanelLayoutReady = true
}

function onVersionPanelDragStart(e) {
  if (!canvasAreaRef.value) return
  versionPanelDragState = {
    startX: e.clientX,
    startY: e.clientY,
    originX: versionPanelPos.value.x,
    originY: versionPanelPos.value.y,
  }
  document.addEventListener('mousemove', onVersionPanelDragMove)
  document.addEventListener('mouseup', onVersionPanelDragEnd)
}

function onVersionPanelDragMove(e) {
  if (!versionPanelDragState || !canvasAreaRef.value) return
  const canvas = canvasAreaRef.value
  const maxX = canvas.clientWidth - VERSION_PANEL_WIDTH - 12
  const maxY = Math.max(12, canvas.clientHeight - 120)
  const dx = e.clientX - versionPanelDragState.startX
  const dy = e.clientY - versionPanelDragState.startY
  versionPanelPos.value = {
    x: Math.min(maxX, Math.max(12, versionPanelDragState.originX + dx)),
    y: Math.min(maxY, Math.max(12, versionPanelDragState.originY + dy)),
  }
}

function onVersionPanelDragEnd() {
  versionPanelDragState = null
  document.removeEventListener('mousemove', onVersionPanelDragMove)
  document.removeEventListener('mouseup', onVersionPanelDragEnd)
}

function overwriteDraftFromVersion() {
  if (selectedVersion.value === 'draft') return
  const version = selectedVersion.value
  Modal.confirm({
    title: '覆盖当前草稿',
    content: `确定将历史版本 v${version} 的内容覆盖到当前草稿吗？此操作会替换未发布的编辑内容，且不可撤销。`,
    okText: '确认覆盖',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await restoreWorkflowVersion(agentId, version)
        const wfRes = await getWorkflowConfig(agentId)
        workflowStatus.value = wfRes.data.status || 'published_editing'
        await selectVersion('draft')
        message.success(`已用 v${version} 覆盖当前草稿`)
      } catch (e) {
        notification.error({ message: '覆盖失败', description: e.message })
      }
    }
  })
}

function formatVersionTime(val) {
  if (val == null || val === '') return ''
  const raw = String(val)
  const normalized = raw.includes('T') && !raw.endsWith('Z')
    ? raw.replace(/(\.\d{3})\d*/, '$1')
    : raw
  const d = new Date(normalized)
  if (Number.isNaN(d.getTime())) {
    return raw.slice(0, 19).replace('T', ' ')
  }
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatVersionDesc(item) {
  const parts = [`${item.nodeCount || 0} 节点`, `${item.edgeCount || 0} 连线`]
  const time = formatVersionTime(item.publishedAt)
  if (time) parts.push(time)
  return parts.join(' · ')
}

async function runWorkflowTest() {
  if (!testInput.value?.trim()) {
    message.warning('请输入测试问题')
    return
  }
  const errors = validateWorkflow(false)
  if (errors.length > 0) {
    notification.warning({
      message: '工作流配置未通过校验',
      description: `发现 ${errors.length} 个错误，请先完善配置后再测试`
    })
    return
  }

  testVisible.value = true
  testRunning.value = true
  testResult.value = null
  testCurrentNodeId.value = null
  clearNodeDebugStatus()
  try {
    const res = await testWorkflow(agentId, {
      input: testInput.value,
      useDraft: testUseDraft.value,
      graph: buildWorkflowPayload()
    })
    testResult.value = res.data
    await animateWorkflowTest(res.data?.nodeEvents || [])
    message.success('测试运行完成')
  } catch (e) {
    notification.error({ message: '测试失败', description: e.message })
    clearNodeDebugStatus()
  } finally {
    testRunning.value = false
  }
}

// 返回
function goBack() {
  const navigate = () => router.push(`/agents/${agentId}`)
  if (isDirty.value) {
    Modal.confirm({
      title: '未保存的修改',
      content: '当前工作流修改后还未保存，是否退出？',
      okText: '退出',
      cancelText: '取消',
      onOk: navigate
    })
  } else {
    navigate()
  }
}
</script>

<style scoped>
.workflow-edit-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.publish-tag { flex-shrink: 0; }
.version-panel-float {
  position: absolute;
  z-index: 12;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow: hidden;
}
.version-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
  cursor: default;
}
.version-panel-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #94a3b8;
  border-radius: 4px;
  cursor: grab;
  flex-shrink: 0;
  user-select: none;
}
.version-panel-drag-handle:hover {
  color: #64748b;
  background: #f1f5f9;
}
.version-panel-drag-handle:active {
  cursor: grabbing;
}
.version-panel-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}
.version-panel-close {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #94a3b8;
  padding: 4px;
  line-height: 1;
}
.version-panel-close:hover { color: #475569; }
.version-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.version-panel-footer {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid #f1f5f9;
}
.version-preview-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  padding: 8px 16px;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  font-size: 13px;
  color: #92400e;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  pointer-events: none;
}
.version-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.version-item:hover { background: #f3f4f6; }
.version-item.active { background: #eef2ff; border: 1px solid #c7d2fe; }
.version-item.draft { margin-bottom: 4px; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-item-title { font-weight: 600; font-size: 14px; color: #1f2937; }
.version-item-note {
  font-size: 13px;
  color: #334155;
  line-height: 1.45;
  margin-bottom: 4px;
  word-break: break-word;
}
.version-item-desc { font-size: 12px; color: #9ca3af; }
.publish-modal-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #71717a;
}
.preview-readonly-alert { margin-bottom: 12px; }
.panel-body-readonly {
  position: relative;
}
.panel-body-readonly::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 15;
  cursor: not-allowed;
  background: transparent;
}
.panel-body-readonly .preview-readonly-alert {
  position: relative;
  z-index: 16;
}
.toolbar-status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.btn-validate { color: #6366f1; }
.auto-save-hint { font-size: 12px; color: #94a3b8; white-space: nowrap; }
.auto-save-hint.saving { color: #6366f1; }
.test-alert { margin-bottom: 12px; }
.test-current-node { font-size: 13px; color: #6366f1; margin-bottom: 12px; padding: 8px 12px; background: #eef2ff; border-radius: 6px; }
.test-event.active { background: #eef2ff; color: #4338ca; font-weight: 600; }
.test-event-type { margin-right: 6px; }

.canvas-area :deep(.vue-flow__handle) {
  width: 16px !important;
  height: 16px !important;
  border: 2px solid #6366f1 !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: width 0.15s, height 0.15s, background 0.15s;
}
.canvas-area :deep(.vue-flow__handle:hover) {
  width: 22px !important;
  height: 22px !important;
  background: #6366f1 !important;
}

.canvas-node-list {
  margin-top: 10px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}
.canvas-node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.canvas-node-item:hover,
.canvas-node-item.active {
  background: #f3f4f6;
}
.canvas-node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.canvas-node-name {
  flex: 1;
  color: #1f2937;
  font-weight: 500;
}
.canvas-node-type {
  color: #9ca3af;
  font-size: 11px;
}
.conv-param-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.test-output {
  background: #f9fafb;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-size: 12px;
}
.test-event {
  font-size: 12px;
  color: #6b7280;
  padding: 4px 0;
}
.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #9ca3af;
}
.text-muted {
  font-size: 12px;
  color: #9ca3af;
}
.kb-config-preview {
  font-size: 12px;
  color: #374151;
}

.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
}

.btn-back:hover {
  background: #f9fafb;
}

.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.toolbar-status {
  flex: 1;
  text-align: center;
}

.status-valid { color: #22c55e; font-size: 13px; }
.status-error { color: #ef4444; font-size: 13px; }
.status-error.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.status-error.clickable:hover {
  background: #fef2f2;
}
.status-empty { color: #9ca3af; font-size: 13px; }

/* 错误下拉面板 */
.error-dropdown {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 400px;
}

.error-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  border-bottom: 1px solid #e5e7eb;
}

.error-list {
  padding: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.error-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}

.error-node {
  color: #7c3aed;
  font-weight: 600;
}

.error-field {
  color: #6b7280;
  background: #e5e7eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.error-msg {
  color: #dc2626;
  flex: 1;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.workflow-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧节点面板 */
.node-panel {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}

.node-panel.collapsed {
  width: 40px;
}

.node-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.node-panel.collapsed .panel-header {
  padding: 12px 8px;
  justify-content: center;
}

.btn-collapse {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}

.node-panel .panel-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.node-group {
  margin-top: 12px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  padding: 4px 0;
}

/* 中间画布 */
.canvas-area {
  flex: 1;
  position: relative;
  background: #fff;
}

/* 拖动节点删除区 */
.workflow-trash {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 120px;
  padding: 14px 24px;
  border-radius: 12px;
  border: 2px dashed #d4d4d8;
  background: rgba(255, 255, 255, 0.95);
  color: #71717a;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: border-color 0.15s, background 0.15s, color 0.15s, transform 0.15s;
  pointer-events: none;
}

.workflow-trash .trash-icon {
  font-size: 28px;
}

.workflow-trash .trash-label {
  font-size: 12px;
  white-space: nowrap;
}

.workflow-trash.is-over:not(.is-disabled) {
  border-color: #ef4444;
  border-style: solid;
  background: #fef2f2;
  color: #dc2626;
  transform: translateX(-50%) scale(1.05);
}

.workflow-trash.is-disabled {
  opacity: 0.65;
}

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #9ca3af;
  font-size: 14px;
}

/* 右侧配置面板 */
.config-panel {
  width: 380px;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.config-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.node-type-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-name {
  font-weight: 600;
  color: #1f2937;
}

.btn-close {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: #6b7280;
}

.config-panel .panel-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.node-errors {
  margin-bottom: 12px;
}

.error-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 4px;
  color: #dc2626;
  font-size: 12px;
  margin-bottom: 4px;
}

.config-panel .panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
}

/* 分支配置 */
.branches-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.branch-item {
  background: #f9fafb;
  border-radius: 6px;
  padding: 8px;
}

.branch-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.branch-row:last-child {
  margin-bottom: 0;
}

.param-value {
  font-size: 12px;
  color: #6b7280;
  min-width: 40px;
}

.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;
  margin-left: 8px;
}

.btn-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  font-size: 16px;
}

.btn-help:hover {
  background: #f3f4f6;
  color: #7c3aed;
}

.edge-icon {
  background: #f3e8ff !important;
  color: #7c3aed !important;
}

.edge-detail-card {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
}

.edge-detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}

.edge-detail-label {
  font-size: 12px;
  color: #6b7280;
}

.edge-detail-value {
  font-size: 13px;
  color: #374151;
  word-break: break-all;
}

.edge-detail-value.mono,
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.edge-connection-flow {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.edge-node-box {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.edge-node-box.source {
  border-color: #c4b5fd;
}

.edge-node-box.target {
  border-color: #86efac;
}

.edge-node-role {
  font-size: 11px;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.edge-node-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.edge-node-type {
  font-size: 12px;
  color: #6b7280;
}

.edge-handle-tag {
  font-size: 11px;
  color: #7c3aed;
  background: #f5f3ff;
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.edge-arrow {
  display: flex;
  align-items: center;
  color: #9ca3af;
  font-size: 16px;
  flex-shrink: 0;
}

.edge-delete-footer {
  margin-top: 16px;
  border-top: none;
  padding: 0;
}

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

.node-help-content li {
  margin-bottom: 4px;
}

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

<style>
/* 全局 VueFlow 样式 */
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';

/* 缩略图：外层 SVG 随内容自适应，避免宽 viewBox 导致左右留白 */
.workflow-minimap.vue-flow__minimap {
  display: inline-block !important;
  width: fit-content !important;
  height: fit-content !important;
  max-width: min(280px, 32vw);
  max-height: 200px;
  line-height: 0;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.workflow-minimap.vue-flow__minimap > svg {
  display: block !important;
  width: auto !important;
  height: auto !important;
  max-width: min(280px, 32vw);
  max-height: 200px;
}

.workflow-minimap .vue-flow__minimap-mask {
  fill: rgba(124, 58, 237, 0.08);
}

/* 选中连线高亮 */
.vue-flow__edge.selected .vue-flow__edge-path {
  stroke: #7c3aed !important;
  stroke-width: 3 !important;
}

/* 知识库/工具下拉富选项 */
.workflow-resource-dropdown .ant-select-item {
  padding: 8px 10px !important;
  height: auto !important;
  min-height: auto !important;
}

.resource-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0;
}

.resource-option-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.resource-option-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.resource-option-icon.knowledge {
  color: #4f46e5;
}

.resource-option-icon.tool {
  color: #059669;
}

.resource-option-title {
  font-weight: 600;
  font-size: 13px;
  color: #1f2937;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-option-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resource-option-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: #9ca3af;
}

.resource-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
}

.resource-tag.enabled {
  background: #dcfce7;
  color: #166534;
}

.resource-tag.type {
  background: #e0e7ff;
  color: #4338ca;
}

.node-id-display {
  display: block;
  padding: 6px 10px;
  font-size: 12px;
  color: #52525b;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  word-break: break-all;
}
</style>