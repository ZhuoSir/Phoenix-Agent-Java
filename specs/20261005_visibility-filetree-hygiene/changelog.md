# Changelog: visibility-filetree-hygiene

## T-04 完成：路径解析器 + 隐藏规则同源化（2026-10-05）
- 新增 `SessionWorkspaceFilters`（内部件判定**单一实现**：目录名单∪点开头 / 文件名单∪`call_*`∪点开头无扩展名∪内部后缀 / 会话 UUID 判据）
- 新增 `SessionFileTree`（三代 `store_key` → 会话内相对路径；**非本会话→历史**；解析失败不抛异常、保留文件名）
- `WorkspaceArtifactScanner` 改为**同源引用**：删除 5 个局部名单常量，`isInternal`/`otherSessionArtifact` 全部委托共享工具（`grep` 复核局部名单已零残留）
- **实现细化（探针发现，不改需求/验收）**：归属判定改为**以会话段为锚**（只看第 0/1 段是否等于本会话 ID），前缀（agentKey/显示名）不参与判定——BUG-78 类"入库前缀 ≠ 库中 sn"现场下不会把本会话文件误归历史；同时他会话 UUID 落在第 0/1 段一律归历史（隔离不放宽）。仅记入 changelog，不回改已确认 plan（非需求/设计缺陷）
- **夹具证据（可复现）**：`fixture/TreeProbe.java` 13 用例 → **PASS=13 FAIL=0**（三代各一 / uid 嵌套 / `.pylibs` 噪音 / `call_*` 占位 / 二代历史 / 一代历史 / 他会话隔离×2 / 无 size / 前缀不匹配 / 内部状态文件 / 内部后缀 / 空键不抛）
  复现命令：`java -cp phoenix-agent/phoenix-agent-core/target/classes specs/20261005_visibility-filetree-hygiene/fixture/TreeProbe.java`
- 编译：`mvn -pl phoenix-admin/phoenix-admin-manager -am package -DskipTests` 绿

## tasks v1.0.0 确认③通过——三重门全绿，进 Phase 4（2026-10-05）
- 用户口令「确认」→ tasks v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门（requirements①/plan②/tasks③）全绿
- Phase 4 开工，首任务 **T-01**（清账组，零风险先行）；交付顺序：T-01~03 清账 → T-04~06 文件树 → T-07~10 静默可见性 → T-11~12 收口

## plan v1.0.0 确认②通过 + tasks v0.1.0 草稿（2026-10-05）
- 用户口令「确认」→ plan v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门第②关过，进 Phase 3
- Q1~Q6 暂定口径随 plan 一并生效（记入 plan §九）
- Phase 3 拆解 **12 任务四组**：组1 清账 T-01~03（.gitignore/残留/死配置/台账销账）｜组2 文件树 T-04~06（解析器+隐藏规则同源 → 单层树接口 → 前端树 UI 两面共用）｜组3 静默可见性 T-07~10（阶段标记 → 心跳节流 → 首帧超时 → 两面指示）｜组4 收口 T-11~12（E2E 矩阵/台账）
- 覆盖核对 R-01(T-07~10)/R-02(T-04~06)/R-03(T-01~03)/R-04(T-11)，无孤儿无环；**零 DDL 零布局改动**；交付顺序=清账→树→静默→收口
- 状态：**待确认③**（未过门，仍未写任何生产代码）

## v1.0.0 确认①通过 + v1.7.0 双建（2026-10-05）
- 用户口令「确认」→ requirements v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门第①关过，进 Phase 2
- Q1~Q6 未逐条答复 → 按 agent 建议口径暂定执行并在 requirements §七 记明，② 关口可纠
- **v1.7.0 双建**：version.md 新增 v1.7.0「在途」行（基点 main tip `456f8ab`，v1.6.0 已合 main 无悬空）+ `releases/v1.7.0/MILESTONE.md` + 分支 `v1.7.0` + 特性分支 `feature/visibility-filetree-hygiene`
- 待办挂载：BL-28 / BL-11 / BL-12 / BL-15 → 已立项(v1.7.0)；BUG-85 → 已规划(v1.7.0)；§四 agent-config 销账随本 spec R-03

## v0.1.0（2026-10-05）立项草稿
- 用户口令：「BUG-85 和 BL-28，还有 BL-12/15/11、§四 两条过期注记 + agent-config 历史快照销账，**列为一个 spec，新建**」
- 挂载：**v1.7.0**（v1.6.0 已于同日发布 tag `v1.6.0` 并 `--no-ff` 合入 main；在途唯一 ⇒ 待立项双建 v1.7.0）
- 范围三组：R-01 长轮静默可见性 + 模型调用超时（BUG-85）｜R-02 会话文件文件夹树（BL-28）｜R-03 工程清账 + 台账销账（BL-11/12/15 + §四）
- 现状取证（全部实测，非推测）：
  - **BUG-85**：会话 `6e9c09e0` 实测单次模型调用静默 **503 秒**（17:39:34.717 发起 → 17:47:57.666 才有下一事件），其间无 End/无错误/无重试；看门狗 600s 未触发（503<600）＋总时长闸=0 ⇒ 设计上不杀；该轮自行恢复并 done。金丝雀同轮 `frames=179917/emitted=5481/dropped=47279`（合并闸 33:1，前端未被压垮；`dropped` 为设计内空帧丢弃）。
  - **BL-28**：`listBySession` 平铺查库（VO 无目录信息）；目录层次可从 `store_key` 的会话内相对路径还原；`rel_path` 为 tee 布局不可展示。
  - **清账**：未跟踪噪声 `.mvn-home/`、`.pnpm-store/`、`AGENTS.md.bak.20260927101837`、`diagrams/`、`scripts/`、`WSL`(0B)、`或在`(0B)；`PhoenixAgentProperties.skillPath` **零引用确证**（`getSkillPath` 全仓 0 命中）；`RulesHarnessAgent.java` 已无未提交改动（旧注记过期可销）；backlog §四 的 agent-config 条目「8/13 已勾」**实测为 13/13 全勾**（注记写错）。
- 开放问题 6 条（Q1~Q6）随 requirements 提出，**plan 前需用户裁决**（其中 Q3 空文件夹、Q5 残留去向影响范围）
- 状态：**待确认①**（未过门，未写任何生产代码、未建版本双建）
