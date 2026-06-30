<script lang="ts" setup>
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import eventBus from '@/utils/eventBus'
import colorFunctions from 'less/lib/less/functions/color.js'
import colorTree from 'less/lib/less/tree/color.js'
import { isISOMobile, isMobile } from '@/utils/utils'
import { cloneDeep } from 'lodash-es'
import { ElMessage } from 'element-plus-secondary'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import QueryConditionConfiguration from './QueryConditionConfiguration.vue'
import type { ComponentInfo } from '@/api/chart'
import { getDynamicRange, getCustomTime } from '@/custom-component/v-query/time-format'
import { getCustomRange } from '@/custom-component/v-query/time-format-dayjs'
import { infoFormat } from './options'
import {
  onBeforeUnmount,
  reactive,
  ref,
  toRefs,
  unref,
  watch,
  computed,
  onMounted,
  onBeforeMount,
  CSSProperties,
  shallowRef,
  provide,
  nextTick
} from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from '@/hooks/web/useI18n'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { comInfo } from './com-info'
import { useEmitt } from '@/hooks/web/useEmitt'
import StyleInject from './StyleInject.vue'
import { getKeyList, reRenderAll } from '@/custom-component/v-query/QueryUtils'
// 查询组件运行态入参，包含组件配置、画布元素、显示位置和缩放比例
const props = defineProps({
  view: {
    type: Object,
    default() {
      return {
        customStyle: ''
      }
    }
  },
  element: {
    type: Object,
    default() {
      return {
        id: null,
        propValue: []
      }
    }
  },
  showPosition: {
    type: String,
    required: true,
    default: ''
  },
  scale: {
    type: Number,
    required: false,
    default: 1
  }
})
const { element, view, scale } = toRefs(props)
const { t } = useI18n()
// 查询组件根节点引用，用于写入组件尺寸 CSS 变量
const vQueryRef = ref()
const dvMainStore = dvMainStoreWithOut()
const { curComponent, canvasViewInfo, mobileInPc, firstLoadMap, editMode } =
  storeToRefs(dvMainStore)
// 当前组件是否处于可编辑状态，画布点击其它组件后会关闭
const canEdit = ref(false)
// 查询条件配置弹窗引用
const queryConfig = ref()
const defaultStyle = {
  border: '',
  placeholderSize: 14,
  placeholderShow: true,
  background: '',
  text: '',
  layout: 'horizontal',
  btnList: ['sure'],
  titleLayout: 'left',
  titleShow: false,
  titleColor: '',
  textColorShow: false,
  labelShow: true,
  title: '',
  labelColor: '#1f2329',
  fontSize: '14',
  fontWeight: '',
  fontStyle: '',
  fontSizeBtn: '14',
  fontWeightBtn: '',
  fontStyleBtn: '',
  queryConditionWidth: 227,
  nameboxSpacing: 8,
  queryConditionSpacing: 16,
  queryConditionHeight: 32,
  btnColor: '#3B82F6',
  labelColorBtn: '#ffffff'
}
// 查询组件自定义样式状态，由画布样式配置和组件局部配置共同驱动
const customStyle = reactive({ ...defaultStyle })
const snapshotStore = snapshotStoreWithOut()
let instanceElMessage = null
let closeTime = null

// 显示必填错误提示，并自动关闭上一条同类提示
const closeElMessage = requiredName => {
  if (instanceElMessage) {
    instanceElMessage.close()
  }
  instanceElMessage = ElMessage({
    message: `【${requiredName}】${t('v_query.before_querying')}`,
    type: 'error'
  })

  if (closeTime) {
    clearTimeout(closeTime)
  }
  closeTime = setTimeout(() => {
    instanceElMessage.close()
  }, 2000)
}
// 查询按钮文字样式，跟随自定义按钮字体和颜色配置
const btnStyle = computed(() => {
  const style = {
    color: customStyle.labelColorBtn
  } as CSSProperties
  if (customStyle.fontSizeBtn) {
    style.fontSize = customStyle.fontSizeBtn + 'px'
  }

  if (customStyle.fontWeightBtn) {
    style.fontWeight = customStyle.fontWeightBtn
  }

  if (customStyle.fontStyleBtn) {
    style.fontStyle = customStyle.fontStyleBtn
  }

  return style
})

// 将 rgba 颜色按白色背景合成为十六进制颜色，供 less 颜色函数计算
function rgbaTo16color(color) {
  let val = color
    .replace(/rgba?\(/, '')
    .replace(/\)/, '')
    .replace(/[\s+]/g, '')
    .split(',')
  let a = parseFloat(val[3] || 1),
    r = Math.floor(a * parseInt(val[0]) + (1 - a) * 255),
    g = Math.floor(a * parseInt(val[1]) + (1 - a) * 255),
    b = Math.floor(a * parseInt(val[2]) + (1 - a) * 255)
  return (
    '#' +
    ('0' + r.toString(16)).slice(-2) +
    ('0' + g.toString(16)).slice(-2) +
    ('0' + b.toString(16)).slice(-2)
  )
}

