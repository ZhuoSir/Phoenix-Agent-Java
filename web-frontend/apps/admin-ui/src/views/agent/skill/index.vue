<script lang="ts" setup>
import type { SkillDetail, SkillItem } from '#/api/core/skill';

import { onMounted, reactive, ref } from 'vue';

import { getGroupInfoPageApi } from '#/api';
import {
  deleteSkillApi,
  getSkillDetailApi,
  getSkillGroupsApi,
  getSkillPageApi,
  getSkillRefsApi,
  offlineSkillApi,
  publishSkillApi,
  updateSkillGroupsApi,
  uploadSkillApi,
} from '#/api/core/skill';
import {
  ElButton,
  ElCard,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload,
} from 'element-plus';

const loading = ref(false);
const rows = ref<SkillItem[]>([]);
const total = ref(0);
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10, status: '' });

async function load() {
  loading.value = true;
  try {
    const res = await getSkillPageApi(query.pageNum, query.pageSize, {
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    });
    rows.value = res.data?.records ?? [];
    total.value = res.data?.totalRow ?? 0;
  } finally {
    loading.value = false;
  }
}

function statusTag(status: string) {
  return status === 'published' ? { text: '已发布', type: 'success' } : { text: '草稿', type: 'info' };
}

function search() {
  query.pageNum = 1;
  void load();
}

onMounted(load);

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false);
const detail = ref<null | SkillDetail>(null);

async function openDetail(row: SkillItem) {
  const res = await getSkillDetailApi(row.id);
  if (!res.success) {
    ElMessage.error(res.msg || '详情加载失败');
    return;
  }
  detail.value = res.data;
  detailVisible.value = true;
}

/* ---------------- 上传 ---------------- */
const uploadVisible = ref(false);
const uploading = ref(false);
const overwrite = ref(false);
const uploadFile = ref<File | null>(null);

function openUpload() {
  uploadFile.value = null;
  overwrite.value = false;
  uploadVisible.value = true;
}

function onFileChange(file: any) {
  uploadFile.value = file.raw as File;
}

async function submitUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择技能 ZIP 包');
    return;
  }
  uploading.value = true;
  try {
    const res = await uploadSkillApi(uploadFile.value, overwrite.value);
    if (!res.success) {
      // 同名冲突（41004）提示后由用户勾选覆盖重试
      ElMessage.error(res.msg || '上传失败');
      return;
    }
    ElMessage.success('上传成功，技能已入库为草稿');
    uploadVisible.value = false;
    void load();
  } finally {
    uploading.value = false;
  }
}

/* ---------------- 发布 / 授权 ---------------- */
const publishVisible = ref(false);
const publishMode = ref<'groups' | 'publish'>('publish');
const publishTarget = ref<null | SkillItem>(null);
const groupOptions = ref<any[]>([]);
const selectedGroupIds = ref<string[]>([]);

async function loadGroups() {
  const res = await getGroupInfoPageApi(1, 200);
  groupOptions.value = (res as any)?.data?.records ?? (res as any)?.records ?? [];
}

async function openPublish(row: SkillItem) {
  publishTarget.value = row;
  publishMode.value = 'publish';
  selectedGroupIds.value = [];
  await loadGroups();
  publishVisible.value = true;
}

async function openGroups(row: SkillItem) {
  publishTarget.value = row;
  publishMode.value = 'groups';
  const res = await getSkillGroupsApi(row.id);
  selectedGroupIds.value = res.success ? (res.data ?? []) : [];
  await loadGroups();
  publishVisible.value = true;
}

async function submitPublish() {
  const target = publishTarget.value;
  if (!target) return;
  const res =
    publishMode.value === 'publish'
      ? await publishSkillApi(target.id, selectedGroupIds.value)
      : await updateSkillGroupsApi(target.id, selectedGroupIds.value);
  if (!res.success) {
    ElMessage.error(res.msg || '操作失败');
    return;
  }
  ElMessage.success(publishMode.value === 'publish' ? '发布成功' : '授权已更新');
  publishVisible.value = false;
  void load();
}

async function offline(row: SkillItem) {
  const res = await offlineSkillApi(row.id);
  if (!res.success) {
    ElMessage.error(res.msg || '下线失败');
    return;
  }
  ElMessage.success('已下线');
  void load();
}

