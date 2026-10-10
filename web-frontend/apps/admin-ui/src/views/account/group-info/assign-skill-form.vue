<script lang="ts" setup>
/**
 * 组×技能分配（T-09 / R-04）：多选表格 + 全量替换提交。
 * 同构 assign-kbase-form.vue；补齐前组管理页无法管理技能授权（只有资源侧单向入口）。
 */
import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage, ElTable, ElTableColumn } from 'element-plus';

import { groupSkillAssignApi, groupSkillsApi } from '#/api/core/group-skill';
import { getSkillPageApi, type SkillItem } from '#/api/core/skill';

defineOptions({ name: 'AssignSkillForm' });

const emit = defineEmits(['success']);

const assignLoading = ref(false);
const allSkills = ref<SkillItem[]>([]);
const selectedIds = ref<number[]>([]);
const tableRef = ref();

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const groupData = modalApi.getData<any>();
    if (!groupData) return;
    assignLoading.value = true;
    try {
      const res = await groupSkillAssignApi(groupData.id, selectedIds.value);
      if (res.success === false) {
        ElMessage.error('技能分配失败');
        return;
      }
      ElMessage.success('技能分配成功');
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
        getSkillPageApi(1, 999),
        groupSkillsApi(groupData.id),
      ]);
      allSkills.value = pageRes.data?.records ?? [];
      selectedIds.value = [...assigned];
      await nextTick();
      if (tableRef.value) {
        tableRef.value.clearSelection();
        allSkills.value.forEach((s) => {
          if (assigned.includes(s.id)) tableRef.value.toggleRowSelection(s, true);
        });
      }
    } catch {
      ElMessage.error('加载技能列表失败');
    } finally {
      assignLoading.value = false;
    }
  },
});

function onSelectionChange(rows: SkillItem[]) {
  selectedIds.value = rows.map((r) => r.id);
}
</script>

<template>
  <Modal title="分配技能" class="w-[560px]">
    <ElTable
      ref="tableRef"
      v-loading="assignLoading"
      :data="allSkills"
      max-height="420"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn type="selection" width="46" />
      <ElTableColumn label="名称" prop="name" />
      <ElTableColumn label="描述" prop="description" show-overflow-tooltip />
      <ElTableColumn label="状态" width="80">
        <template #default="{ row }">
          <span :style="{ color: row.status === 1 ? '#67c23a' : '#909399' }">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </span>
        </template>
      </ElTableColumn>
    </ElTable>
  </Modal>
</template>
