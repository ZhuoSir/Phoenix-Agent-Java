/**
 * 品牌外观配置（brand-configuration T-05）。
 *
 * - GET 为匿名接口（登录页渲染前就需要）
 * - PUT / 上传仅超管
 */
export interface BrandConfig {
  systemName: string;
  logoUrl: string;
  faviconUrl: string;
  primaryColor: string;
}

const BASE = '/platform/brand-config';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const resp = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'phoenix-token': localStorage.getItem('phoenix-token') ?? '',
      ...(init?.headers ?? {}),
    },
  });
  const body: any = await resp.json().catch(() => ({}));
  if (!resp.ok || body?.success === false) {
    throw new Error(body?.msg ?? body?.message ?? `请求失败(${resp.status})`);
  }
  return body.data as T;
}

/** 读取公开品牌信息（匿名） */
export function getBrandConfigApi(): Promise<BrandConfig> {
  return request<BrandConfig>(BASE, { method: 'GET' });
}

/** 更新品牌配置（超管） */
export function updateBrandConfigApi(data: Partial<BrandConfig>): Promise<BrandConfig> {
  return request<BrandConfig>(BASE, { method: 'PUT', body: JSON.stringify(data) });
}

/** 上传品牌图（超管，返回可访问 URL） */
export async function uploadBrandAssetApi(file: File): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  const resp = await fetch(`${BASE}/asset`, {
    method: 'POST',
    headers: { 'phoenix-token': localStorage.getItem('phoenix-token') ?? '' },
    body: form,
  });
  const body: any = await resp.json().catch(() => ({}));
  if (!resp.ok || body?.success === false) {
    throw new Error(body?.msg ?? body?.message ?? `上传失败(${resp.status})`);
  }
  return body.data as string;
}
