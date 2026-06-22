<template>
  <div class="pg-query-result">
    <div v-if="isPlainText" class="pqr-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <!-- SQL 代码块 -->
      <div class="pqr-sql">
        <CodeOutlined class="pqr-icon" />
        <code>{{ data.sql }}</code>
      </div>

      <!-- 结果表格 -->
      <div v-if="data.rows?.length" class="pqr-table-wrap">
        <table class="pqr-table">
          <thead>
            <tr>
              <th v-for="(col, i) in data.columns" :key="i">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in data.rows" :key="ri">
              <td v-for="(val, ci) in row" :key="ci">{{ val }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 统计行 -->
      <div class="pqr-stats">
        <span>共 {{ data.total_rows }} 行</span>
        <span v-if="data.has_more" class="pqr-more-hint">（仅显示前 {{ data.rows.length }} 行）</span>
        <span class="pqr-elapsed">耗时 {{ data.elapsed_ms }}ms</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CodeOutlined } from '@ant-design/icons-vue'

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
.pg-query-result {
  .pqr-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .pqr-sql {
    display: flex; align-items: flex-start; gap: 6px;
    padding: 8px 10px; background: #1e1e1e; border-radius: 6px;
    margin-bottom: 8px;
    .pqr-icon { color: #569cd6; font-size: 13px; margin-top: 1px; flex-shrink: 0; }
    code {
      font-size: 12px; color: #d4d4d4; line-height: 1.5;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      white-space: pre-wrap; word-break: break-word;
    }
  }

  .pqr-table-wrap { overflow-x: auto; margin-bottom: 6px; }

  .pqr-table {
    width: 100%; border-collapse: collapse; font-size: 12px;
    th {
      text-align: left; padding: 5px 8px; background: var(--gray-25);
      border-bottom: 1px solid var(--gray-200); color: var(--gray-600);
      font-weight: 600; white-space: nowrap;
    }
    td {
      padding: 4px 8px; border-bottom: 1px solid var(--gray-100);
      color: var(--gray-700); max-width: 200px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    tr:hover td { background: var(--gray-25); }
  }

  .pqr-stats {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 11px; color: var(--gray-500);
    .pqr-more-hint { color: var(--main-600); }
    .pqr-elapsed { margin-left: auto; }
  }
}
</style>
