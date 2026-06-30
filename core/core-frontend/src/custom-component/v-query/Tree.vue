<script lang="ts" setup>
import {
  ref,
  PropType,
  toRefs,
  nextTick,
  watch,
  onMounted,
  computed,
  inject,
  Ref,
  onBeforeMount,
  shallowRef
} from 'vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import { cloneDeep, debounce, sortBy } from 'lodash-es'
import { fieldTree } from '@/api/dataset'
import router from '@/router'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import colorFunctions from 'less/lib/less/functions/color.js'
import colorTree from 'less/lib/less/tree/color.js'
import { colorStringToHex } from '@/utils/color'
import { ElMessage } from 'element-plus-secondary'
import { useI18n } from '@/hooks/web/useI18n'
/** 国际化翻译函数，提供树筛选组件提示文案 */
const { t } = useI18n()

/** 树形查询组件配置 */
interface SelectConfig {
  name?: string
  selectValue: any
  required: false
  defaultMapValue: any
  defaultValue: any
  queryConditionWidth: number
  resultMode: number
  checkedFieldsMap: object
  displayType: string
  id: string
  placeholder: string
  checkedFields: string[]
  treeFieldList: Array<any>
  dataset: {
    id: string
  }
  field: {
    id: string
  }
  defaultValueCheck: boolean
  multiple: boolean
  optionFilter: []
}

/** 外部注入的筛选器自定义样式 */
const customStyle: any = inject('$custom-style-filter')
/** 外部注入的级联配置列表 */
const cascadeList = inject('cascade-list', Function, true)
/** 树形查询组件入参，区分配置态和运行态 */
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        selectValue: '',
        defaultValue: '',
        required: false,
        queryConditionWidth: 0,
        displayType: '',
        resultMode: 0,
        defaultValueCheck: false,
        multiple: false,
        checkedFieldsMap: {},
        treeFieldList: [],
        optionFilter: []
      }
    }
  },
  isConfig: {
    type: Boolean,
    default: false
  }
})

/** 外部注入的占位符控制配置 */
const placeholder: Ref = inject('placeholder')
/** 根据占位符配置计算展示文案 */
const placeholderText = computed(() => {
  if (placeholder?.value?.placeholderShow) {
    return ['', undefined].includes(props.config.placeholder) ? ' ' : props.config.placeholder
  }
  return ' '
})
/** 当前树查询配置引用 */
const { config } = toRefs(props)
/** 主画布仓库用于读取当前可视化资源编号 */
const dvMainStore = dvMainStoreWithOut()
/** 标记当前值变更是否来自树选择确认 */
const fromTreeSelectConfirm = ref(false)
/** 当前树选择器是否为多选模式 */
const multiple = ref(false)
/** 树选择确认后同步值并触发查询 */
const treeSelectConfirm = val => {
  treeValue.value = val
  handleValueChange()
}

/** 将树选择值同步到运行态 selectValue 或配置态 defaultValue */
const handleValueChange = () => {
  fromTreeSelectConfirm.value = true
  const value = Array.isArray(treeValue.value) ? [...treeValue.value] : treeValue.value
  if (!props.isConfig) {
    config.value.selectValue = Array.isArray(treeValue.value)
      ? [...treeValue.value]
      : treeValue.value
    nextTick(() => {
      fromTreeSelectConfirm.value = false
      isConfirmSearch(config.value.id)
    })
    return
  }
  config.value.defaultValue = value
  fromTreeSelectConfirm.value = false
}

/** 标记当前变更是否由查询组件编号切换触发 */
const changeFromId = ref(false)
/** 查询组件编号变化时重新初始化树选项 */
watch(
  () => config.value.id,
  () => {
    changeFromId.value = true
    init()
    nextTick(() => {
      changeFromId.value = false
    })
  }
)
/** 上一次树字段组合编号，用于避免重复刷新 */
let oldId
/** 树字段列表变化时清空旧值并重新加载选项 */
watch(
  () => config.value.treeFieldList,
  val => {
    let idStr = val.map(ele => ele.id).join('-')
    if (changeFromId.value || idStr === oldId) return
    oldId = idStr
    treeValue.value = config.value.multiple ? [] : undefined
    config.value.defaultValue = config.value.multiple ? [] : undefined
    config.value.selectValue = config.value.multiple ? [] : undefined
    showOrHide.value = false
    getTreeOption()
  }
)

