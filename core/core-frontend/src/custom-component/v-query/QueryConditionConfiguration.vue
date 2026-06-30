<script lang="ts" setup>
import more_v from '@/assets/svg/more_v.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_drag_outlined from '@/assets/svg/icon_drag_outlined.svg'
import icon_visible_outlined from '@/assets/svg/icon_visible_outlined.svg'
import passwordInvisible from '@/assets/svg/password-invisible.svg'
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_dataset from '@/assets/svg/icon_dataset.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_warning_filled from '@/assets/svg/icon_warning_filled.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import {
  ref,
  reactive,
  nextTick,
  computed,
  shallowRef,
  toRefs,
  watch,
  defineAsyncComponent,
  provide,
  unref
} from 'vue'
import { storeToRefs } from 'pinia'
import { enumValueObj } from '@/api/dataset'
import CustomSortFilter from './CustomSortFilter.vue'
import { addQueryCriteriaConfig } from './options'
import { getCustomTime } from './time-format'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import {
  getThisStart,
  getThisEnd,
  getLastStart,
  getAround,
  getAroundStart,
  getCustomRange
} from './time-format-dayjs'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useI18n } from '@/hooks/web/useI18n'
import { fieldType } from '@/utils/attr'
import { ElMessage, ElSelect, ElMessageBox } from 'element-plus-secondary'
import type { DatasetDetail } from '@/api/dataset'
import { getDsDetailsWithPerm, sqlParams, listFieldsWithPermissions } from '@/api/dataset'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import TreeFieldDialog from '@/custom-component/v-query/TreeFieldDialog.vue'
import { cloneDeep } from 'lodash-es'
import { datasetTree as fetchDatasetTree } from '@/api/dataset'
import { Tree } from '@/views/visualized/data/dataset/form/CreatDsGroup.vue'
import draggable from 'vuedraggable'
import type { ManipulateType } from 'dayjs'
import dayjs from 'dayjs'
import ConditionDefaultConfiguration from '@/custom-component/v-query/ConditionDefaultConfiguration.vue'
import { iconChartMap } from '@/components/icon-group/chart-list'
import { iconFieldMap } from '@/components/icon-group/field-list'
import treeSort from '@/utils/treeSortUtils'
import { useCache } from '@/hooks/web/useCache'

const { t } = useI18n()
const { wsCache } = useCache()
const dvMainStore = dvMainStoreWithOut()
const { componentData, canvasViewInfo } = storeToRefs(dvMainStore)
// 默认值配置子组件引用，负责同步单选、多选和展示类型切换后的内部状态。
const defaultConfigurationRef = ref(null)
interface DatasetField {
  type?: string
  innerType?: string
  title: string
  id: string
  tableId: string
}

// 查询组件配置入参，包含当前查询组件标识和已保存的条件配置列表。
const props = defineProps({
  queryElement: {
    type: Object,
    default() {
      return {
        id: null,
        propValue: []
      }
    }
  }
})
// 配置弹窗显示状态。
const dialogVisible = ref(false)
// 重命名输入框引用列表，用于进入重命名模式后自动聚焦。
const renameInput = ref([])
// 手动输入值来源的临时编辑副本，确认前不直接污染条件配置。
const valueSource = ref([])
// 当前弹窗内编辑的查询条件列表。
const conditions = ref([])
// 字段组件全选状态，和半选状态共同驱动选择器头部展示。
const checkAll = ref(false)
// 当前处于重命名状态的条件信息，保存 id、名称和编辑状态。
const activeConditionForRename = reactive({
  id: '',
  name: '',
  visible: false
})
const datasetMap = {}
const snapshotStore = snapshotStoreWithOut()
// 收集画布中可被查询条件绑定的组件，递归展开页签和分组内的子组件。
const dfsComponentData = () => {
  let arr = componentData.value.filter(
    com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
  )
  componentData.value.forEach(ele => {
    if (ele.innerType === 'Tabs') {
      ele.propValue.forEach(itx => {
        arr = [
          ...arr,
          ...itx.componentData.filter(
            com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
          )
        ]

        itx.componentData.forEach(j => {
          if (j.component === 'Group') {
            // 页签内分组还需要继续展开，否则内部图表无法被查询条件绑定。
            arr = [
              ...arr,
              j.propValue.filter(
                com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
              )
            ]
          }
        })
      })
    } else if (ele.component === 'Group') {
      // 顶层分组中的普通图表直接纳入可绑定组件列表。
      arr = [
        ...arr,
        ele.propValue.filter(
          com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
        )
      ]
      ele.propValue.forEach(element => {
        if (element.innerType === 'Tabs') {
          // 分组内的页签需要继续展开到每个页签页面。
          element.propValue.forEach(itx => {
            arr = [
              ...arr,
              ...itx.componentData.filter(
                com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
              )
            ]
          })
        }
      })
    }
  })

  return arr.flat()
}

// 查询条件可绑定的数据集字段组件清单，由画布组件和视图元数据联合生成。
const datasetFieldList = computed(() => {
  return dfsComponentData()
    .map(ele => {
      const obj = canvasViewInfo.value[ele.id]
      if (!obj) return null
      const { id, title, tableId, type } = obj as DatasetField
      return !!id && !!tableId
        ? {
            id,
            type,
            title,
            tableId
          }
        : null
    })
    .filter(ele => !!ele)
})
// 级联变更时清理受影响条件的默认值和映射值，避免保留失效选择。
const setCascadeDefault = val => {
  conditions.value.forEach(ele => {
    if (
      ele.optionValueSource === 1 &&
      [0, 2, 5].includes(+ele.displayType) &&
      val.includes(ele.id)
    ) {
      ele.selectValue = Array.isArray(ele.selectValue) ? [] : undefined
      ele.defaultValue = Array.isArray(ele.defaultValue) ? [] : undefined
      ele.mapValue = Array.isArray(ele.mapValue) ? [] : undefined
      ele.defaultMapValue = Array.isArray(ele.defaultMapValue) ? [] : undefined
    }
  })
}
let cascadeArr = []
// 保存级联关系配置结果，等待确认时写回查询组件配置。
const saveCascade = arr => {
  cascadeArr = arr
}
// 向级联配置子组件暴露当前级联关系。
const getCascadeList = () => {
  return cascadeArr
}
provide('set-cascade-default', setCascadeDefault)
provide('cascade-list', getCascadeList)

// 当前正在编辑的查询条件对象。
const curComponent = ref()
// 手动输入值来源弹层引用。
const manual = ref()
// 当前激活的查询条件 id，用于左侧条件列表高亮和切换判断。
const activeCondition = ref('')
// 字段组件半选状态，和全选状态一起反映选择数量。
const isIndeterminate = ref(false)
// 数据集树数据，用于选择条件值来源数据集和树形结构数据集。
const datasetTree = shallowRef([])
// 当前画布可绑定组件对应的数据集明细列表。
const fields = ref<DatasetDetail[]>()

const { queryElement } = toRefs(props)
// 在扁平化字段集合中查找字段类型，供展示类型和参数兼容性判断。
const getDetype = (id, arr) => {
  return arr.flat().find(ele => ele.id === id)?.fieldType
}
// 判断当前条件是否已经具备可展示的字段配置。
const showConfiguration = computed(() => {
  if (!curComponent.value) return false
  if (!curComponent.value.checkedFields?.length) return false
  return curComponent.value.checkedFields.some(ele => {
    return !!curComponent.value.checkedFieldsMap[ele]
  })
})

// 判断当前条件是否存在跨组件字段类型冲突，控制错误提示展示。
const showTypeError = computed(() => {
  if (!curComponent.value) return false
  if (!curComponent.value.checkedFields?.length) return false
  if (!fields.value?.length) return false
  if (!!curComponent.value.parameters.length && isTimeParameter.value) {
    // 时间参数跨组件绑定时必须保持同一时间粒度结构。
    const timeArr = curComponent.value.parameters.map(ele => ele.type[1])
    const [typeOne] = timeArr
    if (timeArr.some(ele => ele !== typeOne)) {
      return true
    }
  }
  let displayTypeField = null
  let hasParameterNumArrType = 0
  let allNum =
    curComponent.value.checkedFields.every(id => {
      return curComponent.value.checkedFieldsMapArrNum?.[id]?.length
    }) && ['22'].includes(curComponent.value.displayType)
  return curComponent.value.checkedFields.some(id => {
    if (allNum) {
      return false
    }

    if (
      curComponent.value.checkedFieldsMapArrNum?.[id]?.length &&
      ['22', '2'].includes(curComponent.value.displayType)
    ) {
      // 数值范围字段和普通数值字段不能在同一条件中混用。
      if (hasParameterNumArrType === 0) {
        hasParameterNumArrType = 1
      }

      if (hasParameterNumArrType === 2) {
        return true
      }
    }

    if (
      !curComponent.value.checkedFieldsMapArrNum?.[id]?.length &&
      ['22', '2'].includes(curComponent.value.displayType) &&
      curComponent.value.parameters.some(ele => [2, 3].includes(ele.fieldType)) &&
      curComponent.value.checkedFieldsMap[id]
    ) {
      // 普通数值参数和范围数值参数混用时需要提示类型冲突。
      if (hasParameterNumArrType === 0) {
        hasParameterNumArrType = 2
      }

      if (hasParameterNumArrType === 1) {
        return true
      }
    }

    const arr = fields.value.find(ele => ele.componentId === id)
    const checkId = curComponent.value.checkedFieldsMap?.[id]
    const field = duplicateRemoval(Object.values(arr?.fields || {}).flat()).find(
      ele => checkId === ele.id
    )
    if (!field) return false
    if (displayTypeField === null) {
      displayTypeField = field
      return false
    }
    if (displayTypeField?.fieldType === field?.fieldType && displayTypeField?.fieldType === 1) {
      if (!Array.isArray(field.type) || !Array.isArray(displayTypeField.type)) {
        return false
      }
      if (!displayTypeField.type?.length && !field.type?.length) {
        return false
      }
      if (displayTypeField.type?.length !== field.type?.length) {
        return true
      }
    }
    return [2, 3].includes(field?.fieldType) && [2, 3].includes(displayTypeField.fieldType)
      ? false
      : displayTypeField.fieldType !== field?.fieldType
  })
})

const typeList = [
  {
    label: t('data_fill.rename'),
    command: 'rename'
  },
  {
    label: t('data_fill.delete'),
    command: 'del'
  }
]

// 字段组件全选切换时同步选中列表，并尝试补齐相同数据集字段映射。
const handleCheckAllChange = (val: boolean) => {
  curComponent.value.checkedFields = val ? fields.value.map(ele => ele.componentId) : []
  isIndeterminate.value = false
  val && setSameId()
}

// 普通字段组件选择变化后刷新选择状态、关闭弹层并重新推导展示类型。
const handleCheckedFieldsChange = (value: string[]) => {
  handleDialogClick()
  const checkedCount = value.length
  checkAll.value = checkedCount === fields.value.length
  isIndeterminate.value = checkedCount > 0 && checkedCount < fields.value.length
  if (curComponent.value.displayType === '8') return
  setType()
}
// 将已选择的同源字段映射复制到其它同数据集组件，减少重复手动选择。
const setSameId = () => {
  const comIdMap = {}
  Object.keys(curComponent.value.checkedFieldsMap).forEach(ele => {
    if (curComponent.value.checkedFieldsMap[ele]) {
      fields.value.forEach(itx => {
        if (
          itx.componentId === ele &&
          curComponent.value.checkedFields?.includes(itx.componentId)
        ) {
          comIdMap[itx.id] = curComponent.value.checkedFieldsMap[itx.componentId]
          comIdMap[`active-${itx.id}`] = itx.activelist
        }
      })
    }
  })

  Object.keys(curComponent.value.checkedFieldsMap).forEach(ele => {
    if (!curComponent.value.checkedFieldsMap[ele]) {
      fields.value.forEach(itx => {
        if (
          itx.componentId === ele &&
          curComponent.value.checkedFields?.includes(itx.componentId) &&
          comIdMap[itx.id]
        ) {
          curComponent.value.checkedFieldsMap[itx.componentId] = comIdMap[itx.id]
          itx.activelist = comIdMap[`active-${itx.id}`]
        }
      })
    }
  })
}
// 树形展示字段选择变化后刷新树层级默认值、关系图状态和展示类型。
const handleCheckedFieldsChangeTree = (value: string[]) => {
  handleDialogClick()
  const checkedCount = value.length
  checkAll.value = checkedCount === fields.value.length
  isIndeterminate.value = checkedCount > 0 && checkedCount < fields.value.length
  setSameId()
  if (curComponent.value.displayType === '8') return
  setTreeDefault()
  setRelationBack()
  setType()
}

// 判断参数字段是否需要禁用，避免非时间字段参与时间范围参数配置。
const isParametersDisable = item => {
  let isDisabled = false
  if (!isNumParameter.value && isTimeParameter.value) {
    if (notTimeRangeType.value.length && item.fieldType !== 1) {
      isDisabled = true
    }
  }
  return isDisabled
}

// 设置数值范围参数字段，并清理同组件下互斥的时间范围配置。
const setParametersArrNum = (val, componentId) => {
  // 数值范围和时间范围在同一组件上互斥，切换前先清掉另一套范围映射。
  if (curComponent.value.checkedFieldsMapArr?.[componentId]?.length) {
    curComponent.value.checkedFieldsMapArr[componentId] = []
    curComponent.value.checkedFieldsMapEnd[componentId] = ''
    curComponent.value.checkedFieldsMapStart[componentId] = ''
  }
  const timeStartId = curComponent.value.checkedFieldsMapStartNum[componentId]
  const timeEndId = curComponent.value.checkedFieldsMapEndNum[componentId]
  if (timeStartId) {
    curComponent.value.checkedFieldsMapEndNum[componentId] = val.find(ele => ele !== timeStartId)
  }

  if (timeEndId) {
    curComponent.value.checkedFieldsMapStartNum[componentId] = val.find(ele => ele !== timeEndId)
  }

  if (!val.length) {
    // 范围字段清空时同步清理映射、起止字段和参数数组。
    curComponent.value.checkedFieldsMap[componentId] = ''
    curComponent.value.checkedFieldsMapEndNum[componentId] = ''
    curComponent.value.checkedFieldsMapStartNum[componentId] = ''
    curComponent.value.parametersArr[componentId] = []
  }

  if (curComponent.value.checkedFieldsMapArrNum[componentId].length) {
    setParametersNumType(componentId)
  }
  setTypeChange()
}

// 设置时间范围参数字段，并清理同组件下互斥的数值范围配置。
const setParametersArr = (val, componentId) => {
  // 时间范围和数值范围共用参数数组结构，切换前要清理数值范围的起止字段。
  if (curComponent.value.checkedFieldsMapArrNum?.[componentId]?.length) {
    curComponent.value.checkedFieldsMapArrNum[componentId] = []
    curComponent.value.checkedFieldsMapEndNum[componentId] = ''
    curComponent.value.checkedFieldsMapStartNum[componentId] = ''
  }
  const timeStartId = curComponent.value.checkedFieldsMapStart[componentId]
  const timeEndId = curComponent.value.checkedFieldsMapEnd[componentId]
  if (timeStartId) {
    curComponent.value.checkedFieldsMapEnd[componentId] = val.find(ele => ele !== timeStartId)
  }

  if (timeEndId) {
    curComponent.value.checkedFieldsMapStart[componentId] = val.find(ele => ele !== timeEndId)
  }

  if (!val.length) {
    // 时间范围清空后回退到单时间展示类型，并清理粒度配置。
    curComponent.value.checkedFieldsMap[componentId] = ''
    curComponent.value.checkedFieldsMapEnd[componentId] = ''
    curComponent.value.checkedFieldsMapStart[componentId] = ''
    curComponent.value.displayType = '1'
    curComponent.value.parametersArr[componentId] = []
    curComponent.value.timeGranularity = ''
    curComponent.value.timeGranularityMultiple = ''
  }

  if (curComponent.value.checkedFieldsMapArr[componentId].length) {
    setParametersTimeType(componentId)
  }
  setTypeChange()
}

let currentComponentId = ''
let currentParameterId = ''
// 参数类型弹窗通过这两个临时变量记录上下文，确认后再写回当前条件映射。
// 时间参数类型选择弹窗显示状态
const timeDialogShow = ref(false)
// 时间参数类型，0 为单值，1 为开始值，2 为结束值
const timeParameterType = ref(0)
// 当前正在配置的时间参数名称
const timeName = ref('')

// 数值参数类型选择弹窗显示状态
const numDialogShow = ref(false)
// 数值参数类型，0 为单值，1 为最小值，2 为最大值
const numParameterType = ref(0)
// 当前正在配置的数值参数名称
const numName = ref('')

