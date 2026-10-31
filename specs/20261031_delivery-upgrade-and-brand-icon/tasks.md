> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-31 | 确认人: 陈卓 | 确认日期: 2026-10-31

# 任务拆解：一键升级入口 + 品牌图标可访问

> 施工说明：本 spec 为**补记立项**（用户 2026-10-31 指令：把 upgrade 与图标 bug 立成 spec 挂 v2.1.2）。
> 代码与交付包已先行落 main（用户直接指令「定位下问题」「快速打包」），下方已勾任务的验证输出均为真跑结果，
> 未勾项为本 spec 收口仍需完成的动作。**三重确认门对后续发版动作（tag / 合 main / push / 出货）继续有效。**

## 组 1 · 升级入口（交付包侧）

- [x] T-01 把 `docker/scripts/upgrade.sh` 重写为「场景自动判别」统一入口：包根有 `install.sh`+`manifest.json` ⇒ 交付包模式转调 `install.sh`；否则 ⇒ 仓库开发栈模式
  关联: R-01 R-04 　依赖: 无
  验证方式: `bash -n`；`t09` 场景 1/6/7 断言场景判别与命令组装（`t09_upgrade_selftest.sh`）
  验收标准: 包根执行时打印「场景 = 交付包升级」并组装出 `bash install.sh --offline --timeout 900 --project <名> --env-from <路径>`；仓库执行时打印「场景 = 仓库开发栈」；bash 3.2（`/bin/bash`）下可跑
  落地: commit `8bce9a4d`

- [x] T-02 实现项目名与旧 `.env` 的自动探测 + **不确定即中止**：多栈并存列「容器 → 项目名」并拒绝猜测；找不到旧 `.env` 时中止并说明后果与补救命令
  关联: R-02 　依赖: T-01
  验证方式: 本机真实容器（5 套栈并存）跑无参 `--dry-run`；`--project phxupg`（其 compose 目录已被系统清掉）触发缺 `.env` 分支；`t09` 场景 2/3
  验收标准: 两种情形均**非 0 退出**且提示含可直接复制的下一步命令；未出现"随机生成密码继续装"的路径
  落地: commit `8bce9a4d`（实测输出见 `changelog.md` 2026-10-31 条目）

- [x] T-03 `--dry-run` 零副作用 + `--online` 去 `--offline` + 未识别参数连同取值原样转交 `install.sh`
  关联: R-03 R-08 　依赖: T-01
  验证方式: `t09` 场景 1d/1e（包目录 4 项不变、无状态文件）、场景 5a/5b（`--port 8777` 不被当版本号吞掉；`--online` 时命令中无 `--offline`）
  验收标准: 三条断言全绿；dry-run 后 `git status`/载荷目录无任何新增文件

- [x] T-04 仓库开发栈模式去掉「`releases/<VER>/sql` 不存在即拒绝执行」硬门槛，并让镜像 tag 真正生效（修原死变量 `TAG`）
  关联: R-04 　依赖: T-01
  验证方式: `t09` 场景 6/7 干跑对比：`v2.1.1`（PATCH 无 SQL）→ 打印「跳过迁移」且无 migrator 行、命令含 `IMAGE_TAG=2.1.1`；`v2.1.0`（有 SQL）→ 含 `releases/v2.1.0/sql` 与 `migrator-post`；并断言 dry-run 未改写 `docker/.env`（md5 前后一致）
  验收标准: 上述 5 条断言全绿（对应 BUG-213 的 ①②）

- [x] T-05 交付链接线：`package.sh` 把 `upgrade.sh` 与 `install.sh` 同级拷入**包根**
  关联: R-05 　依赖: T-01
  验证方式: `t09` 9a（grep 入包清单）+ 真包核验：`tar -tzf phoenix-2.1.2-amd64.tar.gz | grep upgrade.sh`，并比对包内文件与仓库 HEAD 的 sha256 前缀一致（`7593200ec875df00` = `7593200ec875df00`）
  验收标准: 包根存在 `upgrade.sh`（对应 BUG-213 的 ③）

