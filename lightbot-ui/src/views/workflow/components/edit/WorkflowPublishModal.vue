<template>
  <a-modal
    v-model:open="open"
    title="发布工作流"
    ok-text="确认发布"
    cancel-text="取消"
    class="publish-modal"
    :confirm-loading="saving"
    @ok="$emit('confirm')"
  >
    <div class="publish-modal-content">
      <p class="publish-modal-tip">选填发布说明（最多 50 字），可在版本历史中查看。</p>
      <a-textarea
        :value="description"
        class="publish-modal-textarea"
        :maxlength="50"
        show-count
        :rows="3"
        placeholder="例如：新增检索节点、调整 LLM 提示词"
        @update:value="val => $emit('update:description', val)"
      />
    </div>
  </a-modal>
</template>

<script setup>
defineProps({
  saving: Boolean,
  description: String,
})

defineEmits(['confirm', 'update:description'])

const open = defineModel('open', { type: Boolean, default: false })
</script>

<style scoped>
.publish-modal-content { padding-bottom: 8px; }
.publish-modal-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: #71717a;
}
.publish-modal-textarea { margin-bottom: 28px; }
</style>

<style>
.publish-modal .ant-modal-body { padding-bottom: 28px; }
.publish-modal .ant-modal-footer {
  margin-top: 4px;
  padding-top: 20px;
  border-top: 1px solid #f1f5f9;
}
</style>
