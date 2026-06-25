<template>
  <span v-if="level" class="level-tag" :class="[`level-${level}`, size]">
    <span class="level-tag-inner">Lv {{ level }}</span>
  </span>
</template>

<script setup>
defineProps({
  level: { type: Number, default: 0 },
  size: { type: String, default: 'default' },
})
</script>

<style scoped>
/* ========== 基础 ========== */
.level-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-weight: 800;
  letter-spacing: 0.5px;
  overflow: hidden;
  flex-shrink: 0;
}
.level-tag-inner {
  position: relative;
  z-index: 1;
}

/* 尺寸 */
.level-tag.small {
  font-size: 11px;
  padding: 1px 7px;
}
.level-tag.default {
  font-size: 15px;
  padding: 3px 12px;
  border-radius: 6px;
}

/* ========== Lv 1: 灰色静默 ========== */
.level-tag.level-1 {
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
}

/* ========== Lv 2: 蓝色闪光 ========== */
.level-tag.level-2 {
  color: var(--color-link);
  background: rgba(0, 112, 243, 0.06);
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 6px rgba(0, 112, 243, 0.2);
  box-shadow: 0 0 4px rgba(0, 112, 243, 0.1);
}
.level-tag.level-2::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, #0070f3, #38bdf8, #0070f3);
  background-size: 300% 300%;
  animation: tagRainbow 5s linear infinite;
  opacity: 0.3;
  z-index: -1;
}
.level-tag.level-2::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: var(--color-canvas);
  z-index: -1;
}
.level-tag.level-2 .level-tag-inner {
  background: linear-gradient(90deg, #0070f3, #38bdf8, #0070f3);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 3.5s linear infinite;
}
.level-tag.level-2 .level-tag-inner::after {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(105deg, transparent 38%, rgba(255,255,255,0.5) 45%, rgba(255,255,255,0.8) 50%, rgba(255,255,255,0.5) 55%, transparent 62%);
  animation: tagSparkle 3.5s ease-in-out infinite;
  pointer-events: none;
}
.level-tag.level-2:hover {
  transform: none !important;
  box-shadow: 0 0 4px rgba(0, 112, 243, 0.1) !important;
}

/* ========== Lv 3: 紫色闪光 ========== */
.level-tag.level-3 {
  color: #7c3aed;
  background: linear-gradient(135deg, rgba(0, 112, 243, 0.06), rgba(124, 58, 237, 0.06));
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 6px rgba(124, 58, 237, 0.2);
  box-shadow: 0 0 4px rgba(124, 58, 237, 0.1);
}
.level-tag.level-3::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, #0070f3, #7c3aed, #a78bfa, #7c3aed);
  background-size: 300% 300%;
  animation: tagRainbow 4.5s linear infinite;
  opacity: 0.35;
  z-index: -1;
}
.level-tag.level-3::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: var(--color-canvas);
  z-index: -1;
}
.level-tag.level-3 .level-tag-inner {
  background: linear-gradient(90deg, #7c3aed, #a78bfa, #7c3aed);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 3s linear infinite;
}
.level-tag.level-3 .level-tag-inner::after {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(105deg, transparent 38%, rgba(255,255,255,0.5) 45%, rgba(255,255,255,0.8) 50%, rgba(255,255,255,0.5) 55%, transparent 62%);
  animation: tagSparkle 3s ease-in-out infinite;
  pointer-events: none;
}
.level-tag.level-3:hover {
  transform: none !important;
  box-shadow: 0 0 4px rgba(124, 58, 237, 0.1) !important;
}

/* ========== Lv 4: 金色闪光 ========== */
.level-tag.level-4 {
  color: #d97706;
  background: linear-gradient(135deg, rgba(217, 119, 6, 0.08), rgba(245, 158, 11, 0.08));
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 8px rgba(245, 158, 11, 0.3);
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15);
}
.level-tag.level-4::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, #d97706, #f59e0b, #fbbf24, #d97706);
  background-size: 300% 300%;
  animation: tagRainbow 4s linear infinite;
  opacity: 0.45;
  z-index: -1;
}
.level-tag.level-4::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: var(--color-canvas);
  z-index: -1;
}
.level-tag.level-4 .level-tag-inner {
  background: linear-gradient(90deg, #d97706, #f59e0b, #d97706);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.8s linear infinite;
}
.level-tag.level-4 .level-tag-inner::after {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(105deg, transparent 38%, rgba(255,255,255,0.55) 45%, rgba(255,255,255,0.85) 50%, rgba(255,255,255,0.55) 55%, transparent 62%);
  animation: tagSparkle 2.8s ease-in-out infinite;
  pointer-events: none;
}
.level-tag.level-4:hover {
  transform: none !important;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15) !important;
}

