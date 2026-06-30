# Crest Core 生产准入

本文定义 Crest Core 从生产候选到企业生产可上线的证据要求。默认 Kubernetes 模板、CI 绿灯或本地 dry-run 只能证明“候选可交付”，不能替代真实预发或生产环境验收。

## 一键准入入口

本地或 CI 发布前执行：

```bash
bash scripts/enterprise-readiness-check.sh
```

该命令默认串起：

- `quality-check.sh`：OpenJDK 17、生产 overlay smoke、前端类型/ESLint/构建、后端测试、静态资源一致性、release guard。
- `github-actions-policy-check.sh`：确认 GitHub Actions 第三方 `uses:` 全部固定到 commit SHA，避免 CI 供应链引用可变 tag 或 branch。
- `ci-toolchain-policy-check.sh`：确认 Semgrep、OSV Scanner、Gitleaks、actionlint 和 Trivy 的 CI 安装入口集中在仓库脚本中，并记录固定版本。
- `security-scan.sh`：Semgrep SAST、Gitleaks 当前树、pnpm audit、OSV SCA、Maven CycloneDX SBOM SCA，并在报告落盘后执行 `security-report-check.mjs`，确认所有 SAST/SCA/secret 报告均为 0 finding/0 vulnerability。
- `container-base-image-policy-check.sh`：检查 JDK、runtime 和 Nginx 基础镜像引用，拒绝 `latest`；企业 readiness 默认要求三类基础镜像都固定到 `@sha256`。
- `docker-environment-check.sh`：Docker daemon 和构建磁盘余量预检，默认要求至少 12GiB 可用空间。
- `docker-build-check.sh`：前后端镜像构建和 Nginx 配置检查。
- `test-docker-production-check.sh`：检查 `deploy/docker` 的两服务生产交付、外部 Redis Cluster、严格 env、安全基线和 Compose config。
- `container-image-scan.sh`：Trivy 镜像 CVE 门禁，默认阻断 HIGH/CRITICAL，并在报告落盘后执行 `container-report-check.mjs` 确认前后端镜像报告存在且 HIGH/CRITICAL 为 0。
- `kind-smoke-test.sh`：真实 Kubernetes API Server 的 server-side dry-run；执行前做 Docker/kind 磁盘预检，启用 `CREST_KIND_APPLY=true` 时会先执行 strict production config gate，再对 apply 后的 kind namespace 执行 runtime check。需要验证当前本地构建镜像时，可同时设置 `CREST_KIND_LOAD_LOCAL_IMAGES=true`，脚本会把 `crest-core-service:local-check` 和 `crest-core-web:local-check` 重打为 `sha-<commit>` 形式的不可变标签，装载进 kind，并在 runtime check 前把两个 StatefulSet 切到本地镜像。

每个已执行 gate 的 stdout/stderr 会同步写入 `reports/readiness/gate-logs/<gate>.log`，摘要文件记录日志路径和 SHA-256；失败时可把该目录连同 summary 一起归档，作为 CI 和上线审批的排障证据。Docker 预检失败时，summary 会记录只读 Docker 环境诊断报告；磁盘不足时还会记录只读清理计划路径与 SHA-256；历史密钥审计失败时，summary 还会记录历史审计摘要、redacted JSON 报告、SHA-256、finding 数和处置建议。

排障时可以开启继续收集证据模式：

```bash
CREST_READINESS_CONTINUE_ON_FAILURE=true bash scripts/enterprise-readiness-check.sh
```

该模式不会把失败变成通过；最终仍会返回失败状态。它只是在某个 gate 失败后继续执行后续相互独立的门禁，把更多证据写入同一份 summary。Docker 预检失败后不会继续 Docker build 或 container scan，而是记录 `docker-build: skipped-after-failed-prerequisite` 和 `container-scan: skipped-after-failed-prerequisite`，避免在 Docker 不健康或磁盘不足时继续消耗空间。

