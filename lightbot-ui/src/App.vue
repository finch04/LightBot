<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { onMounted } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useTheme } from './composables/useTheme'

const { isDark, themeConfig } = useTheme()

onMounted(() => {
  const saved = localStorage.getItem('lightbot-theme')
  if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDark.value = true
  }
})
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

/* 深色模式 CSS 变量覆盖 */
[data-theme="dark"] {
  --color-ink: #e4e4e7;
  --color-body: #a1a1aa;
  --color-mute: #71717a;
  --color-hairline: #2e2e33;
  --color-hairline-strong: #3f3f46;
  --color-canvas: #111111;
  --color-canvas-soft: #18181b;
  --color-canvas-soft-2: #222225;
  --color-link: #3b82f6;
  --color-link-deep: #60a5fa;
  --color-link-bg-soft: #1e3a5f;
  --color-success: #22c55e;
  --color-error: #ef4444;
  --color-error-soft: #3b1111;
  --color-error-deep: #f87171;
  --color-warning: #f59e0b;
  --color-warning-soft: #3b2f0a;
  --color-warning-deep: #fbbf24;
  --shadow-1: 0 0 0 1px rgba(255,255,255,0.08);
  --shadow-2: 0px 1px 1px rgba(0,0,0,0.2), 0px 2px 2px rgba(0,0,0,0.2), inset 0 0 0 1px rgba(255,255,255,0.06);
  --shadow-3: 0px 2px 2px rgba(0,0,0,0.2), 0px 8px 8px -8px rgba(0,0,0,0.2), inset 0 0 0 1px rgba(255,255,255,0.06);
  --shadow-4: 0px 2px 2px rgba(0,0,0,0.2), 0px 8px 16px -4px rgba(0,0,0,0.2), inset 0 0 0 1px rgba(255,255,255,0.06);
  --shadow-5: 0px 1px 1px rgba(0,0,0,0.2), 0px 8px 16px -4px rgba(0,0,0,0.2), 0px 24px 32px -8px rgba(0,0,0,0.3), inset 0 0 0 1px rgba(255,255,255,0.06);
}

/* 全局基础样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 禁用浏览器拼写检查波浪线 */
::spelling-error {
  text-decoration: none;
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

[data-theme="dark"] .ant-btn-primary:hover {
  background: #3f3f46 !important;
  border-color: #3f3f46 !important;
}

.ant-btn-primary:disabled,
.ant-btn-primary.ant-btn-disabled {
  background: #171717 !important;
  border-color: #171717 !important;
  color: rgba(255, 255, 255, 0.5) !important;
  opacity: 0.65;
  box-shadow: none !important;
  text-shadow: none !important;
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

/* 表格表头禁止换行 */
.ant-table-thead th,
.ant-table-thead .ant-table-cell {
  white-space: nowrap !important;
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

[data-theme="dark"] ::-webkit-scrollbar-thumb {
  background: #3f3f46;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb:hover {
  background: #52525b;
}
</style>
