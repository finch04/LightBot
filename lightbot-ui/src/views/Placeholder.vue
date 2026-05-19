<template>
  <div class="placeholder">
    <div class="placeholder-icon">🚧</div>
    <h2>{{ route.name }}</h2>
    <p>该功能正在开发中，敬请期待...</p>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #fafafa;
}
.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.placeholder h2 {
  font-size: 20px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.placeholder p {
  font-size: 14px;
  color: #71717a;
}
</style>
