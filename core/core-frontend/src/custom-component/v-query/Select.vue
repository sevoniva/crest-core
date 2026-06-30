<script lang="ts" setup>
import {
  ref,
  toRefs,
  PropType,
  onBeforeMount,
  onMounted,
  shallowRef,
  watch,
  nextTick,
  computed,
  inject,
  onBeforeUnmount,
  onUnmounted,
  defineAsyncComponent,
  Ref
} from 'vue'
import { enumValueObj, type EnumValue, getEnumValue } from '@/api/dataset'
import { cloneDeep, debounce } from 'lodash-es'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import router from '@/router'
import Flat from './Flat.vue'
import eventBus from '@/utils/eventBus'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useI18n } from '@/hooks/web/useI18n'
import colorFunctions from 'less/lib/less/functions/color.js'
import colorTree from 'less/lib/less/tree/color.js'
import { colorStringToHex } from '@/utils/color'
import { isMobile } from '@/utils/utils'
import { ElMessage } from 'element-plus-secondary'

interface SelectConfig {
  name?: string
  selectValue: any
  required: false
  defaultMapValue: any
  mapValue: any
  displayFormat?: number
  defaultValue: any
  checkedFieldsMap: object
  displayType: string
  showEmpty: boolean
  id: string
  sortList?: string[]
  queryConditionWidth: number
  placeholder: string
  resultMode: number
  displayId: string
  defaultValueFirstItem: boolean
  sort: string
  sortId: string
  checkedFields: string[]
  dataset: {
    id: string
  }
  field: {
    id: string
  }
  optionValueSource: number
  defaultValueCheck: boolean
  multiple: boolean
  valueSource: any[]
  optionFilter: any[]
}

const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
// 查询下拉组件入参，config 承载条件配置，isConfig 区分配置态和运行态
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        selectValue: '',
        required: false,
        displayFormat: 0,
        queryConditionWidth: 0,
        resultMode: 0,
        defaultValue: '',
        displayType: '',
        defaultValueCheck: false,
        optionValueSource: 0,
        multiple: false,
        checkedFieldsMap: {},
        optionFilter: []
      }
    }
  },
  isConfig: {
    type: Boolean,
    default: false
  }
})
const { config } = toRefs(props)
let enumValueArr: any[] = []
// 当前下拉选中值，运行态与配置态都会通过它驱动展示
const selectValue = ref()
// 选项加载状态，覆盖远程枚举和值来源切换过程
const loading = ref(false)
// 当前是否多选，异步同步配置中的 multiple 字段
const multiple = ref(false)
// 当前可选项列表，统一转换为 el-select-v2 可消费的 label/value 结构
const options = shallowRef<any[]>([])
const unMountSelect: Ref = inject('unmount-select')
const placeholder: Ref = inject('placeholder')
const releaseSelect = inject('release-unmount-select', Function, true)
const queryDataForId = inject('query-data-for-id', Function, true)
const isConfirmSearch = inject('is-confirm-search', Function, true)
const queryConditionWidth = inject('com-width', Function, true)
const cascadeList = inject('cascade-list', Function, true)
const setCascadeDefault = inject('set-cascade-default', Function, true)
const customStyle: any = inject('$custom-style-filter')

// 下拉占位文案，配置隐藏占位时保留空格以维持组件高度
const placeholderText = computed(() => {
  if (placeholder?.value?.placeholderShow) {
    return ['', undefined].includes(props.config.placeholder) ? ' ' : props.config.placeholder
  }
  return ' '
})

// 当前是否为移动端数据大屏，用于切换到移动端弹层选择器
const isMobileDataV = computed(() => {
  return dvMainStore.dvInfo.type === 'dataV' && isMobile()
})

// 移动端下拉弹层按需加载，避免桌面端增加初始体积
const VanPopupSelect = defineAsyncComponent(() => import('./VanPopupSelect.vue'))

