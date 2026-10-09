# 完成清单 — chat-attachment-understanding（v1.5.0，含 CR-01/02/03）

> 更新: 2026-10-09 | 分支: feature/chat-attachment-understanding | 挂版: v2.0.0

## 一、已完成（12/12，逐条带证据与 commit）

| 任务 | 结论 | 证据件 | 关键 commit |
|---|---|---|---|
| T-01 多模态探针 | qwen3.8-max 读图正确（keys #4025/#55eb） | evidence/T-01_multimodal-probe.txt | 探针提交 |
| T-02 上传/存储/V2.0.0_15 | 白名单/限制/拒绝不留脏 | evidence/T-02_upload-endpoint.txt | T-02 提交 |
| T-03 MULTIMODAL 类型 | 枚举+registry+管理页下拉 | evidence/T-03_multimodal-type.txt | 9346706 |
| T-04 文档抽取 | 截断/加密/损坏/扫描件四类原因 | evidence/T-04_document-extraction.txt | T-04 提交 |
| T-05 鉴权/下载/缩略图 | 403/超管/无 token 信封/直链不可绕 | evidence/T-05_attachment-authz.txt | T-05 提交 |
| T-06 两族接入+两阶段+降级+S9 | 图=IMG-7391-KQ、文档=HT-2026-4821、降级不编造、回填 28→4418 | evidence/T-06_stream-integration.txt | 2b69c47 |
| T-07 admin 两端 UI | typecheck 增量 0、草稿分片、metadata、缩略图 blob | evidence/T-07（commit 内） | 25cb4c7 / e481f51 |
| T-08 mobile 适配 | /m/ 部署+iPhone-14 浏览器验证+截图 | evidence/T-08_mobile-ui.txt | 1296283 / 56d1569 |
| T-09 端到端汇总 | 12R 矩阵 + 身份对面断言 + 部署三段 | evidence/T-09_e2e-matrix.txt | 本提交 |
| T-10 隔离后端（CR-03） | 矩阵 12/12 + rollback 演练 + P-4 日志 | evidence/CR-03_T-10_*.txt | 149bda7 |
| T-11 隔离前端（CR-03） | 浏览器抓包三端 scope | evidence/CR-03_T-11_*.txt | 5f01a2a |
| T-12 隔离 e2e（CR-03） | 两空间互不可见+附件回归 | evidence/CR-03_T-12_*.txt | 979854f |

## 二、未完成/验证缺口（不勾选删除，如实列）

| 项 | 原因分类 | 处置去向 |
|---|---|---|
| S8 身份②（skillScopeHint 末尾拼接）运行时断言 | 验证不通过（环境缺前台账号+技能会话） | 代码审查级结论在案；后续前台账号回归补断言 |
| iOS/Android 相册/拍照来源逐项 | 主动延期（需真机） | 真机回归清单；R-12 以同源规则+两端浏览器/API 实测覆盖 |
| 扫描件/图片型 PDF 的内容理解（OCR/视觉） | 需求变更（不在本期已确认范围） | 如立项走新 CR/spec；本期行为=显式告知原因（已保真） |
| 多模态识别轮次波动 | 风险（模型侧非确定性） | T-06 ⑧ 记录；验收以多次抽样为准 |

## 三、Bug 核对

| BUG | 状态 |
|---|---|
| BUG-153 mobile 构建门常红（既有 11 条类型错误） | 新建（不顺手修；vite 单跑可出包） |
| BUG-154 mobile API 前缀错配（登录/对话流双挂） | 已修复（56d1569） |
| BUG-155 /api/front/harness/chat 恒 500 | 已修复（ba7c7f1） |
| BUG-156 前台气泡当轮不显附件 | 已修复（d7d26e8） |
| BUG-157 点附件应展示而非下载 | 已修复（d7d26e8） |

## 四、升级件

见 artifacts.md：V2.0.0_15（附件表）、V2.0.0_16（会话 source 列+回填+索引，均配 rollback）。

## 五、挂接一致性

releases/v2.0.0/MILESTONE.md 已含本 spec 行与 CR-03 节；tasks 12/12 勾；未勾项无（缺口列于第二节）。
