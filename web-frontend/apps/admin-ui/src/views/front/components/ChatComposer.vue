<script setup lang="ts">
const emit = defineEmits(['open-files'])
import { computed, nextTick, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAgentStore, useChatStore } from '@phoenix/chat-shared';
import { ElMessage, ElIcon, ElOption, ElSelect, ElTooltip } from 'element-plus';
import { FolderOpened, ArrowDown, ArrowUp, Paperclip, Close } from '@element-plus/icons-vue';

import { getMySkillsApi } from '#/api/front/agent';

import { setExplicitSkillIds, setPendingAttachments } from '../api-transport';

import type { ChatAttachmentMeta } from '@phoenix/chat-shared';
import {
  ATTACHMENT_MAX_FILES_PER_SEND,
  attachmentCountMessage,
  validateAttachmentsLocally,
} from '@phoenix/chat-shared';
import {
  fetchAttachmentThumbUrlApi,
  formatAttachmentSize,
  uploadChatAttachmentsApi,
} from '#/api/core/chatAttachment';
import PresetQuestions from './PresetQuestions.vue';

const presetCount = ref(0);

const chat = useChatStore();
const agentStore = useAgentStore();
const { isActiveSessionSending, activeSessionId } = storeToRefs(chat);
const { activeAgent } = storeToRefs(agentStore);

const inputValue = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const presetCollapsed = ref(true);

/** 前台技能区（R-09）：仅 harness 智能体展示当前账号可见可用的技能 */
const mySkills = ref<Array<{ id: number; name: string }>>([]);
const selectedSkillIds = ref<number[]>([]);

watch(
  () => activeAgent.value?.id,
  async (id) => {
    selectedSkillIds.value = [];
    mySkills.value = [];
    if (!id || activeAgent.value?.type !== 'harness') return;
    mySkills.value = await getMySkillsApi(id);
  },
  { immediate: true },
);

const hasPendingConfirm = computed(() => {
  const sessionId = activeSessionId.value;
  if (!sessionId) return false;
  const msgs = chat.messagesByS[sessionId] ?? [];
  return msgs.some((m: any) => m.messageType === 'harness-confirm');
});

// ===== T-07：对话附件（chat-attachment-understanding）=====
/** 待发送附件草稿：**按会话分片**（L-20：切会话不串附件） */
const attachmentDrafts = ref<Map<string, ChatAttachmentMeta[]>>(new Map());
const attachmentInputRef = ref<HTMLInputElement | null>(null);
const uploadingAttachments = ref(false);
const thumbUrls = ref<Map<number, string>>(new Map());

function currentDraft(): ChatAttachmentMeta[] {
  const sid = activeSessionId.value;
  return (sid && attachmentDrafts.value.get(sid)) || [];
}

function takeDraft(): ChatAttachmentMeta[] {
  const list = currentDraft();
  const sid = activeSessionId.value;
  if (sid && list.length) {
    const next = new Map(attachmentDrafts.value);
    next.set(sid, []);
    attachmentDrafts.value = next;
  }
  return list;
}

function pickAttachments() {
  attachmentInputRef.value?.click();
}

async function loadThumb(att: ChatAttachmentMeta) {
  if (att.kind !== "IMAGE" || thumbUrls.value.has(att.id)) return;
  // 缩略图必须 fetch+blob（<img src> 带不了鉴权头 —— T-05 交接注记）
  const url = await fetchAttachmentThumbUrlApi(att.id);
  if (url) {
    const next = new Map(thumbUrls.value);
    next.set(att.id, url);
    thumbUrls.value = next;
  }
}

