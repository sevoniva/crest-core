# Contributing

感谢关注 Crest Core。Crest Core 是基于 DataEase 2.10.22 开源版本继续开发的 GPLv3 BI 项目，并合入 2.10.23 相关安全加固和依赖升级。贡献代码前请先阅读 [docs/development.md](./docs/development.md)。

## 基本原则

- 保留上游 GPLv3 许可证和版权声明。
- 改动保持小而清楚，不把无关问题混进同一个提交。
- 不提交本地运行数据、日志、离线包、备份、账号密码、token、私钥或真实业务数据。
- 不引入私有制品仓库、私有 npm 源或授权不清的二进制文件。
- 修改部署、镜像、默认账号、数据口径或产品边界时，同步更新文档。
- 修改 OceanBase Oracle、Redis Cluster、数据集缓存、导出或权限相关代码时，要说明验证方式。

## 开发环境

推荐工具链：

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9 或兼容版本 |
| Node.js | 22 |
| pnpm | 11 |
| Docker | 20.10+ |

常用命令见 [docs/development.md](./docs/development.md)。

## 分支和提交

分支和发布规则见 [docs/release-process.md](./docs/release-process.md)。受保护分支包括 `main`、`dev` 和 `release/*`，必须通过 PR 合入。

新功能从 `dev` 创建短生命周期分支：

```bash
git checkout dev
git pull --ff-only
git checkout -b feat/your-change
```

已发布版本补丁从对应维护分支创建：

```bash
git checkout release/1.0
git pull --ff-only
git checkout -b hotfix/1.0.1-your-fix
```

提交信息使用简洁的 Conventional Commit 风格：

```text
feat: add datasource validation
fix: isolate redis stream names
docs: update kubernetes guide
chore: ignore local release artifacts
```

## Pull Request

提交 PR 前确认：

- 代码只包含本次变更需要的内容。
- 文档已同步。
- 已说明验证命令和结果。
- 没有提交本地密钥、日志、压缩包、数据库备份或测试账号。
- UI 改动附带截图或说明。
- 数据库变更已说明初始化 SQL、线上补丁 SQL 需求和回归方式。

PR 描述建议包含：

- 变更目的。
- 主要改动。
- 验证结果。
- 兼容性影响。
- 部署注意事项。

## Issue

提交缺陷时请提供：

- Crest Core 版本或镜像标签。
- 部署方式，Kubernetes 或源码运行。
- 浏览器版本。
- 复现步骤。
- 实际结果和期望结果。
- 脱敏后的日志或截图。

公开 Issue 中不要粘贴真实密码、token、生产库连接串、内部地址、客户名称或个人信息。需要提供敏感信息时请先脱敏。

## 文档要求

文档应写实际行为，不写空泛描述。涉及以下内容时必须更新文档：

- Kubernetes、版本号、镜像名、端口、默认账号。
- OceanBase Oracle 初始化 SQL、系统参数和初始数据。
- Redis Cluster、key 前缀、stream、consumer group、WebSocket channel。
- 数据源、数据集、仪表盘、大屏、权限、导出或部署边界。

## 许可确认

向本仓库提交贡献，即表示你确认：

- 你有权提交这些代码、文档或资源。
- 贡献内容可以按 GPLv3 发布。
- 贡献内容不包含未授权的第三方代码、私有数据或保密信息。
