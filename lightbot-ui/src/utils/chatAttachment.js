const IMAGE_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
const VIDEO_MIMES = new Set(['video/mp4', 'video/webm', 'video/quicktime'])
const IMAGE_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp', '.gif'])
const VIDEO_EXT = new Set(['.mp4', '.webm', '.mov'])
const DOCUMENT_EXT = new Set([
  '.md', '.txt', '.pdf', '.doc', '.docx', '.ppt', '.pptx',
  '.xls', '.xlsx', '.csv', '.html', '.htm',
])

const EXT_TO_MIME = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.mov': 'video/quicktime',
  '.md': 'text/markdown',
  '.txt': 'text/plain',
  '.pdf': 'application/pdf',
  '.doc': 'application/msword',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  '.ppt': 'application/vnd.ms-powerpoint',
  '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  '.xls': 'application/vnd.ms-excel',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  '.csv': 'text/csv',
  '.html': 'text/html',
  '.htm': 'text/html',
}

export function getFileExtension(filename) {
  if (!filename) return ''
  const dot = filename.lastIndexOf('.')
  if (dot < 0 || dot >= filename.length - 1) return ''
  return filename.slice(dot).toLowerCase()
}

/** 单条消息默认最大附件数（与后端 ChatAttachmentConstants 一致） */
export const DEFAULT_MAX_ATTACHMENTS_PER_MESSAGE = 3

export function getMaxAttachmentsPerMessage(capabilities) {
  const n = capabilities?.maxAttachmentsPerMessage
  if (n != null && n > 0) return Number(n)
  return DEFAULT_MAX_ATTACHMENTS_PER_MESSAGE
}

/**
 * 校验待发送附件数量
 * @returns {{ ok: boolean, message?: string }}
 */
export function validateAttachmentCount(currentCount, capabilities) {
  const max = getMaxAttachmentsPerMessage(capabilities)
  if (currentCount >= max) {
    return { ok: false, message: `单条消息最多上传 ${max} 个附件` }
  }
  return { ok: true }
}

export function buildUploadHint(capabilities) {
  const parts = []
  const allowMedia = Boolean(capabilities?.allowMediaUpload)
  const mimes = allowMedia ? (capabilities?.allowedFileMimeTypes || []) : []
  const hasImage = mimes.some(m => IMAGE_MIMES.has(m))
  const hasVideo = mimes.some(m => VIDEO_MIMES.has(m))
  const docExts = (capabilities?.allowDocumentUpload ?? capabilities?.enableFileRead)
      ? (capabilities?.allowedDocumentExtensions || [])
      : []
  if (hasImage && capabilities?.maxImageSizeLabel) {
    parts.push(`图片 JPG/PNG/WebP/GIF（≤${capabilities.maxImageSizeLabel}）`)
  }
  if (hasVideo && capabilities?.maxVideoSizeLabel) {
    parts.push(`视频 MP4/WebM/MOV（≤${capabilities.maxVideoSizeLabel}）`)
  }
  if (docExts.length && capabilities?.maxDocumentSizeLabel) {
    parts.push(`文档 MD/TXT/PDF/Office/CSV/HTML（≤${capabilities.maxDocumentSizeLabel}）`)
  }
  const maxCount = getMaxAttachmentsPerMessage(capabilities)
  if (maxCount > 0) {
    parts.push(`最多 ${maxCount} 个/条`)
  }
  if (hasImage && hasVideo) {
    parts.push('同一条消息不可同时包含图片与视频，可与文档搭配')
  }
  return parts.join('\n')
}

/**
 * 同一条消息禁止图片与视频混传（可与文档混传）
 * @param {Array<{type?: string}>} existingAttachments 已选附件
 * @param {'image'|'video'|'document'} incomingType 待上传类型
 * @returns {{ ok: boolean, message?: string }}
 */
export function validateAttachmentMix(existingAttachments, incomingType) {
  if (!incomingType || incomingType === 'document') {
    return { ok: true }
  }
  const list = existingAttachments || []
  const hasImage = list.some(a => a?.type === 'image')
  const hasVideo = list.some(a => a?.type === 'video')
  if (incomingType === 'image' && hasVideo) {
    return { ok: false, message: '同一条消息不能同时上传图片和视频，可与文档附件搭配使用' }
  }
  if (incomingType === 'video' && hasImage) {
    return { ok: false, message: '同一条消息不能同时上传图片和视频，可与文档附件搭配使用' }
  }
  return { ok: true }
}

