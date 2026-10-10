<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { ModelConfig } from '#/api/core/modelConfig';
import { fetchOllamaModelsApi } from '#/api/core/modelConfig';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElDivider,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSkeleton,
  ElSlider,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  activateModelConfigApi,
  deactivateModelConfigApi,
  setDefaultModelConfigApi,
  addModelConfigApi,
  deleteModelConfigApi,
  getModelConfigListApi,
  testModelConfigConnectionApi,
  updateModelConfigApi,
} from '#/api';

import { VbenTableAction } from '#/adapter/vxe-table';
import { useVbenForm } from '#/adapter/form';
import { useColumns } from './data';

defineOptions({ name: 'ModelConfig' });

const columns = useColumns();

const [FilterForm] = useVbenForm({
  commonConfig: { componentProps: { clearable: true } },
  layout: 'inline',
  wrapperClass: 'grid-cols-1',
  submitButtonOptions: { content: '查询' },
  schema: [
    {
      fieldName: 'modelType',
      component: 'Select',
      label: '模型类型',
      labelWidth: 60,
      componentProps: {
        placeholder: '请选择模型类型',
        style: { width: '240px' },
        options: [
          { label: '全部', value: '' },
          { label: '对话模型 (CHAT)', value: 'CHAT' },
          { label: '多模态模型 (MULTIMODAL)', value: 'MULTIMODAL' },
          { label: '嵌入模型 (EMBEDDING)', value: 'EMBEDDING' },
        ],
      },
    },
  ],
  handleSubmit: (values) => {
    activeFilter.value = values.modelType || '';
  },
  handleReset: () => {
    activeFilter.value = '';
  },
});

const loading = ref(true);
/** R-05：Ollama 本机模型列表（拉取成功后模型名称改为可搜索下拉；失败退化手工输入） */
const ollamaModels = ref<string[]>([]);
const loadingModels = ref(false);
const isOllama = computed(() => formData.provider === 'ollama');

async function fetchOllamaModels() {
  if (!formData.baseUrl) {
    ElMessage.warning('请先填写 API 地址');
    return;
  }
  loadingModels.value = true;
  try {
    // 解包约定归一：requestClient 可能已返回 data，也可能返回 {data,...} 信封 —— 两种都兼容，
    // 否则列表拿到非数组 ⇒ length 判定失败 ⇒ 退化为输入框（表现为"点了没反应"）
    const res: any = await fetchOllamaModelsApi(formData.baseUrl);
    // 多形状兼容：[] / {data:[]} / {data:{list:[]}} / {list:[]}（不同 requestClient 解包约定）
    const pick = (v: any): string[] => {
      if (Array.isArray(v)) return v as string[];
      if (v && typeof v === 'object') {
        if (Array.isArray(v.data)) return v.data as string[];
        if (Array.isArray(v.list)) return v.list as string[];
        if (v.data && Array.isArray(v.data.list)) return v.data.list as string[];
        if (v.data) return pick(v.data);
      }
      return [];
    };
    const list = pick(res);
    ollamaModels.value = list;
    if (list.length === 0) {
      // 便于排障：把原始返回形状留在控制台（不含敏感信息）
      console.warn('[ollama] 模型列表为空，原始返回:', res);
    }
    if (ollamaModels.value.length === 0) {
      ElMessage.warning('未获取到模型，请确认 Ollama 已安装模型');
    }
  } catch (error: any) {
    // 失败不阻断保存：给出原因并退化为手工输入
    ollamaModels.value = [];
    ElMessage.error(`获取模型列表失败：${error?.message ?? error}`);
  } finally {
    loadingModels.value = false;
  }
}
const dialogVisible = ref(false);
const isEditMode = ref(false);
const submitting = ref(false);
const activatingId = ref<null | number>(null);
const testingId = ref<null | number>(null);
const activeFilter = ref('');
const configs = ref<ModelConfig[]>([]);
const formRef = ref<FormInstance | null>(null);

