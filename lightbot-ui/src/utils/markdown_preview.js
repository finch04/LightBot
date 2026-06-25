import MarkdownIt from 'markdown-it'
import markdownItKatex from '@vscode/markdown-it-katex'
import taskLists from 'markdown-it-task-lists'
import DOMPurify from 'dompurify'
import { createHighlighter } from 'shiki'

const markdownKatexPlugin = markdownItKatex.default || markdownItKatex

// 简单字符串哈希，用于缓存 key（避免长字符串作 Map key）
function hashStr(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0
  }
  return hash.toString(36)
}

// ── Shiki 异步初始化 ──────────────────────────────────────────────

let highlighterPromise
const getHighlighter = () => {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ['github-light', 'github-dark'],
      langs: ['plaintext']
    }).catch((error) => {
      highlighterPromise = undefined
      throw error
    })
  }
  return highlighterPromise
}

const normalizeCodeLanguage = (lang) => {
  if (!lang) return ''
  const map = {
    js: 'javascript', ts: 'typescript', py: 'python',
    rb: 'ruby', cs: 'csharp', kt: 'kotlin',
    sh: 'bash', shell: 'bash', zsh: 'bash',
    yml: 'yaml', md: 'markdown', tex: 'latex',
    dockerfile: 'docker', 'c++': 'cpp', 'c#': 'csharp',
  }
  return map[lang.toLowerCase()] || lang.toLowerCase()
}

