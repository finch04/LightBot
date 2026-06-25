<template>
  <div class="code-editor" :class="{ fullscreen: isFullscreen }">
    <div class="code-editor-toolbar">
      <a-select
        :value="language"
        :disabled="disabled"
        size="small"
        style="width: 130px"
        @change="onLanguageChange"
      >
        <a-select-option v-for="lang in SCRIPT_LANGUAGES" :key="lang.value" :value="lang.value">
          {{ lang.label }}
        </a-select-option>
      </a-select>
      <a-button type="text" size="small" :disabled="disabled" @click="toggleFullscreen">
        <FullscreenOutlined v-if="!isFullscreen" />
        <FullscreenExitOutlined v-else />
        {{ isFullscreen ? '退出全屏' : '全屏编辑' }}
      </a-button>
    </div>
    <div
      ref="scrollerRef"
      class="code-editor-body code-block-scroll code-block-scroll--dark"
      :style="{ height: `${viewportHeight}px` }"
    >
      <div ref="layerRef" class="code-editor-layer">
        <pre class="code-editor-highlight" aria-hidden="true"><code v-html="highlightedHtml" /></pre>
        <textarea
          ref="textareaRef"
          class="code-editor-input"
          :value="modelValue"
          :disabled="disabled"
          :placeholder="placeholder"
          spellcheck="false"
          wrap="off"
          @input="onInput"
        />
      </div>
    </div>
    <a-modal
      v-model:open="isFullscreen"
      :title="fullscreenTitle"
      width="92vw"
      :footer="null"
      destroy-on-close
      wrap-class-name="code-editor-fullscreen-modal"
      @cancel="isFullscreen = false"
    >
      <div class="code-editor code-editor--modal">
        <div class="code-editor-toolbar">
          <a-select
            :value="language"
            :disabled="disabled"
            size="small"
            style="width: 130px"
            @change="onLanguageChange"
          >
            <a-select-option v-for="lang in SCRIPT_LANGUAGES" :key="lang.value" :value="lang.value">
              {{ lang.label }}
            </a-select-option>
          </a-select>
        </div>
        <div
          ref="modalScrollerRef"
          class="code-editor-body code-editor-body--modal code-block-scroll code-block-scroll--dark"
        >
          <div ref="modalLayerRef" class="code-editor-layer">
            <pre class="code-editor-highlight" aria-hidden="true"><code v-html="highlightedHtml" /></pre>
            <textarea
              ref="modalTextareaRef"
              class="code-editor-input"
              :value="modelValue"
              :disabled="disabled"
              :placeholder="placeholder"
              spellcheck="false"
              wrap="off"
              @input="onInput"
            />
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { FullscreenOutlined, FullscreenExitOutlined } from '@ant-design/icons-vue'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import groovy from 'highlight.js/lib/languages/groovy'
import { SCRIPT_LANGUAGES } from '../nodeConfigMeta'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('groovy', groovy)

const LINE_HEIGHT = 22
const PADDING_Y = 24

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'javascript' },
  disabled: { type: Boolean, default: false },
  rows: { type: Number, default: 12 },
  placeholder: { type: String, default: 'function main(params) { ... }' },
  fullscreenTitle: { type: String, default: '脚本编辑' },
})

const emit = defineEmits(['update:modelValue', 'update:language', 'change'])

const isFullscreen = ref(false)
const scrollerRef = ref(null)
const layerRef = ref(null)
const textareaRef = ref(null)
const modalScrollerRef = ref(null)
const modalLayerRef = ref(null)
const modalTextareaRef = ref(null)

const viewportHeight = computed(() => props.rows * LINE_HEIGHT + PADDING_Y)

const highlightedHtml = computed(() => {
  const code = props.modelValue || ''
  if (!code.trim()) return '&nbsp;'
  try {
    const lang = props.language === 'groovy' ? 'groovy' : props.language
    if (hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  } catch {
    return escapeHtml(code) || '&nbsp;'
  }
})

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 仅外层滚动：textarea 随内容增高，高亮层同步高度，不出现双滚动条 */
function syncLayerHeight(textarea, layer) {
  if (!textarea || !layer) return
  textarea.style.height = '0'
  const contentH = Math.max(viewportHeight.value, textarea.scrollHeight)
  textarea.style.height = `${contentH}px`
  layer.style.minHeight = `${contentH}px`
}

function syncActiveEditor() {
  if (isFullscreen.value) {
    syncLayerHeight(modalTextareaRef.value, modalLayerRef.value)
  } else {
    syncLayerHeight(textareaRef.value, layerRef.value)
  }
}

function onInput(e) {
  const v = e.target.value
  emit('update:modelValue', v)
  emit('change', v)
  nextTick(syncActiveEditor)
}

function onLanguageChange(v) {
  emit('update:language', v)
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

watch(
  () => [props.modelValue, props.language, props.rows],
  () => nextTick(syncActiveEditor),
)

watch(isFullscreen, async open => {
  await nextTick()
  if (open) {
    modalTextareaRef.value?.focus()
    syncLayerHeight(modalTextareaRef.value, modalLayerRef.value)
  } else {
    syncLayerHeight(textareaRef.value, layerRef.value)
  }
})

onMounted(() => nextTick(syncActiveEditor))
</script>

<style scoped>
.code-editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

/* 唯一滚动容器 */
.code-editor-body {
  position: relative;
  border: 1px solid var(--color-border-slate);
  border-radius: 8px;
  overflow: auto;
  overflow-x: auto;
  overflow-y: auto;
  background: #1e293b;
}

.code-editor-body--modal {
  height: 65vh;
  max-height: 65vh;
}

.code-editor-layer {
  position: relative;
  min-height: 100%;
}

.code-editor-highlight,
.code-editor-input {
  margin: 0;
  padding: 12px;
  font-family: ui-monospace, 'Cascadia Code', 'Source Code Pro', Menlo, monospace;
  font-size: 13px;
  line-height: 22px;
  tab-size: 2;
  box-sizing: border-box;
}

.code-editor-highlight {
  position: absolute;
  inset: 0;
  min-width: 100%;
  margin: 0;
  overflow: visible;
  pointer-events: none;
  white-space: pre;
  word-wrap: normal;
  color: #e2e8f0;
}

.code-editor-highlight :deep(.hljs-keyword) { color: #c084fc; }
.code-editor-highlight :deep(.hljs-string) { color: #86efac; }
.code-editor-highlight :deep(.hljs-number) { color: #fcd34d; }
.code-editor-highlight :deep(.hljs-comment) { color: var(--color-mute); }
.code-editor-highlight :deep(.hljs-function) { color: #7dd3fc; }

.code-editor-input {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  min-height: 100%;
  border: none;
  outline: none;
  resize: none;
  overflow: hidden;
  background: transparent;
  color: transparent;
  caret-color: #f8fafc;
  white-space: pre;
  word-wrap: normal;
}

.code-editor-input::placeholder {
  color: var(--color-mute);
}

.code-editor-input:disabled {
  cursor: not-allowed;
}
</style>

<style>
.code-editor-fullscreen-modal .ant-modal-body {
  padding-top: 12px;
}
</style>
