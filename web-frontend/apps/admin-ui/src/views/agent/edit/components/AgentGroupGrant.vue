<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { getGroupInfoPageApi } from '#/api';
import { getAgentGroupsApi, updateAgentGroupsApi } from '#/api/core/agent';
import { ElAlert, ElButton, ElMessage, ElOption, ElSelect } from 'element-plus';

const props = defineProps<{ agentId: number; status?: string }>();

const published = computed(() => props.status === 'published');
const groupOptions = ref<any[]>([]);
const selected = ref<string[]>([]);
const loading = ref(false);
const saving = ref(false);

async function load() {
  if (!props.agentId) return;
  loading.value = true;
  try {
    const [g, cur] = await Promise.all([
      getGroupInfoPageApi(1, 200),
      getAgentGroupsApi(props.agentId),
    ]);
    groupOptions.value = (g as any)?.data?.records ?? (g as any)?.records ?? [];
    selected.value = ((cur as any)?.data ?? []) as string[];
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    const res = await updateAgentGroupsApi(props.agentId, selected.value);
    if (!(res as any).success) {
      ElMessage.error((res as any).msg || '授权保存失败');
      return;
    }
    ElMessage.success('授权已更新（覆盖式，即时生效）');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h3 class="m-0 mb-4 text-base font-semibold">授权组</h3>

    <ElAlert
      :closable="false"
      class="mb-4"
      show-icon
      title="无授权组 = 所有前台用户可见（全公开）；有授权组 = 仅授权组内用户可见。组管理侧维护入口并行保留"
      type="info"
    />
    <ElAlert
      v-if="!published"
      :closable="false"
      class="mb-4"
      show-icon
      title="当前非已发布状态：授权随发布动作一并选择（列表「发布」弹窗）；发布后可在此调整"
      type="warning"
    />

    <div class="mb-5">
      <label class="mb-2 block text-sm font-medium text-gray-700">
        授权组（留空 = 全公开）
      </label>
      <ElSelect
        v-model="selected"
        class="w-full"
        collapse-tags
        collapse-tags-tooltip
        filterable
        multiple
        :loading="loading"
        :disabled="!published"
        placeholder="选择授权组；不选则所有前台用户可见"
      >
        <ElOption v-for="g in groupOptions" :key="g.id" :label="g.name" :value="g.id!" />
      </ElSelect>
    </div>

    <ElButton :loading="saving" :disabled="!published" type="primary" @click="save">
      保存授权
    </ElButton>
  </div>
</template>
