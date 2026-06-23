<template>
  <div class="markdown-preview" v-html="renderedHtml"></div>
</template>

<script setup>
import { watch, shallowRef } from 'vue'
import { renderMarkdown } from '@/utils/markdown_preview'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' },
  /** false=流式进行中（补全未闭合标记）；true=对话结束后的终态渲染 */
  finalized: { type: Boolean, default: true },
})

const renderedHtml = shallowRef('')

watch(
  () => [props.content, props.finalized],
  async ([val, finalized], _, onCleanup) => {
    let expired = false
    onCleanup(() => { expired = true })

    if (!val) {
      renderedHtml.value = ''
      return
    }

    const html = await renderMarkdown(val, { streaming: !finalized })
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

  img { max-width: 100%; height: auto; }
}
</style>
