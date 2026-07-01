<template>
  <a-modal
    v-model:open="visible"
    :width="900"
    :footer="null"
    centered
    destroy-on-close
    class="session-file-preview-modal"
    :body-style="{ padding: 0, height: '75vh', overflow: 'hidden' }"
    @cancel="handleClose"
  >
    <template #title>
      <div class="sfpm-title">
        <FileTypeIcon :name="displayName" :size="18" />
        <span class="sfpm-title-text" :title="displayName">{{ displayName }}</span>
      </div>
    </template>
    <template #extra>
      <a-tooltip title="下载文件" placement="bottom" :get-popup-container="tooltipPopupContainer">
        <a v-if="downloadUrl" :href="downloadUrl" target="_blank" class="sfpm-download">
          <DownloadOutlined /> 下载
        </a>
      </a-tooltip>
    </template>

    <div v-if="loading" class="sfpm-loading">
      <LoadingOutlined spin /> 加载中...
    </div>
    <div v-else class="sfpm-body">
      <video
        v-if="previewType === 'video' && contentUrl"
        :src="contentUrl"
        controls
        class="sfpm-video"
      />
      <FilePreview
        v-else-if="useFilePreview"
        :file-url="contentUrl"
        :file-name="displayName"
        :file-type="fileExtension"
        :content="content"
        :loading="false"
      />
      <div v-else class="sfpm-fallback">
        <p>{{ message || '该文件类型不支持在线预览' }}</p>
        <a-tooltip v-if="downloadUrl || contentUrl" title="下载查看" placement="top" :get-popup-container="tooltipPopupContainer">
          <a :href="downloadUrl || contentUrl" target="_blank" class="sfpm-download-btn">
            <DownloadOutlined /> 下载查看
          </a>
        </a-tooltip>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { LoadingOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { getSessionFileContent, getSessionFileDownloadUrl } from '../api/chatSession'
import FileTypeIcon from './FileTypeIcon.vue'
import FilePreview from './FilePreview.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  sessionId: { type: [String, Number], default: '' },
  file: { type: Object, default: null },
})

const emit = defineEmits(['update:open'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

function tooltipPopupContainer() {
  return document.body
}

const loading = ref(false)
const previewType = ref('')
const content = ref('')
const contentUrl = ref('')
const downloadUrl = ref('')
const message = ref('')

const displayName = computed(() => props.file?.fileName || props.file?.name || '文件预览')

const fileExtension = computed(() => {
  const name = displayName.value
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
})

const useFilePreview = computed(() => {
  const t = previewType.value
  if (['html', 'htm'].includes(fileExtension.value) && contentUrl.value) return true
  return t === 'image' || t === 'pdf' || t === 'markdown' || t === 'text' || t === 'download'
})

watch(() => [props.open, props.file], ([open, file]) => {
  if (open && file) load(file)
  else reset()
}, { immediate: true })

async function load(file) {
  loading.value = true
  resetContent()
  try {
    const res = await getSessionFileContent(props.sessionId, file.path)
    const data = res.data || {}
    previewType.value = data.previewType || ''
    content.value = data.content || ''
    message.value = data.message || ''
    if (data.previewUrl) contentUrl.value = data.previewUrl
    if (data.mimeType?.startsWith('video/')) previewType.value = 'video'
    try {
      const dl = await getSessionFileDownloadUrl(props.sessionId, file.path)
      downloadUrl.value = dl.data || ''
    } catch {
      downloadUrl.value = contentUrl.value
    }
  } catch {
    previewType.value = 'unsupported'
    message.value = '加载文件失败'
  } finally {
    loading.value = false
  }
}

function resetContent() {
  content.value = ''
  contentUrl.value = ''
  downloadUrl.value = ''
  previewType.value = ''
  message.value = ''
}

function reset() {
  resetContent()
  loading.value = false
}

function handleClose() {
  visible.value = false
}
</script>

<style scoped>
.sfpm-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.sfpm-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.sfpm-download {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-link);
  text-decoration: none;
}
.sfpm-loading {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  gap: 8px;
}
.sfpm-body {
  height: 100%;
  overflow: auto;
}
.sfpm-body :deep(.file-preview) {
  min-height: 100%;
  height: 100%;
}
.sfpm-video {
  width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  background: #000;
}
.sfpm-fallback {
  text-align: center;
  color: var(--color-mute);
  padding: 48px 24px;
}
.sfpm-fallback p { margin: 0 0 16px; }
.sfpm-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-link);
}
</style>
