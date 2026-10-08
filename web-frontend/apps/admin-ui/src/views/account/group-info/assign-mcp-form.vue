<script lang="ts" setup>
/**
 * 组×MCP 分配（T-09 / R-04）：多选表格 + 全量替换提交。
 * 同构 assign-kbase-form.vue；补齐前组管理页无法管理 MCP 授权（只有资源侧单向入口）。
 */
import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage, ElTable, ElTableColumn } from 'element-plus';

import { groupMcpAssignApi, groupMcpsApi } from '#/api/core/group-mcp';
import { getMcpPageApi } from '#/api/core/mcp';

defineOptions({ name: 'AssignMcpForm' });

const emit = defineEmits(['success']);

const assignLoading = ref(false);
const allMcps = ref<any[]>([]);
const selectedIds = ref<string[]>([]);
const tableRef = ref();

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const groupData = modalApi.getData<any>();
    if (!groupData) return;
    assignLoading.value = true;
    try {
      const res = await groupMcpAssignApi(groupData.id, selectedIds.value);
      if (res.success === false) {
        ElMessage.error('MCP 分配失败');
        return;
      }
      ElMessage.success('MCP 分配成功');
      modalApi.close();
      emit('success');
    } finally {
      assignLoading.value = false;
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const groupData = modalApi.getData<any>();
    if (!groupData) return;
    assignLoading.value = true;
    try {
      const [pageRes, assigned] = await Promise.all([
        getMcpPageApi(1, 999),
        groupMcpsApi(groupData.id),
      ]);
      const raw: any = pageRes;
      allMcps.value = raw?.data?.records ?? raw?.records ?? [];
      selectedIds.value = [...assigned];
      await nextTick();
      if (tableRef.value) {
        tableRef.value.clearSelection();
        allMcps.value.forEach((m) => {
          if (assigned.includes(m.id)) tableRef.value.toggleRowSelection(m, true);
        });
      }
    } catch {
      ElMessage.error('加载 MCP 列表失败');
    } finally {
      assignLoading.value = false;
    }
  },
});

function onSelectionChange(rows: any[]) {
  selectedIds.value = rows.map((r) => r.id);
}
</script>

<template>
  <Modal title="分配 MCP" class="w-[560px]">
    <ElTable
      ref="tableRef"
      v-loading="assignLoading"
      :data="allMcps"
      max-height="420"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn type="selection" width="46" />
      <ElTableColumn label="名称" prop="name" />
      <ElTableColumn label="类型" prop="type" width="100" />
      <ElTableColumn label="工具数" prop="toolCount" width="80" />
    </ElTable>
  </Modal>
</template>