/** 初始化选中值、多选状态和树选项 */
const init = (fromMount = false) => {
  loading.value = true
  const { defaultValueCheck, multiple: plus, defaultValue } = config.value
  if (defaultValueCheck) {
    config.value.selectValue = Array.isArray(defaultValue)
      ? cloneDeep([...defaultValue])
      : defaultValue
    treeValue.value = Array.isArray(defaultValue) ? cloneDeep([...defaultValue]) : defaultValue
  } else {
    config.value.selectValue = plus ? [] : undefined
    treeValue.value = plus ? [] : undefined
  }
  nextTick(() => {
    oldId = config.value.treeFieldList?.map(ele => ele.id).join('-')
    multiple.value = config.value.multiple
  })
  if (getCascadeFieldId().some(ele => ele.defaultValueFirstItem) && fromMount && !props.isConfig)
    return
  getTreeOption()
}

/** 单个标签最大宽度 */
const tagWidth = computed(() => {
  return Math.min(getCustomWidth() / 3, 50) + 'px'
})

/** 多选标签容器宽度 */
const tagsWidth = computed(() => {
  return getCustomWidth() - 40 + 'px'
})

/** 标签文本宽度 */
const tagTextWidth = computed(() => {
  return Math.min(getCustomWidth() / 3, 50) - 25 + 'px'
})

/** 控制树选择器在字段变化后重建 */
const showOrHide = ref(true)
/** 外部注入的查询条件宽度计算函数 */
const queryConditionWidth = inject('com-width', Function, true)
/** 外部注入的确认查询函数 */
const isConfirmSearch = inject('is-confirm-search', Function, true)
/** 外部注入的无必填名确认查询函数 */
const isConfirmSearchNoRequiredName = inject('query-data-for-id-tree', Function, true)
/** 查询组件编号变化时刷新树选项 */
watch(
  () => config.value.id,
  () => {
    getTreeOption()
  }
)
/** 挂载后初始化树选项，延迟到注入上下文稳定后执行 */
onMounted(() => {
  setTimeout(() => {
    fromSelect = true
    init(true)
  }, 0)
})

/** 运行态默认值变化时同步到树选择器 */
watch(
  () => config.value.defaultValue,
  val => {
    if (props.isConfig) return
    if (config.value.multiple) {
      treeValue.value = Array.isArray(val) ? [...val] : val
    }
    nextTick(() => {
      multiple.value = config.value.multiple
    })
  }
)

/** 运行态选中值变化时同步到树选择器 */
watch(
  () => config.value.selectValue,
  val => {
    if (props.isConfig || fromTreeSelectConfirm.value) return

    if (config.value.multiple) {
      treeValue.value = Array.isArray(val) ? [...val] : val
    }

    nextTick(() => {
      multiple.value = config.value.multiple
      if (!config.value.multiple) {
        treeValue.value = Array.isArray(config.value.selectValue)
          ? [...config.value.selectValue]
          : config.value.selectValue
      }
    })
  }
)

/** 控制多选模式切换时是否展示完整路径 */
const showWholePath = ref(false)
/** 配置态多选模式变化时重置树值并恢复路径展示 */
watch(
  () => config.value.multiple,
  val => {
    if (!props.isConfig || changeFromId.value) return
    showWholePath.value = false
    if (val) {
      treeValue.value = []
    }
    nextTick(() => {
      multiple.value = val
      if (!val) {
        nextTick(() => {
          treeValue.value = undefined
        })
      }
      nextTick(() => {
        showWholePath.value = true
      })
    })
  }
)
/** 树字段缓存编号，避免配置态重复请求 */
let cacheId = ''
/** 树选择器选项列表 */
let treeOptionList = shallowRef([])
/** 树节点过滤方法 */
const filterMethod = (value, data) =>
  (data.label ?? '').toLowerCase().includes((value ?? '').toLowerCase())

/** 将接口返回的字段树转换为树选择器节点并按标签排序 */
const dfs = arr => {
  const mapped = (arr || []).map(ele => {
    const label = ele.text
    const children = ele.children?.length ? dfs(ele.children) : []
    return { ...ele, value: ele.id, label, children }
  })
  return sortBy(mapped, node => (node.label ?? '').toLowerCase())
}
/** 当前级联配置列表 */
const cascade = computed(() => {
  return cascadeList() || []
})
/** 树选项加载状态 */
const loading = ref(false)
/** 当前可视化资源编号，优先取画布仓库，其次取路由参数 */
const currentVisualizationId = () => dvMainStore.dvInfo?.id || router.currentRoute.value.query.dvId