// 查询按钮悬停和按下颜色，基于主色自动生成
const btnHoverStyle = computed(() => {
  let btnColor = customStyle.btnColor
  if (customStyle.btnColor.startsWith('rgb')) {
    btnColor = rgbaTo16color(customStyle.btnColor)
  }

  if (btnColor.startsWith('#')) {
    btnColor = btnColor.substr(1)
  }

  return {
    rawColor: customStyle.btnColor ?? '#3B82F6',
    hoverColor: customStyle.btnColor
      ? colorFunctions
          .mix(new colorTree('ffffff'), new colorTree(btnColor), {
            value: 15
          })
          .toRGB()
      : '#5285FF',
    activeColor: customStyle.btnColor
      ? colorFunctions
          .mix(new colorTree('000000'), new colorTree(btnColor), {
            value: 15
          })
          .toRGB()
      : '#2B5FD9'
  }
})

// 查询按钮主色
const btnPrimaryColor = computed(() => {
  return btnHoverStyle.value.rawColor
})

// 查询按钮悬停色
const btnPrimaryHoverColor = computed(() => {
  return btnHoverStyle.value.hoverColor
})

// 查询按钮按下色
const btnPrimaryActiveColor = computed(() => {
  return btnHoverStyle.value.activeColor
})

// 多选标签背景色，按组件背景色生成更浅的可读颜色
const tagColor = computed(() => {
  if (customStyle.background && !customStyle.background.toLowerCase().includes('#ffffff')) {
    return colorFunctions
      .mix(new colorTree('ffffff'), new colorTree(customStyle.background.substr(1)), {
        value: 15
      })
      .toRGB()
  }
  return '#f0f2f5'
})

// 朴素按钮样式，保留透明背景并使用自定义按钮主色
const btnPlainStyle = computed(() => {
  const style = {
    backgroundColor: 'transparent',
    borderColor: customStyle.btnColor,
    color: customStyle.btnColor
  } as CSSProperties
  if (customStyle.fontSizeBtn) {
    style.fontSize = customStyle.fontSizeBtn + 'px'
  }

  if (customStyle.fontWeightBtn) {
    style.fontWeight = customStyle.fontWeightBtn
  }

  if (customStyle.fontStyleBtn) {
    style.fontStyle = customStyle.fontStyleBtn
  }

  return style
})
// 当前画布组件视图中的查询组件样式配置
const curComponentView = computed(() => {
  return (canvasViewInfo.value[element.value.id] || {}).customStyle
})

const { datasetFieldList } = comInfo()

// 将外部样式配置写入本地响应式样式状态，并记录快照
const setCustomStyle = val => {
  const {
    borderColor,
    btnList,
    titleLayout,
    labelColor,
    text,
    bgColor,
    layout,
    titleShow,
    titleColor,
    title,
    fontSize,
    fontWeight,
    fontStyle,
    fontSizeBtn,
    fontWeightBtn,
    fontStyleBtn,
    queryConditionWidth,
    nameboxSpacing,
    queryConditionSpacing,
    queryConditionHeight,
    labelColorBtn,
    btnColor,
    placeholderSize,
    placeholderShow,
    labelShow
  } = val
  customStyle.background = bgColor || ''
  customStyle.border = borderColor || ''
  customStyle.btnList = [...btnList]
  customStyle.layout = layout
  customStyle.placeholderShow = placeholderShow ?? true
  customStyle.placeholderSize = placeholderSize ?? 14
  nextTick(() => {
    vQueryRef.value.style.setProperty(
      '--ed-component-size',
      `${customStyle.placeholderSize + 18}px`
    )
  })
  customStyle.titleShow = titleShow
  customStyle.titleColor = titleColor
  customStyle.labelColor = labelShow ? labelColor || '' : ''
  customStyle.fontSize = labelShow ? fontSize || '14' : '14'
  customStyle.fontWeight = labelShow ? fontWeight || '' : ''
  customStyle.fontStyle = labelShow ? fontStyle || '' : ''
  customStyle.title = title
  customStyle.text = customStyle.placeholderShow ? text || '' : ''
  customStyle.titleLayout = titleLayout
  customStyle.fontSizeBtn = fontSizeBtn || '14'
  customStyle.fontWeightBtn = fontWeightBtn
  customStyle.fontStyleBtn = fontStyleBtn
  customStyle.queryConditionWidth = queryConditionWidth ?? 227
  customStyle.nameboxSpacing = nameboxSpacing ?? 8
  customStyle.queryConditionSpacing = queryConditionSpacing ?? 16
  customStyle.queryConditionHeight = queryConditionHeight ?? 32
  customStyle.labelColorBtn = labelColorBtn || '#ffffff'
  customStyle.labelShow = labelShow ?? true
  customStyle.btnColor = btnColor || '#3B82F6'
  snapshotStore.recordSnapshotCache('setCustomStyle')
}

// 监听页面级视图样式配置变化，刷新查询组件样式
watch(
  () => view.value,
  val => {
    if (!val?.customStyle?.component) return
    setCustomStyle(val?.customStyle?.component)
  },
  {
    deep: true,
    immediate: true
  }
)

// 监听当前组件局部样式配置变化，刷新查询组件样式
watch(
  () => curComponentView.value,
  val => {
    if (!val?.component) return
    setCustomStyle(val?.component)
  },
  {
    deep: true,
    immediate: true
  }
)
// 查询条件运行态列表
const list = ref([])
let oldList = []
let isResetData = false
// 同步画布元素中的查询条件列表，并保留重置前的基准副本
watch(
  () => props.element.propValue,
  () => {
    list.value = [...props.element.propValue]
    if (isResetData) {
      isResetData = false
      return
    }
    oldList = cloneDeep(props.element.propValue)
  },
  {
    immediate: true
  }
)
// 画布点击其它组件时退出当前查询组件编辑态
const onComponentClick = () => {
  if (curComponent.value.id !== element.value.id) {
    canEdit.value = false
  }
}

