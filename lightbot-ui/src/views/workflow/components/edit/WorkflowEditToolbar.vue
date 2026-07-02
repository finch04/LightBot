<template>
  <div class="workflow-toolbar">
    <button class="btn-back" @click="$emit('back')">
      <ArrowLeftOutlined /> 返回
    </button>
    <a-tag v-if="workflowStatus === 'draft'" color="orange" class="publish-tag">{{ getStatusLabel('draft') }}</a-tag>
    <a-tag v-else-if="workflowStatus === 'published_editing'" color="gold" class="publish-tag">{{ getStatusLabel('published_editing') }}</a-tag>
    <a-tag v-else color="green" class="publish-tag">{{ getStatusLabel('published') }}</a-tag>
    <h1 class="workflow-title">{{ agentName || '工作流配置' }}</h1>
    <div class="toolbar-status">
      <a-dropdown v-if="validationErrors.length > 0" :trigger="['click']">
        <span class="status-error clickable">
          <ExclamationCircleOutlined /> {{ validationErrors.length }} 个配置错误
          <DownOutlined style="margin-left: 4px; font-size: 10px;" />
        </span>
        <template #overlay>
          <div class="error-dropdown">
            <div class="error-header">配置错误详情</div>
            <div class="error-list">
              <div v-for="err in validationErrors" :key="err.nodeId + err.field" class="error-item">
                <span class="error-node">{{ getNodeTitleById(err.nodeId) || '工作流全局' }}</span>
                <span class="error-field">{{ err.field }}</span>
                <span class="error-msg">{{ err.message }}</span>
              </div>
            </div>
          </div>
        </template>
      </a-dropdown>
      <span v-else-if="validationErrors.length === 0 && nodeCount >= 2" class="status-valid status-badge">
        <CheckCircleOutlined /> 配置完整
      </span>
      <span v-else class="status-empty status-badge">请添加节点并配置</span>
      <WorkflowTooltip title="格式化" placement="bottom">
        <a-button type="text" size="small" class="btn-validate" :disabled="isVersionPreview || nodeCount < 2" @click="$emit('format-layout')">
          <ApartmentOutlined />
        </a-button>
      </WorkflowTooltip>
      <WorkflowTooltip title="验证配置" placement="bottom">
        <a-button type="text" size="small" class="btn-validate" @click="$emit('validate')">
          <AuditOutlined />
        </a-button>
      </WorkflowTooltip>
      <span v-if="autoSaving" class="auto-save-hint saving">保存中...</span>
      <span v-else-if="lastAutoSaveTime" class="auto-save-hint">{{ formatAutoSaveTime(lastAutoSaveTime) }} 已自动保存</span>
    </div>
    <div class="toolbar-actions">
      <template v-if="viewingTestHistory">
        <a-button type="default" @click="$emit('open-test')">测试运行</a-button>
        <a-button type="primary" danger @click="$emit('exit-test-history')">退出历史快照</a-button>
      </template>
      <template v-else-if="isVersionPreview">
        <a-button type="primary" @click="$emit('back-to-draft')">
          <RollbackOutlined /> 回到当前版本
        </a-button>
        <a-button type="default" @click="$emit('open-version')">版本管理</a-button>
      </template>
      <template v-else>
        <WorkflowTooltip title="撤回 (Ctrl+Z)" placement="bottom">
          <a-button v-if="canUndo" type="default" @click="$emit('undo')">
            <UndoOutlined /> 撤回
          </a-button>
        </WorkflowTooltip>
        <a-button type="default" @click="$emit('open-global-config')">全局设置</a-button>
        <a-button type="default" @click="$emit('open-test')">测试运行</a-button>
        <a-button type="default" @click="$emit('open-version')">版本管理</a-button>
        <a-button type="default" :disabled="saving" :loading="saving" @click="$emit('save-draft')">
          <SaveOutlined /> 暂存
        </a-button>
        <a-button type="primary" :disabled="saving" @click="$emit('open-publish')">发布</a-button>
      </template>
    </div>
  </div>
</template>

<script setup>
import {
  ArrowLeftOutlined, SaveOutlined, CheckCircleOutlined, ExclamationCircleOutlined,
  DownOutlined, UndoOutlined, AuditOutlined, RollbackOutlined, ApartmentOutlined,
} from '@ant-design/icons-vue'
import WorkflowTooltip from '../WorkflowTooltip.vue'

defineProps({
  agentName: String,
  workflowStatus: String,
  publishedVersion: [String, Number],
  validationErrors: { type: Array, default: () => [] },
  nodeCount: { type: Number, default: 0 },
  isVersionPreview: Boolean,
  viewingTestHistory: Boolean,
  canUndo: Boolean,
  saving: Boolean,
  autoSaving: Boolean,
  lastAutoSaveTime: [Date, String, Number, null],
  getNodeTitleById: { type: Function, required: true },
  formatAutoSaveTime: { type: Function, required: true },
  getStatusLabel: { type: Function, required: true },
})

defineEmits([
  'back', 'format-layout', 'validate', 'back-to-draft', 'open-version',
  'undo', 'open-global-config', 'open-test', 'save-draft', 'open-publish',
  'exit-test-history',
])
</script>

<style scoped>
.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
}
.publish-tag { flex-shrink: 0; }
.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text-dark);
}
.btn-back:hover { background: var(--color-canvas-soft); }
.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}
.toolbar-status {
  flex: 1;
  text-align: center;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  min-height: 32px;
}
.status-valid { color: #22c55e; font-size: 13px; }
.status-error { color: #ef4444; font-size: 13px; }
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 120px;
  justify-content: center;
  white-space: nowrap;
}
.status-error.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.status-error.clickable:hover { background: var(--color-error-bg); }
.status-empty { color: var(--color-mute); font-size: 13px; }
.error-dropdown {
  background: var(--color-canvas);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 400px;
}
.error-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  border-bottom: 1px solid var(--color-hairline);
}
.error-list {
  padding: 8px;
  max-height: 300px;
  overflow-y: auto;
}
.error-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-error-bg);
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}
.error-node { color: #7c3aed; font-weight: 600; }
.error-field {
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.error-msg { color: #dc2626; flex: 1; }
.toolbar-actions { display: flex; gap: 8px; }
.btn-validate { color: var(--color-link); }
.auto-save-hint { font-size: 12px; color: var(--color-mute); white-space: nowrap; min-width: 128px; text-align: center; }
.auto-save-hint.saving { color: var(--color-link); }
</style>
