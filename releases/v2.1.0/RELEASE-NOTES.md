# v2.1.0 发布说明（草稿 · 待冻结）

> 状态：**在途**（未 tag、未 push）。本文件随版本冻结点更新。
> 组成：`release-delivery-hardening`（10/10）+ `vector-dim-flexibility`（16/16）+ `chat-runtime-hardening`（进行中）。

## 一、⚠️ 默认值变更（升级方必须知晓）

| 配置项 | 旧值 | 新值 | 影响 |
|---|---|---|---|
| `phoenix.agent.chat.attachment-max-count`（原硬编码） | 5 | **50** | 单次对话可附加的附件数变多（2026-10-25 用户要求 20→50；前端启动由后端下发，无需重建） |
| `phoenix.agent.model-first-frame-timeout-seconds`（compose env `PHOENIX_AGENT_MODEL_FIRST_FRAME_TIMEOUT_SECONDS`） | 180 | **600** | 首帧等待时间变长（慢模型的请求不再轻易被判超时） |
| `phoenix.agent.tool-repeat-breaker-threshold` | 3 | **5** | 工具连续重复调用熔断阈值放宽 |

> 三项均可配置回旧值；**语义不变**（仅默认值调整）。

## 二、新增配置项

| 配置项 | 默认 | 说明 |
|---|---|---|
| `phoenix.agent.ollama.think` | `auto` | ollama 思考模式：`auto`（按模型名判断思考模型）/ `true` / `false` |
| `VITE_ATTACHMENT_MAX_FILES`（前端构建期） | 50 | 前端附件选择器兜底上限（**后端 `attachment-max-count` 为最终裁决方**，且启动时通过 `/api/chat/attachment/limit` 下发） |
| `phoenix.platform.brand.cache-seconds` | 60 | 品牌公开信息（`GET /platform/brand-config`）服务端缓存秒数，0=不缓存 |
| `phoenix.platform.brand.asset-max-bytes` | 2097152 | 品牌 Logo/favicon 上传大小上限（2MB；**不开放 SVG**） |

## 三、修复（本版）

- **超时语义**：首帧超时/取消后不再继续追加同一消息内容（`onFrame` 终态守卫）；超时提示不再无条件混入正文（改落 `metadata.notice`）。
- **工具熔断**：计数回归"连续"语义（只保留最后签名、变化即清零），避免正常多轮检索被误熔断。
- **思考展示**：ollama 思考模型显式开启 `think`（此前源头无思考）；历史 `ThinkingBlock` 降级为文本，不再静默丢弃。

## 四、能力增强

- 附件上限可配置（默认 20；推荐 20、安全上限 50——依据：单附件截断 24000 字符 × N 与上下文/解析开销）。
- 智能体生成文件后，回复正文给出 **生成物相对路径 + 可点击下载链接**，并落 `metadata.generatedFiles`（刷新后仍可见）。
- **品牌外观配置化**（`brand-configuration`，BL-66）：系统名称 / Logo / favicon / 主题主色可在「系统管理 → 外观设置」自助修改，
  保存后**刷新页面即生效**（不重新构建、不重启）；主色自动派生 hover/active 色阶；隐私协议与服务条款文案随名称联动。
  升级件：`V2.1.0_04__brand_config.sql`（建单行表 + 默认行 + 菜单行，幂等；回滚件在 `sql/rollback/`）。
  **已知偏差**：浏览器标签标题由旧版 `Phoenix Admin` 变为 `<系统名称> - 页面名`（R-01「三处统一」的必然结果）。
- 支持 **Xinference**（Xorbits Inference）接入：`provider=custom` + `base_url=.../v1`，零代码（详见 `docs/XINFERENCE.md`）。

## 四之二、内网离线资源（offline-icons / BL-74）

| 资源 | 位置 | 体积 | 说明 |
|---|---|---|---|
| 图标集合（Iconify） | 前端产物 `/icons/{lucide,ant-design,ep,meteor-icons,fluent-mdl2}.json` | 2.3MB | 运行期 Iconify provider 指向本站 `/icons`，**不再访问 `api.iconify.design`** |
| 报表 vendor | 前端产物 `/js/vendor/{marked.min.js,echarts.min.js}` | 1.2MB | 报表 HTML 引用本站路径，**不再访问 jsDelivr** |

- 更新方式：替换 `apps/admin-ui/public/icons/*.json` 或 `public/js/vendor/*` 后重新构建前端即可（取自 `@iconify-json/*` 与 `marked`/`echarts` 包内文件）
- nginx 对 `/icons/`、`/js/vendor/` 采用 `try_files $uri =404`：缺失文件返回 404，不会静默回落 index.html
- 实测：拦截 `api.iconify.design`/`cdn.jsdelivr.net` 后，登录并遍历主要页面 → 外网请求 **0**、图标 404 **0**、破损图标 **0**

## 五、已知限制
## 四之三、发版遗留缺陷（已知限制 · 带缺陷冻结口径 A）

> 口径：2026-10-25 用户选定 **A（带缺陷冻结）**，以下 9 条 P1 不随 v2.1.0 修复，**顺延 v2.1.1**，随 `BL-75` 跟踪。

| BUG | 影响面 | 现状 | 缓解/说明 |
|---|---|---|---|
| BUG-200 | **前台聊天**对「sn 为空的平台智能体」返回 500 | 待修（方案待定） | 后台/harness 通道正常；前台请使用已配置 sn 的智能体 |
| BUG-199 | 嵌入模型端点不可达/挂起时，检索可能长时间挂起 | 修复中（短超时预检兜底） | 已加短超时 + 降级提示，未根治 |
| BUG-201 | ollama 思考内容展示 | 已修复（待现场回归） | 本版自测：SSE thinking 帧 105 / 1736 字 |
| BUG-202 | 工具重复调用熔断可能误伤正常流程 | 已修复（待功能回归） | 熔断改「连续」语义 + 阈值 3→5 |
| BUG-203 | 首帧超时后仍继续写同一消息 | 已修复（待现场回归） | 终态守卫 + 上游取消 |
| BUG-204 | 对话「一片空白」（重复工具调用致空回答） | 已修复（待现场回归） | 空回答可读兜底 + 熔断原因可见 |
| BUG-205 | todo 状态跨轮不一致 | 已修复（待现场回归） | 每轮开始前规范化「最多一个 in_progress」 |
| BUG-149 | 干净环境无法产出前端镜像（打包链） | 新建 | 现有镜像可用；打包脚本需修 |
| BUG-195 | 站点向量表缺 id 唯一约束 | SQL 已提供，待现场执行 | 按 `V2.1.0_03` 执行即可 |


- 前端暂未提供"重试"入口（超时后重试落新消息的能力后端已具备）。
- 思考展示依赖既有 `thinking-display` 组件；ollama 思考模型在 `auto` 模式下按模型名启用。
- Xinference 的流式/工具调用尚未在真实实例上验证（见 `docs/XINFERENCE.md` §三）。
