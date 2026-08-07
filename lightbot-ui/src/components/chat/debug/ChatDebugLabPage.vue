<template>
  <a-layout class="debug-lab-root">
    <ChatDebugLabHeader
      :nav-items="navMenuItems"
      :selected-keys="selectedNavKeys"
      @nav-click="onNavClick"
      @open-registry="registryOpen = true"
    />

    <a-layout-content class="debug-body-wrap">
      <div class="debug-module">
        <DebugSplitPane v-model="splitRatio">
          <template #editor>
            <div class="debug-editor-pane">
              <ChatDebugComposerWorkspace
                v-if="activeModule === 'composer'"
                ref="composerRef"
                v-model:composer-json="composerJson"
                v-model:ui-state="composerUiState"
                v-model:selected-preset-id="selectedPresetId"
                :preset-options="presetOptions"
                @parse="parseComposer"
                @apply-preset="applyPreset"
                @export-fixture="exportComposerFixture"
                @import-fixture="triggerFixtureImport"
                @compare-preview="onComparePreview"
                @stream-preview="onStreamPreview"
                @sub-mode-change="onComposerSubModeChange"
              />

              <ChatDebugSseReplayPanel v-else-if="activeModule === 'sse'" @preview="onSsePreview" />

              <ChatDebugToolPanel v-else-if="activeModule === 'tool'" ref="toolRef" @parse="parseTool" />

              <ChatDebugMarkdownPanel
                v-else-if="activeModule === 'markdown'"
                ref="markdownRef"
                v-model="markdownSource"
                @parse="parseMarkdown"
              />

              <ChatDebugCapabilityPanel
                v-else-if="activeModule === 'capability'"
                ref="capabilityRef"
                @parse="parseCapability"
              />

              <ChatDebugWorkflowPanel
                v-else-if="activeModule === 'workflow'"
                ref="workflowRef"
                @parse="parseWorkflow"
              />

              <ChatDebugAttachmentPanel
                v-else-if="activeModule === 'attachment'"
                ref="attachmentRef"
                @parse="parseAttachment"
              />

              <ChatDebugPromptPanel
                v-else-if="activeModule === 'prompt'"
                ref="promptRef"
                @parse="parsePrompt"
              />

              <ChatDebugEvalPanel
                v-else-if="activeModule === 'eval'"
                ref="evalRef"
                @parse="parseEval"
              />
            </div>
          </template>

          <template #preview>
            <template v-if="activeModule === 'composer' && comparePreviewLeft && comparePreviewRight">
              <div class="debug-compare-preview">
                <div class="debug-compare-preview-col">
                  <div class="debug-compare-preview-label">预览 A</div>
                  <ChatDebugMessagePreview :msg="comparePreviewLeft" empty-text="—" />
                </div>
                <div class="debug-compare-preview-col">
                  <div class="debug-compare-preview-label">预览 B</div>
                  <ChatDebugMessagePreview :msg="comparePreviewRight" empty-text="—" />
                </div>
              </div>
            </template>
            <template v-else-if="activeModule === 'tool'">
              <ChatDebugPreviewShell class="debug-preview-pane">
                <div v-if="previewToolEvent" class="debug-tool-preview-wrap">
                  <ToolCallRenderer :event="previewToolEvent" :message-index="0" />
                </div>
                <div v-else class="debug-preview-empty">点击「解析预览」查看工具 UI</div>
              </ChatDebugPreviewShell>
            </template>
            <template v-else-if="activeModule === 'markdown'">
              <ChatDebugPreviewShell class="debug-preview-pane">
                <div v-if="markdownParsed !== null" class="debug-md-preview-wrap">
                  <MarkdownPreview :content="markdownParsed" :finalized="true" />
                </div>
                <div v-else class="debug-preview-empty">点击「解析预览」查看 Markdown</div>
              </ChatDebugPreviewShell>
            </template>
            <template v-else-if="activeModule === 'prompt'">
              <ChatDebugPromptPreview :payload="previewPromptPayload" />
            </template>
            <template v-else-if="activeModule === 'eval'">
              <ChatDebugEvalPreview :payload="previewEvalPayload" />
            </template>
            <ChatDebugMessagePreview
              v-else
              :msg="previewMsg"
              :refs-section-expanded="composerUiState.refsSectionExpanded"
              :empty-text="previewEmptyText"
            />
          </template>
        </DebugSplitPane>
      </div>
    </a-layout-content>

    <ChatDebugRegistryModal v-model:open="registryOpen" />

    <input
      ref="fixtureInputRef"
      type="file"
      accept="application/json,.json"
      class="debug-fixture-input"
      @change="onFixtureFileChange"
    />

    <a-layout-footer class="debug-footer">
      Debug Lab · 纯前端 Mock · 与生产渲染同源 · 无需登录
    </a-layout-footer>
  </a-layout>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import DebugSplitPane from '@/components/chat/debug/DebugSplitPane.vue'