Docker 镜像构建默认使用 digest-pinned 基础镜像，`enterprise-readiness-check.sh` 默认会以 `CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS=true` 强制校验。发布 CI 已安装 Buildx 时，`docker-build-check.sh` 保留多架构 index digest；本地 Docker Desktop 缺少 Buildx 时，脚本会按 Docker daemon 架构把 Nginx 默认基础镜像切换到对应的 `linux/amd64` 或 `linux/arm64` manifest digest，避免 legacy builder 把 OCI index 当成不可导出的空层镜像。`docker-build-check.sh` 会在前后端产物构建前先检查三类基础镜像是否已在本地可用，不可用时先执行 `docker pull`，失败会直接提示对应的 `CREST_DOCKER_*_IMAGE`。企业 CI 或网络受限环境应提前把 JDK、runtime 和 Nginx 基础镜像同步到内网仓库，并通过 `CREST_DOCKER_JDK_IMAGE`、`CREST_DOCKER_RUNTIME_IMAGE`、`CREST_DOCKER_NGINX_IMAGE` 显式传入带 `@sha256` 的镜像引用；Go/No-Go 模式会拒绝关闭 digest 强制校验。
如果本地 Docker Desktop 已安装 Buildx 但在多架构 index 导出阶段仍反复访问 Docker Hub 超时，可设置 `CREST_READINESS_DOCKER_BUILDKIT=0` 运行 readiness；脚本会继续使用 digest-pinned 基础镜像，并把 Nginx 切换为当前架构的固定 manifest digest。企业 CI 更推荐使用内网 registry 的 digest-pinned 镜像引用，而不是依赖公网 Hub。

Trivy 漏洞库默认按 `ghcr.io`、`public.ecr.aws`、`mirror.gcr.io` 的顺序传给扫描器，避免企业网络里单个公网 registry 超时导致整条门禁卡住。网络受限环境应显式设置为内网同步源，多个源用逗号分隔：

```bash
CREST_TRIVY_DB_REPOSITORIES=registry.example.internal/security/trivy-db:2,ghcr.io/aquasecurity/trivy-db:2 \
CREST_TRIVY_JAVA_DB_REPOSITORIES=registry.example.internal/security/trivy-java-db:1,ghcr.io/aquasecurity/trivy-java-db:1 \
bash scripts/container-image-scan.sh
```

如本地 Docker Desktop 使用 containerd image store 导致 `trivy image` 无法从 `docker save` 结果读取完整 layer，`container-image-scan.sh` 会自动用 `docker create/export` 导出最终容器 rootfs 并执行 `trivy rootfs`。该 fallback 只在 image scan 没有生成有效 JSON 时触发，报告会写入 `Metadata.CrestScanMode=rootfs-fallback` 和原始镜像名；`container-report-check.mjs` 只接受带该元数据的 filesystem 报告，防止把普通文件系统扫描误当成镜像扫描。

摘要文件会写入 `readiness_status`：

- `failed`：某个已执行门禁失败，生产不可放行；摘要会记录失败 gate、退出码和 blocker。
- `partial-check-passed`：有静态门禁被显式跳过，只能说明部分检查通过。
- `production-candidate-passed`：默认静态准入、SAST/SCA、镜像构建/扫描和 kind dry-run 通过，但真实环境证据还未闭环。
- `go-no-go-passed`：严格 Go/No-Go 所需的 clean source、生产 overlay、真实 runtime/evidence bundle 和外部生产证据全部通过，仍需业务审批签字。

非 `go-no-go-passed` 时，摘要还会列出 `production_release_blocker`，用于区分“候选可交付”和“生产可放行”。

企业审计要求 git 历史也干净时，增加：

```bash
CREST_READINESS_REQUIRE_CLEAN_HISTORY=true bash scripts/enterprise-readiness-check.sh
```

当前仓库如果历史扫描命中旧提交，需要先轮换相关凭据，再选择清理历史或从当前干净工作树建立发布仓库。当前工作树的 secret scan 通过不等于历史泄露已处置。历史审计会写入 redacted JSON `reports/security/gitleaks-history.json` 和机器可读摘要 `reports/security/gitleaks-history-summary.txt`；摘要只记录 finding 数、commit 数、规则/文件分布和处置建议，不包含 secret 原文。

如果企业策略允许以“干净当前树 + 凭据轮换 + 无历史源码包/新发布仓库”的方式交付，可生成不包含 `.git` 历史的源码包：