const { emitter } = useEmitt()
// 首次挂载时尚未完成默认值回填的选择器 id 列表
const unMountSelect = shallowRef([])
onBeforeMount(() => {
  unMountSelect.value = list.value.map(ele => ele.id)
  ;(props.element.cascade || []).forEach(ele => {
    ele.forEach(item => {
      item.currentSelectValue = item.selectValue
    })
  })
})

// 选择器完成默认值同步后，从未挂载列表中移除
const releaseSelect = id => {
  unMountSelect.value = unMountSelect.value.filter(ele => ele !== id)
}

// 针对受影响条件补齐必填默认值，避免级联刷新后出现空必填条件
const fillRequireVal = arr => {
  element.value.propValue?.forEach(next => {
    if (arr.some(itx => (next.checkedFields || []).includes(itx)) && next.required) {
      if (next.displayType === '8') {
        const { conditionValueF, conditionValueS, conditionType } = next
        if (conditionType === 0 && conditionValueF === '') {
          next.conditionValueF = next.defaultConditionValueF
        } else {
          if (conditionValueF === '') {
            next.conditionValueF = next.defaultConditionValueF
          }
          if (conditionValueS === '') {
            next.conditionValueS = next.defaultConditionValueS
          }
        }
      } else if (next.displayType === '22') {
        if (next.numValueStart !== 0 && !next.numValueStart) {
          next.numValueStart = next.defaultNumValueStart
        }

        if (next.numValueEnd !== 0 && !next.numValueEnd) {
          next.numValueEnd = next.defaultNumValueEnd
        }
      } else if (
        (Array.isArray(next.selectValue) && !next.selectValue.length) ||
        (next.selectValue !== 0 && !next.selectValue)
      ) {
        if (
          next.optionValueSource === 1 &&
          (next.defaultMapValue?.length || next.displayId) &&
          ![1, 7].includes(+next.displayType)
        ) {
          next.mapValue = next.defaultMapValue
          next.selectValue = next.multiple ? next.defaultMapValue : next.defaultMapValue[0]
        } else {
          next.selectValue = next.defaultValue
        }
      }
    }
  })
}
// 根据指定查询条件触发关联图表刷新，并在刷新前校验必填和数值范围
const queryDataForId = id => {
  let requiredName = ''
  let numName = ''
  const emitterList = (element.value.propValue || [])
    .filter(ele => ele.id === id)
    .reduce((pre, next) => {
      if (next.required) {
        if (!next.defaultValueCheck) {
          requiredName = next.name
        }

        if (next.displayType === '8') {
          const { conditionValueF, conditionValueS, conditionType } = next
          if (conditionType === 0) {
            requiredName = conditionValueF === '' ? next.name : ''
          } else {
            requiredName =
              [conditionValueF || '', conditionValueS || ''].filter(ele => ele !== '').length < 2
                ? next.name
                : ''
          }
        } else if (next.displayType === '22') {
          if (
            (next.numValueStart !== 0 && !next.numValueStart) ||
            (next.numValueEnd !== 0 && !next.numValueEnd)
          ) {
            requiredName = next.name
          }
        } else if (
          (Array.isArray(next.selectValue) && !next.selectValue.length) ||
          (next.selectValue !== 0 && !next.selectValue)
        ) {
          requiredName = next.name
        }
      }

      if (next.displayType === '22') {
        if (
          !isNaN(next.numValueEnd) &&
          !isNaN(next.numValueStart) &&
          next.numValueEnd < next.numValueStart
        ) {
          numName = next.name
        }
        if (
          [next.numValueEnd, next.numValueStart].filter(itx => ![null, undefined, ''].includes(itx))
            .length === 1
        ) {
          requiredName = next.name
        }
      }

      const keyList = getKeyList(next)
      pre = [...new Set([...keyList, ...pre])]
      return pre
    }, [])
  if (!!requiredName) {
    closeElMessage(requiredName)
    return
  }
  if (!!numName) {
    ElMessage.error(`【${numName}】${t('v_query.the_minimum_value')}`)
    return
  }
  if (!emitterList.length) return
  fillRequireVal(emitterList)
  emitterList.forEach(ele => {
    emitter.emit(`query-data-${ele}`)
  })
}
// 获取查询条件控件宽度，提供给子选择器计算运行态宽度
const getQueryConditionWidth = () => {
  return customStyle.queryConditionWidth
}

// 获取级联关系列表，并把首项默认值状态同步到级联节点
const getCascadeList = () => {
  const { propValue, cascade = [] } = props.element
  const defaultValueFirstItemMap = (propValue || []).reduce((pre, next) => {
    pre[next.id] = next.defaultValueFirstItem
    return pre
  }, {})
  cascade.forEach(itx => {
    itx.forEach(ele => {
      ele.defaultValueFirstItem = defaultValueFirstItemMap[ele.datasetId?.split('--')[1]]
    })
  })
  return cascade
}

