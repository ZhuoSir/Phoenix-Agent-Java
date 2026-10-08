<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useChatStore } from '@phoenix/chat-shared';
import type { ChatAttachmentMeta } from '@phoenix/chat-shared';
import {
  ATTACHMENT_MAX_FILES_PER_SEND,
  attachmentCountMessage,
  validateAttachmentsLocally,
} from '@phoenix/chat-shared';
import { showToast } from 'vant';
import { fetchThumbUrl, uploadAttachments } from '../../services/attachment';
import { setPendingAttachments } from '../../services/chatTransport';

interface Props {
  disabled?: boolean;
  streaming?: boolean;
}
const props = withDefaults(defineProps<Props>(), { disabled: false, streaming: false });

const emit = defineEmits<{
  (e: 'submit', content: string): void;
  (e: 'stop'): void;
}>();

const value = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);

// ===== T-08：对话附件（chat-attachment-understanding）=====
const chat = useChatStore();
const { activeSessionId } = storeToRefs(chat);
/** 待发送附件草稿：**按会话分片**（L-20：切会话不串附件） */
const drafts = ref<Map<string, ChatAttachmentMeta[]>>(new Map());
const inputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const thumbs = ref<Map<number, string>>(new Map());

const currentDraft = computed<ChatAttachmentMeta[]>(() => {
  const sid = activeSessionId.value;
  return (sid && drafts.value.get(sid)) || [];
});

function pickFiles() { inputRef.value?.click(); }

function takeDraft(): ChatAttachmentMeta[] {
  const list = currentDraft.value;
  const sid = activeSessionId.value;
  if (sid && list.length) {
    const next = new Map(drafts.value);
    next.set(sid, []);
    drafts.value = next;
  }
  return list;
}

function removeAttachment(id: number) {
  const sid = activeSessionId.value;
  if (!sid) return;
  const next = new Map(drafts.value);
  next.set(sid, (next.get(sid) || []).filter((a) => a.id !== id));
  drafts.value = next;
}

async function onPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = [...(input.files || [])];
  input.value = '';
  if (!files.length) return;
  const sid = activeSessionId.value;
  if (!sid) { showToast('请先选择或创建会话'); return; }
  const existed = drafts.value.get(sid) || [];
  if (existed.length + files.length > ATTACHMENT_MAX_FILES_PER_SEND) {
    showToast(attachmentCountMessage(existed.length + files.length));
    return;
  }
  // 规则与文案来自 chat-shared（两端单一来源，R-12）；后端仍做双判定最终裁决
  const checked = validateAttachmentsLocally(files);
  for (const c of checked.filter((x) => x.reason)) showToast(`${c.file.name}：${c.reason}`);
  const okFiles = checked.filter((x) => !x.reason).map((x) => x.file);
  if (!okFiles.length) return;
  uploading.value = true;
  try {
    const res = await uploadAttachments(okFiles, sid);
    for (const r of res.rejected) showToast(`${r.fileName}：${r.reason}`);
    if (res.accepted.length) {
      const next = new Map(drafts.value);
      next.set(sid, [...existed, ...res.accepted]);
      drafts.value = next;
      for (const a of res.accepted) {
        if (a.kind === 'IMAGE' && !thumbs.value.has(a.id)) {
          const url = await fetchThumbUrl(a.id);
          if (url) { const t = new Map(thumbs.value); t.set(a.id, url); thumbs.value = t; }
        }
      }
    }
  } catch (error: any) {
    showToast(error?.message || '附件上传失败');
  } finally { uploading.value = false; }
}

onBeforeUnmount(() => {
  for (const u of thumbs.value.values()) URL.revokeObjectURL(u);
  thumbs.value.clear();
});

const MIN_H = 24;
const MAX_H = 140;

async function resize() {
  await nextTick();
  const el = textareaRef.value;
  if (!el) return;
  el.style.height = 'auto';
  const next = Math.min(Math.max(el.scrollHeight, MIN_H), MAX_H);
  el.style.height = `${next}px`;
  el.style.overflowY = el.scrollHeight > MAX_H ? 'auto' : 'hidden';
}

watch(value, () => resize());

