# MILESTONE: v1.4.0

> 创建: 2026-10-02 | 状态: 立项（M0） | 类型: MINOR | 验证日期: -

**版本语义**：MINOR——向下兼容的新功能。首批纳入 BL-22 断线续传（流恢复）。

## 一、需求挂接表（M1）
| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261002_detached-stream | v1.0.0 | 开发完成待合并（六 AC 全绿，feature/detached-stream） | 待定 | 待定 | 断线续传：服务端脱离式执行+重进会话追流（BL-22） |

## 二、汇总进度（M3/M4 勾选）
| （缺陷）nginx 登录页路由豁免（BUG-56） | - | 已修复(v1.4.0) 待发 | - | - | 登出后 500 真身：代理 regex 吞 SPA 路由 |

- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）
