import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false,
        }),
      ],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // 默认 500KB 阈值导致大包静默通过，调到 1500KB 让真正的超大包（Monaco/Shiki 全量语言）才告警
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 按 vendor 拆分，避免单 chunk 过大；首屏只取必需的 vue-vendor + antd-vendor
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'antd-vendor': ['ant-design-vue', '@ant-design/icons-vue'],
          'markdown-vendor': ['markdown-it', 'highlight.js', 'shiki', 'dompurify', '@vscode/markdown-it-katex', 'markdown-it-task-lists'],
          'editor-vendor': ['monaco-editor'],
          'graph-vendor': ['@antv/g6'],
          'flow-vendor': ['@vue-flow/core', '@vue-flow/background', '@vue-flow/controls', '@vue-flow/minimap', 'dagre'],
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 安全响应头：开发态即生效，生产态由 Nginx 同步下发
    // 防点击劫持（X-Frame-Options）、MIME 嗅探（X-Content-Type-Options）、
    // Referer 泄漏（Referrer-Policy）、禁用麦克风/相机/定位（Permissions-Policy）
    headers: {
      'X-Frame-Options': 'SAMEORIGIN',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Permissions-Policy': 'geolocation=(), microphone=(), camera=()',
      // CSP 先用宽松版（允许 unsafe-inline / unsafe-eval），后续按 Report-Only 收集后收紧
      'Content-Security-Policy': [
        "default-src 'self'",
        // img-src 放行 MinIO 开发地址（localhost:9000）；生产环境 MinIO 走 https 或反代后可移除该白名单
        "img-src 'self' data: blob: https: http://localhost:9000",
        "media-src 'self' data: blob:",
        "style-src 'self' 'unsafe-inline'",
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
        // 开发态放行 http：业务页 srcdoc 常调本地后端；localhost 与 127.0.0.1 不同源，'self' 盖不住
        "connect-src 'self' https: http: ws: wss:",
        "font-src 'self' data:",
        "frame-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
      ].join('; '),
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        ws: true,
        // SSE 流式传输：绕过 http-proxy 缓冲，直接透传响应
        selfHandleResponse: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            // 对 SSE 请求禁用代理缓冲
            if (req.url?.includes('/stream')) {
              proxyReq.setHeader('Cache-Control', 'no-cache')
              proxyReq.setHeader('Connection', 'keep-alive')
            }
          })
          proxy.on('proxyRes', (proxyRes, req, res) => {
            // SSE 响应直接透传，不经过 http-proxy 缓冲
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              res.writeHead(200, {
                'Content-Type': 'text/event-stream',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
                'X-Accel-Buffering': 'no',
              })
              proxyRes.pipe(res)
              return
            }
            // 非 SSE 请求正常代理
            let body = []
            proxyRes.on('data', (chunk) => body.push(chunk))
            proxyRes.on('end', () => {
              res.writeHead(proxyRes.statusCode, proxyRes.headers)
              res.end(Buffer.concat(body))
            })
          })
        },
      },
    },
  },
})
