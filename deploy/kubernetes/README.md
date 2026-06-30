# Crest Kubernetes 生产部署

本目录是 Crest Core 的 Kubernetes 主交付路径。当前定位是生产可用、OceanBase Oracle 系统库、OceanBase Oracle 业务数据源、外部共享 Redis Cluster、前后端多副本部署。

应用内部包名、表名和配置前缀仍保持 `crest`，避免无意义的兼容风险。

完整架构说明见 [架构设计](../../docs/architecture-design.md)，端到端部署流程见 [部署设计](../../docs/deployment-design.md)。

## 架构

```text
Ingress + TLS
  -> crest frontend ClusterIP Service
  -> crest frontend StatefulSet, 2 replicas
  -> crest-service backend StatefulSet, 2 replicas
  -> external shared Redis Cluster
  -> external OceanBase Oracle tenant/schema
```

角色说明：

| 资源 | 运行角色 | 说明 |
| --- | --- | --- |
| `crest` | frontend | Nginx 前端网关，多副本 |
| `crest-service` | `CREST_RUNTIME_ROLE=all` | 组合后端，多副本，同时承载 API、Redis Streams 任务消费和 Quartz 调度投递 |
| 外部 Redis Cluster | Redis | 多副本协调、缓存、锁、任务队列和 WebSocket 广播；通过 `CREST_REDIS_KEY_PREFIX` 隔离 |

发布与安全基线：

- frontend 和后端都使用 StatefulSet `OrderedReady` 滚动更新，固定 `crest-0/1` 与 `crest-service-0/1` 两个 Pod 身份。发布过程中不会临时创建第 3 个同类 Pod；该策略要求单 Pod 能承载发布窗口内的基础流量。
- Quartz 调度、Redis Streams 消费和 API 都在 `crest-service` 后端 Pod 内运行，通过 Quartz JDBC Cluster、Redis 锁和数据库状态抢占控制重复执行风险。
- 所有 Pod 使用非 root 用户 `10001`、禁用 ServiceAccount token 自动挂载、丢弃 Linux capabilities、禁止提权、启用 `seccompProfile: RuntimeDefault`，并将容器根文件系统设为只读。
- 后端仅将 `/opt/crest/data` 挂到 RWX PVC；日志、缓存和 `/tmp` 使用带 `sizeLimit` 的 `emptyDir`，避免应用写入镜像层或打满节点磁盘。
- 前端 Nginx 根文件系统只读，`/var/cache/nginx`、`/var/run/nginx` 和 `/tmp` 使用带 `sizeLimit` 的 `emptyDir`。
- 所有容器必须同时配置 CPU、内存和 `ephemeral-storage` 的 requests/limits。
- 多副本工作负载配置 `topologySpreadConstraints`，在多节点集群中优先分散到不同 `kubernetes.io/hostname`。
- 后端启用 Spring Boot graceful shutdown，Pod 使用 `preStop sleep 10` 和 `terminationGracePeriodSeconds=60`，滚动发布时给入口摘除与请求排空留出窗口。
- 后端 liveness/readiness 使用 Spring Boot Actuator health endpoint；readiness 同时检查应用状态、数据库和 Redis。
- 默认通过 Ingress TLS 暴露前端，前端和后端 Service 都保持 `ClusterIP`，生产清单不使用 NodePort。
- NetworkPolicy 默认允许外部访问前端，只允许前端访问后端 API；出站流量不在基础清单中限制，方便连接企业已有 OB、Redis、对象存储和监控链路。

## 文件顺序