// 当前查询组件的级联关系列表，由父级查询组件提供
const cascade = computed(() => {
  return cascadeList() || []
})
let time
// 首项默认值启用且值来源为数据集字段时，配置态禁止手动选择其它项
const disabledFirstItem = computed(() => {
  const { defaultValueFirstItem, optionValueSource } = props.config
  return defaultValueFirstItem && optionValueSource === 1
})
// 当前可视化资源 id，优先从画布状态读取，路由参数作为兼容回退
const currentVisualizationId = () => dvMainStore.dvInfo?.id || router.currentRoute.value.query.dvId
// 根据展示值反查真实字段值，用于级联查询时传递字段映射后的值
const setDefaultMapValue = arr => {
  const { displayId, field } = config.value
  if (config.value.optionValueSource !== 1) {
    return []
  }
  let defaultMapValue = {}
  let defaultValue = []
  arr.forEach(ele => {
    defaultMapValue[ele] = []
  })
  enumValueArr.forEach(ele => {
    if (defaultMapValue[ele[displayId || field?.id]]) {
      defaultMapValue[ele[displayId || field?.id]].push(ele[field?.id])
    }
  })
  Object.values(defaultMapValue).forEach(ele => {
    defaultValue = [...new Set([...defaultValue, ...(ele as unknown as string[])])]
  })
  return defaultValue
}

onUnmounted(() => {
  clearTimeout(time)
  enumValueArr = []
})

// 将当前条件的映射值写回级联链路，供后续条件读取父级选择
const setCascadeValueBack = val => {
  cascade.value.forEach(ele => {
    ele.forEach(item => {
      if (item.datasetId.split('--')[1] === config.value.id) {
        if (props.isConfig) {
          item.selectValue = Array.isArray(val) ? [...val] : val
        }
        item.currentSelectValue = Array.isArray(val) ? [...val] : val
      }
    })
  })
}

// 运行态值变化后触发后续级联条件重新加载选项
const emitCascade = () => {
  cascade.value.forEach(ele => {
    let trigger = false
    ele.forEach(item => {
      if (item.datasetId.split('--')[1] === config.value.id) {
        trigger = true
      } else if (trigger) {
        useEmitt().emitter.emit(`${item.datasetId.split('--')[1]}-select`)
        trigger = false
      }
    })
  })
}

// 配置态值变化时返回受影响的后续级联条件 id 列表
const emitCascadeConfig = () => {
  const arr = []
  cascade.value.forEach(ele => {
    let trigger = false
    ele.forEach(item => {
      if (item.datasetId.split('--')[1] === config.value.id) {
        trigger = true
      } else if (trigger) {
        arr.push(item.datasetId.split('--')[1])
        trigger = false
      }
    })
  })
  return arr
}

// 根据级联链路生成当前远程选项查询需要的过滤条件
const getCascadeFieldId = () => {
  const filter = []
  cascade.value.forEach(ele => {
    let condition = null
    ele.forEach(item => {
      const [_, queryId, fieldId] = item.datasetId.split('--')
      if (queryId === config.value.id && condition) {
        if (item.fieldId) {
          condition.fieldId = item.fieldId
        }
        filter.push(condition)
      } else {
        if (props.isConfig) {
          const value = normalizeCascadeValue(item.selectValue)
          if (value.length) {
            condition = {
              fieldId: fieldId,
              operator: 'in',
              value
            }
          }
        } else {
          const value = normalizeCascadeValue(item.currentSelectValue)
          if (value.length) {
            condition = {
              fieldId: fieldId,
              operator: 'in',
              value
            }
          }
        }
      }
    })
  })
  return filter
}

// 将级联值统一转换为非空数组，过滤空值后用于接口过滤条件
const normalizeCascadeValue = (value: any) => {
  const values = Array.isArray(value) ? value : [value]
  return values.filter(ele => ele !== undefined && ele !== null && ele !== '')
}

