import { notifyFilesChanged } from '#/api/core/agentFiles';
import { forceClearStreamSnapshot, readStreamSnapshot, saveStreamSnapshot } from '#/utils/stream-snapshot';
import type {
  ChatMessage,
  ChatSession,
  ChatTransport,
  SendPayload,
  ChatAttachmentMeta,
} from '@phoenix/chat-shared';
import { useChatStore, useAgentStore } from '@phoenix/chat-shared';

import {
  createSessionApi,
  deleteSessionApi,
  renameSessionApi,
  saveMessageApi,
  TextType,
} from '#/api';
import type { GraphNodeResponse, ResultSetData } from '#/api';
import {
  getSessionMessagesApi,
  streamFrontChat,
  streamFrontChatSql,
  streamFrontHarnessChat,
  confirmFrontHarnessChat,
} from '#/api/front/chat';

import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import sql from 'highlight.js/lib/languages/sql';
import python from 'highlight.js/lib/languages/python';
import json from 'highlight.js/lib/languages/json';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

hljs.registerLanguage('sql', sql);
hljs.registerLanguage('python', python);
hljs.registerLanguage('json', json);

let msgCounter = 0;
function uid(): string {
  msgCounter++;
  return `m-${Date.now().toString(36)}-${msgCounter}`;
}

function toStoreMessage(api: any): ChatMessage {
  // thinking-display R-05：历史消息 metadata.thinking 回显（旧行无键=undefined 静默）
  let thinking: string | undefined;
  let thinkingMs: number | undefined;
  // T-07（R-10）：历史回看附件；旧消息无该键 ⇒ undefined（S6 兼容）
  let attachments: ChatAttachmentMeta[] | undefined;
  // long-turn-resilience T-04：服务端进行中轮次（status=generating）刷新后须仍标流式，
  // 否则思考区误显"Think Done"、正文区按完成态渲染（用户实测：明明是 thinking，刷新后 think done）
  let streaming = false;
  try {
    const md = typeof api.metadata === 'string' ? JSON.parse(api.metadata) : api.metadata;
    if (md && typeof md.thinking === 'string') {
      thinking = md.thinking;
      thinkingMs = typeof md.thinkingMs === 'number' ? md.thinkingMs : undefined;
    }
    if (md && md.status === 'generating') {
      streaming = true;
    }
    if (md && Array.isArray(md.attachments)) {
      attachments = md.attachments as ChatAttachmentMeta[];
    }
  } catch { /* metadata 非 JSON 或为空：按无思考处理（R-04） */ }
  return {
    id: String(api.id ?? `${Date.now()}-${Math.random()}`),
    role: (api.role === 'user' ? 'user' : 'assistant') as 'assistant' | 'user',
    content: api.content ?? '',
    createdAt: api.createTime ? new Date(api.createTime).getTime() : Date.now(),
    messageType: api.messageType ?? 'text',
    metadata: api.metadata,
    thinking,
    thinkingMs,
    streaming,
    attachments,
  };
}

if (!window.copyTextToClipboard) {
  window.copyTextToClipboard = (btn: HTMLElement) => {
    const text = btn.previousElementSibling?.textContent || '';
    const originalText = btn.textContent || '';
    navigator.clipboard
      .writeText(text)
      .then(() => {
        btn.textContent = '已复制!';
        setTimeout(() => {
          btn.textContent = originalText;
        }, 3000);
      })
      .catch(() => {
        btn.textContent = '复制失败';
        setTimeout(() => {
          btn.textContent = originalText;
        }, 3000);
      });
  };
}

function markdownToHtml(markdown: string): string {
  if (!markdown) return '';
  marked.setOptions({ gfm: true, breaks: true });
  const rawHtml = marked.parse(markdown) as string;
  return DOMPurify.sanitize(rawHtml);
}

