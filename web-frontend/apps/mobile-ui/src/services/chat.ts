import type { ChatMessage, ChatSession } from '@phoenix/chat-shared';

import { http } from './http';

// ---- Session APIs ----

interface BackendChatSession {
  id: string;
  title?: string;
  preview?: string;
  agentId?: string;
  updateTime?: string;
  isPinned?: boolean;
  createTime?: string;
  [key: string]: unknown;
}

// CR-03（R-13）：mobile 属前台聊天空间 = FRONT_CHAT（与 admin 运行页 ADMIN_RUN 隔离）
const SESSION_SCOPE = 'FRONT_CHAT';

export async function getAgentSessionsApi(
  agentId: string,
): Promise<ChatSession[]> {
  try {
    const data = await http.get<BackendChatSession[]>(
      `/api/agent/${agentId}/sessions?scope=${SESSION_SCOPE}`,
    );
    return (data ?? []).map((s) => ({
      id: String(s.id),
      title: s.title || '',
      preview: s.preview || '',
      agentId: String(s.agentId ?? agentId),
      updatedAt: s.updateTime
        ? new Date(s.updateTime).getTime()
        : Date.now(),
      isPinned: !!s.isPinned,
    }));
  } catch {
    return [];
  }
}

export async function createSessionApi(
  agentId: string,
  title?: string,
): Promise<ChatSession | null> {
  try {
    const data = await http.post<BackendChatSession>(
      `/api/agent/${agentId}/sessions?scope=${SESSION_SCOPE}`,
      { title: title ?? '新会话' },
    );
    return data
      ? {
          id: String(data.id),
          title: data.title || '新会话',
          preview: '',
          agentId: String(data.agentId ?? agentId),
          updatedAt: data.createTime
            ? new Date(data.createTime).getTime()
            : Date.now(),
        }
      : null;
  } catch {
    return null;
  }
}

export async function deleteSessionApi(sessionId: string): Promise<void> {
  await http.delete(`/api/sessions/${sessionId}?scope=${SESSION_SCOPE}`);
}

export async function pinSessionApi(
  sessionId: string,
  isPinned: boolean,
): Promise<void> {
  await http.put(
    `/api/sessions/${sessionId}/pin?isPinned=${isPinned}&scope=${SESSION_SCOPE}`,
  );
}

export async function renameSessionApi(
  sessionId: string,
  title: string,
): Promise<void> {
  await http.put(
    `/api/sessions/${sessionId}/rename?title=${encodeURIComponent(title.trim())}&scope=${SESSION_SCOPE}`,
  );
}

// ---- Message APIs ----

interface BackendChatMessage {
  id?: number;
  sessionId?: string;
  role: string;
  content: string;
  messageType?: string;
  metadata?: string;
  createTime?: string;
  [key: string]: unknown;
}

export async function getSessionMessagesApi(
  sessionId: string,
): Promise<ChatMessage[]> {
  try {
    const data = await http.get<BackendChatMessage[]>(
      `/api/sessions/${sessionId}/messages?scope=${SESSION_SCOPE}`,
    );
    return (data ?? []).map((m) => {
      // T-08（R-10）：从 metadata 还原附件；旧消息无该键 ⇒ undefined（S6 兼容）
      let attachments;
      try {
        const md = typeof m.metadata === 'string' ? JSON.parse(m.metadata) : m.metadata;
        if (md && Array.isArray(md.attachments)) attachments = md.attachments;
      } catch {
        /* metadata 非 JSON：静默 */
      }
      return {
        id: String(m.id ?? `${Date.now()}-${Math.random()}`),
        role: (m.role === 'user' ? 'user' : 'assistant') as 'user' | 'assistant',
        content: m.content ?? '',
        createdAt: m.createTime ? new Date(m.createTime).getTime() : Date.now(),
        messageType: m.messageType ?? 'text',
        metadata: m.metadata,
        attachments,
      };
    });
  } catch {
    return [];
  }
}

export async function saveMessageApi(
  sessionId: string,
  message: Record<string, unknown>,
): Promise<void> {
  await http.post(`/api/sessions/${sessionId}/messages?scope=${SESSION_SCOPE}`, {
    ...message,
    sessionId,
  });
}

// ---- Preset Question APIs ----

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

export async function getPresetQuestionsApi(
  agentId: string,
): Promise<PresetQuestion[]> {
  try {
    return (
      (await http.get<PresetQuestion[]>(
        `/api/front/${agentId}/preset-questions`,
      )) ?? []
    );
  } catch {
    return [];
  }
}

export async function addPresetQuestionApi(
  data: AddPresetQuestionRequest,
): Promise<PresetQuestion> {
  return http.post<PresetQuestion>('/api/front/addPresetQuestion', data);
}

export async function deletePresetQuestionApi(
  questionId: number,
): Promise<void> {
  await http.delete(`/api/front/deletePresetQuestion/${questionId}`);
}
