# v1.7.0 配置变更（config/changes.md）

| # | 文件 | 变更 | 来源 | 生效条件 |
|---|---|---|---|---|
| 1 | `docker/docker-compose.yaml` | backend 新增环境变量 **`PHOENIX_KB_PATH_GUARD`**（默认 `observe`；曾按用户口令临时 `enforce` 做端到端验证，同日**退回 observe**） | `kb-access-isolation` T-08 | 重建/重起 backend |
| 2 | 无 nginx 配置变更 | — | — | — |
| 3 | 无前端环境变量变更 | — | — | — |

**说明**：本版本**未新增**任何必须手工设置的配置项；`PHOENIX_KB_PATH_GUARD` 缺省即 `observe`（只记审计日志，不改变行为）。