function handleSubmit() {
  const trimmed = value.value.trim();
  if (props.disabled) return;
  if (!trimmed) {
    if (currentDraft.value.length) showToast('请输入内容后再发送（已选附件会保留）');
    return;
  }
  // T-08：本轮附件随消息发送（transport 读取一次即清空）
  setPendingAttachments(takeDraft());
  emit('submit', trimmed);
  value.value = '';
  resize();
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return;
  if (event.shiftKey || event.isComposing) return;
  event.preventDefault();
  handleSubmit();
}
</script>

<template>
  <form class="composer" @submit.prevent="handleSubmit">
    <div v-if="currentDraft.length" class="composer__drafts">
      <span v-for="att in currentDraft" :key="att.id" class="composer__draft">
          <img v-if="att.kind === 'IMAGE' && thumbs.get(att.id)" :src="thumbs.get(att.id)" class="composer__draft-thumb" />
        <span class="composer__draft-name">{{ att.fileName }}</span>
        <i class="composer__draft-x" @click="removeAttachment(att.id)">×</i>
      </span>
    </div>
    <div class="composer__shell">
      <!-- T-08：附件上传（规则与文案来自 chat-shared，两端一致 R-12） -->
      <input
        ref="inputRef"
        type="file"
        multiple
        style="display: none"
        accept=".doc,.docx,.pdf,.xls,.xlsx,.txt,.md,.png,.jpg,.jpeg,.gif,.webp,.bmp"
        @change="onPicked"
      />
      <button
        type="button"
        class="composer__attach"
        :disabled="uploading || props.disabled"
        aria-label="上传附件"
        @click="pickFiles"
      >
        {{ uploading ? "…" : "＋附件" }}
      </button>
      <textarea
        ref="textareaRef"
        v-model="value"
        class="composer__input"
        rows="1"
        placeholder="发消息给智能体…"
        @keydown="handleKeydown"
      />
      <button
        v-if="streaming"
        type="button"
        class="composer__send composer__send--stop"
        aria-label="停止"
        @click="emit('stop')"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" />
        </svg>
      </button>
      <button
        v-else
        type="submit"
        class="composer__send"
        :class="{ 'is-ready': !!value.trim() && !props.disabled }"
        :disabled="!value.trim() || props.disabled"
        aria-label="发送"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            d="M12 19V5M5 12l7-7 7 7"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>
  </form>
</template>

<style lang="scss" scoped>
.composer {
  padding: 8px 12px 20px 12px;
}

.composer__shell {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 8px 8px 16px;
  background: var(--m-bg-elevated);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  transition: border-color 0.15s ease;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 -2px 20px rgba(0, 0, 0, 0.08);

  &:focus-within {
    border-color: var(--m-brand-primary);
  }
}

.composer__input {
  flex: 1 1 auto;
  min-height: 24px;
  max-height: 140px;
  font-size: 16px;
  line-height: 1.5;
  color: var(--m-text-primary);
  resize: none;
  outline: none;
  background: transparent;
  border: none;
}

.composer__input::placeholder {
  color: var(--m-text-muted);
}

.composer__send {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--m-text-muted);
  cursor: pointer;
  background: var(--m-border);
  border: none;
  border-radius: 50%;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease;

  &.is-ready {
    color: #fff;
    background: var(--m-brand-primary);
  }

  &:active.is-ready {
    transform: scale(0.94);
  }

  &:disabled {
    cursor: not-allowed;
  }
}

.composer__send--stop {
  color: #fff;
  background: #ee0a24;

  &:active {
    transform: scale(0.94);
  }
}

/* T-08：附件草稿 chips 与上传按钮 */
.composer__drafts { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px 10px 0; }
.composer__draft {
  display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px;
  border: 1px solid var(--van-border-color, #ebedf0); border-radius: 12px;
  background: var(--van-background-2, #f7f8fa); font-size: 12px; max-width: 200px;
}
.composer__draft-thumb { width: 18px; height: 18px; object-fit: cover; border-radius: 3px; }
.composer__draft-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.composer__draft-x { font-style: normal; cursor: pointer; color: var(--van-gray-6, #969799); }
.composer__attach {
  flex: none; padding: 4px 8px; border: 1px solid var(--van-border-color, #ebedf0);
  border-radius: 12px; background: var(--van-background-2, #fff); font-size: 12px;
  color: var(--van-text-color, #323233);
}
</style>
