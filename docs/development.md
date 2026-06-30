# Crest Core 开发说明

本文面向参与 Crest Core 开发、部署和发布的人，说明首版工程边界、构建方式、数据库资产和提交前检查。

Crest Core 按全新私有化生产环境维护，默认目标是 OpenJDK 17、OceanBase Oracle、Kubernetes 多副本和外部 Redis Cluster。项目按 GPLv3 发布，开发时必须保留版权和许可证声明，不引入不能公开分发的依赖、驱动或数据。

## 工程边界

| 维度 | 首版约定 |
| --- | --- |
| JDK | OpenJDK 17 |
| 系统库 | OceanBase Oracle |
| 业务数据源 | 默认 `obOracle,Excel,ExcelRemote,API` |
| 部署方式 | Kubernetes 多副本 |
| 初始化 | `installer/init-sql/ob-oracle/crest-core-schema.sql` |
| 升级 | 首版不提供历史版本原地升级 |
| Redis | 外部共享 Redis Cluster，必须配置独立 key 前缀 |
| Flyway | 生产关闭，开发可按需显式启用 |
| Demo 数据 | 生产关闭 |

内部包名、artifactId、表名前缀、配置前缀和 Kubernetes 资源名继续保持 `crest`，避免无意义的重命名风险。

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `core/core-backend` | Spring Boot 后端服务，包含接口、业务实现、OB Oracle 迁移资源和最终 JAR 打包入口 |
| `core/core-frontend` | Vue 3、Vite、TypeScript、Element Plus、Pinia 和 vxe-table 前端工程 |
| `sdk/api` | 对内 API、DTO、VO 和接口契约 |
| `sdk/common` | 认证、通用模型、工具类、异常处理和 Spring 配置 |
| `sdk/extensions/extensions-datasource` | 数据源扩展、JDBC 数据源定义和方言能力 |
| `drivers` | 随仓库维护的 JDBC 驱动，当前跟踪 OceanBase JDBC |
| `installer/init-sql/ob-oracle` | 首版 OceanBase Oracle 空库初始化 SQL |
| `deploy/kubernetes` | Kubernetes 生产交付清单，默认使用外部共享 Redis Cluster |
| `.github/workflows/docker-publish.yml` | GHCR 镜像构建和发布流程 |

## 工具链

| 工具 | 建议版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9 或兼容版本 |
| Node.js | 22 |
| pnpm | 11 |
| Docker | 20.10+ |
| Docker Buildx | 多架构镜像发布时需要 |
| Go | 1.25.x，用于安装 OSV Scanner、Gitleaks 和 actionlint |
| actionlint | 1.7.7，用于 GitHub Actions workflow 规范检查 |

Maven 使用仓库内 `.mvn/settings.xml`。前端依赖使用 `core/core-frontend/pnpm-lock.yaml`，`flushbonading` 子包使用自己的 `package-lock.json`。

不要引入私有制品仓库、个人代理源、无法公开访问的镜像仓库或授权不清的二进制文件。确需新增第三方驱动或前端包时，需要在提交说明中写明用途和许可证。

## 常用命令

编译数据源扩展相关模块：

```bash
mvn -s .mvn/settings.xml -pl sdk/extensions/extensions-datasource -am \
  -DskipTests -Dmaven.test.skip=true -Dmaven.antrun.skip=true \
  test-compile
```

构建前端：

```bash
cd core/core-frontend
pnpm install --frozen-lockfile
pnpm run build:base
pnpm run build:lite:check
```

打包后端：

```bash
mvn -s .mvn/settings.xml -pl :core-backend -am clean package -Pstandalone \
  -DskipTests -Dmaven.test.skip=true
```

`standalone` profile 会把 `core/core-frontend/dist` 同步到后端 `static` 资源目录。生产打包不要设置 `-Dcrest.copy.frontend.skip=true`，否则后端 JAR 可能带着旧的前端 hash 文件。

构建本地镜像：

```bash
bash scripts/docker-build-check.sh
```

