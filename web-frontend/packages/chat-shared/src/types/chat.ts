export type ChatRole = 'assistant' | 'user';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  /** 创建时间戳 */
  createdAt: number;
  /** 流式中标记，true 时 UI 显示打字光标；预留给后续接入 */
  streaming?: boolean;
  /** 消息类型：text | html | result-set | markdown-report | html-report */
  messageType?: string;
  /** 消息附加元数据 */
  metadata?: any;
  /** 深度思考全文（thinking-display R-02；持久于 metadata.thinking） */
  thinking?: string;
  /** 思考耗时毫秒 */
  thinkingMs?: number;
  /**
   * 消息附件（chat-attachment-understanding R-10）。
   * **可选字段** ⇒ 旧消息（无该键）与两端既有渲染均不受影响（共享面 S5/S6）。
   */
  attachments?: ChatAttachmentMeta[];
}

/** 对话附件元信息（后端 ChatAttachmentVO 的前端映射） */
export interface ChatAttachmentMeta {
  id: number;
  fileName: string;
  /** DOCUMENT = 文字类文档；IMAGE = 图片 */
  kind: 'DOCUMENT' | 'IMAGE';
  ext: string;
  sizeBytes: number;
  /** 受鉴权的取件端点（后端刻意不暴露存储直链，R-11） */
  url?: string;
  /** 缩略图端点（仅 IMAGE 非空）；必须 fetch+blob 加载（<img src> 带不了鉴权头） */
  thumbUrl?: null | string;
  /** ACTIVE | EXTRACT_FAILED（R-09：解析失败的文档不可用于生成） */
  extractStatus?: string;
  /** 用户可见提示：解析失败原因类别，或"内容已截断"告知（R-08/R-09） */
  notice?: null | string;
}

export interface ChatSession {
  id: string;
  /** 标题，新建会话时可由首条消息推断 */
  title: string;
  /** 末条消息预览 */
  preview: string;
  /** 关联的 agent id */
  agentId: string;
  /** 最近一次更新时间戳（用于排序 / 展示） */
  updatedAt: number;
  /** 是否置顶 */
  isPinned?: boolean;
}

export interface SendPayload {
  sessionId: string;
  content: string;
  agentId?: string;
}
