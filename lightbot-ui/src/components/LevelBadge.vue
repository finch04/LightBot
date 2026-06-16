<template>
  <img
    v-if="level != null && level >= 1 && animFile"
    class="level-badge"
    :src="animFile"
    :style="{ width: `${animW}px`, height: `${animH}px` }"
    alt=""
    draggable="false"
  />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  level: { type: Number, default: null },
  size: { type: Number, default: 80 },
})

const animH = computed(() => props.size)
const animW = computed(() => props.size)

const animMap = {
  1: '/baijin_level.webp',
  2: '/bojin_level.webp',
  3: '/zuanshi_level.webp',
  4: '/zhizun_level.webp',
  5: '/vip_level.webp',
}

const animFile = computed(() => animMap[props.level] || null)
</script>

<style scoped>
.level-badge {
  display: block;
  flex-shrink: 0;
  object-fit: cover;
  object-position: 16.6% 24.9%;
  border-radius: 4px;
  pointer-events: none;
}

</style>