`docker-build-check.sh` 会先执行 `docker-environment-check.sh`，确认 Docker daemon 可用，并默认要求当前构建路径所在磁盘至少有 12GiB 可用空间。磁盘不足时脚本会打印 `docker system df`，并自动生成只读清理计划 `reports/readiness/docker-cleanup-plan.txt`；不会自动清理镜像、volume 或 build cache。临时覆盖阈值可设置 `CREST_DOCKER_MIN_FREE_GB=<GiB>`，确需跳过磁盘检查时设置 `CREST_DOCKER_PRECHECK_SKIP_DISK=true`。

需要排查 Docker 磁盘红灯时，可先生成只读清理计划：

```bash
bash scripts/docker-cleanup-plan.sh
```

报告默认写入 `reports/readiness/docker-cleanup-plan.txt`，包含当前 `docker system df`、`docker builder du` 明细、建议的 build cache/image/container 清理顺序、清理前审批清单，以及需要数据负责人确认的 volume 清理提示。该脚本不会执行任何 `docker prune`。

网络无法直连 Docker Hub 时，可通过 build arg 指向企业镜像代理，默认值仍保持官方多架构镜像：

```bash
CREST_DOCKER_JDK_IMAGE=<mirror>/eclipse-temurin:17-jdk-jammy@sha256:<digest> \
CREST_DOCKER_RUNTIME_IMAGE=<mirror>/ubuntu:24.04@sha256:<digest> \
CREST_DOCKER_NGINX_IMAGE=<mirror>/nginx:1.29-alpine@sha256:<digest> \
bash scripts/docker-build-check.sh
```

`docker-build-check.sh` 会先执行 `container-base-image-policy-check.sh`，拒绝 `latest` 基础镜像，并把 JDK、runtime 和 Nginx 基础镜像是否 digest pin 写入 `reports/readiness/container-base-image-policy.txt`。日常本地构建允许企业镜像代理使用版本 tag；正式发布或 Go/No-Go 必须设置 `CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS=true`，并把三类基础镜像配置成 `name:tag@sha256:<digest>` 或 `name@sha256:<digest>`。

构建脚本会在前端/后端产物构建前先执行基础镜像可用性预检：本地没有镜像时会先 `docker pull`，拉取失败会指出具体的 `CREST_DOCKER_*_IMAGE`。网络受限环境建议先把三类基础镜像同步到内网 registry，再使用上面的环境变量传入内网 digest-pinned 引用；这样可以在企业 CI、离线发布机和本地 kind 验证中使用同一套可追溯基础镜像。

如果本地 Docker Desktop 的 BuildKit 在多架构 index 导出阶段仍访问 Docker Hub 超时，可临时用 `DOCKER_BUILDKIT=0 bash scripts/docker-build-check.sh` 或 `CREST_READINESS_DOCKER_BUILDKIT=0 bash scripts/enterprise-readiness-check.sh`。脚本会改用当前 Docker daemon 架构的 Nginx manifest digest，仍然保留基础镜像 digest 校验。

如果只是验证后端 `jlink` 自定义运行时阶段，可设置 `CREST_DOCKER_BACKEND_MODE=jre-build`。生产发布必须使用默认 `full` 模式完成完整后端 runtime 镜像构建。

## 本地后端启动

本地源码启动需要准备已初始化的 OceanBase Oracle schema，并设置：

```bash
export CREST_DB_TYPE=ob-oracle
export CREST_DB_DRIVER_CLASS_NAME=com.oceanbase.jdbc.Driver
export CREST_DB_HOST=127.0.0.1
export CREST_DB_PORT=2883
export CREST_DB_URL=jdbc:oceanbase://127.0.0.1:2883
export CREST_DB_USERNAME='<user>@<tenant>#<cluster>'
export CREST_DB_PASSWORD='<password>'
export CREST_FLYWAY_ENABLED=false
export CREST_LOAD_DEMO=false
export CREST_ALLOWED_DATASOURCE_TYPES='obOracle,Excel,ExcelRemote,API'
export CREST_AES_KEY='<32-character-aes-key>'
export CREST_AES_IV='<16-character-aes-iv>'
export CREST_INITIAL_PASSWORD='<admin-initial-password>'
./run.sh start
```

