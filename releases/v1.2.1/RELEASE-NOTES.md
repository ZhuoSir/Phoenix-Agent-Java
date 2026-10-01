# v1.2.1 升级说明（发版：2026-10-01）

向下兼容的补丁版本，修复 v1.2.0 交付包验收期实测暴露的 4 个缺陷。**无库表变更、无配置变更、无新接口**，直接替换后端制品/前端 dist 即可。

## 修复
- **BUG-37（P1）** deepseek「测试连接」必挂、AI 生成间歇空内容/JSON 截断——根因：Spring AI 出站带 `Accept-Encoding: br` 但客户端无 brotli 解码器，deepseek(CloudFront) 回 br 时响应体解不出。修复：所有 OpenAI 兼容调用（chat/embedding/audio，含代理分支）统一钉 `Accept-Encoding: identity`。这同时是 BUG-22/23/32 早前间歇复发的总根因
- **BUG-31（P1）** 全新空库首次启动时，Java 自注册智能体（HumanInTheLoop 等）先读库后落行导致 NPE、应用起不来。修复：注册流程改为先 saveBySn 落行再 createHarnessAgent，判空双保险
- **BUG-34（P2）** 模型配置列表接口曾回显明文 apiKey。修复：出口脱敏 `sk-****尾4`；编辑/测试回传脱敏值时按 id 回源真实 key（不破坏原有连通性测试与保存）；测试失败日志不再落真实 key
- **BUG-32（P2）** AI 生成对模型输出形态（裸换行/非标准 JSON）容错不足致 42013。修复：空内容自动重试一次 + JSON 控制字符修复再解析 + 字段级兜底提取（与 BUG-37 形成纵深防御）

## 影响与注意
- apiKey 列表页现为脱敏显示；如需查看/更换，编辑时重新输入完整密钥即可，保持不变则沿用原值
- 无 SQL 升级件、无需执行 migration

## 包含的缺陷修复
BUG-31、BUG-32、BUG-34、BUG-37（明细见 `specs/_project/bugs.md`）
