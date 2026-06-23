<template>
  <div>
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- header -->
      <div style="display:flex;align-items:center;gap:6px;padding:8px 10px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:12px;font-weight:600;color:#1e40af;margin-bottom:8px;">
        <TableOutlined style="color:#2563eb;font-size:14px;" />
        <span>共 {{ data.total }} 张表</span>
      </div>

      <!-- 表名标签 -->
      <div style="display:flex;flex-wrap:wrap;gap:6px;">
        <span v-for="(table, i) in data.tables" :key="i" style="display:inline-flex;align-items:center;gap:4px;padding:4px 12px;background:#eff6ff;border:1px solid #93c5fd;border-radius:6px;font-size:12px;font-family:monospace;color:#1d4ed8;">
          <span style="font-size:10px;color:#60a5fa;background:#dbeafe;border-radius:3px;padding:0 4px;min-width:16px;text-align:center;">{{ i + 1 }}</span>{{ table }}
        </span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { TableOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
</script>