生产模式还必须配置 Redis Cluster、独立 `CREST_REDIS_KEY_PREFIX` 和 `CREST_TOKEN_SECRET`。

## 运行时配置

关键环境变量：

| 变量 | 说明 |
| --- | --- |
| `CREST_DB_TYPE` | 固定 `ob-oracle` |
| `CREST_DB_DRIVER_CLASS_NAME` | 固定 `com.oceanbase.jdbc.Driver` |
| `CREST_DB_HOST` / `CREST_DB_PORT` / `CREST_DB_URL` | OceanBase Oracle 连接入口 |
| `CREST_DB_USERNAME` / `CREST_DB_PASSWORD` | Crest 系统库账号 |
| `CREST_RUNTIME_ROLE` | `all`、`api`、`scheduler`、`worker` |
| `CREST_SHUTDOWN_TIMEOUT` | Spring Boot 优雅停机阶段超时，生产默认 `45s` |
| `CREST_PRODUCTION_MODE` | 生产为 `true` |
| `CREST_FLYWAY_ENABLED` | 生产为 `false` |
| `CREST_LOAD_DEMO` | 生产为 `false` |
| `CREST_INTERNAL_LITE_ENABLED` | 生产为 `true` |
| `CREST_ALLOWED_DATASOURCE_TYPES` | 默认 `obOracle,Excel,ExcelRemote,API` |
| `CREST_API_DOCS_ENABLED` / `CREST_KNIFE4J_ENABLED` | 生产为 `false` |
| `CREST_FEATURE_AI_ENABLED` / `CREST_FEATURE_SQLBOT_ENABLED` / `CREST_FEATURE_TEMPLATE_MARKET_ENABLED` | 生产为 `false` |
| `CREST_FEATURE_FONT_MANAGEMENT_ENABLED` / `CREST_FEATURE_VISUALIZATION_BACKGROUND_ENABLED` | 生产为 `false` |
| `CREST_TOKEN_SECRET` | 生产必填 |
| `CREST_REDIS_CLUSTER_NODES` | 外部 Redis Cluster 节点列表 |
| `CREST_REDIS_DATABASE` | Redis Cluster 固定 `0` |
| `CREST_REDIS_KEY_PREFIX` | 共享 Redis 中 Crest 使用的全局 key/channel/stream/group 前缀 |
| `CREST_REDIS_USERNAME` / `CREST_REDIS_PASSWORD` | Redis ACL 用户名和密码；生产运行时与严格生产 overlay 必填 |
| `CREST_TASK_QUEUE_ENABLED` | 生产为 `true` |
| `CREST_WEBSOCKET_BROADCAST_ENABLED` | 生产为 `true` |
| `CREST_HEALTH_REDIS_ENABLED` | 生产为 `true`，readiness 检查 Redis Cluster |
| `CREST_DATASOURCE_POOL_PRELOAD_ENABLED` | 多副本生产默认 `false` |
| `CREST_AES_KEY` / `CREST_AES_IV` | 配置加密参数 |
| `CREST_INITIAL_PASSWORD` | 首次管理员密码 |
| `CREST_ORIGIN_LIST` | 允许访问来源；生产必须使用真实 HTTPS 域名，并与 Ingress host 一致 |

生产 Kubernetes 部署使用 `CREST_RUNTIME_ROLE=all`，由 `crest-service` 的 2 个后端 Pod 同时承载 HTTP 接口、Redis Streams 任务消费和 Quartz 调度投递。`api`、`worker`、`scheduler` 只保留给本地诊断或特殊容量隔离实验，不作为当前生产基线。

## 数据库资产

生产只交付一个首版空库初始化文件：

```text
installer/init-sql/ob-oracle/crest-core-schema.sql
```

