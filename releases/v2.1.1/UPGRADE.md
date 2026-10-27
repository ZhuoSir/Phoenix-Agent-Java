# v2.1.1 升级说明

> 适用：v2.1.0 → v2.1.1（PATCH，仅打包链修复）

> **完整实测步骤见 `docs/UPGRADE-2.0.0-to-2.1.1.md`**（2026-10-26 真机演练：安装器 9 步 + 11 项验证 + 回滚可逆）

## 一、升级件

**无**（无 SQL、无配置变更）。

## 最简操作（照抄即可）

**路径不是固定的**：包解到哪儿就 `cd` 到哪儿；`tar` 会自带一层目录 `phoenix-2.1.1-arm64`。

```bash
tar -xzf phoenix-2.1.1-arm64.tar.gz
cd phoenix-2.1.1-arm64
bash upgrade.sh                       # ← 一条命令：自动探测旧安装的项目名与旧 .env
```

看到 **`Phoenix 安装收据 —— 安装成功`** 即完成（镜像、迁移、健康等待、自检都在这一步里）。
想先看会执行什么：`bash upgrade.sh --dry-run`（零副作用）。

> ⚠️ **`upgrade.sh` 是 2026-10-27 补的一键入口**：2026-10-26 打出的那份
> `phoenix-2.1.1-arm64.tar.gz` **包根里没有它**（`package.sh` 当时只拷 install.sh/install.ps1）。
> 拿旧包时改用手填两条参数的 `install.sh`（见下）；重新打包后的包直接 `bash upgrade.sh` 即可。
> 详见 `docs/UPGRADE-2.0.0-to-2.1.1.md` §5（含实测证据：t09 21/21、t07 五项门禁 12/12）。

### 手填两个参数（老包可用）

```bash
# ① 查旧安装的项目名（输出形如 phoenix-backend-1 → 项目名 = phoenix）
docker ps -a --format '{{.Names}}' | grep backend

# ② 查旧安装的 .env 在哪
find / -name .env -path '*docker*' 2>/dev/null | head

# ③ 升级（把两个占位换成上面查到的结果）
bash install.sh --offline --timeout 900 \
  --project  <项目名>          \
  --env-from <旧安装目录>/docker/.env
```

| 参数 | 填什么 | 填错的后果 |
|---|---|---|
| `--project` | 旧安装的项目名（`docker ps` 容器名前缀） | 项目名不对 ⇒ 会新建一套空栈，旧栈还在跑旧版 |
| `--env-from` | 旧安装的 `docker/.env` 绝对路径 | 不给 ⇒ 随机生成库密码，新容器连不上旧数据卷 |

> 其余都可以默认：包目录位置、执行目录、`--timeout`（默认 300，建议给 900）。
> `upgrade.sh` 就是把这两个参数自动查好，查不到时**宁可中止也不猜**（防升错栈/连错库）。

## 二、部署

1. `docker compose -p phx210 down`（保留卷）
2. `docker load -i phoenix-2.1.1-arm64.tar`（或用交付包 `install.sh` 覆盖安装）
3. `docker compose -p phx210 up -d`
4. 等三容器 `healthy`

## 三、验证

| # | 验证 | 期望 |
|---|---|---|
| 1 | `POST /api/privilege/auth/login` | 200 |
| 2 | `GET /` | 200 |
| 3 | `GET /platform/brand-config` | 200 |
| 4 | `GET /icons/lucide.json` | 200 JSON |
| 5 | `GET /js/vendor/echarts.min.js` | 200 |

## 四、回滚

回退镜像到 v2.1.0 即可（无数据变更，无需回滚 SQL）。

## 五、已知限制

沿用 v2.1.0（见 `releases/v2.1.0/RELEASE-NOTES.md` §四之三，`BL-75` 跟进）。
