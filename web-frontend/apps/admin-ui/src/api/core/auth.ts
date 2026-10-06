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
 * 管理员登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>(
    '/api/privilege/auth/login',
    data
  );
}

/**
 * 普通用户登录
 */
export async function userLoginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/api/privilege/auth/logout');
}

