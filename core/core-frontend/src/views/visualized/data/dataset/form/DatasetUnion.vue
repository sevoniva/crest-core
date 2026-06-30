<script lang="ts" setup>
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_rename_outlined from '@/assets/svg/icon_rename_outlined.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_textBox_outlined from '@/assets/svg/icon_text-box_outlined.svg'
import icon_fullAssociation from '@/assets/svg/icon_full-association.svg'
import icon_intersect from '@/assets/svg/icon_intersect.svg'
import icon_leftAssociation from '@/assets/svg/icon_left-association.svg'
import icon_rightAssociation from '@/assets/svg/icon_right-association.svg'
import icon_sql_outlined from '@/assets/svg/icon_sql_outlined.svg'
import { getCSSVariable } from '@/utils/color'
import referenceTable from '@/assets/svg/reference-table.svg'
import icon_moreVertical_outlined from '@/assets/svg/icon_more-vertical_outlined.svg'
import { reactive, computed, ref, nextTick, inject, type Ref, watch, unref } from 'vue'
import AddSql from './AddSql.vue'
import { useI18n } from '@/hooks/web/useI18n'
import zeroNodeImg from '@/assets/img/drag.png'
import { ElMessageBox, type Action } from 'element-plus-secondary'
import { guid } from './util'
import { HandleMore } from '@/components/handle-more'
import { propTypes } from '@/utils/propTypes'
import UnionFieldList from './UnionFieldList.vue'
import { type Node, type Field, num } from './util'
import { getTableField } from '@/api/dataset'
import type { SqlNode } from './AddSql.vue'
import { cloneDeep } from 'lodash-es'
import type { Table } from '@/api/dataset'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
const appearanceStore = useAppearanceStoreWithOut()
// 联合编辑器的本地树状态，包含真实节点、拖拽占位节点和当前占位父节点
const state = reactive({
  nodeList: [],
  visualNode: null,
  visualNodeParent: null,
  visualPath: null
})
// 父级数据集表单传入的拖拽遮罩、坐标偏移和数据源名称解析能力
const props = defineProps({
  maskShow: propTypes.bool.def(false),
  offsetX: propTypes.number.def(0),
  offsetY: propTypes.number.def(0),
  dragHeight: propTypes.number.def(260),
  getDsName: propTypes.func
})

// 当前主题主色，用于占位节点和拖拽连线高亮
const primaryColor = computed(() => {
  return appearanceStore.themeColor === 'custom' ? appearanceStore.customColor : getCSSVariable()
})
const isCross = inject<Ref>('isCross')

const iconName = {
  left: icon_leftAssociation,
  right: icon_rightAssociation,
  inner: icon_intersect,
  full: icon_fullAssociation
}
const { t } = useI18n()
// 自定义 SQL 全屏编辑抽屉的显示状态
const editSqlField = ref(false)

// 当前字段选择抽屉中展示的字段列表
const nodeField = ref<Field[]>([])

// 当前正在编辑字段选择的联合节点
const currentNode = ref<Node>()

// 当前正在编辑的自定义 SQL 节点
const sqlNode = ref<SqlNode>()

const allfields = inject('allfields') as Ref

// 拉取指定节点的字段元数据，并按已选字段恢复勾选状态
const getNodeField = ({ datasourceId, id, info, tableName, type, currentDsFields }) => {
  return getTableField({ datasourceId, id, info, tableName, type, isCross: isCross.value })
    .then(res => {
      const idOriginNameMap = allfields.value.reduce((pre, next) => {
        pre[`${next.datasetTableId}${next.originName}`] = next.id
        return pre
      }, {})
      nodeField.value = res as unknown as Field[]
      nodeField.value.forEach(ele => {
        ele.id = idOriginNameMap[`${id}${ele.originName}`]
        ele.checked = currentDsFields.map(ele => ele.originName).includes(ele.originName)
      })
    })
    .finally(() => {
      editUnion.value = true
    })
}

// 当前联合树中已有的表名和数据源组合，用于外部判断重复节点
const nodeNameList = computed(() => {
  const arr = []
  dfsNodeNameList(state.nodeList, arr)
  return arr
})

// 拖拽画布遮罩 DOM 引用，用于点击空白区取消激活节点
const dragMask = ref()

// 点击画布空白区域时清除当前激活节点
const handleClickOutside = ev => {
  if (ev.target === dragMask.value) {
    activeNodeId.value = ''
  }
}

// 当前高亮的节点 ID
const activeNodeId = ref('')

let shadowWidth = 0
let shadowHeight = 0

// 根据树布局深度和宽度计算 SVG 画布尺寸
const setLayOut = () => {
  svgWidth.value = `${shadowWidth * 100 + (shadowWidth + 1) * 200 + 48}px`
  svgHeight.value = `${shadowHeight * 24 + (shadowHeight + 1) * 32 + 48}px`
}
// 带坐标信息的树节点列表，供 SVG foreignObject 渲染
const dfsNodeList = computed(() => {
  let nodeListLocation = []
  shadowWidth = 0
  shadowHeight = 0
  dfsNode(state.nodeList, nodeListLocation)
  setLayOut()
  return nodeListLocation
})

// 深度优先收集节点名和数据源组合，保持联合树中节点引用可检查
const dfsNodeNameList = (list, arr) => {
  list.forEach(ele => {
    arr.push(`${ele.tableName}${ele.datasourceId}`)
    if (ele.children?.length) {
      dfsNodeNameList(ele.children, arr)
    }
  })
}

