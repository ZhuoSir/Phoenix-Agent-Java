import type { Router } from 'vue-router';

/**
 * BUG-88：部署后**旧标签页**的懒加载 chunk 已被新构建替换 → 动态 import 404 → 路由内容区空白
 * （现象：打开页签不显示内容，强制刷新后正常）。
 *
 * <p>策略：识别「chunk 加载失败」类错误 → **整页自动重载一次**（拿到新 index.html 与新 chunk）；
 * 10 秒内只自动重载一次防循环；若仍失败则提示用户手动刷新（不再无限循环）。
 */
const RELOAD_FLAG = 'phoenix:chunk-reload-at';
const RELOAD_GUARD_MS = 10_000;
const CHUNK_ERROR_RE =
  /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|ChunkLoadError|vite:preloadError/i;

function isChunkLoadError(error: unknown): boolean {
  if (!error) {
    return false;
  }
  const text =
    typeof error === 'string'
      ? error
      : `${(error as any)?.name ?? ''} ${(error as any)?.message ?? ''}`;
  return CHUNK_ERROR_RE.test(text);
}

function reloadOnceForChunkError(): boolean {
  try {
    const last = Number(sessionStorage.getItem(RELOAD_FLAG) || 0);
    if (Date.now() - last < RELOAD_GUARD_MS) {
      // 刚重载过仍失败：不循环，交给用户手动刷新（控制台留有原始错误）
      console.error('[chunk-recovery] 自动重载后仍失败，请手动刷新页面');
      return false;
    }
    sessionStorage.setItem(RELOAD_FLAG, String(Date.now()));
  } catch {
    // sessionStorage 不可用时也允许重载一次
  }
  window.location.reload();
  return true;
}

/**
 * 注册 chunk 加载失败自动恢复（在 router 实例创建后调用）。
 */
export function setupChunkRecovery(router: Router) {
  // 懒加载路由 chunk 失败（vue-router 会先接到）
  router.onError((error: unknown) => {
    if (isChunkLoadError(error)) {
      reloadOnceForChunkError();
    }
  });

  // Vite 对 failed dynamic import 的专用事件
  window.addEventListener('vite:preloadError', (event) => {
    (event as Event & { preventDefault?: () => void }).preventDefault?.();
    reloadOnceForChunkError();
  });

  // 兜底：未被捕获的 Promise 拒绝（不同打包器文案不一）
  window.addEventListener('unhandledrejection', (event) => {
    if (isChunkLoadError((event as PromiseRejectionEvent).reason)) {
      event.preventDefault?.();
      reloadOnceForChunkError();
    }
  });

  // 启动成功（路由就绪）→ 清除标记，使下次部署仍能自动恢复一次
  router
    .isReady()
    .then(() => {
      try {
        sessionStorage.removeItem(RELOAD_FLAG);
      } catch {
        // 忽略
      }
    })
    .catch(() => {
      // 忽略
    });
}
