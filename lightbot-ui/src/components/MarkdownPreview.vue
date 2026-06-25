<template>
  <div class="markdown-preview" :class="{ 'no-image-preview': !imagePreview }" ref="containerRef" v-html="renderedHtml" @click="onContainerClick"></div>
  <ChatMediaPreview v-if="imagePreview" v-model:open="previewOpen" :src="previewSrc" media-type="image" />
</template>

<script setup>
import { watch, shallowRef, ref, onBeforeUnmount } from 'vue'
import { renderMarkdown } from '@/utils/markdown_preview'
import ChatMediaPreview from '@/components/ChatMediaPreview.vue'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' },
  /** false=流式进行中（补全未闭合标记）；true=对话结束后的终态渲染 */
  finalized: { type: Boolean, default: true },
  /** 是否启用图片点击预览（仅 AI 对话场景需要） */
  imagePreview: { type: Boolean, default: true },
  /** 是否剥离 YAML frontmatter（SKILL.md 等文件需要） */
  stripFrontmatter: { type: Boolean, default: false },
})

const renderedHtml = shallowRef('')
const containerRef = ref(null)

// ── 图片预览 ──
const previewOpen = ref(false)
const previewSrc = ref('')

function onContainerClick(e) {
  // 拦截 markdown 内的链接点击：外部链接新窗口打开，相对链接阻止跳转
  const anchor = e.target.closest('a')
  if (anchor && containerRef.value?.contains(anchor)) {
    e.preventDefault()
    const href = anchor.getAttribute('href')
    if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
      window.open(href, '_blank', 'noopener')
    }
    return
  }

  if (!props.imagePreview) return
  const img = e.target.closest('img')
  if (!img || !containerRef.value?.contains(img)) return
  if (img.src) {
    previewSrc.value = img.src
    previewOpen.value = true
  }
}

/** 剥离 YAML frontmatter（--- 包裹的头部元数据） */
function stripFrontmatter(text) {
  if (!text) return text
  return text.replace(/^---\n[\s\S]*?\n---\n?/, '')
}

watch(
  () => [props.content, props.finalized],
  async ([val, finalized], _, onCleanup) => {
    let expired = false
    onCleanup(() => { expired = true })

    if (!val) {
      renderedHtml.value = ''
      return
    }

    const textToRender = props.stripFrontmatter ? stripFrontmatter(val) : val
    const html = await renderMarkdown(textToRender, { streaming: !finalized })
    if (!expired) renderedHtml.value = html
  },
  { immediate: true }
)
</script>

<style lang="less">
.markdown-preview {
  max-width: 100%;
  color: var(--gray-1000);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei',
    'Hiragino Sans GB', 'Source Han Sans CN', sans-serif;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;

  h1, h2 { font-size: 1.25rem; margin: 1rem 0 0.5rem; }
  h3, h4 { font-size: 1.1rem; margin: 0.8rem 0 0.4rem; }
  h5, h6 { font-size: 1rem; margin: 0.6rem 0 0.3rem; }

  p { margin: 0.4rem 0; }
  p:last-child { margin-bottom: 0; }

  strong { font-weight: 600; }

  ul, ol { padding-left: 1.625rem; margin: 0.4rem 0; }
  li { margin: 0.15rem 0; }
  li > p, ol > p, ul > p { margin: 0.25rem 0; }

  a { color: var(--main-700); text-decoration: none; }
  a:hover { text-decoration: underline; }

  hr {
    height: 1px;
    margin: 1rem 0;
    border: 0;
    background: linear-gradient(90deg, transparent, var(--gray-200), transparent);
  }

  blockquote {
    margin: 0.6rem 0;
    padding: 0.25rem 0 0.25rem 1rem;
    border-left: 3px solid var(--gray-200);
    color: var(--gray-700);
  }

  code {
    font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.5;
  }

  :not(pre) > code {
    padding: 1px 5px;
    border-radius: 4px;
    background-color: var(--gray-25);
  }

  pre {
    margin: 0.6rem 0;
    padding: 12px 14px;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: auto;
    font-size: 13px;
    line-height: 1.5;
    background: var(--gray-25);

    code {
      padding: 0;
      background: none;
      border-radius: 0;
    }
  }

  table {
    width: 100%;
    border-collapse: collapse;
    margin: 0.8rem 0;
    font-size: 14px;
    display: table;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: hidden;
  }

  th, td {
    padding: 8px 12px;
    text-align: left;
    border: 1px solid var(--gray-100);
  }

  th {
    background-color: var(--gray-25);
    color: var(--gray-800);
    font-weight: 600;
  }

  td { color: var(--gray-800); }

  tbody tr:hover { background-color: var(--gray-25); }

  img {
    display: block;
    max-width: 360px;
    max-height: 260px;
    height: auto;
    border-radius: 6px;
    cursor: pointer;
    object-fit: contain;
    transition: transform 0.2s;
    margin: 0.5rem auto;
  }
  img:hover {
    transform: scale(1.02);
  }
}
.no-image-preview img {
  cursor: default;
}
.no-image-preview img:hover {
  transform: none;
}
</style>
