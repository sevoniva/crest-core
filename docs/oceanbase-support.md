# OceanBase Oracle 支持

Crest Core 首版固定使用 OceanBase Oracle 作为系统库，并默认开放 OceanBase Oracle 作为数据库类业务数据源。

## 支持范围

| 类型 | 用途 | 状态 |
| --- | --- | --- |
| `ob-oracle` | Crest 系统库 | 首版唯一支持 |
| `obOracle` | 业务数据源 | 生产默认开放 |
| `Excel` / `ExcelRemote` / `API` | 文件和接口类数据源 | 生产默认开放 |

业务数据源连接支持 observer 直连，也支持 OBProxy / ODP 代理连接。

## 系统库部署原则

生产环境按以下顺序部署：

1. DBA 创建 OceanBase Oracle 租户、schema、账号和权限。
2. DBA 执行 `installer/init-sql/ob-oracle/crest-core-schema.sql`。
3. 运维配置 Kubernetes Secret 和 ConfigMap。
4. 启动 Crest Core，保持 `CREST_FLYWAY_ENABLED=false`。

执行初始化 SQL 时，`obclient` 必须指定 `--default-character-set=utf8mb4`，避免中文初始化数据在客户端导入阶段乱码。

## 配置项

| 变量 | 说明 |
| --- | --- |
| `CREST_DB_TYPE` | 固定 `ob-oracle` |
| `CREST_DB_DRIVER_CLASS_NAME` | 固定 `com.oceanbase.jdbc.Driver` |
| `CREST_DB_HOST` | OBProxy / ODP 或 observer 地址 |
| `CREST_DB_PORT` | OBProxy / ODP 常用 `2883`，observer 常用 `2881` |
| `CREST_DB_URL` | 完整 JDBC URL，配置后优先于 host 和 port |
| `CREST_DB_USERNAME` | OBProxy 用户名使用 `用户@租户#集群`，observer 直连使用 `用户@租户` |
| `CREST_DB_PASSWORD` | Crest 系统库账号密码 |
| `CREST_FLYWAY_ENABLED` | 生产固定 `false` |
| `CREST_LOAD_DEMO` | 生产固定 `false` |

## 配置示例

OBProxy / ODP：

```bash
CREST_DB_TYPE=ob-oracle
CREST_DB_DRIVER_CLASS_NAME=com.oceanbase.jdbc.Driver
CREST_DB_HOST=10.0.0.10
CREST_DB_PORT=2883
CREST_DB_URL=jdbc:oceanbase://10.0.0.10:2883
CREST_DB_USERNAME=<user>@<tenant>#<cluster>
CREST_DB_PASSWORD=<password>
```

observer 直连时端口通常为 `2881`，用户名不要带 `#集群`。

## 初始化

```bash
obclient --default-character-set=utf8mb4 \
  -h <obproxy-host> -P 2883 \
  -u '<user>@<tenant>#<cluster>' \
  -p'<password>' \
  < installer/init-sql/ob-oracle/crest-core-schema.sql
```

## 验收清单

- 系统库能连接已初始化 schema 并完成登录。
- 生产 Kubernetes 只启动 `crest` 和 `crest-service` 两个 StatefulSet；`crest-service` 以 `CREST_RUNTIME_ROLE=all` 两副本承载 API、worker 和 scheduler。
- 外部 Redis Cluster readiness 通过，任务队列、锁和 WebSocket 广播可用；至少 3 个真实节点，所有 key/channel/stream/group 使用独立 `CREST_REDIS_KEY_PREFIX`，并保持同一个非空 Redis Cluster hash tag；共享 Redis 生产环境使用独立 ACL 用户和密码。
- OceanBase Oracle 业务数据源连接校验、schema 读取、SQL 预览、分页查询、数据集预览和图表查询可用。
- `CREST_ALLOWED_DATASOURCE_TYPES` 只开放 `obOracle,Excel,ExcelRemote,API`。
- 生产环境保持 `CREST_FLYWAY_ENABLED=false`，数据库变更由 DBA 在维护窗口执行。
- 生产 overlay 通过 `bash scripts/production-config-check.sh <production-overlay-path>`，确认没有占位符、弱密钥、localhost origin 或 `latest` 镜像。
