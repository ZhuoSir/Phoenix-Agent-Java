import { requestClient } from '#/api/request';

/**
 * 组×MCP 授权（R-04）：组管理页「分配 MCP」dialog 专用；全量替换语义。
 * 后端入口见 GroupMcpController（/platform/group-mcp）。
 */
export async function groupMcpsApi(groupId: string) {
  const r = await requestClient.get<{ data: string[] }>(
    `/platform/group-mcp/${groupId}/mcps`,
    { responseReturn: 'body' },
  );
  return r.data ?? [];
}

export async function groupMcpAssignApi(groupId: string, mcpIds: string[]) {
  return requestClient.put<{ success: boolean }>(
    `/platform/group-mcp/${groupId}/assign`,
    { mcpIds },
    { responseReturn: 'body' },
  );
}

/** 反向：某 MCP 已授权给哪些组 */
export async function groupIdsOfMcpApi(mcpId: string) {
  const r = await requestClient.get<{ data: string[] }>(
    `/platform/group-mcp/mcp/${mcpId}/groups`,
    { responseReturn: 'body' },
  );
  return r.data ?? [];
}