/** 根据级联配置计算当前树字段过滤条件 */
const getCascadeFieldId = () => {
  const filter = []
  cascade.value.forEach(ele => {
    let condition = null
    ele.forEach(item => {
      const [_, queryId, fieldId] = item.datasetId.split('--')
      const defaultValueFirstItem = item.defaultValueFirstItem
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
              fieldId,
              defaultValueFirstItem,
              operator: 'in',
              value
            }
          }
        } else {
          const value = normalizeCascadeValue(item.currentSelectValue)
          if (value.length) {
            condition = {
              fieldId,
              defaultValueFirstItem,
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

/** 将级联值规范化为非空数组 */
const normalizeCascadeValue = (value: any) => {
  const values = Array.isArray(value) ? value : [value]
  return values.filter(ele => ele !== undefined && ele !== null && ele !== '')
}

/** 标记当前选项刷新是否由级联选择触发 */
let fromSelect = false
/** 级联选择变化后重新拉取树选项 */
const getOptionFromCascade = () => {
  fromSelect = true
  getTreeOption()
}

/** 挂载前注册级联选择事件监听 */
onBeforeMount(() => {
  useEmitt({
    name: `${config.value.id}-select`,
    callback: getOptionFromCascade
  })
})

/** 校验指定值是否存在于当前树节点中 */
const dfsAuth = (tree, val) => {
  return tree.some(ele => {
    if (ele.value === val) {
      return true
    }

    if (ele.children?.length) {
      return dfsAuth(ele.children, val)
    }

    return false
  })
}

/** 判断树数据中是否包含指定节点编号 */
function containsNodeById(source, params) {
  // 统一处理为数组参数
  const searchIds = Array.isArray(params) ? params : [params]

  // 递归搜索节点编号
  function searchById(node) {
    // 检查当前节点编号是否在搜索列表中
    if (searchIds.includes(node.id)) {
      return true
    }

    // 递归搜索子节点
    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
        if (searchById(child)) {
          return true
        }
      }
    }

    return false
  }

  // 遍历所有根节点
  for (const node of source) {
    if (searchById(node)) {
      return true
    }
  }

  return false
}

/** 拉取树字段选项，并根据级联、权限和过滤配置修正当前选中值 */
const getTreeOption = debounce(() => {
  loading.value = true
  fieldTree({
    fieldIds: props.config.treeFieldList.map(ele => ele.id),
    resultMode: config.value.resultMode || 0,
    filter: getCascadeFieldId(),
    visualizationId: currentVisualizationId()
  })
    .then(res => {
      treeOptionList.value = filterTree(dfs(res), config.value.optionFilter)
      if (config.value?.required && config.value?.optionFilter?.length > 0) {
        const isValid = containsNodeById(treeOptionList.value, config.value.selectValue)
        if (!isValid) {
          config.value.selectValue = null
          ElMessage({
            message: `【${config.value?.name}】${t('v_query.before_querying')}`,
            type: 'error',
            duration: 3000
          })
        }
      }

      if (fromSelect) {
        fromTreeSelectConfirm.value = true
        if (multiple.value && Array.isArray(treeValue.value) && treeValue.value.length) {
          treeValue.value = treeValue.value.filter(ele => dfsAuth(treeOptionList.value, ele))
        } else if (treeValue.value && !dfsAuth(treeOptionList.value, treeValue.value)) {
          treeValue.value = undefined
        } else {
          fromSelect = false
          fromTreeSelectConfirm.value = false
        }

        if (fromSelect) {
          config.value.selectValue = Array.isArray(treeValue.value)
            ? [...treeValue.value]
            : treeValue.value
          config.value.defaultValue = config.value.selectValue

          if (props.isConfig) return

          nextTick(() => {
            fromTreeSelectConfirm.value = false
            isConfirmSearchNoRequiredName(config.value.id)
          })
        }
      }
    })
    .finally(() => {
      loading.value = false
      showWholePath.value = true
      fromSelect = false
    })
}, 300)
/** 配置态树字段变化时刷新树选项 */
watch(
  () => props.config.treeFieldList,
  val => {
    if (!props.isConfig) return
    const ids = val.map(ele => ele.id).join('')
    if (cacheId !== val.map(ele => ele.id).join('')) {
      cacheId = ids
      getTreeOption()
    }
  }
)
/** 单选占位值 */
const fakeValue = ref('')
/** 树选择器当前值 */
const treeValue = ref()
/** 计算树选择器宽度 */
const getCustomWidth = () => {
  if (placeholder?.value?.placeholderShow) {
    if (props.config.queryConditionWidth !== undefined) {
      return props.config.queryConditionWidth
    }
    return queryConditionWidth()
  }
  return 227
}
/** 树选择器外层样式 */
const selectStyle = computed(() => {
  return props.isConfig ? {} : { width: getCustomWidth() + 'px' }
})

/** 根据筛选器背景色计算标签背景色 */
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

/** 按配置的节点编号过滤树，并保留匹配节点的祖先和后代 */
function filterTree(treeData, filterIds) {
  if (!filterIds || filterIds.length === 0) {
    return treeData
  }
  const filterSet = new Set(filterIds)

  // 用于存储最终保留的所有节点ID
  const keepIds = new Set()

  // 用于查找节点的Map
  const nodeMap = new Map()
  // 用于构建节点关系的Map（子节点到父节点）
  const parentMap = new Map()

  // 遍历所有节点，构建节点索引和父子关系
  function traverse(nodes, parentId = null) {
    for (const node of nodes) {
      nodeMap.set(node.id, node)
      if (parentId) {
        parentMap.set(node.id, parentId)
      }

      // 递归处理子节点
      if (node.children && node.children.length > 0) {
        traverse(node.children, node.id)
      }
    }
  }

  // 收集所有匹配节点及其祖先和后代
  function collectRelatedNodes(nodeId) {
    if (keepIds.has(nodeId)) return

    keepIds.add(nodeId)
    const node = nodeMap.get(nodeId)

    // 1. 收集所有祖先节点
    let currentId = nodeId
    while (parentMap.has(currentId)) {
      const parentId = parentMap.get(currentId)
      keepIds.add(parentId)
      currentId = parentId
    }

    // 2. 收集所有后代节点（递归）
    function collectDescendants(node) {
      if (node.children && node.children.length > 0) {
        for (const child of node.children) {
          keepIds.add(child.id)
          collectDescendants(child)
        }
      }
    }
    collectDescendants(node)
  }

  // 递归构建过滤后的树
  function buildFilteredTree(nodes) {
    const result = []

    for (const node of nodes) {
      // 如果节点ID在保留集合中，则保留该节点
      if (keepIds.has(node.id)) {
        const newNode = { ...node }

        // 递归处理子节点
        if (newNode.children && newNode.children.length > 0) {
          newNode.children = buildFilteredTree(newNode.children)
        }

        result.push(newNode)
      }
    }

    return result
  }

  // 执行遍历和构建
  traverse(treeData)

  for (const filterId of filterIds) {
    if (nodeMap.has(filterId)) {
      collectRelatedNodes(filterId)
    }
  }

  return buildFilteredTree(treeData)
}
</script>

<template>
  <el-tree-select
    v-model="treeValue"
    :data="treeOptionList"
    clearable
    v-if="multiple && !loading"
    @treeSelectConfirm="treeSelectConfirm"
    :render-after-expand="false"
    show-checkbox
    showBtn
    @change="handleValueChange"
    :placeholder="placeholderText"
    collapse-tags
    :filter-node-method="filterMethod"
    :showWholePath="showWholePath"
    collapse-tags-tooltip
    :tagColor="tagColor"
    :key="'multipleTree' + getCustomWidth()"
    filterable
    :style="selectStyle"
    multiple
  />
  <el-tree-select
    v-model="treeValue"
    @change="handleValueChange"
    :data="treeOptionList"
    check-strictly
    clearable
    :filter-node-method="filterMethod"
    :placeholder="placeholderText"
    :render-after-expand="false"
    v-else-if="!multiple && !loading"
    :key="'singleTree' + getCustomWidth()"
    :showWholePath="showWholePath"
    :style="selectStyle"
    filterable
  />
  <el-tree-select
    v-model="fakeValue"
    v-loading="loading"
    :data="[]"
    :placeholder="placeholderText"
    :render-after-expand="false"
    v-else
    key="fakeTree"
    :style="selectStyle"
  />
</template>

<style lang="less" scoped>
:deep(.ed-select-tags-wrapper) {
  display: inline-flex !important;
}

:deep(.ed-select__tags) {
  max-width: v-bind(tagsWidth) !important;
  .ed-tag {
    max-width: v-bind(tagWidth);
  }

  .ed-select__tags-text {
    max-width: v-bind(tagTextWidth) !important;
  }
}
</style>
