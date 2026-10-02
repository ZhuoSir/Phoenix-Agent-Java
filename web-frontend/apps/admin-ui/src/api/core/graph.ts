export interface GraphRequest {
  agentId: string;
  threadId?: string;
  query: string;
  humanFeedback: boolean;
  humanFeedbackContent?: string;
  rejectedPlan: boolean;
  nl2sqlOnly: boolean;
}

export interface ConfirmButton {
  text: string;
  action: string;
  type?: string;
}

export interface GraphNodeResponse {
  /** 思考增量（thinking-display R-01，独立于 text 正文通道） */
  thinking?: string;
  agentId: string;
  threadId: string;
  nodeName: string;
  textType: TextType;
  text: string;
  error: boolean;
  complete: boolean;
  needConfirm?: boolean;
  toolCalls?: any;
  buttons?: ConfirmButton[];
}

export interface HarnessConfirmRequest {
  sessionId: string;
  /** 智能体ID（R-08 统一寻址） */
  agentId: number;
  /** 存量自注册智能体的 sn（兼容字段，agentId 优先） */
  agentSn?: string;
  allowed: boolean;
  suggestedRules?: any[];
}

export enum TextType {
  JSON = 'JSON',
  PYTHON = 'PYTHON',
  SQL = 'SQL',
  HTML = 'HTML',
  MARK_DOWN = 'MARK_DOWN',
  RESULT_SET = 'RESULT_SET',
  TEXT = 'TEXT',
}

export interface HarnessChatRequest {
  sessionId: string;
  message: string;
  /** 智能体ID（R-08 统一寻址；后端据此走运行时注册表） */
  agentId: number;
  /** 存量自注册智能体的 sn（兼容字段，agentId 优先） */
  harnessSn?: string;
  /** 显式执行的技能 id（由前台/运行页技能区传入） */
  enabledSkillIds?: number[];
}

const API_BASE_URL = ''; // BL-18: 拼接串已含 /api 真实前缀

export interface ChatApiRequest {
  sessionId: string;
  content: string;
  agentSn: string;
  type: string;
}

