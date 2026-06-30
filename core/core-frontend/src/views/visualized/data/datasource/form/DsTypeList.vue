<script lang="ts" setup>
import { shallowRef, PropType, computed, watch } from 'vue'
import { dsTypes, typeList, nameMap } from './option'
import Icon from '@/components/icon-custom/src/Icon.vue'
import { iconDatasourceMap } from '@/components/icon-group/datasource-list'
import { useI18n } from '@/hooks/web/useI18n'

export type DsType = 'OLTP' | 'OLAP' | 'DL' | 'OTHER' | 'LOCAL' | 'latestUse' | 'all'
// 数据源类型列表的筛选条件和最近使用类型
const props = defineProps({
  currentType: {
    type: String as PropType<DsType>,
    default: 'OLTP'
  },
  filterText: {
    type: String,
    default: ''
  },
  latestUseTypes: {
    type: Array as PropType<string[]>,
    default: () => []
  },
  allowedDatasourceTypes: {
    type: Array as PropType<string[]>,
    default: () => ['obOracle', 'Excel', 'ExcelRemote', 'API']
  }
})
// 数据源分类标题使用当前语言环境
const { t } = useI18n()

// 按目录分组后的数据源类型列表
const databaseList = shallowRef([])
// 根据当前分类、搜索文本和最近使用记录生成可展示列表
const currentTypeList = computed(() => {
  const allowedTypeSet = new Set(props.allowedDatasourceTypes)
  if (props.currentType == 'all') {
    return typeList
      .map((ele, index) => {
        return {
          name: nameMap[ele],
          dbList: databaseList.value[index].filter(ele =>
            ele.name.toLowerCase().includes(props.filterText.trim())
          )
        }
      })
      .filter(ele => ele.dbList.length)
  }
  if (props.currentType === 'latestUse') {
    let catalogList = []
    let dstypes = []
    props.latestUseTypes.forEach(type => {
      if (!allowedTypeSet.has(type)) {
        return
      }
      dsTypes.forEach(item => {
        if (item.type === type && catalogList.indexOf(item.catalog) === -1) {
          catalogList.push(item.catalog)
        }
      })
    })
    let dbList = []
    catalogList.forEach(catalog => {
      props.latestUseTypes.forEach(type => {
        if (!allowedTypeSet.has(type)) {
          return
        }
        dsTypes.forEach(item => {
          if (item.type === type && item.catalog === catalog) {
            dbList.push(item)
          }
        })
      })
    })
    dbList = dbList.filter(ele => ele.name.toLowerCase().includes(props.filterText.trim()))
    dstypes.push({ name: t('data_source.recently_created'), dbList })
    return dstypes
  }
  const index = typeList.findIndex(ele => props.currentType === ele)
  return [
    {
      name: nameMap[props.currentType],
      dbList: (databaseList.value[index] || []).filter(ele =>
        ele.name.toLowerCase().includes(props.filterText.trim())
      )
    }
  ]
})
// 初始化内置数据源类型，并按名称排序
const datasourceTypes = () => {
  const arr = [[], [], [], [], []]
  dsTypes
    .filter(item => props.allowedDatasourceTypes.includes(item.type))
    .forEach(item => {
      const index = typeList.findIndex(ele => ele === item.catalog)
      if (index !== -1) {
        arr[index].push(item)
      }
    })
  databaseList.value = arr.map(ele => {
    return ele.sort((a, b) => {
      return a.name.toLowerCase().charCodeAt(0) - b.name.toLowerCase().charCodeAt(0)
    })
  })
}
watch(() => props.allowedDatasourceTypes, datasourceTypes, { immediate: true })
// 将插件数据源追加到对应目录下
const loadDsPlugin = data => {
  data.forEach(item => {
    const { name, category, type, icon, extraParams, staticMap } = item
    if (!props.allowedDatasourceTypes.includes(type)) {
      return
    }
    const node = {
      name,
      catalog: category,
      type,
      icon,
      extraParams,
      isPlugin: true,
      staticMap
    }
    const index = typeList.findIndex(ele => ele === node.catalog)
    if (index !== -1) {
      let copiedArr = JSON.parse(JSON.stringify(databaseList.value))
      copiedArr[index].push(node)
      databaseList.value = copiedArr
    }
  })
}

// 选择数据源类型时通知父级
const emits = defineEmits(['selectDsType'])
// 触发数据源类型选择事件
const selectDs = ({ type }) => {
  emits('selectDsType', type)
}
</script>

<template>
  <div class="ds-type-list">
    <template v-for="ele in currentTypeList" :key="ele.name">
      <div class="title-form_primary">
        {{ ele.name }}
      </div>
      <div class="item-container">
        <div v-for="db in ele.dbList" :key="db.type" class="db-card" @click="selectDs(db)">
          <el-icon class="icon-border">
            <Icon v-if="db['isPlugin']" :static-content="db.icon"></Icon>
            <Icon v-else
              ><component :is="iconDatasourceMap[db.type]" class="svg-icon"></component
            ></Icon>
          </el-icon>
          <p class="db-name">{{ db.name }}</p>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="less" scoped>
.ds-type-list {
  width: 100%;
  position: relative;
  display: flex;
  width: 100%;
  flex-wrap: wrap;

  .title-form_primary {
    margin-bottom: 16px;
  }

  .item-container {
    display: flex;
    width: calc(100% + 16px);
    flex-wrap: wrap;
    margin-left: -16px;
  }

  .db-card {
    height: 64px;
    width: 266px;
    display: flex;
    align-items: center;
    background: #ffffff;
    border: 1px solid #dee0e3;
    border-radius: 6px;
    margin-bottom: 16px;
    margin-left: 16px;
    padding: 16px;
    cursor: pointer;

    .icon-border {
      margin-right: 12px;
      font-size: 32px;
    }

    &:hover {
      box-shadow: 0px 6px 24px rgba(31, 35, 41, 0.08);
    }
  }

  .marLeft {
    margin-left: 0;
  }
}
</style>
