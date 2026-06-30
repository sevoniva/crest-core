# Crest Core OceanBase Oracle 初始化 SQL

本目录用于全新生产环境初始化，不承担历史版本升级职责。

当前入口：

```text
crest-core-schema.sql
```

使用方式：

```bash
obclient --default-character-set=utf8mb4 \
  -h <obproxy-host> -P 2883 \
  -u '<user>@<tenant>#<cluster>' \
  -p'<password>' \
  < installer/init-sql/ob-oracle/crest-core-schema.sql
```

约定：

- 只面向全新 OceanBase Oracle 系统库。
- 生产环境由 DBA 先创建租户、schema、账号和权限。
- 应用侧 `CREST_FLYWAY_ENABLED=false`，启动时不自动迁移生产库。
- 内部表名、配置键和包名仍保持 `crest`。
- 后续功能瘦身时，优先在这里维护最终 schema 和 seed。
