<script setup lang="ts">
/**
 * 智能体知识库绑定面板（T-08）：候选来自 platform bindable，
 * 组不匹配项置灰带原因；保存走全量替换（服务端复核组交集）。
 */
import { ElButton, ElCheckbox, ElIcon, ElMessage, ElTag } from 'element-plus';
import { Lock } from '@element-plus/icons-vue';
import { computed, ref, watch } from 'vue';

import {
  kbaseBindApi,
  kbaseBindableApi,
  type BindableKbase,
} from '#/api/core/knowledgeBase';

const props = defineProps<{ agentId: number }>();

const loading = ref(false);
const saving = ref(false);
const candidates = ref<BindableKbase[]>([]);
const selected = ref<Set<number>>(new Set());

const loaded = computed(() => candidates.value.length > 0);

async function load() {
  if (!props.agentId) return;
  loading.value = true;
  try {
    candidates.value = await kbaseBindableApi(props.agentId);
    selected.value = new Set(
      candidates.value.filter((k) => k.bound).map((k) => k.id),
    );
  } catch (e: any) {
    ElMessage.error(e?.message || '加载知识库候选失败');
  } finally {
    loading.value = false;
  }
}

function toggle(kb: BindableKbase) {
  if (!kb.selectable) return;
  if (selected.value.has(kb.id)) selected.value.delete(kb.id);
  else selected.value.add(kb.id);
  selected.value = new Set(selected.value); // 触发响应
}

async function save() {
  saving.value = true;
  try {
    const res = await kbaseBindApi(props.agentId, [...selected.value]);
    if (res.success === false) {
      ElMessage.error(res.msg || '绑定保存失败');
      return;
    }
    ElMessage.success('知识库绑定已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

watch(() => props.agentId, load, { immediate: true });
</script>

<template>
  <div class="kbase-bind">
    <div class="kbase-bind__head">
      <span class="kbase-bind__title">知识库绑定</span>
      <ElButton :loading="saving" size="small" type="primary" @click="save">保存绑定</ElButton>
    </div>
    <div class="kbase-bind__hint">
      绑定后该智能体对话可召回所选知识库中启用召回的知识；置灰项表示其所属组未包含此智能体（请先到组管理分配）。
    </div>
    <div v-if="loading" class="kbase-bind__loading">加载中…</div>
    <div v-else-if="!loaded" class="kbase-bind__empty">
      暂无知识库，请到「知识库」页面创建
    </div>
    <div v-for="kb in candidates" :key="kb.id" class="kbase-bind__row" :class="{ 'is-disabled': !kb.selectable }">
      <ElCheckbox
        :disabled="!kb.selectable"
        :model-value="selected.has(kb.id)"
        @change="toggle(kb)"
      />
      <div class="kbase-bind__meta">
        <div class="kbase-bind__name">
          {{ kb.name }}
          <span class="kbase-bind__count">{{ kb.itemCount }} 条知识</span>
          <ElTag v-if="kb.status !== 1" size="small" type="warning">已停用</ElTag>
        </div>
        <div v-if="!kb.selectable" class="kbase-bind__reason">
          <ElIcon class="kbase-bind__lock"><Lock /></ElIcon>
          {{ kb.disabledReason }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kbase-bind { padding: 8px 4px; }
.kbase-bind__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.kbase-bind__title { font-size: 14px; font-weight: 600; }
.kbase-bind__hint {
  margin-bottom: 12px;
  font-size: 12px;
  color: #909399;
}
.kbase-bind__loading,
.kbase-bind__empty {
  padding: 24px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
.kbase-bind__row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 8px;
  border-bottom: 1px solid #f0f2f5;
}
.kbase-bind__row.is-disabled { opacity: 0.65; }
.kbase-bind__meta { flex: 1; min-width: 0; }
.kbase-bind__name { display: flex; gap: 8px; align-items: center; font-size: 13px; }
.kbase-bind__count { font-size: 12px; color: #a8abb2; }
.kbase-bind__reason {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 2px;
  font-size: 12px;
  color: #e6a23c;
}
.kbase-bind__lock { font-size: 12px; }
</style>
