> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01

# 技术方案：思考/正文分离（对齐 requirements v1.0.0）

## 0. 数据流（一处分流，三链路共用）

```
AgentEvent(THINKING_BLOCK_DELTA)
  └─ toNodeOutput：思考不再伪装成正文 chunk——改挂 state 键 thinking_text（R-01 核心刀口）
       └─ HarnessEventMapper：thinking_text → eventMap.thinking（content 恒为 ""）
            ├─ 前台 transport：thinking 增量单独累加器（不进 fullText！正文污染即绝迹）
            ├─ admin 运行页：同规则（response.thinking 分支）
            └─ 旧客户端：只读 content → 天然无感（R-07）
落库：onComplete 保存消息时 metadata = JSON.stringify({thinking, thinkingMs})（R-05/零 DDL）
回显：消息装载时解析 metadata.thinking → 折叠区渲染（旧行无键按 R-04 不显示）
```

## 1. 后端（3 处，改动极薄）

- `HarnessChatServiceImpl.toNodeOutput` THINKING 分支：`new StreamingOutput<>(delta,…)` → `NodeOutput.of("harness_agent","harness", state{thinking_text=delta})`（不再进 chunk）
- `HarnessEventMapper`：`state.value("thinking_text")` → `eventMap.put("thinking", t)`；content 键语义不变
- **验证步（不假设）**：`ChatMessageDTO.metadata` 实际类型（String/Map）与 jsonb 写入现状——决定前端 stringify 还是直传对象；若现状 metadata 从未成功落库（确认消息仅本地态），T-03 内修通并实测 round-trip

## 2. 前端（共享组件 + 两页接线）

- **新组件 `ThinkingBlock.vue`**（views/front/components，admin 运行页复用）：
  - props：`content`（思考全文）、`streaming`（思考中→展开滚动+「思考中…」动效）、`durationMs`
  - 正文首字到达 → 父层把 streaming 置 false → **组件自动折叠**为一行「🧠 已深度思考（N 秒）」，点击展开回看（Q1）
  - 无 content → 整块不渲染（R-04）；纯文本弱样式（灰底小字，Non-goal 不做 md 精渲染）
- **前台**（api-transport.ts）：`thinkingBuf` 独立累加器；`onProgress` 双回调（正文/markdown 不变，thinking 走消息对象新字段）；ChatMessage 类型（chat-shared）加 `thinking?: string; thinkingMs?: number`；onComplete 保存带 metadata；历史装载解析 metadata.thinking
- **ChatMessages.vue**：助手气泡顶部插 `<ThinkingBlock>`（正文 v-html 之前）
- **admin 运行页**（graph.ts + run/index.vue）：同 key 消费，nodeBlocks 上方挂 ThinkingBlock；保存消息同 metadata
- **纯净性（R-02/AC-04）**：复制、另存为文件等下游只读 msg.content——结构上已保证（thinking 从不进 content），加一例单测式核对即可

## 3. 兼容与边界

- 旧数据：metadata 无 thinking 键 → R-04 静默；混排旧文本**不回溯清洗**（Non-goal 已批）
- 64KB 截断（R-06）：保存前 `thinking.slice(0, 65536)` 并追加 `…（已截断）` 标记；流式侧不截
- HITL/agentFiles 帧：各走各键，零交叉（回归核对进 AC）
- 非思考模型：事件流无 THINKING 帧 → 前端零状态变化（AC-02）

## 4. 决策与已否方案

| # | 决策 | 被否替代 & 理由 |
|---|---|---|
| P1 | 分流点放 **toNodeOutput/mapper**（事件层），前端只认 thinking 键 | 前端从 content 里正则剥离思考——否：污染先发生、剥离必漏，且复制/文件链路全线受累 |
| P2 | 持久化走 **metadata jsonb**（列现成） | 加 thinking_content 列——否：DDL+实体迁移全动，收益相同；metadata 已是本表约定扩展位 |
| P3 | ThinkingBlock 独立组件双页复用 | 两页各写一份折叠 UI——否：Q3 拍板「行为一致」的最强保障就是同组件 |
| P4 | streaming 状态由父层驱动（正文首字→父置 false） | 组件内部计时猜测——否：正文到达是事件事实，不该靠猜 |

## 5. 风险

1. **deepseek-v4-flash 的"思考"实为正文内独白**（此前所见英文独幕可能非 THINKING 帧而是模型直出）——若真机验证发现独白仍进 content，则属模型行为非通道问题：如实回报，届时再议提示词约束/前端过滤（不擅自扩权）
2. metadata 现状可能从未真正落库过（确认消息仅本地），T-03 连带修通——风险中等，验证步已内置
3. admin 运行页 nodeBlocks 结构复杂，ThinkingBlock 挂位需实测不破坏 sql 结果卡片渲染