import ChatDebugLabHeader from '@/components/chat/debug/ChatDebugLabHeader.vue'
import ChatDebugComposerWorkspace from '@/components/chat/debug/ChatDebugComposerWorkspace.vue'
import ChatDebugSseReplayPanel from '@/components/chat/debug/ChatDebugSseReplayPanel.vue'
import ChatDebugPreviewShell from '@/components/chat/debug/ChatDebugPreviewShell.vue'
import ChatDebugMessagePreview from '@/components/chat/debug/ChatDebugMessagePreview.vue'
import ChatDebugToolPanel from '@/components/chat/debug/ChatDebugToolPanel.vue'
import ChatDebugMarkdownPanel from '@/components/chat/debug/ChatDebugMarkdownPanel.vue'
import ChatDebugCapabilityPanel from '@/components/chat/debug/ChatDebugCapabilityPanel.vue'
import ChatDebugWorkflowPanel from '@/components/chat/debug/ChatDebugWorkflowPanel.vue'
import ChatDebugAttachmentPanel from '@/components/chat/debug/ChatDebugAttachmentPanel.vue'
import ChatDebugPromptPanel from '@/components/chat/debug/ChatDebugPromptPanel.vue'
import ChatDebugPromptPreview from '@/components/chat/debug/ChatDebugPromptPreview.vue'
import ChatDebugEvalPanel from '@/components/chat/debug/ChatDebugEvalPanel.vue'
import ChatDebugEvalPreview from '@/components/chat/debug/ChatDebugEvalPreview.vue'
import ChatDebugRegistryModal from '@/components/chat/debug/ChatDebugRegistryModal.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import {
  apiMessageToEditorJson,
  buildPreviewMessage,
  createDefaultApiMessage,
  editorJsonToApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { DEBUG_PRESETS, getPresetById } from '@/utils/chat/debug/debugPresets'
import { getDebugLabNavMenuItems } from '@/constants/debugLabNav'
import { DEFAULT_DEBUG_UI_STATE } from '@/utils/chat/debug/debugUiState'
import { buildDebugFixture, downloadDebugFixture, readDebugFixtureFile } from '@/utils/chat/debug/debugFixture'

const route = useRoute()
const router = useRouter()

const navMenuItems = getDebugLabNavMenuItems()
const activeModule = ref(route.query.tab || 'composer')
const selectedNavKeys = computed(() => [activeModule.value])
const registryOpen = ref(false)
const splitRatio = ref(0.36)

const composerRef = ref(null)
const toolRef = ref(null)
const markdownRef = ref(null)
const capabilityRef = ref(null)
const workflowRef = ref(null)
const attachmentRef = ref(null)
const promptRef = ref(null)
const evalRef = ref(null)
const fixtureInputRef = ref(null)

const composerJson = ref(apiMessageToEditorJson(createDefaultApiMessage()))
const composerUiState = ref({ ...DEFAULT_DEBUG_UI_STATE })
const previewMsg = ref(null)
const previewToolEvent = ref(null)
const markdownSource = ref('')
const markdownParsed = ref(null)
const comparePreviewLeft = ref(null)
const comparePreviewRight = ref(null)
const previewPromptPayload = ref(null)
const previewEvalPayload = ref(null)

const presetOptions = DEBUG_PRESETS.map((p) => ({ value: p.id, label: p.label }))
const selectedPresetId = ref(undefined)

const previewEmptyText = computed(() => {
  if (activeModule.value === 'tool') return '点击「解析预览」查看工具 UI'
  if (activeModule.value === 'markdown') return '点击「解析预览」查看 Markdown'
  if (activeModule.value === 'sse') return '选择事件积木后开始回放，或直接粘贴真实 SSE 日志'
  return '点击「解析预览」查看渲染效果'
})

function onNavClick({ key }) {
  activeModule.value = key
  router.replace({ query: { ...route.query, tab: key } })
  clearPreview()
}

function clearPreview() {
  previewMsg.value = null
  previewToolEvent.value = null
  markdownParsed.value = null
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewPromptPayload.value = null
  previewEvalPayload.value = null
}

function buildMsgPreview(apiMsg, uiState = composerUiState.value) {
  return buildPreviewMessage(apiMsg, uiState)
}

function parseComposer() {
  const apiMsg = composerRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('消息已解析')
}

function parseTool() {
  const event = toolRef.value?.validateAndGetEvent?.()
  if (!event) return
  previewToolEvent.value = event
  previewMsg.value = null
  message.success('工具结果已解析')
}

