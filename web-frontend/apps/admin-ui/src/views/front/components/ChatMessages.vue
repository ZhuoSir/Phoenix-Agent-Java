<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAgentStore, useChatStore } from '@phoenix/chat-shared';
import { ElMessage } from 'element-plus';
import ReportMessage from './report/ReportMessage.vue';
import ThinkingBlock from './ThinkingBlock.vue';
import type { ResultData } from '#/api/core/resultSet';
import ResultSetDisplay from '#/components/run/ResultSetDisplay.vue';
import { confirmFrontHarnessSignal } from '#/api/front/chat';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const chat = useChatStore();
const agentStore = useAgentStore();
const { activeMessages, activeSession, activeSessionId, isActiveSessionSending, loadingMessages, silenceByS } =
  storeToRefs(chat);
const { agents } = storeToRefs(agentStore);

/** T-10：服务端静默心跳提示（会话级，如「工具执行中 · 已静默 23s」） */
const silenceHint = computed(() => silenceByS.value[activeSessionId.value ?? ''] ?? '');

const currentAgent = computed(() => {
  if (!activeSession.value) return null;
  return (
    agents.value.find((a) => a.id === activeSession.value?.agentId) ?? null
  );
});
const botName = computed(() => currentAgent.value?.name ?? 'AI');

// long-turn-resilience T-03/R-02：长轮活性指示——流式中每秒刷新已用时，证明"在跑"而非卡死
const streamingElapsedText = ref('');
let elapsedStart = 0;
let elapsedTimer: ReturnType<typeof setInterval> | null = null;
watch(
  () => activeMessages.value.some((m: any) => m.streaming),
  (on: boolean) => {
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
      elapsedTimer = null;
    }
    if (!on) {
      streamingElapsedText.value = '';
      return;
    }
    elapsedStart = Date.now();
    elapsedTimer = setInterval(() => {
      const sec = Math.floor((Date.now() - elapsedStart) / 1000);
      const mm = String(Math.floor(sec / 60)).padStart(2, '0');
      const ss = String(sec % 60).padStart(2, '0');
      streamingElapsedText.value = `正在执行（已用时 ${mm}:${ss}）`;
    }, 1000);
  },
  { immediate: true },
);

