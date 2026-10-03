# Tasks: docker-auto-pipeline

> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-03

## 组 1：基座与打包（构建机侧）

- [ ] T-01 编写 `docker/scripts/lib/common.sh` 基座库：带时间戳日志（tee 落盘+控制台）、九步状态机（`.phoenix-install.state` 完成位读写/断点续接）、mirror 竞速探活（curl -m 8 逐个试 /v2/）、收据卡渲染函数、失败退出函数（步骤号+日志路径）
  关联: R-01, R-02, R-09
  依赖: 无
  验证方式: `selftest.sh` 驱动全函数单测（状态机写读/断点续接/失败输出格式断言）；`shellcheck` 过；**用 mac 系统自带 `/bin/bash`（3.2）实跑 selftest**（L-05 兼容性硬证）
  验收标准: selftest 全绿于 bash 3.2 与 bash 5.x 双版本；shellcheck 零 error；日志/状态文件落盘位置与 plan §1.3 一致

- [ ] T-02 编写 `docker/scripts/package.sh`：环境自检（docker/buildx/磁盘余量≥镜像×2.5）→ mirror 竞速 → buildx 双侧 multistage（--platform linux/<arch>，MAVEN_SETTINGS/NPM_REGISTRY 随 --overseas 切换）→ save → payload 组装（deploy/bin/docs/engine 可选）→ manifest.json+SHA256SUMS → tar.gz → 解包自校验
  关联: R-01, R-02, R-03, R-08
  依赖: T-01
  验证方式: 本 mac 实出 arm64 包（全程日志核对：maven.aliyun.com / npmmirror / mirror 前缀出现、registry-1.docker.io 零直连）；`--arch amd64` 跨出第二包；两包分别解包 `sha256sum -c` 全对；`--overseas` 干跑核对 build-arg 切换；故意断一步（改坏 mirror 列表）验证失败输出格式「步骤 N/M+日志路径」
  验收标准: 双架构包产出且自校验通过；manifest 含版本/git描述/arch/构建时间/文件清单+sha256/引擎最低版本；构建机无需 JDK/Node（which 核查留证）

## 组 2：安装主链（目标机侧）

- [ ] T-03 编写 `install.sh` 九步状态机主链（Linux/mac）：探测（OS/arch 对照 manifest 拒装不符/sudo/磁盘/引擎 OSType 读取——windows 即拒/9080 端口预检）→ 引擎段占位（T-04 填充，本任务先做「有引擎→跳过」分支）→ sha 全量校验 → load（digest 比对跳过）→ .env 生成（openssl rand -hex 16、chmod 600、回显一次；已存在保留原值+新键追加提示）→ compose -p phoenix up -d（bin/ 独立 compose 兜底）→ healthy 轮询（5s×300s 可配）→ verify.sh（失败即 install 退 1，Q2 决议）→ 收据卡+RECEIPT 落盘
  关联: R-04, R-07, R-08, R-09
  依赖: T-01, T-02
  验证方式: 本 mac 以隔离项目名+隔离端口真装一遍（九步全绿留证）；**重跑第二次验证幂等**（load 跳过/引擎跳过/.env 不动）；改 .env 密码后重跑验证保留；杀进程模拟第⑥步中断后重跑验证断点续接；篡改包内一文件验证第③步拒装并列出文件名
  验收标准: mac 真装九步全绿+收据卡字段齐全（版本/arch/地址/账号/密码仅首次/数据目录/日志路径/下一步三行）；幂等/断点/拒装三场景实测通过

- [ ] T-04 实现引擎自动安装模块（install.sh 第②步 Linux 分支）：os-release 发行版识别（Ubuntu 20/22/24、Debian 11/12、CentOS/RHEL 8/9 系）→ apt/yum 国内源替换（备份原 sources）→ docker-ce+compose plugin 安装 → 装后 `docker info` 复核；不在支持列表的发行版→降级打印指引退出
  关联: R-02, R-04
  依赖: T-03
  验证方式: `docker run --privileged ubuntu:22.04` 伪靶机（dinD 前置装好）从零引擎真装到九步全绿；centos 系以 stream9 容器同法；不支持发行版（alpine）验证降级输出
  验收标准: Ubuntu/CentOS 伪靶机全链绿；降级分支输出 OFFLINE-ENGINE.md 指引且退出码 1；apt 源替换有备份文件留证