// 判断某棵子树是否全部来自同一个数据源
const dfsForDsId = (arr, datasourceId) => {
  return arr.every(ele => {
    if (ele.children?.length) {
      return dfsForDsId(ele.children, datasourceId)
    }
    if (!ele.datasourceId) {
      return true
    }
    return ele.datasourceId === datasourceId
  })
}

// 判断当前联合树是否跨数据源，父级保存时会用它决定跨源模式
const crossDatasources = computed(() => {
  const { datasourceId, children = [] } = state.nodeList[0] || {}
  if (datasourceId && !!children.length) {
    return !dfsForDsId(children, datasourceId)
  }
  return false
})

let isUpdate = false

// 初始化完成后，任何联合树变化都通知父级进入已变更状态
watch(
  () => state.nodeList,
  () => {
    if (isUpdate) {
      emits('changeUpdate')
    }
  },
  { deep: true }
)

// 从父级传入的联合树初始化本地状态，下一轮 tick 后再开启变更通知
const initState = nodeList => {
  Object.assign(state.nodeList, nodeList)
  nextTick(() => {
    isUpdate = true
    emits('addComplete')
  })
}

// 字段选择抽屉显示状态
const editUnion = ref(false)

// SVG 画布宽度，随树布局动态扩展
const svgWidth = ref('200px')
// SVG 画布高度，随树布局动态扩展
const svgHeight = ref('260px')

// 从联合树中递归删除指定节点
const delNode = (id, arr) => {
  arr.some((ele, index) => {
    if (id === ele.id) {
      arr.splice(index, 1)
      return true
    }
    if (ele.children?.length) {
      delNode(id, ele.children)
    }
    return false
  })
}
let fakeDelId = []

// 收集待删除子树内所有字段 ID，用于检查计算字段依赖
const collectId = arr => {
  arr.forEach(ele => {
    fakeDelId = [...fakeDelId, ...ele.currentDsFields.map(itx => itx.id)]
    if (ele.children?.length) {
      collectId(ele.children)
    }
  })
}

// 预演节点删除，先收集将受影响的字段 ID，不立即修改联合树
const delNodeFake = (id, arr) => {
  arr.forEach(ele => {
    if (id === ele.id) {
      fakeDelId = [...ele.currentDsFields.map(itx => itx.id)]
      if (ele.children?.length) {
        collectId(ele.children)
      }
    } else if (ele.children?.length) {
      delNodeFake(id, ele.children)
    }
  })
}

// 已修改 SQL 内容的节点 ID，用于提示关联条件可能需要重建
const changeSqlId = ref([])
// 已确认过关联条件变更的节点对，用于避免重复提示
const changedNodeId = ref([])
// 保存自定义 SQL 节点，并在新建根节点或占位节点时补齐字段信息
const saveSqlNode = (val: SqlNode, cb) => {
  const { tableName, id, sql, datasourceId, sqlVariableDetails = null, changeFlag = false } = val
  if (changeFlag) {
    changedNodeId.value = changedNodeId.value.filter(itx => itx.from !== id && id !== itx.to)
    !changeSqlId.value.includes(id) && changeSqlId.value.push(id)
  }
  if (state.visualNode) {
    Object.assign(state.visualNode, {
      info: JSON.stringify({ table: tableName, sql }),
      sqlVariableDetails,
      unionType: 'left',
      type: 'sql',
      id,
      datasourceId,
      unionFields: [],
      currentDsFields: []
    })
    if (!state.nodeList.length) {
      state.visualNode.tableName = tableName
      getTableField({
        datasourceId,
        id: id,
        info: state.visualNode.info,
        tableName,
        type: 'sql',
        isCross: isCross.value
      }).then(res => {
        state.visualNode.confirm = true
        state.nodeList.push(state.visualNode)
        currentNode.value = state.nodeList[0]
        nodeField.value = res as unknown as Field[]
        nodeField.value.forEach(ele => {
          ele.checked = true
        })
        state.nodeList[0].currentDsFields = cloneDeep(res)
        cb?.()
        confirmEditUnion()
        confirm()
      })
    } else {
      getTableField({
        datasourceId,
        id: id,
        info: state.visualNode.info,
        tableName,
        type: 'sql',
        isCross: isCross.value
      }).then(() => {
        state.visualNode.confirm = true
        cb?.()
      })
    }
    return
  }
  const obj = {
    info: JSON.stringify({ table: tableName, sql }),
    id,
    datasourceId,
    tableName,
    sqlVariableDetails
  }
  dfsNodeBack([obj], [id], state.nodeList)
  cb?.()
  emits('reGetName')
}

// 记录某条关联线已经处理过 SQL 变更提示
const setChangeStatus = (to, from) => {
  if (changedNodeId.value.some(ele => ele.from === from && ele.to === to)) return
  changedNodeId.value.push({ from, to })
}

