# v1.4.0 发布检查单（M4）
- [x] M3 汇总：config/changes + RELEASE-NOTES + UPGRADE + 运维脚本归位（零 DDL 声明）；git log v1.3.0..v1.4.0 57 提交交叉核对无漏
- [x] 正式镜像构建：jar/dist 全新重建 → phoenix-backend:v1.4.0(1.57GB) + phoenix-frontend:v1.4.0(190MB)
- [x] .env 切 v1.4.0 滚动 + verify **13/13 PASS, exit=0**（含 [13] 双面共存 [14] 轮次端点）
- [x] 全新库全链演练：phoenix_fresh 重放 exit=0 / 台账 9 行 / 表 56 / 零错误，已清库（postgres:16-alpine 本地缺失被 Hub 断连卡住，改用本地 pgvector 镜像当 psql 客户端跑 migrate.sh——L-12 现场应用）
- [x] 三板斧：用户当日浏览器多轮实测（追流/确认卡/文件面板，验收期补记在案）+ 服务端 curl 演练（2/2 计划确认全绿）；确认卡最新版视觉待用户复核（非阻塞，样式类）
- [x] bugs.md：16 单翻「已发布(v1.4.0)」
- [x] 版本分支打 tag v1.4.0 + 根 CHANGELOG + version.md 翻已发布 —— 本笔；「合并 main」「push」已完成（2026-10-03 用户「合并 并且 Push」：main=7fcad72 / 分支+tag 三 ref 全同步）
