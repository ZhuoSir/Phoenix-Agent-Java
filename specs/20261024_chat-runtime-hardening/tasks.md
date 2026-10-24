> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-24 | 确认人: 陈卓 | 确认日期: 2026-10-24

# 任务拆解：对话运行时加固（chat-runtime-hardening）

> 说明：默认值按 plan Q1~Q5 的**推荐值**先落地（附件 20 / 超时 600s / thinking 默认开 / 熔断 5 /
> Xinference 方案 A）—— 2026-10-24 陈卓确认②③「确认，继续执行」采纳上述默认值。

## 分组 A：配置面（R-01 / R-03 / R-05 的配置项）
- [x] T-01 新增配置：`phoenix.agent.chat.attachment-max-count`（默认 20）、
      `phoenix.agent.model-first-frame-timeout-seconds`（默认 600）、
      `phoenix.agent.tool-repeat-breaker-threshold`（默认 5）；compose 暴露对应 env
      —— **验收**：三个配置各改一次值，行为随之变化（日志/接口可证）
- [ ] T-02 配置默认值变更的**兼容性说明**写入发布说明草稿（默认从 5/180/3 变为 20/600/5）

## 分组 B：附件上限（R-01 / BL-62）
- [x] T-03 后端：附件绑定入口做数量校验，超限返回明确错误（含"最多 N 个"）
      —— **验收**：N 个成功、N+1 被拒且错误文案准确
- [x] T-04 前端：附件选择器上限读配置并即时提示（超限时文案含当前上限）
      —— **验收**：前端与后端上限一致（同 N），N+1 在前端即被拦

## 分组 C：生成文件路径提示（R-02 / BL-63）
- [x] T-05 后端：消息 metadata 增 `generatedFiles`（path/size/mtime），并在回复正文尾部追加生成物清单
      —— **验收**：一轮生成 docx 的对话，正文出现绝对路径、metadata 有对应数组
- [ ] T-06 前端：生成物路径渲染为可点击（走既有下载/打开），刷新重进会话仍可见
      —— **验收**：刷新后仍能点开并下载成功

## 分组 D：超时语义（R-03 / BUG-203）
- [ ] T-07 `onFrame` 加终态守卫；超时提示移出 `content`（改 metadata `notice`）
      —— **验收**：超时后同一 message id **不再**被 UPDATE；正文无"超时提示"混入
- [ ] T-08 订阅侧显式取消上游请求（WebClient/OkHttp cancel 实测；不可取消则以 T-07 兜底并记 lesson）
      —— **验收**：超时后上游请求被取消（连接关闭/取消日志可证）或明确记录"不可取消"
- [ ] T-09 超时不影响"重试"：重试生成新消息
      —— **验收**：超时后点重试，落到新 message id

## 分组 E：思考展示（R-04 / BUG-201）
- [x] T-10 ollama 模型开启 thinking（配置项，默认开）+ 不支持时自动忽略
      —— **验收**：qwen3.8(ollama) 对话出现思考内容（非空）
- [x] T-11 历史回传不再静默丢弃 ThinkingBlock（降级为文本或换 formatter）
      —— **验收**：≥3 轮带工具调用后，日志**不再**出现 `Unsupported block type ThinkingBlock`
- [ ] T-12 前端思考区渲染（折叠 + 流式追加）
      —— **现状**：既有 `thinking-display` spec 已实现（`ThinkingBlock.vue` + `ChatMessages.vue` 渲染/自动折叠，
      `api-transport.ts` 回显 `metadata.thinking`）⇒ 本 spec 只需后端开启 think（T-10 ✅）+ T-17 运行时确认
      —— **验收**：思考区内容随流增长，可折叠

## 分组 F：熔断修正（R-05 / BUG-202）
- [x] T-13 计数键改 `agent|session`；清零逻辑改为"只保留最后签名"
      —— **验收**：A/B 交替 20 次不熔断；同参数连续 5 次熔断；两会话互不影响
- [x] T-14 日志只打签名摘要（工具名 + 参数 hash 前 8 位）
      —— **验收**：日志行长度可控，仍可定位

## 分组 G：Xinference 接入（R-06 / BL-65）
- [x] T-15 可行性实测：Xinference `/v1/chat/completions`（流式 + tools）与 `/v1/embeddings`
      —— **验收**：记录实测结论（可用/不可用 + 限制），决定走方案 A 还是 B
- [ ] T-16 若方案 A：补 UI 预设（provider=custom + base_url）与文档；不改后端枚举、不动库结构
      —— **验收**：配置后在对话与向量检索中可用

## 分组 H：回归与交付
- [ ] T-17 默认配置回归：附件 5→20、超时 180→600、熔断 3→5 分别验证"可配 + 默认值生效"
- [ ] T-18 全量回归：对话/检索/知识库主链路不受影响（含 R-09 五项门禁）
- [ ] T-19 changelog 与 completion 收尾（含默认值变更标注、BUG-201~203 闭环状态）

## 依赖与顺序
- A → B/C/D/E/F 可并行（配置先落地，避免各任务各自加常量）
- G 的 T-15 先做评估，再决定 T-16 是否实施
- H 最后做，必须在同一构建产物上跑
