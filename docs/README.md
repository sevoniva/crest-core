# Crest Core 文档

本文是 Crest Core 文档入口。根目录 README 只保留项目概览和常用入口，详细说明按主题维护在本目录和部署目录中。

## 架构与部署

| 文档 | 内容 |
| --- | --- |
| [架构设计](./architecture-design.md) | 系统上下文、运行拓扑、模块边界、数据流、Redis 隔离、高可用和安全设计 |
| [部署设计](./deployment-design.md) | 生产 overlay、OB Oracle、Redis Cluster、多副本、上线顺序和验收清单 |
| [Crest Core 生产范围](./crest-core-scope.md) | OpenJDK 17、OB Oracle、Kubernetes 多副本和默认瘦身边界 |
| [生产准入](./production-readiness.md) | 企业级上线门禁、真实环境验收、备份恢复和 Go/No-Go |
| [生产交付风险登记册](./production-delivery-risk-register.md) | 当前生产候选状态、剩余交付风险、责任分工和收口顺序 |
| [Kubernetes 部署](../deploy/kubernetes/README.md) | OceanBase Oracle、Redis、Secret、多副本和探针 |
| [OceanBase Oracle 初始化](../installer/README.md) | 首版 OB Oracle 空库初始化 SQL |
| [OceanBase 支持](./oceanbase-support.md) | 系统库和业务数据源接入 OceanBase Oracle 的配置与验收 |
| [可观测性](./observability.md) | Prometheus、指标、告警和排障 |

## 产品能力

| 文档 | 内容 |
| --- | --- |
| [数据血缘](./data-lineage.md) | 字段级血缘范围、入口、数据来源和接口 |
| [单点登录](./sso.md) | OIDC / Casdoor 授权码模式、用户映射和安全要求 |
| [平台管理](./platform-management.md) | 用户、组织、角色、菜单权限和资源权限 |

## 开发和发布

| 文档 | 内容 |
| --- | --- |
| [开发说明](./development.md) | 仓库结构、工具链、构建命令、数据库资产和提交检查 |
| [发布管理](./release-process.md) | 分支、补丁、数据库资产和 release 检查 |
| [前端工程](../core/core-frontend/README.md) | 前端构建、视觉约定、品牌资源和页面规则 |
| [发布说明](./release/v1.0.0.md) | 当前稳定版本发布说明 |

过程类审查报告、临时计划和本地环境记录不放入公开文档。
