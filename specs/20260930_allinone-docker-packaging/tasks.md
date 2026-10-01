> 版本: v1.1.0 | 状态: 待重确认 | 更新: 2026-09-30 | 确认人: 陈卓 | 确认日期: 2026-09-30

# 任务清单：一键部署 Docker 交付包

> 验证口径：无测试基建 → 真实构建/真实部署/命令输出留证；本机验证走原生架构镜像，交付构建走 buildx linux/amd64（plan §1）。

## 1. 前置核实与数据准备

- [x] T-01 部署前置核实：① `all_data.sql` 对 `tbl_privilege_module/acl` 的覆盖度（全新装首登后菜单是否完整，缺口如实回文档）；② 现库 admin 用户的组/ACL 关联结构盘点（T-05 种子依据）；③ jar 字节码 major version 决定 JRE 21/23（plan 风险①）
  关联: R-07, A-04 | 依赖: 无
  验证方式: psql 计数 + `javap`/unzip class 头输出留档
  验收标准: 三项均有实测输出；若①发现缺口，回 requirements 补条款重确认而非静默塞种子

- [x] T-02 `docker/init/10_seed_admin.sql`：admin/123456（md5 盐哈希 f1c457c84af9bc85acaeb64bee218755）+ 必要组/ACL 关联，幂等（WHERE NOT EXISTS）
  关联: R-07 | 依赖: T-01
  验证方式: 临时库执行两次零报错；`docker run` pg 容器实测登录接口返 token（口令 123456）
  验收标准: 不含任何真实密钥/无关用户数据；重复执行 no-op

## 2. 镜像与运行时

- [x] T-03 backend.Dockerfile 多阶段构建（含 .dockerignore、maven settings 钩子、JAVA_OPTS 容器参数），构建成功且镜像 grep 无口令
  关联: R-04, R-10 | 依赖: T-01③
  验证方式: `docker build` 输出；`docker history`+镜像内 `grep -r 'phoenix' 配置文件` 无明文口令；容器起服 `GET /echo/ok` 经 docker exec 200
  验收标准: JRE 层版本依 T-01③ 结论；TZ/MaxRAMPercentage 生效（日志/`jcmd VM.flags`）

- [x] T-04 frontend.Dockerfile + nginx 配置（SSE/上传/SPA/gzip 全参数，plan §2）
  关联: R-09 | 依赖: 无
  验证方式: 容器内 nginx 起服，`curl localhost/` 返回 index.html；conf 通过 `nginx -t`
  验收标准: 参数与 plan §2 逐项一致；本机 `pnpm build` 与容器产物一致性比对（dist 文件数/hash 抽样）

- [x] T-05 compose 契约：六服务定义、healthcheck、depends_on healthy/completed_successfully、`.env.example`、卷、project name 隔离
  关联: R-01, R-04, R-11, A-05 | 依赖: T-03, T-04
  验证方式: `docker compose config` 校验；`ss -tlnp` 仅 9080
  验收标准: 无宿主端口泄漏（pg/redis/backend 均无 ports 映射）；.env 变量表与 README 配置节一致

## 3. 初始化机制（本包核心）

- [x] T-06 migrator：哨兵表 `tbl_phoenix_release` + 首启全序 + 非首启增量 + 逐件事务与台账回写；migrator-post 两拍重放 V1.2.0_01
  关联: R-05, R-06, R-08, R-13 | 依赖: T-05
  验证方式: ① 空卷 `up -d` 后台账表=7 行（基线 2+V 件 5）与 NOTICE 输出；② 卷保留重复 `up` 台账不重复、零报错（AC-08）；③ 人为删台账一行→再跑只补该行；④ harness 表场景：backend healthy 后 status 列存在断言
  验收标准: 权威路径引用 sql/ 与 releases/ 原文件（R-02，无第二份）；任一事务失败即退出非零且日志可定位

## 4. 交付脚本与文档

- [x] T-07 断言脚本 `docker/scripts/verify.sh`：密钥红线（种子/git log -S/镜像层）、9080 唯一对外、admin 可登录、台账齐
  关联: R-17, R-01 | 依赖: T-06
  验证方式: 部署完成后实跑，全绿输出留档
  验收标准: 任一断言失败 exit≠0；AC-07 即此脚本输出

- [x] T-08 离线三件套 `build.sh / save-offline.sh / load-and-run.sh`（amd64 交付 + sha256）与 `backup.sh / upgrade.sh`（引用 releases，不造第二套回滚）
  关联: R-03, R-13, R-14 | 依赖: T-06
  验证方式: save tar 的 sha256 可校验、`docker load` 可导入（本机验证）；upgrade.sh 对不存在版本目录报错拒跑
  验收标准: 脚本幂等；README 步骤与脚本行为一致

- [x] T-09 README 八节（起停/离线/首登/配置/备份/升级/故障/安全红线，R-15）
  关联: R-15 | 依赖: T-08
  验证方式: 按 README 文本盲走一遍命令序列（AC-01 的复现路径），逐条可执行
  验收标准: 覆盖 plan §8 风险的环境坑说明（代理注入/跨架构耗时）

## 5. 端到端实测

- [ ] T-10 全链验收 AC-01~AC-06 + AC-08：全新卷一键起、admin 首登、SSE 对话+AI 生成（容器内网络出网）、down/up 零丢失、离线 load 部署冒烟、升降级演练（本库=升级演练、临时卷=降级）、重复 up
  关联: R-01~R-17 | 依赖: T-07
  验证方式: 逐 AC 命令输出/接口响应/日志留证（截图可省，文本为凭）
  验收标准: 八项 AC 全绿方可勾；任一红项修复后复跑该 AC 及受影响 AC

- [x] T-11 收口：artifacts.md 登记（哨兵表/种子/init 机制）、changelog 进度、bugs.md 走查期新发现登记、挂接 v1.2.0（用户 09-30 明示挤入）的台账说明（不动 releases 序号体系）
  关联: R-02, R-16 | 依赖: T-10
  验证方式: 文档交叉核对 + git status 干净（本 spec 全部产物入库）
  验收标准: 「纯增量」核验：`git diff main --stat` 无业务代码文件（docker/ 与 specs/ 之外零改动）
