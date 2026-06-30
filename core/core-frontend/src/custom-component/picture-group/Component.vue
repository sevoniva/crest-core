<template>
  <div class="pic-main">
    <chart-error v-if="isError" :err-msg="errMsg" />
    <img
      draggable="false"
      v-else-if="state.showUrl"
      :style="imageAdapter"
      :src="imgUrlTrans(state.showUrl)"
    />
    <template v-else>
      <chart-empty-info :view-icon="view.type"></chart-empty-info>
    </template>
  </div>
</template>

<script setup lang="ts">
import {
  CSSProperties,
  computed,
  nextTick,
  toRefs,
  reactive,
  ref,
  PropType,
  watch,
  onBeforeMount
} from 'vue'
import { imgUrlTrans } from '@/utils/imgUtils'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { getData } from '@/api/chart'
import { parseJson } from '@/views/chart/components/js/util'
import { mappingColorCustom } from '@/views/chart/components/js/panel/common/common_table'
import { storeToRefs } from 'pinia'
import ChartEmptyInfo from '@/views/chart/components/views/components/ChartEmptyInfo.vue'
import ChartError from '@/views/chart/components/views/components/ChartError.vue'
import { deepCopy } from '@/utils/utils'
/** 主画布仓库提供图片组渲染所需的视图数据和预览状态 */
const dvMainStore = dvMainStoreWithOut()
/** 画布状态引用，用于判断编辑态、全屏态和写回数据明细 */
const { canvasViewInfo, mobileInPc, fullscreenFlag } = storeToRefs(dvMainStore)
/** 图片组运行态数据，包含当前图片地址、数据结果和首屏标记 */
const state = reactive<any>({
  emptyValue: '-',
  data: null,
  viewDataInfo: null,
  showUrl: null,
  firstRender: true,
  previewFirstRender: true,
  curImgList: []
})
/** 数据初始化完成标记，避免异步刷新时重复渲染 */
const initReady = ref(true)
/** 图片组组件入参，承接画布元素、预览位置、刷新计数和视图配置 */
const props = defineProps({
  element: {
    type: Object,
    default() {
      return {
        propValue: { urlList: [] }
      }
    }
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  // 仪表板刷新计时器
  searchCount: {
    type: Number,
    required: false,
    default: 0
  },
  view: {
    type: Object as PropType<ChartObj>,
    default() {
      return {
        propValue: null
      }
    }
  }
})
/** 数据加载是否出现错误 */
const isError = ref(false)
/** 数据加载失败时展示的错误信息 */
const errMsg = ref('')
/** 条件样式计算时选中的原始行数据 */
const dataRowSelect = ref({})
/** 条件样式计算时按字段名称索引的行数据 */
const dataRowNameSelect = ref({})
/** 当前参与条件样式判断的字段显示名 */
const dataRowFiledName = ref([])
/** 图片轮播定时器句柄 */
let carouselTimer = null
/** 将常用入参转换为响应式引用，便于监听和计算 */
const { element, view, showPosition } = toRefs(props)
/** 组件内部自动刷新定时器句柄 */
let innerRefreshTimer = null
/** 内部自动刷新累计次数 */
let innerSearchCount = 0
/** 当前是否处于 PC 画布编辑态，编辑态下禁用自动轮播 */
const isEditMode = computed(
  () => showPosition.value.includes('canvas') && !mobileInPc.value && !fullscreenFlag.value
)

/** 监听外部刷新计数，在未启用内部刷新时触发数据重算 */
watch([() => props.searchCount], () => {
  // 内部计时器启动 忽略外部计时器
  if (!innerRefreshTimer) {
    calcData(view.value, () => {
      // 外部刷新完成后的回调占位
    })
  }
})

// 编辑状态下 不启动刷新
/** 构建预览场景下的内部定时刷新任务 */
const buildInnerRefreshTimer = (
  refreshViewEnable = false,
  refreshUnit = 'minute',
  refreshTime = 5
) => {
  if (showPosition.value === 'preview' && !innerRefreshTimer && refreshViewEnable) {
    innerRefreshTimer && clearInterval(innerRefreshTimer)
    const timerRefreshTime = refreshUnit === 'second' ? refreshTime * 1000 : refreshTime * 60000
    innerRefreshTimer = setInterval(() => {
      calcData(view.value, () => {
        // 内部定时刷新完成后的回调占位
      })
      innerSearchCount++
    }, timerRefreshTime)
  }
}

/** 编辑态切换时重新评估图片轮播状态 */
watch(
  () => isEditMode.value,
  () => {
    initCarousel()
  }
)

/** 初始化图片轮播，条件样式启用或编辑态下会保持静态首图 */
const initCarousel = () => {
  carouselTimer && clearInterval(carouselTimer)
  carouselTimer = null
  const picLength = element.value.propValue.urlList?.length || 0
  const { threshold } = parseJson(view.value.senior)
  // 非编辑状态 未启用条件样式 存在图片 启用轮播
  if (!isEditMode.value && !threshold.enable && picLength > 0 && element.value.carousel?.enable) {
    const switchTime = (element.value.carousel.time || 5) * 1000
    let switchCount = 1
    // 轮播定时器
    carouselTimer = setInterval(() => {
      const nowIndex = switchCount % element.value.propValue.urlList.length
      switchCount++
      nextTick(() => {
        state.showUrl = element.value.propValue.urlList[nowIndex].url
      })
    }, switchTime)
  }
}

/** 根据适配方式生成图片容器样式 */
const imageAdapter = computed(() => {
  const style = {
    position: 'relative',
    width: '100%',
    height: '100%'
  }
  if (element.value.style.adaptation === 'original') {
    style.width = 'auto'
    style.height = 'auto'
  } else if (element.value.style.adaptation === 'equiratio') {
    style.height = 'auto'
  }
  return style as CSSProperties
})

/** 初始化当前数据字段映射，供条件样式按字段名读取行值 */
const initCurFields = chartDetails => {
  dataRowFiledName.value = []
  dataRowSelect.value = {}
  dataRowNameSelect.value = {}
  const sourceFields = Array.isArray(chartDetails.data?.sourceFields)
    ? chartDetails.data.sourceFields
    : []
  if (sourceFields.length) {
    const checkAllAxisStr =
      JSON.stringify(chartDetails.xAxis) +
      JSON.stringify(chartDetails.xAxisExt) +
      JSON.stringify(chartDetails.yAxis) +
      JSON.stringify(chartDetails.yAxisExt)
    sourceFields.forEach(field => {
      if (checkAllAxisStr.indexOf(field.id) > -1) {
        dataRowFiledName.value.push(`[${field.name}]`)
      }
    })
    if (checkAllAxisStr.indexOf('"记录数*"') > -1) {
      dataRowFiledName.value.push(`[记录数*]`)
    }
    const dataFields = Array.isArray(chartDetails.data?.fields) ? chartDetails.data.fields : []
    const rowData = chartDetails.data?.tableRow?.[0] || {}
    const sourceFieldNameIdMap = dataFields.reduce((pre, next) => {
      pre[next['engineFieldName']] = next['name']
      return pre
    }, {})
    for (const key in rowData) {
      dataRowNameSelect.value[sourceFieldNameIdMap[key]] = rowData[key]
    }
  }
  conditionAdaptor(view.value)
}

/** 根据条件样式规则选择最终展示的图片地址 */
const conditionAdaptor = (chart: Chart) => {
  state.showUrl = null
  if (!chart || !chart.senior) {
    return
  }
  const { threshold } = parseJson(chart.senior)
  if (!threshold.enable) {
    return
  }
  const conditions = threshold.tableThreshold ?? []
  if (conditions?.length > 0) {
    for (let i = 0; i < conditions.length; i++) {
      const field = deepCopy(conditions[i])
      let defaultValueColor = null
      field.conditions.sort((a, b) => {
        const aIsDefault = a.term === 'default'
        const bIsDefault = b.term === 'default'

        if (aIsDefault && !bIsDefault) return 1
        if (!aIsDefault && bIsDefault) return -1
        return 0
      })
      const checkResult = mappingColorCustom(
        dataRowNameSelect.value[field.field.name],
        defaultValueColor,
        field,
        'url'
      )
      if (checkResult) {
        state.showUrl = checkResult
      }
    }
  }
}

/** 使用组件配置中的首张图片作为默认展示内容 */
const withInit = () => {
  if (element.value.propValue['urlList'] && element.value.propValue['urlList'].length > 0) {
    state.showUrl = element.value.propValue['urlList'][0].url
  } else {
    state.showUrl = null
  }

  initCarousel()
}

/** 计算图片组数据，支持条件样式取数和无数据源时的静态图片展示 */
const calcData = (viewCalc: Chart, callback) => {
  isError.value = false
  const { threshold } = parseJson(viewCalc.senior)
  if (!threshold.enable) {
    withInit()
    callback?.()
    return
  }
  if (viewCalc.tableId || viewCalc['dataFrom'] === 'template') {
    const v = JSON.parse(JSON.stringify(viewCalc))
    v.type = 'table-info'
    v.render = 'antv'
    v.resultCount = 1
    getData(v)
      .then(res => {
        if (res.code && res.code !== 0) {
          isError.value = true
          errMsg.value = res.msg
        } else {
          res.type = 'picture-group'
          res.render = 'custom'
          state.data = res?.data
          state.viewDataInfo = res
          state.totalItems = res?.totalItems
          const curViewInfo = canvasViewInfo.value[element.value.id]
          if (res.data && curViewInfo) {
            curViewInfo['curFields'] = res.data.fields || []
          }
          dvMainStore.setViewDataDetails(element.value.id, res)
          initReady.value = true
          initCurFields(res)
          initCarousel()
        }
        callback?.()
        nextTick(() => {
          initReady.value = true
        })
      })
      .catch(e => {
        console.error(e)
        nextTick(() => {
          initReady.value = true
        })
        callback?.()
      })
  } else if (!viewCalc.tableId) {
    initReady.value = true
    withInit()
    callback?.()
    nextTick(() => {
      initReady.value = true
    })
  } else {
    withInit()
    nextTick(() => {
      initReady.value = true
    })
    callback?.()
  }
}

// 初始化此处不必刷新
/** 暴露给画布渲染器的渲染入口，图片组当前无需额外刷新动作 */
const renderChart = () => {
  // 图片组初始化阶段无需额外刷新
}

/** 挂载前清理旧轮播并准备内部刷新定时器 */
onBeforeMount(() => {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
  buildInnerRefreshTimer()
})

defineExpose({
  calcData,
  renderChart
})
</script>

<style lang="less" scoped>
.pic-main {
  overflow: hidden;
  width: 100%;
  height: 100%;
  cursor: pointer;
}
.pic-upload {
  display: flex;
  width: 100%;
  height: 100%;
  color: #5370af;
  align-items: center;
  justify-content: center;
}
</style>
