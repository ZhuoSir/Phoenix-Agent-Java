<script lang="ts" setup>
import { ref } from 'vue';
import { Page, useVbenDrawer, useVbenModal } from '@vben/common-ui';

import { getGroupMemberPageApi, deleteAccountGroupInfoByGroupAndAccountApi } from '#/api/core/platform-account-group-info';
import { getGroupAgentInfoByGroupApi } from '#/api/core/platform-group-agent-info';
import { groupKbasesApi } from '#/api/core/knowledgeBase';
import { groupSkillsApi } from '#/api/core/group-skill';
import { groupMcpsApi } from '#/api/core/group-mcp';
import { getSkillPageApi } from '#/api/core/skill';
import { getMcpPageApi } from '#/api/core/mcp';
import { kbPageApi } from '#/api/core/knowledgeBase';
import { getGroupAgentInfoListApi } from '#/api/core/platform-account-tenant-info';
import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import type { VxeGridProps } from '#/adapter/vxe-table';
import type { VbenFormProps } from '@vben/common-ui';

import {
  ElButton, ElDescriptions, ElDescriptionsItem, ElDrawer, ElMessage, ElMessageBox,
  ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag,
} from 'element-plus';

import { getGroupInfoPageApi, toggleStatusGroupInfoApi } from '#/api';

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
  stripe: false,
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

// 详情抽屉·人员表：复用同一 useVbenVxeGrid（与列表页同组件 ⇒ 样式统一）
const [MemberGrid, memberGridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'username', title: '账号', width: 200 },
      { field: 'realName', title: '姓名', width: 140 },
      { field: 'accountStatus', title: '账号状态', width: 110 },
      { field: 'joinTime', title: '加入时间', width: 170 },
      { title: '操作', width: 90, slots: { default: 'action' } },
    ],
    loading: false,
    maxHeight: 360,
    border: false,
    stripe: false,
    emptyText: '暂无数据',
    pagerConfig: { enabled: false },
    data: [] as any[],
  },
});


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

// ===== 组详情（内联抽屉，T-05/T-06：不依赖任何跨组件 API）=====
const detailVisible = ref(false);
const detailGroup = ref<any>({});
const detailMembers = ref<any[]>([]);
const detailMemberTotal = ref(0);
const detailPage = ref(1);
const detailSize = ref(20);
const detailAgents = ref<any[]>([]);
const detailKbases = ref<any[]>([]);
const detailSkills = ref<any[]>([]);
const detailMcps = ref<any[]>([]);
const detailLoading = ref(false);
const detailTab = ref('people');

async function openDetail(row: any) {
  detailGroup.value = { ...row };
  detailPage.value = 1;
  detailVisible.value = true;
  await loadDetail();
}

async function loadDetailMembers() {
  const res: any = await getGroupMemberPageApi(detailGroup.value.id, detailPage.value, detailSize.value);
  const page = res?.data || res;
  detailMembers.value = page?.records || [];
  detailMemberTotal.value = page?.totalRow || 0;
  memberGridApi.setGridOptions({ data: detailMembers.value, loading: false });
}

const toList = (res: any) => {
  const data = res?.data ?? res;
  return Array.isArray(data) ? data : (data?.records ?? []);
};

async function loadDetail() {
  detailLoading.value = true;
  try {
    const gid = detailGroup.value.id;
    // 与「分配」弹窗同一套取数方式：先拿已选 id，再拉全量目录映射出真实名称
    const [agentRows, skillIds, kbaseIds, mcpIds, agentCatalog, skillPage, kbasePage, mcpPage] =
      await Promise.all([
        getGroupAgentInfoByGroupApi(gid).catch(() => null),
        groupSkillsApi(gid).catch(() => []),
        groupKbasesApi(gid).catch(() => []),
        groupMcpsApi(gid).catch(() => []),
        getGroupAgentInfoListApi({ status: 'published' }).catch(() => null),
        getSkillPageApi(1, 999).catch(() => null),
        kbPageApi({ pageNum: 1, pageSize: 999 }).catch(() => null),
        getMcpPageApi(1, 999).catch(() => null),
      ]);

    const nameOf = (r: any) =>
      r?.name || r?.agentName || r?.skillName || r?.kbaseName || r?.mcpName || r?.title || '';
    const catalog = (res: any) => {
      const d = res?.data ?? res;
      if (Array.isArray(d)) return d;
      return d?.records ?? [];
    };
    const idsOf = (res: any): any[] => {
      const d = res?.data ?? res;
      return Array.isArray(d) ? d : [];
    };

    // 智能体：组内行有 agentId，用目录补名字
    const agentNameById = new Map<string, string>(
      catalog(agentCatalog).map((r: any) => [String(r.id ?? r.agentId), nameOf(r)]),
    );
    detailAgents.value = (toList(agentRows) as any[]).map((r: any) => ({
      id: r.agentId ?? r.id,
      name: agentNameById.get(String(r.agentId ?? r.id)) || `智能体 ${r.agentId ?? r.id}`,
    }));

    // 技能 / 知识库 / MCP：组内是 id 列表，用目录补给名字
    const mapIds = (ids: any[], cat: any[]) => {
      const byId = new Map<string, any>(cat.map((r: any) => [String(r.id), r]));
      return ids.map((id) => ({ id, name: nameOf(byId.get(String(id))) || `${id}` }));
    };
    detailSkills.value = mapIds(idsOf(skillIds), catalog(skillPage));
    detailKbases.value = mapIds(idsOf(kbaseIds), catalog(kbasePage));
    detailMcps.value = mapIds(idsOf(mcpIds), catalog(mcpPage));

    await loadDetailMembers();
  } finally { detailLoading.value = false; }
}

