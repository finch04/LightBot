<template>
  <div class="ask-user-result">
    <div v-if="isPlainText" class="au-plain">
      <pre>{{ rawResult }}</pre>
    </div>
    <template v-else>
      <div class="au-question">
        <QuestionCircleOutlined class="au-icon" />
        <span>{{ data.question }}</span>
      </div>
      <div v-if="data.options?.length" class="au-options">
        <div v-for="(opt, i) in data.options" :key="i" class="au-option">
          <span class="au-option-index">{{ i + 1 }}</span>
          <span class="au-option-text">{{ opt }}</span>
        </div>
      </div>
      <div v-if="data.is_open_ended" class="au-hint">
        等待用户输入回答...
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

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
.ask-user-result {
  .au-plain pre {
    margin: 0; padding: 8px 10px; background: var(--gray-25);
    border-radius: 6px; font-size: 12px; line-height: 1.5;
    color: var(--gray-700); white-space: pre-wrap; word-break: break-word;
  }

  .au-question {
    display: flex; align-items: flex-start; gap: 6px;
    padding: 10px; font-size: 13px; color: var(--gray-700);
    line-height: 1.5;
    .au-icon { color: var(--main-600); font-size: 15px; margin-top: 1px; flex-shrink: 0; }
  }

  .au-options {
    display: flex; flex-direction: column; gap: 4px;
    padding: 0 10px 10px;

    .au-option {
      display: flex; align-items: center; gap: 8px;
      padding: 6px 10px; border: 1px solid var(--gray-150);
      border-radius: 6px; transition: border-color 0.2s;
      &:hover { border-color: var(--main-300); }

      .au-option-index {
        font-size: 11px; color: var(--main-700);
        background: var(--main-50); border-radius: 4px;
        padding: 0 5px; min-width: 20px; text-align: center;
      }
      .au-option-text { font-size: 12px; color: var(--gray-700); }
    }
  }

  .au-hint {
    padding: 6px 10px; font-size: 11px; color: var(--gray-500);
    font-style: italic;
  }
}
</style>
