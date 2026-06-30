# Crest Core 生产范围

Crest Core 是面向全新生产环境的轻量交付形态。外部产品名使用 `Crest Core`，但内部包名、镜像名、表前缀、配置前缀和 Kubernetes 资源名继续保持 `crest`，避免在代码、数据库和运行时引入无价值的改名风险。

## 默认运行边界

| 维度 | Core 默认 |
| --- | --- |
| JDK | OpenJDK 17 |
| 系统库 | OceanBase Oracle |
| 业务数据源 | OceanBase Oracle、Excel、远程 Excel、API |
| 部署方式 | Kubernetes 多副本 |
| 数据库初始化 | DBA 预创建 OB Oracle 租户/schema/账号，执行 `installer/init-sql/ob-oracle/crest-core-schema.sql` |
| Flyway | 生产默认关闭 |
| Demo 数据 | 生产默认关闭 |
| 任务执行 | `crest-service` 组合后端 2 副本承载 API、worker、scheduler；worker 用 Redis Cluster Streams 协调，scheduler 用 Quartz JDBC Cluster 协调 |
| WebSocket | 外部 Redis Cluster 广播支持多副本 |

## 默认保留能力

- 工作台、仪表盘、数据大屏。
- OB Oracle 数据源连接、数据集建模、字段管理、数据预览和缓存同步。
- 图表编辑、筛选、联动、跳转、分享和导出。
- 用户、组织、角色、权限、站点设置、系统参数和默认字体渲染。
- Redis 任务队列、缓存、锁和多副本广播；共享 Redis 必须配置独立 `CREST_REDIS_KEY_PREFIX`。
- Quartz JDBC Cluster 调度投递；scheduler 随 `crest-service` 两副本运行，避免单 Pod 故障造成定时触发中断。

## 默认收起能力

Core 默认启用 `crest.internal-lite.enabled=true`，菜单入口不展示模板市场、工具箱、数据血缘、SSO 配置页、审计日志页、数据资产、缓存任务页和字体管理页。

以下外围能力默认关闭，生产清单显式保持关闭：

```yaml
CREST_API_DOCS_ENABLED: "false"
CREST_KNIFE4J_ENABLED: "false"
CREST_FEATURE_AI_ENABLED: "false"
CREST_FEATURE_SQLBOT_ENABLED: "false"
CREST_FEATURE_TEMPLATE_MARKET_ENABLED: "false"
CREST_FEATURE_FONT_MANAGEMENT_ENABLED: "false"
CREST_FEATURE_VISUALIZATION_BACKGROUND_ENABLED: "false"
```

生产 Kubernetes 清单设置：

```yaml
CREST_ALLOWED_DATASOURCE_TYPES: "obOracle,Excel,ExcelRemote,API"
```

因此前端只暴露 `OceanBase Oracle`、`Excel`、`ExcelRemote` 和 `API` 数据源类型，后端保存、连接校验、schema 读取和 provider 工厂入口也会拒绝其他类型。后续如果确实需要增加其他数据库类型，可改为逗号分隔，例如 `obOracle,Excel,ExcelRemote,API,pg`，但需要同步补充驱动、存储和验收用例。

## 可继续物理删除的候选项

当前 Core 先通过配置、菜单、初始化数据和后端校验收窄生产面，避免一次性大删造成回归。后续如果要进一步最小化，建议按下面顺序做独立 PR，每项都要补测试和扫描：

| 优先级 | 候选项 | 删除前提 |
| --- | --- | --- |
| P1 | 旧安装器、离线包、docker-compose 交付脚本 | Kubernetes 已是唯一交付路径，发布流程不再引用这些脚本 |
| P1 | MySQL、OB MySQL、通用 Flyway 迁移资源 | 首版只支持全新 OB Oracle 初始化，升级策略另行设计 |
| P1 | 未开放的数据源 provider 和前端入口 | `CREST_ALLOWED_DATASOURCE_TYPES` 已长期稳定，且对应测试覆盖 |
| P2 | SQLBot/AI 相关后端服务与前端页面 | 确认没有报表、数据集或权限菜单依赖这些接口 |
| P2 | 模板市场、字体管理、背景资源库 | 确认企业版不需要在线资源市场和自定义字体上传 |
| P2 | 旧 SSO 渠道和扫码登录适配 | 保留 OIDC/Casdoor，企业登录方案验收完成 |
| P3 | Demo 数据、示例脚本和工程效率样例 | 初始化 SQL 与文档已不引用 Demo 资产 |

不建议现在删除的能力：

- 用户、组织、角色、权限、审计基础能力；这是企业生产必需面。
- 导出、分享、缓存同步和任务队列；这些是 BI 使用链路核心能力。
- Redis 锁、Streams、缓存和 WebSocket 广播；这是多副本部署的协调基础。
- Excel、远程 Excel 和 API 数据源；这些通常是轻量 BI 生产入口。

## 非目标

- 不做旧版本历史数据迁移。
- 不默认启用 OceanBase Oracle 之外的数据库类业务数据源类型。
- 不默认启用 SQLBot/AI、模板市场、字体自定义管理、背景资源库和旧企业扫码登录渠道。
- 不在本轮物理删除所有兼容分支，避免引入大范围回归；先通过默认配置、菜单、初始化数据和后端校验把生产面收窄。
