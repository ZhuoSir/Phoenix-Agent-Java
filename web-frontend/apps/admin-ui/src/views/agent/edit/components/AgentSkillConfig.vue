<script lang="ts" setup>
import type { SkillOption } from '#/api/core/skill';

import { computed, onMounted, ref } from 'vue';

import {
  bindAgentSkillsApi,
  getAgentBoundSkillsApi,
  getSkillOptionsApi,
} from '#/api/core/skill';
import { ElAlert, ElButton, ElMessage, ElOption, ElSelect } from 'element-plus';

const props = defineProps<{ agentId: number; agentType?: string }>();

const options = ref<SkillOption[]>([]);
const selected = ref<number[]>([]);
const loading = ref(false);
const saving = ref(false);

const isHarness = computed(() => props.agentType === 'harness');

async function load() {
  if (!props.agentId) {
    return;
  }
  loading.value = true;
  try {
    const [optionRes, boundRes] = await Promise.all([
      getSkillOptionsApi(props.agentId),
      getAgentBoundSkillsApi(props.agentId),
    ]);
    options.value = optionRes.success ? (optionRes.data ?? []) : [];
    selected.value = boundRes.success ? (boundRes.data ?? []) : [];
  } finally {
    loading.value = false;
  }
}

function optionLabel(option: SkillOption) {
  return option.status === 'published' ? option.name : `${option.name}（已下线）`;
}

/** 已下线且未绑定的技能不可新选；已下线的已绑定技能保留（可手动解绑） */
function optionDisabled(option: SkillOption) {
  return option.status !== 'published' && !option.bound;
}

async function save() {
  saving.value = true;
  try {
    const res = await bindAgentSkillsApi(props.agentId, selected.value);
    if (!res.success) {
      ElMessage.error(res.msg || '技能配置保存失败');
      return;
    }
    ElMessage.success('技能配置已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h3 class="m-0 mb-4 text-base font-semibold">技能配置</h3>

    <ElAlert
      v-if="!isHarness"
      :closable="false"
      class="mb-4"
      show-icon
      title="当前智能体类型不是 Harness：技能绑定可保存，但运行时不会生效（仅 Harness 类智能体加载技能）"
      type="info"
    />

    <div class="mb-5">
      <label class="mb-2 block text-sm font-medium text-gray-700">
        可选技能（仅「已发布」可新绑定）
      </label>
      <ElSelect
        v-model="selected"
        class="w-full"
        collapse-tags
        collapse-tags-tooltip
        filterable
        multiple
        :loading="loading"
        placeholder="选择该智能体可用的技能，可多选"
      >
        <ElOption
          v-for="option in options"
          :key="option.skillId"
          :disabled="optionDisabled(option)"
          :label="optionLabel(option)"
          :value="option.skillId"
        />
      </ElSelect>
      <div class="mt-2 text-xs text-gray-500">
        已绑定的技能被下线后会保留绑定关系（显示「已下线」），但不会在运行时被加载；需要时可手动移除。
      </div>
    </div>

    <ElButton :loading="saving" type="primary" @click="save">保存技能配置</ElButton>
  </div>
</template>
