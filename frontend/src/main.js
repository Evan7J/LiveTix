import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/global.scss'

// 强制 Element Plus 暗色模式
document.documentElement.classList.add('dark')

const app = createApp(App)

// 禁用 Vue DevTools 检测（避免浏览器扩展在左下角显示绿色调试图标）
app.config.devtools = false

// Pinia with persistence
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

// Register all Element Plus icons
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.mount('#app')
