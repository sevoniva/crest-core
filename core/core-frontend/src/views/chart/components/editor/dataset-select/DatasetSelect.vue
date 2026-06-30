<script setup lang="ts">
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_dataset from '@/assets/svg/icon_dataset.svg'
import icon_done_outlined from '@/assets/svg/icon_done_outlined.svg'
import { Tree } from '../../../../visualized/data/dataset/form/CreatDsGroup.vue'
import { computed, ref, watch, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { useI18n } from '@/hooks/web/useI18n'
import { useAppStoreWithOut } from '@/store/modules/app'
import _ from 'lodash'
import { datasetTree as fetchDatasetTree, datasourceList } from '@/api/dataset'
import { ElFormItem, FormInstance } from 'element-plus-secondary'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useCache } from '@/hooks/web/useCache'
import { useUserStoreWithOut } from '@/store/modules/user'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import treeSort from '@/utils/treeSortUtils'

const dvMainStore = dvMainStoreWithOut()
const { wsCache } = useCache('localStorage')
const userStore = useUserStoreWithOut()
const { t } = useI18n()

// 数据集选择器属性同时支持数据集和数据源两类资源选择场景
const props = withDefaults(
  defineProps<{
    themes?: EditorTheme
    modelValue?: string | number
    stateObj: any
    disabled?: boolean
    viewId: string
    sourceType?: string
  }>(),
  {
    datasetTree: () => [],
    themes: 'dark',
    sourceType: 'dataset',
    disabled: false
  }
)

// 树组件引用，用于执行关键字过滤和暴露节点查询能力
const datasetSelector = ref(null)

// 资源树加载态，控制弹窗内加载占位和空态判断
const loadingDatasetTree = ref(false)

// 当前组织是否仍允许访问已选资源，切换组织后用于阻止跨组织引用
const orgCheck = ref(true)

// 可选资源树数据，数据集和数据源接口返回后统一写入
const datasetTree = ref<Tree[]>([])

// 选择框提示文案按资源类型区分，避免数据集和数据源入口混淆
const selectSource =
  props.sourceType === 'datasource'
    ? t('visualization.select_datasource')
    : t('visualization.select_dataset')

// 新建按钮文案按资源类型切换，并由父组件接管实际创建流程
const newSource =
  props.sourceType === 'datasource'
    ? t('visualization.new_datasource')
    : t('visualization.new_dataset')

// 资源类型名称用于空态、校验和展示文案
const sourceName = computed(() =>
  props.sourceType === 'datasource' ? t('datasource.datasource') : t('visualization.dataset')
)

// 资源树排序沿用用户在数据集树中的排序偏好，接口返回后统一排序
const sortTypeChange = arr => {
  const sortType = wsCache.get('TreeSort-dataset') || 'time_desc'
  datasetTree.value = treeSort(arr, sortType)
}

// 拉取数据集或数据源树，并在结束后触发表单校验刷新选中状态
const initDataset = () => {
  loadingDatasetTree.value = true
  const method = props.sourceType === 'datasource' ? datasourceList : fetchDatasetTree
  const params = props.sourceType === 'datasource' ? null : {}
  ;(method as any)(params)
    .then(res => {
      sortTypeChange((res as unknown as Tree[]) || [])
    })
    .catch(() => {
      sortTypeChange([])
    })
    .finally(() => {
      loadingDatasetTree.value = false
      formRef.value?.validate()
    })
}

// 组件对外事件覆盖双向绑定、状态同步、资源变更和新增弹窗
const emits = defineEmits([
  'update:modelValue',
  'update:stateObj',
  'onDatasetChange',
  'addDsWindow'
])

// modelValue 的本地代理，所有选择变更都通过 update:modelValue 回传父级
const _modelValue = computed({
  get() {
    return props.modelValue
  },
  set(v) {
    emits('update:modelValue', v)
  }
})

// Element Plus 树字段映射，叶子节点代表可被图表选择的真实资源
const dsSelectProps = {
  label: 'name',
  children: 'children',
  value: 'id',
  isLeaf: node => !node.children?.length
}

// 表单引用用于在资源树刷新后重新校验当前选择是否仍存在
const formRef = ref<FormInstance>()
// 弹窗内搜索关键字
const searchStr = ref<string>()

// 搜索关键字变化时直接驱动树组件过滤
watch(searchStr, val => {
  datasetSelector.value.filter(val)
})

// 有可访问资源且加载完成时才展示树，组织失效时转为空态提示
const showTree = computed(() => {
  return (
    datasetTree.value && datasetTree.value.length > 0 && !loadingDatasetTree.value && orgCheck.value
  )
})