// 提供给子组件的占位符显示配置
const getPlaceholder = computed(() => {
  return {
    placeholderShow: customStyle.placeholderShow
  }
})

// 子组件值变化后的查询确认入口，带确认按钮时延迟到按钮点击触发
const isConfirmSearch = (id, disabledFirstItem = false) => {
  if (componentWithSure.value && !disabledFirstItem) return
  queryDataForId(id)
}

// 树形条件使用的查询触发入口，只校验数值范围和半填范围，不重复提示普通必填
const isConfirmSearchNoRequiredName = id => {
  if (componentWithSure.value) return
  let requiredName = ''
  let numName = ''
  const emitterList = (element.value.propValue || [])
    .filter(ele => ele.id === id)
    .reduce((pre, next) => {
      if (next.displayType === '22') {
        if (
          !isNaN(next.numValueEnd) &&
          !isNaN(next.numValueStart) &&
          next.numValueEnd < next.numValueStart
        ) {
          numName = next.name
        }
        if (
          [next.numValueEnd, next.numValueStart].filter(itx => ![null, undefined, ''].includes(itx))
            .length === 1
        ) {
          requiredName = next.name
        }
      }

      const keyList = getKeyList(next)
      pre = [...new Set([...keyList, ...pre])]
      return pre
    }, [])
  if (!!requiredName) {
    closeElMessage(requiredName)
    return
  }
  if (!!numName) {
    ElMessage.error(`【${numName}】${t('v_query.the_minimum_value')}`)
    return
  }
  if (!emitterList.length) return
  fillRequireVal(emitterList)
  emitterList.forEach(ele => {
    emitter.emit(`query-data-${ele}`)
  })
}

// 向查询条件子组件提供运行态服务和级联上下文
provide('is-confirm-search', isConfirmSearch)
provide('unmount-select', unMountSelect)
provide('release-unmount-select', releaseSelect)
provide('query-data-for-id', queryDataForId)
provide('query-data-for-id-tree', isConfirmSearchNoRequiredName)
provide('com-width', getQueryConditionWidth)
provide('cascade-list', getCascadeList)
provide('placeholder', getPlaceholder)

onBeforeUnmount(() => {
  if (instanceElMessage) {
    instanceElMessage.close()
  }
  if (closeTime) {
    clearTimeout(closeTime)
  }
  emitter.off(`addQueryCriteria${element.value.id}`)
  emitter.off(`editQueryCriteria${element.value.id}`)
  emitter.off(`updateQueryCriteria${element.value.id}`)
  eventBus.off('componentClick', onComponentClick)
})

// 根据当前画布字段关系修正查询条件绑定，处理自动条件和失效字段映射
const updateQueryCriteria = () => {
  if (dvMainStore.mobileInPc && !isMobile()) return
  Array.isArray(element.value.propValue) &&
    element.value.propValue?.forEach(ele => {
      if (ele.auto) {
        const componentInfo = {
          datasetId: ele.dataset.id,
          id: ele.field.id
        }
        const checkedFields = []
        const checkedFieldsMap = {}
        datasetFieldList.value.forEach(ele => {
          if (ele.tableId === componentInfo.datasetId) {
            checkedFields.push(ele.id)
            checkedFieldsMap[ele.id] = componentInfo.id
          }
        })
        ele.checkedFields = checkedFields
        ele.checkedFieldsMap = checkedFieldsMap
      } else {
        if (
          !ele.dataset.id ||
          !!ele.parameters.length ||
          ele.optionValueSource !== 1 ||
          ![0, 2, 5].includes(+ele.displayType)
        )
          return
        const checkedFields = datasetFieldList.value.map(itx => itx.id)
        ele.checkedFields.forEach(itx => {
          if (!checkedFields.includes(itx)) {
            ele.checkedFieldsMap[itx] = ''
          }
        })
        ele.checkedFields = ele.checkedFields.filter(itx => checkedFields.includes(itx))
      }
    })
}

onMounted(() => {
  emitter.on(`addQueryCriteria${element.value.id}`, addCriteriaConfigOut)
  emitter.on(`editQueryCriteria${element.value.id}`, editQueryCriteria)
  emitter.on(`updateQueryCriteria${element.value.id}`, updateQueryCriteria)
  updateQueryCriteria()

  if (dvMainStore.mobileInPc && !isMobile()) {
    queryData()
  }
})

// 拖拽悬停占位处理，模板通过 prevent.stop 阻止浏览器默认行为
const dragover = () => {
  // 当前组件只需要接收 drop 事件，悬停阶段不改变状态
}

// 从字段拖拽数据生成查询条件，并记录快照
const drop = e => {
  const componentInfoArr: ComponentInfo[] = JSON.parse(e.dataTransfer.getData('dimension') || '{}')
  componentInfoArr.forEach(componentInfo => {
    const checkedFields = []
    const checkedFieldsMap = {}
    datasetFieldList.value.forEach(ele => {
      if (ele.tableId === componentInfo.datasetId) {
        checkedFields.push(ele.id)
        checkedFieldsMap[ele.id] = componentInfo.id
      }
    })
    // URL 字段类型换成文本字段类型
    const displayType = componentInfo.fieldType === 7 ? 0 : `${componentInfo.fieldType}`
    list.value.push({
      ...infoFormat(componentInfo),
      auto: true,
      optionValueSource: 1,
      checkedFields,
      checkedFieldsMap,
      displayType
    })
  })
  element.value.propValue = [...list.value]
  snapshotStore.recordSnapshotCache('drop')
}