// 关闭自定义 SQL 编辑抽屉，并按保存状态恢复占位节点或刷新字段
const closeSqlNode = () => {
  if (
    state.nodeList.length === 1 &&
    !state.nodeList[0].children?.length &&
    changeSqlId.value.length === 1
  ) {
    currentNode.value = state.nodeList[0]
    const { datasourceId, id, info, tableName, sqlVariableDetails } = currentNode.value
    getTableField({
      datasourceId,
      id,
      info,
      tableName,
      type: 'sql',
      isCross: isCross.value,
      sqlVariableDetails: sqlVariableDetails
    }).then(res => {
      const idOriginNameMap = allfields.value.reduce((pre, next) => {
        pre[`${next.datasetTableId}${next.originName}`] = next.id
        return pre
      }, {})
      nodeField.value = res as unknown as Field[]
      nodeField.value.forEach(ele => {
        ele.id = idOriginNameMap[`${id}${ele.originName}`]
        ele.checked = true
      })
      state.nodeList[0].currentDsFields = cloneDeep(res)
      editUnion.value = true
    })
    changeSqlId.value = []
  }
  if (state.visualNode?.confirm) {
    nextTick(() => {
      emits('joinEditor', [
        {
          ...state.visualNode,
          tableName: (JSON.parse(state.visualNode.info) as { table: string }).table
        },
        state.visualNodeParent
      ])
    })
    editSqlField.value = false
    return
  }
  editSqlField.value = false
  if (!state.visualNodeParent) {
    state.visualNode = null
    return
  }
  state.visualNodeParent.children = state.visualNodeParent.children.filter(ele => !ele.isShadow)
  confirm()
}

// 接收字段选择组件返回的字段列表，暂存到当前节点副本
const changeNodeFields = val => {
  currentNode.value.currentDsFields = val
}

// 关闭字段选择抽屉，并清理未确认的根占位节点
const closeEditUnion = () => {
  nodeField.value = []
  currentNode.value = null
  const [fir] = state.nodeList
  if (fir.isShadow) {
    delete fir.isShadow
    state.nodeList = []
    emits('addComplete')
  }
  editUnion.value = false
}

// 为新增字段补齐前端临时 ID、数据表 ID 和数据源 ID
const setGuid = (arr, id, datasourceId) => {
  arr.forEach(ele => {
    if (!ele.id) {
      ele.id = `${++num.value}`
      ele.datasetTableId = id
      ele.datasourceId = datasourceId
    }
  })
}

// 把字段选择结果写回联合树中的真实节点
const delUpdateDsFields = (id, arr: Node[]) => {
  arr.some(ele => {
    if (id === ele.id) {
      setGuid(currentNode.value.currentDsFields, ele.id, ele.datasourceId)
      ele.currentDsFields = currentNode.value.currentDsFields
      return true
    }
    if (ele.children?.length) {
      delUpdateDsFields(id, ele.children)
    }
    return false
  })
}
// 预演字段更新，收集原字段 ID 以判断计算字段是否会被删除
const delUpdateDsFieldsFake = (id, arr: Node[]) => {
  arr.forEach(ele => {
    if (id === ele.id) {
      fakeDelId = [...ele.currentDsFields.map(itx => itx.id)]
    }
    if (ele.children?.length) {
      delUpdateDsFieldsFake(id, ele.children)
    }
  })
}

// 确认字段选择变更，若删除字段被计算字段引用则先弹出风险确认
const confirmEditUnion = () => {
  delUpdateDsFieldsFake(currentNode.value.id, state.nodeList)
  const currentIds = currentNode.value.currentDsFields.map(ele => ele.id)
  let ids = fakeDelId.filter(ele => !currentIds.includes(ele))
  fakeDelId = []
  if (!!ids.length) {
    const idArr = allfields.value.reduce((pre, next) => {
      if (next.extField === 2) {
        let idMap = next.originName.match(/\[(.+?)\]/g) || []
        idMap = idMap.filter(
          itx => !next.params?.map(element => element.id).includes(itx.slice(1, -1))
        )
        const result = idMap.map(itm => {
          return itm.slice(1, -1)
        })
        result.forEach(ele => {
          if (ids.includes(ele)) {
            pre.push(ele)
          }
        })
      }
      return pre
    }, [])

    if (!!idArr.length) {
      ElMessageBox.confirm(t('data_set.field_selection'), {
        confirmButtonText: t('dataset.confirm'),
        cancelButtonText: t('common.cancel'),
        showCancelButton: true,
        tip: `${t('data_set.field')}: ${allfields.value
          .filter(ele => [...new Set(idArr)].includes(ele.id) && ele.extField !== 2)
          .map(ele => ele.name)
          .join(',')}, ${t('data_set.confirm_the_deletion')}`,
        confirmButtonType: 'danger',
        type: 'warning',
        autofocus: false,
        showClose: false,
        callback: (action: Action) => {
          if (action === 'confirm') {
            delUpdateDsFields(currentNode.value.id, state.nodeList)
            const [fir] = state.nodeList
            if (fir.isShadow) {
              delete fir.isShadow
            }
            closeEditUnion()
            nextTick(() => {
              emits('updateAllfields')
            })
          }
        }
      })
      return
    }
  }

  delUpdateDsFields(currentNode.value.id, state.nodeList)
  const [fir] = state.nodeList
  if (fir.isShadow) {
    delete fir.isShadow
  }
  closeEditUnion()
  nextTick(() => {
    emits('updateAllfields')
  })
}