function parseMarkdown() {
  markdownParsed.value = markdownSource.value
  previewMsg.value = null
  message.success('Markdown 已解析')
}

function parseCapability() {
  const apiMsg = capabilityRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('能力块消息已解析')
}

function parseWorkflow() {
  const apiMsg = workflowRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('工作流消息已解析')
}

function parseAttachment() {
  const apiMsg = attachmentRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('附件消息已解析')
}

function parsePrompt() {
  const payload = promptRef.value?.validateAndGetPayload?.()
  if (!payload) return
  previewMsg.value = null
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewPromptPayload.value = payload
  message.success('Prompt 预览已更新')
}

function parseEval() {
  const payload = evalRef.value?.validateAndGetPayload?.()
  if (!payload) return
  previewMsg.value = null
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewEvalPayload.value = payload
  message.success('Eval 预览已更新')
}

function onStreamPreview(msg) {
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  previewMsg.value = msg
}

function onSsePreview(payload) {
  if (!payload) {
    previewMsg.value = null
    return
  }
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
  const apiMsg = payload.apiMessage || payload
  previewMsg.value = buildMsgPreview(apiMsg, {
    ...composerUiState.value,
    streaming: !!payload.streaming,
    toolsDone: !payload.streaming,
  })
}

function onComparePreview(payload) {
  if (!payload) {
    comparePreviewLeft.value = null
    comparePreviewRight.value = null
    return
  }
  previewMsg.value = null
  comparePreviewLeft.value = buildMsgPreview(payload.left)
  comparePreviewRight.value = buildMsgPreview(payload.right)
  message.success('对比预览已更新')
}

function onComposerSubModeChange(mode) {
  if (mode !== 'compare') {
    comparePreviewLeft.value = null
    comparePreviewRight.value = null
  }
}

function applyPreset(presetId) {
  if (!presetId) return
  const msg = getPresetById(presetId)
  if (!msg) return
  composerJson.value = apiMessageToEditorJson(msg)
  clearPreview()
  message.success('预设已加载，点击「解析预览」查看效果')
}

function exportComposerFixture() {
  try {
    const apiMsg = editorJsonToApiMessage(composerJson.value)
    downloadDebugFixture(buildDebugFixture({
      message: apiMsg,
      uiState: composerUiState.value,
      label: 'composer-fixture',
    }))
    message.success('Fixture 已导出')
  } catch (e) {
    message.error(e.message || '导出失败')
  }
}

function triggerFixtureImport() {
  fixtureInputRef.value?.click()
}

async function onFixtureFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const fixture = await readDebugFixtureFile(file)
    const msg = fixture?.message || fixture
    if (!msg || typeof msg !== 'object') {
      message.error('Fixture 格式无效')
      return
    }
    composerJson.value = apiMessageToEditorJson(msg)
    if (fixture?.uiState) {
      composerUiState.value = { ...DEFAULT_DEBUG_UI_STATE, ...fixture.uiState }
    }
    clearPreview()
    activeModule.value = 'composer'
    message.success('Fixture 已导入，点击「解析预览」查看效果')
  } catch (err) {
    message.error(err.message || '导入失败')
  }
}

watch(() => route.query.tab, (tab) => {
  if (tab && navMenuItems.some((i) => i.key === tab)) {
    activeModule.value = tab
  }
})

watch(composerUiState, () => {
  if (activeModule.value === 'composer' && previewMsg.value && !comparePreviewLeft.value) {
    const apiMsg = composerRef.value?.validateAndGetMessage?.()
    if (apiMsg) previewMsg.value = buildMsgPreview(apiMsg)
  }
}, { deep: true })
</script>

<style scoped>
.debug-lab-root {
  min-height: var(--app-content-height);
  background: var(--color-canvas);
}

.debug-body-wrap {
  padding: 16px 24px 0;
  flex: 1;
}

.debug-module {
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-canvas);
}

.debug-editor-pane {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  background: var(--color-canvas-soft);
}

.debug-preview-pane {
  height: 100%;
}

.debug-preview-empty {
  padding: 48px 24px;
  text-align: center;
  color: var(--gray-400);
  font-size: 14px;
}

.debug-tool-preview-wrap,
.debug-md-preview-wrap {
  width: 100%;
  padding: 0 20px;
}

.debug-compare-preview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  height: 100%;
  padding: 12px;
  min-height: 0;
}

.debug-compare-preview-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.debug-compare-preview-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-fixture-input {
  display: none;
}

.debug-footer {
  text-align: center;
  padding: 12px;
  color: var(--gray-500);
  font-size: 12px;
  background: transparent;
}

@media (max-width: 960px) {
  .debug-compare-preview {
    grid-template-columns: 1fr;
  }
}
</style>