// 处理选中值变化，配置态写默认值，运行态写查询值并触发查询确认
const handleValueChange = () => {
  const value = Array.isArray(selectValue.value) ? [...selectValue.value] : selectValue.value
  if (!props.isConfig) {
    config.value.selectValue = Array.isArray(selectValue.value)
      ? [...selectValue.value]
      : selectValue.value
    config.value.mapValue = setDefaultMapValue(
      Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
    )
    setCascadeValueBack(config.value.mapValue)
    emitCascade()
    nextTick(() => {
      isConfirmSearch(config.value.id)
    })
    return
  }

  setCascadeDefault(emitCascadeConfig())

  config.value.defaultValue = value
  config.value.mapValue = setDefaultMapValue(
    Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
  )
  config.value.defaultMapValue = setDefaultMapValue(
    Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
  )
  setCascadeValueBack(config.value.mapValue)
}

// 展示类型切换后重置默认值和首项默认值设置
const displayTypeChange = () => {
  if (!props.isConfig) return
  config.value.defaultValue = config.value.multiple ? [] : undefined
  selectValue.value = config.value.multiple ? [] : undefined
  config.value.defaultValueFirstItem = false
}

// 根据绑定字段批量获取默认枚举值，适用于内置字段来源模式
const handleFieldIdDefaultChange = (val: string[]) => {
  loading.value = true
  getEnumValue({
    fieldIds: val,
    resultMode: config.value.resultMode || 0,
    visualizationId: currentVisualizationId()
  })
    .then(res => {
      options.value = (res || [])
        .filter(ele => {
          return (
            ele !== null &&
            ((config.value.optionFilter &&
              config.value.optionFilter.length > 0 &&
              config.value.optionFilter.includes(ele)) ||
              !config.value.optionFilter ||
              config.value.optionFilter.length === 0)
          )
        })
        .map(ele => {
          return {
            label: `${ele}`,
            value: `${ele}`
          }
        })
    })
    .finally(() => {
      loading.value = false
      if (config.value.defaultValueCheck) {
        selectValue.value = Array.isArray(config.value.defaultValue)
          ? [...config.value.defaultValue]
          : config.value.defaultValue
      } else {
        selectValue.value = Array.isArray(selectValue.value)
          ? [...selectValue.value]
          : selectValue.value
      }
      requiredComp()
      if (options.value) setEmptyData()
    })
}

// 按自定义排序列表重排选项，未出现在排序列表中的值排在后面
const customSort = () => {
  if (config.value.sortList?.length && config.value.sort === 'customSort') {
    const sortList = config.value.sortList
    options.value = [...options.value].sort((a, b) => {
      const aIndex = sortList.indexOf(a.value)
      const bIndex = sortList.indexOf(b.value)
      if (aIndex === -1 && bIndex === -1) {
        return 0
      }
      if (aIndex === -1) {
        return 1
      }
      if (bIndex === -1) {
        return -1
      }
      return aIndex - bIndex
    })
  }
}