/* ========== Lv 5: 彩虹闪光 ========== */
.level-tag.level-5 {
  color: #c026d3;
  background: linear-gradient(135deg, rgba(225, 29, 72, 0.05), rgba(124, 58, 237, 0.05), rgba(0, 112, 243, 0.05));
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 6px rgba(192, 38, 211, 0.2);
  box-shadow: 0 0 4px rgba(192, 38, 211, 0.1);
}
.level-tag.level-5::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, #e11d48, #7c3aed, #0070f3, #e11d48);
  background-size: 300% 300%;
  animation: tagRainbow 4s linear infinite;
  opacity: 0.45;
  z-index: -1;
}
.level-tag.level-5::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: var(--color-canvas);
  z-index: -1;
}
.level-tag.level-5 .level-tag-inner {
  background: linear-gradient(90deg, #c026d3, #e879f9, #c026d3);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.5s linear infinite;
}
.level-tag.level-5 .level-tag-inner::after {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(105deg, transparent 38%, rgba(255,255,255,0.55) 45%, rgba(255,255,255,0.85) 50%, rgba(255,255,255,0.55) 55%, transparent 62%);
  animation: tagSparkle 2.5s ease-in-out infinite;
  pointer-events: none;
}
.level-tag.level-5:hover {
  transform: none !important;
  box-shadow: 0 0 4px rgba(192, 38, 211, 0.1) !important;
}

/* ========== Lv 6: 金色闪光 ========== */
.level-tag.level-6 {
  color: #b45309;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1), rgba(217, 119, 6, 0.08));
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 8px rgba(245, 158, 11, 0.3);
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15);
}
.level-tag.level-6::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, #f59e0b, #d97706, #fbbf24, #f59e0b);
  background-size: 300% 300%;
  animation: tagRainbow 3s linear infinite;
  opacity: 0.55;
  z-index: -1;
}
.level-tag.level-6::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: var(--color-canvas);
  z-index: -1;
}
.level-tag.level-6 .level-tag-inner {
  background: linear-gradient(90deg, #b45309, #f59e0b, #b45309);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.5s linear infinite;
}
/* 闪光扫过 */
.level-tag.level-6 .level-tag-inner::after {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(
    105deg,
    transparent 38%,
    rgba(255, 255, 255, 0.6) 45%,
    rgba(255, 255, 255, 0.9) 50%,
    rgba(255, 255, 255, 0.6) 55%,
    transparent 62%
  );
  animation: tagSparkle 2s ease-in-out infinite;
  pointer-events: none;
}
.level-tag.level-6:hover {
  transform: none !important;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15) !important;
}
/* 侧边栏暗色 */
.level-tag.small.level-6 {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
  text-shadow: 0 0 8px rgba(251, 191, 36, 0.3);
  box-shadow: 0 0 6px rgba(251, 191, 36, 0.12);
}
.level-tag.small.level-6 .level-tag-inner {
  background: linear-gradient(90deg, #fbbf24, #fde68a, #fbbf24);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.5s linear infinite;
}

/* 侧边栏暗色主题覆盖 */
.level-tag.small.level-1 {
  color: var(--color-mute);
  background: rgba(255,255,255,0.06);
  border-color: rgba(255,255,255,0.08);
}
.level-tag.small.level-2 {
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.08);
  border-color: rgba(56, 189, 248, 0.2);
  text-shadow: 0 0 6px rgba(56, 189, 248, 0.2);
  box-shadow: 0 0 4px rgba(56, 189, 248, 0.1);
}
.level-tag.small.level-2 .level-tag-inner {
  background: linear-gradient(90deg, #38bdf8, #7dd3fc, #38bdf8);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 3.5s linear infinite;
}
.level-tag.small.level-3 {
  color: #a78bfa;
  background: rgba(167, 139, 250, 0.08);
  border-color: rgba(167, 139, 250, 0.25);
  text-shadow: 0 0 6px rgba(167, 139, 250, 0.2);
  box-shadow: 0 0 4px rgba(167, 139, 250, 0.1);
}
.level-tag.small.level-3 .level-tag-inner {
  background: linear-gradient(90deg, #a78bfa, #c4b5fd, #a78bfa);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 3s linear infinite;
}
.level-tag.small.level-4 {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
  border-color: rgba(251, 191, 36, 0.3);
  text-shadow: 0 0 8px rgba(251, 191, 36, 0.3);
  box-shadow: 0 0 6px rgba(251, 191, 36, 0.12);
}
.level-tag.small.level-4 .level-tag-inner {
  background: linear-gradient(90deg, #fbbf24, #fde68a, #fbbf24);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.8s linear infinite;
}
.level-tag.small.level-5 {
  color: #e879f9;
  background: rgba(192, 38, 211, 0.06);
  text-shadow: 0 0 6px rgba(192, 38, 211, 0.2);
  box-shadow: 0 0 4px rgba(192, 38, 211, 0.1);
}
.level-tag.small.level-5 .level-tag-inner {
  background: linear-gradient(90deg, #e879f9, #f0abfc, #e879f9);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: tagTextFlow 2.5s linear infinite;
}

/* ========== 动画 ========== */
@keyframes tagRainbow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

@keyframes tagTextFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}

@keyframes tagSparkle {
  0%, 100% { transform: translateX(-120%); opacity: 0; }
  40% { opacity: 0; }
  50% { opacity: 1; }
  60% { opacity: 0; }
  100% { transform: translateX(120%); opacity: 0; }
}
</style>
