<template>
  <div class="pg-list-tables">
    <div v-if="isPlainText" class="plt-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <div class="plt-summary">
        <TableOutlined class="plt-icon" />
        <span>共 {{ data.total }} 张表</span>
      </div>
      <div class="plt-tables">
        <div v-for="(table, i) in data.tables" :key="i" class="plt-table-item">
          <span class="plt-index">{{ i + 1 }}</span>
          <span class="plt-name">{{ table }}</span>
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
.pg-list-tables {
  .plt-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .plt-summary {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-700);
    margin-bottom: 8px;
    .plt-icon { color: var(--main-600); font-size: 13px; }
  }

  .plt-tables { display: flex; flex-wrap: wrap; gap: 4px; }

  .plt-table-item {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 4px 10px; border: 1px solid var(--gray-150);
    border-radius: 6px; font-size: 12px;
    transition: border-color 0.2s;
    &:hover { border-color: var(--gray-300); }

    .plt-index {
      font-size: 10px; color: var(--gray-500);
      background: var(--gray-100); border-radius: 3px;
      padding: 0 4px; min-width: 16px; text-align: center;
    }
    .plt-name { color: var(--gray-700); font-family: monospace; }
  }
}
</style>
