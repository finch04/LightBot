<template>
  <div class="sim-role">
    <a-dropdown :trigger="['click']" placement="bottomRight">
      <button type="button" class="sim-role__trigger" :class="{ 'is-active': !!activeRole }">
        <UserCog :size="15" />
        <span class="sim-role__label">{{ triggerLabel }}</span>
        <span v-if="activeRole" class="sim-role__kind">{{ roleKindLabel(activeRole) }}</span>
        <ChevronDown :size="14" class="sim-role__chevron" />
      </button>
      <template #overlay>
        <div class="sim-role__menu">
          <div class="sim-role__menu-head">模拟角色（调试）</div>
          <button
            type="button"
            :class="['sim-role__item', { active: !activeRoleId }]"
            @click="onSelect(null)"
          >
            <span>不使用模拟角色</span>
            <span class="sim-role__item-meta">回落 debug_user</span>
          </button>
          <button
            v-for="role in roles"
            :key="role.id"
            type="button"
            :class="['sim-role__item', { active: role.id === activeRoleId }]"
            @click="onSelect(role.id)"
          >
            <span class="sim-role__item-main">
              <b>{{ role.name }}</b>
              <span class="sim-role__kind sim-role__kind--sm">{{ roleKindLabel(role) }}</span>
            </span>
            <span class="sim-role__item-meta">
              {{ summarize(role) }}
            </span>
          </button>
          <div class="sim-role__menu-foot">
            <button type="button" class="btn-link" @click="openManage">管理角色…</button>
          </div>
        </div>
      </template>
    </a-dropdown>

    <a-modal
      v-model:open="manageOpen"
      title="管理模拟角色"
      :width="720"
      :footer="null"
      destroy-on-close
      @cancel="closeManage"
    >
      <p class="sim-role__hint">
        预设将写入本机存储（结构化 JSON，后续可迁到服务端）。
        切换角色时若当前会话已有消息，将新建对话。隔离主键绑定后不可中途更换。
      </p>
      <div class="sim-role__manage-actions">
        <button type="button" class="lb-btn lb-btn--primary lb-btn--sm" @click="startCreate">
          新增角色
        </button>
      </div>
      <div class="sim-role__list">
        <div v-for="role in roles" :key="role.id" class="sim-role__card">
          <div class="sim-role__card-top">
            <div>
              <strong>{{ role.name }}</strong>
              <span class="sim-role__kind">{{ roleKindLabel(role) }}</span>
            </div>
            <div class="sim-role__card-ops">
              <button type="button" class="btn-link" @click="startEdit(role)">编辑</button>
              <a-popconfirm title="确认删除该角色？" ok-text="删除" cancel-text="取消" @confirm="removeRole(role.id)">
                <button type="button" class="btn-link btn-link--danger">删除</button>
              </a-popconfirm>
            </div>
          </div>
          <div class="sim-role__card-meta">
            <code v-if="role.externalUserId">user={{ role.externalUserId }}</code>
            <code v-if="role.regionId">region={{ role.regionId }}</code>
            <code v-if="role.enterpriseId">ent={{ role.enterpriseId }}</code>
            <span v-if="!role.externalUserId && !role.regionId && !role.enterpriseId">未配置隔离字段</span>
          </div>
        </div>
        <div v-if="!roles.length" class="sim-role__empty">暂无角色，请新增。</div>
      </div>

      <a-modal
        v-model:open="formOpen"
        :title="editingId ? '编辑角色' : '新增角色'"
        :width="520"
        ok-text="保存"
        cancel-text="取消"
        destroy-on-close
        @ok="saveForm"
      >
        <a-form layout="vertical" class="sim-role__form">
          <a-form-item label="名称" required>
            <a-input v-model:value="form.name" placeholder="如：成都行业" :maxlength="40" />
          </a-form-item>
          <a-form-item label="用户 ID">
            <a-input v-model:value="form.externalUserId" placeholder="模拟 externalUserId" :maxlength="128" />
          </a-form-item>
          <a-form-item label="地区 ID">
            <a-input v-model:value="form.regionId" placeholder="如 510100" :maxlength="32" />
          </a-form-item>
          <a-form-item label="企业 ID">
            <a-input v-model:value="form.enterpriseId" placeholder="有值则为企业视角" :maxlength="128" />
          </a-form-item>
          <a-form-item label="profile JSON">
            <a-textarea
              v-model:value="form.profileText"
              :rows="4"
              placeholder='可选，如 {"name":"张三"}'
            />
          </a-form-item>
        </a-form>
      </a-modal>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ChevronDown, UserCog } from 'lucide-vue-next'
import {
  createRole,
  loadSimCallerRolesStore,
  roleKindLabel,
  roleToCallerContext,
  saveSimCallerRolesStore,
  touchRole,
} from '../../utils/simCallerRoles'

const props = defineProps({
  userId: { type: [String, Number], default: null },
  /** 当前会话是否已有消息（有则切换角色需新建对话） */
  sessionHasMessages: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'request-new-session'])

const store = ref(loadSimCallerRolesStore(props.userId))
const manageOpen = ref(false)
const formOpen = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '',
  externalUserId: '',
  regionId: '',
  enterpriseId: '',
  profileText: '{}',
})

const roles = computed(() => store.value.roles || [])
const activeRoleId = computed(() => store.value.activeRoleId)
const activeRole = computed(() => roles.value.find((r) => r.id === activeRoleId.value) || null)

const triggerLabel = computed(() => {
  if (!activeRole.value) return '模拟角色'
  return activeRole.value.name
})

watch(
  () => props.userId,
  (uid) => {
    store.value = loadSimCallerRolesStore(uid)
    emitChange()
  },
)

