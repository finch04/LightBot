<template>
  <div class="ask-insight">
    <div v-if="parsed?.error" class="ask-insight__error">{{ parsed.error }}</div>
    <template v-else-if="parsed">
      <div v-if="parsed.summary" class="ask-insight__summary">{{ parsed.summary }}</div>
      <div v-if="parsed.assumptions?.length" class="ask-insight__assumptions">
        <span v-for="(a, i) in parsed.assumptions" :key="i" class="chip">{{ a }}</span>
      </div>
      <div v-if="kpiValue != null" class="ask-insight__kpi">{{ kpiValue }}</div>
      <a-table
        v-if="tableRows.length"
        size="small"
        :pagination="tableRows.length > 10 ? { pageSize: 10 } : false"
        :columns="tableColumns"
        :data-source="tableRows"
        :scroll="{ x: true }"
        row-key="__idx"
      />
      <div v-if="parsed.followups?.length" class="ask-insight__followups">
        <span class="label">还可以问：</span>
        <span v-for="(f, i) in parsed.followups" :key="i" class="chip chip--follow">{{ f }}</span>
      </div>
      <a-collapse v-if="parsed.explain" ghost class="ask-insight__explain">
        <a-collapse-panel key="explain" header="查询说明 / SQL">
          <pre>{{ explainText }}</pre>
        </a-collapse-panel>
      </a-collapse>
    </template>
    <pre v-else class="ask-insight__raw">{{ raw }}</pre>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: [String, Object], default: '' },
})

const raw = computed(() => {
  if (props.result == null) return ''
  return typeof props.result === 'string' ? props.result : JSON.stringify(props.result, null, 2)
})

const parsed = computed(() => {
  try {
    const v = typeof props.result === 'string' ? JSON.parse(props.result) : props.result
    return v && typeof v === 'object' ? v : null
  } catch {
    return null
  }
})

const tableColumns = computed(() => {
  const cols = parsed.value?.table?.columns || []
  return cols.map((c) => ({ title: c, dataIndex: c, key: c, ellipsis: true }))
})

const tableRows = computed(() => {
  const rows = parsed.value?.table?.rows || []
  return rows.map((r, i) => ({ ...r, __idx: i }))
})

const kpiValue = computed(() => {
  if (parsed.value?.chart?.type !== 'kpi') return null
  const field = parsed.value.chart.valueField
  const row = parsed.value.table?.rows?.[0]
  if (!row || !field) return null
  return row[field]
})

const explainText = computed(() => {
  try {
    return JSON.stringify(parsed.value?.explain || {}, null, 2)
  } catch {
    return ''
  }
})
</script>

<style scoped>
.ask-insight {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
}
.ask-insight__summary {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}
.ask-insight__assumptions,
.ask-insight__followups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.chip {
  background: var(--color-canvas-soft, #f5f5f5);
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #666);
}
.chip--follow {
  border: 1px dashed var(--color-border, #ddd);
}
.ask-insight__kpi {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.ask-insight__explain pre,
.ask-insight__raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
.ask-insight__error {
  color: var(--color-danger, #cf1322);
}
.label {
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
</style>
