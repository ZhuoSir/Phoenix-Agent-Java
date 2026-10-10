# v2.0.0 配置变更汇总（config/changes.md）

> 冻结批次（M3 汇总）| 生成 2026-10-10
> 判据来源：`git diff v1.7.0..v2.0.0 -- docker/docker-compose.yaml docker/.env` + 三个 spec 的 `artifacts.md` 配置行
> 结论：**本版仅 1 处配置变更（新增命名卷）**，无环境变量 key 增删改；`artifacts.md` 中配置类型行数为 0，与本节一致。

## 一、变更清单

| # | 类型 | 位置(dataId/文件) | key | 旧值 | 新值 | 来源 spec | 需重启 |
|---|------|------------------|-----|------|------|-----------|--------|
| 1 | Docker Compose 卷 | `docker/docker-compose.yaml` → `services.backend.volumes` | `pylibs:/app/.local` | （无） | 新增命名卷 `pylibs` 挂载至 `/app/.local`（顶层 `volumes:` 同步新增 `pylibs`） | specs/20261007_user-role-group-model（BUG-164 修复） | 是（backend 容器重建） |

**变更动机**（BUG-164）：容器以 uid 10001 运行、`HOME=/` 且系统 site-packages 属 root 不可写 ⇒ 智能体在运行期 `pip install` 文档类依赖（report-generator 等报告类技能）失败。改为 `PIP_USER=1` + `PYTHONUSERBASE=/app/.local` 并把该目录挂成命名卷：① 用户级安装目录可写；② **跨容器重建保留**；③ 命名卷首次创建时从镜像目录预置内容，镜像内预装的 `python-docx/openpyxl/python-pptx/Pillow` 一并带过来。

## 二、无需变更的项（显式声明）

| 项 | 状态 |
|----|------|
| 环境变量 key（`SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_HOST` / `PHOENIX_AGENT_WORKSPACE_ROOT` / `PHOENIX_AGENT_FILES_ROOT` / `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS` / `PHOENIX_KB_PATH_GUARD` 等） | **无增删改**（`git diff` 实测：compose 中仅新增上述挂载行） |
| `.env` 文件 key | **无增删改**（`IMAGE_TAG` / `PHOENIX_HTTP_PORT` / `PG_PASSWORD` / `JAVA_OPTS` 沿用） |
| Nacos / 配置中心 | 本项目不使用（零 `application.yml`，全部走环境变量或 `@ConfigurationProperties` 默认值） |
| 前端运行时配置 | 无（Ollama 默认地址等写在代码 `providerBaseUrlMap` 内，随镜像发布） |

## 三、升级操作要点

- 命名卷**无需手工创建**，`docker compose up -d` 会自动建立并从镜像预置内容；
- **滚动注意**：旧容器（无该卷）升级后，运行期已 pip 安装到旧可写层的包位于 `/app/.local` 之外，重建后不再可见 ⇒ 若智能体此前自行装过额外包，升级后需重新安装一次（镜像预装的四个包不受影响）；
- 回滚：删除挂载行与顶层 `pylibs:` 定义即可（数据卷可保留，不占用业务语义）。