/* ---------------- 删除 ---------------- */
async function remove(row: SkillItem) {
  const refs = await getSkillRefsApi(row.id);
  const bound = refs.data?.boundAgentCount ?? 0;
  const granted = refs.data?.authorizedGroupCount ?? 0;
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复。当前该技能被 ${bound} 个智能体绑定、授权给 ${granted} 个组，删除将级联清理。确认删除「${row.name}」？`,
      '删除技能',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  const res = await deleteSkillApi(row.id);
  if (!res.success) {
    ElMessage.error(res.msg || '删除失败');
    return;
  }
  ElMessage.success('已删除');
  void load();
}
</script>

<template>
  <div class="p-4">
    <ElCard shadow="never">
      <div class="mb-3 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <ElInput
            v-model="query.keyword"
            class="w-[240px]"
            clearable
            placeholder="搜索技能名称/描述"
            @keyup.enter="search"
            @clear="search"
          />
          <ElSelect v-model="query.status" class="w-[140px]" clearable placeholder="状态" @change="search">
            <ElOption label="草稿" value="draft" />
            <ElOption label="已发布" value="published" />
          </ElSelect>
          <ElButton type="primary" @click="search">查询</ElButton>
        </div>
        <ElButton type="primary" @click="openUpload">上传技能包</ElButton>
      </div>

      <ElTable v-loading="loading" :data="rows" border stripe>
        <ElTableColumn label="名称" min-width="160" prop="name" />
        <ElTableColumn label="描述" min-width="240" prop="description" show-overflow-tooltip />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="statusTag(row.status).type as any">{{ statusTag(row.status).text }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="来源" prop="source" width="120" />
        <ElTableColumn label="更新时间" prop="updatedAt" width="200" />
        <ElTableColumn fixed="right" label="操作" width="300">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
            <ElButton v-if="row.status !== 'published'" link type="success" @click="openPublish(row)">
              发布
            </ElButton>
            <template v-else>
              <ElButton link type="warning" @click="openGroups(row)">授权</ElButton>
              <ElButton link type="info" @click="offline(row)">下线</ElButton>
            </template>
            <ElButton link type="danger" @click="remove(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-3 flex justify-end">
        <ElPagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </ElCard>

    <!-- 详情 -->
    <ElDrawer v-model="detailVisible" size="50%" title="技能详情">
      <template v-if="detail">
        <div class="mb-2 font-medium">
          {{ detail.name }}
          <ElTag class="ml-2" :type="statusTag(detail.status).type as any">
            {{ statusTag(detail.status).text }}
          </ElTag>
        </div>
        <div class="mb-3 text-sm text-gray-500">{{ detail.description }}</div>
        <div class="mb-1 text-sm font-medium">SKILL.md</div>
        <pre class="mb-4 max-h-[360px] overflow-auto rounded bg-gray-50 p-3 text-xs">{{ detail.skillContent }}</pre>
        <div class="mb-1 text-sm font-medium">资源文件（{{ detail.resources?.length ?? 0 }}）</div>
        <ElTable :data="detail.resources ?? []" border size="small">
          <ElTableColumn label="路径" prop="path" width="220" />
          <ElTableColumn label="内容预览" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-xs">{{ (row.content || '').slice(0, 120) }}</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </template>
    </ElDrawer>

    <!-- 上传 -->
    <ElDialog v-model="uploadVisible" title="上传技能包" width="520px">
      <ElUpload :auto-upload="false" :limit="1" accept=".zip" drag @change="onFileChange">
        <div class="p-4 text-sm">将技能 ZIP 包拖到此处，或点击选择（包内需含 SKILL.md）</div>
      </ElUpload>
      <div class="mt-3 flex items-center gap-2">
        <ElSwitch v-model="overwrite" />
        <span class="text-sm">同名技能覆盖更新（覆盖后回到草稿态）</span>
      </div>
      <template #footer>
        <ElButton @click="uploadVisible = false">取消</ElButton>
        <ElButton :loading="uploading" type="primary" @click="submitUpload">上传</ElButton>
      </template>
    </ElDialog>

    <!-- 发布 / 授权 -->
    <ElDialog
      v-model="publishVisible"
      :title="publishMode === 'publish' ? '发布技能并授权给组' : '调整授权组（即时生效）'"
      width="520px"
    >
      <ElForm label-width="90px">
        <ElFormItem label="授权组">
          <ElSelect v-model="selectedGroupIds" class="w-full" filterable multiple placeholder="可留空=仅后台可见">
            <ElOption
              v-for="g in groupOptions"
              :key="g.id"
              :label="g.name"
              :value="g.id"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="publishVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitPublish">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
