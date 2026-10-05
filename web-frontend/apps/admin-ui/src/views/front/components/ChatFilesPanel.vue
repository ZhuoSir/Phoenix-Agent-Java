<script setup lang="ts">
/**
 * 会话文件面板（BL-19 + v1.7.0 R-02 树形）：右侧可折叠抽屉。
 * - **树形浏览**：以会话目录为根，逐层进入/返回（单层懒加载，按 path 拉取），文件夹在前
 * - 文件操作不变：预览 / 下载 / 删除（按 row id）
 * - 历史行（他会话段）单列「历史文件」节点
 * 刷新时机：切换会话、进入/返回目录、SSE agentFiles 事件、物化成功广播。
 */
import { storeToRefs } from '@vben/stores';
import { useChatStore } from '@phoenix/chat-shared';
import { ElButton, ElDrawer, ElIcon, ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import { Back, Clock, Document, Download, Delete, Folder, View } from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  deleteAgentFileApi,
  downloadAgentFileApi,
  FILES_CHANGED_EVENT,
  getAgentFileTreeApi,
  HISTORY_PATH,
  notifyFilesChanged,
  type AgentFileTreeNode,
} from '#/api/core/agentFiles';

const props = withDefaults(defineProps<{ sessionId?: null | string }>(), { sessionId: null });
const chat = useChatStore();
const { activeSessionId: chatStoreSessionId } = storeToRefs(chat);
/** admin 运行页显式传入；前台聊天页缺省用 chat store 的当前会话 */
const activeSessionId = computed(() => props.sessionId ?? chatStoreSessionId.value ?? null);

const drawerVisible = ref(false);
function openDrawer() {
  drawerVisible.value = true;
  refresh();
}
defineExpose({ open: openDrawer });

const loading = ref(false);
const currentPath = ref('');
const parentPath = ref('');
const entries = ref<AgentFileTreeNode[]>([]);
const historyTotal = ref(0);

const isTempSession = computed(() => (activeSessionId.value || '').startsWith('temp-'));
/** 面包屑：会话目录 / a / b */
const crumbs = computed(() => {
  const parts = (currentPath.value || '').split('/').filter(Boolean);
  return parts.map((name, idx) => ({ name, path: parts.slice(0, idx + 1).join('/') }));
});
const atHistory = computed(() => currentPath.value === HISTORY_PATH);

async function refresh(silent = true) {
  const sid = activeSessionId.value;
  // 新会话为本地 temp id（chat store createSession 设计），尚未落库——后端无从校验属主，
  // 直接空态提示，不发请求（修复：新会话点文件列表抽屉打不开）
  if (!sid || isTempSession.value) {
    entries.value = [];
    return;
  }
  loading.value = true;
  try {
    const res: any = await getAgentFileTreeApi(sid, currentPath.value, true);
    if (res && res.success === false) {
      ElMessage.warning(res.msg || '读取文件树失败');
      entries.value = [];
      return;
    }
    const level = res?.data ?? res;
    entries.value = Array.isArray(level?.entries) ? level.entries : [];
    parentPath.value = level?.parentPath ?? '';
    historyTotal.value = level?.historyTotal ?? 0;
    if (!silent) ElMessage.success('已刷新');
  } catch (error: any) {
    if (!silent) ElMessage.error(error?.message || '读取文件树失败');
  } finally {
    loading.value = false;
  }
}

function enterDir(node: AgentFileTreeNode) {
  currentPath.value = node.path;
  refresh();
}

function goUp() {
  currentPath.value = atHistory.value ? '' : parentPath.value;
  refresh();
}

function gotoCrumb(path: string) {
  currentPath.value = path;
  refresh();
}

function onFilesChanged() {
  refresh();
}

onMounted(() => {
  window.addEventListener(FILES_CHANGED_EVENT, onFilesChanged);
  refresh();
});
onBeforeUnmount(() => window.removeEventListener(FILES_CHANGED_EVENT, onFilesChanged));
watch(activeSessionId, () => {
  currentPath.value = '';
  refresh();
});

function fmtSize(n?: null | number) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

function previewable(f: AgentFileTreeNode) {
  const m = (f.mime || '').toLowerCase();
  return m.startsWith('image/') || m.startsWith('text/') || m.includes('json') || m.includes('xml');
}

async function onDownload(node: AgentFileTreeNode) {
  try {
    await downloadAgentFileApi(node.id as string, node.name, false);
  } catch (error: any) {
    ElMessage.error(error?.message || '下载失败');
  }
}

async function onPreview(node: AgentFileTreeNode) {
  try {
    await downloadAgentFileApi(node.id as string, node.name, true);
  } catch (error: any) {
    ElMessage.error(error?.message || '预览失败');
  }
}

