import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig({
  plugins: [
    vue({
      // 禁用 Vue DevTools 集成，避免在开发模式下左下角出现绿色调试图标
      devtools: false,
    }),
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  // 全局禁用 Vue 开发工具提示
  define: {
    __VUE_PROD_DEVTOOLS__: false,
    __VUE_OPTIONS_API__: true,
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false,
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    // 允许 cpolar 等内网穿透域名访问
    allowedHosts: ['.cpolar.cn', '.cpolar.com', 'localhost'],
    // 隐藏 Vite 开发模式下的 HMR 错误覆盖层及状态指示器
    hmr: {
      overlay: true,  // 显示错误覆盖层，方便看到报错
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        // additionalData 可能导致 Sass 编译失败，已移除
      },
    },
  },
})
