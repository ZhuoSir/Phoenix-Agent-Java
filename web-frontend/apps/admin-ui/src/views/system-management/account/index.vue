<script lang="ts" setup>
import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import type { VxeGridProps } from '#/adapter/vxe-table';
import type { VbenFormProps } from '@vben/common-ui';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import {
  batchUpdateUserStatusApi,
  deleteUserApi,
  getUserPageApi,
  updateUserStatusApi,
} from '#/api';

import Form from './form.vue';
import PasswordForm from './password-form.vue';
import { useColumns, useSearchFormSchema } from './data';

const formOptions: VbenFormProps = {
  showCollapseButton: false,
  submitOnEnter: true,
  commonConfig: { labelWidth: 60 },
  wrapperClass: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
  actionWrapperClass: 'pl-2 !justify-end md:!justify-start',
  actionPosition: 'left',
  actionLayout: 'inline',
  submitButtonOptions: { content: '查询' },
  resetButtonOptions: { plain: true },
  schema: useSearchFormSchema(),
};

const gridOptions: VxeGridProps = {
  columns: useColumns(),
  // R-15（v2.4.0）：勾选行用于批量启用/禁用（工具栏按钮按勾选数启用）
  checkboxConfig: {
    highlight: true,
  },
  columnConfig: { resizable: true },
  height: 'auto',
  keepSource: true,
  border: false,
  stripe: true,
  showOverflow: true,
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        const params: any = { ...formValues };
        const res = (await getUserPageApi(
          page.currentPage,
          page.pageSize,
          params,
        )) as any;
        const data = res?.data || res;
        return { records: data?.records || [], totalRow: data?.totalRow || 0 };
      },
    },
  },
  pagerConfig: {
    pageSize: 10,
    pageSizes: [10, 20, 50, 100],
  },
};

const [Grid, gridApi] = useVbenVxeGrid({ formOptions, gridOptions });
const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [PasswordModal, passwordModalApi] = useVbenModal({
  connectedComponent: PasswordForm,
  destroyOnClose: true,
});

// R-15（v2.4.0）：勾选数（工具栏批量按钮的可用性）
const selectedCount = ref(0);

function syncSelectedCount() {
  selectedCount.value = (gridApi.grid?.getCheckboxRecords?.() || []).length;
}

function selectedIds(): string[] {
  return (gridApi.grid?.getCheckboxRecords?.() || [])
    .map((r: any) => r.id)
    .filter(Boolean);
}

function clearSelection() {
  gridApi.grid?.clearCheckboxRow?.();
  syncSelectedCount();
}

function onCreate() {
  formModalApi.setData({}).open();
}

function onEdit(row: any) {
  formModalApi.setData({ ...row }).open();
}

function onDelete(row: any) {
  ElMessageBox.confirm(
    `确定要删除账号 "${row.username}" 吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonType: 'danger',
    },
  )
    .then(() => {
      deleteUserApi(row.id).then(() => {
        ElMessage.success('删除成功');
        gridApi.query();
      });
    })
    .catch(() => {});
}

function handleSetPassword(row: any) {
  passwordModalApi.setData({ id: row.id }).open();
}

/** R-15：行级启用/禁用（status 0 启用 / 1 禁用，切换为当前状态的反面） */
function onToggleStatus(row: any) {
  const target = row.status === 0 ? 1 : 0;
  const action = target === 1 ? '禁用' : '启用';
  ElMessageBox.confirm(
    `确定要${action}账号 "${row.username}" 吗？${target === 1 ? '禁用后该账号将无法登录。' : ''}`,
    `${action}确认`,
    { confirmButtonText: `确定${action}`, cancelButtonText: '取消' },
  )
    .then(async () => {
      const res: any = await updateUserStatusApi(row.id, target);
      if (res?.success === false) {
        ElMessage.error(res?.msg || res?.message || `${action}失败`);
        return;
      }
      ElMessage.success(`${action}成功`);
      gridApi.query();
    })
    .catch(() => {});
}

/** R-15：批量启用/禁用（含当前登录账号时后端整体拒绝并提示） */
function onBatchStatus(status: number) {
  const ids = selectedIds();
  if (ids.length === 0) {
    ElMessage.warning('请先勾选账号');
    return;
  }
  const action = status === 1 ? '禁用' : '启用';
  ElMessageBox.confirm(
    `确定要批量${action}选中的 ${ids.length} 个账号吗？${status === 1 ? '禁用后这些账号将无法登录。' : ''}`,
    `批量${action}确认`,
    { confirmButtonText: `确定${action}`, cancelButtonText: '取消' },
  )
    .then(async () => {
      const res: any = await batchUpdateUserStatusApi(ids, status);
      if (res?.success === false) {
        ElMessage.error(res?.msg || res?.message || `批量${action}失败`);
        return;
      }
      ElMessage.success(`批量${action}成功（${res?.data ?? ids.length} 个）`);
      clearSelection();
      gridApi.query();
    })
    .catch(() => {});
}

function getActions(row: any) {
  const isEnabled = row.status === 0;
  return [
    // R-15：启用/禁用按当前状态显示对应动作
    {
      text: isEnabled ? '禁用' : '启用',
      icon: isEnabled ? 'lucide:ban' : 'lucide:circle-check',
      popConfirm: {
        title: `确定要${isEnabled ? '禁用' : '启用'}【${row.username}】吗？`,
        confirm: () => onToggleStatus(row),
        okText: '确定',
        cancelText: '取消',
      },
    },
    {
      text: '设置密码',
      icon: 'lucide:key',
      onClick: () => handleSetPassword(row),
    },
    // R-15：角色/组分配统一走编辑入口（原「分配权限」独立弹窗已移除）
    {
      text: '编辑',
      icon: 'lucide:edit',
      onClick: () => onEdit(row),
    },
    {
      text: '删除',
      icon: 'lucide:trash-2',
      danger: true,
      popConfirm: {
        title: `确定要删除【${row.username}】吗？`,
        confirm: () => onDelete(row),
        okText: '确定',
        cancelText: '取消',
      },
    },
  ];
}

function refreshGrid() {
  // R-15：刷新时清空勾选，避免批量按钮显示过期数量
  clearSelection();
  gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="refreshGrid" />
    <PasswordModal @success="refreshGrid" />
    <Grid
      table-title="账号列表"
      @checkbox-all="syncSelectedCount"
      @checkbox-change="syncSelectedCount"
    >
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">新增</ElButton>
        <!-- R-15（v2.4.0）：批量启用/禁用，未勾选时禁用 -->
        <ElButton
          :disabled="selectedCount === 0"
          @click="onBatchStatus(0)"
        >
          批量启用{{ selectedCount > 0 ? `(${selectedCount})` : '' }}
        </ElButton>
        <ElButton
          :disabled="selectedCount === 0"
          type="danger"
          plain
          @click="onBatchStatus(1)"
        >
          批量禁用{{ selectedCount > 0 ? `(${selectedCount})` : '' }}
        </ElButton>
      </template>
      <template #statusSlot="{ row }">
        <ElTag
          :type="row.status === 0 ? 'success' : 'danger'"
          size="small"
        >
          {{ row.status === 0 ? '启用' : '禁用' }}
        </ElTag>
      </template>
      <template #rolesSlot="{ row }">
        <span>{{
          (row.roles || []).map((r: any) => r.name).join('、')
        }}</span>
      </template>
      <template #action="{ row }">
        <VbenTableAction align="center" :actions="getActions(row)" />
      </template>
    </Grid>
  </Page>
</template>
