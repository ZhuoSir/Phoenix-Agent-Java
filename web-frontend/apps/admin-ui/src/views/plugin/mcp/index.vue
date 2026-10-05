<script lang="ts" setup>
import type { McpDetailVO, McpItem, McpTestResult } from '#/api/core/mcp';

import { onMounted, reactive, ref } from 'vue';

import { getGroupInfoPageApi } from '#/api';
import {
  deleteMcpApi,
  getMcpDetailApi,
  getMcpPageApi,
  grantMcpGroupsApi,
  saveMcpApi,
  testMcpApi,
  toggleMcpStatusApi,
} from '#/api/core/mcp';
import {
  ElAlert,
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
} from 'element-plus';

const loading = ref(false);
const rows = ref<McpItem[]>([]);
const total = ref(0);
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 });

async function load() {
  loading.value = true;
  try {
    const res = await getMcpPageApi(query.pageNum, query.pageSize, {
      keyword: query.keyword || undefined,
    });
    rows.value = (res as any)?.data?.records ?? (res as any)?.records ?? [];
    total.value = (res as any)?.data?.totalRow ?? (res as any)?.totalRow ?? 0;
  } finally {
    loading.value = false;
  }
}

const TRANSPORTS = [
  { label: 'SSE', value: 'sse' },
  { label: 'Streamable HTTP', value: 'streamable_http' },
  { label: 'HTTP', value: 'http' },
  { label: 'stdio（本地命令）', value: 'stdio' },
];
function transportLabel(t: string) {
  return TRANSPORTS.find((x) => x.value === t)?.label ?? t;
}

/* ---------------- 编辑抽屉 ---------------- */
const editVisible = ref(false);
const editSaving = ref(false);
const editForm = reactive({
  argsText: '',
  command: '',
  description: '',
  enableToolsText: '',
  id: '',
  initTimeoutMs: 15_000,
  name: '',
  timeoutMs: 30_000,
  transport: 'sse',
  url: '',
});
const headersKv = ref<{ key: string; value: string }[]>([]);
const envKv = ref<{ key: string; value: string }[]>([]);

function resetForm() {
  editForm.id = '';
  editForm.name = '';
  editForm.transport = 'sse';
  editForm.description = '';
  editForm.url = '';
  editForm.command = '';
  editForm.argsText = '';
  editForm.enableToolsText = '';
  editForm.timeoutMs = 30_000;
  editForm.initTimeoutMs = 15_000;
  headersKv.value = [];
  envKv.value = [];
  testResult.value = null;
}

function openCreate() {
  resetForm();
  editVisible.value = true;
}

async function openEdit(row: any) {
  resetForm();
  const res = await getMcpDetailApi(row.id);
  const d: McpDetailVO = (res as any)?.data;
  if (!d) {
    ElMessage.error((res as any)?.msg || '详情加载失败');
    return;
  }
  editForm.id = d.id;
  editForm.name = d.name;
  editForm.transport = d.transport;
  editForm.description = d.description ?? '';
  editForm.url = d.url ?? '';
  editForm.command = d.command ?? '';
  editForm.argsText = (d.args ?? []).join('\n');
  editForm.enableToolsText = (d.enableTools ?? []).join(',');
  editForm.timeoutMs = d.timeoutMs ?? 30_000;
  editForm.initTimeoutMs = d.initTimeoutMs ?? 15_000;
  headersKv.value = Object.entries(d.headers ?? {}).map(([key, value]) => ({ key, value }));
  envKv.value = Object.entries(d.env ?? {}).map(([key, value]) => ({ key, value }));
  editVisible.value = true;
}

function buildPayload() {
  const headers: Record<string, string> = {};
  for (const kv of headersKv.value) {
    if (kv.key.trim()) headers[kv.key.trim()] = kv.value;
  }
  const env: Record<string, string> = {};
  for (const kv of envKv.value) {
    if (kv.key.trim()) env[kv.key.trim()] = kv.value;
  }
  return {
    args: editForm.argsText.split('\n').map((s) => s.trim()).filter(Boolean),
    command: editForm.command.trim() || undefined,
    description: editForm.description || undefined,
    enableTools: editForm.enableToolsText.split(',').map((s) => s.trim()).filter(Boolean),
    env,
    headers,
    id: editForm.id || undefined,
    initTimeoutMs: editForm.initTimeoutMs,
    name: editForm.name.trim(),
    timeoutMs: editForm.timeoutMs,
    transport: editForm.transport,
    url: editForm.url.trim() || undefined,
  };
}

