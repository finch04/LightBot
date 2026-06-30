<template>
  <div class="chat-mention-input-wrap">
    <ChatMentionPicker
      :visible="picker.open"
      :groups="mentionOptions"
      :query="picker.query"
      :active-index="picker.activeIndex"
      :loading="mentionLoading"
      :caret-rect="picker.caretRect"
      @select="onPickerSelect"
      @hover="onPickerHover"
    />
    <div
      ref="editorRef"
      class="mention-editor"
      :class="{ 'is-empty': !text }"
      :data-placeholder="placeholder"
      contenteditable="true"
      spellcheck="false"
      @input="onInput"
      @keydown="onKeydown"
      @compositionstart="onCompositionStart"
      @compositionend="onCompositionEnd"
      @blur="onBlur"
      @click="onEditorClick"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import ChatMentionPicker from './ChatMentionPicker.vue'
import { useChatMentions } from '../composables/useChatMentions'

const props = defineProps({
  modelValue: { type: String, default: '' },
  agentId: { type: [String, Number], default: null },
  agentVersionId: { type: [String, Number], default: null },
  placeholder: { type: String, default: '输入消息... (Enter 发送, Shift+Enter 换行)' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'update:mentions', 'send'])

const editorRef = ref(null)
const editorEl = () => editorRef.value

const {
  mentions,
  mentionOptions,
  mentionLoading,
  picker,
  detectMentionQuery,
  clearMentions,
  serializeForRequest,
} = useChatMentions(
  () => props.agentId,
  () => props.agentVersionId,
)

defineExpose({
  focus: () => editorEl()?.focus(),
  getText: () => text.value,
  getMentions: () => serializeForRequest(),
  clear: () => resetEditor(''),
  setText: (t) => resetEditor(t || ''),
  moveCursorToEnd,
  moveCursorToStart,
  mentions,
})

const text = ref('')
const lastEmittedText = ref('')
let composing = false

onMounted(() => {
  if (props.modelValue) {
    text.value = props.modelValue
    lastEmittedText.value = props.modelValue
    renderFromText(props.modelValue)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown, true)
})

watch(() => props.modelValue, (val) => {
  if (val !== lastEmittedText.value) {
    text.value = val || ''
    lastEmittedText.value = val || ''
    renderFromText(val || '')
  }
})

function escapeHtml(s) {
  return String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
}
function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, '&quot;')
}

function tokenToChipHtml(m) {
  const name = escapeHtml(m.name || m.token)
  const token = escapeAttr(m.token)
  const type = escapeAttr(m.type || '')
  const id = escapeAttr(String(m.resourceId ?? ''))
  return `<span class="mention-chip" contenteditable="false" data-mention-token="${token}" data-mention-type="${type}" data-mention-id="${id}" data-mention-name="${name}">@${name}</span>`
}

/**
 * 从外部 text（含 @type:id token）重建 contenteditable HTML
 * 同时重建 mentions 数组
 */
function renderFromText(rawText) {
  const el = editorEl()
  if (!el) return
  mentions.value = []
  if (!rawText) {
    el.innerHTML = ''
    return
  }
  const tokenRe = /@(knowledge|subagent|skill|tool):(\d+)/g
  let lastIdx = 0
  let html = ''
  let m
  while ((m = tokenRe.exec(rawText)) !== null) {
    html += escapeHtml(rawText.slice(lastIdx, m.index))
    const type = m[1]
    const resourceId = m[2]
    const token = m[0]
    const opt = findOptionByToken(token)
    const name = opt?.name || token
    html += tokenToChipHtml({ type, resourceId, name, token })
    mentions.value.push({ type, resourceId, name, token })
    lastIdx = m.index + m[0].length
  }
  html += escapeHtml(rawText.slice(lastIdx))
  el.innerHTML = html
  moveCursorToEnd()
}

function findOptionByToken(token) {
  for (const g of mentionOptions.value) {
    for (const it of g.items || []) {
      if (it.token === token) return it
    }
  }
  return null
}

