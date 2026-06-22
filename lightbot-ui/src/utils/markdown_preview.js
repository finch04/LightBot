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
      breaks: false,
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

  // 表格行被挤在一行：用 || 连接下一格/下一行（模型常见输出；允许 || 与 ** 之间有空格）
  s = s.replace(/\|\|\s*(?=\s*\*\*)/g, '|\n|')
  s = s.replace(/\|\|\s*(?=\s*-{2,})/g, '|\n|')

  // 无序列表「-文字」缺空格
  s = s.replace(/^(-)([^\s-])/gm, '$1 $2')

  // 0. 常见「粘连」：--- 与标题、列表、粗体挤在同一行（模型极易这样输出）
  s = s.replace(/(---)(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/(---)(\*\*)/g, '$1\n\n$2')
  s = s.replace(/(---)(-\s)/g, '$1\n\n$2')
  s = s.replace(/(---)(\d+\.)/g, '$1\n\n$2')

  // 1. 先补换行（后续标题规则依赖行首匹配）
  s = s.replace(/([：:；;。！？])(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/([。；;！？])(-\s)/g, '$1\n$2')

  // 2. ATX 标题：##标题 -> ## 标题；###1. -> ### 1.
  s = s.replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
  s = s.replace(/^(#{1,6})\s*(\d+\.)/gm, '$1 $2')

  // 2.1 标题紧跟表格管道符：###标题| col1 | col2 → ### 标题\n| col1 | col2
  //     marked GFM 表格解析器优先级高于 ATX 标题，会导致整行被当成表头
  s = s.replace(/^(#{1,6}\s+[^\n|]+)\|/gm, '$1\n|')

  // 有序列表项：行首「1.**」→「1. **」（GFM 要求 . 后有空格）
  s = s.replace(/^(\d+\.)(\*\*)/gm, '$1 $2')
  // 中文小结里「亮点1.**」「如下1.**」等缺换行
  s = s.replace(/(亮点|要点|包括|如下|清单|步骤)(\d+\.\*\*)/g, '$1\n$2')
  // 中文/中文标点 + 「数字.**」→ 换行（避免拉丁字母如 com3.** 被拆开）
  s = s.replace(/([\u4e00-\u9fff：:；,，。！？、])(\d+\.\*\*)/g, '$1\n$2')
  // 域名/英文后直接跟「1-9.**」列表（如 qq.com3.**）
  s = s.replace(/([a-zA-Z])([1-9]\.\*\*)/g, '$1\n$2')

  // 3. 标题/正文与列表项粘在一起
  s = s.replace(/([\u4e00-\u9fff\d\)])(-\s+(?!\d))/g, '$1\n$2')
  s = s.replace(/([\u4e00-\u9fff\d\)])(-\*\*)/g, '$1\n$2')
  s = s.replace(/^-\*\*/gm, '- **')
  s = s.replace(/(\*\*[^*\n]+\*\*)(-\s)/g, '$1\n$2')
  s = s.replace(/(\d+\.[\u4e00-\u9fff]+)(-\s)/g, '$1\n$2')

  // 4. 分隔线（--- 单独成行才是 hr）
  s = s.replace(/([^\n-\s])---([^\n-])/g, '$1\n\n---\n\n$2')
  s = s.replace(/^---([^\n-])/gm, '---\n\n$1')

  // 5. 行首「1.**」再处理一次（上面插入换行后可能出现新的行首）
  s = s.replace(/^(\d+\.)(\*\*)/gm, '$1 $2')

  return s
}

/**
 * 拆 GFM 表格行单元格（去掉首尾可选的 |）
 * @param {string} line
 * @returns {string[]}
 */
function splitTableCellsForTable(line) {
  let s = String(line).trim()
  if (!s.includes('|')) return []
  if (s.startsWith('|')) s = s.slice(1).trimStart()
  if (s.endsWith('|')) s = s.slice(0, -1).trimEnd()
  if (!s) return []
  return s.split('|').map((c) => c.trim())
}

function isTableSeparatorRow(cells) {
  if (!cells || cells.length < 2) return false
  return cells.every((c) => /^:?-{3,}:? *$/.test(c))
}

function isTableDataLine(line) {
  const t = line.trim()
  if (!t || !t.includes('|')) return false
  if (t.startsWith('```')) return false
  if (/^#{1,6}\s/.test(t) && (t.match(/\|/g) || []).length < 2) return false
  const cells = splitTableCellsForTable(line)
  if (cells.length < 2) return false
  return true
}

function padTableCells(cells, n) {
  const out = cells.slice(0, n)
  while (out.length < n) out.push('')
  return out
}

function formatTableRow(cells) {
  return '| ' + cells.join(' | ') + ' |'
}

/**
 * 流式阶段补全未闭合的 GFM 表格（补分隔行、统一列数），便于 marked 解析出 <table>
 */
function patchStreamingTables(text) {
  if (!text || !text.includes('|')) return text

  const lines = text.split('\n')
  const out = []
  let i = 0
  let inFence = false

  while (i < lines.length) {
    const line = lines[i]
    const trim = line.trim()

    if (trim.startsWith('```')) {
      inFence = !inFence
      out.push(line)
      i++
      continue
    }
    if (inFence) {
      out.push(line)
      i++
      continue
    }

    if (!isTableDataLine(line)) {
      out.push(line)
      i++
      continue
    }

    const block = []
    let j = i
    while (j < lines.length) {
      const L = lines[j]
      const t = L.trim()
      if (t.startsWith('```')) break
      if (t === '' && block.length > 0) break
      if (t === '' && block.length === 0) {
        j++
        break
      }
      if (t === '') break
      if (!isTableDataLine(L)) break
      block.push(L)
      j++
    }

    if (block.length === 0) {
      out.push(line)
      i++
      continue
    }

    const parsed = block.map((raw) => {
      const cells = splitTableCellsForTable(raw)
      return { raw, cells, isSep: isTableSeparatorRow(cells) }
    })

    let n = 2
    parsed.forEach((p) => {
      n = Math.max(n, p.cells.length)
    })

    const rows = []
    for (let k = 0; k < parsed.length; k++) {
      const p = parsed[k]
      rows.push({ ...p, cells: padTableCells(p.cells, n) })

      if (k === 0 && !p.isSep) {
        const next = parsed[k + 1]
        if (!next || !next.isSep) {
          rows.push({ isSep: true, synthetic: true, cells: Array(n).fill('---') })
        }
      }
    }

    rows.forEach((r) => {
      if (r.isSep) {
        const cells = r.cells.map((c) =>
          /^:?-{3,}:? *$/.test(c) ? c.replace(/\s+/g, '') || '---' : '---'
        )
        out.push(formatTableRow(padTableCells(cells, n)))
      } else {
        out.push(formatTableRow(r.cells))
      }
    })

    i = j
  }

  return out.join('\n')
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
  processed = patchStreamingTables(processed)
  if (streaming) {
    processed = patchStreamingMarkdown(processed)
  }

  return marked.parse(processed)
}
