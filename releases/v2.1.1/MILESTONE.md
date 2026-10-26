# 里程碑 v2.1.1

> 状态：**进行中**（2026-10-26 创建）
> 类型：**PATCH**（打包链修复，不含功能变更、不含 SQL、不含配置变更）
> **版本分支**: `2.1.1`（基点 = `main` @ `b99a3db5`，即 v2.1.0 发版后的 main）
> 交付包：修复后重打 `phoenix-2.1.1-arm64.tar.gz`（sha256 见收据）

## 一、修复项

| BUG | 描述 | 状态 |
|---|---|---|
| BUG-212 | `apps/admin-ui/package.json` 残留 `vxe-table` 依赖行，锁文件未收录 ⇒ 容器内 `pnpm install --frozen-lockfile` 失败，`package.sh` 第 3/8 步中断（BUG-149 的一个具体成因） | 已修复(v2.1.1) |

## 二、升级件

**无**（无库表、无配置变更）。升级方式：替换镜像/交付包即可。

## 三、验证

- `docker/scripts/package.sh --arch arm64 --version 2.1.1` → 8/8 步通过
- 出货门禁 4/4 PASS（`sha256sums` / `jar-readable` / `prereq-match` / `fresh-db-install`）
- 运行栈冒烟：登录 / 首页 / 品牌 / 图标 / vendor 全 200

## 四、已知限制

沿用 v2.1.0 的已知限制（9 条 P1 以「已延期」随版，随 `BL-75` 排期），本版未新增。
