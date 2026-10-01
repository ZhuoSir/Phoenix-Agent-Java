import { requestClient } from '#/api/request';

/** 知识库实体（data 域 /api/knowledge-base） */
export interface KnowledgeBase {
  id: number;
  name: string;
  description?: null | string;
  status: number;
  creator?: null | string;
  createTime?: string;
  updateTime?: string;
  groupNames: string[];
  itemCount: number;
  boundAgentNames?: null | string[];
}

interface Envelope<T = any> {
  success: boolean;
  message?: string;
  data?: T;
}

export async function kbPageApi(query: { name?: string; pageNum: number; pageSize: number; status?: number }) {
  return requestClient.post<Envelope<KnowledgeBase[]> & { total?: number }>(
    '/api/knowledge-base/query/page', query, { responseReturn: 'body' },
  );
}

export async function kbCreateApi(body: { description?: string; name: string }) {
  return requestClient.post<Envelope<KnowledgeBase>>('/api/knowledge-base', body, { responseReturn: 'body' });
}

export async function kbUpdateApi(body: { description?: string; id: number; name?: string; status?: number }) {
  return requestClient.put<Envelope<KnowledgeBase>>('/api/knowledge-base', body, { responseReturn: 'body' });
}

export async function kbRemoveApi(id: number) {
  return requestClient.delete<Envelope<boolean>>(`/api/knowledge-base/${id}`, { responseReturn: 'body' });
}

/** 智能体绑定候选（platform ReturnVo 信封） */
export interface BindableKbase {
  id: number;
  name: string;
  status: number;
  itemCount: number;
  bound: boolean;
  selectable: boolean;
  disabledReason?: null | string;
}

export async function kbaseBindableApi(agentId: number) {
  const r = await requestClient.get<{ code: string; data: BindableKbase[]; success: boolean }>(
    `/platform/agent-kbase/agent/${agentId}/bindable`, { responseReturn: 'body' },
  );
  return r.data ?? [];
}

export async function kbaseBindApi(agentId: number, kbaseIds: number[]) {
  return requestClient.put<{ success: boolean; msg?: string }>(
    `/platform/agent-kbase/agent/${agentId}/bind`, { kbaseIds }, { responseReturn: 'body' },
  );
}

/** 组×知识库分配（platform） */
export async function groupKbasesApi(groupId: string) {
  const r = await requestClient.get<{ data: number[] }>(
    `/platform/group-kbase/${groupId}/kbases`, { responseReturn: 'body' },
  );
  return r.data ?? [];
}

export async function kbaseGroupsApi(kbaseId: number) {
  const r = await requestClient.get<{ data: string[] }>(
    `/platform/group-kbase/kbase/${kbaseId}/groups`, { responseReturn: 'body' },
  );
  return r.data ?? [];
}

export async function groupKbaseAssignApi(groupId: string, kbaseIds: number[]) {
  return requestClient.put<{ success: boolean }>(
    `/platform/group-kbase/${groupId}/assign`, { kbaseIds }, { responseReturn: 'body' },
  );
}