- [x] T-06 出货门禁四项 → 五项：新增 `upgrade-entry`（包根缺脚本 / dry-run 组装不出 install.sh 命令 / 出现副作用 ⇒ FAIL）
  关联: R-05 　依赖: T-03, T-05
  验证方式: ① 假载荷快跑 `gate.sh --skip-install` 看该项 PASS；② `t07_gate_selftest` 正例断言 `通过=5 失败=0`；③ `t07` 新增反例 C：删掉包根 `upgrade.sh` ⇒ `门禁 FAIL item=upgrade-entry`
  验收标准: 三处结果符合预期；`gate.sh -h` 行号范围同步修正（打印不串位）

- [x] T-07 新增自测 `docker/scripts/tests/t09_upgrade_selftest.sh`（覆盖 R-01~R-05 可判定的全部行为）
  关联: R-01 R-02 R-03 R-04 R-05 　依赖: T-01~T-06
  验证方式: `bash docker/scripts/tests/t09_upgrade_selftest.sh` → 退出码 0
  验收标准: **通过 22 / 失败 0 / 跳过 0**（每场景独立载荷副本，L-102）

- [x] T-08 真包回归：在**真实 2.1.1 交付包解包载荷**上跑五项门禁（含 `fresh-db-install` 空库真装 + 零残留核验）
  关联: R-05 R-08 　依赖: T-06
  验证方式: `PHX_TEST_PKG=docker/dist/phoenix-2.1.1-arm64.tar.gz bash docker/scripts/tests/t07_gate_selftest.sh`
  验收标准: **通过 12 / 失败 0**；日志含「门禁结束 结果=PASS 通过=5 失败=0」；「门禁栈已清理（零残留容器）」

## 组 2 · 品牌图标可显示

- [x] T-09 取证并定位：对比「上传返回值 / 浏览器实际请求 / 正确静态面」三方案，确认根因是**存储路径被当 URL**
  关联: R-06 　依赖: 无
  验证方式: 真栈 curl 三连（修前）：上传返回 `data-agent/brand/a22cd7cc-….png`；请求 `/data-agent/brand/…` → `200` 但响应体 `<!doctype html>`；请求 `/uploads/data-agent/brand/…` → `200`、70 字节、`PNG image data, 10 x 10`
  验收标准: 三条命令输出留档（`changelog.md`），结论可判定；同时核对头像上传链路（`/api/upload/avatar`）为正确写法，作为对照

- [x] T-10 修复：`BrandAssetServiceImpl.upload()` 在 `storeFile()` 之后过一道 `getFileUrl()`，返回可访问 URL（本地 `/uploads/…`，OSS 返回对象域名）
  关联: R-06 　依赖: T-09
  验证方式: 重建 jar（maven 容器 `--target jar`）→ `docker cp` 进开发栈 backend → 重启至 healthy → 上传 70 字节 png 断言返回 `/uploads/data-agent/brand/<uuid>.png`；再**匿名**（不带 token）GET 该地址断言 `Content-Type: image/png` + 70 字节 + `file -b` = PNG image data
  验收标准: 修后两条断言全绿；后端日志出现 `[brand] 上传 … path=…, url=/uploads/…`（同时含 path 与 url，便于现场核对）

- [x] T-11 兜底：`BrandConfigServiceImpl` 读写两侧加 `toAccessUrl()`——「裸存储路径」自动补 `/uploads/` 前缀并打 INFO 日志；其余形态维持原校验
  关联: R-07 　依赖: T-10
  验证方式: PUT 传旧格式 `data-agent/brand/<uuid>.png` → 断言落库/回显为 `/uploads/data-agent/brand/<uuid>.png`，日志含「检测到存储相对路径，兜底补访问前缀 /uploads/: …」；同时确认默认值 `/imgs/logo.png`、`/favicon.ico` 不被误改
  验收标准: 两项断言 + 默认值不回归（匿名 `GET /platform/brand-config` 回显一致）

