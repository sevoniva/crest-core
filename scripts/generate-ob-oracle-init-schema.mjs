import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const releaseVersion = 'v1.0.0'

const definitions = [
  {
    name: 'OceanBase Oracle',
    initOutput: 'installer/init-sql/ob-oracle/crest-core-schema.sql',
    sources: [
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.1__initial_schema.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.2__export_task_queue.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.3__datasource_sync_task_queue.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.4__dataset_sync_task_queue.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.5__dataset_sync_task_queue_metadata.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.6__scheduled_task_queue_state.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.7__repair_seed_text_encoding.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.8__audit_log_filter_index.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.9__rename_metadata_tables.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.10__normalize_runtime_identifiers.sql',
      'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.11__normalize_scheduler_metadata_names.sql'
    ]
  }
]

const checkMode = process.argv.includes('--check')
const skippedSourceNames = new Set([
  'V1.0.0.7__repair_seed_text_encoding.sql',
  'V1.0.0.9__rename_metadata_tables.sql',
  'V1.0.0.10__normalize_runtime_identifiers.sql',
  'V1.0.0.11__normalize_scheduler_metadata_names.sql'
])

const read = relativePath => readFileSync(path.join(root, relativePath), 'utf8')

const parseTableRenamePairs = relativePath => {
  const source = read(relativePath)
  return [...source.matchAll(/crest_rename_table_if_needed\('([^']+)', '([^']+)'\)/g)].map(
    match => [match[1], match[2]]
  )
}

const parseTextReplacePairs = relativePath => {
  const source = read(relativePath)
  const pairs = [...source.matchAll(/crest_replace_text_if_needed\([^,]+,[^,]+,\s*'([^']*)',\s*'([^']*)'\)/g)].map(
    match => [match[1], match[2]]
  )
  return [...new Map(pairs.map(pair => [pair.join('\u0000'), pair])).values()]
}

const tableRenamePairs = [
  ...parseTableRenamePairs('core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.9__rename_metadata_tables.sql'),
  ...parseTableRenamePairs(
    'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.11__normalize_scheduler_metadata_names.sql'
  )
]
const tableRenameMap = new Map(tableRenamePairs)

const resolveFinalTableName = tableName => {
  let current = tableName
  const visited = new Set()
  while (tableRenameMap.has(current) && !visited.has(current)) {
    visited.add(current)
    current = tableRenameMap.get(current)
  }
  return current
}

const tableNameReplacements = [...new Set(tableRenamePairs.map(([oldName]) => oldName))]
  .map(oldName => [oldName, resolveFinalTableName(oldName)])
  .filter(([oldName, newName]) => oldName !== newName)
  .flatMap(([oldName, newName]) => [
    [oldName, newName],
    [oldName.toUpperCase(), newName]
  ])

const runtimeIdentifierReplacements = parseTextReplacePairs(
  'core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.10__normalize_runtime_identifiers.sql'
)

const schedulerMetadataReplacements = [
  ['QRTZ_BLOB_TRIGGERS_ibfk_1', 'fk_csch_blob_trigger'],
  ['QRTZ_CRON_TRIGGERS_ibfk_1', 'fk_csch_cron_trigger'],
  ['QRTZ_SIMPLE_TRIGGERS_ibfk_1', 'fk_csch_simple_trigger'],
  ['QRTZ_SIMPROP_TRIGGERS_ibfk_1', 'fk_csch_simprop_trigger'],
  ['QRTZ_TRIGGERS_ibfk_1', 'fk_csch_trigger_job'],
  ['`qrtz_instance` varchar(1024) DEFAULT NULL COMMENT \'Quartz 实例 ID\'', '`scheduler_fire_instance_id` varchar(1024) DEFAULT NULL COMMENT \'调度触发实例编号\''],
  ['`qrtz_instance` longtext COMMENT \'状态\'', '`scheduler_fire_instance_id` longtext COMMENT \'调度触发实例编号\''],
  ['qrtz_instance', 'scheduler_fire_instance_id'],
  ['`scheduler_fire_instance_id` varchar(1024) DEFAULT NULL COMMENT \'Quartz 实例 ID\'', '`scheduler_fire_instance_id` varchar(1024) DEFAULT NULL COMMENT \'调度触发实例编号\''],
  ['`scheduler_fire_instance_id` longtext COMMENT \'状态\'', '`scheduler_fire_instance_id` longtext COMMENT \'调度触发实例编号\''],
  ['CREATE INDEX `SCHED_NAME` ON `core_schedule_triggers`', 'CREATE INDEX `idx_core_schedule_triggers_job` ON `core_schedule_triggers`'],
  ['CREATE INDEX SCHED_NAME ON core_schedule_triggers', 'CREATE INDEX idx_core_schedule_triggers_job ON core_schedule_triggers']
]

