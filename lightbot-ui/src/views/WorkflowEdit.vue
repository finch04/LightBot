<template>
  <div class="workflow-edit-page">
    <WorkflowEditToolbar
      :agent-name="agent?.name"
      :workflow-status="workflowStatus"
      :published-version="publishedVersion"
      :validation-errors="validationErrors"
      :node-count="nodes.length"
      :is-version-preview="isVersionPreview"
      :viewing-test-history="viewingTestHistory"
      :can-undo="canUndo"
      :saving="saving"
      :auto-saving="autoSaving"
      :last-auto-save-time="lastAutoSaveTime"
      :get-node-title-by-id="getNodeTitleById"
      :format-auto-save-time="formatAutoSaveTime"
      :get-status-label="getWorkflowStatusLabel"
      @back="goBack"
      @format-layout="formatWorkflowLayout"
      @validate="validateWorkflow"
      @back-to-draft="backToCurrentDraft"
      @open-version="openVersionDrawer"
      @undo="undoAction"
      @open-global-config="globalConfigVisible = true"
      @open-test="testVisible = true"
      @exit-test-history="backToLiveTest"
      @save-draft="saveDraft"
      @open-publish="openPublishModal"
      @open-workflow-exchange="openWorkflowExchange"
    />

    <!-- 加载遮罩 -->
    <Transition name="workflow-loading-fade">
      <div v-if="!workflowLoaded" class="workflow-loading-overlay">
        <div class="workflow-loading-spinner">
          <div class="loading-dots">
            <span /><span /><span />
          </div>
          <div class="loading-text">正在加载工作流...</div>
        </div>
      </div>
    </Transition>

    <div class="workflow-content">
      <WorkflowEditLeftPanel
        v-model:panel-collapsed="panelCollapsed"
        v-model:left-panel-tab="leftPanelTab"
        v-model:canvas-node-search="canvasNodeSearch"
        v-model:node-search="nodeSearch"
        :filtered-canvas-nodes="filteredCanvasNodes"
        :filtered-node-groups="filteredNodeGroups"
        :selected-node-id="selectedNode?.id"
        :get-node-color="getNodeColor"
        :get-node-title="getNodeTitle"
        :get-node-meta="getNodeMeta"
        @open-node-help="nodeHelpVisible = true"
        @open-tab="openLeftPanelTab"
        @focus-node="focusNode"
        @drag-start="onDragStart"
      />

      <div class="workflow-canvas-shell">
        <WorkflowUndoToastStack />
        <WorkflowEditCanvas
          ref="workflowCanvasRef"
          :flow-id="WORKFLOW_FLOW_ID"
          :nodes="nodes"
          v-model:edges="edges"
          :edge-types="edgeTypes"
          :default-edge-options="defaultEdgeOptions"
          :is-valid-connection="isValidWorkflowConnectionFn"
          :is-version-preview="isVersionPreview"
          :is-node-dragging="isNodeDragging"
          :drag-over-trash="dragOverTrash"
          :can-delete-dragged-node="canDeleteDraggedNode"
          :trash-label="trashDeleteLabel"
          :get-node-color="getNodeColor"
          :edge-insert-anchor-edge="edgeInsertAnchorEdge"
          :edge-insert-label-style="edgeInsertLabelStyle"
          :version-visible="versionVisible"
          :version-panel-style="versionPanelStyle"
          :version-list="publishedVersionList"
          :version-loading="versionLoading"
          :selected-version="selectedVersion"
          :format-version-desc="formatVersionDesc"
          @edges-change="onEdgesChange"
          @connect="onConnect"
          @edge-update="onEdgeUpdate"
          @nodes-change="onNodesChange"
          @node-drag-start="onNodeDragStart"
          @node-drag="onNodeDrag"
          @node-drag-stop="onNodeDragStop"
          @drop="onDrop"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @edge-mouse-enter="onEdgeMouseEnter"
          @edge-mouse-move="onEdgeMouseMove"
          @edge-mouse-leave="onEdgeMouseLeave"
          @pane-click="onPaneClick"
          @edge-insert-pointer-enter="onEdgeInsertPointerEnter"
          @edge-insert-pointer-leave="onEdgeInsertPointerLeave"
          @insert-node-on-edge="onInsertNodeOnEdge"
          @edge-insert-menu-open="onEdgeInsertMenuOpen"
          @edge-insert-menu-close="onEdgeInsertMenuClose"
          @version-panel-drag-start="onVersionPanelDragStart"
          @close-version-panel="versionVisible = false"
          @select-version="selectVersion"
          @overwrite-draft="overwriteDraftFromVersion"
        />

        <div v-if="viewingTestHistory" class="test-history-viewer-overlay">
          <div class="test-history-banner">
            <span>{{ testAnimating ? '正在回放历史执行过程（与当前草稿可能不一致）' : '正在查看历史测试快照（与当前草稿可能不一致）' }}</span>
            <a-button v-if="testAnimating" size="small" type="link" class="test-history-skip-btn" @click="skipTestHistoryReplay">跳过动画</a-button>
          </div>
          <WorkflowViewerCanvas
            v-if="testHistoryViewerNodes.length"
            ref="testHistoryViewerRef"
            flow-id="lightbot-workflow-test-history"
            :nodes="testHistoryViewerNodes"
            :edges="testHistoryViewerEdges"
            :node-states="testHistoryNodeStates"
            :highlighted-edge-ids="testHistoryHighlightedEdges"
            :selected-node-id="testCurrentNodeId"
          />
          <div v-else class="test-history-empty">该记录无工作流图快照，请在右侧查看执行轨迹</div>
        </div>
      </div>

      <ResizableSidePanel
        v-if="selectedEdge"
        storage-key="workflow-edge-detail-panel-width"
        :default-width="480"
        :min-width="320"
        :max-width="720"
      >
        <WorkflowEdgeDetailPanel
          :edge="selectedEdge"
          :is-version-preview="isVersionPreview"
          :edge-target-candidates="edgeTargetCandidates"
          :edge-source-handle-options="edgeSourceHandleOptions"
          :handle-in="HANDLE_IN"
          :handle-out="HANDLE_OUT"
          :get-edge-source-label="getEdgeSourceLabel"
          :get-edge-source-type="getEdgeSourceType"
          :get-edge-target-label="getEdgeTargetLabel"
          :get-edge-target-type="getEdgeTargetType"
          :get-handle-display-name="getHandleDisplayName"
          :get-node-title="getNodeTitle"
          @close="clearEdgeSelection"
          @target-change="onEdgeTargetChange"
          @source-handle-change="onEdgeSourceHandleChange"
          @delete="deleteSelectedEdge"
        />
      </ResizableSidePanel>

      <ResizableSidePanel
        v-else-if="selectedNode && !testAnimating && !testRunning && !testStreaming"
        storage-key="workflow-node-detail-panel-width"
        :default-width="480"
        :min-width="320"
        :max-width="720"
      >
        <WorkflowNodeDetailPanel
          :node="selectedNode"
          :nodes="nodes"
          :edges="edges"
          :is-version-preview="isVersionPreview"
          :can-test-selected-node="canTestSelectedNode"
          :node-errors="getNodeErrors(selectedNode.id)"
          :knowledge-list="knowledgeList"
          :tools="tools"
          :target-nodes="getTargetNodes()"
          :filter-knowledge-option="filterKnowledgeOption"
          :filter-tool-option="filterToolOption"
          :get-tool-type-label="getToolTypeLabel"
          :get-node-color="getNodeColor"
          :get-node-title="getNodeTitle"
          :is-group-builtin-node="isGroupBuiltinNode"
          @close="closeNodePanel"
          @open-example="openNodeExampleModal"
          @open-test="openNodeTestDrawer"
          @copy="copySelectedNode"
          @sync="syncNodes"
          @knowledge-change="onKnowledgeChange"
          @tool-change="onToolChange"
          @remove-source-handles="onRemoveSourceHandles"
          @delete="deleteSelectedNode"
        />
      </ResizableSidePanel>
    </div>

    <!-- 弹窗 / 抽屉：必须放在根 div 内（antd Modal/Drawer 内部用 Teleport 挂到 body，
         不影响布局），否则 WorkflowEdit 会变成多根组件，导致外层 <transition mode="out-in">
         无法绑定 transitionend，leave 卡死 → 切走后主内容区空白 -->
    <NodeExampleModal
      v-model:open="nodeExampleVisible"
      :example="nodeExampleContent"
      :readonly="isVersionPreview"
      :allow-apply="nodeExampleAllowApply"
      @apply="applyNodeExampleConfig"
    />

    <NodeSingleTestDrawer
      v-model:open="nodeTestVisible"
      :agent-id="agentId"
      :node="selectedNode"
      :graph-payload="workflowGraphPayload"
      @test-complete="onNodeTestComplete"
    />

    <WorkflowNodeHelpModal v-model:open="nodeHelpVisible" />

    <a-modal v-model:open="workflowExchangeVisible" title="工作流互通" :width="720" :footer="null">
      <a-tabs v-model:active-key="workflowExchangePlatformTab" class="workflow-exchange-platform-tabs">
        <a-tab-pane key="dify" tab="Dify">
          <a-tabs v-model:active-key="difyActiveTab" @change="onDifyTabChange">
            <a-tab-pane key="import" tab="导入">
          <a-alert type="info" show-icon class="dify-modal-intro">
            <template #message>导入逻辑说明</template>
            <template #description>
              上传 Dify Workflow YAML 后先预检，再写入当前草稿。常见节点会转换为 LightBot 节点；没有等价运行时的节点将保留已脱敏的 Dify 原始结构，确保可以再次导出。模型、知识库、工具和认证需要重新绑定，导入不会覆盖已发布版本。
            </template>
          </a-alert>
          <a-upload :max-count="1" accept=".yml,.yaml" :before-upload="selectDifyImportFile" :file-list="difyImportFileList" @remove="clearDifyImportFile">
            <a-button><UploadOutlined /> 选择 Dify YAML</a-button>
          </a-upload>
          <a-spin :spinning="difyImportLoading">
            <div v-if="difyImportPreview" class="dify-preview-summary">
              <a-descriptions size="small" :column="3" bordered>
                <a-descriptions-item label="应用">{{ difyImportPreview.appName || '-' }}</a-descriptions-item>
                <a-descriptions-item label="节点">{{ difyImportPreview.nodeCount }}</a-descriptions-item>
                <a-descriptions-item label="连线">{{ difyImportPreview.edgeCount }}</a-descriptions-item>
              </a-descriptions>
              <div v-if="difyImportPreview.issues?.length" class="dify-issue-list">
                <div v-for="issue in difyImportPreview.issues" :key="`dialog-import-${issue.code}-${issue.nodeId || 'global'}`" class="dify-issue-item">
                  <a-tag :color="issue.severity === 'BLOCKER' ? 'red' : issue.severity === 'WARNING' ? 'gold' : 'blue'">{{ issue.severity }}</a-tag>
                  <span>{{ issue.nodeId ? `[${issue.nodeId}] ` : '' }}{{ issue.message }}</span>
                </div>
              </div>
              <a-alert v-else type="success" show-icon message="预检通过，可导入到当前草稿" />
            </div>
          </a-spin>
          <div class="dify-modal-footer">
            <a-button @click="workflowExchangeVisible = false">取消</a-button>
            <a-button :disabled="!difyImportFile" :loading="difyImportLoading" @click="previewDifyImport">预检</a-button>
            <a-button type="primary" :disabled="!canCommitDifyImport" :loading="difyImportCommitting" @click="commitDifyImport">确认导入</a-button>
          </div>
            </a-tab-pane>
            <a-tab-pane key="export" tab="导出">
          <a-alert type="info" show-icon class="dify-modal-intro">
            <template #message>导出逻辑说明</template>
            <template #description>
              导出服务器已保存的当前草稿，不调用 Dify API。节点会按 Dify DSL 映射，从 Dify 导入的节点将保留已脱敏的原始结构。API Key、Token、密码、认证等敏感信息不会写入 YAML；知识库、工具、MCP 和模型需在目标 Dify 环境重新绑定。
            </template>
          </a-alert>
          <a-spin :spinning="difyExportLoading">
            <div v-if="difyExportPreview" class="dify-preview-summary">
              <a-descriptions size="small" :column="3" bordered>
                <a-descriptions-item label="节点">{{ difyExportPreview.nodeCount }}</a-descriptions-item>
                <a-descriptions-item label="连线">{{ difyExportPreview.edgeCount }}</a-descriptions-item>
                <a-descriptions-item label="可导出">{{ difyExportPreview.exportableCount }}</a-descriptions-item>
              </a-descriptions>
              <div v-if="difyExportPreview.issues?.length" class="dify-issue-list">
                <div v-for="issue in difyExportPreview.issues" :key="`dialog-export-${issue.code}-${issue.nodeId || 'global'}`" class="dify-issue-item">
                  <a-tag :color="issue.severity === 'BLOCKER' ? 'red' : issue.severity === 'WARNING' ? 'gold' : 'blue'">{{ issue.severity }}</a-tag>
                  <span>{{ issue.nodeId ? `[${issue.nodeId}] ` : '' }}{{ issue.message }}</span>
                </div>
              </div>
              <a-alert v-else type="success" show-icon message="预检通过，可下载 Dify YAML" />
            </div>
          </a-spin>
          <div class="dify-modal-footer">
            <a-button @click="workflowExchangeVisible = false">取消</a-button>
            <a-button :loading="difyExportLoading" @click="previewDifyExport">重新预检</a-button>
            <a-button type="primary" :disabled="!canDownloadDifyExport" :loading="difyExportDownloading" @click="downloadDifyExport">下载 YAML</a-button>
          </div>
            </a-tab-pane>
          </a-tabs>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <WorkflowGlobalConfigModal
      v-model:open="globalConfigVisible"
      :config="globalConfig"
      @ok="globalConfigVisible = false"
      @add-param="addConversationParam"
      @remove-param="removeConversationParam"
    />

    <WorkflowTestDrawer
      v-model:open="testVisible"
      v-model:test-mode="testMode"
      v-model:test-input="testInput"
      v-model:test-use-draft="testUseDraft"
      :test-running="testRunning"
      :test-streaming="testStreaming"
      :test-animating="testAnimating"
      :test-failed-node-id="testFailedNodeId"
      :test-messages="testMessages"
      :test-result="testResult"
      :test-current-node-id="testCurrentNodeId"
      :test-pending-confirm="testPendingConfirm"
      :history-list="testHistoryList"
      :history-loading="testHistoryLoading"
      :selected-history-run-id="selectedHistoryRunId"
      :viewing-history="viewingTestHistory"
      :get-node-title-by-id="getNodeTitleById"
      @close="onTestDrawerClose"
      @run="runWorkflowTest"
      @resume="resumeWorkflowTest"
      @abandon="abandonWorkflowTest"
      @clear-conversation="clearTestConversation"
      @select-node="onTestTimelineNodeSelect"
      @open-history-run="openTestRunHistory"
      @delete-history="deleteTestRunHistory"
      @clear-history="clearTestRunHistory"
      @refresh-history="loadTestHistoryList"
      @skip-replay="skipTestHistoryReplay"
      @back-to-live="backToLiveTest"
    />

    <WorkflowPublishModal
      v-model:open="publishModalVisible"
      v-model:description="publishDescription"
      :saving="saving"
      @confirm="confirmPublishWorkflow"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'WorkflowEdit' })
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, computed, shallowRef, triggerRef, nextTick, watch, markRaw, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVueFlow, applyNodeChanges, applyEdgeChanges, addEdge } from '@vue-flow/core'
import { getEdgeInsertCenter } from './workflow/workflowEdgeGeometry'
import { message, notification, Modal } from 'ant-design-vue'
import { getAgentDetail } from '../api/agent'
import { getKnowledge, getKnowledgeList } from '../api/knowledge'
import { getTools } from '../api/tool'
import {
  getWorkflowConfig,
  saveWorkflowDraft,
  publishWorkflow as publishWorkflowApi,
  listWorkflowVersions,
  getWorkflowVersionDetail,
  restoreWorkflowVersion,
  listWorkflowTestRuns,
  getWorkflowTestRun,
  deleteWorkflowTestRun,
  clearWorkflowTestRuns,
  previewDifyWorkflowImport,
  importDifyWorkflow,
  previewDifyWorkflowExport,
  downloadDifyWorkflow,
} from '../api/workflow'
import { testWorkflowStream, resumeWorkflowStream } from '../api/workflowTestStream'
import { abandonWorkflowConfirm } from '../api/workflow'
import { handleLiveWorkflowTestEvent, applyWorkflowTestStreamResult, patchLocalConfirmEventsBeforeResume, patchLocalConfirmEventsOnAbandon, findSuspendNodeIdFromEvents } from './workflow/composables/useWorkflowTestLive.js'
import NodeSingleTestDrawer from '../views/workflow/components/NodeSingleTestDrawer.vue'
import NodeExampleModal from '../views/workflow/components/NodeExampleModal.vue'
import WorkflowEditToolbar from '../views/workflow/components/edit/WorkflowEditToolbar.vue'
import WorkflowUndoToastStack from '../views/workflow/components/edit/WorkflowUndoToastStack.vue'
import { loadAgentStatusLabels, formatAgentStatus } from '../utils/agentStatus'
import WorkflowEditLeftPanel from '../views/workflow/components/edit/WorkflowEditLeftPanel.vue'
import WorkflowEditCanvas from '../views/workflow/components/edit/WorkflowEditCanvas.vue'
import WorkflowEdgeDetailPanel from '../views/workflow/components/edit/WorkflowEdgeDetailPanel.vue'
import WorkflowNodeDetailPanel from '../views/workflow/components/edit/WorkflowNodeDetailPanel.vue'
import ResizableSidePanel from '../views/workflow/components/ResizableSidePanel.vue'
import WorkflowNodeHelpModal from '../views/workflow/components/edit/WorkflowNodeHelpModal.vue'
import WorkflowGlobalConfigModal from '../views/workflow/components/edit/WorkflowGlobalConfigModal.vue'
import WorkflowTestDrawer from '../views/workflow/components/edit/WorkflowTestDrawer.vue'
import WorkflowViewerCanvas from '../views/workflow/components/WorkflowViewerCanvas.vue'
import WorkflowPublishModal from '../views/workflow/components/edit/WorkflowPublishModal.vue'
import { workflowGraphToVueFlow, normalizeWorkflowGraphSnapshot } from '../views/workflow/workflowGraphToVueFlow.js'
import { buildExecutedEdgeIds, eventsToNodeStates } from '../views/workflow/workflowViewerAdapter.js'
import { replayWorkflowNodeEvents } from '../views/workflow/composables/useWorkflowReplayAnimation.js'
import { showWorkflowUndoToast } from '../views/workflow/utils/workflowUndoToast.js'
import { getNodeExample, canApplyNodeExample } from '../views/workflow/nodeConfigMeta'
import { canSingleTestNodeType } from '../views/workflow/workflowNodeTest'
import { ensureConditionGroups } from '../views/workflow/conditionUtils'
import { validateWorkflowReferences } from '../views/workflow/workflowReferValidate'
import {
  validateMultiOutgoingEdges,
  wouldViolateMultiOutgoingEdge,
  getMultiOutgoingEdgeHint,
} from '../views/workflow/workflowGraphValidate'
import { applyWorkflowAutoLayout, applyWorkflowBezierEdgeStyle } from '../views/workflow/workflowLayout'
import { WORKFLOW_DRAGGING_GROUP_ID_KEY } from '../views/workflow/useGroupDragMask'
import '../views/workflow/workflowGroupDragMask.css'
import WorkflowBezierEdge from '../views/workflow/edges/WorkflowBezierEdge.vue'
import {
  isGroupNodeType,
  isGroupBuiltinType,
  canBeGroupChild,
  findGroupAtPoint,
  findGroupForNode,
  attachNodeToGroup,
  attachNodeToGroupAndResize,
  detachFromGroup,
  createGroupWithBuiltins,
  ensureGroupBuiltins,
  migrateGroupNodeFields,
  sortNodesParentFirst,
  fitGroupBoundsToChildren,
  isGroupNestedDrop,
  getAbsolutePosition,
  getNodeDropPoint,
  getNodeParentId,
  hasEdgesToGroupSiblings,
  getGroupBuiltinPair,
} from '../views/workflow/workflowGroup'
import { getDefaultNodeData as buildDefaultNodeData, getNodeTitle as metaGetNodeTitle, getNodeColor as metaGetNodeColor, getNodeMeta, getNodeLibraryGroups, createConditionId } from '../views/workflow/nodeMeta'
import {
  HANDLE_IN,
  HANDLE_OUT,
  normalizeConnection,
  migrateWorkflowEdge,
  normalizeWorkflowEdges,
  ensureEdgeId,
  isValidWorkflowConnection,
  getHandleDisplayName,
  buildEdgeId,
} from '../views/workflow/workflowConnection'
import { useTheme } from '../composables/useTheme'
import { UploadOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const { isDark } = useTheme()

// Vue Flow edge 样式按主题切换：浅色用 slate-400 (#94a3b8)，深色用 slate-500 (#52525b) 保证可见
const edgeStrokeColor = computed(() => (isDark.value ? '#52525b' : '#94a3b8'))
const edgeStyle = computed(() => ({ strokeWidth: 2, stroke: edgeStrokeColor.value }))
const agentId = route.params.agentId

const WORKFLOW_FLOW_ID = 'lightbot-workflow-edit'

// VueFlow hooks（getNodes 为画布真实坐标来源；与画布子组件共用 flow id）
const {
  fitView,
  addSelectedEdges,
  removeSelectedEdges,
  getSelectedEdges,
  getNodes,
  setCenter,
  setViewport,
  findNode,
  screenToFlowCoordinate,
  updateNodeInternals,
  updateNode,
} = useVueFlow(WORKFLOW_FLOW_ID)

const workflowCanvasRef = ref(null)
/** 子组件 defineExpose 的 ref 在父组件访问时会自动解包，勿再 .value */
const canvasAreaRef = computed(() => workflowCanvasRef.value?.canvasAreaEl ?? null)
const trashRef = computed(() => workflowCanvasRef.value?.trashEl ?? null)

/** 避免 edge 组件被 reactive 包装导致大量 Vue warn */
const edgeTypes = markRaw({
  'workflow-bezier': markRaw(WorkflowBezierEdge),
  default: markRaw(WorkflowBezierEdge),
})

const defaultEdgeOptions = computed(() => ({
  type: 'workflow-bezier',
  selectable: true,
  updatable: true,
  style: edgeStyle.value,
}))

// 状态
const agent = ref(null)
const saving = ref(false)
const panelCollapsed = ref(false)
const nodeSearch = ref('')
const nodeHelpVisible = ref(false)
const workflowExchangeVisible = ref(false)
const workflowExchangePlatformTab = ref('dify')
const difyActiveTab = ref('import')
const difyImportFile = ref(null)
const difyImportFileList = ref([])
const difyImportPreview = ref(null)
const difyExportPreview = ref(null)
const difyImportLoading = ref(false)
const difyImportCommitting = ref(false)
const difyExportLoading = ref(false)
const difyExportDownloading = ref(false)
const canCommitDifyImport = computed(() => Boolean(difyImportPreview.value)
  && !(difyImportPreview.value.issues || []).some(issue => issue.severity === 'BLOCKER'))
const canDownloadDifyExport = computed(() => Boolean(difyExportPreview.value)
  && !(difyExportPreview.value.issues || []).some(issue => issue.severity === 'BLOCKER'))
const nodeExampleVisible = ref(false)
const nodeTestVisible = ref(false)
const nodeExampleContent = computed(() => {
  const n = selectedNode.value
  if (!n?.type) return null
  if (n.type === 'script') {
    return getNodeExample('script', { scriptLanguage: n.data?.scriptLanguage || 'javascript' })
  }
  return getNodeExample(n.type)
})

const nodeExampleAllowApply = computed(() => {
  const type = selectedNode.value?.type
  return type ? canApplyNodeExample(type) : false
})

const canTestSelectedNode = computed(() => {
  const type = selectedNode.value?.type
  return type ? canSingleTestNodeType(type) : false
})

const workflowGraphPayload = computed(() => buildWorkflowPayload())
const leftPanelTab = ref('library')
const canvasNodeSearch = ref('')
const workflowStatus = ref('draft')
const publishedVersion = ref(0)
const globalConfigVisible = ref(false)
const testVisible = ref(false)
const testMode = ref('generation')
const testMessages = ref([])
const testInput = ref('')
const testUseDraft = ref(true)
const testRunning = ref(false)
const testStreaming = ref(false)
const testAnimating = ref(false)
const testFailedNodeId = ref(null)
const testStreamAbortController = ref(null)
const testCurrentNodeId = ref(null)
const testResult = ref(null)
const testPendingConfirm = ref(null)
const testHistoryList = ref([])
const testHistoryLoading = ref(false)
const selectedHistoryRunId = ref(null)
const viewingTestHistory = ref(false)
const liveTestSnapshot = ref(null)
/** 历史回放只读画板（REQ-TEST-004：使用 workflow_graph 快照，不与编辑画布混用） */
const testHistoryViewerRef = ref(null)
const testHistoryViewerNodes = ref([])
const testHistoryViewerEdges = ref([])
const testHistoryRawEdges = ref([])
const testHistoryNodeStates = ref({})
const testHistoryHighlightedEdges = computed(() => {
  if (!viewingTestHistory.value) return new Set()
  const executedIds = Object.keys(testHistoryNodeStates.value).filter(
    id => testHistoryNodeStates.value[id]?.debugStatus,
  )
  return buildExecutedEdgeIds(testHistoryRawEdges.value, executedIds)
})
/** 历史回放动画代次，用于取消/防重复触发 */
const testAnimationGeneration = ref(0)
const versionVisible = ref(false)
const publishModalVisible = ref(false)
const publishDescription = ref('')
const lastAutoSaveTime = ref(null)
const autoSaving = ref(false)
const agentStatusLabels = ref(null)
const workflowLoaded = ref(false)
/** 初始化加载阶段跳过脏检查，避免 VueFlow 同步后误报未保存 */
const workflowInitializing = ref(true)
let autoSaveTimer = null
const versionList = ref([])
const publishedVersionList = computed(() =>
  (versionList.value || []).filter(v => v?.version != null && v.version > 0),
)
const versionLoading = ref(false)
const selectedVersion = ref('draft')
const VERSION_PANEL_WIDTH = 300
const versionPanelPos = ref({ x: 0, y: 48 })
let versionPanelDragState = null
let versionPanelLayoutReady = false
let versionPanelResizeObserver = null

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
/** 打开节点详情侧栏时的 data 快照，用于撤回与脏检测 */
const nodePanelSnapshot = ref(null)
const nodePanelHistoryRecorded = ref(false)

watch([selectedNode, selectedEdge, panelCollapsed, versionVisible], () => {
  if (!versionVisible.value) return
  nextTick(() => clampVersionPanelPosition())
})

// 主题切换时，把已存在的所有 edge 的 stroke 同步到当前主题色（defaultEdgeOptions 只对新建 edge 生效）
watch(edgeStrokeColor, (next) => {
  if (!edges.value?.length) return
  edges.value = edges.value.map((e) => ({
    ...e,
    style: { ...(e.style || {}), stroke: next, strokeWidth: 2 },
  }))
})

watch(testVisible, (open) => {
  if (open) loadTestHistoryList()
})

const validationErrors = ref([])
const savedWorkflowSnapshot = ref('')
const isNodeDragging = ref(false)
const dragOverTrash = ref(false)
/** 正在拖动的循环/批处理容器 id，子节点通过 inject 同步遮罩 */
const draggingGroupId = ref(null)
provide(WORKFLOW_DRAGGING_GROUP_ID_KEY, draggingGroupId)

let groupDragPointerReleaseHandler = null

function bindGroupDragPointerRelease() {
  unbindGroupDragPointerRelease()
  groupDragPointerReleaseHandler = () => {
    endGroupDragMask()
  }
  document.addEventListener('mouseup', groupDragPointerReleaseHandler, true)
  document.addEventListener('pointerup', groupDragPointerReleaseHandler, true)
  document.addEventListener('pointercancel', groupDragPointerReleaseHandler, true)
}

function unbindGroupDragPointerRelease() {
  if (!groupDragPointerReleaseHandler) return
  document.removeEventListener('mouseup', groupDragPointerReleaseHandler, true)
  document.removeEventListener('pointerup', groupDragPointerReleaseHandler, true)
  document.removeEventListener('pointercancel', groupDragPointerReleaseHandler, true)
  groupDragPointerReleaseHandler = null
}

function setGroupChildrenDragMask(groupId, masked) {
  if (!groupId) return
  let changed = false
  const next = nodes.value.map(n => {
    if (getNodeParentId(n) !== groupId) return n
    const prevMasked = !!n.data?.groupDragMasked
    if (prevMasked === masked) return n
    changed = true
    return {
      ...n,
      data: {
        ...(n.data || {}),
        groupDragMasked: masked,
      },
    }
  })
  if (changed) nodes.value = next
}

function clearGroupDragMaskDom() {
  const root = canvasAreaRef.value
  if (!root) return
  root.querySelectorAll('.wf-group-child-mask').forEach(el => {
    el.classList.remove('wf-group-child-mask')
  })
}

function endGroupDragMask(groupId) {
  const activeGroupId = groupId
    || draggingGroupId.value
    || (draggingNode.value && isGroupNodeType(draggingNode.value.type) ? draggingNode.value.id : null)
  setGroupChildrenDragMask(activeGroupId, false)
  draggingGroupId.value = null
  unbindGroupDragPointerRelease()
  // 兜底清理，避免内置开始/结束节点残留遮罩 class。
  clearGroupDragMaskDom()
}
const draggingNode = ref(null)
const hoveredEdge = ref(null)
/** 插入菜单打开时保持锚点，避免鼠标移入 Popover 后层被销毁 */
const edgeInsertAnchorEdge = ref(null)
const edgeInsertHoverLock = ref(false)
const edgeInsertMenuOpen = ref(false)
let edgeHoverLeaveTimer = null

const isDirty = computed(() => {
  if (workflowInitializing.value || !workflowLoaded.value) return false
  if (!savedWorkflowSnapshot.value) return false
  return savedWorkflowSnapshot.value !== getWorkflowSnapshot()
})

const canDeleteDraggedNode = computed(() => getSelectedDeletableNodes().length > 0 || isNodeDeletable(draggingNode.value))

const trashDeleteLabel = computed(() => {
  if (!canDeleteDraggedNode.value) return '开始/结束节点不可删除'
  const count = getSelectedDeletableNodes().length
  if (count > 1) return `拖到此处删除 ${count} 个节点`
  return '拖到此处删除'
})

function isGroupBuiltinNode(node) {
  return node && isGroupBuiltinType(node.type)
}

function isNodeDeletable(node) {
  return !!(node && node.type !== 'start' && node.type !== 'end' && !isGroupBuiltinType(node.type))
}

// 资源列表
const knowledgeList = ref([])
const tools = ref([])

// 节点和边数据（edges 用 ref 保证 VueFlow 受控模式正确刷新）
const nodes = ref([])
const edges = ref([])

function normalizePosition(pos) {
  if (!pos) return { x: 100, y: 100 }
  return {
    x: Number(pos.x ?? 100),
    y: Number(pos.y ?? 100)
  }
}

function deepClonePayloadValue(value) {
  if (value == null) return value
  try {
    return JSON.parse(JSON.stringify(value))
  } catch (_) {
    return value
  }
}

function buildWorkflowPayload() {
  // 始终以 nodes 源数据为准，避免 VueFlow 内部节点与源数据 position 不一致导致误报 dirty
  return {
    nodes: nodes.value.map(n => {
      const payload = deepClonePayloadValue(n) || {}
      payload.id = n.id
      payload.type = n.type
      payload.position = normalizePosition(n.position)
      payload.data = deepClonePayloadValue(n.data)
      if ('parentNode' in n) payload.parentNode = n.parentNode
      if ('extent' in n) payload.extent = n.extent
      if ('style' in n) payload.style = deepClonePayloadValue(n.style)
      if ('zIndex' in n) payload.zIndex = n.zIndex
      return payload
    }),
    edges: edges.value.map(e => {
      const m = migrateWorkflowEdge(e)
      return {
        ...deepClonePayloadValue(e),
        id: m.id,
        source: m.source,
        target: m.target,
        sourceHandle: m.sourceHandle,
        targetHandle: m.targetHandle || HANDLE_IN,
      }
    }),
    globalConfig: deepClonePayloadValue(globalConfig.value)
  }
}

function getWorkflowSnapshot() {
  return JSON.stringify(buildWorkflowPayload())
}

const filteredCanvasNodes = computed(() => {
  const keyword = (canvasNodeSearch.value || '').toLowerCase()
  return nodes.value.filter(n => {
    if (isGroupBuiltinType(n.type)) return false
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

function getWorkflowStatusLabel(code) {
  if (code === 'draft') return '未发布'
  return formatAgentStatus(code, publishedVersion.value, agentStatusLabels.value)
}

function scheduleAutoSave() {
  if (!workflowLoaded.value || isVersionPreview.value) return
  if (!isDirty.value) return
  // 即时校验：失败则不启动自动保存计时器，直到配置再次通过校验
  // showToast=false 保持 silent，不打扰用户编辑；validationErrors 仍会刷新供顶栏显示
  if (validateWorkflow(false).length > 0) {
    clearTimeout(autoSaveTimer)
    return
  }
  clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => doAutoSave(true), 2500)
}

async function doAutoSave(silent = true) {
  if (autoSaving.value || !isDirty.value || isVersionPreview.value) return false
  // 双重保险：timer 排队期间用户可能再次改坏配置，执行前再校验一次
  if (validateWorkflow(false).length > 0) return false
  // 静默自动保存不展示「保存中...」，避免顶栏频繁闪烁
  if (!silent) autoSaving.value = true
  try {
    await saveWorkflowDraft(agentId, buildWorkflowPayload())
    markWorkflowSaved()
    lastAutoSaveTime.value = new Date()
    if (workflowStatus.value === 'published') {
      workflowStatus.value = 'published_editing'
    }
    return true
  } catch (e) {
    if (!silent) {
      notification.error({ message: '自动保存失败', description: e.message })
    }
    return false
  } finally {
    if (!silent) autoSaving.value = false
  }
}

async function flushAutoSave() {
  clearTimeout(autoSaveTimer)
  if (isDirty.value) {
    await doAutoSave(true)
  }
}

function captureNodePanelSnapshot(node) {
  if (!node?.id || isVersionPreview.value) {
    nodePanelSnapshot.value = null
    nodePanelHistoryRecorded.value = false
    return
  }
  nodePanelSnapshot.value = {
    nodeId: node.id,
    data: JSON.parse(JSON.stringify(node.data || {})),
    undoNodes: JSON.parse(JSON.stringify(nodes.value)),
    undoEdges: JSON.parse(JSON.stringify(edges.value)),
  }
  nodePanelHistoryRecorded.value = false
}

function hasNodePanelPendingChanges(node) {
  if (!node?.id || !nodePanelSnapshot.value) return false
  if (nodePanelSnapshot.value.nodeId !== node.id) return false
  return JSON.stringify(node.data || {}) !== JSON.stringify(nodePanelSnapshot.value.data)
}

/** 节点详情内首次修改时入栈撤回（保存打开侧栏时的画布状态） */
function ensureNodePanelEditHistory() {
  if (nodePanelHistoryRecorded.value || !nodePanelSnapshot.value || !selectedNode.value) return
  if (nodePanelSnapshot.value.nodeId !== selectedNode.value.id) return
  if (!hasNodePanelPendingChanges(selectedNode.value)) return
  history.value.push({
    nodes: nodePanelSnapshot.value.undoNodes,
    edges: nodePanelSnapshot.value.undoEdges,
    label: `修改「${getNodeDisplayName(selectedNode.value)}」节点配置`,
  })
  if (history.value.length > 20) history.value.shift()
  nodePanelHistoryRecorded.value = true
}

/** 将选中节点的 data 同步写入 nodes 源数据（避免仅改 VueFlow 引用导致未脏标记/未保存） */
function patchSelectedNodeData(nextData) {
  const nodeId = selectedNode.value?.id
  if (!nodeId) return
  const stored = nodes.value.find(n => n.id === nodeId)
  if (stored) stored.data = nextData
  selectedNode.value.data = nextData
}

function syncSelectedNodeToStore() {
  const nodeId = selectedNode.value?.id
  if (!nodeId) return
  const stored = nodes.value.find(n => n.id === nodeId)
  if (stored && selectedNode.value.data && stored.data !== selectedNode.value.data) {
    stored.data = selectedNode.value.data
  }
}

async function closeNodePanel() {
  const node = selectedNode.value
  if (!node) return
  if (isVersionPreview.value) {
    nodePanelSnapshot.value = null
    nodePanelHistoryRecorded.value = false
    selectedNode.value = null
    return
  }
  syncSelectedNodeToStore()
  clearTimeout(autoSaveTimer)
  if (isDirty.value) {
    // doAutoSave 校验失败会跳过保存并返回 false，此时不展示"已自动保存"避免误导用户
    const saved = await doAutoSave(true)
    if (saved) {
      message.success(`「${node.data?.label || getNodeTitle(node.type)}」已自动保存`)
    }
  }
  nodePanelSnapshot.value = null
  nodePanelHistoryRecorded.value = false
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
  const migratedNodes = (graph.nodes || []).map(migrateWorkflowNode).map(n => ({
    ...n,
    position: normalizePosition(n.position),
    ...(n.parentNode ? { parentNode: n.parentNode, extent: n.extent || 'parent', zIndex: n.zIndex ?? 10 } : {}),
  }))
  const withBuiltins = ensureGroupBuiltins(migratedNodes, graph.edges || [])
  nodes.value = sortNodesParentFirst(withBuiltins.nodes.map(n => ensureNodeDraggable(migrateGroupNodeFields(n))))
  edges.value = applyWorkflowBezierEdgeStyle(normalizeWorkflowEdges(withBuiltins.edges))
  triggerRef(nodes)
  history.value = []
}

function panToNode(node) {
  const pos = normalizePosition(node.position)
  try {
    setCenter(pos.x + 90, pos.y + 40, { zoom: 1, duration: 300 })
  } catch (_) {
    setViewport({ x: -pos.x + 200, y: -pos.y + 120, zoom: 1 })
  }
}

function focusNode(node) {
  if (!node?.id) return
  if (selectedNode.value?.id === node.id && !selectedEdge.value) {
    panToNode(node)
    return
  }
  selectedNode.value = node
  clearEdgeSelection()
  captureNodePanelSnapshot(node)
  panToNode(node)
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

// 记录操作历史（label 用于撤回时的说明文案）
function recordHistory(label = '画布变更') {
  history.value.push({
    nodes: JSON.parse(JSON.stringify(nodes.value)),
    edges: JSON.parse(JSON.stringify(edges.value)),
    label,
  })
  if (history.value.length > 20) {
    history.value.shift()
  }
}

function getNodeDisplayName(nodeOrId) {
  const node = typeof nodeOrId === 'string'
    ? nodes.value.find(n => n.id === nodeOrId)
    : nodeOrId
  if (!node) return '节点'
  return node.data?.label || getNodeTitle(node.type) || node.id
}

function formatEdgeHistoryLabel(action, edgeLike) {
  if (!edgeLike?.source || !edgeLike?.target) return action
  const link = `「${getNodeDisplayName(edgeLike.source)}」→「${getNodeDisplayName(edgeLike.target)}」`
  return `${action}${link}`
}

/** 批量移动/删除节点的撤回说明 */
function formatBatchNodeHistoryLabel(action, nodeList) {
  const names = (nodeList || []).map(n => getNodeDisplayName(n)).filter(Boolean)
  if (!names.length) {
    return action === 'delete' ? '删除节点' : '移动节点位置'
  }
  if (names.length === 1) {
    return action === 'delete'
      ? `删除「${names[0]}」节点`
      : `移动「${names[0]}」节点位置`
  }
  if (names.length <= 3) {
    const joined = names.map(n => `「${n}」`).join('、')
    return action === 'delete'
      ? `删除 ${names.length} 个节点：${joined}`
      : `移动 ${names.length} 个节点：${joined}`
  }
  return action === 'delete'
    ? `批量删除 ${names.length} 个节点`
    : `批量移动 ${names.length} 个节点`
}

/** 画布当前选中的可删除节点（Shift 框选 / Ctrl 多选） */
function getSelectedDeletableNodes() {
  const selectedIds = new Set(
    (getNodes.value || []).filter(n => n.selected).map(n => n.id),
  )
  if (selectedIds.size) {
    return nodes.value.filter(n => selectedIds.has(n.id) && isNodeDeletable(n))
  }
  if (selectedNode.value && isNodeDeletable(selectedNode.value)) {
    return [selectedNode.value]
  }
  return []
}

/** 拖到垃圾桶时：优先删除全部选中节点，否则删除当前拖拽节点 */
function resolveTrashDeleteTargets(dragList) {
  const selected = getSelectedDeletableNodes()
  if (selected.length) return selected
  return (dragList || [])
    .map(d => nodes.value.find(n => n.id === d.id) || d)
    .filter(isNodeDeletable)
}

/** 撤销拖拽开始时误记录的「移动」历史（改为记录删除） */
function cancelPendingMoveHistory() {
  const last = history.value[history.value.length - 1]
  if (!last?.label) return
  if (last.label.includes('移动') && last.label.includes('节点')) {
    history.value.pop()
  }
}

// 撤回操作
function undoAction() {
  if (history.value.length === 0) return
  const lastState = history.value.pop()
  nodes.value = lastState.nodes
  edges.value = [...lastState.edges]
  triggerRef(nodes)
  selectedNode.value = null
  selectedEdge.value = null
  nodePanelSnapshot.value = null
  nodePanelHistoryRecorded.value = false
  clearEdgeSelection()
  clearEdgeInsertUi()
  scheduleAutoSave()
  showWorkflowUndoToast(lastState.label)
}

/** 加载序号：避免快速切换 agent / HMR 重跑 setup 时旧请求回写状态 */
let workflowLoadSeq = 0

async function loadWorkflowPage() {
  const seq = ++workflowLoadSeq
  const currentAgentId = route.params.agentId
  workflowLoaded.value = false
  try {
    agentStatusLabels.value = await loadAgentStatusLabels()
    if (seq !== workflowLoadSeq) return

    const res = await getAgentDetail(currentAgentId)
    if (seq !== workflowLoadSeq) return
    agent.value = res.data.agent

    const wfRes = await getWorkflowConfig(currentAgentId)
    if (seq !== workflowLoadSeq) return
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

    const [knowledgeRes, toolRes] = await Promise.all([
      getKnowledgeList({ pageNum: 1, pageSize: 100 }),
      getTools({ pageNum: 1, pageSize: 100 })
    ])
    if (seq !== workflowLoadSeq) return
    knowledgeList.value = knowledgeRes.data.records || []
    tools.value = toolRes.data.records || []

    await nextTick()
    await finalizeWorkflowGraphAfterLoad()
    if (seq !== workflowLoadSeq) return
    scheduleFitView(true)
  } catch (e) {
    if (seq === workflowLoadSeq) {
      notification.error({ message: '加载失败', description: e.message })
    }
  } finally {
    if (seq === workflowLoadSeq) {
      workflowLoaded.value = true
    }
  }
}

// immediate：HMR 重跑 setup 时也会重新加载；纯 onMounted 在热更新后不会再执行
watch(() => route.params.agentId, () => {
  loadWorkflowPage()
}, { immediate: true })

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  nextTick(() => {
    if (canvasAreaRef.value) {
      versionPanelResizeObserver = new ResizeObserver(() => clampVersionPanelPosition())
      versionPanelResizeObserver.observe(canvasAreaRef.value)
    }
  })
})

function onKeyDown(event) {
  if (isVersionPreview.value) return
  const tag = document.activeElement?.tagName?.toLowerCase()
  const inInput = tag === 'input' || tag === 'textarea' || document.activeElement?.isContentEditable

  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z' && !event.shiftKey) {
    if (!inInput && canUndo.value) {
      event.preventDefault()
      undoAction()
    }
    return
  }

  if (event.key !== 'Delete' && event.key !== 'Backspace') return
  if (inInput) return

  event.preventDefault()
  if (selectedEdge.value) {
    deleteSelectedEdge()
  } else if (getSelectedDeletableNodes().length) {
    deleteSelectedNodes()
  }
}

onUnmounted(() => {
  cleanupWorkflowResources()
})

/**
 * KeepAlive 缓存的本组件在切走时不会触发 onUnmounted，必须用 onDeactivated 清理全局监听、
 * 定时器、SSE；切回时用 onActivated 重新绑定。否则 keydown / ResizeObserver / live test SSE
 * 会持续在后台运行，且 keydown 的 preventDefault 会污染其他页面的 Delete/Backspace 行为。
 */
onDeactivated(() => {
  cleanupWorkflowResources()
})

onActivated(() => {
  window.addEventListener('keydown', onKeyDown)
  nextTick(() => {
    if (canvasAreaRef.value && !versionPanelResizeObserver) {
      versionPanelResizeObserver = new ResizeObserver(() => clampVersionPanelPosition())
      versionPanelResizeObserver.observe(canvasAreaRef.value)
    }
  })
})

/** 清理全局监听、定时器与 SSE：onUnmounted 与 onDeactivated 共用 */
function cleanupWorkflowResources() {
  stopLiveTestStream()
  clearTimeout(nodeInternalsTimer)
  window.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mousemove', onVersionPanelDragMove)
  document.removeEventListener('mouseup', onVersionPanelDragEnd)
  unbindGroupDragPointerRelease()
  versionPanelResizeObserver?.disconnect()
  versionPanelResizeObserver = null
  clearTimeout(autoSaveTimer)
  clearTimeout(edgeHoverLeaveTimer)
}

/** 兼容旧数据并补齐默认字段 */
function migrateWorkflowNode(node) {
  const defaults = buildDefaultNodeData(node.type)
  const rawData = node.data || {}
  const data = { ...(defaults || {}), ...rawData }
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
  if (node.type === 'condition') {
    data.conditionGroups = ensureConditionGroups(data)
  }
  if (node.type === 'script') {
    if (!data.scriptLanguage) data.scriptLanguage = defaults.scriptLanguage || 'javascript'
    if (!data.inputParams?.length) data.inputParams = defaults.inputParams || []
    if (!data.outputParams?.length) data.outputParams = defaults.outputParams || []
    if (!data.retryConfig) data.retryConfig = defaults.retryConfig || { enabled: false, maxAttempts: 3, delayMs: 1000 }
    if (!data.errorStrategy) data.errorStrategy = defaults.errorStrategy || 'defaultValue'
    if (data.defaultOutput == null) data.defaultOutput = defaults.defaultOutput || '{}'
    if (!data.scriptContent) data.scriptContent = defaults.scriptContent || ''
  }
  if (node.type === 'loop') {
    if (!rawData.input_params?.length && rawData.inputParams?.length) data.input_params = rawData.inputParams
    if (!rawData.output_params?.length && rawData.outputParams?.length) data.output_params = rawData.outputParams
    if (rawData.iterator_type == null && rawData.iteratorType != null) data.iterator_type = rawData.iteratorType
    if (rawData.count_limit == null && rawData.countLimit != null) data.count_limit = rawData.countLimit
    if (rawData.error_strategy == null && rawData.errorStrategy != null) data.error_strategy = rawData.errorStrategy
    if (!data.iterator_type && data.iteratorType) data.iterator_type = data.iteratorType
    if (!data.iteratorType && data.iterator_type) data.iteratorType = data.iterator_type
    if (data.count_limit == null && data.countLimit != null) data.count_limit = data.countLimit
    if (!data.input_params?.length && data.arrayVariable) {
      data.input_params = [{ key: 'item', type: 'Object', value_from: 'refer', value: data.arrayVariable }]
    }
    if (!data.output_params?.length) data.output_params = data.outputParams || defaults.output_params || [{ key: 'result', type: 'Object' }]
  }
  if (node.type === 'batch') {
    if (!rawData.input_params?.length && rawData.inputParams?.length) data.input_params = rawData.inputParams
    if (!rawData.output_params?.length && rawData.outputParams?.length) data.output_params = rawData.outputParams
    if (rawData.batch_size == null && rawData.batchSize != null) data.batch_size = rawData.batchSize
    if (rawData.concurrent_size == null && rawData.concurrentSize != null) data.concurrent_size = rawData.concurrentSize
    if (rawData.error_strategy == null && rawData.errorStrategy != null) data.error_strategy = rawData.errorStrategy
    if (data.batch_size == null && data.batchSize != null) data.batch_size = data.batchSize
    if (data.concurrent_size == null && data.concurrentSize != null) data.concurrent_size = data.concurrentSize
    if (!data.error_strategy && data.errorStrategy) data.error_strategy = data.errorStrategy
    if (!data.input_params?.length && data.arrayVariable) {
      data.input_params = [{ key: 'item', type: 'Object', value_from: 'refer', value: data.arrayVariable }]
    }
    if (!data.output_params?.length) data.output_params = data.outputParams || defaults.output_params || [{ key: 'result', type: 'Array' }]
  }
  if (node.type === 'tool') {
    if (data.toolId != null) data.toolId = String(data.toolId)
    if (!Array.isArray(data.inputMappings)) {
      data.inputMappings = defaults.inputMappings || [{ key: 'query', value: '{{query}}' }]
    }
    if (!Array.isArray(data.outputMappings)) {
      data.outputMappings = defaults.outputMappings || [{ key: 'toolResult', value: '{{output}}' }]
    }
  }
  const migrated = migrateGroupNodeFields({ ...node, data })
  if (migrated.parentNode) {
    migrated.extent = migrated.extent || 'parent'
    migrated.zIndex = migrated.zIndex ?? 10
  }
  return ensureNodeDraggable(migrated)
}

/** 保证画布节点可拖动（避免持久化数据或内置类型把 draggable 写成 false） */

function ensureNodeDraggable(node) {
  if (!node) return node
  const n = { ...node }
  if (isGroupBuiltinType(n.type)) {
    n.draggable = true
    n.selectable = n.selectable !== false
    n.connectable = n.connectable !== false
    return n
  }
  if (isGroupNodeType(n.type)) {
    n.draggable = true
    n.selectable = n.selectable !== false
    n.connectable = n.connectable !== false
    n.dragHandle = '.group-shell'
    return n
  }
  n.draggable = true
  n.selectable = n.selectable !== false
  n.connectable = n.connectable !== false
  if (n.type === 'input' || n.type === 'output') {
    n.sourcePosition = n.sourcePosition || 'right'
    n.targetPosition = n.targetPosition || 'left'
    if (n.style?.width || n.style?.height) {
      const { width, height, ...restStyle } = n.style
      n.style = Object.keys(restStyle).length ? restStyle : undefined
    }
  }
  return n
}

function flowPointFromEvent(event) {
  try {
    const pt = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
    if (pt && Number.isFinite(pt.x) && Number.isFinite(pt.y)) return pt
  } catch (_) { /* Vue Flow 未就绪时走 DOM 回退 */ }
  const pane = canvasAreaRef.value?.querySelector('.vue-flow__viewport')
    || canvasAreaRef.value?.querySelector('.vue-flow')
  if (pane) {
    const rect = pane.getBoundingClientRect()
    const vp = pane.querySelector('.vue-flow__transformationpane')
    const transform = vp ? getComputedStyle(vp).transform : ''
    let scale = 0.8
    if (transform && transform !== 'none') {
      const m = transform.match(/matrix\(([^)]+)\)/)
      if (m) scale = parseFloat(m[1].split(',')[0]) || scale
    }
    return {
      x: (event.clientX - rect.left) / scale,
      y: (event.clientY - rect.top) / scale,
    }
  }
  return { x: 100, y: 100 }
}

function pruneInvalidEdges(nodeList, edgeList) {
  return edgeList.filter(e => {
    const src = nodeList.find(n => n.id === e.source)
    const tgt = nodeList.find(n => n.id === e.target)
    return isValidWorkflowConnection(
      { source: e.source, target: e.target, sourceHandle: e.sourceHandle, targetHandle: e.targetHandle },
      { nodes: nodeList, edges: edgeList, excludeEdgeId: e.id },
    )
  })
}

let nodeInternalsTimer = null

/** 批量刷新节点尺寸（防抖，避免与 dimensions 事件形成死循环） */
function scheduleUpdateNodeInternals(nodeIds) {
  clearTimeout(nodeInternalsTimer)
  nodeInternalsTimer = setTimeout(() => {
    const ids = nodeIds?.length ? nodeIds : nodes.value.map(n => n.id)
    ids.forEach(id => {
      try {
        updateNodeInternals(id)
      } catch (_) { /* ignore */ }
    })
  }, 16)
}

/** 提交节点列表（保证 parent 在前、子节点 expandParent，并刷新 Vue Flow 内部尺寸） */
function commitWorkflowNodes(list, { refreshInternals = true, internalIds = null } = {}) {
  nodes.value = sortNodesParentFirst(
    (list || []).map(n => {
      const m = ensureNodeDraggable(migrateGroupNodeFields({
        ...n,
        position: normalizePosition(n.position),
      }))
      if (m.parentNode) {
        m.extent = m.extent || 'parent'
        m.expandParent = m.expandParent !== false
        m.zIndex = m.zIndex ?? 10
      }
      return m
    }),
  )
  if (refreshInternals) {
    const ids = internalIds?.length ? internalIds : nodes.value.map(n => n.id)
    scheduleUpdateNodeInternals(ids)
  }
}

/** 局部更新节点，避免整表重刷引发闪烁 */
function patchWorkflowNodes(patches, { refreshIds = [] } = {}) {
  if (!patches?.length) return
  const patchMap = new Map(patches.map(p => [p.id, p]))
  nodes.value = sortNodesParentFirst(
    nodes.value.map(n => {
      const p = patchMap.get(n.id)
      if (!p) return n
      const merged = {
        ...n,
        ...p,
        position: normalizePosition(p.position ?? n.position),
      }
      if (p.parentNode === null) {
        delete merged.parentNode
        delete merged.extent
        delete merged.expandParent
      }
      return ensureNodeDraggable(migrateGroupNodeFields(merged))
    }),
  )
  if (refreshIds.length) scheduleUpdateNodeInternals(refreshIds)
}

/**
 * 将节点纳入/移出循环批处理容器，并撑开容器边框
 * @returns {boolean} 是否改动了 nodes
 */
function applyNodeGroupMembership(nodeId, absolutePoint) {
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node || isGroupBuiltinType(node.type)) return false

  if (isGroupNodeType(node.type)) {
    if (isGroupNestedDrop(node.type, nodes.value, absolutePoint, findNode)) {
      message.warning('循环/批处理节点不能嵌套到其他容器中')
    }
    return false
  }

  if (!canBeGroupChild(node.type)) return false

  const dropPoint = absolutePoint || getNodeDropPoint(node, nodes.value, findNode)
  const flowNodes = getNodes.value?.length ? getNodes.value : nodes.value
  const pid = getNodeParentId(node)
  const groupAtPoint = findGroupAtPoint(flowNodes, dropPoint, { excludeId: nodeId, findNode })

  // 已在容器内：落点仍在内容区则保留，否则移出（未与容器内节点连线时）
  if (pid) {
    const parent = flowNodes.find(n => n.id === pid)
    if (parent && isGroupNodeType(parent.type)) {
      const stillInside = groupAtPoint?.id === pid
      if (stillInside) {
        const kept = {
          ...node,
          extent: 'parent',
          expandParent: true,
          position: normalizePosition(node.position),
        }
        const resized = fitGroupBoundsToChildren(parent, nodes.value, findNode)
        const patches = [{ id: nodeId, ...kept }]
        if (resized.style?.width !== parent.style?.width || resized.style?.height !== parent.style?.height) {
          patches.push({ id: pid, style: resized.style })
        }
        patchWorkflowNodes(patches, { refreshIds: [nodeId, pid] })
        return true
      }
      if (hasEdgesToGroupSiblings(nodeId, pid, nodes.value, edges.value)) {
        message.warning('该节点已与容器内其他节点连线，请先断开连线再移出容器')
        const center = getNodeDropPoint(node, nodes.value, findNode)
        const rel = attachNodeToGroup(node, parent, center, findNode)
        patchWorkflowNodes([{ id: nodeId, ...rel }], { refreshIds: [nodeId] })
        return true
      }
      const absTopLeft = getAbsolutePosition(node, nodes.value, findNode)
      const next = nodes.value.map(n => (
        n.id === nodeId
          ? { ...detachFromGroup(node), position: absTopLeft }
          : n
      ))
      const resized = fitGroupBoundsToChildren(parent, next, findNode)
      patchWorkflowNodes(
        [
          {
            id: nodeId,
            position: absTopLeft,
            parentNode: null,
            extent: null,
            expandParent: null,
          },
          { id: pid, style: resized.style },
        ],
        { refreshIds: [nodeId, pid] },
      )
      return true
    }
  }

  if (groupAtPoint) {
    const { node: attached, group: resized } = attachNodeToGroupAndResize(
      node,
      groupAtPoint,
      nodes.value,
      dropPoint,
      findNode,
    )
    patchWorkflowNodes(
      [
        { id: nodeId, ...attached },
        { id: resized.id, style: resized.style },
      ],
      { refreshIds: [nodeId, resized.id] },
    )
    return true
  }

  return false
}

