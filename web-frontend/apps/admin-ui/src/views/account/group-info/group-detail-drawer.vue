<script setup lang="ts">
import { computed, ref } from 'vue';
import { useVbenDrawer } from '@vben/common-ui';
import {
  ElButton, ElDescriptions, ElDescriptionsItem, ElMessage, ElMessageBox,
  ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag,
} from 'element-plus';
import {
  deleteAccountGroupInfoByGroupAndAccountApi,
  getGroupMemberPageApi,
} from '#/api/core/platform-account-group-info';
import { getGroupAgentInfoByGroupApi } from '#/api/core/platform-group-agent-info';
import { groupKbasesApi } from '#/api/core/knowledgeBase';
import { groupSkillsApi } from '#/api/core/group-skill';
import { groupMcpsApi } from '#/api/core/group-mcp';

defineOptions({ name: 'GroupDetailDrawer' });
const emit = defineEmits(['assign']);

const group = ref<any>({});
const members = ref<any[]>([]);
const memberTotal = ref(0);
const memberPage = ref(1);
const memberSize = ref(20);
const agents = ref<any[]>([]);
const kbases = ref<any[]>([]);
const skills = ref<any[]>([]);
const mcps = ref<any[]>([]);
const loading = ref(false);

const [Drawer, drawerApi] = useVbenDrawer({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    group.value = drawerApi.getData<any>() || {};
    memberPage.value = 1;
    void loadAll();
  },
});

const listOf = (res: any) => {
  const data = res?.data ?? res;
  return Array.isArray(data) ? data : (data?.records ?? []);
};

async function loadMembers() {
  const res: any = await getGroupMemberPageApi(group.value.id, memberPage.value, memberSize.value);
  const page = res?.data || res;
  members.value = page?.records || [];
  memberTotal.value = page?.totalRow || 0;
}

async function loadAll() {
  loading.value = true;
  try {
    const gid = group.value.id;
    const [a, k, s, m] = await Promise.all([
      getGroupAgentInfoByGroupApi(gid).catch(() => null),
      groupKbasesApi(gid).catch(() => null),
      groupSkillsApi(gid).catch(() => null),
      groupMcpsApi(gid).catch(() => null),
    ]);
    agents.value = listOf(a); kbases.value = listOf(k); skills.value = listOf(s); mcps.value = listOf(m);
    await loadMembers();
  } finally { loading.value = false; }
}

/** T-06：移除成员（复用既有 DELETE .../group/{g}/account/{a}），二次确认 + 刷新 */
async function removeMember(row: any) {
  try {
    await ElMessageBox.confirm(`确定要把「${row.username || row.accountId}」移出本组吗？`, '移除成员', {
      confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning',
    });
  } catch { return; }
  await deleteAccountGroupInfoByGroupAndAccountApi(group.value.id, row.accountId);
  ElMessage.success('已移除');
  await loadAll();
}

/** 供父组件（分配弹窗成功回调）刷新详情数据 */
/** 父组件调用：传入组行数据并打开（避免父层依赖 connected drawer 的 api 契约） */
async function open(row: any) {
  group.value = row || {};
  memberPage.value = 1;
  drawerApi.setData(row || {}).open();
  await loadAll();
}

defineExpose({ reload: loadAll, open });

function openAssign(domain: string) {
  // 分配能力仍复用现有 assign-*-form 弹窗（plan 决策），由父组件打开
  emit('assign', domain, group.value);
}

const statusText = computed(() => (Number(group.value.status) === 0 ? '启用' : '禁用'));
</script>

<template>
  <Drawer :title="`组详情 - ${group.name || ''}`" class="w-[760px]">
    <div class="space-y-4">
      <ElDescriptions :column="2" border size="small">
        <ElDescriptionsItem label="组名称">{{ group.name }}</ElDescriptionsItem>
        <ElDescriptionsItem label="组编码">{{ group.sn }}</ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="Number(group.status) === 0 ? 'success' : 'danger'">{{ statusText }}</ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="描述">{{ group.description || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">{{ group.createTime }}</ElDescriptionsItem>
        <ElDescriptionsItem label="更新时间">{{ group.updateTime }}</ElDescriptionsItem>
      </ElDescriptions>

      <div class="flex gap-2">
        <ElTag>人员 {{ memberTotal }}</ElTag>
        <ElTag type="info">智能体 {{ agents.length }}</ElTag>
        <ElTag type="info">知识库 {{ kbases.length }}</ElTag>
        <ElTag type="info">技能 {{ skills.length }}</ElTag>
        <ElTag type="info">MCP {{ mcps.length }}</ElTag>
      </div>

      <ElTabs v-loading="loading">
        <ElTabPane label="人员" name="people">
          <div class="mb-2 flex justify-end">
            <ElButton size="small" type="primary" @click="openAssign('people')">添加成员</ElButton>
          </div>
          <ElTable :data="members" size="small" border>
            <ElTableColumn prop="username" label="账号" width="150" />
            <ElTableColumn prop="realName" label="姓名" width="130" />
            <ElTableColumn prop="accountStatus" label="账号状态" width="100" />
            <ElTableColumn prop="joinTime" label="加入时间" width="160" />
            <ElTableColumn label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <ElButton link type="danger" @click="removeMember(row)">移除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <div class="mt-2 flex justify-end gap-2">
            <ElButton :disabled="memberPage <= 1" size="small" @click="memberPage--; loadMembers()">上一页</ElButton>
            <ElButton :disabled="memberPage * memberSize >= memberTotal" size="small" @click="memberPage++; loadMembers()">下一页</ElButton>
          </div>
        </ElTabPane>

        <ElTabPane label="智能体" name="agent">
          <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="openAssign('agent')">分配智能体</ElButton></div>
          <ElTable :data="agents" size="small" border><ElTableColumn prop="agentName" label="名称" /></ElTable>
        </ElTabPane>

        <ElTabPane label="知识库" name="kbase">
          <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="openAssign('kbase')">分配知识库</ElButton></div>
          <ElTable :data="kbases" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
        </ElTabPane>

        <ElTabPane label="技能" name="skill">
          <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="openAssign('skill')">分配技能</ElButton></div>
          <ElTable :data="skills" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
        </ElTabPane>

        <ElTabPane label="MCP" name="mcp">
          <div class="mb-2 flex justify-end"><ElButton size="small" type="primary" @click="openAssign('mcp')">分配 MCP</ElButton></div>
          <ElTable :data="mcps" size="small" border><ElTableColumn prop="name" label="名称" /></ElTable>
        </ElTabPane>
      </ElTabs>
    </div>
  </Drawer>
</template>