function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function generateResultSetTable(
  resultSetData: ResultSetData,
  pageSize: number,
): string {
  const columns = resultSetData.column || [];
  const allData = resultSetData.data || [];
  const total = allData.length;
  const totalPages = Math.ceil(total / pageSize);

  let tableHtml = `<div class="result-set-container"><div class="result-set-header"><div class="result-set-info"><span>查询结果 (共 ${total} 条记录)</span></div></div><div class="result-set-table-container">`;

  for (let page = 1; page <= totalPages; page++) {
    const startIndex = (page - 1) * pageSize;
    const endIndex = Math.min(startIndex + pageSize, total);
    const currentPageData = allData.slice(startIndex, endIndex);

    tableHtml += `<div class="result-set-page ${page === 1 ? 'result-set-page-active' : ''}" data-page="${page}"><table class="result-set-table"><thead><tr>`;
    columns.forEach((column) => {
      tableHtml += `<th>${escapeHtml(column)}</th>`;
    });
    tableHtml += `</tr></thead><tbody>`;

    if (currentPageData.length === 0) {
      tableHtml += `<tr><td colspan="${columns.length}" class="result-set-empty-cell">暂无数据</td></tr>`;
    } else {
      currentPageData.forEach((row) => {
        tableHtml += `<tr>`;
        columns.forEach((column) => {
          tableHtml += `<td>${escapeHtml(row[column] || '')}</td>`;
        });
        tableHtml += `</tr>`;
      });
    }
    tableHtml += `</tbody></table></div>`;
  }

  tableHtml += `</div></div>`;
  return tableHtml;
}

function formatNodeContent(node: GraphNodeResponse[]): string {
  let content = '';
  for (let idx = 0; idx < node.length; idx++) {
    const nd = node[idx];
    if (!nd) continue;

    if (nd.textType === TextType.HTML) {
      content += nd.text;
    } else if (nd.textType === TextType.TEXT) {
      content += nd.text.replaceAll('\n', '<br>');
    } else if (
      nd.textType === TextType.JSON ||
      nd.textType === TextType.PYTHON ||
      nd.textType === TextType.SQL
    ) {
      let pre = '';
      let p = idx;
      for (; p < node.length; p++) {
        if (node[p]?.textType !== nd.textType) break;
        pre += node[p]?.text || '';
      }
      try {
        const language = nd.textType.toLowerCase();
        const highlighted = hljs.highlight(pre, { language });
        content += `<pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">${language}</span><span hidden>${pre}</span><button onclick='copyTextToClipboard(this)' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs ${language}">${highlighted.value}</code></pre>`;
      } catch {
        content += `<pre><code>${pre}</code></pre>`;
      }
      if (p < node.length) idx = p - 1;
      else break;
    } else if (nd.textType === TextType.MARK_DOWN) {
      let markdown = '';
      let p = idx;
      for (; p < node.length; p++) {
        if (node[p]?.textType !== TextType.MARK_DOWN) break;
        markdown += node[p]?.text || '';
      }
      const safeHtml = markdownToHtml(markdown);
      content += `<div class="markdown-report">${safeHtml}</div>`;
      if (p < node.length) idx = p - 1;
      else break;
    } else if (nd.textType === TextType.RESULT_SET) {
      try {
        const resultData = JSON.parse(nd.text);
        const resultSetData = resultData.resultSet;
        if (resultSetData.errorMsg) {
          content += `<div class="result-set-error">错误: ${resultSetData.errorMsg}</div>`;
          continue;
        }
        if (!resultSetData.column?.length || !resultSetData.data?.length) {
          content += `<div class="result-set-empty">查询结果为空</div>`;
          continue;
        }
        content += generateResultSetTable(resultSetData, 20);
      } catch {
        content += `<div class="result-set-error">解析结果集数据失败</div>`;
      }
    } else {
      content += nd.text;
    }
  }
  return content;
}

function generateNodeHtml(node: GraphNodeResponse[]): string {
  const content = formatNodeContent(node);
  return `
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">${node[0]?.nodeName ?? '空节点'}</div>
      <div class="agent-response-content">${content}</div>
    </div>
  `;
}

function generateBlocksHtml(blocks: GraphNodeResponse[][]): string {
  return blocks.map((block) => generateNodeHtml(block)).join('\n');
}

// Per-session threadId for conversation continuation
const sessionThreadIds = new Map<string, string>();

/**
 * 本轮显式执行的技能 id（R-09）：由 ChatComposer 在发送前设置，
 * 发送时读取一次并立即清空，保证只对下一条消息生效（不持久）。
 */
let explicitSkillIds: number[] = [];

export function setExplicitSkillIds(ids: number[]) {
  explicitSkillIds = Array.isArray(ids) ? [...ids] : [];
}

/**
 * T-07（chat-attachment-understanding）：本轮待发送附件。
 * 与 explicitSkillIds 同范式 —— **读取一次即清空**，仅对下一条消息生效，不持久。
 */
let pendingAttachments: ChatAttachmentMeta[] = [];

export function setPendingAttachments(list: ChatAttachmentMeta[]) {
  pendingAttachments = Array.isArray(list) ? [...list] : [];
}

