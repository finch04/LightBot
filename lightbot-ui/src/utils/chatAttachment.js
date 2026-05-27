const IMAGE_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
const VIDEO_MIMES = new Set(['video/mp4', 'video/webm', 'video/quicktime'])
const IMAGE_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp', '.gif'])
const VIDEO_EXT = new Set(['.mp4', '.webm', '.mov'])

const EXT_TO_MIME = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.mov': 'video/quicktime',
}

export function getFileExtension(filename) {
  if (!filename) return ''
  const dot = filename.lastIndexOf('.')
  if (dot < 0 || dot >= filename.length - 1) return ''
  return filename.slice(dot).toLowerCase()
}

/** 根据 Agent 能力生成上传说明（大小文案由后端 maxImageSizeLabel / maxVideoSizeLabel 提供） */
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
  const mimes = capabilities?.allowedFileMimeTypes || []
  if (!mimes.length) return ''
  const parts = []
  const hasImage = mimes.some(m => IMAGE_MIMES.has(m))
  const hasVideo = mimes.some(m => VIDEO_MIMES.has(m))
  if (hasImage && capabilities?.maxImageSizeLabel) {
    parts.push(`图片 JPG/PNG/WebP/GIF（≤${capabilities.maxImageSizeLabel}）`)
  }
  if (hasVideo && capabilities?.maxVideoSizeLabel) {
    parts.push(`视频 MP4/WebM/MOV（≤${capabilities.maxVideoSizeLabel}）`)
  }
  const maxCount = getMaxAttachmentsPerMessage(capabilities)
  if (maxCount > 0) {
    parts.push(`最多 ${maxCount} 个/条`)
  }
  return parts.join('\n')
}

/**
 * 上传前校验格式与大小（大小上限仅使用后端下发的 maxImageBytes / maxVideoBytes）
 * @returns {{ ok: boolean, message?: string }}
 */
export function validateChatAttachmentFile(file, capabilities) {
  if (!file) {
    return { ok: false, message: '请选择文件' }
  }
  if (!file.size) {
    return { ok: false, message: '文件不能为空' }
  }

  const allowedMimes = capabilities?.allowedFileMimeTypes || []
  if (!allowedMimes.length) {
    return { ok: false, message: '当前 Agent 未开启文件上传' }
  }

  const ext = getFileExtension(file.name)
  let mime = (file.type || '').toLowerCase()
  if (!IMAGE_MIMES.has(mime) && !VIDEO_MIMES.has(mime)) {
    const fromExt = EXT_TO_MIME[ext]
    if (fromExt) mime = fromExt
  }

  if (!ext) {
    return { ok: false, message: '文件名需包含扩展名（如 .jpg、.mp4）' }
  }

  let type = null
  if (IMAGE_EXT.has(ext)) {
    if (!IMAGE_MIMES.has(mime)) {
      mime = EXT_TO_MIME[ext]
    }
    if (IMAGE_MIMES.has(mime)) type = 'image'
  } else if (VIDEO_EXT.has(ext)) {
    if (!VIDEO_MIMES.has(mime)) {
      mime = EXT_TO_MIME[ext]
    }
    if (VIDEO_MIMES.has(mime)) type = 'video'
  }

  if (!type) {
    return { ok: false, message: `不支持的文件格式。${buildUploadHint(capabilities) || ''}` }
  }

  if (!allowedMimes.includes(mime)) {
    return { ok: false, message: buildUploadHint(capabilities) || '当前 Agent 不允许该文件类型' }
  }

  const maxImage = capabilities?.maxImageBytes != null ? Number(capabilities.maxImageBytes) : null
  const maxVideo = capabilities?.maxVideoBytes != null ? Number(capabilities.maxVideoBytes) : null
  if (type === 'image') {
    if (maxImage == null || maxImage <= 0) {
      return { ok: false, message: '图片大小限制未配置，请刷新后重试' }
    }
    if (file.size > maxImage) {
      const label = capabilities.maxImageSizeLabel || ''
      return { ok: false, message: label ? `图片不能超过 ${label}` : '图片超过允许大小' }
    }
  }
  if (type === 'video') {
    if (maxVideo == null || maxVideo <= 0) {
      return { ok: false, message: '视频大小限制未配置，请刷新后重试' }
    }
    if (file.size > maxVideo) {
      const label = capabilities.maxVideoSizeLabel || ''
      return { ok: false, message: label ? `视频不能超过 ${label}` : '视频超过允许大小' }
    }
  }

  return { ok: true }
}
