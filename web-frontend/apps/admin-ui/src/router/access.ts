import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

// import { ElMessage } from 'element-plus';

import { getPrivilegeMenusApi } from '#/api';
import type { ModuleTreeVO } from '#/api/core/menu';
import { BasicLayout, IFrameView } from '#/layouts';
// import { $t } from '#/locales';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/** 将嵌套/平铺菜单统一扁平化，再按 pid 重建树结构 */
function normalizeMenuTree(modules: ModuleTreeVO[]): ModuleTreeVO[] {
  const flatMap = new Map<string, ModuleTreeVO>();

  function collect(list: ModuleTreeVO[]) {
    for (const mod of list) {
      flatMap.set(mod.id, { ...mod, children: [] });
      if (mod.children?.length) {
        collect(mod.children);
      }
    }
  }
  collect(modules);

  const roots: ModuleTreeVO[] = [];
  for (const node of flatMap.values()) {
    if (node.pid && flatMap.has(node.pid)) {
      flatMap.get(node.pid)!.children!.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

/**
 * R-12（v2.1.0）「新窗口菜单」约定：菜单行的 `url` 以 `#/`（站内独立页）或 `http(s)://`（外链）开头时，
 * 该菜单在**新浏览器窗口**打开——不占 admin 内的页签、不叠加后台外壳。
 * 依据 vben `basic/menu/use-navigation.ts`：菜单 path 命中 `isHttpUrl()`（`^https?://`）或路由 meta.link 时
 * 走 `openWindow(path, '_blank')`；**相对链接不满足**（会退化成 router.push），故此处把站内 `#/...`
 * 用**运行时 origin + VITE_BASE** 绝对化（不把 host/port 写进 DB，避免部署绑定）。
 * 此类菜单生成**合成 path**（`/external/<sn>`），避免与独立路由（如 `/front/chat`）撞路径；真实目标放 `meta.link`。
 * 例：智能体中心 `url=#/front/chat` ⇒ `path=/external/AgentChatCenter` + `meta.link=http://<当前host>/#/front/chat`。
 */
const EXTERNAL_LINK_RE = /^(#\/|https?:\/\/)/;

function toAbsoluteLink(url: string): string {
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  // VITE_BASE 生产为 '/' ⇒ prefix='' ；若为子路径（如 '/admin/'）也能正确拼出 /admin/#/...
  const base = import.meta.env.VITE_BASE || '/';
  const prefix = base.endsWith('/') ? base.slice(0, -1) : base;
  const suffix = url.startsWith('#') ? `/${url}` : url;
  return `${window.location.origin}${prefix}${suffix}`;
}

function convertModuleTreeToRoute(
  modules: ModuleTreeVO[],
): RouteRecordStringComponent[] {
  const tree = normalizeMenuTree(modules);

  return tree.map((module) => {
    const isExternalLink = EXTERNAL_LINK_RE.test(module.url ?? '');
    const route: RouteRecordStringComponent = {
      name: module.sn,
      path: isExternalLink ? `/external/${module.sn}` : module.url || '',
      component:
        !isExternalLink && module.type === '1' && module.component
          ? module.component
          : '',
      meta: {
        icon: module.image || undefined,
        order: module.orderNo,
        title: module.name,
        hideInMenu: module.isShow === 0,
        link: isExternalLink ? toAbsoluteLink(module.url!) : undefined,
      },
      children: module.children?.length
        ? convertModuleTreeToRoute(module.children)
        : [],
    };
    return route;
  });
}

/**
 * 添加文件扫描路径，
 * 根据用户角色从后端获取菜单树并生成可访问路由
 * - 后端模式：完全由后端 API 返回的菜单决定路由
 * - 通过 pageMap 将组件路径字符串解析为实际 Vue 组件
 * - 将权限值（pvalues）存入 accessStore 用于按钮级权限控制
 */
async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = {
    ...import.meta.glob('../views/**/*.vue'),
    ...import.meta.glob('../components/**/*.vue'),
  };

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      // ElMessage({
      //   duration: 500,
      //   message: `${$t('common.loadingMenu')}...`,
      // });
      const res = await getPrivilegeMenusApi();
      // 存储权限值（pvalues）到 accessStore，用于前端按钮级权限控制
      if (res.pvalues?.length) {
        const accessStore = useAccessStore();
        accessStore.setAccessCodes(res.pvalues.map((p) => p.name));
      }

      return convertModuleTreeToRoute(res.menus);
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
