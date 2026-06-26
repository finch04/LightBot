<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/app/eval')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">创建实验</h1>
        <p class="page-desc">选择评测集、配置评测对象和评估器，创建评测实验</p>
      </div>
    </div>

    <div class="create-card">
      <ExperimentCreateForm
        :label-span="4"
        @success="onCreateSuccess"
      >
        <template #cancel>
          <button class="btn-cancel" @click="router.push('/app/eval')">取消</button>
        </template>
      </ExperimentCreateForm>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import ExperimentCreateForm from '../components/eval/ExperimentCreateForm.vue'

const router = useRouter()

function onCreateSuccess() {
  router.push('/app/eval?tab=experiments')
}
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: var(--color-canvas-soft);
}
.page-header {
  margin-bottom: 24px;
}
.btn-back {
  background: none;
  border: none;
  color: var(--color-mute);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: var(--color-link); }
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin: 0;
}
.create-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 32px;
  max-width: 800px;
}
.btn-cancel {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  cursor: pointer;
  font-size: 14px;
}
.btn-cancel:hover { border-color: var(--color-link); color: var(--color-link); }
</style>