async function removeDetailMember(row: any) {
  try {
    await ElMessageBox.confirm(`确定把「${row.username || row.accountId}」移出本组？`, '移除成员', {
      confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning',
    });
  } catch { return; }
  await deleteAccountGroupInfoByGroupAndAccountApi(detailGroup.value.id, row.accountId);
  ElMessage.success('已移除');
  await loadDetail();
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
      onClick: () => openDetail(row),
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
    <ElDrawer v-model="detailVisible" :title="`组详情 - ${detailGroup.name || ''}`" size="62%">
      <div class="space-y-4">
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="组名称">{{ detailGroup.name }}</ElDescriptionsItem>
          <ElDescriptionsItem label="组编码">{{ detailGroup.sn }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <ElTag :type="Number(detailGroup.status) === 0 ? 'success' : 'danger'">
              {{ Number(detailGroup.status) === 0 ? '启用' : '禁用' }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="描述">{{ detailGroup.description || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">{{ detailGroup.createTime }}</ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">{{ detailGroup.updateTime }}</ElDescriptionsItem>
        </ElDescriptions>
        <div class="flex gap-2">
          <ElTag>人员 {{ detailMemberTotal }}</ElTag>
          <ElTag type="info">智能体 {{ detailAgents.length }}</ElTag>
          <ElTag type="info">知识库 {{ detailKbases.length }}</ElTag>
          <ElTag type="info">技能 {{ detailSkills.length }}</ElTag>
          <ElTag type="info">MCP {{ detailMcps.length }}</ElTag>
        </div>
        <ElTabs v-model="detailTab" v-loading="detailLoading">
          <ElTabPane label="人员" name="people">
            <div class="mb-2 flex justify-end">
              <ElButton size="small" type="primary" @click="onDetailAssign('people', detailGroup)">添加成员</ElButton>
            </div>
            <div v-if="detailMembers.length">
              <MemberGrid>
                <template #action="{ row }">
                  <ElButton link type="danger" @click="removeDetailMember(row)">移除</ElButton>
                </template>
              </MemberGrid>
            </div>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无数据</div>
            <div class="mt-2 flex justify-end gap-2">
              <ElButton :disabled="detailPage <= 1" size="small" @click="detailPage--; loadDetailMembers()">上一页</ElButton>
              <ElButton :disabled="detailPage * detailSize >= detailMemberTotal" size="small" @click="detailPage++; loadDetailMembers()">下一页</ElButton>
            </div>
          </ElTabPane>
          <ElTabPane label="智能体" name="agent">
            <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="onDetailAssign('agent', detailGroup)">分配智能体</ElButton></div>
            <div v-if="detailAgents.length">
              <ElTable :data="detailAgents" size="small" border><ElTableColumn prop="agentName" label="名称" /></ElTable>
            </div>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无数据</div>
          </ElTabPane>
          <ElTabPane label="知识库" name="kbase">
            <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="onDetailAssign('kbase', detailGroup)">分配知识库</ElButton></div>
            <div v-if="detailKbases.length">
              <ElTable :data="detailKbases" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
            </div>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无数据</div>
          </ElTabPane>
          <ElTabPane label="技能" name="skill">
            <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="onDetailAssign('skill', detailGroup)">分配技能</ElButton></div>
            <div v-if="detailSkills.length">
              <ElTable :data="detailSkills" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
            </div>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无数据</div>
          </ElTabPane>
          <ElTabPane label="MCP" name="mcp">
            <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="onDetailAssign('mcp', detailGroup)">分配 MCP</ElButton></div>
            <div v-if="detailMcps.length">
              <ElTable :data="detailMcps" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
            </div>
            <div v-else class="py-6 text-center text-sm text-gray-400">暂无数据</div>
          </ElTabPane>
        </ElTabs>
      </div>
    </ElDrawer>
    <FormModal @success="refreshGrid" />
    <AssignPeopleModal @success="refreshGrid()" />
    <AssignAgentModal @success="refreshGrid()" />
    <AssignKbaseModal @success="refreshGrid()" />
    <AssignSkillModal @success="refreshGrid()" />
    <AssignMcpModal @success="refreshGrid()" />
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