async function onAttachmentsPicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = [...(input.files || [])];
  input.value = ''; // 允许再次选同一文件
  if (!files.length) return;
  // 会话可能尚未创建（composer 惰性建会话）：先建，避免附件无处归属
  let sid = activeSessionId.value;
  if (!sid) {
    if (!activeAgent.value?.id) {
      ElMessage.error('当前没有可用的智能体');
      return;
    }
    try {
      await chat.createSession(activeAgent.value.id);
    } catch {
      ElMessage.error('创建会话失败');
      return;
    }
    sid = activeSessionId.value;
  }
  if (!sid) return;
  const existed = attachmentDrafts.value.get(sid) || [];
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
      const next = new Map(attachmentDrafts.value);
      next.set(sid, [...existed, ...res.accepted]);
      attachmentDrafts.value = next;
      for (const a of res.accepted) await loadThumb(a);
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '附件上传失败');
  } finally {
    uploadingAttachments.value = false;
  }
}

function removeAttachment(id: number) {
  const sid = activeSessionId.value;
  if (!sid) return;
  const next = new Map(attachmentDrafts.value);
  next.set(sid, (next.get(sid) || []).filter((a) => a.id !== id));
  attachmentDrafts.value = next;
}

const MIN_HEIGHT = 48;
const MAX_HEIGHT = 200;

async function resize() {
  await nextTick();
  const el = textareaRef.value;
  if (!el) return;
  el.style.height = 'auto';
  const next = Math.min(Math.max(el.scrollHeight, MIN_HEIGHT), MAX_HEIGHT);
  el.style.height = `${next}px`;
  el.style.overflowY = el.scrollHeight > MAX_HEIGHT ? 'auto' : 'hidden';
}

watch(inputValue, () => {
  resize();
});

async function handlePresetQuestionClick(question: string) {
  if (isActiveSessionSending.value) {
    ElMessage.warning('智能体正在处理中，请稍后...');
    return;
  }
  if (hasPendingConfirm.value) {
    ElMessage.warning('请先处理当前确认请求');
    return;
  }

  if (!activeSessionId.value) {
    try {
      if (!activeAgent.value?.id) {
        ElMessage.error('当前没有可用的智能体');
        return;
      }
      await chat.createSession(activeAgent.value.id);
    } catch {
      ElMessage.error('创建会话失败');
      return;
    }
  }

  inputValue.value = question;
  await nextTick();
  await handleSubmit();
}

async function handleSubmit() {
  const value = inputValue.value.trim();
  if (!value) {
    if (currentDraft().length) ElMessage.warning("请输入问题后再发送（已选附件会保留）");
    return;
  }
  if (isActiveSessionSending.value) return;
  if (hasPendingConfirm.value) return;
  inputValue.value = '';
  resize();
  // 显式技能仅对下一条消息生效（不持久）
  // T-07：本轮附件随消息发送（transport 读取一次即清空）
  setPendingAttachments(takeDraft());
  setExplicitSkillIds(selectedSkillIds.value);
  await chat.send(value);
  selectedSkillIds.value = [];
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return;
  if (event.shiftKey || event.isComposing) return;
  if (hasPendingConfirm.value) return;
  event.preventDefault();
  handleSubmit();
}
</script>

