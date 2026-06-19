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
  color: #a1a1aa;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
}

/* ========== Lv 2: 蓝色微光 ========== */
.level-tag.level-2 {
  color: #0070f3;
  background: rgba(0, 112, 243, 0.06);
  border: 1px solid rgba(0, 112, 243, 0.25);
}
.level-tag.level-2::before {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(60deg, transparent 42%, rgba(0, 112, 243, 0.12) 50%, transparent 58%);
  animation: tagShine 3.5s ease-in-out infinite;
}

/* ========== Lv 3: 紫色渐变 ========== */
.level-tag.level-3 {
  color: #7c3aed;
  background: linear-gradient(135deg, rgba(0, 112, 243, 0.06), rgba(124, 58, 237, 0.06));
  border: 1px solid rgba(124, 58, 237, 0.3);
}
.level-tag.level-3::before {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(60deg, transparent 42%, rgba(124, 58, 237, 0.15) 50%, transparent 58%);
  animation: tagShine 3s ease-in-out infinite;
}

/* ========== Lv 4: 金色发光 ========== */
.level-tag.level-4 {
  color: #d97706;
  background: linear-gradient(135deg, rgba(217, 119, 6, 0.08), rgba(245, 158, 11, 0.08));
  border: 1px solid rgba(245, 158, 11, 0.4);
  text-shadow: 0 0 8px rgba(245, 158, 11, 0.3);
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15);
}
.level-tag.level-4::before {
  content: '';
  position: absolute;
  inset: -50%;
  background: linear-gradient(60deg, transparent 40%, rgba(245, 158, 11, 0.2) 50%, transparent 60%);
  animation: tagShine 2.5s ease-in-out infinite;
}

/* ========== Lv 5: 彩虹流光 ========== */
.level-tag.level-5 {
  color: #e11d48;
  background: linear-gradient(135deg, rgba(225, 29, 72, 0.06), rgba(124, 58, 237, 0.06), rgba(0, 112, 243, 0.06));
  border: 1px solid transparent;
  background-clip: padding-box;
  text-shadow: 0 0 10px rgba(225, 29, 72, 0.3);
  box-shadow: 0 0 8px rgba(225, 29, 72, 0.15), 0 0 16px rgba(124, 58, 237, 0.08);
  animation: tagGlow 3s ease-in-out infinite;
}
.level-tag.level-5::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: inherit;
  background: linear-gradient(135deg, #e11d48, #7c3aed, #0070f3, #e11d48);
  background-size: 300% 300%;
  animation: tagRainbow 4s linear infinite;
  z-index: -1;
}
.level-tag.level-5::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 3px;
  background: inherit;
  z-index: -1;
}
.level-tag.level-5 .level-tag-inner {
  background: linear-gradient(90deg, #e11d48, #7c3aed, #0070f3, #e11d48);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: tagTextFlow 3s linear infinite;
}
.level-tag.level-5:hover {
  transform: none !important;
  box-shadow: 0 0 8px rgba(225, 29, 72, 0.15), 0 0 16px rgba(124, 58, 237, 0.08) !important;
}

/* 侧边栏暗色主题覆盖 */
.level-tag.small.level-1 {
  color: #71717a;
  background: rgba(255,255,255,0.06);
  border-color: rgba(255,255,255,0.08);
}
.level-tag.small.level-2 {
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.08);
  border-color: rgba(56, 189, 248, 0.2);
}
.level-tag.small.level-3 {
  color: #a78bfa;
  background: rgba(167, 139, 250, 0.08);
  border-color: rgba(167, 139, 250, 0.25);
}
.level-tag.small.level-4 {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
  border-color: rgba(251, 191, 36, 0.3);
  text-shadow: 0 0 8px rgba(251, 191, 36, 0.3);
  box-shadow: 0 0 6px rgba(251, 191, 36, 0.12);
}
.level-tag.small.level-5 {
  background: rgba(225, 29, 72, 0.06);
  text-shadow: 0 0 10px rgba(225, 29, 72, 0.3);
  box-shadow: 0 0 8px rgba(225, 29, 72, 0.12), 0 0 16px rgba(124, 58, 237, 0.06);
}

/* ========== 动画 ========== */
@keyframes tagShine {
  0%, 100% { transform: translateX(-100%); }
  50% { transform: translateX(100%); }
}

@keyframes tagGlow {
  0%, 100% { box-shadow: 0 0 8px rgba(225, 29, 72, 0.15), 0 0 16px rgba(124, 58, 237, 0.08); }
  50% { box-shadow: 0 0 12px rgba(225, 29, 72, 0.25), 0 0 24px rgba(124, 58, 237, 0.15); }
}

@keyframes tagRainbow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

@keyframes tagTextFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}
</style>
