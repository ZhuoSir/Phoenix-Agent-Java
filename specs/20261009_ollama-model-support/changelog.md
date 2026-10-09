# 变更日志：ollama-model-support

- **2026-10-09 v0.1.0 创建（Phase 0 立项）**
  - 挂载: **v2.0.0**（2026-10-09，启动版本问句确认；用户回复「挂2.0.0」）
  - 来源: **BL-29**（用户 2026-10-05 列入待办 → 2026-10-09 口令「这个需求要进入建设」）
  - 落实: 目录 + 四件套草稿（requirements/plan/tasks/changelog）；MILESTONE 纳入需求表加行；backlog BL-29 翻「已立项(v2.0.0)」
  - 立项同轮实勘（写入 requirements 假设与待确认问题）：本机 Ollama 0.40.2 已装 `gemma3:latest`；
    容器经 `host.docker.internal:11434` 实测可达；框架自带 `agentscope-extensions-model-ollama:2.0.0`（jar 在本机仓）
  - 用户范围拍板（2026-10-09）：三类模型类型**都支持** / provider 加 `ollama` 一等项 / **要做**模型列表拉取 / 本机已装 Ollama / **要求**工具调用 / 认可 `host.docker.internal` 写法
