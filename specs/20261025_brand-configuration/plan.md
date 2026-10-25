> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-25 | 确认人: （待确认）

# 技术方案：品牌外观配置化（brand-configuration）

## 一、坑核对（必备节，置顶）

已核对 `specs/_project/lessons.md` 全部 **112 条** active 坑，相交 5 条：

| 坑 | 与本方案的关系 | 避坑做法 |
|---|---|---|
| **L-113** 业务配置优先工程配置（application.yml） | 本 spec 用户明确选 **DB 运行时配置**，与 L-113 相反 | **显式例外并记录理由**：品牌外观需客户现场自助修改且要求"刷新即生效、不重启"，属**运行期运营配置**而非工程参数；工程参数（超时/上限/批大小）仍走 yml。二者边界写入 changelog |
| **L-111** 未导出/写错绑定导致点击静默失效 | 新增品牌设置页 + 保存按钮 + 上传，易"点了没反应" | 保存/上传必须 `catch` 并 `ElMessage.error`；网络面板无请求即视为缺陷；上线前用 DOM + 请求双证 |
| **L-104** 字符串 replace 必须断言命中数 | 要把 6 处硬编码改为读 store（`Phoenix Agent` / `Phoenix Admin` / favicon / 主题色） | 每处替换前 `grep` 计数并断言；改完 `grep` 确认零残留（除默认值兜底常量） |
| **L-63** 服务端判别列不可信前端 | 系统名称/色值由后端判定合法性（长度/色值格式/URL 白名单） | 后端校验：名称 1~40 字、色值 `^#[0-9a-fA-F]{6}$`、图片仅允许上传返回的 URL 或 http(s) |
| **L-64/同类** 未认证接口泄露 | 登录页需要品牌 ⇒ **GET 必须匿名** | GET 只返回 4 个公开字段（name/logo/favicon/primaryColor），不含操作人/时间等内部信息；其它接口全部鉴权 |

## 二、方案概述

```
管理页「外观设置」(超管)
   ├─ 名称输入 / 主题色选择器 / Logo+favicon 上传
   → PUT /platform/brand-config（超管校验 + 校验规则 + 审计）
   → tbl_platform_brand_config（单行，id=1，默认值随迁移预置）

启动 / 登录页
   → GET /platform/brand-config（匿名，仅公开字段，带 ETag/短缓存）
   → 前端 brandStore（失败回退默认值，不阻塞登录）
       ├─ document.title + favicon href 替换
       ├─ CSS 变量注入：--el-color-primary 及派生 --el-color-primary-light-{3,5,7,8,9}/dark-2
       ├─ 登录页品牌区 / 侧边栏标题 / 版权文案替换
       └─ 协议弹窗与隐私/条款正文平台名替换
```

**为什么是"启动时拉取 + CSS 变量"**：满足 R-05（刷新即生效、不重建不重启），且不引入运行时换肤框架。

## 三、改动面

**后端（phoenix-platform 域，新增 4 件）**

| 文件 | 改动 |
|---|---|
| `sql/V2.1.0_04__brand_config.sql`（+ rollback + 根 `sql/` 镜像） | 建表 `tbl_platform_brand_config`（单行）+ 幂等插入默认行（默认值 = 现状硬编码值） |
| `phoenix-platform-api`：`BrandConfigVO` / `BrandConfigUpdateDTO` | 公开字段：systemName / logoUrl / faviconUrl / primaryColor |
| `phoenix-platform-core`：`BrandConfigService(Impl)` | 读取（带 60s 本地缓存）/ 更新（校验 + 审计日志 + 清缓存）/ 资源上传（复用 `FileStorageServiceFactory`） |
| `phoenix-platform-rest`：`BrandConfigController` | `GET /platform/brand-config`（**匿名**，白名单路径）<br>`PUT /platform/brand-config`（超管）<br>`POST /platform/brand-config/asset`（超管，multipart，限 2MB，仅 png/jpg/svg/ico） |

**前端（admin-ui）**

| 文件 | 改动 |
|---|---|
| `src/api/core/brand-config.ts`（新） | get/update/upload 三个调用 |
| `src/stores/brand.ts`（新） | 拉取 + 回退默认 + 暴露 `applyBrand()` |
| `src/bootstrap.ts` | 启动时 `await loadBrand()`（失败降级）再挂载，注入 title/favicon/CSS 变量 |
| `src/views/system-management/brand-settings/index.vue`（新） | 外观设置页（名称/主色/Logo/favicon 上传 + 预览 + 保存） |
| `views/auth/components/BrandPanel.vue`、`PolicyDialog.vue`、`views/front/privacy-agreement.vue`、`views/front/terms-of-service.vue`、`index.html` | 硬编码文案/图标 → 读 store 或占位（`index.html` 的 title 由 bootstrap 覆写） |
| 路由 + 菜单 | 新增「外观设置」入口（超管可见） |

## 四、接口与数据流

```jsonc
// GET /api/platform/brand-config （匿名）
{"data":{"systemName":"Phoenix Agent","logoUrl":"/imgs/logo.png",
         "faviconUrl":"/favicon.ico","primaryColor":"#1677ff"}}

// PUT /api/platform/brand-config （超管，phoenix-token）
{"systemName":"××标准院智能平台","primaryColor":"#1f6feb","logoUrl":"...","faviconUrl":"..."}
// 校验失败示例：{"success":false,"message":"系统名称长度需在 1~40 字"}
// 非超管：{"success":false,"message":"仅超级管理员可修改品牌配置"}
```

