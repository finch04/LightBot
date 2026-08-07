<template>
  <div v-if="showInputDisclaimer" class="input-hint">智元可能会犯错，请核实重要信息。</div>
  <div v-else class="input-hint-carousel">
    <div class="input-hint-carousel-row">
      <span class="input-question-label">你可以问我</span>
      <div class="input-question-rotate">
        <transition name="hint-slide" mode="out-in">
          <span
            :key="questionRotateIndex"
            class="input-question-text"
            @click="onApplyQuestion(inputHintQuestions[questionRotateIndex])"
          >
            {{ inputHintQuestions[questionRotateIndex] }}
          </span>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  showInputDisclaimer: { type: Boolean, default: true },
  inputHintQuestions: { type: Array, default: () => [] },
  questionRotateIndex: { type: Number, default: 0 },
})

const emit = defineEmits(['apply-question'])

function onApplyQuestion(q) {
  emit('apply-question', q)
}
</script>

<style scoped>
.input-hint {
  text-align: center;
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 8px;
}
.input-hint-carousel {
  display: flex;
  justify-content: center;
  margin-top: 8px;
  max-width: 100%;
  padding: 2px 16px;
}
.input-hint-carousel-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(560px, 100%);
}
.input-question-label {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1;
  font-weight: 600;
  color: var(--color-link);
  background: var(--color-info-bg);
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 4px 10px;
  white-space: nowrap;
}
.input-question-rotate {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  min-height: calc(13px * 1.6);
}
.input-question-rotate .input-question-text {
  position: static;
  width: 100%;
}
.input-question-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-mute);
  cursor: pointer;
  transition: color 0.15s;
  display: block;
  padding: 1px 0 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.input-question-text:hover {
  color: var(--color-link);
}
.hint-slide-enter-active {
  transition: opacity 0.5s ease, transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.hint-slide-leave-active {
  transition: opacity 0.35s ease, transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.hint-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.hint-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
