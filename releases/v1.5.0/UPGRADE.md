# v1.5.0 升级指南

## A. 既有部署（v1.3.0/v1.4.0 → v1.5.0）
本版**零 DDL、应用代码零变化**——既有栈可以不升级照常跑。若想要 compose 重启自愈（BUG-63 修复）：
```bash
cd <repo> && git fetch && git checkout v1.5.0   # 或用发布包内 compose
cd docker
docker compose up -d --force-recreate --pull never postgres redis backend nginx
docker inspect <栈>-backend-1 --format '{{.HostConfig.RestartPolicy.Name}}'   # 应输出 unless-stopped
sh scripts/verify.sh    # 13 断言全绿
```
回滚：换回 v1.4.0 的 compose 文件同样 force-recreate 即可（数据卷双向兼容）。

## B. 新机器安装（本版主打）
- **Windows Server/Win10/11**：源码 zip → 管理员 PowerShell → `.\docker\scripts\bootstrap.ps1`（全自动：WSL2/引擎/打包/安装/收据）
- **Linux**：`bash docker/scripts/bootstrap.sh`
- **mac**：先装 Docker Desktop/Colima，再 bootstrap.sh
- **离线内网机**：有网机器 `package.sh` 出 tar.gz → 拷入 → 解包 → `bash install.sh [--offline]`
- 细节与故障速查：`docker/scripts/README.md`

## C. 升级既有安装（包对包）
```bash
tar -xzf phoenix-<新版>.tar.gz && cd phoenix-<新版>
bash install.sh --env-from <旧安装目录>/docker/.env    # 密码/端口承接，数据卷自动保留，IMAGE_TAG 自动跟版
```

## D. 验证
`phoenix-ctl.sh verify`（或 sh scripts/verify.sh）13 断言全绿；全新安装 [7] 出现 WARN 属预期（首轮对话后自动齐）。
