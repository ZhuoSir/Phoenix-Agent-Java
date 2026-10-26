# v2.1.1 发布说明

> 状态：**进行中**（2026-10-26）｜ 类型：PATCH

## 一、修复

- **BUG-212**：打包链在容器内构建前端镜像时失败
  （`ERR_PNPM_OUTDATED_LOCKFILE`：`apps/admin-ui/package.json` 的 `vxe-table` 依赖行未收录进 `pnpm-lock.yaml`）。
  实际代码走 `useVbenVxeGrid`（vben 封装），不直接 `import 'vxe-table'` ⇒ 删除该依赖行即可。
  **影响**：v2.1.0 无法在干净环境产出前端镜像（即 BUG-149 的具体成因）。

## 二、升级说明

- **无 SQL、无配置变更**；有数据库的现场无需执行任何升级件
- 部署：替换镜像（backend/frontend）或直接用 v2.1.1 交付包重装

## 三、兼容性

- 与 v2.1.0 行为一致（仅打包链修复）；接口/数据/配置零变更
- 已知限制沿用 v2.1.0（9 条 P1 以已延期随版，`BL-75` 跟进）

## 四、验证

- `package.sh` 8/8 步通过；出货门禁 4/4 PASS（含 `fresh-db-install` 真装一次）
- 运行栈冒烟：登录/首页/品牌/图标/vendor 全 200