// 处理节点更多菜单命令，包含字段选择、重命名、编辑 SQL 和删除节点
const handleCommand = (ele, command) => {
  if (command === 'editorField') {
    getNodeField(ele)
    currentNode.value = cloneDeep(ele)
  }

  if (command === 'rename') {
    tableRename({ name: ele.tableName, id: ele.id })
  }

  if (command === 'editorSql') {
    const { tableName, datasourceId, info, id, sqlVariableDetails } = ele
    if (ele.type === 'sql') {
      sqlNode.value = {
        sql: ((JSON.parse(info) as { sql: string }) || {}).sql,
        tableName,
        id,
        variables: JSON.parse(sqlVariableDetails),
        datasourceId
      }
      editSqlField.value = true
      return
    }
  }

  if (command === 'del') {
    delNodeFake(ele.id, state.nodeList)
    if (!!fakeDelId.length) {
      const idArr = allfields.value.reduce((pre, next) => {
        if (next.extField === 2) {
          const idMap = next.originName.match(/\[(.+?)\]/g) || []
          const result = idMap.map(itm => {
            return itm.slice(1, -1)
          })
          result.forEach(ele => {
            if (fakeDelId.includes(ele)) {
              pre.push(ele)
            }
          })
        }
        return pre
      }, [])
      fakeDelId = []
      if (!!idArr.length) {
        ElMessageBox.confirm(t('data_set.confirm_to_delete', { a: ele.tableName }), {
          confirmButtonText: t('dataset.confirm'),
          cancelButtonText: t('common.cancel'),
          showCancelButton: true,
          tip: t('data_set.also_be_deleted'),
          confirmButtonType: 'danger',
          type: 'warning',
          autofocus: false,
          showClose: false,
          callback: (action: Action) => {
            if (action === 'confirm') {
              delNode(ele.id, state.nodeList)
              nextTick(() => {
                emits('addComplete')
                emits('updateAllfields')
              })
            }
          }
        })
        return
      }
    }

    delNode(ele.id, state.nodeList)
    nextTick(() => {
      emits('addComplete')
      emits('updateAllfields')
    })
  }
}

// 点击关联线时定位两端节点，并通知父级打开关联条件编辑器
const handlePathClick = ele => {
  const { from, to } = ele
  const arr = []
  const idArr = [from.id, to.id]
  dfsPath(arr, idArr, state.nodeList)
  emits('joinEditor', arr)
}

// 按节点 ID 深度优先收集关联线两端节点
const dfsPath = (arr, idArr, list) => {
  list.forEach(ele => {
    if (idArr.includes(ele.id)) {
      arr.unshift(ele)
    }
    if (ele.children?.length) {
      dfsPath(arr, idArr, ele.children)
    }
  })
}

// 按节点 ID 将外部保存后的节点信息写回联合树
const dfsNodeBack = (arr, idArr, list) => {
  list.forEach(ele => {
    if (idArr.includes(ele.id)) {
      idArr.shift()
      const node = arr.shift()
      Object.assign(ele, node)
    }
    if (ele.children?.length) {
      dfsNodeBack(arr, idArr, ele.children)
    }
  })
}

// 删除某个字段时，从对应数据表节点的已选字段中同步移除
const dfsNodeFieldBack = (list, { originName, datasetTableId }) => {
  list.forEach(ele => {
    if (datasetTableId === ele.id) {
      const currentDsFields = ele.currentDsFields.filter(ele => ele.originName !== originName)
      ele.currentDsFields = currentDsFields
    }
    if (ele.children?.length) {
      dfsNodeFieldBack(ele.children, { originName, datasetTableId })
    }
  })
}

// 对外暴露的字段删除回写入口，父级字段面板删除字段时调用
const dfsNodeFieldBackReal = ele => {
  dfsNodeFieldBack(state.nodeList, ele)
}

const menuList = [
  {
    svgName: icon_textBox_outlined,
    label: t('data_set.field_selection'),
    command: 'editorField'
  },
  {
    svgName: icon_deleteTrash_outlined,
    label: t('data_set.delete'),
    command: 'del'
  }
]

const sqlMenu = [
  {
    svgName: icon_edit_outlined,
    label: t('data_set.edit_sql'),
    command: 'editorSql'
  },
  {
    svgName: icon_rename_outlined,
    label: t('datasource.field_rename'),
    command: 'rename'
  }
]

// 当前拖拽节点相对画布的横向偏移
const dragOffsetX = ref(0)
// 当前拖拽节点相对画布的纵向偏移
const dragOffsetY = ref(0)

// 计算拖拽矩形和候选投放区域的交叠面积
function elementInteractArea(pos1, pos2) {
  const pos1Width = pos1.right - pos1.left
  const pos1Height = pos1.bottom - pos1.top
  const pos2Width = pos2.right - pos2.left
  const pos2Height = pos2.bottom - pos2.top

  const axisOverlap =
    pos1Width + pos2Width - (Math.max(pos1.right, pos2.right) - Math.min(pos1.left, pos2.left))
  const crossOverlap =
    pos1Height + pos2Height - (Math.max(pos1.bottom, pos2.bottom) - Math.min(pos1.top, pos2.top))
  if (axisOverlap <= 0 || crossOverlap <= 0) {
    return 0
  }
  return axisOverlap * crossOverlap
}

// 可投放区域列表，包含节点下方和叶子节点右侧的候选区域
const possibleNodeAreaList = computed(() => {
  let flatArr = []
  leafNode(dfsNodeList.value, flatArr)
  return flatArr.filter(ele => !ele.isShadow)
})

