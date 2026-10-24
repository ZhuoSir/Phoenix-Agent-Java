# 前端表格使用规范（vben + vxe-table）

> 由来：2026-10-24 `group-management-ui` 里为「表格高度/分页/刷新/样式统一」来回折腾了十几轮。
> 本文把踩过的坑固化成规则，**以后新增任何表格先照本文做，不要再试错**。

## 一、只用一种表格组件

项目的表格统一是 **vben 的 `useVbenVxeGrid`**（`#/adapter/vxe-table`）。

- ✅ 用 `const [Grid, gridApi] = useVbenVxeGrid({ gridOptions: {...} })`，模板里 `<Grid />`
- ❌ **不要**用 Element Plus `ElTable` 自绘业务表格（样式/分页/空态/行高都跟全局不一致）
- ❌ **不要** `import { VxeTable } from 'vxe-table'`（admin-ui 未声明该依赖；vben 已封装，直接引用会构建失败）

## 二、分页：用网格内置分页器，别手写按钮

```ts
gridOptions: {
  pagerConfig: { enabled: true, pageSize: 10, pageSizes: [10, 20, 50] },
}
```
- ❌ 不要写 `<ElButton>上一页/下一页</ElButton>`（与列表页不统一）
- **`pagerConfig` 在同一个对象里只能出现一次**。写两次时后者覆盖前者（曾因 `enabled:false` 覆盖 `enabled:true`，整页签没有分页器）
- 远程分页：`proxyConfig` 必须放在 **`gridOptions` 内部**（放外面不生效），返回 `{ records, total }`
  并同时给 `page: { total }` 以兼容 vxe；本地数据分页额外传 `pagerConfig.total = rows.length`

## 三、更新数据：要么整份给，要么走 query

- ✅ 首次/重建：把 `columns + height + pagerConfig + data` **整份**传 `gridApi.setGridOptions({...})`
- ❌ **不要**只传 `columns`：会把 `height`/`pagerConfig` 一起冲掉（曾导致分页器消失、高度跳变）
- ⚠️ 每页/每次查询后重新拉数：用 `gridApi.query()`（proxy 模式）

## 四、高度：统一常量 + 同一结构

- 所有表格统一 `height: 460`（抽屉内）/ `height: 'auto'`（列表页）
- 各 Tab 的 **DOM 结构必须一致**（同样的按钮行 + 直接放网格）；曾因某个 Tab 多套了一层
  `<div style="height:460px">` 导致那一页表格被夹矮
- 空态交给网格：`emptyText: '暂无数据'`；**不要**用 `v-if` 再手写一行"暂无数据"（会多出一块空白）

## 五、展示型字段在数据层转换

- 状态/枚举（如 `accountStatus` 1/0 → 启用/禁用）**在拉数后映射成中文**再喂给表格
- ❌ 不要依赖 vxe 的 `formatter` / `slots` 透传（vben 包装下曾不生效，显示仍是数字）

## 六、写操作后必须刷新

- 弹窗/抽屉里的写操作成功回调，要同时刷新**外层列表**与**当前抽屉内的表格**：
```ts
function onAssignSuccess() {
  refreshGrid();                       // 列表
  if (detailVisible.value) {
    loadDetail();                      // 抽屉其它数据
    memberGridApi.query();             // 人员表格
  }
}
```
- 弹窗绑定写 `@success="onAssignSuccess"`（**不要**写成 `@success="refreshGrid()"`，那样抽屉不会刷新）

## 七、验证方式（别再用"构建通过"当证据）

1. **DOM 断言**：`browser_inspect observe` 看表格列/行/分页文案（不受截图缓存影响）
2. **数据断言**：写操作后**查数据库**确认落库（例：保存描述后 `select description from tbl_platform_group_info`）
3. 截图仅在能刷新页面时使用；**同一 sha 重复出现 = 缓存图，不要当证据**
