# Changelog: visibility-filetree-hygiene

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
