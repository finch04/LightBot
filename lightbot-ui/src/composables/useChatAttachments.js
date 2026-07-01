import { message } from 'ant-design-vue'
import { uploadChatAttachment } from '../api/chat'
import {
  validateChatAttachmentFile,
  validateAttachmentCount,
  validateAttachmentMix,
} from '../utils/chatAttachment'
import { captureVideoThumbnail } from '../utils/videoThumbnail'

/**
 * Chat 页面附件管理 composable
 * 管理文件上传、附件预览
 *
 * 注意：pendingAttachments, fileInputRef, uploading, attachmentPreviewOpen,
 *       attachmentPreviewAtt 由外部创建并传入，composable 只操作这些 ref
 */
export function useChatAttachments({
  selectedAgentId, sessionId, chatCapabilities,
  pendingAttachments, fileInputRef, uploading,
  attachmentPreviewOpen, attachmentPreviewAtt,
}) {
  function getAttThumbUrl(att) {
    if (!att) return ''
    if (att.type === 'image') return att.thumbnailUrl || att.previewUrl || ''
    if (att.type === 'video') return att.thumbnailUrl || ''
    return ''
  }

  function openAttachmentPreview(att) {
    if (!att) return
    if (att.type === 'image' || att.type === 'video') {
      if (!getAttThumbUrl(att) && !att.previewUrl) return
    } else if (att.type === 'document') {
      if (!att.previewUrl && !att.objectKey) {
        message.warning('暂无可预览内容')
        return
      }
    } else {
      return
    }
    attachmentPreviewAtt.value = att
    attachmentPreviewOpen.value = true
  }

  function triggerFileUpload() {
    fileInputRef.value?.click()
  }

  async function onFileSelected(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !selectedAgentId.value) return

    const countCheck = validateAttachmentCount(pendingAttachments.value.length, chatCapabilities.value)
    if (!countCheck.ok) {
      message.warning(countCheck.message)
      return
    }
    const validation = validateChatAttachmentFile(file, chatCapabilities.value)
    if (!validation.ok) {
      message.warning(validation.message)
      return
    }
    const mixCheck = validateAttachmentMix(pendingAttachments.value, validation.type)
    if (!mixCheck.ok) {
      message.warning(mixCheck.message)
      return
    }

    uploading.value = true
    try {
      const res = await uploadChatAttachment(selectedAgentId.value, sessionId.value, file)
      const att = { ...res.data }
      if (att.type === 'video') {
        try {
          att.thumbnailUrl = await captureVideoThumbnail(file, { maxWidth: 112, maxHeight: 72 })
        } catch {
          if (att.previewUrl) {
            try {
              att.thumbnailUrl = await captureVideoThumbnail(att.previewUrl, { maxWidth: 112, maxHeight: 72 })
            } catch { /* 跨域等 */ }
          }
        }
      }
      pendingAttachments.value.push(att)
    } catch {
      // 业务/网络错误提示由 request 拦截器统一展示，避免重复 toast
    } finally {
      uploading.value = false
    }
  }

  function removeAttachment(index) {
    pendingAttachments.value.splice(index, 1)
  }

  return {
    getAttThumbUrl,
    openAttachmentPreview,
    triggerFileUpload,
    onFileSelected,
    removeAttachment,
  }
}