function openNodeTestDrawer() {
  if (!selectedNode.value || isVersionPreview.value) return
  if (!canTestSelectedNode.value) {
    message.warning('该节点类型暂不支持单节点测试')
    return
  }
  nodeTestVisible.value = true
}

function copySelectedNode() {
  const src = selectedNode.value
  if (!src || isVersionPreview.value || src.type === 'start' || src.type === 'end' || isGroupBuiltinType(src.type)) return
  recordHistory(`复制「${getNodeDisplayName(src)}」节点`)
  const pos = normalizePosition(src.position)
  const newId = `node_${Date.now()}`
  const idMap = { [src.id]: newId }
  const toCopy = [src]
  if (isGroupNodeType(src.type)) {
    nodes.value.filter(n => getNodeParentId(n) === src.id).forEach((child, i) => {
      idMap[child.id] = `node_${Date.now()}_c${i}`
      toCopy.push(child)
    })
  }
  const newNodes = toCopy.map((n, idx) => {
    const nid = idMap[n.id]
    const base = {
      id: nid,
      type: n.type,
      position: {
        x: normalizePosition(n.position).x + (idx === 0 ? 48 : 0),
        y: normalizePosition(n.position).y + (idx === 0 ? 48 : 0),
      },
      data: JSON.parse(JSON.stringify(n.data || {})),
    }
    if (n.style) base.style = JSON.parse(JSON.stringify(n.style))
    if (n.extent) base.extent = n.extent
    if (n.zIndex != null) base.zIndex = n.zIndex
    const pid = getNodeParentId(n)
    if (pid && idMap[pid]) base.parentNode = idMap[pid]
    return migrateGroupNodeFields(base)
  })
  commitWorkflowNodes([...nodes.value, ...newNodes])
  selectedNode.value = nodes.value.find(n => n.id === idMap[src.id]) || newNodes[0]
  message.success(isGroupNodeType(src.type) ? '已复制容器及内部节点' : '已复制节点')
  scheduleAutoSave()
}

