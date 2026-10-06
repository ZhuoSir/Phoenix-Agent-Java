import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { loginApi, logoutApi, userLoginApi } from '#/api';
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
      const isUser = params.roleType === 'user';
      const result = isUser
        ? await userLoginApi({ username: params.username, password: params.password })
        : await loginApi(params);
      const { token, userId, username, realName, email, phone, userType } =
        result as typeof result & { hasAdminRole?: boolean };

      if (token) {
        localStorage.setItem('phoenix-token', token);
        accessStore.setAccessToken(token);

        // T-05（统一账号中心）：落地页按「是否持有后台角色」判定。
        // 原实现用 userType===1 判「普通用户」，但该列语义是「0 自建 / 1 IDM」（见 BUG-101 同源的语义混淆），
        // 会把 IDM 来源的后台用户误送前台。前台入口(isUser)仍固定进前台。
        const hasAdminRole = (result as { hasAdminRole?: boolean }).hasAdminRole;
        const homePath =
          isUser || hasAdminRole === false ? '/front/chat' : '/agent/list';
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
          userType,
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
