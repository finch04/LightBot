<template>
  <div class="pg-describe-table">
    <div v-if="isPlainText" class="pdt-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <!-- 表名 -->
      <div class="pdt-header">
        <TableOutlined class="pdt-icon" />
        <span class="pdt-table-name">{{ data.table_name }}</span>
        <span class="pdt-col-count">{{ data.columns?.length || 0 }} 个字段</span>
      </div>

      <!-- 字段表格 -->
      <div v-if="data.columns?.length" class="pdt-table-wrap">
        <table class="pdt-table">
          <thead>
            <tr>
              <th>字段名</th>
              <th>类型</th>
              <th>可空</th>
              <th>默认值</th>
              <th>注释</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(col, i) in data.columns" :key="i">
              <td class="col-name">{{ col.column_name }}</td>
              <td class="col-type">{{ col.data_type }}</td>
              <td class="col-nullable">{{ col.is_nullable ? 'Y' : 'N' }}</td>
              <td class="col-default">{{ col.column_default || '-' }}</td>
              <td class="col-comment">{{ col.column_comment || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 索引 -->
      <div v-if="data.indexes?.length" class="pdt-indexes">
        <div class="pdt-index-title">索引</div>
        <div v-for="(idx, i) in data.indexes" :key="i" class="pdt-index-item">
          <span class="pdt-index-name">{{ idx.index_name }}</span>
          <span class="pdt-index-def">{{ idx.index_def }}</span>
        </div>
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

const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})

const isPlainText = computed(() => !data.value)
</script>

<style lang="less" scoped>
.pg-describe-table {
  .pdt-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .pdt-header {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
    .pdt-icon { color: var(--main-600); font-size: 13px; }
    .pdt-table-name { font-weight: 600; font-family: monospace; }
    .pdt-col-count { color: var(--gray-500); margin-left: auto; }
  }

  .pdt-table-wrap { overflow-x: auto; margin-bottom: 8px; }

  .pdt-table {
    width: 100%; border-collapse: collapse; font-size: 12px;
    th {
      text-align: left; padding: 6px 8px; background: var(--gray-25);
      border-bottom: 1px solid var(--gray-200); color: var(--gray-600);
      font-weight: 600; white-space: nowrap;
    }
    td {
      padding: 5px 8px; border-bottom: 1px solid var(--gray-100);
      color: var(--gray-700);
    }
    .col-name { font-family: monospace; font-weight: 500; }
    .col-type { font-family: monospace; color: var(--main-700); }
    .col-nullable { text-align: center; }
    .col-default { font-family: monospace; color: var(--gray-500); }
    .col-comment { color: var(--gray-500); }
  }

  .pdt-indexes {
    border: 1px solid var(--gray-150); border-radius: 6px; overflow: hidden;
    .pdt-index-title {
      padding: 6px 10px; background: var(--gray-25);
      font-size: 12px; font-weight: 600; color: var(--gray-600);
      border-bottom: 1px solid var(--gray-100);
    }
    .pdt-index-item {
      padding: 5px 10px; font-size: 12px;
      border-bottom: 1px solid var(--gray-100);
      &:last-child { border-bottom: none; }
      .pdt-index-name { font-weight: 500; color: var(--gray-700); margin-right: 8px; }
      .pdt-index-def { color: var(--gray-500); font-family: monospace; font-size: 11px; }
    }
  }
}
</style>
