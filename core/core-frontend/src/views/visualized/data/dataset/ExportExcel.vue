<script lang="ts" setup>
import dvPreviewDownload from '@/assets/svg/icon_download_outlined.svg'
import DeleteIcon from '@/assets/svg/delete.svg'
import icon_fileExcel_colorful from '@/assets/svg/icon_file-excel_colorful.svg'
import icon_refresh_outlined from '@/assets/svg/icon_refresh_outlined.svg'
import { ref, h, onUnmounted, computed, reactive } from 'vue'
import { EmptyBackground } from '@/components/empty-background'
import { ElButton, ElMessage, ElMessageBox, ElTabPane, ElTabs } from 'element-plus-secondary'
import { RefreshLeft } from '@element-plus/icons-vue'
import {
  exportTasks,
  exportRetry,
  exportDelete,
  exportDeleteAll,
  exportDeletePost,
  exportTasksRecords,
  generateDownloadUri
} from '@/api/dataset'
import { useI18n } from '@/hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
import Icon from '@/components/icon-custom/src/Icon.vue'
import { useCache } from '@/hooks/web/useCache'
import { useLinkStoreWithOut } from '@/store/modules/link'
import { useAppStoreWithOut } from '@/store/modules/app'
import { createExportNoticeQueue, formatExportNoticeMessage } from './exportNoticeQueue.mjs'

const { t } = useI18n()
type ExportTask = {
  id: string
  exportStatus?: string
  exportFromName?: string
  [key: string]: any
}
// 导出中心分页状态
const state = reactive({
  paginationConfig: {
    currentPage: 1,
    pageSize: 10,
    total: 0
  }
})
// 当前分页下的导出任务列表
const tableData = ref<ExportTask[]>([])
// 抽屉列表加载状态
const drawerLoading = ref(false)
// 导出中心抽屉显隐状态
const drawer = ref(false)
// 失败原因弹窗显隐状态
const msgDialogVisible = ref(false)
// 当前失败原因文本
const msg = ref('')
// 当前激活的任务状态标签
const activeName = ref('ALL')
// 表格多选的导出任务
const multipleSelection = ref<ExportTask[]>([])
// 空状态描述文案
const description = ref(t('data_set.no_tasks_yet'))
// 导出任务状态标签列表及数量文案
const tabList = ref([
  {
    label: t('data_set.exporting') + '(0)',
    name: 'IN_PROGRESS'
  },
  {
    label: t('data_set.success') + '(0)',
    name: 'SUCCESS'
  },
  {
    label: t('data_set.fail') + '(0)',
    name: 'FAILED'
  },
  {
    label: t('data_set.waiting') + '(0)',
    name: 'PENDING'
  },
  {
    label: t('data_set.all') + '(0)',
    name: 'ALL'
  }
])
let timer: ReturnType<typeof setInterval> | undefined
// 关闭导出中心并停止轮询
const handleClose = () => {
  drawer.value = false
  clearInterval(timer)
}
const { wsCache } = useCache()
const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
const desktop = wsCache.get('app.desktop')

onUnmounted(() => {
  // 抽屉组件卸载时同时释放轮询和通知队列，避免跨页面重复提醒。
  clearInterval(timer)
  exportNoticeQueue.clear()
})
// 切换任务状态标签并刷新列表和各状态数量
const handleClick = (tab?: { paneName?: string | number }, _ev?: Event) => {
  if (tab) {
    activeName.value = String(tab.paneName)
  }
  if (activeName.value === 'ALL') {
    description.value = t('data_export.no_file')
  } else if (activeName.value === 'FAILED') {
    description.value = t('data_export.no_failed_file')
  } else {
    description.value = t('data_export.no_task')
  }
  drawerLoading.value = true
  exportTasksRecords().then(res => {
    // 先刷新状态数量，再加载当前分页数据，保证标签计数和表格内容同步。
    tabList.value.forEach(item => {
      if (item.name === 'ALL') {
        item.label = t('data_set.all') + '(' + res.data.ALL + ')'
      }
      if (item.name === 'IN_PROGRESS') {
        item.label = t('data_set.exporting') + '(' + res.data.IN_PROGRESS + ')'
      }
      if (item.name === 'SUCCESS') {
        item.label = t('data_set.success') + '(' + res.data.SUCCESS + ')'
      }
      if (item.name === 'FAILED') {
        item.label = t('data_set.fail') + '(' + res.data.FAILED + ')'
      }
      if (item.name === 'PENDING') {
        item.label = t('data_set.waiting') + '(' + res.data.PENDING + ')'
      }
    })
  })
  exportTasks(state.paginationConfig.currentPage, state.paginationConfig.pageSize, activeName.value)
    .then(res => {
      state.paginationConfig.total = res.data.total
      tableData.value = res.data.records
    })
    .finally(() => {
      drawerLoading.value = false
    })
}

