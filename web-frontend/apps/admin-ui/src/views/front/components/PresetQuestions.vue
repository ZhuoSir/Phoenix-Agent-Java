<script setup lang="ts">
/**
 * 前台预设问题（只读）：仅展示当前智能体的启用项、点击填入输入框。
 * 增删属管理操作（预设为该智能体全体用户共享），入口只在 admin 侧——BUG-45。
 */
import { computed, ref, watch } from 'vue';
import { ElMessage, ElIcon } from 'element-plus';
import { ArrowRight, ChatLineRound, Loading } from '@element-plus/icons-vue';
import { storeToRefs } from 'pinia';
import { useAgentStore } from '@phoenix/chat-shared';
import { getPresetQuestionsApi } from '#/api/front/chat';
import type { PresetQuestion } from '#/api/front/chat';

const emit = defineEmits<{
  select: [question: string];
  loaded: [count: number];
}>();

const agentStore = useAgentStore();
const { activeAgent } = storeToRefs(agentStore);

const questions = ref<PresetQuestion[]>([]);
const loading = ref(false);

const activeQuestions = computed(() => {
  return questions.value.filter((q: any) => q.isActive !== false);
});

async function loadPresetQuestions() {
  if (!activeAgent.value?.id) return;
  loading.value = true;
  try {
    questions.value = (await getPresetQuestionsApi(
      Number(activeAgent.value.id),
    )) as PresetQuestion[];
    emit('loaded', activeQuestions.value.length);
  } catch {
    ElMessage.error('加载预设问题失败');
  } finally {
    loading.value = false;
  }
}

function handleClick(question: PresetQuestion) {
  emit('select', question.question || '');
}

watch(
  () => activeAgent.value?.id,
  (id) => {
    if (id) {
      loadPresetQuestions();
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="preset-questions">
    <div class="preset-questions__header flex gap-2">
      <el-icon class="preset-questions__header-icon"><ChatLineRound /></el-icon>
      <span class="preset-questions__header-title">预设问题</span>
    </div>

    <div v-if="loading" class="preset-questions__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div
      v-else-if="activeQuestions.length === 0"
      class="preset-questions__empty flex-items-center"
    >
      暂无预设问题
    </div>

    <div v-else class="preset-questions__list">
      <div
        v-for="question in activeQuestions"
        :key="question.id"
        class="preset-questions__item"
        @click="handleClick(question)"
      >
        <span class="preset-questions__item-text">{{ question.question }}</span>
        <el-icon class="preset-questions__item-arrow"><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.preset-questions {
  padding: 12px 16px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
}

.preset-questions__header {
  display: flex;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 10px;
  border-bottom: 1px solid hsl(var(--border));
}

.preset-questions__header-icon {
  color: hsl(var(--primary));
}

.preset-questions__header-title {
  font-size: 13px;
  font-weight: 600;
}

.preset-questions__loading,
.preset-questions__empty {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 12px 0;
  font-size: 12px;
  color: #909399;
}

.preset-questions__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preset-questions__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.15s;
}

.preset-questions__item:hover {
  background: hsl(var(--accent));
}

.preset-questions__item-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-questions__item-arrow {
  flex: none;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