生成和校验：

```bash
node scripts/generate-ob-oracle-init-schema.mjs
node scripts/generate-ob-oracle-init-schema.mjs --check
```

规则：

- 初始化 SQL 只面向全新 OceanBase Oracle 系统库。
- 生产库由 DBA 预创建租户、schema、账号和权限。
- 生产启动时应用不自动迁移系统库。
- 后续如需要支持原地升级，必须先设计补丁 SQL、回滚策略和验收流程，再扩展发布规范。
- 初始化数据不能写入本地 IP、个人账号、外部库密码或临时资源。

## Kubernetes 验证

```bash
node scripts/verify-kubernetes-production.mjs deploy/kubernetes
kubectl create --dry-run=client -f deploy/kubernetes -o name
```

清单必须保持：

- `CREST_PRODUCTION_MODE=true`
- `CREST_DB_TYPE=ob-oracle`
- `CREST_FLYWAY_ENABLED=false`
- `CREST_LOAD_DEMO=false`
- `CREST_INTERNAL_LITE_ENABLED=true`
- `CREST_ALLOWED_DATASOURCE_TYPES=obOracle,Excel,ExcelRemote,API`
- `CREST_API_DOCS_ENABLED=false` 且 `CREST_KNIFE4J_ENABLED=false`
- SQLBot/AI、模板市场、字体自定义管理和背景资源库 feature flag 保持 `false`
- `CREST_REDIS_CLUSTER_NODES` 至少包含 3 个真实 `host:port` 节点
- `CREST_REDIS_DATABASE=0`
- `CREST_REDIS_KEY_PREFIX` 必须显式配置，使用组织/环境唯一的 Redis Cluster hash tag，例如 `{<org>-<env>-crest-core}:prod`，不能照抄模板或示例值
- Redis cache、lock、pub/sub、stream 和 consumer group 配置必须使用同一个 hash tag
- 共享 Redis 生产运行时和 overlay 必须配置 Redis ACL 用户名和密码，避免使用默认用户和弱密码
- 容器根文件系统必须只读；业务数据走 RWX PVC，缓存、日志和 `/tmp` 走带 `sizeLimit` 的 `emptyDir`
- 容器必须配置 CPU、内存和 `ephemeral-storage` 的 requests/limits
- 后端必须启用 Spring Boot graceful shutdown，Kubernetes Pod 必须配置 `preStop sleep 10`，并与 `terminationGracePeriodSeconds=60` 保持配套
- `crest` 前端和 `crest-service` 组合后端各 2 副本；不单独启动 `crest-worker` 或 `crest-scheduler`
- 共享数据卷支持 RWX
- 前端和后端 Service 保持 `ClusterIP`，生产入口通过 Ingress TLS 暴露
- Pod 使用非 root 用户、`seccompProfile=RuntimeDefault`、禁用 ServiceAccount token 自动挂载、禁止提权并丢弃 capabilities
- NetworkPolicy 限制入站访问：外部只进前端，后端只接受前端和监控抓取
- 前端和组合后端都使用 `maxSurge=0`、`maxUnavailable=1`，发布窗口内不会临时出现第 3 个 Pod，但对应工作负载可能短暂只剩 1 个可用 Pod。scheduler 依赖 Quartz JDBC Cluster、Redis 锁和数据库状态防重，worker 依赖 Redis Streams consumer group 和数据库状态防重

如本地可启动 kind，可进一步跑 API Server 级校验：

```bash
bash scripts/kind-smoke-test.sh
```

`kind-smoke-test.sh` 会先确认 Docker daemon 可用。创建新 kind 集群或执行 `CREST_KIND_APPLY=true` 时默认要求当前 Docker 数据所在磁盘至少有 8GiB 可用空间，避免创建或真实部署过程中才因磁盘不足失败。若目标 kind 集群已经存在且只做 server-side dry-run，脚本不会再用创建集群的磁盘门槛阻断校验。临时覆盖阈值可设置 `CREST_KIND_MIN_FREE_GB=<GiB>`；只在明确知道环境足够时才使用 `CREST_KIND_SKIP_ENV_CHECK=true`。

