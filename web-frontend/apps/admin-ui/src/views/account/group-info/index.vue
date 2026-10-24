<script lang="ts" setup>
import { useVbenDrawer } from '@vben/common-ui';
import { Page, useVbenModal } from '@vben/common-ui';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import type { VxeGridProps } from '#/adapter/vxe-table';
import type { VbenFormProps } from '@vben/common-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { getGroupInfoPageApi, toggleStatusGroupInfoApi } from '#/api';

import GroupDetailDrawer from './group-detail-drawer.vue';
import AssignAgentForm from './assign-agent-form.vue';
import AssignKbaseForm from './assign-kbase-form.vue';
import AssignMcpForm from './assign-mcp-form.vue';
import AssignSkillForm from './assign-skill-form.vue';
import AssignPeopleForm from './assign-people-form.vue';
import Form from './form.vue';
import { useColumns, useSearchFormSchema } from './data';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [AssignPeopleModal, assignPeopleModalApi] = useVbenModal({
  connectedComponent: AssignPeopleForm,
  destroyOnClose: true,
});
const [AssignAgentModal, assignAgentModalApi] = useVbenModal({
  connectedComponent: AssignAgentForm,
  destroyOnClose: true,
});
const [AssignKbaseModal, assignKbaseModalApi] = useVbenModal({
  connectedComponent: AssignKbaseForm,
});
// R-04（T-09）：补齐技能与 MCP 的组侧授权入口 —— 此前组管理页只能管人员/智能体/知识库
const [AssignSkillModal, assignSkillModalApi] = useVbenModal({
  connectedComponent: AssignSkillForm,
  destroyOnClose: true,
});
const [AssignMcpModal, assignMcpModalApi] = useVbenModal({
  connectedComponent: AssignMcpForm,
  destroyOnClose: true,
});

const formOptions: VbenFormProps = {
  showCollapseButton: false,
  submitOnEnter: true,
  commonConfig: { labelWidth: 60 },
  wrapperClass: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  actionWrapperClass: 'pl-2 !justify-end md:!justify-start',
  actionPosition: 'left',
  actionLayout: 'inline',
  submitButtonOptions: { content: '查询' },
  resetButtonOptions: { plain: true },
  schema: useSearchFormSchema(),
};

const gridOptions: VxeGridProps = {
  columns: useColumns(),
  columnConfig: { resizable: true },
  height: 'auto',
  keepSource: true,
  border: false,
  stripe: true,
  showOverflow: true,
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        const res = (await getGroupInfoPageApi(
          page.currentPage,
          page.pageSize,
          formValues,
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
const [, detailDrawerApi] = useVbenDrawer({ connectedComponent: true });

function onCreate() {
  formModalApi.setData({}).open();
}

function onEdit(row: any) {
  formModalApi.setData({ ...row }).open();
}

function refreshGrid() {
  gridApi.query();
}

function onToggleStatus(row: any) {
  // 组(group)域语义：0=启用、1=禁用（后端 GroupInfoServiceImpl:37 只认 0 为有效组）。
  // ⚠️ 与账号(account)域相反（account 的 1=启用）——双账号体系历史包袱，见 BL-05，勿"顺手统一"。
  const isEnabled = Number(row.status) === 0;
  ElMessageBox.confirm(
    `确定要${isEnabled ? '禁用' : '启用'}组 "${row.name}" 吗？`,
    isEnabled ? '禁用' : '启用',
    {
      confirmButtonText: isEnabled ? '确定禁用' : '确定启用',
      cancelButtonText: '取消',
      confirmButtonType: isEnabled ? 'danger' : 'primary',
    },
  )
    .then(() => {
      toggleStatusGroupInfoApi(row.id).then(() => {
        ElMessage.success(`组${isEnabled ? '禁用' : '启用'}成功`);
        refreshGrid();
      });
    })
    .catch(() => {});
}

/** T-05：详情抽屉内的分配入口 → 复用既有 assign-*-form 弹窗 */
function onDetailAssign(domain: string, row: any) {
  const data = { ...row };
  if (domain === 'people') assignPeopleModalApi.setData(data).open();
  else if (domain === 'agent') assignAgentModalApi.setData(data).open();
  else if (domain === 'kbase') assignKbaseModalApi.setData(data).open();
  else if (domain === 'skill') assignSkillModalApi.setData(data).open();
  else if (domain === 'mcp') assignMcpModalApi.setData(data).open();
}

function getActions(row: any) {
  const isEnabled = row.status === 0;
  // R-01/R-02（group-management-ui T-04）：行内只保留 3 个动作。
  // 原「分配人员/智能体/知识库/技能/MCP」全部迁入「查看详情」抽屉，避免动作过多把状态切换挤出可视区。
  return [
    {
      text: '查看详情',
      icon: 'lucide:eye',
      onClick: () => detailDrawerApi.setData({ ...row }).open(),
    },
    {
      text: '编辑',
      icon: 'lucide:edit',
      onClick: () => onEdit(row),
    },
    {
      text: isEnabled ? '禁用' : '启用',
      icon: isEnabled ? 'lucide:ban' : 'lucide:check-circle',
      danger: isEnabled,
      popConfirm: {
        title: `确定要${isEnabled ? '禁用' : '启用'}组【${row.name}】吗？`,
        confirm: () => onToggleStatus(row),
        okText: '确定',
        cancelText: '取消',
      },
    },
  ];
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="refreshGrid" />
    <AssignPeopleModal @success="refreshGrid" />
    <AssignAgentModal @success="refreshGrid" />
    <AssignKbaseModal @success="refreshGrid" />
    <AssignSkillModal @success="refreshGrid" />
    <AssignMcpModal @success="refreshGrid" />
    <Grid table-title="组管理">
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">新增</ElButton>
      </template>
      <template #statusSlot="{ row }">
        <ElTag
          :type="row.status === 0 || row.status === '0' ? 'success' : 'danger'"
          size="small"
        >
          {{ row.status === 0 || row.status === '0' ? '启用' : '禁用' }}
        </ElTag>
      </template>
      <template #action="{ row }">
        <VbenTableAction align="center" :actions="getActions(row)" />
      </template>
    </Grid>
  </Page>
</template>
