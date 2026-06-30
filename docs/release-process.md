# Crest Core 分支和发布规范

本文定义 Crest Core 首版后的分支、补丁和发布规则。当前产品按全新环境交付，不提供旧部署的原地升级链路。

## 核心原则

- 已发布 tag 不移动、不删除、不重打。
- 业务代码、SQL、配置、前端产物或镜像内容变化时必须发布新版本。
- 补丁版本只做最小修复，不混入新功能、重构或无关格式化。
- 系统库目前只支持 OceanBase Oracle；数据库变更必须同步更新首版初始化 SQL，并在 release notes 中说明是否需要手工补丁 SQL。
- 受保护分支和 tag 必须通过 Pull Request、Release Guard 和 GitHub Rulesets。

## 长期分支

| 分支 | 用途 | 合入方式 |
| --- | --- | --- |
| `main` | 稳定主线，代表最新可发布状态 | 只接受 PR |
| `dev` | 新功能和非紧急修复集成分支 | 只接受 PR |
| `release/<major.minor>` | 已发布稳定线维护分支，例如 `release/1.0` | 只接受 PR |

## 临时分支命名

| 场景 | 分支格式 | 目标分支 |
| --- | --- | --- |
| 新功能或规划变更 | `feat/<short-name>` | `dev` |
| 未发布普通缺陷 | `fix/<short-name>` | `dev` |
| 已发布版本补丁 | `hotfix/<next-version>-<short-name>` | 对应 `release/<major.minor>` |
| 安全修复 | `security/<short-name>` 或 `hotfix/<next-version>-security-<short-name>` | `dev` 或对应 release 分支 |
| CI、发布流程、维护 | `ci/<short-name>` 或 `chore/<short-name>` | 按影响范围选择 |
| 文档 | `docs/<short-name>` | 对应长期分支 |

## 版本发布

发布前必须满足：

- `VERSION`、Maven 根版本、`crest.version`、前端 `package.json` 版本一致。
- `CHANGELOG.md` 有当前版本章节。
- `docs/release/vX.Y.Z.md` 存在。
- `scripts/generate-ob-oracle-init-schema.mjs --check` 通过。
- `scripts/verify-kubernetes-production.mjs deploy/kubernetes` 通过。
- `kubectl create --dry-run=client -f deploy/kubernetes -o name` 通过。
- `scripts/enterprise-readiness-check.sh` 静态准入通过；正式生产环境还必须按 [生产准入](./production-readiness.md) 留存真实环境验收证据和 `production-evidence-bundle.sh` 输出。
- 正式 Go/No-Go 后必须执行 `scripts/production-go-no-go-summary-check.sh reports/readiness/enterprise-readiness-summary.txt`，防止把生产候选摘要误当成生产放行摘要。
- 镜像构建和容器 CVE 扫描 workflow 通过；正式发布 workflow 会在推送镜像前重新执行 quality gate、SAST、SCA 和容器镜像漏洞扫描。
- GitHub Actions 第三方 `uses:` 必须固定到 commit SHA，并通过 `scripts/github-actions-policy-check.sh`；升级 action 时同步更新 SHA 和行尾版本注释。
- CI 扫描器和 workflow linter 必须通过仓库内 `scripts/install-*.sh` 集中安装并通过 `scripts/ci-toolchain-policy-check.sh`；不要在 workflow 内联 `go install` 或 `pipx install semgrep`。
- 正式发布和 Go/No-Go 使用的 JDK、runtime、Nginx 基础镜像必须固定到 `@sha256`，并通过 `scripts/container-base-image-policy-check.sh`。
- 面向真实环境的 overlay 必须由 `scripts/render-production-overlay.sh` 或等价受控流程生成，并通过 `scripts/production-config-check.sh <production-overlay-path>`。
- 企业审计要求 git 历史无泄露时，`scripts/history-secret-audit.sh` 必须通过；若发现旧提交命中，先轮换相关凭据，再从清理后的历史或新的干净发布仓库交付。
- 正式发布镜像只能使用与 `VERSION` 一致的不可变 `vX.Y.Z` 版本标签，并随构建附带 `sha-<commit>` 标签；不发布 `main`、`latest` 等可变生产标签。

发布交付物：

```text
ghcr.io/sevoniva/crest-core-service:vX.Y.Z
ghcr.io/sevoniva/crest-core-web:vX.Y.Z
crest-core-vX.Y.Z-linux-amd64-offline.tar.gz
crest-core-vX.Y.Z-linux-arm64-offline.tar.gz
installer/init-sql/ob-oracle/crest-core-schema.sql
deploy/kubernetes/
deploy/docker/
```

## 数据库变更

首版交付只支持全新 OB Oracle 系统库初始化。修改表结构、初始化数据或系统参数时：

1. 修改 OB Oracle 迁移资源。
2. 执行 `node scripts/generate-ob-oracle-init-schema.mjs` 刷新首版初始化 SQL。
3. 执行 `node scripts/generate-ob-oracle-init-schema.mjs --check`。
4. 在 release notes 中说明全新安装行为。
5. 如果已发布环境也要升级，必须另行设计补丁 SQL、备份要求、回滚策略和验收步骤。

已经发布的初始化 SQL 不直接覆盖线上环境；线上数据库变更必须由 DBA 在维护窗口执行经过评审的补丁 SQL。

## 协作要求

- 需求不明确时，先判定是否影响已发布版本、数据库结构、Kubernetes 清单或镜像发布。
- 涉及已发布版本的问题，优先按 hotfix 流程处理。
- 涉及新功能或规划能力的问题，从功能分支进入集成分支。
- 不移动、不删除、不覆盖已发布 tag，除非已按事故流程完成风险确认。