// 从布局树中收集可接收拖拽的节点区域
const leafNode = (arr, leafList) => {
  arr.forEach((ele, index) => {
    const fromX = ele.x * 300 + 24
    const fromY = ele.y * 56 + 24
    let toX = fromX + 200
    let toY = fromY + 56
    const next = arr[index + 1]
    if (next) {
      toY = next.y * 56 + 24
    }
    if (ele.children?.length) {
      leafNode(ele.children, leafList)
    }
    if (!!ele.x || (!!ele.y && !!ele.x)) {
      leafList.push({
        ...ele,
        isShadow: ele.isShadow,
        isLeaf: !ele.children?.length,
        fromX,
        fromY,
        toX,
        toY
      })
    }
  })
}

// 扁平化后的节点列表，用于 SVG 节点渲染
const flatNodeList = computed(() => {
  let flatArr = []
  flatNode(dfsNodeList.value, flatArr)
  return flatArr
})

// 深度优先把布局树展开成一维节点数组
const flatNode = (arr, flatNodeList) => {
  arr.forEach(ele => {
    flatNodeList.push(ele)
    if (ele.children?.length) {
      flatNode(ele.children, flatNodeList)
    }
  })
}

// 扁平化后的连线列表，用于 SVG path 和关联按钮渲染
const flatPathList = computed(() => {
  let flatArr = []
  const [root = {}] = dfsNodeList.value
  flatLine(root, flatArr)
  return flatArr
})

// 根据联合树层级计算每个节点的画布坐标和子树高度
const dfsNode = (arr = [], nodeListLocation, x = 0, y = 0) => {
  arr.map((ele, index) => {
    const pre = nodeListLocation[index - 1]
    if (!ele.children?.length) {
      let idxChild = index + y
      let maxY = 0
      if (pre) {
        const last = pre.children?.length
          ? pre.children[pre.children.length - 1]
          : { y: 0, maxY: 0 }
        idxChild = Math.max(last.y, pre.y) + 1
        maxY += idxChild
        idxChild = Math.max(idxChild, last.maxY)
      }
      nodeListLocation.push({
        ...ele,
        x,
        y: idxChild,
        maxY,
        isShadow: !!ele.isShadow
      })
      shadowHeight = Math.max(idxChild, shadowHeight)
      shadowWidth = Math.max(x, shadowWidth)
    } else {
      const children = []
      const pre = nodeListLocation[index - 1]
      let idx = y
      let maxY = 0
      if (pre) {
        const last = pre.children?.length
          ? pre.children[pre.children.length - 1]
          : { y: 0, maxY: 0 }
        idx = Math.max(last.y, pre.y) + 1
        maxY = last.maxY
      }
      dfsNode(ele.children, children, x + 1, idx ? Math.max(idx, maxY) : idx)
      maxY = Math.max(children[children.length - 1].maxY + 1, maxY)

      nodeListLocation.push({
        ...ele,
        x,
        y: children[0].y,
        maxY,
        isShadow: !!ele.isShadow,
        children
      })
      shadowHeight = Math.max(children[0].y, shadowHeight)
      shadowWidth = Math.max(x, shadowWidth)
    }
  })
}

// 在目标节点旁插入拖拽占位节点，position 为 b 表示下方，r 表示右侧子节点
const dfsNodeShadow = (arr, { tableName, id }, position, parent) => {
  return arr.some((ele, index) => {
    if (ele.tableName === tableName && id === ele.id) {
      const flag = tableName + '_&&' + position
      if (ele.isShadow && state.visualNode.flag === flag) return true
      state.visualNode = {
        tableName: '',
        isShadow: true,
        flag
      }

      if (position === 'b') {
        state.visualNodeParent = parent
        arr.splice(index + 1, 0, state.visualNode)
      } else {
        state.visualNodeParent = ele
        ele.children = [state.visualNode]
      }
      return true
    }
    if (ele.children?.length) {
      return dfsNodeShadow(ele.children, { tableName, id }, position, ele)
    }
    return false
  })
}

// 将布局树转换成连线渲染数据，并标记 SQL 变更导致的关联提示状态
const flatLine = (item, flatNodeList) => {
  let sqlChangeFlag = changeSqlId.value.includes(item.id)
  if (item.children?.length) {
    sqlChangeFlag = item.children.some(itx => changeSqlId.value.includes(itx.id)) || sqlChangeFlag
  }
  const from = { ...item, d: '' }
  ;(item.children || []).forEach(ele => {
    let localSqlChangeFlag = true
    changedNodeId.value.some(element => {
      if (
        (element.from === item.id && ele.id === element.to) ||
        (element.from === ele.id && item.id === element.to)
      ) {
        localSqlChangeFlag = false
        return true
      }
      return false
    })
    flatNodeList.push({
      from,
      sqlChangeFlag: localSqlChangeFlag && sqlChangeFlag,
      isShadow: ele.isShadow || item.isShadow,
      to: {
        ...ele
      },
      d:
        ele.y === from.y
          ? `M ${item.x * 300 + 224} ${ele.y * 56 + 40} l 100 0`
          : `M ${item.x * 300 + 240} ${from.y * 56 + 40} l 0 ${(ele.y - from.y) * 56} l 84 0`
    })
    if (ele.children?.length) {
      flatLine(ele, flatNodeList)
    }
  })
}

// 拖拽离开画布时撤销临时占位节点
const dragleave_handler = ev => {
  ev.preventDefault()
  notConfirm()
}