// 空态文案区分无数据和切换组织导致的权限失效
const emptyMsg = computed(() => {
  return orgCheck.value ? '暂无' + sourceName.value : '已切换至新组织，无权访问其他组织的资源'
})

// 只有组织权限失效时展示显式空态，普通无数据沿用树组件自身空态
const showEmptyInfo = computed(() => {
  return !showTree.value && !loadingDatasetTree.value && !orgCheck.value
})

// 后端返回 root 包裹节点时剥离 root，使弹窗只展示业务目录
const computedTree = computed(() => {
  if (showTree.value) {
    if (datasetTree.value[0]?.id === '0') {
      return datasetTree.value[0].children || []
    }
  }
  return datasetTree.value
})

// 扁平化后的叶子节点列表，用于快速定位当前选中资源
const flattedTree = computed(() => {
  return _.filter(flatTree(computedTree.value), node => node.leaf)
})

// 当前 modelValue 对应的资源节点，找不到时代表已删除或无权限
const selectedNode = computed(() => {
  return _.find(flattedTree.value, node => node.id === _modelValue.value)
})

// 判断当前选择是否仍存在于可访问资源树中
const exist = computed(() => {
  if (_modelValue.value) {
    if (selectedNode.value === undefined) {
      return false
    }
  }
  return true
})

// 输入框展示当前资源名称；资源缺失时展示明确失效提示
const selectedNodeName = computed(() => {
  if (!exist.value) {
    return sourceName.value + '不存在'
  }
  return selectedNode.value?.name
})

// 表单模型只承载名称字段，配合自定义校验提示资源失效
const form = computed(() => {
  return { name: selectedNodeName.value }
})

// 校验规则用于阻止保存已删除或无权限的数据集引用
const rules = ref([
  {
    validator: (...params) => {
      if (!exist.value) {
        params[2](new Error())
      } else {
        params[2]()
      }
    },
    trigger: ['change', 'blur']
  }
])

// 将目录树展开为扁平列表，保留原节点结构供后续选择使用
function flatTree(tree: Tree[] = []) {
  let result = _.cloneDeep(tree)
  _.forEach(tree, node => {
    if (node.children && node.children.length > 0) {
      result = _.union(result, flatTree(node.children))
    }
  })
  return result
}
// 向父组件通知资源切换，父级负责刷新字段、图表配置和联动状态
const onDatasetChange = val => {
  emits('onDatasetChange', val)
}
// 树节点过滤只匹配名称，空关键字保留全部节点
const filterNode = (value: string, data: Tree) => {
  if (!value) return true
  return data.name?.includes(value)
}

// 手动刷新资源树，供弹窗刷新按钮和外部事件复用
const refresh = () => {
  initDataset()
}
// 打开新增数据集或数据源窗口，实际创建流程由父级承接
const addDataset = () => {
  emits('addDsWindow')
}

// 选择器弹窗引用，用于选中叶子节点后主动关闭
const datasetSelectorPopover = ref()

// 只允许叶子资源被选中；变化时先通知父级，再写回双向绑定并关闭弹窗
const dsClick = (data: Tree) => {
  if (data.leaf) {
    if (_modelValue.value !== data.id) {
      onDatasetChange(data.id)
    }
    // 选中值写回后，输入框和表单校验会自动使用新的资源名称
    _modelValue.value = data.id
    // 叶子节点选择完成即收起弹窗，避免用户继续误点目录节点
    datasetSelectorPopover.value?.hide()
  }
}
// 弹窗显隐状态用于驱动输入框右侧箭头方向
const _popoverShow = ref(false)
// 弹窗打开时标记展开状态
function onPopoverShow() {
  _popoverShow.value = true
}
// 弹窗关闭时恢复折叠状态
function onPopoverHide() {
  _popoverShow.value = false
}

// 暴露给父级按节点编号读取树节点，常用于编辑器回显资源信息
function getNode(nodeId: number) {
  return datasetSelector?.value?.getNode(nodeId)
}

// 特定组件允许清空数据集绑定，用于纯文本或图片组等非强依赖数据组件
const clearShow = computed(
  () =>
    props.sourceType === 'dataset' &&
    dvMainStore.curComponent &&
    ['rich-text', 'picture-group'].includes(dvMainStore.curComponent.innerType)
)

// 清空当前数据集绑定，并通知图表轴和下钻字段清理关联配置
const handleClear = e => {
  e.preventDefault()
  e.stopPropagation()
  dsClick({ leaf: true, id: null } as Tree)
  useEmitt().emitter.emit('clear-remove', ['xAxis', 'yAxis', 'drillFields'])
}

