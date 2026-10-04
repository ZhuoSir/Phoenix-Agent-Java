# v1.5.0 发布检查单（M4）
- [x] M3 汇总：审计对账绿（A=C 单spec/零DDL相符/进度属实/账证初对）；config/changes+RELEASE-NOTES+UPGRADE+checklist+sql/README 落盘；artifacts.md 补 R-10 增量
- [ ] 正式包构建：package.sh --version 1.5.0（arm64 本机验证 + amd64 视网络/真机情况）
- [ ] bootstrap 全链复跑（正式版本号，演练五同法）
- [ ] 开发栈 verify 13/13 回归
- [ ] T-12 Windows 真机（若届时已回传则销账；未回传按冻结决议以"延期在案"发版）
- [ ] bugs.md：已验证(v1.5.0)×2 → 已发布(v1.5.0)
- [ ] 版本分支打 tag v1.5.0 → CHANGELOG → 台账 → 问「合并 main」→ 问「push」
