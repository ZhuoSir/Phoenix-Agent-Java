import { requestClient } from '#/api/request';

export interface Agent {
  id?: number;
  type?: string;
  sn?: null | string;
  name?: string;
  description?: string;
  avatar?: null | string;
  status?: string;
  apiKey?: null | string;
  apiKeyEnabled?: number;
  prompt?: null | string;
  category?: null | string;
  adminId?: null | number;
  tags?: null | string;
  createTime?: string;
  updateTime?: string;
  humanReviewEnabled?: number;
}

export async function getAgentListApi(
  status?: string,
  keyword?: string,
): Promise<Agent[]> {
  const params: Record<string, string> = {};
  if (status) params.status = status;
  if (keyword) params.keyword = keyword;
  try {
    return (
      (await requestClient.get<Agent[]>('/platform/account-info/getMyAgents', { params })) ?? []
    );
  } catch {
    return [];
  }
}

/** 当前前台账号在某智能体下可见可用的技能（R-09 技能区数据源） */
export async function getMySkillsApi(agentId: number | string): Promise<
  Array<{ description?: string; id: number; name: string; status?: string }>
> {
  try {
    const res = await requestClient.get<{
      data: Array<{ description?: string; id: number; name: string; status?: string }>;
      success: boolean;
    }>('/platform/account-info/getMySkills', {
      params: { agentId },
      responseReturn: 'body',
    });
    return res?.data ?? [];
  } catch {
    return [];
  }
}
