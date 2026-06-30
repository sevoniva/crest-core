# Crest 可观测性

Crest Core 支持按需启用 Prometheus 指标端点。默认 Kubernetes 交付不内置 Prometheus、Grafana、Alertmanager 或 dashboard provisioning；生产环境应接入企业已有监控平台。启用后，后端通过 `/api/v1/actuator/prometheus` 输出指标，Prometheus 使用 Bearer Token 访问 `crest-service:8100` 或实际后端服务地址。前端 Nginx 默认不公开该路径。

建议在企业 Grafana 中维护 6 类看板：

| 看板 | 用途 |
| --- | --- |
| Crest 服务总览 | 服务采集状态、请求量、错误率、P95 延迟、CPU、磁盘和高频接口 |
| Crest API 监控 | API 纳管率、模块分类、模块请求量、模块错误量、接口明细和未访问接口清单 |
| Crest 告警与 SLO | 可用性、5xx 错误率、P95 延迟、堆内存、连接池和当前告警 |
| Crest JVM 运行时 | JVM 内存、GC、线程、类加载和运行时长 |
| Crest 数据库与连接池 | Hikari 连接池、JDBC 连接、连接获取耗时和连接等待 |
| Crest 任务与缓存 | 线程池、调度任务、缓存命中、缓存写入和缓存清理 |

## 接入模式

Crest 不要求也不默认安装 Prometheus/Grafana。生产环境按现场监控体系接入：

| 模式 | 适用场景 | 做法 |
| --- | --- | --- |
| 只开启 metrics | 企业已有 Prometheus 和 Grafana | 在生产 overlay 环境文件中设置 `CREST_PROMETHEUS_ENABLED=true` 和 `CREST_PROMETHEUS_TOKEN`，在企业 Prometheus 中增加抓取任务，导入 Crest 看板和告警规则 |
| 外部托管监控 | 企业使用托管 Prometheus、Grafana 或统一 APM | 只暴露后端 metrics，由平台侧配置抓取、看板、告警和通知 |

企业 Prometheus 抓取示例：

```yaml
scrape_configs:
  - job_name: crest-service
    metrics_path: /api/v1/actuator/prometheus
    scheme: http
    bearer_token: <prometheus-scrape-token>
    static_configs:
      - targets:
          - crest-service:8100
```

## Kubernetes

当前 Kubernetes 交付清单不内置 Prometheus 和 Grafana。生产环境如需纳入企业监控，按以下方式接入：

1. 在 `.local/crest-production.env` 中设置 `CREST_PROMETHEUS_ENABLED=true`；
2. 在 `.local/crest-production.env` 中设置至少 32 位的 `CREST_PROMETHEUS_TOKEN`，由 `scripts/render-production-overlay.sh` 写入生产 Secret；
3. 在企业 Prometheus 中增加抓取任务，目标为 `crest-service.<namespace>.svc:8100`；
4. 在企业 Grafana 中按现场规范配置看板和告警。

严格生产配置检查允许 Prometheus 继续保持默认关闭；如果显式开启，则会要求 Secret 中存在 `CREST_PROMETHEUS_TOKEN`，避免指标端点在生产环境无认证暴露。

前端 Service 不公开 `/api/v1/actuator/` 下的任何端点。抓取指标时应访问后端 Service `crest-service`，Kubernetes 健康探针应直连后端 Pod 或 Service。

## 指标口径

| 指标 | 口径 |
| --- | --- |
| `crest_api_route_info` | Crest 启动后注册的 API 路由清单，包含 `method`、`uri`、`module`、`controller`、`handler` 标签 |
| `http_server_requests_seconds_count` | HTTP 请求次数，按 `method`、`uri`、`status`、`outcome` 等标签聚合 |
| `http_server_requests_seconds_sum` | HTTP 请求耗时总和 |
| `http_server_requests_seconds_bucket` | HTTP 请求耗时直方图，用于 P95、P99 计算 |
| `jvm_memory_used_bytes` | JVM 内存使用量 |
| `jvm_gc_pause_seconds_*` | GC 停顿次数和耗时 |
| `jvm_threads_*` | JVM 线程数量 |
| `executor_*` | WebSocket、审计日志、调度等运行时线程池状态 |
| `hikaricp_connections_*` | Hikari 数据库连接池状态 |
| `process_cpu_usage` | 当前进程 CPU 使用率 |
| `up` | Prometheus 采集状态 |

