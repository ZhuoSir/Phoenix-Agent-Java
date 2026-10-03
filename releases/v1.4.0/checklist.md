# v1.4.0 发布检查单（M4）
- [x] M3 汇总：config/changes + RELEASE-NOTES + UPGRADE + 运维脚本归位（零 DDL 声明）；git log v1.3.0..v1.4.0 57 提交交叉核对无漏
- [ ] 正式镜像构建：phoenix-backend:v1.4.0 + phoenix-frontend:v1.4.0（当前在跑 v1.4.0-dev）
- [ ] .env 切 v1.4.0 滚动 + verify 12 断言全绿
- [ ] 全新库全链演练（基线+v1.2.0×5+v1.3.0×3 重放，v1.4.0 零 DDL 无新增）
- [ ] 三板斧人工验收复跑（刷新续看/停止/计划确认卡）
- [ ] bugs.md：已验证(v1.4.0)×16 → 已发布(v1.4.0)
- [ ] 版本分支打 tag v1.4.0 → CHANGELOG 生成 → 台账翻悬空 → 问「合并 main」→ 问「push」
