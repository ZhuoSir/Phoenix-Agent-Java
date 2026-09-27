import { requestClient } from '#/api/request';

/** 技能状态：draft=草稿 published=已发布 */
export interface SkillItem {
  description?: string;
  id: number;
  name: string;
  source?: string;
  status: string;
  updatedAt?: string;
}

export interface SkillResource {
  content?: string;
  path: string;
}

export interface SkillDetail extends SkillItem {
  resources?: SkillResource[];
  skillContent?: string;
}

export interface SkillRef {
  authorizedGroupCount: number;
  boundAgentCount: number;
}

export interface SkillOption {
  bound: boolean;
  description?: string;
  name: string;
  skillId: number;
  status: string;
}

export interface SkillPageResult {
  records: SkillItem[];
  totalRow: number;
}

/** 技能分页列表（管理视角全量） */
export async function getSkillPageApi(
  pageNum: number,
  pageSize: number,
  params?: Record<string, any>,
) {
  return requestClient.get<{
    code: string;
    data: SkillPageResult;
    msg: string;
    success: boolean;
  }>('/api/skill', {
    params: { pageNum, pageSize, ...params },
    responseReturn: 'body',
  });
}

/** 技能详情（正文 + 资源清单） */
export async function getSkillDetailApi(id: number) {
  return requestClient.get<{
    code: string;
    data: SkillDetail;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}`, { responseReturn: 'body' });
}

/** 上传技能 ZIP；overwrite=true 时同名覆盖 */
export async function uploadSkillApi(file: File, overwrite = false) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('overwrite', String(overwrite));
  return requestClient.post<{
    code: string;
    data: number;
    msg: string;
    success: boolean;
  }>('/api/skill/upload', formData, {
    responseReturn: 'body',
  });
}

/** 发布（可携带授权组） */
export async function publishSkillApi(id: number, groupIds: string[] = []) {
  return requestClient.post<{
    code: string;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}/publish`, { groupIds }, { responseReturn: 'body' });
}

/** 下线 */
export async function offlineSkillApi(id: number) {
  return requestClient.post<{
    code: string;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}/offline`, {}, { responseReturn: 'body' });
}

/** 调整授权组（即时生效） */
export async function updateSkillGroupsApi(id: number, groupIds: string[]) {
  return requestClient.put<{
    code: string;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}/groups`, { groupIds }, { responseReturn: 'body' });
}

/** 删除前引用计数 */
export async function getSkillRefsApi(id: number) {
  return requestClient.get<{
    code: string;
    data: SkillRef;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}/refs`, { responseReturn: 'body' });
}

/** 删除（仅草稿） */
export async function deleteSkillApi(id: number) {
  return requestClient.delete<{
    code: string;
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}`, { responseReturn: 'body' });
}

/** 已授权组 id（授权界面回显） */
export async function getSkillGroupsApi(id: number) {
  return requestClient.get<{
    code: string;
    data: string[];
    msg: string;
    success: boolean;
  }>(`/api/skill/${id}/groups`, { responseReturn: 'body' });
}

/** 智能体编辑页可选池 */
export async function getSkillOptionsApi(agentId: number) {
  return requestClient.get<{
    code: string;
    data: SkillOption[];
    msg: string;
    success: boolean;
  }>('/api/skill/options', { params: { agentId }, responseReturn: 'body' });
}

/** 智能体已绑定技能 id */
export async function getAgentBoundSkillsApi(agentId: number) {
  return requestClient.get<{
    code: string;
    data: number[];
    msg: string;
    success: boolean;
  }>(`/api/skill/binding/agent/${agentId}`, { responseReturn: 'body' });
}

/** 覆盖式保存智能体技能绑定 */
export async function bindAgentSkillsApi(agentId: number, skillIds: number[]) {
  return requestClient.put<{
    code: string;
    msg: string;
    success: boolean;
  }>(`/api/skill/binding/agent/${agentId}`, { skillIds }, { responseReturn: 'body' });
}
