<script lang="ts" setup>
import type { AgentRuntimeConfig, AgentRuntimePreview } from '#/api/core/agentRuntime';
import type { Datasource } from '#/api/core/datasource';
import type { ModelConfig } from '#/api/core/modelConfig';

import { computed, onMounted, ref } from 'vue';

import {
  getAgentRuntimeConfigApi,
  previewAgentRuntimeApi,
  saveAgentRuntimeConfigApi,
} from '#/api/core/agentRuntime';
import { getAllDatasourcesApi } from '#/api/core/datasource';
import { getModelConfigListApi } from '#/api/core/modelConfig';
import {
  ElAlert,
  ElButton,
  ElCol,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
} from 'element-plus';

/**
 * 对话智能体运行配置（T-15 / R-09）：模型、计划模式、记忆、知识库工具（含 topK/阈值）、
 * 数据库两级工具 + 数据源、文件系统策略。
 *
 * 该面板只在智能体已保存（有 id）后可用——运行配置按 agentId 落库。
 */
const props = defineProps<{ agentId: number }>();

const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const preview = ref<AgentRuntimePreview | null>(null);
const models = ref<ModelConfig[]>([]);
const datasources = ref<Datasource[]>([]);

const config = ref<AgentRuntimeConfig>({
  modelConfigId: null,
  planMode: false,
  memoryEnabled: true,
  knowledgeEnabled: false,
  knowledgeTopK: 10,
  knowledgeSimilarityThreshold: 0.65,
  dbQueryEnabled: false,
  dbDeepAnalysisEnabled: false,
  datasourceId: null,
  filesystemPolicy: 'local',
  maxIterations: null,
  compactionTriggerTokens: null,
  compactionKeepMessages: null,
  toolResultMaxChars: null,
});

const dbToolOn = computed(
  () => !!config.value.dbQueryEnabled || !!config.value.dbDeepAnalysisEnabled,
);

/** R-16：下拉只列「启用集合」；未选时运行按默认模型（占位符直接显示默认那条） */
const chatModels = computed(() =>
  models.value.filter((m) => (m.modelType || '').toUpperCase() === 'CHAT' && m.isActive),
);

const defaultChatModel = computed(
  () => chatModels.value.find((m) => m.isDefault) ?? null,
);

/** 部分接口的泛型写的是载荷，运行时返回的是 ReturnVo 包装，这里统一拆包 */
function unwrap<T>(res: any): T[] | T | null {
  if (res && typeof res === 'object' && 'success' in res) {
    return res.success ? (res.data ?? null) : null;
  }
  return (res ?? null) as T;
}

