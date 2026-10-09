/**
 * 对话附件规则与文案（chat-attachment-understanding R-01~R-04、R-12）。
 *
 * **两端单一来源**：admin-ui 与 mobile-ui 都从这里取白名单/上限/文案，
 * 避免"两端行为漂移"（R-12 要求能力与规则一致，交互形态可不同）。
 * 口径与后端 ChatAttachmentServiceImpl 保持一致（后端为最终裁决方，前端只做即时反馈）。
 */

/** 文字类文档白名单（R-01；.svg 刻意排除 —— 可内嵌脚本，XSS 面） */
export const ATTACHMENT_DOC_EXTENSIONS: readonly string[] = [
  'doc',
  'docx',
  'pdf',
  'xls',
  'xlsx',
  'txt',
  'md',
];

/** 图片白名单（R-02） */
export const ATTACHMENT_IMAGE_EXTENSIONS: readonly string[] = [
  'png',
  'jpg',
  'jpeg',
  'gif',
  'webp',
  'bmp',
];

/** 单文件大小上限（R-04，与后端 MAX_FILE_BYTES 一致） */
export const ATTACHMENT_MAX_FILE_BYTES = 20 * 1024 * 1024;

/** 单次上传数量上限（R-04，与后端 MAX_FILES_PER_REQUEST 一致） */
export const ATTACHMENT_MAX_FILES_PER_SEND = 5;

/** 类型不符的统一文案（R-03） */
export const ATTACHMENT_TYPE_MESSAGE =
  '仅支持文档（word/pdf/excel/txt/md）与常见图片格式（png/jpg/jpeg/gif/webp/bmp）';

/** 取扩展名（小写，无扩展名返回空串） */
export function attachmentExtOf(fileName: string): string {
  const i = fileName.lastIndexOf('.');
  if (i < 0 || i === fileName.length - 1) return '';
  return fileName.slice(i + 1).toLowerCase();
}

/** 扩展名是否在白名单内（前端即时反馈；后端仍会做"扩展名+真实内容类型"双判定） */
export function isAllowedAttachmentName(fileName: string): boolean {
  const ext = attachmentExtOf(fileName);
  return (
    ATTACHMENT_DOC_EXTENSIONS.includes(ext) ||
    ATTACHMENT_IMAGE_EXTENSIONS.includes(ext)
  );
}

/** 判定附件类别（用于选择上传接口后的本地归类与图标渲染） */
export function attachmentKindOf(
  fileName: string,
): 'DOCUMENT' | 'IMAGE' | null {
  const ext = attachmentExtOf(fileName);
  if (ATTACHMENT_IMAGE_EXTENSIONS.includes(ext)) return 'IMAGE';
  if (ATTACHMENT_DOC_EXTENSIONS.includes(ext)) return 'DOCUMENT';
  return null;
}

/** 超限文案（含具体上限值，R-04 要求提示里给出上限） */
export function attachmentSizeMessage(): string {
  return `文件超过大小上限 ${Math.floor(ATTACHMENT_MAX_FILE_BYTES / 1024 / 1024)}MB`;
}

export function attachmentCountMessage(actual: number): string {
  return `单次最多上传 ${ATTACHMENT_MAX_FILES_PER_SEND} 个附件（当前 ${actual} 个）`;
}

/** 本地预校验：返回每个文件的拒绝原因（null = 通过），供两端复用同一规则 */
export function validateAttachmentsLocally(
  files: File[],
): Array<{ file: File; reason: null | string }> {
  return files.map((file) => {
    if (!isAllowedAttachmentName(file.name)) {
      return { file, reason: ATTACHMENT_TYPE_MESSAGE };
    }
    if (file.size > ATTACHMENT_MAX_FILE_BYTES) {
      return { file, reason: attachmentSizeMessage() };
    }
    return { file, reason: null };
  });
}
