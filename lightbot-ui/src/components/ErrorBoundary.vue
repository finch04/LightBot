<template>
  <slot v-if="!error" />
  <div v-else class="error-boundary">
    <div class="error-card">
      <div class="error-icon">!</div>
      <h3 class="error-title">页面出现异常</h3>
      <p class="error-desc">{{ friendlyMessage }}</p>
      <pre v-if="errorDetail" class="error-detail">{{ errorDetail }}</pre>
      <div class="error-actions">
        <button class="btn-primary" @click="handleRetry">重试</button>
        <button class="btn-secondary" @click="handleReload">刷新页面</button>
        <button class="btn-secondary" @click="copyErrorInfo">
          <CheckOutlined v-if="copied" />
          <CopyOutlined v-else />
          {{ copied ? '已复制' : '复制报错' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onErrorCaptured } from 'vue'
import { CheckOutlined, CopyOutlined } from '@ant-design/icons-vue'
import { captureException } from '../utils/errorReport'
import { isHandledRequestError } from '../utils/requestError'

const props = defineProps({
  /** 友好的错误提示文案，覆盖默认 */
  message: { type: String, default: '' },
  /** 是否在控制台展示完整错误（开发态 true） */
  verbose: { type: Boolean, default: true },
})

const emit = defineEmits(['error'])

const error = ref(null)
const capturedContext = ref(null)
const copied = ref(false)
let copiedResetTimer = null

const friendlyMessage = computed(() => props.message || '当前组件渲染失败，可尝试重试或刷新页面')
const errorDetail = computed(() => {
  if (!props.verbose || !error.value) return ''
  const err = error.value
  return `${err.message || ''}\n${err.stack || ''}`.slice(0, 800)
})

onErrorCaptured((err, instance, info) => {
  // 请求层已 toast/跳转的业务错误：吞掉，绝不替换整页为错误页
  // 典型场景：a-modal @ok="asyncSave" 未写 try/catch，await 失败被 Vue 送进 errorCaptured
  if (isHandledRequestError(err)) {
    return false
  }
  error.value = err
  capturedContext.value = {
    source: 'errorCaptured',
    info,
    componentTag: instance?.$options?.__name || instance?.$options?.name,
  }
  // 上报到监控 + 触发上游 hook（如局部错误需要全局通知）
  captureException(err, capturedContext.value)
  emit('error', err, info)
  // 返回 false 阻止错误继续向上冒泡（外层 ErrorBoundary 不会重复捕获）
  return false
})

function handleRetry() {
  error.value = null
  capturedContext.value = null
}

function handleReload() {
  location.reload()
}

// 组装完整报错信息（含时间/URL/组件/堆栈/traceId），用户复制后开发者可直接定位
function buildFullErrorText() {
  const err = error.value
  if (!err) return ''
  const lines = [
    `Time: ${new Date().toISOString()}`,
    `URL: ${location.href}`,
  ]
  const lastTraceId = sessionStorage.getItem('lightbot:traceId')
  if (lastTraceId) {
    lines.push(`TraceId: ${lastTraceId}`)
  }
  if (capturedContext.value?.componentTag) {
    lines.push(`Component: ${capturedContext.value.componentTag}`)
  }
  if (capturedContext.value?.info) {
    lines.push(`Info: ${capturedContext.value.info}`)
  }
  lines.push(`Message: ${err.message || String(err)}`)
  if (err.stack) {
    lines.push(`Stack:\n${err.stack}`)
  }
  return lines.join('\n')
}

async function copyErrorInfo() {
  const text = buildFullErrorText()
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      // 降级方案：用临时 textarea + execCommand 兼容旧浏览器/非 HTTPS
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copied.value = true
    if (copiedResetTimer) clearTimeout(copiedResetTimer)
    copiedResetTimer = setTimeout(() => {
      copied.value = false
      copiedResetTimer = null
    }, 2000)
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary] 复制失败:', e)
  }
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 32px;
}

.error-card {
  max-width: 540px;
  padding: 32px 28px;
  background: var(--color-bg-container, #fff);
  border: 1px solid var(--color-hairline, #ebebeb);
  border-radius: var(--radius-lg, 12px);
  text-align: center;
  box-shadow: var(--shadow-2);
}

.error-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: var(--color-error-soft, #f7d4d6);
  color: var(--color-error, #ee0000);
  font-size: 28px;
  font-weight: 700;
  line-height: 48px;
}

.error-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--color-ink, #171717);
}

.error-desc {
  margin: 0 0 16px;
  color: var(--color-body, #4d4d4d);
  font-size: 14px;
}

.error-detail {
  margin: 0 0 16px;
  padding: 12px;
  background: var(--color-canvas-soft-2, #f5f5f5);
  border-radius: var(--radius-sm, 6px);
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--color-body, #4d4d4d);
  text-align: left;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 16px;
  border-radius: var(--radius-pill, 100px);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: var(--color-primary, #171717);
  color: #fff;
}

.btn-primary:hover {
  background: #27272a;
}

.btn-secondary {
  background: transparent;
  color: var(--color-ink, #171717);
  border: 1px solid var(--color-hairline, #ebebeb);
}

.btn-secondary:hover {
  border-color: var(--color-hairline-strong, #a1a1a1);
}
</style>
