<template>
  <a-config-provider :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>

<style>
/* DESIGN.md 全局变量 */
:root {
  /* Brand Colors */
  --color-primary: #171717;
  --color-on-primary: #ffffff;
  --color-ink: #171717;
  --color-body: #4d4d4d;
  --color-mute: #888888;
  --color-hairline: #ebebeb;
  --color-hairline-strong: #a1a1a1;
  --color-canvas: #ffffff;
  --color-canvas-soft: #fafafa;
  --color-canvas-soft-2: #f5f5f5;
  --color-link: #0070f3;
  --color-link-deep: #0761d1;
  --color-link-bg-soft: #d3e5ff;
  --color-success: #0070f3;
  --color-error: #ee0000;
  --color-error-soft: #f7d4d6;
  --color-error-deep: #c50000;
  --color-warning: #f5a623;
  --color-warning-soft: #ffefcf;
  --color-warning-deep: #ab570a;

  /* Typography */
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;

  /* Spacing */
  --space-xxs: 4px;
  --space-xs: 8px;
  --space-sm: 12px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  /* Rounded */
  --radius-xs: 4px;
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-pill: 100px;

  /* Elevation */
  --shadow-1: 0 0 0 1px rgba(0,0,0,0.08);
  --shadow-2: 0px 1px 1px rgba(0,0,0,0.02), 0px 2px 2px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
  --shadow-3: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 8px -8px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
  --shadow-4: 0px 2px 2px rgba(0,0,0,0.04), 0px 8px 16px -4px rgba(0,0,0,0.04), inset 0 0 0 1px rgba(0,0,0,0.08);
  --shadow-5: 0px 1px 1px rgba(0,0,0,0.02), 0px 8px 16px -4px rgba(0,0,0,0.04), 0px 24px 32px -8px rgba(0,0,0,0.06), inset 0 0 0 1px rgba(0,0,0,0.08);
}

/* 全局基础样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--font-sans);
  color: var(--color-ink);
  background: var(--color-canvas-soft);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Ant Design 主题覆盖 - Vercel 风格 */
.ant-btn-primary {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
  border-radius: var(--radius-pill) !important;
  font-weight: 500;
  box-shadow: none !important;
}

.ant-btn-primary:hover {
  background: #27272a !important;
  border-color: #27272a !important;
}

.ant-btn-default {
  border-radius: var(--radius-pill) !important;
  font-weight: 500;
}

.ant-input,
.ant-input-affix-wrapper,
.ant-select-selector,
.ant-picker {
  border-radius: var(--radius-sm) !important;
}

.ant-modal .ant-modal-content {
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-5) !important;
}

.ant-modal .ant-modal-header {
  border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
}

.ant-message .ant-message-notice-content {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-4) !important;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
}
</style>