async function onDelete(node: AgentFileTreeNode) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${node.name}」？`, '删除', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await deleteAgentFileApi(node.id as string);
    ElMessage.success('已删除');
    notifyFilesChanged();
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败');
  }
}

function dirSubtitle(node: AgentFileTreeNode) {
  const files = node.fileCount ?? 0;
  const dirs = node.dirCount ?? 0;
  return dirs > 0 ? `${dirs} 个文件夹 · ${files} 个文件` : `${files} 个文件`;
}
</script>

<template>
  <ElDrawer
    v-model="drawerVisible"
    :append-to-body="true"
    direction="rtl"
    size="360px"
    title="本会话文件"
  >
    <!-- 面包屑 + 返回（历史节点单独标注） -->
    <div v-if="!isTempSession" class="files-panel__nav">
      <ElButton v-if="currentPath" size="small" text @click="goUp">
        <ElIcon><Back /></ElIcon>
      </ElButton>
      <span class="files-panel__crumb" :class="{ 'is-current': !currentPath }" @click="gotoCrumb('')">
        会话目录
      </span>
      <template v-for="c in crumbs" :key="c.path">
        <span class="files-panel__sep">/</span>
        <span
          class="files-panel__crumb"
          :class="{ 'is-current': c.path === currentPath }"
          @click="gotoCrumb(c.path)"
        >{{ c.name }}</span>
      </template>
    </div>

    <div v-if="loading && !entries.length" class="files-panel__empty">加载中…</div>
    <div v-else-if="!entries.length" class="files-panel__empty">
      暂无产物文件<br />
      <small>{{ isTempSession
        ? '发送第一条消息后，本会话生成的文件会出现在这里'
        : (currentPath ? '此文件夹为空' : '智能体写出的文件会在回复结束后出现在这里') }}</small>
    </div>

    <div
      v-for="node in entries"
      :key="node.type === 'file' ? (node.id as string) : node.path"
      class="files-panel__row"
      :class="{ 'is-clickable': node.type !== 'file' }"
      @click="node.type === 'dir' || node.type === 'history' ? enterDir(node) : undefined"
    >
      <ElIcon class="files-panel__icon" :class="{ 'is-dir': node.type !== 'file' }">
        <Folder v-if="node.type === 'dir'" />
        <Clock v-else-if="node.type === 'history'" />
        <Document v-else />
      </ElIcon>
      <div class="files-panel__meta">
        <div class="files-panel__name" :title="node.name">{{ node.name }}</div>
        <div class="files-panel__sub">
          <template v-if="node.type === 'dir'">{{ dirSubtitle(node) }}</template>
          <template v-else-if="node.type === 'history'">跨会话遗留 · {{ node.fileCount }} 项</template>
          <template v-else>{{ fmtSize(node.sizeBytes) }} · {{ node.source }}</template>
        </div>
      </div>
      <div v-if="node.type === 'file'" class="files-panel__ops" @click.stop>
        <ElTooltip v-if="previewable(node)" content="预览" placement="top">
          <ElButton size="small" text @click="onPreview(node)"><ElIcon><View /></ElIcon></ElButton>
        </ElTooltip>
        <ElTooltip content="下载" placement="top">
          <ElButton size="small" text @click="onDownload(node)"><ElIcon><Download /></ElIcon></ElButton>
        </ElTooltip>
        <ElTooltip content="删除" placement="top">
          <ElButton size="small" text @click="onDelete(node)"><ElIcon><Delete /></ElIcon></ElButton>
        </ElTooltip>
      </div>
    </div>

    <template #footer>
      <ElButton size="small" :loading="loading" @click="refresh(false)">刷新</ElButton>
      <span v-if="historyTotal > 0 && !atHistory" class="files-panel__hint">
        （含 {{ historyTotal }} 条跨会话遗留）
      </span>
    </template>
  </ElDrawer>
</template>

<style scoped>
.files-panel__nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding-bottom: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #606266;
  border-bottom: 1px solid #f0f2f5;
}
.files-panel__crumb {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.files-panel__crumb:hover { color: #409eff; }
.files-panel__crumb.is-current { color: #303133; font-weight: 600; cursor: default; }
.files-panel__sep { color: #c0c4cc; }
.files-panel__empty {
  padding: 28px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
.files-panel__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid #f0f2f5;
}
.files-panel__row.is-clickable { cursor: pointer; }
.files-panel__row.is-clickable:hover { background: #f5f7fa; }
.files-panel__icon { color: #909399; flex: none; }
.files-panel__icon.is-dir { color: #e6a23c; }
.files-panel__meta { flex: 1; min-width: 0; }
.files-panel__name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.files-panel__sub { font-size: 12px; color: #a8abb2; }
.files-panel__ops { display: flex; flex: none; }
.files-panel__hint { margin-left: 8px; font-size: 12px; color: #a8abb2; }
</style>
