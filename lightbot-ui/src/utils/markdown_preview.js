import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'

let configured = false

function ensureConfigured() {
  if (configured) return
  configured = true

  marked.use(
    markedHighlight({
      langPrefix: 'hljs language-',
      highlight(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
          return hljs.highlight(code, { language: lang }).value
        }
        return hljs.highlightAuto(code).value
      },
    }),
    {
      renderer: {
        link({ href, title, tokens }) {
          const text = this.parser.parseInline(tokens)
          const titleAttr = title ? ` title="${title}"` : ''
          return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
        },
      },
      breaks: true,
      gfm: true,
    }
  )
}

/**
 * 规范化 LLM 常见 Markdown 写法（尤其中文模型省略空格/换行的情况）
 */
export function normalizeMarkdown(text) {
  if (!text) return ''

  let s = text.replace(/\r\n/g, '\n')

  // 1. 先补换行（后续标题规则依赖行首匹配）
  s = s.replace(/([：:；;。！？])(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/([。；;！？])(-\s)/g, '$1\n$2')

  // 2. ATX 标题：##标题 -> ## 标题；###1. -> ### 1.
  s = s.replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
  s = s.replace(/^(#{1,6})\s*(\d+\.)/gm, '$1 $2')

  // 3. 标题/正文与列表项粘在一起
  s = s.replace(/([\u4e00-\u9fff\d\)])(-\s+(?!\d))/g, '$1\n$2')
  s = s.replace(/([\u4e00-\u9fff\d\)])(-\*\*)/g, '$1\n$2')
  s = s.replace(/^-\*\*/gm, '- **')
  s = s.replace(/(\*\*[^*\n]+\*\*)(-\s)/g, '$1\n$2')
  s = s.replace(/(\d+\.[\u4e00-\u9fff]+)(-\s)/g, '$1\n$2')

  // 4. 分隔线
  s = s.replace(/([^\n-\s])---([^\n-])/g, '$1\n\n---\n\n$2')
  s = s.replace(/^---([^\n-])/gm, '---\n\n$1')

  return s
}

function patchStreamingMarkdown(text) {
  let processed = text
  const backtickCount = (processed.match(/```/g) || []).length
  if (backtickCount % 2 !== 0) {
    processed += '\n```'
  }
  const boldCount = (processed.match(/\*\*/g) || []).length
  if (boldCount % 2 !== 0) {
    processed += '**'
  }
  return processed
}

/**
 * 渲染 Markdown 为 HTML
 * @param {string} text 原始文本
 * @param {{ streaming?: boolean }} options streaming=true 时补全未闭合标记；false 时做终态完整渲染
 */
export function renderMarkdown(text, { streaming = false } = {}) {
  if (!text) return ''
  ensureConfigured()

  let processed = normalizeMarkdown(text)
  if (streaming) {
    processed = patchStreamingMarkdown(processed)
  }

  return marked.parse(processed)
}
