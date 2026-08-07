<template>
  <a-layout class="debug-lab-layout">
    <slot />
  </a-layout>
</template>

<style scoped>
.debug-lab-layout {
  min-height: var(--app-content-height, 100vh);
  background: var(--color-canvas);
}
</style>
