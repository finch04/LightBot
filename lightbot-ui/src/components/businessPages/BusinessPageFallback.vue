<template>
  <div class="bpf-card">
    <div class="bpf-header">
      <AppstoreOutlined />
      <span>{{ title }}</span>
    </div>
    <p class="bpf-desc">
      <code>{{ pageType || 'unknown' }}</code> 缺少可渲染内容：请在能力中心配置
      <code>pageHtml</code>（H5 HTML）或外链 <code>pageUrl</code>，
      或由上层 <code>registerBusinessPageComponent</code> 注入组件。
    </p>
    <pre class="bpf-json">{{ prettyPayload }}</pre>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  payload: { type: Object, required: true },
})

const pageType = computed(() => props.payload?.pageType || '')
const title = computed(() => props.payload?.title || props.payload?.displayName || '业务办理页')
const prettyPayload = computed(() => {
  try {
    return JSON.stringify(props.payload, null, 2)
  } catch {
    return String(props.payload)
  }
})
</script>

<style scoped>
.bpf-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  background: var(--bg-elevated, #fff);
  padding: 14px 16px;
}
.bpf-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}
.bpf-desc {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  line-height: 1.5;
}
.bpf-desc code {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-muted, #f3f4f6);
}
.bpf-json {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--bg-muted, #f8fafc);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