本目录中的 YAML 是生产模板和 dry-run 校验输入，包含 `CHANGE_ME_*` 占位符。生产环境不要直接 apply 该目录；必须先按 [生成生产 Overlay](#生成生产-overlay) 生成 `.local/production-overlay`，并让严格配置检查通过。

模板文件按文件名前缀排序。仅在本地校验或临时测试时使用：

```bash
kubectl create --dry-run=client -f deploy/kubernetes -o name
```

关键文件：

| 文件 | 资源 |
| --- | --- |
| `00-crest-env-configmap.yaml` | 运行参数 |
| `01-crest-db-secret.yaml` | OB Oracle、加密、初始密码和 token secret |
| `02-crest-redis-secret.yaml` | Redis ACL 用户名和密码 |
| `02a-crest-serviceaccount.yaml` | 应用 ServiceAccount |
| `03-crest-data-pvc.yaml` | 应用共享数据卷，必须 RWX |
| `07-crest-service-headless-service.yaml` | 后端 StatefulSet headless Service |
| `08-crest-service-statefulset.yaml` | 组合后端多副本 |
| `09`-`12` | 后端/前端 ClusterIP Service、前端 StatefulSet headless Service 与前端 Nginx 配置 |
| `13-crest-ingress.yaml` | 前端 Ingress TLS |
| `16`-`17` | PDB |
| `19`-`20` | NetworkPolicy |

## 必须修改

`00-crest-env-configmap.yaml`

```yaml
CREST_ORIGIN_LIST: "https://change-me-crest.example.invalid"
CREST_DB_HOST: "CHANGE_ME_OB_HOST"
CREST_DB_PORT: "2883"
CREST_DB_URL: "jdbc:oceanbase://CHANGE_ME_OB_HOST:2883"
CREST_ALLOWED_DATASOURCE_TYPES: "obOracle,Excel,ExcelRemote,API"
CREST_INTERNAL_LITE_ENABLED: "true"
CREST_SHUTDOWN_TIMEOUT: "45s"
CREST_REDIS_CLUSTER_NODES: "CHANGE_ME_REDIS_NODE_1:6379,CHANGE_ME_REDIS_NODE_2:6379,CHANGE_ME_REDIS_NODE_3:6379"
CREST_REDIS_KEY_PREFIX: "{<org>-<env>-crest-core}:prod"
CREST_HEALTH_REDIS_ENABLED: "true"
```

`CREST_ALLOWED_DATASOURCE_TYPES` 控制可创建的业务数据源类型。Crest Core 生产默认数据库类数据源只开放 OceanBase Oracle，同时保留 Excel 和 API 数据源；如需扩展其他数据库类型，先补齐驱动、存储和验收用例。

生产清单默认关闭 API 文档、SQLBot/AI、模板市场、字体自定义管理和背景资源库相关开关。除非已经完成安全评审和验收，不要在生产直接打开这些开关。

`13-crest-ingress.yaml`

```yaml
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - change-me-crest.example.invalid
      secretName: crest-tls
  rules:
    - host: change-me-crest.example.invalid
```

生产 overlay 必须替换为真实 HTTPS 域名，并提前创建或由 cert-manager 管理 `crest-tls`。如果企业入口不是 Nginx Ingress，请按平台调整 `ingressClassName` 和 annotation，但仍应保持 TLS 入口和内部 `ClusterIP` Service。

`01-crest-db-secret.yaml`

```yaml
CREST_DB_USERNAME: "CHANGE_ME_OB_USER@ob_oracle#obcluster"
CREST_DB_PASSWORD: "CHANGE_ME_OB_PASSWORD"
CREST_AES_KEY: "CHANGE_ME_AES_KEY_32_CHARS_00000"
CREST_AES_IV: "CHANGE_ME_IV_16_"
CREST_INITIAL_PASSWORD: "CHANGE_ME_INITIAL_ADMIN_PASSWORD"
CREST_TOKEN_SECRET: "CHANGE_ME_LONG_RANDOM_TOKEN_SECRET"
```

如果通过 observer 直连，把端口改为 `2881`，用户名使用 `用户@租户`。

`02-crest-redis-secret.yaml`

```yaml
CREST_REDIS_USERNAME: "CHANGE_ME_REDIS_ACL_USER"
CREST_REDIS_PASSWORD: "CHANGE_ME_REDIS_PASSWORD"
```

Redis 与其他系统共用时，必须为 Crest 分配独立 ACL 用户和独立前缀。`<org>` 必须替换为企业、团队或环境唯一标识，不能直接使用模板值。示例使用 Redis Cluster hash tag：

```yaml
CREST_REDIS_KEY_PREFIX: "{<org>-<env>-crest-core}:prod"
CREST_REDIS_CACHE_KEY_PREFIX: "{<org>-<env>-crest-core}:prod:cache:"
CREST_LOCK_KEY_PREFIX: "{<org>-<env>-crest-core}:prod:lock"
CREST_EXPORT_TASK_STREAM: "{<org>-<env>-crest-core}:prod:stream:export-task"
CREST_EXPORT_TASK_CONSUMER_GROUP: "{<org>-<env>-crest-core}:prod:group:export-workers"
CREST_SYNC_TASK_STREAM: "{<org>-<env>-crest-core}:prod:stream:dataset-sync-task"
CREST_SYNC_TASK_CONSUMER_GROUP: "{<org>-<env>-crest-core}:prod:group:dataset-sync-workers"
CREST_DATASOURCE_SYNC_TASK_STREAM: "{<org>-<env>-crest-core}:prod:stream:datasource-sync-task"
CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP: "{<org>-<env>-crest-core}:prod:group:datasource-sync-workers"
CREST_SCHEDULED_TASK_STREAM: "{<org>-<env>-crest-core}:prod:stream:scheduled-task"
CREST_SCHEDULED_TASK_CONSUMER_GROUP: "{<org>-<env>-crest-core}:prod:group:scheduled-workers"
CREST_WEBSOCKET_BROADCAST_CHANNEL: "{<org>-<env>-crest-core}:prod:pubsub:websocket"
```

同一个 Redis Cluster 上部署多套 Crest 时，至少要按组织、环境和实例改成不同前缀，例如 `{<org>-prod-crest-core-a}:prod`、`{<org>-prod-crest-core-b}:prod`。Redis Cluster 模式必须保持 `CREST_REDIS_DATABASE="0"`。
生产运行时会拒绝少于 3 个节点、模板节点、空 hash tag、过短、过泛或仍像模板/示例的 hash tag，且会拒绝 cache、lock、pub/sub、stream 和 consumer group 配置使用不同 hash tag。
生产运行时和严格生产检查都会要求配置 Redis ACL 用户名和密码；共享 Redis 不允许使用默认用户或弱密码。

`CREST_AES_KEY` 必须为 32 位，`CREST_AES_IV` 必须为 16 位。生产环境不要使用示例值。

## 生成生产 Overlay

推荐用脚本从环境变量生成本地生产 overlay，输出目录默认是 `.local/production-overlay`，不会进入 git。脚本会写入本地明文 Secret 清单并立即执行严格生产配置检查；托管集群中建议再接 SealedSecret、ExternalSecret 或平台密钥系统。

```bash
mkdir -p .local
cp deploy/kubernetes/production.env.example .local/crest-production.env
# 修改 .local/crest-production.env 中的真实生产值
set -a
source .local/crest-production.env
set +a

bash scripts/render-production-overlay.sh
kubectl apply -n <namespace> -f .local/production-overlay
```

可选变量：

```bash
export CREST_ORIGIN_LIST='https://crest.example.com'
export CREST_INGRESS_CLASS_NAME='nginx'
export CREST_DATA_STORAGE_CLASS='<rwx-storage-class>'
export CREST_DATA_STORAGE_SIZE='50Gi'
export CREST_REDIS_SSL_ENABLED='true'
export CREST_PROMETHEUS_ENABLED='true'
export CREST_PROMETHEUS_TOKEN='<at-least-32-character-prometheus-token>'
```

`CREST_PROMETHEUS_ENABLED` 默认是 `false`。如果企业监控需要抓取 `/api/v1/actuator/prometheus`，必须同时配置 `CREST_PROMETHEUS_TOKEN`；生产 overlay 和严格配置检查会拒绝“开启指标但没有 Bearer Token”的配置。

## 存储要求

`crest-service` 挂载 `/opt/crest/data`。多副本部署要求 `crest-data` 使用支持 `ReadWriteMany` 的存储类，例如 NFS、CephFS 或云厂商共享文件存储。生产 overlay 必须显式设置 `CREST_DATA_STORAGE_CLASS`，避免依赖集群默认 StorageClass 导致跨环境不可复现。

如果集群没有 RWX 存储，必须先把上传、导出、字体、静态资源等数据路径改成共享对象存储或平台共享存储，再部署多副本。

## 后端多副本调度

生产清单显式设置：

```yaml
CREST_QUARTZ_INSTANCE_ID: "AUTO"
CREST_QUARTZ_CLUSTERED: "true"
CREST_QUARTZ_CLUSTER_CHECKIN_INTERVAL: "10000"
CREST_QUARTZ_MISFIRE_THRESHOLD: "60000"
```

Quartz 使用 OB Oracle 中的 `core_schedule_*` 表做集群协调。调度投递运行在 `crest-service` 的 2 个后端 Pod 内，业务任务执行前仍会经过 Redis 锁和数据库状态抢占，防止发布、重启或 Pod 故障恢复时重复执行。

## 数据库初始化

生产环境默认关闭 Flyway：

```yaml
CREST_FLYWAY_ENABLED: "false"
```

由 DBA 创建 OceanBase Oracle 租户/schema/账号后，执行全新初始化 SQL：

```bash
obclient --default-character-set=utf8mb4 \
  -h <obproxy-host> -P 2883 \
  -u '<user>@<tenant>#<cluster>' \
  -p'<password>' \
  < installer/init-sql/ob-oracle/crest-core-schema.sql
```

## 校验

```bash
node scripts/verify-kubernetes-production.mjs deploy/kubernetes
kubectl create --dry-run=client -f deploy/kubernetes -o name
bash scripts/kind-smoke-test.sh
```

`kind-smoke-test.sh` 会先确认 Docker daemon 可用。创建新 kind 集群或执行 `CREST_KIND_APPLY=true` 时默认要求至少 8GiB 可用空间；如果 `crest-core` 集群已存在且只做 server-side dry-run，则不再用创建集群的磁盘门槛阻断校验。它验证清单可被真实 Kubernetes API Server 接受，但不会声称 Pod 已经可连接 OB Oracle、Redis Cluster 或镜像仓库。需要验证真实 overlay 时，设置 `CREST_KIND_MANIFEST_DIR=<production-overlay-path>`。需要真实 apply 时，设置 `CREST_KIND_APPLY=true`；apply 后会默认执行 `production-runtime-check.mjs`，失败时不会把 kind 部署误报为通过。

```bash
CREST_KIND_MANIFEST_DIR=.local/production-overlay \
CREST_KIND_APPLY=true \
bash scripts/kind-smoke-test.sh
```

本地 kind 需要验证当前工作区刚构建的镜像时，先生成 `crest-service:local-check` 和
`crest-web:local-check`，再让脚本重打 `sha-<commit>` 形式的不可变标签并装载进 kind。
脚本会把 `crest` 和 `crest-service` 两个 StatefulSet 切到本地镜像后再执行 runtime check。

```bash
CREST_DOCKER_BUILD_ARTIFACTS=true bash scripts/docker-build-check.sh

CREST_KIND_MANIFEST_DIR=.local/production-overlay \
CREST_KIND_APPLY=true \
CREST_KIND_LOAD_LOCAL_IMAGES=true \
CREST_KIND_LOCAL_IMAGE_TAG="sha-$(git rev-parse --short=12 HEAD)" \
bash scripts/kind-smoke-test.sh
```

kind 默认的 local-path StorageClass 不支持 RWX，默认清单也不会创建真实 TLS Secret。如果只是要在本地单节点 kind 中验证 apply、镜像装载和对象形态，可显式启用本地依赖：

```bash
CREST_KIND_APPLY=true \
CREST_KIND_LOAD_LOCAL_IMAGES=true \
CREST_KIND_CREATE_LOCAL_RWX_STORAGE=true \
CREST_KIND_CREATE_LOCAL_TLS_SECRET=true \
CREST_KIND_LOCAL_IMAGE_TAG="sha-$(git rev-parse --short=12 HEAD)" \
bash scripts/kind-smoke-test.sh
```

这会创建一个 kind 专用 hostPath RWX PV 和 placeholder `crest-tls` Secret，只用于本地 smoke；生产或预发必须使用真实 RWX 存储类和证书管理。

默认目录里的 `CHANGE_ME_*` 是模板占位符。正式环境替换域名、OB、Redis、Secret 和镜像后，必须对最终 overlay 执行严格配置检查：

```bash
bash scripts/production-config-check.sh <production-overlay-path>
```

严格模式会拒绝占位符、localhost origin、非 HTTPS origin、Ingress host 与 origin 不一致、弱/过短密钥、`latest` 镜像 tag，以及不符合 Redis Cluster 生产约束的配置。

部署后检查：

```bash
kubectl get pods
kubectl rollout status statefulset/crest
kubectl rollout status statefulset/crest-service
```

后端 readiness 应同时覆盖数据库和 Redis；日志不应持续出现数据库连接失败、Redis 连接失败、任务重复执行或文件写入失败。

真实生产或预发环境 apply 后，执行 live runtime check：

```bash
node scripts/production-runtime-check.mjs --namespace <namespace> --context <kube-context>
```

该检查会等待两个 StatefulSet rollout，确认 Pod Ready、固定两个 Pod 身份、拓扑分散、探针、流量 Service 保持 `ClusterIP`、headless Service 存在、Service endpoint 存在、Ingress TLS 与 `CREST_ORIGIN_LIST` 一致、`crest-tls` Secret 可用、`crest-data` PVC 已绑定为 RWX、PDB/NetworkPolicy 规则、容器安全上下文、`emptyDir.sizeLimit` 和资源 requests/limits 仍符合生产基线。若需要强制 Ingress 已分配负载均衡地址，增加：

```bash
CREST_REQUIRE_INGRESS_ADDRESS=true node scripts/production-runtime-check.mjs --namespace <namespace>
```

上线评审需要留存集群对象、事件、脱敏 Secret 摘要和 runtime check 输出时：

```bash
CREST_READINESS_COLLECT_EVIDENCE=true \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<kube-context> \
bash scripts/enterprise-readiness-check.sh
```

## 网络隔离

基础清单包含两类 NetworkPolicy：

- `crest-web`：允许外部入口访问前端 Nginx `8080`。
- `crest-service`：仅允许前端 Pod 访问后端 `8100`；同时允许 `monitoring` 命名空间中带 `app.kubernetes.io/name=prometheus` 标签的 Prometheus Pod 抓取后端指标。

如果企业监控命名空间或 Prometheus 标签不同，请在生产 overlay 中调整 `20-crest-service-network-policy.yaml`，并重新执行：

```bash
bash scripts/production-config-check.sh <production-overlay-path>
```