// 聚焦时检查当前组织是否和资源树缓存一致，防止跨组织访问历史资源
const handleFocus = () => {
  if (
    props.sourceType === 'dataset' &&
    userStore.getOid &&
    wsCache.get('user.oid') &&
    userStore.getOid !== wsCache.get('user.oid')
  ) {
    orgCheck.value = false
  } else {
    orgCheck.value = true
  }
}

// 对外暴露节点读取能力，方便编辑器其他区域复用当前树实例
defineExpose({ getNode })
// 嵌入式环境下不展示新增入口，避免宿主页面越权创建资源
const appStore = useAppStoreWithOut()
// 产品内嵌和 iframe 都按嵌入式模式处理新增入口
const isCrestBi = computed(() => appStore.getIsCrestBi || appStore.getIsIframe)
// 挂载后加载资源树，并监听外部保存应用后触发的资源刷新事件
onMounted(() => {
  initDataset()
  useEmitt({
    name: 'refresh-dataset-selector',
    callback: () => refresh()
  })
})
</script>

<template>
  <div>
    <el-popover
      ref="datasetSelectorPopover"
      trigger="click"
      placement="bottom-start"
      :width="320"
      popper-class="customDatasetSelect"
      :show-arrow="false"
      @show="onPopoverShow"
      @hide="onPopoverHide"
      :disabled="disabled"
      :effect="themes"
      :offset="4"
    >
      <template #reference>
        <el-form ref="formRef" :model="form">
          <el-form-item prop="name" :rules="rules">
            <el-input
              :effect="themes"
              v-model="selectedNodeName"
              class="data-set-dark"
              @focus="handleFocus"
              :disabled="disabled"
              :placeholder="selectSource"
            >
              <template #suffix>
                <el-icon
                  v-show="!disabled"
                  class="input-arrow-icon"
                  :class="{ reverse: _popoverShow }"
                >
                  <ArrowDown />
                </el-icon>
                <el-icon
                  v-show="!disabled"
                  v-if="clearShow"
                  class="input-custom-clear-icon"
                  @click="handleClear"
                >
                  <CircleClose />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
        </el-form>
      </template>
      <template #default>
        <el-container :class="themes">
          <el-header>
            <div class="m-title" :class="{ dark: themes === 'dark' }">
              <div>{{ sourceName }}</div>
              <el-button type="primary" link class="refresh-btn" @click="refresh">
                {{ t('commons.refresh') }}
              </el-button>
            </div>
            <el-input
              :effect="themes"
              v-model="searchStr"
              :placeholder="t('dataset.search')"
              :prefix-icon="Search"
              clearable
            />
          </el-header>
          <el-main :class="{ dark: themes === 'dark' }">
            <el-scrollbar max-height="252px" always>
              <div class="m-loading" v-if="loadingDatasetTree" v-loading="loadingDatasetTree"></div>
              <div class="empty-info" v-if="showEmptyInfo">{{ emptyMsg }}</div>
              <!--          <div class="empty-info" v-if="showEmptySearchInfo">暂无相关数据</div>-->
              <el-tree
                :class="{ dark: themes === 'dark' }"
                v-if="showTree"
                ref="datasetSelector"
                node-key="id"
                :data="computedTree"
                :teleported="false"
                :props="dsSelectProps"
                :render-after-expand="false"
                filterable
                @node-click="dsClick"
                :filter-node-method="filterNode"
                empty-text="暂无相关数据"
              >
                <template #default="{ node, data }">
                  <div
                    class="tree-row-item"
                    :title="node.label"
                    :class="{ dark: themes === 'dark', active: _modelValue === data.id }"
                  >
                    <div class="m-icon">
                      <el-icon v-if="!data.leaf">
                        <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                      </el-icon>
                      <el-icon v-if="data.leaf">
                        <Icon name="icon_dataset"><icon_dataset class="svg-icon" /></Icon>
                      </el-icon>
                    </div>
                    {{ node.label }}

                    <el-icon class="checked-item" v-if="_modelValue === data.id">
                      <Icon name="icon_done_outlined"><icon_done_outlined class="svg-icon" /></Icon>
                    </el-icon>
                  </div>
                </template>
              </el-tree>
            </el-scrollbar>
          </el-main>
          <el-footer v-if="!isCrestBi">
            <div class="footer-container">
              <el-button
                type="primary"
                :icon="Plus"
                link
                class="add-btn"
                @click="addDataset"
                v-permission="sourceType === 'datasource' ? ['datasource'] : ['dataset']"
              >
                {{ newSource }}
              </el-button>
            </div>
          </el-footer>
        </el-container>
      </template>
    </el-popover>
  </div>
</template>

