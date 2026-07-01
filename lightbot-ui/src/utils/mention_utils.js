/** @ mention token 正则（与后端 ChatMentionDTO.token 格式一致） */
export const MENTION_TOKEN_RE = /@(knowledge|subagent|skill|tool):(\d+)/g

/**
 * 解析消息文本中的 mention token 片段
 * @param {string} content
 * @returns {Array<{ kind: 'text'|'mention', text?: string, token?: string, type?: string, resourceId?: string, start: number, end: number }>}
 */
export function parseMentionText(content = '') {
  const value = String(content || '')
  const segments = []
  let lastIndex = 0
  const re = new RegExp(MENTION_TOKEN_RE.source, 'g')
  let match
  while ((match = re.exec(value)) !== null) {
    const start = match.index
    const token = match[0]
    const end = start + token.length
    if (start > lastIndex) {
      segments.push({ kind: 'text', text: value.slice(lastIndex, start), start: lastIndex, end: start })
    }
    segments.push({
      kind: 'mention',
      token,
      type: match[1],
      resourceId: match[2],
      start,
      end,
    })
    lastIndex = end
  }
  if (lastIndex < value.length) {
    segments.push({ kind: 'text', text: value.slice(lastIndex), start: lastIndex, end: value.length })
  }
  return segments
}

/**
 * 从 mention 快照列表构建 token → 快照 Map（同时索引 @type:id 与 type+resourceId）
 * @param {Array} mentions
 */
export function buildMentionMap(mentions = []) {
  const map = new Map()
  for (const m of mentions) {
    if (!m) continue
    if (m.token) map.set(m.token, m)
    if (m.type && m.resourceId != null) {
      const computed = `@${m.type}:${m.resourceId}`
      if (!map.has(computed)) map.set(computed, m)
      const rid = String(m.resourceId)
      if (!map.has(`${m.type}:${rid}`)) map.set(`${m.type}:${rid}`, m)
    }
  }
  return map
}

/**
 * 按 token 或 type+resourceId 查找 mention 快照
 */
export function resolveMentionSnapshot(snapMap, snapshotMentions, token, type, resourceId) {
  if (token && snapMap.has(token)) return snapMap.get(token)
  const computed = `@${type}:${resourceId}`
  if (snapMap.has(computed)) return snapMap.get(computed)
  if (snapMap.has(`${type}:${resourceId}`)) return snapMap.get(`${type}:${resourceId}`)
  if (!Array.isArray(snapshotMentions)) return null
  return snapshotMentions.find(m =>
    m?.type === type && String(m.resourceId) === String(resourceId),
  ) || null
}

/**
 * 从 message.metadata 解析 mentions 快照
 * @param {string|object|null} metadata
 */
export function parseMentionsFromMetadata(metadata) {
  if (!metadata) return []
  try {
    let meta = metadata
    if (typeof meta === 'string') {
      meta = JSON.parse(meta)
      if (typeof meta === 'string') meta = JSON.parse(meta)
    }
    return Array.isArray(meta?.mentions) ? meta.mentions : []
  } catch {
    return []
  }
}

/**
 * 消息是否包含 mention token
 */
export function contentHasMentionTokens(content) {
  if (!content) return false
  return new RegExp(MENTION_TOKEN_RE.source).test(content)
}