// 根据远程字段和值来源配置加载枚举选项，并同步默认值、权限过滤和级联值
const handleFieldIdChange = (val: EnumValue) => {
  let change = false
  loading.value = true
  enumValueObj(val)
    .then(res => {
      let oldArr = []
      if (selectValue.value?.length && config.value.multiple) {
        oldArr = [...selectValue.value]
      }
      enumValueArr = [...(res || [])]
      options.value = [
        ...new Set(
          (res || []).map(ele => {
            return `${ele[val.displayId || val.queryId]}`
          })
        )
      ]
        .filter(ele => {
          return (
            (config.value.optionFilter &&
              config.value.optionFilter.length > 0 &&
              config.value.optionFilter.includes(ele)) ||
            !config.value.optionFilter ||
            config.value.optionFilter.length === 0
          )
        })
        .map(ele => {
          return {
            label: `${ele}`,
            value: `${ele}`,
            checked: oldArr.includes(ele)
          }
        })
      customSort()
      if (!res?.length) {
        options.value = []
        selectValue.value = config.value.multiple ? [] : undefined
        config.value.defaultValue = selectValue.value
      }

      const valArr = options.value.map(ele => ele.value)

      if (
        config.value.multiple &&
        Array.isArray(selectValue.value) &&
        selectValue.value.length &&
        !selectValue.value.every(ele => valArr.includes(ele))
      ) {
        const delArr = selectValue.value.filter(ele => !valArr.includes(ele))
        selectValue.value = selectValue.value.filter(ele => valArr.includes(ele))
        options.value = options.value.filter(ele => !delArr.includes(ele.value))
        config.value.defaultValue = selectValue.value
        change = true
      }

      if (!config.value.multiple && selectValue.value && !valArr.includes(selectValue.value)) {
        options.value = options.value.filter(ele => selectValue.value !== ele.value)
        selectValue.value = undefined
        config.value.defaultValue = selectValue.value
        change = true
      }

      if (change) {
        config.value.mapValue = setDefaultMapValue(
          Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
        )
        config.value.defaultMapValue = setDefaultMapValue(
          Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
        )
        setCascadeValueBack(config.value.mapValue)
      }
    })
    .finally(() => {
      let changeAuth = change
      change = false
      loading.value = false
      if (disabledFirstItem.value && config.value.defaultValueCheck) {
        time = setTimeout(() => {
          clearTimeout(time)
          setDefaultValueFirstItem()
        }, 300)
        return
      }

      if (config.value.defaultValueCheck && !isFromRemote.value) {
        selectValue.value = Array.isArray(config.value.defaultValue)
          ? [...config.value.defaultValue]
          : config.value.defaultValue
        let shouldReSearch = false
        if (unMountSelect.value.includes(config.value.id)) {
          const mapValue = setDefaultMapValue(
            Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
          )
          if (mapValue?.length !== config.value.defaultMapValue?.length) {
            shouldReSearch = true
          } else if (!mapValue.every(value => config.value.defaultMapValue.includes(value))) {
            shouldReSearch = true
          }
          releaseSelect(config.value.id)
        }
        config.value.mapValue = setDefaultMapValue(
          Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
        )

        if (shouldReSearch || changeAuth) {
          queryDataForId(config.value.id)
        }
      } else {
        selectValue.value = Array.isArray(selectValue.value)
          ? [...selectValue.value]
          : selectValue.value
      }
      if (config.value?.required && config.value?.optionFilter?.length > 0) {
        const isValid = selectValue.value?.some(value =>
          options.value?.some(option => option.value === value)
        )
        if (!isValid) {
          config.value.selectValue = null
          ElMessage({
            message: `【${config.value?.name}】${t('v_query.before_querying')}`,
            type: 'error',
            duration: 3000
          })
        }
      }
      setCascadeValueBack(config.value.mapValue)
      isFromRemote.value = false
    })
}

// 下拉弹层显示状态，用于加载态样式和弹层类名
const visible = ref(false)

// 空值展示配置变化时，重新插入或移除空值选项
watch(
  () => config.value.showEmpty,
  () => {
    setEmptyData()
  }
)

// 启用首项默认值时，将当前选项第一项写入默认值或运行态选择值
const setDefaultValueFirstItem = () => {
  if (!options.value.length) return
  selectValue.value = config.value.multiple ? [options.value[0].value] : options.value[0].value
  const value = Array.isArray(selectValue.value) ? [...selectValue.value] : selectValue.value
  if (!props.isConfig) {
    config.value.selectValue = Array.isArray(selectValue.value)
      ? [...selectValue.value]
      : selectValue.value
    config.value.mapValue = setDefaultMapValue(
      Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
    )
    setCascadeValueBack(config.value.mapValue)
    emitCascade()
    nextTick(() => {
      isConfirmSearch(config.value.id, true)
    })
    return
  }

  setCascadeDefault(emitCascadeConfig())

  config.value.defaultValue = value
  config.value.mapValue = setDefaultMapValue(
    Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
  )
  config.value.defaultMapValue = setDefaultMapValue(
    Array.isArray(selectValue.value) ? [...selectValue.value] : [selectValue.value]
  )
  setCascadeValueBack(config.value.mapValue)
}