/** 发送前校验已选附件是否混传图片与视频 */
export function validatePendingAttachmentMix(attachments) {
  const list = attachments || []
  const hasImage = list.some(a => a?.type === 'image')
  const hasVideo = list.some(a => a?.type === 'video')
  if (hasImage && hasVideo) {
    return { ok: false, message: '同一条消息不能同时上传图片和视频，请移除其中一种后再发送' }
  }
  return { ok: true }
}

/** 生成 file input 的 accept 属性（MIME + 扩展名） */
export function buildFileAcceptTypes(capabilities) {
  const mimes = capabilities?.allowMediaUpload ? (capabilities?.allowedFileMimeTypes || []) : []
  const exts = (capabilities?.allowDocumentUpload ?? capabilities?.enableFileRead)
      ? (capabilities?.allowedDocumentExtensions || [])
      : []
  return [...mimes, ...exts].join(',')
}

/**
 * 上传前校验格式与大小
 * @returns {{ ok: boolean, message?: string, type?: string }}
 */
export function validateChatAttachmentFile(file, capabilities) {
  if (!file) {
    return { ok: false, message: '请选择文件' }
  }
  if (!file.size) {
    return { ok: false, message: '文件不能为空' }
  }

  const allowedMimes = capabilities?.allowedFileMimeTypes || []
  const allowedDocExt = capabilities?.allowedDocumentExtensions || []
  const allowMedia = Boolean(capabilities?.allowMediaUpload) && allowedMimes.length > 0
  const allowDoc = Boolean(capabilities?.allowDocumentUpload ?? capabilities?.enableFileRead)
      && allowedDocExt.length > 0
  if (!allowMedia && !allowDoc) {
    return { ok: false, message: '当前 Agent 未开启附件上传' }
  }

  const ext = getFileExtension(file.name)
  if (!ext) {
    return { ok: false, message: '文件名需包含扩展名（如 .pdf、.jpg）' }
  }

  let mime = (file.type || '').toLowerCase()
  const fromExt = EXT_TO_MIME[ext]
  if (fromExt) mime = fromExt

  let type = null
  if (IMAGE_EXT.has(ext) && IMAGE_MIMES.has(mime)) {
    type = 'image'
  } else if (VIDEO_EXT.has(ext) && VIDEO_MIMES.has(mime)) {
    type = 'video'
  } else if (DOCUMENT_EXT.has(ext)) {
    type = 'document'
  }

  if (!type) {
    return { ok: false, message: `不支持的文件格式。${buildUploadHint(capabilities) || ''}` }
  }

  if (type === 'image') {
    if (!allowMedia || !allowedMimes.includes(mime)) {
      return { ok: false, message: '当前 Agent 未开启图像输入' }
    }
    const maxImage = capabilities?.maxImageBytes != null ? Number(capabilities.maxImageBytes) : null
    if (maxImage == null || maxImage <= 0) {
      return { ok: false, message: '图片大小限制未配置，请刷新后重试' }
    }
    if (file.size > maxImage) {
      const label = capabilities.maxImageSizeLabel || ''
      return { ok: false, message: label ? `图片不能超过 ${label}` : '图片超过允许大小' }
    }
  } else if (type === 'video') {
    if (!allowMedia || !allowedMimes.includes(mime)) {
      return { ok: false, message: '当前 Agent 未开启视频输入' }
    }
    const maxVideo = capabilities?.maxVideoBytes != null ? Number(capabilities.maxVideoBytes) : null
    if (maxVideo == null || maxVideo <= 0) {
      return { ok: false, message: '视频大小限制未配置，请刷新后重试' }
    }
    if (file.size > maxVideo) {
      const label = capabilities.maxVideoSizeLabel || ''
      return { ok: false, message: label ? `视频不能超过 ${label}` : '视频超过允许大小' }
    }
  } else if (type === 'document') {
    if (!allowDoc || !allowedDocExt.includes(ext)) {
      return { ok: false, message: '当前 Agent 未开启文件读取' }
    }
    const maxDoc = capabilities?.maxDocumentBytes != null ? Number(capabilities.maxDocumentBytes) : null
    if (maxDoc == null || maxDoc <= 0) {
      return { ok: false, message: '文档大小限制未配置，请刷新后重试' }
    }
    if (file.size > maxDoc) {
      const label = capabilities.maxDocumentSizeLabel || '5MB'
      return { ok: false, message: `文档不能超过 ${label}` }
    }
  }

  return { ok: true, type }
}