function onNodeTestComplete({ nodeId, success }) {
  const n = nodes.value.find(item => item.id === nodeId)
  if (n?.data) {
    n.data.debugStatus = success ? 'success' : 'fail'
    triggerRef(nodes)
  }
}

function openNodeExampleModal() {
  if (!selectedNode.value || selectedNode.value.type === 'start' || selectedNode.value.type === 'end') return
  nodeExampleVisible.value = true
}

function applyNodeExampleConfig(exampleData) {
  if (!selectedNode.value || isVersionPreview.value) return
  recordHistory(`应用「${getNodeDisplayName(selectedNode.value)}」示例配置`)
  const nextData = {
    ...base,
    ...exampleData,
    label: base.label || exampleData.label,
  }
  patchSelectedNodeData(nextData)
  syncNodes()
  message.success('已应用示例配置')
}

function getDefaultNodeData(type) {
  return buildDefaultNodeData(type)
}

// 实时校验工作流配置（拖拽中跳过，避免顶栏状态闪烁）
watch(
  () => getWorkflowSnapshot(),
  () => {
    if (workflowInitializing.value || isNodeDragging.value) return
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
    const selected = getSelectedEdges()
    if (selected?.length) {
      removeSelectedEdges(selected)
    }
  } catch (_) { /* VueFlow 未就绪时忽略 */ }
}

