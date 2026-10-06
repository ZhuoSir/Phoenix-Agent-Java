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
    // R-11 / T-19（v2.0.1）：前台独立入口下线 —— 旧前台智能体列表重定向到 admin 侧。
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
    // R-12（v2.1.0）：`/front/chat` **恢复为独立 chat 页**（撤销 T-19 的重定向）——
    // 它是「智能体中心」菜单在新浏览器窗口里的落地页；不在 BasicLayout 之下 ⇒ 无侧栏/标签栏/页签容器，
    // 满足"不叠加后台外壳"，同时仍复用同一登录态与同一套接口（不构成第二套登录/访问体系）。
    name: 'Chat',
    path: '/front/chat',
    component: () => import('#/views/front/chat.vue'),
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
