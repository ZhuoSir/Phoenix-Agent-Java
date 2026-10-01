# v1.2.1 发版检查单

## 产物完整性
- [x] 无 SQL 件（纯代码 PATCH，sql/ 目录空、config/changes.md 记"无"）
- [x] RELEASE-NOTES.md / UPGRADE.md / checklist 齐全
- [x] 台账对齐：BUG-31/32/34/37 均「已修复(v1.2.1)」，MILESTONE 纳入表一致

## 演练（实测留档，A 交付栈 v1.2.1-dev 镜像）
- [x] deepseek「测试连接」修复前 100% 挂、修复后 3/3 通过；qwen 1/1（BUG-37）
- [x] 双项 AI 生成 21s 成功、四段齐全（BUG-37+32 联合）
- [x] 空库首启 NPE 测试：dump 克隆库删光 5 sn 行 → 新镜像首启 Started、NPE=0（BUG-31）
- [x] apiKey 列表全脱敏 + 脱敏值回传测试连接按 id 回源成功（BUG-34）
- [x] 用户界面复验通过（2026-10-01 确认）

## 发布
- [x] 全部修复合并 main（0006a5a + ace8fb9），构建 exit=0
- [x] git tag -a v1.2.1（见下条 commit 后打）
- [x] 项目根 CHANGELOG.md 追加 v1.2.1 条目
- [x] bugs.md 本版本修复转「已发布(v1.2.1)」
- [x] releases/v1.2.1/ 产物进 main
