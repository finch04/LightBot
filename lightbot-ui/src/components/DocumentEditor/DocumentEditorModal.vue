<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    :width="previewMode ? 1280 : 960"
    :footer="null"
    centered
    :maskClosable="false"
    :bodyStyle="{ padding: '0', height: '72vh', display: 'flex', flexDirection: 'column' }"
    @cancel="handleClose"
  >
    <!-- 编辑器区域 -->
    <div class="editor-container">
      <div v-if="loading" class="editor-loading">
        <LoadingOutlined spin /> 加载中...
      </div>
      <div v-else-if="!editable" class="editor-unsupported">
        <FileTextOutlined style="font-size: 48px; color: #d9d9d9; margin-bottom: 16px;" />
        <p>该文件类型（{{ fileType?.toUpperCase() }}）暂不支持在线编辑</p>
        <p class="editor-unsupported-hint">目前支持 Markdown、TXT、CSV 格式的在线编辑</p>
      </div>
      <MarkdownEditor
        v-else
        ref="editorRef"
        v-model="content"
        :language="editorLanguage"
        :preview="isMarkdownFile && previewMode"
        class="editor-body"
      />
    </div>

    <!-- 底部操作栏 -->
    <div v-if="editable" class="editor-footer">
      <div class="editor-footer-left">
        <a-tooltip title="历史版本">
          <button class="btn-icon-only" @click="openVersionDrawer">
            <HistoryOutlined />
          </button>
        </a-tooltip>
        <a-tooltip v-if="isMarkdownFile" :key="'preview-' + previewMode" :title="previewMode ? '关闭预览' : '开启预览'">
          <button class="btn-icon-only" @click="previewMode = !previewMode" :class="{ active: previewMode }">
            <EyeOutlined v-if="!previewMode" />
            <EyeInvisibleOutlined v-else />
          </button>
        </a-tooltip>
        <span class="editor-char-count">{{ content.length.toLocaleString() }} 字符</span>
        <a-tag v-if="rebuilding" color="processing">
          <LoadingOutlined spin /> 重建中...
        </a-tag>
        <a-tag v-else-if="saved" color="success">已保存</a-tag>
      </div>
      <div class="editor-footer-right">
        <button class="btn-outline-sm" @click="handleClose">
          {{ hasChanges ? '取消' : '关闭' }}
        </button>
        <button
          v-if="hasChanges"
          class="btn-primary-sm"
          :disabled="saving"
          @click="showSaveConfirm = true"
        >
          {{ saving ? '保存中...' : '保存并重建' }}
        </button>
        <button v-else class="btn-primary-sm" disabled>
          保存并重建
        </button>
      </div>
    </div>

    <!-- 保存确认弹窗 -->
    <a-modal
      v-model:open="showSaveConfirm"
      title="确认保存"
      :width="420"
      centered
      :maskClosable="false"
      :closable="false"
      @ok="handleSave"
      ok-text="确认保存"
      cancel-text="取消"
      :confirm-loading="saving"
    >
      <p style="margin: 0;">保存后将自动重新处理文档（分块+向量化），确定保存？</p>
    </a-modal>
  </a-modal>

  <!-- 版本历史抽屉 -->
  <a-drawer
    :open="showVersionDrawer"
    title="历史版本"
    :width="480"
    @close="showVersionDrawer = false"
  >
    <div v-if="versionLoading" class="version-loading">
      <LoadingOutlined spin /> 加载中...
    </div>
    <div v-else-if="versionList.length === 0" class="version-empty">
      暂无历史版本
    </div>
    <div v-else class="version-list">
      <div v-for="v in versionList" :key="v.id" class="version-item">
        <div class="version-info">
          <span class="version-tag">v{{ v.version }}</span>
          <span class="version-hash">{{ v.contentHash?.substring(0, 8) }}</span>
          <span class="version-time">{{ formatTime(v.createTime) }}</span>
        </div>
        <div class="version-actions">
          <button class="btn-outline-sm" @click="handleViewVersion(v.id)">查看</button>
          <button class="btn-outline-sm btn-rollback" @click="handleRollbackClick(v.id)">回滚</button>
        </div>
      </div>
    </div>
  </a-drawer>

  <!-- 版本内容查看弹窗 -->
  <a-modal
    v-model:open="showVersionContent"
    title="版本内容"
    :width="720"
    centered
    :footer="null"
    :maskClosable="true"
  >
    <div v-if="versionContentLoading" class="version-loading">
      <LoadingOutlined spin /> 加载中...
    </div>
    <pre v-else class="version-content-pre">{{ versionContentText }}</pre>
  </a-modal>

  <!-- 回滚确认弹窗 -->
  <a-modal
    v-model:open="showRollbackConfirm"
    title="确认回滚"
    :width="420"
    centered
    :maskClosable="false"
    :closable="false"
    @ok="handleRollback"
    ok-text="确认回滚"
    cancel-text="取消"
    :confirm-loading="rollbacking"
  >
    <p style="margin: 0;">回滚后当前内容将被覆盖，并自动重新处理文档，确定回滚？</p>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { LoadingOutlined, FileTextOutlined, EyeOutlined, EyeInvisibleOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import { getEditableContent, saveDocumentContent, listVersions, getVersionContent, rollbackVersion } from '../../api/documentEdit'
import MarkdownEditor from './MarkdownEditor.vue'

const props = defineProps({
  open: Boolean,
  documentId: { type: [String, Number], default: null },
})

const emit = defineEmits(['update:open', 'saved'])

const editorRef = ref(null)
const loading = ref(false)
const saving = ref(false)
const rebuilding = ref(false)
const saved = ref(false)
const editable = ref(false)
const fileType = ref('')
const fileName = ref('')
const editMode = ref('')
const originalContent = ref('')
const content = ref('')
const fileHash = ref('')
const previewMode = ref(false)
const showSaveConfirm = ref(false)

// 版本历史
const showVersionDrawer = ref(false)
const versionList = ref([])
const versionLoading = ref(false)
const showVersionContent = ref(false)
const versionContentText = ref('')
const versionContentLoading = ref(false)
const rollbackTarget = ref(null)
const showRollbackConfirm = ref(false)
const rollbacking = ref(false)

const hasChanges = computed(() => content.value !== originalContent.value)

const isMarkdownFile = computed(() => fileType.value?.toLowerCase() === 'md')

const modalTitle = computed(() => {
  if (!fileName.value) return '文档编辑'
  return `编辑 - ${fileName.value}`
})

const editorLanguage = computed(() => {
  const ext = fileType.value?.toLowerCase()
  if (ext === 'md') return 'markdown'
  if (ext === 'csv') return 'plaintext'
  return 'plaintext'
})

watch(() => props.open, (val) => {
  if (val && props.documentId) {
    loadContent()
  } else {
    resetState()
  }
})

async function loadContent() {
  loading.value = true
  saved.value = false
  rebuilding.value = false
  try {
    const res = await getEditableContent(props.documentId)
    const data = res.data || {}
    editable.value = data.editable ?? false
    fileType.value = data.fileType || ''
    fileName.value = data.fileName || ''
    editMode.value = data.editMode || ''
    originalContent.value = data.content || ''
    content.value = data.content || ''
    fileHash.value = data.fileHash || ''
  } catch (e) {
    message.error('加载文档内容失败')
    emit('update:open', false)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!hasChanges.value) return

  saving.value = true
  try {
    const res = await saveDocumentContent(props.documentId, {
      content: content.value,
      editMode: editMode.value,
      expectedHash: fileHash.value,
    })
    const data = res.data || {}
    saved.value = true
    originalContent.value = content.value
    fileHash.value = data.newHash || fileHash.value

    message.success(data.message || '保存成功，正在重新处理文档...')

    // 关闭编辑弹窗，回到文档列表
    showSaveConfirm.value = false
    emit('update:open', false)
    emit('saved')
  } catch (e) {
    const code = e.response?.data?.code
    if (code === 60014) {
      message.error('文档已被其他人修改，请刷新后重试')
    } else {
      message.error(e.response?.data?.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

let rebuildTimer = null

async function openVersionDrawer() {
  showVersionDrawer.value = true
  versionLoading.value = true
  try {
    const res = await listVersions(props.documentId)
    versionList.value = res.data || []
  } catch {
    message.error('加载版本列表失败')
  } finally {
    versionLoading.value = false
  }
}

async function handleViewVersion(versionId) {
  versionContentLoading.value = true
  showVersionContent.value = true
  try {
    const res = await getVersionContent(props.documentId, versionId)
    versionContentText.value = res.data || ''
  } catch {
    message.error('加载版本内容失败')
    showVersionContent.value = false
  } finally {
    versionContentLoading.value = false
  }
}

function handleRollbackClick(versionId) {
  rollbackTarget.value = versionId
  showRollbackConfirm.value = true
}

async function handleRollback() {
  if (!rollbackTarget.value) return
  rollbacking.value = true
  try {
    await rollbackVersion(props.documentId, rollbackTarget.value)
    message.success('回滚成功，正在重新处理文档...')
    showRollbackConfirm.value = false
    showVersionDrawer.value = false
    emit('update:open', false)
    emit('saved')
  } catch (e) {
    message.error(e.response?.data?.message || '回滚失败')
  } finally {
    rollbacking.value = false
  }
}

function formatTime(time) {
  if (!time) return '-'
  const d = new Date(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function handleClose() {
  emit('update:open', false)
}

function resetState() {
  loading.value = false
  saving.value = false
  saved.value = false
  editable.value = false
  fileType.value = ''
  fileName.value = ''
  editMode.value = ''
  originalContent.value = ''
  content.value = ''
  fileHash.value = ''
  previewMode.value = false
  showSaveConfirm.value = false
  showVersionDrawer.value = false
  versionList.value = []
  showVersionContent.value = false
  versionContentText.value = ''
  showRollbackConfirm.value = false
  rollbackTarget.value = null
  if (rebuildTimer) {
    clearInterval(rebuildTimer)
    rebuildTimer = null
  }
}
</script>

<style scoped>
.editor-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.editor-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-mute);
  font-size: 14px;
  gap: 8px;
}
.editor-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-mute);
  font-size: 14px;
  text-align: center;
}
.editor-unsupported p {
  margin: 4px 0;
}
.editor-unsupported-hint {
  font-size: 12px;
  color: var(--color-mute);
}
.editor-body {
  flex: 1;
  min-height: 0;
}
.editor-char-count {
  font-size: 12px;
  color: var(--color-mute);
}
.btn-icon-only {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: none;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-icon-only:hover {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
}
.btn-icon-only.active {
  background: var(--color-purple-bg);
  border-color: #b4b4ff;
  color: #5b5bf0;
}
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
}
.editor-footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.editor-footer-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-outline-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-sm:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.btn-primary-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary-sm:hover:not(:disabled) {
  background: #27272a;
}
.btn-primary-sm:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.version-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--color-mute);
  font-size: 14px;
  gap: 8px;
}
.version-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--color-mute);
  font-size: 14px;
}
.version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
}
.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-tag {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-ink);
}
.version-hash {
  font-size: 12px;
  color: var(--color-mute);
  font-family: monospace;
}
.version-time {
  font-size: 12px;
  color: var(--color-mute);
}
.version-actions {
  display: flex;
  gap: 6px;
}
.btn-rollback {
  color: #ef4444;
  border-color: #fca5a5;
}
.btn-rollback:hover {
  color: #dc2626;
  border-color: #f87171;
}
.version-content-pre {
  max-height: 50vh;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--color-canvas-soft);
  padding: 12px;
  border-radius: 6px;
  border: 1px solid var(--color-hairline);
  margin: 0;
}
</style>