<template>
  <div class="composer">
    <form class="composer__inner" @submit.prevent="handleSubmit">
      <!-- BL-19：卡内顶栏工具条——左技能/预设，右文件；与输入区同容器天然对齐 -->
      <div class="composer__toolbar">
        <div
          v-if="activeAgent?.type === 'harness' && mySkills.length > 0"
          class="composer__tb-skills"
        >
          <span class="composer__tb-label">显式技能</span>
          <ElSelect
            v-model="selectedSkillIds"
            class="composer__tb-select"
            clearable
            collapse-tags
            collapse-tags-tooltip
            filterable
            multiple
            placeholder="模型自主匹配"
            size="small"
          >
            <ElOption
              v-for="skill in mySkills"
              :key="skill.id"
              :label="skill.name"
              :value="skill.id"
            />
          </ElSelect>
        </div>
        <button
          v-if="presetCount > 0"
          type="button"
          class="composer__tb-chip"
          :class="{ 'is-open': !presetCollapsed }"
          @click="presetCollapsed = !presetCollapsed"
        >
          <el-icon class="composer__tb-icon"><ArrowUp v-if="!presetCollapsed" /><ArrowDown v-else /></el-icon>
          预设问题
        </button>
        <span class="composer__tb-spacer" />
        <!-- T-07：附件上传（白名单/上限即时反馈，最终裁决在后端 R-01~R-04） -->
        <input
          ref="attachmentInputRef"
          type="file"
          multiple
          style="display: none"
          accept=".doc,.docx,.pdf,.xls,.xlsx,.txt,.md,.png,.jpg,.jpeg,.gif,.webp,.bmp"
          @change="onAttachmentsPicked"
        />
        <button
          type="button"
          class="composer__tb-chip"
          :disabled="uploadingAttachments"
          title="上传附件：文档 word/pdf/excel/txt/md，图片 png/jpg/gif/webp/bmp；单个≤20MB，单次≤5个"
          @click="pickAttachments"
        >
          <el-icon class="composer__tb-icon"><Paperclip /></el-icon>
          {{ uploadingAttachments ? '上传中…' : '附件' }}
        </button>
        <button
          type="button"
          class="composer__tb-chip composer__tb-chip--files"
          @click="emit('open-files')"
        >
          <el-icon class="composer__tb-icon"><FolderOpened /></el-icon>
          文件列表
        </button>
      </div>
      <div v-show="!presetCollapsed" class="composer__preset-body">
        <PresetQuestions @select="handlePresetQuestionClick" @loaded="presetCount = $event" />
      </div>
      <!-- T-07：待发送附件草稿（按会话分片，L-20） -->
      <div v-if="currentDraft().length" class="composer__attachments">
        <span
          v-for="att in currentDraft()"
          :key="att.id"
          class="composer__att-chip"
        >
          <img
            v-if="att.kind === 'IMAGE' && thumbUrls.get(att.id)"
            :src="thumbUrls.get(att.id)"
            class="composer__att-thumb"
          />
          <span class="composer__att-name">{{ att.fileName }}</span>
          <span class="composer__att-size">{{ formatAttachmentSize(att.sizeBytes) }}</span>
          <el-icon class="composer__att-x" @click="removeAttachment(att.id)"><Close /></el-icon>
        </span>
      </div>
      <textarea
        ref="textareaRef"
        v-model="inputValue"
        class="composer__input"
        rows="1"
        placeholder="输入您的问题，Enter 发送，Shift + Enter 换行"
        @keydown="handleKeydown"
      />
      <div class="composer__actions">
        <span class="composer__actions-spacer" />
        <el-tooltip
          v-if="!isActiveSessionSending"
          :content="!inputValue.trim() ? '请输入问题' : '发送'"
          placement="top"
          :show-after="300"
        >
          <button
            type="submit"
            class="composer__send"
            :disabled="!inputValue.trim() || hasPendingConfirm"
            aria-label="发送"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <path
                d="M2.5 8h9m0 0L7.75 4.25M11.5 8l-3.75 3.75"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </el-tooltip>
        <el-tooltip
          v-else
          content="停止"
          placement="top"
          :show-after="300"
        >
          <button
            type="button"
            class="composer__send composer__send--stop"
            aria-label="停止"
            @click="chat.stopSending(activeSessionId ?? undefined)"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <rect x="3" y="3" width="10" height="10" rx="2" fill="currentColor" />
            </svg>
          </button>
        </el-tooltip>
      </div>
    </form>
  </div>
</template>

