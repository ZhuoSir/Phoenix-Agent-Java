import { requestClient } from '#/api/request';

/**
 * 组×技能授权（R-04）：组管理页「分配技能」dialog 专用；全量替换语义。
 * 后端入口见 GroupSkillController（/platform/group-skill）。
 */
export async function groupSkillsApi(groupId: string) {
  const r = await requestClient.get<{ data: number[] }>(
    `/platform/group-skill/${groupId}/skills`,
    { responseReturn: 'body' },
  );
  return r.data ?? [];
}

export async function groupSkillAssignApi(groupId: string, skillIds: number[]) {
  return requestClient.put<{ success: boolean }>(
    `/platform/group-skill/${groupId}/assign`,
    { skillIds },
    { responseReturn: 'body' },
  );
}

/** 反向：某技能已授权给哪些组 */
export async function groupIdsOfSkillApi(skillId: number) {
  const r = await requestClient.get<{ data: string[] }>(
    `/platform/group-skill/skill/${skillId}/groups`,
    { responseReturn: 'body' },
  );
  return r.data ?? [];
}