```bash
CREST_READINESS_CREATE_CLEAN_SOURCE=true bash scripts/enterprise-readiness-check.sh
```

也可以单独执行：

```bash
bash scripts/create-clean-source-release.sh
```

该脚本会把当前工作树复制到 `reports/release-source/`，排除 `.git`、`.local`、`reports`、构建产物、本地密钥/证书/备份等路径，然后对导出的源码再次执行 Gitleaks 当前树扫描。它不能替代历史凭据轮换；它解决的是企业交付物不携带旧提交历史的问题。
clean source 摘要会记录生成时间、版本、源码分支、源码 commit、文件数量、归档包 SHA-256 和当前树 secret scan 结果，便于审批包不解压源码也能追溯交付物来源。如果仓库中已有 `reports/security/gitleaks-history.json`，clean source 摘要还会记录剩余历史命中数量和 `history_scan_report_sha256`，用于和外部凭据轮换证据中的历史扫描报告互相校验。通过 `enterprise-readiness-check.sh` 生成 clean source 时，若未显式设置 `CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT` 且默认历史报告不存在，脚本会先补跑一次非阻断的 redacted history audit，并按 `CREST_SECURITY_REPORT_DIR` 对齐 clean-source 使用的历史报告路径。正式交付时建议显式声明历史处置口径，例如：

```bash
CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH=clean-source \
CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery \
CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES=initial-admin-password,application-encryption-key \
CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true \
bash scripts/create-clean-source-release.sh
```

正式生产发布时，clean source 必须从已提交、可追溯的干净工作树生成：

```bash
CREST_READINESS_CREATE_CLEAN_SOURCE=true \
CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true \
bash scripts/enterprise-readiness-check.sh
```

该模式会拒绝未提交、已暂存或未跟踪的源码变更，避免交付物只能追溯到 dirty worktree；同时会强制启用 clean-source 凭据轮换校验，历史扫描仍有命中时必须声明受影响凭据类别并完成 `rotated-before-delivery`。

本地质量门禁还会执行：

```bash
bash scripts/test-production-overlay-render.sh
```

该测试使用合规假值生成 `.local/production-overlay-smoke`，再运行严格生产配置检查，用来防止 overlay 模板、Secret 引用、Ingress TLS、Redis Cluster hash tag、镜像 tag 和 PVC 配置在重构中被破坏。

## 生产 Overlay

生产 overlay 必须由受控流程生成并严格校验。推荐：

```bash
mkdir -p .local
cp deploy/kubernetes/production.env.example .local/crest-production.env
# 编辑 .local/crest-production.env 为真实生产值
set -a
source .local/crest-production.env
set +a

CREST_READINESS_RENDER_OVERLAY=true bash scripts/enterprise-readiness-check.sh
```

脚本会生成 `.local/production-overlay` 并执行 `production-config-check.sh`。该目录包含明文 Secret，不能提交仓库；在企业集群中建议用 SealedSecret、ExternalSecret 或平台密钥系统接管。

## 真实环境验收

预发或生产 namespace apply 后，必须执行：

```bash
CREST_READINESS_LIVE_CHECK=true \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<context> \
bash scripts/enterprise-readiness-check.sh
```

如果只想跑 live gate：

```bash
CREST_READINESS_SKIP_QUALITY=true \
CREST_READINESS_SKIP_SECURITY=true \
CREST_READINESS_SKIP_DOCKER=true \
CREST_READINESS_SKIP_CONTAINER_SCAN=true \
CREST_READINESS_SKIP_KIND=true \
CREST_READINESS_LIVE_CHECK=true \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<context> \
bash scripts/enterprise-readiness-check.sh
```

live gate 会验证 rollout、Pod Ready、探针、TLS Ingress、Service、RWX PVC、PDB、NetworkPolicy、安全上下文、资源 request/limit、ConfigMap/Secret、Redis 前缀和禁用功能开关。

如需同时采集上线证据目录：

```bash
CREST_READINESS_COLLECT_EVIDENCE=true \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<context> \
bash scripts/enterprise-readiness-check.sh
```

