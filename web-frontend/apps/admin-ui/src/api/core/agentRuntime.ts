import { requestClient } from '#/api/request';

/** 对话智能体运行配置（R-03/R-05/R-09）：库配置驱动运行时构建 */
export interface AgentRuntimeConfig {
  agentId?: number;
  /** 对话模型配置ID；不传/为空=使用默认模型 */
  modelConfigId?: number | null;
  planMode?: boolean;
  memoryEnabled?: boolean;
  knowledgeEnabled?: boolean;
  /** 知识库检索召回条数 1~50 */
  knowledgeTopK?: number;
  /** 工具迭代上限 1~100；null/undefined=系统默认（runtime-max-iterations） */
  maxIterations?: null | number;
  /** 上下文治理（R-05）：压缩触发 token 数；null/undefined=全局默认（当前 102400 ≈ 0.8×128k，DSH 换算） */
  compactionTriggerTokens?: null | number;
  /** 上下文治理（R-05）：压缩后保留消息条数；null/undefined=全局默认（当前 20） */
  compactionKeepMessages?: null | number;
  /** 上下文治理（R-05）：单个工具结果最大字符数，超出走回收（不删，落盘可回读）；null/undefined=全局默认（当前 8192） */
  toolResultMaxChars?: null | number;
  /** 知识库检索相似度阈值 0~1 */
  knowledgeSimilarityThreshold?: number;
  dbQueryEnabled?: boolean;
  dbDeepAnalysisEnabled?: boolean;
  /** 数据库类工具目标数据源 */
  datasourceId?: number | null;
  /** 文件系统策略 local/remote */
  filesystemPolicy?: string;
}

/** 构建预演结果：按当前配置真实构建一次，回显生效工具/技能池/实例来源 */
export interface AgentRuntimePreview {
  agentId: number;
  sn?: null | string;
  runtimeKey?: string;
  buildOk?: boolean;
  errorMessage?: null | string;
  instanceSource?: string;
  summary?: string;
  registryStats?: string;
  toolNames?: string[];
  skillNames?: string[];
  skillPoolSize?: number;
  modelConfigId?: null | number;
  planMode?: boolean;
  memoryEnabled?: boolean;
  knowledgeEnabled?: boolean;
  dbQueryEnabled?: boolean;
  dbDeepAnalysisEnabled?: boolean;
  datasourceId?: null | number;
  filesystemPolicy?: string;
}

/** 后端统一返回体（ReturnVo） */
export interface ApiResult<T> {
  code: string;
  data: T;
  msg: string;
  success: boolean;
}

const API_BASE_URL = '/api/agent';

export async function getAgentRuntimeConfigApi(agentId: number) {
  return requestClient.get<ApiResult<AgentRuntimeConfig>>(
    `${API_BASE_URL}/${agentId}/runtime-config`,
    { responseReturn: 'body' },
  );
}

export async function saveAgentRuntimeConfigApi(
  agentId: number,
  config: AgentRuntimeConfig,
) {
  return requestClient.put<ApiResult<boolean>>(
    `${API_BASE_URL}/${agentId}/runtime-config`,
    config,
    { responseReturn: 'body' },
  );
}

export async function previewAgentRuntimeApi(agentId: number) {
  return requestClient.get<ApiResult<AgentRuntimePreview>>(
    `${API_BASE_URL}/${agentId}/runtime-config/preview`,
    { responseReturn: 'body' },
  );
}
