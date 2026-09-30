<script lang="ts" setup>
/**
 * 轻量 Markdown 编辑字段（T-10，Q4=B 自研方案）。
 *
 * 设计取舍：项目已装 markdown-it（渲染侧），但不含任何编辑器组件；本需求实质是
 * 「能直接写 md + 能预览 + 生成产物也是 md」，故不引入第三方编辑器（体积/主题冲突），
 * 用 textarea + 光标处插入 + markdown-it 预览实现。
 *
 * 安全：预览 html:false 且只允许 http/https/mailto 链接，避免提示词里的原始 HTML/JS 被渲染执行。
 */
import { computed, ref } from 'vue';

import MarkdownIt from 'markdown-it';

import { getProfileSkeletonApi } from '#/api/core/agentProfile';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    placeholder?: string;
    /** 必含骨架段落（用于缺失提示，来源仍是后端骨架） */
    requiredSections?: string[];
    rows?: number;
    disabled?: boolean;
  }>(),
  { placeholder: '可用 Markdown 书写：## 标题、- 列表、**加粗**、`代码`', rows: 16, disabled: false, requiredSections: () => [] },
);

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

type ViewMode = 'edit' | 'preview' | 'split';

const md = new MarkdownIt({
  html: false,
  linkify: false,
  breaks: true,
  validateLink: (url: string) => /^(?:https?:|mailto:)/i.test(url),
});

interface ToolbarButton {
  fn: () => void;
  label: string;
  primary?: boolean;
}

const textareaRef = ref<HTMLTextAreaElement | null>(null);
const viewMode = ref<ViewMode>('split');
const skeleton = ref('');

const value = computed({
  get: () => props.modelValue ?? '',
  set: (v: string) => emit('update:modelValue', v),
});

const previewHtml = computed(() => md.render(value.value || ''));

const charCount = computed(() => value.value.length);

/** 骨架段落缺失提示（不阻断保存，仅提醒） */
const missingSections = computed(() => {
  if (props.requiredSections.length === 0) return [];
  return props.requiredSections.filter((s) => !value.value.includes(`## ${s}`));
});

function applyText(next: string) {
  value.value = next;
}

/** 在光标处插入/包裹文本（保持光标可用，不吞已输入内容） */
function wrapSelection(before: string, after = '', blockHint?: string) {
  const el = textareaRef.value;
  const text = value.value;
  if (!el) {
    applyText(text + before + (blockHint ?? '') + after);
    return;
  }
  const start = el.selectionStart ?? text.length;
  const end = el.selectionEnd ?? text.length;
  const selected = text.slice(start, end) || blockHint || '';
  const next = text.slice(0, start) + before + selected + after + text.slice(end);
  applyText(next);
  requestAnimationFrame(() => {
    el.focus();
    const caret = start + before.length + selected.length + after.length;
    el.setSelectionRange(caret, caret);
  });
}

async function insertSkeleton() {
  if (!skeleton.value) {
    const res = await getProfileSkeletonApi();
    if (!res.success || !res.data) {
      return;
    }
    skeleton.value = res.data;
  }
  const el = textareaRef.value;
  const text = value.value;
  const head = text.trim() ? `${text.replace(/\s+$/, '')}\n\n` : '';
  applyText(head + skeleton.value + '\n');
  requestAnimationFrame(() => el?.focus());
}

function insertInlineCode() {
  wrapSelection('`', '`', 'code');
}
function insertBold() {
  wrapSelection('**', '**', '加粗文本');
}
function insertItalic() {
  wrapSelection('*', '*', '斜体文本');
}
function insertHeading() {
  wrapSelection('\n## ', '', '段落名');
}
function insertUl() {
  wrapSelection('\n- ', '', '要点一');
}
function insertOl() {
  wrapSelection('\n1. ', '', '步骤一');
}
function insertCodeBlock() {
  wrapSelection('\n```\n', '\n```\n', '示例代码');
}
function insertLink() {
  wrapSelection('[', '](https://example.com)', '链接文字');
}