`CREST_PROMETHEUS_MAX_URI_TAGS` 用于限制 `http.server.requests` 的 URI 标签数量，默认 `200`。生产环境 URI 模板异常增长时，应先排查路由或代理配置，避免高基数指标影响 Prometheus。

## API 纳管口径

API 监控区分两个概念：

| 名称 | 含义 |
| --- | --- |
| 接口纳管率 | 已注册并暴露到 `crest_api_route_info` 的 API 占比。正常值为 `100%` |
| 已访问业务接口 | 当前 Prometheus 时间范围内发生过请求的 API 数量 |
| 未访问接口清单 | 已纳管但当前时间范围内没有访问记录的 API。该清单用于测试覆盖和巡检，不表示监控缺失 |

业务模块由路由前缀归类，当前包含 `visualization`、`dataset`、`datasource`、`access-control`、`system`、`sharing`、`authentication`、`export`、`lineage`、`operations` 和 `platform`。

## SLO 建议

| 目标 | 建议口径 | 默认告警阈值 |
| --- | --- | --- |
| 可用性 | `avg_over_time(up{job="crest-service"}[窗口])` | 连续 2 分钟采集失败触发 critical |
| 5xx 错误率 | 5xx 请求速率 / 总请求速率 | 连续 5 分钟高于 2% 触发 warning |
| P95 延迟 | HTTP 请求耗时直方图 P95 | 连续 5 分钟高于 2 秒触发 warning |
| JVM 堆内存 | 已用堆内存 / 最大堆内存 | 连续 10 分钟高于 85% 触发 warning |
| 数据库连接池 | 活跃连接 / 最大连接 | 连续 5 分钟高于 85% 触发 warning |
| 线程池队列 | `executor_queued_tasks` 汇总 | 连续 5 分钟超过 100 触发 warning |

上述阈值用于默认交付。正式生产环境应结合部署规模、业务峰值、数据库规格和 SLA 要求调整。

## 告警规则

建议在企业监控平台中维护以下规则分组：

| 分组 | 规则 |
| --- | --- |
| `crest.availability` | 服务采集中断、API 路由清单缺失 |
| `crest.api-slo` | HTTP 5xx 错误率偏高、HTTP P95 延迟偏高 |
| `crest.runtime` | 进程 CPU 偏高、JVM 堆内存偏高、线程池队列积压 |
| `crest.database` | 数据库连接池使用率偏高、数据库连接等待 |

生产环境需要通知能力时，应接入企业已有 Alertmanager、Grafana Alerting 或统一告警平台。

## 排查入口

| 现象 | 优先检查 |
| --- | --- |
| Grafana 面板显示 No data | Prometheus target、Grafana datasource、容器代理变量、看板变量 |
| 接口纳管率不是 100% | `crest_api_route_info` 是否存在、服务是否重启完成、Prometheus 是否已重新抓取 |
| 5xx 错误率升高 | 后端日志、最近发布、数据库连接池、慢 SQL、外部依赖 |
| P95 延迟升高 | 慢接口 Top、数据库连接池、CPU、GC、线程池队列 |
| 连接池使用率升高 | 慢 SQL、连接泄漏、数据库连接上限、数据库负载 |
| JVM 堆内存升高 | 缓存增长、导出任务、对象保留、GC 频率 |

## 系统页面

管理员登录后进入：

```text
系统设置 -> 系统参数 -> 可观测性
```

页面展示 Prometheus 是否开启、指标端点、token 是否已配置、Grafana 访问地址和已加载看板名称。页面不会展示或保存 Prometheus token。

## 安全建议

- 不要把 `CREST_PROMETHEUS_TOKEN` 写入公开文档、截图或前端页面；
- 生产环境通过 Docker env、Kubernetes Secret 或企业密钥系统管理 token；
- Prometheus 服务不直接暴露到未受控网络；
- Grafana 使用独立强密码，并接入现场 SSO 或网关认证；
- 集群支持 NetworkPolicy 时，由现场平台按最小访问原则限制网关、Prometheus 和后端服务之间的流量；
- 指标数据可能包含接口路径、状态码和异常标签，按内部运维数据管理。
