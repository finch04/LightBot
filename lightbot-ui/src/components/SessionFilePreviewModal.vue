<template>
  <FilePreviewModal
    v-model:open="visible"
    :file-name="displayName"
    :file-url="fileUrl"
    :download-url="downloadUrl"
    :content="content"
    :loading="loading"
    :is-video="isVideo"
    :file-type="fileExtension"
  />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getSessionFileContent, getSessionFileDownloadUrl } from '../api/chatSession'
import FilePreviewModal from './FilePreviewModal.vue'
import { getFileExtension } from '../utils/filePreview'

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

const loading = ref(false)
const fileUrl = ref('')
const downloadUrl = ref('')
const content = ref('')
const isVideo = ref(false)

const displayName = computed(() => props.file?.fileName || props.file?.name || '文件预览')

const fileExtension = computed(() => {
  const fromName = getFileExtension(displayName.value)
  if (fromName) return fromName
  const path = props.file?.path || ''
  return getFileExtension(path.split('/').pop() || '')
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
    content.value = data.content || ''
    if (data.previewUrl) {
      fileUrl.value = data.previewUrl
    }
    isVideo.value = data.previewType === 'video' || (data.mimeType || '').startsWith('video/')
    try {
      const dl = await getSessionFileDownloadUrl(props.sessionId, file.path)
      downloadUrl.value = dl.data || fileUrl.value
    } catch {
      downloadUrl.value = fileUrl.value
    }
    // 二进制类文件：previewUrl 即预览地址
    if (!fileUrl.value && downloadUrl.value) {
      fileUrl.value = downloadUrl.value
    }
  } catch {
    fileUrl.value = ''
    content.value = ''
  } finally {
    loading.value = false
  }
}

function resetContent() {
  fileUrl.value = ''
  downloadUrl.value = ''
  content.value = ''
  isVideo.value = false
}

function reset() {
  resetContent()
  loading.value = false
}
</script>
