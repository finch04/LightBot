<template>
  <a-modal v-model:open="open" title="全局设置" :width="640" @ok="$emit('ok')">
    <a-form layout="vertical">
      <a-form-item label="上下文轮次（history_max_round）">
        <a-input-number v-model:value="config.history_config.history_max_round" :min="0" :max="50" style="width: 100%" />
      </a-form-item>
      <a-form-item label="启用对话历史">
        <a-switch v-model:checked="config.history_config.history_switch" />
      </a-form-item>
      <a-divider>会话变量（conversation_params）</a-divider>
      <div v-for="(param, idx) in config.variable_config.conversation_params" :key="idx" class="conv-param-row">
        <a-input v-model:value="param.key" placeholder="变量名" style="flex:1" />
        <a-input v-model:value="param.default_value" placeholder="默认值" style="flex:1" />
        <a-button type="text" danger @click="$emit('remove-param', idx)"><DeleteOutlined /></a-button>
      </div>
      <a-button type="dashed" block @click="$emit('add-param')"><PlusOutlined /> 添加会话变量</a-button>
    </a-form>
  </a-modal>
</template>

<script setup>
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'

defineProps({
  config: { type: Object, required: true },
})

defineEmits(['ok', 'add-param', 'remove-param'])

const open = defineModel('open', { type: Boolean, default: false })
</script>

<style scoped>
.conv-param-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
