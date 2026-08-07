/**
 * 控制台「模拟角色」本地存储（一期 localStorage；结构对齐后续落库）。
 *
 * 存储形态：
 * {
 *   version: 1,
 *   activeRoleId: string | null,
 *   roles: [{ id, name, externalUserId, regionId, enterpriseId, profile, createdAt, updatedAt }]
 * }
 */

export const SIM_CALLER_ROLES_VERSION = 1
export const SIM_CALLER_ROLES_STORAGE_PREFIX = 'lightbot.simCallerRoles.v1'

function storageKey(userId) {
  const uid = userId != null && String(userId).trim() ? String(userId).trim() : 'anonymous'
  return `${SIM_CALLER_ROLES_STORAGE_PREFIX}.${uid}`
}

function nowIso() {
  return new Date().toISOString()
}

function newId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `role_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

/** 默认示例角色（仅在空库时写入） */
export function defaultRoles() {
  const t = nowIso()
  return [
    {
      id: newId(),
      name: '成都行业',
      externalUserId: 'sim_industry_cd',
      regionId: '510100',
      enterpriseId: '',
      profile: {},
      createdAt: t,
      updatedAt: t,
    },
    {
      id: newId(),
      name: '示例企业',
      externalUserId: 'sim_ent_001',
      regionId: '',
      enterpriseId: 'ent_001',
      profile: {},
      createdAt: t,
      updatedAt: t,
    },
  ]
}

export function emptyStore() {
  return {
    version: SIM_CALLER_ROLES_VERSION,
    activeRoleId: null,
    roles: [],
  }
}

function normalizeProfile(raw) {
  if (raw == null) return {}
  if (typeof raw === 'object' && !Array.isArray(raw)) return { ...raw }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
    } catch {
      // ignore
    }
  }
  return {}
}

export function normalizeRole(raw) {
  if (!raw || typeof raw !== 'object') return null
  const id = String(raw.id || '').trim() || newId()
  const name = String(raw.name || '').trim() || '未命名角色'
  return {
    id,
    name,
    externalUserId: String(raw.externalUserId || '').trim(),
    regionId: String(raw.regionId || '').trim(),
    enterpriseId: String(raw.enterpriseId || '').trim(),
    profile: normalizeProfile(raw.profile),
    createdAt: raw.createdAt || nowIso(),
    updatedAt: raw.updatedAt || nowIso(),
  }
}

export function normalizeStore(raw) {
  const base = emptyStore()
  if (!raw || typeof raw !== 'object') return base
  const roles = Array.isArray(raw.roles)
    ? raw.roles.map(normalizeRole).filter(Boolean)
    : []
  let activeRoleId = raw.activeRoleId != null ? String(raw.activeRoleId) : null
  if (activeRoleId && !roles.some((r) => r.id === activeRoleId)) {
    activeRoleId = null
  }
  return {
    version: SIM_CALLER_ROLES_VERSION,
    activeRoleId,
    roles,
  }
}

export function loadSimCallerRolesStore(userId) {
  try {
    const text = localStorage.getItem(storageKey(userId))
    if (!text) {
      const seeded = {
        version: SIM_CALLER_ROLES_VERSION,
        activeRoleId: null,
        roles: defaultRoles(),
      }
      saveSimCallerRolesStore(userId, seeded)
      return seeded
    }
    return normalizeStore(JSON.parse(text))
  } catch {
    return emptyStore()
  }
}

export function saveSimCallerRolesStore(userId, store) {
  const normalized = normalizeStore(store)
  localStorage.setItem(storageKey(userId), JSON.stringify(normalized))
  return normalized
}

/** 转为请求 callerContext；全空则 null */
export function roleToCallerContext(role) {
  if (!role) return null
  const ctx = {}
  if (role.externalUserId) ctx.externalUserId = role.externalUserId
  if (role.regionId) ctx.regionId = role.regionId
  if (role.enterpriseId) ctx.enterpriseId = role.enterpriseId
  const profile = normalizeProfile(role.profile)
  if (Object.keys(profile).length) ctx.profile = profile
  return Object.keys(ctx).length ? ctx : null
}

export function roleKindLabel(role) {
  if (!role) return '未启用'
  if (role.enterpriseId) return '企业'
  if (role.regionId) return '行业'
  if (role.externalUserId) return '用户'
  return '空'
}

export function createRole(partial = {}) {
  const t = nowIso()
  return normalizeRole({
    id: newId(),
    name: partial.name || '新角色',
    externalUserId: partial.externalUserId || '',
    regionId: partial.regionId || '',
    enterpriseId: partial.enterpriseId || '',
    profile: partial.profile || {},
    createdAt: t,
    updatedAt: t,
  })
}

export function touchRole(role, patch = {}) {
  return normalizeRole({
    ...role,
    ...patch,
    id: role.id,
    createdAt: role.createdAt,
    updatedAt: nowIso(),
  })
}
