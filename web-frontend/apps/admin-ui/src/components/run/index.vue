<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue';
import ChatFilesPanel from '#/views/front/components/ChatFilesPanel.vue';
import ThinkingBlock from '#/views/front/components/ThinkingBlock.vue';

// thinking-display T-04：admin 运行页思考累加器（与会话绑定，流式实时可见 + 报告保存携带 metadata）
const thinkingMap = reactive(new Map<string, { ms: number; start: number; text: string }>());
function getThinkingTrack(sid: string) {
  let t = thinkingMap.get(sid);
  if (!t) { t = { text: '', ms: 0, start: 0 }; thinkingMap.set(sid, t); }
  return t;
}
function thinkingMetaOf(sid: string): string | undefined {
  const t = thinkingMap.get(sid);
  if (!t || !t.text) return undefined;
  const clipped = t.text.length > 65_536 ? `${t.text.slice(0, 65_536)}…（已截断）` : t.text;
  return JSON.stringify({ thinking: clipped, thinkingMs: t.ms });
}
const filesPanelRef = ref<InstanceType<typeof ChatFilesPanel> | null>(null);
import { notifyFilesChanged } from '#/api/core/agentFiles';
import { flushStreamSnapshot, forceClearStreamSnapshot, readStreamSnapshot, saveStreamSnapshot } from '#/utils/stream-snapshot';
import type { Agent } from '#/api/core/agent';
import type { ChatMessage, ChatSession } from '#/api/core/chat';
import type {
  ChatApiRequest,
  ConfirmButton,
  GraphNodeResponse,
  GraphRequest,
  HarnessChatRequest,
} from '#/api/core/graph';
import { harnessTurnCancelApi, harnessTurnStatusApi } from '#/api/core/graph';
import type {
  ResultData,
  ResultSetData,
  ResultSetDisplayConfig,
} from '#/api/core/resultSet';
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAvatar,
  ElButton,
  ElButtonGroup,
  ElEmpty,
  ElIcon,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTooltip,
} from 'element-plus';
import { FolderOpened,
  ArrowDown,
  CircleClose,
  Close,
  Document,
  Download,
  FullScreen,
  Loading,
  Paperclip,
  Promotion,
  WarningFilled,
} from '@element-plus/icons-vue';

import {
  confirmHarnessSignalApi,
  createSessionApi,
  getAgentApi,
  getSessionMessagesApi,
  saveMessageApi,
  streamChat,
  streamHarnessChat,
  streamHarnessTurnJoin,
  streamSearch,
  TextType,
} from '#/api';

import { downloadHtmlReportApi } from '#/api/core/chat';

// T-07：对话附件（chat-attachment-understanding）—— API 封装 + 两端规则单一来源
import {
  fetchAttachmentThumbUrlApi,
  formatAttachmentSize,
  previewAttachmentApi,
  uploadChatAttachmentsApi,
} from '#/api/core/chatAttachment';
import type { ChatAttachmentMeta } from '@phoenix/chat-shared';
import {
  ATTACHMENT_MAX_FILES_PER_SEND,
  attachmentCountMessage,
  validateAttachmentsLocally,
} from '@phoenix/chat-shared';

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

import ChatSessionSidebar from '#/components/run/ChatSessionSidebar.vue';
import HumanFeedback from '#/components/run/HumanFeedback.vue';
import PresetQuestions from '#/components/run/PresetQuestions.vue';
import MarkdownAgentContainer from '#/components/run/markdown';
import ReportHtmlView from '#/components/run/ReportHtmlView.vue';
import ResultSetDisplay from '#/components/run/ResultSetDisplay.vue';

declare global {
  interface Window {
    copyTextToClipboard: (btn: HTMLElement) => void;
    handleResultSetPagination: (
      btn: HTMLElement,
      direction: 'prev' | 'next',
    ) => void;
  }
}

interface SessionRuntimeState {
    snapText?: string;
  isStreaming: boolean;
  // BUG-81：服务端轮次在跑（本页仅轮询）也必须**按会话记账**——页面级单例会让
  // 别的会话的"进行中"泄漏到当前会话（输入框锁死+按钮变终止，切不回来）
  remoteRunning: boolean;
  /** T-04：join 追流的关闭句柄（切会话/停止时必须关，防泄漏订阅） */
  closeJoin: (() => void) | null;
  /** T-10：服务端静默心跳提示（按会话记账，如「工具执行中 · 已静默 23s」） */
  silenceText: string;
  // BUG-81：HITL 确认条同族视图态（全局会让 A 的待确认锁死 B 的输入框）
  showHarnessConfirm: boolean;
  pendingConfirmButtons: ConfirmButton[];
  pendingConfirmPlanHtml: string;
  pendingConfirmSessionId: string;
  pendingConfirmAgentId: number;
  nodeBlocks: GraphNodeResponse[][];
  closeStream: (() => void) | null;
  lastRequest: GraphRequest | null;
  htmlReportContent: string;
  htmlReportSize: number;
  markdownReportContent: string;
}

function useSessionStateManager() {
  const sessionStates = reactive<Map<string, SessionRuntimeState>>(new Map());

  /** T-10：心跳帧 → 会话级静默提示；真实增量/结束 → 清除 */
  const applySilenceFrame = (sid: string, frame: any) => {
    if (!frame) return;
    if (frame.silenceMs != null) {
      const sec = Math.round(Number(frame.silenceMs) / 1000);
      const st = getSessionState(sid);
      if (st) st.silenceText = `${frame.phaseLabel || '处理中'} · 已静默 ${sec}s`;
      return;
    }
    if (frame.text || frame.thinking || frame.end) {
      const st = getSessionState(sid);
      if (st) st.silenceText = '';
    }
  };

  const getSessionState = (sessionId: string): SessionRuntimeState => {
    if (!sessionStates.has(sessionId)) {
      sessionStates.set(sessionId, {
        isStreaming: false,
        remoteRunning: false,
        silenceText: '',
        closeJoin: null,
        showHarnessConfirm: false,
        pendingConfirmButtons: [],
        pendingConfirmPlanHtml: '',
        pendingConfirmSessionId: '',
        pendingConfirmAgentId: 0,
        nodeBlocks: [],
        closeStream: null,
        lastRequest: null,
        htmlReportContent: '',
        htmlReportSize: 0,
        markdownReportContent: '',
      });
    }
    return sessionStates.get(sessionId)!;
  };

  const syncStateToView = (
    sessionId: string,
    viewState: {
      isStreaming: { value: boolean };
      remoteRunning: { value: boolean };
      nodeBlocks: { value: GraphNodeResponse[][] };
    },
  ) => {
    const state = getSessionState(sessionId);
    viewState.isStreaming.value = state.isStreaming;
    viewState.remoteRunning.value = state.remoteRunning;
    viewState.nodeBlocks.value = state.nodeBlocks;
  };

  const saveViewToState = (
    sessionId: string,
    viewState: {
      isStreaming: { value: boolean };
      remoteRunning: { value: boolean };
      nodeBlocks: { value: GraphNodeResponse[][] };
    },
  ) => {
    const state = getSessionState(sessionId);
    state.isStreaming = viewState.isStreaming.value;
    state.remoteRunning = viewState.remoteRunning.value;
    state.nodeBlocks = viewState.nodeBlocks.value;
  };

  const deleteSessionState = (sessionId: string) => {
    const state = sessionStates.get(sessionId);
    if (state?.closeStream) {
      state.closeStream();
    }
    if (state?.closeJoin) {
      state.closeJoin();
    }
    sessionStates.delete(sessionId);
  };

  return {
    sessionStates,
    getSessionState,
    applySilenceFrame,
    syncStateToView,
    saveViewToState,
    deleteSessionState,
  };
}

const route = useRoute();
const router = useRouter();

const agent = ref<Agent>({});
const currentSession = ref<ChatSession | null>(null);
const currentMessages = ref<ChatMessage[]>([]);
const userInput = ref('');
const {
  getSessionState,
  syncStateToView,
  saveViewToState,
  deleteSessionState,
  applySilenceFrame,
} = useSessionStateManager();
const isStreaming = ref(false);

// BUG-75：区分「本页在直播」(isStreaming) 与「服务端轮次在跑、本页仅轮询」(remoteRunning)——
// 前者渲染直播区，后者只控制输入禁用/终止按钮；混用会导致刷新后上下双输出窗口
const remoteRunning = ref(false);
const nodeBlocks = ref<GraphNodeResponse[][]>([]);
const options = ref({
  markdownIt: { linkify: true },
  linkAttributes: { attrs: { target: '_blank', rel: 'noopener' } },
});
const requestOptions = reactive({
  humanFeedback: false,
  nl2sqlOnly: false,
  reportFormat: 'markdown' as 'markdown' | 'html',
});
const showReportFullscreen = ref(false);
const fullscreenReportContent = ref('');
const fullscreenReportFormat = ref<'markdown' | 'html'>('markdown');
const messageReportFormat = reactive<Record<string, 'markdown' | 'html'>>({});
function getMessageFormat(messageId: number | undefined): 'markdown' | 'html' {
  return (messageId != null && messageReportFormat[messageId]) || 'markdown';
}
const inputControlsCollapsed = ref(false);
const autoScroll = ref(true);
const chatContainer = ref<HTMLElement | null>(null);

// long-turn-resilience：长轮活性指示（每秒刷新已用时，证明"在跑"而非卡死）
const streamElapsedText = ref('');

/** T-10：当前会话的静默提示（服务端心跳驱动；切会话自动切换） */
const currentSilenceText = computed(() => {
  const sid = currentSession.value?.id;
  return sid ? (getSessionState(sid)?.silenceText ?? '') : '';
});
let streamElapsedStart = 0;
let streamElapsedTimer: ReturnType<typeof setInterval> | null = null;
watch(isStreaming, (on: boolean) => {
  if (streamElapsedTimer) {
    clearInterval(streamElapsedTimer);
    streamElapsedTimer = null;
  }
  if (!on) {
    streamElapsedText.value = '';
    return;
  }
  streamElapsedStart = Date.now();
  streamElapsedTimer = setInterval(() => {
    const sec = Math.floor((Date.now() - streamElapsedStart) / 1000);
    const mm = String(Math.floor(sec / 60)).padStart(2, '0');
    const ss = String(sec % 60).padStart(2, '0');
    streamElapsedText.value = `正在执行（已用时 ${mm}:${ss}）`;
  }, 1000);
});
const showHumanFeedback = ref(false);
const showHarnessConfirm = ref(false);
const pendingConfirmButtons = ref<ConfirmButton[]>([]);
// BUG-58 后续体验修：admin 确认条无正文区——计划文本（思考通道）在此呈现，用户看得见"确认什么"
const pendingConfirmPlanHtml = ref('');
const pendingConfirmSessionId = ref('');
const pendingConfirmAgentId = ref<number>(0);
const lastRequest = ref<GraphRequest | null>(null);

