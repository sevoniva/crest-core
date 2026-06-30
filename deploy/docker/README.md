# Crest Core Docker Compose 生产交付

本目录提供本地 Docker Compose 生产交付包，用于没有 Kubernetes 的单机或小型企业环境。它仍然遵守 Crest Core 的生产边界：OpenJDK 17 镜像、外部 OceanBase Oracle、外部 Redis Cluster、两个工作负载和最小化功能集。

## 交付边界

| 项 | 约束 |
| --- | --- |
| 工作负载 | 只启动 `crest-core-web` 和 `crest-core-service` 两个服务 |
| 后端副本 | 使用 `--scale crest-core-service=2`，不能配置 `container_name` |
| 数据库 | 外部 OceanBase Oracle，不在 compose 内启动数据库 |
| Redis | 外部共享 Redis Cluster，不在 compose 内启动单机 Redis |
| 入口 | 默认只绑定 `127.0.0.1:8080`，生产公网入口应由企业 TLS 反向代理暴露 |
| 持久化 | `crest-core-data` 和 `crest-core-service-logs` 使用 Docker named volume |
| 安全 | 非 root 用户、只读根文件系统、丢弃 capabilities、禁止提权、healthcheck |

Docker Compose 的本地多副本不等价于 Kubernetes 高可用：它没有跨节点调度、PDB、NetworkPolicy、Ingress Controller 和 RWX StorageClass 能力。真正多节点生产仍优先使用 `deploy/kubernetes`；本交付包适合单主机生产、预发、POC 转生产或企业平台暂未提供 K8s 的场景。

## 准备外部依赖

1. 由 DBA 初始化 OceanBase Oracle schema：

```bash
obclient --default-character-set=utf8mb4 \
  -h <obproxy-host> -P 2883 \
  -u '<user>@<tenant>#<cluster>' \
  -p'<password>' \
  < installer/init-sql/ob-oracle/crest-core-schema.sql
```

2. 准备 Redis Cluster ACL 用户和独立命名空间。共享 Redis 必须满足：

| 配置 | 要求 |
| --- | --- |
| `CREST_REDIS_CLUSTER_NODES` | 至少 3 个真实 `host:port` 节点 |
| `CREST_REDIS_USERNAME` | 独立 ACL 用户，不能是 `default` |
| `CREST_REDIS_DATABASE` | Cluster 模式固定为 `0` |
| Redis hash tag | 所有 key/channel/stream/group 使用同一个 `{org-env-crest-core}` |

3. 准备已推送到企业镜像仓库的 `crest-core-service` 和 `crest-core-web` 镜像，tag 不允许使用 `latest`。

## 配置

```bash
mkdir -p .local
cp deploy/docker/production.env.example .local/crest-docker-production.env
```

编辑 `.local/crest-docker-production.env`，替换所有 `<...>`、`example` 和弱密钥。然后执行严格检查：

```bash
node scripts/verify-docker-production.mjs deploy/docker --strict-config .local/crest-docker-production.env
```

如果本机安装了 Docker Compose，也可以展开最终配置：

```bash
docker compose \
  --env-file .local/crest-docker-production.env \
  -f deploy/docker/compose.yaml \
  config
```

## 启动

生产默认启动一个前端入口和两个组合后端副本：

```bash
docker compose \
  --env-file .local/crest-docker-production.env \
  -f deploy/docker/compose.yaml \
  up -d --scale crest-core-service=2
```

查看状态：

```bash
docker compose \
  --env-file .local/crest-docker-production.env \
  -f deploy/docker/compose.yaml \
  ps
```

后端容器会在启动时把 `CREST_WORKER_ID` 默认设置为容器 hostname，保证两个副本的 worker identity 不冲突。任务消费仍依赖 Redis Streams consumer group，定时调度仍依赖 Quartz JDBC Cluster 和 Redis 锁。

## 暴露入口

默认配置为：

```env
CREST_HTTP_BIND=127.0.0.1
CREST_HTTP_PORT=8080
```

这意味着宿主机外部不能直接访问 compose 入口。生产公网访问建议使用 Nginx、HAProxy、企业 API Gateway 或云负载均衡在宿主机外层终止 TLS，再反代到 `127.0.0.1:8080`。只有在外层网络已经有 TLS 和访问控制时，才把 `CREST_HTTP_BIND` 改为 `0.0.0.0`。

## 停止与升级

```bash
docker compose \
  --env-file .local/crest-docker-production.env \
  -f deploy/docker/compose.yaml \
  down
```

升级时替换 `CREST_BACKEND_IMAGE` 和 `CREST_FRONTEND_IMAGE` 为新版本 tag，重新执行严格检查后再滚动重建：

```bash
docker compose \
  --env-file .local/crest-docker-production.env \
  -f deploy/docker/compose.yaml \
  up -d --scale crest-core-service=2
```

不要在升级中删除 `crest-core-data` 或 `crest-core-service-logs` named volume。确需清理 volume，必须先完成业务备份和负责人确认。

## 生产验收

上线前至少完成：

```bash
bash scripts/test-docker-production-check.sh
bash scripts/docker-build-check.sh
bash scripts/security-scan.sh
bash scripts/container-image-scan.sh
bash scripts/redis-cluster-namespace-check.sh
```

真正生产可用还需要外部证据：OB 初始化和备份恢复、Redis ACL/命名空间隔离、TLS 反向代理、镜像漏洞扫描、业务冒烟、故障演练和回滚记录。
