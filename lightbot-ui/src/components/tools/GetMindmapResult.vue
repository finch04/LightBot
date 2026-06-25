<template>
  <div class="get-mindmap-result">
    <div v-if="isPlainText" class="gm-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <!-- header -->
      <div class="gm-header">
        <BranchesOutlined class="gm-header-icon" />
        <span>思维导图 — {{ data.knowledge_name }}</span>
      </div>
      <!-- 思维导图（SVG） -->
      <div class="gm-content">
        <svg ref="svgRef" class="gm-svg"></svg>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { BranchesOutlined } from '@ant-design/icons-vue'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'

const props = defineProps({ event: { type: Object, required: true } })

const svgRef = ref(null)

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

/** 递归 JSON → Markdown */
function jsonToMarkdown(node, level) {
  const prefix = '#'.repeat(Math.min(level + 1, 6))
  let md = `${prefix} ${node.content || node.name || ''}\n`
  if (node.children) {
    for (const child of node.children) {
      md += jsonToMarkdown(child, level + 1)
    }
  }
  return md
}

/** Markdown → markmap root */
function toMarkmapRoot(mindmap) {
  if (!mindmap) return null
  const md = typeof mindmap === 'string' ? mindmap : jsonToMarkdown(mindmap, 0)
  const transformer = new Transformer()
  const { root } = transformer.transform(md)
  return root
}

/** 渲染 markmap 到指定 SVG */
function renderToSvg(svgEl, root) {
  if (!svgEl || !root) return
  svgEl.innerHTML = ''
  Markmap.create(svgEl, { maxWidth: 300 }, root)
}

function renderMindmap() {
  const root = toMarkmapRoot(data.value?.mindmap)
  nextTick(() => renderToSvg(svgRef.value, root))
}

onMounted(() => {
  if (!isPlainText.value) renderMindmap()
})

watch(() => data.value, () => {
  if (!isPlainText.value) nextTick(renderMindmap)
})
</script>

<style lang="less" scoped>
.get-mindmap-result {
  border: 1px solid #fdba74;
  border-left: 3px solid #f97316;
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-warn-bg);

  .gm-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .gm-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; border-bottom: 1px solid #fdba74;
    background: #ffedd5; font-size: 12px; font-weight: 600; color: #9a3412;
    .gm-header-icon { color: #ea580c; font-size: 14px; }
  }

  .gm-content {
    padding: 8px 10px;
    overflow: auto;
    max-height: 300px;
  }

  .gm-svg {
    width: 100%;
    min-height: 200px;
    background: var(--color-canvas);
    border-radius: 6px;
    --markmap-text-color: var(--color-ink);
    --markmap-circle-open-bg: var(--color-canvas);
    --markmap-code-bg: var(--color-canvas-soft-2);
    --markmap-code-color: var(--color-ink);

    :deep(.markmap-link) {
      stroke: var(--color-mute);
    }
    :deep(.markmap-node > circle) {
      stroke: var(--color-mute);
      fill: var(--color-canvas);
    }
  }
}
</style>
