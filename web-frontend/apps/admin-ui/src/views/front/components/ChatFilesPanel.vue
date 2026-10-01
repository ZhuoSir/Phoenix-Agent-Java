<script setup lang="ts">
/**
 * 会话文件面板（BL-19）：右侧可折叠抽屉，列出本会话产物，支持下载/预览/删除。
 * 刷新时机：切换会话、SSE agentFiles 事件、物化成功广播。
 */
import { storeToRefs } from '@vben/stores';
import { useChatStore } from '@phoenix/chat-shared';
import { ElButton, ElDrawer, ElIcon, ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import { Document, Download, Delete, View } from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  deleteAgentFileApi,
  downloadAgentFileApi,
  FILES_CHANGED_EVENT,
  listAgentFilesApi,
  notifyFilesChanged,
  type AgentFileItem,
} from '#/api/core/agentFiles';

const props = withDefaults(defineProps<{ sessionId?: null | string }>(), { sessionId: null });
const chat = useChatStore();
const { activeSessionId: chatStoreSessionId } = storeToRefs(chat);
/** admin 运行页显式传入；前台聊天页缺省用 chat store 的当前会话 */
const activeSessionId = computed(() => props.sessionId ?? chatStoreSessionId.value ?? null);

const drawerVisible = ref(false);
function openDrawer() { drawerVisible.value = true; refresh(); }
defineExpose({ open: openDrawer });
const loading = ref(false);
const files = ref<AgentFileItem[]>([]);

async function refresh(silent = true) {
  if (!activeSessionId.value) {
    files.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listAgentFilesApi(activeSessionId.value, true);
    files.value = (res as any)?.data ?? res ?? [];
    if (!silent) ElMessage.success('文件列表已刷新');
  } catch (error: any) {
    if (!silent) ElMessage.error(error?.message || '获取文件列表失败');
  } finally {
    loading.value = false;
  }
}

function onFilesChanged() {
  refresh();
}

onMounted(() => {
  window.addEventListener(FILES_CHANGED_EVENT, onFilesChanged);
  refresh();
});
onBeforeUnmount(() => window.removeEventListener(FILES_CHANGED_EVENT, onFilesChanged));
watch(activeSessionId, () => refresh());

function fmtSize(n: number) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

function previewable(f: AgentFileItem) {
  const m = (f.mime || '').toLowerCase();
  return m.startsWith('image/') || m.startsWith('text/') || m.includes('json') || m.includes('xml');
}

async function onDownload(f: AgentFileItem) {
  try {
    await downloadAgentFileApi(f.id, f.fileName, false);
  } catch (error: any) {
    ElMessage.error(error?.message || '下载失败');
  }
}

async function onPreview(f: AgentFileItem) {
  try {
    await downloadAgentFileApi(f.id, f.fileName, true);
  } catch (error: any) {
    ElMessage.error(error?.message || '预览失败');
  }
}

async function onDelete(f: AgentFileItem) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${f.fileName}」？`, '删除', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await deleteAgentFileApi(f.id);
    ElMessage.success('已删除');
    notifyFilesChanged();
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败');
  }
}
</script>

<template>
  <ElDrawer
    v-model="drawerVisible"
    :append-to-body="true"
    direction="rtl"
    size="340px"
    title="本会话文件"
  >
    <div v-if="loading && !files.length" class="files-panel__empty">加载中…</div>
    <div v-else-if="!files.length" class="files-panel__empty">
      暂无产物文件<br />
      <small>智能体写出的文件会在回复结束后出现在这里</small>
    </div>
    <div v-for="f in files" :key="f.id" class="files-panel__row">
      <ElIcon class="files-panel__icon"><Document /></ElIcon>
      <div class="files-panel__meta">
        <div class="files-panel__name" :title="f.fileName">{{ f.fileName }}</div>
        <div class="files-panel__sub">{{ fmtSize(f.sizeBytes) }} · {{ f.source }}</div>
      </div>
      <div class="files-panel__ops">
        <ElTooltip v-if="previewable(f)" content="预览" placement="top">
          <ElButton size="small" text @click="onPreview(f)"><ElIcon><View /></ElIcon></ElButton>
        </ElTooltip>
        <ElTooltip content="下载" placement="top">
          <ElButton size="small" text @click="onDownload(f)"><ElIcon><Download /></ElIcon></ElButton>
        </ElTooltip>
        <ElTooltip content="删除" placement="top">
          <ElButton size="small" text @click="onDelete(f)"><ElIcon><Delete /></ElIcon></ElButton>
        </ElTooltip>
      </div>
    </div>
    <template #footer>
      <ElButton size="small" :loading="loading" @click="refresh(false)">刷新</ElButton>
    </template>
  </ElDrawer>
</template>

<style scoped>
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
.files-panel__icon { color: #909399; flex: none; }
.files-panel__meta { flex: 1; min-width: 0; }
.files-panel__name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.files-panel__sub { font-size: 12px; color: #a8abb2; }
.files-panel__ops { display: flex; flex: none; }
</style>
