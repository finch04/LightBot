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
  server: {
    host: '0.0.0.0',
    port: 5173,
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