// 打开导出中心并启动任务状态轮询
const init = (params?: { activeName?: string }) => {
  clearInterval(timer)
  drawer.value = true
  if (params && params.activeName !== undefined) {
    activeName.value = params.activeName
  }
  handleClick()
  timer = setInterval(() => {
    // 只有全部和进行中标签需要轮询，其余状态通过用户切换时刷新。
    if (['IN_PROGRESS', 'ALL'].includes(activeName.value)) {
      exportTasksRecords().then(res => {
        tabList.value.forEach(item => {
          if (item.name === 'ALL') {
            item.label = t('data_set.all') + '(' + res.data.ALL + ')'
          }
          if (item.name === 'IN_PROGRESS') {
            item.label = t('data_set.exporting') + '(' + res.data.IN_PROGRESS + ')'
          }
          if (item.name === 'SUCCESS') {
            item.label = t('data_set.success') + '(' + res.data.SUCCESS + ')'
          }
          if (item.name === 'FAILED') {
            item.label = t('data_set.fail') + '(' + res.data.FAILED + ')'
          }
          if (item.name === 'PENDING') {
            item.label = t('data_set.waiting') + '(' + res.data.PENDING + ')'
          }
        })
      })
      exportTasks(
        state.paginationConfig.currentPage,
        state.paginationConfig.pageSize,
        activeName.value
      ).then(res => {
        state.paginationConfig.total = res.data.total
        tableData.value = res.data.records
      })
    }
  }, 5000)
}
const linkStore = useLinkStoreWithOut()
const appStore = useAppStoreWithOut()
// 当前应用是否运行在嵌入式集成模式
const isCrestBi = computed(() => appStore.getIsCrestBi)
const exportNoticeQueue = createExportNoticeQueue({
  emit: notice => {
    // Web 模式下把短时间内的导出结果聚合成一条提示，减少多任务导出时的消息刷屏。
    const noticeText = formatExportNoticeMessage(
      notice,
      t('data_set.successful_go_to'),
      t('data_set.failed_go_to'),
      t('data_set.export_tasks_successful_go_to', { count: notice.count }),
      t('data_set.export_tasks_failed_go_to', { count: notice.count })
    )
    openMessageLoading(
      noticeText,
      notice.status === 'FAILED' ? 'error' : 'success',
      notice.status === 'FAILED' ? callbackExportError : callbackExportSuc
    )
  }
})

// 接收后端导出任务通知，并在普通 Web 模式下聚合弹出提示
const taskExportTopicCall = (task: string) => {
  if (!linkStore.getLinkToken && !isCrestBi.value && !appStore.getIsIframe) {
    let exportTask: ExportTask
    try {
      exportTask = JSON.parse(task)
    } catch {
      // 非标准任务通知直接忽略，避免影响正常导出中心轮询。
      return
    }
    exportNoticeQueue.push(exportTask)
  }
}

// 弹出导出任务通知，并提供跳转导出中心的操作入口
const openMessageLoading = (text: string, type = 'success', cb?: () => void) => {
  // 通知类型支持成功、失败和加载态
  const customClass = `crest-message-${type || 'success'} crest-message-export`
  const messageText = String(text)
  ElMessage({
    message: h('p', null, [
      h(
        'span',
        {
          title: messageText,
          class: 'ellipsis m50-export'
        },
        messageText
      ),
      h(
        ElButton,
        {
          text: true,
          size: 'small',
          class: 'btn-text',
          onClick: () => {
            cb?.()
          }
        },
        t('data_export.export_center')
      )
    ]),
    icon: type === 'loading' ? h(RefreshLeft) : '',
    type,
    showClose: true,
    customClass
  } as any)
}

// 跳转导出中心失败任务标签
const callbackExportError = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'FAILED' })
}

// 跳转导出中心成功任务标签
const callbackExportSuc = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'SUCCESS' })
}