const defaultFormData: ModelConfig = {
  provider: '',
  apiKey: '',
  baseUrl: '',
  modelName: '',
  modelType: 'CHAT',
  temperature: 0,
  maxTokens: 2000,
  completionsPath: '',
  embeddingsPath: '',
  isActive: false,
  proxyEnabled: false,
  proxyHost: '',
  proxyPort: 7890,
  proxyUsername: '',
  proxyPassword: '',
};

const formData = reactive<ModelConfig>({ ...defaultFormData });

const providerBaseUrlMap: Record<string, string> = {
  deepseek: 'https://api.deepseek.com',
  qwen: 'https://dashscope.aliyuncs.com/compatible-mode',
  openai: 'https://api.openai.com',
  siliconflow: 'https://api.siliconflow.cn',
  // R-01：Ollama 本地/内网默认地址（容器访问宿主；内网部署可改为内网 IP）
  ollama: 'http://host.docker.internal:11434',
  custom: 'https://modelservice.jdcloud.com/',
};

function updateBaseUrlByProvider(provider: string) {
  if (provider && provider !== 'custom') {
    formData.baseUrl = providerBaseUrlMap[provider] || '';
  }
}

const formRules: FormRules = {
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  apiKey: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (formData.provider === 'custom' || formData.provider === 'ollama') {
          callback();
        } else if (!value || value.trim() === '') {
          callback(new Error('请输入API密钥'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
  baseUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }],
  temperature: [
    {
      type: 'number',
      min: 0,
      max: 2,
      message: '温度值必须在0-2之间',
      trigger: 'blur',
    },
  ],
  maxTokens: [
    {
      type: 'number',
      min: 100,
      message: '最大Token至少为100',
      trigger: 'blur',
    },
  ],
  proxyHost: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (formData.proxyEnabled && (!value || value.trim() === '')) {
          callback(new Error('启用代理时，必须填写代理主机地址'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
  proxyPort: [
    {
      validator: (_rule: any, value: number, callback: any) => {
        if (formData.proxyEnabled && !value) {
          callback(new Error('启用代理时，必须填写代理端口'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

const dialogTitle = computed(() =>
  isEditMode.value ? '编辑模型配置' : '新增模型配置',
);

const filteredConfigs = computed(() => {
  if (!activeFilter.value) return configs.value;
  return configs.value.filter((c) => c.modelType === activeFilter.value);
});

async function loadConfigs() {
  loading.value = true;
  try {
    const data = await getModelConfigListApi();
    configs.value = (data as any)?.data ?? data ?? [];
  } catch {
    ElMessage.error('获取模型配置列表失败，请检查网络！');
    configs.value = [];
  } finally {
    loading.value = false;
  }
}

/**
 * BUG-106（2026-10-06）：本项目接口信封是 `{success, message, data}`，而请求层用 `responseReturn:'body'`
 * 拿到的就是**整个信封对象** ⇒ 业务失败时它依然是真值，原先的 `if (result)` 会把失败当成功提示
 * （用户只看到"添加成功"、列表却没变，无任何报错）。统一在此判定 success，失败即抛出以便上层提示。
 */
function assertApiOk(result: unknown, fallbackMsg: string) {
  if (result && typeof result === 'object' && 'success' in result) {
    const envelope = result as { message?: string; success?: boolean };
    if (envelope.success === false) {
      throw new Error(envelope.message || fallbackMsg);
    }
  }
}

function showAddDialog() {
  isEditMode.value = false;
  Object.assign(formData, defaultFormData);
  // BUG-106（2026-10-06）：`defaultFormData` **没有 id 字段** ⇒ Object.assign 清不掉上一次「编辑」写入的 id，
  // 新增时会带着旧主键去 INSERT（id 非空时 MyBatis-Flex 显式插入）⇒ duplicate key、静默失败、列表不刷新。
  // 与「菜单管理」页 handleAddChild 的既有写法保持一致：
  delete formData.id;
  dialogVisible.value = true;
}

function handleEdit(config: ModelConfig) {
  isEditMode.value = true;
  Object.assign(formData, config);
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
    submitting.value = true;

    if (isEditMode.value) {
      const result = await updateModelConfigApi({ ...formData });
      assertApiOk(result, '配置更新失败');
      ElMessage.success('配置更新成功');
      dialogVisible.value = false;
      await loadConfigs();
    } else {
      // BUG-106 双保险：新增请求绝不携带主键（主键由服务端生成）
      const payload = { ...formData };
      delete (payload as { id?: number }).id;
      const result = await addModelConfigApi(payload);
      assertApiOk(result, '配置添加失败');
      ElMessage.success('配置添加成功');
      dialogVisible.value = false;
      await loadConfigs();
    }
  } catch (error: any) {
    if (error?.message) {
      ElMessage.error(error.message);
    }
  } finally {
    submitting.value = false;
  }
}

function getActions(config: ModelConfig) {
  return [
    {
      text: '连接测试',
      icon: 'lucide:cable',
      loading: testingId.value === config.id,
      onClick: () => handleTestConnection(config),
    },
    {
      text: '启用',
      icon: 'lucide:check-circle',
      ifShow: () => !config.isActive,
      loading: activatingId.value === config.id,
      popConfirm: {
        title:
          config.modelType === 'EMBEDDING'
            ? '您正在更换嵌入模型，此操作风险较高！由于不同模型的向量空间不一致，切换后可能导致所有历史向量数据（含数据源、智能体知识、业务知识）将全部失效且无法检索。确定要执行吗？'
            : `确定要启用【${config.provider} - ${config.modelName}】吗？（启用=可被智能体选择的模型，同类型可同时启用多个）`,
        confirm: () => handleActivate(config.id),
        okText: '确定',
        cancelText: '取消',
      },
    },
    {
      text: '停用',
      icon: 'lucide:minus-circle',
      ifShow: () => !!config.isActive,
      loading: deactivatingId.value === config.id,
      popConfirm: {
        title: `确定要停用【${config.provider} - ${config.modelName}】吗？`,
        confirm: () => handleDeactivate(config.id),
        okText: '确定',
        cancelText: '取消',
      },
    },
    {
      text: '设为默认',
      icon: 'lucide:star',
      ifShow: () => !config.isDefault,
      loading: settingDefaultId.value === config.id,
      popConfirm: {
        title:
          config.modelType === 'EMBEDDING'
            ? `您正在更换默认嵌入模型。历史向量由旧模型生成，与新模型向量空间不可比，需重新初始化数据源 schema 才与新模型一致（系统不会自动重算）。确定切换吗？`
            : `确定把【${config.provider} - ${config.modelName}】设为该类型的默认模型吗？（同类型旧默认会被取消，该条会自动置为启用）`,
        confirm: () => handleSetDefault(config.id),
        okText: '确定',
        cancelText: '取消',
      },
    },
    {
      text: '编辑',
      icon: 'lucide:edit',
      onClick: () => handleEdit(config),
    },
    {
      text: '删除',
      icon: 'lucide:trash-2',
      danger: true,
      popConfirm: {
        title: `确定要删除【${config.provider} - ${config.modelName}】吗？`,
        confirm: () => handleDelete(config),
        okText: '确定',
        cancelText: '取消',
      },
    },
  ];
}

async function handleDelete(config: ModelConfig) {
  try {
    if (config.id) {
      await deleteModelConfigApi(config.id);
      ElMessage.success('配置删除成功');
      await loadConfigs();
    }
  } catch {
    ElMessage.error('删除失败');
  }
}

const deactivatingId = ref<number | null>(null);
const settingDefaultId = ref<number | null>(null);

async function handleDeactivate(id?: number) {
  if (!id) return;
  try {
    deactivatingId.value = id;
    const res = await deactivateModelConfigApi(id);
    if (res && res.success === false) {
      ElMessage.error(res.message || '停用失败');
      return;
    }
    ElMessage.success('模型已停用');
    await loadConfigs();
  } catch (error: any) {
    ElMessage.error(error?.message || '停用失败');
  } finally {
    deactivatingId.value = null;
  }
}

async function handleSetDefault(id?: number) {
  if (!id) return;
  try {
    settingDefaultId.value = id;
    const res = await setDefaultModelConfigApi(id);
    if (res && res.success === false) {
      ElMessage.error(res.message || '设置默认模型失败');
      return;
    }
    ElMessage.success('已设为默认模型（对新请求立即生效，无需重启）');
    await loadConfigs();
  } catch (error: any) {
    ElMessage.error(error?.message || '设置默认模型失败');
  } finally {
    settingDefaultId.value = null;
  }
}

async function handleActivate(id?: number) {
  if (!id) return;

  try {
    activatingId.value = id;
    const res = await activateModelConfigApi(id);
    if (res && res.success === false) {
      ElMessage.error(res.message || '启用失败');
      return;
    }
    ElMessage.success('模型已启用（同类型其他启用项不受影响）');
    await loadConfigs();
  } catch (error: any) {
    ElMessage.error(error?.message || '启用过程中发生错误');
  } finally {
    activatingId.value = null;
  }
}

async function handleTestConnection(config: ModelConfig) {
  if (!config.id) return;

  try {
    testingId.value = config.id;
    const result = await testModelConfigConnectionApi(config);
    if (result && result?.success == true) {
      ElMessage.success('连接测试成功！');
    }else {
      ElMessage.error(result?.message);
    }
  } catch {
    ElMessage.error('连接测试过程中发生错误');
  } finally {
    testingId.value = null;
  }
}

function rowAs(row: any): ModelConfig {
  return row as ModelConfig;
}

function getProviderTagType(provider: string) {
  const typeMap: Record<
    string,
    'danger' | 'info' | 'primary' | 'success' | 'warning'
  > = {
    deepseek: 'success',
    qwen: 'warning',
    openai: 'primary',
    siliconflow: 'danger',
    custom: 'info',
  };
  return typeMap[provider] || 'info';
}

onMounted(loadConfigs);
</script>

<template>
  <Page auto-content-height>
    <ElCard class="search-section" :body-style="{ padding: '12px 20px' }">
      <FilterForm />
    </ElCard>

    <ElCard v-if="!loading" :body-style="{ padding: '20px' }">
      <div class="table-toolbar">
        <ElButton type="primary" @click="showAddDialog">新增</ElButton>
        <ElButton @click="loadConfigs">刷新</ElButton>
      </div>
      <ElTable :data="filteredConfigs" style="width: 100%" stripe>
        <template v-for="col in columns" :key="col.label">
          <ElTableColumn v-if="!col.slot" v-bind="col" />
          <ElTableColumn v-else v-bind="col">
            <template #default="scope">
              <template v-if="col.slot === 'provider'">
                <ElTag :type="getProviderTagType(scope.row.provider)" size="small">
                  {{ scope.row.provider }}
                </ElTag>
              </template>
              <template v-else-if="col.slot === 'modelType'">
                <ElTag
                    :type="
                      scope.row.modelType === 'CHAT'
                        ? 'primary'
                        : scope.row.modelType === 'MULTIMODAL'
                          ? 'warning'
                          : 'success'
                    "
                  size="small"
                >
                  {{ scope.row.modelType === 'CHAT' ? '对话模型' : (scope.row.modelType === 'MULTIMODAL' ? '多模态模型' : (scope.row.modelType === 'EMBEDDING' ? '嵌入模型' : (scope.row.modelType === 'AUDIO' ? '音频模型' : scope.row.modelType))) }}
                </ElTag>
              </template>
              <template v-else-if="col.slot === 'path'">
                <div v-if="scope.row.modelType === 'CHAT' && scope.row.completionsPath">
                  <ElTag type="primary" size="small">
                    对话: {{ scope.row.completionsPath }}
                  </ElTag>
                </div>
                <div v-else-if="scope.row.modelType === 'EMBEDDING' && scope.row.embeddingsPath">
                  <ElTag type="success" size="small">
                    嵌入: {{ scope.row.embeddingsPath }}
                  </ElTag>
                </div>
                <div v-else>
                  <span class="text-gray-400 italic">使用默认路径</span>
                </div>
              </template>
              <template v-else-if="col.slot === 'temperature'">
                {{ scope.row.temperature ?? 0.0 }}
              </template>
              <template v-else-if="col.slot === 'maxTokens'">
                {{ scope.row.maxTokens ?? 2000 }}
              </template>
              <template v-else-if="col.slot === 'status'">
                <ElTag
                  :type="scope.row.isActive ? 'success' : 'info'"
                  size="small"
                  effect="light"
                >
                  {{ scope.row.isActive ? '已启用' : '未启用' }}
                </ElTag>
              </template>
              <!-- 启用（可多选集合）与默认（每类型唯一）分两列展示，避免一列两个标记难扫读 -->
              <template v-else-if="col.slot === 'isDefault'">
                <ElTag
                  v-if="scope.row.isDefault"
                  type="warning"
                  size="small"
                  effect="dark"
                >
                  默认
                </ElTag>
                <span v-else class="text-gray-300">—</span>
              </template>
            </template>
          </ElTableColumn>
        </template>
        <ElTableColumn label="操作" width="330">
          <template #default="{ row }">
            <VbenTableAction :actions="getActions(rowAs(row))" />
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElSkeleton v-if="loading" :rows="6" animated />

    <div v-if="!loading && filteredConfigs.length === 0" class="py-16">
      <ElEmpty description="暂无模型配置">
        <ElButton type="primary" @click="showAddDialog">新增</ElButton>
      </ElEmpty>
    </div>

    <ElDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <ElForm
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        label-position="left"
      >
        <ElFormItem label="提供商" prop="provider">
          <ElSelect
            v-model="formData.provider"
            placeholder="请选择提供商"
            style="width: 100%"
            @change="updateBaseUrlByProvider"
          >
            <ElOption label="DeepSeek" value="deepseek" />
            <ElOption label="Qwen" value="qwen" />
            <ElOption label="OpenAI" value="openai" />
            <ElOption label="Siliconflow" value="siliconflow" />
            <ElOption label="Ollama（本地/内网）" value="ollama" />
            <ElOption label="Custom" value="custom" />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="模型类型" prop="modelType">
          <ElRadioGroup v-model="formData.modelType">
            <ElRadio label="CHAT">对话模型</ElRadio>
            <ElRadio label="MULTIMODAL">多模态模型</ElRadio>
            <ElRadio label="EMBEDDING">嵌入模型</ElRadio>
          </ElRadioGroup>
        </ElFormItem>

        <ElFormItem label="模型名称" prop="modelName">
          <div style="display: flex; gap: 8px; width: 100%">
            <ElSelect
              v-if="isOllama"
              v-model="formData.modelName"
              filterable
              allow-create
              default-first-option
              placeholder="选择本机已安装模型（可搜索）"
              style="flex: 1"
            >
              <ElOption v-for="m in ollamaModels" :key="m" :label="m" :value="m" />
            </ElSelect>
            <ElInput
              v-else
              v-model="formData.modelName"
              placeholder="请输入模型名称"
              style="flex: 1"
            />
            <ElButton
              v-if="isOllama"
              :loading="loadingModels"
              @click="fetchOllamaModels"
            >
              获取模型列表
            </ElButton>
          </div>
        </ElFormItem>

        <ElFormItem
          label="API密钥"
          prop="apiKey"
          :required="formData.provider !== 'custom' && formData.provider !== 'ollama'"
        >
          <ElInput
            v-model="formData.apiKey"
            type="password"
            show-password
            :placeholder="
              formData.apiKey && formData.apiKey.includes('****')
                ? '已脱敏显示；保持不变则沿用原密钥，重新输入则替换'
                : formData.provider === 'custom' || formData.provider === 'ollama'
                  ? '可选填（Ollama 本地服务无鉴权）'
                  : '请输入API密钥'
            "
          />
        </ElFormItem>

        <ElFormItem label="Base URL" prop="baseUrl">
          <ElInput
            v-model="formData.baseUrl"
            placeholder="请填写兼容 OpenAI 协议的 Base URL，通常不包含 /v1 后缀"
          />
        </ElFormItem>

        <ElFormItem
          v-if="formData.modelType === 'CHAT' || formData.modelType === 'MULTIMODAL'"
          label="Completions路径"
          prop="completionsPath"
        >
          <ElInput
            v-model="formData.completionsPath"
            placeholder="附加到base-url的路径。留空则使用默认值/v1/chat/completions"
          />
        </ElFormItem>

        <ElFormItem
          v-if="formData.modelType === 'EMBEDDING'"
          label="Embeddings路径"
          prop="embeddingsPath"
        >
          <ElInput
            v-model="formData.embeddingsPath"
            placeholder="附加到base-url的路径。留空则使用默认值/v1/embeddings"
          />
        </ElFormItem>

        <ElFormItem label="温度" prop="temperature">
          <ElSlider
            v-model="formData.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-input
            show-input-controls
          />
          <div class="text-xs text-gray-500 mt-1">
            建议默认0。控制生成文本的随机性，值越高越随机
          </div>
        </ElFormItem>

        <ElFormItem label="最大Token" prop="maxTokens">
          <ElInputNumber
            v-model="formData.maxTokens"
            :min="100"
            :step="1000"
            style="width: 100%"
          />
          <div class="text-xs text-gray-500 mt-1">
            单位 token（约 0.7 token/汉字）。填多大由你手动设定；超过模型自身上限时
            API 会按模型最大值截断或不报错，请按所用模型的官方输出上限填写
          </div>
        </ElFormItem>

        <ElDivider content-position="left">网络代理配置</ElDivider>

        <ElFormItem label="启用代理">
          <ElSwitch v-model="formData.proxyEnabled" />
          <span class="text-xs text-gray-500 ml-2">
            如果您的服务器处于受限内网，请开启代理以连接 AI 服务
          </span>
        </ElFormItem>

        <template v-if="formData.proxyEnabled">
          <ElFormItem
            label="代理主机"
            prop="proxyHost"
            :required="formData.proxyEnabled"
          >
            <ElInput
              v-model="formData.proxyHost"
              placeholder="例如: 127.0.0.1 或 proxy.example.com"
            />
          </ElFormItem>

          <ElFormItem
            label="代理端口"
            prop="proxyPort"
            :required="formData.proxyEnabled"
          >
            <ElInputNumber
              v-model="formData.proxyPort"
              :min="1"
              :max="65535"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>

          <ElFormItem label="代理用户名" prop="proxyUsername">
            <ElInput
              v-model="formData.proxyUsername"
              placeholder="可选，代理服务器需要认证时填写"
            />
          </ElFormItem>

          <ElFormItem label="代理密码" prop="proxyPassword">
            <ElInput
              v-model="formData.proxyPassword"
              type="password"
              show-password
              placeholder="可选"
            />
          </ElFormItem>
        </template>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditMode ? '更新' : '创建' }}
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.search-section {
  border-radius: 12px;
  margin-bottom: 16px;
}

.search-section :deep(.vben-form) {
  align-items: center;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
</style>
