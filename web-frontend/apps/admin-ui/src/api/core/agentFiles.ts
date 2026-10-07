import { requestClient } from '#/api/request';

/** 会话产物文件（BL-19） */
export interface AgentFileItem {
  id: string;
  fileName: string;
  sizeBytes: number;
  mime?: null | string;
  source: string;
  createTime: string;
}

export async function listAgentFilesApi(sessionId: string, scan = false) {
  return requestClient.get<AgentFileItem[]>('/api/agent/files', {
    params: { sessionId, scan },
    responseReturn: 'body',
  });
}

/** 会话文件树节点（v1.7.0 R-02）：单层返回；dir/file/history 三型 */
export const HISTORY_PATH = '__history__';

export interface AgentFileTreeNode {
  type: 'dir' | 'file' | 'history';
  name: string;
  /** 会话内相对路径（已折叠 {uid} 层）；history 型为固定 `__history__` */
  path: string;
  dirCount?: number;
  fileCount?: number;
  id?: string;
  sizeBytes?: number;
  mime?: null | string;
  source?: string;
  createTime?: string;
}

/** 会话文件树单层（payload 只含当前层） */
export interface AgentFileTreeLevel {
  rootName: string;
  path: string;
  parentPath: string;
  dirTotal: number;
  fileTotal: number;
  historyTotal: number;
  entries: AgentFileTreeNode[];
}

export async function getAgentFileTreeApi(sessionId: string, path = '', scan = true) {
  return requestClient.get<AgentFileTreeLevel>('/api/agent/files/tree', {
    params: { sessionId, path, scan },
    responseReturn: 'body',
  });
}

/** 下载走原生 fetch（需要 blob + 自定义头），带 token 与错误信封双兼容 */
export async function downloadAgentFileApi(id: string, fileName: string, inline = false) {
  const token = localStorage.getItem('phoenix-token') || '';
  const resp = await fetch(
    `/api/agent/files/${id}/download${inline ? '?inline=1' : ''}`,
    { headers: { 'phoenix-token': token } },
  );
  const ct = resp.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    const body = await resp.json();
    throw new Error(body?.msg || '下载失败');
  }
  const blob = await resp.blob();
  const url = URL.createObjectURL(blob);
  if (inline) {
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return;
  }
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

export async function deleteAgentFileApi(id: string) {
  return requestClient.delete<boolean>(`/api/agent/files/${id}`, {
    responseReturn: 'body',
  });
}

/** 消息内容物化为会话文件（报告 HTML「另存为文件」） */
export async function materializeAgentFileApi(sessionId: string, fileName: string, content: string) {
  return requestClient.post<AgentFileItem>('/api/agent/files/materialize',
    { sessionId, fileName, content }, { responseReturn: 'body' });
}

/** 轻量刷新广播：物化成功 / SSE agentFiles 到达时通知面板 */
export const FILES_CHANGED_EVENT = 'phoenix:agent-files-changed';

export function notifyFilesChanged() {
  window.dispatchEvent(new CustomEvent(FILES_CHANGED_EVENT));
}
