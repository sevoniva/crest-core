<script lang="ts" setup>
import userImg from '@/assets/svg/user-img.svg'
import { useI18n } from '@/hooks/web/useI18n'
import { computed } from 'vue'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { useRequestStoreWithOut } from '@/store/modules/request'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import ShortcutTable from './ShortcutTable.vue'
import { useUserStoreWithOut } from '@/store/modules/user'
import { useRouter } from 'vue-router_2'
import { useCache } from '@/hooks/web/useCache'

const userStore = useUserStoreWithOut()
const interactiveStore = interactiveStoreWithOut()
const permissionStore = usePermissionStoreWithOut()
const requestStore = useRequestStoreWithOut()
const { t } = useI18n()
// 工作台资源统计由交互状态统一提供，权限和数量都从同一份数据派生。
const busiDataMap = computed(() => interactiveStore.data)
const { wsCache } = useCache()
const router = useRouter()
// 后台内打开保持当前窗口，门户或外部入口打开新窗口，避免打断用户当前上下文。
const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
// 快捷创建基础配置只描述入口形态，权限状态在 computed 中按接口数据补齐。
const quickCreationBaseList = [
  {
    icon: '/svg/icon_dashboard.svg',
    name: 'panel',
    color: '#3B82F6',
    code: 'DASHBOARD'
  },
  {
    icon: '/svg/icon_data-visualization.svg',
    name: 'screen',
    color: '#1FB6A6',
    code: 'SCREEN'
  },
  {
    icon: '/svg/icon_dataset.svg',
    name: 'dataset',
    color: '#6E62E8',
    code: 'DATASET'
  },
  {
    icon: '/svg/icon_database.svg',
    name: 'datasource',
    color: '#F5A623',
    code: 'SOURCE'
  }
]

// 合并快捷创建入口和权限状态，模板只消费可直接渲染的数据结构。
const quickCreationList = computed(() =>
  quickCreationBaseList.map((item, index) => ({
    ...item,
    menuAuth: !!busiDataMap.value[index]?.menuAuth,
    anyManage: !!busiDataMap.value[index]?.anyManage
  }))
)

// 顶部统计只展示主要业务资源，过滤空数据避免接口未返回时出现占位卡片。
const busiCountCardList = computed(() =>
  [0, 1, 2].map(index => busiDataMap.value[index]).filter(Boolean)
)

// 点击快捷入口前先检查创建权限，避免无权限用户触发无效路由跳转。
const quickCreate = (flag: number, hasAuth: boolean) => {
  if (!hasAuth) {
    return
  }
  switch (flag) {
    case 0:
      createPanel()
      break
    case 1:
      createScreen()
      break
    case 2:
      createDataset()
      break
    case 3:
      createDatasource()
      break
    default:
      break
  }
}

// 仪表板创建沿用旧路由参数，兼容已有编辑器入口识别逻辑。
const createPanel = () => {
  const baseUrl = '#/dashboard?opt=create'
  window.open(baseUrl, openType)
}

// 数据大屏创建需要进入画布编辑器，并通过 opt=create 初始化新资源。
const createScreen = () => {
  const baseUrl = '#/dvCanvas?opt=create'
  window.open(baseUrl, openType)
}
// 数据集表单使用 router.resolve 生成完整 href，适配 hash 路由部署方式。
const createDataset = () => {
  let routeData = router.resolve({
    path: '/dataset-form'
  })
  window.open(routeData.href, openType)
}
// 数据源创建仍使用列表页参数入口，保持与后台菜单行为一致。
const createDatasource = () => {
  const baseUrl = '#/data/datasource?opt=create'
  window.open(baseUrl, openType)
}

// 统计树只以叶子资源作为趋势样本，父级目录不参与更新时间计算。
const getLeafTimes = item => {
  const stack = [...(item?.treeNodes || [])]
  const times: number[] = []
  while (stack.length) {
    const node = stack.pop()
    if (node?.leaf) {
      const time = Number(node.createTime || node.updateTime || node.lastEditTime || 0)
      if (time) {
        times.push(time)
      }
    }
    if (node?.children?.length) {
      node.children.forEach(child => stack.push(child))
    }
  }
  return times
}

