import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import type { ChatMessage, ChatSession } from '../types/chat';
import type { ChatTransport, OnNodeMessage } from '../transport/types';

import { mockChatTransport } from '../transport/mock';

export const useChatStore = defineStore('phoenix-chat-shared/chat', () => {
  let transport: ChatTransport = mockChatTransport;

  const sessions = ref<ChatSession[]>([]);
  const messagesByS = ref<Record<string, ChatMessage[]>>({});

  /** v1.7.0 T-10：服务端静默心跳 → 会话级"仍在执行"提示（如「工具执行中 · 已静默 23s」） */
  const silenceByS = ref<Record<string, string>>({});
  const activeSessionId = ref<string | null>(null);

  const loadingSessions = ref(false);
  const loadingMessages = ref(false);
  const sendingSessions = ref(new Set<string>());
  /**
   * BUG-158（方案 B）：已"视觉收尾"的会话 —— 轮末框架尾巴（记忆 flush 等同步收尾，实测 18~24s）
   * 期间流仍开着但不会再有内容帧；静默≥阈值且已有内容时把会话从 sending 摘除并停打字光标，
   * 让用户感知立即结束；真 end 帧到达后照常收尾（幂等）。
   */
  const settledSessions = ref(new Set<string>());

  /** 静默判定阈值（ms）：phase=IDLE 且 silenceMs 达此值且本轮已有内容 ⇒ 视觉收尾 */
  const SETTLE_SILENCE_MS = 8000;

  function settleSession(sessionId: string) {
    if (!sendingSessions.value.has(sessionId)) return;
    const nextSending = new Set(sendingSessions.value);
    nextSending.delete(sessionId);
    sendingSessions.value = nextSending;
    const nextSettled = new Set(settledSessions.value);
    nextSettled.add(sessionId);
    settledSessions.value = nextSettled;
    // 停打字光标/思考动画
    const msgs = messagesByS.value[sessionId] ?? [];
    for (let i = msgs.length - 1; i >= 0; i--) {
      const m = msgs[i] as any;
      if (m.role === 'assistant' && m.streaming) {
        m.streaming = false;
        break;
      }
    }
  }

  function clearSettled(sessionId: string) {
    if (!settledSessions.value.has(sessionId)) return;
    const next = new Set(settledSessions.value);
    next.delete(sessionId);
    settledSessions.value = next;
  }

  /** 传输层调用：静默帧达阈值且本轮已有内容 ⇒ 视觉收尾 */
  function maybeSettleFromSilence(
    sessionId: string,
    frame: { phase?: string; silenceMs?: number },
    hasContent: boolean,
  ) {
    if (!hasContent) return;
    if (frame?.phase !== 'IDLE') return;
    if ((frame.silenceMs ?? 0) < SETTLE_SILENCE_MS) return;
    settleSession(sessionId);
  }

  const abortControllers = new Map<string, AbortController>();

  const sending = computed(() => sendingSessions.value.size > 0);

  const activeSession = computed<ChatSession | null>(() => {
    if (!activeSessionId.value) return null;
    return sessions.value.find((s) => s.id === activeSessionId.value) ?? null;
  });

  const activeMessages = computed<ChatMessage[]>(() => {
    if (!activeSessionId.value) return [];
    return messagesByS.value[activeSessionId.value] ?? [];
  });

  const isActiveSessionSending = computed<boolean>(
    () => activeSessionId.value !== null && sendingSessions.value.has(activeSessionId.value),
  );

  function setTransport(next: ChatTransport) {
    transport = next;
  }

  async function loadSessions() {
    loadingSessions.value = true;
    try {
      const list = await transport.listSessions();
      sessions.value = list;
      if (!activeSessionId.value && list.length > 0) {
        await switchSession(list[0]!.id);
      }
    } finally {
      loadingSessions.value = false;
    }
  }

  async function loadMessages(sessionId: string, force = false) {
    if (!force && messagesByS.value[sessionId]) return;
    loadingMessages.value = true;
    try {
      const list = await transport.listMessages(sessionId);
      messagesByS.value = { ...messagesByS.value, [sessionId]: list };
      // detached-stream T-05：服务端进行中的轮次 → 自动追流（P7：本地快照已在 transport 层让位）
      if (!sendingSessions.value.has(sessionId) && transport.joinActiveTurn) {
        let attached = false;
        try {
          attached = await transport.joinActiveTurn(
            sessionId,
            (text, thinking) => {
              // AC-01 修复：服务端开轮即有增量助手行——join 原地更新最后一条助手消息，绝不新建气泡（防双显）
              const arr = messagesByS.value[sessionId] ?? [];
              let i2 = -1;
              for (let k = arr.length - 1; k >= 0; k--) {
                if (arr[k]?.role === 'assistant') { i2 = k; break; }
              }
              if (i2 < 0) {
                arr.push({ id: `join-${Date.now()}`, role: 'assistant', content: '', createdAt: Date.now(), streaming: true } as any);
                i2 = arr.length - 1;
              }
              const base = arr[i2] as any;
              const upd = { ...base, content: text || base.content, thinking: thinking ?? base.thinking, streaming: true } as any;
              arr[i2] = upd;
              messagesByS.value = { ...messagesByS.value, [sessionId]: [...arr] };
            },
            () => {
              const s2 = new Set(sendingSessions.value);
              s2.delete(sessionId);
              sendingSessions.value = s2;
              void loadMessages(sessionId, true);
            },
          );
        } catch {
          attached = false;
        }
        if (attached) {
          sendingSessions.value = new Set(sendingSessions.value).add(sessionId);
        }
      }
    } finally {
      loadingMessages.value = false;
    }
  }

  async function switchSession(id: string) {
    activeSessionId.value = id;
    await loadMessages(id);
  }

  async function createSession(agentId: string) {
    const session: ChatSession = {
      id: `temp-${Date.now()}`,
      title: '新会话',
      preview: '',
      agentId,
      updatedAt: Date.now(),
    };
    sessions.value = [session, ...sessions.value];
    messagesByS.value = { ...messagesByS.value, [session.id]: [] };
    activeSessionId.value = session.id;
    return session;
  }

  async function persistCurrentSessionIfNeeded() {
    const session = activeSession.value;
    if (!session || !session.id.startsWith('temp-')) return;
    const persisted = await transport.createSession(session.agentId);
    const oldId = session.id;
    const idx = sessions.value.findIndex((s) => s.id === oldId);
    if (idx >= 0) {
      persisted.updatedAt = persisted.updatedAt ?? Date.now();
      sessions.value[idx] = persisted;
    }
    const msgs = messagesByS.value[oldId] ?? [];
    const next = { ...messagesByS.value };
    delete next[oldId];
    next[persisted.id] = msgs;
    messagesByS.value = next;
    activeSessionId.value = persisted.id;
  }

  async function deleteSession(id: string) {
    if (!id.startsWith('temp-')) {
      await transport.deleteSession(id);
    }
    sessions.value = sessions.value.filter((s) => s.id !== id);
    const next = { ...messagesByS.value };
    delete next[id];
    messagesByS.value = next;
    if (activeSessionId.value === id) {
      const fallback = sessions.value[0]?.id ?? null;
      activeSessionId.value = fallback;
      if (fallback) await loadMessages(fallback);
    }
  }

  async function renameSession(id: string, title: string) {
    const target = sessions.value.find((s) => s.id === id);
    if (target) {
      target.title = title;
      target.updatedAt = Date.now();
    }
    if (!id.startsWith('temp-')) {
      await transport.renameSession(id, title);
    }
  }

  async function pinSession(id: string, isPinned: boolean) {
    const target = sessions.value.find((s) => s.id === id);
    if (target) {
      target.isPinned = isPinned;
      target.updatedAt = Date.now();
    }
    if (!id.startsWith('temp-')) {
      await transport.pinSession(id, isPinned);
    }
  }

  async function send(content: string) {
    const trimmed = content.trim();
    if (!trimmed) return;
    if (!activeSessionId.value) return;
    if (sendingSessions.value.has(activeSessionId.value)) return;
    await persistCurrentSessionIfNeeded();
    const sessionId = activeSessionId.value;
    clearSettled(sessionId);
    // 乐观写入用户消息
    const msgs = messagesByS.value[sessionId] ?? [];
    const optimistic: ChatMessage = {
      id: `local-${Date.now()}`,
      role: 'user',
      content: trimmed,
      createdAt: Date.now(),
    };
    messagesByS.value = {
      ...messagesByS.value,
      [sessionId]: [...msgs, optimistic],
    };

    let streamMsgId: string | null = null;

    // 报告文本流式回调：创建/更新占位消息
    function updateStreamMessage(text: string, thinking?: string) {
      const msgs = messagesByS.value[sessionId] ?? [];
      if (!streamMsgId) {
        streamMsgId = `stream-${Date.now()}`;
        msgs.push({
          id: streamMsgId,
          role: 'assistant',
          content: '',
          createdAt: Date.now(),
          streaming: true,
        });
      }
      const idx = msgs.findIndex((m) => m.id === streamMsgId);
      if (idx >= 0) {
        msgs[idx] = { ...msgs[idx]!, content: text, thinking: thinking || msgs[idx]!.thinking };
        messagesByS.value = { ...messagesByS.value, [sessionId]: [...msgs] };
      }
    }

    // 节点回调：将已完成的节点消息直接推入消息列表
    const onNodeMessage: OnNodeMessage = (nodeMsg) => {
      // T-10：心跳帧不进消息列表——只更新"静默提示"，任何真实增量/结束则清除
      const beat = nodeMsg as any;
      if (beat && beat.silenceMs != null) {
        const sec = Math.round(Number(beat.silenceMs) / 1000);
        silenceByS.value = {
          ...silenceByS.value,
          [sessionId]: `${beat.phaseLabel || '处理中'} · 已静默 ${sec}s`,
        };
        return;
      }
      if (beat && (beat.content || beat.thinking || beat.end) && silenceByS.value[sessionId]) {
        const next = { ...silenceByS.value };
        delete next[sessionId];
        silenceByS.value = next;
      }
      const msgs = messagesByS.value[sessionId] ?? [];
      msgs.push(nodeMsg);
      messagesByS.value = { ...messagesByS.value, [sessionId]: [...msgs] };
    };

    sendingSessions.value = new Set(sendingSessions.value).add(sessionId);
    const ac = new AbortController();
    abortControllers.set(sessionId, ac);
    const sendTimeout = setTimeout(
      () => ac.abort(),
      600_000,
    );
    try {
      const currentSession = sessions.value.find((s) => s.id === sessionId);
      const reply = await transport.send(
        { sessionId, content: trimmed, agentId: currentSession?.agentId },
        ac.signal,
        (text: string, thinking?: string) => {
          updateStreamMessage(text, thinking);
        },
        (nodeMsg) => {
          onNodeMessage(nodeMsg);
        },
      );
      clearTimeout(sendTimeout);
      if (ac.signal.aborted) return;

      // 将占位消息（报告文本流）替换为最终回复
      if (streamMsgId) {
        const msgs = messagesByS.value[sessionId] ?? [];
        const idx = msgs.findIndex((m) => m.id === streamMsgId);
        if (idx >= 0) {
          msgs[idx] = { ...reply, streaming: false };
          messagesByS.value = { ...messagesByS.value, [sessionId]: [...msgs] };
        } else {
          messagesByS.value = {
            ...messagesByS.value,
            [sessionId]: [...msgs, { ...reply, streaming: false }],
          };
        }
      } else {
        const msgs = messagesByS.value[sessionId] ?? [];
        messagesByS.value = {
          ...messagesByS.value,
          [sessionId]: [...msgs, { ...reply, streaming: false }],
        };
      }

      // 更新会话预览 / 排序
      const session = currentSession;
      if (session) {
        session.preview = trimmed;
        session.updatedAt = Date.now();
        if (session.title === '新会话') {
          session.title =
            trimmed.length > 20 ? `${trimmed.slice(0, 20)}…` : trimmed;
        }
        sessions.value = [...sessions.value].sort(
          (a, b) => b.updatedAt - a.updatedAt,
        );
      }
    } catch (err: any) {
      // 移除流式占位消息
      const msgs = messagesByS.value[sessionId] ?? [];
      messagesByS.value = {
        ...messagesByS.value,
        [sessionId]: msgs.filter((m) => m.id !== streamMsgId),
      };
      if (err?.name === 'AbortError') return;
      throw err;
    } finally {
      clearTimeout(sendTimeout);
      const next = new Set(sendingSessions.value);
      next.delete(sessionId);
      sendingSessions.value = next;
      clearSettled(sessionId);
      abortControllers.delete(sessionId);
    }
  }

  function stopSending(sessionId?: string) {
    if (sessionId) {
      // detached-stream R-06：显式停止走服务端（断≠停）
      void transport.cancelTurn?.(sessionId);
      abortControllers.get(sessionId)?.abort();
    } else {
      for (const ac of abortControllers.values()) {
        ac.abort();
      }
    }
  }

  function reset() {
    sessions.value = [];
    messagesByS.value = {};
    activeSessionId.value = null;
  }

  return {
    sessions,
    messagesByS,
    silenceByS,
    activeSessionId,
    activeSession,
    activeMessages,
    loadingSessions,
    loadingMessages,
    sending,
    sendingSessions,
    settledSessions,
    settleSession,
    clearSettled,
    maybeSettleFromSilence,
    SETTLE_SILENCE_MS,
    isActiveSessionSending,
    setTransport,
    loadSessions,
    loadMessages,
    switchSession,
    createSession,
    persistCurrentSessionIfNeeded,
    deleteSession,
    renameSession,
    pinSession,
    send,
    stopSending,
    reset,
  };
});
