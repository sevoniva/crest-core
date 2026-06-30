# Changelog

## v1.0.0 - 2026-06-28

Crest Core v1.0.0 是面向全新私有化生产环境的首个交付版本。该版本聚焦轻量 BI 核心能力，默认采用 OpenJDK 17、OceanBase Oracle 系统库、OceanBase Oracle 业务数据源、外部 Redis Cluster，并提供 Kubernetes 多副本和 Docker Compose 单主机生产交付。

### 首版范围

- 保留工作台、数据源、数据集、图表、仪表盘、数据大屏、导出、用户组织角色权限、站点设置、字体和系统参数。
- 生产默认启用 `crest.internal-lite.enabled=true`，关闭 SQLBot/AI、模板市场、API 文档页、字体上传和背景资源库等外围能力。
- 系统库只支持 `CREST_DB_TYPE=ob-oracle`，生产默认 `CREST_FLYWAY_ENABLED=false`、`CREST_LOAD_DEMO=false`。
- 业务数据源默认允许 `obOracle,Excel,ExcelRemote,API`，后端保存、连接校验和 schema 读取入口 fail-closed 拒绝其他类型。
- Kubernetes 生产清单只启动 `crest` 前端和 `crest-service` 组合后端两个工作负载；组合后端以 2 副本承载 API、worker 和 scheduler。
- Docker Compose 生产交付只启动 `crest-core-web` 和 `crest-core-service` 两个服务；后端通过 `--scale crest-core-service=2` 运行两个副本。
- 外部共享 Redis Cluster 用于缓存、分布式锁、任务队列和 WebSocket 广播，所有 key、channel、stream 和 consumer group 使用同一个环境专属 hash tag 隔离。

### 交付物

- `ghcr.io/sevoniva/crest-core-service:v1.0.0`
- `ghcr.io/sevoniva/crest-core-web:v1.0.0`
- `crest-core-v1.0.0-linux-amd64-offline.tar.gz`
- `crest-core-v1.0.0-linux-arm64-offline.tar.gz`
- `installer/init-sql/ob-oracle/crest-core-schema.sql`
- `deploy/kubernetes/`
- `deploy/docker/`

### 验收

- 后端使用 OpenJDK 17 构建和测试。
- 前端使用 Node.js 22 / pnpm 11 构建。
- 首版初始化 SQL 由 `scripts/generate-ob-oracle-init-schema.mjs` 生成并校验。
- Kubernetes 清单通过生产规则校验和 `kubectl --dry-run=client` 校验。
- Docker Compose 交付通过 `scripts/verify-docker-production.mjs` 和严格 env 校验。
