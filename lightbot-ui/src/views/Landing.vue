<template>
  <div class="landing-container">
    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-container">
      <a-spin size="large" />
      <p class="loading-text">正在加载...</p>
    </div>

    <template v-else>
      <!-- 背景装饰 -->
      <div class="ambient" aria-hidden="true">
        <span class="orb orb-1"></span>
        <span class="orb orb-2"></span>
        <span class="orb orb-3"></span>
        <div class="grid-mesh"></div>
      </div>

      <!-- 顶部导航 -->
      <header class="glass-header">
        <div class="logo" @click="router.push('/')">
          <img src="/lightbot-logo-single.png" alt="LightBot" class="logo-img" />
          <span class="logo-text">LightBot</span>
        </div>
        <div class="header-actions">
          <a
            class="github-link"
            :href="config.github || 'https://github.com/finch04/LightBot'"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="GitHub"
          >
            <svg height="20" width="20" viewBox="0 0 16 16" version="1.1">
              <path
                fill-rule="evenodd"
                d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
              ></path>
            </svg>
          </a>
          <button class="btn-login-header" @click="router.push('/login')">登录</button>
        </div>
      </header>

      <!-- Hero 区域 -->
      <main class="hero-section">
        <div class="hero-layout">
          <!-- 左侧文案 -->
          <div class="hero-content reveal-up">
            <h1 class="title reveal-up delay-1">{{ config.title || 'LightBot' }}</h1>
            <Transition name="subtitle-switch" mode="out-in">
              <p v-if="currentSubtitle" class="subtitle" :key="currentSubtitle">
                {{ currentSubtitle }}
              </p>
            </Transition>
            <p class="description reveal-up delay-1">{{ config.description }}</p>
            <div class="hero-actions reveal-up delay-2">
              <button class="button-base primary" @click="handleStart">
                <span>开始体验</span>
                <RightOutlined />
              </button>
            </div>
          </div>

          <!-- 右侧功能动画 -->
          <aside class="hero-visual reveal-up delay-1">
            <div class="visual-card">
              <div class="visual-glow" aria-hidden="true"></div>
              <div class="feature-grid">
                <div
                  v-for="(feature, index) in features"
                  :key="feature.icon"
                  class="feature-item"
                  :class="{ active: activeFeature === index }"
                  @mouseenter="activeFeature = index"
                >
                  <div class="feature-icon-wrap">
                    <component :is="iconMap[feature.icon]" />
                  </div>
                  <span class="feature-title">{{ feature.title }}</span>
                </div>
              </div>
              <Transition name="desc-switch" mode="out-in">
                <div v-if="activeFeatureData" class="feature-desc-area" :key="activeFeatureData.title">
                  <p class="feature-desc-title">{{ activeFeatureData.title }}</p>
                  <p class="feature-desc-text">{{ activeFeatureData.desc }}</p>
                </div>
              </Transition>
            </div>
          </aside>
        </div>
      </main>

      <!-- 底部 -->
      <footer class="footer">
        <p class="copyright">{{ config.copyright || '© 2026 LightBot. All Rights Reserved.' }}</p>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLandingConfig } from '../api/landing'
