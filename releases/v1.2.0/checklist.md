# v1.2.0 发版检查单

## 产物完整性
- [x] sql/ 文件数 = MILESTONE.md 登记数（正向 5 = 5），rollback 一一配对（R1.2.0_01~05）
- [x] 每个 SQL 文件头注释块完整（版本/序号/来源/前置/可重入/预估/回滚）
- [x] config/changes.md 覆盖全部配置项（与三 spec artifacts 核对：6 键 + #7 数据变更；无遗漏 yml diff）
- [x] RELEASE-NOTES.md / UPGRADE.md 已评审（用户 2026-10-01「发版」口令视同评审通过）

## 演练（必须真做，不许纸面勾选）
- [x] 演练按 UPGRADE.md 走通（演练环境=全 Docker 交付栈 docker/）：fresh up 全序建库(基线三步曲+V01~05)、制品重建滚动×3、SQL 增量台账演练、配置核对(#1~6 代码默认+#7 实测)、启服——见 MILESTONE「M3 汇总校验记录」+ spec changelog 10-01 三节
- [x] 核心功能验证通过（验证人：陈卓 2026-10-01 UI 实操登录/技能管理/模型页/对话；agent verify 9/9 断言留档）
- [x] 回滚 SQL 全链逆序已走（phx_m3dbg R05→R01 全 errors=0；交付库 R05↔V05 往返 is_active 快照一致）；制品回退未演练=首个 tag 版本无前版可回，属规则允许的空项
- [x] 演练发现问题全部闭环：BUG-31(种子规避) BUG-33(nginx折叠) BUG-35/36(修复) BUG-34(遗留新建)；UPGRADE.md 已增补「方式B 一键Docker」节；无新增 SQL/配置需补号

## 发布
- [x] 4/4 spec 已合并 main（merge b2a5e4c），mvn package exit=0、前端 build exit=0、交付镜像已运行验证
- [ ] git tag -a v1.2.0 已打（message 含 RELEASE-NOTES 摘要 + spec 清单）
- [ ] 项目根 CHANGELOG.md 已追加 v1.2.0 条目（来源 = MILESTONE 升级项表）
- [ ] bugs.md 本版本「已验证」批量转「已发布(v1.2.0)」+ 日期
- [ ] releases/v1.2.0/ 全部产物随 tag 进 main

> 备注（M3 预校验，2026-09-30）：汇总集已在临时库做过「基线+01~05 全序 errors=0、断言、逆序回滚全 0」——
> 该记录不满足"演练"标准（未含制品部署/启服/真实流量验证），演练项仍须真做。