function cloneEdge(edge) {
  if (!edge) return null
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    type: edge.type,
    selectable: edge.selectable,
    style: edge.style ? { ...edge.style } : undefined,
  }
}

const edgeInsertLabelStyle = computed(() => {
  const anchor = edgeInsertAnchorEdge.value
  if (!anchor) return null
  const center = getEdgeInsertCenter(anchor, findNode, nodes.value)
  if (!center) return null
  return {
    position: 'absolute',
    pointerEvents: 'all',
    transform: `translate(-50%, -50%) translate(${center.x}px, ${center.y}px)`,
  }
})

function getNodeCenterForInsert(node) {
  const p = normalizePosition(node.position)
  const isRound = node.type === 'start' || node.type === 'end'
  const w = isRound ? 64 : 180
  const h = isRound ? 64 : 88
  return { x: p.x + w / 2, y: p.y + h / 2 }
}

function syncEdgeInsertAnchor(edge) {
  if (!edge) return
  hoveredEdge.value = edge
  edgeInsertAnchorEdge.value = edge
}

function onEdgeMouseEnter({ edge }) {
  if (isVersionPreview.value || isNodeDragging.value || edgeInsertMenuOpen.value) return
  clearTimeout(edgeHoverLeaveTimer)
  syncEdgeInsertAnchor(edge)
}

