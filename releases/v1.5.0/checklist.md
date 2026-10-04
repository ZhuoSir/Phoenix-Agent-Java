# v1.5.0 发布检查单（M4）
- [x] M3 汇总：审计对账绿（A=C 单spec/零DDL相符/进度属实/账证初对）；config/changes+RELEASE-NOTES+UPGRADE+checklist+sql/README 落盘；artifacts.md 补 R-10 增量
- [x] 正式包构建：以**演练五 bs-test 全链包为终证**（1.36GB/sha自校验过/收据卡）；1.5.0 与 bs-test 的增量（phoenix-ctl/README/compose重启策略）**已分别单项实证**（ctl dev栈实测/compose inspect unless-stopped/README纯文档）；正式版重跑经用户裁决省略（「真机演练已做过，直接发版」2026-10-04）；amd64 同 T-02 降级口径延真机
- [x] bootstrap 全链：演练五即终证（一条命令 16:00:48→16:10:41 收据卡，verify 12P+1预期W exit=0）；正式版本号复跑经用户裁决省略（同上）
- [x] 开发栈 verify 13/13 回归（M4 审计基线轮 exit=0，2026-10-04 17:3x）
- [x] T-12 Windows 真机：用户裁决「先不考虑」（2026-10-04 发版口令）——按冻结决议以**延期在案**发版，去向=真机随时可销账
- [x] bugs.md：BUG-62/63 翻「已发布(v1.5.0)」×2
- [x] 落账批处理→四步核验→tag v1.5.0→CHANGELOG→台账——本笔；「合并 main」「push」待用户口令
