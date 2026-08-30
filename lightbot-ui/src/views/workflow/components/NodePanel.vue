<template>
  <div class="node-panel">
    <div class="node-panel__header">
      <span class="node-panel__title">{{ nodeTitle }}</span>
      <button type="button" class="node-panel__remove" @click="handleRemove">删除</button>
    </div>
    <div class="node-panel__body">
      <label class="node-panel__label">节点输入</label>
      <input
        v-model="inputValue"
        class="node-panel__input"
        placeholder="请输入节点输入"
        @change="handleChange($event.target.value)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  node: { type: Object, default: null }
})

const emit = defineEmits(['remove'])

const selectedNode = ref(null)
const nodeInput = ref(null)
const inputValue = ref('')

const nodeTitle = computed(
  () => selectedNode.value?.label || selectedNode.value?.type || '节点配置'
)

const handleChange = (value) => {
  const data = nodeInput.value
  // 节点数据可能为空（例如节点已被移除或正在卸载），避免空引用
  if (!data) return
  data.input = value
}

const handleRemove = () => {
  if (!selectedNode.value) return
  emit('remove', selectedNode.value)
}

// 节点切换/变更时同步输入面板状态，节点不存在时保持空引用
watch(
  () => props.node,
  (node) => {
    selectedNode.value = node || null
    nodeInput.value = node?.data ?? null
    inputValue.value = typeof nodeInput.value?.input === 'string' ? nodeInput.value.input : ''
  },
  { immediate: true }
)
</script>