const commentTextReplacements = [
  ["COMMENT 'api'", "COMMENT '接口'"],
  ["COMMENT 'table-row'", "COMMENT '表格行扩展配置'"],
  ["COMMENT 'user or api'", "COMMENT '消息来源类型'"],
  ["COMMENT 'mysql oracle ...'", "COMMENT '数据库引擎类型'"],
  ["COMMENT 'create sql'", "COMMENT '建表 SQL'"],
  ["COMMENT 'free or license'", "COMMENT '令牌类型'"],
  ["COMMENT 'Create timestamp'", "COMMENT '创建时间'"],
  ["COMMENT 'Update timestamp'", "COMMENT '更新时间'"],
  ["COMMENT 'uuid'", "COMMENT 'UUID'"],
  ["COMMENT 'ticket'", "COMMENT '分享票据'"],
  ["COMMENT 'Web hooks'", "COMMENT 'Webhook 接收人'"],
  ["COMMENT 'url'", "COMMENT '地址'"],
  ["COMMENT 'content_type'", "COMMENT '内容类型'"],
  ["COMMENT 'link jump ID'", "COMMENT '跳转配置ID'"],
  ["COMMENT 'SM2 private key'", "COMMENT 'SM2 私钥'"],
  ["COMMENT 'SM2 public key'", "COMMENT 'SM2 公钥'"],
  ["COMMENT 'SM2 key creation time'", "COMMENT 'SM2 密钥生成时间'"]
]

const orderedTableNameReplacements = tableNameReplacements.sort((left, right) => right[0].length - left[0].length)

const orderedTextReplacements = [...schedulerMetadataReplacements, ...commentTextReplacements, ...runtimeIdentifierReplacements].sort(
  (left, right) => right[0].length - left[0].length
)

const identifierChar = char => /[A-Za-z0-9_]/.test(char)

const replaceSqlIdentifier = (source, oldName, newName) => {
  let result = ''
  let index = 0
  while (index < source.length) {
    const matchIndex = source.indexOf(oldName, index)
    if (matchIndex === -1) {
      result += source.slice(index)
      break
    }
    const before = matchIndex > 0 ? source[matchIndex - 1] : ''
    const afterIndex = matchIndex + oldName.length
    const after = afterIndex < source.length ? source[afterIndex] : ''
    const boundaryMatch = (!before || !identifierChar(before)) && (!after || !identifierChar(after))
    result += source.slice(index, matchIndex)
    result += boundaryMatch ? newName : oldName
    index = afterIndex
  }
  return result
}

const applyFinalNaming = source => {
  const withTableNames = orderedTableNameReplacements.reduce(
    (content, [oldValue, newValue]) => replaceSqlIdentifier(content, oldValue, newValue),
    source
  )
  return orderedTextReplacements.reduce(
    (content, [oldValue, newValue]) => content.split(oldValue).join(newValue),
    withTableNames
  )
}

const separator = index =>
  [
    '',
    '-- ----------------------------------------------------------------------',
    `-- Section ${index + 1}`,
    '-- ----------------------------------------------------------------------',
    ''
  ].join('\n')

const buildContent = definition => {
  const header = [
    `-- Crest Core ${releaseVersion} ${definition.name} 空库一次性初始化 SQL。`,
    '-- 仅适用于 Crest Core 首版全新系统库。',
    `-- 本文件直接使用 ${releaseVersion} 最终命名，不包含升级过程中的重命名 SQL。`,
    '-- 本文件由 scripts/generate-ob-oracle-init-schema.mjs 生成。',
    ''
  ].join('\n')

  const body = definition.sources
    .filter(source => !skippedSourceNames.has(path.basename(source)))
    .map((source, index) => {
      const sourcePath = path.join(root, source)
      return `${separator(index)}${applyFinalNaming(readFileSync(sourcePath, 'utf8')).trim()}\n`
    })
    .join('\n')
  const oracleComments = ''

  return `${header}${body}${oracleComments}`
}

for (const definition of definitions) {
  const expected = buildContent(definition)
  const outputs = [
    ...(definition.initOutput
      ? [{ path: path.join(root, definition.initOutput), label: definition.initOutput }]
      : [])
  ]

  if (checkMode) {
    for (const output of outputs) {
      if (!existsSync(output.path) || readFileSync(output.path, 'utf8') !== expected) {
        console.error(`${output.label} is not up to date`)
        process.exitCode = 1
      }
    }
    continue
  }

  for (const output of outputs) {
    mkdirSync(path.dirname(output.path), { recursive: true })
    writeFileSync(output.path, expected, 'utf8')
    console.log(`generated ${output.label}`)
  }
}
