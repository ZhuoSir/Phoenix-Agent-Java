# v1.3.0 升级指南

## A. 已在旧版交付栈（≤v1.2.1 / v1.3.0-rcX）
```bash
cd <repo> && git pull && cd docker
IMAGE_TAG=<新镜像tag> docker compose build backend nginx   # 或 build.sh 后 retag
docker compose up -d                                        # migrator 自动补 V1.3.0_01~03
sh scripts/verify.sh                                        # 全绿即成
```
- 浏览器需一次普通刷新（index.html no-cache 已保证后续免刷）
- 存量智能体知识自动迁移为知识库，召回结果对照验证不变（spec AC-04 已演练）
- 回滚：compose 指回旧 tag + `psql -f sql/rollback/R1.3.0_0X__*.sql`（逆序），数据侧仅删新表/新列

## B. 全新环境（在线构建机）
```bash
git clone <repo> && cd <repo> && git checkout v1.3.0
cp docker/.env.example docker/.env    # 设 PG/Redis 口令
sh docker/scripts/build.sh && cd docker && docker compose up -d && sh scripts/verify.sh
```

## C. 离线环境
构建机 `sh docker/scripts/build.sh --amd64 && sh docker/scripts/save-offline.sh` →
目标机 `sh docker/scripts/load-and-run.sh <tar>`

## 升级后必做（人工）
1. 管理端「模型管理」确认真实 API key（仓库仅占位符 sk-xxxxxx）
2. 首登改 admin 密码（种子默认 123456）
3. 验证一键：`sh docker/scripts/verify.sh`（11 断言，含本版新增文件面板/知识库项）
