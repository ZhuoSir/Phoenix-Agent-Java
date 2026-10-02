import { fileURLToPath, URL } from 'node:url';

import { loadEnv } from 'vite';

import { defineConfig } from '@vben/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

const targetUrl = 'http://localhost:8066';
// BUG-25(T-07)：dev 端口取 .env.development 的 VITE_PORT（5777），不再被 vite 默认 5173 顶替
const localEnv = loadEnv('development', process.cwd(), '');
export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: { port: Number(localEnv.VITE_PORT || 5173) },
    },
    vite: {
      resolve: {
        alias: {
          '@': fileURLToPath(new URL('./src', import.meta.url)),
          '@phoenix/chat-shared': fileURLToPath(
            new URL('../../packages/chat-shared/src/index.ts', import.meta.url),
          ),
        },
      },
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
      server: {
        proxy: {
          '/api/upload': {
            changeOrigin: true,
            // 不 rewrite，保留 /api 前缀，让后端 FileUploadController 能正确匹配
            target: targetUrl,
            ws: true,
          },
          // BL-18（BUG-33 根治）：前端直出后端真实路径，dev 代理只透传不剥层
          '/api': {
            changeOrigin: true,
            target: targetUrl,
            ws: true,
          },
          '/platform': {
            changeOrigin: true,
            target: targetUrl,
          },
          '/auth': {
            changeOrigin: true,
            target: targetUrl,
          },
          '/nl2sql': {
            changeOrigin: true,
            // rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: targetUrl,
            ws: true,
          },
          '/uploads': {
            changeOrigin: true,
            // rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: targetUrl,
            ws: true,
          },
        },
      },
    },
  };
});