默认只执行 server-side dry-run，不会真正创建 Pod。已有真实镜像、OB Oracle 和 Redis Cluster 测试环境时，可显式启用 apply；apply 后脚本会继续执行 runtime check，避免把“对象已创建”误判为“部署可用”：

```bash
CREST_KIND_APPLY=true bash scripts/kind-smoke-test.sh
```

若要验证真实生产 overlay，而不是仓库默认模板，先渲染 overlay，再指定目录：

```bash
CREST_KIND_MANIFEST_DIR=.local/production-overlay \
CREST_KIND_APPLY=true \
bash scripts/kind-smoke-test.sh
```

## 企业级门禁

提交前至少执行：

```bash
bash scripts/enterprise-readiness-check.sh
bash scripts/quality-check.sh
bash scripts/security-scan.sh
bash scripts/docker-build-check.sh
bash scripts/container-image-scan.sh
```

`enterprise-readiness-check.sh` 是企业准入总入口，默认串起 quality、SAST/SCA、镜像构建/扫描和 kind dry-run；真实 overlay、live runtime、上线证据采集、外部生产证据和 git 历史审计通过环境变量显式打开。上线评审时使用 `CREST_READINESS_REQUIRE_GO_NO_GO=true`，该模式会拒绝跳过静态门禁、clean source、生产 overlay、evidence bundle、live runtime check 或外部生产证据检查。

`quality-check.sh` 会强制使用 OpenJDK 17，依次校验 OB Oracle 初始化 SQL、Kubernetes 生产清单、kubectl dry-run、生产 overlay smoke、项目代码规范、前端 TypeScript、ESLint、生产构建、后端测试、前后端静态资源一致性和 release guard。

`test-production-overlay-render.sh` 使用合规假值生成 `.local/production-overlay-smoke`，并调用严格生产配置检查，覆盖真实 overlay 生成链路中的 YAML quoting、Secret 注入、Ingress TLS、Redis Cluster hash tag、镜像 tag 和 RWX PVC 配置。

`code-style-check.sh` 是本项目当前的后端/前端高信号规范门禁，会阻断补丁空白错误、管理员默认密码初始化回归、Java 直接写 stdout/stderr、Java 裸 `printStackTrace()`、`@SuppressWarnings("all")`、散落的 `JWT.decode`、前端 `console.log/info/debug`/`debugger` 和生产源码中的 `TODO/FIXME`。

`workflow-lint.sh` 会使用 actionlint 检查 `.github/workflows/` 下的 GitHub Actions workflow，并调用 `github-actions-policy-check.sh` 确认第三方 `uses:` 全部固定到 40 位 commit SHA；本地 action `./...` 允许使用，`docker://` action 必须使用 `@sha256`。workflow 中可在 SHA 后用注释保留来源 tag，例如 `# v6.0.2`，但不能直接使用 `@v6.0.2`、`@v4` 或分支名。该脚本还会调用 `ci-toolchain-policy-check.sh`，要求 Semgrep、OSV Scanner、Gitleaks、actionlint 和 Trivy 通过仓库内 `scripts/install-*.sh` 集中安装，拒绝 workflow 内联 `go install` 或 `pipx install semgrep`。`quality-check.sh` 会调用这些检查；CI 固定安装 actionlint `v1.7.7`。本地可执行 `CREST_ACTIONLINT_INSTALL_DIR=/tmp/crest-tools bash scripts/install-actionlint.sh`，再通过 `CREST_ACTIONLINT_BIN=/tmp/crest-tools/actionlint` 指定。

`security-scan.sh` 会执行：