// 拖拽悬停时根据交叠面积选择最佳投放位置
const dragover_handler = ev => {
  ev.preventDefault()

  dragOffsetX.value = ev.offsetX - props.offsetX
  dragOffsetY.value = ev.offsetY - props.offsetY

  const lg = state.nodeList.length
  const [fir] = state.nodeList

  if (!lg) return

  const [obj] = fir.children || []

  if (!fir.children?.length || obj?.isShadow) {
    if (obj?.isShadow) return
    state.visualNode = {
      tableName: '',
      isShadow: true,
      flag: '_&&'
    }

    state.nodeList[0].children = [state.visualNode]
    state.visualNodeParent = state.nodeList[0]
    return
  }

  let resultList = possibleNodeAreaList.value.map(ele => {
    const { fromX, fromY, toX, toY, isLeaf = false, tableName } = ele
    return [
      elementInteractArea(
        {
          left: dragOffsetX.value,
          right: dragOffsetX.value + 200,
          top: dragOffsetY.value,
          bottom: dragOffsetY.value + 32
        },
        {
          left: fromX,
          right: toX,
          top: fromY,
          bottom: toY
        }
      ),
      isLeaf || state.visualNode?.flag === tableName + '_&&r'
        ? elementInteractArea(
            {
              left: dragOffsetX.value,
              right: dragOffsetX.value + 200,
              top: dragOffsetY.value,
              bottom: dragOffsetY.value + 32
            },
            {
              left: fromX + 200,
              right: toX + 100,
              top: fromY,
              bottom: fromY + 32
            }
          )
        : 0
    ]
  })

  let maxIndex = 0

  resultList.reduce((pre, next, idx) => {
    const max = Math.max(...pre) > Math.max(...next)
    maxIndex = max ? maxIndex : idx
    return max ? pre : next
  })

  let maxArr = resultList[maxIndex]

  if (Array.isArray(state.visualNodeParent?.children)) {
    const shadowIndex = state.visualNodeParent.children.findIndex(ele => ele.isShadow)
    if (shadowIndex > -1) {
      state.visualNodeParent.children.splice(shadowIndex, 1)
    }
  }
  state.visualNode = null

  if (Math.max(...maxArr)) {
    const { tableName, isShadow = false, id } = possibleNodeAreaList.value[maxIndex]
    const [b, r] = maxArr
    if (!isShadow) {
      dfsNodeShadow(state.nodeList, { tableName, id }, b >= r ? 'b' : 'r', state.nodeList[0])
    }
  }
}

// 拖拽进入画布时阻止浏览器默认行为，允许后续 drop 生效
const dragenter_handler = ev => {
  ev.preventDefault()
}

// 投放数据表或自定义 SQL 节点，并根据位置创建根节点或关联节点
const drop_handler = ev => {
  ev.preventDefault()
  let data = ev.dataTransfer.getData('text/plain') || ev.dataTransfer.getData('text')
  const { tableName, type, datasourceId, name: noteName } = JSON.parse(data) as Table
  const extraData = {
    info: JSON.stringify({
      table: tableName,
      sql: ''
    }),
    noteName,
    unionType: 'left',
    unionFields: [],
    currentDsFields: [],
    sqlVariableDetails: null
  }

  if (!state.nodeList.length) {
    if (type === 'sql') {
      state.visualNode = {
        tableName,
        type,
        datasourceId,
        id: guid(),
        ...extraData
      }
      sqlNode.value = {
        sql: '',
        tableName,
        id: state.visualNode.id,
        datasourceId
      }
      editSqlField.value = true
      return
    }
    state.nodeList.push({
      tableName,
      type,
      isShadow: true,
      datasourceId,
      id: guid(),
      ...extraData
    })

    currentNode.value = state.nodeList[0]

    getTableField({
      datasourceId,
      id: currentNode.value.id,
      info: currentNode.value.info,
      tableName,
      isCross: isCross.value,
      type
    })
      .then(res => {
        nodeField.value = res as unknown as Field[]
        nodeField.value.forEach(ele => {
          ele.checked = true
        })
        state.nodeList[0].currentDsFields = cloneDeep(res)
      })
      .finally(() => {
        editUnion.value = true
      })
    nextTick(() => {
      emits('addComplete')
    })
    return
  }

  nextTick(() => {
    emits('addComplete')
  })

  if (!state.visualNode) return
  if (type === 'sql') {
    sqlNode.value = {
      sql: '',
      tableName,
      id: guid(),
      datasourceId
    }
    editSqlField.value = true
    return
  }

  nextTick(() => {
    Object.assign(state.visualNode, {
      tableName,
      type,
      datasourceId,
      id: guid(),
      ...extraData
    })
    emits('joinEditor', [
      {
        tableName,
        type,
        datasourceId,
        id: state.visualNode.id,
        ...extraData
      },
      state.visualNodeParent
    ])
  })
}

// 接收父级关联编辑器保存后的父子节点信息，并写回本地联合树
const setStateBack = (node, parent) => {
  delete parent.children
  delete node.children
  dfsNodeBack([parent, node], [parent.id, node.id], state.nodeList)
  if (state.visualNode) {
    confirm()
  }
}