// 首项默认值开关变化时立即尝试回填第一项
watch(
  () => config.value.defaultValueFirstItem,
  val => {
    if (!val) return
    setDefaultValueFirstItem()
  }
)

// 根据空值展示开关维护固定的空数据选项
const setEmptyData = () => {
  const { showEmpty, displayType, optionValueSource } = config.value
  if (+displayType !== 0 || optionValueSource === 1) return
  const [s] = options.value
  if (showEmpty) {
    if (s?.value !== '_empty_$') {
      options.value = [{ label: t('v_query.empty_data'), value: '_empty_$' }, ...options.value]
    }
  } else {
    if (s?.value === '_empty_$') {
      options.value = options.value.slice(1)
    }
  }
}

// 配置态默认值变化时同步本地选中值和多选状态
watch(
  () => config.value.defaultValue,
  val => {
    if (config.value.multiple) {
      selectValue.value = Array.isArray(val) ? [...val] : val
    }
    nextTick(() => {
      multiple.value = config.value.multiple
    })
  }
)

// 查询条件 id 变化时重新初始化本地值和选项
watch(
  () => config.value.id,
  () => {
    init()
  }
)

// 运行态外部选中值变化时同步本地选择器状态
watch(
  () => config.value.selectValue,
  val => {
    if (props.isConfig) return
    if (config.value.multiple) {
      selectValue.value = Array.isArray(val) ? [...val] : val
    }
    nextTick(() => {
      multiple.value = config.value.multiple
      if (!config.value.multiple) {
        selectValue.value = Array.isArray(config.value.selectValue)
          ? [...config.value.selectValue]
          : config.value.selectValue
      }
    })
  }
)

// 配置态多选开关变化时重置本地值，并在需要时回填首项默认值
watch(
  () => config.value.multiple,
  val => {
    if (!props.isConfig) return
    if (val) {
      selectValue.value = []
      setDefaultValueFirstItem()
    }
    nextTick(() => {
      multiple.value = val
      if (!val) {
        nextTick(() => {
          selectValue.value = undefined
          if (!config.value.defaultValueFirstItem || !config.value.defaultValueCheck) return
          setDefaultValueFirstItem()
        })
      }
    })
  }
)

// 字段、展示字段或排序配置变化时，防抖刷新选项
watch(
  [
    () => config.value.field.id,
    () => config.value.displayId,
    () => config.value.sort,
    () => config.value.sortId
  ],
  val => {
    if (!val) return
    debounceOptions(1)
  }
)

// 自定义排序列表变化时，在当前选项上立即应用排序
watch([() => config.value.sortList], val => {
  if (!val?.length || config.value.sort !== 'customSort') return
  customSort()
})

// 值来源模式变化时重置无效值，并刷新对应来源的选项
watch(
  () => config.value.optionValueSource,
  (valNew, newOld) => {
    if (!props.isConfig) return
    if ([valNew, newOld].includes(2)) {
      selectValue.value = Array.isArray(selectValue.value) ? [] : undefined
      config.value.selectValue = cloneDeep(selectValue.value)
      config.value.defaultValue = cloneDeep(selectValue.value)
    }
    debounceOptions(valNew)
    config.value.defaultValueFirstItem = false
  }
)

// 绑定组件和字段映射变化时，刷新内置字段来源选项
watch(
  [() => config.value.checkedFields, () => config.value.checkedFieldsMap],
  () => {
    debounceOptions(config.value.optionValueSource)
  },
  {
    deep: true
  }
)

