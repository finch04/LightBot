import parser from 'cron-parser'
import dayjs from 'dayjs'

/**
 * 解析标准 5 段 Cron（分 时 日 月 周），返回接下来 N 次执行时间
 * @param {string} cronExpr
 * @param {number} [count=5]
 * @returns {{ ok: boolean, times: string[], error?: string }}
 */
export function previewCronNextRuns(cronExpr, count = 5) {
  const expr = String(cronExpr || '').trim()
  if (!expr) {
    return { ok: false, times: [], error: '请填写 Cron 表达式' }
  }
  const parts = expr.split(/\s+/).filter(Boolean)
  if (parts.length !== 5) {
    return { ok: false, times: [], error: '需为标准 5 段：分 时 日 月 周' }
  }
  try {
    const interval = parser.parseExpression(expr, { currentDate: new Date() })
    const times = []
    for (let i = 0; i < count; i += 1) {
      const next = interval.next()
      const date = typeof next.toDate === 'function' ? next.toDate() : next
      times.push(dayjs(date).format('YYYY-MM-DD HH:mm:ss'))
    }
    return { ok: true, times }
  } catch (e) {
    return { ok: false, times: [], error: e?.message || 'Cron 表达式无效' }
  }
}