- Semgrep `p/default` 与 `p/owasp-top-ten` SAST。
- Gitleaks secret scan，阻断私钥、token、真实口令等敏感信息进入仓库。
- Maven dependency tree 依赖清单。
- Maven CycloneDX 聚合 SBOM。
- pnpm production audit。
- OSV Scanner 前端 lockfile SCA。
- OSV Scanner Java/Maven SBOM SCA。
- `security-report-check.mjs` 报告级复核，要求 Semgrep、Gitleaks、pnpm audit、OSV 前端和 OSV Maven SBOM 报告均为 0 finding/0 vulnerability，并确认 Maven dependency tree 与 CycloneDX SBOM 已落盘。

`osv-scanner.toml` 只允许有明确原因和到期时间的例外。当前 TinyMCE 相关例外用于处理 OSV Scanner v1 对 `tinymce@8.6.0` 的假阳性：advisory 元数据标注 npm 8.x 在 8.5.1 修复，且 8.6.0 包内已经包含 DOMPurify 3.4.5。例外到期前必须复查，能删除就删除。

`docker-build-check.sh` 会构建前端产物、后端 JAR、校验静态资源同步，并构建前后端 Docker 镜像。构建前会运行基础镜像策略检查和 Docker 环境预检；CI 中 `.github/workflows/container-gates.yml` 会执行同一脚本；企业内网可通过 `CREST_DOCKER_JDK_IMAGE`、`CREST_DOCKER_RUNTIME_IMAGE` 和 `CREST_DOCKER_NGINX_IMAGE` 指向内部镜像仓库。发布 workflow 和 Go/No-Go 模式要求这些基础镜像使用 `@sha256` 固定 digest，避免同一个 tag 在不同时间解析到不同镜像。

`container-image-scan.sh` 会使用 Trivy 扫描 `crest-web` 和 `crest-service` 镜像，默认发现 `HIGH` 或 `CRITICAL` 漏洞即失败，报告输出到 `reports/container`。扫描结束后还会执行 `container-report-check.mjs` 解析 Trivy JSON，确认报告存在、目标是 container image，且 HIGH/CRITICAL 为 0。本地可先执行 `CREST_TRIVY_INSTALL_DIR=/tmp/crest-tools bash scripts/install-trivy.sh`，再用 `CREST_TRIVY_BIN=/tmp/crest-tools/trivy bash scripts/container-image-scan.sh`。`install-trivy.sh` 支持 `CREST_DOWNLOAD_PROXY`、`CREST_DOWNLOAD_MAX_TIME_SECONDS`、`CREST_DOWNLOAD_CONNECT_TIMEOUT_SECONDS`、`CREST_DOWNLOAD_SPEED_TIME_SECONDS` 和 `CREST_DOWNLOAD_SPEED_LIMIT_BYTES`，用于慢网或企业代理环境。如果本地没有 Trivy 二进制，脚本会回退到 `aquasec/trivy:<version>` 容器扫描本地 Docker 镜像；企业内网可通过 `CREST_TRIVY_DOWNLOAD_BASE_URL` 或 `CREST_TRIVY_DOCKER_IMAGE` 指向内部镜像源或制品源。Trivy 漏洞库默认按 `ghcr.io`、`public.ecr.aws`、`mirror.gcr.io` 的顺序配置；慢网或内网环境可用 `CREST_TRIVY_DB_REPOSITORIES` 和 `CREST_TRIVY_JAVA_DB_REPOSITORIES` 传入逗号分隔的内部镜像源列表。

`history-secret-audit.sh` 和 `create-clean-source-release.sh` 需要 Gitleaks。本地可执行 `CREST_GITLEAKS_INSTALL_DIR=/tmp/crest-tools bash scripts/install-gitleaks.sh`，再通过 `CREST_GITLEAKS_BIN=/tmp/crest-tools/gitleaks` 指向固定版本。

`security-scan.sh` 的 SCA 还需要 OSV Scanner。本地可执行 `CREST_OSV_SCANNER_INSTALL_DIR=/tmp/crest-tools bash scripts/install-osv-scanner.sh`，再通过 `CREST_OSV_SCANNER=/tmp/crest-tools/osv-scanner` 指向固定版本。

