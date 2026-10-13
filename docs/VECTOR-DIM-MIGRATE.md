# 向量维度迁移（独立运维脚本，R-04）

> 场景：更换 embedding 模型导致向量维度变化（如 512 → 1024）。
> 脚本：`docker/scripts/vector-dim-migrate.sh`（随交付包分发）

## 一、为什么必须迁移

- `vector(n)` 是 pgvector 的**列级类型**，维度写死在表上；不迁移则写入/检索报维度不一致。
- 平台 **R-09 会动态校验"所有含 `embedding` 列的表"**，任一表维度与当前模型不一致即拒绝写入。
  ⇒ **不能只改一张表**：脚本默认处理全部相关表（可用 `--table` 限定）。

## 二、用法

```bash
# 先预演（事务内执行后回滚，数据不变）
bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container phoenix-postgres-1 --dry-run

# 确认后真跑（必须显式 --yes：脚本会 TRUNCATE 旧向量）
bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container phoenix-postgres-1 --yes

# 只处理单表
bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container phoenix-postgres-1 --table tbl_vector_store_rag --yes
```
环境变量：`PHX_PG_USER`（默认 phoenix）、`PHX_PG_DB`（默认 phoenix）、`PHX_PG_CONTAINER`（默认 phoenix-postgres-1）。

## 三、脚本做了什么（逐表）

1. **迁移前快照**（表/维度/行数）+ **重复 id 预检**（TRUNCATE 后无从查起；缺唯一约束会引发 `ON CONFLICT` 报错 BUG-195）
2. 删除 `embedding` 上的**性能索引**——`pg_constraint.conindid` 与 `*_pkey/*_key` 双重排除，**绝不误删主键/唯一约束**
3. `TRUNCATE` 旧向量（**换模型后旧向量语义作废**；同时避免类型转换的补零/截断）
4. `ALTER COLUMN embedding TYPE vector(<dims>)`
5. 重建同名 hnsw 索引（`vector_cosine_ops`）
6. **迁移后自检**：所有相关表维度 == 目标维度 且每表 `id` 有唯一约束；不通过则打印可照做的修复语句
   （`ALTER TABLE ... ADD CONSTRAINT ..._pkey PRIMARY KEY (id);` / 查重 / 去重），**不静默删数据**

退出码：`0` 成功且自检通过 ｜ `1` 自检不通过（已打印修复语句） ｜ `2` 用法或环境错误。

## 四、迁移后必做

1. 平台把 **EMBEDDING 默认模型**设为现场的对应维度模型（否则 R-09 仍会拒绝）
2. **知识库文档重新入库/重新嵌入**（旧向量已被有意清空）
3. 重启一次 backend（向量库维度在启动时装配）
4. 抽验：新建一篇知识库文档 → 检索能命中