// 下载当前成功标签下的全部任务，或仅下载已选择任务
const downLoadAll = () => {
  if (multipleSelection.value.length === 0) {
    // 未选择任务时下载当前成功列表全部文件。
    tableData.value.forEach(item => {
      generateDownloadUri(item.id).then(uri => {
        window.open(PATH_URL + uri)
      })
    })
    return
  }
  multipleSelection.value.map(ele => {
    // 已选择任务时按选择顺序逐个生成下载地址。
    generateDownloadUri(ele.id).then(uri => {
      window.open(PATH_URL + uri)
    })
  })
}
// 展示导出失败原因
const showMsg = item => {
  msg.value = ''
  msg.value = item.msg
  msgDialogVisible.value = true
}
// 将后端时间戳格式化为本地可读时间
const timestampFormatDate = value => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}
import { PATH_URL } from '@/config/axios/service'
import GridTable from '../../../../components/grid-table/src/GridTable.vue'
// 下载单个导出任务文件
const downloadClick = item => {
  generateDownloadUri(item.id).then(uri => {
    window.open(PATH_URL + uri, openType)
  })
}

// 重新提交失败的导出任务
const retry = item => {
  exportRetry(item.id).then(() => {
    handleClick()
  })
}

// 删除单个导出任务
const deleteField = item => {
  ElMessageBox.confirm(t('data_export.sure_del'), {
    confirmButtonType: 'danger',
    type: 'warning',
    autofocus: false,
    showClose: false
  })
    .then(() => {
      exportDelete(item.id).then(() => {
        ElMessage.success(t('commons.delete_success'))
        handleClick()
      })
    })
    .catch(() => {
      // 用户取消删除时不做处理
    })
}

// 同步表格多选结果
const handleSelectionChange = (val: ExportTask[]) => {
  multipleSelection.value = val
}

// 切换分页页码并刷新任务列表
const pageChange = index => {
  if (typeof index !== 'number') {
    return
  }
  state.paginationConfig.currentPage = index
  handleClick()
}
// 切换分页大小并回到第一页
const sizeChange = size => {
  state.paginationConfig.currentPage = 1
  state.paginationConfig.pageSize = size
  handleClick()
}

// 删除当前标签下全部任务，或删除已选择的任务
const delAll = () => {
  if (multipleSelection.value.length === 0) {
    // 未选择任务时按当前状态标签批量清理。
    ElMessageBox.confirm(t('data_export.sure_del_all'), {
      confirmButtonType: 'danger',
      type: 'warning',
      autofocus: false,
      showClose: false
    })
      .then(() => {
        exportDeleteAll(
          activeName.value,
          multipleSelection.value.map(ele => ele.id)
        ).then(() => {
          ElMessage.success(t('commons.delete_success'))
          handleClick()
        })
      })
      .catch(() => {
        // 用户取消删除时不做处理
      })
    return
  }

  ElMessageBox.confirm(t('data_export.sure_del'), {
    // 已选择任务时仅删除选中的任务 ID 列表。
    confirmButtonType: 'danger',
    type: 'warning',
    autofocus: false,
    showClose: false
  })
    .then(() => {
      exportDeletePost(multipleSelection.value.map(ele => ele.id)).then(() => {
        ElMessage.success(t('commons.delete_success'))
        handleClick()
      })
    })
    .catch(() => {
      // 用户取消删除时不做处理
    })
}

useEmitt({ name: 'task-export-topic-call', callback: taskExportTopicCall })

defineExpose({
  // 父级通过事件总线或 ref 打开导出中心，并可指定默认激活标签。
  init
})
</script>