// 重命名弹窗显示状态
const dialogRename = ref(false)
// 重命名表单实例引用，用于确认前校验
const renameForm = ref()
const defaultParam = {
  name: '',
  id: ''
}
// 在联合树中按 ID 更新节点展示名
const dfsNodeListRename = arr => {
  arr.some(ele => {
    if (ele.id === renameParam.id && ele.tableName !== renameParam.name) {
      ele.tableName = renameParam.name
      return true
    }

    if (!!ele.children?.length) {
      dfsNodeListRename(ele.children)
    }
  })
}
// 重命名弹窗表单数据
const renameParam = reactive(cloneDeep(defaultParam))
// 确认重命名，校验通过后写回联合树并通知父级刷新名称缓存
const confirmRename = () => {
  renameForm.value.validate(val => {
    if (val) {
      dfsNodeListRename(state.nodeList)
      renameParam.name = ''
      renameParam.id = ''
      dialogRename.value = false
      emits('reGetName')
    }
  })
}
// 打开重命名弹窗并带入当前节点名称
const tableRename = ({ name, id }) => {
  renameParam.name = name
  renameParam.id = id
  dialogRename.value = true
}
// 确认当前拖拽占位节点，将其转为真实节点并清理占位状态
const confirm = () => {
  state.visualNode.isShadow = false
  delete state.visualNode.flag
  state.visualNode = null
  state.visualNodeParent = null
}

// 撤销当前拖拽占位节点，常用于拖拽离开或取消关联编辑
const notConfirm = () => {
  if (!state.visualNodeParent) return
  state.visualNodeParent.children = state.visualNodeParent.children.filter(ele => !ele.isShadow)
  confirm()
}

// 返回联合树深拷贝，避免父级直接修改本地响应式状态
const getNodeList = () => {
  return cloneDeep(unref(state.nodeList))
}

defineExpose({
  nodeNameList,
  getNodeList,
  setStateBack,
  notConfirm,
  dfsNodeFieldBackReal,
  initState,
  setChangeStatus,
  crossDatasources
})

// 激活节点并直接打开字段选择抽屉
const handleActiveNode = ele => {
  activeNodeId.value = ele.id
  handleCommand(ele, 'editorField')
}

// 联合编辑器对父级发出的结构、字段和名称变更事件
const emits = defineEmits([
  'addComplete',
  'joinEditor',
  'updateAllfields',
  'changeUpdate',
  'reGetName'
])
</script>