// 应用时间参数类型选择结果，并同步展示类型与参数数组
const timeTypeChange = () => {
  if (!curComponent.value.checkedFieldsMapArr[currentComponentId]) {
    curComponent.value.checkedFieldsMapArr[currentComponentId] = []
  }

  if (timeParameterType.value === 0) {
    // 单值时间参数会清空起止字段，展示类型回退到单时间选择。
    curComponent.value.checkedFieldsMap[currentComponentId] = currentParameterId
    curComponent.value.checkedFieldsMapArr[currentComponentId] = []
    curComponent.value.checkedFieldsMapStart[currentComponentId] = ''
    curComponent.value.checkedFieldsMapEnd[currentComponentId] = ''
  }

  if (timeParameterType.value === 1) {
    // 开始时间加入范围字段集合，并自动推导结束时间候选。
    curComponent.value.checkedFieldsMapStart[currentComponentId] = currentParameterId
    curComponent.value.checkedFieldsMapArr[currentComponentId] = [
      ...new Set([
        ...curComponent.value.checkedFieldsMapArr[currentComponentId],
        currentParameterId
      ])
    ]

    if (curComponent.value.checkedFieldsMapArr[currentComponentId].length === 1) {
      curComponent.value.checkedFieldsMapEnd[currentComponentId] = ''
    } else {
      curComponent.value.checkedFieldsMapEnd[currentComponentId] =
        curComponent.value.checkedFieldsMapArr[currentComponentId].length === 2
          ? curComponent.value.checkedFieldsMapArr[currentComponentId].find(
              ele => ele !== currentParameterId
            )
          : currentParameterId
    }
  }

  if (timeParameterType.value === 2) {
    // 结束时间加入范围字段集合，并自动推导开始时间候选。
    curComponent.value.checkedFieldsMapArr[currentComponentId] = [
      ...new Set([
        ...curComponent.value.checkedFieldsMapArr[currentComponentId],
        currentParameterId
      ])
    ]

    curComponent.value.checkedFieldsMapEnd[currentComponentId] = currentParameterId

    if (curComponent.value.checkedFieldsMapArr[currentComponentId].length === 1) {
      curComponent.value.checkedFieldsMapStart[currentComponentId] = ''
    } else {
      curComponent.value.checkedFieldsMapStart[currentComponentId] =
        curComponent.value.checkedFieldsMapArr[currentComponentId].length === 2
          ? curComponent.value.checkedFieldsMapArr[currentComponentId].find(
              ele => ele !== currentParameterId
            )
          : currentParameterId
    }
  }

  curComponent.value.displayType = curComponent.value.checkedFieldsMapArr[currentComponentId].length
    ? '7'
    : '1'
  setParametersTimeType(currentComponentId)
  setTypeChange()
  timeDialogShow.value = false
}

// 为树形展示自动选择默认数据集，优先使用已绑定字段所属的数据集
const setTreeDefault = () => {
  if (curComponent.value.displayType !== '9' || relationshipChartIndex.value !== 0) return
  if (!!curComponent.value.checkedFields.length) {
    let tableId = ''
    fields.value.forEach(ele => {
      if (
        curComponent.value.checkedFields.includes(ele.componentId) &&
        curComponent.value.checkedFieldsMap[ele.componentId] &&
        !tableId
      ) {
        tableId = datasetFieldList.value.find(itx => itx.id === ele.componentId)?.tableId
      }
    })
    if (tableId && !curComponent.value.treeDatasetId) {
      curComponent.value.treeDatasetId = tableId
      getOptions(curComponent.value.treeDatasetId, curComponent.value)
    }
  }
}

// 批量校验时为树形条件补齐默认数据集，避免未打开配置面板时漏填
const setTreeDefaultBatch = component => {
  if (!!component.checkedFields.length) {
    let tableId = ''
    fields.value.forEach(ele => {
      if (
        component.checkedFields.includes(ele.componentId) &&
        component.checkedFieldsMap[ele.componentId] &&
        !tableId
      ) {
        tableId = datasetFieldList.value.find(itx => itx.id === ele.componentId)?.tableId
      }
    })
    if (tableId && !component.treeDatasetId) {
      component.treeDatasetId = tableId
    }
  }
}

// 应用数值参数类型选择结果，并同步展示类型与参数数组
const numTypeChange = () => {
  if (!curComponent.value.checkedFieldsMapArrNum[currentComponentId]) {
    curComponent.value.checkedFieldsMapArrNum[currentComponentId] = []
  }

  if (numParameterType.value === 0) {
    // 单值数值参数会清空最小值和最大值字段，展示类型回退到普通数值输入。
    curComponent.value.checkedFieldsMap[currentComponentId] = currentParameterId
    curComponent.value.checkedFieldsMapArrNum[currentComponentId] = []
    curComponent.value.checkedFieldsMapStartNum[currentComponentId] = ''
    curComponent.value.checkedFieldsMapEndNum[currentComponentId] = ''
  }

  if (numParameterType.value === 1) {
    // 最小值字段加入范围集合，并自动维护另一端字段。
    curComponent.value.checkedFieldsMapStartNum[currentComponentId] = currentParameterId
    curComponent.value.checkedFieldsMapArrNum[currentComponentId] = [
      ...new Set([
        ...curComponent.value.checkedFieldsMapArrNum[currentComponentId],
        currentParameterId
      ])
    ]

    if (curComponent.value.checkedFieldsMapArrNum[currentComponentId].length === 1) {
      curComponent.value.checkedFieldsMapEndNum[currentComponentId] = ''
    } else {
      curComponent.value.checkedFieldsMapEndNum[currentComponentId] =
        curComponent.value.checkedFieldsMapArrNum[currentComponentId].length === 2
          ? curComponent.value.checkedFieldsMapArrNum[currentComponentId].find(
              ele => ele !== currentParameterId
            )
          : currentParameterId
    }
  }

  if (numParameterType.value === 2) {
    // 最大值字段加入范围集合，并自动维护另一端字段。
    curComponent.value.checkedFieldsMapArrNum[currentComponentId] = [
      ...new Set([
        ...curComponent.value.checkedFieldsMapArrNum[currentComponentId],
        currentParameterId
      ])
    ]

    curComponent.value.checkedFieldsMapEndNum[currentComponentId] = currentParameterId

    if (curComponent.value.checkedFieldsMapArrNum[currentComponentId].length === 1) {
      curComponent.value.checkedFieldsMapStartNum[currentComponentId] = ''
    } else {
      curComponent.value.checkedFieldsMapStartNum[currentComponentId] =
        curComponent.value.checkedFieldsMapArrNum[currentComponentId].length === 2
          ? curComponent.value.checkedFieldsMapArrNum[currentComponentId].find(
              ele => ele !== currentParameterId
            )
          : currentParameterId
    }
  }

  curComponent.value.displayType = curComponent.value.checkedFieldsMapArrNum[currentComponentId]
    .length
    ? '22'
    : '2'
  setParametersNumType(currentComponentId)
  setTypeChange()
  numDialogShow.value = false
}

// 根据数值范围起止字段回填参数数组，只保留带变量名的有效参数
const setParametersNumType = componentId => {
  // 参数数组只服务后续 SQL 参数绑定，普通字段即使被选中也不进入 parametersArr。
  curComponent.value.parametersArr[componentId] = duplicateRemoval(
    unref(fields)
      .filter(ele => ele.componentId === componentId)
      .map(ele => Object.values(ele?.fields || {}).flat())
      .flat()
      .filter(
        ele =>
          [
            curComponent.value.checkedFieldsMapEndNum[componentId],
            curComponent.value.checkedFieldsMapStartNum[componentId]
          ]
            .filter(ele => !!ele)
            .includes(ele.id) && !!ele.variableName
      )
  )
}

// 根据时间范围起止字段回填参数数组，并同步时间粒度到范围配置
const setParametersTimeType = componentId => {
  // 时间范围参数会额外反推 timeGranularityMultiple，供默认值配置渲染范围控件。
  curComponent.value.parametersArr[componentId] = duplicateRemoval(
    unref(fields)
      .filter(ele => ele.componentId === componentId)
      .map(ele => Object.values(ele?.fields || {}).flat())
      .flat()
      .filter(
        ele =>
          [
            curComponent.value.checkedFieldsMapEnd[componentId],
            curComponent.value.checkedFieldsMapStart[componentId]
          ]
            .filter(ele => !!ele)
            .includes(ele.id) && !!ele.variableName
      )
  )
  if (!curComponent.value.parametersArr[componentId].length) return
  const timeRangeParameter = curComponent.value.parametersArr[componentId][0]
  if (!timeRangeParameter?.type?.length) return
  const [v1, v2] = timeRangeParameter.type
  curComponent.value.timeGranularityMultiple = typeTimeMap[v2 || v1]
    ? `${typeTimeMap[v2 || v1]}range`
    : ''
  curComponent.value.timeGranularity = typeTimeMap[v2 || v1]
}

// 打开数值参数类型选择弹窗，并根据当前字段位置预选单值、最小值或最大值
const numClick = (componentId, timeVal) => {
  numParameterType.value =
    timeVal.id === curComponent.value.checkedFieldsMapStartNum[componentId]
      ? 1
      : timeVal.id === curComponent.value.checkedFieldsMapEndNum[componentId]
      ? 2
      : 0
  currentComponentId = componentId
  currentParameterId = timeVal.id
  numName.value = timeVal.variableName
  numDialogShow.value = true
}

// 打开时间参数类型选择弹窗，并根据当前字段位置预选单值、开始值或结束值
const timeClick = (componentId, timeVal) => {
  timeParameterType.value =
    timeVal.id === curComponent.value.checkedFieldsMapStart[componentId]
      ? 1
      : timeVal.id === curComponent.value.checkedFieldsMapEnd[componentId]
      ? 2
      : 0
  currentComponentId = componentId
  currentParameterId = timeVal.id
  timeName.value = timeVal.variableName
  timeDialogShow.value = true
}

// 按字段 id 去重，保留输入数组中最后出现的有效字段对象
const duplicateRemoval = arr => {
  const objList = []
  let idList = arr.map(ele => ele.id)
  for (let index = 0; index < arr.length; index++) {
    const element = arr[index]
    if (idList.includes(element.id)) {
      objList.push(element)
      idList = idList.filter(ele => ele !== element.id)
    }
  }
  return objList
}

// 根据已绑定字段生成参数列表，并在必要时同步同数据集组件的字段选择
const setParameters = field => {
  const fieldArr = Object.values(curComponent.value.checkedFieldsMap).filter(ele => !!ele) as any[]
  // 参数列表只保留当前绑定字段中带变量名的字段，避免无参字段进入 SQL 参数配置。
  curComponent.value.parameters = duplicateRemoval(
    (Object.values(field?.fields || {}) as any[])
      .flat()
      .filter(ele => fieldArr.includes(ele.id) && !!ele.variableName)
      .concat(curComponent.value.parameters.filter(ele => fieldArr.includes(ele.id)))
  )
  fields.value.forEach(ele => {
    if (
      ele.id === field.id &&
      curComponent.value.checkedFields?.includes(ele.componentId) &&
      !curComponent.value.checkedFieldsMap[ele.componentId]
    ) {
      ele.activelist = field.activelist
      curComponent.value.checkedFieldsMap[ele.componentId] =
        curComponent.value.checkedFieldsMap[field.componentId]
    }
  })

  const notChangeType =
    curComponent.value.checkedFields.some(ele => {
      return (
        curComponent.value.checkedFieldsMapStart[ele] || curComponent.value.checkedFieldsMapEnd[ele]
      )
    }) && +curComponent.value.displayType === 7
  nextTick(() => {
    if (isTimeParameter.value && !notChangeType) {
      // 单时间参数会根据字段格式自动选择 year/month/date/datetime 粒度。
      const timeParameter = curComponent.value.parameters.find(ele => ele.fieldType === 1)
      curComponent.value.timeGranularity =
        typeTimeMap[timeParameter.type[1] || timeParameter.type[0]]
      curComponent.value.displayType = '1'
    }

    if (!!curComponent.value.parameters.length) {
      // 存在参数字段时默认使用数据集选项，减少手动输入选项与参数查询不一致。
      curComponent.value.conditionType = 0
      if (curComponent.value.optionValueSource === 0) {
        curComponent.value.optionValueSource = 1
      }
    }
    if (curComponent.value.displayType === '22') {
      const isNumParameter = curComponent.value.checkedFields.some(ele => {
        return curComponent.value.parameters?.some(
          itx =>
            [2, 3].includes(itx.fieldType) && curComponent.value.checkedFieldsMap[ele] === itx.id
        )
      })

      const isSingle = curComponent.value.checkedFields.every(id => {
        return !curComponent.value.checkedFieldsMapArr?.[id]?.length
      })

      if (isSingle && isNumParameter) {
        curComponent.value.displayType = '2'
      }
    }
    setTypeChange()
  })

  if (notChangeType) return
  setType()
  setTreeDefault()
  setRelationBack()
}

// 根据当前选中字段类型推导查询条件展示类型，保留树形和已配置范围的特殊类型
const setType = () => {
  if (curComponent.value.checkedFields?.length) {
    const [id] = curComponent.value.checkedFields
    const arr = fields.value.find(ele => ele.componentId === id)
    const checkId = curComponent.value.checkedFieldsMap?.[id]
    const field = Object.values(arr?.fields || {})
      .flat()
      .find(ele => checkId === ele.id)

    if (field?.fieldType !== undefined) {
      let displayType = curComponent.value.displayType
      // 已配置的范围数值和树形展示类型优先级更高，不被单字段类型推导覆盖。
      if (['22'].includes(curComponent.value.displayType) && [2, 3].includes(field?.fieldType)) {
        return
      }
      if (['9'].includes(curComponent.value.displayType)) {
        return
      }
      if (!(field?.fieldType === 1 && curComponent.value.displayType === '7')) {
        curComponent.value.displayType = `${
          [3, 4].includes(field?.fieldType) ? 2 : field?.fieldType
        }`
      }
      if (field?.fieldType === 7) {
        curComponent.value.displayType = '0'
      }

      if (
        +displayType !== +curComponent.value.displayType &&
        !([3, 4].includes(+displayType) && +curComponent.value.displayType === 2)
      ) {
        setTypeChange()
      }
    }
  }

  if (
    curComponent.value.checkedFields.some(ele => {
      return (
        curComponent.value.checkedFieldsMapStart[ele] || curComponent.value.checkedFieldsMapEnd[ele]
      )
    }) &&
    +curComponent.value.displayType === 1
  ) {
    curComponent.value.displayType = '7'
  }
}

let oldDisplayType

// 切换展示类型时处理树形类型的降级确认，避免误清空树形配置
const handleSetTypeChange = () => {
  let displayType = curComponent.value.displayType
  if (oldDisplayType === '9' && ['0', '8'].includes(displayType)) {
    curComponent.value.displayType = '9'
    ElMessageBox.confirm(t('common.changing_the_display'), {
      confirmButtonType: 'primary',
      type: 'warning',
      cancelButtonText: t('common.cancel'),
      autofocus: false,
      showClose: false
    }).then(() => {
      curComponent.value.displayType = displayType
      setTypeChange()
    })
  } else {
    setTypeChange()
  }
}

// 展示类型变化后的统一收敛入口，负责清理字段、刷新默认配置和树形状态
const setTypeChange = () => {
  handleDialogClick()
  nextTick(() => {
    // 展示类型变化后清空选项字段，避免旧字段类型与新控件类型不匹配。
    curComponent.value.field.id = ''
    defaultConfigurationRef.value?.displayTypeChange?.()
    if (
      +curComponent.value.displayType === 7 &&
      ['yearrange', 'monthrange', 'daterange', 'datetimerange'].includes(
        curComponent.value.timeGranularity
      )
    ) {
      curComponent.value.timeGranularityMultiple = curComponent.value.timeGranularity
    }
    setTreeDefault()
    setRelationBack()
    if (curComponent.value.displayType === '0' && curComponent.value.treeFieldList?.length) {
      curComponent.value.treeFieldList = []
      curComponent.value.treeCheckedList = []
    }
    oldDisplayType = curComponent.value.displayType
  })
}

// 当前条件是否包含时间参数字段，用于限制时间粒度和范围控件
const isTimeParameter = computed(() => {
  return curComponent.value.checkedFields.some(ele => {
    return curComponent.value.parameters?.some(
      itx =>
        itx.fieldType === 1 &&
        !!itx.variableName &&
        curComponent.value.checkedFieldsMap[ele] === itx.id
    )
  })
})

// 当前条件是否包含数值参数字段，用于开放数值范围配置
const isNumParameter = computed(() => {
  return curComponent.value.parameters?.some(
    ele => [2, 3].includes(ele.fieldType) && !!ele.variableName
  )
})

// 当前条件是否已配置数值范围字段
const notNumRange = computed(() => {
  return curComponent.value.checkedFields.some(ele => {
    return curComponent.value.checkedFieldsMapArrNum[ele]?.length > 0
  })
})