<style lang="scss" scoped>
.composer {
  padding: 12px 24px 16px;

  &__inner {
    position: relative;
    display: flex;
    flex-direction: column;
    max-width: var(--pc-chat-content-width); /* thinking/composer 视觉对齐：与消息内容区同宽 */
    margin: 0 auto;
    background: hsl(var(--background));
    border: 1px solid hsl(var(--border));
    border-radius: 12px;
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    box-shadow: 0 -2px 20px rgba(0, 0, 0, 0.08);
    transition:
      border-color 0.15s ease,
      box-shadow 0.15s ease;


    &:focus-within {
      border-color: hsl(var(--primary));
      box-shadow: 0 0 0 3px hsl(var(--primary) / 12%);
    }
  }

  &__input {
    width: 100%;
    min-height: 48px;
    max-height: 200px;
    padding: 12px 14px 4px;
    overflow-y: hidden;
    font-family: inherit;
    font-size: 14px;
    line-height: 1.6;
    color: hsl(var(--foreground));
    resize: none;
    outline: none;
    background: transparent;
    border: none;

    &::placeholder {
      color: hsl(var(--muted-foreground));
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 10px 8px 14px;
  }

  &__actions-spacer {
    flex: 1 1 auto;
  }

  &__send {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    color: #fff;
    cursor: pointer;
    background: hsl(var(--primary));
    border: 1px solid hsl(var(--primary));
    border-radius: 50%;
    transition:
      background 0.15s ease,
      color 0.15s ease,
      border-color 0.15s ease,
      opacity 0.15s ease;

    &:hover {
      filter: brightness(0.95);
    }

    &:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }

    &--stop {
      color: #fff;
      cursor: pointer;
      background: #f56c6c;
      border-color: #f56c6c;

      &:hover {
        filter: brightness(0.9);
      }
    }
  }

  &__preset {
    position: relative;
    max-width: var(--pc-chat-content-width); /* thinking/composer 视觉对齐：与消息内容区同宽 */
    margin: 0 auto 10px;
  }

  &__preset-header {
    position: absolute;
    top: -24px;
    right: 0;
    display: flex;
    gap: 4px;
    align-items: center;
    justify-content: space-between;
    padding: 3px 8px;
    cursor: pointer;
    border-radius: 8px;
    transition: background 0.15s ease;

    &:hover {
      /* background: hsl(var(--primary) / 8%); */
      *{
        color: hsl(var(--primary) / 80%);
      }
    }
  }

  &__preset-title {
    font-size: 13px;
    font-weight: 500;
    color: hsl(var(--muted-foreground));
  }

  &__preset-close {
    position: absolute;
    top: 10px;
    right: 8px;
    font-size: 16px;
    color: hsl(var(--muted-foreground));
    cursor: pointer;
    transition: transform 0.2s ease;
  }

  &__preset-toggle {
    font-size: 16px;
    color: hsl(var(--muted-foreground));
    transition: transform 0.2s ease;
  }

  &__preset-body {
    margin-top: 8px;
  }
}



.composer__toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 14px;
  border-bottom: 1px solid #f0f2f5;
}
.composer__tb-skills {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}
.composer__tb-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.composer__tb-select {
  width: 190px;
}
.composer__tb-spacer { flex: 1; }
.composer__tb-chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 4px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  background: #f4f6f8;
  border: none;
  border-radius: 14px;
  transition: all 0.15s;
}
.composer__tb-chip:hover,
.composer__tb-chip.is-open {
  color: var(--el-color-primary, #409eff);
  background: #ecf5ff;
}
.composer__tb-chip--files { color: #337ecc; }
.composer__tb-chip--files:hover { color: var(--el-color-primary, #409eff); }
.composer__tb-icon { font-size: 13px; }
.composer__preset-body {
  padding: 10px 14px 2px;
}

/* T-07：附件草稿 chips */
.composer__attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 10px 0;
}
.composer__att-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 14px;
  background: var(--el-fill-color-light, #f7f8fa);
  font-size: 12px;
  max-width: 260px;
}
.composer__att-thumb {
  width: 20px;
  height: 20px;
  object-fit: cover;
  border-radius: 4px;
}
.composer__att-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.composer__att-size {
  color: var(--el-text-color-secondary, #909399);
}
.composer__att-x {
  cursor: pointer;
  color: var(--el-text-color-secondary, #909399);
}
</style>
