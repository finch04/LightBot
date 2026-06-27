<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useTheme } from './composables/useTheme'

const { isDark, themeConfig } = useTheme()
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

  /* 绿色色阶（文档定位） */
  --green-50: #f0fdf4;
  --green-100: #dcfce7;
  --green-200: #bbf7d0;
  --green-300: #86efac;
  --green-400: #4ade80;
  --green-500: #22c55e;
  --green-600: #16a34a;
  --green-700: #166534;

  /* 蓝色色阶（参考文献/知识检索） */
  --blue-50: #eff6ff;
  --blue-100: #dbeafe;
  --blue-200: #bfdbfe;
  --blue-300: #93c5fd;
  --blue-400: #60a5fa;
  --blue-500: #3b82f6;
  --blue-600: #2563eb;
  --blue-700: #1d4ed8;
  --blue-800: #1e40af;

  /* 语义背景色 */
  --color-canvas-soft-3: #f0f0f0;
  --color-warn-bg: #fffbeb;
  --color-warn-bg-deep: #fef3c7;
  --color-error-bg: #fef2f2;
  --color-success-bg: #f0fdf4;
  --color-info-bg: #e8f4ff;
  --color-purple-bg: #f5f3ff;
  --color-purple-border: #c4b5fd;

  /* 文字色扩展 */
  --color-text-dark: #334155;
  --color-text-code: #1e293b;

  /* 边框色扩展 */
  --color-border-slate: #e2e8f0;
  --color-border-green: #bbf7d0;
  --color-border-blue: #bfdbfe;

  /* Typography */
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;

  /* Sidebar — 始终深色 */
  --sidebar-bg: #171717;
  --sidebar-bg-hover: #27272a;
  --sidebar-border: #27272a;
  --sidebar-text: #d4d4d8;
  --sidebar-text-bright: #fafafa;

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

  /* 灰色色阶 */
  --gray-0: #ffffff;
  --gray-10: #fbfcfc;
  --gray-25: #f8fafa;
  --gray-50: #f5f7f7;
  --gray-100: #eff2f2;
  --gray-200: #e4e6e6;
  --gray-300: #d7d9d9;
  --gray-400: #bdbfbf;
  --gray-500: #979999;
  --gray-600: #697070;
  --gray-700: #4c4d4d;
  --gray-800: #323333;
  --gray-900: #1e1f1f;
  --gray-1000: #151616;

  /* 透明度系统 */
  --light-85: rgba(255, 255, 255, 0.85);
  --dark-85: rgba(0, 0, 0, 0.85);
  --dark-70: rgba(0, 0, 0, 0.7);
  --dark-50: rgba(0, 0, 0, 0.5);
  --dark-25: rgba(0, 0, 0, 0.25);
  --dark-10: rgba(0, 0, 0, 0.1);

  /* Ant Design 兼容变量 */
  --color-bg-container: var(--gray-0);
  --color-bg-elevated: var(--gray-10);
  --color-text-secondary: var(--dark-70);
  --color-trans-light: var(--light-85);
  --color-trans-dark: var(--dark-85);

  /* 思维导图/知识图谱 — 浅色模式用默认深色 */
  --mindmap-text: #1e293b;
  --mindmap-stroke: #e2e8f0;
  --kg-canvas-bg: var(--color-canvas);
}

/* 深色模式 CSS 变量覆盖 */
[data-theme="dark"] {
  /* --color-primary 保持 #171717 不变：深色按钮/卡片始终深色 */
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

  /* 绿色色阶 - 深色 */
  --green-50: #052e16;
  --green-100: #14532d;
  --green-200: #166534;
  --green-300: #16a34a;
  --green-400: #22c55e;
  --green-500: #4ade80;
  --green-600: #86efac;
  --green-700: #bbf7d0;

  /* 蓝色色阶 - 深色 */
  --blue-50: #0c1a3d;
  --blue-100: #1e3a5f;
  --blue-200: #1e3a5f;
  --blue-300: #2563eb;
  --blue-400: #3b82f6;
  --blue-500: #60a5fa;
  --blue-600: #93c5fd;
  --blue-700: #bfdbfe;
  --blue-800: #dbeafe;

  /* 语义背景色 - 深色 */
  --color-canvas-soft-3: #2e2e33;
  --color-warn-bg: #3b2f0a;
  --color-warn-bg-deep: #422006;
  --color-error-bg: #3b1111;
  --color-success-bg: #052e16;
  --color-info-bg: #0c1a3d;
  --color-purple-bg: #1e1040;
  --color-purple-border: #4c1d95;

  /* 文字色扩展 - 深色 */
  --color-text-dark: #e4e4e7;
  --color-text-code: #e4e4e7;

  /* 边框色扩展 - 深色 */
  --color-border-slate: #333;
  --color-border-green: #166534;
  --color-border-blue: #1e3a5f;

  /* 灰色色阶反转 */
  --gray-0: #030303;
  --gray-10: #080808;
  --gray-25: #0c0d0d;
  --gray-50: #151616;
  --gray-100: #1e1f1f;
  --gray-200: #4c4d4d;
  --gray-300: #697070;
  --gray-400: #979999;
  --gray-500: #bdbfbf;
  --gray-600: #d7d9d9;
  --gray-700: #e4e6e6;
  --gray-800: #eef0f0;
  --gray-900: #eff2f2;
  --gray-1000: #f5f7f7;

  /* 透明度反转 */
  --light-85: rgba(0, 0, 0, 0.85);
  --dark-85: rgba(255, 255, 255, 0.85);
  --dark-70: rgba(255, 255, 255, 0.7);
  --dark-50: rgba(255, 255, 255, 0.5);
  --dark-25: rgba(255, 255, 255, 0.25);
  --dark-10: rgba(255, 255, 255, 0.1);

  /* Ant Design 兼容变量 - 深色 */
  --color-bg-container: #1f1f1f;
  --color-bg-elevated: #262626;
  --color-text-secondary: rgba(255, 255, 255, 0.65);
  --color-trans-light: rgba(0, 0, 0, 0.85);
  --color-trans-dark: rgba(255, 255, 255, 0.85);

  /* 思维导图/知识图谱深色适配 */
  --mindmap-text: #fff;
  --mindmap-stroke: #555;
  --kg-canvas-bg: #000;

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

[data-theme="dark"] .ant-btn-primary {
  border-color: rgba(255, 255, 255, 0.15) !important;
}
[data-theme="dark"] .ant-btn-primary:hover {
  background: #3f3f46 !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
}

.ant-btn-primary:disabled,
.ant-btn-primary.ant-btn-disabled {
  background: var(--color-canvas-soft-2) !important;
  border-color: var(--color-hairline) !important;
  color: var(--dark-25) !important;
  box-shadow: none !important;
  text-shadow: none !important;
  cursor: not-allowed !important;
}

[data-theme="dark"] .ant-btn-primary:disabled,
[data-theme="dark"] .ant-btn-primary.ant-btn-disabled {
  background: rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(255, 255, 255, 0.15) !important;
  color: rgba(255, 255, 255, 0.35) !important;
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

/* ===== 深色模式：自定义组件适配 ===== */
/* Ant Design 组件由 darkAlgorithm 自动处理，仅保留自定义组件覆盖 */

[data-theme="dark"] .tool-calls-group {
  background: var(--color-bg-elevated) !important;
  border-color: var(--color-hairline) !important;
}

[data-theme="dark"] .search-docs-result *,
[data-theme="dark"] .web-search-result *,
[data-theme="dark"] .sandbox-file-result * {
  border-color: var(--color-hairline) !important;
}
</style>