<template>
  <div
    @drop="$event => drop_handler($event)"
    @dragenter="$event => dragenter_handler($event)"
    @dragover="$event => dragover_handler($event)"
    @dragleave="$event => dragleave_handler($event)"
    class="drag-mask_dataset"
    ref="dragMask"
    @click="handleClickOutside"
    :style="{ height: dragHeight + 'px' }"
  >
    <svg
      version="1.1"
      baseProfile="full"
      :width="svgWidth"
      :height="svgHeight"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        :key="ele.d"
        class="path-point"
        v-for="ele in flatPathList"
        :d="ele.d"
        :stroke-dasharray="ele.isShadow ? '4,4' : '0'"
        :stroke="ele.isShadow ? primaryColor : '#BBBFC4'"
        stroke-width="1"
        fill="none"
      />
      <foreignObject
        :key="ele.tableName"
        v-for="ele in flatNodeList"
        :x="ele.x * 300 + 24"
        :y="ele.y * 56 + 24"
        width="200"
        height="32"
      >
        <div
          @click="handleActiveNode(ele)"
          class="node-union"
          ref="activeNode"
          :class="[
            {
              'shadow-node': ele.isShadow,
              'active-node': activeNodeId === ele.id
            }
          ]"
        >
          <el-icon>
            <Icon
              ><component
                class="svg-icon"
                :is="ele.type !== 'sql' ? referenceTable : icon_sql_outlined"
              ></component
            ></Icon>
          </el-icon>
          <span class="tableName">{{ ele.tableName }}</span>
          <span class="placeholder">{{ t('data_set.custom_sql_here') }}</span>
          <handle-more
            style="margin-left: auto"
            :iconName="icon_moreVertical_outlined"
            :menuList="ele.type === 'sql' ? [...sqlMenu, ...menuList] : menuList"
            @handle-command="command => handleCommand(ele, command)"
          ></handle-more>
        </div>
      </foreignObject>

      <foreignObject
        :key="ele.d"
        v-for="ele in flatPathList"
        :x="ele.from.x * 300 + 272"
        :y="ele.to.y * 56 + 24"
        width="32"
        height="32"
      >
        <div
          v-if="!ele.isShadow"
          @click="handlePathClick(ele)"
          class="path-union"
          :style="{ borderColor: ele.sqlChangeFlag ? '#F54A45' : '' }"
        >
          <el-icon>
            <Icon><component class="svg-icon" :is="iconName[ele.to.unionType]"></component></Icon>
          </el-icon>
        </div>
      </foreignObject>
    </svg>
    <div
      class="mask-dataset"
      :class="[
        {
          'mask-dataset-none': !state.nodeList.length
        }
      ]"
      v-if="maskShow"
    ></div>
    <div class="zero" v-if="!state.nodeList.length">
      <img :src="zeroNodeImg" alt="" />
      <p>{{ t('data_set.on_the_left') }}</p>
      <p>{{ t('data_set.a_data_set') }}</p>
    </div>
  </div>
  <el-dialog
    v-model="dialogRename"
    :close-on-press-escape="false"
    :close-on-click-modal="false"
    :title="t('data_set.rename_table')"
    width="420px"
  >
    <el-form
      ref="renameForm"
      require-asterisk-position="right"
      :model="renameParam"
      label-position="top"
    >
      <el-form-item
        prop="name"
        :label="t('data_set.table_name')"
        :rules="[
          {
            required: true,
            message: t('commons.cannot_be_null')
          }
        ]"
      >
        <el-input :placeholder="t('common.inputText')" v-model="renameParam.name"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button secondary @click="dialogRename = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button type="primary" @click="confirmRename">
          {{ t('common.sure') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
  <el-drawer
    :before-close="closeEditUnion"
    v-model="editUnion"
    modal-class="union-item-drawer"
    size="600px"
    direction="rtl"
  >
    <template #header v-if="currentNode">
      <div style="width: 100%">
        <div class="info">
          <span :title="currentNode.tableName" class="label ellipsis">{{
            currentNode.tableName
          }}</span>
        </div>
        <div class="info" style="margin-top: 4px">
          <span
            :title="getDsName(currentNode.datasourceId)"
            style="max-width: 550px"
            class="name ellipsis"
            >{{ t('auth.datasource') }}:{{ getDsName(currentNode.datasourceId) }}</span
          >
        </div>
        <div class="info" style="margin-top: 4px">
          <span :title="currentNode.noteName" style="max-width: 500px" class="name ellipsis"
            >{{ t('data_set.table_remarks') }}:{{ currentNode.noteName || '-' }}</span
          >
        </div>
      </div>
    </template>
    <union-field-list
      :field-list="nodeField"
      :node="currentNode"
      v-if="nodeField.length"
      @checkedFields="changeNodeFields"
    />
    <template #footer>
      <el-button secondary @click="closeEditUnion">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmEditUnion">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-drawer>
  <el-drawer
    direction="btt"
    :close-on-click-modal="false"
    size="calc(100% - 100px)"
    :with-header="false"
    :close-on-press-escape="false"
    modal-class="sql-drawer-fullscreen"
    v-model="editSqlField"
  >
    <add-sql @save="saveSqlNode" @close="closeSqlNode" :sqlNode="sqlNode"></add-sql>
  </el-drawer>
</template>

<style lang="less">
.path-point {
  cursor: pointer;
}

.sql-drawer-fullscreen {
  .ed-drawer.btt > .ed-drawer__body {
    padding: 0;
  }
}

.union-item-drawer {
  .ed-drawer__header {
    height: auto;
    font-family: var(--crest-custom_font, 'PingFang');

    .ed-drawer__close-btn {
      top: 40.5px;
    }

    .info {
      width: 100%;
      display: flex;
      .label {
        font-weight: 500;
        font-size: 16px;
        color: #1f2329;
        max-width: 500px;
      }
      .name {
        font-weight: 400;
        font-size: 14px;
        color: #646a73;
        line-height: 22px;
      }
    }
  }
  .field-block-body {
    height: calc(100% - 70px) !important;
  }
}

.node-union {
  height: 100%;
  width: 100%;
  border: 1px solid #dee0e3;
  border-radius: 6px;
  font-family: var(--crest-custom_font, 'PingFang');
  font-size: 14px;
  font-weight: 400;
  color: #1f2329;
  padding-left: 9px;
  display: flex;
  align-items: center;
  background: #fff;
  position: relative;
  cursor: pointer;
  padding-right: 12px;

  &:hover {
    border-color: var(--ed-color-primary);
  }

  .placeholder {
    display: none;
  }

  & > .ed-icon {
    font-size: 13.3px;
    margin-right: 9.33px;
  }
  .tableName {
    max-width: 125px;
    text-overflow: ellipsis;
    overflow: hidden;
    white-space: nowrap;
  }

  &:not(.shadow-node)::before {
    content: '';
    position: absolute;
    width: 3px;
    height: 32px;
    left: -1px;
    top: -1px;
    background: var(--ed-color-primary);
    border-radius: 6px 0px 0px 4px;
  }
}

.path-union {
  width: 100%;
  height: 100%;
  border: 1px solid #dee0e3;
  border-radius: 50%;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: var(--ed-color-primary);
  cursor: pointer;
  &:hover {
    border-color: var(--ed-color-primary);
  }
}

.shadow-node {
  border: 1px dashed;
  border-color: var(--ed-color-primary);
  background-color: rgba(59, 130, 246, 0.08);
  .ed-icon,
  .tableName {
    display: none;
  }

  .placeholder {
    display: inline-block;
  }
}

.active-node {
  border-color: var(--ed-color-primary);
}

.drag-mask_dataset {
  background: #f5f6f7;
  overflow: auto;
  position: relative;
  width: 100%;
  border: none !important;
}

.mask-dataset {
  position: absolute;
  left: 16px;
  top: 16px;
  width: calc(100% - 32px);
  height: calc(100% - 32px);
  z-index: 5;
  user-select: none;
}

.mask-dataset-none {
  background-color: #e5ebf8;
  border: 1px dashed;
  border-color: var(--ed-color-primary);
}

.zero {
  position: absolute;
  left: 16px;
  top: 16px;
  width: calc(100% - 32px);
  height: calc(100% - 32px);
  z-index: 6;
  user-select: none;
  display: flex;
  align-items: center;
  flex-direction: column;
  padding-top: 42px;
  img {
    width: 125px;
    height: 125px;
    margin-bottom: 8px;
    -webkit-user-drag: none;
  }

  p {
    font-family: var(--crest-custom_font, 'PingFang');
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    text-align: center;
    color: #646a73;
  }
}
</style>
