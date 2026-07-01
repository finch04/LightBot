<template>
  <div class="session-file-preview">
    <div v-if="!file" class="sfp-empty">
      <EyeOutlined class="sfp-empty-icon" />
      <div class="sfp-empty-title">选择文件后可在此预览</div>
      <div class="sfp-empty-desc">也可以从左侧文件树点击文件预览或下载</div>
    </div>
    <div v-else-if="loading" class="sfp-loading">
      <LoadingOutlined spin /> 加载中...
    </div>
    <div v-else class="sfp-content">
      <div class="sfp-header">
        <FileTextOutlined class="sfp-header-icon" />
        <span class="sfp-header-name" :title="file.name">{{ file.name }}</span>
        <a :href="downloadUrl" target="_blank" class="sfp-download" v-if="downloadUrl">
          <DownloadOutlined /> 下载
        </a>
      </div>
      <div class="sfp-body">
        <img v-if="previewType === 'image' && contentUrl" :src="contentUrl" :alt="file.name" class="sfp-img" />
        <iframe v-else-if="previewType === 'pdf' && contentUrl" :src="contentUrl" class="sfp-pdf" title="pdf-preview"></iframe>
        <MarkdownPreview v-else-if="previewType === 'markdown' && content" :content="content" :image-preview="false" />
        <pre v-else-if="previewType === 'text' && content" class="sfp-text">{{ content }}</pre>
        <div v-else-if="previewType === 'download'" class="sfp-fallback">
          <p>该文件类型不支持在线预览</p>
          <a :href="contentUrl" target="_blank" class="sfp-download"><DownloadOutlined /> 下载查看</a>
        </div>
        <div v-else class="sfp-fallback">
          <p>{{ message || '暂不支持预览' }}</p>
          <a v-if="contentUrl" :href="contentUrl" target="_blank" class="sfp-download"><DownloadOutlined /> 下载</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { LoadingOutlined, EyeOutlined, FileTextOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { getSessionFileContent, getSessionFileDownloadUrl } from '../api/chatSession'
import MarkdownPreview from './MarkdownPreview.vue'

const props = defineProps({
  sessionId: { type: [String, Number], default: '' },
  file: { type: Object, default: null },
})

const loading = ref(false)
const previewType = ref('')
const content = ref('')
const contentUrl = ref('')
const downloadUrl = ref('')
const message = ref('')

watch(() => props.file, (f) => {
  if (f) load(f)
  else reset()
}, { immediate: true })

async function load(file) {
  loading.value = true
  reset()
  try {
    const res = await getSessionFileContent(props.sessionId, file.path)
    const data = res.data || {}
    previewType.value = data.previewType || ''
    content.value = data.content || ''
    message.value = data.message || ''
    if (data.previewUrl) contentUrl.value = data.previewUrl
    // 单独获取下载 URL（attachment disposition）
    try {
      const dl = await getSessionFileDownloadUrl(props.sessionId, file.path)
      downloadUrl.value = dl.data || ''
    } catch { downloadUrl.value = '' }
  } catch (e) {
    previewType.value = 'unsupported'
    message.value = '加载文件失败'
  } finally {
    loading.value = false
  }
}

function reset() {
  content.value = ''
  contentUrl.value = ''
  downloadUrl.value = ''
  previewType.value = ''
  message.value = ''
}
</script>

<style scoped>
.session-file-preview { height: 100%; display: flex; flex-direction: column; min-height: 0; }
.sfp-empty, .sfp-loading {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  text-align: center; color: var(--color-mute); padding: 24px;
}
.sfp-empty-icon { font-size: 36px; color: var(--color-hairline-strong); margin-bottom: 12px; }
.sfp-empty-title { font-size: 14px; color: var(--color-body); margin-bottom: 4px; }
.sfp-empty-desc { font-size: 12px; color: var(--color-mute); }

.sfp-content { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.sfp-header {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px;
  border-bottom: 1px solid var(--color-hairline); background: var(--color-canvas);
}
.sfp-header-icon { color: var(--color-link); font-size: 16px; }
.sfp-header-name { flex: 1; font-weight: 600; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sfp-download {
  display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--color-link);
  padding: 4px 10px; border: 1px solid var(--color-hairline); border-radius: 6px; text-decoration: none;
}
.sfp-download:hover { background: var(--color-canvas-soft-2); }

.sfp-body { flex: 1; overflow: auto; padding: 16px; min-height: 0; }
.sfp-img { max-width: 100%; max-height: 100%; display: block; margin: 0 auto; object-fit: contain; }
.sfp-pdf { width: 100%; height: 100%; min-height: 480px; border: none; }
.sfp-text {
  font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px; white-space: pre-wrap;
  word-break: break-word; color: var(--color-body); margin: 0; background: var(--color-canvas-soft);
  padding: 12px; border-radius: 6px;
}
.sfp-fallback { text-align: center; color: var(--color-mute); padding: 32px; }
.sfp-fallback p { margin: 0 0 12px; font-size: 13px; }
</style>
