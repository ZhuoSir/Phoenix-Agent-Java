<script lang="ts" setup>
/**
 * 组×知识库分配（T-08，同构 assign-agent）：多选表格 + 全量替换提交。
 */
import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage, ElTable, ElTableColumn } from 'element-plus';

import {
  groupKbaseAssignApi,
  groupKbasesApi,
  kbPageApi,
  type KnowledgeBase,
} from '#/api/core/knowledgeBase';

defineOptions({ name: 'AssignKbaseForm' });

const emit = defineEmits(['success']);

const assignLoading = ref(false);
const allKbases = ref<KnowledgeBase[]>([]);
const selectedIds = ref<number[]>([]);
const tableRef = ref();

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const groupData = modalApi.getData<any>();
    if (!groupData) return;
    assignLoading.value = true;
    try {
      const res = await groupKbaseAssignApi(groupData.id, selectedIds.value);
      if (res.success === false) {
        ElMessage.error('知识库分配失败');
        return;
      }
      ElMessage.success('知识库分配成功');
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
        kbPageApi({ pageNum: 1, pageSize: 999 }),
        groupKbasesApi(groupData.id),
      ]);
      allKbases.value = pageRes.data ?? [];
      selectedIds.value = [...assigned];
      nextTick(() => {
        if (tableRef.value) {
          tableRef.value.clearSelection();
          allKbases.value.forEach((kb) => {
            if (assigned.includes(kb.id)) tableRef.value.toggleRowSelection(kb, true);
          });
        }
      });
    } catch {
      ElMessage.error('加载知识库失败');
    } finally {
      assignLoading.value = false;
    }
  },
});

function onSelectionChange(rows: KnowledgeBase[]) {
  selectedIds.value = rows.map((r) => r.id);
}
</script>

<template>
  <Modal title="分配知识库" class="w-[560px]">
    <ElTable
      ref="tableRef"
      v-loading="assignLoading"
      :data="allKbases"
      max-height="420"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn type="selection" width="46" />
      <ElTableColumn label="名称" prop="name" />
      <ElTableColumn label="条目数" prop="itemCount" width="80" />
      <ElTableColumn label="状态" width="80">
        <template #default="{ row }">
          <span :style="{ color: row.status === 1 ? '#67c23a' : '#909399' }">
            {{ row.status === 1 ? '启用' : '停用' }}
          </span>
        </template>
      </ElTableColumn>
    </ElTable>
  </Modal>
</template>