## 五、关键设计决策

1. **单行表而非 key-value**：字段固定、强类型、易校验（`id=1` 约束单行；`CHECK (id=1)`）。
2. **匿名 GET**：登录页必须先拿到品牌；只暴露 4 个公开字段，加 60s 服务端缓存 + 前端版本号 query 防缓存。
3. **色阶派生在前端**：后端只存 1 个主色，前端用 HSL 明暗算法生成 `light-3/5/7/8/9` 与 `dark-2`，与 Element Plus 变量命名对齐。
4. **上传走既有存储**：复用 `FileStorageServiceFactory`（local/oss），返回 URL 存库；限制 2MB + 白名单扩展名（防 SVG XSS：`svg` 仅允许作为 logo，且响应头 `Content-Disposition: inline` + `X-Content-Type-Options: nosniff`）。
5. **默认值迁移**：SQL 幂等插入默认行；`R-04` 要求"未配置=现状"，故默认行必须与当前硬编码值逐字一致。
6. **审计**：`updated_by` / `update_time` 落库 + `log.info("[brand] 更新 by={} name={} color={}")`；不新建审计表（范围可控）。

## 六、配置项

| 键 | 默认 | 说明 |
|---|---|---|
| `phoenix.platform.brand.cache-seconds` | 60 | GET 结果服务端缓存秒数（0=不缓存） |
| `phoenix.platform.brand.asset-max-bytes` | 2097152 | 品牌图上传大小上限（2MB） |

> 说明：品牌**业务值**存 DB（用户决策 Q2=A），上述两个为工程参数，按 L-113 落 `application.yml`。

## 七、共享面身份矩阵（必备）

| 共享对象 | 既有身份 | 变更后预期行为 |
|---|---|---|
| 浏览器标题 / favicon | ① 登录页 ② 后台所有页面 ③ 前台 chat 页 | 三处均由 bootstrap 统一覆写；未配置时保持原值 |
| 品牌名文案（`Phoenix Agent`） | ① 登录页 BrandPanel ② 版权 ③ 协议弹窗 ④ 隐私页 ⑤ 服务条款 | 全部读 store；**grep 零残留**（默认值常量除外） |
| 主题主色 | ① Element Plus 组件 ② Vben 布局强调色 ③ 少量硬编码色值页面（datasource / ChatComposer / SystemSettingsModal） | 通过 `--el-color-primary` 及派生变量统一生效；硬编码色值页面**改为引用变量**（逐个确认，不遗漏） |
| Logo 图 | ① 登录页 ② 侧边栏折叠态 | 两处同源；未上传时用 `/imgs/logo.png` |
| 匿名接口面 | 登录页（未登录） | 仅 `GET /platform/brand-config` 匿名；**其它品牌接口全部鉴权**（超管） |

## 八、关键日志点清单（必备）

| 场景 | 级别 | 触发点 | 必带字段 |
|---|---|---|---|
| 品牌配置读取（缓存未命中） | DEBUG | `BrandConfigServiceImpl.get` | `nameLen, color, cacheHit=false` |
| 品牌配置更新 | INFO | `update` | `by(userId), systemName, primaryColor` |
| 品牌配置更新被拒 | WARN | `update` 校验/权限失败 | `by(userId), reason` |
| 品牌图上传 | INFO | `uploadAsset` | `by, ext, bytes, url` |
| 前端拉取失败降级 | WARN（前端 console + 上报可选） | bootstrap | `reason`（不阻塞启动） |

四类强制日志点：服务启停 不涉及；**外部调用**（存储服务）已覆盖（上传 INFO + 失败 WARN）；安全事件（越权/非法扩展名/超大文件）WARN 必记。

## 九、被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 放 `application.yml` | 用户 Q2 明确要运行时可改；改 yml 需重启，客户现场不可行 |
| 构建期 env（`VITE_BRAND_*`） | 需重新构建前端，客户无法自助 |
| 多租户多品牌表 | 本 spec 非目标（一套部署一套品牌），过度设计 |
| 用 Element Plus 主题编辑器全量换肤 | 超出"主色 + 衍生色"范围，且暗色模式另有排期 |
| 前端直接读 localStorage 存品牌 | 无服务端权威值，多端不一致，且不可审计 |

## 十、风险与规避

| 风险 | 规避 |
|---|---|
| 登录页在未登录时请求失败 ⇒ 白屏 | `loadBrand()` 加 3s 超时 + 失败回退默认值，**永不阻塞挂载** |
| 客户端缓存导致改名不生效 | 前端请求带 `?_v=timestamp`；服务端 60s 缓存可调 |
| SVG 上传被用作 XSS 载体 | 仅允许位图 + ico 作为 favicon；SVG 需服务端清洗（本期**不开放 SVG**，填 URL 才可用） |
| 色阶派生与 Element Plus 变量名不一致 | 按 EP 官方变量名生成（`--el-color-primary-light-3/5/7/8/9`、`--el-color-primary-dark-2`），并在页面逐一目检 |
| 改文案漏掉某处硬编码 | 交付前 `grep -rn "Phoenix Agent\|Phoenix Admin"` 必须为 0（默认值常量除外） |
