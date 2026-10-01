# v1.2.1 升级操作手册

纯 PATCH，无 SQL、无配置变更。

## 方式 A：手工升级（沿用 v1.2.0 部署形态）
1. 停服 → 替换 `phoenix-admin.jar`（含 4 个修复）+ 管理端前端 dist（apiKey 编辑态提示语）
2. 启服
3. 验证：模型管理「测试连接」deepseek 通过；列表页 apiKey 显示 `sk-****xxxx`；AI 生成双项正常

## 方式 B：Docker 交付包（docker/）
1. 重新构建镜像：`sh docker/scripts/build.sh`（thin 模式复用 host jar/dist）或改 `IMAGE_TAG=v1.2.1`
2. `cd docker && docker compose up -d --force-recreate backend nginx`
3. 数据卷不动（本版本无 migration）

## 回滚
无结构变更，回滚=换回 v1.2.0 制品即可。