// BUG-81：确认条一族同属"每会话"视图态。做成页面级单例时，A 会话的待确认会把 B 会话的
// 输入框一起锁死（且确认条按钮/目标会话会串到 B）。与 isStreaming 同法：切会话时存取本会话副本。
function saveConfirmToState(sessionId: string) {
  const st = getSessionState(sessionId);
  st.showHarnessConfirm = showHarnessConfirm.value;
  st.pendingConfirmButtons = pendingConfirmButtons.value;
  st.pendingConfirmPlanHtml = pendingConfirmPlanHtml.value;
  st.pendingConfirmSessionId = pendingConfirmSessionId.value;
  st.pendingConfirmAgentId = pendingConfirmAgentId.value;
}

function syncConfirmFromState(sessionId: string) {
  const st = getSessionState(sessionId);
  showHarnessConfirm.value = st.showHarnessConfirm;
  pendingConfirmButtons.value = st.pendingConfirmButtons;
  pendingConfirmPlanHtml.value = st.pendingConfirmPlanHtml;
  pendingConfirmSessionId.value = st.pendingConfirmSessionId;
  pendingConfirmAgentId.value = st.pendingConfirmAgentId;
}

function clearConfirmView() {
  showHarnessConfirm.value = false;
  pendingConfirmButtons.value = [];
  pendingConfirmPlanHtml.value = '';
  pendingConfirmSessionId.value = '';
  pendingConfirmAgentId.value = 0;
}

// 确认条任何变动都镜像回当前会话副本（否则"已确认"的旧条会在切回该会话时复活）
watch(
  [
    showHarnessConfirm,
    pendingConfirmButtons,
    pendingConfirmPlanHtml,
    pendingConfirmSessionId,
    pendingConfirmAgentId,
  ],
  () => {
    const sid = currentSession.value?.id;
    if (sid) saveConfirmToState(sid);
  },
);
const resultSetDisplayConfig = reactive<ResultSetDisplayConfig>({
  showSqlResults: false,
  pageSize: 20,
});

const agentId = computed(() => Number(route.params.id));

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

window.handleResultSetPagination = (
  btn: HTMLElement,
  direction: 'prev' | 'next',
) => {
  const container = btn.closest('.result-set-container');
  if (!container) return;
  const currentPageElement = container.querySelector(
    '.result-set-current-page',
  );
  const prevBtn = container.querySelector(
    '.result-set-pagination-prev',
  ) as HTMLButtonElement;
  const nextBtn = container.querySelector(
    '.result-set-pagination-next',
  ) as HTMLButtonElement;
  const pages = container.querySelectorAll('.result-set-page');
  if (!currentPageElement || !prevBtn || !nextBtn || pages.length === 0) return;
  let currentPage = Number.parseInt(currentPageElement.textContent || '1');
  const totalPages = pages.length;
  if (direction === 'prev' && currentPage > 1) currentPage--;
  else if (direction === 'next' && currentPage < totalPages) currentPage++;
  pages.forEach((page: Element) =>
    page.classList.remove('result-set-page-active'),
  );
  const targetPage = container.querySelector(
    `.result-set-page[data-page="${currentPage}"]`,
  );
  if (targetPage) targetPage.classList.add('result-set-page-active');
  currentPageElement.textContent = currentPage.toString();
  prevBtn.disabled = currentPage === 1;
  nextBtn.disabled = currentPage === totalPages;
};

async function loadAgent() {
  try {
    const result = await getAgentApi(agentId.value);
    if (!result) {
      ElMessage.error('智能体不存在');
      router.push('/agent/list');
      return;
    }
    agent.value = result;
  } catch {
    ElMessage.error('加载智能体失败');
    router.push('/agent/list');
  }
}

async function selectSession(session: ChatSession | null) {
  if (currentSession.value) {
    saveViewToState(currentSession.value.id, { isStreaming, remoteRunning, nodeBlocks });
    saveConfirmToState(currentSession.value.id);
    // T-04：离开会话即关掉它的追流订阅（回来时会重新探测+重连），避免无主订阅堆积
    const prevState = getSessionState(currentSession.value.id);
    prevState.closeJoin?.();
    prevState.closeJoin = null;
  }
  currentSession.value = session;
  try {
    if (session === null) {
      currentMessages.value = [];
      nodeBlocks.value = [];
      isStreaming.value = false;
      remoteRunning.value = false;
      clearConfirmView();
      return;
    }
    syncStateToView(session.id, { isStreaming, remoteRunning, nodeBlocks });
    syncConfirmFromState(session.id);
    // T-04：join 续渲需要"原始 markdown 基线"（applyServerRowRender 会把 content 转成 HTML），故转换前先取
    const rows = (await getSessionMessagesApi(session.id)) as any[];
    let joinBase = '';
    let joinBaseThinking = '';
    for (let i = rows.length - 1; i >= 0; i--) {
      if (rows[i]?.role === 'assistant') {
        joinBase = String(rows[i]?.content ?? '');
        joinBaseThinking = String(rows[i]?.thinking ?? '');
        break;
      }
    }
    currentMessages.value = applyServerRowRender(rows) as any;
    // detached-stream T-05 + long-turn-resilience T-04：admin 进行中的轮次 → **join 追流**
    // （服务端 replay 全量帧 + live 原地续渲），替换原 5s 轮询；无进行中轮则静默返回
    void (async () => {
      try {
        // BUG-81：探测结果必须**回写本会话**（true/false 都写），否则上一会话的 remoteRunning
        // 会残留到新会话：输入框不可写 + 只剩终止按钮（用户实测"切会话就发不出去了"）
        const running = await harnessTurnStatusApi(session.id);
        // BUG-74：闭包必须带会话守卫——切会话/新建会话后严禁把旧会话内容写进当前视图
        if (currentSession.value?.id !== session.id) {
          getSessionState(session.id).remoteRunning = running;
          return;
        }
        remoteRunning.value = running;
        getSessionState(session.id).remoteRunning = running;
        if (!running) return;

        let joined = '';
        let joinedThinking = '';
        let lastRender = 0;
        let finished = false;
        let attempts = 0;

        // 原地续渲（绝不新建气泡 → 防 BUG-75 双输出窗口）；节流 ≥150ms：BUG-70 的教训是逐帧全量重渲会 O(n²) 打死主线程
        const paint = (force: boolean) => {
          if (currentSession.value?.id !== session.id) return;
          const now = Date.now();
          if (!force && now - lastRender < 150) return;
          lastRender = now;
          const list = currentMessages.value as any[];
          let idx = -1;
          for (let i = list.length - 1; i >= 0; i--) {
            if (list[i]?.role === 'assistant') {
              idx = i;
              break;
            }
          }
          if (idx < 0) {
            // 服务端尚未落助手行：本地补一个流式气泡（后续帧原地更新它）
            list.push({
              id: `join-${Date.now()}`,
              role: 'assistant',
              messageType: 'md-card',
              content: '',
              thinking: '',
              streaming: true,
            } as any);
            idx = list.length - 1;
          }
          const row = list[idx];
          row.messageType = row.messageType || 'md-card';
          row.content = markdownToHtml(joinBase + joined);
          if (joinedThinking) row.thinking = joinBaseThinking + joinedThinking;
          row.streaming = true;
          currentMessages.value = [...list];
        };

        const finish = async () => {
          if (finished) return;
          finished = true;
          const st = getSessionState(session.id);
          st.closeJoin = null;
          if (currentSession.value?.id !== session.id) return;
          remoteRunning.value = false;
          st.remoteRunning = false;
          try {
            // 末次拉取：以服务端定稿行为准（join 只负责"看得见"，落库不归它管）
            currentMessages.value = applyServerRowRender(await getSessionMessagesApi(session.id) as any[]) as any;
          } catch { /* ignore */ }
        };

        // BUG-76：连接异常=不确定态，绝不当作"轮次已结束"——重探状态仍有界重连
        const reconnect = async () => {
          getSessionState(session.id).closeJoin = null;
          if (currentSession.value?.id !== session.id) return;
          attempts += 1;
          if (attempts <= 5) {
            setTimeout(() => {
              if (currentSession.value?.id === session.id) connect();
            }, 2000);
            return;
          }
          await finish();
        };

        const connect = () => {
          const close = streamHarnessTurnJoin(
            session.id,
            agentId.value,
            async (response) => {
              if (currentSession.value?.id !== session.id) return; // BUG-74 会话守卫
              applySilenceFrame(session.id, response);
              if ((response as any).agentFiles) notifyFilesChanged();
              const piece = String((response as any).text || '');
              const th = String((response as any).thinking || '');
              if (!piece && !th) return;
              joined += piece;
              joinedThinking += th;
              paint(false);
            },
            async () => {
              await reconnect();
            },
            async () => {
              paint(true);
              await finish();
            },
          );
          getSessionState(session.id).closeJoin = close;
        };

        connect();
        if (currentSession.value?.id !== session.id) {
          // 探测期间已切走：立即关闭，避免无主订阅
          getSessionState(session.id).closeJoin?.();
          getSessionState(session.id).closeJoin = null;
        }
      } catch { /* ignore */ }
    })();
    // thinking-display R-05：历史 metadata 解析思考（旧行无键静默）
    for (const m of currentMessages.value as any[]) {
      try {
        const md = typeof m.metadata === 'string' ? JSON.parse(m.metadata) : m.metadata;
        if (md && typeof md.thinking === 'string') { m.thinking = md.thinking; m.thinkingMs = md.thinkingMs; }
      } catch { /* 无 metadata 或非 JSON */ }
      // BUG-57：服务端行装载转 html（与轮询共用）
      applyServerRowRender(currentMessages.value as any[]);
      // BUG-53 A′：刷新中断快照回显（本地气泡，不落库）
      const snap = readStreamSnapshot(session.id);
      if (snap) {
        forceClearStreamSnapshot(session.id);
        const lastM = currentMessages.value[currentMessages.value.length - 1] as any;
        const gen = !!lastM && lastM.role === 'assistant' && String(lastM.metadata ?? '').includes('generating');
        if ((!lastM || lastM.role === 'user') && !gen) {
          currentMessages.value.push({
            id: `snap-${snap.ts}`,
            role: 'assistant',
            content: snap.contentHtml,
            createdAt: snap.ts,
            messageType: 'text',
            metadata: { interrupted: true, interruptedAt: snap.ts },
            thinking: snap.thinking,
          } as any);
        }
      }
    }
    await nextTick();
    scrollToBottom();
  } catch {
    ElMessage.error('加载消息失败');
  }
}

