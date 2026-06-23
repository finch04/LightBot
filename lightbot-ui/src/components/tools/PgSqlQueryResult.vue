<template>
  <div>
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- SQL 代码块 -->
      <div style="display:flex;align-items:flex-start;gap:6px;padding:8px 10px;background:#1e1e1e;border-radius:8px;margin-bottom:8px;">
        <CodeOutlined style="color:#569cd6;font-size:13px;margin-top:1px;flex-shrink:0;" />
        <code style="font-size:12px;color:#d4d4d4;line-height:1.5;font-family:'Monaco','Menlo','Ubuntu Mono',monospace;white-space:pre-wrap;word-break:break-word;">{{ data.sql }}</code>
      </div>

      <!-- 结果表格 -->
      <div v-if="data.rows?.length" style="border:1px solid #93c5fd;border-radius:8px;overflow:hidden;margin-bottom:6px;background:#fff;">
        <div style="overflow-x:auto;max-height:240px;overflow-y:auto;">
          <table style="width:100%;border-collapse:collapse;font-size:12px;">
            <thead>
              <tr>
                <th style="text-align:center;padding:7px 8px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;width:32px;position:sticky;top:0;z-index:1;">#</th>
                <th v-for="(col, ci) in data.columns" :key="ci" style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;position:sticky;top:0;z-index:1;">{{ col }}</th>
                <th style="text-align:center;padding:7px 8px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;position:sticky;top:0;z-index:1;width:60px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in displayRows" :key="ri" :style="{ background: ri % 2 === 0 ? '#fff' : '#f0f7ff' }">
                <td style="text-align:center;padding:6px 8px;border-bottom:1px solid #dbeafe;color:#9ca3af;font-size:10px;">{{ ri + 1 }}</td>
                <td v-for="(val, vi) in row" :key="vi" :title="String(val ?? '')" style="padding:6px 10px;border-bottom:1px solid #dbeafe;color:#374151;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ val ?? '' }}</td>
                <td style="text-align:center;padding:6px 8px;border-bottom:1px solid #dbeafe;">
                  <button @click="openRowDetail(ri)" style="appearance:none;border:none;background:transparent;color:#2563eb;font-size:11px;cursor:pointer;padding:2px 6px;">详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else style="text-align:center;padding:16px;color:#9ca3af;font-size:12px;background:#f9fafb;border-radius:8px;margin-bottom:6px;">查询无结果</div>

      <!-- 统计行 -->
      <div style="display:flex;align-items:center;gap:8px;padding:6px 10px;background:#dbeafe;border-radius:8px;font-size:11px;color:#1e40af;">
        <span>共 {{ data.total_rows }} 行</span>
        <span v-if="data.has_more" style="color:#dc2626;">（仅显示前 {{ data.rows.length }} 行）</span>
        <span style="margin-left:auto;color:#3b82f6;">耗时 {{ data.elapsed_ms }}ms</span>
        <button @click="detailVisible = true" style="appearance:none;border:1px solid #93c5fd;border-radius:4px;background:#eff6ff;color:#1d4ed8;font-size:11px;padding:2px 10px;cursor:pointer;display:inline-flex;align-items:center;gap:4px;">
          <ExpandOutlined /> 查看全部
        </button>
      </div>
    </template>

    <!-- 全量详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="查询结果详情"
      :footer="null"
      width="900px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '16px' }"
    >
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px;padding:10px 12px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;">
        <code style="font-size:12px;color:#1e40af;font-family:'Monaco','Menlo',monospace;flex:1;white-space:pre-wrap;word-break:break-word;margin:0;">{{ data.sql }}</code>
        <span style="font-size:11px;color:#3b82f6;white-space:nowrap;">共 {{ data.total_rows }} 行 · {{ data.elapsed_ms }}ms</span>
      </div>
      <div style="border:1px solid #93c5fd;border-radius:8px;overflow:hidden;">
        <div style="overflow:auto;max-height:60vh;" class="modal-table-scroll">
          <table style="width:auto;border-collapse:collapse;font-size:12px;min-width:100%;">
            <thead>
              <tr>
                <th style="text-align:center;padding:7px 8px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;width:32px;position:sticky;top:0;left:0;z-index:2;">#</th>
                <th v-for="(col, ci) in data.columns" :key="ci" style="text-align:left;padding:7px 10px;background:#dbeafe;border-bottom:1px solid #93c5fd;color:#1e40af;font-weight:600;white-space:nowrap;position:sticky;top:0;z-index:1;">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in data.rows" :key="ri" :style="{ background: ri % 2 === 0 ? '#fff' : '#f0f7ff' }">
                <td style="text-align:center;padding:6px 8px;border-bottom:1px solid #dbeafe;color:#9ca3af;font-size:10px;white-space:nowrap;position:sticky;left:0;z-index:1;" :style="{ background: ri % 2 === 0 ? '#fff' : '#f0f7ff' }">{{ ri + 1 }}</td>
                <td v-for="(val, vi) in row" :key="vi" style="padding:6px 10px;border-bottom:1px solid #dbeafe;color:#374151;white-space:nowrap;max-width:360px;overflow:hidden;text-overflow:ellipsis;" :title="String(val ?? '')">{{ val ?? '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </a-modal>

    <!-- 单行详情弹窗 -->
    <a-modal
      v-model:open="rowDetailVisible"
      :title="'第 ' + (activeRowIndex + 1) + ' 行详情'"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto', padding: '16px' }"
    >
      <div v-if="activeRow" style="display:flex;flex-direction:column;gap:10px;">
        <div v-for="(val, ci) in activeRow" :key="ci" style="border:1px solid #93c5fd;border-radius:8px;overflow:hidden;">
          <div style="padding:6px 10px;background:#dbeafe;font-size:12px;font-weight:600;color:#1e40af;border-bottom:1px solid #93c5fd;">
            {{ data.columns?.[ci] || ('列 ' + (ci + 1)) }}
          </div>
          <div style="padding:10px;font-size:13px;line-height:1.7;color:#1f2937;white-space:pre-wrap;word-break:break-word;max-height:300px;overflow-y:auto;">
            {{ val ?? '' }}
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { CodeOutlined, ExpandOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const rawResult = computed(() => props.event.result || '')
const detailVisible = ref(false)
const rowDetailVisible = ref(false)
const activeRowIndex = ref(0)

const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

// 内嵌表格最多显示 5 行
const displayRows = computed(() => {
  if (!data.value?.rows) return []
  return data.value.rows.slice(0, 5)
})

// 当前行数据
const activeRow = computed(() => {
  if (!data.value?.rows || activeRowIndex.value >= data.value.rows.length) return null
  return data.value.rows[activeRowIndex.value]
})

function openRowDetail(ri) {
  activeRowIndex.value = ri
  rowDetailVisible.value = true
}
</script>

<style scoped>
.modal-table-scroll::-webkit-scrollbar { width: 5px; height: 5px; }
.modal-table-scroll::-webkit-scrollbar-thumb { background: #bfdbfe; border-radius: 4px; }
.modal-table-scroll::-webkit-scrollbar-thumb:hover { background: #93c5fd; }
.modal-table-scroll::-webkit-scrollbar-track { background: transparent; }
</style>
