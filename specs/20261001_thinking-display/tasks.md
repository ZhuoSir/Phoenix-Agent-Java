> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01

# 任务清单：思考/正文分离（thinking-display）

> 验证口径：A 交付栈真机 + curl/SSE/psql 留证；浏览器交互由用户验收。共 5 任务（改动薄，粒度按层切）。

## 1. 后端分流

- [x] T-01 事件层双通道：toNodeOutput 思考分支改挂 `thinking_text` 状态键（不再产 chunk）；HarnessEventMapper 透出 `eventMap.thinking`，content 从此只含正文
  关联: R-01, R-07 | 依赖: 无
  验证方式: 部署后 curl SSE 用思考型模型提推理题→帧含 thinking 键且末帧 content 无独白混杂（特征词比对）；非思考模型回归：无 thinking 键、正文如常；THINKING 事件不触发时后端日志零异常
  验收标准: 三链路（front/admin/API）共用 mapper 一处生效；旧键 content 语义不破坏

## 2. 前端

- [ ] T-02 ThinkingBlock 组件 + 前台聊天页接线：灰底弱样式、流式展开滚动+「思考中…」、正文首字到达自动折叠为「🧠 已深度思考（N 秒）」可点开回看；api-transport 双累加器（thinking 永不进 fullText）；chat-shared ChatMessage 加 thinking/thinkingMs；历史装载解析 metadata
  关联: R-02, R-03, R-04, R-05(回显) | 依赖: T-01
  验证方式: vue-tsc 改动文件零新增；build 通过；交互由用户浏览器验收（AC-01/02/03）；复制与「另存为文件」内容核对不含思考（AC-04）
  验收标准: 非思考模型完全无占位；旧消息（无 metadata.thinking）静默兼容

- [ ] T-03 持久化链路修通：saveMessage metadata round-trip 实测（确认 DTO 类型与 jsonb 写入现状，断则修）；前端保存前 64KB 截断+「已截断」标记；psql 核对落库与刷新回显
  关联: R-05, R-06 | 依赖: T-02
  验证方式: 对话一轮→psql 查 metadata->>'thinking' 非空；构造超长思考（或 curl 直投大 metadata）验证截断行为；刷新回显折叠区内容一致
  验收标准: metadata 无 thinking 键的历史行零影响；截断仅影响持久层不影响流式观感

- [ ] T-04 admin 运行页接线：graph.ts 消费 thinking 键；run/index.vue 消息气泡挂 ThinkingBlock（同组件复用）；admin 消息保存带 metadata；sql 结果卡片等既有渲染回归
  关联: R-02, AC-05 | 依赖: T-02
  验证方式: admin 对话页实测思考区呈现/折叠；nodeBlocks（sql 表格/报告卡）渲染无回归；vue-tsc 零新增
  验收标准: 与前台行为逐点一致（同组件同事件）

## 3. 收口

- [ ] T-05 E2E 与收口：AC-01~05 留证 artifacts（含风险1 判定：真机独白是否仍进 content——若属模型行为，如实回写并停手回报）；HITL/agentFiles/文件面板回归；completion/artifacts/changelog/backlog BL-21 销账/MILESTONE 推进
  关联: 全部 | 依赖: T-01~T-04
  验收标准: 五 AC 红绿如实标注（部分达成注明）；风险1 有实测结论；verify 11/10 项无回归