function renderMessage(msg: Record<string, any>): string {
  const content = String(msg.content ?? '');
  if (msg.role === 'user') {
    return escapeHtml(content).replaceAll('\n', '<br>');
  }
  return content;
  // return content.replaceAll('\n', '<br>');
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

const scrollRef = ref<HTMLElement | null>(null);

// long-turn-resilience T-03 附加：消息区贴底跟随（流式内容增长也跟随；用户上翻则暂停，滚回底部恢复）
let stickToBottom = true;
function onMessagesScroll() {
  const el = scrollRef.value;
  if (!el) return;
  stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= 40;
}

async function scrollToBottom(force = true) {
  await nextTick();
  const el = scrollRef.value;
  if (!el) return;
  if (!force && !stickToBottom) return;
  el.scrollTop = el.scrollHeight;
}

watch(activeMessages, () => {
  stickToBottom = true;
  void scrollToBottom();
});

watch(activeSessionId, () => {
  stickToBottom = true;
  void scrollToBottom();
});

// 流式正文/思考增长 → 贴底跟随（原实现只在消息数组变化时滚动，内容增长不跟随=需手动下拉）
watch(
  () => {
    const last = activeMessages.value[activeMessages.value.length - 1] as any;
    return last ? `${String(last.content ?? '').length}:${String(last.thinking ?? '').length}` : '';
  },
  () => {
    void scrollToBottom(false);
  },
);

let msgCounter = 0;
function uid(): string {
  msgCounter++;
  return `m-${Date.now().toString(36)}-${msgCounter}`;
}

const confirming = ref(false);

async function handleConfirmAction(
  msg: any,
  btn: { text: string; action: string; type?: string },
) {
  if (confirming.value) return;
  confirming.value = true;
  const metadata = msg.metadata || {};
  const { sessionId: confirmSessionId, agentSn, agentId } = metadata;
  if (!confirmSessionId || (!agentSn && !agentId)) {
    confirming.value = false;
    return;
  }

  // BL-22 架构修正：确认/取消只发放行信号——原 send 流在等待期保持打开并续播，
  // 不再二开消费流（历史上 confirm 响应含确认前全文导致需要删占位重建，现无此必要）
  const allowed = btn.action === 'confirm';
  // 修复"弹窗不下去"：点击即整卡移除（原流占位气泡继续承接后续内容）
  const remain = (chat.messagesByS[confirmSessionId] ?? []).filter((m: any) => m.id !== msg.id);
  chat.messagesByS = { ...chat.messagesByS, [confirmSessionId]: [...remain] };
  try {
    await confirmFrontHarnessSignal({ sessionId: confirmSessionId, agentSn, agentId, allowed });
  } catch (error: any) {
    ElMessage.error(`操作失败: ${error?.message ?? error}`);
  } finally {
    confirming.value = false;
  }
}
</script>

<template>
  <div ref="scrollRef" class="chat-messages" @scroll="onMessagesScroll">
    <div class="chat-messages__inner">
      <div
        v-for="(msg, index) in activeMessages"
        :key="msg.id"
        :class="['chat-message', msg.role]"
      >
        <div
          v-if="msg.role === 'assistant'"
          class="chat-message__avatar"
          :class="{
            'chat-message__avatar--hidden':
              index > 0 && activeMessages[index - 1].role === 'assistant',
          }"
        >
          {{ botName.charAt(0) }}
        </div>

        <div class="chat-message__content">
          <div
            v-if="(msg as any).metadata && (msg as any).metadata.interrupted"
            class="chat-message__interrupted"
          >
            ⚠ 输出在页面刷新时中断，以下为已生成部分
          </div>
          <!-- thinking-display T-02：思考区（正文首字到达/完成后自动折叠，点击回看） -->
          <ThinkingBlock
            v-if="msg.role === 'assistant' && (msg as any).thinking"
            :content="(msg as any).thinking"
            :streaming="!!msg.streaming"
            :has-content="!!msg.content"
            :duration-ms="(msg as any).thinkingMs"
          />
          <div
            v-if="(msg as any).messageType === 'html'"
            class="chat-message__html"
            v-show="String(msg.content ?? '').trim()"
            v-html="msg.content"
          ></div>
          <div
            v-else-if="(msg as any).messageType === 'markdown-report'"
            class="chat-message__report"
          >
            <ReportMessage :content="msg.content" :sessionId="activeSession?.id" />
          </div>
          <div
            v-else-if="(msg as any).messageType === 'result-set'"
            class="chat-message__result-set"
          >
            <ResultSetDisplay
              v-if="msg.content"
              :resultData="JSON.parse(msg.content) as ResultData"
              :pageSize="20"
            />
          </div>
          <div
            v-else-if="(msg as any).messageType === 'harness-confirm'"
            class="chat-message__confirm"
          >
            <div class="chat-message__confirm-head">
              <span class="chat-message__confirm-dot"></span>
              需要你的确认后才会继续执行
            </div>
            <div
              v-if="msg.content"
              class="chat-message__confirm-text"
              v-html="renderMessage(msg)"
            ></div>
            <div class="chat-message__confirm-buttons">
              <button
                v-for="(btn, bidx) in (msg as any).metadata?.buttons || []"
                :key="bidx"
                :class="[
                  'chat-message__confirm-btn',
                  btn.type === 'danger' ? 'chat-message__confirm-btn--danger' : 'chat-message__confirm-btn--primary',
                ]"
                :disabled="confirming"
                @click="handleConfirmAction(msg, btn)"
              >
                {{ btn.text }}
              </button>
            </div>
          </div>
          <div
            v-else
            class="chat-message__text"
            :class="{
              'chat-message__text--markdown': msg.role === 'assistant',
              'chat-message__text--streaming': msg.streaming
            }"
            v-show="String(msg.content ?? '').trim()"
            v-html="renderMessage(msg)"
          ></div>
          <div v-if="msg.streaming && streamingElapsedText" class="chat-message__elapsed">
            {{ streamingElapsedText }}<span v-if="silenceHint" class="chat-message__silence"> · {{ silenceHint }}</span>
          </div>
        </div>

        <div
          v-if="msg.role === 'user'"
          class="chat-message__avatar chat-message__avatar--user"
        >
          我
        </div>
      </div>

      <div
        v-if="isActiveSessionSending && !activeMessages.some((m) => m.streaming)"
        class="chat-message assistant chat-message--typing"
      >
        <div class="chat-message__avatar">
          {{ botName.charAt(0) }}
        </div>
        <div class="chat-message__content">
          <div class="chat-message__bubble chat-message__bubble--typing">
            <span class="dot" />
            <span class="dot" />
            <span class="dot" />
          </div>
        </div>
      </div>

      <div
        v-if="loadingMessages && activeMessages.length === 0"
        class="chat-messages__loading"
      >
        <span class="chat-messages__loading-spinner"></span>
        <span>加载中...</span>
      </div>

      <div
        v-else-if="activeMessages.length === 0 && !isActiveSessionSending"
        class="chat-messages__empty"
      >
        当前会话还没有消息，发送一条试试
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-message__silence {
  color: #e6a23c;
}