/** T-04：中断轮文案带**时间归属**（`不是本轮`的语义靠时间点自明，避免用户把旧中断当成新轮） */
function interruptedAtText(message: any): string {
  const ts = Number(message?.metadata?.interruptedAt ?? message?.createdAt ?? 0);
  if (!ts) return '时间未知';
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

// ===== T-07：对话附件（chat-attachment-understanding）=====
/** 待发送附件草稿：**按会话分片**（L-20：切会话不串附件，不用单例 ref） */
const pendingAttachments = ref<Map<string, ChatAttachmentMeta[]>>(new Map());
const attachmentInputRef = ref<HTMLInputElement | null>(null);
const uploadingAttachments = ref(false);
/** 缩略图 object URL 缓存（卸载时统一 revoke，防泄漏） */
const thumbUrlCache = new Map<number, string>();

function currentPendingAttachments(): ChatAttachmentMeta[] {
  const sid = currentSession.value?.id;
  return (sid && pendingAttachments.value.get(sid)) || [];
}

function pickAttachmentFiles() {
  attachmentInputRef.value?.click();
}

async function onAttachmentPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = [...(input.files || [])];
  input.value = ''; // 允许再次选同一文件
  if (!files.length) return;
  const sid = currentSession.value?.id;
  if (!sid) {
    ElMessage.warning('请先创建或选择会话');
    return;
  }
  const existed = pendingAttachments.value.get(sid) || [];
  if (existed.length + files.length > ATTACHMENT_MAX_FILES_PER_SEND) {
    ElMessage.error(attachmentCountMessage(existed.length + files.length));
    return;
  }
  // 前端即时反馈（后端仍做「扩展名 + 真实内容类型」双判定，R-01）
  const checked = validateAttachmentsLocally(files);
  for (const c of checked.filter((x) => x.reason)) {
    ElMessage.error(`${c.file.name}：${c.reason}`);
  }
  const okFiles = checked.filter((x) => !x.reason).map((x) => x.file);
  if (!okFiles.length) return;
  uploadingAttachments.value = true;
  try {
    const res = await uploadChatAttachmentsApi(okFiles, sid);
    // R-03：非法者逐个指明，不整批静默失败
    for (const r of res.rejected) ElMessage.error(`${r.fileName}：${r.reason}`);
    if (res.accepted.length) {
      const next = new Map(pendingAttachments.value);
      next.set(sid, [...existed, ...res.accepted]);
      pendingAttachments.value = next;
      await loadThumbs(res.accepted);
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '附件上传失败');
  } finally {
    uploadingAttachments.value = false;
  }
}

function removePendingAttachment(id: number) {
  const sid = currentSession.value?.id;
  if (!sid) return;
  const next = new Map(pendingAttachments.value);
  next.set(sid, (next.get(sid) || []).filter((a) => a.id !== id));
  pendingAttachments.value = next;
}

/** 缩略图必须 fetch+blob（<img src> 带不了鉴权头 —— T-05 交接注记） */
async function loadThumbs(list: ChatAttachmentMeta[]) {
  for (const a of list) {
    if (a.kind === 'IMAGE' && !thumbUrlCache.has(a.id)) {
      const url = await fetchAttachmentThumbUrlApi(a.id);
      if (url) thumbUrlCache.set(a.id, url);
    }
  }
}

function cachedThumb(id: number): string | undefined {
  return thumbUrlCache.get(id);
}

/** BUG-157：点击附件 = **展示优先**（可预览格式新标签页打开；office 类回退下载并提示） */
async function onDownloadAttachment(att: ChatAttachmentMeta) {
  try {
    const result = await previewAttachmentApi(att);
    if (result === 'downloaded') {
      ElMessage.info('该格式浏览器无法在线预览，已改为下载');
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '打开附件失败');
  }
}

onBeforeUnmount(() => {
  for (const u of thumbUrlCache.values()) URL.revokeObjectURL(u);
  thumbUrlCache.clear();
});

async function sendMessage() {
  if (!userInput.value.trim()) {
    ElMessage.warning('请输入请求消息！');
    return;
  }
  if (!currentSession.value || isStreaming.value) {
    ElMessage.warning('智能体正在处理中，请稍后...');
    return;
  }

  const needsTitle =
    !currentSession.value.title || currentSession.value.title === '新会话';
  const sessionId = currentSession.value.id;
  thinkingMap.set(sessionId, { text: '', ms: 0, start: 0 }); // 新一轮思考轨迹重置

  // T-07：本会话待发送附件（草稿按会话分片）
  const sentAttachments = currentPendingAttachments();
  const attachmentIds = sentAttachments.map((a) => a.id);
  const userMessage: ChatMessage = {
    sessionId,
    role: 'user',
    content: userInput.value,
    messageType: 'text',
    titleNeeded: needsTitle,
    // 附件与告知写入 metadata：历史回看仍可见（R-10/R-07）；
    // 后端 saveMessage 据 metadata.attachmentIds 回填附件 message_id（S9）
    ...(attachmentIds.length
      ? { metadata: JSON.stringify({ attachmentIds, attachments: sentAttachments }) }
      : {}),
  };
  (userMessage as any).attachments = sentAttachments.length ? sentAttachments : undefined;

  try {
    await saveMessageApi(sessionId, userMessage);
    currentMessages.value.push(userMessage);

    const sessionState = getSessionState(sessionId);
    const request: GraphRequest = {
      agentId: String(agentId.value),
      query: userInput.value,
      humanFeedback: requestOptions.humanFeedback,
      nl2sqlOnly: requestOptions.nl2sqlOnly,
      rejectedPlan: false,
      humanFeedbackContent: undefined,
      threadId: sessionState.lastRequest?.threadId || undefined,
      attachmentIds: attachmentIds.length ? attachmentIds : undefined,
    };

    userInput.value = '';
    // 清空本会话附件草稿（其它会话草稿不受影响 —— L-20 分片）
    if (attachmentIds.length) {
      const next = new Map(pendingAttachments.value);
      next.set(sessionId, []);
      pendingAttachments.value = next;
    }
    await sendGraphRequest(request, true);
  } catch {
    ElMessage.error('发送消息失败');
  }
}