<style scoped lang="less">
.ed-input--dark.data-set-dark {
  :deep(.ed-input__wrapper) {
    background-color: #1a1a1a;
  }
}
:deep(.ed-input__wrapper) {
  cursor: pointer;
  padding: 1px 11px;

  .ed-input__inner {
    cursor: pointer;
    font-size: 12px;
  }
}
:deep(.ed-form-item) {
  margin-bottom: 0;
}
:deep(.ed-form-item.is-error .ed-input__wrapper) {
  input {
    color: var(--ed-color-danger);
  }
}
</style>

<style lang="less">
.input-custom-clear-icon {
  font-size: 14px;
}
.input-arrow-icon {
  font-size: 16px;
  transform: rotateZ(0);
  transition: transform var(--ed-transition-duration);

  &.reverse {
    transform: rotateZ(-180deg);
  }
}
.customDatasetSelect {
  --ed-popover-padding: 0 !important;
  max-height: 356px;

  .ed-container {
    max-height: 356px;
    &.dark {
      background: #292929;
    }

    .ed-header {
      --ed-header-height: 68px;
      --ed-header-padding: 0 11px;

      .m-title {
        width: 100%;
        display: flex;
        flex-direction: row;
        justify-content: space-between;
        align-items: center;
        height: 28px;
        padding-top: 8px;

        color: #1f2329;
        font-size: 12px;
        font-style: normal;
        font-weight: 500;
        line-height: 20px;

        .refresh-btn {
          font-size: 12px;
          font-weight: 400;
          cursor: pointer;
          min-width: 30px;
          min-width: 30px;
        }

        &.dark {
          color: #ebebeb;
        }
      }

      .ed-input {
        padding: 4px 0;
        font-size: 12px;
      }
    }

    .ed-footer {
      --ed-footer-height: 36px;
      --ed-footer-padding: 0 11px;
      border-top: rgba(31, 35, 41, 0.15) 1px solid;

      .footer-container {
        height: calc(100% - 3px);
        display: flex;
        flex-direction: row;
        align-items: center;
      }

      .add-btn {
        font-size: 12px;
        font-weight: 400;
      }
    }

    .ed-main {
      --ed-main-padding: 0;
      overflow-x: hidden;

      .empty-info {
        color: #646a73;
        font-size: 12px;
        font-style: normal;
        font-weight: 400;
        line-height: 20px;
        text-align: center;
      }

      .m-loading {
        width: 100%;
        height: 80px;
        .ed-loading-mask {
          background-color: transparent;
        }
      }

      &.dark {
        background-color: #292929;
        color: #ebebeb;

        .empty-info {
          color: #a6a6a6;
        }
      }

      .ed-tree__empty-block {
        position: unset;
        color: #646a73;
        font-size: 12px;
        font-style: normal;
        font-weight: 400;
        line-height: 20px;
        min-height: 20px;
        text-align: start;
        .ed-tree__empty-text {
          position: inherit;
          transform: inherit;
          color: inherit;
          font-size: inherit;
          padding: 0 11px;
        }
      }

      .ed-tree {
        &.dark {
          color: #ebebeb;
          background-color: #292929;

          .ed-tree__empty-block {
            color: #a6a6a6;
          }

          .ed-tree-node__expand-icon {
            color: #a6a6a6;
            &.is-leaf {
              color: transparent;
            }
          }

          .ed-tree-node__content:hover {
            background: rgba(235, 235, 235, 0.1);
          }
          .ed-tree-node:not(.is-effect):focus > .ed-tree-node__content {
            background: rgba(235, 235, 235, 0.1);
          }
        }
      }

      .tree-row-item {
        display: block;
        overflow-x: hidden;
        text-overflow: ellipsis;
        word-break: break-all;
        white-space: nowrap;
        font-size: 12px;
        font-style: normal;
        font-weight: 400;
        line-height: 20px;

        padding-right: 11px;

        &::-webkit-scrollbar {
          display: none;
        }

        .m-icon {
          margin-right: 4px;
          font-size: 16px;
          height: 20px;
          display: inline-block;
          vertical-align: bottom;
        }

        &.active {
          color: var(--ed-color-primary);
          padding-right: 30px;
        }
        .checked-item {
          position: absolute;
          right: 10px;
          padding-top: 2px;
          color: var(--ed-color-primary);
          font-size: 16px;
        }
      }
    }
  }

  .ed-button.is-link {
    font-size: 12px;
    font-weight: 400;
    padding: 4px;

    &:not(.is-disabled):focus,
    &:not(.is-disabled):hover {
      color: var(--ed-color-primary);
      border-color: transparent;
      background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
    }
    &:not(.is-disabled):active {
      color: var(--ed-color-primary);
      border-color: transparent;
      background-color: var(--ed-color-primary-33, rgba(59, 130, 246, 0.2));
    }
  }
}
</style>
