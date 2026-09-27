import { requestClient } from '#/api/request';

/** 可生成字段（与后端 ProfileFieldEnm 对齐） */
export type ProfileField = 'DESCRIPTION' | 'PROMPT';

export interface ProfileGenerateRequest {
  name: string;
  description?: string;
  prompt?: string;
  targets: ProfileField[];
}

export interface ProfileGenerateResult {
  description?: null | string;
  prompt?: null | string;
  modelConfigId?: null | number;
  modelName?: null | string;
}

interface ApiResult<T> {
  code: string;
  data: T;
  msg: string;
  success: boolean;
}

const BASE = '/api/agent';

/**
 * Markdown 骨架（与后端 meta-prompt 必含段落同源，前端不再抄一份）
 */
export async function getProfileSkeletonApi() {
  return requestClient.get<ApiResult<string>>(
    `${BASE}/generate-profile/skeleton`,
    { responseReturn: 'body' },
  );
}

/**
 * AI 生成描述/提示词（后端只返回文本，不落库；由用户确认后随表单保存）
 */
export async function generateProfileApi(req: ProfileGenerateRequest) {
  return requestClient.post<ApiResult<ProfileGenerateResult>>(
    `${BASE}/generate-profile`,
    req,
    { responseReturn: 'body' },
  );
}
