import { requestClient } from '#/api/request';
import type { GraphNodeResponse, GraphRequest } from '#/api';
import { TextType } from '#/api';

export interface ChatSession {
  id: string;
  title: string;
  preview: string;
  agentId: string;
  updatedAt: number;
}

export interface ApiChatMessage {
  id?: number;
  sessionId: string;
  role: string;
  content: string;
  messageType?: string;
  metadata?: string;
  createTime?: string;
  titleNeeded?: boolean;
}

export async function getAgentSessionsApi(
  agentId: number | string,
): Promise<ChatSession[]> {
  try {
    return (
      (await requestClient.get<ChatSession[]>(
        `/api/agent/${agentId}/sessions`,
      )) ?? []
    );
  } catch {
    return [];
  }
}

export async function getSessionMessagesApi(
  sessionId: string,
): Promise<ApiChatMessage[]> {
  try {
    return (
      (await requestClient.get<ApiChatMessage[]>(
        `/api/sessions/${sessionId}/messages`,
      )) ?? []
    );
  } catch {
    return [];
  }
}

export async function renameSessionApi(
  sessionId: string,
  title: string,
): Promise<void> {
  await requestClient.put(`/api/sessions/${sessionId}/rename`, null, {
    params: { title: title.trim() },
  });
}

export async function pinSessionApi(
  sessionId: string,
  isPinned: boolean,
): Promise<void> {
  await requestClient.put(`/api/sessions/${sessionId}/pin`, null, {
    params: { isPinned },
  });
}

export async function deleteSessionApi(sessionId: string): Promise<void> {
  await requestClient.delete(`/api/sessions/${sessionId}`);
}

export interface PresetQuestion {
  id?: number;
  question?: string;
  answer?: string;
  sortOrder?: number;
  isActive?: boolean;
}

export interface AddPresetQuestionRequest {
  agentId: number;
  question: string;
  sortOrder?: number;
}

export async function addPresetQuestionApi(
  data: AddPresetQuestionRequest,
): Promise<PresetQuestion> {
  return requestClient.post<PresetQuestion>(
    '/api/front/addPresetQuestion',
    data,
  );
}

export async function deletePresetQuestionApi(
  questionId: number,
): Promise<void> {
  await requestClient.delete(
    `/api/front/deletePresetQuestion/${questionId}`,
  );
}

export async function getPresetQuestionsApi(
  agentId: number,
): Promise<PresetQuestion[]> {
  try {
    return (
      (await requestClient.get<PresetQuestion[]>(
        `/api/front/${agentId}/preset-questions`,
      )) ?? []
    );
  } catch {
    return [];
  }
}

export interface FrontChatStreamRequest {
  sessionId: string;
  content: string;
  agentSn: string;
  type: string;
}

const API_BASE_URL = '/api';

function getAuthToken(): string {
  return localStorage.getItem('phoenix-token') || '';
}

export function streamFrontChat(
  request: FrontChatStreamRequest,
  onMessage: (response: GraphNodeResponse) => Promise<void>,
  onError?: (error: Error) => Promise<void>,
  onComplete?: () => Promise<void>,
): () => void {
  const url = `${API_BASE_URL}/front/stream/chat`;
  const controller = new AbortController();

  const doFetch = async () => {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'phoenix-token': getAuthToken(),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) {
        throw new Error('No reader available');
      }

      let buffer = '';
      let endDispatched = false;
      let currentData = '';

      const dispatchEvent = async () => {
        if (currentData) {
          try {
            const parsed = JSON.parse(currentData);
            if (parsed.end) {
              endDispatched = true;
              await onComplete?.();
              return;
            }
            const nodeResponse: GraphNodeResponse = {
              agentId: request.agentSn,
              threadId: request.sessionId,
              nodeName: 'Agent',
              textType: TextType.TEXT,
              text: parsed.content || '',
              error: false,
              complete: false,
            };
            await onMessage(nodeResponse);
          } catch {
            await onError?.(new Error('Failed to parse server response'));
          }
        }
        currentData = '';
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!(value instanceof Uint8Array)) {
          console.error('[SSE] streamFrontChat: received non-Uint8Array chunk', typeof value);
          continue;
        }
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n');
        buffer = parts.pop() || '';
        for (const line of parts) {
          if (line === '') {
            await dispatchEvent();
          } else if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            currentData = line.slice(5).trim();
          }
        }
      }
      if (buffer) {
        const line = buffer.trim();
        if (line.startsWith('data:')) {
          currentData = line.slice(5).trim();
          await dispatchEvent();
        }
      }
      // BUG-46：流被关闭但没有 end 帧（中断/刷新/断网）→ 兜底触发 onComplete，保住已生成内容
      if (!endDispatched) {
        endDispatched = true;
        await onComplete?.();
      }
    } catch (error: any) {
      if (error.name === 'AbortError') return;
      await onError?.(new Error('Stream connection failed'));
    }
  };

  doFetch();

  return () => {
    controller.abort();
  };
}

