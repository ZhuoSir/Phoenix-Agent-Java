<script setup lang="ts">
/**
 * 知识库管理（knowledge-base spec T-07）：与智能体管理平级的独立模块。
 * 数据域 ApiResponse 信封（success/message/data），删除保护 message 自带绑定清单。
 */
import { onMounted, ref } from 'vue';

import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  kbCreateApi,
  kbPageApi,
  kbRemoveApi,
  kbUpdateApi,
  type KnowledgeBase,
} from '#/api/core/knowledgeBase';

import AgentKnowledgeConfig from '#/views/agent/list/components/AgentKnowledgeConfig.vue';

defineOptions({ name: 'KnowledgeBaseManager' });

const loading = ref(false);
const list = ref<KnowledgeBase[]>([]);
const total = ref(0);
const query = ref({ name: '', pageNum: 1, pageSize: 10 });

// 新建/编辑
const dialogVisible = ref(false);
const editId = ref<null | number>(null);
const form = ref<{ description: string; name: string }>({ name: '', description: '' });
const saving = ref(false);

// 条目管理抽屉
const drawerVisible = ref(false);
const activeKb = ref<null | KnowledgeBase>(null);

async function load() {
  loading.value = true;
  try {
    const res = await kbPageApi({ ...query.value });
    list.value = res.data ?? [];
    total.value = res.total ?? 0;
    if (res.success === false && res.message) ElMessage.error(res.message);
  } catch (e: any) {
    ElMessage.error(e?.message || '加载知识库失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editId.value = null;
  form.value = { name: '', description: '' };
  dialogVisible.value = true;
}

function openEdit(row: KnowledgeBase) {
  editId.value = row.id;
  form.value = { name: row.name, description: row.description || '' };
  dialogVisible.value = true;
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入知识库名称');
    return;
  }
  saving.value = true;
  try {
    const res = editId.value == null
      ? await kbCreateApi({ name: form.value.name.trim(), description: form.value.description })
      : await kbUpdateApi({ id: editId.value, ...form.value });
    if (res.success === false) {
      ElMessage.error(res.message || '保存失败');
      return;
    }
    ElMessage.success(editId.value == null ? '创建成功' : '更新成功');
    dialogVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row: KnowledgeBase) {
  const next = row.status === 1 ? 0 : 1;
  try {
    const res = await kbUpdateApi({ id: row.id, status: next });
    if (res.success === false) {
      ElMessage.error(res.message || '状态更新失败');
      row.status = next === 1 ? 0 : 1; // ElSwitch 已翻转，回滚视觉
      return;
    }
    row.status = next;
    ElMessage.success(next === 1 ? '已启用' : '已停用（其召回即时失效）');
  } catch {
    row.status = next === 1 ? 0 : 1;
  }
}

async function remove(row: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${row.name}」？`, '删除确认', { type: 'warning' });
  } catch {
    return;
  }
  const res = await kbRemoveApi(row.id);
  if (res.success === false) {
    // R-04：后端 message 自带绑定智能体清单
    ElMessage.error(res.message || '删除失败');
    return;
  }
  ElMessage.success('已删除');
  await load();
}

function openItems(row: KnowledgeBase) {
  activeKb.value = row;
  drawerVisible.value = true;
}

onMounted(load);
</script>

<template>
  <div class="kb-page">
    <div class="kb-page__toolbar">
      <ElInput
        v-model="query.name"
        class="kb-page__search"
        clearable
        placeholder="按名称搜索"
        @clear="load"
        @keyup.enter="load"
      />
      <ElButton @click="load">搜索</ElButton>
      <span class="kb-page__spacer" />
      <ElButton type="primary" @click="openCreate">新建知识库</ElButton>
    </div>

    <ElTable v-loading="loading" :data="list" border class="kb-page__table">
      <ElTableColumn label="名称" min-width="160" prop="name" />
      <ElTableColumn label="描述" min-width="200" prop="description" show-overflow-tooltip />
      <ElTableColumn label="组授权" min-width="160">
        <template #default="{ row }">
          <ElTag v-for="g in row.groupNames" :key="g" class="kb-page__tag" size="small" type="info">{{ g }}</ElTag>
          <span v-if="!row.groupNames?.length" class="kb-page__none">未分配</span>
        </template>
      </ElTableColumn>
      <ElTableColumn align="center" label="条目数" width="90">
        <template #default="{ row }">{{ row.itemCount }}</template>
      </ElTableColumn>
      <ElTableColumn align="center" label="启用" width="80">
        <template #default="{ row }">
          <ElSwitch :model-value="row.status === 1" @change="toggleStatus(row)" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="更新时间" width="170">
        <template #default="{ row }">{{ (row.updateTime || '').replace('T', ' ').slice(0, 19) }}</template>
      </ElTableColumn>
      <ElTableColumn align="right" label="操作" width="230">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openItems(row)">知识管理</ElButton>
          <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
          <ElButton link type="danger" @click="remove(row)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="kb-page__pager">
      <span class="kb-page__total">共 {{ total }} 条</span>
      <ElButton :disabled="query.pageNum <= 1" size="small" @click="query.pageNum--; load()">上一页</ElButton>
      <span class="kb-page__page">{{ query.pageNum }}</span>
      <ElButton :disabled="query.pageNum * query.pageSize >= total" size="small" @click="query.pageNum++; load()">下一页</ElButton>
    </div>

    <ElDialog v-model="dialogVisible" :title="editId == null ? '新建知识库' : '编辑知识库'" width="440px">
      <ElForm label-width="72px">
        <ElFormItem label="名称" required>
          <ElInput v-model="form.name" maxlength="64" show-word-limit placeholder="不超过 64 字" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput v-model="form.description" :rows="3" maxlength="512" show-word-limit type="textarea" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton :loading="saving" type="primary" @click="save">保存</ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="drawerVisible" :title="`知识管理 · ${activeKb?.name || ''}`" size="72%">
      <AgentKnowledgeConfig v-if="activeKb" :kb-id="activeKb.id" />
    </ElDrawer>
  </div>
</template>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 16px;
}
.kb-page__toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.kb-page__search { max-width: 240px; }
.kb-page__spacer { flex: 1; }
.kb-page__table { flex: 1; }
.kb-page__tag { margin-right: 4px; }
.kb-page__none { font-size: 12px; color: #c0c4cc; }
.kb-page__pager {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}
.kb-page__total,
.kb-page__page { font-size: 13px; color: #606266; }
</style>
