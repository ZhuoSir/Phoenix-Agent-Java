<script setup lang="ts">
/**
 * 深度思考展示块（thinking-display T-02）：
 * 流式思考中 → 展开滚动 + "思考中…"；正文首字到达（streaming=false）→ 自动折叠为
 * 一行「🧠 已深度思考（N 秒）」，点击回看。无内容整块不渲染（R-04）。
 */
import { computed, ref, watch } from 'vue';

const props = defineProps<{ content: string; streaming: boolean; hasContent?: boolean; durationMs?: number }>();

const expanded = ref(true);
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
      <span class="thinking__icon">🧠</span>
      <span v-if="streaming" class="thinking__label thinking__label--live">深度思考中…</span>
      <span v-else class="thinking__label">已深度思考{{ seconds ? `（${seconds}）` : '' }}</span>
      <span class="thinking__caret">{{ expanded ? '▾' : '▸' }}</span>
    </button>
    <div v-show="expanded" class="thinking__body">{{ content }}</div>
  </div>
</template>

<style scoped>
.thinking {
  margin-bottom: 8px;
  border-radius: 8px;
  background: #f7f8fa;
  border: 1px solid #eceef2;
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
.thinking__label--live { color: #6b7bff; }
.thinking__caret { margin-left: auto; font-size: 11px; }
.thinking__body {
  max-height: 180px;
  padding: 2px 12px 10px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa1ad;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
