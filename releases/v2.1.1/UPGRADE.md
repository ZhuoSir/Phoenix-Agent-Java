# v2.1.1 升级说明

> 适用：v2.1.0 → v2.1.1（PATCH，仅打包链修复）

## 一、升级件

**无**（无 SQL、无配置变更）。

## 二、部署

1. `docker compose -p phx210 down`（保留卷）
2. `docker load -i phoenix-2.1.1-arm64.tar`（或用交付包 `install.sh` 覆盖安装）
3. `docker compose -p phx210 up -d`
4. 等三容器 `healthy`

## 三、验证

| # | 验证 | 期望 |
|---|---|---|
| 1 | `POST /api/privilege/auth/login` | 200 |
| 2 | `GET /` | 200 |
| 3 | `GET /platform/brand-config` | 200 |
| 4 | `GET /icons/lucide.json` | 200 JSON |
| 5 | `GET /js/vendor/echarts.min.js` | 200 |

## 四、回滚

回退镜像到 v2.1.0 即可（无数据变更，无需回滚 SQL）。

## 五、已知限制

沿用 v2.1.0（见 `releases/v2.1.0/RELEASE-NOTES.md` §四之三，`BL-75` 跟进）。
