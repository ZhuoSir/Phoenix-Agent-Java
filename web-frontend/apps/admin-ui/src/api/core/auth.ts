import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    token: string;
    userId: string;
    username: string;
    realName: string;
    email?: string;
    phone?: string;
    userType?: number;
    /** T-05（统一账号中心）：是否持有后台角色——落地页判定依据 */
    hasAdminRole?: boolean;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 登录（统一账号端点）
 * R-11 / T-19（v2.0.1）：**只保留一套登录逻辑** —— 原 `userLoginApi`（前台端点 `/auth/login`）已删除；
 * 前台/后台账号一律走本端点，落地页由返回的 `hasAdminRole` 决定。
 * 注：后端 `/auth/login` 端点因 pc-ui / mobile-ui 仍在调用而**保留**（一期不删，二期评估下线）。
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/api/privilege/auth/login',
    data
  );
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/api/privilege/auth/logout');
}

