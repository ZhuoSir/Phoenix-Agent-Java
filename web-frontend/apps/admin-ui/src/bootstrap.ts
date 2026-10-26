import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@vben/access';
import { registerLoadingDirective, setDefaultModalProps } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { addAPIProvider } from '@vben/icons';
import { initStores } from '@vben/stores';
import '@vben/styles';
import '@vben/styles/ele';

import { useTitle } from '@vueuse/core';
import { ElLoading } from 'element-plus';

import { $t, setupI18n } from '#/locales';

import { initComponentAdapter } from './adapter/component';
import { initSetupVbenForm } from './adapter/form';
import App from './app.vue';
import { router } from './router';

// 图标本地化（offline-icons R-01）：把 Iconify 默认 API provider 指向本站静态目录 /icons，
// 运行时只请求本站（不再访问 https://api.iconify.design），适配内网离线环境。
addAPIProvider('', {
  resources: ['/icons'],
  maxURL: 512,
  rotate: 750,
  timeout: 2000,
});

async function bootstrap(namespace: string) {
  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupVbenForm();

  // 设置弹窗的默认配置
  setDefaultModalProps({
    fullscreenButton: false,
    draggable: true,
  });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 2000,
  // });
  const app = createApp(App);

  // 注册Element Plus提供的v-loading指令
  app.directive('loading', ElLoading.directive);

  // 注册Vben提供的v-loading和v-spinning指令
  registerLoadingDirective(app, {
    loading: false, // Vben提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册Vben提供的v-loading指令
    spinning: 'spinning',
  });

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-tore
  await initStores(app, { namespace });

  // 安装权限指令
  registerAccessDirective(app);

  // 初始化 tippy
  const { initTippy } = await import('@vben/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫
  app.use(router);

  // 配置Motion插件
  const { MotionPlugin } = await import('@vben/plugins/motion');
  app.use(MotionPlugin);

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const path = router.currentRoute.value.path;
      const appName = path.startsWith('/front')
        ? preferences.app.name.replace(/Admin/gi, '').trim()
        : preferences.app.name;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + appName;
      useTitle(pageTitle);
    }
  });

  // 附件单次上传上限：后端下发（application.yml: phoenix.agent.chat.attachment-max-count）

  // 失败保持构建期默认，不阻塞启动

  try {

    const resp = await fetch('/api/chat/attachment/limit', {

      headers: { 'phoenix-token': localStorage.getItem('phoenix-token') ?? '' },

    });

    const body: any = await resp.json();

    const maxCount = body?.data?.maxCount;

    if (typeof maxCount === 'number' && maxCount > 0) {

      const { setAttachmentMaxFiles } = await import('@phoenix/chat-shared');

      setAttachmentMaxFiles(maxCount);

    }

  } catch {

    // 忽略：回落到构建期默认值

  }
  // 品牌外观（brand-configuration T-05）：3s 超时 + 失败回落默认，绝不阻塞挂载
  try {
    const { loadBrand } = await import('#/stores/brand');
    await loadBrand();
  } catch (e) {
    console.warn('[brand] 初始化异常（已忽略）', e);
  }



  app.mount('#app');
}

export { bootstrap };
