<template>
  <el-form size="small" label-position="left" :label-width="14">
    <el-row :gutter="8" v-for="(x, i) in positionKeysGroup" :key="i">
      <el-col :span="12" v-for="({ key, label, min, max, step }, j) in x" :key="j">
        <el-form-item class="form-item" :class="'form-item-' + themes" :label="label">
          <el-input-number
            :effect="themes"
            :disabled="curComponent['isLock']"
            :min="min"
            :max="max"
            :step="step"
            v-model="positionMounted[key]"
            @change="onPositionChange(key)"
            controls-position="right"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item
      v-if="curComponent && curComponent.component === 'Tabs'"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="curComponent['resizeInnerKeep']"
        @change="snapshotChange"
      >
        {{ t('visualization.keep_size') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes">
      <el-checkbox
        v-if="curComponent"
        size="small"
        :effect="themes"
        v-model="curComponent['maintainRadio']"
        @change="maintainRadioChange"
      >
        {{ t('visualization.keep_ratio') }}
      </el-checkbox>
    </el-form-item>
    <el-row v-if="curComponent && curComponent.multiDimensional">
      <el-col :span="12">
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="curComponent.multiDimensional.enable"
            @change="multiDimensionalChange"
          >
            {{ t('visualization.rotation_3d') }}
          </el-checkbox>
        </el-form-item>
        <template v-if="curComponent.multiDimensional.enable">
          <el-form-item class="form-item" :class="'form-item-' + themes" label="X">
            <el-input-number
              :effect="themes"
              :disabled="curComponent['isLock']"
              :min="-360"
              :max="360"
              :step="1"
              v-model="curComponent.multiDimensional.x"
              @change="multiDimensionalChange"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item class="form-item" :class="'form-item-' + themes" label="Y">
            <el-input-number
              :effect="themes"
              :disabled="curComponent['isLock']"
              :min="-360"
              :max="360"
              :step="1"
              v-model="curComponent.multiDimensional.y"
              @change="multiDimensionalChange"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item class="form-item" :class="'form-item-' + themes" label="Z">
            <el-input-number
              :effect="themes"
              :disabled="curComponent['isLock']"
              :min="-360"
              :max="360"
              :step="1"
              v-model="curComponent.multiDimensional.z"
              @change="multiDimensionalChange"
              controls-position="right"
            />
          </el-form-item>
        </template>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { positionData } from '@/utils/attr'
import { storeToRefs } from 'pinia'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import _ from 'lodash'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { groupSizeStyleAdaptor, groupStyleRevert } from '@/utils/style'
import { isGroupCanvas, isTabCanvas } from '@/utils/canvasUtils'
import { useI18n } from '@/hooks/web/useI18n'
// 保存当前组件所属画布的父节点
const parentNode = ref(null)
// 保存当前组件所属画布 ID
const canvasId = ref('canvas-main')
const snapshotStore = snapshotStoreWithOut()
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const { curComponent, canvasStyleData } = storeToRefs(dvMainStore)
// 保存面板中展示的宽高和坐标值
const positionMounted = ref({
  width: 0,
  height: 0,
  top: 0,
  left: 0
})

withDefaults(
  defineProps<{
    themes: EditorTheme
  }>(),
  { themes: 'dark' }
)

// 过滤当前组件支持编辑的位置字段
const positionKeys = computed(() => {
  if (curComponent.value) {
    const curComponentStyleKeys = Object.keys(curComponent.value.style)
    return positionData.filter(item => curComponentStyleKeys.includes(item.key))
  } else {
    return []
  }
})

// 将位置字段拆分为两列展示
const positionKeysGroup = computed(() => {
  const _list = []
  _.forEach(positionKeys.value, (x, i) => {
    const index = i % 2
    if (_list[index] === undefined) {
      _list[index] = []
    }
    _list[index].push(x)
  })
  return _list
})

// 同步单个位置字段变更，并处理等比缩放和分组画布适配
const onPositionChange = key => {
  if (!positionMounted.value[key]) {
    positionMounted.value[key] = 0
  }
  if (curComponent.value.maintainRadio) {
    curComponent.value.style[key] = Math.ceil(
      (positionMounted.value[key] * canvasStyleData.value.scale) / 100
    )
    if (key === 'width') {
      curComponent.value.style['height'] =
        curComponent.value.style['width'] / curComponent.value.aspectRatio
      positionMounted.value['height'] = Math.round(
        positionMounted.value['width'] / curComponent.value.aspectRatio
      )
    } else if (key === 'height') {
      curComponent.value.style['width'] =
        curComponent.value.style['height'] * curComponent.value.aspectRatio
      positionMounted.value['width'] = Math.round(
        positionMounted.value['height'] * curComponent.value.aspectRatio
      )
    }
  } else {
    curComponent.value.style[key] = (positionMounted.value[key] * canvasStyleData.value.scale) / 100
  }

  // 分组或 Tab 内部画布需要把定位值还原到分组样式中
  if (isGroupCanvas(canvasId.value) || isTabCanvas(canvasId.value)) {
    groupStyleRevert(curComponent.value, {
      width: parentNode.value.offsetWidth,
      height: parentNode.value.offsetHeight
    })
  }

  if (['Group', 'Tabs'].includes(curComponent.value.component)) {
    // 分组和 Tab 组件需要同步内部组件尺寸
    groupSizeStyleAdaptor(curComponent.value)
  }

  snapshotStore.recordSnapshotCache('onPositionChange')
}

// 开启等比缩放时记录当前组件宽高比例
const maintainRadioChange = () => {
  curComponent.value.aspectRatio = curComponent.value.style.width / curComponent.value.style.height
  snapshotStore.recordSnapshotCache('maintainRadioChange')
}
// 记录 3D 旋转配置变更
const multiDimensionalChange = () => {
  snapshotStore.recordSnapshotCache('multiDimensionalChange')
}

// 记录普通位置设置变更
const snapshotChange = () => {
  snapshotStore.recordSnapshotCache('snapshotChange')
}

// 根据当前组件样式初始化位置面板值
const positionInit = () => {
  if (curComponent.value) {
    canvasId.value = curComponent.value.canvasId
    parentNode.value = document.querySelector('#editor-' + canvasId.value)
    Object.keys(positionMounted.value).forEach(key => {
      positionMounted.value[key] = Math.round(
        (curComponent.value.style[key] * 100) / canvasStyleData.value.scale
      )
    })
    if (curComponent.value.maintainRadio) {
      positionMounted.value.width = Math.round(
        positionMounted.value.height * curComponent.value.aspectRatio
      )
    }
  }
}

// 监听当前组件变化并刷新位置面板
watch(
  () => curComponent.value,
  () => {
    positionInit()
  },
  {
    deep: true,
    immediate: true
  }
)
</script>

<style lang="less" scoped>
:deep(.ed-form-item) {
  display: flex !important;
  .ed-form-item__label {
    line-height: 24px;
    margin: 3px 0 !important;
  }
}
</style>