export const apiChatTransport: ChatTransport = {
  async listSessions(): Promise<ChatSession[]> {
    throw new Error('listSessions is not supported via API transport');
  },

  async listMessages(sessionId: string): Promise<ChatMessage[]> {
    const list = await getSessionMessagesApi(sessionId);
    const messages = list.map((m) => toStoreMessage(m));
    // Convert markdown content for assistant text messages
    for (const msg of messages) {
      if (msg.role === 'assistant' && !msg.messageType) {
        msg.messageType = 'text';
      }
      if (
        msg.role === 'assistant' &&
        msg.messageType === 'text' &&
        (
          // BUG-57：TurnManager 行存原始 markdown——含 <svg>/<html> 代码块也须渲染
          //（旧启发式为「前端已存 HTML 行」设计，对新行误伤致样式整体丢失）
          !/<[a-z][\s\S]*>/i.test(msg.content)
          // BUG-57 三发：API 的 metadata 有 string/object 双形态——统一序列化后判定
          || JSON.stringify((msg as any).metadata ?? {}).includes('turnId')
        )
      ) {
        msg.content = markdownToHtml(msg.content);
        // BUG-57 v2：不再改 'html'——.chat-message__html 无任何 CSS（裸分支）。
        // 转译后保持 'text' 走 v-else 的 .chat-message__text--markdown，与直播/旧行同一条装修好的路

      }
    }
    // Restore threadId from last assistant message's metadata
    const lastAssistant = [...messages]
      .reverse()
      .find((m) => m.role === 'assistant' && m.metadata);
    if (lastAssistant?.metadata) {
      try {
        const meta =
          typeof lastAssistant.metadata === 'string'
            ? JSON.parse(lastAssistant.metadata)
            : lastAssistant.metadata;
        if (meta.threadId) {
          sessionThreadIds.set(sessionId, meta.threadId);
        }
      } catch {
        // ignore parse errors
      }
    }
    // BUG-53 A′：中断快照回显（本地气泡不落库；服务端已有 assistant 尾行则丢弃快照防重复）
    const snap = readStreamSnapshot(sessionId);
    if (snap) {
      forceClearStreamSnapshot(sessionId);
      const lastMsg = messages[messages.length - 1];
      // P7（detached-stream R-09）：尾行是 generating 中的服务端消息 → 本地快照让位
      const gen = !!lastMsg && lastMsg.role === 'assistant' && String((lastMsg as any).metadata ?? '').includes('generating');
      if ((!lastMsg || lastMsg.role === 'user') && !gen) {
        messages.push({
          id: `snap-${snap.ts}`,
          role: 'assistant',
          content: snap.contentHtml,
          createdAt: snap.ts,
          messageType: 'html',
          metadata: { interrupted: true },
          thinking: snap.thinking,
        } as any);
      }
    }
    return messages;
  },

  async createSession(agentId: string): Promise<ChatSession> {
    // CR-03：前台空间 = FRONT_CHAT
    const session = await createSessionApi(Number(agentId), '新会话', undefined, 'FRONT_CHAT');
    if (!session) throw new Error('创建会话失败');
    return {
      id: String(session.id),
      title: session.title || '新会话',
      preview: '',
      agentId: String(session.agentId ?? agentId),
      updatedAt: session.createTime
        ? new Date(session.createTime).getTime()
        : Date.now(),
    };
  },

  async deleteSession(sessionId: string): Promise<void> {
    await deleteSessionApi(sessionId);
  },

  async renameSession(sessionId: string, title: string): Promise<void> {
    await renameSessionApi(sessionId, title);
  },

  async joinActiveTurn(
    sessionId: string,
    onProgress?: (text: string, thinking?: string) => void,
    onDone?: () => void,
  ): Promise<boolean> {
    const token = localStorage.getItem('phoenix-token') || '';
    let active = false;
    try {
      const r = await fetch(`/platform/harness/turn/status?sessionId=${encodeURIComponent(sessionId)}`, { headers: { 'phoenix-token': token } });
      active = ((await r.json()) as any)?.data === true;
    } catch {
      return false;
    }
    if (!active) return false;
    let textBuf = '';
    let thinkBuf = '';
    // BUG-61：追流重放是帧风暴（千帧一瞬灌入），每帧全量 markdown 渲染会打死主线程——150ms 节流合并
    let lastPush = 0;
    let pushTimer: ReturnType<typeof setTimeout> | null = null;
    // long-turn-resilience T-03：增量 markdown（完成块冻结/仅重解析尾块，DSH reasoning-chunks 方法论）
    // 替代 BUG-61 时代的"每推全量 markdown"——长轮数千次全量解析仍会打死主线程
    let mdRendered = '';
    let mdHtml = '';
    const incrementalMarkdown = (buf: string): string => {
      if (!buf) {
        mdRendered = '';
        mdHtml = '';
        return '';
      }
      if (!buf.startsWith(mdRendered)) {
        // 非追加（新一轮/回退/重放）→ 重置缓存
        mdRendered = '';
        mdHtml = '';
      }
      const cutPoint = buf.lastIndexOf('\n\n');
      const fenceBalanced =
        (buf.match(/```/g) || []).length % 2 === 0 &&
        (cutPoint <= 0 || (buf.slice(0, cutPoint).match(/```/g) || []).length % 2 === 0);
      if (cutPoint <= 0 || !fenceBalanced) {
        // 围栏未闭合或无块边界 → 全量保守解析
        mdHtml = markdownToHtml(buf);
        mdRendered = buf;
        return mdHtml;
      }
      if (cutPoint + 2 > mdRendered.length) {
        mdHtml += markdownToHtml(buf.slice(mdRendered.length, cutPoint + 2));
        mdRendered = buf.slice(0, cutPoint + 2);
      }
      const tail = buf.slice(cutPoint + 2);
      return mdHtml + (tail ? markdownToHtml(tail) : '');
    };
    // 大缓冲自适应节流：正文越长推送间隔越大（DOM 整体替换成本线性）
    const pushInterval = () => (textBuf.length > 20000 ? 400 : 150);
    const pushNow = () => {
      lastPush = Date.now();
      onProgress?.(incrementalMarkdown(textBuf), thinkBuf || undefined);
    };
    const pushThrottled = () => {
      const now = Date.now();
      const iv = pushInterval();
      if (now - lastPush >= iv) { pushNow(); }
      else if (!pushTimer) { pushTimer = setTimeout(() => { pushTimer = null; pushNow(); }, iv); }
    };
    (async () => {
      try {
        const resp = await fetch(`/platform/harness/turn/stream?sessionId=${encodeURIComponent(sessionId)}`, { headers: { 'phoenix-token': token, Accept: 'text/event-stream' } });
        const reader = resp.body?.getReader();
        if (!reader) throw new Error('no body');
        const dec = new TextDecoder();
        let pnd = '';
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          pnd += dec.decode(value, { stream: true });
          const ls = pnd.split('\n');
          pnd = ls.pop() ?? '';
          for (const ln of ls) {
            if (!ln.startsWith('data:')) continue;
            let p: any;
            try { p = JSON.parse(ln.slice(5)); } catch { continue; }
            if (p.content) textBuf += String(p.content);
            if (p.thinking) thinkBuf += String(p.thinking);
            if (p.content || p.thinking) pushThrottled();
            if (p.end) { if (pushTimer) { clearTimeout(pushTimer); pushTimer = null; } pushNow(); onDone?.(); return; }
          }
        }
        if (pushTimer) { clearTimeout(pushTimer); pushTimer = null; }
        pushNow();
        onDone?.();
      } catch {
        onDone?.();
      }
    })();
    return true;
  },
  async cancelTurn(sessionId: string): Promise<void> {
    try {
      await fetch(`/platform/harness/turn/cancel?sessionId=${encodeURIComponent(sessionId)}`, { method: 'POST', headers: { 'phoenix-token': localStorage.getItem('phoenix-token') || '' } });
    } catch { /* ignore */ }
  },

  async send(
    payload: SendPayload,
    signal?: AbortSignal,
    onProgress?: (text: string, thinking?: string) => void,
    onNodeMessage?: (message: ChatMessage) => void,
  ): Promise<ChatMessage> {
    let { sessionId, content, agentId } = payload;
    forceClearStreamSnapshot(sessionId); // 新一轮开始，清旧快照（BUG-53 A′）

    if (!agentId) {
      const chatStore = useChatStore();
      const session = chatStore.sessions.find((s) => s.id === sessionId);
      agentId = session?.agentId || useAgentStore().activeAgentId;
    }
    if (!agentId) {
      throw new Error('agentId is required for sending messages');
    }

    const needsTitle = (() => {
      try {
        const store = useChatStore();
        const session = store.sessions.find((s) => s.id === sessionId);
        return !session?.title || session.title === '新会话';
      } catch {
        return false;
      }
    })();

    // T-07：本轮附件（读取一次即清空，仅对本条消息生效）
    const attachmentsForThisTurn =
      pendingAttachments.length > 0 ? [...pendingAttachments] : undefined;
    const attachmentIdsForThisTurn = attachmentsForThisTurn?.map((a) => a.id);
    pendingAttachments = [];

    const userMessage: any = {
      sessionId,
      role: 'user',
      content,
      messageType: 'text',
      titleNeeded: needsTitle,
      // R-10/S9：附件写入 metadata ⇒ 后端回填 message_id；历史回看可还原
      ...(attachmentIdsForThisTurn?.length
          ? {
              metadata: JSON.stringify({
                attachmentIds: attachmentIdsForThisTurn,
                attachments: attachmentsForThisTurn,
              }),
          }
        : {}),
      // 本地即时渲染（当轮不必等刷新）
      ...(attachmentsForThisTurn?.length ? { attachments: attachmentsForThisTurn } : {}),
    };
    await saveMessageApi(sessionId, userMessage);

    // BUG-156：本地气泡是 store 自建的占位消息（只有 content），不带 attachments ⇒
    // 当轮不显示、刷新后靠 metadata 还原才有。这里把本轮附件**回写 store 内存消息**（响应式，立即渲染）。
    if (attachmentsForThisTurn?.length) {
      try {
        const chatStore = useChatStore();
        const list = (chatStore.messagesByS as Record<string, any[]>)[sessionId] ?? [];
        for (let i = list.length - 1; i >= 0; i--) {
          const m = list[i];
          if (m && m.role === 'user' && m.content === content) {
            m.attachments = attachmentsForThisTurn;
            break;
          }
        }
      } catch {
        /* 回写失败不影响发送主流程（刷新后仍可从 metadata 还原） */
      }
    }

    const agentStore = useAgentStore();
    const currentAgent = agentStore.agents.find((a) => a.id === agentId);
    const isSql = currentAgent?.type === 'sql';
    const isHarness = currentAgent?.type === 'harness';

    const reply = await new Promise<ChatMessage>((resolve, reject) => {
      if (isHarness) {
        let fullText = '';
        let thinkingBuf = '';
        let thinkingStart = 0;
        let thinkingMs = 0;
        let abortRequested = false;

        const onAbort = () => {
          abortRequested = true;
        };
        signal?.addEventListener('abort', onAbort, { once: true });

        // 本轮显式技能：读取一次后立即清空（仅对本条消息生效，不持久）
        const skillIdsForThisTurn = explicitSkillIds.length > 0 ? [...explicitSkillIds] : undefined;
        explicitSkillIds = [];

        const closeStream = streamFrontHarnessChat(
          {
            sessionId,
            message: content,
            agentId: Number(agentId),
            harnessSn: currentAgent?.sn ?? undefined,
            enabledSkillIds: skillIdsForThisTurn,
            // T-07：附件 id（不传即后端短路，行为不变 —— S1'）
            attachmentIds: attachmentIdsForThisTurn,
          },
          async (response) => {
            if (abortRequested) return;
            // BUG-158（方案 B）：轮末框架尾巴（记忆 flush 等同步收尾，实测 18~24s）期间只有静默帧；
            // 静默达阈值且本轮已有内容 ⇒ 视觉收尾（store 摘 sending、停打字光标），真 end 帧到达照常收尾（幂等）
            if ((response as any).phase) {
              useChatStore().maybeSettleFromSilence(sessionId, response as any, fullText.length > 0);
            }
            if (response.error) return;
            // BL-19：本轮产物登记成功（轮末扫描事件），刷新文件面板
            if ((response as any).agentFiles) notifyFilesChanged();
            if (response.needConfirm && response.buttons) {
              // 确认卡内容三级取源：正文 > 工具调用提炼（plan_exit.summary/待执行命令）> 思考流兜底
              // ——用户要确认的是"要做什么"，不是模型内心独白（用户反馈：原始思考无意义）
              const tcList = ((response as any).toolCalls || []) as any[];
              const distilled = tcList
                .map((t) => {
                  const inp = (t?.input || {}) as Record<string, any>;
                  if (typeof inp.summary === 'string' && inp.summary.trim()) return inp.summary.trim();
                  if (typeof inp.command === 'string' && inp.command.trim()) return `将执行命令：\`${inp.command.trim()}\``;
                  if (typeof inp.path === 'string' && t?.name) return `将操作文件：\`${inp.path}\`（${t.name}）`;
                  return t?.name ? `将调用工具：${t.name}` : '';
                })
                .filter(Boolean)
                .join('\n\n');
              const confirmHtml = (fullText || '').trim()
                ? markdownToHtml(fullText)
                : distilled
                  ? `<p style="margin:0 0 6px;color:#8a919f;font-size:12px">待确认的执行计划</p>${markdownToHtml(distilled.slice(0, 4000))}`
                  : thinkingBuf
                    ? `<p style="margin:0 0 6px;color:#8a919f;font-size:12px">模型思考摘要（待确认）</p>${markdownToHtml(thinkingBuf.slice(-1200))}`
                    : '';
              onNodeMessage?.({
                id: uid(),
                role: 'assistant',
                content: confirmHtml,
                createdAt: Date.now(),
                messageType: 'harness-confirm',
                metadata: {
                  needConfirm: true,
                  buttons: response.buttons,
                  toolCalls: response.toolCalls,
                  agentId: Number(agentId),
                  sessionId,
                },
              });
              return;
            }
            const th = (response as any).thinking;
            if (th) {
              if (!thinkingBuf) thinkingStart = Date.now();
              thinkingBuf += th;
            }
            if (response.text) {
              fullText += response.text;
            }
            if (th || response.text) {
              if (thinkingBuf && !thinkingMs) thinkingMs = Date.now() - thinkingStart;
              const htmlNow = markdownToHtml(fullText);
              // BUG-53 A′：节流快照，刷新后由 store 回显
              if (htmlNow || thinkingBuf) saveStreamSnapshot(sessionId, htmlNow, thinkingBuf || undefined);
              onProgress?.(htmlNow, thinkingBuf || undefined);
            }
          },
          async (error) => {
            signal?.removeEventListener('abort', onAbort);
            if (abortRequested) return;
            reject(error);
          },
          async () => {
            signal?.removeEventListener('abort', onAbort);
            forceClearStreamSnapshot(sessionId); // 正常收尾，快照作废（BUG-53）
            if (abortRequested) return;

            const text = fullText || '已处理完成';
            const html = markdownToHtml(text);
            /* detached-stream T-05：助手行由服务端 TurnManager 开轮即插、5s 增量、终定稿
               （R-05 落库所有权移交）——前端 onComplete 保存退役 */

            resolve({
              id: uid(),
              role: 'assistant',
              content: html,
              createdAt: Date.now(),
              messageType: 'text',
              thinking: thinkingBuf || undefined,
              thinkingMs: thinkingMs || undefined,
            } as any);
          },
        );

        if (signal?.aborted) {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', onAbort);
          reject(new DOMException('Aborted', 'AbortError'));
          return;
        }

        const abortHandler = () => {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', abortHandler);
          reject(new DOMException('Aborted', 'AbortError'));
        };
        signal?.addEventListener('abort', abortHandler, { once: true });
      } else if (isSql) {
        // --- SQL intelligent agent: use streamFrontChatSql with full node processing ---
        let currentNodeName: string | null = null;
        let currentBlockIndex = -1;
        const nodeBlocks: GraphNodeResponse[][] = [];
        let threadId: string | undefined = sessionThreadIds.get(sessionId);
        let abortRequested = false;
        const pendingSavePromises: Promise<void>[] = [];

        const saveNodeMessage = async (
          node: GraphNodeResponse[],
        ): Promise<void> => {
          if (!node || node.length === 0) return;

          const first = node[0]!;
          if (first.textType === TextType.RESULT_SET) {
            try {
              const resultData = JSON.parse(first.text);
              if (
                resultData.displayStyle?.type &&
                resultData.displayStyle.type !== 'table'
              ) {
                const storeMsg: ChatMessage = {
                  id: uid(),
                  role: 'assistant',
                  content: first.text,
                  createdAt: Date.now(),
                  messageType: 'result-set',
                };
                onNodeMessage?.(storeMsg);
                const aiMessage: any = {
                  sessionId,
                  role: 'assistant',
                  content: first.text,
                  messageType: 'result-set',
                };
                await saveMessageApi(sessionId, aiMessage);
                return;
              }
            } catch {
              /* ignore */
            }
          }

          const nodeHtml = generateNodeHtml(node);
          const storeMsg: ChatMessage = {
            id: uid(),
            role: 'assistant',
            content: nodeHtml,
            createdAt: Date.now(),
            messageType: 'html',
          };
          onNodeMessage?.(storeMsg);
          const aiMessage: any = {
            sessionId,
            role: 'assistant',
            content: nodeHtml,
            messageType: 'html',
          };
          try {
            await saveMessageApi(sessionId, aiMessage);
          } catch {
            /* ignore */
          }
        };

        const onAbort = () => {
          abortRequested = true;
        };
        signal?.addEventListener('abort', onAbort, { once: true });

        const closeStream = streamFrontChatSql(
          {
            agentId,
            query: content,
            humanFeedback: false,
            nl2sqlOnly: false,
            rejectedPlan: false,
            ...(threadId ? { threadId } : {}),
          },
          async (response) => {
            if (abortRequested) return;
            if (response.error) return;
            threadId = response.threadId || threadId;

            if (response.textType === TextType.RESULT_SET) {
              currentNodeName = 'result_set';
              if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
                const p = saveNodeMessage(nodeBlocks[currentBlockIndex]!);
                pendingSavePromises.push(p);
              }
              nodeBlocks.push([{ ...response, text: response.text }]);
              currentBlockIndex = nodeBlocks.length - 1;
            } else {
              const isNewNode =
                currentNodeName === null || response.nodeName !== currentNodeName;

              if (isNewNode) {
                if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
                  const p = saveNodeMessage(nodeBlocks[currentBlockIndex]!);
                  pendingSavePromises.push(p);
                }
                nodeBlocks.push([{ ...response, text: response.text }]);
                currentBlockIndex = nodeBlocks.length - 1;
                currentNodeName = response.nodeName;
              } else {
                if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
                  nodeBlocks[currentBlockIndex]?.push({
                    ...response,
                    text: response.text,
                  });
                } else {
                  nodeBlocks.push([{ ...response, text: response.text }]);
                  currentBlockIndex = nodeBlocks.length - 1;
                  currentNodeName = response.nodeName;
                }
              }
            }

            // 只发送 MARK_DOWN 报告文本（节点消息通过 onNodeMessage 推送）
            let reportText = '';
            for (const block of nodeBlocks) {
              for (const nd of block) {
                if (nd.textType === TextType.MARK_DOWN) {
                  reportText += nd.text || '';
                }
              }
            }
            if (reportText) {
              onProgress?.(markdownToHtml(reportText));
            }
          },
          async (error) => {
            signal?.removeEventListener('abort', onAbort);
            if (abortRequested) return;
            try {
              if (pendingSavePromises.length > 0) {
                await Promise.all(pendingSavePromises);
              }
            } catch {
              // ignore
            }
            reject(error);
          },
          async () => {
            signal?.removeEventListener('abort', onAbort);
            if (abortRequested) return;

            try {
              if (pendingSavePromises.length > 0) {
                await Promise.all(pendingSavePromises);
              }
            } catch {
              // ignore
            }

            // Detect if any blocks contain MARK_DOWN type (report content)
            let markdownContent = '';
            for (const block of nodeBlocks) {
              for (const nd of block) {
                if (nd.textType === TextType.MARK_DOWN) {
                  markdownContent += nd.text || '';
                }
              }
            }

            if (markdownContent) {
              // Save markdown-report message (raw markdown for client-side rendering)
              const mdMessage: any = {
                sessionId,
                role: 'assistant',
                content: markdownContent,
                messageType: 'markdown-report',
                metadata: threadId ? JSON.stringify({ threadId }) : undefined,
              };
              try {
                await saveMessageApi(sessionId, mdMessage);
              } catch {
                // ignore
              }

              if (threadId) {
                sessionThreadIds.set(sessionId, threadId);
              }

              resolve({
                id: uid(),
                role: 'assistant',
                content: markdownContent,
                createdAt: Date.now(),
                messageType: 'markdown-report',
              });
            } else {
              // Fallback: generate combined HTML message (original behavior)
              const html = generateBlocksHtml(nodeBlocks);
              const text = html || '已处理完成';
              const assistantMessage: any = {
                sessionId,
                role: 'assistant',
                content: text,
                messageType: 'html',
                metadata: threadId ? JSON.stringify({ threadId }) : undefined,
              };
              try {
                await saveMessageApi(sessionId, assistantMessage);
              } catch {
                // ignore
              }

              if (threadId) {
                sessionThreadIds.set(sessionId, threadId);
              }

              resolve({
                id: uid(),
                role: 'assistant',
                content: text,
                createdAt: Date.now(),
                messageType: 'html',
              });
            }
          },
        );

        if (signal?.aborted) {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', onAbort);
          reject(new DOMException('Aborted', 'AbortError'));
          return;
        }

        const abortHandler = () => {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', abortHandler);
          reject(new DOMException('Aborted', 'AbortError'));
        };
        signal?.addEventListener('abort', abortHandler, { once: true });
      } else {
        // --- Non-SQL intelligent agent: use streamFrontChat with simple text accumulation ---
        let fullText = '';
        let abortRequested = false;

        const onAbort = () => {
          abortRequested = true;
        };
        signal?.addEventListener('abort', onAbort, { once: true });

        const closeStream = streamFrontChat(
          {
            sessionId,
            content,
            agentSn: currentAgent?.sn ?? '',
            type: currentAgent?.type || '',
            // T-07：附件 id（SQL/nl2sql 走 streamFrontChatSql，不接附件：模态不同且为 GET 传参）
            attachmentIds: attachmentIdsForThisTurn,
          },
          async (response) => {
            if (abortRequested) return;
            if (response.text) {
              fullText += response.text;
              onProgress?.(markdownToHtml(fullText));
            }
          },
          async (error) => {
            signal?.removeEventListener('abort', onAbort);
            if (abortRequested) return;
            reject(error);
          },
          async () => {
            signal?.removeEventListener('abort', onAbort);
            if (abortRequested) return;

            const text = fullText || '已处理完成';
            const html = markdownToHtml(text);
            const assistantMessage: any = {
              sessionId,
              role: 'assistant',
              content: html,
              messageType: 'text',
            };
            try {
              await saveMessageApi(sessionId, assistantMessage);
            } catch {
              /* ignore */
            }

            resolve({
              id: uid(),
              role: 'assistant',
              content: html,
              createdAt: Date.now(),
              messageType: 'text',
            });
          },
        );

        if (signal?.aborted) {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', onAbort);
          reject(new DOMException('Aborted', 'AbortError'));
          return;
        }

        const abortHandler = () => {
          abortRequested = true;
          closeStream();
          signal?.removeEventListener('abort', abortHandler);
          reject(new DOMException('Aborted', 'AbortError'));
        };
        signal?.addEventListener('abort', abortHandler, { once: true });
      }
    });

    return reply;
  },
};