function onEdgeMouseMove({ edge }) {
  if (isVersionPreview.value || isNodeDragging.value) return
  if (!hoveredEdge.value || hoveredEdge.value.id !== edge.id) return
  hoveredEdge.value = edge
}

function onEdgeMouseLeave() {
  if (edgeInsertHoverLock.value || edgeInsertMenuOpen.value) return
  clearTimeout(edgeHoverLeaveTimer)
  edgeHoverLeaveTimer = setTimeout(() => {
    if (!edgeInsertHoverLock.value && !edgeInsertMenuOpen.value) {
      hoveredEdge.value = null
      edgeInsertAnchorEdge.value = null
    }
  }, 280)
}

function onEdgeInsertPointerEnter() {
  clearTimeout(edgeHoverLeaveTimer)
  edgeInsertHoverLock.value = true
}

function onEdgeInsertPointerLeave() {
  if (edgeInsertMenuOpen.value) return
  edgeInsertHoverLock.value = false
  onEdgeMouseLeave()
}

function onEdgeInsertMenuOpen() {
  clearTimeout(edgeHoverLeaveTimer)
  edgeInsertMenuOpen.value = true
  edgeInsertHoverLock.value = true
}

function onEdgeInsertMenuClose() {
  edgeInsertMenuOpen.value = false
  edgeInsertHoverLock.value = false
  hoveredEdge.value = null
  edgeInsertAnchorEdge.value = null
}

function clearEdgeInsertUi() {
  clearTimeout(edgeHoverLeaveTimer)
  hoveredEdge.value = null
  edgeInsertAnchorEdge.value = null
  edgeInsertHoverLock.value = false
  edgeInsertMenuOpen.value = false
}

function createWorkflowEdgeFromConnection(conn, idSuffix) {
  const normalized = normalizeConnection(conn)
  return ensureEdgeId({
    id: `edge_${Date.now()}_${idSuffix}`,
    type: 'workflow-bezier',
    source: normalized.source,
    target: normalized.target,
    sourceHandle: normalized.sourceHandle || HANDLE_OUT,
    targetHandle: HANDLE_IN,
    selectable: true,
    style: edgeStyle.value,
  }, idSuffix)
}

/** 在连线中间插入节点并拆成两条边 */
function onInsertNodeOnEdge(nodeType) {
  const edge = hoveredEdge.value
  if (!edge || isVersionPreview.value) return
  if (nodeType === 'start' || nodeType === 'end') {
    message.warning('不能在连线中间插入开始或结束节点')
    return
  }

  const sourceNode = getNodeById(edge.source)
  const targetNode = getNodeById(edge.target)
  if (!sourceNode || !targetNode) return

  const newNodeId = `node_${Date.now()}`
  const sc = getNodeCenterForInsert(sourceNode)
  const tc = getNodeCenterForInsert(targetNode)
  const mid = {
    x: (sc.x + tc.x) / 2,
    y: (sc.y + tc.y) / 2,
  }
  let newNode = {
    id: newNodeId,
    type: nodeType,
    position: { x: mid.x - 90, y: mid.y - 44 },
    data: getDefaultNodeData(nodeType),
  }
  if (isGroupNodeType(nodeType)) {
    message.warning('循环/批处理容器请从节点库拖入画布')
    return
  }
  // 插入到连线中间时若落在容器内则挂入
  const midPack = attachNodeToGroupAndResize(
    newNode,
    findGroupAtPoint(nodes.value, mid, { findNode }) || null,
    nodes.value,
    mid,
    findNode,
  )
  if (midPack.group) {
    newNode = midPack.node
  }

  const edgeToNew = normalizeConnection({
    source: edge.source,
    target: newNodeId,
    sourceHandle: edge.sourceHandle || HANDLE_OUT,
    targetHandle: HANDLE_IN,
  })
  const edgeFromNew = normalizeConnection({
    source: newNodeId,
    target: edge.target,
    sourceHandle: HANDLE_OUT,
    targetHandle: HANDLE_IN,
  })

  if (!edge.id) {
    message.warning('无法识别连线，请刷新后重试')
    return
  }
  const edgesWithoutOld = edges.value.filter(e => e.id !== edge.id)
  let nodesWithNew = [...nodes.value]
  if (midPack.group) {
    nodesWithNew = nodesWithNew.map(n => (n.id === midPack.group.id ? midPack.group : n))
    newNode = midPack.node
  }
  nodesWithNew.push(newNode)
  const ctx = { nodes: sortNodesParentFirst(nodesWithNew), edges: edgesWithoutOld }

  if (!isValidWorkflowConnection(edgeToNew, ctx)) {
    message.warning('无法插入：上游连线不合法')
    return
  }
  if (!isValidWorkflowConnection(edgeFromNew, ctx)) {
    message.warning('无法插入：下游连线不合法')
    return
  }

  recordHistory(`在连线上插入「${getNodeTitle(nodeType)}」节点`)
  commitWorkflowNodes(ctx.nodes)
  edges.value = [
    ...edgesWithoutOld,
    createWorkflowEdgeFromConnection(edgeToNew, Date.now()),
    createWorkflowEdgeFromConnection(edgeFromNew, Date.now() + 1),
  ]
  triggerRef(nodes)
  clearEdgeInsertUi()
  clearEdgeSelection()
  selectedNode.value = newNode
  message.success(`已插入「${getNodeTitle(nodeType)}」`)
  scheduleAutoSave()
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

  recordHistory(`添加「${getNodeTitle(nodeType)}」节点`)

  const position = flowPointFromEvent(event)
  let newNode

  if (isGroupNodeType(nodeType)) {
    if (isGroupNestedDrop(nodeType, nodes.value, position, findNode)) {
      message.warning('循环/批处理节点不能嵌套到其他容器中')
      return
    }
    const pack = createGroupWithBuiltins(nodeType, position, getDefaultNodeData(nodeType))
    commitWorkflowNodes([...nodes.value, pack.group, ...pack.children])
    edges.value = pruneInvalidEdges(nodes.value, edges.value)
    scheduleFitView()
    return
  }

  const newId = `node_${Date.now()}`
  newNode = {
    id: newId,
    type: nodeType,
    position,
    data: getDefaultNodeData(nodeType),
    draggable: true,
    selectable: true,
    connectable: true,
    ...(nodeType === 'input' || nodeType === 'output'
      ? { sourcePosition: 'right', targetPosition: 'left' }
      : {}),
  }
  commitWorkflowNodes([...nodes.value, newNode])
  applyNodeGroupMembership(newId, position)
  edges.value = pruneInvalidEdges(nodes.value, edges.value)
  scheduleFitView()
}

/**
 * 同步拖拽中的节点坐标（对齐 admin onNodeDrag：position 不走 onNodesChange）
 */
function syncDraggedNodePositions(dragNodes) {
  if (isVersionPreview.value || !dragNodes?.length) return
  const posMap = new Map()
  for (const dn of dragNodes) {
    if (dn?.id && dn.position) {
      posMap.set(dn.id, normalizePosition(dn.position))
    }
  }
  if (!posMap.size) return
  let changed = false
  const next = nodes.value.map(n => {
    if (!posMap.has(n.id)) return n
    const nextPos = posMap.get(n.id)
    const cur = normalizePosition(n.position)
    if (cur.x === nextPos.x && cur.y === nextPos.y) return n
    changed = true
    return { ...n, position: nextPos }
  })
  if (changed) {
    nodes.value = next
  }
}

/**
 * 节点变更：position 由 onNodeDrag 同步；dimensions 只刷新内部尺寸，不写回 nodes（避免卡死）
 */
function onNodesChange(changes) {
  if (isVersionPreview.value || !changes?.length) return

  if (changes.every(c => c.type === 'dimensions')) {
    scheduleUpdateNodeInternals(changes.map(c => c.id).filter(Boolean))
    return
  }

  const actionable = changes.filter(c => c.type !== 'position' && c.type !== 'dimensions' && c.type !== 'select')
  if (!actionable.length) return

  const type = actionable[0]?.type
  if (type !== 'remove') return

  const prevMap = new Map(nodes.value.map(n => [n.id, n]))
  const nextNodes = applyNodeChanges(actionable, nodes.value).map(n => {
    const old = prevMap.get(n.id)
    const merged = old
      ? {
          ...n,
          parentNode: n.parentNode ?? old.parentNode,
          extent: n.extent ?? old.extent,
          expandParent: n.expandParent ?? old.expandParent,
          zIndex: n.zIndex ?? old.zIndex,
          style: n.style ?? old.style,
        }
      : n
    return ensureNodeDraggable(isGroupNodeType(n.type) ? migrateGroupNodeFields(merged) : merged)
  })
  nodes.value = sortNodesParentFirst(nextNodes)
}

function onNodeDragStart({ node, nodes: draggedNodes }) {
  const dragList = draggedNodes?.length ? draggedNodes : (node ? [node] : [])
  draggingNode.value = node || dragList[0] || null
  isNodeDragging.value = true
  dragOverTrash.value = false
  recordHistory(formatBatchNodeHistoryLabel('move', dragList.map(d => nodes.value.find(n => n.id === d.id) || d)))
  if (node && isGroupNodeType(node.type)) {
    draggingGroupId.value = node.id
    setGroupChildrenDragMask(node.id, true)
    bindGroupDragPointerRelease()
  }
  // 拖出容器：仅放开当前子节点的 parent 限制（不整表替换，避免闪烁）
  if (node && getNodeParentId(node) && !isGroupBuiltinType(node.type)) {
    const idx = nodes.value.findIndex(n => n.id === node.id)
    if (idx >= 0) {
      nodes.value[idx] = { ...nodes.value[idx], extent: undefined, expandParent: false }
    }
    try {
      updateNode(node.id, { extent: undefined, expandParent: false })
    } catch (_) { /* ignore */ }
  }
}

function onNodeDrag({ node, nodes: draggedNodes, event }) {
  const dragList = draggedNodes?.length ? draggedNodes : node ? [node] : []
  syncDraggedNodePositions(dragList)

  if (event && trashRef.value) {
    const rect = trashRef.value.getBoundingClientRect()
    dragOverTrash.value =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom
  }
}

function onNodeDragStop({ node, nodes: draggedNodes, event }) {
  const activeNode = node || draggedNodes?.[0] || draggingNode.value
  endGroupDragMask(activeNode && isGroupNodeType(activeNode.type) ? activeNode.id : null)

  const dragList = draggedNodes?.length ? draggedNodes : activeNode ? [activeNode] : []
  syncDraggedNodePositions(dragList)

  if (event && trashRef.value) {
    const rect = trashRef.value.getBoundingClientRect()
    dragOverTrash.value =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom
  }

  if (dragOverTrash.value) {
    cancelPendingMoveHistory()
    const targets = resolveTrashDeleteTargets(dragList)
    if (targets.length) {
      const count = removeNodesByIds(targets.map(n => n.id))
      if (count > 0) {
        message.success(count > 1 ? `已删除 ${count} 个节点` : '节点已删除')
      }
    }
    isNodeDragging.value = false
    dragOverTrash.value = false
    draggingNode.value = null
    validateWorkflow(false)
    scheduleAutoSave()
    return
  }

  if (activeNode && isGroupBuiltinType(activeNode.type) && getNodeParentId(activeNode)) {
    const pid = getNodeParentId(activeNode)
    const resized = fitGroupBoundsToChildren(
      nodes.value.find(n => n.id === pid),
      nodes.value,
      findNode,
    )
    const patches = [{
      id: activeNode.id,
      extent: 'parent',
      expandParent: true,
      position: normalizePosition(activeNode.position),
    }]
    if (resized?.style) patches.push({ id: pid, style: resized.style })
    patchWorkflowNodes(patches, { refreshIds: [activeNode.id, pid] })
  } else if (activeNode && !isGroupNodeType(activeNode.type) && !isGroupBuiltinType(activeNode.type)) {
    const dropPoint = getNodeDropPoint(
      nodes.value.find(n => n.id === activeNode.id) || activeNode,
      nodes.value,
      findNode,
    )
    applyNodeGroupMembership(activeNode.id, dropPoint)
    edges.value = pruneInvalidEdges(nodes.value, edges.value)
  }
  isNodeDragging.value = false
  dragOverTrash.value = false
  draggingNode.value = null
  validateWorkflow(false)
  scheduleAutoSave()
}

