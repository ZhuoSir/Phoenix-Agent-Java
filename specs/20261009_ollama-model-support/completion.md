# 完成清单：ollama-model-support

> 版本: v1.0.0（三重确认齐）｜挂载: v2.0.0（在途）｜分支: feature/ollama-model-support

## 一、已完成（含实测证据）

| 任务 | 结论 | 证据 |
|---|---|---|
| T-01 依赖+类型上移 | ✅ 编译 0 错误；既有 deepseek 对话回归 2s/14 帧/end:true | evidence/T-01_T-02_deploy-verify.txt |
| T-02 provider 归一+空 key | ✅ 空 key 保存成功；非法 provider 被拒并列出可选值；DB len(api_key)=0 | 同上 |
| T-03 Ollama CHAT 装配 | ✅ 流式对话 649 字 end:true；Ollama 侧 11×/api/chat（原生协议） | evidence/T-03_T-04_ollama-chat-tools.txt |
| T-04 工具调用 | ✅ 正例 getRagInfo 真实调用 14 次；反例 gemma3 返回"does not support tools"并可诊断 | 同上 |
| T-05 嵌入模型与维度 | ✅ 768 维迁移（4 表 vector(768)）；备份重嵌入恢复 rag 2556 / simple 2556 / memory 7；相似度检索距离 0.0253 | evidence/T-08_dimension-guard.txt + 迁移记录 |
| T-06 连接测试 | ✅ 三类 ollama 行均"模型可用"；不可达端口给出目标 URL+根因 | evidence/T-06_T-07_conn-list.txt |
| T-07 模型列表接口 | ✅ 返回本机 3 个模型；无 token → 401 | 同上 |
| T-08 维度不一致拒绝 | ✅ 友好拒绝实测：给出"表 768 / 模型 512"两维度 + 处置建议（BL-34） | evidence/T-11_T-12_T-13_final.txt |
| T-11 离线/内网 | ✅ 替代验证（四项只访问本地端点）；**局限：未真断外网** | 同上 |
| T-12 七类日志点 | ✅ 装配/维度/模型列表/连接测试/向量化/维度拒绝/密钥变更 均有实证；密钥零明文 | 同上 |
| T-13 共享面回归 | ✅ 矩阵 4 对象逐身份（前端浏览器项除外） | 同上 |

## 二、未完成 / 验证缺口（如实列，不勾选）

| 项 | 原因分类 | 处置去向 |
|---|---|---|
| T-09/T-10 浏览器验收 | 未验证（需重建 admin-ui 产物 + nginx 镜像） | 代码与 typecheck 已完成；构建后补 UI 断言 |
| 1 篇知识文档 FAILED（id=37 一审判决案-刘青） | 迁移窗口期失败；其 chunk 不在恢复范围 | 需单独重嵌入或确认可弃 |
| 备份中 harness 表 5 行未恢复 | 该 legacy 表 `content` 列为空（文本在 `payload`），且**非当前知识检索路径**（现行路径为 rag/simple store，已全量恢复） | 如确认需要，按 payload 解析后另行恢复 |
| 离线"断网实测" | 环境限制（未切断容器外网） | 已用"端点唯一性"替代并写明局限 |
| 存量 512 维数据的可回溯性 | 已按用户批准 B 方案丢弃（备份留存） | 备份：backups/vector_tables_512_20261010_0413.sql |

## 三、升级件

- **DDL**：无新增列；**含数据面迁移**（4 张向量表 512→768，由应用启动期按模型维度重建）——已在 README/证据中说明
- **配置**：无新增配置键
- **依赖**：`agentscope-extensions-model-ollama`（父 POM 管理 + agent-core 声明；jar 已在本机仓，离线可编）

## 四、挂接一致性

- MILESTONE v2.0.0 需求表含本 spec 行 ✅；version.md 记挂载 ✅
- backlog BL-29 已翻「已立项(v2.0.0)」✅
- **BL-34（存量向量批量重建）**：本 spec 完成了**维度迁移与重建**（部分），指纹一致性校验仍未做 ⇒ BL-34 保持未交付，备注补充本 spec 已完成的部分

## 五、收尾状态（2026-10-10）

- **已合并回账房**：`v2.0.0`（merge commit `5f99875`），施工分支已删除
- **任务**：13 项中 **10 项勾选**（T-01~T-08、T-11~T-13）；**T-09/T-10** 仅剩浏览器交互验收
  - 已完成：前端代码 + typecheck（203=基线）+ admin-ui 构建 + nginx 部署 + **运行容器内产物断言命中**（`/usr/share/nginx/html/js/chat-*.js` 含 `ollama/models`）
  - 待做：登录（含验证码）后的点击级验收：提供商下拉出现 Ollama / 地址预填 / key 空可保存 / 获取模型列表下拉 / 停 Ollama 退化手工输入
- **未推送**：本地 `v2.0.0` 比远端多 20 个提交（**push 按纪律只问不做，等口令**）
- **环境现状**：EMBEDDING 默认 = ollama/nomic-embed-text（768 维）；4 张向量表 `vector(768)`；知识库可用（HJP 六库 48 分块与迁移前一致）
