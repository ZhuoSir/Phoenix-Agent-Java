# Changelog: agent-session-files

## v1.0.0（2026-10-01）确认人: 陈卓
- **三重确认第①关通过**：requirements v1.0.0 已确认（用户「继续BL-19，确认需求」）
- Q1~Q4 决议入基线（范围=当前会话 / 存储统一 uploads / 50MB+属主下载+不自动清理 / 挂 v1.3.0）
- 进入 Phase 2 Plan

## Implement 收口（2026-10-01）——T-01~T-11 全勾，AC 十项（AC-08 部分达成如实标注）
- 实测驱动的两处机制修订（回文档**待追认**）：① plan P1 装饰器→轮末扫描主通路（plan v1.1.0）；② requirements R-02 REMOTE 不可见→materialize 兜底（requirements v1.1.0）
- 自造缺陷两处修复并留证：Flex 链式 where().eq() 误型致列表恒空→单表达式重写；框架内部文件(MEMORY.md/consolidation_state)入黑名单
- AC 证据全录 artifacts.md；verify 扩至 10 断言（10/10）；compose 双根配置入镜像
- 待用户：① 追认两处修订 ② 浏览器验收 9080 会话页文件抽屉 → 通过后合并 main

## v1.0.0 plan（2026-10-01）确认人: 陈卓
- **第②关通过**：装饰器 tee 双后端方案 + P1~P5 决策 + 5 风险接受
- 用户技术问答佐证：生成文件走 tool（write_file/shell），upload 系模型侧通道，面板下载为新建 REST——方案不变，T-05 单列 shell 直写兜底
- Phase 3：tasks T-01~T-11 生成即确认（沿用既定授权），分支 feature/agent-session-files