export async function handleHarnessConfirm(
  sessionId: string,
  agentSn: string,
  allowed: boolean,
  onNodeMessage?: (message: ChatMessage) => void,
  onProgress?: (text: string, thinking?: string) => void,
): Promise<string> {
  let currentNodeName: string | null = null;
  let currentBlockIndex = -1;
  const nodeBlocks: GraphNodeResponse[][] = [];
  let markdownContent = '';

  await confirmFrontHarnessChat(
    { sessionId, agentSn, allowed },
    async (response) => {
      if (response.error) return;

      const isNewNode =
        currentNodeName === null || response.nodeName !== currentNodeName;

      if (isNewNode) {
        if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
          const nodeHtml = generateNodeHtml(nodeBlocks[currentBlockIndex]!);
          const storeMsg: ChatMessage = {
            id: uid(),
            role: 'assistant',
            content: nodeHtml,
            createdAt: Date.now(),
            messageType: 'html',
          };
          onNodeMessage?.(storeMsg);
        }
        nodeBlocks.push([{ ...response, text: response.text }]);
        currentBlockIndex = nodeBlocks.length - 1;
        currentNodeName = response.nodeName;
      } else {
        if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
          nodeBlocks[currentBlockIndex]?.push({
            ...response,
            text: response.text,
          });
        } else {
          nodeBlocks.push([{ ...response, text: response.text }]);
          currentBlockIndex = nodeBlocks.length - 1;
          currentNodeName = response.nodeName;
        }
      }

      markdownContent = '';
      for (const block of nodeBlocks) {
        for (const nd of block) {
          if (nd.textType === TextType.MARK_DOWN) {
            markdownContent += nd.text || '';
          }
        }
      }
      if (markdownContent) {
        onProgress?.(markdownToHtml(markdownContent));
      }
    },
    async () => {
      if (currentBlockIndex >= 0 && nodeBlocks[currentBlockIndex]) {
        const nodeHtml = generateNodeHtml(nodeBlocks[currentBlockIndex]!);
        const storeMsg: ChatMessage = {
          id: uid(),
          role: 'assistant',
          content: nodeHtml,
          createdAt: Date.now(),
          messageType: 'html',
        };
        onNodeMessage?.(storeMsg);
      }
    },
  );

  return markdownContent || generateBlocksHtml(nodeBlocks) || '已处理完成';
}
