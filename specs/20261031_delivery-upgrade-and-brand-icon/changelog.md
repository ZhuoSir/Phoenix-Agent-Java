# changelog · delivery-upgrade-and-brand-icon

> 挂载: **v2.1.2**（2026-10-31，用户指令「把这个 Upgrade 和 头像的 bug，补充个新的 spec，挂到版本 2.1.2 上，基于 Main 分支」）
> 基点: 版本分支 `2.1.2` 自 `main` @ `6dab10b3` 切出（v2.1.1 已发版并已合 main，无悬空）

## v0.1.0（2026-10-31）— 立项（补记）

**性质**：补记立项。生产代码与交付包已按用户直接指令先行落 `main`（`8bce9a4d` 交付链、`e65760be` 品牌图标、
`4b6b255d` 打包记录、`6dab10b3` 2.1.1→main 合并），本 spec 把两件事纳入 v2.1.2 的账并提供验收依据。
三重确认门对**后续发版动作**（tag / 合 main / push / 出货宣称）继续有效；本目录内文档当前为草稿。

### 起因（两条用户原话，不作引申）

| # | 用户原话 | 归口 |
|---|---|---|
| 1 | 「太磨叽了，没有完整的一键升级操作吗」「你这个太复杂了看不明便」「补充到 upgrade 中」「现在的 upgrade 不能用吗？」 | BUG-213（`upgrade.sh` 对交付包不可用） |
| 2 | 「图标上传后，无法正常显示，看路径应该是被拦截了，返回到了主页，定位下问题」 | BUG-214（存储路径被当 URL 返回） |

### 取证记录（真跑，非推演）

**BUG-213（2026-10-26 实测，2026-10-27 修复）**

```
$ bash docker/scripts/upgrade.sh v2.1.1
[upgrade] 拒绝执行：releases/v2.1.1/sql 不存在（不造第二套流程）      ← PATCH 版被硬门槛挡死
$ grep -n 'TAG=' docker/scripts/upgrade.sh
16:TAG="${IMAGE_TAG:-v1.2.0}"                                        ← 赋值后从未使用（切版本不生效）
$ tar -tzf phoenix-2.1.1-arm64.tar.gz | grep upgrade.sh
phoenix-2.1.1-arm64/docker/scripts/upgrade.sh                        ← 只在 docker/scripts 下，包根没有
```

**BUG-214（2026-10-31 实测，同日修复）**

```
① 上传（修前）  {"code":"100","data":"data-agent/brand/a22cd7cc-….png","success":true}
② 浏览器于是请求 /data-agent/brand/a22cd7cc-….png
   HTTP/1.1 200 OK  →  响应体 <!doctype html><html lang="zh">…      ← SPA 兜底把主页 HTML 发回来了
③ 同一文件正确地址 /uploads/data-agent/brand/a22cd7cc-….png
   HTTP/1.1 200 OK  →  70 字节，file -b = PNG image data, 10 x 10, 8-bit/color RGBA
④ 库内佐证：logo_url / favicon_url 仍是默认值（该值不以 / 或 http(s):// 开头，PUT 校验也会拒）
```

### 修复与实测（2026-10-31）

```
修后 上传      data = "/uploads/data-agent/brand/7bcb9c1a-….png"
修后 匿名 GET  200 / Content-Type: image/png / 70 字节 PNG（登录页无需 token 即可取图）
修后 保存      PUT 成功 → 匿名 GET /platform/brand-config 回显同一地址
兜底 V-8      PUT 传旧格式 "data-agent/brand/….png" → 落库 /uploads/…，日志：
              [brand] 检测到存储相对路径，兜底补访问前缀 /uploads/: data-agent/brand/….png
对照 R-09     头像 POST /api/upload/avatar → /uploads/data-agent/avatars/….png，匿名 200 image/png ✅
              附件 VO 仍只暴露受鉴权端点；WebConfig.DENIED_STATIC_PREFIXES 未改动
还原          品牌值回到修前（Phoenix Agent / /imgs/logo.png / /favicon.ico / #0063e6）；
              本次 3 张验证用临时图（brand×2、avatars×1）已删除
```

### 交付物与门禁

```
包  docker/dist/phoenix-2.1.2-amd64.tar.gz  1.8G
sha256 ebecb5f69ce0937a09af515318f063abb38bc4fe834cfbff9fc6036abc58bcc6   （shasum -c → OK）
manifest git=v2.1.1-7-ge65760be  arch=amd64
门禁 结果=PASS 通过=5 失败=0 耗时=247s（含新增项 upgrade-entry + fresh-db-install 空库真装；门禁栈零残留）
包内核验 upgrade.sh 存在且与仓库 HEAD 同 sha256 前缀（7593200ec875df00）；docs/INSTALL.md 含升级节 4 处引用
作废 2026-10-31 09:25 那份 sha256=078c51f8…（不含图标修复）
```

### 自测与回归

```
bash docker/scripts/tests/t09_upgrade_selftest.sh  → 通过 22 / 失败 0 / 跳过 0
PHX_TEST_PKG=…2.1.1… bash docker/scripts/tests/t07_gate_selftest.sh → 通过 12 / 失败 0（含反例「包根缺 upgrade.sh ⇒ FAIL」）
既有回归 t01 18/0、t05 9/0、t08 10/0；t02/t06 因缺载荷源与目标容器未起而**未能运行**（环境前置，非本次改动致）
mdtable_check.py 对全部改动 md 全绿
```

### 环境插曲（如实）

1. 为打包启动 Docker Desktop（此前 daemon 不在），并重启了开发栈 `phx210`（9091）—— 用户实测期间的动栈，已声明（L-16）。
2. `docker.1panel.live` 源已不可达（10-26 时可用）→ 换 `https://docker.1ms.run` 成功；
   `~/.phoenix/*.log` 在受限环境写入被拒会被误报为「镜像源传输质量差」→ `PHX_LOG_FILE` 指到工作区内即正常（两条已记 v2.1.1 MILESTONE §七）。

### 台账联动

- `BUG-213` → 已修复(v2.1.2)（随包重打生效）；`BUG-214` → 已修复(v2.1.2)，均关联本 spec
- 新坑：`L-114`（仓库里有脚本 ≠ 客户机能用）、`L-115`（上传验收必须 GET 回来的 URL 断言是图片）
- `releases/v2.1.2/MILESTONE.md` 建账；`specs/_project/version.md` 补 v2.1.1/v2.1.2 两行并纠正 v2.1.0 状态

### 待办

- [ ] 确认①（requirements v0.1.0 → v1.0.0）
- [ ] 确认②（plan）
- [ ] 确认③（tasks）
- [ ] M3 汇总（`RELEASE-NOTES.md` / `UPGRADE.md`）与 M4 发版（tag / 合 main / push）——**均需口令**
- [ ] T-16：真实 amd64 机器端到端复验（本轮构建机为 arm64，门禁以 `--allow-arch-mismatch` 放行）
- [ ] Q1：是否补打 arm64 包；Q2：作废的 `078c51f8…` 包是否从 `docker/dist` 移走
