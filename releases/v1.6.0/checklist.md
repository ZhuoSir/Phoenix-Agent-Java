# v1.6.0 发布检查单（M4）

> 冻结批次（2026-10-05）已完成项打勾；**发版批次待用户口令**（tag / CHANGELOG / push / 合并 main）

## 一、M3 汇总（冻结批次）
- [x] 需求挂接核对：4 个 spec 全部实现完成（mcp-client-tools 9/9、agent-publish-group-grant 7/7、workspace-isolation 12/12、long-turn-resilience 7/8〔T-06→BL-27〕）
- [x] SQL 汇总与 Flyway 风格校对：`V1.6.0_01/02/03` 齐（头部含 版本/序号/类型/来源/前置/可重入/预估），rollback `R1.6.0_01/02/03` 配对
- [x] 零 DDL 声明核对：agent-publish-group-grant、workspace-isolation 均零 DDL（与各自 artifacts 一致）
- [x] 台账在册：`tbl_phoenix_release` 已含 V1.6.0_01/02/03（迁移器真实执行过）
- [x] 文档落盘：`MILESTONE.md`（状态已冻结）/`RELEASE-NOTES.md`/`UPGRADE.md`/`config/changes.md`/`checklist.md`（本件）

## 二、验证（冻结批次，实测留档）
- [x] **全新库全序重放**：临时库 `phx_verify_freeze_1814` — 基线序列/all_data/两个种子 → V1.2.0_01~05 → V1.3.0_01~03 → V1.6.0_01~03，**全绿**；结构断言 三列=3、MCP 三表=3、插件菜单=1、台账 12 行
- [x] **回滚零残留**：逆序 `R1.6.0_03/02/01` 各 exit=0 → 三列=0、三表=0、菜单=0（临时库已 DROP）
- [x] **开发栈 verify 13/13**：`docker/scripts/verify.sh` 逐条 PASS（含部署 BUG-64 修复后复跑）
- [x] **typecheck 基线**：`vue-tsc --noEmit` 213 条**预存**错误（上游 vben 包 + 存量页面）；本版改动文件 **0 新增**
- [x] **线上实测**：backend/nginx healthy；R-06 四项（同会话三工具同处 / 跨会话 0 条 / shell `pwd`=会话目录 / 旧会话可见）；MCP 链（agent36 变体键含 sessionId、35 工具）；join 追流（用户实测通过）；BUG-64（404）；面板噪音（用户确认已消）
- [x] **部署纪律（L-16）**：每次重启前查 `status='generating'` 计数=0；一次因用户长轮活跃**自动推迟到轮末**（后台守候任务 18:03:31 门禁清空后执行）

## 三、发版批次（2026-10-05 用户口令「发版」；仅打包一项经裁决省略）
- [x] 正式包构建：**经用户裁决省略**（2026-10-05 口令「不用打包了，太大了没必要；直接走后面」）——以 tag+镜像即交付；与 v1.5.0「真机演练已做过直接发版」同类先例，如实注记
- [-] 冻结基线上重跑 bootstrap 全链：**随打包一并省略**（无新包则无新 bootstrap 面；v1.5.0 包内脚本原样可用，本版未改脚本）
- [x] `bugs.md`：本版 **18 条**「已验证」→「已发布(v1.6.0)」（实测翻账逐条命中：BUG-64/65/66/67/68/70~82）；BUG-69/BUG-83（延期）、BUG-85（新建）保留现状
- [x] `version.md`：v1.6.0 行「已冻结」→「**已发布(2026-10-05)**」，tag 列填 `v1.6.0`
- [x] tag `v1.6.0`（本提交打注记 tag）+ 根 `CHANGELOG.md` v1.6.0 条目落账
- [ ] **push**：待执行（本地网络 push 不通，走用户手推：`git push origin v1.6.0 && git push origin v1.6.0 --tags`）
- [ ] **「合并到 main 吗？」**：按规矩单独问，未合期间禁从 main 拉包顶版
- [ ] 冻结后新需求口径：**挂下一版 v1.7.0**（v1.6.0 不再收新内容）
