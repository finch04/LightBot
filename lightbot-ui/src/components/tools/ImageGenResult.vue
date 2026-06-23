<template>
  <div>
    <!-- 错误/纯文本 -->
    <div v-if="isPlainText" style="display:flex;align-items:flex-start;gap:8px;padding:10px 12px;border-radius:8px;font-size:12px;line-height:1.6;"
      :style="isError
        ? 'background:#fef2f2;border:1px solid #fca5a5;color:#991b1b'
        : 'background:#fafafa;border:1px solid #e5e7eb;color:#374151'">
      <CloseCircleOutlined v-if="isError" style="color:#ef4444;font-size:14px;margin-top:1px;flex-shrink:0;" />
      <InfoCircleOutlined v-else style="color:#6b7280;font-size:14px;margin-top:1px;flex-shrink:0;" />
      <pre style="margin:0;white-space:pre-wrap;word-break:break-word;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 图片 -->
      <div style="text-align:center;padding:8px;">
        <img :src="data.image_url" alt="生成图片"
          style="max-width:360px;max-height:260px;border-radius:8px;cursor:pointer;transition:transform 0.2s;object-fit:contain;"
          @click="previewOpen = true"
          onmouseover="this.style.transform='scale(1.02)'"
          onmouseout="this.style.transform='scale(1)'" />
      </div>

      <!-- Prompt 信息 -->
      <div style="display:flex;align-items:flex-start;gap:6px;padding:8px 12px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:12px;color:#1e40af;margin-top:6px;">
        <PictureOutlined style="color:#2563eb;font-size:13px;margin-top:1px;flex-shrink:0;" />
        <span style="flex:1;line-height:1.5;">{{ data.prompt }}</span>
      </div>

      <ChatMediaPreview v-model:open="previewOpen" :src="data.image_url" media-type="image" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { PictureOutlined, CloseCircleOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import ChatMediaPreview from '@/components/ChatMediaPreview.vue'

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

const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

const isError = computed(() => {
  if (!isPlainText.value) return false
  const text = displayText.value || ''
  return text.includes('失败') || text.includes('错误') || text.includes('异常') || text.includes('未配置')
})

const previewOpen = ref(false)
</script>
