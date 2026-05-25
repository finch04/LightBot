<template>
  <div class="workflow-toolbar">
    <button class="btn-back" @click="$emit('back')">
      <ArrowLeftOutlined /> 返回
    </button>
    <a-tag v-if="workflowStatus === 'draft'" color="orange" class="publish-tag">未发布</a-tag>
    <a-tag v-else-if="workflowStatus === 'published_editing'" color="gold" class="publish-tag">已发布编辑中</a-tag>
    <a-tag v-else color="green" class="publish-tag">已发布 v{{ publishedVersion }}</a-tag>
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
      <span v-else-if="validationErrors.length === 0 && nodeCount >= 2" class="status-valid">
        <CheckCircleOutlined /> 配置完整
      </span>
      <span v-else class="status-empty">请添加节点并配置</span>
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
      <template v-if="isVersionPreview">
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
  canUndo: Boolean,
  saving: Boolean,
  autoSaving: Boolean,
  lastAutoSaveTime: [Date, String, Number, null],
  getNodeTitleById: { type: Function, required: true },
  formatAutoSaveTime: { type: Function, required: true },
})

defineEmits([
  'back', 'format-layout', 'validate', 'back-to-draft', 'open-version',
  'undo', 'open-global-config', 'open-test', 'save-draft', 'open-publish',
])
</script>

<style scoped>
.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.publish-tag { flex-shrink: 0; }
.btn-back {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
}
.btn-back:hover { background: #f9fafb; }
.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
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
}
.status-valid { color: #22c55e; font-size: 13px; }
.status-error { color: #ef4444; font-size: 13px; }
.status-error.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.status-error.clickable:hover { background: #fef2f2; }
.status-empty { color: #9ca3af; font-size: 13px; }
.error-dropdown {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 400px;
}
.error-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  border-bottom: 1px solid #e5e7eb;
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
  background: #fef2f2;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}
.error-node { color: #7c3aed; font-weight: 600; }
.error-field {
  color: #6b7280;
  background: #e5e7eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.error-msg { color: #dc2626; flex: 1; }
.toolbar-actions { display: flex; gap: 8px; }
.btn-validate { color: #6366f1; }
.auto-save-hint { font-size: 12px; color: #94a3b8; white-space: nowrap; }
.auto-save-hint.saving { color: #6366f1; }
</style>
