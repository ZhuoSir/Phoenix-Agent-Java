# Changelog: fix-bug-batch

## Implement T-08~T-09（2026-10-02）批次关账
- T-08 揪出真断点并修复：前台 confirm 走 sn-only 重载（库配置 sn 空→必挂）+ payload 无 agentId + ChatMessages 空 sn 早退——三处修齐；端到端复验：chat needConfirm(1) → confirm 2494帧 end×2 无500 → 行单行 done len=800 → 前台确认产物落盘 ✓
- 插曲：验证号 status 语义踩坑（1=启用，传 0 被禁用）
- T-09 十单终态：9 已修复(v1.4.0) + BUG-04 不修复(批准) + BUG-24③延期注记；台账本批「新建」残留=0；探针账号/会话/文件/planMode 全清

## Implement T-06~T-07（2026-10-02）
- T-06 取消令牌+节点守卫：StreamCancellation 注册表 + NodeBeanUtil 单点守卫 + GraphServiceImpl 三钩子；实测取消链完整（subscribe→disconnected→Stopping→cleaned→后续零活动）；注：现图引擎下 dispose 即断链，节点守卫为兜底保险与语义显式化（③硬中断按批准记技术债）；过程插曲：Docker VM 盘满 Redis MISCONF→prune 8G+容器误清重建
- T-07 模型类型三分（对话/嵌入/音频）；vite dev 端口 loadEnv VITE_PORT 实测 5777 ✓

## Implement T-05（2026-10-02）删除级联+孤儿清理
- AgentServiceImpl.deleteById 级联五表（runtime/skill/group 软删，kbase/datasource 绑定物理删——表无软删列的事实形态）；级联失败不阻断删除（catch+warn）
- **事实订正**：tbl_data_agent 无软删列（删除=物理）→ 矩阵D「软删」表述对本体不适用，孤儿判定改为存在性；ops 脚本（spec/sql/04，注释级干跑清单+幂等 UPDATE/DELETE），现网执行 0 错、孤儿终验 0
- E2E：临时 agent 34 建→配 runtime(0)→DELETE→del_flag=1 + INFO 留痕；探针清场
- 过程自纠：sed 大小写漏改×2（改 python 全量正则终清）

## Implement T-04（2026-10-02）问答联合向量化
- DocumentConverterUtil QA 口径=question+\n+answer（答案空退化问句）；re-embed 端点+知识库面板「重刷向量」按钮（确认框+计数回执）
- 矩阵B实测：新条目向量 content=联合文本 ✓；重刷×2 幂等向量行数恒 1 ✓；DOCUMENT 分支零触碰 ✓；列名笔误 kb_id→knowledge_base_id 自纠
- **业务域口径修正（如实）**：现场证实 BusinessKnowledge 为「名词/说明/同义词」结构，不存在共享 QA 转换器——Q&A 时"统一生效"的担忧对象不存在，业务侧实际零影响（比批准范围更小，不越权）

## Implement T-01~T-03（2026-10-02）
- T-01 登录码值 23009（发现为历史半修：文案已对码未改）+ 建号双层密码校验；矩阵A 四断言全绿（登录23009/改密对面23007/空密拒绝101/正常通路）；过程插曲：compose 标签被 && 链断裂卡旧镜像，追因后修复
- T-02 核实即关账：缺陷对象（未挂仓的 HumanInTheLoop 旧路径）已被技能体系重构取代，现场无残留
- T-03 test.yml 一行口令对齐（phoenix）入库——常驻脏文件清零
- 部署态切换至 v1.4.0-dev 标签（版本分支制首个开发线镜像）

## v1.0.0 tasks（2026-10-02）确认人: 陈卓
- **第③关通过（三重确认门全绿）**→ Implement；分支 feature/fix-bug-batch 自 v1.4.0 切（新制式首单）

## Tasks v0.1.0（2026-10-02）草稿
- 九任务五组；矩阵 A/B/C/D 断言逐身份分配到 T-01/04/05/06 验证方式；T-08 验证单带"不通就地修"兜底；T-09 台账零残留终检

## v1.0.0 plan（2026-10-02）确认人: 陈卓
- **第②关通过**（用户「确认方案」，矩阵 A~D/被否案/三风险接受）→ Phase 3

## v1.0.0（2026-10-02）确认人: 陈卓
- **第①关通过**（用户「确认需求」）→ Phase 2 plan v0.1.0（含四张共享面身份矩阵——skill 新规首航）

## v0.3.0（2026-10-02）四裁决齐
- Q3 批复①+②（取消令牌+节点早停入本批，硬中断延期技术债注记）；范围冻结：9 修复单+1 验证单+1 不修复归档
- 待第①关「确认需求」

## v0.2.0（2026-10-02）裁决入册
- Q0 挂 **v1.4.0**（MILESTONE 已登记）；Q1 BUG-04 不修复(批准:陈卓)；Q2 BUG-18 转修复条款（问答联合向量化+历史重刷，BUG-49 回表机制保留）；Q3 BUG-24 方案解释中待批

## v0.1.0（2026-10-02）创建
- 用户指令「开放 bug 10 单弄个 spec 一起修」；验尸分型：7 修复单 + 1 验证单 + 3 裁决单
- 挂版与 Q1~Q3 口径待用户拍板（R-09/10/11 三选一）