证据目录默认写入 `reports/readiness/evidence-<namespace>-<timestamp>`。其中 Secret 只保留类型、key 名和解码长度，不写入 Secret 值；证据包生成和 Go/No-Go 校验都会拒绝包含原始 `data`/`stringData` 的 Secret 摘要，并要求 `crest-db-secret`、`crest-redis-secret`、`crest-tls` 的脱敏元数据齐全。证据包默认以 `--require-ingress-address` 运行 runtime check，`summary.txt` 必须记录 `runtime_check_require_ingress_address=true`，防止 Ingress 尚未获得真实地址时被误判为上线可用。目录内会生成 `evidence-manifest.sha256`，对采集到的 Kubernetes 对象、事件、脱敏 Secret 摘要和 runtime check 输出做 SHA-256 摘要，便于上线审批包归档后复核。

Kubernetes API 无法证明的外部证据放入一个私有目录，例如：

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

该目录格式见 [外部生产证据模板](./production-external-evidence-template.md)。脚本会检查 OB Oracle 初始化、备份恢复、Redis Cluster ACL/隔离/故障切换、历史凭据轮换、TLS Ingress、RWX 存储、业务冒烟和故障演练记录是否齐全，并要求 `redis-cluster.md` 引用 `redis-cluster-namespace-check.sh` 生成的通过报告；该报告必须证明至少 3 个 Redis Cluster 节点、节点地址格式合法、同一 hash tag 下的 key、stream、pub/sub 正向探测通过，以及未授权 key、stream、channel 操作被 Redis ACL 拒绝。`credential-rotation.md` 引用的历史扫描 JSON 必须存在，`history_scan_report_sha256` 必须与该 JSON 重算 digest 一致，且 finding 数必须与 `history_findings_remaining` 一致。检查结果只写入摘要，不打印证据正文；Go/No-Go 会要求摘要包含 10 个固定证据文件的结构化清单和 SHA-256，并重新计算每个文件的 digest。

## 必须留存的上线证据

上线评审至少留存以下输出或截图：

- `reports/security/`：SAST/SCA/SBOM 报告。
- `reports/container/`：Trivy 前后端镜像扫描报告，HIGH/CRITICAL 为 0；Go/No-Go 会校验这些报告的 `ArtifactName` 覆盖生产证据包中两个 StatefulSet 实际部署的镜像。
- `reports/readiness/enterprise-readiness-summary.txt`，其中应包含 `github_actions_policy_report`、`ci_toolchain_policy_report`、`container_base_image_policy_report`、`docker_build_base_image_policy_report` 和对应 SHA-256。
- `reports/release-source/`：如选择无历史源码交付，保留 clean source 包、SHA-256 和 `gitleaks-clean-source.json`。
- `reports/readiness/evidence-<namespace>-<timestamp>/`：真实集群对象、事件、脱敏 Secret 摘要、runtime check 输出和 `evidence-manifest.sha256`。
- `CREST_EXTERNAL_EVIDENCE_DIR` 指向的外部证据目录，以及 `reports/readiness/external-evidence-summary.txt`。
- `production-config-check.sh <production-overlay-path>` 通过记录。
- `production-runtime-check.mjs --namespace <namespace>` 通过记录。
- OB Oracle 初始化 SQL 执行记录、DBA 备份记录和一次恢复演练记录。
- Redis Cluster 至少 3 个真实节点、独立 ACL 用户、唯一 key 前缀、非模板 hash tag 和连接测试记录。
- 历史 secret 扫描结论、相关凭据轮换或失效确认、以及 clean-source/clean-history 交付决策记录。
- `crest-data` PVC 已 Bound 且为 ReadWriteMany 的记录。
- Ingress TLS 证书、真实域名访问和证书到期监控记录。

## 故障演练

预发环境至少完成这些演练：

```bash
kubectl -n <namespace> rollout restart deploy/crest-service
kubectl -n <namespace> rollout status deploy/crest-service --timeout=300s

kubectl -n <namespace> delete pod -l app.kubernetes.io/name=crest-service --wait=false
kubectl -n <namespace> rollout status deploy/crest-service --timeout=300s
```

演练期间业务侧应确认登录、仪表盘访问、数据集预览、导出、异步同步任务、定时任务和 WebSocket 刷新没有持续失败。Redis Cluster 或 OB Oracle 故障切换需要在企业基础设施演练中完成，Crest 侧以 readiness 失败、恢复后自动 Ready、任务不重复执行作为验收标准。

