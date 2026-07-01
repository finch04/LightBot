<template>
  <a-modal
    v-model:open="visible"
    :width="900"
    :footer="null"
    centered
    destroy-on-close
    class="file-preview-modal"
    :body-style="{ padding: 0, height: '75vh', overflow: 'hidden' }"
    @cancel="handleClose"
  >
    <template #title>
      <div class="fpm-header">
        <div class="fpm-title-main">
          <FileTypeIcon :name="displayName" :size="18" />
          <span class="fpm-title-text" :title="displayName">{{ displayName }}</span>
        </div>
        <a-tooltip
          v-if="effectiveDownloadUrl"
          title="下载文件"
          placement="bottom"
          :get-popup-container="tooltipPopupContainer"
        >
          <a
            :href="effectiveDownloadUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="fpm-download"
            @click.stop
          >
            <DownloadOutlined /> 下载
          </a>
        </a-tooltip>
      </div>
    </template>

    <div class="fpm-body">
      <div v-if="combinedLoading" class="fpm-loading">
        <LoadingOutlined spin /> 加载中...
      </div>
      <video
        v-else-if="isVideo && fileUrl"
        :src="fileUrl"
        controls
        class="fpm-video"
      />
      <FilePreview
        v-else
        :file-url="fileUrl"
        :file-name="displayName"
        :file-type="fileTypeExt"
        :content="content"
        :loading="false"
        :download-url="effectiveDownloadUrl"
      />
    </div>
  </a-modal>
</template>

<script setup>
import { computed } from 'vue'
import { DownloadOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import FileTypeIcon from './FileTypeIcon.vue'
import FilePreview from './FilePreview.vue'
import { getFileExtension } from '../utils/filePreview'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 展示用文件名 */
  fileName: { type: String, default: '文件预览' },
  /** 预览 URL（PDF/图片/Office 等） */
  fileUrl: { type: String, default: '' },
  /** 下载 URL，默认同 fileUrl */
  downloadUrl: { type: String, default: '' },
  /** 文本/Markdown 等内容（后端已读取时传入） */
  content: { type: String, default: '' },
  /** 外部加载态 */
  loading: { type: Boolean, default: false },
  /** 是否为视频预览 */
  isVideo: { type: Boolean, default: false },
  /** 扩展名（可选，不传则从 fileName 解析） */
  fileType: { type: String, default: '' },
})

const emit = defineEmits(['update:open'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const displayName = computed(() => props.fileName || '文件预览')

const fileTypeExt = computed(() => {
  if (props.fileType) return props.fileType.toLowerCase()
  return getFileExtension(displayName.value)
})

const effectiveDownloadUrl = computed(() => props.downloadUrl || props.fileUrl || '')

const combinedLoading = computed(() => props.loading)

function tooltipPopupContainer() {
  return document.body
}

function handleClose() {
  visible.value = false
}
</script>

<style scoped>
.fpm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-width: 0;
  padding-right: 28px;
}
.fpm-title-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.fpm-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.fpm-download {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-link);
  text-decoration: none;
  padding: 4px 10px;
  border-radius: 6px;
  transition: background 0.15s;
}
.fpm-download:hover {
  background: var(--color-canvas-soft);
  color: var(--color-link);
}
.fpm-body {
  height: 100%;
  overflow: auto;
}
.fpm-body :deep(.file-preview) {
  min-height: 100%;
  height: 100%;
}
.fpm-loading {
  height: 100%;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  gap: 8px;
}
.fpm-video {
  width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  background: #000;
}
</style>

<style>
/* 标题栏与下载按钮同排展示（避免 #extra 插槽无效） */
.file-preview-modal .ant-modal-header {
  padding: 12px 16px;
}
.file-preview-modal .ant-modal-title {
  width: 100%;
}
</style>
