export const DATASET_SYNC_CONFIG_KEYS = [
  'name',
  'updateType',
  'incrementalFieldId',
  'startTime',
  'syncRate',
  'cron',
  'simpleCronValue',
  'simpleCronType',
  'endTime',
  'fullSyncIntervalHours',
  'verifyEnabled',
  'cacheExpireHours',
  'taskTimeoutMinutes',
  'failureWarnThreshold'
]

// 后台轮询刷新时保留用户正在编辑的配置项，只更新任务状态、校验结果和运行指标
export const applyDatasetSyncTaskPatch = (target, task, { preserveConfig = false } = {}) => {
  if (!target || !task) {
    return target
  }
  Object.keys(task).forEach(key => {
    if (preserveConfig && DATASET_SYNC_CONFIG_KEYS.includes(key)) {
      return
    }
    target[key] = task[key]
  })
  return target
}

// 判断字段是否为可同步的普通字段
export const isDatasetSyncField = field =>
  Number(field?.extField) === 0 && field?.checked !== false

// 增量字段只允许时间或数值字段，避免文本字段按字典序参与水位比较
export const isDatasetIncrementalField = field =>
  isDatasetSyncField(field) &&
  [field?.extractedFieldType, field?.fieldType].some(type => [1, 2, 3].includes(Number(type)))

// 根据数据集缓存配置构建保存任务载荷
export const buildDatasetSyncTaskPayload = ({ syncTask, datasetGroupId, datasetName }) => ({
  id: syncTask.id,
  datasetGroupId,
  name: datasetName,
  updateType: syncTask.updateType,
  incrementalFieldId: syncTask.updateType === 'add_scope' ? syncTask.incrementalFieldId : null,
  startTime: syncTask.startTime,
  syncRate: syncTask.syncRate,
  cron: syncTask.cron,
  simpleCronValue: syncTask.simpleCronValue,
  simpleCronType: syncTask.simpleCronType,
  endTime: syncTask.endTime,
  fullSyncIntervalHours: syncTask.fullSyncIntervalHours,
  verifyEnabled: syncTask.verifyEnabled,
  cacheExpireHours: syncTask.cacheExpireHours,
  taskTimeoutMinutes: syncTask.taskTimeoutMinutes,
  failureWarnThreshold: syncTask.failureWarnThreshold
})

// 格式化同步日志影响行数
export const formatDatasetSyncRowCount = log => {
  if (!log) {
    return ''
  }
  if (log.rowCount === null || log.rowCount === undefined) {
    return ''
  }
  if (log.taskStatus === 'UnderExecution') {
    return log.rowCount > 0 ? `已写入 ${log.rowCount} 行` : ''
  }
  return `${log.rowCount} 行`
}

// 标准化后端同步日志描述，统一页面展示文案
export const normalizeDatasetSyncInfo = info => {
  if (!info) {
    return ''
  }
  return String(info)
    .replaceAll('对账通过', '源数据与缓存一致')
    .replaceAll('对账执行失败', '数据一致性校验失败')
}

// 根据同步任务状态计算页面状态文案
export const getDatasetSyncStatusText = syncTask => {
  if (syncTask?.syncMode !== 1) return '未开启'
  if (!syncTask?.hasDatasetId) return '待保存'
  if (syncTask?.taskStatus === 'UnderExecution') return '更新中'
  if (syncTask?.failureWarned) return '更新失败'
  if (syncTask?.lastVerifyStatus === 'WARNING') return '更新失败'
  if (syncTask?.lastExecStatus === 'Completed') {
    return syncTask?.cacheReady === 1 && !syncTask?.cacheExpired ? '缓存可用' : '待更新'
  }
  if (syncTask?.lastExecStatus === 'Error') return '更新失败'
  return '待更新'
}

// 计算立即执行缓存更新按钮的禁用原因
export const getDatasetSyncNowDisabledReason = ({ hasDatasetId, syncRunning }) => {
  if (!hasDatasetId) return '请先保存数据集，再执行缓存更新'
  if (syncRunning) return '缓存更新正在执行'
  return ''
}

// 计算增量更新不可用原因
export const getIncrementalDisabledReason = incrementalFieldOptions => {
  return Array.isArray(incrementalFieldOptions) && incrementalFieldOptions.length
    ? ''
    : '增量同步需要时间或数值字段'
}

// 执行立即更新缓存的完整流程
export const executeDatasetSyncNowFlow = async ({
  currentDatasetId,
  syncRunning,
  warn,
  saveCurrentDataset,
  persistDatasetSync,
  executeDatasetSync,
  loadDatasetSyncLogs,
  applyTask,
  applyLogs,
  startSyncPolling,
  success
}) => {
  if (!currentDatasetId) {
    warn('请先保存数据集')
    return { submitted: false, reason: 'missingDatasetId' }
  }
  if (syncRunning) {
    warn('缓存更新正在执行')
    return { submitted: false, reason: 'running' }
  }

  await saveCurrentDataset(currentDatasetId)
  await persistDatasetSync(currentDatasetId)
  const task = await executeDatasetSync(currentDatasetId)
  if (task) {
    applyTask(task)
  }
  const logs = await loadDatasetSyncLogs(currentDatasetId)
  applyLogs(logs)
  startSyncPolling()
  success('缓存更新已提交')
  return { submitted: true, task, logs }
}
