# v2.1.2 发布检查单（M2 冻结 / M3 汇总 / M4 发布）

> 生成 2026-10-31 ｜ 状态：**M3 汇总完成，M4 发布进行中**
> 勾选规则：**勾 = 从事实推导（附判据）；手工勾而无判据 = 无效勾**。演练项为唯一不可推导项，强制留证。

---

## 一、M2 冻结前置检查

- [x] **纳入 spec 已合并**：`specs/20261031_delivery-upgrade-and-brand-icon` 三文档 **v1.0.0 已确认**（陈卓 2026-10-31）；代码提交在 `main` 且为版本分支基点祖先（`8bce9a4d` BUG-213 / `e65760be` BUG-214），文档补记提交在版本分支（`28ea601c` / `a094e68c` / `47f78d5a`）
- [x] **tasks 勾选与延期处置**：14/16 勾选；`T-15` 本轮收口已勾；`T-16`（真实 amd64 端到端复验）标「延期→v2.1.3，原因: 无真实 x86 机器」并写入 MILESTONE §六
- [x] **P0/P1 缺陷**：本版无 P0/P1（三条纳入缺陷均 P2，状态均为「已修复(v2.1.2)」）
- [x] **账证初对**：`git log v2.1.1..2.1.2 --grep "Bug:"` 得 BUG-213 / BUG-214 / BUG-215，全部可在 `bugs.md` 定位且状态 ≥ 已修复
- [x] **无孤儿 spec**：`grep "挂载:.*2\.1\.2" specs/*/changelog.md` 仅命中本 spec，与 MILESTONE 纳入表一一对应
- [x] **无未收口 CR**：本 spec 无 `changes/` 目录（0 份 CR）

## 二、M3 汇总

- [x] **需求挂接对账（审计步骤 0）**：`git log v2.1.1..2.1.2 --grep "Spec:"` 得 3 条（`28ea601c` / `47f78d5a` / `a094e68c`），全部指向同一 spec；MILESTONE 纳入表 1 行 = 事实 1 个，**账平**
- [x] **SQL 汇总**：本版无 SQL（`releases/v2.1.2/sql/` 不存在），数据库台账仍停 `V2.1.0_01~04`
- [x] **配置汇总**：`config/changes.md` 已落盘，结论「无配置变更」，与 artifacts 事实一致（本 spec 未创建 artifacts.md = 无库表/配置件）
- [x] **文档落盘**：`MILESTONE.md` / `RELEASE-NOTES.md` / `UPGRADE.md` / `config/changes.md` / `checklist.md`（本文件）
- [x] **RELEASE-NOTES 覆盖**：纳入需求 1 个（已写「新增能力」节）+ 纳入缺陷 3 条（BUG-213/214/215）全部在册
- [x] **RELEASE-NOTES「修复」节生成**：从 `bugs.md` 过滤「修复版本 = v2.1.2」生成，共 **3 条**
- [x] **UPGRADE 一致性**：无 SQL ⇒ 执行序条数 0 == `sql/` 件数 0；含部署顺序、7 项验证（新增 `/imgs/logo.png` 与「侧边栏随品牌设置变」两条断言）、回滚节、已知限制
- [x] **CR 收口核对**：0 份 CR，无未收口

## 三、验证（实测留档）

### 3.1 交付包与门禁

- [x] **重打包（含 BUG-215）**：`package.sh --arch amd64 --version 2.1.2 --mirror https://docker.1ms.run`
      → **8/8 步完成**，产物 `docker/dist/phoenix-2.1.2-amd64.tar.gz`，sha256 `1a84f672d0e7f2a99240add4f0bf597eb43654070699875d22dd4166805dded0`
- [x] **出货门禁（五项）**：结果 `PASS 通过=5 失败=0 耗时=260s`，证据目录 `docker/dist/gate-2.1.2-amd64-20261031-134927`
- [x] **包内容核验**：`shasum -a 256 -c` OK；包根含 `upgrade.sh`；包内 `docs/INSTALL.md` 有升级节
- [x] **作废包隔离**：不含 BUG-214/215 的旧包已改名 `phoenix-2.1.2-amd64.superseded-ebecb5f6.tar.gz`，防误发

### 3.2 脚本与行为自测

- [x] `t09_upgrade_selftest.sh` → **通过 22 / 失败 0 / 跳过 0**（2026-10-31 真跑，Bash 3.2 判别/探测/拒绝/零副作用/TAG 生效/接线）
- [x] `t07_gate_selftest.sh` → **通过 12 / 失败 0**，含反例「包根缺 `upgrade.sh` ⇒ FAIL item=upgrade-entry」
- [x] 品牌图标端到端（开发栈 9091）：上传 → `/uploads/data-agent/brand/<uuid>.png`；匿名 GET → `200 / image/png`；
      BUG-215 修复后 `/imgs/logo.png` → `200 / image/png / 99560B`（修复前为旧图 4871B 或 HTML 兜底）

### 3.3 演练留证（唯一不可推导项，**空 = 不通过**）

| 项 | 值 |
|---|---|
| 验证人 | agent（自动化门禁 `fresh-db-install` 真装一次）；用户侧目视确认待强刷后回执 |
| 演练环境 | 构建机 arm64 上的隔离栈（`PHX_ALLOW_ARCH_MISMATCH=1` 放行 + 真装真启） |
| 演练日期 | 2026-10-31 |
| 演练结论 | `PASS 通过=5 失败=0 耗时=260s`（门禁 `fresh-db-install` 项 PASS） |

---

## 四、M4 发布

- [ ] 发版落账批处理（tag 前一次写全）：`bugs.md` 本版 3 条「已修复」→「已发布(v2.1.2)+日期」；MILESTONE 纳入 spec 翻终态「已发布(v2.1.2)」+ 状态头「已发布」+ 汇总进度全勾；`version.md` 翻「已发布」；根 `CHANGELOG.md` 增补 v2.1.2 节
- [ ] 四步核验：a) 逐条 grep 回验 + 改动 md 表格闸退出码 0；b) 重跑里程碑审计全绿；c) `git status --porcelain` 干净且落账已提交；d) 三条齐绿
- [ ] 打 tag **`v2.1.2`**（注解 tag，打在版本分支落账 tip）
- [ ] 合并 `2.1.2` → `main`（`--no-ff`）→ 验证 `git diff v2.1.2 main -- releases/v2.1.2/ specs/_project/version.md` **为空**
- [ ] push（**需口令**）→ 远端复核 `git ls-remote` 与远端 MILESTONE 终态

## 五、遗留与后续

| # | 项 | 去向 |
|---|---|---|
| 1 | 真实 amd64 机器端到端复验（T-16） | 延期 → v2.1.3 |
| 2 | Windows 侧 `upgrade.ps1` 一键升级 | 未立项（BL 待记） |
| 3 | 浏览器侧图标目视确认 | 用户强刷回执 |