// 远程搜索关键字
const searchText = ref('')
// 当前选项刷新是否来自级联远程触发，用于避免覆盖运行态默认值
const isFromRemote = ref(false)

// 手动值来源列表变化时刷新选项
watch(
  () => config.value.valueSource,
  () => {
    config.value.optionValueSource === 2 && debounceOptions(2)
  }
)

// 必填条件在权限过滤后校验选中值是否仍可用，不可用时清空并提示
const requiredComp = () => {
  if (config.value?.required && config.value?.optionFilter?.length > 0) {
    const isValid = hasIntersection(options.value, selectValue.value)
    if (!isValid) {
      config.value.selectValue = null
      ElMessage({
        message: `【${config.value?.name}】${t('v_query.before_querying')}`,
        type: 'error',
        duration: 3000
      })
    }
  }
}

// 判断当前选中值和可选项是否存在交集，用于必填权限过滤校验
const hasIntersection = (options, selectValue) => {
  if (!Array.isArray(options) || options.length === 0) {
    return false
  }
  if (selectValue == null) {
    return false
  }
  const selectedValues = Array.isArray(selectValue) ? selectValue : [selectValue]
  if (selectedValues.length === 0) {
    return false
  }
  const optionValues = options.map(option => option.value)

  return selectedValues.some(value => optionValues.includes(value))
}

// 根据值来源类型加载选项，支持内置字段、远程数据集字段和手动输入三种来源
const setOptions = (num: number) => {
  if (num !== config.value.optionValueSource) return
  const {
    optionValueSource,
    checkedFieldsMap,
    checkedFields,
    field,
    valueSource,
    displayId,
    sort,
    sortId
  } = config.value
  switch (optionValueSource) {
    case 0:
      const arr = Object.values(checkedFieldsMap).filter(ele => !!ele) as string[]
      if (!!checkedFields.length && !!arr.length) {
        handleFieldIdDefaultChange(
          checkedFields.map(ele => checkedFieldsMap[ele]).filter(ele => !!ele)
        )
      } else {
        options.value = []
      }
      break
    case 1:
      if (field.id) {
        handleFieldIdChange({
          queryId: field.id,
          displayId: displayId || field.id,
          sort: sort === 'customSort' ? 'asc' : sort,
          sortId: sort === 'customSort' ? '' : sortId,
          resultMode: config.value.resultMode || 0,
          searchText: searchText.value,
          filter: getCascadeFieldId(),
          visualizationId: currentVisualizationId()
        })
      } else {
        options.value = []
      }
      break
    case 2:
      options.value = cloneDeep(
        (valueSource || [])
          .filter(ele => {
            return (
              ele !== null &&
              ((config.value.optionFilter &&
                config.value.optionFilter.length > 0 &&
                config.value.optionFilter.includes(ele)) ||
                !config.value.optionFilter ||
                config.value.optionFilter.length === 0)
            )
          })
          .map(ele => {
            return {
              label: `${ele}`,
              value: `${ele}`,
              checked: Array.isArray(selectValue.value)
                ? selectValue.value.includes(`${ele}`)
                : selectValue.value === ele
            }
          })
      )
      requiredComp()
      setEmptyData()
      break
    default:
      break
  }
}

const debounceOptions = debounce(setOptions, 300)

// 初始化本地选中值和多选状态，并按当前值来源加载选项
const init = () => {
  const { defaultValueCheck, multiple: plus, defaultValue, optionValueSource } = config.value
  if (defaultValueCheck) {
    config.value.selectValue = Array.isArray(defaultValue) ? [...defaultValue] : defaultValue
    selectValue.value = Array.isArray(defaultValue) ? [...defaultValue] : defaultValue
  } else {
    config.value.selectValue = plus ? [] : undefined
    selectValue.value = plus ? [] : undefined
  }
  nextTick(() => {
    multiple.value = config.value.multiple
  })
  debounceOptions(optionValueSource)
}

