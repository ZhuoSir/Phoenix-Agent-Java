# Changelog: thinking-display

## 验收期修复两轮（2026-10-02）
- 用户实测「思考区不出现」双断点定位：①graph.ts dispatch 白名单漏透 thinking（登记 BUG-52）
  ②ChatMessages import 锚点失配致注册缺失（T-02 自引入，任务完成度回修不另立单；与 BUG-50 同族，
  教训三连：import 必须断言锚点、未注册组件运行时无警、vite/vue-tsc 均拦不住）
- 三探针（transport/store/render）运行时定位法记入方法论：静态核不果时以最小日志切链路
- UI 按用户反馈：文案「🧠 深度思考中…」→ Thinking（完成态 Thinking · Ns），样式并入站内灰调
  （浅底 + 左侧竖线区分），admin/chat 同组件单点生效

## Implement 收口（2026-10-01）——T-01~T-05 全勾
- 三通道实测全绿：thinking 独立键（70~733 帧样本）/正文零独白/agentFiles→end 帧序不变/HITL 未触及
- 风险1 判定解除：英文独白确属思考通道（分流后绝迹）
- 待用户：浏览器验收两页折叠交互与刷新回显 → 合并

## 三重确认闭环（2026-10-01）确认人: 陈卓
- tasks v1.0.0 已确认（用户「确认任务」）；三门全绿 → 开分支 feature/thinking-display，进入 Implement

## v1.0.0 plan（2026-10-01）确认人: 陈卓
- **第②关通过**（用户「确认方案」，P1~P4 与风险1 处置口径接受）
- **流程调整（用户明令）**：本 spec 的 tasks 生成后需用户确认，不沿用「生成即确认」授权

## v1.0.0（2026-10-01）确认人: 陈卓
- **第①关通过**（用户「确认需求」，R-06 64KB 截断默认生效）；进入 Phase 2 Plan

## v0.2.0（2026-10-01）三问决议入册
- Q1 流式展开+正文开始自动折叠（R-03 定稿）；Q2 持久化 metadata 零 DDL（R-05 定稿）；Q3 双端同做
- 待「确认需求」过第①关

## v0.1.0（2026-10-01）创建
- Specify 起稿：7 条 EARS / 5 AC / Non-goals / 假设 A-01~04；BL-21 立项来源
- 实证底料：混流点 HarnessChatServiceImpl:282 同型 StreamingOutput + mapper content 单键；chat_message.metadata 列已存在（零 DDL）
- 开放问题 Q1 默认交互 / Q2 存储上限 / Q3 范围页面，待用户拍板
