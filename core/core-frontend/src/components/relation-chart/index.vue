<script lang="ts" setup>
import { ref, reactive, nextTick } from 'vue'
import { cloneDeep } from 'lodash-es'
import {
  datasourceRelationship as datasourceRelation,
  datasetRelationship as datasetRelation
} from '@/api/relation/index'
import RelationGraphView from './GraphView.vue'
// 控制血缘关系抽屉的显示状态
const relationDrawer = ref(false)
// 保存图谱容器的实时尺寸，供关系图组件按抽屉区域渲染
const chartSize = reactive({
  height: 0,
  width: 0
})
// 读取抽屉内容区尺寸并同步给图谱渲染参数
const getChartSize = () => {
  const dom = document.querySelector('.relation-drawer_content')
  if (!dom) return
  Object.assign(chartSize, {
    height: dom.offsetHeight + 'px',
    width: dom.offsetWidth + 'px'
  })
}

// 缓存当前关系接口返回的图谱数据
let resRef = null
// 加载指定数据源的血缘关系图数据
const datasourceRelationship = id => {
  datasourceRelation(id)
    .then(res => {
      resRef = cloneDeep(res || {})
    })
    .finally(() => {
      tableLoading.value = false
    })
}
// 加载指定数据集的血缘关系图数据
const datasetRelationship = id => {
  datasetRelation(id)
    .then(res => {
      resRef = cloneDeep(res || {})
    })
    .finally(() => {
      tableLoading.value = false
    })
}

// 记录本次打开关系图时的查询类型、编号和展示名称
const current = {
  queryType: '',
  num: '',
  label: ''
}
// 标识关系图数据是否仍在加载中
const tableLoading = ref(false)
// 对外入口：打开抽屉并按查询类型拉取对应血缘数据
const getChartData = obj => {
  Object.assign(current, obj || {})
  const { queryType, num } = current
  tableLoading.value = true
  relationDrawer.value = true
  nextTick(() => {
    getChartSize()
    switch (queryType) {
      case 'datasource':
        datasourceRelationship(num)
        break
      case 'dataset':
        datasetRelationship(num)
        break
      default:
        break
    }
  })
}

defineExpose({
  getChartData
})
</script>

<template>
  <el-drawer
    title="血缘关系图"
    v-model="relationDrawer"
    modal-class="crest-relation-drawer"
    size="1200px"
    direction="rtl"
  >
    <div class="relation-drawer_content">
      <RelationGraphView :graph="resRef" :loading="tableLoading" />
    </div>
  </el-drawer>
</template>

<style lang="less">
.crest-relation-drawer {
  .ed-drawer__body {
    padding-bottom: 24px;
  }
  .relation-drawer_content {
    border: 1px solid #dee0e3;
    width: 100%;
    height: 100%;
    background: #f5f6f7;
    border-radius: 6px;
    position: relative;
  }
}
</style>