/** 从 contenteditable DOM 提取 text + mentions（chip 视作 token 占位） */
function extractFromDom() {
  const el = editorEl()
  if (!el) return { text: '', mentions: [] }
  let text = ''
  const list = []
  el.childNodes.forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      if (node.classList?.contains('mention-chip')) {
        const token = node.dataset.mentionToken || ''
        const type = node.dataset.mentionType || ''
        const resourceId = node.dataset.mentionId || ''
        const name = node.dataset.mentionName || token
        text += token
        list.push({ type, resourceId: String(resourceId), name, token })
      } else {
        text += node.innerText || node.textContent || ''
      }
    }
  })
  return { text, mentions: list }
}

function onInput() {
  const { text: newText, mentions: newMentions } = extractFromDom()
  text.value = newText
  lastEmittedText.value = newText
  emit('update:modelValue', newText)
  mentions.value = newMentions
  emit('update:mentions', newMentions)
  detectMentionAtCursor()
}

function onKeydown(e) {
  // IME 输入中：Enter 不触发发送
  if (composing) {
    if (e.key === 'Enter') {
      e.preventDefault()
      return
    }
    return
  }

  // 浮层打开时优先处理 picker 键盘导航
  if (picker.value.open) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      picker.value.activeIndex = Math.min(picker.value.activeIndex + 1, totalPickerItems() - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      picker.value.activeIndex = Math.max(picker.value.activeIndex - 1, 0)
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      const item = pickerItemAtIndex(picker.value.activeIndex)
      if (item) onPickerSelect(item)
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      closePicker()
      return
    }
  }

  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
    return
  }

  // Backspace 删除 chip：光标紧贴在 chip 后时，删整个 chip
  if (e.key === 'Backspace' && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
    const sel = window.getSelection()
    if (sel && sel.isCollapsed && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0)
      const prev = previousSiblingVisible(range.startContainer, range.startOffset)
      if (prev && prev.nodeType === Node.ELEMENT_NODE && prev.classList?.contains('mention-chip')) {
        e.preventDefault()
        const token = prev.dataset.mentionToken
        prev.remove()
        mentions.value = mentions.value.filter(m => m.token !== token)
        onInput()
      }
    }
  }
}

function previousSiblingVisible(node, offset) {
  if (offset > 0 && node.nodeType === Node.TEXT_NODE) return null
  let cur = node
  if (cur.previousSibling) return cur.previousSibling
  while (cur && cur.parentNode && !cur.previousSibling) {
    cur = cur.parentNode
  }
  return cur ? cur.previousSibling : null
}

function onCompositionStart() { composing = true }
function onCompositionEnd() {
  composing = false
  onInput()
}

function onBlur() {
  setTimeout(() => {
    if (!editorEl()?.matches(':focus-within')) closePicker()
  }, 150)
}

function onEditorClick() {
  detectMentionAtCursor()
}

function onDocMousedown(e) {
  const el = editorEl()
  if (el && !el.contains(e.target) && !e.target.closest('.mention-picker')) {
    closePicker()
  }
}

function detectMentionAtCursor() {
  const el = editorEl()
  if (!el) return
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return
  const range = sel.getRangeAt(0)
  if (!el.contains(range.startContainer)) return

  const beforeText = textBeforeCaret(range)
  const detected = detectMentionQuery(beforeText, beforeText.length)
  if (detected) {
    picker.value.open = true
    picker.value.query = detected.query
    picker.value.activeIndex = 0
    picker.value.caretRect = getCaretRect(sel)
    document.addEventListener('mousedown', onDocMousedown, true)
  } else if (picker.value.open) {
    closePicker()
  }
}

/** 获取光标视觉坐标（@ 符号位置），用于浮层 fixed 定位 */
function getCaretRect(sel) {
  const r = sel.getRangeAt(0).cloneRange()
  let rect = r.getBoundingClientRect()
  // 空行时 rect 可能为零尺寸，退到 getClientRects 最后一项
  if (rect.top === 0 && rect.bottom === 0 && rect.left === 0) {
    const rects = r.getClientRects()
    if (rects.length > 0) rect = rects[rects.length - 1]
  }
  return { left: rect.left, top: rect.top, bottom: rect.bottom }
}

/** 取光标前的纯文本（chip 以 token 形式参与） */
function textBeforeCaret(range) {
  const el = editorEl()
  let text = ''
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_ALL, null)
  while (walker.nextNode()) {
    const node = walker.currentNode
    if (node === range.startContainer) {
      if (node.nodeType === Node.TEXT_NODE) {
        text += node.textContent.slice(0, range.startOffset)
      }
      break
    }
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent
    } else if (node.nodeType === Node.ELEMENT_NODE && node.classList?.contains('mention-chip')) {
      text += node.dataset.mentionToken || ''
    }
  }
  return text
}

