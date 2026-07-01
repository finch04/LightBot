<template>
  <div class="mention-text-renderer">
    <template v-for="(part, idx) in parts" :key="idx">
      <a-tooltip v-if="part.type === 'mention'" placement="top">
        <template #title>
          <div class="mention-tooltip-title">{{ part.tooltipTitle }}</div>
          <div v-if="part.tooltipSub" class="mention-tooltip-sub">{{ part.tooltipSub }}</div>
        </template>
        <span
          class="mention-chip"
          :class="part.chipClass"
          :data-mention-type="part.mentionType"
          :data-mention-token="part.token"
        >@{{ part.name }}</span>
      </a-tooltip>
      <span v-else class="mention-plain">{{ part.text }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getMentionChipClass, getMentionTooltip } from '@/utils/mentionDisplay'
import { buildMentionMap, parseMentionText, resolveMentionSnapshot } from '@/utils/mention_utils'

const props = defineProps({
  /** 原始消息文本（含 @type:id token） */
  content: { type: String, default: '' },
  /** 历史快照中的 mentions 列表（msg.metadata.mentions） */
  mentions: { type: Array, default: () => [] },
  finalized: { type: Boolean, default: true },
})

const parts = computed(() => {
  const content = props.content || ''
  if (!content) return []

  const map = buildMentionMap(props.mentions)
  const result = []

  for (const segment of parseMentionText(content)) {
    if (segment.kind === 'text') {
      if (segment.text) result.push({ type: 'text', text: segment.text })
      continue
    }
    const token = segment.token
    const mentionType = segment.type
    const m = resolveMentionSnapshot(map, props.mentions, token, mentionType, segment.resourceId)
    const valid = !!m
    const name = m?.name || mentionType || token
    const tooltip = getMentionTooltip(mentionType, m?.name || name, token, valid)
    result.push({
      type: 'mention',
      token,
      mentionType,
      name: valid ? name : `${name}（已失效）`,
      chipClass: getMentionChipClass(mentionType, valid),
      tooltipTitle: tooltip.title,
      tooltipSub: tooltip.sub,
    })
  }
  return result
})
</script>

<style lang="less">
.mention-text-renderer {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.mention-plain {
  white-space: pre-wrap;
}

.mention-text-renderer .mention-chip {
  display: inline;
  padding: 1px 6px;
  margin: 0 1px;
  border-radius: 4px;
  font-size: 0.92em;
  font-weight: 500;
  line-height: 1.5;
  cursor: default;
  white-space: nowrap;
  background: rgba(59, 130, 246, 0.14);
  color: #2563eb;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

.mention-text-renderer .mention-chip-knowledge {
  background: rgba(16, 185, 129, 0.16);
  color: #059669;
}

.mention-text-renderer .mention-chip-subagent {
  background: rgba(245, 158, 11, 0.16);
  color: #d97706;
}

.mention-text-renderer .mention-chip-skill {
  background: rgba(168, 85, 247, 0.16);
  color: #9333ea;
}

.mention-text-renderer .mention-chip-tool {
  background: rgba(59, 130, 246, 0.16);
  color: #2563eb;
}

.mention-text-renderer .mention-chip-invalid {
  background: rgba(239, 68, 68, 0.14);
  color: #dc2626;
  text-decoration: line-through;
}

[data-theme="dark"] .mention-text-renderer .mention-chip-knowledge {
  background: rgba(16, 185, 129, 0.28);
  color: #6ee7b7;
  box-shadow: inset 0 0 0 1px rgba(110, 231, 183, 0.28);
}

[data-theme="dark"] .mention-text-renderer .mention-chip-subagent {
  background: rgba(245, 158, 11, 0.28);
  color: #fcd34d;
  box-shadow: inset 0 0 0 1px rgba(252, 211, 77, 0.28);
}

[data-theme="dark"] .mention-text-renderer .mention-chip-skill {
  background: rgba(168, 85, 247, 0.28);
  color: #d8b4fe;
  box-shadow: inset 0 0 0 1px rgba(216, 180, 254, 0.28);
}

[data-theme="dark"] .mention-text-renderer .mention-chip-tool {
  background: rgba(59, 130, 246, 0.28);
  color: #93c5fd;
  box-shadow: inset 0 0 0 1px rgba(147, 197, 253, 0.28);
}

[data-theme="dark"] .mention-text-renderer .mention-chip-invalid {
  background: rgba(239, 68, 68, 0.24);
  color: #fca5a5;
  box-shadow: inset 0 0 0 1px rgba(252, 165, 165, 0.28);
}

.mention-tooltip-title {
  font-weight: 500;
}

.mention-tooltip-sub {
  margin-top: 2px;
  font-size: 12px;
  opacity: 0.85;
}
</style>