function removeNodesByIds(nodeIds, { skipHistory = false, historyLabel = null } = {}) {
  const uniqueIds = [...new Set((nodeIds || []).filter(Boolean))]
  const removeIds = new Set()
  let blockedBuiltin = false

  for (const nodeId of uniqueIds) {
    const node = nodes.value.find(n => n.id === nodeId)
    if (!node || !isNodeDeletable(node)) continue
    if (isGroupBuiltinType(node.type) && getNodeParentId(node)) {
      blockedBuiltin = true
      continue
    }
    removeIds.add(nodeId)
    if (isGroupNodeType(node.type)) {
      nodes.value.filter(n => getNodeParentId(n) === nodeId).forEach(c => removeIds.add(c.id))
    }
  }

  if (blockedBuiltin && !removeIds.size) {
    message.warning('容器内置节点不可单独删除，请删除循环/批处理容器')
    return 0
  }
  if (!removeIds.size) return 0

  const removedNodes = nodes.value.filter(n => removeIds.has(n.id))
  if (!skipHistory) {
    recordHistory(historyLabel || formatBatchNodeHistoryLabel('delete', removedNodes))
  }

  nodes.value = nodes.value.filter(n => !removeIds.has(n.id))
  edges.value = edges.value.filter(e => !removeIds.has(e.source) && !removeIds.has(e.target))
  triggerRef(nodes)
  if (selectedNode.value && removeIds.has(selectedNode.value.id)) {
    selectedNode.value = null
  }
  nodePanelSnapshot.value = null
  nodePanelHistoryRecorded.value = false
  clearEdgeSelection()
  clearEdgeInsertUi()
  scheduleFitView()
  scheduleAutoSave()
  return removeIds.size
}

function removeNodeById(nodeId, { skipHistory = false, historyLabel = null } = {}) {
  return removeNodesByIds([nodeId], { skipHistory, historyLabel })
}

function isValidWorkflowConnectionFn(connection) {
  return isValidWorkflowConnection(connection, {
    nodes: nodes.value,
    edges: edges.value,
  })
}

function applyEdgePatch(edgeId, patch) {
  const idx = edges.value.findIndex(e => e.id === edgeId)
  if (idx < 0) return false
  const old = edges.value[idx]
  const normalized = normalizeConnection({
    source: patch.source ?? old.source,
    target: patch.target ?? old.target,
    sourceHandle: patch.sourceHandle ?? old.sourceHandle,
    targetHandle: patch.targetHandle ?? old.targetHandle ?? HANDLE_IN,
  })
  const merged = redirectContainerConnection(normalized)
  if (!isValidWorkflowConnection(merged, {
    nodes: nodes.value,
    edges: edges.value,
    excludeEdgeId: edgeId,
  })) {
    message.warning('连线不合法：请从「出」连到下游节点「入」，且不能形成逆向或重复连线')
    return false
  }
  const updated = {
    ...old,
    ...merged,
    targetHandle: HANDLE_IN,
    selectable: true,
    style: old.style || edgeStyle.value,
  }
  const next = [...edges.value]
  next[idx] = updated
  edges.value = next
  if (selectedEdge.value?.id === edgeId) {
    selectedEdge.value = updated
  }
  scheduleAutoSave()
  return true
}

// 同步 Vue Flow 边变更到受控数据（选中、增删等；历史栈由 onConnect / deleteSelectedEdge 等显式记录）
function onEdgesChange(changes) {
  if (!changes?.length) return
  edges.value = applyEdgeChanges(changes, edges.value)
  if (changes.some(c => c.type === 'add' || c.type === 'remove')) {
    scheduleAutoSave()
  }
}

/**
 * 容器壳层连线自动重定向：
 * - source 为容器壳层 → 重定向到内置 end 节点（出 = 结束节点）
 * - target 为容器壳层 → 重定向到内置 start 节点（入 = 开始节点）
 */
function redirectContainerConnection(conn) {
  const srcNode = nodes.value.find(n => n.id === conn.source)
  const tgtNode = nodes.value.find(n => n.id === conn.target)
  let { source, target, sourceHandle, targetHandle } = conn

  // source 是容器壳层 → 重定向到内置 end 节点
  if (srcNode && isGroupNodeType(srcNode.type)) {
    const pair = getGroupBuiltinPair(srcNode.type)
    const endNode = nodes.value.find(n => getNodeParentId(n) === srcNode.id && n.type === pair.end)
    if (endNode) {
      source = endNode.id
      sourceHandle = HANDLE_OUT
    }
  }

  // target 是容器壳层 → 重定向到内置 start 节点
  if (tgtNode && isGroupNodeType(tgtNode.type)) {
    const pair = getGroupBuiltinPair(tgtNode.type)
    const startNode = nodes.value.find(n => getNodeParentId(n) === tgtNode.id && n.type === pair.start)
    if (startNode) {
      target = startNode.id
      targetHandle = HANDLE_IN
    }
  }

  return { ...conn, source, target, sourceHandle, targetHandle }
}

