<template>
  <div class="tag-input">
    <a-tag
      v-for="tag in tags"
      :key="tag"
      closable
      @close="remove(tag)"
    >
      {{ tag }}
    </a-tag>
    <a-input
      v-if="inputVisible"
      ref="inputRef"
      v-model:value="inputValue"
      size="small"
      class="tag-input-field"
      @keyup.enter="handleConfirm"
      @blur="handleConfirm"
    />
    <a-tag v-else class="tag-add-btn" @click="showInput">
      <PlusOutlined /> 添加
    </a-tag>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const tags = ref([])
const inputVisible = ref(false)
const inputValue = ref('')
const inputRef = ref(null)

watch(() => props.modelValue, (val) => {
  const newTags = val ? val.split(',').map(s => s.trim()).filter(Boolean) : []
  if (JSON.stringify(newTags) !== JSON.stringify(tags.value)) {
    tags.value = newTags
  }
}, { immediate: true })

function syncToModel() {
  emit('update:modelValue', tags.value.join(','))
}

function remove(tag) {
  tags.value = tags.value.filter(t => t !== tag)
  syncToModel()
}

function showInput() {
  inputVisible.value = true
  nextTick(() => inputRef.value?.focus())
}

function handleConfirm() {
  if (inputValue.value.trim() && !tags.value.includes(inputValue.value.trim())) {
    tags.value.push(inputValue.value.trim())
    syncToModel()
  }
  inputVisible.value = false
  inputValue.value = ''
}
</script>

<style scoped>
.tag-input {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.tag-input-field {
  width: 100px;
}
.tag-add-btn {
  cursor: pointer;
  border-style: dashed;
  background: #fff;
}
.tag-add-btn:hover {
  border-color: #0070f3;
  color: #0070f3;
}
</style>
