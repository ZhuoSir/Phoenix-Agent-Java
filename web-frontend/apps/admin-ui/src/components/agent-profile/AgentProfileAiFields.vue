<script lang="ts" setup>
/**
 * 智能体「描述 + 提示词」字段组（T-11，R-01~R-04/R-09）。
 *
 * 之所以把两个字段与 AI 生成动作做成一个组件：抽屉与 /agent/:id 编辑页要行为一致
 * （覆盖确认、撤销、独立 loading、失败不清空），两处各写一份必然漂移。
 *
 * 约定：组件只改表单值，**不保存**（保存仍由页面的保存按钮负责）。
 */
import { computed, ref } from 'vue';

import {
  generateProfileApi,
  type ProfileField,
} from '#/api/core/agentProfile';
import { ElButton, ElInput, ElMessage, ElMessageBox } from 'element-plus';

import MarkdownEditorField from '../markdown-editor/MarkdownEditorField.vue';

const props = withDefaults(
  defineProps<{
    /** 生成依据：智能体名称 */
    name?: null | string;
    /** 两个字段允许 null（库表可空），组件内统一按空串处理 */
    description?: null | string;
    prompt?: null | string;
    /** 提示词必含骨架段落（与后端 meta-prompt 同源，由调用方传入常量） */
    requiredSections?: string[];
    disabled?: boolean;
  }>(),
  { name: '', description: '', prompt: '', disabled: false, requiredSections: () => ['角色', '描述', '能力', '安全范围'] },
);

const emit = defineEmits<{
  'update:description': [value: string];
  'update:prompt': [value: string];
}>();

const descriptionValue = computed({
  get: () => props.description ?? '',
  set: (v: string) => emit('update:description', v),
});
const promptValue = computed({
  get: () => props.prompt ?? '',
  set: (v: string) => emit('update:prompt', v),
});

const loadingDesc = ref(false);
const loadingPrompt = ref(false);
const loadingBoth = ref(false);
/** 撤销快照：只记本次生成前的内容，保存后不清 */
const undoSnapshot = ref<null | { description: string; prompt: string }>(null);
const lastError = ref('');

const generating = computed(
  () => loadingDesc.value || loadingPrompt.value || loadingBoth.value,
);

async function runGenerate(targets: ProfileField[]) {
  const name = (props.name ?? '').trim();
  if (!name) {
    ElMessage.warning('请先填写智能体名称');
    return;
  }
  // 覆盖前确认（R-04）：任一目标字段已有内容时询问
  const willOverwrite = targets.some((t) =>
    t === 'DESCRIPTION' ? !!descriptionValue.value.trim() : !!promptValue.value.trim(),
  );
  if (willOverwrite) {
    try {
      await ElMessageBox.confirm(
        '生成结果将覆盖已填写的内容，是否继续？（覆盖后可点「撤销」恢复）',
        '确认覆盖',
        { type: 'warning', confirmButtonText: '继续生成', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }

  const wantDesc = targets.includes('DESCRIPTION');
  const wantPrompt = targets.includes('PROMPT');
  if (targets.length === 2) loadingBoth.value = true;
  else if (wantDesc) loadingDesc.value = true;
  else loadingPrompt.value = true;
  lastError.value = '';

  try {
    const res = await generateProfileApi({
      name,
      description: descriptionValue.value || undefined,
      prompt: promptValue.value || undefined,
      targets,
    });
    if (!res.success || !res.data) {
      lastError.value = res.msg || '生成失败，请稍后重试';
      ElMessage.error(lastError.value);
      return;
    }
    undoSnapshot.value = {
      description: descriptionValue.value,
      prompt: promptValue.value,
    };
    if (wantDesc && res.data.description) descriptionValue.value = res.data.description;
    if (wantPrompt && res.data.prompt) promptValue.value = res.data.prompt;
    ElMessage.success(
      `已生成（模型：${res.data.modelName || '默认对话模型'}），可继续编辑后再保存`,
    );
  } catch (error: any) {
    lastError.value = error?.message || '生成失败，请稍后重试';
    ElMessage.error(lastError.value);
  } finally {
    loadingBoth.value = false;
    loadingDesc.value = false;
    loadingPrompt.value = false;
  }
}

function undo() {
  if (!undoSnapshot.value) return;
  descriptionValue.value = undoSnapshot.value.description;
  promptValue.value = undoSnapshot.value.prompt;
  undoSnapshot.value = null;
  ElMessage.info('已恢复到生成前内容');
}
</script>

<template>
  <div>
    <div class="mb-5">
      <label class="mb-2 block text-sm font-medium text-gray-700">描述</label>
      <ElInput
        v-model="descriptionValue"
        :rows="3"
        type="textarea"
        :disabled="disabled"
        placeholder="请输入智能体描述（纯文本，一句话）"
      />
      <div class="mt-2 flex items-center gap-2">
        <ElButton size="small" :loading="loadingDesc" :disabled="disabled || generating" @click="runGenerate(['DESCRIPTION'])">
          AI 生成描述
        </ElButton>
        <span class="text-xs text-gray-400">纯文本，不超过 120 字</span>
      </div>
    </div>

    <div class="mb-5">
      <label class="mb-2 block text-sm font-medium text-gray-700">智能体Prompt</label>
      <MarkdownEditorField
        v-model="promptValue"
        :disabled="disabled"
        :required-sections="requiredSections"
        :rows="16"
        placeholder="用 Markdown 书写系统提示词：## 角色 / ## 描述 / ## 能力 / ## 安全范围"
      />
      <div class="mt-2 flex flex-wrap items-center gap-2">
        <ElButton size="small" :loading="loadingPrompt" :disabled="disabled || generating" @click="runGenerate(['PROMPT'])">
          AI 生成提示词
        </ElButton>
        <ElButton size="small" type="primary" :loading="loadingBoth" :disabled="disabled || generating" @click="runGenerate(['DESCRIPTION','PROMPT'])">
          AI 同时生成两项
        </ElButton>
        <ElButton v-if="undoSnapshot" size="small" :disabled="disabled" @click="undo">
          撤销生成
        </ElButton>
        <span class="text-xs text-gray-400">
          生成使用「模型管理里设为默认」的对话模型；结果只填表单，点保存才生效
        </span>
      </div>
      <div v-if="lastError" class="mt-1 text-xs text-red-500">{{ lastError }}</div>
    </div>
  </div>
</template>
