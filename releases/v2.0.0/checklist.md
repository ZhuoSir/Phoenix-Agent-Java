# v2.0.0 发布检查单（M3 汇总 / M4 发布）

> 生成 2026-10-10 | 状态：**M3 汇总完成，M4 发布待口令**
> 勾选规则：**勾 = 从事实推导（附判据）；手工勾而无判据 = 无效勾**。演练项为唯一不可推导项，强制留证。

---

## 一、M3 汇总（冻结批次）

- [x] **需求挂接核对**：`git log v1.7.0..v2.0.0 --grep "Spec:"` footer 集合 ∪ `specs/*/changelog.md` 挂载行，与 MILESTONE 纳入表一致（3 个 spec：`user-role-group-model` / `chat-attachment-understanding` / `ollama-model-support`）
- [x] **SQL 汇总与 Flyway 风格校对**：`sql/V2.0.0_01~16` 齐（**16 件**），`sql/rollback/` **16 件一一配对**，件头注释块（版本/序号/类型/来源/前置/可重入/回滚）齐全
- [x] **配置汇总**：`config/changes.md` 已落盘，覆盖 1 处配置变更（compose 新增 `pylibs` 卷，来源 BUG-164）；`artifacts.md` 配置行数为 0，与其一致
- [x] **文档落盘**：`MILESTONE.md` / `RELEASE-NOTES.md` / `UPGRADE.md` / `config/changes.md` / `checklist.md`（本文件）
- [x] **RELEASE-NOTES「包含需求」覆盖**：3 个 spec 全部在册，完成度与 tasks.md 事实一致（28/28、12/12、13/13）
- [x] **RELEASE-NOTES「修复」节生成**：从 `bugs.md` 过滤「修复版本 = v2.0.0」生成，共 **44 条**（已验证 24 / 已修复待验证 18 / 未修复或转办 2）
- [x] **UPGRADE 执行序一致性**：执行序条数 **16** == `sql/` 正向件数 16；含配置变更步骤、逐项验证（13 项）、**逆序回滚节（16 → 01）**
- [x] **CR 收口核对**：4 份 CR（`user-role-group-model` CR-01/CR-02、`chat-attachment-understanding` CR-01/CR-03）**合入八动作全勾**，0 未收口
- [x] **账证初对**：本版提交的 `Bug:` footer 全部可在 `bugs.md` 定位（旧号 165~173 经「编号迁移（2026-10-10）」注记 + 各行「关联」列旧号标记，+13 一一对应）

---

## 二、验证（实测留档）

### 2.1 本次部署实测（2026-10-10，本机栈）

- [x] 后端编译打包：`mvn package -DskipTests -pl phoenix-admin/phoenix-admin-manager -am` → **BUILD SUCCESS**（19 模块，2 分 22 秒，javac release 21）
- [x] 前端构建：`pnpm -F @vben/web-ele build` → **✓ built**（exit=0）；产物断言 `dist/` 含 `ollama` ✓
- [x] 镜像重建：`phoenix-backend:v2.0.0`（重层全部 CACHED，仅重跑 jar 拷贝层）+ `phoenix-frontend:v2.0.0`
- [x] 服务重启：`docker compose up -d` → backend/nginx/postgres/redis **healthy**、migrator `exited(0)`
- [x] 健康检查：`/echo/ok` → **200**；前端 `/` → **200**
- [x] 登录：admin 登录成功（业务码 100）
- [x] 真实智能体调用：智能体①对话正常返回（40 秒）
- [x] 产物一致性：**运行容器内 jar 的 sha256 == 本地构建产物 sha256**（`5266dd1809b3460c`）；容器内 jar 含 `ToolLoopBreakerMiddleware` / `OllamaApiClient` / `EmbeddingDimensionResolver`
- [x] 部署前置纪律：重启前确认无在跑会话（`status=generating` 近 10 分钟 = 0）；旧容器日志先备份（19,794 行）

### 2.2 端到端验收（**待做**）