// 判断当前选择是否不允许再配置数值范围，防止单值数值参数与范围参数混用
const canNotNumRange = computed(() => {
  if (
    curComponent.value.checkedFields.every(id => {
      return curComponent.value.checkedFieldsMapArrNum?.[id]?.length > 0
    })
  ) {
    return false
  } else {
    return curComponent.value.checkedFields.some(ele => {
      return curComponent.value.parameters?.some(
        itx => [2, 3].includes(itx.fieldType) && curComponent.value.checkedFieldsMap[ele] === itx.id
      )
    })
  }
})

// 当前条件是否已配置时间范围字段
const notTimeRange = computed(() => {
  return curComponent.value.checkedFields.some(ele => {
    return curComponent.value.checkedFieldsMapArr[ele]?.length > 0
  })
})

// 当前时间范围字段的原始时间格式，用于约束可选粒度
const notTimeRangeType = computed(() => {
  const fieldsArr = unref(fields)
    .map(ele => Object.values(ele?.fields || {}).flat())
    .flat()
  const field = Object.values(curComponent.value.checkedFieldsMapArr || {}).flat()
  const obj = fieldsArr.find(ele => ele.id === field[0])
  return obj?.type || []
})

const timeList = [
  {
    label: t('dynamic_time.year'),
    value: 'year'
  },
  {
    label: t('chart.y_M'),
    value: 'month'
  },
  {
    label: t('chart.y_M_d'),
    value: 'date'
  },
  {
    label: t('chart.y_M_d_H_m_s'),
    value: 'datetime'
  }
]

const typeTimeMap = {
  'DATETIME-YEAR': 'year',
  'YYYY-MM': 'month',
  'YYYY/MM': 'month',
  'YYYY-MM-DD': 'date',
  'YYYY/MM/DD': 'date',
  'YYYY-MM-DD HH:mm:ss': 'datetime',
  'YYYY/MM/DD HH:mm:ss': 'datetime'
}

// 根据已选时间参数的格式裁剪可选时间粒度，防止选择高于字段精度的粒度
const timeParameterList = computed(() => {
  if (!isTimeParameter.value) return timeList
  const timeParameter = curComponent.value.parameters?.find(
    ele => ele.fieldType === 1 && !!ele.variableName
  )
  if (!timeParameter?.type?.length) return timeList
  const [year, y] = timeParameter.type
  let stopPush = false
  return timeList.reduce((pre, ele) => {
    if (ele.value === (typeTimeMap[y] || typeTimeMap[year])) {
      stopPush = true
      pre.push(ele)
    } else if (!stopPush) {
      pre.push(ele)
    }
    return pre
  }, [])
})

// 取消配置并关闭弹窗，关闭前同步子弹层状态
const cancelClick = () => {
  handleDialogClick()
  dialogVisible.value = false
}

// 初始化数据集树，并过滤掉不可选择的空目录
const initDataset = () => {
  fetchDatasetTree({}).then(res => {
    const result = (res as unknown as Tree[]) || []
    if (result[0]?.id === '0') {
      sortTypeChange(dfs(result[0].children))
    } else {
      sortTypeChange(dfs(result))
    }
  })
}

// 按缓存排序方式整理数据集树
const sortTypeChange = arr => {
  const sortType = wsCache.get('TreeSort-dataset') || 'time_desc'
  datasetTree.value = treeSort(arr, sortType)
}

let newDatasetId = ''
let oldDatasetId = ''
// 切换数据集树节点时检查是否影响已有级联关系，必要时记录待确认的新旧数据集
const handleCurrentChange = node => {
  if (!curComponent.value.dataset?.id) return
  let id = `${curComponent.value.dataset?.id}--${curComponent.value.id}`
  let isChange = false
  for (let i in cascadeArr) {
    const [fir, sec] = cascadeArr[i]
    if (fir?.datasetId.includes(id) || sec?.datasetId.includes(id)) {
      isChange = true
    }
  }
  if (!isChange) return
  oldDatasetId = curComponent.value.dataset?.id
  newDatasetId = node.id
}

// 用户确认切换数据集后清理相关级联关系，并重新加载字段选项
const confirmIdChange = () => {
  curComponent.value.dataset.id = newDatasetId
  clearCascadeArrDataset(`${oldDatasetId}--${curComponent.value.id}`)
  newDatasetId = ''
  oldDatasetId = ''
  handleDatasetChange()
}

// 条件值来源数据集变化时重置字段、展示字段和排序字段，并重新加载可选字段
const handleDatasetChange = () => {
  if (!!newDatasetId && !!oldDatasetId) {
    // 已参与级联的数据集切换必须二次确认，否则会留下指向旧数据集的级联边。
    curComponent.value.dataset.id = oldDatasetId
    ElMessageBox.confirm(t('v_query.to_modify_it'), {
      confirmButtonType: 'primary',
      type: 'warning',
      confirmButtonText: t('commons.confirm'),
      cancelButtonText: t('commons.cancel'),
      autofocus: false,
      showClose: false
    }).then(() => {
      confirmIdChange()
    })
    return
  }
  curComponent.value.field.id = ''
  curComponent.value.displayId = ''
  curComponent.value.sortId = ''
  getOptions(curComponent.value.dataset.id, curComponent.value)
}

// 树形结构数据集变化时清空树字段配置，并重新加载树形可选字段
const handleDatasetTreeChange = () => {
  curComponent.value.treeFieldList = []
  curComponent.value.treeCheckedList = []
  relationshipChartIndex.value = 0
  curComponent.value.oldTreeLoad = true
  getOptions(curComponent.value.treeDatasetId, curComponent.value)
}

// 条件值字段变化时重置默认值，并在缺省展示字段时同步展示字段
const handleFieldChange = () => {
  if (!curComponent.value.defaultValueCheck) return
  curComponent.value.defaultValue = curComponent.value.multiple ? [] : undefined
  if (!curComponent.value.displayId) {
    curComponent.value.displayId = curComponent.value.field.id
  }
}

// 选项值来源变化时重置默认值，并在字段来源模式下自动补齐数据集和字段
const handleValueSourceChange = () => {
  curComponent.value.defaultValue = curComponent.value.multiple ? [] : undefined
  multipleChange(curComponent.value.multiple)
  if (curComponent.value.optionValueSource === 1 && !curComponent.value.dataset.id) {
    // 字段来源模式优先复用已绑定图表字段，减少用户重复选择同一个数据集字段。
    let id = ''
    let comId = ''
    Object.keys(curComponent.value.checkedFieldsMap).forEach(ele => {
      if (curComponent.value.checkedFieldsMap[ele]) {
        comId = ele
        id = curComponent.value.checkedFieldsMap[ele]
      }
    })
    fields.value.forEach(ele => {
      if (ele.componentId === comId) {
        curComponent.value.dataset.id = ele.id
      }
    })
    curComponent.value.displayId = id
    curComponent.value.sortId = id
    curComponent.value.field.id = id
    getOptions(curComponent.value.dataset.id, curComponent.value)
  }
}

// 切换单选或多选模式，并按目标模式转换默认值和选中值结构
const multipleChange = (val: boolean, isMultipleChange = false) => {
  if (isMultipleChange) {
    curComponent.value.defaultValue = val ? [] : undefined
  }
  const { defaultValue } = curComponent.value
  if (Array.isArray(defaultValue)) {
    curComponent.value.selectValue = val ? defaultValue : undefined
  } else {
    curComponent.value.selectValue = val
      ? defaultValue !== undefined
        ? [defaultValue]
        : []
      : defaultValue
  }

  curComponent.value.multiple = val
}

// 判断时间范围是否超出动态窗口或固定区间限制，用于默认值校验
const isInRange = (ele, startWindowTime, timeStamp) => {
  const {
    intervalType,
    regularOrTrends,
    regularOrTrendsValue,
    relativeToCurrent,
    timeNum,
    relativeToCurrentType,
    around,
    dynamicWindow,
    maximumSingleQuery,
    timeNumRange,
    relativeToCurrentRange,
    relativeToCurrentTypeRange,
    aroundRange
  } = ele.timeRange || {}
  let isDynamicWindowTime = false

  const noTime = ele.timeGranularityMultiple.split('time').join('').split('range')[0]
  const queryTimeType = noTime === 'date' ? 'day' : (noTime as ManipulateType)
  if (startWindowTime && dynamicWindow) {
    isDynamicWindowTime =
      dayjs(startWindowTime)
        .add(maximumSingleQuery, queryTimeType)
        .startOf(queryTimeType)
        .valueOf() -
        1000 <
      timeStamp
  }

  if (intervalType === 'none') {
    if (dynamicWindow) return isDynamicWindowTime
    return false
  }
  let startTime
  if (relativeToCurrent === 'custom') {
    startTime = getAroundStart(relativeToCurrentType, around === 'f' ? 'subtract' : 'add', timeNum)
  } else {
    switch (relativeToCurrent) {
      case 'thisYear':
        startTime = getThisStart('year')
        break
      case 'lastYear':
        startTime = getLastStart('year')
        break
      case 'thisMonth':
        startTime = getThisStart('month')
        break
      case 'lastMonth':
        startTime = getLastStart('month')
        break
      case 'thisQuarter':
        startTime = getThisStart('quarter')
        break
      case 'thisWeek':
        startTime = new Date(
          dayjs().startOf('week').add(1, 'day').startOf('day').format('YYYY/MM/DD HH:mm:ss')
        )
        break
      case 'today':
        startTime = getThisStart('day')
        break
      case 'yesterday':
        startTime = getLastStart('day')
        break
      case 'monthBeginning':
        startTime = getThisStart('month')
        break
      case 'monthEnd':
        startTime = getThisEnd('month')
        break
      case 'yearBeginning':
        startTime = getThisStart('year')
        break

      default:
        break
    }
  }

  const startValue = regularOrTrends === 'fixed' ? regularOrTrendsValue : startTime
  if (intervalType === 'start') {
    return startWindowTime < +new Date(startValue) || isDynamicWindowTime
  }

  if (intervalType === 'end') {
    return timeStamp > +new Date(startValue) || isDynamicWindowTime
  }

  if (intervalType === 'timeInterval') {
    let endTime
    if (relativeToCurrentRange === 'custom') {
      startTime =
        regularOrTrends === 'fixed'
          ? new Date(
              dayjs(new Date(regularOrTrendsValue[0])).startOf(noTime).format('YYYY/MM/DD HH:mm:ss')
            )
          : getAroundStart(relativeToCurrentType, around === 'f' ? 'subtract' : 'add', timeNum)
      endTime =
        regularOrTrends === 'fixed'
          ? new Date(
              dayjs(new Date(regularOrTrendsValue[1])).endOf(noTime).format('YYYY/MM/DD HH:mm:ss')
            )
          : getAround(
              relativeToCurrentTypeRange,
              aroundRange === 'f' ? 'subtract' : 'add',
              timeNumRange
            )
    } else {
      ;[startTime, endTime] = getCustomRange(relativeToCurrentRange)
    }

    return (
      startWindowTime < +new Date(startTime) - 1000 ||
      timeStamp > +new Date(endTime) ||
      isDynamicWindowTime
    )
  }
}

// 级联配置弹窗按需加载，减少查询条件配置弹窗的初始体积
const CascadeDialog = defineAsyncComponent(() => import('./QueryCascade.vue'))
// 级联配置弹窗组件引用
const cascadeDialog = ref()
// 打开级联配置弹窗，并构造可参与级联的条件字段映射
const openCascadeDialog = () => {
  const cascadeMap = conditions.value
    .filter(ele => {
      return (
        ([0, 2, 5].includes(+ele.displayType) &&
          ele.optionValueSource === 1 &&
          !!ele.checkedFields?.length &&
          !!Object.values(ele.checkedFieldsMap).filter(item => !!item).length) ||
        ([9].includes(+ele.displayType) && ele.treeFieldList?.length)
      )
    })
    .reduce((pre, next) => {
      const isTree = [9].includes(+next.displayType)
      const field = isTree ? next.treeFieldList?.[0] : next.field
      const datasetId = isTree ? field?.datasetGroupId : next.dataset?.id
      if (!field?.id || !datasetId) return pre
      const fieldList = datasetMap[datasetId]?.fields?.dimensionList || next.dataset?.fields || []
      pre[next.id] = {
        datasetId,
        isTree,
        name: next.name,
        queryId: next.id,
        fieldId: field.id,
        fieldType: fieldList.find(ele => ele.id === field.id)?.fieldType
      }
      return pre
    }, {})
  cascadeDialog.value.init(cascadeMap, cascadeArr)
}

// 数据集切换后清理受影响的级联关系，保留仍然有效的另一端条件
const clearCascadeArrDataset = id => {
  for (let i in cascadeArr) {
    const [fir, sec] = cascadeArr[i]
    if (fir?.datasetId.includes(id)) {
      cascadeArr[i] = []
    } else if (sec?.datasetId.includes(id)) {
      cascadeArr[i] = [fir]
    }
  }
  cascadeArr = cascadeArr.filter(ele => !!ele.length)
}

const indexNumCascade = [
  t('visualization.number1'),
  t('visualization.number2'),
  t('visualization.number3'),
  t('visualization.number4'),
  t('visualization.number5')
]

const validateConditionType = ({
  defaultConditionValueF,
  defaultConditionValueS,
  conditionType
}) => {
  // 单条件只校验首个值，双条件必须同时具备左右两侧默认值。
  if (conditionType === 0) {
    return defaultConditionValueF === ''
  } else {
    return defaultConditionValueF === '' || defaultConditionValueS === ''
  }
}

// 将默认条件值写入运行态条件值，供文本条件和必填校验复用
const setParams = ele => {
  const {
    defaultConditionValueOperatorF,
    defaultConditionValueF,
    defaultConditionValueOperatorS,
    defaultConditionValueS
  } = ele
  ele.conditionValueOperatorF = defaultConditionValueOperatorF
  ele.conditionValueF = defaultConditionValueF
  ele.conditionValueOperatorS = defaultConditionValueOperatorS
  ele.conditionValueS = defaultConditionValueS
}

