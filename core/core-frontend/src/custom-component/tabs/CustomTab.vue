<template>
  <el-tabs :class="['component-tabs', ...tabClassName]" :style="tabStyle" v-bind="$attrs">
    <slot></slot>
  </el-tabs>
</template>

<script setup lang="ts">
import { computed } from 'vue'
// 定义组件入参，约束外部传入配置
const props = defineProps({
  hideTitle: Boolean,
  /* 颜色可以单词，如red；也可以是颜色值 */
  // 字体颜色
  fontColor: String,
  // 激活字体颜色
  activeColor: String,
  // 边框颜色 如果是none就无边框 如果是none Card类型激活的下滑线也消失
  borderColor: String,
  // 激活边框颜色 目前只针对card类型
  borderActiveColor: String,
  // 样式类型  radioGroup只在Card类型有效, 同时必须给borderColor borderActiveColor
  styleType: {
    type: String,
    default: '',
    validator: (val: string) => ['', 'radioGroup'].includes(val)
  }
})

// 根据当前配置计算界面样式
const tabStyle = computed(() => [
  { '--crest-font-color': props.fontColor },
  { '--crest-active-color': props.activeColor },
  { '--crest-border-color': props.borderColor },
  { '--crest-border-active-color': props.borderActiveColor }
])
// 根据当前配置计算界面样式
const tabClassName = computed(() =>
  props.styleType
    ? [props.styleType, props.fontColor && 'fontColor', props.activeColor && 'activeColor']
    : [
        props.fontColor && 'fontColor',
        props.activeColor && 'activeColor',
        noBorder.value ? 'noBorder' : props.borderColor && 'borderColor',
        props.borderActiveColor && 'borderActiveColor',
        props.hideTitle && 'no-header'
      ]
)

// 衔接当前组件交互和状态同步
const noBorder = computed(() => props.borderColor === 'none')
</script>

<style lang="less">
.component-tabs {
  height: 100%;
  &.no-header {
    .ed-tabs__header {
      display: none;
    }
  }
  &.ed-tabs--card {
    > .ed-tabs__header {
      height: auto !important;
    }
  }
  &.fontColor {
    .ed-tabs__item {
      color: var(--crest-font-color);

      &.is-active {
        color: var(--el-color-primary);
      }

      &:hover {
        color: var(--el-color-primary);
      }
    }
  }

  &.activeColor {
    .ed-tabs__item {
      &.is-active {
        color: var(--crest-active-color);
      }

      &:hover {
        color: var(--crest-active-color);
      }
    }

    .ed-tabs__active-bar {
      height: 0px;
      background-color: var(--crest-active-color);
    }
  }

  // card样式的边框
  &.noBorder.ed-tabs--card {
    > .ed-tabs__header {
      border-bottom: none;

      .ed-tabs__nav {
        border: none;
      }

      .ed-tabs__item {
        border-left: none;
      }

      .ed-tabs__item.is-active {
        border-bottom: none;
      }
    }
  }

  &.borderActiveColor.ed-tabs--card {
    > .ed-tabs__header .ed-tabs__item.is-active {
      border-bottom-color: var(--crest-border-active-color);
    }
  }

  &.borderColor.ed-tabs--card {
    > .ed-tabs__header {
      border-bottom-color: var(--crest-border-color);

      .ed-tabs__nav {
        border-color: var(--crest-border-color);
      }

      .ed-tabs__item {
        border-left-color: var(--crest-border-color);
      }
    }

    .ed-tabs__item {
      &.is-active {
        color: var(--crest-active-color);
      }

      &:hover {
        color: var(--crest-active-color);
      }
    }

    .ed-tabs__active-bar {
      height: 0px;
      background-color: var(--crest-active-color);
    }
  }

  // 简洁样式的边框
  &.noBorder {
    .ed-tabs__nav-wrap::after {
      background: none;
    }
  }

  &.borderColor {
    .ed-tabs__nav-wrap::after {
      background: var(--crest-border-color);
    }
  }

  // radioGroup 类型
  &.radioGroup.ed-tabs--card {
    > .ed-tabs__header {
      border-bottom: none;

      .ed-tabs__nav {
        border: none;
      }

      .ed-tabs__item {
        border: 1px solid var(--crest-border-color);
        border-right: 0;

        &:first-child {
          border-left: 1px solid var(--crest-border-color);
          border-radius: 6px 0 0 4px;
        }

        &:last-child {
          border-right: 1px solid var(--crest-border-color);
          border-radius: 0 4px 4px 0;
        }

        &.is-active {
          border: 1px solid var(--crest-border-active-color);

          & + .ed-tabs__item {
            border-left: 0;
          }
        }
      }
    }
  }
}
</style>
