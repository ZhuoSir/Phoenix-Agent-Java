<script setup lang="ts">
/**
 * 深度思考展示块（thinking-display T-02）：
 * 流式思考中 → 展开滚动 + "思考中…"；正文首字到达（streaming=false）→ 自动折叠为
 * 一行「🧠 已深度思考（N 秒）」，点击回看。无内容整块不渲染（R-04）。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';

const props = defineProps<{ content: string; streaming: boolean; hasContent?: boolean; durationMs?: number }>();

const expanded = ref(true);

// long-turn-resilience T-03 附加：思考区贴底跟随（用户手动上翻则暂停跟随，滚回底部自动恢复）
const bodyRef = ref<HTMLElement | null>(null);
const stickToBottom = ref(true);
function onThinkingScroll() {
  const el = bodyRef.value;
  if (!el) return;
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight <= 24;
}

/** 滚到最新一行（挂载/展开/增量到达时调用；历史回显也要停在底部） */
async function scrollThinkingToBottom() {
  await nextTick();
  const el = bodyRef.value;
  if (el) el.scrollTop = el.scrollHeight;
}

// 刷新后挂载即贴底（原先只在内容变化时跟随 → 刷新后停在第一行需手动下拉）
onMounted(() => {
  stickToBottom.value = true;
  void scrollThinkingToBottom();
});

// 展开时同样贴底
watch(expanded, (on: boolean) => {
  if (on) void scrollThinkingToBottom();
});
watch(
  () => props.content,
  async () => {
    if (!stickToBottom.value) return;
    await nextTick();
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  },
);
// 新一轮思考开始 → 恢复跟随并跳底
watch(
  () => props.streaming,
  async (on: boolean) => {
    if (!on) return;
    stickToBottom.value = true;
    await nextTick();
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  },
);
// R-03：正文首字到达(hasContent)或流式结束(streaming→false) → 自动折叠；再次思考则恢复展开
watch(
  () => [props.hasContent, props.streaming] as const,
  ([has, st], [prevHas, prevSt]) => {
    if (!prevHas && has) expanded.value = false;
    if (!prevSt && st) expanded.value = true;
    if (prevSt && !st && !has) expanded.value = false;
  },
);

const seconds = computed(() => {
  const ms = props.durationMs ?? 0;
  return ms > 0 ? `${Math.max(1, Math.round(ms / 1000))} 秒` : '';
});
</script>

<template>
  <div v-if="content" class="thinking">
    <button class="thinking__head" type="button" @click="expanded = !expanded">
      <span v-if="streaming" class="thinking__dots"><i /><i /><i /></span>
      <span class="thinking__label">{{ streaming ? 'Thinking…' : 'Think Done' }}{{ !streaming && seconds ? ` · ${seconds}` : '' }}</span>
      <span class="thinking__caret">{{ expanded ? '▾' : '▸' }}</span>
    </button>
    <div v-show="expanded" ref="bodyRef" class="thinking__body" @scroll="onThinkingScroll">{{ content }}</div>
  </div>
</template>

<style scoped>
.thinking {
  margin-bottom: 8px;
  border: 1px solid #eef0f4;
  border-left: 3px solid #c6d4f7;
  border-radius: 8px;
  background: #fafbfc;
}
.thinking__head {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: #8a919f;
  cursor: pointer;
  background: none;
  border: none;
  text-align: left;
}
.thinking__label { font-weight: 500; letter-spacing: 0.2px; }
.thinking__caret { margin-left: auto; font-size: 11px; color: #c0c4cc; }
.thinking__body {
  max-height: 180px;
  padding: 2px 12px 10px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa1ad;
  white-space: pre-wrap;
  word-break: break-word;
}
.thinking__dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}
.thinking__dots i {
  width: 4px;
  height: 4px;
  background: #9db6f0;
  border-radius: 50%;
  animation: thinking-bounce 1s infinite ease-in-out;
}
.thinking__dots i:nth-child(2) { animation-delay: 0.15s; }
.thinking__dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes thinking-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.55; }
  50% { transform: translateY(-3px); opacity: 1; }
}
</style>