export interface FrontHarnessChatRequest {
  sessionId: string;
  message: string;
  /** 前台按智能体 id 寻址（唯一寻址方式，R-08；后端据此做组可见性 + 运行时解析） */
  agentId: number | string;
  /** 存量自注册智能体的 sn（兼容字段，agentId 优先） */
  harnessSn?: string;
  /** 显式执行的技能 id（R-09：不传=模型自主匹配） */
  enabledSkillIds?: number[];
}

export interface FrontHarnessConfirmRequest {
  sessionId: string;
  agentSn: string;
  /** BUG-11(T-08)：库配置智能体 sn 为空，agentId 主寻址 */
  agentId?: number | string;
  allowed: boolean;
  suggestedRules?: any[];
}

export function streamFrontHarnessChat(
  request: FrontHarnessChatRequest,
  onMessage: (response: GraphNodeResponse) => Promise<void>,
  onError?: (error: Error) => Promise<void>,
  onComplete?: () => Promise<void>,
): () => void {
  const url = `/platform/harness/chat`; // BL-18: 裸域直连（曾靠代理剥层）
  const controller = new AbortController();

  const doFetch = async () => {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'phoenix-token': getAuthToken(),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) {
        throw new Error('No reader available');
      }

      let buffer = '';
      let endDispatched = false;
      let currentData = '';

      const dispatchEvent = async () => {
        if (currentData) {
          try {
            const parsed = JSON.parse(currentData);
            if (parsed.end) {
              endDispatched = true;
              await onComplete?.();
              return;
            }
            const nodeResponse: GraphNodeResponse = {
              agentId: String(request.agentId),
              threadId: request.sessionId,
              nodeName: 'Harness',
              textType: TextType.MARK_DOWN,
              text: parsed.content || '',
              error: false,
              complete: false,
              needConfirm: parsed.needConfirm || false,
              toolCalls: parsed.toolCalls || undefined,
              buttons: parsed.buttons || undefined,
              // thinking-display R-01：思考增量独立通道（旧帧无此键=undefined）
              thinking: parsed.thinking || undefined,
            };
            await onMessage(nodeResponse);
          } catch {
            await onError?.(new Error('Failed to parse server response'));
          }
        }
        currentData = '';
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!(value instanceof Uint8Array)) {
          console.error('[SSE] streamFrontChat: received non-Uint8Array chunk', typeof value);
          continue;
        }
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n');
        buffer = parts.pop() || '';
        for (const line of parts) {
          if (line === '') {
            await dispatchEvent();
          } else if (line.startsWith('data:')) {
            currentData = line.slice(5).trim();
          }
        }
      }
      if (buffer) {
        const line = buffer.trim();
        if (line.startsWith('data:')) {
          currentData = line.slice(5).trim();
          await dispatchEvent();
        }
      }
      // BUG-46：流被关闭但没有 end 帧（中断/刷新/断网）→ 兜底触发 onComplete，保住已生成内容
      if (!endDispatched) {
        endDispatched = true;
        await onComplete?.();
      }
    } catch (error: any) {
      if (error.name === 'AbortError') return;
      await onError?.(new Error('Stream connection failed'));
    }
  };

  doFetch();

  return () => {
    controller.abort();
  };
}