- [ ] T-05 编写 `install.ps1`（Windows WSL2 路线）：管理员检查 → 既有引擎 OSType=windows 拒绝+解释 → WSL2 探测/功能启用（dism 两 feature + wsl --install Ubuntu-22.04 --no-launch）→ 重启提示+状态文件续接 → 包拷入 WSL → `wsl bash install.sh --from-wsl` → Windows 侧收据（localhost 地址；旧 Win10 netsh 端口转发代执行）；嵌套虚拟化不可用如实停住
  关联: R-05
  依赖: T-03
  验证方式: `pwsh -NoProfile -Command` 语法解析过 + PSScriptAnalyzer（可用时）；三分支桩测（mock docker info 输出 windows/linux、mock wsl --status 缺失/就绪）验证走向正确；**真机全链移交用户 Server 2022 执行（T-10），agent 收日志判定**
  验收标准: 桩测三分支全对；状态文件断点在「功能启用后重启」场景续接正确；真机日志由用户回传后判定通过（L-11：真实环境实测，不以桩测代真机结论）

- [ ] T-06 离线降级与 `--with-engine-debs`：package.sh 可选打入 Ubuntu 22.04 amd64 引擎 deb 组（apt download 于对应容器内）；install.sh 断网检测（mirror 与官方源全不可达）→ engine/ 有 deb 则 dpkg 本地装全链继续，无则打印指引退出 1
  关联: R-06
  依赖: T-04
  验证方式: 伪靶机 `--network none` + debs 包全链真跑（引擎本地装起）；同靶机无 debs 验证降级退出；deb 组清单与体积入 manifest
  验收标准: 断网两场景（有 debs 成功/无 debs 体面退出）实测通过；--with-engine-debs 包体积增量 ~100MB 量级如实在文档标注

## 组 3：文档与收口

- [ ] T-07 幂等升级专项加固：跨版本升级路径验证（旧 .env 保留+新增键提示合并+migrator 增量自动执行+数据卷保留）；同版本重装=无损修复
  关联: R-07
  依赖: T-03, T-04
  验证方式: 伪靶机先装「上一版模拟包」（改 manifest 版本号的同构包）再装本版包，验证数据行保留（预置标记行）+ .env 原密码生效 + verify 绿
  验收标准: 升级后预置数据可查、密码未变、收据卡显示新版本；重装修复场景全绿

- [ ] T-08 文档套件：payload docs/（INSTALL.md 五平台速查、OFFLINE-ENGINE.md 断网装引擎、RECEIPT-SAMPLE.md）+ 仓库侧 README/UPGRADE 指路新流水线 + build.sh/save-offline/load-and-run 头部注释标注「手工链，日常用 package/install」（Q3 决议：保留不删）
  关联: R-04, R-05, R-06
  依赖: T-02, T-03, T-05, T-06
  验证方式: 文档内每条命令行抽取实跑核对（L-01 教训：不默写）；五平台各一段「从裸机到收据」完整命令序可由第三方照抄执行
  验收标准: 抽测命令全部真实可跑；旧三脚本注释就位且行为零变化（头部注释不进镜像）

- [ ] T-09 实测矩阵收口 + 共享面回归（身份矩阵对面断言）：①mac 全链复跑 ②ubuntu 伪靶机全链复跑 ③断网变体复跑 ④**开发栈（phoenix-release）verify 复跑 13/13 绿**（compose.yaml/verify.sh 未受扰证据）⑤build.sh --multistage 参数干跑核对（Dockerfile ARG 兼容证据）⑥旧 load-and-run 对新 tar 内 images.tar 可用性抽测
  关联: 全部 R（回归面）
  依赖: T-02, T-03, T-04, T-05, T-06, T-07, T-08
  验证方式: 六项全真跑，输出全量留档 changelog（L-07：双侧取证）
  验收标准: 六项全绿；任何一项红→回对应任务修复后全矩阵重跑

- [ ] T-10 台账收尾：completion.md/artifacts.md 生成；MILESTONE v1.5.0 需求表状态与件数更新；Windows 真机验收移交包+指引给用户（Server 2022），真机日志回收后 bugs/lessons 记账；spec changelog 收口
  关联: R-05（真机判定）, 流程
  依赖: T-09
  验证方式: completion 与实际勾选核对（审计判据同款）；移交包含 ps1+包路径+三步指引
  验收标准: 台账五件齐；真机移交物就位；未勾项（若有）原因+去向写明

## 自检
- R 覆盖：R-01(T-01/02) R-02(T-01/02/04) R-03(T-02) R-04(T-03/04/08) R-05(T-05/10) R-06(T-06/08) R-07(T-03/07) R-08(T-02/03) R-09(T-01/03)——**全覆盖，无孤儿任务**
- 依赖均指向更小编号，无循环
- 共享面验证规则：身份矩阵（plan §三）6 对象的对面断言全部落在 T-09 ④⑤⑥
- 粒度：10 任务，每任务一次会话可完成、可独立验证回滚
