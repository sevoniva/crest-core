<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, reactive } from 'vue'
import dayjs from 'dayjs'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus-secondary'
import GridTable from '@/components/grid-table/src/GridTable.vue'
import {
  datasetSyncTaskPage,
  executeDatasetSync,
  pauseDatasetSync,
  resumeDatasetSync,
  retryDatasetSync,
  type DatasetSyncTask
} from '@/api/dataset'
import {
  cacheTaskActions,
  cacheTaskFailureReason,
  cacheTaskRunningSnapshot,
  cacheTaskStatusText,
  formatCacheTaskDuration,
  resolveCacheTaskPollingError,
  shouldContinueCacheTaskPolling
} from './cacheTaskPolicy.mjs'

const POLL_INTERVAL = 1200
const MAX_POLL_ATTEMPTS = 100

// 维护缓存任务列表的筛选、分页和操作状态
const state = reactive({
  loading: false,
  keyword: '',
  updateType: 'all',
  syncRate: 'all',
  page: 1,
  pageSize: 20,
  total: 0,
  records: [] as DatasetSyncTask[],
  actionKey: ''
})

const pollingTimers = new Map<string | number, number>()

// 缓存任务表格分页配置
const paginationConfig = reactive({
  currentPage: state.page,
  pageSize: state.pageSize,
  pageSizes: [20, 50, 100],
  pagerCount: 7,
  layout: 'total, prev, pager, next, sizes, jumper',
  total: state.total
})

// 将筛选状态转换为接口查询参数
const requestParams = computed(() => ({
  keyword: state.keyword || undefined,
  updateType: state.updateType === 'all' ? undefined : state.updateType,
  syncRate: state.syncRate === 'all' ? undefined : state.syncRate
}))

// 格式化任务时间
const formatTime = (time?: number) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-')
// 格式化可为空的统计数值
const formatCount = (count?: number) => (count === null || count === undefined ? '-' : count)
// 转换更新类型文案
const updateTypeLabel = (type?: string) => (type === 'add_scope' ? '增量' : '全量')
// 转换同步频率文案
const syncRateLabel = (rate?: string) => {
  if (rate === 'SIMPLE_CRON' || rate === 'CRON') return '定时'
  return '手动'
}
// 根据任务状态选择标签类型
const statusTagType = (task: DatasetSyncTask) => {
  const status = cacheTaskStatusText(task)
  if (status === '缓存可用') return 'success'
  if (status === '更新中') return 'warning'
  if (status === '更新失败') return 'danger'
  return 'info'
}
// 判断指定行动按钮是否处于加载态
const actionLoading = (row: DatasetSyncTask, action: string) =>
  state.actionKey === `${action}-${row.datasetGroupId}`

// 按数据集 ID 查找当前列表记录
const findRecord = (datasetGroupId?: string | number) =>
  state.records.find(record => String(record.datasetGroupId) === String(datasetGroupId))

// 合并更新当前列表中的指定任务记录
const updateRecord = (datasetGroupId: string | number, patch: Partial<DatasetSyncTask>) => {
  const record = findRecord(datasetGroupId)
  if (record) {
    Object.assign(record, patch)
  }
}

// 加载缓存任务分页数据
const loadTasks = async ({ showLoading = true } = {}) => {
  if (showLoading) {
    state.loading = true
  }
  try {
    const data = await datasetSyncTaskPage(state.page, state.pageSize, requestParams.value)
    state.records = data?.records || []
    state.total = Number(data?.total || 0)
    paginationConfig.currentPage = state.page
    paginationConfig.pageSize = state.pageSize
    paginationConfig.total = state.total
  } finally {
    if (showLoading) {
      state.loading = false
    }
  }
}

// 停止指定任务的轮询
const stopPolling = (datasetGroupId: string | number) => {
  const timer = pollingTimers.get(datasetGroupId)
  if (timer) {
    window.clearTimeout(timer)
    pollingTimers.delete(datasetGroupId)
  }
}

// 安排指定任务的状态轮询
const schedulePolling = (datasetGroupId: string | number, attempts = 0) => {
  stopPolling(datasetGroupId)
  if (attempts >= MAX_POLL_ATTEMPTS) {
    return
  }
  const timer = window.setTimeout(async () => {
    const nextAttempts = attempts + 1
    try {
      await loadTasks({ showLoading: false })
      const task = findRecord(datasetGroupId)
      if (shouldContinueCacheTaskPolling({ task, attempts: nextAttempts })) {
        schedulePolling(datasetGroupId, nextAttempts)
      } else {
        pollingTimers.delete(datasetGroupId)
      }
    } catch (e) {
      const result = resolveCacheTaskPollingError({
        attempts: nextAttempts,
        maxAttempts: MAX_POLL_ATTEMPTS
      })
      if (result.shouldRetry) {
        schedulePolling(datasetGroupId, nextAttempts)
      } else {
        pollingTimers.delete(datasetGroupId)
        ElMessage.warning(result.message)
      }
    }
  }, POLL_INTERVAL)
  pollingTimers.set(datasetGroupId, timer)
}

// 按当前筛选条件重新查询第一页
const search = () => {
  state.page = 1
  loadTasks()
}

// 重置筛选条件并重新查询
const resetFilters = () => {
  state.keyword = ''
  state.updateType = 'all'
  state.syncRate = 'all'
  search()
}

// 切换分页页码
const pageChange = (page: number) => {
  state.page = page
  paginationConfig.currentPage = page
  loadTasks()
}

