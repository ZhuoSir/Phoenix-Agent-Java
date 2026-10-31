/**
 * 品牌外观（brand-configuration T-05 / T-06）。
 *
 * - 启动时拉取（失败回落默认，绝不阻塞挂载）
 * - 注入：document.title 基础名、favicon、Element Plus 主色 CSS 变量（含派生色阶）
 * - 供模板读取：`brand.systemName` / `brand.logoUrl`
 */
import { reactive, readonly } from 'vue';
import { getBrandConfigApi, type BrandConfig } from '#/api/core/brand-config';

/** 默认值 = 升级前实际表现（与后端默认行一致） */
export const BRAND_DEFAULTS: BrandConfig = {
  systemName: 'Phoenix Agent',
  logoUrl: '/imgs/logo.png',
  faviconUrl: '/favicon.ico',
  primaryColor: '#006BE6',
};

const state = reactive<BrandConfig>({ ...BRAND_DEFAULTS });

export const brand = readonly(state);

/** #RRGGBB → {r,g,b} */
function hexToRgb(hex: string): [number, number, number] {
  const v = hex.replace('#', '');
  return [parseInt(v.slice(0, 2), 16), parseInt(v.slice(2, 4), 16), parseInt(v.slice(4, 6), 16)];
}

/** 与白色混合（ratio=0 原色，1 全白） */
function mixWhite(hex: string, ratio: number): string {
  const [r, g, b] = hexToRgb(hex);
  const f = (c: number) => Math.round(c + (255 - c) * ratio);
  return `rgb(${f(r)}, ${f(g)}, ${f(b)})`;
}

/** 与黑色混合 */
function mixBlack(hex: string, ratio: number): string {
  const [r, g, b] = hexToRgb(hex);
  const f = (c: number) => Math.round(c * (1 - ratio));
  return `rgb(${f(r)}, ${f(g)}, ${f(b)})`;
}

/** 注入 Element Plus 主色与派生色阶（R-03） */
function applyPrimaryColor(hex: string) {
  const root = document.documentElement;
  root.style.setProperty('--el-color-primary', hex);
  root.style.setProperty('--el-color-primary-light-3', mixWhite(hex, 0.3));
  root.style.setProperty('--el-color-primary-light-5', mixWhite(hex, 0.5));
  root.style.setProperty('--el-color-primary-light-7', mixWhite(hex, 0.7));
  root.style.setProperty('--el-color-primary-light-8', mixWhite(hex, 0.8));
  root.style.setProperty('--el-color-primary-light-9', mixWhite(hex, 0.9));
  root.style.setProperty('--el-color-primary-dark-2', mixBlack(hex, 0.2));
}

/** 注入 favicon 与标题基础名 */
function applyIconAndTitle(cfg: BrandConfig) {
  const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (link && cfg.faviconUrl) {
    link.href = cfg.faviconUrl;
  }
  if (cfg.systemName) {
    document.title = `${cfg.systemName} - ${document.title.split(' - ').pop() ?? ''}`;
  }
}

/** 应用品牌（供启动注入与保存后即时预览） */
export function applyBrand(cfg: BrandConfig) {
  Object.assign(state, cfg);
  applyPrimaryColor(cfg.primaryColor);
  applyIconAndTitle(cfg);
  // 让 Vben 的动态标题（路由切换时生成）也使用品牌名：否则每次路由切换会把我们的 title 覆盖回 VITE_APP_TITLE
  // 同时把侧边栏/登录页 logo 指向品牌配置里的 logoUrl：后台「品牌设置」换图后，顶部系统名左侧图标随之生效
  import('@vben/preferences')
    .then(({ updatePreferences }) => {
      const logoUrl = cfg.logoUrl || BRAND_DEFAULTS.logoUrl;
      updatePreferences({
        app: { name: cfg.systemName },
        logo: { enable: true, source: logoUrl, sourceDark: logoUrl },
      });
    })
    .catch(() => {});
}

/**
 * 启动加载：3s 超时，失败保留默认值（R-04 / R-05）。
 */
export async function loadBrand(): Promise<void> {
  try {
    const cfg = await Promise.race([
      getBrandConfigApi(),
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), 3000)),
    ]);
    applyBrand({ ...BRAND_DEFAULTS, ...cfg });
  } catch (e) {
    // 失败回落默认：不得影响登录/使用
    console.warn('[brand] 加载失败，使用默认品牌:', e);
    applyBrand(BRAND_DEFAULTS);
  }
}
