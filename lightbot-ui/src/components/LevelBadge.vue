<template>
  <div class="level-badge" :style="{ width: `${size}px`, height: `${size}px` }">
    <img
      v-if="animFile"
      class="level-badge-img"
      :src="animFile"
      :style="{ width: `${size}px`, height: `${size}px` }"
      alt=""
      draggable="false"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  level: { type: Number, default: null },
  size: { type: Number, default: 80 },
})

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
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}

.level-badge-img {
  display: block;
  object-fit: cover;
  object-position: 16.6% 24.9%;
  border-radius: 4px;
  pointer-events: none;
}
</style>
