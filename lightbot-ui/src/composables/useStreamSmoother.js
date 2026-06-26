import { onUnmounted } from 'vue'

/**
 * 流式输出平滑缓冲
 *
 * 将不均匀的 token chunk 缓冲后按平滑速率 flush，消除界面闪烁。
 * 自适应策略：缓冲区越大 drain 越快，保证跟上 LLM 输出速度。
 *
 * @param {Object} options
 * @param {function} options.onFlush - flush 回调：(text: string) => void
 */
export function useStreamSmoother(options = {}) {
  const { onFlush } = options

  let buffer = ''
  let rafId = null
  let lastDrainTime = 0
  let active = false

  /** 每帧 drain 字符数：min + 缓冲区比例，保证缓冲区越大输出越快 */
  function charsPerFrame() {
    return 2 + Math.floor(buffer.length / 3)
  }

  function push(chunk) {
    if (!chunk) return
    buffer += chunk
    if (active && !rafId) {
      rafId = requestAnimationFrame(drain)
    }
  }

  function drain() {
    rafId = null
    if (!buffer || !active) return

    const now = performance.now()

    if (lastDrainTime === 0) {
      lastDrainTime = now
    }

    const elapsed = now - lastDrainTime
    // 固定 30ms 间隔（~33fps），避免 RAF 频率波动导致输出不均
    if (elapsed < 30) {
      rafId = requestAnimationFrame(drain)
      return
    }

    const count = Math.min(charsPerFrame(), buffer.length)
    const text = buffer.substring(0, count)
    buffer = buffer.substring(count)
    lastDrainTime = now
    onFlush?.(text)

    if (buffer) {
      rafId = requestAnimationFrame(drain)
    }
  }

  function flush() {
    if (buffer) {
      const text = buffer
      buffer = ''
      onFlush?.(text)
    }
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
  }

  function start() {
    active = true
    lastDrainTime = 0
    if (buffer && !rafId) {
      rafId = requestAnimationFrame(drain)
    }
  }

  function stop() {
    active = false
    flush()
  }

  function reset() {
    buffer = ''
    active = false
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    lastDrainTime = 0
  }

  onUnmounted(() => {
    if (rafId) cancelAnimationFrame(rafId)
  })

  return { push, flush, start, stop, reset }
}