// 打开指定查询条件的配置弹窗
const editeQueryConfig = (queryId: string) => {
  queryConfig.value.setCondition(queryId)
}

// 编辑查询条件入口，没有条件时先新增一个条件
const editQueryCriteria = () => {
  if (!list.value.length) {
    addCriteriaConfigOut()
    return
  }
  editeQueryConfig(list.value[0].id)
}

// 从外部新增查询条件，并立即进入配置状态
const addCriteriaConfigOut = () => {
  queryConfig.value.setConditionOut()
}

// 删除指定查询条件，记录快照并触发关联组件重渲染
const delQueryConfig = index => {
  const com = cloneDeep(unref(list))
  list.value.splice(index, 1)
  element.value.propValue = [...list.value]
  snapshotStore.recordSnapshotCache('delQueryConfig')
  reRenderAll(com, cloneDeep(unref(list)))
}

// 将查询条件恢复到上一次配置基准，并重算动态时间默认值和级联值
const resetData = () => {
  isResetData = true
  element.value.propValue = []
  nextTick(() => {
    element.value.propValue = cloneDeep(oldList)
    ;(element.value.propValue || []).reduce((pre, next) => {
      next.conditionValueF = next.defaultConditionValueF
      next.conditionValueOperatorF = next.defaultConditionValueOperatorF
      next.conditionValueS = next.defaultConditionValueS
      next.conditionValueOperatorS = next.defaultConditionValueOperatorS

      if (next.displayType === '22') {
        next.numValueEnd = next.defaultNumValueEnd
        next.numValueStart = next.defaultNumValueStart
      }

      if (!next.defaultValueCheck) {
        next.defaultValue = next.multiple || +next.displayType === 7 ? [] : undefined
      }
      next.selectValue = Array.isArray(next.defaultValue)
        ? [...next.defaultValue]
        : next.defaultValue
      if (next.optionValueSource === 1 && next.defaultMapValue?.length) {
        next.mapValue = Array.isArray(next.defaultMapValue)
          ? [...next.defaultMapValue]
          : next.defaultMapValue
      }

      if (
        next.defaultValueCheck &&
        [1, 7].includes(+next.displayType) &&
        next.timeType === 'dynamic'
      ) {
        if (+next.displayType === 1) {
          let selectValue = getDynamicRange(next) || []
          next.defaultValue = new Date(selectValue[0])
          next.selectValue = new Date(selectValue[0])
        } else {
          const {
            timeNum,
            relativeToCurrentType,
            around,
            relativeToCurrentRange,
            arbitraryTime,
            timeGranularity,
            timeNumRange,
            relativeToCurrentTypeRange,
            aroundRange,
            timeGranularityMultiple,
            arbitraryTimeRange
          } = next

          let startTime = getCustomTime(
            timeNum,
            relativeToCurrentType,
            timeGranularity,
            around,
            arbitraryTime,
            timeGranularityMultiple,
            'start-panel'
          )
          let endTime = getCustomTime(
            timeNumRange,
            relativeToCurrentTypeRange,
            timeGranularity,
            aroundRange,
            arbitraryTimeRange,
            timeGranularityMultiple,
            'end-panel'
          )

          if (!!relativeToCurrentRange && relativeToCurrentRange !== 'custom') {
            ;[startTime, endTime] = getCustomRange(relativeToCurrentRange)
          }
          next.defaultValue = [startTime, endTime]
          next.selectValue = [startTime, endTime]
        }
      }

      ;(props.element.cascade || []).forEach(ele => {
        ele.forEach(item => {
          const comId = item.datasetId.split('--')[1]
          if (next.id === comId) {
            item.currentSelectValue = Array.isArray(next.selectValue)
              ? next.selectValue
              : [next.selectValue].filter(itx => ![null, undefined].includes(itx))
            useEmitt().emitter.emit(`${item.datasetId.split('--')[1]}-select`)
          }
        })
      })
      const keyList = getKeyList(next)
      pre = [...new Set([...keyList, ...pre])]
      return pre
    }, [])
    !componentWithSure.value && queryData()
  })
}

// 清空所有可见查询条件的运行态值，并按按钮模式决定是否立即查询
const clearData = () => {
  ;(props.element.cascade || []).forEach(ele => {
    ele.forEach(item => {
      if (item.currentSelectValue?.length) {
        useEmitt().emitter.emit(`${item.datasetId.split('--')[1]}-select`)
        item.currentSelectValue = []
      }
    })
  })
  ;(list.value || []).reduce((pre, next) => {
    if (!next.visible) {
      return pre
    }
    next.selectValue = next.multiple || +next.displayType === 7 ? [] : undefined
    if (next.optionValueSource === 1 && next.defaultMapValue?.length) {
      next.mapValue = next.multiple ? [] : undefined
    }
    next.conditionValueF = ''
    next.conditionValueS = ''

    if (next.displayType === '22') {
      next.numValueEnd = undefined
      next.numValueStart = undefined
    }
    const keyList = getKeyList(next)
    pre = [...new Set([...keyList, ...pre])]
    return pre
  }, [])
  !componentWithSure.value && queryData()
}
// 当前可见的查询条件列表
const listVisible = computed(() => {
  return list.value.filter(itx => itx.visible)
})

