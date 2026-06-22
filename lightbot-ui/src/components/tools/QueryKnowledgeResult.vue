<template>
  <div class="query-knowledge-result">
    <!-- 纯文本降级（错误信息） -->
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;max-height:300px;overflow-y:auto;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- QA 优先命中 -->
      <div v-if="data.qa_answer" style="border:1px solid #93c5fd;border-left:3px solid #3b82f6;border-radius:8px;background:#eff6ff;overflow:hidden;margin-bottom:8px;">
        <div style="display:flex;align-items:center;gap:6px;padding:8px 10px;border-bottom:1px solid #93c5f6;background:#dbeafe;font-size:12px;font-weight:600;color:#1e40af;">
          <QuestionCircleOutlined style="color:#2563eb;" />
          <span>命中高匹配问答对</span>
        </div>
        <div style="padding:10px;font-size:13px;line-height:1.6;color:#374151;white-space:pre-wrap;word-break:break-word;">{{ data.qa_answer }}</div>
      </div>

      <!-- 有结果：摘要 + 卡片列表 -->
      <div v-if="data.results?.length" style="margin-bottom:8px;">
        <div style="display:flex;align-items:center;gap:8px;padding:10px 12px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:13px;font-weight:500;color:#1e40af;margin-bottom:10px;">
          <SearchOutlined style="color:#2563eb;font-size:14px;" />
          <span>找到 {{ data.total }} 条相关内容</span>
          <button @click="detailVisible = true" style="margin-left:auto;appearance:none;border:none;border-radius:6px;background:#3b82f6;color:#fff;font-size:12px;padding:5px 14px;cursor:pointer;display:inline-flex;align-items:center;gap:6px;font-weight:500;">
            <EyeOutlined />
            <span>查看详情</span>
          </button>
        </div>

        <div style="display:flex;flex-direction:column;gap:6px;">
          <div
            v-for="(item, i) in visibleItems"
            :key="i"
            style="border:1px solid #93c5fd;border-left:3px solid #3b82f6;border-radius:8px;overflow:hidden;cursor:pointer;transition:border-color 0.2s,box-shadow 0.2s;"
            @click="openItemDetail(item)"
          >
            <div style="display:flex;align-items:center;gap:8px;padding:8px 10px;background:#eff6ff;">
              <span v-if="item.result_type === 'qa_pair'" style="display:inline-flex;align-items:center;gap:4px;font-size:11px;padding:2px 8px;border-radius:4px;white-space:nowrap;font-weight:500;flex-shrink:0;background:#dbeafe;color:#1d4ed8;">
                <QuestionCircleOutlined /> 问答对
              </span>
              <span v-else style="display:inline-flex;align-items:center;gap:4px;font-size:11px;padding:2px 8px;border-radius:4px;white-space:nowrap;font-weight:500;flex-shrink:0;background:#dbeafe;color:#1d4ed8;">
                <FileTextOutlined /> 文档
              </span>
              <span style="flex:1;font-size:12px;font-weight:500;color:#374151;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                {{ item.result_type === 'qa_pair' ? item.question : item.document_name }}
              </span>
              <span v-if="typeof item.score === 'number'" style="font-size:11px;font-weight:600;color:#1d4ed8;background:#eff6ff;border:1px solid #bfdbfe;border-radius:4px;padding:1px 6px;white-space:nowrap;flex-shrink:0;">
                {{ (item.score * 100).toFixed(0) }}%
              </span>
            </div>
            <div style="padding:6px 12px;font-size:12px;color:#6b7280;line-height:1.5;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
              {{ getPreview(getItemContent(item), 120) }}
            </div>
          </div>

          <div v-if="data.results.length > displayLimit" style="margin-top:4px;text-align:center;">
            <button @click="displayLimit = data.results.length" style="appearance:none;border:1px dashed #d1d5db;border-radius:6px;background:transparent;color:#6b7280;font-size:12px;padding:6px 12px;cursor:pointer;width:100%;">
              查看剩余 {{ data.results.length - displayLimit }} 条结果
            </button>
          </div>
        </div>
      </div>

      <!-- 无结果：样式化提示 -->
      <div v-if="!data.qa_answer && !data.results?.length" style="display:flex;align-items:center;gap:8px;justify-content:center;padding:16px 12px;font-size:13px;color:#6b7280;border:1px solid #d1d5db;border-radius:8px;background:#f9fafb;">
        <SearchOutlined style="color:#9ca3af;font-size:16px;" />
        <span>未在知识库中找到与问题相关的内容</span>
      </div>
    </template>

    <!-- 单条详情弹窗 -->
    <a-modal
      v-model:open="itemDetailVisible"
      :title="itemDetailTitle"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '70vh', overflow: 'auto' }"
      :maskClosable="false"
    >
      <div v-if="activeItem">
        <div style="display:flex;align-items:center;gap:10px;margin-bottom:16px;padding-bottom:12px;border-bottom:2px solid #dbeafe;">
          <span v-if="activeItem.result_type === 'qa_pair'" style="display:inline-flex;align-items:center;gap:4px;font-size:12px;padding:4px 10px;border-radius:4px;font-weight:500;background:#dbeafe;color:#1d4ed8;">
            <QuestionCircleOutlined /> 问答对
          </span>
          <span v-else style="display:inline-flex;align-items:center;gap:4px;font-size:12px;padding:4px 10px;border-radius:4px;font-weight:500;background:#dbeafe;color:#1d4ed8;">
            <FileTextOutlined /> 文档片段
          </span>
          <span v-if="typeof activeItem.score === 'number'" style="font-size:12px;font-weight:600;color:#1d4ed8;background:#dbeafe;padding:3px 10px;border-radius:12px;">
            匹配度 {{ (activeItem.score * 100).toFixed(1) }}%
          </span>
          <span v-if="activeItem.document_name" style="font-size:12px;color:#6b7280;display:inline-flex;align-items:center;gap:4px;">
            <FileTextOutlined /> {{ activeItem.document_name }}
          </span>
        </div>
        <!-- QA -->
        <template v-if="activeItem.result_type === 'qa_pair'">
          <div style="margin-bottom:16px;">
            <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">问题</div>
            <div style="font-size:14px;line-height:1.7;color:#1f2937;">{{ activeItem.question }}</div>
          </div>
          <div style="margin-bottom:16px;">
            <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">答案</div>
            <div style="font-size:14px;line-height:1.7;color:#1f2937;white-space:pre-wrap;word-break:break-word;padding:12px;background:#f9fafb;border:2px solid #bfdbfe;border-radius:6px;max-height:400px;overflow-y:auto;">{{ activeItem.answer }}</div>
          </div>
        </template>
        <!-- Chunk -->
        <template v-else>
          <div style="margin-bottom:16px;">
            <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">内容</div>
            <div style="font-size:14px;line-height:1.7;color:#1f2937;white-space:pre-wrap;word-break:break-word;padding:12px;background:#f9fafb;border:2px solid #bfdbfe;border-radius:6px;max-height:400px;overflow-y:auto;">{{ activeItem.content }}</div>
          </div>
        </template>
      </div>
    </a-modal>

    <!-- 全量详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="知识库查询结果详情"
      :footer="null"
      width="800px"
      :bodyStyle="{ maxHeight: '75vh', overflow: 'auto', padding: '20px' }"
      :maskClosable="false"
    >
      <div>
        <!-- 统计栏 -->
        <div style="display:flex;gap:16px;margin-bottom:20px;padding:16px;background:#eff6ff;border-radius:8px;border:1px solid #bfdbfe;">
          <div style="display:flex;flex-direction:column;align-items:center;gap:4px;flex:1;">
            <span style="font-size:24px;font-weight:700;color:#2563eb;">{{ data.total || 0 }}</span>
            <span style="font-size:12px;color:#3b82f6;">总结果数</span>
          </div>
          <div style="display:flex;flex-direction:column;align-items:center;gap:4px;flex:1;">
            <span style="font-size:24px;font-weight:700;color:#2563eb;">{{ chunkCount }}</span>
            <span style="font-size:12px;color:#3b82f6;">文档片段</span>
          </div>
          <div style="display:flex;flex-direction:column;align-items:center;gap:4px;flex:1;">
            <span style="font-size:24px;font-weight:700;color:#2563eb;">{{ qaCount }}</span>
            <span style="font-size:12px;color:#3b82f6;">问答对</span>
          </div>
        </div>

        <!-- QA 高匹配 -->
        <div v-if="data.qa_answer" style="margin-bottom:20px;">
          <div style="display:flex;align-items:center;gap:8px;font-size:14px;font-weight:600;color:#1d4ed8;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #bfdbfe;">
            <QuestionCircleOutlined />
            <span>高匹配问答对</span>
          </div>
          <div style="border:1px solid #93c5fd;border-left:4px solid #3b82f6;border-radius:8px;background:#eff6ff;overflow:hidden;">
            <div style="padding:16px;font-size:14px;line-height:1.8;color:#1f2937;white-space:pre-wrap;word-break:break-word;">{{ data.qa_answer }}</div>
          </div>
        </div>

        <!-- 详细结果 -->
        <div v-if="data.results?.length">
          <div style="display:flex;align-items:center;gap:8px;font-size:14px;font-weight:600;color:#1d4ed8;margin-bottom:12px;padding-bottom:8px;border-bottom:2px solid #bfdbfe;">
            <FileTextOutlined />
            <span>详细结果</span>
          </div>
          <div style="display:flex;flex-direction:column;gap:14px;">
            <div
              v-for="(item, i) in data.results"
              :key="i"
              style="border:2px solid #93c5fd;border-left:4px solid #3b82f6;border-radius:10px;overflow:hidden;background:#fff;"
            >
              <div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;background:#eff6ff;border-bottom:1px solid #dbeafe;">
                <span v-if="item.result_type === 'qa_pair'" style="display:inline-flex;align-items:center;gap:6px;font-size:12px;padding:4px 10px;border-radius:4px;font-weight:500;background:#dbeafe;color:#1d4ed8;">
                  <QuestionCircleOutlined /> 问答对
                </span>
                <span v-else style="display:inline-flex;align-items:center;gap:6px;font-size:12px;padding:4px 10px;border-radius:4px;font-weight:500;background:#dbeafe;color:#1d4ed8;">
                  <FileTextOutlined /> 文档片段
                </span>
                <span v-if="typeof item.score === 'number'" style="font-size:12px;font-weight:600;color:#1d4ed8;background:#dbeafe;padding:4px 10px;border-radius:12px;">
                  匹配度 {{ (item.score * 100).toFixed(1) }}%
                </span>
              </div>
              <div style="padding:16px;">
                <template v-if="item.result_type === 'qa_pair'">
                  <div style="margin-bottom:14px;">
                    <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">问题</div>
                    <div style="font-size:14px;line-height:1.7;color:#1f2937;">{{ item.question }}</div>
                  </div>
                  <div>
                    <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">答案</div>
                    <div style="font-size:14px;line-height:1.7;color:#1f2937;white-space:pre-wrap;word-break:break-word;padding:12px;background:#f9fafb;border:1px solid #d4e4fb;border-radius:6px;max-height:300px;overflow-y:auto;">{{ item.answer }}</div>
                  </div>
                </template>
                <template v-else>
                  <div style="margin-bottom:14px;">
                    <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">文档</div>
                    <div style="font-size:14px;line-height:1.7;color:#1f2937;">{{ item.document_name }}</div>
                  </div>
                  <div>
                    <div style="font-size:12px;font-weight:600;color:#2563eb;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.5px;">内容</div>
                    <div style="font-size:14px;line-height:1.7;color:#1f2937;white-space:pre-wrap;word-break:break-word;padding:12px;background:#f9fafb;border:1px solid #d4e4fb;border-radius:6px;max-height:300px;overflow-y:auto;">{{ item.content }}</div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  SearchOutlined,
  QuestionCircleOutlined,
  FileTextOutlined,
  EyeOutlined
} from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true }
})

const displayLimit = ref(5)
const detailVisible = ref(false)
const itemDetailVisible = ref(false)
const activeItem = ref(null)

const rawResult = computed(() => props.event.result || '')
const data = computed(() => {
  try { return JSON.parse(rawResult.value) } catch { return null }
})
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)
const visibleItems = computed(() => (data.value?.results || []).slice(0, displayLimit.value))
const chunkCount = computed(() => (data.value?.results || []).filter(r => r.result_type === 'chunk').length)
const qaCount = computed(() => (data.value?.results || []).filter(r => r.result_type === 'qa_pair').length)

const itemDetailTitle = computed(() => {
  if (!activeItem.value) return ''
  if (activeItem.value.result_type === 'qa_pair') return '问答对详情'
  return activeItem.value.document_name || '文档片段详情'
})

function getItemContent(item) {
  return item.result_type === 'qa_pair' ? (item.answer || item.content) : item.content
}

function getPreview(text, maxLen) {
  if (!text) return ''
  return text.length <= maxLen ? text : text.substring(0, maxLen) + '...'
}

function openItemDetail(item) {
  activeItem.value = item
  itemDetailVisible.value = true
}
</script>
