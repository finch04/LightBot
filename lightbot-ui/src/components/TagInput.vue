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
      :maxlength="20"
      show-count
      @keyup.enter="handleConfirm"
      @blur="handleConfirm"
    />
    <a-tag v-else-if="tags.length < 3" class="tag-add-btn" @click="showInput">
      <PlusOutlined /> 添加
    </a-tag>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const tags = ref([])
const inputVisible = ref(false)
const inputValue = ref('')
const inputRef = ref(null)

const MAX_TAG_LENGTH = 20
const MAX_TAG_COUNT = 3

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
  if (tags.value.length >= MAX_TAG_COUNT) return
  inputVisible.value = true
  nextTick(() => inputRef.value?.focus())
}

function handleConfirm() {
  const val = inputValue.value.trim()
  if (val) {
    if (val.length > MAX_TAG_LENGTH) {
      message.warning(`标签最多 ${MAX_TAG_LENGTH} 个字符`)
    } else if (tags.value.length >= MAX_TAG_COUNT) {
      message.warning(`最多添加 ${MAX_TAG_COUNT} 个标签`)
    } else if (!tags.value.includes(val)) {
      tags.value.push(val)
      syncToModel()
    }
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
