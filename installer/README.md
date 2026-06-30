# Crest Core 安装资产

`installer` 目录只保留首版全新生产环境需要的安装资产，不再维护单机 Docker、内置数据库、离线升级包或历史版本升级脚本。

## 目录

| 路径 | 说明 |
| --- | --- |
| `init-sql/ob-oracle/crest-core-schema.sql` | OceanBase Oracle 空库一次性初始化 SQL |
| `init-sql/ob-oracle/README.md` | 初始化 SQL 使用说明 |
| `LICENSE` | GPLv3 许可证副本 |

## 数据库初始化

生产环境由 DBA 创建 OceanBase Oracle 租户、schema、账号和权限，然后执行：

```bash
obclient --default-character-set=utf8mb4 \
  -h <obproxy-host> -P 2883 \
  -u '<user>@<tenant>#<cluster>' \
  -p'<password>' \
  < installer/init-sql/ob-oracle/crest-core-schema.sql
```

通过 observer 直连时通常使用 `2881` 端口，用户名使用 `用户@租户`，不要带 `#集群`。

## 运行约定

- 系统库类型固定为 `CREST_DB_TYPE=ob-oracle`。
- 生产环境保持 `CREST_FLYWAY_ENABLED=false`，应用启动时不修改系统库结构。
- 生产环境保持 `CREST_LOAD_DEMO=false`。
- Kubernetes 清单位于 `deploy/kubernetes`，是首版主交付路径。

生成和校验初始化 SQL：

```bash
node scripts/generate-ob-oracle-init-schema.mjs
node scripts/generate-ob-oracle-init-schema.mjs --check
```
