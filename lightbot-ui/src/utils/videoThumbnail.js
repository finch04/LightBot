/**
 * 从视频 File 或 URL 截取首帧，生成 JPEG data URL 缩略图
 * @param {File|string} source
 * @param {{ seekTime?: number, maxWidth?: number, maxHeight?: number }} options
 * @returns {Promise<string>}
 */
export function captureVideoThumbnail(source, options = {}) {
  const { seekTime = 0.1, maxWidth = 120, maxHeight = 80 } = options
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.muted = true
    video.playsInline = true
    video.preload = 'auto'
    let objectUrl = null

    const cleanup = () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
      video.removeAttribute('src')
      video.load()
    }

    const fail = (err) => {
      cleanup()
      reject(err || new Error('无法生成视频缩略图'))
    }

    video.onerror = () => fail(new Error('视频加载失败'))

    const drawFrame = () => {
      const w = video.videoWidth
      const h = video.videoHeight
      if (!w || !h) {
        fail(new Error('视频尺寸无效'))
        return
      }
      const canvas = document.createElement('canvas')
      const scale = Math.min(maxWidth / w, maxHeight / h, 1)
      canvas.width = Math.max(1, Math.round(w * scale))
      canvas.height = Math.max(1, Math.round(h * scale))
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        fail(new Error('Canvas 不可用'))
        return
      }
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      try {
        resolve(canvas.toDataURL('image/jpeg', 0.82))
      } catch (e) {
        fail(e)
      } finally {
        cleanup()
      }
    }

    video.onloadeddata = () => {
      const duration = video.duration
      const t = Number.isFinite(duration) && duration > 0
        ? Math.min(seekTime, Math.max(0, duration - 0.05))
        : seekTime
      video.onseeked = drawFrame
      try {
        video.currentTime = t
      } catch {
        drawFrame()
      }
    }

    if (source instanceof File) {
      objectUrl = URL.createObjectURL(source)
      video.src = objectUrl
    } else if (typeof source === 'string' && source) {
      video.crossOrigin = 'anonymous'
      video.src = source
    } else {
      reject(new Error('不支持的视频源'))
      return
    }
    video.load()
  })
}

/** 为附件列表中的视频补充 thumbnailUrl（就地修改） */
export async function enrichVideoThumbnails(attachments) {
  if (!attachments?.length) return
  await Promise.all(
    attachments.map(async (att) => {
      if (att?.type !== 'video' || att.thumbnailUrl) return
      const src = att._localFile || att.previewUrl
      if (!src) return
      try {
        att.thumbnailUrl = await captureVideoThumbnail(src, { maxWidth: 112, maxHeight: 72 })
      } catch {
        // 跨域或格式不支持时保留无缩略图
      }
    })
  )
}
