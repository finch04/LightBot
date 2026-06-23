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

  // 1.2 非行首的 ATX 标题：非换行、非#、非空白符紧跟 ## → 拆行 + 补空行
  //     marked 要求 ## 必须在行首（前有 \n），否则渲染为纯文本
  //     排除 # 和空白避免误伤已正确格式的标题（如 "# ## 标题"）
  s = s.replace(/([^\n# \t])(#{1,6}\s)/g, '$1\n\n$2')

  // 1.3 行内 ATX 标题缺空格：中文/英文后紧跟 ##标题 → 先补空格再拆行
  //     规则 68 的 ^ 只匹配行首，无法处理行内的 ##标题
  s = s.replace(/([^ \t\n#])(#{1,6})([^\s#])/g, '$1$2 $3')
  //     补空格后可能产生新的非行首标题，再执行一次拆行
  s = s.replace(/([^\n# \t])(#{1,6}\s)/g, '$1\n\n$2')

  // 1.1 通用规则：非空行直接紧跟 ATX 标题时，补一个空行（确保标题前有空行）
  s = s.replace(/([^\n])(\n)(#{1,6}\s)/g, (match, prevChar, nl, heading) => {
    // 前面已经是空行则跳过
    if (prevChar === '\n') return match
    return prevChar + '\n\n' + heading
  })

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

  // 3.1 无序列表嵌套推断：上一项以 ：或 : 结尾 → 后续同级列表项缩进为子列表
  //     AI 常输出「- 项目经验：\n- 子项A\n- 子项B」，实际需要「- 项目经验：\n  - 子项A\n  - 子项B」才能渲染嵌套
  {
    const lines = s.split('\n')
    const out = []
    let parentIndent = -1 // 当前父项的基准缩进
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i]
      const listMatch = line.match(/^([ \t]*)- /)
      if (listMatch) {
        const curIndent = listMatch[1].length
        if (parentIndent >= 0 && curIndent > parentIndent) {
          // 比父项更深 → 已经是子项，无需额外缩进
          if (/[：:]\s*$/.test(line.trimEnd())) {
            parentIndent = curIndent // 子父项，更新基准
          }
          out.push(line)
          continue
        }
        if (parentIndent >= 0 && curIndent === parentIndent) {
          // 与父项同级：以 ：结尾 → 新父项（兄弟父）；否则 → 子项
          if (/[：:]\s*$/.test(line.trimEnd())) {
            parentIndent = curIndent
          } else {
            out.push('  ' + line)
            continue
          }
        } else {
          // 比父项更浅 或 无父项 → 以 ：结尾则成为新父项
          if (/[：:]\s*$/.test(line.trimEnd())) {
            parentIndent = curIndent
          } else {
            parentIndent = -1
          }
        }
      } else {
        parentIndent = -1
      }
      out.push(line)
    }
    s = out.join('\n')
  }

  s = s.replace(/(\*\*[^*\n]+\*\*)(-\s)/g, '$1\n$2')
  s = s.replace(/(\d+\.[\u4e00-\u9fff]+)(-\s)/g, '$1\n$2')

  // 4. 分隔线（--- 单独成行才是 hr）
  s = s.replace(/([^\n-\s])---([^\n-])/g, '$1\n\n---\n\n$2')
  s = s.replace(/^---([^\n-])/gm, '---\n\n$1')

  // 5. 行首「1.**」再处理一次（上面插入换行后可能出现新的行首）
  s = s.replace(/^(\d+\.)(\*\*)/gm, '$1 $2')

  // 6. 有序列表缺空格：1.文字 → 1. 文字（GFM 要求 . 后有空格）
  s = s.replace(/(\d+\.)([^\s\d*.])/gm, '$1 $2')

  // 6.1 有序列表粘连：1.XXX2.XXX → 分行（数字.内容 紧跟 数字.内容）
  s = s.replace(/(\d+\.\s*\S[^\n]*?)(?=\d+\.\s*\S)/g, '$1\n')

  // 7. 标题后紧跟非空行内容（非标题、非空行、非列表、非表格）→ 插入空行
  s = s.replace(/^(#{1,6}\s+[^\n]+)(\n(?!\n|#|-|\||\*\*|```|\d+\.).+)/gm, '$1\n$2')

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