- [ ] **发布演练**：在演练库/演练环境按 `UPGRADE.md` 全流程跑一遍（含 01~16 正序 + 16→01 逆序）
- [ ] **第一~三节 13 项验证**（`UPGRADE.md` 第三节）逐项实测留证
- [ ] **P0/P1 缺陷验证**（**当前发版硬阻断**，见第四节）

### 2.3 演练留证（唯一不可推导项，**空 = 不通过**）

| 项 | 值 |
|---|---|
| 验证人 | 陈卓（2026-10-07 选定「隔离栈 + 真实浏览器」方案并参与 UI 断言） |
| 演练环境 | **待填**（隔离栈 + 演练库） |
| 演练日期 | **待填** |
| 演练结论 | **待填** |

---

## 三、M4 发布（**待用户口令**）

- [ ] 冻结 v2.0.0（MILESTONE 状态头 → 已冻结）
- [ ] 发版落账批处理（tag 前一次写全）：`bugs.md` 本版「已验证」→「已发布(v2.0.0)+日期」；`backlog.md` 本版已立项 spec →「已交付(v2.0.0)」；MILESTONE 各 spec → 终态「已发布(v2.0.0)」+ 状态头「已发布」+ 汇总进度全勾；`version.md` → 已发版；根 `CHANGELOG.md` 增补 v2.0.0 节
- [ ] 四步核验（tag 硬门）：a) 逐条 grep 回验 + 全部改动 md 跑 `mdtable_check.py` 退出码 0；b) 重跑里程碑审计全绿；c) `git status --porcelain` 为空且落账已 commit；d) 三条齐绿
- [ ] 打 tag：`git tag -a v2.0.0 -m "<摘要 + 3 个 spec 清单>"`（打在版本分支落账 tip）
- [ ] 合并 `v2.0.0` → `main`（`--no-ff`；**口令才动**）→ 合并后验证 `git diff v2.0.0 main -- releases/v2.0.0/ specs/_project/version.md` **应为空**
- [ ] 推送 tag 与分支（**口令才动**）→ 推后远端复核
- [ ] 通知使用方：**升级为破坏性变更、需全量备份、在线用户将掉线重登**

---

## 四、遗留与阻断（冻结时登记）

| # | 项 | 性质 | 处置 |
|---|----|------|------|
| 1 | **18 条缺陷停在「已修复」未达「已验证」，其中 8 条 P0/P1**（BUG-116、140、142、145、147、161、164、185） | **发版硬阻断**（冻结前置要求 P0/P1 全部已验证） | 用户 2026-10-10 选「并行方案」：本机可验的 5 条（116/140/161/164/185）+ Windows 侧 3 条（142/145/147）分头验证 |
| 2 | **BUG-149（P1）状态为「新建」**，但 `pnpm-lock.yaml` 已入库（`git ls-files` 非空） | 账实不符 | 核实后更新台账状态 |
| 3 | **MILESTONE 缺陷表状态与 `bugs.md` 不一致**：BUG-116 在 MILESTONE 记「已验证」、在 `bugs.md` 记「已修复」 | 账实不符 | 以 `bugs.md`（唯一权威）为准对齐，或在验证后同步翻两处 |
| 4 | **MILESTONE 缺陷表未登记第三批（BUG-174~187）** | 漏登 | 补登记 |
| 5 | **`.env` 的 `JRE_BASE_IMG` 与实际发布镜像基座不一致**（声明 temurin、实际 UBI9+Elasticsearch） | 配置隐患 | 修 `.env` 或补注记（`UPGRADE.md` 已按实际基座给命令） |
| 6 | BUG-148 脚本加固未实施（现场用 prune+重拉绕过） | 已知限制 | 列入后续 |
| 7 | BUG-158 第 2 层 UI 端到端待复测 | 待复测 | 用户复测 |
| 8 | BUG-181 转 BL-47（用户 2026-10-10 批准） | 已裁决 | BL-47 跟踪 |
| 9 | 9 个历史 spec 无「挂载:」行（`20260927_*` / `20261001_*` 等） | 历史遗留 | **用户 2026-10-10 裁决：不用管** |