// 将资源更新时间压缩为 7 个采样点，用于左侧统计卡片的迷你趋势线。
const getSparkValues = item => {
  const times = getLeafTimes(item).sort((a, b) => a - b)
  const total = Number(item?.leafNodeCount || times.length || 0)
  if (!times.length || total <= 0) {
    return new Array(7).fill(0)
  }

  const min = times[0]
  const max = times[times.length - 1]
  if (min === max) {
    // 所有资源时间相同时使用固定递增曲线，避免趋势线退化成一条直线。
    return [
      0,
      0,
      Math.ceil(total * 0.18),
      Math.ceil(total * 0.42),
      Math.ceil(total * 0.68),
      Math.ceil(total * 0.86),
      total
    ]
  }

  let cursor = 0
  return new Array(7).fill(0).map((_, index) => {
    // 每个采样点统计落在当前时间阈值内的累计资源数。
    const threshold = min + ((max - min) / 6) * index
    while (cursor < times.length && times[cursor] <= threshold) {
      cursor += 1
    }
    return cursor
  })
}

// 将趋势图数值映射到固定 46x18 视窗，保证不同卡片的折线尺度一致。
const getSparkPoints = item => {
  const values = getSparkValues(item)
  const max = Math.max(...values)
  const min = Math.min(...values)
  const range = max - min || 1
  return values
    .map((value, index) => {
      const x = (46 / 6) * index
      const y = max === min ? (max ? 9 : 15) : 16 - ((value - min) / range) * 14
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

// 最后一个点用于绘制当前累计值标记，解析失败时回落到右侧中位坐标。
const getSparkLastPoint = item => {
  const point = getSparkPoints(item).split(' ').pop() || '46,9'
  const [x, y] = point.split(',')
  return { x, y }
}
</script>

<template>
  <div class="workbranch" v-loading="requestStore.loadingMap[permissionStore.currentPath]">
    <div class="info-quick-creation">
      <div class="user-info work-card">
        <div class="profile-row">
          <el-icon class="main-color user-icon-container">
            <Icon name="user-img"><userImg class="svg-icon" /></Icon>
          </el-icon>
          <div class="info">
            <div class="name-role flex-align-center">
              <span :title="userStore.getName" class="name ellipsis">{{ userStore.getName }}</span>
            </div>
            <span v-if="userStore.getUid" class="id"> {{ `ID: ${userStore.getUid}` }} </span>
          </div>
        </div>
        <div
          class="stat-item"
          :class="{ 'crest-item-hidden': !item['menuAuth'] }"
          v-for="(item, index) in busiCountCardList"
          :key="index"
        >
          <span
            class="stat-bar"
            :style="{ backgroundColor: quickCreationList[index]?.color || '#3B82F6' }"
          />
          <span class="stat-meta">
            <span class="name">
              {{ t(`auth.${quickCreationList[index].name}`) }}
            </span>
            <span class="code">{{ quickCreationList[index]?.code }}</span>
          </span>
          <svg class="sparkline" viewBox="0 0 46 18" aria-hidden="true">
            <polyline
              :points="getSparkPoints(item)"
              fill="none"
              :stroke="quickCreationList[index]?.color || '#3B82F6'"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
              opacity="0.85"
            />
            <circle
              :cx="getSparkLastPoint(item).x"
              :cy="getSparkLastPoint(item).y"
              r="2"
              :fill="quickCreationList[index]?.color || '#3B82F6'"
            />
          </svg>
          <span class="num"> {{ item['menuAuth'] ? item['leafNodeCount'] : '*' }} </span>
        </div>
      </div>

      <div class="quick-creation work-card">
        <span class="label"> {{ t('work_branch.create_quickly') }} </span>
        <div class="item-creation">
          <div
            :key="ele.name"
            class="item"
            :class="{
              'quick-create-disabled': !ele['menuAuth'] || !ele['anyManage']
            }"
            :style="{ '--accent': ele.color }"
            v-for="(ele, index) in quickCreationList"
            @click="quickCreate(index, ele['menuAuth'] && ele['anyManage'])"
          >
            <el-tooltip
              v-if="!ele['menuAuth'] || !ele['anyManage']"
              class="box-item"
              effect="dark"
              :content="t('work_branch.permission_to_create')"
              placement="top"
            >
              <div class="empty-tooltip-container" />
            </el-tooltip>
            <span class="quick-icon" :style="{ backgroundColor: ele.color }">
              <img :src="ele.icon" :alt="t(`auth.${ele.name}`)" />
            </span>
            <span class="name">
              {{ t(`auth.${ele.name}`) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    <div class="workbranch-content">
      <el-scrollbar class="workbranch-content-scroll">
        <shortcut-table />
      </el-scrollbar>
    </div>
  </div>
</template>

<style lang="less" scoped>
.workbranch {
  /* 工作台采用左侧资源概览、右侧常用入口的双栏布局。 */
  display: grid;
  grid-template-columns: clamp(320px, 18vw, 360px) minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
  width: 100%;
  height: calc(100vh - 60px);
  min-height: 640px;
  padding: clamp(22px, 2vw, 32px) clamp(28px, 4vw, 72px);
  overflow: hidden;
  font-family: var(--crest-font-sans);
  background: #f8fafc;
  --workbranch-card-radius: 14px;

  .main-btn {
    display: inline-flex;
    height: 20px;
    padding: 0 6px;
    align-items: center;
  }

  .work-card {
    /* 卡片统一使用浅边框和轻阴影，和门户首页资源卡视觉保持一致。 */
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: var(--workbranch-card-radius);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  }

  .info-quick-creation {
    display: flex;
    flex-direction: column;
    gap: 16px;
    width: 100%;
    height: 100%;
    min-height: 0;

    .main-color {
      color: #3b82f6;
      background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
    }

    .user-info {
      /* 用户资料和资源统计放在同一张卡片内，减少首屏纵向跳动。 */
      display: flex;
      flex-direction: column;
      gap: 11px;
      padding: 22px 24px 18px;

      .profile-row {
        display: flex;
        align-items: center;
        gap: 14px;
        margin-bottom: 7px;
      }

      .user-icon-container {
        /* 头像右下角状态点仅表示当前登录态，不参与交互。 */
        position: relative;
        flex: none;
        width: 52px !important;
        height: 52px !important;

        &::after {
          position: absolute;
          right: -2px;
          bottom: -2px;
          width: 14px;
          height: 14px;
          content: '';
          background: #22c55e;
          border: 2.5px solid #ffffff;
          border-radius: 50%;
        }
      }

      .ed-icon {
        font-size: 22px;
        padding: 0;
        border-radius: 50%;
      }

      .info {
        display: flex;
        flex: 1;
        align-items: flex-start;
        flex-wrap: wrap;
        min-width: 0;

        .name-role {
          width: 100%;
          margin-bottom: 3px;
          color: #0f172a;
          font-family: var(--crest-font-sans);
          font-style: normal;

          .name {
            max-width: 210px;
            font-size: 17px;
            font-weight: 600;
            line-height: 24px;
          }
        }

        .id {
          width: 200px;
          color: #64748b;
          font-family: var(--crest-font-mono);
          font-size: 12px;
          font-weight: 400;
          line-height: 18px;
        }
      }

      .stat-item {
        /* 资源统计行保持固定密度，便于快速比较不同资源数量。 */
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 6px 0;
        font-family: var(--crest-font-sans);
        font-style: normal;

        &:first-of-type {
          padding-top: 16px;
          border-top: 1px solid #f1f5f9;
        }

        .stat-bar {
          flex: none;
          width: 7px;
          height: 24px;
          border-radius: 2px;
        }

        .stat-meta {
          display: flex;
          flex: 1;
          flex-direction: column;
          min-width: 0;
        }

        .name {
          color: #64748b;
          font-size: 12.5px;
          font-weight: 400;
          line-height: 18px;
        }

        .code {
          color: #94a3b8;
          font-family: var(--crest-font-mono);
          font-size: 10.5px;
          line-height: 16px;
        }

        .sparkline {
          flex: none;
          width: 46px;
          height: 18px;
        }

        .num {
          min-width: 34px;
          color: #0f172a;
          font-size: 22px;
          font-weight: 700;
          line-height: 22px;
          text-align: right;
          font-variant-numeric: tabular-nums;
        }
      }

      .crest-item-hidden {
        cursor: not-allowed;
      }
    }

    .quick-creation {
      /* 快捷创建区域填满左侧剩余空间，入口数量变化时仍保持顶部对齐。 */
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
      padding: 18px 22px 22px;

      .label {
        color: #334155;
        font-feature-settings: 'clig' off, 'liga' off;
        font-family: var(--crest-font-sans);
        font-size: 13.5px;
        font-style: normal;
        font-weight: 600;
        line-height: 20px;
      }

      .item-creation {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
        align-content: start;
        flex: 1;
        margin-top: 14px;

        .item {
          /* 入口卡片使用 accent 变量驱动悬浮态，保证图标色和边框反馈一致。 */
          position: relative;
          display: flex;
          align-items: center;
          gap: 11px;
          min-height: 64px;
          padding: 12px 13px;
          overflow: hidden;
          cursor: pointer;
          background: #ffffff;
          border: 1px solid #e2e8f0;
          border-radius: 10px;
          transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;

          &::after {
            position: absolute;
            inset: 0;
            pointer-events: none;
            content: '';
            background: linear-gradient(135deg, var(--accent, #3b82f6) 0%, transparent 38%);
            opacity: 0;
            transition: opacity 0.2s ease;
          }

          &:hover {
            border-color: transparent;
            box-shadow: 0 4px 14px -4px rgba(15, 23, 42, 0.1), 0 0 0 1.5px var(--accent, #3b82f6);
            transform: translateY(-1px);
          }

          &:hover::after {
            opacity: 0.08;
          }

          .quick-icon {
            position: relative;
            z-index: 1;
            display: flex;
            flex: none;
            align-items: center;
            justify-content: center;
            width: 36px;
            height: 36px;
            border-radius: 8px;

            img {
              width: 22px;
              height: 22px;
              object-fit: contain;
            }
          }

          .name {
            position: relative;
            z-index: 1;
            min-width: 0;
            color: #0f172a;
            font-family: var(--crest-font-sans);
            font-size: 13.5px;
            font-style: normal;
            font-weight: 600;
            line-height: 20px;
          }
        }

        .item-quick {
          width: 100%;
          .main-color-quick {
            font-size: 32px;
            margin-right: 12px;
          }
        }
        .quick-create-disabled {
          /* 无权限入口保留位置但禁用交互，避免权限变化造成布局重排。 */
          cursor: not-allowed;
          color: var(--ed-color-info-light-5);
          background-color: var(--ed-color-info-light-9);
          border-color: var(--ed-color-info-light-8);
          .name {
            color: var(--ed-color-info-light-5) !important;
          }
          .quick-icon {
            background-color: var(--ed-color-primary-light-8) !important;
            border-color: var(--ed-color-info-light-8) !important;
          }
          .empty-tooltip-container {
            width: 146px;
            position: absolute;
            height: 52px;
            margin-left: -16px;
          }
          .empty-tooltip-container-template {
            width: 300px;
            position: absolute;
            height: 52px;
            margin-left: -16px;
          }
          .template-create {
            opacity: 0.3;
          }
        }
      }
    }
  }

  .workbranch-content {
    /* 右侧内容只负责滚动承载，避免外层页面出现双滚动条。 */
    min-width: 0;
    height: 100%;
    min-height: 0;
    overflow: hidden;
    border-radius: var(--workbranch-card-radius);

    .workbranch-content-scroll {
      height: 100%;
      border-radius: inherit;

      :deep(.ed-scrollbar__wrap),
      :deep(.ed-scrollbar__view) {
        height: 100%;
        border-radius: inherit;
      }
    }
  }
}

@media (max-width: 1180px) {
  .workbranch {
    /* 中等宽度下压缩左栏，优先保留右侧资源表格的阅读空间。 */
    grid-template-columns: 300px minmax(0, 1fr);
    padding: 20px;
  }
}

@media (max-width: 900px) {
  .workbranch {
    /* 窄屏改为单列布局，允许页面整体滚动。 */
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 60px);
    overflow: auto;
  }

  .workbranch .workbranch-content {
    height: 620px;
    min-height: 520px;
  }
}
</style>
