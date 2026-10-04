import { requestClient } from '#/api/request';

/** MCP 列表行 */
export interface McpItem {
  boundCount: number;
  description?: string;
  groupCount: number;
  id: string;
  name: string;
  status: string;
  transport: string;
  updateTime?: string;
}

/** MCP 详情（敏感值已脱敏回显） */
export interface McpDetailVO {
  args?: string[];
  boundAgentIds?: number[];
  command?: string;
  createTime?: string;
  description?: string;
  enableTools?: string[];
  env?: Record<string, string>;
  groupIds?: string[];
  headers?: Record<string, string>;
  id: string;
  initTimeoutMs?: number;
  name: string;
  status: string;
  timeoutMs?: number;
  transport: string;
  updateTime?: string;
  url?: string;
}

export interface McpSavePayload {
  args?: string[];
  command?: string;
  description?: string;
  enableTools?: string[];
  env?: Record<string, string>;
  headers?: Record<string, string>;
  id?: string;
  initTimeoutMs?: number;
  name: string;
  status?: string;
  timeoutMs?: number;
  transport: string;
  url?: string;
}

export interface McpTestResult {
  elapsedMs?: number;
  error?: string;
  success: boolean;
  toolCount?: number;
  toolNames?: string[];
}

/** 分页列表 */
export async function getMcpPageApi(pageNum: number, pageSize: number, params?: Record<string, any>) {
  return requestClient.get<any>('/api/mcp', {
    params: { pageNum, pageSize, ...params },
    responseReturn: 'body',
  });
}

/** 详情 */
export async function getMcpDetailApi(id: string) {
  return requestClient.get<any>(`/api/mcp/${id}`, { responseReturn: 'body' });
}

/** 新建/编辑（掩码值回传=保留原密文） */
export async function saveMcpApi(payload: McpSavePayload) {
  return requestClient.post<any>('/api/mcp', payload, { responseReturn: 'body' });
}

/** 启用/停用 */
export async function toggleMcpStatusApi(id: string, status: string) {
  return requestClient.put<any>(`/api/mcp/${id}/status`, null, {
    params: { status },
    responseReturn: 'body',
  });
}

/** 删除（被绑定时服务端拒绝并返回绑定智能体） */
export async function deleteMcpApi(id: string) {
  return requestClient.delete<any>(`/api/mcp/${id}`, { responseReturn: 'body' });
}

/** 组授权（覆盖式） */
export async function grantMcpGroupsApi(id: string, groupIds: string[]) {
  return requestClient.put<any>(`/api/mcp/${id}/groups`, groupIds, { responseReturn: 'body' });
}

/** 测试连接（按当前表单即时试连，不落库） */
export async function testMcpApi(payload: Record<string, any>) {
  return requestClient.post<any>('/api/mcp/test', payload, { responseReturn: 'body' });
}