const CODE_FENCE_RE = /(^|\n) {0,3}(```|~~~)/
const CODE_FENCE_LANGUAGE_RE = /(^|\n) {0,3}(```+|~~~+)[ \t]*([^\s:,`]*)/g

const hasCodeFence = (content) => CODE_FENCE_RE.test(content)

const collectCodeFenceLanguages = (content) => {
  const languages = new Set()
  for (const match of String(content || '').matchAll(CODE_FENCE_LANGUAGE_RE)) {
    const language = normalizeCodeLanguage(match[3])
    if (language) languages.add(language)
  }
  return [...languages]
}

const ensureLanguages = async (highlighter, languages) => {
  const loaded = new Set(highlighter.getLoadedLanguages())
  await Promise.all(
    languages
      .filter((language) => !loaded.has(language))
      .map((language) => {
        try {
          return highlighter.loadLanguage(language).catch(() => null)
        } catch {
          return null
        }
      })
  )
}

// ── markdown-it 渲染器工厂（带缓存）─────────────────────────────────

const rendererCache = new Map()

const createRenderer = ({ themeName, highlighter }) =>
  new MarkdownIt({
    html: true,
    breaks: true,
    linkify: true,
    typographer: true,
    highlight: highlighter
      ? (code, lang) => {
          const language = normalizeCodeLanguage(lang)
          const loadedLanguages = highlighter.getLoadedLanguages()
          const targetLanguage = loadedLanguages.includes(language) ? language : 'plaintext'
          return highlighter.codeToHtml(code, { lang: targetLanguage, theme: themeName })
        }
      : undefined
  })
    .use(markdownKatexPlugin, { throwOnError: false, errorColor: '#cc0000', trust: false })
    .use(taskLists, { enabled: false, label: false, labelAfter: false })

const getRenderer = async (theme, needsHighlight) => {
  const cacheKey = needsHighlight ? theme : 'plain'
  const cached = rendererCache.get(cacheKey)
  if (cached) return cached

  const rendererPromise = needsHighlight
    ? getHighlighter().then((highlighter) => createRenderer({ themeName: theme, highlighter }))
    : Promise.resolve(createRenderer({ themeName: theme }))
  rendererCache.set(cacheKey, rendererPromise)
  return rendererPromise
}

// ── HTML 缓存（LRU 100 条）──────────────────────────────────────────

const renderedHtmlCache = new Map()
const MAX_RENDER_CACHE_SIZE = 100

const getCachedHtml = (cacheKey) => renderedHtmlCache.get(cacheKey)
const setCachedHtml = (cacheKey, html) => {
  if (renderedHtmlCache.size >= MAX_RENDER_CACHE_SIZE) {
    renderedHtmlCache.delete(renderedHtmlCache.keys().next().value)
  }
  renderedHtmlCache.set(cacheKey, html)
}

// ── normalizeMarkdown（精简版：仅处理后端转义问题）──────────────────

/**
 * 规范化 LLM 常见 Markdown 写法（尤其中文模型省略空格/换行的情况）。
 * markdown-it 配置 breaks/html/linkify/typographer 后，大部分问题由引擎自动处理，
 * 此处仅修复引擎无法处理的边界情况。
 */
export function normalizeMarkdown(text) {
  if (!text) return ''

  let s = text.replace(/\r\n/g, '\n')

  // 表格行被挤在一行：用 || 连接下一格/下一行（模型常见输出）
  s = s.replace(/\|\|\s*(?=\s*\*\*)/g, '|\n|')
  s = s.replace(/\|\|\s*(?=\s*-{2,})/g, '|\n|')

  // 无序列表「-文字」缺空格（markdown-it 需要 - 后有空格）
  s = s.replace(/^(-)([^\s-])/gm, '$1 $2')

  // ATX 标题缺空格：##标题 → ## 标题（markdown-it 需要 # 后有空格）
  s = s.replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')

  // 非行首标题：中文/标点后紧跟 ## → 拆行 + 补空行
  s = s.replace(/([：:；;。！？])(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/([^ \t\n#])(#{1,6})([^\s#])/g, '$1$2 $3')
  s = s.replace(/([^\n# \t])(#{1,6}\s)/g, '$1\n\n$2')

  // 有序列表缺空格：1.文字 → 1. 文字
  s = s.replace(/^(\d+\.)([^\s\d*.])/gm, '$1 $2')
  // 有序列表缺空格：1.** → 1. **
  s = s.replace(/^(\d+\.)(\*\*)/gm, '$1 $2')

  // 列表项挤在同一行：「xxx- **」或「xxx - 」→ 拆行（AI 常见输出）
  s = s.replace(/([^\s])(- \*\*)/g, '$1\n$2')
  s = s.replace(/([^\s])(- [^\s-*])/g, '$1\n$2')

  // 分隔线与标题/粗体粘连在同一行
  s = s.replace(/(---)(#{1,6})/g, '$1\n\n$2')
  s = s.replace(/(---)(\*\*)/g, '$1\n\n$2')
  s = s.replace(/(---)(-\s)/g, '$1\n\n$2')
  s = s.replace(/(---)(\d+\.)/g, '$1\n\n$2')

  return s
}

// ── 表格流式补丁 ──────────────────────────────────────────────────

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
 * 流式阶段补全未闭合的 GFM 表格（补分隔行、统一列数），便于 markdown-it 解析出 <table>
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
      if (t === '' && block.length === 0) { j++; break }
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
    parsed.forEach((p) => { n = Math.max(n, p.cells.length) })

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

// ── 代码围栏畸形修复 ──────────────────────────────────────────

/**
 * 修复 AI 输出中常见的代码围栏畸形：
 * 1. 语言标签和代码粘在一行：```javascriptconst x = 1;  →  ```javascript\nconst x = 1;
 * 2. 闭合 ``` 紧跟内容不在行首：code```  →  code\n```
 */
function normalizeCodeFences(text) {
  if (!text || !text.includes('```')) return text

  const KNOWN_LANGS = [
    'javascript', 'typescript', 'python', 'java', 'csharp', 'ruby', 'go', 'rust',
    'c', 'cpp', 'php', 'swift', 'kotlin', 'scala', 'bash', 'shell', 'sql', 'html',
    'css', 'json', 'yaml', 'xml', 'markdown', 'latex', 'r', 'lua', 'perl', 'haskell',
    'elixir', 'clojure', 'groovy', 'powershell', 'dockerfile', 'makefile', 'toml',
  ]

  let s = text

  // 修复1：``` 后语言标签和代码粘连（无换行）
  // 标题```javascript代码 → 标题\n```javascript\n代码
  // 通过已知语言列表精确切分语言标签和代码（解决 javascriptconst 无法拆分的问题）
  s = s.replace(
    /(^|\n)(.*?)`{3,}([a-zA-Z0-9_+#.:-]{1,20})((?:\n)|[^\n`])/gm,
    (match, prefix, before, lang, after) => {
      // 尝试从 lang 中切出已知语言名
      let actualLang = lang
      let codeAfter = after
      if (!KNOWN_LANGS.includes(lang.toLowerCase())) {
        for (let i = lang.length - 1; i >= 2; i--) {
          if (KNOWN_LANGS.includes(lang.substring(0, i).toLowerCase())) {
            actualLang = lang.substring(0, i)
            codeAfter = lang.substring(i) + after
            break
          }
        }
      }
      const needsSplit = before && /\S$/.test(before)
      const beforeFixed = needsSplit ? before + '\n' : before
      if (codeAfter === '\n') {
        return prefix + beforeFixed + '```' + actualLang + '\n'
      }
      return prefix + beforeFixed + '```' + actualLang + '\n' + codeAfter
    }
  )

  // 修复2：闭合 ``` 在行尾紧跟内容（markdown-it 要求 ``` 独占一行）
  // code```  →  code\n```
  // .*\S 匹配行内容（含反引号），``` 后仅允许空白到行尾（排除 ```language 开头的围栏）
  s = s.replace(
    /(^|\n)(.*\S)`{3,}[ \t]*$/gm,
    '$1$2\n```'
  )

  return s
}

