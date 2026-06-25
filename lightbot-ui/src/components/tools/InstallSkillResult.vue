<template>
  <div class="install-skill-result">
    <!-- 纯文本降级 -->
    <div v-if="isPlainText" class="isr-plain">
      <pre>{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 错误 -->
      <div v-if="data._error" class="isr-card isr-card-error">
        <div class="isr-header isr-header-error">
          <CloseCircleOutlined class="isr-header-icon" />
          <span>技能安装失败</span>
        </div>
        <pre class="isr-error-msg">{{ data.message || '未知错误' }}</pre>
      </div>

      <!-- 安装结果 -->
      <div v-else class="isr-card">
        <!-- 总结 -->
        <div class="isr-header" :class="{ 'isr-header-success': data.success, 'isr-header-error': !data.success }">
          <CheckCircleOutlined v-if="data.success" class="isr-header-icon" />
          <CloseCircleOutlined v-else class="isr-header-icon" />
          <span>{{ data.message || (data.success ? '安装成功' : '安装失败') }}</span>
        </div>

        <!-- 已安装列表 -->
        <div v-if="data.installed?.length" class="isr-list">
          <div v-for="(skill, i) in data.installed" :key="i" class="isr-skill-item">
            <div class="isr-skill-icon">
              <ThunderboltOutlined />
            </div>
            <div class="isr-skill-info">
              <div class="isr-skill-name">
                {{ skill.displayName || skill.slug }}
                <span class="isr-skill-slug">{{ skill.slug }}</span>
              </div>
            </div>
            <div v-if="skill.activated" class="isr-badge isr-badge-active">已激活</div>
            <div v-else class="isr-badge isr-badge-inactive">未激活</div>
          </div>
        </div>

        <!-- 失败列表 -->
        <div v-if="data.errors?.length" class="isr-errors">
          <div v-for="(err, i) in data.errors" :key="i" class="isr-error-item">
            <WarningOutlined /> {{ err }}
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  CheckCircleOutlined, CloseCircleOutlined,
  ThunderboltOutlined, WarningOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
</script>

<style lang="less" scoped>
.install-skill-result {
  font-size: 12px;

  .isr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  // ── 卡片容器 ──
  .isr-card {
    border: 1px solid #c4b5fd;
    border-left: 3px solid #8b5cf6;
    border-radius: 8px;
    overflow: hidden;
    background: #f5f3ff;
  }
  .isr-card-error {
    border-color: #fca5a5;
    border-left-color: #ef4444;
    background: #fef2f2;
  }

  // ── Header ──
  .isr-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px;
    font-size: 12px; font-weight: 600;
    border-bottom: 1px solid #c4b5fd;
  }
  .isr-header-success {
    background: #dcfce7; border-bottom-color: #bbf7d0; color: #166534;
    .isr-header-icon { color: #16a34a; }
  }
  .isr-header-error {
    background: #fee2e2; border-bottom-color: #fca5a5; color: #991b1b;
    .isr-header-icon { color: #dc2626; }
  }
  .isr-header-icon { font-size: 14px; flex-shrink: 0; }

  // ── 技能列表 ──
  .isr-list { padding: 4px 0; }

  .isr-skill-item {
    display: flex; align-items: center; gap: 8px;
    padding: 8px 12px;
    &:hover { background: #ede9fe; }
  }

  .isr-skill-icon {
    width: 28px; height: 28px; flex-shrink: 0;
    border-radius: 6px;
    background: #ede9fe; border: 1px solid #c4b5fd;
    display: flex; align-items: center; justify-content: center;
    color: #7c3aed; font-size: 14px;
  }

  .isr-skill-info {
    flex: 1; min-width: 0;
  }
  .isr-skill-name {
    font-size: 13px; font-weight: 500; color: #1e1b4b;
    display: flex; align-items: center; gap: 6px;
  }
  .isr-skill-slug {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 11px; color: #6d28d9;
    background: #ede9fe; border: 1px solid #c4b5fd;
    border-radius: 4px; padding: 0 5px;
  }

  // ── 激活状态标签 ──
  .isr-badge {
    font-size: 11px; font-weight: 600; padding: 1px 8px;
    border-radius: 4px; flex-shrink: 0;
  }
  .isr-badge-active {
    color: #16a34a; background: #dcfce7; border: 1px solid #bbf7d0;
  }
  .isr-badge-inactive {
    color: #d97706; background: #fffbeb; border: 1px solid #fde68a;
  }

  // ── 错误内容 ──
  .isr-error-msg {
    margin: 0; padding: 10px 12px;
    color: #b91c1c; font-size: 12px; line-height: 1.6;
    white-space: pre-wrap; word-break: break-word;
  }

  // ── 错误列表 ──
  .isr-errors {
    padding: 8px 12px;
    border-top: 1px dashed #fca5a5;
    background: #fff5f5;
  }
  .isr-error-item {
    font-size: 11px; color: #b91c1c; padding: 2px 0;
    display: flex; align-items: center; gap: 4px;
  }
}
</style>
