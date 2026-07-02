import { ref, onUnmounted } from 'vue'

/**
 * 右侧详情侧栏宽度拖拽
 * @param {{ storageKey?: string, defaultWidth?: number, minWidth?: number, maxWidth?: number, maxViewportRatio?: number }} options
 */
export function useResizableSidePanel(options = {}) {
  const {
    storageKey,
    defaultWidth = 480,
    minWidth = 320,
    maxWidth = 720,
    maxViewportRatio = 0.55,
  } = options

  function clampWidth(value) {
    const viewportCap = Math.floor(window.innerWidth * maxViewportRatio)
    const upper = Math.min(maxWidth, viewportCap)
    return Math.min(upper, Math.max(minWidth, value))
  }

  function readStoredWidth() {
    if (!storageKey) return defaultWidth
    try {
      const raw = localStorage.getItem(storageKey)
      if (raw != null) {
        const parsed = Number.parseInt(raw, 10)
        if (!Number.isNaN(parsed)) return clampWidth(parsed)
      }
    } catch {
      /* ignore */
    }
    return clampWidth(defaultWidth)
  }

  const width = ref(readStoredWidth())
  const isResizing = ref(false)

  let startX = 0
  let startWidth = 0

  function persistWidth() {
    if (!storageKey) return
    try {
      localStorage.setItem(storageKey, String(width.value))
    } catch {
      /* ignore */
    }
  }

  function onResizeMove(e) {
    const delta = startX - e.clientX
    width.value = clampWidth(startWidth + delta)
  }

  function onResizeEnd() {
    isResizing.value = false
    document.removeEventListener('mousemove', onResizeMove)
    document.removeEventListener('mouseup', onResizeEnd)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    persistWidth()
  }

  function onResizeStart(e) {
    if (e.button !== 0) return
    e.preventDefault()
    isResizing.value = true
    startX = e.clientX
    startWidth = width.value
    document.addEventListener('mousemove', onResizeMove)
    document.addEventListener('mouseup', onResizeEnd)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  function resetWidth() {
    width.value = clampWidth(defaultWidth)
    persistWidth()
  }

  onUnmounted(() => {
    document.removeEventListener('mousemove', onResizeMove)
    document.removeEventListener('mouseup', onResizeEnd)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  })

  return {
    width,
    isResizing,
    minWidth,
    maxWidth,
    onResizeStart,
    resetWidth,
  }
}
