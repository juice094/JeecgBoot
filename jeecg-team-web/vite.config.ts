import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 3200,
    proxy: {
      // 后端 jeecg-boot（本机开发 8081；云上默认 8080）
      '/jeecg-boot': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
