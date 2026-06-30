<template>
  <MarkdownPreview :content="renderedContent" :finalized="finalized" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownPreview from './MarkdownPreview.vue'

const MENTION_TOKEN_RE = /@(knowledge|subagent|skill|tool):(\d+)/g

const props = defineProps({
  /** 原始消息文本（含 @type:id token） */
  content: { type: String, default: '' },
  /** 历史快照中的 mentions 列表（msg.metadata.mentions） */
  mentions: { type: Array, default: () => [] },
  finalized: { type: Boolean, default: true },
})

/**
 * 把 @type:id token 替换为 chip span，让 MarkdownPreview 渲染为可点击的 chip。
 * mentions 快照缺失时降级展示 @name（已失效），避免裸露 token。
 */
const renderedContent = computed(() => {
  if (!props.content) return ''

  const map = new Map()
  for (const m of props.mentions || []) {
    if (m?.token) map.set(m.token, m)
  }

  return props.content.replace(MENTION_TOKEN_RE, (token) => {
    const m = map.get(token)
    const type = m?.type || token.split(':')[0].slice(1) || 'unknown'
    const name = m?.name || token
    const label = m ? escapeHtml(name) : escapeHtml(name) + '（已失效）'
    const cls = m ? `mention-chip mention-chip-${escapeAttr(type)}` : 'mention-chip mention-chip-invalid'
    return `<span class="${cls}" data-mention-type="${escapeAttr(type)}" data-mention-token="${escapeAttr(token)}">@${label}</span>`
  })
})

function escapeHtml(s) {
  return String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
}
function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, '&quot;')
}
</script>

<style lang="less">
.markdown-preview :deep(.mention-chip) {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  margin: 0 2px;
  background: rgba(59, 130, 246, 0.12);
  color: #3b82f6;
  border-radius: 4px;
  font-size: 0.92em;
  line-height: 1.4;
  user-select: none;
  white-space: nowrap;
}

.markdown-preview :deep(.mention-chip-knowledge) {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.markdown-preview :deep(.mention-chip-subagent) {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.markdown-preview :deep(.mention-chip-skill) {
  background: rgba(168, 85, 247, 0.12);
  color: #9333ea;
}

.markdown-preview :deep(.mention-chip-tool) {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.markdown-preview :deep(.mention-chip-invalid) {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
  text-decoration: line-through;
}
</style>
