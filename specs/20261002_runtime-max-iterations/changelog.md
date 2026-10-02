# Changelog: runtime-max-iterations

## 合并（2026-10-02）
- 用户「合并」→ 本分支（含 BUG-54/55）并入 main，分支删除；不发版随 v1.3.0 统一指令

## 同分支夹带（2026-10-02 下午）：BUG-55 SSE 心跳+超时三件套（用户"做吧"批准，直接在本分支实施）
- SseSupport(agent-core) 15s comment 心跳包 5 端点；nginx 读超时 900s×2；[model-call] 留痕
- 过程自纠三处：wrap 脚本括号错位×2、nginx 注释误用 //、测试 grep 假阴性（Spring 写 ":ping" 无空格）
- 实测：25s 静默窗口 2 帧 ping 注入 ✓ end/agentFiles/登记全通；测试数据全清

## Implement 收口补丁（2026-10-02 同日）——「留空清除」两处真缺陷修复
- ①VO 缺 maxIterations 字段：保存实际已落库但 GET 回显恒 None（曾致误判），补字段+映射
- ②MyBatis-Flex update(entity) 忽略 null → PUT 空体无法清除已设值（前记「留空清除 ✓」当时为**假阳性**，
  系误读 None 回显）；UpdateChain 定向强写该列后真复测：设 40→GET 40→空体清→GET None、DB NULL 双证
- 教训：null 语义字段禁用 update(entity) 单通道；「留空=清除」类需求必测 设值→清除 完整回路

## Implement 收口（2026-10-02）——T-01~T-05 全勾
- A/B 对照铁证：上限 2 半程断+文案带（2）；上限 40 完整跑完；四态校验+留空清除全过
- **语义定标**：maxIters=模型推理轮次（单轮可并行多工具）——UI/警告文案按此表述
- 过程修正如实记：VO 缺字段致回显假阴性（DB 实际已存）；DTO 字段曾重复插入；@Service/@Component 注解锚点错
- 交付 rc6；测试数据与配置全还原

## Tasks v0.1.0（2026-10-02）草稿
- T-01~T-05；T-02 内嵌语义探针（plan 风险1 定标）；前置声明：等 thinking-display 合并后另开分支

## v1.0.0 plan（2026-10-02）确认人: 陈卓
- **第②关通过**（用户「确认方案」，P1~P3 与两风险接受）→ Phase 3 tasks 草稿（本 spec 走标准三关，tasks 亦单独确认）

## Plan v0.1.0（2026-10-02）初稿
- V1.3.0_03 加列；工厂区间注入；警告文案生效值重读配置（P3 反射与事件夹带均否）；UI ElInputNumber 钳 1~100
- 风险1：maxIters 精确语义待 T 件探针定文案；风险3：实施分支待 thinking-display 合并后另开

## v1.0.0（2026-10-02）确认人: 陈卓
- **第①关通过**（用户「确认需求」，A-01~04 默认生效）；进入 Phase 2 Plan
- 注记：本笔此前一次落盘脚本笔误把本文件覆写成路径字符串，已重写恢复；requirements 未受影响（首行版本头已确认 v1.0.0）

## v0.1.0（2026-10-02）创建
- 立项来源：用户实测 ExceedMaxIters 中断（gantt svg→png），问"迭代次数能否可配置"；拍板走 A 标准三关
- 实证底料：Builder.maxIters(int) 存在、工厂零调用、runtime_config 表可直接扩列
- 5 R / 5 AC / Non-goals / 假设 A-01~04