## Go/No-Go

上线评审使用严格 Go/No-Go 模式：

```bash
CREST_READINESS_REQUIRE_GO_NO_GO=true \
CREST_READINESS_CREATE_CLEAN_SOURCE=true \
CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true \
CREST_READINESS_RENDER_OVERLAY=true \
CREST_READINESS_COLLECT_EVIDENCE=true \
CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence \
CREST_K8S_NAMESPACE=<namespace> \
CREST_KUBE_CONTEXT=<context> \
bash scripts/enterprise-readiness-check.sh
```

该模式会拒绝跳过 quality、SAST/SCA、Docker build、镜像 CVE、kind dry-run、clean source、生产 overlay、evidence bundle、live runtime check 或外部生产证据检查。它仍不能替代业务验收和审批签字，但可以防止把“生产候选门禁”误当成“上线放行”。

Go/No-Go 模式会在结束时自动执行摘要强校验；发布流水线或审批脚本也可以重复执行同一命令，确保候选状态不会被误放行：

```bash
bash scripts/production-go-no-go-summary-check.sh reports/readiness/enterprise-readiness-summary.txt
```

该命令只接受 `readiness_status=go-no-go-passed`、`production_release_status=ready-for-business-approval`、没有 `production_release_blocker`，并且摘要中保留了 Go/No-Go 模式、clean source、生产 overlay、evidence bundle、外部生产证据、历史凭据处置路径和所有静态门禁的 `passed` 记录。摘要还必须包含 GitHub Actions 不可变引用策略报告、CI toolchain 集中安装策略报告、基础镜像策略报告、Docker build 实际基础镜像策略报告、SAST/SCA 报告 manifest、Trivy 镜像扫描报告 manifest、clean source 包、clean source summary、clean source 版本/commit/文件数、clean source secret scan 报告、clean source 历史 secret scan 报告、`credential-rotation.md` 引用的历史 secret scan 报告、生产 evidence bundle summary、`evidence-manifest.sha256` 和外部证据 summary 的 SHA-256；强校验会确认这些文件存在并重算 digest，同时重新解析 GitHub Actions SHA pin、CI 工具固定版本、基础镜像 digest pin 状态、SAST/SCA、Trivy、clean source 和历史 secret JSON 报告，逐条重算 evidence manifest 中记录的证据文件 digest，复核 Redis namespace 报告至少包含 3 个 Redis Cluster 节点和合法 `host:port` 节点地址，并确认 evidence bundle 是以 Ingress 地址硬门禁执行 runtime check，runtime 输出包含成功行且没有 `runtime-check: warning:`，再比对 clean source summary 内部的生成时间、版本、源码分支、源码 commit、文件数量、归档包、secret scan、干净工作树和历史处置字段，便于审批包归档后复核。历史凭据处置路径来自外部证据中的 `credential-rotation.md`，并且必须和 clean source summary 中记录的历史处置口径一致：`clean-history` 要求 `history_secret_findings_remaining=0`；`clean-source` 或 `fresh-repository` 要求 `history_secret_credential_rotation_status=rotated-before-delivery`，同时必须记录受影响凭据类别、轮换工单或审计记录、审批人/团队和 `YYYY-MM-DD` 格式的审批日期。clean source summary 与外部凭据证据引用的历史扫描报告 SHA-256 必须一致，防止审批包混用不同历史扫描结果。

可以进入生产的最低条件：

- 所有静态门禁、SAST/SCA、镜像 CVE、kind dry-run 通过。
- 正式发布源码包从干净工作树生成，发布 commit、镜像 tag、SBOM 和扫描报告可互相追溯。
- 真实生产 overlay 严格校验通过，且没有占位符、弱密钥、`latest` 镜像或 localhost origin。
- 真实预发或生产 runtime check 通过。
- OB Oracle、Redis Cluster、RWX 存储、TLS Ingress、备份恢复、凭据轮换和故障演练证据齐全。
- 企业审计要求 git 历史无泄露时，历史审计通过或已完成凭据轮换和干净历史交付。

任一项缺失时，只能定性为生产候选，不能声明完整企业生产达标。