/** BL-22 架构修正：前台确认同样只发放行信号，原 send 流续播 */
export async function confirmFrontHarnessSignal(request: FrontHarnessConfirmRequest): Promise<void> {
  try {
    const resp = await fetch(`${API_BASE_URL}/front/harness/confirm`, {
      method: 'POST',
      headers: {
        'phoenix-token': getAuthToken(),
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(request),
    });
    await resp.body?.cancel().catch(() => {});
  } catch { /* ignore */ }
}

export async function confirmFrontHarnessChat(
  request: FrontHarnessConfirmRequest,
  onMessage?: (response: GraphNodeResponse) => Promise<void>,
  onComplete?: () => Promise<void>,
): Promise<void> {
  const httpResponse = await fetch(`${API_BASE_URL}/front/harness/confirm`, {
    method: 'POST',
    headers: {
      'phoenix-token': getAuthToken(),
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(request),
  });
  if (!httpResponse.ok) {
    throw new Error(`Confirm request failed! status: ${httpResponse.status}`);
  }
  const reader = httpResponse.body?.getReader();
  const decoder = new TextDecoder();
  if (!reader) return;
  let buffer = '';
      let endDispatched = false;
  let currentData = '';
  const dispatchEvent = async () => {
    if (currentData) {
      try {
        const parsed = JSON.parse(currentData);
        if (parsed.end) {
          endDispatched = true;
          await onComplete?.();
          return;
        }
        const nodeResponse: GraphNodeResponse = {
          agentId: request.agentSn,
          threadId: request.sessionId,
          nodeName: 'Harness',
          textType: TextType.MARK_DOWN,
          text: parsed.content || '',
          error: false,
          complete: false,
        };
        await onMessage?.(nodeResponse);
      } catch {
        // skip parse errors
      }
    }
    currentData = '';
  };
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) {
        console.error('[SSE] admin-front: received non-Uint8Array chunk', typeof value);
        throw new Error(`SSE stream received invalid chunk type: ${typeof value}`);
      }
      if (value.length === 0) continue;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split('\n');
      buffer = parts.pop() || '';
      for (const line of parts) {
        if (line === '') {
          await dispatchEvent();
        } else if (line.startsWith('data:')) {
          currentData = line.slice(5).trim();
        }
      }
    }
    if (buffer) {
      const line = buffer.trim();
      if (line.startsWith('data:')) {
        currentData = line.slice(5).trim();
        await dispatchEvent();
      }
    }
  } catch (error: any) {
    if (error.name === 'AbortError') return;
    // 连接异常中断但已有部分数据：刷出剩余内容后优雅结束
    if (currentData || buffer?.trim()) {
      if (buffer) {
        const line = buffer.trim();
        if (line.startsWith('data:')) {
          currentData = line.slice(5).trim();
          await dispatchEvent();
        }
      }
      await onComplete?.();
      return;
    }
    throw new Error(`SSE stream error: ${error.message}`);
  }
}

export function streamFrontChatSql(
  request: GraphRequest,
  onMessage: (response: GraphNodeResponse) => Promise<void>,
  onError?: (error: Error) => Promise<void>,
  onComplete?: () => Promise<void>,
): () => void {
  const params = new URLSearchParams();
  params.append('agentId', request.agentId);
  if (request.threadId) {
    params.append('threadId', request.threadId);
  }
  params.append('query', request.query);
  params.append('humanFeedback', request.humanFeedback.toString());
  params.append('rejectedPlan', request.rejectedPlan.toString());
  params.append('nl2sqlOnly', request.nl2sqlOnly.toString());
  if (request.humanFeedbackContent) {
    params.append('humanFeedbackContent', request.humanFeedbackContent);
  }

  const url = `${API_BASE_URL}/api/front/stream/chatsql?${params.toString()}`;
  const controller = new AbortController();
  let isCompleted = false;

  const doFetch = async () => {
    try {
      const response = await fetch(url, {
        headers: {
          'phoenix-token': getAuthToken(),
          Accept: 'text/event-stream',
        },
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) {
        throw new Error('No reader available');
      }

      let buffer = '';
      let endDispatched = false;
      let currentEvent = '';
      let currentData = '';

      const dispatchEvent = async () => {
        if (currentEvent === 'complete') {
          isCompleted = true;
          await onComplete?.();
          return;
        }
        if (currentData) {
          try {
            const nodeResponse: GraphNodeResponse = JSON.parse(currentData);
            await onMessage(nodeResponse);
          } catch {
            await onError?.(new Error('Failed to parse server response'));
          }
        }
        currentEvent = '';
        currentData = '';
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!(value instanceof Uint8Array)) {
          console.error('[SSE] streamFrontChat: received non-Uint8Array chunk', typeof value);
          continue;
        }
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n');
        buffer = parts.pop() || '';
        for (const line of parts) {
          if (line === '') {
            await dispatchEvent();
          } else if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            currentData = line.slice(5).trim();
          }
        }
      }
      if (buffer) {
        if (buffer.startsWith('data:')) {
          currentData = buffer.slice(5).trim();
        } else if (buffer.startsWith('event:')) {
          currentEvent = buffer.slice(6).trim();
        }
        await dispatchEvent();
      }
      // BUG-46：流被关闭但没有 end 帧（中断/刷新/断网）→ 兜底触发 onComplete，保住已生成内容
      if (!endDispatched) {
        endDispatched = true;
        await onComplete?.();
      }
    } catch (error: any) {
      if (error.name === 'AbortError') return;
      if (isCompleted) return;
      await onError?.(new Error('Stream connection failed'));
    }
  };

  doFetch();

  return () => {
    controller.abort();
  };
}
