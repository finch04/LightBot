<template>
  <div class="image-gen-result">
    <div v-if="isPlainText" class="ig-plain">
      <pre>{{ displayText }}</pre>
    </div>
    <template v-else>
      <div class="ig-image-wrap">
        <img :src="data.image_url" alt="生成图片" class="ig-image" @click="openPreview" />
      </div>
      <div class="ig-meta">
        <PictureOutlined class="ig-icon" />
        <span class="ig-prompt">{{ data.prompt }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'

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

function openPreview() {
  if (data.value?.image_url) {
    window.open(data.value.image_url, '_blank')
  }
}
</script>

<style lang="less" scoped>
.image-gen-result {
  .ig-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .ig-image-wrap {
    text-align: center; padding: 8px;
    .ig-image {
      max-width: 100%; max-height: 400px; border-radius: 8px;
      cursor: pointer; transition: transform 0.2s;
      &:hover { transform: scale(1.02); }
    }
  }

  .ig-meta {
    display: flex; align-items: flex-start; gap: 6px;
    padding: 6px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; color: var(--gray-600);
    .ig-icon { color: var(--main-600); font-size: 13px; margin-top: 1px; flex-shrink: 0; }
    .ig-prompt { flex: 1; line-height: 1.5; }
  }
}
</style>