import {
  RightOutlined,
  RobotOutlined,
  DatabaseOutlined,
  ApartmentOutlined,
  ApiOutlined,
  ToolOutlined,
  ThunderboltOutlined,
  ExperimentOutlined,
  EyeOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()

// 已登录则跳转应用
if (localStorage.getItem('token')) {
  router.replace('/app')
}

const isLoading = ref(true)
const config = ref({})
const activeFeature = ref(0)
let featureTimer = null
let subtitleTimer = null
const subtitleIndex = ref(0)

const iconMap = {
  Agent: RobotOutlined,
  Knowledge: DatabaseOutlined,
  Workflow: ApartmentOutlined,
  Mcp: ApiOutlined,
  Tool: ToolOutlined,
  Skill: ThunderboltOutlined,
  Eval: ExperimentOutlined,
  Observability: EyeOutlined,
}

const features = computed(() => config.value.features || [])
const activeFeatureData = computed(() => features.value[activeFeature.value] || null)

const subtitles = computed(() => config.value.subtitles || [])
const currentSubtitle = computed(() => subtitles.value[subtitleIndex.value] || config.value.subtitle || '')

function startFeatureCycle() {
  stopFeatureCycle()
  if (features.value.length <= 1) return
  featureTimer = setInterval(() => {
    activeFeature.value = (activeFeature.value + 1) % features.value.length
  }, 3000)
}

function stopFeatureCycle() {
  if (featureTimer) {
    clearInterval(featureTimer)
    featureTimer = null
  }
}

function startSubtitleCycle() {
  stopSubtitleCycle()
  if (subtitles.value.length <= 1) return
  subtitleTimer = setInterval(() => {
    subtitleIndex.value = (subtitleIndex.value + 1) % subtitles.value.length
  }, 2800)
}

function stopSubtitleCycle() {
  if (subtitleTimer) {
    clearInterval(subtitleTimer)
    subtitleTimer = null
  }
}

function handleStart() {
  if (localStorage.getItem('token')) {
    router.push('/app')
  } else {
    router.push('/login')
  }
}

onMounted(async () => {
  try {
    const res = await getLandingConfig()
    const raw = res?.data ?? res
    if (raw) {
      config.value = typeof raw === 'string' ? JSON.parse(raw) : raw
    }
  } catch (e) {
    console.error('加载 Landing 配置失败:', e)
    // 使用默认值
    config.value = {
      title: 'LightBot',
      subtitle: 'AI Native 智能体平台',
      subtitles: ['AI Native 智能体平台', '一站式 RAG 知识库引擎', '可视化 Workflow 编排'],
      description: '构建智能体、知识库、工作流与工具集成的统一平台。',
      features: [
        { icon: 'Agent', title: '智能体', desc: '多模型驱动的自主推理 Agent' },
        { icon: 'Knowledge', title: '知识库', desc: '向量检索 + 图谱融合的 RAG 引擎' },
        { icon: 'Workflow', title: '工作流', desc: '可视化 DAG 编排' },
      ],
      github: 'https://github.com/finch04/LightBot',
      copyright: '© 2026 LightBot. All Rights Reserved.',
    }
  } finally {
    isLoading.value = false
    startFeatureCycle()
    startSubtitleCycle()
  }
})

onUnmounted(() => {
  stopFeatureCycle()
  stopSubtitleCycle()
})
</script>

<style scoped>
.landing-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: var(--color-ink);
  background: var(--color-canvas);
  position: relative;
  overflow-x: hidden;
  font-family: var(--font-sans);
}

/* 加载状态 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  gap: 16px;
}
.loading-text {
  color: var(--color-mute);
  font-size: 14px;
}

/* 背景装饰 */
.ambient {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  will-change: transform;
}
.orb-1 {
  width: 500px;
  height: 500px;
  top: -180px;
  right: -120px;
  background: rgba(0, 112, 243, 0.08);
  animation: orbFloat 20s ease-in-out infinite;
}
.orb-2 {
  width: 420px;
  height: 420px;
  bottom: -160px;
  left: -100px;
  background: rgba(121, 40, 202, 0.06);
  animation: orbFloat 24s ease-in-out infinite reverse;
}
.orb-3 {
  width: 350px;
  height: 350px;
  top: 40%;
  left: 55%;
  background: rgba(80, 227, 194, 0.06);
  animation: orbFloat 28s ease-in-out infinite;
}
.grid-mesh {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
  background-size: 64px 64px;
  -webkit-mask-image: radial-gradient(ellipse 70% 50% at 50% 10%, #000, transparent 70%);
  mask-image: radial-gradient(ellipse 70% 50% at 50% 10%, #000, transparent 70%);
}

/* 顶部导航 */
.glass-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 16px 40px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  border-bottom: 1px solid var(--color-hairline);
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}
.logo-img {
  height: 28px;
  width: 28px;
  object-fit: contain;
}
.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-ink);
  letter-spacing: -0.02em;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.github-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  text-decoration: none;
  color: var(--color-mute);
  transition: color 0.2s, background 0.2s;
}
.github-link:hover {
  color: var(--color-ink);
  background: var(--color-canvas-soft-2);
}
.github-link svg {
  fill: currentColor;
}
.btn-login-header {
  height: 36px;
  padding: 0 20px;
  border-radius: 100px;
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}
.btn-login-header:hover {
  border-color: var(--color-ink);
  background: var(--color-canvas-soft);
}

/* Hero 区域 */
.hero-section {
  position: relative;
  z-index: 1;
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 120px 40px 48px;
}
.hero-layout {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 48px;
  align-items: center;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
}
.hero-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 标题 */
.title {
  font-size: clamp(3rem, 5vw, 4.5rem);
  font-weight: 600;
  margin: 0;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: var(--color-ink);
}

