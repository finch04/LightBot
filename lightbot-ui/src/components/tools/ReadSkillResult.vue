<template>
  <div class="read-skill-result">
    <div v-if="isPlainText" class="rs-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="rs-header">
        <ThunderboltOutlined class="rs-icon" />
        <span v-if="data.displayName" class="rs-name">{{ data.displayName }}</span>
        <span class="rs-slug">{{ data.slug }}</span>
        <span v-if="data.activated" class="rs-activated">已激活</span>
      </div>
      <!-- frontmatter 元数据 -->
      <div v-if="meta" class="rs-meta">
        <div v-if="meta.description" class="rs-meta-row">
          <span class="rs-meta-label">描述</span>
          <span class="rs-meta-value">{{ meta.description }}</span>
        </div>
        <div v-if="meta.version" class="rs-meta-row">
          <span class="rs-meta-label">版本</span>
          <span class="rs-meta-value rs-meta-tag">{{ meta.version }}</span>
        </div>
        <div v-if="meta.tool_dependencies && meta.tool_dependencies.length" class="rs-meta-row">
          <span class="rs-meta-label">工具依赖</span>
          <span class="rs-meta-value">
            <span v-for="t in meta.tool_dependencies" :key="t" class="rs-meta-tag">{{ t }}</span>
          </span>
        </div>
      </div>
      <!-- 指令内容（仅渲染 frontmatter 之后的部分） -->
      <div v-if="bodyContent" class="rs-content">
        <MarkdownPreview :content="bodyContent" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

/** 解析 SKILL.md 的 YAML frontmatter 和 body */
const meta = computed(() => {
  const content = data.value?.content
  if (!content || typeof content !== 'string') return null
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---/)
  if (!match) return null
  return parseSimpleYaml(match[1])
})

const bodyContent = computed(() => {
  const content = data.value?.content
  if (!content || typeof content !== 'string') return content || ''
  const idx = content.indexOf('---\n', 4)
  if (idx === -1) return content
  return content.substring(idx + 4).trim()
})

/** 简易 YAML 解析（仅支持 key: value 和 - item 列表） */
function parseSimpleYaml(text) {
  const result = {}
  let currentKey = null
  let currentList = null
  for (const line of text.split('\n')) {
    const listMatch = line.match(/^\s+-\s+(.+)/)
    if (listMatch && currentKey) {
      if (!currentList) currentList = []
      currentList.push(listMatch[1].trim())
      result[currentKey] = currentList
      continue
    }
    currentList = null
    const kvMatch = line.match(/^(\w[\w_]*):\s*(.*)/)
    if (kvMatch) {
      currentKey = kvMatch[1]
      const val = kvMatch[2].trim()
      if (val) {
        result[currentKey] = val
        currentList = null
      } else {
        currentList = []
        result[currentKey] = currentList
      }
    }
  }
  return Object.keys(result).length ? result : null
}
</script>

<style lang="less" scoped>
.read-skill-result {
  border: 1px solid #c4b5fd;
  border-left: 3px solid #8b5cf6;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-purple-bg);

  .rs-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .rs-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #c4b5fd;
    background: var(--color-purple-bg); font-size: 12px; font-weight: 600; color: #5b21b6;
    .rs-icon { color: #7c3aed; font-size: 14px; }
    .rs-name { font-weight: 600; }
    .rs-slug { font-family: monospace; color: #6d28d9; font-size: 11px; }
    .rs-activated {
      margin-left: auto; font-size: 11px; color: #16a34a;
      background: var(--color-success-bg); border: 1px solid #bbf7d0;
      border-radius: 4px; padding: 0 6px;
    }
  }

  .rs-meta {
    padding: 8px 10px;
    display: flex; flex-direction: column; gap: 4px;
    border-bottom: 1px solid #e9e5ff;
    background: var(--color-purple-bg);
    font-size: 12px;

    .rs-meta-row {
      display: flex; align-items: baseline; gap: 8px;
    }
    .rs-meta-label {
      color: #7c3aed; font-weight: 500; white-space: nowrap;
      min-width: 56px;
    }
    .rs-meta-value {
      color: var(--gray-700); display: flex; gap: 4px; flex-wrap: wrap;
    }
    .rs-meta-tag {
      display: inline-block; font-family: monospace; font-size: 11px;
      background: var(--color-purple-bg); color: #6d28d9; border: 1px solid #c4b5fd;
      border-radius: 4px; padding: 0 5px; line-height: 18px;
    }
  }

  .rs-content {
    padding: 10px 12px;
    max-height: 400px;
    overflow-y: auto;
  }
}
</style>