async function sendGraphRequest(request: GraphRequest, rejectedPlan: boolean) {
  if (!currentSession.value) return;
  const sessionId = currentSession.value.id;
  const sessionTitle = currentSession.value.title;
  const sessionState = getSessionState(sessionId);

  try {
    lastRequest.value = request;
    isStreaming.value = true;
    nodeBlocks.value = [];
    showHarnessConfirm.value = false;

    let currentNodeName: string | null = null;
    let currentBlockIndex = -1;
    const pendingSavePromises: Promise<void>[] = [];

    resetReportState(sessionState, request);

    const saveNodeMessage = async (
      node: GraphNodeResponse[],
    ): Promise<void> => {
      if (!node || node.length === 0) return;
      // BL-22 R-05：harness 轮次由服务端 TurnManager 落库（单行 markdown），客户端保存退役防双泡；
      // graph(NL2SQL) 流不经 TurnManager，保存链路维持原样
      if (agent.value.type === 'harness') return;

      const first = node[0]!;
      if (first.textType === TextType.RESULT_SET) {
        try {
          const resultData: ResultData = JSON.parse(first.text);
          if (
            resultData.displayStyle?.type &&
            resultData.displayStyle.type !== 'table'
          ) {
            const aiMessage: ChatMessage = {
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
      const aiMessage: ChatMessage = {
        sessionId,
        role: 'assistant',
        content: nodeHtml,
        messageType: 'html',
      };
      await saveMessageApi(sessionId, aiMessage);
    };

    let closeStreamFn: (() => void) | null = null;

    // R-13：按 type 分流；harness 以 agentId 寻址（不再有 sn || id 兜底）
    switch (agent.value.type) {
      case 'agent': {
        if (!agent.value.sn) {
          isStreaming.value = false;
          ElMessage.error('该智能体缺少运行时标识（sn），暂时无法对话');
          return;
        }
        closeStreamFn = startAgentStream();
        break;
      }
      case 'harness':
        closeStreamFn = startHarnessStream();
        break;
      default:
        closeStreamFn = startSearchStream();
        break;
    }

    function startAgentStream() {
      const chatRequest: ChatApiRequest = {
        sessionId,
        content: request.query,
        agentSn: String(agent.value.sn),
        type: 'agent',
        attachmentIds: request.attachmentIds,
      };

      return streamChat(
        chatRequest,
        async (response: GraphNodeResponse) => {
          if (response.error) return;

          handleNodeResponse(response);
        },
        async (error: Error) => {
          ElMessage.error(`请求失败: ${error.message}`);
          handleStreamError(error);
        },
        async () => {
          if (pendingSavePromises.length > 0) {
            await Promise.all(pendingSavePromises);
          }

          if (sessionState.htmlReportContent) {
            const htmlReportMessage: ChatMessage = {
              sessionId,
              role: 'assistant',
              content: sessionState.htmlReportContent,
              messageType: 'html-report',
            };
            try {
              await saveMessageApi(sessionId, htmlReportMessage);
              if (currentSession.value?.id === sessionId) {
                currentMessages.value.push(htmlReportMessage);
              }
            } catch {
              ElMessage.error('保存HTML报告失败！');
            }
            sessionState.isStreaming = false;
            if (currentSession.value?.id === sessionId) {
              isStreaming.value = false;
              nodeBlocks.value = [];
            }
          } else if (sessionState.markdownReportContent) {
            const markdownMessage: ChatMessage = {
              sessionId,
              role: 'assistant',
              content: sessionState.markdownReportContent,
              messageType: 'markdown-report',
            };
            try {
              await saveMessageApi(sessionId, markdownMessage);
              if (currentSession.value?.id === sessionId) {
                currentMessages.value.push(markdownMessage);
              }
            } catch {
              console.error('保存Markdown报告失败');
            }
            sessionState.isStreaming = false;
            if (currentSession.value?.id === sessionId) {
              isStreaming.value = false;
              nodeBlocks.value = [];
            }
          } else {
            // streamChat 路径：合并所有节点文本作为报告保存
            const allText = sessionState.nodeBlocks
              .flat()
              .map((n) => n.text)
              .filter(Boolean)
              .join('');
            if (allText) {
              const reportMessage: ChatMessage = {
                sessionId,
                role: 'assistant',
                content: allText,
                messageType: 'markdown-report',
              };
              try {
                await saveMessageApi(sessionId, reportMessage);
                if (currentSession.value?.id === sessionId) {
                  currentMessages.value.push(reportMessage);
                }
              } catch {
                console.error('保存报告失败');
              }
              sessionState.isStreaming = false;
              if (currentSession.value?.id === sessionId) {
                isStreaming.value = false;
                nodeBlocks.value = [];
              }
            } else {
              if (
                currentBlockIndex >= 0 &&
                sessionState.nodeBlocks[currentBlockIndex]
              ) {
                await saveNodeMessage(
                  sessionState.nodeBlocks[currentBlockIndex]!,
                );
              }

              if (requestOptions.humanFeedback && rejectedPlan) {
                showHumanFeedback.value = true;
              } else {
                sessionState.isStreaming = false;
                if (currentSession.value?.id === sessionId) {
                  isStreaming.value = false;
                }
              }
            }
          }

          handleStreamComplete();
        },
      );
    }

    function startHarnessStream() {
      const harnessRequest: HarnessChatRequest = {
        sessionId,
        message: request.query,
        agentId: Number(agent.value.id),
        attachmentIds: request.attachmentIds,
      };

      return streamHarnessChat(
        harnessRequest,
        async (response: GraphNodeResponse) => {
          applySilenceFrame(sessionId, response);
          // BL-19：本轮产物登记事件 → 刷新文件面板（admin 运行页）
          if ((response as any).agentFiles) notifyFilesChanged();
          if ((response as any).text) {
            sessionState.snapText = (sessionState.snapText || '') + String((response as any).text);
            saveStreamSnapshot(sessionId, sessionState.snapText, getThinkingTrack(sessionId).text || undefined);
          }
          // thinking-display R-01：思考增量独立累加（严禁进 nodeBlocks 正文）
          if ((response as any).thinking) {
            const t = getThinkingTrack(sessionId);
            if (!t.start) t.start = Date.now();
            t.text += (response as any).thinking;
            if (!t.ms) t.ms = Date.now() - t.start;
          }
          if (response.error) {
            ElMessage.error(`处理错误: ${response.text}`);
            return;
          }

          handleNodeResponse(response);
        },
        async (error: Error) => {
          ElMessage.error(`流式请求失败: ${error.message}`);
          handleStreamError(error);
        },
        async () => {
          await handleStreamEnd();
        },
      );
    }

    function startSearchStream() {
      return streamSearch(
        request,
        async (response: GraphNodeResponse) => {
          if (response.error) {
            ElMessage.error(`处理错误: ${response.text}`);
            return;
          }

          if (sessionState.lastRequest) {
            sessionState.lastRequest.threadId = response.threadId;
          }

          handleNodeResponse(response);
        },
        async (error: Error) => {
          ElMessage.error(`流式请求失败: ${error.message}`);
          handleStreamError(error);
        },
        async () => {
          await handleStreamEnd();
        },
      );
    }

    function handleNodeResponse(response: GraphNodeResponse) {
      if (isReportGeneratorNode(response)) {
        const isNewNode =
          currentNodeName === null ||
          response.nodeName !== currentNodeName;

        if (isNewNode) {
          if (
            currentBlockIndex >= 0 &&
            sessionState.nodeBlocks[currentBlockIndex]
          ) {
            const p = saveNodeMessage(
              sessionState.nodeBlocks[currentBlockIndex]!,
            );
            pendingSavePromises.push(p);
          }
          sessionState.nodeBlocks.push([
            { ...response, text: response.text },
          ]);
          currentBlockIndex = sessionState.nodeBlocks.length - 1;
          currentNodeName = response.nodeName;
        }

        if (response.textType === 'HTML') {
          sessionState.htmlReportContent += response.text;
          sessionState.htmlReportSize =
            sessionState.htmlReportContent.length;
          const reportNode = sessionState.nodeBlocks.find(
            (block) =>
              block.length > 0 &&
              isReportGeneratorNode(block[0]!) &&
              block[0]!.textType === 'HTML',
          );
          if (reportNode) {
            reportNode[0]!.text = `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`;
          } else {
            sessionState.nodeBlocks.push([
              {
                ...response,
                text: `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`,
              },
            ]);
          }
        } else if (response.textType === 'MARK_DOWN') {
          sessionState.markdownReportContent += response.text;
          const reportNode = sessionState.nodeBlocks.find(
            (block) =>
              block.length > 0 &&
              isReportGeneratorNode(block[0]!) &&
              block[0]!.textType === 'MARK_DOWN',
          );
          if (reportNode) {
            reportNode[0]!.text = `正在收集Markdown报告... 已收集 ${sessionState.markdownReportContent.length} 字节`;
          } else {
            sessionState.nodeBlocks.push([
              {
                ...response,
                text: `正在收集Markdown报告... 已收集 ${sessionState.markdownReportContent.length} 字节`,
              },
            ]);
          }
        }
      } else if (response.textType === TextType.RESULT_SET) {
        currentNodeName = 'result_set';
        if (
          currentBlockIndex >= 0 &&
          sessionState.nodeBlocks[currentBlockIndex]
        ) {
          const p = saveNodeMessage(
            sessionState.nodeBlocks[currentBlockIndex]!,
          );
          pendingSavePromises.push(p);
        }
        sessionState.nodeBlocks.push([
          { ...response, text: response.text },
        ]);
        currentBlockIndex = sessionState.nodeBlocks.length - 1;
      } else {
        const isNewNode =
          currentNodeName === null ||
          response.nodeName !== currentNodeName;

        if (isNewNode) {
          if (
            currentBlockIndex >= 0 &&
            sessionState.nodeBlocks[currentBlockIndex]
          ) {
            const p = saveNodeMessage(
              sessionState.nodeBlocks[currentBlockIndex]!,
            );
            pendingSavePromises.push(p);
          }
          sessionState.nodeBlocks.push([
            { ...response, text: response.text },
          ]);
          currentBlockIndex = sessionState.nodeBlocks.length - 1;
          currentNodeName = response.nodeName;
        } else {
          if (
            currentBlockIndex >= 0 &&
            sessionState.nodeBlocks[currentBlockIndex]
          ) {
            sessionState.nodeBlocks[currentBlockIndex]!.push({
              ...response,
              text: response.text,
            });
          } else {
            sessionState.nodeBlocks.push([
              { ...response, text: response.text },
            ]);
            currentBlockIndex = sessionState.nodeBlocks.length - 1;
            currentNodeName = response.nodeName;
          }
        }
      }

      if (response.needConfirm && response.buttons && response.buttons.length > 0) {
        showHarnessConfirm.value = true;
        pendingConfirmButtons.value = response.buttons;
        // 确认条内容三级取源：正文 > 工具调用提炼(plan_exit.summary/命令) > 思考流兜底
        const tcList = ((response as any).toolCalls || []) as any[];
        const distilled = tcList
          .map((t: any) => {
            const inp = (t?.input || {}) as Record<string, any>;
            if (typeof inp.summary === 'string' && inp.summary.trim()) return inp.summary.trim();
            if (typeof inp.command === 'string' && inp.command.trim()) return `将执行命令：\`${inp.command.trim()}\``;
            if (typeof inp.path === 'string' && t?.name) return `将操作文件：\`${inp.path}\`（${t.name}）`;
            return t?.name ? `将调用工具：${t.name}` : '';
          })
          .filter(Boolean)
          .join('\n\n');
        const planSrc = getThinkingTrack(String(response.threadId ?? '')).text || '';
        const bodySrc = sessionState.nodeBlocks.flat().map((n: any) => n.text || '').join('');
        const pickSrc = bodySrc.trim() ? bodySrc : distilled || planSrc.slice(-1200);
        pendingConfirmPlanHtml.value = pickSrc ? markdownToHtml(pickSrc.slice(0, 4000)) : '';
        pendingConfirmSessionId.value = response.threadId;
        pendingConfirmAgentId.value = Number(response.agentId);
      }

      if (currentSession.value?.id === sessionId) {
        nodeBlocks.value = sessionState.nodeBlocks;
        if (autoScroll.value) scrollToBottom();
      }
    }

    function handleStreamError(error: Error) {
      if (pendingSavePromises.length > 0) {
        Promise.all(pendingSavePromises);
      }
      sessionState.isStreaming = false;
      sessionState.closeStream = null;
      currentNodeName = null;
      if (currentSession.value?.id === sessionId) {
        isStreaming.value = false;
        selectSession(currentSession.value);
      }
    }

    async function handleStreamEnd() {
      if (pendingSavePromises.length > 0) {
        await Promise.all(pendingSavePromises);
      }

      if (sessionState.htmlReportContent) {
        const htmlReportMessage: ChatMessage = {
          sessionId,
          role: 'assistant',
          content: sessionState.htmlReportContent,
          messageType: 'html-report',
          metadata: thinkingMetaOf(sessionId), // thinking-display R-05
        } as any;
        try {
          await saveMessageApi(sessionId, htmlReportMessage);
          if (currentSession.value?.id === sessionId) {
            currentMessages.value.push(htmlReportMessage);
          }
        } catch {
          ElMessage.error('保存HTML报告失败！');
        }
      } else if (sessionState.markdownReportContent) {
        const markdownMessage: ChatMessage = {
          sessionId,
          role: 'assistant',
          content: sessionState.markdownReportContent,
          messageType: 'markdown-report',
          metadata: thinkingMetaOf(sessionId), // thinking-display R-05
        } as any;
        try {
          await saveMessageApi(sessionId, markdownMessage);
          if (currentSession.value?.id === sessionId) {
            currentMessages.value.push(markdownMessage);
          }
        } catch {
          console.error('保存Markdown报告失败');
        }
      } else {
        if (
          currentBlockIndex >= 0 &&
          sessionState.nodeBlocks[currentBlockIndex]
        ) {
          await saveNodeMessage(
            sessionState.nodeBlocks[currentBlockIndex]!,
          );
        }

        if (requestOptions.humanFeedback && rejectedPlan) {
          showHumanFeedback.value = true;
        }
      }

      handleStreamComplete();
    }

    function handleStreamComplete() {
      sessionState.isStreaming = false;
      if (currentSession.value?.id === sessionId) {
        isStreaming.value = false;
        nodeBlocks.value = [];
      }

      ElMessage.success(`会话[${sessionTitle}]处理完成`);
      currentNodeName = null;
      sessionState.closeStream = null;
      if (currentSession.value?.id === sessionId) {
        selectSession(currentSession.value);
      }
    }

    sessionState.closeStream = closeStreamFn;
  } catch {
    ElMessage.error('发送消息失败');
    sessionState.isStreaming = false;
    sessionState.closeStream = null;
    if (currentSession.value?.id === sessionId) {
      isStreaming.value = false;
    }
  }
}

// BUG-57 完整修复：服务端行装载统一过这道转换（selectSession 首载与轮询刷新共用）
function applyServerRowRender(list: any[]) {
  for (const m of list) {
    const mdRaw = m.metadata;
    const metaStr = typeof mdRaw === 'string' ? mdRaw : JSON.stringify(mdRaw ?? {});
    // long-turn-resilience T-04：metadata 解析（thinking/thinkingMs/流式态）必须在本函数统一完成——
    // 5s 轮询 tick 只走本函数；此前仅首载单独解析 thinking，tick 覆盖后即丢
    // （BUG-72 用户实测：思考块"刷新后有、过几秒又消失"）
    try {
      const md = typeof mdRaw === 'string' ? JSON.parse(mdRaw) : mdRaw;
      if (md && typeof md.thinking === 'string') {
        (m as any).thinking = md.thinking;
        (m as any).thinkingMs = typeof md.thinkingMs === 'number' ? md.thinkingMs : undefined;
      }
      // T-07（R-10）：历史回看附件——从 metadata 还原并异步加载缩略图；
      // 旧消息无该键 ⇒ 不进此分支（S6 兼容）
      if (md && Array.isArray(md.attachments)) {
        (m as any).attachments = md.attachments;
        loadThumbs(md.attachments);
      }
    } catch { /* metadata 非 JSON：静默（旧行无键） */ }
    // 服务端进行中轮次（status=generating）刷新后须保持流式态，
    // 否则思考块硬编码 false → 明明是 thinking 却显示 "Think Done"（用户实测）
    (m as any).streaming = metaStr.includes('generating');
    if (m.role === 'assistant' && (!m.messageType || m.messageType === 'text') && metaStr.includes('turnId')) {
      m.content = markdownToHtml(String(m.content ?? ''));
      m.messageType = 'md-card'; // 专属分支：全宽卡片；'html' 留给 legacy generateNodeHtml 行原样渲染
    }
  }
  return list;
}

function formatMessageContent(message: ChatMessage): string {
  if (message.messageType === 'text') {
    return message.content?.replaceAll(/\n/g, '<br>') || '';
  }
  return message.content || '';
}

async function downloadHtmlReportFromMessageByServer(content: string) {
  if (!content) {
    ElMessage.warning('没有可下载的HTML报告');
    return;
  }
  if (!currentSession.value) {
    ElMessage.warning('当前没有会话信息');
    return;
  }
  try {
    await downloadHtmlReportApi(currentSession.value.id, content);
    ElMessage.success('HTML报告下载成功');
  } catch {
    ElMessage.error('下载HTML报告失败');
  }
}

function openReportFullscreen(content: string, msgId?: number) {
  fullscreenReportContent.value = content;
  fullscreenReportFormat.value = getMessageFormat(msgId);
  showReportFullscreen.value = true;
}

function closeReportFullscreen() {
  showReportFullscreen.value = false;
  fullscreenReportContent.value = '';
}

function downloadMarkdownReportFromMessage(content: string) {
  if (!content) {
    ElMessage.warning('没有可下载的Markdown报告');
    return;
  }
  const blob = new Blob([content], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `report_${Date.now()}.md`;
  document.body.append(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  ElMessage.success('Markdown报告下载成功');
}

function generateNodeHtml(node: GraphNodeResponse[]): string {
  const content = formatNodeContent(node);
  return `
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">${node.length > 0 ? node[0]!.nodeName : '空节点'}</div>
      <div class="agent-response-content">${content}</div>
    </div>
  `;
}

function formatNodeContent(node: GraphNodeResponse[]): string {
  let content = '';
  for (let idx = 0; idx < node.length; idx++) {
    const nd = node[idx];
    if (!nd) continue;

    if (nd.textType === TextType.HTML) {
      content += nd.text;
    } else if (nd.textType === TextType.TEXT) {
      content += nd.text.replaceAll(/\n/g, '<br>');
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
      if (!resultSetDisplayConfig.showSqlResults) continue;
      try {
        const resultData: ResultData = JSON.parse(nd.text);
        const resultSetData = resultData.resultSet;
        if (resultSetData.errorMsg) {
          content += `<div class="result-set-error">错误: ${resultSetData.errorMsg}</div>`;
          continue;
        }
        if (!resultSetData.column?.length || !resultSetData.data?.length) {
          content += `<div class="result-set-empty">查询结果为空</div>`;
          continue;
        }
        if (
          !resultData.displayStyle?.type ||
          resultData.displayStyle.type === 'table'
        ) {
          content += generateResultSetTable(
            resultSetData,
            resultSetDisplayConfig.pageSize,
          );
        }
      } catch {
        content += `<div class="result-set-error">解析结果集数据失败</div>`;
      }
    } else {
      content += nd.text;
    }
  }
  return content;
}

function markdownToHtml(markdown: string): string {
  if (!markdown) return '';
  marked.setOptions({ gfm: true, breaks: true });
  const rawHtml = marked.parse(markdown) as string;
  return DOMPurify.sanitize(rawHtml);
}

function resetReportState(
  sessionState: SessionRuntimeState,
  request: GraphRequest,
) {
  sessionState.isStreaming = true;
  sessionState.nodeBlocks = [];
  sessionState.lastRequest = request;
  sessionState.htmlReportContent = '';
  sessionState.htmlReportSize = 0;
  sessionState.markdownReportContent = '';
}

// long-turn-resilience：消息区贴底跟随（用户上翻即暂停，回底自动恢复；流式内容增长也跟随）
let stickToBottom = true;
function onMessagesScroll() {
  const el = chatContainer.value;
  if (!el) return;
  stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= 40;
}

function scrollToBottom(force = true) {
  if (!force && !stickToBottom) return;
  nextTick(() => {
    requestAnimationFrame(() => {
      if (chatContainer.value) {
        chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
      }
    });
  });
}

// 流式正文/思考增长 → 贴底跟随（原实现不跟随内容增长，需手动下拉）
watch(
  () => [
    nodeBlocks.value.length,
    nodeBlocks.value[nodeBlocks.value.length - 1]?.length ?? 0,
    thinkingMap.get(currentSession.value?.id ?? '')?.text?.length ?? 0,
  ],
  () => {
    scrollToBottom(false);
  },
);


async function handleHarnessButtonClick(btn: ConfirmButton) {
  const allowed = btn.action === 'confirm';
  showHarnessConfirm.value = false;
  pendingConfirmPlanHtml.value = '';
  isStreaming.value = true;
  try {
    await confirmHarnessSignalApi(
      pendingConfirmSessionId.value,
      pendingConfirmAgentId.value,
      allowed,
    );
    // 原流继续（isStreaming 由其 onComplete 收尾）；此处不再二开流消费
    isStreaming.value = true;
      } catch (error: any) {
    ElMessage.error(`操作失败: ${error.message}`);
  }
}

async function handleHumanFeedback(
  request: GraphRequest,
  rejectedPlan: boolean,
  content: string,
) {
  content = content.trim() || 'Accept';
  showHumanFeedback.value = false;
  const newRequest: GraphRequest = { ...request };
  newRequest.rejectedPlan = rejectedPlan;
  newRequest.humanFeedbackContent = content;
  await sendGraphRequest(newRequest, rejectedPlan);
}

async function handlePresetQuestionClick(question: string) {
  if (isStreaming.value) {
    ElMessage.warning('智能体正在处理中，请稍后...');
    return;
  }

  if (!currentSession.value) {
    try {
      const newSession = await createSessionApi(agentId.value, '新会话');
      if (!newSession) {
        ElMessage.error('创建会话失败');
        return;
      }
      currentSession.value = newSession;
      ElMessage.success('新会话创建成功');
    } catch {
      ElMessage.error('创建会话失败');
      return;
    }
  }

  userInput.value = question;
  nextTick(() => sendMessage());
}

function onPresetQuestionsLoaded(payload: { hasQuestions: boolean }) {
  if (!payload.hasQuestions) {
    inputControlsCollapsed.value = true;
  }
}

async function stopStreaming() {
  if (!currentSession.value) {
    ElMessage.warning('当前没有活动的会话');
    return;
  }

  const sessionId = currentSession.value.id;
  const sessionState = getSessionState(sessionId);

  // BUG-75：轮询态（服务端在跑但本页无本地流）→ 调服务端取消，否则终止按钮点了等于没点
  if (!sessionState.closeStream && remoteRunning.value) {
    try {
      await harnessTurnCancelApi(sessionId);
      // T-04：停止后主动收掉追流订阅（服务端轮已移除，流会自然收尾，这里双保险）
      sessionState.closeJoin?.();
      sessionState.closeJoin = null;
      remoteRunning.value = false;
      sessionState.remoteRunning = false;
      currentMessages.value = applyServerRowRender(await getSessionMessagesApi(sessionId) as any[]) as any;
      ElMessage.success('已停止对话');
    } catch {
      ElMessage.error('停止对话失败');
    }
    return;
  }

  try {
    if (!sessionState.closeStream) {
      ElMessage.warning('没有正在进行的对话');
      return;
    }

    sessionState.closeStream();
    sessionState.closeStream = null;

    if (sessionState.nodeBlocks?.length > 0) {
      const saveOneNode = async (node: GraphNodeResponse[]): Promise<void> => {
        if (!node?.length) return;
        const nodeHtml = generateNodeHtml(node);
        const aiMessage: ChatMessage = {
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

      const promises = sessionState.nodeBlocks.map((block) =>
        saveOneNode(block),
      );
      await Promise.all(promises);
    }

    sessionState.isStreaming = false;
    sessionState.nodeBlocks = [];
    sessionState.htmlReportContent = '';
    sessionState.htmlReportSize = 0;
    sessionState.markdownReportContent = '';

    if (currentSession.value?.id === sessionId) {
      isStreaming.value = false;
      nodeBlocks.value = [];
    }

    await selectSession(currentSession.value);
    ElMessage.success('已停止对话');
  } catch {
    sessionState.isStreaming = false;
    sessionState.closeStream = null;
    if (currentSession.value?.id === sessionId) {
      isStreaming.value = false;
      nodeBlocks.value = [];
    }
    ElMessage.error('停止对话失败');
  }
}

function generateResultSetTable(
  resultSetData: ResultSetData,
  pageSize: number,
): string {
  const columns = resultSetData.column || [];
  const allData = resultSetData.data || [];
  const total = allData.length;
  const totalPages = Math.ceil(total / pageSize);

  let tableHtml = `<div class="result-set-container"><div class="result-set-header"><div class="result-set-info"><span>查询结果 (共 ${total} 条记录)</span><div class="result-set-pagination-controls"><span class="result-set-pagination-info">第 <span class="result-set-current-page">1</span> 页，共 ${totalPages} 页</span><div class="result-set-pagination-buttons"><button class="result-set-pagination-btn result-set-pagination-prev" onclick="handleResultSetPagination(this, 'prev')" disabled>上一页</button><button class="result-set-pagination-btn result-set-pagination-next" onclick="handleResultSetPagination(this, 'next')" ${totalPages > 1 ? '' : 'disabled'}>下一页</button></div></div></div></div><div class="result-set-table-container">`;

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

function getMarkdownContentFromNode(node: GraphNodeResponse[]): string {
  if (!node?.length) return '';
  const firstNode = node[0]!;
  if (
    isReportGeneratorNode(firstNode) &&
    firstNode.textType === 'MARK_DOWN'
  ) {
    const sessionId = currentSession.value?.id;
    if (sessionId) {
      const state = getSessionState(sessionId);
      return state.markdownReportContent || '';
    }
  }
  let markdown = '';
  for (let idx = 0; idx < node.length; idx++) {
    if (node[idx]?.textType === 'MARK_DOWN') {
      let p = idx;
      for (; p < node.length; p++) {
        if (node[p]?.textType !== 'MARK_DOWN') break;
        markdown += node[p]?.text || '';
      }
      if (p < node.length) idx = p - 1;
      else break;
    }
  }
  return markdown;
}

function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function handleNl2sqlOnlyChange(value: string | number | boolean) {
  if (value) {
    requestOptions.humanFeedback = false;
  }
}

function isReportGeneratorNode(
  node: GraphNodeResponse | undefined | null,
): boolean {
  if (!node) return false;
  return (
    node.nodeName === 'ReportGeneratorNode' || node.nodeName === '生成报表'
  );
}

function firstNode(block: GraphNodeResponse[]): GraphNodeResponse | undefined {
  return block[0];
}

onMounted(async () => {
  await loadAgent();
});

// T-03②：页面关闭/切后台兜底写一次快照——400ms 节流窗口内的最后一段不再丢（A′ 语义只增不减）
function flushActiveSnapshot() {
  const sid = currentSession.value?.id;
  if (!sid) return;
  const state = getSessionState(sid) as any;
  if (!state?.snapText) return;
  flushStreamSnapshot(sid, state.snapText, getThinkingTrack(sid).text || undefined);
}
window.addEventListener('pagehide', flushActiveSnapshot);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') flushActiveSnapshot();
});
</script>

<template>
  <Page>
    <el-container
      style="
        display: flex;
        flex-direction: row;
        gap: 0;
        height: calc(100vh - 120px);
      "
    >
      <ChatSessionSidebar
        :agent="agent"
        :current-session="currentSession"
        @select-session="selectSession"
        @delete-session-state="deleteSessionState"
      />

      <el-main
        style="
          display: flex;
          flex: 1;
          flex-direction: column;
          overflow: hidden;
        "
      >
        <div class="chat-container" ref="chatContainer" @scroll="onMessagesScroll">
          <div v-if="!currentSession" class="empty-state">
            <el-empty description="请选择一个会话或创建新会话开始对话" />
            <PresetQuestions
              v-if="agent.id"
              :agentId="agent.id"
              :onQuestionClick="handlePresetQuestionClick"
              class="empty-state-preset"
            />
          </div>
          <div v-else class="messages-area">
            <div
              v-for="message in currentMessages"
              :key="message.id"
              :class="
                message.messageType === 'text'
                  ? ['message-container', message.role]
                  : ''
              "
            >
              <div
                v-if="(message as any).metadata && (message as any).metadata.interrupted"
                class="run-interrupted-tip"
              >
                ⚠ 输出在页面刷新时中断（{{ interruptedAtText(message) }}），以下为已生成部分
              </div>
              <div
                v-if="message.messageType === 'html'"
                v-html="message.content"
              ></div>
              <!-- 服务端 markdown 行专属：全宽卡片（think 上 / 回复下，同列合并） -->
              <div v-else-if="message.messageType === 'md-card'" class="md-response">
                <ThinkingBlock
                  v-if="(message as any).thinking"
                  :content="(message as any).thinking"
                  :duration-ms="(message as any).thinkingMs"
                  :has-content="true"
                  :streaming="!!(message as any).streaming"
                />
                <div
                  v-if="String(message.content ?? '').trim()"
                  class="md-card"
                  v-html="message.content"
                ></div>
              </div>
              <div
                v-else-if="message.messageType === 'result-set'"
                class="result-set-message"
              >
                <ResultSetDisplay
                  v-if="message.content"
                  :resultData="JSON.parse(message.content)"
                  :pageSize="resultSetDisplayConfig.pageSize"
                />
              </div>
              <div
                v-else-if="message.messageType === 'markdown-report'"
                class="markdown-report-message"
              >
                <div
                  class="markdown-report-header"
                  style="
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                  "
                >
                  <div class="report-info">
                    <el-icon><Document /></el-icon>
                    <span>报告已生成</span>
                    <el-radio-group
                      :model-value="getMessageFormat(message.id)"
                      @change="(val: any) => { if (message.id != null) messageReportFormat[message.id] = val }"
                      size="small"
                      class="report-format-inline"
                    >
                      <el-radio-button value="markdown"
                        >Markdown</el-radio-button
                      >
                      <el-radio-button value="html">HTML</el-radio-button>
                    </el-radio-group>
                  </div>
                  <el-button-group size="large">
                    <el-button
                      type="primary"
                      @click="
                        downloadMarkdownReportFromMessage(message.content)
                      "
                    >
                      <el-icon><Download /></el-icon>
                      下载Markdown报告
                    </el-button>
                    <el-button
                      type="success"
                      @click="
                        downloadHtmlReportFromMessageByServer(message.content)
                      "
                    >
                      <el-icon><Download /></el-icon>
                      下载HTML报告
                    </el-button>
                    <el-tooltip content="全屏查看报告" placement="top">
                      <el-button
                        type="info"
                        @click="openReportFullscreen(message.content, message.id)"
                      >
                        <el-icon><FullScreen /></el-icon>
                        全屏
                      </el-button>
                    </el-tooltip>
                  </el-button-group>
                </div>
                <div class="markdown-report-content">
                  <markdown-agent-container
                    v-if="getMessageFormat(message.id) === 'markdown'"
                    class="md-body"
                    :content="message.content"
                    :options="options"
                  />
                  <ReportHtmlView v-else :content="message.content" />
                </div>
              </div>
              <div v-else :class="['message', message.role]">
                <div class="message-avatar">
                  <!-- BUG-110：助手消息应显示**智能体真实头像**（此前恒为首字圈）。
                       el-avatar 的 src 加载失败时会自动回落到插槽内容（首字），故无需额外兜底逻辑。 -->
                  <el-avatar
                    :size="32"
                    :src="message.role === 'user' ? undefined : (agent.avatar || undefined)"
                    style="font-size:16px;font-weight:600;color:#fff;background:#2f6bff"
                  >
                    {{ message.role === 'user' ? '我' : (agent.name?.charAt(0) || 'AI') }}
                  </el-avatar>
                </div>
                <div class="message-content">
                  <!-- T-07（R-10）：消息附件（历史回看从 metadata 还原；点击经鉴权端点下载） -->
                  <div
                    v-if="(message as any).attachments?.length"
                    style="display:flex;flex-direction:column;gap:4px;margin-bottom:6px"
                  >
                    <div
                      v-for="att in (message as any).attachments"
                      :key="att.id"
                      style="display:flex;align-items:center;gap:6px;padding:4px 8px;border:1px solid #e4e7ed;border-radius:6px;background:#fafafa;font-size:12px;cursor:pointer;max-width:420px"
                      @click="onDownloadAttachment(att)"
                    >
                      <img
                        v-if="att.kind === 'IMAGE' && cachedThumb(att.id)"
                        :src="cachedThumb(att.id)"
                        style="width:28px;height:28px;object-fit:cover;border-radius:4px"
                      />
                      <el-icon v-else><Document /></el-icon>
                      <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ att.fileName }}</span>
                      <span style="color:#909399">{{ formatAttachmentSize(att.sizeBytes) }}</span>
                      <span
                        v-if="att.notice"
                        style="color:#e6a23c"
                        :title="att.notice"
                      >⚠</span>
                    </div>
                  </div>
                  <ThinkingBlock
                    v-if="message.role === 'assistant' && (message as any).thinking"
                    :content="(message as any).thinking"
                    :duration-ms="(message as any).thinkingMs"
                    :has-content="true"
                    :streaming="!!(message as any).streaming"
                  />
                  <div
                    v-if="String(message.content ?? '').trim()"
                    class="message-text"
                    v-html="formatMessageContent(message)"
                  ></div>
                </div>
              </div>
            </div>

            <div v-if="isStreaming || nodeBlocks.length > 0" class="streaming-response">
              <ThinkingBlock
                v-if="currentSession && thinkingMap.get(currentSession.id)?.text"
                :content="thinkingMap.get(currentSession.id)!.text"
                :duration-ms="thinkingMap.get(currentSession.id)?.ms"
                :has-content="nodeBlocks.length > 0"
                :streaming="isStreaming"
              />
              <div v-if="nodeBlocks.length > 0" class="agent-response-container">
                <template v-for="(nodeBlock, index) in nodeBlocks" :key="index">
                  <div
                    v-if="
                      isReportGeneratorNode(firstNode(nodeBlock)) &&
                      firstNode(nodeBlock)?.textType === 'MARK_DOWN'
                    "
                    class="agent-response-block"
                  >
                    <div class="agent-response-title">
                      {{ firstNode(nodeBlock)?.nodeName }}
                    </div>
                    <div class="agent-response-content">
                      <markdown-agent-container
                        v-if="requestOptions.reportFormat === 'markdown'"
                        class="md-body"
                        :content="getMarkdownContentFromNode(nodeBlock)"
                        :options="options"
                      />
                      <ReportHtmlView
                        v-else
                        :content="getMarkdownContentFromNode(nodeBlock)"
                      />
                    </div>
                  </div>
                  <div
                    v-else-if="firstNode(nodeBlock)?.textType === 'RESULT_SET'"
                    class="agent-response-block"
                  >
                    <div class="agent-response-title">
                      {{ firstNode(nodeBlock)?.nodeName }}
                    </div>
                    <div class="agent-response-content">
                      <ResultSetDisplay
                        v-if="firstNode(nodeBlock)?.text"
                        :resultData="JSON.parse(firstNode(nodeBlock)!.text)"
                        :pageSize="resultSetDisplayConfig.pageSize"
                      />
                    </div>
                  </div>
                  <!-- 流式 markdown（harness）与历史同款 md-card，输出中/完成后视觉统一 -->
                  <div
                    v-else-if="firstNode(nodeBlock)?.textType === 'MARK_DOWN'"
                    class="md-card"
                    v-html="markdownToHtml(getMarkdownContentFromNode(nodeBlock))"
                  ></div>
                  <div v-else v-html="generateNodeHtml(nodeBlock)"></div>
                </template>
              </div>
              <div v-if="isStreaming" class="streaming-footer">
                <div class="streaming-indicator">
                  <span class="streaming-dot"></span>
                  <span class="streaming-dot"></span>
                  <span class="streaming-dot"></span>
                </div>
                <span v-if="streamElapsedText" class="streaming-elapsed">
                  {{ streamElapsedText }}<span v-if="currentSilenceText" class="streaming-silence"> · {{ currentSilenceText }}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <HumanFeedback
          v-if="showHumanFeedback"
          :request="lastRequest!"
          @feedback="handleHumanFeedback"
        />

        <div v-if="showHarnessConfirm && pendingConfirmButtons.length > 0" class="harness-confirm-area">
          <div class="harness-confirm-header">
            <el-icon><WarningFilled /></el-icon>
            <span>请确认操作</span>
          </div>
          <div
            v-if="pendingConfirmPlanHtml"
            class="harness-confirm-plan"
            v-html="pendingConfirmPlanHtml"
          ></div>
          <div class="harness-confirm-actions">
            <el-button
              v-for="(btn, idx) in pendingConfirmButtons"
              :key="idx"
              :type="(btn.type as any) || 'primary'"
              @click="handleHarnessButtonClick(btn)"
            >
              {{ btn.text }}
            </el-button>
          </div>
        </div>

        <div class="input-area" v-if="currentSession">
          <div class="input-controls">
            <div
              class="input-controls-header"
              @click="inputControlsCollapsed = !inputControlsCollapsed"
            >
              <span class="input-controls-title">更多选项</span>
              <span class="input-controls-actions">
                <!-- BL-19：@click.stop——header 整行有折叠 click，冒泡会误触发"展开" -->
                <el-button
                  size="small"
                  plain
                  type="primary"
                  @click.stop="filesPanelRef?.open()"
                >
                  <el-icon style="margin-right: 4px"><FolderOpened /></el-icon>
                  文件
                </el-button>
                <el-button
                  type="primary"
                  size="small"
                  class="input-controls-toggle-btn"
                  :class="{ collapsed: inputControlsCollapsed }"
                >
                  <el-icon class="input-controls-toggle-icon"
                    ><ArrowDown
                  /></el-icon>
                  {{ inputControlsCollapsed ? '展开' : '收起' }}
                </el-button>
              </span>
            </div>
            <div v-show="!inputControlsCollapsed" class="input-controls-body">
              <PresetQuestions
                v-if="currentSession && agent.id"
                :agentId="agent.id"
                :onQuestionClick="handlePresetQuestionClick"
                @loaded="onPresetQuestionsLoaded"
              />
              <div v-if="agent.type == 'sql'" class="switch-group">
                <div class="switch-item">
                  <span class="switch-label">人工反馈</span>
                  <el-tooltip
                    :disabled="!requestOptions.nl2sqlOnly"
                    content="该功能在NL2SQL模式下不能使用"
                    placement="top"
                  >
                    <el-switch
                      v-model="requestOptions.humanFeedback"
                      :disabled="
                        requestOptions.nl2sqlOnly ||
                        isStreaming ||
                        showHumanFeedback
                      "
                    />
                  </el-tooltip>
                </div>
                <div class="switch-item">
                  <span class="switch-label">仅NL2SQL</span>
                  <el-switch
                    v-model="requestOptions.nl2sqlOnly"
                    :disabled="isStreaming || showHumanFeedback"
                    @change="handleNl2sqlOnlyChange"
                  />
                </div>
                <div class="switch-item">
                  <span class="switch-label">自动Scroll</span>
                  <el-switch v-model="autoScroll" />
                </div>
                <div class="switch-item">
                  <span class="switch-label">显示SQL结果</span>
                  <el-tooltip
                    content="启用本功能会将SQL查询结果存储到DataAgent项目的数据库中，如果数据量较大不建议开启本功能"
                    placement="top"
                  >
                    <el-switch
                      v-model="resultSetDisplayConfig.showSqlResults"
                      :disabled="isStreaming || showHumanFeedback"
                    />
                  </el-tooltip>
                </div>
                <div class="switch-item">
                  <span class="switch-label">每页数量</span>
                  <el-select
                    v-model="resultSetDisplayConfig.pageSize"
                    :disabled="isStreaming || showHumanFeedback"
                    style="width: 80px"
                  >
                    <el-option label="5" :value="5" />
                    <el-option label="10" :value="10" />
                    <el-option label="20" :value="20" />
                    <el-option label="50" :value="50" />
                    <el-option label="100" :value="100" />
                  </el-select>
                </div>
              </div>
            </div>
          </div>
          <!-- T-07：待发送附件草稿（按会话分片，L-20） -->
          <div
            v-if="currentPendingAttachments().length"
            style="display:flex;flex-wrap:wrap;gap:6px;padding:6px 8px 0"
          >
            <div
              v-for="att in currentPendingAttachments()"
              :key="att.id"
              style="display:flex;align-items:center;gap:6px;padding:3px 8px;border:1px solid #dcdfe6;border-radius:14px;background:#f7f8fa;font-size:12px"
            >
              <img
                v-if="att.kind === 'IMAGE' && cachedThumb(att.id)"
                :src="cachedThumb(att.id)"
                style="width:20px;height:20px;object-fit:cover;border-radius:4px"
              />
              <span style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ att.fileName }}</span>
              <span style="color:#909399">{{ formatAttachmentSize(att.sizeBytes) }}</span>
              <el-icon
                style="cursor:pointer;color:#909399"
                @click="removePendingAttachment(att.id)"
              ><Close /></el-icon>
            </div>
          </div>
          <div class="input-container">
            <!-- T-07：附件选择（白名单与上限的即时反馈在前端，最终裁决在后端 R-01~R-04） -->
            <input
              ref="attachmentInputRef"
              type="file"
              multiple
              style="display:none"
              accept=".doc,.docx,.pdf,.xls,.xlsx,.txt,.md,.png,.jpg,.jpeg,.gif,.webp,.bmp"
              @change="onAttachmentPicked"
            />
            <el-button
              circle
              title="上传附件（文档 word/pdf/excel/txt/md，图片 png/jpg/gif/webp/bmp；单个≤20MB，单次≤5个）"
              :loading="uploadingAttachments"
              :disabled="isStreaming || remoteRunning || showHarnessConfirm"
              style="margin-right:6px"
              @click="pickAttachmentFiles"
            >
              <el-icon><Paperclip /></el-icon>
            </el-button>
            <el-input
              v-model="userInput"
              type="textarea"
              :rows="3"
              placeholder="请输入您的问题..."
              :disabled="isStreaming || remoteRunning || showHarnessConfirm"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <el-button
              v-if="!isStreaming && !remoteRunning && !showHarnessConfirm"
              type="primary"
              @click="sendMessage"
              circle
              class="send-button"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
            <el-button
              v-if="isStreaming || remoteRunning"
              type="danger"
              @click="stopStreaming"
              circle
              class="send-button stop-button-inline"
            >
              <el-icon><CircleClose /></el-icon>
            </el-button>
          </div>
        </div>
      </el-main>
    </el-container>

    <Teleport to="body">
      <div
        v-if="showReportFullscreen"
        class="report-fullscreen-overlay"
        @click.self="closeReportFullscreen"
      >
        <div class="report-fullscreen-container">
          <div class="report-fullscreen-header">
            <span class="report-fullscreen-title">
              {{
                fullscreenReportFormat === 'markdown'
                  ? 'Markdown 报告'
                  : 'HTML 报告'
              }}
            </span>
            <el-button
              type="danger"
              circle
              class="report-fullscreen-close"
              @click="closeReportFullscreen"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="report-fullscreen-content">
            <markdown-agent-container
              v-if="fullscreenReportFormat === 'markdown'"
              class="md-body report-fullscreen-body"
              :content="fullscreenReportContent"
              :options="options"
            />
            <ReportHtmlView
              v-else
              :content="fullscreenReportContent"
              class="report-fullscreen-body"
            />
          </div>
        </div>
    </div>
    </Teleport>
    <!-- BL-19：抽屉挂 Page 根级（此前误落全屏报告 Teleport 容器内导致正常态不挂载） -->
    <ChatFilesPanel ref="filesPanelRef" :session-id="currentSession?.id ?? null" />
  </Page>
</template>

<style scoped>
.chat-container {
  flex: 1;
  padding: 20px;
  margin-bottom: 0;
  overflow-y: auto;
  background: #f8f9fa;
  margin-left: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 24px;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.empty-state-preset {
  width: 100%;
  max-width: 800px;
}

.messages-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-container {
  display: flex;
  max-width: 100%;
}

.message-container.user {
  justify-content: flex-end;
}

.message-container.assistant {
  justify-content: flex-start;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.message.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message.assistant {
  align-self: flex-start;
}

.markdown-report {
  line-height: 1.6;
  color: #1f2933;
}

.markdown-report pre {
  padding: 10px 12px;
  overflow: auto;
  background: #f6f8fa;
  border-radius: 6px;
}

.message-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.message-content :deep(.thinking) {
  margin-bottom: 0;
}

.md-response {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.md-response :deep(.thinking) {
  margin-bottom: 0;
}

.md-card {
  width: 100%;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.65;
  color: #303133;
  word-break: break-word;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
}

/* 前台聊天页同源排版（GitHub 风），保证两端观感一致 */
.md-card :deep(p) { margin: 0 0 8px; }
.md-card :deep(p:last-child) { margin-bottom: 0; }
.md-card :deep(h1), .md-card :deep(h2), .md-card :deep(h3), .md-card :deep(h4) { margin: 16px 0 8px; font-weight: 600; line-height: 1.3; }
.md-card :deep(h1:first-child), .md-card :deep(h2:first-child), .md-card :deep(h3:first-child) { margin-top: 0; }
.md-card :deep(h1) { font-size: 18px; }
.md-card :deep(h2) { font-size: 16px; padding-bottom: 4px; border-bottom: 1px solid #f0f2f5; }
.md-card :deep(h3) { font-size: 15px; }
.md-card :deep(h4) { font-size: 14px; }
.md-card :deep(ul), .md-card :deep(ol) { padding-left: 20px; margin: 8px 0; }
.md-card :deep(li) { margin: 4px 0; }
.md-card :deep(pre) { padding: 12px; margin: 8px 0; overflow-x: auto; background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; }
.md-card :deep(code) { font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace; font-size: 13px; line-height: 1.45; }
.md-card :deep(pre code) { padding: 0; background: transparent; border: none; }
.md-card :deep(code:not(pre code)) { padding: 2px 6px; color: #476582; background: #f0f4f8; border-radius: 4px; }
.md-card :deep(blockquote) { padding: 4px 12px; margin: 8px 0; color: #606266; border-left: 4px solid #409eff; }
.md-card :deep(table) { width: 100%; margin: 8px 0; font-size: 13px; border-collapse: collapse; }
.md-card :deep(th), .md-card :deep(td) { padding: 6px 10px; text-align: left; border: 1px solid #e0e0e0; }
.md-card :deep(th) { font-weight: 600; background: #f5f7fa; }
.md-card :deep(tr:nth-child(even)) { background: #fafafa; }
.md-card :deep(img) { max-width: 100%; border-radius: 6px; }
.md-card :deep(a) { color: #409eff; text-decoration: none; }
.md-card :deep(hr) { margin: 12px 0; border: none; border-top: 1px solid #eceef2; }

.message-text {
  padding: 12px 16px;
  line-height: 1.5;
  overflow-wrap: break-word;
  border-radius: 12px;
}

.message.user .message-text {
  color: white;
  background: #409eff;
}

.message.assistant .message-text {
  color: #303133;
  background: white;
  border: 1px solid #e8e8e8;
}

.streaming-response {
  padding: 16px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.streaming-header {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.streaming-header span {
  font-weight: 500;
  color: #409eff;
}

.stop-button-inline {
  width: 48px;
  height: 48px;
}

.streaming-indicator {
  display: flex;
  gap: 6px;
  align-items: center;
}

.streaming-elapsed {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.streaming-footer {
  padding-top: 12px;
}

.streaming-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: bounce 1.4s ease-in-out infinite both;
}

.streaming-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.streaming-dot:nth-child(2) {
  animation-delay: -0.16s;
}

.streaming-dot:nth-child(3) {
  animation-delay: 0s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.html-report-message {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: #f8fbff;
  border: 1px solid #e1f0ff;
  border-radius: 12px;
}

.markdown-report-message {
  padding: 16px;
  margin-bottom: 16px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
}

.markdown-report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.markdown-report-content {
  margin-top: 16px;
}

.report-info {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
  color: #409eff;
}

.report-format-inline {
  margin-left: 8px;
}

.report-fullscreen-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 70%);
}

.report-fullscreen-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1200px;
  height: 90vh;
  overflow: hidden;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgb(0 0 0 / 30%);
}

.report-fullscreen-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #f8f9fa;
  border-bottom: 1px solid #e8e8e8;
}

.report-fullscreen-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.report-fullscreen-close {
  flex-shrink: 0;
}

.report-fullscreen-content {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

.report-fullscreen-body {
  min-height: 100%;
}

.input-area {
  flex-shrink: 0;
  padding: 2px 16px 16px 16px;
  background: white;
  border: 1px solid #e8e8e8;
  margin-left: 16px;
}

.input-controls {
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.input-controls-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.input-controls-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  user-select: none;
}

.input-controls-header:hover {
  color: #409eff;
}

.input-controls-title {
  font-weight: 500;
}

.input-controls-toggle-btn {
  flex-shrink: 0;
}

.input-controls-toggle-btn .input-controls-toggle-icon {
  margin-right: 4px;
  transition: transform 0.2s ease;
}

.input-controls-toggle-btn.collapsed .input-controls-toggle-icon {
  transform: rotate(-90deg);
}

.input-controls-body {
  padding-bottom: 12px;
}

.switch-group {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: center;
}

.switch-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.switch-label {
  font-size: 14px;
  color: #606266;
}

.send-button {
  width: 48px;
  height: 48px;
}

.input-container {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

@media (max-width: 768px) {
  .el-aside {
    width: 250px !important;
  }

  .message {
    max-width: 90%;
  }

  .input-container {
    flex-direction: column;
  }
}
</style>

<style>
.agent-response-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agent-response-block {
  overflow: hidden;
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.agent-response-block:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgb(64 158 255 / 10%);
}

.agent-response-title {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #409eff;
  background: #ecf5ff;
  border-bottom: 1px solid #e8e8e8;
}

.agent-response-content {
  min-height: 40px;
  padding: 16px;
  font-family: Monaco, Menlo, 'Ubuntu Mono', monospace;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.agent-response-content .markdown-container {
  font-family: inherit;
  line-height: 1.4;
  white-space: normal;
}

.agent-response-content pre {
  padding: 0;
  margin: 0;
  background: transparent;
  border: none;
}

.agent-response-content code {
  padding: 0;
  font-family: Monaco, Menlo, 'Ubuntu Mono', monospace;
  background: transparent;
}

.node-content pre {
  margin: 0;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.agent-response-content pre.hljs {
  padding: 16px;
  margin: 8px 0;
  overflow-x: auto;
  background: #f6f8fa !important;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
}

.agent-response-content code.hljs {
  padding: 0;
  font-size: 13px;
  line-height: 1.45;
  background: transparent !important;
}

.agent-response-content .hljs {
  display: block;
  padding: 16px;
  overflow-x: auto;
  color: #24292e;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
}

.markdown-report {
  line-height: 1.6;
  color: #1f2933;
}

.markdown-report pre {
  padding: 10px 12px;
  overflow: auto;
  background: #f6f8fa;
  border-radius: 6px;
}

.result-set-container {
  margin: 8px 0;
  overflow: hidden;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.result-set-header {
  padding: 12px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #e8e8e8;
}

.result-set-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: #606266;
}

.result-set-pagination-controls {
  display: flex;
  gap: 16px;
  align-items: center;
}

.result-set-pagination-info {
  font-size: 14px;
  color: #606266;
}

.result-set-pagination-buttons {
  display: flex;
  gap: 8px;
}

.result-set-pagination-btn {
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  transition: all 0.3s;
}

.result-set-pagination-btn:hover:not(:disabled) {
  background: #f5f7fa;
  border-color: #c6e2ff;
}

.result-set-pagination-btn:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background: #f5f7fa;
}

.result-set-table-container {
  position: relative;
  overflow-x: auto;
}

.result-set-page {
  display: none;
}

.result-set-page-active {
  display: block;
}

.result-set-table {
  width: 100%;
  font-size: 13px;
  border-collapse: collapse;
}

.result-set-table th {
  padding: 8px 12px;
  font-weight: 600;
  color: #606266;
  text-align: left;
  white-space: nowrap;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

.result-set-table td {
  max-width: 200px;
  padding: 8px 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  border-bottom: 1px solid #f0f0f0;
}

.result-set-table tr:hover {
  background: #f5f7fa;
}

.result-set-empty-cell {
  padding: 20px;
  color: #909399;
  text-align: center;
}

.result-set-error {
  padding: 8px 12px;
  margin: 8px 0;
  color: #f56c6c;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
}

.result-set-empty {
  padding: 8px 12px;
  margin: 8px 0;
  color: #909399;
  text-align: center;
  background: #f4f4f5;
  border-radius: 4px;
}

.harness-confirm-area {
  padding: 20px;
  margin: 16px 0;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 12px;
}

.harness-confirm-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 500;
  color: #d48806;
}

.harness-confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.result-set-message {
  width: 100%;
}

@media (max-width: 768px) {
  .result-set-table-container {
    font-size: 12px;
  }

  .result-set-table th,
  .result-set-table td {
    padding: 6px 8px;
  }
}
.run-interrupted-tip {
  margin: 4px 0 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: #b8860b;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}
.harness-confirm-plan {
  max-height: 220px;
  margin: 4px 0 8px;
  padding: 6px 10px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.6;
  color: #5c6470;
  background: #fff;
  border-radius: 6px;
}
</style>