// 保存前校验所有查询条件，覆盖字段绑定、默认值、树形结构和范围合法性
const validate = () => {
  return conditions.value.some(ele => {
    if (ele.auto) return false
    if (!ele.checkedFields?.length || ele.checkedFields.some(itx => !ele.checkedFieldsMap[itx])) {
      ElMessage.error(t('v_query.be_linked_first'))
      return true
    }

    if (
      ele.displayType === '0' &&
      ele.defaultValueCheck &&
      ((Array.isArray(ele.defaultValue) && !ele.defaultValue.length) || !ele.defaultValue)
    ) {
      if (ele.optionValueSource !== 1) {
        ElMessage.error(t('report.filter.title'))
        return true
      }

      if (!ele.defaultValueFirstItem) {
        ElMessage.error(t('report.filter.title'))
        return true
      }
    }

    if (ele.displayType === '9') {
      // 树形条件需要同时校验数据集、层级字段和每一层级的图表字段映射。
      if (
        ele.defaultValueCheck &&
        ((Array.isArray(ele.defaultValue) && !ele.defaultValue.length) || !ele.defaultValue)
      ) {
        ElMessage.error(t('report.filter.title'))
        return true
      }
      if (!ele.treeDatasetId) {
        setTreeDefaultBatch(ele)
        if (!ele.treeDatasetId) {
          ElMessage.error(t('data_set.dataset_cannot_be'))
        }
        return true
      }

      if (!ele.treeFieldList?.length) {
        ElMessage.error(t('common.tree_structure'))
        return true
      }
      if (
        ele.treeCheckedList
          ?.slice(0, ele.treeFieldList.length)
          .some(
            item =>
              !item.checkedFields?.length ||
              item.checkedFields.some(itx => !item.checkedFieldsMap[itx])
          )
      ) {
        ElMessage.error(t('v_query.be_linked_first'))
        return true
      }
    }

    if (ele.displayType === '22' && ele.defaultValueCheck) {
      // 数值范围保存前先把默认起止值同步到运行态，预览查询直接读取 numValueStart/End。
      ele.numValueEnd = ele.defaultNumValueEnd
      ele.numValueStart = ele.defaultNumValueStart
      if (
        (ele.defaultNumValueEnd !== 0 && !ele.defaultNumValueEnd) ||
        (ele.defaultNumValueStart !== 0 && !ele.defaultNumValueStart)
      ) {
        ElMessage.error(t('v_query.required_default_value_empty'))
        return true
      }
      if (
        !isNaN(ele.defaultNumValueEnd) &&
        !isNaN(ele.defaultNumValueStart) &&
        ele.defaultNumValueEnd < ele.defaultNumValueStart
      ) {
        ElMessage.error(t('v_query.the_minimum_value'))
        return true
      }
    }
    let displayTypeField = null
    let errorTips = t('v_query.cannot_be_performed')
    let hasParameterNumArrType = 0
    if (
      ele.checkedFields.some(id => {
        if (ele.checkedFieldsMapArrNum?.[id]?.length) {
          if (hasParameterNumArrType === 0) {
            hasParameterNumArrType = 1
          }

          if (hasParameterNumArrType === 2) {
            return true
          }
        }

        if (
          !ele.checkedFieldsMapArrNum?.[id]?.length &&
          ['22'].includes(ele.displayType) &&
          !!ele.parameters.length
        ) {
          if (hasParameterNumArrType === 0) {
            hasParameterNumArrType = 2
          }

          if (hasParameterNumArrType === 1) {
            return true
          }
        }

        if (ele.checkedFieldsMapArrNum?.[id]?.length === 1 && ele.displayType === '22') {
          errorTips = t('v_query.numerical_parameter_configuration')
          return true
        }

        if (ele.checkedFieldsMapArr?.[id]?.length === 1 && ele.displayType === '7') {
          errorTips = t('v_query.and_end_time')
          return true
        }

        const arr = fields.value.find(itx => itx.componentId === id)
        const checkId = ele.checkedFieldsMap?.[id]
        const field = duplicateRemoval(Object.values(arr?.fields || {}).flat()).find(
          itx => checkId === itx.id
        )
        if (!field) return false
        if (displayTypeField === null) {
          displayTypeField = field
          return false
        }
        if (displayTypeField?.fieldType === field?.fieldType && displayTypeField?.fieldType === 1) {
          if (!Array.isArray(field.type) || !Array.isArray(displayTypeField.type)) {
            return false
          }
          if (!displayTypeField.type?.length && !field.type?.length) {
            return false
          }
          if (displayTypeField.type?.length !== field.type?.length) {
            errorTips = t('v_query.format_is_inconsistent')
            return true
          }
          for (let index = 0; index < displayTypeField.type.length; index++) {
            if (displayTypeField.type[index] !== field.type[index]) {
              errorTips = t('v_query.format_is_inconsistent')
              return true
            }
          }
        }
        return displayTypeField.fieldType !== field?.fieldType
      })
    ) {
      ElMessage.error(errorTips)
      return true
    }

    if (ele.required) {
      if (ele.displayType === '8') {
        setParams(ele)
        const result = validateConditionType(ele)
        if (result) {
          ElMessage.error(t('v_query.required_default_value_empty'))
        }
        return result
      }

      if (!ele.defaultValueCheck) {
        ElMessage.error(t('v_query.required_default_value_empty'))
        return true
      }

      if (ele.displayType === '22') {
        if (
          (ele.defaultNumValueEnd !== 0 && !ele.defaultNumValueEnd) ||
          (ele.defaultNumValueStart !== 0 && !ele.defaultNumValueStart)
        ) {
          ElMessage.error(t('v_query.required_default_value_empty'))
          return true
        }
        return false
      }

      if (
        (Array.isArray(ele.defaultValue) && !ele.defaultValue.length) ||
        (ele.defaultValue !== 0 && !ele.defaultValue)
      ) {
        ElMessage.error(t('v_query.required_default_value_empty'))
        return true
      }
    }

    if (ele.displayType === '8') {
      setParams(ele)
      return false
    }

    if (!ele.defaultValueCheck) {
      const isMultiple = +ele.displayType === 7 || ele.multiple
      ele.selectValue = isMultiple ? [] : undefined
      ele.defaultValue = isMultiple ? [] : undefined
    }

    if (ele.displayType === '1') {
      if (!ele.defaultValueCheck) return false
      if (ele.timeType === 'fixed') {
        if (!ele.defaultValue) {
          ElMessage.error(t('v_query.cannot_be_empty_time'))
          return true
        }
      }
    }

    if (ele.displayType === '2') {
      if (!ele.defaultValueCheck) return false
      if (
        (Array.isArray(ele.defaultValue) && !ele.defaultValue.length) ||
        (!Array.isArray(ele.defaultValue) && ['', undefined, null].includes(ele.defaultValue))
      ) {
        ElMessage.error(t('report.filter.title'))
        return true
      }
    }

    if (+ele.displayType === 7) {
      if (!ele.defaultValueCheck) return false
      if (ele.timeType === 'fixed') {
        const [s, e] = ele.defaultValue || []
        if (!s || !e) {
          ElMessage.error(t('v_query.cannot_be_empty_time'))
          return true
        }
      }
      const {
        timeNum,
        relativeToCurrentType,
        around,
        relativeToCurrentRange,
        timeGranularityMultiple,
        arbitraryTime,
        timeGranularity,
        timeNumRange,
        relativeToCurrentTypeRange,
        aroundRange,
        arbitraryTimeRange,
        timeType
      } = ele

      let startTime =
        timeType === 'dynamic'
          ? getCustomTime(
              timeNum,
              relativeToCurrentType,
              timeGranularity,
              around,
              arbitraryTime,
              timeGranularityMultiple,
              'start-config'
            )
          : new Date(ele.defaultValue[0])
      let endTime =
        timeType === 'dynamic'
          ? getCustomTime(
              timeNumRange,
              relativeToCurrentTypeRange,
              timeGranularity,
              aroundRange,
              arbitraryTimeRange,
              timeGranularityMultiple,
              'end-config'
            )
          : new Date(ele.defaultValue[1])
      if (!!relativeToCurrentRange && relativeToCurrentRange !== 'custom') {
        ;[startTime, endTime] = getCustomRange(relativeToCurrentRange)
      }
      if (+startTime > +endTime) {
        ElMessage.error(t('v_query.the_start_time'))
        return true
      }
      if (!ele.setTimeRange) return false

      // 设置最大查询窗口后，默认时间必须落在动态窗口或固定区间允许范围内。
      if (
        isInRange(
          ele,
          timeGranularityMultiple.includes('time')
            ? dayjs(+startTime).startOf('day').valueOf()
            : +startTime,
          timeGranularityMultiple.includes('time')
            ? dayjs(+endTime).startOf('day').valueOf()
            : +endTime
        )
      ) {
        ElMessage.error(t('v_query.range_please_reset'))
        return true
      }
      return false
    }

    if (
      ele.displayType !== '9' &&
      ele.optionValueSource === 2 &&
      !ele.valueSource?.filter(ele => !!ele).length
    ) {
      ElMessage.error(t('v_query.cannot_be_empty_input'))
      return true
    }

    if (
      !['9', '22', '1', '7'].includes(ele.displayType) &&
      ele.optionValueSource === 1 &&
      !ele.field.id
    ) {
      ElMessage.error(
        !ele.dataset?.id ? t('v_query.option_value_field') : t('v_query.the_data_set')
      )
      return true
    }
  })
}

// 关闭配置弹窗前同步子组件状态，并清理当前条件和关系图索引
const handleBeforeClose = () => {
  defaultConfigurationRef.value?.mult()
  defaultConfigurationRef.value?.single()
  handleDialogClick()
  if (curComponent.value) {
    curComponent.value.id = ''
  }
  relationshipChartIndex.value = 0
  dialogVisible.value = false
}
// 查询条件配置对外事件，分别通知查询数据刷新和画布组件重渲染
const emits = defineEmits(['queryData', 'reRenderAll'])
// 校验并确认配置，写回查询组件属性、记录快照并触发外部刷新事件
const confirmClick = () => {
  if (validate()) return
  defaultConfigurationRef.value?.mult()
  defaultConfigurationRef.value?.single()
  handleDialogClick()
  dialogVisible.value = false
  conditions.value.forEach(ele => {
    curComponent.value = ele
    multipleChange(
      ['1', '7'].includes(curComponent.value.displayType)
        ? curComponent.value.displayType === '7'
        : curComponent.value.multiple
    )
  })
  const oldArr = cloneDeep(unref(queryElement.value.propValue))
  queryElement.value.propValue = []
  nextTick(() => {
    conditions.value.forEach(itx => {
      cascadeArr.forEach(ele => {
        ele.forEach(item => {
          if (item.datasetId.split('--')[1] === itx.id && itx.defaultValueCheck) {
            // 确认保存时把当前默认值同步给级联节点，运行态首次查询可直接使用。
            const val = itx.mapValue
            item.selectValue = Array.isArray(val) ? [...val] : val
            item.currentSelectValue = Array.isArray(val) ? [...val] : val
          }
        })
      })
    })
    queryElement.value.cascade = cloneDeep(cascadeArr)
    cascadeArr = []
    queryElement.value.propValue = cloneDeep(conditions.value)
    snapshotStore.recordSnapshotCache('confirmClick')
    curComponent.value.id = ''
    relationshipChartIndex.value = 0
    nextTick(() => {
      emits('reRenderAll', oldArr, cloneDeep(unref(conditions)))
      emits('queryData')
    })
  })
}

// 当前条件值来源可选择的字段列表，按展示类型过滤字段类型兼容项
const fieldsComputed = computed(() => {
  return curComponent.value.dataset.fields.filter(ele => {
    return (
      ele.fieldType === +curComponent.value.displayType ||
      ([0, 2, 3, 4].includes(ele.fieldType) && [0, 2].includes(+curComponent.value.displayType)) ||
      (ele.fieldType === 7 && +curComponent.value.displayType === 0)
    )
  })
})

// 取消手动值来源编辑，恢复为当前条件已保存的值来源
const cancelValueSource = () => {
  valueSource.value = cloneDeep(curComponent.value.valueSource)
  if (!valueSource.value.length) {
    valueSource.value.push('')
  }
  manual.value.hide()
}

// 确认手动值来源编辑，过滤空值后写回当前条件
const confirmValueSource = () => {
  if (
    valueSource.value.some(ele => {
      if (typeof ele === 'string') {
        return !ele.trim()
      }
      return false
    })
  ) {
    ElMessage.error(t('v_query.cannot_be_empty_input'))
    return
  }

  curComponent.value.valueSource = cloneDeep(
    valueSource.value.filter(ele => {
      if (typeof ele === 'string') {
        return ele.trim()
      }
      return true
    })
  )
  handleValueSourceChange()
  cancelValueSource()
}

// 载入已有查询条件并打开指定条件的配置面板
const setCondition = (queryId: string) => {
  conditions.value = (cloneDeep(props.queryElement.propValue) || []).map(ele =>
    parameterCompletion(ele)
  )
  init(queryId)
}

// 从外部新增一个查询条件并立即进入配置
const setConditionOut = () => {
  conditions.value = (cloneDeep(props.queryElement.propValue) || []).map(ele =>
    parameterCompletion(ele)
  )
  addQueryCriteria()
  init(conditions.value[conditions.value.length - 1].id)
}

// 根据字段 id 定位其所在的维度、指标或参数页签
const setActiveSelectTab = (arr, id) => {
  let activelist = 'dimensionList'
  arr.some((ele, index) => {
    if ((ele || []).some(itx => itx.id === id)) {
      activelist = ['dimensionList', 'quotaList', 'parameterList'][index]
      return true
    }
    return false
  })

  return activelist
}

// 初始化弹窗数据，加载画布字段关联的数据集详情和参数字段
const init = (queryId: string) => {
  initDataset()
  relationshipChartIndex.value = 0
  renameInput.value = []
  handleCondition({ id: queryId })
  cascadeArr = cloneDeep(queryElement.value.cascade || [])
  dialogVisible.value = true
  const datasetFieldIdList = datasetFieldList.value.map(ele => ele.tableId)
  for (const i in datasetMap) {
    if (!datasetFieldIdList.includes(i)) {
      delete datasetMap[i]
    }
  }

  const datasetMapKeyList = Object.keys(datasetMap)

  if (datasetFieldIdList.every(ele => datasetMapKeyList.includes(ele))) {
    fields.value = datasetFieldList.value
      .map(ele => {
        if (!datasetMap[ele.tableId]) return null
        return { ...datasetMap[ele.tableId], componentId: ele.id }
      })
      .filter(ele => !!ele)
  }
  const params = [...new Set(datasetFieldList.value.map(ele => ele.tableId).filter(ele => !!ele))]
  if (!params.length) return
  Promise.all([getDsDetailsWithPerm(params), sqlParams(params)])
    .then(([dq, p]) => {
      dq.filter(ele => !!ele).forEach(ele => {
        ele.activelist = 'dimensionList'
        ele.fields.parameterList = p.filter(
          itx => itx.datasetGroupId === ele.id && !itx.params?.length
        ) as any
        ele.hasParameter = !!ele.fields.parameterList.length
        ele.fields.dimensionList = (ele.fields.dimensionList || []).filter(
          itx => !itx.params?.length
        )
        ele.fields.quotaList = (ele.fields.quotaList || []).filter(itx => !itx.params?.length)
        datasetMap[ele.id] = ele
      })
      fields.value = datasetFieldList.value
        .map(ele => {
          if (!datasetMap[ele.tableId]) return null
          const activeCom = datasetMap[ele.tableId].fields || {}
          const activelist = setActiveSelectTab(
            [activeCom.dimensionList, activeCom.quotaList, activeCom.parameterList],
            curComponent.value.checkedFieldsMap[ele.id]
          )
          return { ...datasetMap[ele.tableId], componentId: ele.id, activelist }
        })
        .filter(ele => !!ele)
    })
    .finally(() => {
      if (!curComponent.value.treeDatasetId) {
        nextTick(() => {
          setTreeDefault()
        })
      }
      handleCheckedFieldsChange(curComponent.value.checkedFields)
    })
}

// 去重手动值来源，保留用户输入顺序中的唯一值
const weightlessness = () => {
  valueSource.value = Array.from(new Set(valueSource.value))
}

// 为旧配置补齐新增属性的默认值，保证后续逻辑可以按完整结构读取
const parameterCompletion = ele => {
  const attributes = {
    timeType: 'fixed',
    hideConditionSwitching: false,
    required: false,
    defaultMapValue: [],
    mapValue: [],
    parametersStart: null,
    conditionType: 0,
    conditionValueOperatorF: 'eq',
    conditionValueF: '',
    conditionValueOperatorS: 'like',
    conditionValueS: '',
    resultMode: 0,
    defaultConditionValueOperatorF: 'eq',
    defaultConditionValueF: '',
    defaultConditionValueOperatorS: 'like',
    defaultConditionValueS: '',
    parametersEnd: null,
    relativeToCurrent: 'custom',
    timeNum: 0,
    relativeToCurrentRange: 'custom',
    relativeToCurrentType: 'year',
    around: 'f',
    arbitraryTime: new Date(),
    timeNumRange: 0,
    relativeToCurrentTypeRange: 'year',
    aroundRange: 'f',
    treeDatasetId: '',
    displayId: '',
    sortId: '',
    sort: 'asc',
    arbitraryTimeRange: new Date(),
    setTimeRange: false,
    showEmpty: false,
    defaultNumValueStart: null,
    defaultNumValueEnd: null,
    numValueEnd: null,
    numValueStart: null,
    displayFormat: 0,
    timeRange: {
      intervalType: 'none',
      dynamicWindow: false,
      maximumSingleQuery: 0,
      regularOrTrends: 'fixed',
      regularOrTrendsValue: '',
      relativeToCurrent: 'custom',
      timeNum: 0,
      relativeToCurrentType: 'year',
      around: 'f',
      timeNumRange: 0,
      relativeToCurrentTypeRange: 'year',
      aroundRange: 'f'
    },
    oldTreeLoad: false,
    treeCheckedList: [],
    defaultValueFirstItem: false,
    treeFieldList: []
  }
  Object.entries(attributes).forEach(([key, val]) => {
    ele[key] ?? (ele[key] = val)
  })

  if (!ele.treeDatasetId) {
    ele.treeDatasetId = ele.dataset.id
  }

  if (!ele.timeRange.relativeToCurrentRange) {
    ele.timeRange.relativeToCurrentRange = 'custom'
  }

  return ele
}

