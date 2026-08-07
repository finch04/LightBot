<template>
  <div class="empty-state">
    <img src="/lightbot-logo-single.png" alt="智元" class="empty-logo" />
    <template v-if="loading">
      <!-- Agent 加载中：欢迎语与推荐问题区显示骨架屏，避免先显示默认文案再闪烁切换 -->
      <div class="welcome-skeleton">
        <div class="skeleton-line skeleton-line-lg"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line skeleton-line-sm"></div>
      </div>
      <div class="recommended-skeleton">
        <div v-for="i in 3" :key="i" class="skeleton-chip"></div>
      </div>
    </template>
    <template v-else>
      <div class="welcome-content"><MarkdownPreview :content="welcomeMessage" /></div>
      <!-- 推荐问题：全部展示 -->
      <div v-if="recommendedQuestions.length > 0" class="recommended-questions">
        <button
          v-for="(q, qi) in recommendedQuestions"
          :key="qi"
          class="btn-question"
          @click="$emit('select-question', q)"
        >
          {{ q }}
        </button>
      </div>
      <!-- 无默认Agent提示 -->
      <div v-if="!selectedAgentId && agentsLength > 0" class="no-default-hint">
        没有默认Agent，<router-link to="/app/agents">去创建</router-link>
      </div>
    </template>
  </div>
</template>

<script setup>
import MarkdownPreview from '../MarkdownPreview.vue'

defineProps({
  welcomeMessage: { type: String, default: '' },
  recommendedQuestions: { type: Array, default: () => [] },
  selectedAgentId: { type: [String, Number], default: null },
  agentsLength: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
})

defineEmits(['select-question'])
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}
.empty-logo {
  height: 64px;
  margin-bottom: 24px;
  object-fit: contain;
}
/* Agent 加载中的骨架屏 */
.welcome-skeleton {
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}
.skeleton-line {
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(
    90deg,
    var(--color-canvas-soft-2) 25%,
    var(--color-canvas-soft) 37%,
    var(--color-canvas-soft-2) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
  width: 60%;
}
.skeleton-line-lg { width: 75%; height: 22px; border-radius: 11px; }
.skeleton-line-sm { width: 40%; }
.recommended-skeleton {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}
.skeleton-chip {
  width: 140px;
  height: 34px;
  border-radius: 100px;
  background: linear-gradient(
    90deg,
    var(--color-canvas-soft-2) 25%,
    var(--color-canvas-soft) 37%,
    var(--color-canvas-soft-2) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
.welcome-content {
  text-align: center;
  max-width: 600px;
  margin-bottom: 24px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
}
.welcome-content :deep(h1),
.welcome-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.welcome-content :deep(p) {
  margin: 0 0 8px;
  color: var(--color-mute);
}
.recommended-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}
.btn-question {
  padding: 8px 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-question:hover {
  border-color: var(--color-link);
  color: var(--color-link);
  background: var(--color-link-bg-soft);
}
.no-default-hint {
  font-size: 13px;
  color: var(--color-mute);
  margin-top: 8px;
}
.no-default-hint a {
  color: var(--color-link);
  text-decoration: none;
}
.no-default-hint a:hover {
  text-decoration: underline;
}
</style>