- [x] T-12 共享面不回归核验（R-09）：头像、会话附件、知识库原件三处
  关联: R-09 　依赖: T-10
  验证方式: `POST /api/upload/avatar` 返回 `/uploads/data-agent/avatars/….png` 且匿名 GET 得 `200 image/png`；代码核对 `ChatAttachmentVO.url` 仍指受鉴权端点、`WebConfig.DENIED_STATIC_PREFIXES` 未改动
  验收标准: 头像实测通过；另两处为"未触碰 + 语义核对"，无新增暴露面；验证产生的临时图已删除

## 组 3 · 交付与收尾

- [x] T-13 打出含本修复的交付包并过门禁：`phoenix-2.1.2-amd64.tar.gz`
  关联: R-05 　依赖: T-08, T-10
  验证方式: `package.sh --arch amd64 --version 2.1.2 --mirror https://docker.1ms.run` 八步全通过；`shasum -a 256 -c` → OK；门禁日志 `结果=PASS 通过=5 失败=0 耗时=247s`；manifest `git=v2.1.1-7-ge65760be`、`arch=amd64`
  验收标准: 收据 sha256 = `ebecb5f69ce0937a09af515318f063abb38bc4fe834cfbff9fc6036abc58bcc6`（1.8G）；**作废** 09:25 那份（`078c51f8…`，不含图标修复）并已在台账注明
  备注: 中途 `docker.1panel.live` 源不可达、Docker Desktop 退出各致一次失败，换源/重启后续跑步骤 4-8 通过

- [x] T-14 文档与台账落地（用户可见口径 + 账）
  关联: R-01 R-08 　依赖: T-13
  验证方式: 升级手册 `docs/UPGRADE-2.0.0-to-2.1.1.md` §最简操作/§5、`releases/v2.1.1/UPGRADE.md`、包内 `docker/payload-docs/INSTALL.md`（升级节）、`docker/README.md` §6、`docker/scripts/README.md` 脚本地图；台账 `BUG-213`/`BUG-214`/`L-114`/`L-115`/`releases/v2.1.1/MILESTONE.md` §七
  验收标准: `mdtable_check.py` 对全部改动 md 全绿；手册里每条命令都在目标形态（包内/仓库）实测过，未实测处显式写明

- [x] T-15 spec 三重确认 + v2.1.2 收口动作（2026-10-31 用户口令「2.1.2 走发版流程，然后合并Main」；三文档 v1.0.0 已确认，确认人 陈卓）
  关联: 全部 　依赖: T-14
  验证方式: requirements/plan/tasks 三份版本头翻「已确认」并记确认人；随后按口令执行 `releases/v2.1.2/RELEASE-NOTES.md` + `UPGRADE.md` 汇总（M3）、打 tag `v2.1.2`、合 main、push（M4）
  验收标准: 账先 tag 后（四步核验通过才打 tag）；未收口前 v2.1.2 保持「在途」，不对外宣称已发布

- [ ] T-16 真实 amd64 机器上端到端复验一次升级（含图标上传显示）（延期→v2.1.3，原因: 本轮仍无真实 x86 机器；构建机 arm64、门禁 allow-arch-mismatch 放行）
  关联: R-01 R-06 　依赖: T-15
  验证方式: 目标机 `bash upgrade.sh` 走到收据；外观设置上传 Logo → 页面即时显示、刷新后登录页显示；`select seq from tbl_phoenix_release` 台账 4 行
  验收标准: 收据 + 图标显示 + 台账三项齐备即记「已验证」；本轮构建机为 arm64（门禁以 allow-arch-mismatch 放行），故本项不完成不得对外称"amd 已实测"（L-12）

## R 覆盖自检

| 需求 | 覆盖任务 |
|---|---|
| R-01 | T-01, T-02, T-08, T-16 |
| R-02 | T-02, T-07 |
| R-03 | T-03, T-06, T-07 |
| R-04 | T-01, T-04 |
| R-05 | T-05, T-06, T-08, T-13 |
| R-06 | T-09, T-10, T-16 |
| R-07 | T-11 |
| R-08 | T-03, T-08, T-14 |
| R-09 | T-12 |

反向检查：16 个任务全部有回指的 R，无超范围任务；无占位任务。
