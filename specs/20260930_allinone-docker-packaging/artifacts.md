# 升级件登记: allinone-docker-packaging

| 类型 | 内容摘要 | 来源任务 | 草案位置 | 已汇总至里程碑 |
|---|---|---|---|---|
| 交付物 | docker/ 目录：compose+两 Dockerfile+nginx conf+init(00/10/20/migrate/post)+scripts(build/save/load/backup/upgrade/verify)+README | T-03~T-09 | `docker/` | -（v1.2.1 未立 M0） |
| 机制 | 哨兵台账表 `tbl_phoenix_release`（包私有，**不进 releases 序号体系**，plan §9） | T-06 | `docker/init/migrate.sh` | - |
| 种子 | admin/123456 + 5 个自注册 sn 行（幂等 INSERT，包内文件，不改仓库 sql/） | T-02 | `docker/init/10_seed_admin.sql`、`20_seed_runtime_agents.sql` | - |
| 配置 | `.env` 键：PHOENIX_HTTP_PORT/PG_PASSWORD/IMAGE_TAG/JAVA_OPTS(带引号)/PGVECTOR_IMAGE/REDIS_IMAGE/PGCLIENT_IMAGE/JRE_BASE_IMG/NGINX_BASE_IMG/JAVA_BIN | T-05 | `docker/.env.example` | - |
| 依赖修复 | admin-ui 补声明 `json-bigint`（dev 侥幸、生产 build 必挂的真缺失） | T-04 | `web-frontend/apps/admin-ui/package.json` | - |
| 回滚 | 不适用：交付物为纯新增目录；删除 docker/ 即回退（卷数据用 `docker compose down -v` 清理，README §5/§6） | - | - | - |
