# 升级件登记: thinking-display

| 类型 | 内容摘要 | 来源任务 | 已汇总至里程碑 |
|---|---|---|---|
| 无 DDL | 思考持久化复用 tbl_data_chat_message.metadata（jsonb）新键 thinking/thinkingMs——无结构变更 | T-03 | - |
| 无配置 | 无新增配置键 | - | - |
| 协议 | SSE 事件 Map 新增 `thinking` 键（向后兼容，旧客户端只读 content 不受影响） | T-01 | - |

## 验证记录（2026-10-01，A 栈 rc5）
- T-01：真实思考模型探针——thinking 70 帧/正文特征词零混入（R-01/R-07）
- T-03：metadata round-trip 三拍（存→DB jsonb→回读）；64KB 截断双端一致实现
- T-05 回归：文件事件帧序 agentFiles(1278)<end(1279)、thinking 733 帧独立、正文纯净（R-02）
- UI 项（AC-01/03/05 折叠交互、双端观感）：待用户浏览器验收
- 风险1 判定：此前英文独白确系 THINKING 通道内容（非模型正文直出），分流后绝迹——已解除