// 当前查询组件是否展示确认按钮，决定条件变化后是否立即查询
const componentWithSure = computed(() => {
  return customStyle.btnList.includes('sure')
})

// 从有确认按钮切换为无确认按钮时，立即按当前条件触发查询
watch(
  () => componentWithSure.value,
  (val, oldVal) => {
    if (!val && oldVal) {
      queryData()
    }
  },
  {
    immediate: false
  }
)

// 单个查询项选择框高度变量
const boxWidth = computed(() => {
  return `${customStyle.placeholderSize}px`
})

// 单个查询项控件高度变量
const boxHeight = computed(() => {
  return `${customStyle.queryConditionHeight || 32}px`
})

// 按所有查询条件触发关联图表刷新，并校验必填和数值范围
const queryData = () => {
  let requiredName = ''
  let numName = ''
  const emitterList = (element.value.propValue || []).reduce((pre, next) => {
    if (next.required) {
      if (!next.defaultValueCheck) {
        requiredName = next.name
      }

      if (next.displayType === '8') {
        const { conditionValueF, conditionValueS, conditionType } = next
        if (conditionType === 0) {
          requiredName = conditionValueF === '' ? next.name : ''
        } else {
          requiredName =
            [conditionValueF || '', conditionValueS || ''].filter(ele => ele !== '').length < 2
              ? next.name
              : ''
        }
      } else if (next.displayType === '22') {
        if (
          (next.numValueEnd !== 0 && !next.numValueEnd) ||
          (next.numValueStart !== 0 && !next.numValueStart)
        ) {
          requiredName = next.name
        }
      } else if (
        (Array.isArray(next.selectValue) && !next.selectValue.length) ||
        (next.selectValue !== 0 && !next.selectValue)
      ) {
        requiredName = next.name
      }
    }

    if (next.displayType === '22') {
      if (
        !isNaN(next.numValueEnd) &&
        !isNaN(next.numValueStart) &&
        next.numValueEnd < next.numValueStart
      ) {
        numName = next.name
      }
      if (
        [next.numValueEnd, next.numValueStart].filter(itx => ![null, undefined, ''].includes(itx))
          .length === 1
      ) {
        requiredName = next.name
      }
    }
    const keyList = getKeyList(next)
    pre = [...new Set([...keyList, ...pre])]
    return pre
  }, [])
  if (!!requiredName) {
    closeElMessage(requiredName)
    return
  }

  if (!!numName) {
    ElMessage.error(`【${numName}】${t('v_query.the_minimum_value')}`)
    return
  }
  if (!emitterList.length) return
  if (!(dvMainStore.mobileInPc && !isMobile())) {
    dvMainStore.setFirstLoadMap([...new Set([...emitterList, ...firstLoadMap.value])])
  }
  emitterList.forEach(ele => {
    emitter.emit(`query-data-${ele}`)
  })
}
// 查询组件标题样式
const titleStyle = computed(() => {
  return {
    textAlign: customStyle.titleLayout || 'left',
    color: customStyle.titleColor || '#1f2329'
  } as CSSProperties
})

// 查询条件标签样式，跟随字体和颜色配置
const labelStyle = computed(() => {
  const style = {
    fontSize: customStyle.fontSize + 'px',
    lineHeight: +customStyle.fontSize + 8 + 'px'
  } as CSSProperties
  if (customStyle.fontWeight) {
    style.fontWeight = customStyle.fontWeight
  }

  if (customStyle.fontStyle) {
    style.fontStyle = customStyle.fontStyle
  }

  if (customStyle.labelColor) {
    style.color = customStyle.labelColor
  }
  return style
})

// 查询条件布局方式，隐藏标签时固定为横向布局
const comLayout = computed(() => {
  return customStyle.labelShow ? customStyle.layout : 'horizontal'
})

// 非横向布局时为查询控件预留标签高度
const paddingTop = computed<CSSProperties>(() => {
  return {
    paddingTop: comLayout.value !== 'horizontal' ? customStyle.nameboxSpacing + 22 + 'px' : '0'
  }
})

// 横向布局时为标签和控件之间预留间距
const marginRight = computed<CSSProperties>(() => {
  return {
    marginRight: comLayout.value === 'horizontal' ? customStyle.nameboxSpacing + 'px' : '8px'
  }
})

// 查询组件整体缩放样式，移动端使用 transform 保持交互区域准确
const autoStyle = computed(() => {
  if (isISOMobile()) {
    return {
      position: 'absolute',
      height: 100 / scale.value + '%!important',
      width: 100 / scale.value + '%!important',
      left: 50 * (1 - 1 / scale.value) + '%', // 放大余量 除以 2
      top: 50 * (1 - 1 / scale.value) + '%', // 放大余量 除以 2
      transform: 'scale(' + scale.value + ') translateZ(0)'
    } as CSSProperties
  } else {
    return { zoom: scale.value }
  }
})
</script>

