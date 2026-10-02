/**
 * 流式回答本地快照（BUG-53 前端保底 A′）：生成中每 ~400ms 写 sessionStorage；
 * 刷新/崩溃后重进会话回显半截内容并标注「已中断」。服务端执行仍随连接取消，
 * 续跑能力由 detached-stream spec（B 方案）另行解决。
 */
export interface StreamSnapshot {
  contentHtml: string;
  thinking?: string;
  ts: number;
}

const KEY_PREFIX = 'phoenix:stream-snap:';
export const SNAPSHOT_TTL_MS = 120_000;

let lastWrite = 0;

export function saveStreamSnapshot(sessionId: string, contentHtml: string, thinking?: string) {
  const now = Date.now();
  if (now - lastWrite < 400) return;
  lastWrite = now;
  try {
    sessionStorage.setItem(KEY_PREFIX + sessionId, JSON.stringify({ contentHtml, thinking, ts: now }));
  } catch { /* 配额/隐私模式静默 */ }
}

export function forceClearStreamSnapshot(sessionId: string) {
  lastWrite = 0;
  try {
    sessionStorage.removeItem(KEY_PREFIX + sessionId);
  } catch { /* ignore */ }
}

export function readStreamSnapshot(sessionId: string): StreamSnapshot | null {
  try {
    const raw = sessionStorage.getItem(KEY_PREFIX + sessionId);
    if (!raw) return null;
    const snap = JSON.parse(raw) as StreamSnapshot;
    if (!snap || typeof snap.contentHtml !== 'string' || Date.now() - snap.ts > SNAPSHOT_TTL_MS) {
      forceClearStreamSnapshot(sessionId);
      return null;
    }
    if (!snap.contentHtml.trim() && !snap.thinking) {
      forceClearStreamSnapshot(sessionId);
      return null;
    }
    return snap;
  } catch {
    return null;
  }
}