const toolbarButtons: ToolbarButton[] = [
  { label: '插入骨架', fn: () => void insertSkeleton(), primary: true },
  { label: '标题', fn: insertHeading },
  { label: '加粗', fn: insertBold },
  { label: '斜体', fn: insertItalic },
  { label: '无序列表', fn: insertUl },
  { label: '有序列表', fn: insertOl },
  { label: '行内代码', fn: insertInlineCode },
  { label: '代码块', fn: insertCodeBlock },
  { label: '链接', fn: insertLink },
];

const viewModes: { key: ViewMode; label: string }[] = [
  { key: 'edit', label: '编辑' },
  { key: 'split', label: '分栏' },
  { key: 'preview', label: '预览' },
];

function onTab(event: KeyboardEvent) {
  // Tab 缩进两格，不劫持 Ctrl+S（保存交给页面自身）
  if (event.key === 'Tab') {
    event.preventDefault();
    wrapSelection('  ', '');
  }
}

defineExpose({ focus: () => textareaRef.value?.focus() });
</script>

<template>
  <div class="md-field">
    <div class="md-field__bar">
      <button v-for="btn in toolbarButtons" :key="btn.label" type="button" class="md-field__btn"
        :class="{ 'md-field__btn--primary': btn.primary }" :disabled="props.disabled" @click="btn.fn">
        {{ btn.label }}
      </button>
      <span class="md-field__spacer"></span>
      <button v-for="mode in viewModes" :key="mode.key" type="button" class="md-field__btn"
        :class="{ 'md-field__btn--active': viewMode === mode.key }" @click="viewMode = mode.key">
        {{ mode.label }}
      </button>
    </div>

    <div class="md-field__body" :class="`md-field__body--${viewMode}`">
      <textarea v-if="viewMode !== 'preview'" ref="textareaRef" class="md-field__textarea"
        :value="value" :placeholder="placeholder" :disabled="disabled" :rows="rows" spellcheck="false"
        @input="applyText(($event.target as HTMLTextAreaElement).value)" @keydown="onTab"></textarea>
      <div v-if="viewMode !== 'edit'" class="md-field__preview">
        <!-- eslint-disable-next-line vue/no-v-html —— 内容经 markdown-it(html:false) 渲染，仅安全链接 -->
        <div class="md-preview" v-html="previewHtml"></div>
      </div>
    </div>

    <div class="md-field__foot">
      <span>{{ charCount }} 字</span>
      <span v-if="missingSections.length > 0" class="md-field__warn">
        建议补全段落：{{ missingSections.join('、') }}（不阻断保存）
      </span>
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<style scoped>
.md-field {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
}

.md-field__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  padding: 6px 8px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}

.md-field__btn {
  padding: 2px 8px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.md-field__btn:hover:not(:disabled) {
  color: #409eff;
  border-color: #b3d8ff;
}

.md-field__btn--primary {
  color: #fff;
  background: #409eff;
  border-color: #409eff;
}

.md-field__btn--active {
  color: #409eff;
  border-color: #409eff;
}

.md-field__btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.md-field__spacer {
  flex: 1;
}

.md-field__body {
  display: grid;
  grid-template-columns: 1fr;
}

.md-field__body--split {
  grid-template-columns: 1fr 1fr;
}

.md-field__textarea {
  width: 100%;
  padding: 10px 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  resize: vertical;
  border: none;
  outline: none;
}

.md-field__preview {
  padding: 10px 14px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.7;
  border-left: 1px solid #ebeef5;
  max-height: 640px;
}

.md-field__body--edit .md-field__preview {
  display: none;
}

.md-field__body--preview .md-field__textarea {
  display: none;
}

.md-field__body--preview .md-field__preview {
  border-left: none;
}

.md-field__foot {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 10px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #ebeef5;
}

.md-field__warn {
  color: #e6a23c;
}

.md-preview :deep(h2) {
  margin: 12px 0 6px;
  font-size: 15px;
}

.md-preview :deep(pre) {
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.md-preview :deep(ul),
.md-preview :deep(ol) {
  padding-left: 20px;
}
</style>