// 新建连线
function onConnect(params) {
  if (isVersionPreview.value) return
  const normalized = normalizeConnection(params)

  // 容器壳层自动重定向：source=容器 → 找内置 end 节点，target=容器 → 找内置 start 节点
  const redirected = redirectContainerConnection(normalized)
  if (!isValidWorkflowConnection(redirected, { nodes: nodes.value, edges: edges.value })) {
    message.warning('无法连接：请从上游节点右侧「出」拖到下游节点左侧「入」')
    return
  }
  if (wouldViolateMultiOutgoingEdge(nodes.value, edges.value, redirected)) {
    message.warning(getMultiOutgoingEdgeHint())
    return
  }
  recordHistory(formatEdgeHistoryLabel('新建', redirected))
  const newEdge = {
    id: `edge_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
    type: 'workflow-bezier',
    source: redirected.source,
    target: redirected.target,
    sourceHandle: redirected.sourceHandle || HANDLE_OUT,
    targetHandle: HANDLE_IN,
    selectable: true,
    style: edgeStyle.value,
  }
  edges.value = addEdge(newEdge, [...edges.value])
  scheduleAutoSave()
}

// 拖拽重连连线端点
function onEdgeUpdate({ edge, connection }) {
  if (isVersionPreview.value || !edge || !connection) return
  recordHistory(formatEdgeHistoryLabel('调整', edge))
  applyEdgePatch(edge.id, connection)
}

const edgeTargetCandidates = computed(() =>
  nodes.value.filter(n => n.type !== 'start' && !isGroupNodeType(n.type))
)

const edgeSourceHandleOptions = computed(() => {
  const edge = selectedEdge.value
  if (!edge) return []
  const node = nodes.value.find(n => n.id === edge.source)
  if (!node) return [{ id: HANDLE_OUT, label: '出（默认）' }]
  if (node.type === 'condition') {
    const opts = []
    ;(node.data?.conditionGroups || []).forEach((g, i) => {
      if (g?.id) {
        opts.push({ id: `${node.id}_${g.id}`, label: `出（${g.label || `条件 ${i + 1}`}）` })
      }
    })
    opts.push({ id: `${node.id}_default`, label: '出（都未命中）' })
    return opts
  }
  if (node.type === 'classifier') {
    const opts = []
    ;(node.data?.conditions || []).forEach(c => {
      if (c.id && c.id !== 'default') {
        opts.push({ id: `${node.id}_${c.id}`, label: `出（${c.subject || c.id}）` })
      }
    })
    if (node.id) {
      opts.push({ id: `${node.id}_default`, label: '出（其他意图）' })
    }
    return opts
  }
  return [{ id: HANDLE_OUT, label: '出（默认）' }]
})

function onEdgeTargetChange(targetId) {
  if (!selectedEdge.value || isVersionPreview.value) return
  recordHistory(formatEdgeHistoryLabel('修改目标节点：', selectedEdge.value))
  applyEdgePatch(selectedEdge.value.id, { target: targetId, targetHandle: HANDLE_IN })
}

function onEdgeSourceHandleChange(sourceHandle) {
  if (!selectedEdge.value || isVersionPreview.value) return
  recordHistory(formatEdgeHistoryLabel('修改出口：', selectedEdge.value))
  applyEdgePatch(selectedEdge.value.id, { sourceHandle })
}

// 点击节点：从 nodes 源数据取引用，避免 VueFlow 内部节点与侧栏双向同步死循环
function onNodeClick(event) {
  const nodeId = event?.node?.id
  if (!nodeId) return
  if (selectedNode.value?.id === nodeId && !selectedEdge.value) return
  clearEdgeSelection()
  selectedEdge.value = null
  const stored = nodes.value.find(n => n.id === nodeId) || event.node
  selectedNode.value = stored
  captureNodePanelSnapshot(stored)
}

// 点击连线（仅选中，不触发删除）
function onEdgeClick({ edge }) {
  selectedNode.value = null
  selectedEdge.value = cloneEdge(edge)
  try {
    const prev = getSelectedEdges()
    if (prev?.length) {
      removeSelectedEdges(prev)
    }
    if (edge?.id) {
      addSelectedEdges([edge.id])
    }
  } catch (_) { /* ignore */ }
}

// 点击空白区域
function onPaneClick() {
  closeNodePanel()
  clearEdgeSelection()
  clearEdgeInsertUi()
}

// 同步节点数据
function syncNodes() {
  syncSelectedNodeToStore()
  ensureNodePanelEditHistory()
  triggerRef(nodes)
  scheduleAutoSave()
}

/** 删除条件组/意图等动态出口后，移除对应 sourceHandle 的边 */
function onRemoveSourceHandles({ nodeId, handles }) {
  if (!nodeId || !handles?.length || isVersionPreview.value) return
  const handleSet = new Set(handles)
  const next = edges.value.filter(e => !(e.source === nodeId && handleSet.has(e.sourceHandle)))
  if (next.length === edges.value.length) return
  recordHistory('删除分支连线')
  edges.value = next
  scheduleAutoSave()
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
  selectedNode.value.data.toolName = tool?.name || ''
  if (tool?.name === 'ask_user') {
    const mappings = selectedNode.value.data.outputMappings
    if (!Array.isArray(mappings) || mappings.length === 0) {
      selectedNode.value.data.outputMappings = [
        { key: 'answer', value: '{{answer}}', label: '用户回答', desc: 'resume 后用户选择或输入的内容' },
        { key: 'question', value: '{{question}}', label: '提问内容', desc: '工具执行阶段即可用' },
      ]
    }
  }
  syncNodes()
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
  if (node) panToNode(node)
}

function clearReplayNodeStatus() {
  if (viewingTestHistory.value && testHistoryViewerNodes.value.length) {
    testHistoryNodeStates.value = {}
    return
  }
  clearNodeDebugStatus()
}

function focusTestHistoryNode(nodeId) {
  if (!nodeId) return
  testHistoryViewerRef.value?.focusNodeById?.(nodeId)
}

function setReplayNodeStatus(nodeId, status, durationMs) {
  if (viewingTestHistory.value) {
    if (!testHistoryViewerNodes.value.length) return
    testHistoryNodeStates.value = {
      ...testHistoryNodeStates.value,
      [nodeId]: {
        ...testHistoryNodeStates.value[nodeId],
        debugStatus: status,
        ...(durationMs != null ? { durationMs } : {}),
      },
    }
    focusTestHistoryNode(nodeId)
    return
  }
  setNodeDebugStatus(nodeId, status)
}

async function animateWorkflowTest(events, animationToken) {
  const token = animationToken ?? testAnimationGeneration.value
  testAnimating.value = true
  try {
    await replayWorkflowNodeEvents(events, {
      isCancelled: () => token !== testAnimationGeneration.value,
      onClear: clearReplayNodeStatus,
      onNodeStart: (ev) => {
        setReplayNodeStatus(ev.nodeId, 'executing')
        testCurrentNodeId.value = ev.nodeId
      },
      onNodeComplete: (ev) => {
        const status = ev.success === false ? 'fail' : (ev.suspended ? 'executing' : 'success')
        setReplayNodeStatus(ev.nodeId, status, ev.durationMs)
      },
    })
  } finally {
    if (token === testAnimationGeneration.value) {
      testAnimating.value = false
    }
  }
}

function applyTestHistorySnapshot(detail) {
  const snapshot = normalizeWorkflowGraphSnapshot(detail?.workflowGraph)
  testHistoryRawEdges.value = snapshot.edges
  const viewer = workflowGraphToVueFlow(snapshot)
  testHistoryViewerNodes.value = viewer.nodes
  testHistoryViewerEdges.value = viewer.edges
  testHistoryNodeStates.value = {}
}

function clearTestHistorySnapshot() {
  testHistoryViewerNodes.value = []
  testHistoryViewerEdges.value = []
  testHistoryRawEdges.value = []
  testHistoryNodeStates.value = {}
}

// 删除选中节点（支持 Shift/Ctrl 多选批量删除）
function deleteSelectedNodes() {
  const selected = getSelectedDeletableNodes()
  if (!selected.length) return
  removeNodesByIds(selected.map(n => n.id))
}

function deleteSelectedNode() {
  deleteSelectedNodes()
}

// 删除选中连线（必须按 id 删除，避免误删全部）
function deleteSelectedEdge() {
  const edge = selectedEdge.value
  if (!edge?.id) {
    message.warning('无法识别连线，请刷新页面后重试')
    return
  }
  recordHistory(formatEdgeHistoryLabel('删除', edge))
  edges.value = applyEdgeChanges(
    [{ type: 'remove', id: edge.id, source: edge.source, target: edge.target, sourceHandle: edge.sourceHandle ?? null, targetHandle: edge.targetHandle ?? null }],
    edges.value
  )
  selectedEdge.value = null
  clearEdgeSelection()
  scheduleAutoSave()
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

  // 3. 检查节点连接与逆向边
  const connectedIds = new Set()
  edges.value.forEach(e => {
    connectedIds.add(e.source)
    connectedIds.add(e.target)
    const src = nodes.value.find(n => n.id === e.source)
    const tgt = nodes.value.find(n => n.id === e.target)
    if (src?.type === 'end') {
      errors.push({ nodeId: e.source, field: 'connection', message: '结束节点不能作为连线起点' })
    }
    if (tgt?.type === 'start') {
      errors.push({ nodeId: e.target, field: 'connection', message: '开始节点不能作为连线终点' })
    }
    if (e.targetHandle && e.targetHandle !== HANDLE_IN) {
      errors.push({ nodeId: e.target, field: 'connection', message: `连线必须接入目标节点「入」端口，当前为 ${e.targetHandle}` })
    }
    if (e.sourceHandle === HANDLE_IN) {
      errors.push({ nodeId: e.source, field: 'connection', message: '连线不能从目标节点「入」端口连出' })
    }
    if (!isValidWorkflowConnection(
      { source: e.source, target: e.target, sourceHandle: e.sourceHandle, targetHandle: e.targetHandle },
      { nodes: nodes.value, edges: edges.value, excludeEdgeId: e.id }
    )) {
      errors.push({ nodeId: e.source, field: 'connection', message: '存在非法或重复连线' })
    }
  })
  nodes.value.forEach(n => {
    if (n.type === 'start' || isGroupNodeType(n.type) || isGroupBuiltinType(n.type) || getNodeParentId(n)) return
    if (!connectedIds.has(n.id)) {
      errors.push({ nodeId: n.id, field: 'connection', message: '节点未连接到工作流' })
    }
  })

  // 3.1 非分支节点禁止多条出边（当前引擎仅执行第一条）
  validateMultiOutgoingEdges(nodes.value, edges.value).forEach(err => errors.push(err))

  // 4. 检查节点配置
  nodes.value.forEach(n => {
    if (isGroupBuiltinType(n.type)) return
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
    if (n.type === 'condition') {
      const groups = (n.data?.conditionGroups || []).filter(g => g?.id && Array.isArray(g.rules) && g.rules.length)
      if (!groups.length) {
        errors.push({ nodeId: n.id, field: 'conditionGroups', message: '请配置至少一个条件组' })
      }
      const defaultHandle = `${n.id}_default`
      const hasDefaultEdge = edges.value.some(e => e.source === n.id && e.sourceHandle === defaultHandle)
      if (!hasDefaultEdge) {
        errors.push({ nodeId: n.id, field: 'defaultBranch', message: '条件分支节点缺少「都未命中」默认路径' })
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
      errors.push({ nodeId: n.id, field: 'componentCode', message: '请选择子工作流' })
    }
    if (n.type === 'sub_agent') {
      if (n.data.subAgentId == null || n.data.subAgentId === '') {
        errors.push({ nodeId: n.id, field: 'subAgentId', message: '请选择 SubAgent' })
      }
      if (!n.data.task?.trim()) {
        errors.push({ nodeId: n.id, field: 'task', message: '请填写任务描述' })
      }
    }
  })

  // 5. 变量引用静态校验（命名空间 {{nodeId.field}}）
  const referErrors = validateWorkflowReferences(nodes.value, edges.value)
  referErrors.forEach(err => errors.push(err))

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
  // 保存到数据库前强制校验：校验失败禁止暂存，避免把不完整的工作流写入草稿
  const errors = validateWorkflow(false)
  if (errors.length > 0) {
    notification.warning({
      message: '工作流配置不完整，无法暂存',
      description: `发现 ${errors.length} 个配置错误，请完善后重试`,
    })
    return
  }
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

function openWorkflowExchange() {
  if (isVersionPreview.value) {
    message.warning('历史版本只读，请回到当前草稿后导入')
    return
  }
  workflowExchangePlatformTab.value = 'dify'
  difyActiveTab.value = 'import'
  workflowExchangeVisible.value = true
}

async function onDifyTabChange(tab) {
  if (tab !== 'export' || isVersionPreview.value) return
  await previewDifyExport()
}

function selectDifyImportFile(file) {
  const fileName = file?.name?.toLowerCase() || ''
  if (!fileName.endsWith('.yml') && !fileName.endsWith('.yaml')) {
    message.error('仅支持 .yml 或 .yaml 文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('Dify YAML 不能超过 2 MiB')
    return false
  }
  difyImportFile.value = file
  difyImportFileList.value = [file]
  difyImportPreview.value = null
  return false
}

function clearDifyImportFile() {
  difyImportFile.value = null
  difyImportFileList.value = []
  difyImportPreview.value = null
  return true
}

async function previewDifyImport() {
  if (!difyImportFile.value) return
  difyImportLoading.value = true
  try {
    const res = await previewDifyWorkflowImport(agentId, difyImportFile.value)
    difyImportPreview.value = res.data
  } catch (e) {
    difyImportPreview.value = null
  } finally {
    difyImportLoading.value = false
  }
}

async function commitDifyImport() {
  if (!canCommitDifyImport.value || !difyImportFile.value) return
  difyImportCommitting.value = true
  try {
    const res = await importDifyWorkflow(agentId, difyImportFile.value)
    const graph = res.data?.graph
    if (!graph) throw new Error('导入结果缺少工作流图')
    applyWorkflowGraph(graph)
    await finalizeWorkflowGraphAfterLoad()
    workflowStatus.value = workflowStatus.value === 'published' ? 'published_editing' : 'draft'
    workflowExchangeVisible.value = false
    clearDifyImportFile()
    message.success('Dify 工作流已导入到当前草稿，请补齐待修复配置后再发布')
  } catch (e) {
    notification.error({ message: '导入失败', description: e.message })
  } finally {
    difyImportCommitting.value = false
  }
}

async function previewDifyExport() {
  difyExportLoading.value = true
  try {
    const res = await previewDifyWorkflowExport(agentId)
    difyExportPreview.value = res.data
  } catch (e) {
    difyExportPreview.value = null
  } finally {
    difyExportLoading.value = false
  }
}

async function downloadDifyExport() {
  if (!canDownloadDifyExport.value) return
  difyExportDownloading.value = true
  try {
    const blob = await downloadDifyWorkflow(agentId)
    const link = document.createElement('a')
    link.href = URL.createObjectURL(new Blob([blob], { type: 'application/x-yaml;charset=utf-8' }))
    const name = (agent.value?.name || 'lightbot-workflow').replace(/[\\/:*?"<>|]/g, '_')
    link.download = `${name}.yml`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(link.href)
    message.success('Dify YAML 已下载')
  } catch (e) {
    notification.error({ message: '导出失败', description: e.message })
  } finally {
    difyExportDownloading.value = false
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
  clampVersionPanelPosition()
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

async function finalizeWorkflowGraphAfterLoad() {
  workflowInitializing.value = true
  await nextTick()
  scheduleUpdateNodeInternals()
  validateWorkflow(false)
  await nextTick()
  markWorkflowSaved()
  workflowInitializing.value = false
}

async function selectVersion(version) {
  if (version !== 'draft' && (version == null || version === 'undefined')) {
    message.warning('该版本无效，请选择已发布的版本')
    return
  }
  selectedVersion.value = version
  selectedNode.value = null
  clearEdgeSelection()
  testVisible.value = false

  if (version === 'draft') {
    try {
      const wfRes = await getWorkflowConfig(agentId)
      workflowStatus.value = wfRes.data.status || 'draft'
      applyWorkflowGraph(wfRes.data.draft)
      await finalizeWorkflowGraphAfterLoad()
      scheduleFitView(false)
    } catch (e) {
      notification.error({ message: '加载草稿失败', description: e.message })
    }
    return
  }

  try {
    const res = await getWorkflowVersionDetail(agentId, version)
    applyWorkflowGraph(res.data)
    await finalizeWorkflowGraphAfterLoad()
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

/** 右侧节点详情等导致画布宽度变化时，约束版本面板不越界、不错位 */
function clampVersionPanelPosition() {
  if (!versionVisible.value) return
  const canvas = canvasAreaRef.value
  if (!canvas) return
  const maxX = Math.max(12, canvas.clientWidth - VERSION_PANEL_WIDTH - 12)
  const maxY = Math.max(12, canvas.clientHeight - 120)
  versionPanelPos.value = {
    x: Math.min(Math.max(12, versionPanelPos.value.x), maxX),
    y: Math.min(Math.max(12, versionPanelPos.value.y), maxY),
  }
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

function openLeftPanelTab(tab) {
  leftPanelTab.value = tab
  panelCollapsed.value = false
}

function formatWorkflowLayout() {
  if (isVersionPreview.value || nodes.value.length < 2) return
  recordHistory('自动整理节点布局')
  const snapshotNode = n => ({
    id: n.id,
    x: n.position?.x,
    y: n.position?.y,
    w: n.style?.width,
    h: n.style?.height,
  })
  const before = JSON.stringify(nodes.value.map(snapshotNode))
  const { nodes: laid, edges: laidEdges } = applyWorkflowAutoLayout(nodes.value, edges.value)
  const after = JSON.stringify(laid.map(snapshotNode))
  commitWorkflowNodes(laid)
  edges.value = laidEdges
  scheduleAutoSave()
  if (before !== after) {
    message.success('已格式化工作流布局')
  } else {
    message.info('当前布局已整齐，无需调整')
  }
}

function clearTestConversation() {
  testMessages.value = []
  testResult.value = null
  testPendingConfirm.value = null
  viewingTestHistory.value = false
  selectedHistoryRunId.value = null
  liveTestSnapshot.value = null
}

async function loadTestHistoryList() {
  testHistoryLoading.value = true
  try {
    const res = await listWorkflowTestRuns(agentId)
    testHistoryList.value = res.data || []
  } catch {
    testHistoryList.value = []
  } finally {
    testHistoryLoading.value = false
  }
}

function stopTestReplayAnimation() {
  testAnimationGeneration.value++
  testAnimating.value = false
  testCurrentNodeId.value = null
  clearReplayNodeStatus()
}

/** 跳过历史回放动画，直接展示最终执行状态 */
function skipTestHistoryReplay() {
  if (!viewingTestHistory.value || !testAnimating.value) return
  testAnimationGeneration.value++
  testAnimating.value = false
  testCurrentNodeId.value = null
  const events = testResult.value?.nodeEvents || []
  if (testHistoryViewerNodes.value.length) {
    testHistoryNodeStates.value = eventsToNodeStates(events)
  }
}

async function applyTestRunDetail(detail, { fromHistory = false, replayAnimation = true } = {}) {
  if (fromHistory) {
    applyTestHistorySnapshot(detail)
  }
  testResult.value = {
    output: detail.output,
    nodeEvents: detail.nodeEvents || [],
    variables: detail.variables || {},
    usedDraft: detail.usedDraft,
    suspended: detail.status === 'suspended',
    runId: detail.runId,
    testRunId: detail.id,
  }
  if (detail.status === 'suspended' && detail.confirmForm && !fromHistory) {
    testPendingConfirm.value = { runId: detail.runId, confirmForm: detail.confirmForm }
  } else {
    testPendingConfirm.value = null
  }
  testCurrentNodeId.value = null
  if (!replayAnimation) {
    if (fromHistory && testHistoryViewerNodes.value.length) {
      testHistoryNodeStates.value = eventsToNodeStates(detail.nodeEvents || [])
    }
    return
  }
  await nextTick()
  if (fromHistory && testHistoryViewerNodes.value.length) {
    await nextTick()
    testHistoryViewerRef.value?.fitView?.()
  }
  const token = ++testAnimationGeneration.value
  await animateWorkflowTest(detail.nodeEvents || [], token)
}

async function openTestRunHistory(runId) {
  // 同一条历史正在回放时，重复点击不重新触发动画
  if (testAnimating.value && selectedHistoryRunId.value === runId) {
    return
  }
  // 切换到另一条记录时，取消进行中的回放动画
  if (testAnimating.value) {
    testAnimationGeneration.value++
    testAnimating.value = false
  }
  selectedHistoryRunId.value = runId
  viewingTestHistory.value = true
  testVisible.value = true
  selectedNode.value = null
  clearEdgeSelection()
  versionVisible.value = false
  if (!liveTestSnapshot.value && testResult.value) {
    liveTestSnapshot.value = {
      testResult: testResult.value,
      testPendingConfirm: testPendingConfirm.value,
    }
  }
  try {
    const res = await getWorkflowTestRun(agentId, runId)
    await applyTestRunDetail(res.data, { fromHistory: true })
  } catch (e) {
    notification.error({ message: '加载历史记录失败', description: e.message })
  }
}

function backToLiveTest() {
  stopTestReplayAnimation()
  viewingTestHistory.value = false
  selectedHistoryRunId.value = null
  testResult.value = null
  testPendingConfirm.value = null
  liveTestSnapshot.value = null
  clearTestHistorySnapshot()
}

async function deleteTestRunHistory(runId) {
  Modal.confirm({
    title: '删除测试记录',
    content: '确定删除这条测试运行记录吗？删除后无法恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteWorkflowTestRun(agentId, runId)
        if (selectedHistoryRunId.value === runId) {
          backToLiveTest()
          testResult.value = null
        }
        await loadTestHistoryList()
        message.success('已删除')
      } catch (e) {
        notification.error({ message: '删除失败', description: e.message })
      }
    },
  })
}

async function clearTestRunHistory() {
  Modal.confirm({
    title: '清空测试历史',
    content: '确定清空全部测试运行记录吗？清空后无法恢复。',
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await clearWorkflowTestRuns(agentId)
        backToLiveTest()
        testResult.value = null
        testHistoryList.value = []
        message.success('已清空测试历史')
      } catch (e) {
        notification.error({ message: '清空失败', description: e.message })
      }
    },
  })
}

function onTestTimelineNodeSelect(nodeId) {
  testCurrentNodeId.value = nodeId
  if (viewingTestHistory.value && testHistoryViewerNodes.value.length) {
    focusTestHistoryNode(nodeId)
    return
  }
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return
  // 测试执行/回放中仅定位画布，不展开节点详情侧栏
  if (testAnimating.value || testRunning.value || testStreaming.value) {
    panToNode(node)
    return
  }
  focusNode(node)
}

function stopLiveTestStream() {
  testStreamAbortController.value?.abort()
  testStreamAbortController.value = null
  testStreaming.value = false
}

function buildLiveTestEventCtx() {
  return {
    testResult,
    testPendingConfirm,
    testCurrentNodeId,
    testFailedNodeId,
    setReplayNodeStatus,
  }
}

function onTestDrawerClose() {
  // 历史快照回放中关闭侧栏：仅隐藏抽屉，画布动画与历史状态保持
  if (viewingTestHistory.value) {
    return
  }
  stopLiveTestStream()
  testAnimationGeneration.value++
  testAnimating.value = false
  clearReplayNodeStatus()
}

async function runWorkflowTest() {
  if (!testInput.value?.trim()) {
    message.warning(testMode.value === 'generation' ? '请输入测试内容' : '请输入本轮消息')
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

  const userText = testInput.value.trim()
  if (testMode.value === 'conversation') {
    testMessages.value.push({ role: 'user', content: userText })
  }

  testVisible.value = true
  viewingTestHistory.value = false
  selectedHistoryRunId.value = null
  liveTestSnapshot.value = null
  selectedNode.value = null
  clearEdgeSelection()
  stopLiveTestStream()
  testAnimationGeneration.value++
  testRunning.value = true
  testStreaming.value = true
  testResult.value = null
  testPendingConfirm.value = null
  testCurrentNodeId.value = null
  testFailedNodeId.value = null
  clearNodeDebugStatus()

  const payload = {
    input: userText,
    useDraft: testUseDraft.value,
    graph: buildWorkflowPayload(),
    testMode: testMode.value,
  }
  if (testMode.value === 'conversation') {
    payload.conversationHistory = testMessages.value.map(m => ({
      role: m.role,
      content: m.content,
    }))
  }

  const abortController = new AbortController()
  testStreamAbortController.value = abortController
  const eventCtx = buildLiveTestEventCtx()

  try {
    await testWorkflowStream(agentId, payload, {
      signal: abortController.signal,
      onEvent: (event) => handleLiveWorkflowTestEvent(event, eventCtx),
      onDone: (result) => {
        if (result) {
          applyWorkflowTestStreamResult(result, eventCtx)
        }
      },
    })

    if (testResult.value?.suspended) {
      message.info('工作流已暂停，请填写确认表单后继续')
      await loadTestHistoryList()
      return
    }
    if (testMode.value === 'conversation' && testResult.value?.output) {
      testMessages.value.push({ role: 'assistant', content: testResult.value.output })
    }
    testInput.value = ''
    message.success('测试运行完成')
    await loadTestHistoryList()
  } catch (e) {
    if (e.name === 'AbortError') return
    if (testMode.value === 'conversation' && testMessages.value.length) {
      const last = testMessages.value[testMessages.value.length - 1]
      if (last?.role === 'user' && last.content === userText) {
        testMessages.value.pop()
      }
    }
    notification.error({ message: '测试失败', description: e.message })
    clearNodeDebugStatus()
  } finally {
    testStreamAbortController.value = null
    testStreaming.value = false
    testRunning.value = false
  }
}

async function resumeWorkflowTest(formData) {
  if (!testPendingConfirm.value?.runId) return
  selectedNode.value = null
  clearEdgeSelection()
  stopLiveTestStream()
  testRunning.value = true
  testStreaming.value = true
  testFailedNodeId.value = null

  const runId = testPendingConfirm.value.runId
  const suspendNodeId = findSuspendNodeIdFromEvents(testResult.value?.nodeEvents, runId)
  patchLocalConfirmEventsBeforeResume(testResult, suspendNodeId, formData)
  if (suspendNodeId) {
    setReplayNodeStatus(suspendNodeId, 'success')
  }
  testPendingConfirm.value = null
  const abortController = new AbortController()
  testStreamAbortController.value = abortController
  const eventCtx = buildLiveTestEventCtx()

  try {
    await resumeWorkflowStream(agentId, { runId, formData }, {
      signal: abortController.signal,
      onEvent: (event) => handleLiveWorkflowTestEvent(event, eventCtx),
      onDone: (result) => {
        if (result) {
          applyWorkflowTestStreamResult(result, eventCtx)
        }
      },
    })

    if (testResult.value?.suspended) {
      message.info('工作流再次暂停，请继续确认')
      return
    }
    testPendingConfirm.value = null
    if (testMode.value === 'conversation' && testResult.value?.output) {
      testMessages.value.push({ role: 'assistant', content: testResult.value.output })
    }
    message.success('工作流执行完成')
    await loadTestHistoryList()
  } catch (e) {
    if (e.name === 'AbortError') return
    notification.error({ message: '恢复执行失败', description: e.message })
  } finally {
    testStreamAbortController.value = null
    testStreaming.value = false
    testRunning.value = false
  }
}

async function abandonWorkflowTest() {
  if (!testPendingConfirm.value?.runId) return
  const runId = testPendingConfirm.value.runId
  const suspendNodeId = findSuspendNodeIdFromEvents(testResult.value?.nodeEvents, runId)
  const notice = '工作流已终止（用户放弃回答）'

  testRunning.value = true
  try {
    await abandonWorkflowConfirm(agentId, { runId })
    const patched = patchLocalConfirmEventsOnAbandon(
      { nodeEvents: testResult.value?.nodeEvents || [] },
      suspendNodeId,
    )
    testPendingConfirm.value = null
    const prevOutput = testResult.value?.output || ''
    testResult.value = {
      ...(testResult.value || {}),
      nodeEvents: patched || testResult.value?.nodeEvents || [],
      suspended: false,
      output: prevOutput && !prevOutput.includes(notice) ? `${prevOutput}\n\n${notice}` : (prevOutput || notice),
    }
    if (suspendNodeId) {
      setReplayNodeStatus(suspendNodeId, 'failed')
    }
    message.success('已放弃本次确认，工作流已终止')
  } catch (e) {
    notification.error({ message: '放弃确认失败', description: e.message })
  } finally {
    testRunning.value = false
  }
}

// 返回
function goBack() {
  const navigate = () => router.push(`/app/agents/${agentId}`)
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
  height: var(--app-content-height);
  display: flex;
  flex-direction: column;
  background: var(--color-canvas-soft-2);
  overflow-x: hidden;
  position: relative;
}

/* 工作流加载遮罩 */
.workflow-loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 100;
  background: var(--color-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}
.workflow-loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.loading-dots {
  display: flex;
  gap: 8px;
}
.loading-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-link);
  animation: dot-bounce 1.2s ease-in-out infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.loading-text {
  font-size: 14px;
  color: var(--color-mute);
  font-weight: 500;
}
.workflow-loading-fade-leave-active {
  transition: opacity 0.3s ease;
}
.workflow-loading-fade-leave-to {
  opacity: 0;
}

.publish-tag { flex-shrink: 0; }
/* 盖在 VueFlow、缩略图、缩放控件、删除区之上 */
.edge-insert-label-layer {
  pointer-events: none;
}
.edge-insert-label-layer > * {
  pointer-events: auto;
}
.canvas-overlay-top {
  position: absolute;
  inset: 0;
  z-index: 1000;
  pointer-events: none;
}
.version-panel-float {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border: 1px solid var(--color-border-slate);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow: hidden;
  pointer-events: auto;
}
.version-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
  cursor: default;
}
.version-panel-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--color-mute);
  border-radius: 4px;
  cursor: grab;
  flex-shrink: 0;
  user-select: none;
}
.version-panel-drag-handle:hover {
  color: var(--color-mute);
  background: var(--color-canvas-soft);
}
.version-panel-drag-handle:active {
  cursor: grabbing;
}
.version-panel-title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-code);
}
.version-panel-close {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-mute);
  padding: 4px;
  line-height: 1;
}
.version-panel-close:hover { color: var(--color-body); }
.version-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.version-panel-footer {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid var(--color-hairline);
}
.version-preview-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  padding: 8px 16px;
  background: var(--color-warn-bg);
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
.version-item:hover { background: var(--color-canvas-soft-2); }
.version-item.active { background: var(--color-info-bg); border: 1px solid var(--color-purple-border); }
.version-item.draft { margin-bottom: 4px; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-item-title { font-weight: 600; font-size: 14px; color: var(--color-ink); }
.version-item-note {
  font-size: 13px;
  color: var(--color-text-dark);
  line-height: 1.45;
  margin-bottom: 4px;
  word-break: break-word;
}
.version-item-desc { font-size: 12px; color: var(--color-mute); }
.publish-modal-content {
  padding-bottom: 8px;
}
.publish-modal-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-mute);
}
.publish-modal-textarea {
  margin-bottom: 28px;
}
:deep(.publish-modal .ant-modal-body) {
  padding-bottom: 28px;
}
:deep(.publish-modal .ant-modal-footer) {
  margin-top: 4px;
  padding-top: 20px;
  border-top: 1px solid var(--color-hairline);
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
.btn-validate { color: var(--color-link); }
.auto-save-hint { font-size: 12px; color: var(--color-mute); white-space: nowrap; }
.auto-save-hint.saving { color: var(--color-link); }
.test-alert { margin-bottom: 12px; }
.test-mode-segment { margin-bottom: 8px; }
.test-mode-hint { font-size: 12px; color: var(--color-mute); margin-bottom: 12px; line-height: 1.5; }
.test-chat-box {
  max-height: 220px;
  overflow-y: auto;
  margin-bottom: 12px;
  padding: 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.test-chat-empty { font-size: 12px; color: var(--color-mute); text-align: center; padding: 16px 0; }
.test-chat-msg { margin-bottom: 10px; }
.test-chat-msg.user .test-chat-content { background: var(--color-info-bg); }
.test-chat-msg.assistant .test-chat-content { background: var(--color-canvas); border: 1px solid var(--color-hairline); }
.test-chat-role { font-size: 11px; color: var(--color-mute); display: block; margin-bottom: 4px; }
.test-chat-content { font-size: 13px; color: var(--color-text-dark); padding: 8px 10px; border-radius: 6px; white-space: pre-wrap; word-break: break-word; }
.test-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.test-current-node { font-size: 13px; color: var(--color-link); margin-bottom: 12px; padding: 8px 12px; background: var(--color-info-bg); border-radius: 6px; }
.test-event.active { background: var(--color-info-bg); color: #4338ca; font-weight: 600; }
.test-event-type { margin-right: 6px; }

.canvas-area :deep(.vue-flow__controls),
.canvas-area :deep(.vue-flow__minimap) {
  z-index: 5 !important;
}
.canvas-area :deep(.vue-flow__handle) {
  width: 16px !important;
  height: 16px !important;
  border: 2px solid #6366f1 !important;
  background: var(--color-canvas) !important;
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
  background: var(--color-canvas-soft-2);
}
.canvas-node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.canvas-node-name {
  flex: 1;
  color: var(--color-ink);
  font-weight: 500;
}
.canvas-node-type {
  color: var(--color-mute);
  font-size: 11px;
}
.conv-param-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.test-output {
  background: var(--color-canvas-soft);
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-size: 12px;
}
.test-event {
  font-size: 12px;
  color: var(--color-mute);
  padding: 4px 0;
}
.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-mute);
}
.text-muted {
  font-size: 12px;
  color: var(--color-mute);
}
.kb-config-preview {
  font-size: 12px;
  color: var(--color-text-dark);
}

.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
}

.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text-dark);
}

.btn-back:hover {
  background: var(--color-canvas-soft);
}

.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}

.toolbar-status {
  flex: 1;
  text-align: center;
}

.status-valid { color: var(--green-500); font-size: 13px; }
.status-error { color: var(--color-error); font-size: 13px; }
.status-error.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.status-error.clickable:hover {
  background: var(--color-error-bg);
}
.status-empty { color: var(--color-mute); font-size: 13px; }

/* 错误下拉面板 */
.error-dropdown {
  background: var(--color-canvas);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 400px;
}

.error-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  border-bottom: 1px solid var(--color-hairline);
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
  background: var(--color-error-bg);
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}

.error-node {
  color: #7c3aed;
  font-weight: 600;
}

.error-field {
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
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

.workflow-canvas-shell {
  flex: 1;
  position: relative;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.workflow-canvas-shell :deep(.canvas-area) {
  flex: 1;
  min-height: 0;
}

.test-history-viewer-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
}

.test-history-banner {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fef3c7;
  color: #92400e;
  font-size: 13px;
  text-align: center;
  border-bottom: 1px solid #fcd34d;
}

.test-history-skip-btn {
  flex-shrink: 0;
  padding: 0;
  height: auto;
  color: #92400e;
}

.test-history-viewer-overlay :deep(.workflow-viewer-canvas) {
  flex: 1;
  min-height: 0;
}

.test-history-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 14px;
  padding: 24px;
}

/* 左侧节点面板 */
.node-panel {
  width: 240px;
  background: var(--color-canvas);
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
  border-bottom: 1px solid var(--color-hairline);
}

.node-panel.collapsed .panel-header {
  padding: 12px 8px;
  justify-content: center;
}

.panel-collapsed-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  flex: 1;
}
.rail-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.rail-btn:hover {
  background: var(--color-info-bg);
  color: var(--color-link);
}
.rail-btn.active {
  background: #6366f1;
  color: #fff;
}

.btn-collapse {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--color-mute);
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
  color: var(--color-mute);
  padding: 4px 0;
}

/* 中间画布 */
.canvas-area {
  flex: 1;
  position: relative;
  background: var(--color-canvas);
  isolation: isolate;
}

/* 拖动节点删除区 */
.workflow-trash {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 30;
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
  color: var(--color-mute);
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
  background: var(--color-error-bg);
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
  color: var(--color-mute);
  font-size: 14px;
}

/* 右侧配置面板（宽度由 ResizableSidePanel 控制） */
.config-panel {
  width: 100%;
  min-width: 0;
  max-width: none;
  flex-shrink: 0;
  background: var(--color-canvas);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.config-panel .panel-header,
.config-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-hairline);
  gap: 12px;
}

.panel-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-left: auto;
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
  color: var(--color-ink);
}

.btn-node-example {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--color-info-bg);
  color: var(--color-link);
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-node-example:hover {
  background: #6366f1;
  color: #fff;
}

.node-detail-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.btn-node-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: var(--color-canvas-soft);
  color: var(--color-body);
  cursor: pointer;
}
.btn-node-action:hover:not(:disabled) {
  background: #6366f1;
  color: #fff;
}
.btn-node-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.config-panel .node-type-badge {
  flex: 1;
  min-width: 0;
}

.btn-close {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--color-mute);
}

.config-panel .panel-body {
  position: relative;
  flex: 1;
  padding: 16px;
  overflow-x: hidden;
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
  background: var(--color-error-bg);
  border-radius: 4px;
  color: #dc2626;
  font-size: 12px;
  margin-bottom: 4px;
}

.config-panel .panel-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--color-hairline);
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
  background: var(--color-canvas-soft);
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
  color: var(--color-mute);
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
  color: var(--color-mute);
  cursor: pointer;
  font-size: 16px;
}

.btn-help:hover {
  background: var(--color-canvas-soft-2);
  color: #7c3aed;
}

.edge-icon {
  background: #f3e8ff !important;
  color: #7c3aed !important;
}

.edge-detail-card {
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
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
  color: var(--color-mute);
}

.edge-detail-value {
  font-size: 13px;
  color: var(--color-text-dark);
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
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
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
  color: var(--color-mute);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.edge-node-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.edge-node-type {
  font-size: 12px;
  color: var(--color-mute);
}

.edge-handle-tag {
  font-size: 11px;
  color: #7c3aed;
  background: var(--color-purple-bg);
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.edge-arrow {
  display: flex;
  align-items: center;
  color: var(--color-mute);
  font-size: 16px;
  flex-shrink: 0;
}

.edge-retarget-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-hairline);
}
.edge-retarget-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dark);
  margin-bottom: 12px;
}
.edge-retarget-row {
  margin-bottom: 12px;
}
.edge-retarget-row label {
  display: block;
  font-size: 12px;
  color: var(--color-mute);
  margin-bottom: 4px;
}
.edge-retarget-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
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
  color: var(--color-text-dark);
}

.node-help-content h4 {
  margin: 16px 0 8px;
  font-size: 15px;
  color: var(--color-ink);
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
  background: var(--color-purple-bg);
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
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.node-help-tip {
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--color-warn-bg);
  border: 1px solid var(--color-warn-bg-deep);
  border-radius: 8px;
  color: #92400e;
  font-size: 13px;
}
</style>

<style>
/* Vue Flow 画板样式由 WorkflowCanvasCore 统一引入 workflowCanvasShell.css */

/* 与 AgentDetail 问号提示一致：限制宽度，箭头对准触发元素 */
.no-flip-tooltip .ant-tooltip-inner {
  max-width: min(320px, calc(100vw - 24px));
  word-break: break-word;
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
.resource-option-icon.mcp {
  color: #7c3aed;
}

.resource-option-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-ink);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-option-desc {
  font-size: 12px;
  color: var(--color-mute);
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
  color: var(--color-mute);
}

.resource-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
}

.resource-tag.enabled {
  background: var(--color-success-bg);
  color: #166534;
}

.resource-tag.type {
  background: var(--color-purple-bg);
  color: #4338ca;
}

.node-id-display {
  display: block;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--color-body);
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  word-break: break-all;
}

.dify-modal-intro {
  margin-bottom: 16px;
}

.dify-preview-summary {
  margin-top: 16px;
}

.dify-issue-list {
  margin-top: 12px;
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  padding: 8px;
}

.dify-issue-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 4px;
  color: var(--color-body);
  font-size: 13px;
}

.dify-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
</style>