// 计算运行态查询条件宽度，配置为空时回退到父级提供的组件宽度
const getCustomWidth = () => {
  if (placeholder?.value?.placeholderShow) {
    if (props.config.queryConditionWidth === undefined) {
      return queryConditionWidth()
    }
    return props.config.queryConditionWidth
  }
  return 227
}

// 标准下拉选择器样式，配置态不固定宽度，运行态使用查询条件宽度
const selectStyle = computed(() => {
  return props.isConfig ? {} : { width: getCustomWidth() + 'px' }
})

// 平铺展示模式样式，配置态使用固定宽度，运行态跟随查询条件宽度
const selectStyleFlat = computed(() => {
  return props.isConfig ? { width: '415px' } : { width: getCustomWidth() + 'px' }
})

// 多选选择器组件引用，用于外部点击时收起弹层
const mult = ref()
// 单选选择器组件引用，用于外部点击时收起弹层
const single = ref()

// 级联上游变化时刷新远程数据集字段来源选项
const getOptionFromCascade = () => {
  if (config.value.optionValueSource !== 1 || ![0, 2, 5].includes(+config.value.displayType)) return
  isFromRemote.value = true
  debounceOptions(1)
}
// 当前选择器被点击时通知其它选择器收起弹层
const selectHideClick = () => {
  useEmitt().emitter.emit('select-hide_lick', config.value.id)
}

// 响应其它选择器点击事件，收起当前单选或多选弹层
const hideClick = id => {
  if (id === config.value.id) return
  const vnode = single.value || mult.value
  vnode?.handleClickOutside?.()
}

onBeforeMount(() => {
  init()
  useEmitt({
    name: `${config.value.id}-select`,
    callback: getOptionFromCascade
  })

  useEmitt({
    name: 'select-hide_lick',
    callback: hideClick
  })
})

// 当前选择器是否渲染在数据大屏环境中，用于切换弹层滚动条样式
const isDataV = ref(false)

// 下拉弹层样式类名，按加载状态和大屏环境追加滚动条样式
const popperClass = computed(() => {
  let str = 'filter-select-popper_class'
  if (visible.value) {
    str = 'load-select ' + str
  }

  if (isDataV.value) {
    str = str + ' color-scrollbar__thumb'
  }
  return str
})

onMounted(() => {
  isDataV.value =
    Boolean(document.querySelector('#canvas-dv-outer')) ||
    Boolean(document.querySelector('.datav-preview'))
})

// 根据自定义背景色计算标签背景色，保证深色大屏中标签可读
const tagColor = computed(() => {
  if (
    !customStyle ||
    ['#FFFFFF', 'rgba(255, 255, 255, 1)', 'rgb(255, 255, 255)'].includes(customStyle.background)
  )
    return ''
  if (customStyle.background === '#131C42') return 'rgb(38, 53, 82)'
  const hexColor = customStyle.background.startsWith('#')
    ? customStyle.background
    : colorStringToHex(customStyle.background)

  return colorFunctions
    .mix(new colorTree('ffffff'), new colorTree(hexColor.substr(1)), { value: 20 })
    .toRGB()
})

// 多选标签宽度，按查询条件宽度分配两列展示空间
const tagWidth = computed(() => {
  return (getCustomWidth() - 65) / 2 + 'px'
})

// 多选标签文本宽度，预留标签内边距和关闭图标空间
const tagTextWidth = computed(() => {
  return (getCustomWidth() - 65) / 2 - 20 + 'px'
})

// 平铺模式当前激活项，统一转为数组供 Flat 组件判断选中状态
const activeItems = computed(() => {
  return Array.isArray(selectValue.value) ? selectValue.value : [selectValue.value]
})

// 平铺选项点击处理，按单选或多选模式更新选中值
const handleItemClick = (item: any) => {
  if (multiple.value) {
    if (selectValue.value.includes(item)) {
      selectValue.value = selectValue.value.filter(ele => ele !== item)
    } else {
      selectValue.value = [...selectValue.value, item]
    }
  } else {
    selectValue.value = selectValue.value === item ? undefined : item
  }

  handleValueChange()
}

