<template>
  <a-select
    :value="value"
    @update:value="v => emit('update:value', v)"
    :mode="mode === 'multiple' ? 'multiple' : undefined"
    :placeholder="placeholder"
    :disabled="disabled"
    :allow-clear="allowClear"
    :max-tag-count="maxTagCount"
    :show-search="showSearch"
    :filter-option="filterOption"
    :loading="loading"
    option-label-prop="label"
    style="width: 100%"
  >
    <a-select-option
      v-for="a in filteredAgents"
      :key="a.id"
      :value="a.id"
      :label="a.name"
    >
      <EntitySelectOption type="agent" :name="a.name" />
    </a-select-option>
  </a-select>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import EntitySelectOption from './EntitySelectOption.vue'
import { getAgents } from '../api/agent'

const props = defineProps({
  value: { type: [String, Array], default: undefined },
  mode: { type: String, default: 'single' },
  placeholder: { type: String, default: '选择 Agent' },
  disabled: Boolean,
  allowClear: { type: Boolean, default: true },
  maxTagCount: { type: Number, default: 3 },
  showSearch: { type: Boolean, default: true },
})

const emit = defineEmits(['update:value', 'change'])

const loading = ref(false)
const agents = ref([])
const searchText = ref('')

function filterOption(input, option) {
  return option.label.toLowerCase().includes(input.toLowerCase())
}

async function loadAgents() {
  loading.value = true
  try {
    const res = await getAgents({ pageSize: 200 })
    agents.value = (res.data?.records || res.data || []).map(a => ({
      id: String(a.id),
      name: a.name,
    }))
  } catch {
    agents.value = []
  } finally {
    loading.value = false
  }
}

const filteredAgents = computed(() => {
  if (!searchText.value) return agents.value
  const kw = searchText.value.toLowerCase()
  return agents.value.filter(a => a.name.toLowerCase().includes(kw))
})

onMounted(loadAgents)
</script>