async function submitEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('名称必填');
    return;
  }
  editSaving.value = true;
  try {
    const res = await saveMcpApi(buildPayload() as any);
    if (!(res as any)?.success) {
      ElMessage.error((res as any)?.msg || '保存失败');
      return;
    }
    ElMessage.success(editForm.id ? '已保存' : '已创建');
    editVisible.value = false;
    void load();
  } finally {
    editSaving.value = false;
  }
}

/* ---------------- 测试连接 ---------------- */
const testing = ref(false);
const testResult = ref<McpTestResult | null>(null);

async function runTest() {
  testing.value = true;
  testResult.value = null;
  try {
    const res = await testMcpApi(buildPayload());
    testResult.value = ((res as any)?.data ?? null) as McpTestResult | null;
    if ((res as any)?.success === false) {
      ElMessage.error((res as any)?.msg || '测试请求失败');
    }
  } catch (error) {
    ElMessage.error(String(error));
  } finally {
    testing.value = false;
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false);
const detail = ref<McpDetailVO | null>(null);

async function openDetail(row: any) {
  const res = await getMcpDetailApi(row.id);
  detail.value = ((res as any)?.data ?? null) as McpDetailVO | null;
  if (!detail.value) {
    ElMessage.error((res as any)?.msg || '详情加载失败');
    return;
  }
  detailVisible.value = true;
}

/* ---------------- 启停 / 删除 ---------------- */
async function toggle(row: any) {
  const next = row.status === 'enabled' ? 'disabled' : 'enabled';
  const res = await toggleMcpStatusApi(row.id, next);
  if (!(res as any)?.success) {
    ElMessage.error((res as any)?.msg || '操作失败');
    return;
  }
  ElMessage.success(next === 'enabled' ? '已启用' : '已停用');
  void load();
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除 MCP「${row.name}」？`, '删除确认', { type: 'warning' });
  } catch {
    return;
  }
  const res = await deleteMcpApi(row.id);
  if (!(res as any)?.success) {
    ElMessage.error((res as any)?.msg || '删除失败');
    return;
  }
  ElMessage.success('已删除');
  void load();
}

/* ---------------- 组授权 ---------------- */
const grantVisible = ref(false);
const grantTarget = ref<McpItem | null>(null);
const selectedGroupIds = ref<string[]>([]);
const groupOptions = ref<any[]>([]);

async function loadGroups() {
  const res = await getGroupInfoPageApi(1, 200);
  groupOptions.value = (res as any)?.data?.records ?? (res as any)?.records ?? [];
}

async function openGrant(row: any) {
  grantTarget.value = row;
  if (groupOptions.value.length === 0) await loadGroups();
  const res = await getMcpDetailApi(row.id);
  selectedGroupIds.value = ((res as any)?.data?.groupIds ?? []) as string[];
  grantVisible.value = true;
}

async function submitGrant() {
  const target = grantTarget.value;
  if (!target) return;
  const res = await grantMcpGroupsApi(target.id, selectedGroupIds.value);
  if (!(res as any)?.success) {
    ElMessage.error((res as any)?.msg || '授权失败');
    return;
  }
  ElMessage.success('授权已更新');
  grantVisible.value = false;
  void load();
}

onMounted(load);
</script>

<template>
  <div class="p-4">
    <ElCard shadow="never">
      <div class="mb-3 flex items-center gap-2">
        <ElInput
          v-model="query.keyword"
          clearable
          placeholder="按名称搜索"
          style="width: 240px"
          @keyup.enter="query.pageNum = 1; load()"
        />
        <ElButton type="primary" @click="query.pageNum = 1; load()">查询</ElButton>
        <div class="flex-1"></div>
        <ElButton type="primary" @click="openCreate">新建 MCP</ElButton>
      </div>

      <ElTable v-loading="loading" :data="rows" border stripe>
        <ElTableColumn label="名称" min-width="150" prop="name" />
        <ElTableColumn label="传输" width="150">
          <template #default="{ row }">
            <ElTag size="small" type="info">{{ transportLabel(row.transport) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElSwitch
              :model-value="row.status === 'enabled'"
              @change="toggle(row)"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="授权组" prop="groupCount" width="80" />
        <ElTableColumn label="绑定智能体" prop="boundCount" width="100" />
        <ElTableColumn label="描述" min-width="160" prop="description" show-overflow-tooltip />
        <ElTableColumn label="更新时间" prop="updateTime" width="170" />
        <ElTableColumn fixed="right" label="操作" width="230">
          <template #default="{ row }">
            <ElButton link size="small" @click="openDetail(row)">详情</ElButton>
            <ElButton link size="small" @click="openEdit(row)">编辑</ElButton>
            <ElButton link size="small" @click="openGrant(row)">授权</ElButton>
            <ElButton link size="small" type="danger" @click="remove(row)">删除</ElButton>
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
          @size-change="query.pageNum = 1; load()"
        />
      </div>
    </ElCard>

    <!-- 编辑/新建抽屉 -->
    <ElDrawer v-model="editVisible" :title="editForm.id ? '编辑 MCP' : '新建 MCP'" size="580px">
      <ElAlert
        v-if="editForm.transport === 'stdio'"
        class="mb-3"
        :closable="false"
        title="stdio 将在后端容器内拉起本地进程（R-05）：仅配置可信命令，操作将留痕（操作人/时间/命令）。"
        type="warning"
      />
      <ElAlert
        v-if="editForm.id"
        class="mb-3"
        :closable="false"
        title="掩码值（含 ****）原样提交即保留原密钥；输入新值即更新。"
        type="info"
      />
      <ElForm label-width="120px">
        <ElFormItem label="名称" required>
          <ElInput v-model="editForm.name" maxlength="64" placeholder="唯一名称" />
        </ElFormItem>
        <ElFormItem label="传输类型" required>
          <ElSelect v-model="editForm.transport" style="width: 100%">
            <ElOption v-for="t in TRANSPORTS" :key="t.value" :label="t.label" :value="t.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="editForm.transport !== 'stdio'" label="URL" required>
          <ElInput v-model="editForm.url" placeholder="http(s)://…/sse 或 /mcp" />
        </ElFormItem>
        <ElFormItem v-if="editForm.transport === 'stdio'" label="命令" required>
          <ElInput v-model="editForm.command" placeholder="如 python3 / node / npx" />
        </ElFormItem>
        <ElFormItem v-if="editForm.transport === 'stdio'" label="参数">
          <ElInput v-model="editForm.argsText" :rows="3" placeholder="每行一个参数" type="textarea" />
        </ElFormItem>
        <ElFormItem v-if="editForm.transport !== 'stdio'" label="鉴权 Headers">
          <div class="w-full">
            <div v-for="(kv, i) in headersKv" :key="i" class="mb-1 flex gap-1">
              <ElInput v-model="kv.key" placeholder="Header 名" style="width: 40%" />
              <ElInput v-model="kv.value" placeholder="值（保存后加密，回显脱敏）" style="width: 50%" />
              <ElButton link type="danger" @click="headersKv.splice(i, 1)">删</ElButton>
            </div>
            <ElButton link @click="headersKv.push({ key: '', value: '' })">+ 添加 Header</ElButton>
          </div>
        </ElFormItem>
        <ElFormItem v-if="editForm.transport === 'stdio'" label="环境变量">
          <div class="w-full">
            <div v-for="(kv, i) in envKv" :key="i" class="mb-1 flex gap-1">
              <ElInput v-model="kv.key" placeholder="变量名" style="width: 40%" />
              <ElInput v-model="kv.value" placeholder="值（保存后加密，回显脱敏）" style="width: 50%" />
              <ElButton link type="danger" @click="envKv.splice(i, 1)">删</ElButton>
            </div>
            <ElButton link @click="envKv.push({ key: '', value: '' })">+ 添加变量</ElButton>
          </div>
        </ElFormItem>
        <ElFormItem label="工具白名单">
          <ElInput v-model="editForm.enableToolsText" placeholder="逗号分隔；留空=挂载全部工具" />
        </ElFormItem>
        <ElFormItem label="调用超时(ms)">
          <ElInput v-model.number="editForm.timeoutMs" type="number" />
        </ElFormItem>
        <ElFormItem label="连接超时(ms)">
          <ElInput v-model.number="editForm.initTimeoutMs" type="number" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput v-model="editForm.description" :rows="2" type="textarea" />
        </ElFormItem>
        <ElFormItem label="测试连接">
          <div class="w-full">
            <ElButton :loading="testing" @click="runTest">按当前表单试连</ElButton>
            <div v-if="testResult" class="mt-2">
              <template v-if="testResult.success">
                <ElTag size="small" type="success">
                  连接成功 · {{ testResult.toolCount }} 个工具 · {{ testResult.elapsedMs }}ms
                </ElTag>
                <div class="mt-1 flex flex-wrap gap-1">
                  <ElTag v-for="n in testResult.toolNames" :key="n" size="small" type="info">{{ n }}</ElTag>
                </div>
              </template>
              <template v-else>
                <ElTag size="small" type="danger">连接失败</ElTag>
                <div class="mt-1 text-xs text-red-500">{{ testResult.error }}</div>
              </template>
            </div>
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton :loading="editSaving" type="primary" @click="submitEdit">保存</ElButton>
      </template>
    </ElDrawer>

    <!-- 详情抽屉 -->
    <ElDrawer v-model="detailVisible" title="MCP 详情" size="520px">
      <template v-if="detail">
        <ElForm label-width="110px">
          <ElFormItem label="名称">{{ detail.name }}</ElFormItem>
          <ElFormItem label="传输类型">{{ transportLabel(detail.transport) }}</ElFormItem>
          <ElFormItem label="状态">
            <ElTag :type="detail.status === 'enabled' ? 'success' : 'info'" size="small">
              {{ detail.status === 'enabled' ? '启用' : '停用' }}
            </ElTag>
          </ElFormItem>
          <ElFormItem v-if="detail.url" label="URL">{{ detail.url }}</ElFormItem>
          <ElFormItem v-if="detail.command" label="命令">
            {{ detail.command }} {{ (detail.args ?? []).join(' ') }}
          </ElFormItem>
          <ElFormItem v-if="detail.headers && Object.keys(detail.headers).length" label="Headers">
            <div v-for="(v, k) in detail.headers" :key="k" class="text-xs">{{ k }}: {{ v }}</div>
          </ElFormItem>
          <ElFormItem v-if="detail.env && Object.keys(detail.env).length" label="环境变量">
            <div v-for="(v, k) in detail.env" :key="k" class="text-xs">{{ k }}={{ v }}</div>
          </ElFormItem>
          <ElFormItem label="工具白名单">
            {{ (detail.enableTools ?? []).length ? detail.enableTools!.join(', ') : '全部' }}
          </ElFormItem>
          <ElFormItem label="超时">
            连接 {{ detail.initTimeoutMs }}ms / 调用 {{ detail.timeoutMs }}ms
          </ElFormItem>
          <ElFormItem label="授权组">{{ (detail.groupIds ?? []).length }} 个</ElFormItem>
          <ElFormItem label="绑定智能体">
            {{ (detail.boundAgentIds ?? []).length ? detail.boundAgentIds!.join(', ') : '无' }}
          </ElFormItem>
          <ElFormItem v-if="detail.description" label="描述">{{ detail.description }}</ElFormItem>
        </ElForm>
      </template>
    </ElDrawer>

    <!-- 组授权弹窗 -->
    <ElDialog v-model="grantVisible" title="授权用户组" width="480px">
      <p class="mb-2 text-xs text-gray-500">
        与智能体/技能授权同构：仅授权组内的前台用户可在对话中使用该 MCP 的工具（未授权=不可见）。
      </p>
      <ElSelect v-model="selectedGroupIds" multiple placeholder="选择用户组" style="width: 100%">
        <ElOption v-for="g in groupOptions" :key="g.id" :label="g.name" :value="g.id!" />
      </ElSelect>
      <template #footer>
        <ElButton @click="grantVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitGrant">保存授权</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
