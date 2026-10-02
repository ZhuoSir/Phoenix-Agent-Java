> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-02 | 确认人: 陈卓 | 确认日期: 2026-10-02

# 技术方案：工具迭代上限可配置（对齐 requirements v1.0.0）

## 1. 数据（升级件 V1.3.0_03，spec/sql 草案）

`ALTER TABLE tbl_data_agent_runtime_config ADD COLUMN IF NOT EXISTS max_iterations int NULL;`
+ 注释（NULL=框架默认）；回滚件 drop column。存量零影响（R-05/AC-05）。

## 2. 后端（三薄刀）

- **实体/DTO**：`AgentRuntimeConfig` 加 `Integer maxIterations`（列映射走既有驼峰-下划线约定）；保存链路（运行时配置 save 端点）透传该字段，**服务层校验** 1~100（越界抛可读 message，data 域信封落地，对齐 knowledge_top_k 同款校验风格）（R-01/R-03）
- **工厂注入**：`HarnessAgentFactory` 构建段（resolve config 之后、build 之前）：`if (isOn 区间内) builder.maxIters(n)`；NULL/越界脏数据 → 不调用（回退框架默认，运行侧容错 R-03）（R-02）
- **超限文案带生效值**：`HarnessChatServiceImpl` 的 ExceedMaxIters 警告分支拼 `（N）`——N 取该智能体 runtime config 的 maxIterations；为空则按 A-04 **省略数字**（不猜框架默认值，避免谎报）（R-04）

## 3. 前端

`AgentRuntimeConfig.vue` 运行时配置区新增「工具迭代上限」：`ElInputNumber :min=1 :max=100`，可留空；行内提示「复杂工具任务（图形绘制/文件转换）建议 20~40；留空=系统默认」。保存/回显走既有配置接口字段透传（api/core/agentRuntime.ts 类型补字段）。

## 4. 决策与已否方案

| # | 决策 | 被否替代 & 理由 |
|---|---|---|
| P1 | 存 runtime_config 加列（与 topK 等同类参数同表同排） | 全局 application 配置项——否：本需求语义就是**按智能体**；环境变量——否：绕开管理页与审计 |
| P2 | 值校验放**服务层**（保存侧），运行侧只做区间容错 | 前端-only 校验——否：API 直写可绕 |
| P3 | 生效值读取在警告文案处**重读配置**（每轮一次，DB 轻查询） | 从 HarnessAgent 实例反射 maxIters——否：私有字段脆依赖；把 N 塞进事件——否：ExceedMaxItersEvent 是框架事件不带自定义字段 |

## 5. 风险

1. maxIters 语义若含「模型调用轮」而非「工具调用次」（措辞级差异），UI 文案以 AgentScope 行为实测为准（T 件里先做上限=1 的最小行为探针，再定稿提示语）——不影响功能正确性
2. 上限调高 → 失控任务耗 token 变长：范围钳 1~100 + 按智能体显式配置，风险自负可控
3. 分支拓扑：本 spec 文档现落在 feature/thinking-display（11 提交待合并）；实施代码将在 thinking-display 合并 main 后另开 feature 分支，避免互相夹带
