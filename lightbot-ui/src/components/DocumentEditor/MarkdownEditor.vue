<template>
  <div class="markdown-editor-wrap" :class="{ 'with-preview': preview }">
    <div ref="containerRef" class="markdown-editor"></div>
    <div v-if="preview" class="markdown-preview">
      <div class="markdown-preview-header">预览</div>
      <div class="markdown-preview-body" v-html="previewHtml"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'
import { marked } from 'marked'

const props = defineProps({
  modelValue: { type: String, default: '' },
  readOnly: { type: Boolean, default: false },
  language: { type: String, default: 'markdown' },
  preview: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'change'])

const containerRef = ref(null)
let editor = null

const previewHtml = computed(() => {
  return marked.parse(props.modelValue || '', { breaks: true })
})

onMounted(() => {
  if (!containerRef.value) return

  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: props.language,
    readOnly: props.readOnly,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    lineNumbers: 'on',
    fontSize: 14,
    lineHeight: 22,
    padding: { top: 16, bottom: 16 },
    automaticLayout: true,
    tabSize: 2,
    renderWhitespace: 'selection',
    overviewRulerBorder: false,
    scrollbar: {
      verticalScrollbarSize: 8,
      horizontalScrollbarSize: 8,
    },
    theme: 'vs',
  })

  editor.onDidChangeModelContent(() => {
    const value = editor.getValue()
    emit('update:modelValue', value)
    emit('change', value)
  })
})

watch(() => props.modelValue, (val) => {
  if (editor && editor.getValue() !== val) {
    editor.setValue(val || '')
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})

defineExpose({
  getEditor: () => editor,
  focus: () => editor?.focus(),
  format: () => editor?.getAction('editor.action.formatDocument')?.run(),
})
</script>

<style scoped>
.markdown-editor-wrap {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 400px;
}
.markdown-editor-wrap .markdown-editor {
  flex: 1;
  min-width: 0;
}
.markdown-editor-wrap.with-preview .markdown-editor {
  border-right: 1px solid #e5e7eb;
}
.markdown-preview {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.markdown-preview-header {
  padding: 8px 16px;
  font-size: 12px;
  color: #71717a;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.markdown-preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  font-size: 14px;
  line-height: 1.75;
  color: #171717;
}
.markdown-preview-body :deep(h1) { font-size: 1.5em; margin: 0.5em 0; font-weight: 600; }
.markdown-preview-body :deep(h2) { font-size: 1.3em; margin: 0.5em 0; font-weight: 600; }
.markdown-preview-body :deep(h3) { font-size: 1.1em; margin: 0.5em 0; font-weight: 600; }
.markdown-preview-body :deep(p) { margin: 0.5em 0; }
.markdown-preview-body :deep(code) { background: #f4f4f5; padding: 2px 4px; border-radius: 4px; font-size: 0.9em; }
.markdown-preview-body :deep(pre) { background: #f4f4f5; padding: 12px; border-radius: 6px; overflow-x: auto; }
.markdown-preview-body :deep(ul), .markdown-preview-body :deep(ol) { padding-left: 1.5em; margin: 0.5em 0; }
.markdown-preview-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.5em 0; }
.markdown-preview-body :deep(th), .markdown-preview-body :deep(td) { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
.markdown-preview-body :deep(th) { background: #f9fafb; font-weight: 600; }
.markdown-preview-body :deep(blockquote) { border-left: 4px solid #d4d4d8; padding-left: 12px; color: #52525b; margin: 0.5em 0; }
.markdown-preview-body :deep(hr) { border: none; border-top: 1px solid #e5e7eb; margin: 1em 0; }
</style>
