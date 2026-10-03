# 升级件登记: fix-bug-batch

- 零 DDL（schema 无变更）。
- 配置变更：vite dev 端口生效 VITE_PORT（前端工程行为修正，非部署配置）；application-test.yml 内容更新（开发者本地 profile，非交付件）。
- 运维脚本（非 schema）：specs/20261002_fix-bug-batch/sql/04_orphan_cleanup_ops.sql —— BUG-14 存量孤儿一次性清理，现网 2026-10-02 已执行（干跑清单 0、执行 0 错）。M3 汇总时并入 UPGRADE.md 操作段引用。
- API 新增：POST /api/knowledge-base/{id}/re-embed（T-04）。
