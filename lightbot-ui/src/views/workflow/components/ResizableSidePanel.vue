<template>
  <aside
    class="resizable-side-panel"
    :class="{ 'is-resizing': isResizing, 'is-animated': animated && !isResizing }"
    :style="panelStyle"
  >
    <div
      class="resize-handle"
      role="separator"
      aria-orientation="vertical"
      aria-label="拖动调整侧栏宽度"
      title="拖动调整宽度"
      @mousedown="onResizeStart"
      @dblclick="resetWidth"
    />
    <div class="resizable-side-panel__content">
      <slot />
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useResizableSidePanel } from '../composables/useResizableSidePanel.js'

const props = defineProps({
  storageKey: { type: String, default: 'workflow-detail-panel-width' },
  defaultWidth: { type: Number, default: 480 },
  minWidth: { type: Number, default: 320 },
  maxWidth: { type: Number, default: 720 },
  maxViewportRatio: { type: Number, default: 0.55 },
  /** 非拖拽时宽度变化是否过渡动画 */
  animated: { type: Boolean, default: true },
})

const {
  width,
  isResizing,
  onResizeStart,
  resetWidth,
} = useResizableSidePanel({
  storageKey: props.storageKey,
  defaultWidth: props.defaultWidth,
  minWidth: props.minWidth,
  maxWidth: props.maxWidth,
  maxViewportRatio: props.maxViewportRatio,
})

const panelStyle = computed(() => ({
  width: `${width.value}px`,
  minWidth: `${props.minWidth}px`,
  maxWidth: `${props.maxWidth}px`,
}))
</script>

<style scoped>
.resizable-side-panel {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--color-canvas);
  border-left: 1px solid #e5e7eb;
  overflow: hidden;
}

.resizable-side-panel.is-animated {
  transition: width 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.resizable-side-panel.is-resizing {
  transition: none;
}

.resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8px;
  transform: translateX(-50%);
  cursor: col-resize;
  z-index: 30;
  touch-action: none;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  transform: translateX(-50%);
  border-radius: 1px;
  background: transparent;
  transition: background 0.18s ease, width 0.18s ease;
}

.resize-handle:hover::after,
.resizable-side-panel.is-resizing .resize-handle::after {
  width: 3px;
  background: rgba(99, 102, 241, 0.55);
}

.resizable-side-panel__content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.resizable-side-panel__content :deep(.config-panel) {
  width: 100%;
  min-width: 0;
  max-width: none;
  flex: 1;
  border-left: none;
}
</style>