async function load() {
  if (!props.agentId) {
    return;
  }
  loading.value = true;
  try {
    const [configRes, modelRes, dsRes] = await Promise.all([
      getAgentRuntimeConfigApi(props.agentId),
      getModelConfigListApi(),
      getAllDatasourcesApi(),
    ]);
    const loaded = unwrap<AgentRuntimeConfig>(configRes);
    if (loaded && typeof loaded === 'object' && !Array.isArray(loaded)) {
      config.value = { ...config.value, ...loaded };
    }
    models.value = (unwrap<ModelConfig>(modelRes) as ModelConfig[]) ?? [];
    datasources.value = (unwrap<Datasource>(dsRes) as Datasource[]) ?? [];
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (dbToolOn.value && !config.value.datasourceId) {
    ElMessage.warning('开启数据库类工具前需先选择目标数据源');
    return;
  }
  saving.value = true;
  try {
    const res = await saveAgentRuntimeConfigApi(props.agentId, config.value);
    if (!res.success) {
      ElMessage.error(res.msg || '运行配置保存失败');
      return;
    }
    ElMessage.success('运行配置已保存（下一次对话生效）');
    await load();
  } finally {
    saving.value = false;
  }
}

/** 构建预演：按当前已保存配置真实构建一次运行时实例，回显生效工具与技能池 */
async function runPreview() {
  previewing.value = true;
  try {
    const res = await previewAgentRuntimeApi(props.agentId);
    if (!res.success) {
      ElMessage.error(res.msg || '构建预演失败');
      return;
    }
    preview.value = res.data ?? null;
    if (preview.value && preview.value.buildOk === false) {
      ElMessage.error(`构建失败：${preview.value.errorMessage || '未知原因'}`);
    }
  } finally {
    previewing.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h3 class="m-0 mb-2 text-base font-semibold">对话智能体配置</h3>
    <p class="m-0 mb-4 text-sm text-gray-500">
      运行配置决定该智能体的实际能力（模型、知识库检索、数据库查询、计划模式、记忆）。
      保存后下一次对话生效；修改会触发运行时实例重建。
    </p>

    <ElAlert
      v-if="!agentId"
      type="info"
      :closable="false"
      show-icon
      title="请先保存智能体，再配置运行能力"
      class="mb-4"
    />

    <template v-else>
      <div class="mb-6">
        <h4 class="m-0 mb-3 text-sm font-semibold">模型与行为</h4>
        <ElRow :gutter="20">
          <ElCol :span="12">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                对话模型
              </label>
              <ElSelect
                v-model="config.modelConfigId"
                :placeholder="defaultChatModel
                  ? `未选择（默认：${defaultChatModel.modelName}）`
                  : '未选择（需在模型管理设置默认对话模型）'"
                clearable
                class="w-full"
              >
                <ElOption
                  v-for="m in chatModels"
                  :key="m.id"
                  :label="`${m.modelName}${m.isDefault ? '（默认）' : ''}`"
                  :value="m.id!"
                />
              </ElSelect>
              <p class="m-0 mt-1 text-xs text-gray-400">
                仅可选「已启用」的对话模型；不选则使用模型管理里的默认模型
              </p>
            </div>
          </ElCol>
          <ElCol :span="12">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                文件系统策略
              </label>
              <ElSelect v-model="config.filesystemPolicy" class="w-full">
                <ElOption label="本地沙箱（支持 shell 与脚本类技能）" value="local" />
                <ElOption label="远程存储（多实例共享，不支持 shell）" value="remote" />
              </ElSelect>
            </div>
          </ElCol>
          <ElCol :span="12">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                工具迭代上限（1~100）
              </label>
              <ElInputNumber
                v-model="config.maxIterations"
                :min="1"
                :max="100"
                class="w-full"
                placeholder="留空=系统默认"
              />
              <p class="mt-1 text-xs text-gray-400">
                按模型推理轮次计（单轮可并行多个工具调用）；复杂任务（多文件产出、图形绘制/转换）建议
                20~40；留空沿用系统默认
              </p>
            </div>
          </ElCol>
        </ElRow>
        <!-- R-05 上下文治理三输入（T-06）：留空=全局默认；三项均进配置指纹 → 改值即重建实例 -->
        <ElRow :gutter="16" class="mt-4">
          <ElCol :span="8">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                压缩触发 token 数
              </label>
              <ElInputNumber
                v-model="config.compactionTriggerTokens"
                :min="1000"
                :max="2000000"
                :step="1024"
                class="w-full"
                placeholder="留空=全局默认（102400）"
              />
              <p class="mt-1 text-xs text-gray-400">
                对话历史超过该 token 数即压缩（保留最近若干条）。默认 102400 ≈ 0.8×128k 上下文；
                长轮/多文件任务可下调，留空沿用全局默认
              </p>
            </div>
          </ElCol>
          <ElCol :span="8">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                压缩保留消息条数
              </label>
              <ElInputNumber
                v-model="config.compactionKeepMessages"
                :min="4"
                :max="200"
                class="w-full"
                placeholder="留空=全局默认（20）"
              />
              <p class="mt-1 text-xs text-gray-400">
                压缩后原样保留的最近消息条数（其余交给摘要）。调小省 token，调大更保真；留空沿用全局默认
              </p>
            </div>
          </ElCol>
          <ElCol :span="8">
            <div class="mb-4">
              <label class="mb-2 block text-sm font-medium text-gray-700">
                工具结果最大字符数
              </label>
              <ElInputNumber
                v-model="config.toolResultMaxChars"
                :min="512"
                :max="1048576"
                :step="1024"
                class="w-full"
                placeholder="留空=全局默认（8192）"
              />
              <p class="mt-1 text-xs text-gray-400">
                单个工具返回超过该长度即回收为预览（原文落盘、可回读，不丢数据）。留空沿用全局默认
              </p>
            </div>
          </ElCol>
        </ElRow>
        <div class="flex flex-wrap items-center gap-8">
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-700">计划模式</span>
            <ElSwitch v-model="config.planMode" />
            <span class="text-xs text-gray-400">复杂任务先出计划再执行</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-700">长期记忆</span>
            <ElSwitch v-model="config.memoryEnabled" />
            <span class="text-xs text-gray-400">关闭后不写入记忆文件</span>
          </div>
        </div>
      </div>

      <div class="mb-6">
        <h4 class="m-0 mb-3 text-sm font-semibold">工具能力</h4>

        <div class="mb-4 rounded-lg border border-gray-100 p-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-800">知识库检索</span>
            <ElSwitch v-model="config.knowledgeEnabled" />
            <span class="text-xs text-gray-400">
              按本智能体的知识范围检索（不跨智能体）
            </span>
          </div>
          <ElRow v-if="config.knowledgeEnabled" :gutter="20" class="mt-3">
            <ElCol :span="8">
              <label class="mb-2 block text-xs text-gray-600">召回条数 topK（1~50）</label>
              <ElInputNumber
                v-model="config.knowledgeTopK"
                :min="1"
                :max="50"
                class="w-full"
              />
            </ElCol>
            <ElCol :span="8">
              <label class="mb-2 block text-xs text-gray-600">相似度阈值（0~1）</label>
              <ElInputNumber
                v-model="config.knowledgeSimilarityThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="2"
                class="w-full"
              />
            </ElCol>
          </ElRow>
        </div>

        <div class="mb-4 rounded-lg border border-gray-100 p-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-800">数据库取数</span>
            <ElSwitch v-model="config.dbQueryEnabled" />
            <span class="text-xs text-gray-400">
              自然语言 → SQL → 只读查询结果（单次取数）
            </span>
          </div>
          <div class="mt-3 flex items-center gap-2">
            <span class="text-sm font-medium text-gray-800">数据库深度分析</span>
            <ElSwitch v-model="config.dbDeepAnalysisEnabled" />
            <span class="text-xs text-gray-400">
              多步骤取数与分析（内部走 NL2SQL 状态图，耗时较长）
            </span>
          </div>
          <div v-if="dbToolOn" class="mt-3">
            <label class="mb-2 block text-xs text-gray-600">目标数据源（必填）</label>
            <ElSelect
              v-model="config.datasourceId"
              placeholder="请选择数据源"
              class="w-full"
            >
              <ElOption
                v-for="ds in datasources"
                :key="ds.id"
                :label="`${ds.name}（${ds.type}）`"
                :value="ds.id!"
              />
            </ElSelect>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <ElButton type="primary" :loading="saving" @click="save">
          保存运行配置
        </ElButton>
        <ElButton :loading="previewing" @click="runPreview">构建预演</ElButton>
        <span class="text-xs text-gray-400">
          预演会按已保存的配置真实构建一次实例，用于确认生效能力
        </span>
      </div>

      <div v-if="preview" class="mt-4 rounded-lg bg-gray-50 p-4 text-xs">
        <div class="mb-1">
          <span class="text-gray-500">实例来源：</span>
          <span>{{ preview.instanceSource }}</span>
          <span class="ml-3 text-gray-500">技能池：</span>
          <span>{{ preview.skillNames?.join('、') || '无' }}</span>
        </div>
        <div class="mb-1">
          <span class="text-gray-500">生效工具：</span>
          <span>{{ preview.toolNames?.join('、') || '无' }}</span>
        </div>
        <div class="break-all text-gray-500">
          {{ preview.summary }}
        </div>
      </div>
    </template>
  </div>
</template>