function closePicker() {
  picker.value.open = false
  picker.value.query = ''
  picker.value.activeIndex = 0
  picker.value.caretRect = null
  document.removeEventListener('mousedown', onDocMousedown, true)
}

function totalPickerItems() {
  let n = 0
  for (const g of visibleGroups()) n += (g.items?.length || 0)
  return n
}

function visibleGroups() {
  const q = (picker.value.query || '').trim().toLowerCase()
  if (!q) return mentionOptions.value
  return mentionOptions.value
    .map(g => ({
      ...g,
      items: (g.items || []).filter(it =>
        (it.name || '').toLowerCase().includes(q) ||
        (it.description || '').toLowerCase().includes(q),
      ),
    }))
    .filter(g => g.items.length > 0)
}

function pickerItemAtIndex(idx) {
  let n = 0
  for (const g of visibleGroups()) {
    for (const it of g.items || []) {
      if (n === idx) return it
      n++
    }
  }
  return null
}

function onPickerSelect(item) {
  if (!item || !item.enabled) return
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) {
    closePicker()
    return
  }
  const curRange = sel.getRangeAt(0)
  // 光标当前在 @query 之后；向前扩展 1+query.length 字符覆盖整个 @query
  const extendLen = 1 + (picker.value.query?.length || 0)
  const node = curRange.startContainer
  const offset = curRange.startOffset

  if (node.nodeType === Node.TEXT_NODE && offset >= extendLen) {
    const delRange = document.createRange()
    delRange.setStart(node, offset - extendLen)
    delRange.setEnd(node, offset)
    delRange.deleteContents()

    const chip = createChipElement(item)
    delRange.insertNode(chip)

    const space = document.createTextNode(' ')
    if (chip.nextSibling) {
      chip.parentNode.insertBefore(space, chip.nextSibling)
    } else {
      chip.parentNode.appendChild(space)
    }

    const after = document.createRange()
    after.setStartAfter(space)
    after.collapse(true)
    sel.removeAllRanges()
    sel.addRange(after)
  }
  closePicker()
  onInput()
}

function createChipElement(m) {
  const chip = document.createElement('span')
  chip.className = 'mention-chip'
  chip.contentEditable = 'false'
  chip.dataset.mentionToken = m.token
  chip.dataset.mentionType = m.type
  chip.dataset.mentionId = String(m.resourceId)
  chip.dataset.mentionName = m.name || m.token
  chip.textContent = '@' + (m.name || m.token)
  return chip
}

function onPickerHover(idx) {
  picker.value.activeIndex = idx
}

function moveCursorToEnd() {
  const el = editorEl()
  if (!el) return
  el.focus()
  const range = document.createRange()
  range.selectNodeContents(el)
  range.collapse(false)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
}

function moveCursorToStart() {
  const el = editorEl()
  if (!el) return
  el.focus()
  const range = document.createRange()
  range.selectNodeContents(el)
  range.collapse(true)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
}

function resetEditor(newText) {
  text.value = newText
  lastEmittedText.value = newText
  clearMentions()
  if (editorEl()) editorEl().innerHTML = ''
  emit('update:modelValue', newText)
  emit('update:mentions', [])
}
</script>

<style lang="less" scoped>
.chat-mention-input-wrap {
  position: relative;
  flex: 1;
}

.mention-editor {
  width: 100%;
  min-height: 32px;
  max-height: 200px;
  overflow-y: auto;
  padding: 6px 10px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-color, #111);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  word-break: break-word;
  white-space: pre-wrap;

  &:empty::before {
    content: attr(data-placeholder);
    color: var(--text-color-secondary, #9ca3af);
    pointer-events: none;
  }

  &:focus {
    outline: none;
  }
}

:deep(.mention-chip) {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  margin: 0 2px;
  background: var(--primary-color-bg, rgba(59, 130, 246, 0.12));
  color: var(--primary-color, #3b82f6);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
  user-select: none;
  cursor: default;
  vertical-align: baseline;
  white-space: nowrap;
}
</style>
