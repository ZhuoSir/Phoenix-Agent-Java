# v1.4.0 升级指南

## A. 旧版交付栈升级（v1.2.x / v1.3.0 → v1.4.0）
```bash
cd <repo> && git fetch && git checkout v1.4.0
# 1) 重建两镜像（联网构建机；国内网络默认走阿里云/npmmirror，无需额外参数）
sh docker/scripts/build.sh                    # 或 multistage: 见 README-Windows-WSL2.md §4
# 2) 滚动
cd docker && cp .env.example .env 的增量键并入现有 .env   # IMAGE_TAG=v1.4.0；三个 TURN_* 键有默认可不配
docker compose up -d
# 3) 一次性运维脚本（存量孤儿清理，幂等）
docker compose exec -T postgres psql -U phoenix -d phoenix -f /init/../sql/ops/04_orphan_cleanup_ops.sql
#    （或宿主机: psql -f releases/v1.4.0/sql/ops/04_orphan_cleanup_ops.sql）
# 4) 验证
sh scripts/verify.sh        # 12 断言全 PASS
```
- 无 SQL 升级件（零 DDL），migrator 自动跳过
- 浏览器一次普通刷新（index no-cache 已保证）
- 回滚：compose 指回旧 tag 即可（零 schema 变更，数据双向兼容；孤儿清理脚本无需回滚——只动已删智能体的残留行）

## B. 全新环境
同 v1.3.0 指南（UPGRADE §B/C）：在线 `git checkout v1.4.0 && build.sh && compose up`；离线 `save-offline.sh`→`load-and-run.sh`。
Windows Server/无 Docker Desktop：见 `docker/README-Windows-WSL2.md`（WSL2 路线全指南）。

## C. 升级后必做（人工）
1. 首登改 admin 密码；模型管理确认真实 API key
2. 快速验收三板斧：长任务中途刷新（应自动续看）→ 点「停止」（应真停）→ 计划模式确认卡（应显示提炼计划、点击消失）
