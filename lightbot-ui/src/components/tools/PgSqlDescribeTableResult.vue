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
      <div v-if="data.columns?.length" style="margin-bottom:10px;">
        <a-table
          :columns="columns"
          :data-source="tableData"
          :pagination="false"
          size="small"
          :scroll="{ x: 600 }"
          row-key="ordinalPosition"
        />
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
import { computed, h } from 'vue'
import { TableOutlined, BranchesOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

const tableData = computed(() => {
  if (!data.value?.columns) return []
  return data.value.columns.map((col, i) => ({
    ordinalPosition: i + 1,
    columnName: col.column_name,
    dataType: col.data_type,
    isNullable: col.is_nullable ? 'YES' : 'NO',
    columnDefault: col.column_default || '-',
    comment: col.column_comment || '-',
  }))
})

const columns = [
  { title: '#', dataIndex: 'ordinalPosition', width: 48, align: 'center' },
  {
    title: '字段名', dataIndex: 'columnName', width: 140, ellipsis: true,
    customRender: ({ text }) => h('code', { style: 'font-family:monospace;font-weight:500;color:#1f2937;background:#f0f7ff;padding:1px 6px;border-radius:4px;font-size:12px;' }, text),
  },
  { title: '类型', dataIndex: 'dataType', width: 120 },
  {
    title: '可空', dataIndex: 'isNullable', width: 64, align: 'center',
    customRender: ({ text }) => text === 'YES'
      ? h('span', { style: 'color:#d97706;font-weight:500' }, 'Y')
      : h('span', { style: 'color:#a1a1aa' }, 'N'),
  },
  { title: '默认值', dataIndex: 'columnDefault', width: 160, ellipsis: true },
  { title: '注释', dataIndex: 'comment', ellipsis: true },
]
</script>
