<template>
  <component :is="iconComponent" v-if="iconComponent" />
</template>

<script setup>
import { computed } from 'vue'
import { getNodeMeta } from '../nodeMeta'

const props = defineProps({
  type: { type: String, required: true }
})

const iconComponent = computed(() => getNodeMeta(props.type).icon)
</script>
