import type { ChatAttachmentMeta } from '@phoenix/chat-shared';

/**
 * 对话附件 API（chat-attachment-understanding T-07）。
 *
 * 鉴权说明：附件端点一律走 `phoenix-token` 头（后端刻意**不支持** `?token=`，避免 token 落访问日志）。
 * 因此缩略图**不能**用 `<img src>` 直连 —— 必须 fetch 成 blob 再 `URL.createObjectURL`（T-05 交接注记）。
 */
const TOKEN_KEY = 'phoenix-token';
const BASE = '/api/chat/attachment';

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { 'phoenix-token': token } : {};
}

export interface ChatAttachmentUploadResult {
  accepted: ChatAttachmentMeta[];
  rejected: Array<{ fileName: string; reason: string }>;
}

function describeHttpError(status: number): string {
  if (status === 401) return '未登录或登录已失效';
  if (status === 403) return '无权访问他人上传的附件';
  if (status === 404) return '附件不存在或原文件已被清理';
  return `请求失败（HTTP ${status}）`;
}

/** 上传附件（multipart，可多个；白名单/大小/数量的最终裁决在后端） */
export async function uploadChatAttachmentsApi(
  files: File[],
  sessionId?: string,
): Promise<ChatAttachmentUploadResult> {
  const form = new FormData();
  if (sessionId) form.append('sessionId', sessionId);
  for (const file of files) form.append('file', file, file.name);
  // 注意：FormData 不可手动设 Content-Type（浏览器需自带 boundary）
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  });
  const json = await res.json().catch(() => ({} as any));
  if (!res.ok || json?.success === false) {
    throw new Error(json?.message || describeHttpError(res.status));
  }
  return {
    accepted: json?.data?.accepted ?? [],
    rejected: json?.data?.rejected ?? [],
  };
}

/** 按会话列出附件（普通用户仅见本人；超管见全部 —— 后端裁决） */
export async function listChatAttachmentsApi(
  sessionId: string,
): Promise<ChatAttachmentMeta[]> {
  const res = await fetch(
    `${BASE}?sessionId=${encodeURIComponent(sessionId)}`,
    { headers: authHeaders() },
  );
  const json = await res.json().catch(() => ({} as any));
  if (!res.ok || json?.success === false) {
    throw new Error(json?.message || describeHttpError(res.status));
  }
  return (json?.data ?? []) as ChatAttachmentMeta[];
}

/** 取原件 Blob（受鉴权） */
export async function fetchAttachmentBlobApi(id: number): Promise<Blob> {
  const res = await fetch(`${BASE}/${id}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(describeHttpError(res.status));
  return res.blob();
}

/**
 * 取缩略图并转为 object URL（**必须走 blob**：`<img src>` 带不了鉴权头）。
 * 非图片类或取图失败返回 null，由调用方回退为文件图标。
 */
export async function fetchAttachmentThumbUrlApi(
  id: number,
): Promise<null | string> {
  try {
    const res = await fetch(`${BASE}/${id}/thumb`, { headers: authHeaders() });
    if (!res.ok) return null;
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  } catch {
    return null;
  }
}

/** 下载原件（blob + a[download]，避免直链绕过鉴权） */
export async function downloadAttachmentApi(
  attachment: ChatAttachmentMeta,
): Promise<void> {
  const blob = await fetchAttachmentBlobApi(attachment.id);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = attachment.fileName || `attachment-${attachment.id}`;
  document.body.append(a);
  a.click();
  a.remove();
  // 释放 object URL，避免内存泄漏
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** 人类可读大小 */
export function formatAttachmentSize(sizeBytes: number): string {
  if (!sizeBytes && sizeBytes !== 0) return '';
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`;
}

/** 预览用的确定性 mime（不信服务器 mime 的怪值：如 md 被 Tika 判成 text/x-web-markdown，Chrome 不认会转下载） */
const PREVIEW_MIME: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  bmp: 'image/bmp',
  pdf: 'application/pdf',
  txt: 'text/plain;charset=utf-8',
  md: 'text/plain;charset=utf-8',
};
/** 浏览器可在线预览的扩展名（其余回退下载） */
const PREVIEWABLE_EXTENSIONS = [
  'png',
  'jpg',
  'jpeg',
  'gif',
  'webp',
  'bmp',
  'pdf',
  'txt',
  'md',
];

/**
 * BUG-157（确认人澄清：R-10「取件」= **展示优先**）：点击附件 = 预览。
 * 图片/PDF/txt/md 经**受鉴权端点**取 blob 后新标签页打开；
 * docx/xlsx 等浏览器无法渲染的格式回退下载，返回 'downloaded' 供调用方提示。
 */
export async function previewAttachmentApi(
  attachment: ChatAttachmentMeta,
): Promise<'downloaded' | 'previewed'> {
  const blob = await fetchAttachmentBlobApi(attachment.id);
  const ext = (attachment.ext || '').toLowerCase();
  if (PREVIEWABLE_EXTENSIONS.includes(ext)) {
    // 强制渲染 mime：避免 text/x-web-markdown 之类 Chrome 不认的类型被转成下载（BUG-157 复验发现）
    const viewBlob = new Blob([blob], { type: PREVIEW_MIME[ext] || blob.type });
    const url = URL.createObjectURL(viewBlob);
    window.open(url, '_blank');
    // 新标签页持有引用，延迟释放避免打开过程中被回收
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return 'previewed';
  }
  await downloadAttachmentApi(attachment);
  return 'downloaded';
}
