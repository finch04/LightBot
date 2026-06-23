<template>
  <div>
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 表名 header -->
      <div style="display:flex;align-items:center;gap:6px;padding:8px 10px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:12px;font-weight:600;color:#1e40af;margin-bottom:8px;">
        <TableOutlined style="color:#2563eb;font-size:14px;" />
        <span style="font-family:monospace;">{{ data.table_name }}</span>
        <span style="margin-left:auto;color:#3b82f6;font-weight:500;">{{ data.columns?.length || 0 }} 个字段</span>
      </div>

      <!-- 字段表格 -->
      <div v-if="data.columns?.length" style="border:1px solid #93c5fd;border-radius:8px;overflow:hidden;margin-bottom:10px;background:#fff;">
        <div style="overflow-x:auto;&::-webkit-scrollbar{height:4px;}">
          <table style="width:100%;border-collapse:collapse;font-size:12px;">
            <thead>
              <tr>
                <th style="text-align:center;padding:7px 8px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;width:32px;">#</th>
                <th style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;">字段名</th>
                <th style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;">类型</th>
                <th style="text-align:center;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;">可空</th>
                <th style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;">默认值</th>
                <th style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;">注释</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(col, i) in data.columns" :key="i" :style="{ background: i % 2 === 0 ? '#fff' : '#f0f7ff' }">
                <td style="text-align:center;padding:6px 8px;border-bottom:1px solid #dbeafe;color:#9ca3af;font-size:10px;">{{ i + 1 }}</td>
                <td style="padding:6px 10px;border-bottom:1px solid #dbeafe;font-family:monospace;font-weight:500;color:#1f2937;">{{ col.column_name }}</td>
                <td style="padding:6px 10px;border-bottom:1px solid #dbeafe;font-family:monospace;color:#1d4ed8;white-space:nowrap;">{{ col.data_type }}</td>
                <td style="text-align:center;padding:6px 10px;border-bottom:1px solid #dbeafe;">
                  <span :style="{ color: col.is_nullable ? '#f59e0b' : '#9ca3af', fontWeight: 500 }">{{ col.is_nullable ? 'Y' : 'N' }}</span>
                </td>
                <td style="padding:6px 10px;border-bottom:1px solid #dbeafe;font-family:monospace;font-size:11px;color:#6b7280;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ col.column_default || '-' }}</td>
                <td style="padding:6px 10px;border-bottom:1px solid #dbeafe;color:#6b7280;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ col.column_comment || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 索引 -->
      <div v-if="data.indexes?.length" style="border:1px solid #93c5fd;border-radius:8px;overflow:hidden;background:#fff;">
        <div style="display:flex;align-items:center;gap:6px;padding:7px 10px;background:#dbeafe;font-size:12px;font-weight:600;color:#1e40af;border-bottom:1px solid #93c5fd;">
          <BranchesOutlined style="color:#2563eb;font-size:12px;" />
          <span>索引（{{ data.indexes.length }}）</span>
        </div>
        <div v-for="(idx, i) in data.indexes" :key="i" style="display:flex;align-items:baseline;gap:8px;padding:6px 10px;border-bottom:1px solid #dbeafe;font-size:12px;" :style="{ borderBottom: i === data.indexes.length - 1 ? 'none' : '1px solid #dbeafe' }">
          <span style="font-weight:500;color:#1d4ed8;font-family:monospace;white-space:nowrap;flex-shrink:0;">{{ idx.index_name }}</span>
          <code style="color:#6b7280;font-family:monospace;font-size:11px;background:#f0f7ff;padding:2px 6px;border-radius:4px;white-space:nowrap;overflow-x:auto;margin:0;">{{ idx.index_def }}</code>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { TableOutlined, BranchesOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
</script>
