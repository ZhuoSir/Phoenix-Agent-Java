import type { PageResult } from '#/api/core/privilege-types';

import { requestClient } from '#/api/request';

export interface PrivilegeUser {
  roles?: Array<{ id?: string; name?: string }>;
  id?: string;
  username?: string;
  realName?: string;
  mobile?: string;
  status?: number;
  secret?: string;
  password?: string;
  employeeId?: string;
  createTime?: string;
  updateTime?: string;
}

export async function getUserPageApi(
  page: number,
  size: number,
  params?: Record<string, any>,
) {
  return requestClient.get<PageResult<PrivilegeUser>>(
    '/api/privilege/user/page',
    {
      params: { page, size, ...params },
      responseReturn: 'body',
    },
  );
}

export async function getUserApi(id: string) {
  return requestClient.get<PrivilegeUser>(`/api/privilege/user/${id}`, {
    responseReturn: 'body',
  });
}

export async function getUserByUsernameApi(username: string) {
  return requestClient.get<PrivilegeUser>(
    `/api/privilege/user/username/${username}`,
    { responseReturn: 'body' },
  );
}

export async function createUserApi(data: Record<string, any>) {
  return requestClient.post('/api/privilege/user', data, {
    responseReturn: 'body',
  });
}

export async function updateUserApi(data: Record<string, any>) {
  return requestClient.put('/api/privilege/user', data, {
    responseReturn: 'body',
  });
}

export async function deleteUserApi(id: string) {
  return requestClient.delete(`/api/privilege/user/${id}`, {
    responseReturn: 'body',
  });
}

/** R-15（v2.4.0）：启用/禁用单个账号。status：0 启用 / 1 禁用 */
export async function updateUserStatusApi(id: string, status: number) {
  return requestClient.put(
    '/api/privilege/user/status',
    { id, status },
    { responseReturn: 'body' },
  );
}

/** R-15（v2.4.0）：批量启用/禁用。返回实际更新行数 */
export async function batchUpdateUserStatusApi(
  ids: string[],
  status: number,
) {
  return requestClient.put(
    '/api/privilege/user/status/batch',
    { ids, status },
    { responseReturn: 'body' },
  );
}

export async function updatePasswordApi(
  userId: number,
  oldPassword: string,
  newPassword: string,
) {
  return requestClient.put(
    '/api/privilege/user/password',
    { userId, oldPassword, newPassword },
    { responseReturn: 'body' },
  );
}

export async function resetPasswordApi(id: string) {
  return requestClient.put(
    `/api/privilege/user/reset-password/${id}`,
    undefined,
    { responseReturn: 'body' },
  );
}

export async function adminSetPasswordApi(userId: string, newPassword: string) {
  return requestClient.put(
    '/api/privilege/user/setPassword',
    { userId, newPassword },
    { responseReturn: 'body' },
  );
}
