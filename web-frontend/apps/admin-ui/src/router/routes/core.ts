import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
/** 全局404页面 */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: () => import('#/views/_core/fallback/not-found.vue'),
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  /**
   * 根路由
   * 使用基础布局，作为所有页面的父级容器，子级就不必配置BasicLayout。
   * 此路由必须存在，且不应修改
   */
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: '/auth/login',
    children: [],
  },
  {
    name: 'Login',
    path: LOGIN_PATH,
    component: () => import('#/views/auth/LoginPage.vue'),
    meta: {
      hideInTab: true,
      title: $t('page.auth.login'),
    },
  },
  {
    // R-11 / T-19（v2.0.1）：前台独立入口下线 —— 旧前台路由重定向到 admin 侧对应页面。
    // 页面文件保留（一期不删代码；chat.vue 仍被「智能体中心」菜单以 pageMap 动态路由复用）。
    name: 'AgentList',
    path: '/front/agent',
    redirect: '/agent/list',
    meta: {
      hideInTab: true,
      hideInMenu: true,
      title: '智能体',
    },
  },
  {
    name: 'Chat',
    path: '/front/chat',
    redirect: '/agent/chat',
    meta: {
      hideInTab: true,
      hideInMenu: true,
      title: 'Chat',
    },
  },
  {
    name: 'TermsOfService',
    path: '/front/terms-of-service',
    component: () => import('#/views/front/terms-of-service.vue'),
    meta: {
      hideInTab: true,
      hideInMenu: true,
      title: '服务条款',
    },
  },
  {
    name: 'PrivacyAgreement',
    path: '/front/privacy-agreement',
    component: () => import('#/views/front/privacy-agreement.vue'),
    meta: {
      hideInTab: true,
      hideInMenu: true,
      title: '隐私协议',
    },
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'Authentication',
    },
    name: 'Authentication',
    path: '/auth',
    redirect: '/auth/code-login',
    children: [
      {
        name: 'CodeLogin',
        path: 'code-login',
        component: () => import('#/views/_core/authentication/code-login.vue'),
        meta: {
          title: $t('page.auth.codeLogin'),
        },
      },
      {
        name: 'QrCodeLogin',
        path: 'qrcode-login',
        component: () =>
          import('#/views/_core/authentication/qrcode-login.vue'),
        meta: {
          title: $t('page.auth.qrcodeLogin'),
        },
      },
      {
        name: 'ForgetPassword',
        path: 'forget-password',
        component: () =>
          import('#/views/_core/authentication/forget-password.vue'),
        meta: {
          title: $t('page.auth.forgetPassword'),
        },
      },
      {
        name: 'Register',
        path: 'register',
        component: () => import('#/views/_core/authentication/register.vue'),
        meta: {
          title: $t('page.auth.register'),
        },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