// ── 流式 Markdown 补丁 ──────────────────────────────────────────

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

// ── 渲染入口 ──────────────────────────────────────────────────────

/**
 * 渲染 Markdown 为 HTML（异步，支持 Shiki 语言按需加载）
 * @param {string} text 原始文本
 * @param {{ streaming?: boolean, theme?: string }} options
 * @returns {Promise<string>}
 */
export async function renderMarkdown(text, { streaming = false, theme = 'github-dark' } = {}) {
  if (!text) return ''

  let processed = normalizeMarkdown(text)
  processed = normalizeCodeFences(processed)
  processed = patchStreamingTables(processed)
  if (streaming) {
    processed = patchStreamingMarkdown(processed)
  }

  const themeName = theme === 'github-dark' ? 'github-dark' : 'github-light'
  const needsHighlight = hasCodeFence(processed)
  const cacheKey = `${needsHighlight ? themeName : 'plain'} ${hashStr(processed)}_${processed.length}`

  const cachedHtml = getCachedHtml(cacheKey)
  if (cachedHtml !== undefined) return cachedHtml

  if (needsHighlight) {
    const highlighter = await getHighlighter()
    await ensureLanguages(highlighter, collectCodeFenceLanguages(processed))
  }

  const md = await getRenderer(themeName, needsHighlight)
  const rawHtml = md.render(processed)

  const html = DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['input'],
    ADD_ATTR: ['class', 'style', 'target', 'rel', 'type', 'checked', 'disabled']
  })

  setCachedHtml(cacheKey, html)
  return html
}

// ── 同步渲染（供 computed 等同步场景使用，无 Shiki 高亮）────────────

const syncRenderer = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
})
  .use(markdownKatexPlugin, { throwOnError: false, errorColor: '#cc0000', trust: false })
  .use(taskLists, { enabled: false, label: false, labelAfter: false })

/**
 * 同步渲染 Markdown 为 HTML（无 Shiki 高亮，使用内置高亮）
 * 适用于 computed 等同步场景，如文件预览、文档编辑器等。
 * @param {string} text 原始文本
 * @returns {string}
 */
export function renderMarkdownSync(text) {
  if (!text) return ''
  const rawHtml = syncRenderer.render(text)
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['input'],
    ADD_ATTR: ['class', 'style', 'target', 'rel', 'type', 'checked', 'disabled']
  })
}