`render-production-overlay.sh` 用真实域名、OB、Redis、Secret 和镜像环境变量生成 `.local/production-overlay`，并自动执行严格生产配置检查。该输出目录包含明文 Secret，已被 `.gitignore` 忽略；生产集群建议把生成结果接入 SealedSecret、ExternalSecret 或平台密钥系统。`deploy/kubernetes` 原始目录包含占位符，只作为模板和 dry-run 输入，正式环境只 apply 生成后的 overlay。

上线前还必须对已替换真实域名、OB、Redis、Secret 和镜像的生产 overlay 执行：

```bash
bash scripts/production-config-check.sh <production-overlay-path>
```

这个严格检查不会用于默认模板目录，因为模板中保留了 `CHANGE_ME_*` 占位符；它用于阻断真实环境误带占位符、弱密钥、localhost origin 或 `latest` 镜像。

生产或预发环境 apply 后执行 live runtime check：

```bash
node scripts/production-runtime-check.mjs --namespace <namespace> --context <kube-context>
```

正式证据包和 Go/No-Go 会以 `--require-ingress-address` 执行该命令；手动生产复核也建议加上该参数，避免 Ingress 尚未获得真实地址时被误判为可上线。该命令会检查 rollout、Pod Ready、滚动策略、拓扑分散、探针、ClusterIP Service、Ingress TLS、TLS Secret、RWX PVC、PDB 规则、NetworkPolicy 规则、容器安全上下文、`emptyDir.sizeLimit`、资源 requests/limits 和生产 ConfigMap/Secret 值。它需要真实 OB Oracle、Redis Cluster、镜像仓库、Ingress Controller 和 TLS Secret 已经就绪。

需要留存上线证据时执行：

```bash
CREST_READINESS_COLLECT_EVIDENCE=true \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<kube-context> \
bash scripts/enterprise-readiness-check.sh
```

OB Oracle 初始化/备份/恢复、Redis Cluster ACL/隔离/故障切换、历史凭据轮换、TLS、RWX 存储、业务冒烟和故障演练这些 Kubernetes API 无法证明的内容，按 `docs/production-external-evidence-template.md` 放入私有目录后执行：

```bash
CREST_REDIS_CLUSTER_NODES=<redis-node-1>:6379,<redis-node-2>:6379,<redis-node-3>:6379 \
CREST_REDIS_USERNAME=<redis-acl-user> \
CREST_REDIS_PASSWORD='<redis-password>' \
CREST_REDIS_KEY_PREFIX='{<org>-<env>-crest-core}:prod' \
CREST_REDIS_CACHE_KEY_PREFIX='{<org>-<env>-crest-core}:prod:cache:' \
CREST_LOCK_KEY_PREFIX='{<org>-<env>-crest-core}:prod:lock' \
CREST_WEBSOCKET_BROADCAST_CHANNEL='{<org>-<env>-crest-core}:prod:pubsub:websocket' \
CREST_EXPORT_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:export-task' \
CREST_EXPORT_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:export-workers' \
CREST_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:dataset-sync-task' \
CREST_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:dataset-sync-workers' \
CREST_DATASOURCE_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:datasource-sync-task' \
CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:datasource-sync-workers' \
CREST_SCHEDULED_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:scheduled-task' \
CREST_SCHEDULED_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:scheduled-workers' \
bash scripts/redis-cluster-namespace-check.sh

CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence \
bash scripts/production-external-evidence-check.sh
```

本地网络无法直连 Go module/sumdb 时，可用国内代理安装 OSV Scanner：

