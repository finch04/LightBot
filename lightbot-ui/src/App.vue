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
  border-color: #444 !important;
}
[data-theme="dark"] .ant-btn-primary:hover {
  background: #3f3f46 !important;
  border-color: #555 !important;
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

/* ===== 深色模式全局适配 ===== */

/* 输入框、选择器、文本域 */
[data-theme="dark"] .ant-input,
[data-theme="dark"] .ant-input-affix-wrapper,
[data-theme="dark"] .ant-select-selector,
[data-theme="dark"] .ant-picker,
[data-theme="dark"] .ant-input-textarea textarea {
  background: #1a1a1a !important;
  color: #e4e4e7 !important;
  border-color: #333 !important;
}
[data-theme="dark"] .ant-input::placeholder,
[data-theme="dark"] .ant-input-textarea textarea::placeholder {
  color: #52525b !important;
}
[data-theme="dark"] .ant-input-affix-wrapper:hover,
[data-theme="dark"] .ant-input:hover,
[data-theme="dark"] .ant-select-selector:hover,
[data-theme="dark"] .ant-picker:hover {
  border-color: #4f46e5 !important;
}
[data-theme="dark"] .ant-input:focus,
[data-theme="dark"] .ant-input-affix-wrapper-focused,
[data-theme="dark"] .ant-select-focused .ant-select-selector {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15) !important;
}

/* 下拉面板 */
[data-theme="dark"] .ant-select-dropdown,
[data-theme="dark"] .ant-picker-dropdown .ant-picker-panel-container,
[data-theme="dark"] .ant-dropdown-menu {
  background: #222 !important;
  border-color: #333 !important;
}
[data-theme="dark"] .ant-select-item {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-select-item-option-active {
  background: #2a2a2e !important;
}
[data-theme="dark"] .ant-select-item-option-selected {
  background: #27272a !important;
}

/* 弹窗、抽屉 */
[data-theme="dark"] .ant-modal-content,
[data-theme="dark"] .ant-drawer-content {
  background: #1a1a1a !important;
}
[data-theme="dark"] .ant-modal-header,
[data-theme="dark"] .ant-drawer-header {
  background: #1a1a1a !important;
}
[data-theme="dark"] .ant-modal-title,
[data-theme="dark"] .ant-drawer-title {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-modal-close-x,
[data-theme="dark"] .ant-drawer-close {
  color: #71717a !important;
}

/* 表格 */
[data-theme="dark"] .ant-table {
  background: #1a1a1a !important;
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-table-thead > tr > th,
[data-theme="dark"] .ant-table-thead .ant-table-cell {
  background: #222 !important;
  color: #a1a1aa !important;
  border-bottom-color: #2e2e33 !important;
}
[data-theme="dark"] .ant-table-tbody > tr > td {
  border-bottom-color: #2e2e33 !important;
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-table-tbody > tr:hover > td {
  background: #222 !important;
}
[data-theme="dark"] .ant-table-tbody > tr.ant-table-row:hover > td {
  background: #222 !important;
}

/* 分页 */
[data-theme="dark"] .ant-pagination-item {
  background: #1a1a1a !important;
  border-color: #333 !important;
}
[data-theme="dark"] .ant-pagination-item a {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-pagination-item-active {
  border-color: #6366f1 !important;
}

/* 标签 */
[data-theme="dark"] .ant-tag {
  background: #222 !important;
  border-color: #333 !important;
  color: #a1a1aa !important;
}

/* Tabs */
[data-theme="dark"] .ant-tabs-tab {
  color: #71717a !important;
}
[data-theme="dark"] .ant-tabs-tab-active .ant-tabs-tab-btn {
  color: #e4e4e7 !important;
}

/* Tooltip / Popover */
[data-theme="dark"] .ant-tooltip-inner,
[data-theme="dark"] .ant-popover-inner {
  background: #2a2a2e !important;
  color: #e4e4e7 !important;
}

/* 卡片容器 (通用模式) */
[data-theme="dark"] .ant-card {
  background: #1a1a1a !important;
  border-color: #2e2e33 !important;
  color: #e4e4e7 !important;
}

/* 描述列表 */
[data-theme="dark"] .ant-descriptions-item-label,
[data-theme="dark"] .ant-descriptions-title {
  color: #a1a1aa !important;
}
[data-theme="dark"] .ant-descriptions-item-content {
  color: #e4e4e7 !important;
}

/* 徽章 */
[data-theme="dark"] .ant-badge-status-text {
  color: #e4e4e7 !important;
}

/* 菜单 */
[data-theme="dark"] .ant-menu {
  background: transparent !important;
}
[data-theme="dark"] .ant-menu-item {
  color: #a1a1aa !important;
}
[data-theme="dark"] .ant-menu-item-selected {
  color: #e4e4e7 !important;
  background: #222 !important;
}

/* Switch */
[data-theme="dark"] .ant-switch {
  background: #3f3f46 !important;
}

/* Empty */
[data-theme="dark"] .ant-empty-description {
  color: #71717a !important;
}

/* Dropdown */
[data-theme="dark"] .ant-dropdown {
  background: #222 !important;
}
[data-theme="dark"] .ant-dropdown-menu-item {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-dropdown-menu-item:hover {
  background: #2a2a2e !important;
}

/* 气泡确认框 */
[data-theme="dark"] .ant-popconfirm .ant-popover-inner {
  background: #222 !important;
}

/* Alert */
[data-theme="dark"] .ant-alert {
  background: #222 !important;
  border-color: #333 !important;
}
[data-theme="dark"] .ant-alert-message {
  color: #e4e4e7 !important;
}

/* Skeleton */
[data-theme="dark"] .ant-skeleton-content .ant-skeleton-title,
[data-theme="dark"] .ant-skeleton-content .ant-skeleton-paragraph > li {
  background: linear-gradient(90deg, #222 25%, #2a2a2e 37%, #222 63%) !important;
}

/* Progress */
[data-theme="dark"] .ant-progress-text {
  color: #a1a1aa !important;
}

/* 工具调用分组组件 */
[data-theme="dark"] .tool-calls-group {
  background: #222 !important;
  border-color: #333 !important;
}

/* 工具结果组件 - 深色适配 */
[data-theme="dark"] .search-docs-result,
[data-theme="dark"] .web-search-result,
[data-theme="dark"] .sandbox-file-result {
  background: transparent !important;
}
[data-theme="dark"] .search-docs-result *,
[data-theme="dark"] .web-search-result *,
[data-theme="dark"] .sandbox-file-result * {
  border-color: #333 !important;
}
</style>