function persist() {
  store.value = saveSimCallerRolesStore(props.userId, store.value)
}

function emitChange() {
  emit('change', {
    activeRoleId: activeRoleId.value,
    role: activeRole.value,
    callerContext: roleToCallerContext(activeRole.value),
  })
}

function summarize(role) {
  const parts = []
  if (role.externalUserId) parts.push(role.externalUserId)
  if (role.regionId) parts.push(role.regionId)
  if (role.enterpriseId) parts.push(role.enterpriseId)
  return parts.join(' · ') || '—'
}

function applyActive(roleId) {
  store.value = {
    ...store.value,
    activeRoleId: roleId,
  }
  persist()
  emitChange()
}

function onSelect(roleId) {
  if (props.disabled) return
  if (roleId === activeRoleId.value) return
  if (props.sessionHasMessages) {
    Modal.confirm({
      title: '切换模拟角色需新建对话',
      content: '当前会话已绑定隔离身份，不能中途更换。确认后将进入新对话并应用所选角色。',
      okText: '新建对话',
      cancelText: '取消',
      onOk: () => {
        applyActive(roleId)
        emit('request-new-session')
      },
    })
    return
  }
  applyActive(roleId)
  message.success(roleId ? '已切换模拟角色' : '已关闭模拟角色')
}

function openManage() {
  manageOpen.value = true
}

function closeManage() {
  manageOpen.value = false
  formOpen.value = false
}

function startCreate() {
  editingId.value = null
  form.name = ''
  form.externalUserId = ''
  form.regionId = ''
  form.enterpriseId = ''
  form.profileText = '{}'
  formOpen.value = true
}

function startEdit(role) {
  editingId.value = role.id
  form.name = role.name
  form.externalUserId = role.externalUserId || ''
  form.regionId = role.regionId || ''
  form.enterpriseId = role.enterpriseId || ''
  form.profileText = JSON.stringify(role.profile || {}, null, 2)
  formOpen.value = true
}

function parseProfileText() {
  const text = (form.profileText || '').trim() || '{}'
  try {
    const obj = JSON.parse(text)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
      throw new Error('profile 须为 JSON 对象')
    }
    return obj
  } catch (e) {
    message.error(e?.message || 'profile JSON 无效')
    return null
  }
}

function saveForm() {
  if (!form.name.trim()) {
    message.warning('请填写角色名称')
    return Promise.reject()
  }
  const profile = parseProfileText()
  if (profile == null) return Promise.reject()

  const patch = {
    name: form.name.trim(),
    externalUserId: form.externalUserId.trim(),
    regionId: form.regionId.trim(),
    enterpriseId: form.enterpriseId.trim(),
    profile,
  }
  if (editingId.value) {
    store.value = {
      ...store.value,
      roles: store.value.roles.map((r) =>
        r.id === editingId.value ? touchRole(r, patch) : r,
      ),
    }
  } else {
    store.value = {
      ...store.value,
      roles: [...store.value.roles, createRole(patch)],
    }
  }
  persist()
  emitChange()
  formOpen.value = false
  message.success('已保存')
}

function removeRole(id) {
  store.value = {
    ...store.value,
    roles: store.value.roles.filter((r) => r.id !== id),
    activeRoleId: store.value.activeRoleId === id ? null : store.value.activeRoleId,
  }
  persist()
  emitChange()
  message.success('已删除')
}

// 初始同步一次给父组件
emitChange()

defineExpose({
  getCallerContext: () => roleToCallerContext(activeRole.value),
  activeRole,
})
</script>

<style scoped>
.sim-role__trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 10px 0 12px;
  border: none;
  border-radius: var(--radius-md, 8px);
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  font-size: 13px;
  max-width: 240px;
  transition: background 0.15s, color 0.15s;
}
.sim-role__trigger:hover,
.sim-role__trigger.is-active {
  background: var(--color-link-bg-soft);
  color: var(--color-link-deep);
}
.sim-role__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.sim-role__kind {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--color-canvas);
  color: var(--color-ink);
  border: 1px solid var(--color-hairline);
}
.sim-role__kind--sm {
  margin-left: 6px;
}
.sim-role__chevron {
  flex-shrink: 0;
  opacity: 0.7;
}

.sim-role__menu {
  min-width: 280px;
  max-width: 360px;
  padding: 8px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.sim-role__menu-head {
  font-size: 12px;
  color: var(--color-mute);
  padding: 4px 8px 8px;
}
.sim-role__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--color-ink);
}
.sim-role__item:hover {
  background: var(--color-canvas-soft-2);
}
.sim-role__item.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  box-shadow: inset 2px 0 0 var(--color-ink);
}
.sim-role__item-main {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.sim-role__item-main b {
  font-weight: 600;
}
.sim-role__item-meta {
  font-size: 11px;
  color: var(--color-mute);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.sim-role__menu-foot {
  border-top: 1px solid var(--color-hairline);
  margin-top: 6px;
  padding: 8px 8px 4px;
}

.sim-role__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}
.sim-role__manage-actions {
  margin-bottom: 12px;
}
.sim-role__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
}
.sim-role__card {
  padding: 12px 14px;
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  background: var(--color-canvas-soft);
}
.sim-role__card-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 6px;
}
.sim-role__card-top strong {
  margin-right: 8px;
  font-size: 14px;
}
.sim-role__card-ops {
  display: flex;
  gap: 10px;
}
.sim-role__card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--color-mute);
}
.sim-role__card-meta code {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
}
.sim-role__empty {
  padding: 24px;
  text-align: center;
  color: var(--color-mute);
  font-size: 13px;
}
.sim-role__form :deep(.ant-form-item) {
  margin-bottom: 12px;
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}
.btn-link--danger {
  color: var(--color-error);
}
</style>
