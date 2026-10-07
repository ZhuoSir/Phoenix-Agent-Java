import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { loginApi, logoutApi } from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      // R-11 / T-19（v2.0.1）：单套登录逻辑 —— 不再有"普通用户/管理员"两条分支，统一一个端点
      const result = await loginApi(params);
      const { token, userId, username, realName, email, phone } =
        result as typeof result & { hasAdminRole?: boolean };

      if (token) {
        localStorage.setItem('phoenix-token', token);
        accessStore.setAccessToken(token);

        // T-05（统一账号中心）：落地页按「是否持有后台角色」判定。
        // R-14（v2.3.0）：user_type（自建/IDM）已下线，不再参与任何判定。
        // T-19（v2.0.1）：无后台角色者落「智能体中心」(/agent/chat，R-11 新入口)，不再落前台路由 /front/chat。
        const hasAdminRole = (result as { hasAdminRole?: boolean }).hasAdminRole;
        const homePath =
          hasAdminRole === false ? '/agent/chat' : '/agent/list';
        userInfo = {
          avatar: '',
          desc: '',
          homePath,
          realName,
          token,
          userId,
          username,
          email,
          phone,
        };

        userStore.setUserInfo(userInfo);

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(homePath);
        }

        if (userInfo?.realName) {
          ElNotification({
            message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            title: $t('authentication.loginSuccess'),
            type: 'success',
          });
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    try {
      localStorage.removeItem('phoenix-token');
    } catch {}
    try {
      resetAllStores();
    } catch (error) {
      console.error('resetAllStores error:', error);
    }
    try {
      accessStore.setLoginExpired(false);
    } catch {}

    // 回登录页带上当前路由地址
    const redirectQuery = redirect
      ? `?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
      : '';
    window.location.href = LOGIN_PATH + redirectQuery;
  }

  async function fetchUserInfo() {
    const { getUserInfoApi } = await import('#/api');
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});