export function streamChat(
  request: ChatApiRequest,
  onMessage: (response: GraphNodeResponse) => Promise<void>,
  onError?: (error: Error) => Promise<void>,
  onComplete?: () => Promise<void>,
): () => void {
  const url = `${API_BASE_URL}/api/admin/agent/chat`;
  const controller = new AbortController();

  const doFetch = async () => {
    try {
      const token = localStorage.getItem('phoenix-token');
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'phoenix-token': token || '',
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
      let currentData = '';

      const dispatchEvent = async () => {
        if (currentData) {
          try {
            const parsed = JSON.parse(currentData);
            if (parsed.end || parsed.complete) {
              await onComplete?.();
              return;
            }
            if (parsed.error) {
              await onError?.(new Error(parsed.content || parsed.text || 'Unknown error'));
              return;
            }
            const textTypeValue = parsed.textType || 'TEXT';
            const nodeResponse: GraphNodeResponse = {
              agentId: request.agentSn,
              threadId: request.sessionId,
              nodeName: parsed.nodeName || 'Agent',
              textType: Object.values(TextType).includes(textTypeValue)
                ? (textTypeValue as TextType)
                : TextType.TEXT,
              text: parsed.content || parsed.text || '',
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
          console.error('[SSE] admin-core: received non-Uint8Array chunk', typeof value);
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

export function streamSearch(
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

  const url = `${API_BASE_URL}/api/admin/agent/stream/chatsql?${params.toString()}`;
  const controller = new AbortController();
  let isCompleted = false;

  const doFetch = async () => {
    try {
      const token = localStorage.getItem('phoenix-token');
      const response = await fetch(url, {
        headers: {
          'phoenix-token': token || '',
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
        if (done) {
          break;
        }
        if (!(value instanceof Uint8Array)) {
          console.error('[SSE] admin-streamSearch: received non-Uint8Array chunk', typeof value);
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

/** detached-stream T-05：admin 域轮次状态/取消 */
export async function harnessTurnStatusApi(sessionId: string): Promise<boolean> {
  const token = localStorage.getItem('phoenix-token') || '';
  try {
    const resp = await fetch(`/api/admin/harness/turn/status?sessionId=${encodeURIComponent(sessionId)}`, { headers: { 'phoenix-token': token } });
    return ((await resp.json()) as any)?.data === true;
  } catch {
    return false;
  }
}

export async function harnessTurnCancelApi(sessionId: string): Promise<void> {
  const token = localStorage.getItem('phoenix-token') || '';
  try {
    await fetch(`/api/admin/harness/turn/cancel?sessionId=${encodeURIComponent(sessionId)}`, { method: 'POST', headers: { 'phoenix-token': token } });
  } catch { /* ignore */ }
}

export function streamHarnessChat(
  request: HarnessChatRequest,
  onMessage: (response: GraphNodeResponse) => Promise<void>,
  onError?: (error: Error) => Promise<void>,
  onComplete?: () => Promise<void>,
): () => void {
  const url = `${API_BASE_URL}/api/admin/harness/chat`;
  const controller = new AbortController();

  const doFetch = async () => {
    try {
      const token = localStorage.getItem('phoenix-token');
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'phoenix-token': token || '',
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
      let currentData = '';

      const dispatchEvent = async () => {
        if (currentData) {
          try {
            const parsed = JSON.parse(currentData);
            if (parsed.end) {
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
              // thinking-display T-04 修复：字段白名单曾漏透 thinking，admin 思考通道的断点在此
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
          console.error('[SSE] admin-core: received non-Uint8Array chunk', typeof value);
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

/** BL-22 架构修正：确认只发放行信号——原 chat 流在等待期保持打开并续播，禁止二开消费流 */
export async function confirmHarnessSignalApi(sessionId: string, agentId: number | string | null, allowed: boolean): Promise<void> {
  const token = localStorage.getItem('phoenix-token') || '';
  try {
    const resp = await fetch('/api/admin/harness/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'phoenix-token': token },
      body: JSON.stringify({ sessionId, agentId, allowed }),
    });
    // 服务端返回的是本 attach 视图的 SSE——立即取消，数据由原流承接
    await resp.body?.cancel().catch(() => {});
  } catch { /* 放行失败由原流超时兜底 */ }
}

export async function confirmHarnessChat(
  request: HarnessConfirmRequest,
  onMessage?: (response: GraphNodeResponse) => Promise<void>,
  onComplete?: () => Promise<void>,
): Promise<void> {
  const token = localStorage.getItem('phoenix-token');
  console.log('[confirmHarnessChat] sending request', request);
  const httpResponse = await fetch(`${API_BASE_URL}/api/admin/harness/confirm`, {
    method: 'POST',
    headers: {
      'phoenix-token': token || '',
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(request),
  });
  console.log('[confirmHarnessChat] response status', httpResponse.status);
  if (!httpResponse.ok) {
    throw new Error(`Confirm request failed! status: ${httpResponse.status}`);
  }
  const reader = httpResponse.body?.getReader();
  const decoder = new TextDecoder();
  if (!reader) return;
  let buffer = '';
  let currentData = '';
  const dispatchEvent = async () => {
    if (currentData) {
      try {
        const parsed = JSON.parse(currentData);
        if (parsed.end) {
          await onComplete?.();
          return;
        }
        const nodeResponse: GraphNodeResponse = {
          agentId: String(request.agentId ?? request.agentSn ?? ''),
          threadId: request.sessionId,
          nodeName: 'Harness',
          textType: TextType.MARK_DOWN,
          text: parsed.content || '',
          // thinking-display/BL-22：确认流同键透传思考增量
          thinking: parsed.thinking || undefined,
          error: false,
          complete: false,
        };
        await onMessage?.(nodeResponse);
        console.log('[confirmHarnessChat] received', parsed);
      } catch (e) {
        console.error('[confirmHarnessChat] parse error:', currentData, e);
      }
    }
    currentData = '';
  };
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) {
        console.error('[SSE] admin-confirmHarnessChat: received non-Uint8Array chunk', typeof value);
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
