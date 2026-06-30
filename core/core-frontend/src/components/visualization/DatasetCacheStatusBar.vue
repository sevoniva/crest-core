<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { datasetSyncDependencies, type DatasetSyncTask } from '@/api/dataset'
import { cacheTaskStatusText } from '@/views/visualized/data/cache-task/cacheTaskPolicy.mjs'
import { useUserStoreWithOut } from '@/store/modules/user'

const userStore = useUserStoreWithOut()

// 接收可视化资源和刷新标识
const props = defineProps({
  visualizationId: {
    type: [String, Number],
    default: ''
  },
  reloadKey: {
    type: [String, Number],
    default: ''
  }
})

// 缓存依赖加载状态
const loading = ref(false)
// 当前可视化资源关联的数据集同步任务
const tasks = ref<DatasetSyncTask[]>([])

// 判断当前用户是否可查看缓存状态
const canView = computed(() => userStore.getAdmin)
// 弹层内优先展示的数据集任务
const visibleTasks = computed(() => tasks.value.slice(0, 4))
// 未展示的数据集任务数量
const remainingCount = computed(() => Math.max(tasks.value.length - visibleTasks.value.length, 0))
// 格式化任务最近执行时间
const formatTime = (time?: number) => (time ? dayjs(time).format('MM-DD HH:mm') : '-')
// 计算缓存状态触发器样式
const triggerClass = computed(() => {
  if (tasks.value.some(task => cacheTaskStatusText(task) === '更新失败')) return 'status-error'
  if (tasks.value.some(task => cacheTaskStatusText(task) === '更新中')) return 'status-running'
  if (tasks.value.some(task => cacheTaskStatusText(task) !== '缓存可用')) return 'status-pending'
  return 'status-ready'
})
// 压缩展示单个任务状态文本
const compactStatusText = (task: DatasetSyncTask) => {
  const status = cacheTaskStatusText(task)
  if (status === '缓存可用') return '可用'
  if (status === '更新失败') return '失败'
  return status
}
// 计算单个任务状态样式
const statusClass = (task: DatasetSyncTask) => {
  const status = cacheTaskStatusText(task)
  if (status === '缓存可用') return 'status-ready'
  if (status === '更新中') return 'status-running'
  if (status === '更新失败') return 'status-error'
  return 'status-pending'
}

// 加载可视化资源的数据集缓存依赖
const loadDependencies = async () => {
  if (!canView.value || !props.visualizationId) {
    tasks.value = []
    return
  }
  loading.value = true
  try {
    tasks.value = await datasetSyncDependencies(props.visualizationId)
  } catch {
    tasks.value = []
  } finally {
    loading.value = false
  }
}

// 监听资源或刷新标识变化并重新加载依赖
watch(() => [props.visualizationId, props.reloadKey, canView.value], loadDependencies, {
  immediate: true
})
</script>

<template>
  <el-popover
    v-if="canView && tasks.length"
    width="360"
    trigger="hover"
    placement="bottom-end"
    popper-class="dataset-cache-status-popover"
  >
    <template #reference>
      <button
        type="button"
        class="dataset-cache-status-trigger"
        :class="triggerClass"
        :disabled="loading"
      >
        <span class="cache-status-dot"></span>
        <span>缓存</span>
      </button>
    </template>
    <div class="cache-popover">
      <div class="cache-popover-title">缓存状态</div>
      <div
        v-for="task in visibleTasks"
        :key="task.datasetGroupId"
        class="cache-status-row"
        :class="statusClass(task)"
      >
        <span class="cache-status-dot"></span>
        <span class="cache-dataset-name">{{
          task.datasetName || task.name || task.datasetGroupId
        }}</span>
        <span class="cache-status-text">{{ compactStatusText(task) }}</span>
        <span class="cache-time">{{ formatTime(task.lastExecTime) }}</span>
      </div>
      <div v-if="remainingCount" class="cache-more">另有 {{ remainingCount }} 个数据集</div>
    </div>
  </el-popover>
</template>

<style scoped lang="less">
.dataset-cache-status-trigger {
  display: inline-flex;
  height: 32px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  cursor: pointer;
  font-size: 12px;
  box-sizing: border-box;

  &:hover {
    border-color: #c0c4cc;
    color: #303133;
  }

  &:disabled {
    cursor: default;
    opacity: 0.7;
  }
}

.cache-popover {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cache-popover-title {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.cache-status-row {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto auto;
  min-height: 28px;
  align-items: center;
  column-gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  line-height: 18px;
}

.cache-status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--cache-status-color, #909399);
}

.cache-dataset-name {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-status-text {
  color: var(--cache-status-color, #606266);
  font-weight: 500;
}

.cache-time {
  color: #909399;
}

.status-ready {
  --cache-status-color: #2f7d32;
}

.status-running {
  --cache-status-color: #b36b00;
}

.status-error {
  --cache-status-color: #cf1322;
}

.status-pending {
  --cache-status-color: #909399;
}

.cache-more {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>