<template>
  <el-drawer
    modal-class="crest-export-excel"
    class="export-center-drawer"
    :title="$t('data_export.export_center')"
    v-model="drawer"
    direction="rtl"
    size="min(1120px, calc(100vw - 72px))"
    append-to-body
    :before-close="handleClose"
  >
    <!-- 导出中心抽屉聚合任务状态、批量操作、下载和失败原因查看。 -->
    <div class="export-center-content" v-loading="drawerLoading">
      <el-tabs v-model="activeName" class="export-tabs" @tab-click="handleClick">
        <el-tab-pane v-for="tab in tabList" :key="tab.name" :label="tab.label" :name="tab.name" />
      </el-tabs>
      <div class="export-toolbar">
        <!-- 工具栏按钮根据任务状态和选择数量切换文案与操作范围。 -->
        <el-button
          v-if="activeName === 'SUCCESS' && multipleSelection.length === 0"
          class="export-action"
          secondary
          @click="downLoadAll"
        >
          <template #icon>
            <Icon name="dv-preview-download"><dvPreviewDownload class="svg-icon" /></Icon>
          </template>
          {{ $t('data_export.download_all') }}
        </el-button>
        <el-button
          v-if="activeName === 'SUCCESS' && multipleSelection.length !== 0"
          class="export-action"
          secondary
          @click="downLoadAll"
        >
          <template #icon>
            <Icon name="dv-preview-download"><dvPreviewDownload class="svg-icon" /></Icon>
          </template>
          {{ $t('data_export.download') }}
        </el-button>
        <el-button
          v-if="multipleSelection.length === 0"
          class="export-action is-danger"
          secondary
          @click="delAll"
          ><template #icon>
            <Icon name="delete"><DeleteIcon class="svg-icon" /></Icon> </template
          >{{ $t('data_export.del_all') }}
        </el-button>
        <el-button
          v-if="multipleSelection.length !== 0"
          class="export-action is-danger"
          secondary
          @click="delAll"
          ><template #icon>
            <Icon name="delete"><DeleteIcon class="svg-icon" /></Icon> </template
          >{{ $t('commons.delete') }}
        </el-button>
      </div>
      <div class="table-container" :class="!tableData.length && 'hidden-bottom'">
        <GridTable
          ref="multipleTable"
          :pagination="state.paginationConfig"
          :table-data="tableData"
          class="popper-max-width export-grid"
          @current-change="pageChange"
          @size-change="sizeChange"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column prop="fileName" :label="$t('driver.file_name')" min-width="260">
            <template #default="scope">
              <div class="name-excel">
                <el-icon style="font-size: 24px">
                  <Icon name="icon_file-excel_colorful"
                    ><icon_fileExcel_colorful class="svg-icon"
                  /></Icon>
                </el-icon>
                <div class="name-content">
                  <div class="fileName">{{ scope.row.fileName }}</div>
                  <div
                    v-if="scope.row.exportStatus === 'FAILED'"
                    class="task-meta status-failed"
                    @click="showMsg(scope.row)"
                  >
                    {{ $t('data_export.export_failed') }}
                  </div>
                  <div v-if="scope.row.exportStatus === 'SUCCESS'" class="task-meta status-success">
                    {{ scope.row.fileSize }}{{ scope.row.fileSizeUnit }}
                  </div>
                </div>
              </div>
              <div v-if="scope.row.exportStatus === 'FAILED'" class="red-line" />
              <el-progress
                v-if="scope.row.exportStatus === 'IN_PROGRESS'"
                :percentage="+scope.row.exportProgress"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="exportFromName"
            :label="$t('data_export.export_obj')"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column prop="exportTime" width="156" :label="$t('data_export.export_time')">
            <template #default="scope">
              <span>{{ timestampFormatDate(scope.row.exportTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="exportFromType" width="92" :label="$t('data_export.export_from')">
            <template #default="scope">
              <span v-if="scope.row.exportFromType === 'dataset'">{{
                t('data_set.data_set')
              }}</span>
              <span v-if="scope.row.exportFromType === 'chart'">{{ t('data_set.view') }}</span>
              <span v-if="scope.row.exportFromType === 'data_filling'">{{
                t('data_fill.data_fill')
              }}</span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="!desktop"
            prop="orgName"
            :label="t('data_set.organization')"
            width="110"
            show-overflow-tooltip
          />
          <el-table-column prop="operate" width="132" :label="$t('commons.operating')">
            <template #default="scope">
              <div class="export-row-actions">
                <el-tooltip effect="dark" :content="t('data_set.download')" placement="top">
                  <el-button
                    v-if="scope.row.exportStatus === 'SUCCESS'"
                    class="row-icon-btn"
                    text
                    @click="downloadClick(scope.row)"
                  >
                    <template #icon>
                      <el-icon>
                        <Icon name="dv-preview-download"
                          ><dvPreviewDownload class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </template>
                  </el-button>
                </el-tooltip>

                <el-tooltip effect="dark" :content="t('data_set.re_export')" placement="top">
                  <el-button
                    v-if="scope.row.exportStatus === 'FAILED'"
                    class="row-icon-btn"
                    text
                    @click="retry(scope.row)"
                  >
                    <template #icon>
                      <Icon name="icon_refresh_outlined"
                        ><icon_refresh_outlined class="svg-icon"
                      /></Icon>
                    </template>
                  </el-button>
                </el-tooltip>

                <el-tooltip effect="dark" :content="t('data_set.delete')" placement="top">
                  <el-button class="row-icon-btn is-danger" text @click="deleteField(scope.row)">
                    <template #icon>
                      <Icon name="delete"><DeleteIcon class="svg-icon" /></Icon>
                    </template>
                  </el-button>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <empty-background :description="description" img-type="noneWhite" />
          </template>
        </GridTable>
      </div>
    </div>
  </el-drawer>

  <el-dialog :title="t('data_set.reason_for_failure')" v-model="msgDialogVisible" width="30%">
    <span>{{ msg }}</span>
    <template v-slot:footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="msgDialogVisible = false">{{
          t('data_set.closure')
        }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="less">
.crest-export-excel {
  // 导出中心使用独立 CSS 变量，避免与数据集页面其它抽屉样式相互影响。
  --export-ink: #0f172a;
  --export-ink-2: #334155;
  --export-muted: #64748b;
  --export-subtle: #94a3b8;
  --export-line: #e2e8f0;
  --export-soft: #f8fafc;
  --export-primary: #3b82f6;
  --export-danger: #ef4444;
  --export-success: #1fb6a6;

  background: rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(2px);

  .export-center-drawer {
    max-width: calc(100vw - 24px);
    background: var(--export-soft);
    border-left: 1px solid var(--export-line);
    box-shadow: -16px 0 40px rgba(15, 23, 42, 0.12);
  }

  .ed-drawer {
    border-radius: 18px 0 0 18px;
  }

  .ed-drawer__body {
    display: flex;
    flex-direction: column;
    padding: 0 24px 24px;
  }

  .ed-drawer__header {
    align-items: center;
    min-height: 68px;
    padding: 0 24px;
    margin-bottom: 0;
    color: var(--export-ink);
    background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
    border-bottom: none;

    > span {
      font-size: 18px;
      font-weight: 700;
      line-height: 24px;
      letter-spacing: 0;
    }
  }

  .export-center-content {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    height: 100%;
  }

  .export-tabs {
    margin-top: 4px;

    .ed-tabs__header {
      margin-bottom: 16px;
      border-bottom: 1px solid var(--export-line);
    }

    .ed-tabs__nav-wrap::after {
      display: none;
    }

    .ed-tabs__item {
      height: 42px;
      padding: 0 18px;
      color: var(--export-muted);
      font-weight: 600;
    }

    .ed-tabs__item.is-active {
      color: var(--export-primary);
    }

    .ed-tabs__active-bar {
      height: 3px;
      background: var(--export-primary);
      border-radius: 99px;
    }
  }

  .export-toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    min-height: 42px;
    margin-bottom: 14px;

    .ed-button + .ed-button {
      margin-left: 0;
    }

    .export-action {
      height: 36px;
      padding: 0 14px;
      color: var(--export-ink-2);
      font-weight: 600;
      background: #ffffff;
      border: 1px solid var(--export-line);
      border-radius: 10px;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

      &:hover {
        color: var(--export-primary);
        border-color: rgba(59, 130, 246, 0.42);
        box-shadow: 0 8px 18px rgba(59, 130, 246, 0.12);
      }

      &.is-danger:hover {
        color: var(--export-danger);
        border-color: rgba(239, 68, 68, 0.38);
        box-shadow: 0 8px 18px rgba(239, 68, 68, 0.1);
      }
    }
  }

  .table-container {
    // 表格容器占满抽屉剩余高度，分页固定在底部区域内展示。
    flex: 1;
    min-height: 0;
    height: auto;
    overflow: hidden;
    background: #ffffff;
    border: 1px solid var(--export-line);
    border-radius: 14px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

    .export-grid {
      height: 100%;
      min-width: 0;
      min-height: 0;
      overflow: hidden;
    }

    .export-grid > .ed-table {
      flex: 1 1 auto;
      min-height: 0;
      height: auto !important;
    }

    .export-grid .pagination-cont {
      flex: 0 0 auto;
      align-items: center;
      justify-content: flex-end;
      min-width: 0;
      min-height: 44px;
      padding: 10px 16px 0;
      margin-top: 0;
      overflow: visible;
      border-top: 1px solid #eef2f7;
    }

    .export-grid .ed-pagination {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      justify-content: flex-end;
      max-width: 100%;
      color: var(--export-ink-2);
    }

    .export-grid .ed-pagination__sizes,
    .export-grid .ed-pagination__jump {
      margin-left: 6px;
    }

    .export-grid .ed-pagination__goto,
    .export-grid .ed-pagination__classifier,
    .export-grid .ed-pagination__total {
      color: var(--export-ink-2);
      white-space: nowrap;
    }

    .export-grid .ed-pagination__editor {
      width: 58px;
    }

    .ed-table {
      color: var(--export-ink-2);
      --ed-table-header-bg-color: #ffffff;
      --ed-table-row-hover-bg-color: #fafbfc;
      --ed-table-border-color: #eef2f7;
    }

    .ed-table__header-wrapper th.ed-table__cell {
      height: 46px;
      color: #8aa0bd;
      font-size: 12px;
      font-weight: 700;
      background: #ffffff;
      border-bottom: 1px solid var(--export-line);
    }

    .ed-table__body-wrapper .ed-table__row {
      height: 64px;
    }

    .ed-table .cell {
      padding-right: 14px;
      padding-left: 14px;
    }

    &.hidden-bottom {
      .ed-table::before {
        display: none;
      }
    }

    .name-excel {
      display: flex;
      align-items: center;

      .ed-icon {
        flex: 0 0 auto;
        width: 34px;
        height: 34px;
        margin-right: 10px;
        background: #eef6ff;
        border: 1px solid #dbeafe;
        border-radius: 10px;
      }

      .name-content {
        max-width: 260px;
        min-width: 0;
        .fileName {
          overflow: hidden;
          white-space: nowrap;
          text-overflow: ellipsis;
          width: 100%;
          font-size: 14px;
          font-weight: 700;
          line-height: 22px;
          color: var(--export-ink);
        }

        .task-meta {
          display: inline-flex;
          align-items: center;
          max-width: 100%;
          margin-top: 2px;
          overflow: hidden;
          font-size: 12px;
          font-weight: 600;
          line-height: 20px;
          white-space: nowrap;
          text-overflow: ellipsis;
        }

        .status-failed {
          color: var(--export-danger);
          cursor: pointer;
        }

        .status-success {
          color: var(--export-muted);
          font-family: 'JetBrains Mono', monospace;
        }
      }
    }

    .ed-progress {
      margin-top: 8px;

      .ed-progress-bar__outer {
        height: 7px !important;
        background: #eaf1f8;
        border-radius: 99px;
      }

      .ed-progress-bar__inner {
        background: linear-gradient(90deg, var(--export-primary), var(--export-success));
        border-radius: 99px;
      }
    }

    .export-row-actions {
      // 行操作按钮固定尺寸并右对齐，避免下载、重试、删除组合导致列宽抖动。
      display: inline-flex;
      gap: 6px;
      align-items: center;
      justify-content: flex-end;
      width: 100%;
      min-width: 66px;
      white-space: nowrap;

      .ed-button + .ed-button {
        margin-left: 0;
      }

      .row-icon-btn {
        width: 30px;
        height: 30px;
        padding: 0;
        color: var(--export-muted);
        background: #ffffff;
        border: 1px solid var(--export-line);
        border-radius: 8px;

        &:hover {
          color: var(--export-primary);
          background: #eff6ff;
          border-color: rgba(59, 130, 246, 0.38);
        }

        &.is-danger:hover {
          color: var(--export-danger);
          background: #fff1f2;
          border-color: rgba(239, 68, 68, 0.34);
        }

        .ed-icon {
          font-size: 12px;
        }
      }
    }

    th.ed-table__cell.is-leaf {
      border-color: var(--export-line);
    }

    .red-line {
      width: 100%;
      height: 3px;
      background: var(--export-danger);
      position: absolute;
      left: 0;
      bottom: 0;
    }
  }
}

@media (max-width: 900px) {
  .crest-export-excel {
    .export-center-drawer {
      width: calc(100vw - 12px) !important;
      max-width: calc(100vw - 12px);
    }

    .ed-drawer__body {
      padding-right: 14px;
      padding-left: 14px;
    }
  }
}
</style>
