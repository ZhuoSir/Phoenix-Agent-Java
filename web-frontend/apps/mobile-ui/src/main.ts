import { createApp } from 'vue';

import {
  configureAuthStorage,
  useAgentStore,
  useAuthStore,
  useChatStore,
} from '@phoenix/chat-shared';
import { createPinia } from 'pinia';

import App from './App.vue';
import router from './router';
import { realAgentTransport } from './services/agentTransport';
import { realAuthTransport } from './services/authTransport';
import { realChatTransport } from './services/chatTransport';

import 'vant/es/popup/style';
import 'vant/es/toast/style';

import 'vant/lib/index.css';
import './styles/global.scss';

// 每次加载清除上次的缓存，强制重新认证（自建应用 token 过期场景）
// Object.keys(localStorage)
//   .filter((k) => k.startsWith('mobile-ui:'))
//   .forEach((k) => localStorage.removeItem(k));
configureAuthStorage({ storageKey: 'mobile-ui' });

const app = createApp(App);

app.use(createPinia());

const auth = useAuthStore();
useAuthStore().setTransport(realAuthTransport);
useAgentStore().setTransport(realAgentTransport);
useChatStore().setTransport(realChatTransport);

async function bootstrap() {
  console.log('[bootstrap] 启动，token:', auth.token ? '存在' : '为空');
  // R-11（v2.0.0）：第三方免登（客户端内 SSO）已整体下线，本应用只保留账号密码登录。
  // 未登录时**不再尝试任何 SSO/免登请求**，直接落到登录页（LoginPage + POST /auth/login）。
  if (auth.token) {
    console.log('[bootstrap] 已有 token，加载智能体列表...');
    void useAgentStore().loadAll();
  } else {
    console.log('[bootstrap] 无 token → 停留密码登录页（SSO 已下线）');
  }

  console.log('[bootstrap] 安装路由');
  app.use(router);

  console.log('[bootstrap] 挂载 Vue 应用');
  app.mount('#app');
}

bootstrap();