/* 副标题轮播 */
.subtitle {
  font-size: 22px;
  font-weight: 500;
  color: var(--color-body);
  line-height: 1.4;
  margin: 0;
  min-height: 31px;
}
.subtitle-switch-enter-active,
.subtitle-switch-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.subtitle-switch-enter-from,
.subtitle-switch-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* 描述 */
.description {
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-mute);
  margin: 0;
  max-width: 520px;
}

/* CTA 按钮 */
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  margin-top: 8px;
}
.button-base {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 52px;
  padding: 0 32px;
  border-radius: 100px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  text-decoration: none;
  transition: all 0.2s;
  font-family: var(--font-sans);
}
.button-base.primary {
  background: var(--color-ink);
  color: var(--color-canvas);
  box-shadow: 0 4px 16px -4px rgba(23, 23, 23, 0.3);
}
.button-base.primary:hover {
  background: #27272a;
  box-shadow: 0 8px 24px -4px rgba(23, 23, 23, 0.35);
}
.button-base.primary:hover :deep(svg) {
  transform: translateX(3px);
}
.button-base.primary :deep(svg) {
  transition: transform 0.2s ease;
}

/* Ant Design icon sizing */
.button-base :deep(.anticon) {
  font-size: 16px;
  display: inline-flex;
  align-items: center;
}
.feature-icon-wrap :deep(.anticon) {
  font-size: 22px;
  display: inline-flex;
  align-items: center;
}

/* 右侧功能卡片 */
.hero-visual {
  display: flex;
  justify-content: center;
}
.visual-card {
  position: relative;
  width: 100%;
  max-width: 480px;
  padding: 28px;
  border-radius: 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  box-shadow: var(--shadow-4);
  overflow: hidden;
}
.visual-glow {
  position: absolute;
  top: -30%;
  right: -15%;
  width: 60%;
  height: 60%;
  background: radial-gradient(circle, rgba(0, 112, 243, 0.06), transparent 70%);
  pointer-events: none;
}

/* 功能网格 2x4 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  position: relative;
  z-index: 1;
}
.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid transparent;
}
.feature-item:hover {
  background: var(--color-canvas-soft);
}
.feature-item.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
  transform: translateY(-2px);
}
.feature-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--color-canvas-soft-2);
  border: 1px solid var(--color-hairline);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-body);
  transition: all 0.3s ease;
}
.feature-item.active .feature-icon-wrap {
  background: var(--color-ink);
  border-color: var(--color-ink);
  color: var(--color-canvas);
  box-shadow: 0 4px 12px -2px rgba(23, 23, 23, 0.25);
}
.feature-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-body);
  text-align: center;
  line-height: 1.3;
}
.feature-item.active .feature-title {
  color: var(--color-ink);
  font-weight: 600;
}

/* 功能描述区域 */
.feature-desc-area {
  margin-top: 20px;
  padding: 16px 20px;
  border-radius: 10px;
  background: var(--color-canvas-soft);
  border: 1px solid var(--color-hairline);
  position: relative;
  z-index: 1;
  min-height: 76px;
}
.feature-desc-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 6px;
}
.feature-desc-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-mute);
  margin: 0;
}
.desc-switch-enter-active,
.desc-switch-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.desc-switch-enter-from,
.desc-switch-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* 页脚 */
.footer {
  position: relative;
  z-index: 1;
  margin-top: auto;
  border-top: 1px solid var(--color-hairline);
  text-align: center;
  padding: 20px;
}
.copyright {
  color: var(--color-mute);
  font-size: 13px;
  margin: 0;
}

/* 入场动画 */
.reveal-up {
  opacity: 0;
  transform: translateY(16px);
  animation: revealUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.reveal-up.delay-1 { animation-delay: 110ms; }
.reveal-up.delay-2 { animation-delay: 220ms; }

@keyframes revealUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes orbFloat {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(0, -24px) scale(1.03);
  }
}

/* 响应式 */
@media (max-width: 960px) {
  .hero-layout {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  .hero-content {
    align-items: flex-start;
    text-align: left;
  }
  .visual-card {
    max-width: 480px;
    margin: 0 auto;
  }
  .feature-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 640px) {
  .glass-header {
    padding: 12px 20px;
  }
  .hero-section {
    padding: 100px 20px 32px;
  }
  .title {
    font-size: 2.5rem;
  }
  .subtitle {
    font-size: 18px;
  }
  .feature-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
  .feature-item {
    padding: 12px 4px;
  }
  .feature-icon-wrap {
    width: 40px;
    height: 40px;
  }
  .button-base {
    width: 100%;
  }
}
</style>
