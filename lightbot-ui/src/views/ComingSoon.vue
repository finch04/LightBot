<template>
  <div class="coming-soon">
    <div class="coming-soon-card">
      <div class="coming-soon-icon">{{ icon }}</div>
      <h2 class="coming-soon-title">{{ title }}</h2>
      <p class="coming-soon-desc">该功能正在规划中，敬请期待</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta?.title || route.name || '敬请期待')
const icon = computed(() => route.meta?.emoji || '🚧')
</script>

<style scoped>
.coming-soon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 360px;
  background: var(--color-canvas-soft);
  padding: 32px;
}
.coming-soon-card {
  text-align: center;
  max-width: 360px;
}
.coming-soon-icon {
  font-size: 40px;
  line-height: 1;
  margin-bottom: 16px;
}
.coming-soon-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-ink);
  letter-spacing: -0.02em;
}
.coming-soon-desc {
  margin: 0;
  font-size: 14px;
  color: var(--color-mute);
}
</style>
