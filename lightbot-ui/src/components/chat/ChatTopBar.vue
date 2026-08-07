<template>
  <div class="chat-topbar">
    <div class="chat-topbar-left">
      <a-tooltip v-if="!titleEditing && titleEditable" title="修改标题">
        <div class="chat-topbar-title" @click="$emit('start-title-edit')">
          {{ sessionTitle || '新对话' }}
          <EditOutlined class="chat-topbar-title-icon" />
        </div>
      </a-tooltip>
      <div v-else-if="!titleEditing" class="chat-topbar-title chat-topbar-title-static">
        {{ sessionTitle || '新对话' }}
      </div>
      <div v-else class="chat-topbar-title-edit">
        <a-input
          ref="titleInputRef"
          :value="titleEditValue"
          size="small"
          placeholder="请输入会话标题（不超过 50 字）"
          :maxlength="50"
          @update:value="$emit('update:title-edit-value', $event)"
          @press-enter="$emit('confirm-title-edit')"
          @blur="$emit('confirm-title-edit')"
          @keydown.esc="$emit('cancel-title-edit')"
        />
        <a-tooltip title="取消">
          <button
            class="btn-title-cancel"
            @mousedown.prevent
            @click="$emit('cancel-title-edit')"
          >
            <CloseOutlined />
          </button>
        </a-tooltip>
      </div>
    </div>
    <div class="chat-topbar-right">
      <ChatSimRoleControl
        v-if="showSimRole"
        :user-id="simRoleUserId"
        :session-has-messages="sessionHasMessages"
        :disabled="simRoleDisabled"
        @change="$emit('sim-role-change', $event)"
        @request-new-session="$emit('sim-role-new-session')"
      />
      <a-tooltip v-if="showRuntimePanel" :title="runtimePanelOpen ? '关闭协作状态' : '打开协作状态'">
        <button class="btn-topbar-file" :class="{ 'is-active': runtimePanelOpen }" @click="$emit('toggle-runtime-panel')">
          <DashboardOutlined />
        </button>
      </a-tooltip>
      <a-tooltip v-if="showSubagentDrawer" title="子智能体状态">
        <a-badge :count="subagentRunningCount" :offset="[-2, 2]" :overflow-count="9">
          <button class="btn-topbar-file" @click="$emit('open-subagent-drawer')">
            <RobotOutlined />
          </button>
        </a-badge>
      </a-tooltip>
      <a-tooltip v-if="showFileDrawer" title="会话文件">
        <button class="btn-topbar-file" @click="$emit('open-file-drawer')">
          <FolderOpenOutlined />
        </button>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { EditOutlined, CloseOutlined, DashboardOutlined, FolderOpenOutlined, RobotOutlined } from '@ant-design/icons-vue'
import ChatSimRoleControl from './ChatSimRoleControl.vue'

defineProps({
  sessionTitle: { type: String, default: '' },
  titleEditing: { type: Boolean, default: false },
  titleEditValue: { type: String, default: '' },
  showFileDrawer: { type: Boolean, default: true },
  showRuntimePanel: { type: Boolean, default: false },
  runtimePanelOpen: { type: Boolean, default: false },
  showSubagentDrawer: { type: Boolean, default: false },
  subagentRunningCount: { type: Number, default: 0 },
  titleEditable: { type: Boolean, default: true },
  showSimRole: { type: Boolean, default: true },
  simRoleUserId: { type: [String, Number], default: null },
  sessionHasMessages: { type: Boolean, default: false },
  simRoleDisabled: { type: Boolean, default: false },
})

defineEmits([
  'start-title-edit',
  'confirm-title-edit',
  'cancel-title-edit',
  'update:title-edit-value',
  'open-file-drawer',
  'toggle-runtime-panel',
  'open-subagent-drawer',
  'sim-role-change',
  'sim-role-new-session',
])

const titleInputRef = ref(null)

defineExpose({
  titleInputRef,
})
</script>

<style scoped>
.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 32px;
  border-bottom: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
  flex-shrink: 0;
  gap: 16px;
  width: 100%;
}

.chat-topbar-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.chat-topbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}

.chat-topbar-title:hover {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
}

.chat-topbar-title-icon {
  font-size: 12px;
  color: var(--color-mute);
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
}

.chat-topbar-title:hover .chat-topbar-title-icon {
  opacity: 1;
}

.chat-topbar-title-static {
  cursor: default;
  padding: 4px 8px;
}

.chat-topbar-title-edit {
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.chat-topbar-title-edit :deep(input) {
  font-size: 15px;
  font-weight: 600;
}

.btn-title-cancel {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.btn-title-cancel:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-error);
  border-color: var(--color-hairline-strong);
}

.chat-topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-topbar-file {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  border: none;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: background 0.15s, color 0.15s;
}

.btn-topbar-file:hover {
  background: var(--color-hairline);
  color: var(--color-ink);
}

.btn-topbar-file.is-active {
  background: var(--color-link-bg-soft);
  color: var(--color-link-deep);
}

.btn-topbar-debug {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  border: none;
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: background 0.15s, color 0.15s;
}

.btn-topbar-debug:hover {
  background: rgba(245, 158, 11, 0.2);
  color: #b45309;
}
</style>
