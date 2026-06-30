# Changelog

## v1.0.0 - 2026-06-28

Crest Core v1.0.0 是面向全新私有化生产环境的首个交付版本。该版本聚焦轻量 BI 核心能力，默认采用 OpenJDK 17、OceanBase Oracle 系统库、OceanBase Oracle 业务数据源和 Kubernetes 多副本部署，不包含历史版本原地升级链路。

### 首版范围

- 保留工作台、数据源、数据集、图表、仪表盘、数据大屏、导出、用户组织角色权限、站点设置、字体和系统参数。
- 生产默认启用 `crest.internal-lite.enabled=true`，收起模板市场、工具箱、数据血缘、SSO 配置页、审计日志页、数据资产和缓存任务页。
- 系统库只支持 `CREST_DB_TYPE=ob-oracle`，生产默认 `CREST_FLYWAY_ENABLED=false`、`CREST_LOAD_DEMO=false`。
- 业务数据源默认允许 `obOracle,Excel,ExcelRemote,API`，后端保存、连接校验和 schema 读取入口 fail-closed 拒绝其他类型。
- Kubernetes 清单拆分 API、worker、scheduler 和 frontend；API、worker、frontend 提供多副本与 PDB。
- 外部共享 Redis Cluster 用于缓存、分布式锁、任务队列和 WebSocket 广播，所有 key、channel、stream 和 group 通过 `CREST_REDIS_KEY_PREFIX` 隔离。

### 交付物

- `ghcr.io/sevoniva/crest-service:v1.0.0`
- `ghcr.io/sevoniva/crest-web:v1.0.0`
- `installer/init-sql/ob-oracle/crest-core-schema.sql`
- `deploy/kubernetes/`

### 验收

- 后端使用 OpenJDK 17 构建和测试。
- 前端使用 Node.js 22 / pnpm 11 构建。
- 首版初始化 SQL 由 `scripts/generate-ob-oracle-init-schema.mjs` 生成并校验。
- Kubernetes 清单通过生产规则校验和 `kubectl --dry-run=client` 校验。