// 画布组件被点击时收起当前下拉，避免弹层悬停在非当前组件上
const componentClick = () => {
  mult.value?.blur()
  single.value?.blur()
}

onMounted(() => {
  eventBus.on('componentClick', componentClick)
})
onBeforeUnmount(() => {
  eventBus.off('componentClick', componentClick)
})

// 清空当前选择，并同步配置或运行态查询值
const onClear = () => {
  selectValue.value = multiple.value ? [] : undefined
  handleValueChange()
}

// 移动端弹层确认后写回选择值，并复用统一变更处理
const onConfirm = (val: any) => {
  selectValue.value = multiple.value ? [...val] : val[0]
  handleValueChange()
}

defineExpose({
  displayTypeChange,
  mult,
  single
})
</script>

<template>
  <Flat
    @handleItemClick="handleItemClick"
    :options="options"
    :selectStyle="selectStyleFlat"
    v-loading="loading"
    :multiple="multiple"
    :disabled="disabledFirstItem && props.isConfig"
    :activeItems="activeItems"
    v-if="config.displayFormat === 1"
  ></Flat>
  <el-select-v2
    v-else-if="multiple"
    key="multiple"
    ref="mult"
    v-model="selectValue"
    :placeholder="placeholderText"
    v-loading="loading"
    filterable
    @click="selectHideClick"
    @change="handleValueChange"
    :popper-class="popperClass"
    multiple
    show-checked
    :tagColor="tagColor"
    scrollbar-always-on
    :disabled="disabledFirstItem && props.isConfig"
    clearable
    :style="selectStyle"
    collapse-tags
    :options="options"
    collapse-tags-tooltip
  ></el-select-v2>
  <el-select-v2
    v-else
    v-model="selectValue"
    key="single"
    @click="selectHideClick"
    :placeholder="placeholderText"
    scrollbar-always-on
    v-loading="loading"
    @change="handleValueChange"
    clearable
    :disabled="disabledFirstItem && props.isConfig"
    ref="single"
    :style="selectStyle"
    filterable
    radio
    :popper-class="popperClass"
    :options="options"
  >
    <template #default="{ item }">
      <el-radio-group v-model="selectValue">
        <el-radio :title="item.label" :label="item.value">{{ item.label }}</el-radio>
      </el-radio-group>
    </template>
  </el-select-v2>
  <VanPopupSelect
    @onClear="onClear"
    @onConfirm="onConfirm"
    :options="options"
    :selectValue="selectValue"
    :multiple="multiple"
    v-if="isMobileDataV"
  ></VanPopupSelect>
</template>

<style lang="less">
.filter-select-popper_class {
  --ed-fill-color-light: #f5f7fa47;
  font-family: var(--crest-canvas_custom_font);
  .ed-vl__window.ed-select-dropdown__list {
    min-width: 200px;
  }
  .ed-select-dropdown {
    width: auto !important;
    min-width: 150px;
  }
  .ed-select-dropdown__option-item {
    .ed-checkbox__label:hover {
      color: #1f2329;
    }
    .ed-radio-group,
    .ed-checkbox {
      width: 100%;
      .ed-radio {
        width: 100%;
      }
      .ed-radio__label,
      .ed-checkbox__label {
        width: calc(100% - 16px);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  .ed-select-btn-group {
    color: #1f2329;
  }
}

.color-scrollbar__thumb {
  .ed-scrollbar__thumb {
    background: #bbbfc4 !important;
    opacity: 1 !important;
  }
}
</style>

<style lang="less" scoped>
:deep(.ed-select__selected-item) {
  .ed-tag {
    max-width: v-bind(tagWidth) !important;
  }

  .ed-select__tags-text {
    max-width: v-bind(tagTextWidth) !important;
  }
}
</style>
