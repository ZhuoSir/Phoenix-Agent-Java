# v2.1.2 升级说明

> 适用：v2.1.1 → v2.1.2（PATCH，交付链与品牌图标修复）｜ 部署基线：tag `v2.1.2`
> 上一版手册仍可参考：`docs/UPGRADE-2.0.0-to-2.1.1.md`（安装器 9 步 + 11 项验证 + 回滚可逆的真机演练记录）

## 一、升级件

**无**（无 SQL、无配置变更）。数据库台账仍停 `V2.1.0_01~04`（4 行）。

## 二、最简操作（照抄即可）

**路径不固定**：包解到哪儿就 `cd` 到哪儿；`tar` 自带一层目录 `phoenix-2.1.2-amd64`。

```bash
tar -xzf phoenix-2.1.2-amd64.tar.gz
cd phoenix-2.1.2-amd64
bash upgrade.sh --dry-run     # 先看会执行什么（零副作用，可选）
bash upgrade.sh               # ← 一条命令：自动探测旧安装的项目名与旧 .env
```

看到 **`Phoenix 安装收据 —— 安装成功`** 即完成（载镜像、承接 env、重建容器、迁移、健康等待、自检都在这一步里）。

`upgrade.sh` 查不到目标时**宁可中止也不猜**：

- 机器上存在两套及以上 Phoenix 栈 → 列出「容器名 → 项目名」，要你补 `--project`；
- 找不到旧 `docker/.env` → 中止并提示 `--env-from`（缺它会随机生成库密码 ⇒ 新容器连不上旧数据卷）。

### 手填两个参数（异常场景 / 旧包兜底）

```bash
docker ps -a --format '{{.Names}}' | grep backend     # ① 查旧项目名（phoenix-backend-1 → phoenix）
find / -name .env -path '*docker*' 2>/dev/null | head # ② 查旧 .env 路径
bash install.sh --offline --timeout 900 \
  --project  <项目名> \
  --env-from <旧安装目录>/docker/.env
```

| 参数 | 填什么 | 填错的后果 |
|---|---|---|
| `--project` | 旧安装的 compose 项目名（容器名前缀） | 项目名不对 ⇒ 新建一套空栈，旧栈仍在跑旧版 |
| `--env-from` | 旧安装的 `docker/.env` 绝对路径 | 不给 ⇒ 随机库密码，新容器连不上旧数据卷 |

## 三、部署顺序（手工路径，可选）

1. `docker compose -p <项目名> down`（保留卷）
2. `docker load -i phoenix-2.1.2-amd64.tar`（或直接用 `install.sh --offline` 覆盖安装）
3. `docker compose -p <项目名> up -d`
4. 等 postgres / redis / backend 三容器 `healthy`

## 四、验证

| # | 验证 | 期望 |
|---|---|---|
| 1 | `GET /` | 200（前端首页） |
| 2 | `POST /api/privilege/auth/login` | 200（账号可登录） |
| 3 | `GET /platform/brand-config` | 200，`logoUrl` 以 `/uploads/` 或 `http(s)://` 开头 |
| 4 | 匿名 `GET <logoUrl>` | 200 且 `Content-Type: image/*`（登录页要能显示） |
| 5 | `GET /imgs/logo.png` | 200 且 `Content-Type: image/png`（**新增断言**：修复前该请求可能命中 SPA 兜底返回 HTML） |
| 6 | 外观设置上传 Logo → 保存 → 强刷 | 侧边栏（系统名左侧）、登录页品牌图、浏览器标签 favicon **同时**变（**新增断言**，BUG-215） |
| 7 | `docker ps` 镜像 tag | 全部为 2.1.2（`upgrade.sh` 的 TAG 生效验证，BUG-213） |

## 五、回滚

回退镜像 tag 到 v2.1.1 并重建容器即可（无 SQL、无配置、无数据变更，无需回滚脚本）：

```bash
IMAGE_TAG=2.1.1 docker compose -p <项目名> up -d --no-deps backend nginx
```

## 六、已知限制

1. **未在真实 amd64 机器端到端复验**：本轮构建机为 arm64，出货门禁的 `fresh-db-install` 以 `PHX_ALLOW_ARCH_MISMATCH=1` 放行（模拟执行）。
   仍**建议**出货前按本文件在真实 x86 机器跑一遍；对应 spec 任务 `T-16` 已由用户 2026-10-31 口令拍板**作废**（不再作为待办追），但不等于已验证——本条仍是已知限制，不是完成项。
2. **Windows 侧无一键升级**：`install.ps1` 只覆盖安装；升级需进 WSL 执行 `bash upgrade.sh`。
3. 若现场把 `phoenix.data.file.url-prefix` 改成非 `/uploads`，需同步 nginx `location /uploads`（本版未加自动断言）。
4. 浏览器侧图标缓存：换图后建议 `Cmd/Ctrl+Shift+R` 强刷；品牌图 URL 为 uuid 命名，正常不会命中旧缓存。