// 切换分页大小
const sizeChange = (pageSize: number) => {
  state.pageSize = pageSize
  state.page = 1
  paginationConfig.pageSize = pageSize
  paginationConfig.currentPage = 1
  loadTasks()
}

// 执行缓存任务操作并按需启动轮询
const runAction = async (
  row: DatasetSyncTask,
  action: 'execute' | 'pause' | 'resume' | 'retry',
  successMessage: string
) => {
  if (!row.datasetGroupId) return
  stopPolling(row.datasetGroupId)
  state.actionKey = `${action}-${row.datasetGroupId}`
  try {
    if (action === 'execute') {
      await executeDatasetSync(row.datasetGroupId)
      updateRecord(row.datasetGroupId, cacheTaskRunningSnapshot(row))
      schedulePolling(row.datasetGroupId)
    } else if (action === 'pause') {
      await pauseDatasetSync(row.datasetGroupId)
      await loadTasks()
    } else if (action === 'resume') {
      await resumeDatasetSync(row.datasetGroupId)
      await loadTasks()
    } else {
      await retryDatasetSync(row.datasetGroupId)
      updateRecord(row.datasetGroupId, cacheTaskRunningSnapshot(row))
      schedulePolling(row.datasetGroupId)
    }
    ElMessage.success(successMessage)
  } finally {
    state.actionKey = ''
  }
}

onMounted(loadTasks)
onBeforeUnmount(() => {
  pollingTimers.forEach(timer => window.clearTimeout(timer))
  pollingTimers.clear()
})
</script>

<template>
  <div class="cache-task-manage">
    <div class="cache-task-content">
      <div class="cache-task-table-info">
        <div class="search-operate">
          <el-input
            v-model.trim="state.keyword"
            clearable
            class="cache-task-search"
            placeholder="搜索数据集名称或 ID"
            @clear="search"
            @keyup.enter="search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="state.updateType" class="cache-task-filter" @change="search">
            <el-option label="全部更新方式" value="all" />
            <el-option label="全量" value="all_scope" />
            <el-option label="增量" value="add_scope" />
          </el-select>
          <el-select v-model="state.syncRate" class="cache-task-filter" @change="search">
            <el-option label="全部频率" value="all" />
            <el-option label="手动" value="RIGHTNOW" />
            <el-option label="定时" value="SIMPLE_CRON" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadTasks">刷新</el-button>
        </div>

        <div class="info-table">
          <grid-table
            class="cache-task-grid-table"
            :pagination="paginationConfig"
            :table-data="state.records"
            :is-search="!!state.keyword"
            :data-loading="state.loading"
            :row-key="row => row.datasetGroupId"
            @size-change="sizeChange"
            @current-change="pageChange"
          >
            <el-table-column label="数据集" min-width="260">
              <template #default="{ row }">
                <div class="dataset-cell">
                  <span class="dataset-name">{{ row.datasetName || row.name || '-' }}</span>
                  <span class="dataset-id">{{ row.datasetGroupId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row)" effect="plain">
                  {{ cacheTaskStatusText(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新方式" width="100">
              <template #default="{ row }">{{ updateTypeLabel(row.updateType) }}</template>
            </el-table-column>
            <el-table-column label="频率" width="100">
              <template #default="{ row }">{{ syncRateLabel(row.syncRate) }}</template>
            </el-table-column>
            <el-table-column label="最近更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.lastExecTime) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="110">
              <template #default="{ row }">{{
                formatCacheTaskDuration(row.durationMillis)
              }}</template>
            </el-table-column>
            <el-table-column label="源数据行数" width="120">
              <template #default="{ row }">{{ formatCount(row.lastSourceRowCount) }}</template>
            </el-table-column>
            <el-table-column label="缓存行数" width="110">
              <template #default="{ row }">{{ formatCount(row.lastCacheRowCount) }}</template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ cacheTaskFailureReason(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  :disabled="!cacheTaskActions(row).canExecute"
                  :loading="actionLoading(row, 'execute')"
                  @click="runAction(row, 'execute', '缓存更新已提交')"
                >
                  立即更新
                </el-button>
                <el-button
                  text
                  type="primary"
                  :disabled="!cacheTaskActions(row).canPause"
                  :loading="actionLoading(row, 'pause')"
                  @click="runAction(row, 'pause', '已暂停')"
                >
                  暂停
                </el-button>
                <el-button
                  text
                  type="primary"
                  :disabled="!cacheTaskActions(row).canResume"
                  :loading="actionLoading(row, 'resume')"
                  @click="runAction(row, 'resume', '已恢复')"
                >
                  恢复
                </el-button>
                <el-button
                  text
                  type="primary"
                  :disabled="!cacheTaskActions(row).canRetry"
                  :loading="actionLoading(row, 'retry')"
                  @click="runAction(row, 'retry', '重试任务已提交')"
                >
                  重试
                </el-button>
              </template>
            </el-table-column>
          </grid-table>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.cache-task-manage {
  width: 100%;
  height: 100%;
  padding: 24px;
  background: #f5f6f8;
  box-sizing: border-box;
}

.cache-task-content {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.cache-task-table-info {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  padding: 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  box-sizing: border-box;
}

.search-operate {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.cache-task-search {
  width: 260px;
}

.cache-task-filter {
  width: 150px;
}

.info-table {
  flex: 1;
  min-height: 0;
}

.dataset-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.dataset-name {
  overflow: hidden;
  color: #1f2937;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-id {
  color: #909399;
  font-size: 12px;
}

:deep(.ed-button + .ed-button) {
  margin-left: 8px;
}
</style>
