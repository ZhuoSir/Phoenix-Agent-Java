import { requestClient } from '#/api/request';

export interface ChatSession {
  id: string;
  agentId: number;
  title: string;
  status: string;
  isPinned: boolean;
  userId?: number;
  createTime?: string;
  updateTime?: string;
}

export interface ChatMessage {
  id?: number;
  sessionId: string;
  role: string;
  content: string;
  messageType: string;
  metadata?: string;
  createTime?: string;
  titleNeeded?: boolean;
}

const API_BASE_URL = '/api';

export async function getAgentSessionsApi(agentId: number, scope?: string): Promise<ChatSession[]> {
  const response = await requestClient.get<{ success: boolean; data: ChatSession[] }>(
    `${API_BASE_URL}/agent/${agentId}/sessions`,
    { responseReturn: 'body', params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    return [];
  }
  return response.data ?? [];
}

export async function createSessionApi(agentId: number, title?: string, userId?: number, scope?: string): Promise<ChatSession | null> {
  const response = await requestClient.post<{ success: boolean; data: ChatSession }>(
    `${API_BASE_URL}/agent/${agentId}/sessions`,
    { title, userId },
    { responseReturn: 'body', params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    return null;
  }
  return response.data;
}

export async function clearAgentSessionsApi(agentId: number, scope?: string): Promise<void> {
  const response = await requestClient.delete<{ success: boolean; message?: string }>(
    `${API_BASE_URL}/agent/${agentId}/sessions`,
    { responseReturn: 'body', params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    throw new Error(response.message || '清除会话失败');
  }
}

export async function getSessionMessagesApi(sessionId: string, scope?: string): Promise<ChatMessage[]> {
  const response = await requestClient.get<{ success: boolean; data: ChatMessage[] }>(
    `${API_BASE_URL}/sessions/${sessionId}/messages`,
    // BUG-76：长轮期间后端负载高，默认超时易触发前端"请求超时"toast 与误判 → 放宽到 60s
    { responseReturn: 'body', timeout: 60_000, params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    return [];
  }
  return response.data ?? [];
}

export async function saveMessageApi(sessionId: string, message: ChatMessage, scope?: string): Promise<void> {
  const response = await requestClient.post<{ success: boolean; message?: string }>(
    `${API_BASE_URL}/sessions/${sessionId}/messages`,
    { ...message, sessionId },
    { responseReturn: 'body', params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    throw new Error(response.message || '保存消息失败');
  }
}

export async function pinSessionApi(sessionId: string, isPinned: boolean, scope?: string): Promise<void> {
  const response = await requestClient.put<{ success: boolean; message?: string }>(
    `${API_BASE_URL}/sessions/${sessionId}/pin`,
    null,
    { params: { isPinned, ...(scope ? { scope } : {}) }, responseReturn: 'body' },
  );
  if (!response.success) {
    throw new Error(response.message || '置顶操作失败');
  }
}

export async function renameSessionApi(sessionId: string, title: string, scope?: string): Promise<void> {
  const response = await requestClient.put<{ success: boolean; message?: string }>(
    `${API_BASE_URL}/sessions/${sessionId}/rename`,
    null,
    { params: { title: title.trim(), ...(scope ? { scope } : {}) }, responseReturn: 'body' },
  );
  if (!response.success) {
    throw new Error(response.message || '重命名失败');
  }
}

export async function deleteSessionApi(sessionId: string, scope?: string): Promise<void> {
  const response = await requestClient.delete<{ success: boolean; message?: string }>(
    `${API_BASE_URL}/sessions/${sessionId}`,
    { responseReturn: 'body', params: scope ? { scope } : undefined },
  );
  if (!response.success) {
    throw new Error(response.message || '删除会话失败');
  }
}

export async function downloadHtmlReportApi(sessionId: string, content: string): Promise<void> {
  const token = localStorage.getItem('phoenix-token');
  const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/reports/html`, {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain;charset=utf-8',
      ...(token ? { 'phoenix-token': token } : {}),
    },
    body: content,
  });
  const blob = await response.blob();
  const contentDisposition = response.headers.get('content-disposition');
  let filename = 'report.html';
  if (contentDisposition) {
    const filenameMatch = contentDisposition.match(/filename="?([^;"]+)"?/);
    if (filenameMatch?.[1]) {
      filename = filenameMatch[1];
    }
  }
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
