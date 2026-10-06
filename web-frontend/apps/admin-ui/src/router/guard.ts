import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        // BUG-89：redirect 可能指向登录页自身（历史自我重定向留下的嵌套值）→ 不可再跳自己，
        // 否则 vue-router 中止导航 ⇒ 内容区空白（现象：打开页签无内容、强刷才恢复）
        const raw = (to.query?.redirect as string) || '';
        let target = '';
        try {
          target = raw ? decodeURIComponent(raw) : '';
        }
        catch {
          target = '';
        }
        if (!target || target.split('?')[0] === LOGIN_PATH) {
          target = userStore.userInfo?.homePath || '/agent/list';
        }
        return target;
      }
      // Chat 路由需登录，且仅允许 userType === 1 的用户访问
      if (to.name === 'Chat') {
        if (!accessStore.accessToken) {
          return {
            path: LOGIN_PATH,
            query: { redirect: encodeURIComponent(to.fullPath) },
            replace: true,
          };
        }
        if (accessStore.isAccessChecked && userStore.userInfo?.userType !== 1) {
          return decodeURIComponent(
            userStore.userInfo?.homePath || '/agent/list',
          );
        }
        return true;
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // BUG-89：判据从 `to.fullPath` 改为 `to.path`——落在 `/auth/login?redirect=…` 时
      // fullPath 必然 != LOGIN_PATH，会把**登录页自身**再包一层 redirect（自我重定向 + 嵌套），
      // 导航被中止 ⇒ 空白页。已在登录页时直接放行渲染（`return true`，不能 `return to`，那同样是"跳自己"）。
      if (to.path === LOGIN_PATH) {
        return true;
      }
      return {
        path: LOGIN_PATH,
        // 如不需要，直接删除 query
        query:
          to.fullPath === '/agent/list'
            ? {}
            : { redirect: encodeURIComponent(to.fullPath) },
        // 携带当前跳转的页面，登录后重新跳转该页面
        replace: true,
      };
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());
    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    // userType === 1 的用户跳转到 chat 页面
    let redirectPath: string;
    if (userInfo.userType === 1) {
      redirectPath = '/front/chat';
    } else {
      redirectPath = (from.query.redirect ??
        (to.path === '/agent/list'
          ? userInfo.homePath || '/agent/list'
          : to.fullPath)) as string;
    }

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
