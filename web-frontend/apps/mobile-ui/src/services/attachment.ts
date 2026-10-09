import type { ChatAttachmentMeta } from '@phoenix/chat-shared';

/**
 * 对话附件 API（chat-attachment-understanding T-08，mobile-ui）。
 *
 * 与 admin-ui 同一后端契约；鉴权走 `phoenix-token` 头（后端不支持 `?token=`，避免 token 落访问日志），
 * 因此缩略图必须 **fetch + blob**（`<img src>` 带不了鉴权头 —— T-05 交接注记）。
 */
const TOKEN_KEY = 'phoenix-token';
const BASE = '/api/chat/attachment';

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { 'phoenix-token': token } : {};
}

export interface AttachmentUploadResult {
  accepted: ChatAttachmentMeta[];
  rejected: Array<{ fileName: string; reason: string }>;
}

function describe(status: number): string {
  if (status === 401) return '未登录或登录已失效';
  if (status === 403) return '无权访问他人上传的附件';
  if (status === 404) return '附件不存在或原文件已被清理';
  return `请求失败（HTTP ${status}）`;
}

/** 上传附件（multipart，可多个；白名单/大小/数量的最终裁决在后端） */
export async function uploadAttachments(
  files: File[],
  sessionId?: string,
): Promise<AttachmentUploadResult> {
  const form = new FormData();
  if (sessionId) form.append('sessionId', sessionId);
  for (const file of files) form.append('file', file, file.name);
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders(), // FormData 不可手动设 Content-Type（需浏览器自带 boundary）
    body: form,
  });
  const json = await res.json().catch(() => ({} as any));
  if (!res.ok || json?.success === false) {
    throw new Error(json?.message || describe(res.status));
  }
  return {
    accepted: json?.data?.accepted ?? [],
    rejected: json?.data?.rejected ?? [],
  };
}

/** 缩略图 object URL（IMAGE 类；失败返回 null，由调用方回退图标） */
export async function fetchThumbUrl(id: number): Promise<null | string> {
  try {
    const res = await fetch(`${BASE}/${id}/thumb`, { headers: authHeaders() });
    if (!res.ok) return null;
    return URL.createObjectURL(await res.blob());
  } catch {
    return null;
  }
}

/** 下载原件（blob + a[download]，不用直链以免绕过鉴权） */
export async function downloadAttachment(
  att: ChatAttachmentMeta,
): Promise<void> {
  const res = await fetch(`${BASE}/${att.id}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(describe(res.status));
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = att.fileName || `attachment-${att.id}`;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
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
 * 可预览格式经**受鉴权端点**取 blob 后新标签页打开；其余回退下载，返回 'downloaded'。
 */
export async function previewAttachment(
  att: ChatAttachmentMeta,
): Promise<'downloaded' | 'previewed'> {
  const res = await fetch(`${BASE}/${att.id}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(describe(res.status));
  const blob = await res.blob();
  const ext = (att.ext || '').toLowerCase();
  if (PREVIEWABLE_EXTENSIONS.includes(ext)) {
    const viewBlob = new Blob([blob], { type: PREVIEW_MIME[ext] || blob.type });
    const url = URL.createObjectURL(viewBlob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return 'previewed';
  }
  await downloadAttachment(att);
  return 'downloaded';
}
