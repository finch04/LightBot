<template>
  <div class="about-page">
    <!-- 花瓣特效 -->
    <div class="petals-container">
      <div v-for="i in 40" :key="i" class="petal" :style="petalStyle(i)"></div>
    </div>

    <div class="about-card">
      <div class="logo-ellipse">
        <div class="logo-ellipse-glow"></div>
        <img src="/lightbot-logo.png" alt="LightBot" class="about-logo" />
      </div>
      <h1 class="about-title">LightBot</h1>
      <p class="about-desc">轻量级 AI Agent 平台</p>

      <div class="about-info">
        <div class="info-row">
          <span class="info-label">技术栈</span>
          <span class="info-value">Spring Boot + Spring AI + Vue 3</span>
        </div>
        <div class="info-row">
          <span class="info-label">作者</span>
          <span class="info-value">finch</span>
        </div>
        <div class="info-row">
          <span class="info-label">开源地址</span>
          <a class="info-link" href="https://github.com/finch04/LightBot" target="_blank" rel="noopener noreferrer">
            <GithubOutlined /> GitHub
          </a>
        </div>
      </div>

      <div class="about-features">
        <h2>核心能力</h2>
        <ul>
          <li>Agent 定义与运行时 — 多模型支持、Tool 调用、记忆管理</li>
          <li>RAG 知识库 — 文档解析、向量检索、知识图谱</li>
          <li>Workflow 引擎 — DAG 编排、并行执行、条件分支</li>
          <li>评估体系 — 数据集管理、基准测试、RAG 评估</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { GithubOutlined } from '@ant-design/icons-vue'

// 每片花瓣的随机样式
function petalStyle(i) {
  const size = 10 + Math.random() * 14
  const left = Math.random() * 100
  const delay = Math.random() * 6
  const duration = 4 + Math.random() * 4
  const rotate = Math.random() * 360
  const hue = 330 + Math.random() * 40 // 粉色范围
  return {
    width: size + 'px',
    height: size * 0.6 + 'px',
    left: left + '%',
    animationDelay: delay + 's',
    animationDuration: duration + 's',
    transform: `rotate(${rotate}deg)`,
    background: `hsla(${hue}, 80%, 80%, 0.7)`,
  }
}
</script>

<style scoped>
.about-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 20px;
  min-height: 100%;
  background: linear-gradient(135deg, #fdf2f8 0%, #f0f4ff 50%, #f5f3ff 100%);
  overflow: hidden;
}

/* 花瓣容器 */
.petals-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.petal {
  position: absolute;
  top: -20px;
  border-radius: 50% 0 50% 0;
  opacity: 0.8;
  animation: petalFall linear infinite;
  filter: blur(0.5px);
}

@keyframes petalFall {
  0% {
    top: -5%;
    transform: rotate(0deg) translateX(0);
    opacity: 0;
  }
  10% {
    opacity: 0.8;
  }
  90% {
    opacity: 0.6;
  }
  100% {
    top: 105%;
    transform: rotate(720deg) translateX(80px);
    opacity: 0;
  }
}

/* 卡片 */
.about-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 520px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-radius: 16px;
  padding: 40px 36px;
  padding-top: 48px;
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.06),
    0 0 0 1px rgba(255, 255, 255, 0.6);
  text-align: center;
  animation: cardIn 0.6s ease-out;
  overflow: visible;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.logo-ellipse {
  position: absolute;
  top: -28px;
  left: -28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  box-shadow:
    0 6px 24px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  overflow: hidden;
  transform-origin: 70% 20%;
  animation: ellipseHang 3s ease-in-out infinite;
  z-index: 2;
}
.logo-ellipse::before {
  content: '';
  position: absolute;
  top: 4px;
  left: 50%;
  transform: translateX(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  box-shadow: 0 0 4px rgba(255, 255, 255, 0.1);
  z-index: 3;
}
.logo-ellipse-glow {
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  background: conic-gradient(from 0deg, transparent, rgba(139, 92, 246, 0.3), transparent, rgba(59, 130, 246, 0.3), transparent);
  animation: glowSpin 6s linear infinite;
  z-index: 0;
}
.logo-ellipse::after {
  content: '';
  position: absolute;
  inset: 2px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  z-index: 1;
}
.about-logo {
  position: relative;
  z-index: 2;
  width: 90px;
  height: 45px;
  border-radius: 8px;
}
@keyframes ellipseHang {
  0%, 100% { transform: rotate(-3deg); }
  50% { transform: rotate(3deg); }
}
@keyframes glowSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.about-title {
  font-size: 26px;
  font-weight: 700;
  color: #171717;
  margin: 0 0 4px;
  letter-spacing: 0.5px;
}

.about-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0 0 28px;
}

.about-info {
  text-align: left;
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
  margin-bottom: 28px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.info-label {
  width: 80px;
  flex-shrink: 0;
  font-size: 13px;
  color: #a1a1aa;
}

.info-value {
  font-size: 14px;
  color: #27272a;
}

.info-link {
  font-size: 14px;
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: color 0.2s;
}

.info-link:hover {
  color: #1d4ed8;
}

.about-features {
  text-align: left;
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}

.about-features h2 {
  font-size: 15px;
  font-weight: 600;
  color: #171717;
  margin: 0 0 12px;
}

.about-features ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.about-features li {
  font-size: 13px;
  color: #52525b;
  padding: 5px 0;
  padding-left: 16px;
  position: relative;
}

.about-features li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ec4899, #8b5cf6);
}
</style>
