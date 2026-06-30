<script lang="ts" setup>
import icon_drag_outlined from '@/assets/svg/icon_drag_outlined.svg'
import draggable from 'vuedraggable'
import { getFieldData, getDrillFieldData } from '@/api/chart'
import { reactive, watch, ref } from 'vue'
import { useCache } from '@/hooks/web/useCache'

const { wsCache } = useCache()
// 控制自定义排序列表加载状态
const loading = ref(false)

// 保存可拖拽排序项
const state = reactive({
  sortList: []
})

// 定义自定义排序编辑器接收的图表、字段和原始排序参数
const props = defineProps({
  chart: {
    type: Object,
    required: true
  },
  field: {
    type: Object,
    required: true
  },
  fieldType: {
    type: String,
    required: true
  },
  originSortList: {
    type: Array,
    default: () => [],
    required: false
  }
})

// 声明排序变化时向父组件派发的事件
const emit = defineEmits(['onSortChange'])

// 监听图表配置变化并重新加载排序候选值
watch(
  () => props.chart,
  () => {
    init()
  },
  { deep: true }
)

// 加载字段值并按已有排序结果初始化拖拽列表
const init = () => {
  loading.value = true
  const chart = props.chart
  if (!chart.chartExtRequest) {
    chart.chartExtRequest = {
      user: wsCache.get('user.uid')
    }
  }
  const param = {
    fieldId: props.field.id,
    fieldType: props.fieldType,
    data: chart
  }
  let reqMethod = props.fieldType === 'drillFields' ? getDrillFieldData : getFieldData
  reqMethod(param)
    .then(response => {
      const strArr = response.data
      if (props.originSortList?.length) {
        const tmp = []
        props.originSortList.forEach(ele => {
          const index = strArr.findIndex(item => item === ele)
          if (index !== -1) {
            tmp.push(strArr[index])
            strArr.splice(index, 1)
          }
        })
        strArr.unshift(...tmp)
      }
      state.sortList = strArr
        .filter(ele => ele?.trim())
        .map(ele => {
          return transStr2Obj(ele)
        })
      onUpdate()
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}
// 将当前拖拽列表转换为字符串数组并派发
const onUpdate = () => {
  const strArr = state.sortList.map(ele => {
    return transObj2Str(ele)
  })
  emit('onSortChange', strArr)
}

// 将字段值转换为拖拽列表项对象
const transStr2Obj = str => {
  return { name: 'name', value: str }
}

// 将拖拽列表项对象转换回字段值
const transObj2Str = obj => {
  return obj.value
}

init()
</script>

<template>
  <el-scrollbar height="100%" max-height="599px">
    <draggable
      v-loading="loading"
      :list="state.sortList"
      animation="300"
      class="drag-list"
      item-key="name"
      @update="onUpdate"
    >
      <template #item="{ element }">
        <span :key="element.value" class="item-dimension" :title="element.value">
          <el-icon class="item-icon">
            <Icon name="icon_drag_outlined"><icon_drag_outlined class="svg-icon" /></Icon>
          </el-icon>
          <span class="item-span">
            {{ element.value }}
          </span>
        </span>
      </template>
    </draggable>
  </el-scrollbar>
</template>

<style lang="less" scoped>
.drag-list {
  height: 50vh;
}

.item-dimension {
  padding: 2px;
  margin: 2px;
  border: 1px solid #dee0e3;
  border-radius: 6px;
  text-align: left;
  color: #606266;
  background-color: white;
  display: flex;
  align-items: center;
  cursor: move;
}

.item-icon {
  font-size: 16px;
  margin: 0 4px;
  color: #646a73;
}

.item-span {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  color: #1f2329;

  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 22px;
}

.blackTheme .item-dimension {
  border: solid 1px;
  border-color: var(--TableBorderColor);
  color: var(--TextPrimary);
  background-color: var(--MainBG);
}

.item-dimension + .item-dimension {
  margin-top: 6px;
}

.item-dimension:hover {
  box-shadow: 0px 4px 8px 0px rgba(31, 35, 41, 0.1);
}

.blackTheme .item-dimension:hover {
  color: var(--Main);
  background: var(--ContentBG);
}
</style>
