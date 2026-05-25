<template>
  <a-modal
    :open="open"
    :title="example?.title || '节点说明'"
    :width="640"
    :footer="null"
    destroy-on-close
    @update:open="v => emit('update:open', v)"
  >
    <template v-if="example">
      <p class="node-example-summary">{{ example.summary }}</p>
      <div class="node-example-label">示例配置（JSON）</div>
      <pre class="node-example-pre code-block-scroll code-block-scroll--dark">{{ formattedJson }}</pre>
      <a-button v-if="allowApply" type="primary" size="small" :disabled="!canApply" @click="applyExample">
        应用到当前节点
      </a-button>
      <p v-if="!allowApply" class="node-example-readonly-tip">
        该节点需选择已有资源（如知识库、工具、模型），仅作配置说明参考，不支持一键应用示例。
      </p>
      <p v-else-if="readonly" class="node-example-readonly-tip">历史版本预览下不可应用</p>
    </template>
    <a-empty v-else description="暂无该节点类型的示例说明" />
  </a-modal>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  example: { type: Object, default: null },
  readonly: { type: Boolean, default: false },
  /** 是否展示「应用到当前节点」（资源类节点应为 false） */
  allowApply: { type: Boolean, default: true },
})

const emit = defineEmits(['update:open', 'apply'])

const formattedJson = computed(() => {
  if (!props.example?.example) return '{}'
  return JSON.stringify(props.example.example, null, 2)
})

const canApply = computed(() => props.allowApply && !props.readonly && !!props.example?.example)

function applyExample() {
  if (!canApply.value) return
  emit('apply', props.example.example)
  emit('update:open', false)
}
</script>

<style scoped>
.node-example-summary {
  color: #4b5563;
  margin-bottom: 12px;
  line-height: 1.6;
}
.node-example-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}
.node-example-pre {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  max-height: 360px;
  overflow: auto;
  margin-bottom: 12px;
}
.node-example-readonly-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 8px;
}
</style>
