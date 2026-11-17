import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import './styles/variables.css';
import './styles/global.css';
import { initTheme } from '@/utils/theme';

initTheme(); // 挂载前应用主题，防首帧闪烁

createApp(App).use(createPinia()).use(router).mount('#app');