.chat-messages {
  height: 100%;
  overflow-y: auto;
  background: hsl(var(--background));

  &__inner {
    display: flex;
    flex-direction: column;
    gap: 18px;
    max-width: var(--pc-chat-content-width);
    padding: 24px 0 16px;
    margin: 0 auto;
  }
}

.chat-message {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  &.user {
    justify-content: flex-end;
  }

  &__avatar {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    font-size: 13px;
    font-weight: 600;
    color: hsl(var(--primary-foreground));
    background: hsl(var(--primary));
    border-radius: 50%;

    &--user {
      color: hsl(var(--primary-foreground));
      background: hsl(var(--primary));
    }
  }

  &.assistant &__avatar {
    color: hsl(var(--primary));
    background: hsl(var(--primary) / 12%);
  }

  &.assistant &__content {
    width: 91.5%;
  }

  &__avatar--hidden {
    visibility: hidden;
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
    max-width: 91.5%;
  }

  &__text {
    padding: 10px 14px;
    font-size: 14px;
    line-height: 1.65;
    color: hsl(var(--foreground));
    word-break: break-word;
    background: hsl(var(--card));
    border-radius: 10px;

    :deep(p) {
      margin: 0;
    }
  }

  &.user &__text {
    color: hsl(var(--primary-foreground));
    background: hsl(var(--primary));
    border: none;
  }

  &__elapsed {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__text--streaming {
    &::after {
      display: inline-block;
      width: 1.2em;
      font-size: 1.3em;
      text-align: left;
      content: '.';
      animation: pc-dots 1.5s steps(3, end) infinite;
    }
  }

  &__text--markdown {
    padding: 10px 14px;
    line-height: 1.65;
    word-break: break-word;

    :deep(p) {
      margin: 0 0 8px;
    }

    :deep(p:last-child) {
      margin-bottom: 0;
    }

    :deep(pre) {
      padding: 12px;
      margin: 8px 0;
      overflow-x: auto;
      background: #f6f8fa;
      border: 1px solid #e1e4e8;
      border-radius: 6px;
    }

    :deep(code) {
      font-family:
        SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
      font-size: 13px;
      line-height: 1.45;
    }

    :deep(pre code) {
      padding: 0;
      background: transparent;
      border: none;
    }

    :deep(code:not(pre code)) {
      padding: 2px 6px;
      color: #476582;
      background: #f0f4f8;
      border-radius: 4px;
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
      margin: 8px 0;
    }

    :deep(li) {
      margin: 4px 0;
    }

    :deep(blockquote) {
      padding: 4px 12px;
      margin: 8px 0;
      color: #606266;
      border-left: 4px solid #409eff;
    }

    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 16px 0 8px;
      line-height: 1.3;
    }

    :deep(table) {
      width: 100%;
      margin: 8px 0;
      font-size: 13px;
      border-collapse: collapse;
    }

    :deep(th),
    :deep(td) {
      padding: 6px 10px;
      text-align: left;
      border: 1px solid #e0e0e0;
    }

    :deep(th) {
      font-weight: 600;
      background: #f5f7fa;
    }

    :deep(tr:nth-child(even)) {
      background: #fafafa;
    }

    :deep(img) {
      max-width: 100%;
      border-radius: 6px;
    }

    :deep(a) {
      color: #409eff;
      text-decoration: none;
    }

    :deep(a:hover) {
      text-decoration: underline;
    }

    :deep(hr) {
      margin: 16px 0;
      border: none;
      border-top: 1px solid #e0e0e0;
    }
  }
}