// 切换当前编辑条件，并补齐字段映射、范围映射和树形配置状态
const handleCondition = (item, idx = 0) => {
  handleDialogClick()
  if (activeConditionForRename.id) return
  activeCondition.value = item.id
  const obj = conditions.value.find(ele => ele.id === item.id)
  if (!obj.checkedFieldsMapArr) {
    obj.checkedFieldsMapArr = {}
    obj.checkedFieldsMapArrNum = {}
    obj.checkedFieldsMapStart = {}
    obj.checkedFieldsMapStartNum = {}
    obj.checkedFieldsMapEnd = {}
    obj.checkedFieldsMapEndNum = {}
    obj.parametersArr = {}
  }
  curComponent.value = obj
  curComponent.value.dataset.fields = []
  nextTick(() => {
    defaultConfigurationRef.value.changeMultiple(curComponent.value.multiple)
  })
  if (curComponent.value.dataset.id) {
    listFieldsWithPermissions(curComponent.value.dataset.id).then(res => {
      curComponent.value.dataset.fields = res.data
    })
  }
  if (!curComponent.value.checkedFieldsMapStart) {
    curComponent.value.checkedFieldsMapStart = {}
  }
  if (!curComponent.value.checkedFieldsMapStartNum) {
    curComponent.value.checkedFieldsMapStartNum = {}
  }
  if (!curComponent.value.checkedFieldsMapEnd) {
    curComponent.value.checkedFieldsMapEnd = {}
  }
  if (!curComponent.value.checkedFieldsMapEndNum) {
    curComponent.value.checkedFieldsMapEndNum = {}
  }
  if (!curComponent.value.checkedFieldsMapArr) {
    curComponent.value.checkedFieldsMapArr = {}
  }
  if (!curComponent.value.checkedFieldsMapArrNum) {
    curComponent.value.checkedFieldsMapArrNum = {}
  }
  if (!curComponent.value.parametersArr) {
    curComponent.value.parametersArr = {}
  }
  datasetFieldList.value.forEach(ele => {
    if (!curComponent.value.checkedFieldsMap[ele.id]) {
      curComponent.value.checkedFieldsMap[ele.id] = ''
    }
    if (!curComponent.value.checkedFieldsMapStart[ele.id]) {
      curComponent.value.checkedFieldsMapStart[ele.id] = ''
    }

    if (!curComponent.value.checkedFieldsMapStartNum[ele.id]) {
      curComponent.value.checkedFieldsMapStartNum[ele.id] = ''
    }
    if (!curComponent.value.checkedFieldsMapEnd[ele.id]) {
      curComponent.value.checkedFieldsMapEnd[ele.id] = ''
    }
    if (!curComponent.value.checkedFieldsMapEndNum[ele.id]) {
      curComponent.value.checkedFieldsMapEndNum[ele.id] = ''
    }
    if (!curComponent.value.checkedFieldsMapArr[ele.id]) {
      curComponent.value.checkedFieldsMapArr[ele.id] = []
    }
    if (!curComponent.value.checkedFieldsMapArrNum[ele.id]) {
      curComponent.value.checkedFieldsMapArrNum[ele.id] = []
    }
    if (!curComponent.value.parametersArr[ele.id]) {
      curComponent.value.parametersArr[ele.id] = []
    }
  })

  const idMap = datasetFieldList.value.map(ele => ele.id)
  curComponent.value.checkedFields = curComponent.value.checkedFields.filter(ele =>
    idMap.includes(ele)
  )
  if (!!fields.value?.length) {
    fields.value.forEach(ele => {
      const activeCom = ele.fields
      ele.activelist = setActiveSelectTab(
        [activeCom.dimensionList, activeCom.quotaList, activeCom.parameterList],
        curComponent.value.checkedFieldsMap[ele.componentId]
      )
    })
    handleCheckedFieldsChange(curComponent.value.checkedFields)
  }
  multipleChange(curComponent.value.multiple)
  // 手动值来源编辑使用副本，切换条件时重新从当前条件恢复。
  valueSource.value = cloneDeep(curComponent.value.valueSource)
  if (!valueSource.value.length) {
    valueSource.value.push('')
  }
  nextTick(() => {
    if (curComponent.value.displayType === '9') {
      oldDisplayType = '9'
      handleRelationshipChart(idx, true)
      if (!curComponent.value.treeDatasetId && fields.value?.length) {
        nextTick(() => {
          setTreeDefault()
        })
      } else if (curComponent.value.treeDatasetId) {
        getOptions(curComponent.value.treeDatasetId, curComponent.value)
      }
    }
    curComponent.value.showError = showError.value
    curComponent.value.auto && (document.querySelector('.chart-field').scrollTop = 0)
  })
}

// 加载指定数据集的可选字段，并写入对应条件配置
const getOptions = (id, component) => {
  listFieldsWithPermissions(id).then(res => {
    component.dataset.fields = res.data
  })
}

// 排序字段变化时清理自定义排序，并在排序字段失效时重置排序方式
const handleSortChange = () => {
  handleFieldChange()
  curComponent.value.sortList = []
  if (sortComputed.value) {
    curComponent.value.sort = ''
  }
}

// 重置排序相关状态，并在默认值启用时清空已选默认值
const resetSort = () => {
  if (sortComputed.value) {
    curComponent.value.sort = ''
  }
  if (!curComponent.value.defaultValueCheck) return
  curComponent.value.defaultValue = curComponent.value.multiple ? [] : undefined
}

// 自定义排序弹窗组件引用
const customSortFilterRef = ref()

// 保存自定义排序列表到当前条件
const sortSave = list => {
  curComponent.value.sortList = cloneDeep(list)
}

// 打开自定义排序配置，首次打开时从枚举接口加载当前字段取值
const handleCustomClick = async () => {
  if (sortComputed.value || curComponent.value.sort !== 'customSort') return
  let list = cloneDeep(curComponent.value.sortList || [])
  if (!list.length) {
    const arr = await enumValueObj({ queryId: curComponent.value.sortId, searchText: '' })
    list = arr.map(ele => ele[curComponent.value.sortId])
  }
  customSortFilterRef.value.sortInit([...new Set(list)])
}

// 判断排序字段与展示字段是否不一致，不一致时禁用自定义排序
const sortComputed = computed(() => {
  const { sortId, displayId } = curComponent.value
  return sortId && displayId && sortId !== displayId
})

// 树形字段设计弹窗组件引用
const treeDialog = ref()
// 打开树形字段设计，仅允许维度文本字段作为层级字段
const startTreeDesign = () => {
  treeDialog.value.init(
    curComponent.value.dataset.fields.filter(ele => ele.groupType === 'd' && ele.fieldType === 0),
    curComponent.value.treeFieldList
  )
}
// 保存树形层级字段，并同步每个层级的字段映射
const saveTree = arr => {
  curComponent.value.treeFieldList = arr
  setSameField()
}

// 根据树形层级字段同步各组件字段映射，保证关系图每层都有可用配置
const setSameField = () => {
  curComponent.value.treeFieldList.forEach((ele, index) => {
    if (!curComponent.value.treeCheckedList[index]) {
      // 新增树层级时复制当前绑定关系作为初始值，减少逐层重复选择。
      curComponent.value.treeCheckedList = [
        ...curComponent.value.treeCheckedList,
        {
          checkedFields: [...curComponent.value.checkedFields],
          checkedFieldsMap: cloneDeep(curComponent.value.checkedFieldsMap)
        }
      ]
    }
    fields.value.forEach(item => {
      const ids = item.fields.dimensionList.map(itx => itx.id)
      if (ids.includes(ele.id)) {
        curComponent.value.treeCheckedList[index].checkedFieldsMap[item.componentId] = ele.id
      }
    })
  })

  curComponent.value.checkedFields =
    curComponent.value.treeCheckedList[relationshipChartIndex.value].checkedFields
  curComponent.value.checkedFieldsMap =
    curComponent.value.treeCheckedList[relationshipChartIndex.value].checkedFieldsMap
}

// 当前条件是否存在必填配置缺失，用于条件列表中的错误标记
const showError = computed(() => {
  if (!curComponent.value) return false
  const {
    optionValueSource,
    checkedFieldsMap,
    checkedFields,
    field,
    valueSource,
    displayType,
    treeCheckedList,
    treeFieldList
  } = curComponent.value
  const arr = checkedFields.filter(ele => !!checkedFieldsMap[ele])
  if (!checkedFields.length || !arr.length) {
    return true
  }

  if (9 === +displayType) {
    for (const key in treeCheckedList) {
      if (key > treeFieldList.length) continue
      const treeArr = treeCheckedList[key].checkedFields.filter(
        ele => !!treeCheckedList[key].checkedFieldsMap[ele]
      )
      if (!treeCheckedList[key].checkedFields.length || !treeArr.length) {
        return true
      }
    }
  }

  if ([1, 7, 8, 22, 9].includes(+displayType)) {
    return false
  }
  return (optionValueSource === 1 && !field.id) || (optionValueSource === 2 && !valueSource.length)
})
// 关闭默认值子组件内部弹层，避免点击父弹窗时保留悬浮层
const handleDialogClick = () => {
  defaultConfigurationRef.value?.handleDialogClick()
}

// 单时间条件的相对当前时间快捷选项，按当前时间粒度生成
const relativeToCurrentList = computed(() => {
  let list = []
  if (!curComponent.value) return list
  switch (curComponent.value.timeGranularity) {
    case 'year':
      list = [
        {
          label: t('dynamic_year.current'),
          value: 'thisYear'
        },
        {
          label: t('dynamic_year.last'),
          value: 'lastYear'
        }
      ]
      break
    case 'month':
      list = [
        {
          label: t('cron.this_month'),
          value: 'thisMonth'
        },
        {
          label: t('dynamic_month.last'),
          value: 'lastMonth'
        }
      ]
      break
    case 'date':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('dynamic_time.firstOfMonth'),
          value: 'monthBeginning'
        },
        {
          label: t('dynamic_time.endOfMonth'),
          value: 'monthEnd'
        },
        {
          label: t('dynamic_time.firstOfYear'),
          value: 'yearBeginning'
        }
      ]
      break
    case 'datetime':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('dynamic_time.firstOfMonth'),
          value: 'monthBeginning'
        },
        {
          label: t('dynamic_time.endOfMonth'),
          value: 'monthEnd'
        },
        {
          label: t('dynamic_time.firstOfYear'),
          value: 'yearBeginning'
        }
      ]
      break

    default:
      break
  }

  return [
    ...list,
    {
      label: t('dynamic_time.custom'),
      value: 'custom'
    }
  ]
})

// 时间范围条件的相对当前时间快捷选项，按范围粒度生成
const relativeToCurrentListRange = computed(() => {
  let list = []
  if (!curComponent.value) return list
  switch (curComponent.value.timeGranularityMultiple) {
    case 'yearrange':
      list = [
        {
          label: t('dynamic_year.current'),
          value: 'thisYear'
        },
        {
          label: t('dynamic_year.last'),
          value: 'lastYear'
        }
      ]
      break
    case 'monthrange':
      list = [
        {
          label: t('cron.this_month'),
          value: 'thisMonth'
        },
        {
          label: t('dynamic_month.last'),
          value: 'lastMonth'
        },
        {
          label: t('v_query.last_3_months'),
          value: 'LastThreeMonths'
        },
        {
          label: t('v_query.last_6_months'),
          value: 'LastSixMonths'
        },
        {
          label: t('v_query.last_12_months'),
          value: 'LastTwelveMonths'
        },
        {
          label: t('common.to_this_month'),
          value: 'YearToThisMonth'
        }
      ]
      break
    case 'daterange':
    case 'datetimerange':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('v_query.last_3_days'),
          value: 'LastThreeDays'
        },
        {
          label: t('v_query.month_to_date'),
          value: 'monthBeginning'
        },
        {
          label: t('v_query.year_to_date'),
          value: 'yearBeginning'
        },
        {
          label: t('common.month_to_yesterday'),
          value: 'monthToYesterday'
        }
      ]
      break

    default:
      break
  }

  return [
    ...list,
    {
      label: t('dynamic_time.custom'),
      value: 'custom'
    }
  ]
})

// 单时间粒度变化时同步相对时间单位和默认快捷选项
const timeGranularityChange = (val: string) => {
  curComponent.value.relativeToCurrentType = ['date', 'datetime'].includes(val) ? 'date' : val
  if (curComponent.value.relativeToCurrent !== 'custom') {
    curComponent.value.relativeToCurrent = relativeToCurrentList.value[0]?.value
  }
}

// 时间类型配置变化时，根据单时间或范围模式分发到对应粒度处理
const handleTimeTypeChange = () => {
  if (curComponent.value.displayType === '1') {
    timeGranularityChange(curComponent.value.timeGranularity)
  } else {
    timeGranularityMultipleChange(curComponent.value.timeGranularityMultiple)
  }
}

// 时间范围粒度变化时同步起止时间单位，并初始化时间范围限制结构
const timeGranularityMultipleChange = (val: string) => {
  handleDialogClick()
  curComponent.value.relativeToCurrentType = ['daterange', 'datetimerange'].includes(val)
    ? 'date'
    : val.split('range')[0]
  curComponent.value.relativeToCurrentTypeRange = curComponent.value.relativeToCurrentType
  if (curComponent.value.relativeToCurrentRange !== 'custom') {
    curComponent.value.relativeToCurrentRange = relativeToCurrentListRange.value[0]?.value
  }

  if (curComponent.value.timeRange) return

  curComponent.value.timeRange = {
    intervalType: 'none',
    dynamicWindow: false,
    maximumSingleQuery: 0,
    regularOrTrends: 'fixed',
    regularOrTrendsValue: '',
    relativeToCurrent: 'custom',
    relativeToCurrentRange: 'custom',
    timeNum: 0,
    relativeToCurrentType: curComponent.value.relativeToCurrentRange,
    around: 'f',
    timeNumRange: 0,
    relativeToCurrentTypeRange: curComponent.value.relativeToCurrentRange,
    aroundRange: 'f'
  }
}
// 同步当前条件错误状态到条件对象，供列表和保存逻辑读取
watch(
  () => showError.value,
  val => {
    if (!curComponent.value) return
    curComponent.value.showError = val
  }
)

// 收集重命名输入框引用，进入重命名后用于聚焦
const setRenameInput = val => {
  renameInput.value.push(val)
}
// 树形关系图当前层级索引
const relationshipChartIndex = ref(0)
// 切换条件或树形关系层级，避免重复点击当前条件时丢失层级状态
const notCurrentEle = (ele, index) => {
  if (activeCondition.value !== ele.id) {
    handleCondition(ele, index)
  } else {
    handleRelationshipChart(index)
  }
}

// 保存当前树形层级的字段选择，切换层级前写回缓存
const setRelationBack = () => {
  curComponent.value.treeCheckedList[relationshipChartIndex.value] = {
    checkedFields: [...curComponent.value.checkedFields],
    checkedFieldsMap: cloneDeep(curComponent.value.checkedFieldsMap)
  }
}
// 切换树形关系图层级，并恢复该层级对应的字段选择状态
const handleRelationshipChart = (index, initShip = false) => {
  if (curComponent.value.treeCheckedList?.length && !initShip) {
    curComponent.value.treeCheckedList[relationshipChartIndex.value] = {
      checkedFields: [...curComponent.value.checkedFields],
      checkedFieldsMap: cloneDeep(curComponent.value.checkedFieldsMap)
    }
  }
  relationshipChartIndex.value = index
  if (!curComponent.value?.treeCheckedList?.length && !curComponent.value.oldTreeLoad) {
    curComponent.value.treeCheckedList = curComponent.value.treeFieldList.map(ele => {
      return {
        checkedFields: [...curComponent.value.checkedFields],
        checkedFieldsMap: curComponent.value.checkedFields.reduce((pre, next) => {
          pre[next] = ele.id
          return pre
        }, {})
      }
    })
  } else if (!curComponent.value?.treeCheckedList?.length && curComponent.value.oldTreeLoad) {
    curComponent.value.treeCheckedList = curComponent.value.treeFieldList.map(() => {
      return {
        checkedFields: [...curComponent.value.checkedFields],
        checkedFieldsMap: cloneDeep(curComponent.value.checkedFieldsMap)
      }
    })
  }
  if (!curComponent.value?.treeCheckedList[index]) return
  const { checkedFields, checkedFieldsMap } = curComponent.value?.treeCheckedList[index]
  curComponent.value.checkedFields = checkedFields
  curComponent.value.checkedFieldsMap = checkedFieldsMap
  const checkedCount = checkedFields?.length
  checkAll.value = checkedCount === fields.value?.length
  isIndeterminate.value = checkedCount > 0 && checkedCount < fields.value?.length
}

// 处理条件列表的删除和重命名命令
const addOperation = (cmd, condition, index) => {
  switch (cmd) {
    case 'del':
      renameInput.value = []
      conditions.value.splice(index, 1)
      curComponent.value = null
      break
    case 'rename':
      renameInput.value = []
      Object.assign(activeConditionForRename, condition)
      setTimeout(() => {
        nextTick(() => {
          renameInput.value[0]?.focus()
        })
      }, 400)
      break
    default:
      break
  }
}
// 数据集树选择器字段映射配置
const dsSelectProps = {
  label: 'name',
  children: 'children',
  value: 'id',
  isLeaf: node => !node.children?.length
}

// 过滤数据集树中的空目录，只保留可选数据集叶子节点和含叶子的目录
const dfs = arr => {
  return (arr || []).filter(ele => {
    if (!!ele.children?.length && !ele.leaf) {
      ele.children = dfs(ele.children) || []
      return !!ele.children?.length
    }
    return ele.leaf
  })
}

// 重命名输入框失焦后校验名称并写回条件列表
const renameInputBlur = () => {
  if (activeConditionForRename.name.trim() === '') {
    ElMessage.error(t('v_query.cannot_be_empty_name'))
    renameInput.value[0]?.focus()
    return
  }
  conditions.value.some(ele => {
    if (activeConditionForRename.id === ele.id) {
      ele.name = activeConditionForRename.name
      return true
    }
    return false
  })
  activeConditionForRename.id = ''
}

// 新增一个查询条件，并补齐当前版本所需的默认属性
const addQueryCriteria = () => {
  relationshipChartIndex.value = 0
  conditions.value.push(parameterCompletion(addQueryCriteriaConfig()))
}

