<script lang="ts" setup>
import type { McpOption } from '#/api/core/mcp';

import { onMounted, ref } from 'vue';

import {
  bindAgentMcpsApi,
  getAgentBoundMcpsApi,
  getMcpOptionsApi,
} from '#/api/core/mcp';
import { ElAlert, ElButton, ElMessage, ElOption, ElSelect } from 'element-plus';

const props = defineProps<{ agentId: number }>();

const options = ref<McpOption[]>([]);
const selected = ref<string[]>([]);
const loading = ref(false);
const saving = ref(false);

async function load() {
  if (!props.agentId) {
    return;
  }
  loading.value = true;
  try {
    const [optionRes, boundRes] = await Promise.all([
      getMcpOptionsApi(props.agentId),
      getAgentBoundMcpsApi(props.agentId),
    ]);
    options.value = (optionRes as any).success ? ((optionRes as any).data ?? []) : [];
    selected.value = (boundRes as any).success ? ((boundRes as any).data ?? []) : [];
  } finally {
    loading.value = false;
  }
}

function optionLabel(option: McpOption) {
  return option.status === 'enabled' ? option.name : `${option.name}（已停用）`;
}

/** 已停用且未绑定的 MCP 不可新选；已绑定的停用项保留（可手动解绑）——镜像技能语义 */
function optionDisabled(option: McpOption) {
  return option.status !== 'enabled' && !option.bound;
}

async function save() {
  saving.value = true;
  try {
    const res = await bindAgentMcpsApi(props.agentId, selected.value);
    if (!(res as any).success) {
      ElMessage.error((res as any).msg || 'MCP 配置保存失败');
      return;
    }
    ElMessage.success('MCP 配置已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h3 class="m-0 mb-4 text-base font-semibold">MCP 工具配置</h3>

    <ElAlert
      :closable="false"
      class="mb-4"
      show-icon
      title="绑定的 MCP 工具在对话运行时挂载（至迟下一轮生效）；前台用户还需其所属组获得该 MCP 授权才可用（与技能同构）"
      type="info"
    />

    <div class="mb-5">
      <label class="mb-2 block text-sm font-medium text-gray-700">
        可选 MCP（仅「启用」状态可新绑定）
      </label>
      <ElSelect
        v-model="selected"
        class="w-full"
        collapse-tags
        collapse-tags-tooltip
        filterable
        multiple
        :loading="loading"
        placeholder="选择该智能体可挂载的 MCP Server，可多选"
      >
        <ElOption
          v-for="option in options"
          :key="option.id"
          :disabled="optionDisabled(option)"
          :label="optionLabel(option)"
          :value="option.id"
        />
      </ElSelect>
      <div class="mt-2 text-xs text-gray-500">
        已绑定的 MCP 被停用后保留绑定关系（显示「已停用」），运行时不挂载；需要时可手动移除。
      </div>
    </div>

    <ElButton :loading="saving" type="primary" @click="save">保存 MCP 配置</ElButton>
  </div>
</template>