<template>
  <div class="v-query-container" ref="vQueryRef" :style="autoStyle" @keydown.stop @keyup.stop>
    <!-- 标题由组件样式统一控制，隐藏标题时查询条件区域占满组件高度。 -->
    <p v-if="customStyle.titleShow" class="title" :style="titleStyle">
      {{ customStyle.title }}
    </p>
    <div
      :class="['v-query', comLayout, customStyle.titleShow && !!customStyle.title && 'title-show']"
      @dragover.prevent.stop="dragover"
      @drop.prevent.stop="drop"
    >
      <!-- 编辑态空列表支持点击新增或拖入字段；预览和移动端禁用直接新增。 -->
      <div v-if="!listVisible.length" class="no-list-label flex-align-center">
        <div class="container flex-align-center">
          {{ t('v_query.here_or_click') }}
          <el-button
            :disabled="showPosition === 'preview' || mobileInPc"
            @click="addCriteriaConfigOut"
            style="font-family: inherit"
            text
          >
            {{ t('v_query.add_query_condition') }}
          </el-button>
        </div>
      </div>
      <div class="query-fields-container">
        <!-- 查询条件按可见配置渲染，隐藏条件仍保留在 propValue 中参与配置管理。 -->
        <div
          class="query-item"
          :style="{ marginRight: `${customStyle.queryConditionSpacing}px` }"
          :key="ele.id"
          v-for="(ele, index) in listVisible"
        >
          <div class="query-field" :style="paddingTop">
            <div class="label" :style="marginRight">
              <div class="label-wrapper" v-show="customStyle.labelShow">
                <div class="label-wrapper-text" :style="labelStyle">
                  <el-tooltip
                    popper-class="label-wrapper-text_tooltip"
                    effect="dark"
                    :content="ele.name"
                    :show-arrow="false"
                    placement="top-start"
                  >
                    {{ ele.name }}
                  </el-tooltip>
                </div>
                <span v-if="ele.required" class="required">*</span>
              </div>
              <div
                class="label-wrapper-tooltip"
                v-if="
                  !['preview', 'edit-preview'].includes(showPosition) &&
                  !dvMainStore.mobileInPc &&
                  editMode === 'edit'
                "
              >
                <!-- 编辑态快捷操作只修改当前查询条件，不触发画布选中状态切换。 -->
                <el-tooltip
                  effect="dark"
                  :content="t('v_query.set_filter_condition')"
                  placement="top"
                >
                  <el-icon @click="editeQueryConfig(ele.id)">
                    <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </el-tooltip>
                <el-tooltip effect="dark" :content="t('v_query.delete_condition')" placement="top">
                  <el-icon style="margin-left: 8px" @click="delQueryConfig(index)">
                    <Icon name="icon_delete-trash_outlined"
                      ><icon_deleteTrash_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </el-tooltip>
              </div>
            </div>
            <div class="query-select">
              <StyleInject
                v-if="customStyle.queryConditionWidth !== 0"
                :customStyle="customStyle"
                :config="ele"
              ></StyleInject>
            </div>
          </div>
        </div>
        <!-- 按钮区根据样式配置展示清空、重置和查询，是否立即查询由 componentWithSure 决定。 -->
        <div class="query-button" v-if="!!listVisible.length">
          <el-button
            @click.stop="clearData"
            :style="btnPlainStyle"
            v-if="customStyle.btnList.includes('clear')"
            plain
          >
            {{ t('commons.clear') }}
          </el-button>
          <el-button
            @click.stop="resetData"
            :style="btnPlainStyle"
            v-if="customStyle.btnList.includes('reset')"
            plain
          >
            {{ t('chart.reset') }}
          </el-button>
          <el-button
            @click.stop="queryData"
            style="margin-right: 7px"
            :style="btnStyle"
            v-if="componentWithSure"
            type="primary"
          >
            {{ t('commons.adv_search.search') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
  <Teleport to="body">
    <!-- 配置弹窗挂到 body，避免被画布缩放和组件 overflow 裁剪。 -->
    <QueryConditionConfiguration
      :query-element="element"
      @queryData="queryData"
      ref="queryConfig"
      @reRenderAll="reRenderAll"
    ></QueryConditionConfiguration>
  </Teleport>
</template>

<style lang="less" scoped>
.v-query-container {
  width: 100%;
  height: 100%;
  overflow: auto;
  position: relative;
  --ed-font-size-base: v-bind(boxWidth);

  :deep(.ed-select-v2 .ed-select-v2__selection .ed-tag),
  :deep(.select-trigger .ed-select__tags .ed-tag) {
    background-color: v-bind(tagColor);
  }

  :deep(.ed-input),
  :deep(.ed-date-editor) {
    --ed-input-height: v-bind(boxHeight);
  }

  :deep(.ed-select__wrapper),
  :deep(.text-search-select .ed-input__wrapper),
  :deep(.text-search-select .ed-select__wrapper) {
    height: v-bind(boxHeight);
  }

  .ed-button--primary {
    --ed-button-bg-color: v-bind(btnHoverStyle.rawColor);
    --ed-button-border-color: v-bind(btnHoverStyle.rawColor);
    --ed-button-hover-border-color: v-bind(btnHoverStyle.hoverColor);
    --ed-button-hover-bg-color: v-bind(btnHoverStyle.hoverColor);
    background-color: v-bind(btnPrimaryColor);
  }

  .ed-button--primary.ed-button--primary.ed-button--primary:hover,
  .ed-button--primary.ed-button--primary.ed-button--primary:focus {
    background-color: v-bind(btnPrimaryHoverColor);
  }

  .ed-button--primary.ed-button--primary.ed-button--primary:active {
    background-color: v-bind(btnPrimaryActiveColor);
    border-color: v-bind(btnPrimaryHoverColor);
  }

  :deep(.ed-tag) {
    --ed-tag-font-size: v-bind(boxWidth);
  }

  :deep(.ed-select-v2),
  :deep(.ed-select__wrapper) {
    font-size: v-bind(boxWidth);
  }

  .no-list-label {
    width: 100%;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 0;
    .container {
      width: 100%;
      justify-content: center;
      color: #646a73;
      text-align: center;
      font-size: 16px;
      font-style: normal;
      font-weight: 400;
      line-height: 24px;
      .ed-button {
        font-size: 16px;
        font-style: normal;
        font-weight: 400;
        line-height: 24px;
      }
    }
  }
  .title {
    color: #1f2329;
    font-feature-settings: 'clig' off, 'liga' off;
    font-size: 14px;
    font-style: normal;
    font-weight: 500;
    line-height: 22px;
    letter-spacing: -0.1px;
  }
}
.v-query {
  width: 100%;
  height: 100%;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.87);
  align-items: center;
  position: relative;
  display: flex;
  margin: auto 0;
  padding: 16px 4px 4px 4px;
  .query-fields-container {
    display: flex;
    flex-wrap: wrap;
    width: 100%;
    height: 100%;
  }
  .query-item {
    position: relative;
    line-height: 28px;
    margin: 5px 16px 5px 0;
    max-width: 100%;
    min-width: 60px;
    .query-field {
      position: relative;
      .label {
        display: flex;
        overflow: hidden;
        color: #1f2329;

        .label-wrapper {
          visibility: visible;
          pointer-events: auto;
          cursor: auto;
          line-height: 16px;
          color: #575757;
          box-sizing: border-box;
          margin: 0;
          padding: 0;
          display: flex;
          flex: 1 1 0;
          overflow: hidden;
        }
        .label-wrapper-text {
          position: relative;
          cursor: pointer;
          flex: 0 1 auto;
          max-width: 100%;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          color: #1f2329;
          font-size: 14px;
          font-style: normal;
          font-weight: 400;
          line-height: 22px;
        }

        .required {
          font-size: 14px;
          color: #f54a45;
          margin-left: 3px;
          line-height: 22px;
        }
        .label-wrapper-tooltip {
          align-items: center;
          background: #fff;
          border-radius: 2px;
          font-size: 16px;
          display: none;
          flex: 0 0 auto;
          height: 16px;
          line-height: 16px;
          color: #575757;
          white-space: nowrap;
        }
      }

      .query-select {
        display: flex;
        flex-wrap: wrap;
        line-height: 28px;
        position: relative;

        :deep(.ed-date-editor) {
          .ed-input__wrapper {
            width: 100%;
          }
        }
      }
    }
  }
  .query-button {
    align-self: flex-end;
    line-height: 40px;
    margin: auto 0 5px auto;
    z-index: 0;
  }

  &.title-show {
    height: calc(100% - 22px);
  }

  &.vertical {
    .query-fields-container {
      .query-field {
        padding-top: 30px;

        &:hover {
          .label-wrapper-tooltip {
            display: inline-flex !important;
            cursor: pointer;
            padding: 4px 8px;
            height: 26px;
            width: 58px;
            border-radius: 6px;
            border: 1px solid #dee0e3;
            background: #fff;
            box-shadow: 0px 4px 8px 0px rgba(31, 35, 41, 0.1);
          }
        }
        .label {
          align-items: center;
          height: 16px;
          left: 0;
          line-height: 16px;
          max-width: 100%;
          position: absolute;
          right: 0;
          top: 0;
          overflow: visible;
        }
      }
    }
  }

  &.horizontal {
    line-height: 1.5;
    color: rgba(0, 0, 0, 0.87);
    align-items: center;
    display: flex;
    margin: auto 0;
    .query-fields-container {
      align-items: flex-start;

      .query-field {
        align-items: center;
        display: flex;
        .label {
          visibility: visible;
          pointer-events: auto;
          cursor: auto;
          line-height: 28px;
          box-sizing: border-box;
          flex: 0 0 auto;
          margin-right: 8px;

          .label-wrapper {
            max-width: 200px;
          }
          .label-wrapper-tooltip {
            position: absolute;
            right: 0;
            top: -21px;
            z-index: 11;
            padding: 4px 8px;
            height: 26px;
            width: 58px;
            border-radius: 6px;
            border: 1px solid #dee0e3;
            background: #fff;
            box-shadow: 0px 4px 8px 0px rgba(31, 35, 41, 0.1);
          }
        }
        &:hover {
          .label-wrapper-tooltip {
            display: block;
            cursor: pointer;
          }
        }
      }
    }
  }
}
</style>
<style lang="less">
.label-wrapper-text_tooltip {
  max-width: 200px;
  white-space: wrap;
}
.load-select {
  .ed-select-dropdown__list {
    & > div {
      &:nth-child(1) {
        .ed-radio__inner::after {
          display: none !important;
        }
      }
    }
  }
  .ed-select-dropdown {
    &:nth-child(1) {
      .ed-checkbox__inner::after {
        display: none !important;
      }
    }
  }
}
</style>