```bash
GOPROXY=https://goproxy.cn,direct GOSUMDB=off GONOSUMDB='*' \
  GOBIN=/tmp/crest-tools \
  CREST_OSV_SCANNER_INSTALL_DIR=/tmp/crest-tools bash scripts/install-osv-scanner.sh
GOPROXY=https://goproxy.cn,direct GOSUMDB=off GONOSUMDB='*' \
  GOBIN=/tmp/crest-tools \
  CREST_GITLEAKS_INSTALL_DIR=/tmp/crest-tools bash scripts/install-gitleaks.sh
GOPROXY=https://goproxy.cn,direct GOSUMDB=off GONOSUMDB='*' \
  GOBIN=/tmp/crest-tools \
  CREST_ACTIONLINT_INSTALL_DIR=/tmp/crest-tools bash scripts/install-actionlint.sh
CREST_SEMGREP_INSTALL_DIR=/tmp/crest-tools bash scripts/install-semgrep.sh
CREST_OSV_SCANNER=/tmp/crest-tools/osv-scanner \
  CREST_SEMGREP_BIN=/tmp/crest-tools/semgrep \
  CREST_GITLEAKS_BIN=/tmp/crest-tools/gitleaks \
  bash scripts/security-scan.sh
CREST_ACTIONLINT_BIN=/tmp/crest-tools/actionlint bash scripts/code-style-check.sh
```

扫描报告写入 `reports/security/`，该目录不提交仓库。GitHub Actions 中 `Quality Gates` 和 `Security Gates` 会重复执行这些检查并上传安全报告。第三方 GitHub Action 升级时，先查询目标发布 tag 对应的 commit SHA，再改 workflow 的 `uses:` SHA 和行尾版本注释；`github-actions-policy-check.sh` 会拒绝可变 tag 或 branch。扫描器和 workflow linter 升级时，修改对应 `scripts/install-*.sh` 的默认版本并让 `ci-toolchain-policy-check.sh` 记录新版本，不要在 workflow YAML 里直接写下载命令。

企业安全审计如果要求 git 历史也干净，执行：

```bash
bash scripts/history-secret-audit.sh
```

该脚本会对 git 历史执行 redacted Gitleaks 扫描，只在控制台汇总规则、文件和提交数量，不打印密文。若历史命中真实凭据，应先轮换凭据，再选择发布清理后的历史或从当前干净工作树创建新的发布仓库；不要把历史泄露仅靠当前文件删除视为已修复。

如果企业审计允许不携带历史的源码交付，可以在完成凭据轮换后生成 clean source 包：

```bash
bash scripts/create-clean-source-release.sh
```

该脚本会输出 `reports/release-source/crest-core-<version>-source-<timestamp>.tar.gz`、SHA-256 文件和 `reports/security/gitleaks-clean-source.json`，并阻断导出源码中的 Secret 命中。它故意排除 `.git`、`.local`、`reports`、构建产物、本地密钥/证书/备份等路径；如果已有 `reports/security/gitleaks-history.json`，摘要会记录剩余历史命中数量。正式交付 clean-source 路径时，设置 `CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true` 并声明 `CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery` 和 `CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES`；通过 `CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true` 走生产准入入口时会自动强制该校验。如果企业要求完整 git 历史无泄露，仍必须清理历史并让 `scripts/history-secret-audit.sh` 通过。

正式发布源码包应从干净、已提交的工作树生成：

```bash
CREST_READINESS_CREATE_CLEAN_SOURCE=true \
CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true \
bash scripts/enterprise-readiness-check.sh
```

## 提交前检查

- 后端使用 JDK 17 构建或测试。
- 前端 `pnpm run build:base` 和 `pnpm run build:lite:check` 通过。
- `bash scripts/quality-check.sh` 通过。
- `bash scripts/security-scan.sh` 通过。
- `bash scripts/history-secret-audit.sh` 按企业审计要求通过，或已完成凭据轮换并以干净历史交付。
- 如选择无历史源码包交付，`bash scripts/create-clean-source-release.sh` 通过并留存 SHA-256 与 `gitleaks-clean-source.json`。
- 上线前 `bash scripts/production-external-evidence-check.sh` 对外部证据目录校验通过。
- 初始化 SQL `--check` 通过。
- Kubernetes 生产校验和 dry-run 通过。
- 文档、release notes、版本号和镜像 tag 与实际行为一致。
- 不提交 `.env`、密码、token、私钥、证书、备份、日志、离线包或真实业务数据。
