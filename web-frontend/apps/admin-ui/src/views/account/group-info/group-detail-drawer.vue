<script setup lang="ts">
import { computed, ref } from 'vue';
import { useVbenDrawer } from '@vben/common-ui';
import { ElButton, ElDescriptions, ElDescriptionsItem, ElTable, ElTableColumn, ElTag } from 'element-plus';
import { getGroupMemberPageApi } from '#/api/core/platform-account-group-info';
import { getGroupAgentInfoByGroupApi } from '#/api/core/platform-group-agent-info';
import { groupKbasesApi } from '#/api/core/knowledgeBase';
import { groupSkillsApi } from '#/api/core/group-skill';
import { groupMcpsApi } from '#/api/core/group-mcp';

defineOptions({ name: 'GroupDetailDrawer' });
const emit = defineEmits(['success']);

const group = ref<any>({});
const members = ref<any[]>([]);
const memberTotal = ref(0);
const memberPage = ref(1);
const memberSize = ref(20);
const counts = ref({ agent: 0, kbase: 0, skill: 0, mcp: 0 });
const loading = ref(false);

const [Drawer, drawerApi] = useVbenDrawer({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    group.value = drawerApi.getData<any>() || {};
    memberPage.value = 1;
    void loadAll();
  },
});

async function loadMembers() {
  const res: any = await getGroupMemberPageApi(group.value.id, memberPage.value, memberSize.value);
  const page = res?.data || res;
  members.value = page?.records || [];
  memberTotal.value = page?.totalRow || 0;
}

async function loadCounts() {
  const gid = group.value.id;
  const [agentRes, kbaseRes, skillRes, mcpRes] = await Promise.all([
    getGroupAgentInfoByGroupApi(gid).catch(() => null),
    groupKbasesApi(gid).catch(() => null),
    groupSkillsApi(gid).catch(() => null),
    groupMcpsApi(gid).catch(() => null),
  ]);
  const len = (res: any) => (Array.isArray(res?.data) ? res.data.length : (Array.isArray(res) ? res.length : 0));
  counts.value = { agent: len(agentRes), kbase: len(kbaseRes), skill: len(skillRes), mcp: len(mcpRes) };
}

async function loadAll() {
  loading.value = true;
  try { await Promise.all([loadMembers(), loadCounts()]); } finally { loading.value = false; }
}

const statusText = computed(() => (Number(group.value.status) === 0 ? '启用' : '禁用'));
</script>

<template>
  <Drawer :title="`组详情 - ${group.name || ''}`" class="w-[720px]">
    <div class="space-y-4">
      <ElDescriptions :column="2" border size="small">
        <ElDescriptionsItem label="组名称">{{ group.name }}</ElDescriptionsItem>
        <ElDescriptionsItem label="组编码">{{ group.sn }}</ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="Number(group.status) === 0 ? 'success' : 'danger'">{{ statusText }}</ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="描述" :span="2">{{ group.description || '-' }}</ElDescriptionsItem>
      </ElDescriptions>

      <div class="flex gap-2">
        <ElTag>人员 {{ memberTotal }}</ElTag>
        <ElTag type="info">智能体 {{ counts.agent }}</ElTag>
        <ElTag type="info">知识库 {{ counts.kbase }}</ElTag>
        <ElTag type="info">技能 {{ counts.skill }}</ElTag>
        <ElTag type="info">MCP {{ counts.mcp }}</ElTag>
      </div>

      <ElTable v-loading="loading" :data="members" size="small" border>
        <ElTableColumn prop="username" label="账号" width="160" />
        <ElTableColumn prop="realName" label="姓名" width="140" />
        <ElTableColumn prop="accountStatus" label="账号状态" width="100" />
        <ElTableColumn prop="joinTime" label="加入时间" width="170" />
      </ElTable>
      <div class="flex justify-end">
        <ElButton :disabled="memberPage <= 1" size="small" @click="memberPage--; loadMembers()">上一页</ElButton>
        <ElButton :disabled="memberPage * memberSize >= memberTotal" size="small" @click="memberPage++; loadMembers()">下一页</ElButton>
      </div>
    </div>
  </Drawer>
</template>
