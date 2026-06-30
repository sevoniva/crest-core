<template>
  <div class="testcase-template">
    <div
      :class="[
        {
          ['template-img-active']: itemActive
        },
        'template-img'
      ]"
      :style="itemStyle"
      @click.stop="setBoard"
    >
      <Icon class-name="svg-background"
        ><component
          :style="{ color: commonBackground.innerImageColor }"
          class="svg-icon svg-background"
          :is="iconBoardMap[mainIconClass]"
        ></component
      ></Icon>
    </div>
    <span class="demonstration">{{ template.name }}</span>
  </div>
</template>

<script setup lang="ts">
import Icon from '@/components/icon-custom/src/Icon.vue'
import { computed, toRefs } from 'vue'
import { hexColorToRGBA } from '@/views/chart/components/js/util'
import { iconBoardMap } from '@/components/icon-group/board-list'

// 背景模板项属性，包含模板信息和公共背景配置
const props = defineProps({
  template: {
    type: Object,
    default() {
      return {}
    }
  },
  commonBackground: {
    type: Object,
    required: true
  }
})

// 保持模板和背景配置的响应式引用
const { template, commonBackground } = toRefs(props)
// 背景模板切换时通知父级刷新边框配置
const emits = defineEmits(['borderChange'])

// 根据背景色配置生成模板预览样式
const itemStyle = computed(() => {
  if (commonBackground.value.backgroundColorSelect) {
    return {
      'background-color': hexColorToRGBA(commonBackground.value.color, commonBackground.value.alpha)
    }
  } else {
    return {}
  }
})

// 从模板 SVG 路径中解析图标组件名称
const mainIconClass = computed(() => {
  return template.value.url.replace('board/', '').replace('.svg', '')
})
// 判断当前模板是否为已选背景图
const itemActive = computed(() => {
  return commonBackground.value && commonBackground.value.innerImage === template.value.url
})

// 设置当前背景模板并通知父级
const setBoard = () => {
  commonBackground.value.innerImage = template.value.url
  emits('borderChange')
}
</script>

<style scoped lang="less">
.testcase-template {
  display: inline-block;
  margin: 5px 0px;
  width: 90px;
}

.demonstration {
  display: block;
  font-size: 8px;
  color: gray;
  text-align: center;
  margin: 10px auto;
  width: 110px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.template-img {
  position: relative;
  height: 70px;
  width: 110px;
  margin: 0 auto;
  box-sizing: border-box;
  border-radius: 3px;
}

.template-img:hover {
  border: solid 1px #4b8fdf;
  border-radius: 3px;
  color: deepskyblue;
  cursor: pointer;
}

.template-img > i {
  display: none;
  float: right;
  color: gray;
  margin: 2px;
}

.template-img > i:hover {
  color: red;
}

.template-img:hover > .el-icon-error {
  display: inline;
}

.template-img:hover > .el-icon-edit {
  display: inline;
}

.template-img-active {
  border: solid 1px red;
  border-radius: 3px;
  color: deepskyblue;
}

.svg-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100% !important;
  height: 100% !important;
}
:deep(.ed-row) {
  flex-direction: column;
}
</style>