// 新增查询条件后立即选中，供弹窗内新增按钮使用
const addQueryCriteriaAndSelect = () => {
  addQueryCriteria()
  handleCondition(conditions.value[conditions.value.length - 1])
}

// 对外新增条件并返回新条件 id，供画布工具栏调用后定位条件
const addCriteriaConfig = () => {
  addQueryCriteria()
  return conditions.value[conditions.value.length - 1].id
}

defineExpose({
  setCondition,
  addCriteriaConfig,
  setConditionOut
})
</script>

<template>
  <el-dialog
    class="query-condition-configuration"
    v-model="dialogVisible"
    width="1200px"
    :title="t('v_query.query_condition_setting')"
    @click.stop
    :before-close="handleBeforeClose"
    @mousedown.stop
    @mousedup.stop
  >
    <div class="container" @click="handleDialogClick">
      <div class="query-condition-list">
        <div class="title">
          {{ t('v_query.query_condition') }}
          <el-icon @click="addQueryCriteriaAndSelect">
            <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
          </el-icon>
        </div>
        <draggable tag="div" :list="conditions" handle=".handle">
          <template #item="{ element, index }">
            <div
              :key="element.id"
              @dblclick.stop="addOperation('rename', element, index)"
              @click.stop="handleCondition(element)"
              class="list-item_box"
              :style="{
                marginBottom: element.treeFieldList
                  ? element.treeFieldList.slice(1).length * 40 + 'px'
                  : 0
              }"
            >
              <div
                class="list-item_primary"
                :class="element.id === activeCondition && relationshipChartIndex === 0 && 'active'"
              >
                <el-icon class="handle">
                  <Icon name="icon_drag_outlined"><icon_drag_outlined class="svg-icon" /></Icon>
                </el-icon>
                <div class="label flex-align-center icon" :title="element.name">
                  <el-icon
                    v-if="!element.auto && element.showError"
                    style="font-size: 16px; color: #f54a45"
                  >
                    <icon name="icon_warning_filled"><icon_warning_filled class="svg-icon" /></icon>
                  </el-icon>
                  {{ element.name }}
                </div>
                <div class="condition-icon flex-align-center">
                  <handle-more
                    @handle-command="cmd => addOperation(cmd, element, index)"
                    :menu-list="typeList"
                    :icon-name="more_v"
                    placement="bottom-end"
                  ></handle-more>
                  <el-icon
                    class="hover-icon"
                    @click.stop="element.visible = !element.visible"
                    v-if="element.visible"
                  >
                    <Icon name="icon_visible_outlined"
                      ><icon_visible_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                  <el-icon
                    class="hover-icon"
                    @click.stop="element.visible = !element.visible"
                    v-else
                  >
                    <Icon name="password-invisible"><passwordInvisible class="svg-icon" /></Icon>
                  </el-icon>
                </div>
                <div @click.stop v-if="activeConditionForRename.id === element.id" class="rename">
                  <el-input
                    @blur="renameInputBlur"
                    :ref="setRenameInput"
                    v-model="activeConditionForRename.name"
                  ></el-input>
                </div>
              </div>
              <template v-if="element.treeFieldList">
                <!-- 树形查询条件在左侧额外展示层级节点，点击层级可维护该层关联图表。 -->
                <div
                  :class="
                    element.id === activeCondition &&
                    relationshipChartIndex === Number(index) + 1 &&
                    'active'
                  "
                  class="list-item_primary list-tree_primary"
                  :style="{
                    top: 40 * (Number(index) + 1) + 'px',
                    paddingLeft: 32 + 16 * (Number(index) + 1) + 'px'
                  }"
                  v-for="(itx, index) in element.treeFieldList.slice(1)"
                  :key="itx.field"
                  @click.stop="notCurrentEle(element, Number(index) + 1)"
                >
                  {{ itx.name }}
                </div>
              </template>
            </div>
          </template>
        </draggable>
      </div>
      <div v-if="!!curComponent" class="chart-field" :class="curComponent.auto && 'hidden'">
        <!-- 字段绑定区控制查询条件作用到哪些图表字段，自动模式下只展示不可编辑遮罩。 -->
        <el-scrollbar>
          <div class="mask" v-if="curComponent.auto"></div>
          <div class="title flex-align-center">
            {{ t('v_query.chart_and_field') }}
            <el-radio-group class="ml-4 larger-radio" v-model="curComponent.auto">
              <el-radio :disabled="!curComponent.auto" :label="true">
                <div class="flex-align-center">
                  {{ t('chart.margin_model_auto') }}
                  <el-tooltip effect="dark" placement="top">
                    <template #content>
                      <div>
                        {{ t('v_query.be_switched_to') }}
                        <br />
                        {{ t('v_query.to_automatic_again') }}
                      </div>
                    </template>
                    <el-icon style="margin-left: 4px; color: #646a73">
                      <icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></icon>
                    </el-icon>
                  </el-tooltip>
                </div>
              </el-radio>
              <el-radio :label="false">{{ t('commons.custom') }}</el-radio>
            </el-radio-group>
          </div>
          <div class="select-all">
            <el-checkbox
              v-model="checkAll"
              :indeterminate="isIndeterminate"
              @change="handleCheckAllChange"
              >{{ t('dataset.check_all') }}</el-checkbox
            >
          </div>
          <div class="field-list">
            <el-checkbox-group
              v-model="curComponent.checkedFields"
              @change="handleCheckedFieldsChangeTree"
            >
              <div v-for="field in fields" :key="field.componentId" class="list-item-field">
                <el-checkbox :label="field.componentId"
                  ><el-icon class="component-type">
                    <Icon
                      ><component
                        :is="iconChartMap[canvasViewInfo[field.componentId].type]"
                      ></component
                    ></Icon> </el-icon
                  ><span
                    :title="canvasViewInfo[field.componentId].title"
                    class="checkbox-name ellipsis"
                    >{{ canvasViewInfo[field.componentId].title }}</span
                  ></el-checkbox
                >
                <span :title="field.name" class="dataset ellipsis">{{ field.name }}</span>
                <el-select
                  @change="val => setParametersArr(val, field.componentId)"
                  @focus="handleDialogClick"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  key="checkedFieldsMapArrTime"
                  :multiple-limit="2"
                  class="field-select--input"
                  style="margin-left: 12px"
                  popper-class="field-select--dqp"
                  v-if="
                    curComponent.checkedFields.includes(field.componentId) &&
                    curComponent.checkedFieldsMapArr &&
                    curComponent.checkedFieldsMapArr[field.componentId] &&
                    curComponent.checkedFieldsMapArr[field.componentId].length
                  "
                  v-model="curComponent.checkedFieldsMapArr[field.componentId]"
                  clearable
                >
                  <!-- 时间范围参数最多选择两个字段，并通过字段后缀标记开始时间或结束时间。 -->
                  <template v-if="curComponent.checkedFieldsMap[field.componentId]" #prefix>
                    <el-icon>
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[
                              getDetype(
                                curComponent.checkedFieldsMap[field.componentId],
                                Object.values(field.fields)
                              )
                            ]
                          }`"
                          :is="
                            iconFieldMap[
                              fieldType[
                                getDetype(
                                  curComponent.checkedFieldsMap[field.componentId],
                                  Object.values(field.fields)
                                )
                              ]
                            ]
                          "
                        ></component
                      ></Icon>
                    </el-icon>
                  </template>
                  <template #header>
                    <el-tabs stretch class="params-select--header" v-model="field.activelist">
                      <el-tab-pane
                        disabled
                        :label="t('chart.dimension')"
                        name="dimensionList"
                      ></el-tab-pane>
                      <el-tab-pane
                        disabled
                        :label="t('chart.quota')"
                        name="quotaList"
                      ></el-tab-pane>
                      <el-tab-pane :label="t('dataset.param')" name="parameterList"></el-tab-pane>
                    </el-tabs>
                  </template>
                  <el-option
                    v-for="ele in field.fields[field.activelist]"
                    :key="ele.id"
                    :label="ele.name || ele.variableName"
                    :value="ele.id"
                    :disabled="isParametersDisable(ele)"
                  >
                    <div class="flex-align-center icon">
                      <el-icon>
                        <Icon :className="`field-icon-${fieldType[ele.fieldType]}`"
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${fieldType[ele.fieldType]}`"
                            :is="iconFieldMap[fieldType[ele.fieldType]]"
                          ></component
                        ></Icon>
                      </el-icon>
                      <span :title="ele.name || ele.variableName" class="ellipsis">
                        {{ ele.name || ele.variableName }}
                      </span>
                      <span
                        v-if="
                          curComponent.checkedFieldsMapArr[field.componentId].includes(ele.id) &&
                          field.activelist === 'parameterList'
                        "
                        @click.stop="timeClick(field.componentId, ele)"
                        class="range-time_setting"
                      >
                        {{
                          curComponent.checkedFieldsMapStart[field.componentId] === ele.id
                            ? t('dataset.start_time')
                            : curComponent.checkedFieldsMapEnd[field.componentId] === ele.id
                            ? t('dataset.end_time')
                            : ''
                        }}
                        <el-icon>
                          <Icon>
                            <icon_edit_outlined class="svg-icon"></icon_edit_outlined>
                          </Icon>
                        </el-icon>
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <el-select
                  @change="val => setParametersArrNum(val, field.componentId)"
                  @focus="handleDialogClick"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  key="checkedFieldsMapArr"
                  :multiple-limit="2"
                  class="field-select--input"
                  style="margin-left: 12px"
                  popper-class="field-select--dqp"
                  v-else-if="
                    curComponent.checkedFields.includes(field.componentId) &&
                    curComponent.checkedFieldsMapArrNum &&
                    curComponent.checkedFieldsMapArrNum[field.componentId] &&
                    curComponent.checkedFieldsMapArrNum[field.componentId].length
                  "
                  v-model="curComponent.checkedFieldsMapArrNum[field.componentId]"
                  clearable
                >
                  <!-- 数值范围参数最多选择两个数值字段，并分别映射为最小值和最大值。 -->
                  <template v-if="curComponent.checkedFieldsMap[field.componentId]" #prefix>
                    <el-icon>
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[
                              getDetype(
                                curComponent.checkedFieldsMap[field.componentId],
                                Object.values(field.fields)
                              )
                            ]
                          }`"
                          :is="
                            iconFieldMap[
                              fieldType[
                                getDetype(
                                  curComponent.checkedFieldsMap[field.componentId],
                                  Object.values(field.fields)
                                )
                              ]
                            ]
                          "
                        ></component
                      ></Icon>
                    </el-icon>
                  </template>
                  <template #header>
                    <el-tabs stretch class="params-select--header" v-model="field.activelist">
                      <el-tab-pane
                        disabled
                        :label="t('chart.dimension')"
                        name="dimensionList"
                      ></el-tab-pane>
                      <el-tab-pane
                        disabled
                        :label="t('chart.quota')"
                        name="quotaList"
                      ></el-tab-pane>
                      <el-tab-pane :label="t('dataset.param')" name="parameterList"></el-tab-pane>
                    </el-tabs>
                  </template>
                  <el-option
                    v-for="ele in field.fields[field.activelist]"
                    :key="ele.id"
                    :label="ele.name || ele.variableName"
                    :value="ele.id"
                    :disabled="![2, 3].includes(ele.fieldType)"
                  >
                    <div class="flex-align-center icon">
                      <el-icon>
                        <Icon :className="`field-icon-${fieldType[ele.fieldType]}`"
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${fieldType[ele.fieldType]}`"
                            :is="iconFieldMap[fieldType[ele.fieldType]]"
                          ></component
                        ></Icon>
                      </el-icon>
                      <span :title="ele.name || ele.variableName" class="ellipsis">
                        {{ ele.name || ele.variableName }}
                      </span>
                      <span
                        v-if="
                          curComponent.checkedFieldsMapArrNum[field.componentId].includes(ele.id) &&
                          field.activelist === 'parameterList'
                        "
                        @click.stop="numClick(field.componentId, ele)"
                        class="range-time_setting"
                      >
                        {{
                          curComponent.checkedFieldsMapStartNum[field.componentId] === ele.id
                            ? t('chart.min')
                            : curComponent.checkedFieldsMapEndNum[field.componentId] === ele.id
                            ? t('chart.max')
                            : ''
                        }}
                        <el-icon>
                          <Icon>
                            <icon_edit_outlined class="svg-icon"></icon_edit_outlined>
                          </Icon>
                        </el-icon>
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <el-select
                  @change="setParameters(field)"
                  @focus="handleDialogClick"
                  filterable
                  style="margin-left: 12px"
                  popper-class="field-select--dqp"
                  v-else-if="curComponent.checkedFields.includes(field.componentId)"
                  v-model="curComponent.checkedFieldsMap[field.componentId]"
                  clearable
                >
                  <template v-if="curComponent.checkedFieldsMap[field.componentId]" #prefix>
                    <el-icon>
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[
                              getDetype(
                                curComponent.checkedFieldsMap[field.componentId],
                                Object.values(field.fields)
                              )
                            ]
                          }`"
                          :is="
                            iconFieldMap[
                              fieldType[
                                getDetype(
                                  curComponent.checkedFieldsMap[field.componentId],
                                  Object.values(field.fields)
                                )
                              ]
                            ]
                          "
                        ></component
                      ></Icon>
                    </el-icon>
                  </template>
                  <template #header>
                    <el-tabs stretch class="params-select--header" v-model="field.activelist">
                      <el-tab-pane :label="t('chart.dimension')" name="dimensionList"></el-tab-pane>
                      <el-tab-pane
                        :disabled="curComponent.displayType === '9'"
                        :label="t('chart.quota')"
                        name="quotaList"
                      ></el-tab-pane>
                      <el-tab-pane
                        v-if="field.hasParameter"
                        :label="t('dataset.param')"
                        :disabled="curComponent.displayType === '9'"
                        name="parameterList"
                      ></el-tab-pane>
                    </el-tabs>
                  </template>
                  <el-option
                    v-for="ele in field.fields[field.activelist]"
                    :key="ele.id"
                    :label="ele.name || ele.variableName"
                    :value="ele.id"
                    :disabled="
                      ele.desensitized ||
                      (curComponent.displayType === '9' && ele.fieldType === 1) ||
                      isParametersDisable(ele)
                    "
                  >
                    <div
                      class="flex-align-center icon"
                      :title="ele.desensitized ? t('v_query.as_query_conditions') : ''"
                    >
                      <el-icon>
                        <Icon :className="`field-icon-${fieldType[ele.fieldType]}`"
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${fieldType[ele.fieldType]}`"
                            :is="iconFieldMap[fieldType[ele.fieldType]]"
                          ></component
                        ></Icon>
                      </el-icon>
                      <span :title="ele.name || ele.variableName" class="ellipsis">
                        {{ ele.name || ele.variableName }}
                      </span>
                      <span
                        @click.stop="
                          () =>
                            isNumParameter
                              ? numClick(field.componentId, ele)
                              : timeClick(field.componentId, ele)
                        "
                        v-if="
                          curComponent.checkedFieldsMap[field.componentId] === ele.id &&
                          field.activelist === 'parameterList' &&
                          (isTimeParameter || isNumParameter)
                        "
                        class="range-time_setting"
                      >
                        {{ isNumParameter ? t('chart.value_formatter_value') : t('dataset.time') }}
                        <el-icon>
                          <Icon>
                            <icon_edit_outlined class="svg-icon"></icon_edit_outlined>
                          </Icon>
                        </el-icon>
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <span style="width: 172px; margin-left: 12px" v-else></span>
              </div>
            </el-checkbox-group>
          </div>
        </el-scrollbar>
      </div>
      <div
        v-if="!!curComponent"
        class="condition-configuration"
        :class="curComponent.auto && 'condition-configuration_hide'"
      >
        <!-- 条件配置区按展示类型收敛字段、参数、默认值和选项来源。 -->
        <el-scrollbar>
          <div class="mask condition" v-if="curComponent.auto"></div>
          <div class="title flex-align-center">
            {{ t('v_query.query_condition_configuration') }}
            <el-checkbox
              :disabled="
                curComponent.auto ||
                (curComponent.displayType === '9' && relationshipChartIndex !== 0)
              "
              v-model="curComponent.required"
              :label="t('v_query.required_items')"
            />
          </div>
          <div
            v-show="
              (curComponent.displayType !== '9' && showConfiguration && !showTypeError) ||
              (curComponent.displayType === '9' && relationshipChartIndex == 0)
            "
            class="configuration-list"
          >
            <div class="list-item">
              <div class="label">{{ t('v_query.display_type') }}</div>
              <div class="value">
                <el-select
                  @focus="handleDialogClick"
                  @change="handleSetTypeChange"
                  v-model="curComponent.displayType"
                >
                  <el-option
                    :disabled="!['0', '8', '9'].includes(curComponent.displayType)"
                    :label="t('v_query.text_drop_down')"
                    value="0"
                  />
                  <el-option
                    :disabled="!['0', '8', '9'].includes(curComponent.displayType)"
                    :label="t('v_query.text_search')"
                    value="8"
                  />
                  <el-option
                    :disabled="
                      !['0', '8', '9'].includes(curComponent.displayType) ||
                      !!curComponent.parameters.length
                    "
                    :label="t('v_query.drop_down_tree')"
                    value="9"
                  />

                  <template v-if="['2', '22'].includes(curComponent.displayType)">
                    <el-option
                      :disabled="!['2', '22'].includes(curComponent.displayType) || notNumRange"
                      :label="t('v_query.number_drop_down')"
                      value="2"
                    />
                    <el-option
                      :disabled="!['2', '22'].includes(curComponent.displayType) || canNotNumRange"
                      :label="t('v_query.number_range')"
                      value="22"
                    />
                  </template>
                  <el-option
                    v-else
                    :disabled="curComponent.displayType !== '5'"
                    :label="t('v_query.number_drop_down')"
                    value="5"
                  />
                  <el-option
                    :disabled="
                      !['1', '7'].includes(curComponent.displayType) ||
                      (isTimeParameter && notTimeRange)
                    "
                    :label="t('dataset.time')"
                    value="1"
                  />
                  <el-option
                    :disabled="
                      !['1', '7'].includes(curComponent.displayType) ||
                      (isTimeParameter && !notTimeRange)
                    "
                    :label="t('common.component.dateRange')"
                    value="7"
                  />
                </el-select>
              </div>
            </div>
            <div class="list-item" v-if="curComponent.displayType === '9'">
              <div :title="t('v_query.of_option_values')" class="label ellipsis">
                {{ t('v_query.of_option_values') }}
              </div>
              <div class="value">
                <el-radio-group class="larger-radio icon-info" v-model="curComponent.resultMode">
                  <el-radio :label="0"
                    >{{ t('login.default_login') }}
                    <el-tooltip effect="dark" :content="t('common.up_to_options')" placement="top">
                      <el-icon style="margin-left: 4px; color: #646a73">
                        <icon name="icon_info_outlined"
                          ><icon_info_outlined class="svg-icon"
                        /></icon>
                      </el-icon> </el-tooltip
                  ></el-radio>

                  <el-radio :label="1">{{ t('chart.result_mode_all') }}</el-radio>
                </el-radio-group>
              </div>
            </div>
            <div class="list-item" v-if="curComponent.displayType === '9'">
              <div :title="t('copilot.pls_choose_dataset')" class="label ellipsis">
                {{ t('copilot.pls_choose_dataset') }}
              </div>
              <div class="value">
                <el-tree-select
                  :teleported="false"
                  v-model="curComponent.treeDatasetId"
                  :data="datasetTree"
                  :placeholder="t('copilot.pls_choose_dataset')"
                  @change="handleDatasetTreeChange"
                  :props="dsSelectProps"
                  placement="bottom"
                  :render-after-expand="false"
                  filterable
                  popper-class="dataset-tree"
                >
                  <template #default="{ node, data }">
                    <div class="content">
                      <el-icon size="18px" v-if="!data.leaf">
                        <Icon><dvFolder class="svg-icon" /></Icon>
                      </el-icon>
                      <el-icon size="18px" v-if="data.leaf">
                        <Icon><icon_dataset class="svg-icon" /></Icon>
                      </el-icon>
                      <span
                        class="label-tree ellipsis"
                        style="margin-left: 8px"
                        :title="node.label"
                        >{{ node.label }}</span
                      >
                    </div>
                  </template>
                </el-tree-select>
              </div>
            </div>
            <div class="list-item" v-if="curComponent.displayType === '9'">
              <div class="label" style="width: 135px; height: 26px; line-height: 26px">
                {{ t('v_query.tree_structure_design') }}
                <el-button
                  v-if="curComponent.treeFieldList && !!curComponent.treeFieldList.length"
                  text
                  @click="startTreeDesign"
                >
                  <template #icon>
                    <icon><icon_edit_outlined class="svg-icon" /></icon>
                  </template>
                </el-button>
              </div>
              <div class="search-tree">
                <template v-if="curComponent.treeFieldList && !!curComponent.treeFieldList.length">
                  <div
                    v-for="(ele, index) in curComponent.treeFieldList"
                    :key="ele.id"
                    class="tree-field"
                  >
                    <span class="level-index"
                      >{{ t('visualization.level') }}{{ indexNumCascade[index] }}</span
                    >
                    <span class="field-type"
                      ><el-icon>
                        <Icon
                          ><component
                            :class="`field-icon-${fieldType[ele.fieldType]}`"
                            class="svg-icon"
                            :is="iconFieldMap[fieldType[ele.fieldType]]"
                          ></component
                        ></Icon> </el-icon
                    ></span>
                    <span class="field-tree_name ellipsis" :title="ele.name">{{ ele.name }}</span>
                    <span class="field-relationship_chart" v-if="index === 0">{{
                      t('common.associated_chart_first')
                    }}</span>
                    <span class="field-relationship_chart" v-else>
                      <el-button text @click="handleRelationshipChart(index)">
                        {{ t('common.associated_chart') }}
                      </el-button>
                    </span>
                  </div>
                </template>
                <el-button class="start-tree_design" @click="startTreeDesign" v-else text>
                  <template #icon>
                    <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
                  </template>
                  {{ t('v_query.the_tree_structure') }}
                </el-button>
              </div>
              <TreeFieldDialog ref="treeDialog" @save-tree="saveTree"></TreeFieldDialog>
            </div>
            <div class="list-item" v-if="['1', '7'].includes(curComponent.displayType)">
              <div :title="t('v_query.time_granularity')" class="label ellipsis">
                {{ t('v_query.time_granularity') }}
              </div>
              <div class="value">
                <template v-if="curComponent.displayType === '7' && !isTimeParameter">
                  <el-select
                    @change="timeGranularityMultipleChange"
                    :placeholder="t('v_query.the_time_granularity')"
                    @focus="handleDialogClick"
                    v-model="curComponent.timeGranularityMultiple"
                  >
                    <el-option :label="t('chart.y')" value="yearrange" />
                    <el-option :label="t('chart.y_M')" value="monthrange" />
                    <el-option :label="t('chart.y_M_d')" value="daterange" />
                    <el-option :label="t('chart.y_M_d_H_m_s')" value="datetimerange" />
                  </el-select>
                </template>
                <template v-else>
                  <el-select
                    @change="timeGranularityChange"
                    :placeholder="t('v_query.the_time_granularity')"
                    v-model="curComponent.timeGranularity"
                  >
                    <el-option
                      v-for="ele in timeParameterList"
                      :key="ele.value"
                      :label="ele.label"
                      :value="ele.value"
                    />
                  </el-select>
                </template>
              </div>
            </div>
            <div
              class="list-item top-item"
              v-if="!['1', '7', '8', '9', '22'].includes(curComponent.displayType)"
            >
              <div :title="t('v_query.option_value_source')" class="label ellipsis">
                {{ t('v_query.option_value_source') }}
              </div>
              <div class="value">
                <div class="value">
                  <el-radio-group
                    class="larger-radio"
                    @change="handleValueSourceChange"
                    v-model="curComponent.optionValueSource"
                  >
                    <el-radio :disabled="!!curComponent.parameters.length" :label="0">{{
                      t('chart.margin_model_auto')
                    }}</el-radio>
                    <el-radio :label="1">{{ t('chart.select_dataset') }}</el-radio>
                    <el-radio :label="2">{{ t('v_query.manual_input') }}</el-radio>
                  </el-radio-group>
                </div>
                <template v-if="curComponent.optionValueSource === 1">
                  <!-- 数据集来源模式通过查询字段、展示字段和排序字段共同定义下拉选项。 -->
                  <div class="value">
                    <el-tree-select
                      :teleported="false"
                      v-model="curComponent.dataset.id"
                      :data="datasetTree"
                      :placeholder="t('copilot.pls_choose_dataset')"
                      @change="handleDatasetChange"
                      @current-change="handleCurrentChange"
                      :props="dsSelectProps"
                      placement="bottom"
                      :render-after-expand="false"
                      filterable
                      popper-class="dataset-tree"
                    >
                      <template #default="{ node, data }">
                        <div class="content">
                          <el-icon size="18px" v-if="!data.leaf">
                            <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                          </el-icon>
                          <el-icon size="18px" v-if="data.leaf">
                            <Icon name="icon_dataset"><icon_dataset class="svg-icon" /></Icon>
                          </el-icon>
                          <span
                            class="label-tree ellipsis"
                            style="margin-left: 8px"
                            :title="node.label"
                            >{{ node.label }}</span
                          >
                        </div>
                      </template>
                    </el-tree-select>
                  </div>
                  <div style="display: flex; align-items: center" class="value ellipsis">
                    <span :title="t('v_query.query_field')" class="label">{{
                      t('v_query.query_field')
                    }}</span>
                    <el-select
                      @change="handleFieldChange"
                      :placeholder="t('v_query.query_field')"
                      class="search-field"
                      v-model="curComponent.field.id"
                    >
                      <template v-if="curComponent.field.id" #prefix>
                        <el-icon>
                          <Icon
                            ><component
                              class="svg-icon"
                              :class="`field-icon-${
                                fieldType[
                                  getDetype(curComponent.field.id, curComponent.dataset.fields)
                                ]
                              }`"
                              :is="
                                iconFieldMap[
                                  fieldType[
                                    getDetype(curComponent.field.id, curComponent.dataset.fields)
                                  ]
                                ]
                              "
                            ></component
                          ></Icon>
                        </el-icon>
                      </template>
                      <el-option
                        v-for="ele in curComponent.dataset.fields.filter(
                          ele =>
                            ele.fieldType === +curComponent.displayType ||
                            ([3, 4].includes(ele.fieldType) && +curComponent.displayType === 2) ||
                            (ele.fieldType === 7 && +curComponent.displayType === 0)
                        )"
                        :key="ele.id"
                        :label="ele.name"
                        :value="ele.id"
                        :disabled="ele.desensitized"
                      >
                        <div
                          class="flex-align-center icon"
                          :title="ele.desensitized ? t('v_query.as_query_conditions') : ''"
                        >
                          <el-icon>
                            <Icon :className="`field-icon-${fieldType[ele.fieldType]}`"
                              ><component
                                class="svg-icon"
                                :class="`field-icon-${fieldType[ele.fieldType]}`"
                                :is="iconFieldMap[fieldType[ele.fieldType]]"
                              ></component
                            ></Icon>
                          </el-icon>
                          <span>
                            {{ ele.name }}
                          </span>
                        </div>
                      </el-option>
                    </el-select>
                  </div>
                  <div style="display: flex; align-items: center" class="value">
                    <span :title="t('v_query.display_field')" class="label ellipsis">{{
                      t('v_query.display_field')
                    }}</span>
                    <el-select
                      :placeholder="t('v_query.display_field')"
                      class="search-field"
                      v-model="curComponent.displayId"
                      @change="resetSort"
                    >
                      <template v-if="curComponent.displayId" #prefix>
                        <el-icon>
                          <Icon
                            ><component
                              class="svg-icon"
                              :class="`field-icon-${
                                fieldType[
                                  getDetype(curComponent.displayId, curComponent.dataset.fields)
                                ]
                              }`"
                              :is="
                                iconFieldMap[
                                  fieldType[
                                    getDetype(curComponent.displayId, curComponent.dataset.fields)
                                  ]
                                ]
                              "
                            ></component
                          ></Icon>
                        </el-icon>
                      </template>
                      <el-option
                        v-for="ele in fieldsComputed"
                        :key="ele.id"
                        :label="ele.name"
                        :value="ele.id"
                        :disabled="ele.desensitized"
                      >
                        <div
                          class="flex-align-center icon"
                          :title="ele.desensitized ? t('v_query.as_query_conditions') : ''"
                        >
                          <el-icon>
                            <Icon :className="`field-icon-${fieldType[ele.fieldType]}`"
                              ><component
                                class="svg-icon"
                                :class="`field-icon-${fieldType[ele.fieldType]}`"
                                :is="iconFieldMap[fieldType[ele.fieldType]]"
                              ></component
                            ></Icon>
                          </el-icon>
                          <span>
                            {{ ele.name }}
                          </span>
                        </div>
                      </el-option>
                    </el-select>
                  </div>
                  <div class="value">
                    <span class="label">{{ t('chart.total_sort_field') }}</span>
                    <div>
                      <el-select
                        clearable
                        :placeholder="t('v_query.the_sorting_field')"
                        v-model="curComponent.sortId"
                        class="sort-field"
                        style="width: 240px"
                        @change="handleSortChange"
                      >
                        <template v-if="curComponent.sortId" #prefix>
                          <el-icon>
                            <Icon
                              ><component
                                class="svg-icon"
                                :class="`field-icon-${
                                  fieldType[
                                    getDetype(curComponent.sortId, curComponent.dataset.fields)
                                  ]
                                }`"
                                :is="
                                  iconFieldMap[
                                    fieldType[
                                      getDetype(curComponent.sortId, curComponent.dataset.fields)
                                    ]
                                  ]
                                "
                              ></component
                            ></Icon>
                          </el-icon>
                        </template>
                        <el-option
                          v-for="ele in curComponent.dataset.fields"
                          :key="ele.id"
                          :label="ele.name"
                          :value="ele.id"
                          :disabled="ele.desensitized"
                        >
                          <div
                            class="flex-align-center icon"
                            :title="ele.desensitized ? t('v_query.as_query_conditions') : ''"
                          >
                            <el-icon>
                              <Icon
                                ><component
                                  :class="`field-icon-${fieldType[ele.fieldType]}`"
                                  class="svg-icon"
                                  :is="iconFieldMap[fieldType[ele.fieldType]]"
                                ></component
                              ></Icon>
                            </el-icon>
                            <span>
                              {{ ele.name }}
                            </span>
                          </div>
                        </el-option>
                      </el-select>
                      <el-select
                        class="sort-type"
                        v-model="curComponent.sort"
                        @change="handleFieldChange"
                      >
                        <el-option :label="t('chart.asc')" value="asc" />
                        <el-option :label="t('chart.desc')" value="desc" />
                        <el-option
                          @click="handleCustomClick"
                          :title="sortComputed ? $t('v_query.display_sort') : ''"
                          :disabled="sortComputed"
                          :label="t('v_query.custom_sort')"
                          value="customSort"
                        />
                      </el-select>
                    </div>
                  </div>
                </template>
                <div v-if="curComponent.optionValueSource === 2" class="value flex-align-center">
                  <!-- 手工输入模式先在弹层内编辑临时数组，确认后再写回当前条件。 -->
                  <el-popover
                    placement="bottom-start"
                    popper-class="manual-input"
                    ref="manual"
                    :width="358"
                    trigger="click"
                  >
                    <template #reference>
                      <el-button text>
                        <template #icon>
                          <Icon name="icon_edit_outlined"
                            ><icon_edit_outlined class="svg-icon"
                          /></Icon>
                        </template>
                        {{ t('common.edit') }}
                      </el-button>
                    </template>
                    <div class="manual-input-container">
                      <el-scrollbar>
                        <div class="title">{{ t('auth.manual_input') }}</div>
                        <div class="select-value">
                          <span> {{ t('data_fill.form.option_value') }} </span>
                          <div :key="index" v-for="(_, index) in valueSource" class="select-item">
                            <el-input
                              maxlength="64"
                              v-if="curComponent.displayType === '2'"
                              @blur="weightlessness"
                              v-model.number="valueSource[index]"
                            ></el-input>
                            <el-input
                              maxlength="64"
                              v-else
                              @blur="weightlessness"
                              v-model="valueSource[index]"
                            ></el-input>
                            <el-button
                              v-if="valueSource.length !== 1"
                              @click="valueSource.splice(index, 1)"
                              class="value"
                              text
                            >
                              <template #icon>
                                <Icon name="icon_delete-trash_outlined"
                                  ><icon_deleteTrash_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </div>
                        </div>
                      </el-scrollbar>
                      <div class="add-btn">
                        <el-button @click="valueSource.push('')" text>
                          <template #icon>
                            <Icon name="icon_add_outlined"
                              ><icon_add_outlined class="svg-icon"
                            /></Icon>
                          </template>
                          {{ t('data_fill.form.add_option') }}
                        </el-button>
                      </div>
                      <div class="manual-footer flex-align-center">
                        <el-button @click="cancelValueSource">{{ t('chart.cancel') }} </el-button>
                        <el-button @click="confirmValueSource" type="primary"
                          >{{ t('chart.confirm') }}
                        </el-button>
                      </div>
                    </div>
                  </el-popover>
                  <div
                    v-if="!!curComponent.valueSource.length"
                    class="config-flag flex-align-center"
                  >
                    {{ t('v_query.configured') }}
                  </div>
                </div>
              </div>
              <template v-if="['0', '2', '5'].includes(curComponent.displayType)">
                <div
                  class="label ellipsis"
                  :title="t('common.display_formats')"
                  style="margin-top: 10.5px"
                >
                  {{ t('common.display_formats') }}
                </div>
                <div class="value" style="margin-top: 10.5px">
                  <el-radio-group
                    class="larger-radio icon-info"
                    v-model="curComponent.displayFormat"
                  >
                    <el-radio :label="0">{{ t('common.dropdown_display') }} </el-radio>
                    <el-radio :label="1">{{ t('common.tile_display') }}</el-radio>
                  </el-radio-group>
                </div>
              </template>
              <div
                class="label ellipsis"
                :title="t('v_query.of_option_values')"
                style="margin-top: 10.5px"
              >
                {{ t('v_query.of_option_values') }}
              </div>
              <div class="value" style="margin-top: 10.5px">
                <el-radio-group class="larger-radio icon-info" v-model="curComponent.resultMode">
                  <el-radio :label="0"
                    >{{ t('chart.default') }}
                    <el-tooltip effect="dark" :content="t('common.up_to_options')" placement="top">
                      <el-icon style="margin-left: 4px; color: #646a73">
                        <icon name="icon_info_outlined"
                          ><icon_info_outlined class="svg-icon"
                        /></icon>
                      </el-icon> </el-tooltip
                  ></el-radio>
                  <el-radio :label="1">{{ t('data_set.all') }}</el-radio>
                </el-radio-group>
              </div>
            </div>
            <div class="list-item top-item" v-if="curComponent.displayType === '8'">
              <div :title="t('v_query.condition_type')" class="label ellipsis">
                {{ t('v_query.condition_type') }}
              </div>
              <div class="value">
                <div class="value">
                  <el-radio-group class="larger-radio" v-model="curComponent.conditionType">
                    <el-radio :label="0">{{ t('v_query.single_condition') }}</el-radio>
                    <el-radio :label="1" :disabled="!!curComponent.parameters.length">{{
                      t('v_query.with_condition')
                    }}</el-radio>
                    <el-radio :label="2" :disabled="!!curComponent.parameters.length">{{
                      t('v_query.or_condition')
                    }}</el-radio>
                  </el-radio-group>
                </div>
              </div>
            </div>
            <div style="margin-bottom: 10.5px" v-if="curComponent.displayType === '8'">
              <el-checkbox
                v-model="curComponent.hideConditionSwitching"
                :label="t('v_query.hide_condition_switch')"
              />
            </div>
            <condition-default-configuration
              ref="defaultConfigurationRef"
              @handleTimeTypeChange="handleTimeTypeChange"
              :cur-component="curComponent"
            ></condition-default-configuration>
          </div>
          <div v-if="showTypeError && showConfiguration" class="empty">
            <empty-background :description="t('v_query.cannot_be_performed')" img-type="error" />
          </div>
          <div
            v-else-if="curComponent.displayType === '9' && relationshipChartIndex !== 0"
            class="empty"
          >
            <empty-background :description="t('common.other_levels')" img-type="error" />
          </div>
          <div v-else-if="!showConfiguration" class="empty">
            <empty-background :description="t('v_query.be_linked_first')" img-type="noneWhite" />
          </div>
        </el-scrollbar>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <!-- 级联配置属于跨条件高级行为，放在底部入口避免干扰单条件编辑流程。 -->
        <el-button class="query-cascade" @click="openCascadeDialog">{{
          t('v_query.component_cascade_configuration')
        }}</el-button>
        <el-button @click="cancelClick">{{ t('chart.cancel') }} </el-button>
        <el-button @click="confirmClick" type="primary">{{ t('chart.confirm') }} </el-button>
      </div>
    </template>
  </el-dialog>
  <el-dialog :title="timeName" v-model="timeDialogShow" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('v_query.time_type')" class="form-item" prop="name">
        <el-radio-group v-model="timeParameterType">
          <el-radio :label="0">{{ t('data_set.time') }}</el-radio>
          <el-radio :label="1">{{ t('datasource.start_time') }}</el-radio>
          <el-radio :label="2">{{ t('datasource.end_time') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="timeDialogShow = false">{{ t('chart.cancel') }}</el-button>
      <el-button type="primary" @click="timeTypeChange">{{ t('chart.confirm') }}</el-button>
    </template>
  </el-dialog>
  <el-dialog :title="numName" v-model="numDialogShow" width="420px">
    <el-form label-position="top">
      <el-form-item :label="t('chart.map_line_type')" class="form-item" prop="name">
        <el-radio-group v-model="numParameterType">
          <el-radio :label="0">{{ t('chart.value_formatter_value') }}</el-radio>
          <el-radio :label="1">{{ t('chart.min') }}</el-radio>
          <el-radio :label="2">{{ t('chart.max') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="numDialogShow = false">{{ t('dataset.cancel') }}</el-button>
      <el-button type="primary" @click="numTypeChange">{{ t('dataset.confirm') }}</el-button>
    </template>
  </el-dialog>
  <customSortFilter ref="customSortFilterRef" @save="sortSave"></customSortFilter>
  <CascadeDialog @saveCascade="saveCascade" ref="cascadeDialog"></CascadeDialog>
</template>

<style lang="less">
.range-time_setting {
  height: 20px;
  padding: 1px 4px 1px 4px;
  border-radius: 2px;
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 400;
  line-height: 20px;
  background: #1f23291a;
  color: #1f2329;
  margin-left: 4px;

  .ed-icon {
    font-size: 12px;
    margin-right: 0 !important;
    margin-left: 4px;
  }
}
.field-select--dqp {
  min-width: 210px !important;
}
.ed-select-dropdown__header {
  padding: 0 8px !important;
  .params-select--header {
    --ed-tabs-header-height: 32px;
    .ed-tabs__item {
      font-weight: 400;
      font-size: 15px;
    }
  }
}
.condition-value-select-popper {
  .ed-select-dropdown__item.selected::after {
    display: none;
  }
}
.dataset-parameters {
  font-family: var(--crest-custom_font, 'PingFang');
  font-style: normal;
  font-weight: 400;
  .ed-select-dropdown__item {
    height: 50px;
    line-height: 50px;
    padding-top: 4px;
    &.selected::after {
      top: 30% !important;
    }
  }
  .variable-name {
    font-size: 14px;
    line-height: 22px;
  }
  .dataset-full-name {
    color: #8d9199;
    font-size: 12px;
    line-height: 20px;
  }
}
.query-condition-configuration {
  --ed-font-weight-primary: 400;

  .query-cascade {
    position: absolute;
    left: 24px;
    bottom: 24px;
  }

  .ed-dialog__headerbtn {
    top: 21px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .ed-select__prefix {
    font-size: 16px;
    &::after {
      display: none;
    }
  }
  .container {
    font-size: 14px;
    font-family: var(--crest-custom_font, 'PingFang');
    width: 1152px;
    height: 454px;
    border-radius: 6px;
    border: 1px solid #dee0e3;
    display: flex;
    .ed-checkbox:not(.is-disabled) {
      .ed-checkbox__label:hover {
        color: #1f2329;
      }
    }
    .query-condition-list {
      height: 100%;
      background: #f5f6f7;
      border-right: 1px solid #dee0e3;
      width: 208px;
      overflow-y: auto;

      .title {
        padding: 16px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        font-style: normal;
        font-weight: 500;
        line-height: 22px;

        .ed-icon {
          cursor: pointer;
          font-size: 16px;
          color: var(--ed-color-primary);
        }
      }

      .list-item_box {
        width: 100%;
        position: relative;
        .list-tree_primary {
          position: absolute;
          left: 0;
          padding: 8px 32px;
          width: 100%;
        }
      }
      .list-item_primary {
        border-radius: 0;
        position: relative;
        .label {
          width: 75%;
        }

        .rename {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
          padding: 4px 10px;
          z-index: 5;
        }
      }
    }

    .mask {
      position: absolute;
      top: 30px;
      left: 0;
      width: 100%;
      z-index: 5;
      background: rgba(255, 255, 255, 0.6);
      height: calc(100% - 30px);

      &.condition {
        height: calc(100% - 45px);
        top: 45px;
      }
    }

    .chart-field {
      height: calc(100% - 16px);
      padding: 0 16px 16px 16px;
      width: 474px;
      position: relative;
      overflow-y: auto;
      margin-top: 16px;

      .flex-align-center {
        position: sticky;
        top: 0;
        justify-content: space-between;
        background: #fff;
        z-index: 5;
        .ed-radio {
          height: 20px;
        }
      }

      .title {
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        font-style: normal;
        font-weight: 500;
        line-height: 22px;
        margin-bottom: 8px;
      }

      .select-all {
        height: 40px;
      }

      .field-list {
        .component-type {
          margin-right: 4px;
          font-size: 20px;
          color: var(--ed-color-primary);
        }
        .list-item-field {
          height: 32px;
          display: flex;
          align-items: center;
          margin-bottom: 8px;

          .field-select--input {
            .ed-select__prefix {
              padding-right: 0;
            }

            .ed-select__input {
              margin-left: 6px !important;
            }
            .ed-tag {
              max-width: 46px !important;
              .ed-tag__close {
                margin-left: 2px;
              }
              .ed-select__tags-text {
                max-width: 30px !important;
              }
            }
          }

          .ed-checkbox__label {
            display: inline-flex;
            align-items: center;
            .checkbox-name {
              width: 110px;
            }
          }

          .dataset {
            color: #646a73;
            font-size: 14px;
            height: 22px;
            width: 90px;
            line-height: 22px;
            margin-left: 8px;
          }

          .ed-select {
            width: 172px;
          }
        }
      }
    }
    .hidden {
      overflow-y: hidden;
    }

    .condition-configuration {
      border-left: 1px solid #dee0e3;
      width: 467px;
      position: relative;
      overflow: hidden;

      .ed-scrollbar {
        padding: 16px;
      }

      &.condition-configuration_hide {
        overflow: hidden;
      }
      .mask {
        left: -1px;
        width: calc(100% + 2px);
      }

      .config-flag {
        color: #646a73;
        height: 16px;
        padding: 0px 4px;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 10px;
        font-style: normal;
        font-weight: 500;
        line-height: 13px;
        border-radius: 2px;
        background: rgba(31, 35, 41, 0.1);
        margin-left: 8px;

        &.range-filter-time-flag {
          display: inline-block;
          padding: 1px 4px;
          line-height: 14px;
          margin-left: 4px;
        }
      }

      .flex-align-center {
        position: sticky;
        top: 0;
        justify-content: space-between;
        background: #fff;
        z-index: 5;
        .ed-checkbox {
          height: 20px;
        }
      }
      .title {
        margin-bottom: 16px;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        font-style: normal;
        font-weight: 500;
        line-height: 22px;
        position: relative;

        &.flex-align-center::after {
          content: '';
          position: absolute;
          width: 100%;
          height: 16px;
          background: #fff;
          top: -16px;
          left: 0;
        }
      }

      .configuration-list {
        .list-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 10.5px;
          flex-wrap: wrap;
          .search-tree {
            width: 100%;
            height: 216px;
            margin-top: 8px;
            position: relative;
            padding: 16px;
            box-shadow: 0px 0px 12px rgba(0, 0, 0, 0.12);

            .start-tree_design {
              position: absolute;
              left: 50%;
              top: 50%;
              transform: translate(-50%, -50%);
            }

            .tree-field {
              display: flex;
              align-items: center;
              margin-bottom: 16px;
              .level-index {
                margin-right: 40px;
              }

              .field-type {
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 16px;
              }

              .field-tree_name {
                margin-left: 8px;
                width: 100px;
              }

              .field-relationship_chart {
                margin-left: 8px;
              }
            }
          }

          .setting-content {
            width: 100%;
            padding-left: 24px;
          }

          &.top-item {
            .label {
              margin-bottom: auto;
              padding-top: 5.5px;
            }
          }
          .label {
            width: 85px;
            color: #1f2329;
          }

          .value {
            .ed-select {
              width: 321px;
            }
            width: 321px;
            .value {
              margin-top: 8px;
              &:first-child {
                margin-top: -0.5px;
              }
              .search-field {
                width: 257px;
              }

              .sort-field {
                width: 176px;
              }

              .label {
                line-height: 32px;
                font-size: 14px;
                margin-right: 8px;
              }
            }
          }

          .value {
            width: 321px;
            .condition-type {
              margin-top: 3px !important;
              display: flex;
              position: relative;
              .ed-input__wrapper {
                border: none;
                border-radius: 0;
                box-shadow: none;
                height: 26px;
                font-family: var(--crest-custom_font, 'PingFang');
                word-wrap: break-word;
                text-align: left;
                color: rgba(0, 0, 0, 0.65);
                list-style: none;
                user-select: none;
                cursor: pointer;
                line-height: 26px;
                box-sizing: border-box;
                max-width: 100%;
                overflow: hidden;
                white-space: nowrap;
                text-overflow: ellipsis;
                opacity: 1;
              }

              .ed-select .ed-input.is-focus .ed-input__wrapper,
              .ed-select:hover:not(.ed-select--disabled) .ed-input__wrapper,
              .ed-select .ed-input__wrapper.is-focus {
                box-shadow: none !important;
              }

              .ed-select {
                width: 120px;
                .ed-input__wrapper {
                  padding: 0;
                }
              }

              .condition-type-tip {
                font-size: 12px;
                color: #646a73;
                line-height: 26px;
                margin-right: 8px;
              }

              .bottom-line {
                box-sizing: border-box;
                height: 1px;
                background-color: #000;
                opacity: 0.3;
                position: absolute;
                right: 5px;
                bottom: 3px;
                width: 220px;
                z-index: 10;

                &.next-line {
                  width: 206px;
                }
              }
              &:first-child {
                margin-top: -0.5px;
              }
            }
          }
          .value {
            .sort-field {
              width: 240px;
            }
            .sort-type {
              width: 73px;
              margin-left: 8px;
            }
          }
          .parameters {
            margin-left: auto;
            margin-top: 8px;

            .w100 {
              width: 100%;
            }
            .ed-select,
            .ed-date-editor,
            .ed-date-editor--datetime .ed-input__wrapper,
            .ed-select-v2 {
              width: 415px;
            }

            .ed-date-editor {
              .ed-input__wrapper {
                width: 100%;
              }
            }
          }
          .parameters-range {
            width: 100%;
            padding-left: 24px;
            display: flex;
            flex-wrap: wrap;

            .range-title,
            .params-start,
            .params-end {
              width: 50%;
            }

            .params-start,
            .params-end {
              margin-top: 8px;
              .ed-select {
                width: 100%;
              }
            }

            .params-end {
              padding-left: 4px;
            }

            .params-start {
              padding-right: 4px;
            }
          }

          .setting {
            &.setting {
              margin-top: 8px;
            }
            &.parameters {
              width: 100%;
              padding-left: 24px;
              .ed-date-editor {
                width: 325px !important;
              }
            }
            margin-left: auto;
            display: flex;
            justify-content: space-between;
            align-items: center;
            .setting-label {
              width: 80px;
              margin-right: 8px;
            }

            .setting-value {
              margin: 8px 0;
              &.select {
                margin-top: 0;
                .ed-select {
                  width: 325px;
                }
              }
            }

            .setting-input {
              display: flex;
              padding-left: 86px;
              justify-content: flex-end;
              align-items: center;
              &.range {
                padding-left: 0px;
              }
              & > div + div {
                margin-left: 8px;
              }

              &.with-date {
                .ed-input-number {
                  width: 71px;
                }
                .ed-select {
                  width: 62px;
                }

                .ed-date-editor.ed-input {
                  width: 106px;
                }
              }
            }

            &.is-year-month-range {
              .setting-input {
                &.with-date {
                  .ed-input-number,
                  .ed-select {
                    width: 103px;
                  }
                }
                .ed-date-editor.ed-input {
                  display: none;
                }
              }
            }
          }
        }
      }
    }
  }
}
.manual-input {
  height: 405px;
  padding: 0 !important;

  .manual-input-container {
    .title {
      padding: 16px;
    }

    .add-btn {
      padding: 8px 16px;
    }
    .select-value {
      padding-left: 16px;
      max-height: 246px;
      .value {
        color: #646a73;
        margin-left: 6px;
        font-size: 20px;
      }

      .select-item {
        margin: 8px 0;
        &:last-child {
          margin-bottom: 0;
        }
        .ed-input {
          width: 298px;
        }
      }
    }
    .manual-footer {
      position: absolute;
      bottom: 0;
      padding: 16px;
      height: 63px;
      width: 100%;
      border-top: 1px solid rgba(31, 35, 41, 0.15);
      justify-content: flex-end;
    }
  }
}
.dataset-tree {
  .content {
    display: flex;
    align-items: center;
    width: 100%;
    .label-tree {
      margin-left: 5px;
      width: calc(100% - 45px);
    }
  }
  max-width: 321px;
  .ed-select-dropdown__item.selected {
    font-weight: 400;
  }
}
.larger-radio {
  &.icon-info {
    .ed-radio__label {
      display: flex;
      align-items: center;
    }
  }
  .ed-radio__inner {
    width: 16px;
    height: 16px;
  }
}
</style>