.result-set-table-wrap {
  margin: 8px 0;
  overflow-x: auto;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.result-set-table {
  width: 100%;
  font-size: 13px;
  border-collapse: collapse;

  th {
    padding: 8px 12px;
    font-weight: 600;
    color: #606266;
    text-align: left;
    white-space: nowrap;
    background: #f5f7fa;
    border-bottom: 1px solid #e8e8e8;
  }

  td {
    max-width: 200px;
    padding: 8px 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    word-break: break-word;
    border-bottom: 1px solid #f0f0f0;
  }

  tr:hover {
    background: #f5f7fa;
  }
}

.chat-messages__empty {
  padding: 32px 0;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  text-align: center;
}

.chat-messages__loading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  font-size: 13px;
  color: hsl(var(--muted-foreground));

  &-spinner {
    width: 18px;
    height: 18px;
    border: 2px solid hsl(var(--border));
    border-top-color: hsl(var(--primary));
    border-radius: 50%;
    animation: messages-loading-spin 0.6s linear infinite;
  }
}

@keyframes messages-loading-spin {
  to { transform: rotate(360deg); }
}

.chat-message__bubble--typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 12px 14px;

  .dot {
    width: 6px;
    height: 6px;
    background: hsl(var(--muted-foreground));
    border-radius: 50%;
    animation: pc-typing 1s infinite ease-in-out;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes pc-typing {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

.chat-message__confirm {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
}

.chat-message__confirm-head {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: #8a919f;
}

.chat-message__confirm-dot {
  width: 7px;
  height: 7px;
  background: #e6a23c;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(230 162 60 / 15%);
  animation: confirm-pulse 1.6s ease-in-out infinite;
}

@keyframes confirm-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.chat-message__confirm-text {
  max-height: 260px;
  padding-top: 10px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.7;
  color: #4b5563;
  border-top: 1px solid #f0f2f5;
}

.chat-message__confirm-text :deep(p) { margin: 0 0 6px; }
.chat-message__confirm-text :deep(p:last-child) { margin-bottom: 0; }
.chat-message__confirm-text :deep(ul), .chat-message__confirm-text :deep(ol) { padding-left: 18px; margin: 6px 0; }
.chat-message__confirm-text :deep(li) { margin: 2px 0; }
.chat-message__confirm-text :deep(h1), .chat-message__confirm-text :deep(h2), .chat-message__confirm-text :deep(h3) { margin: 10px 0 6px; font-size: 14px; font-weight: 600; }
.chat-message__confirm-text :deep(code) { padding: 1px 5px; font-family: SFMono-Regular, Consolas, Menlo, monospace; font-size: 12px; color: #476582; background: #f0f4f8; border-radius: 4px; }
.chat-message__confirm-text :deep(pre) { padding: 10px 12px; margin: 6px 0; overflow-x: auto; background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; }
.chat-message__confirm-text :deep(pre code) { padding: 0; color: inherit; background: none; }
.chat-message__confirm-text :deep(table) { width: 100%; margin: 6px 0; font-size: 12px; border-collapse: collapse; }
.chat-message__confirm-text :deep(th), .chat-message__confirm-text :deep(td) { padding: 4px 8px; text-align: left; border: 1px solid #e0e0e0; }
.chat-message__confirm-text :deep(th) { font-weight: 600; background: #f5f7fa; }

.chat-message__confirm-buttons {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.chat-message__confirm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 18px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.15s ease;
}

.chat-message__confirm-btn:hover {
  opacity: 0.88;
}

.chat-message__confirm-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.chat-message__confirm-btn--primary {
  color: #fff;
  background: hsl(var(--primary));
  border: 1px solid transparent;
  box-shadow: 0 1px 3px hsl(var(--primary) / 30%);
}

.chat-message__confirm-btn--danger {
  color: #5c6470;
  background: #fff;
  border: 1px solid #dcdfe6;
}

.chat-message__confirm-btn--danger:hover {
  color: #409eff;
  background: #f7f9ff;
  border-color: #c6d4f7;
  opacity: 1;
}

@keyframes pc-dots {
  0% {
    content: '.';
  }

  33% {
    content: '..';
  }

  66% {
    content: '...';
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
  margin-bottom: 10px;
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

.markdown-report p {
  margin: 0 0 8px;
}

.markdown-report p:last-child {
  margin-bottom: 0;
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

.chat-message__interrupted {
  margin-bottom: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: #b8860b;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}
</style>