<script setup lang="ts">
import { getStyle } from '@/utils/style'
import eventBus from '@/utils/eventBus'
import { ref, toRefs, computed, nextTick, onMounted, onBeforeUnmount, PropType } from 'vue'
import findComponent from '@/utils/components'
import { downloadCanvas2 } from '@/utils/imgUtils'
import ComponentEditBar from '@/components/visualization/ComponentEditBar.vue'
import ComponentSelector from '@/components/visualization/ComponentSelector.vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import Board from '@/components/visual-board/Board.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { activeWatermarkCheckUser, removeActiveWatermark } from '@/components/watermark/watermark'
import { isMobile } from '@/utils/utils'
import { isMainCanvas } from '@/utils/canvasUtils'
import CrestPreviewPopDialog from '@/components/visualization/CrestPreviewPopDialog.vue'
import Icon from '../../icon-custom/src/Icon.vue'
import replaceOutlined from '@/assets/svg/icon_replace_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import {
  isBlurBgEnabled,
  getBlurBgStyle,
  getComponentBackgroundStyle
} from '@/utils/backgroundStyleUtils'
// 组件包装器中的按钮和导出文案翻译入口
const { t } = useI18n()

// 组件内容容器引用，用于定位编辑区域
const componentWrapperInnerRef = ref(null)
// 组件编辑工具栏引用，用于触发复用选择等操作
const componentEditBarRef = ref(null)
// 大屏主状态，保存当前选中组件和画布状态
const dvMainStore = dvMainStoreWithOut()
// 图片下载中的加载状态
const downLoading = ref(false)
// 普通组件样式只提取位置、尺寸和旋转属性
const commonFilterAttrs = ['width', 'height', 'top', 'left', 'rotate']
// 弹窗预览组件引用
const crestPreviewPopDialogRef = ref(null)
// 边框激活时需要额外保留边框相关样式
const commonFilterAttrsFilterBorder = [
  'width',
  'height',
  'top',
  'left',
  'rotate',
  'borderActive',
  'borderWidth',
  'borderRadius',
  'borderStyle',
  'borderColor'
]

// 组件包装器接收画布组件配置、展示场景和缩放信息
const props = defineProps({
  curStyle: {
    type: Object as PropType<Record<string, any>>
  },
  active: {
    type: Boolean,
    default: false
  },
  popActive: {
    type: Boolean,
    default: false
  },
  canvasStyleData: {
    type: Object as PropType<Record<string, any>>,
    required: false,
    default: () => ({})
  },
  canvasViewInfo: {
    type: Object as PropType<Record<string, any>>,
    required: false,
    default: () => ({})
  },
  dvInfo: {
    type: Object as PropType<Record<string, any>>,
    required: false,
    default: () => ({})
  },
  config: {
    type: Object as PropType<Record<string, any>>,
    required: false,
    default() {
      return {
        component: null,
        propValue: null,
        request: null,
        linkage: null,
        type: null,
        events: null,
        style: null,
        id: null,
        animations: null
      }
    }
  },
  viewInfo: {
    type: Object as PropType<Record<string, any>>,
    required: false
  },
  index: {
    required: false,
    type: [Number, String],
    default: 0
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  canvasId: {
    type: String,
    default: 'canvas-main'
  },
  // 仪表板刷新计时器
  searchCount: {
    type: Number,
    required: false,
    default: 0
  },
  scale: {
    type: Number,
    required: false,
    default: 100
  },
  isSelector: {
    type: Boolean,
    default: false
  },
  //图表渲染id后缀
  suffixId: {
    type: String,
    required: false,
    default: 'common'
  },
  // 字体
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  },
  optType: {
    type: String,
    required: false
  },
  // 画布滚动距离
  scrollMain: {
    type: Number,
    default: 0
  }
})
// 将常用入参转成响应式引用，供计算属性和事件处理复用
const {
  config,
  showPosition,
  index,
  canvasStyleData,
  canvasViewInfo,
  dvInfo,
  searchCount,
  scale,
  suffixId,
  scrollMain
} = toRefs(props)
// 动态组件实例引用
const component = ref(null)
// 组件包装器向外透传放大、数据集参数和点击事件
const emits = defineEmits(['userViewEnlargeOpen', 'datasetParamsInit', 'onPointClick'])
// 外层包装节点编号，和组件编号绑定以便 DOM 查询
const wrapperId = 'wrapper-outer-id-' + config.value.id

// 放大预览区域的内部节点编号
const viewDemoInnerId = computed(() => 'enlarge-inner-content-' + config.value.id)
// 将当前组件区域导出为图片，并在导出前后处理水印和地图准备事件
const htmlToImage = () => {
  useEmitt().emitter.emit('l7-prepare-picture', config.value.id)
  downLoading.value = true
  setTimeout(() => {
    const vueDom = document.getElementById(viewDemoInnerId.value)
    activeWatermarkCheckUser(viewDemoInnerId.value, 'canvas-main', scale.value / 100)
    downloadCanvas2('img', vueDom, t('chart.chart'), () => {
      // do callback
      removeActiveWatermark(viewDemoInnerId.value)
      downLoading.value = false
      useEmitt().emitter.emit('l7-unprepare-picture', config.value.id)
    })
  }, 1000)
}

// 处理组件内部鼠标按下，兼容复用、预览和移动端编辑场景
const handleInnerMouseDown = e => {
  // do setCurComponent
  if (showPositionActive.value.includes('multiplexing')) {
    componentEditBarRef.value.multiplexingCheckOut()
    e?.stopPropagation()
    e?.preventDefault()
  }
  if (
    (!['rich-text'].includes(config.value.innerType) &&
      ['popEdit', 'preview'].includes(showPositionActive.value)) ||
    dvMainStore.mobileInPc
  ) {
    onClick()
    if (e.target?.className?.includes?.('ed-input__inner')) return
    e?.stopPropagation()
    e?.preventDefault()
  }
}

// 选中当前组件，并同步编辑器点击状态
const onClick = () => {
  // 将当前点击组件的事件传播出去
  eventBus.emit('componentClick')
  dvMainStore.setInEditorStatus(true)
  dvMainStore.setClickComponentStatus(true)
  dvMainStore.setCurComponent({ component: config.value, index: index.value })
}

// 根据组件类型提取实际渲染所需的基础样式
const getComponentStyleDefault = style => {
  if (config.value.component.includes('Svg')) {
    return getStyle(style, [
      'top',
      'left',
      'width',
      'height',
      'rotate',
      'backgroundColor',
      'borderWidth',
      'borderStyle',
      'borderColor'
    ])
  } else {
    return getStyle(style, style.borderActive ? commonFilterAttrs : commonFilterAttrsFilterBorder)
  }
}

// 鼠标进入组件时通知画布高亮当前组件
const onMouseEnter = () => {
  eventBus.emit('v-hover', config.value.id)
}

// 判断组件是否启用模糊背景
const blurBgEnable = computed(() => {
  return isBlurBgEnabled(config.value.commonBackground)
})

// 计算模糊背景样式，并按深层缩放修正尺寸
const blurBgStyle = computed(() => {
  return getBlurBgStyle(config.value.commonBackground, deepScale.value)
})

// 计算组件公共背景样式，兼容用户视图和组合组件
const componentBackgroundStyle = computed(() => {
  if (config.value.commonBackground) {
    return getComponentBackgroundStyle(config.value.commonBackground, {
      scale: deepScale.value,
      isUserView: config.value.component === 'UserView',
      forceNoPadding: ['Group'].includes(config.value.component)
    })
  }
  return {}
})

/**
 * 判断公共背景是否使用内置 SVG 图片
 */
const svgInnerEnable = computed(() => {
  const { backgroundImageEnable, backgroundType, innerImage } = config.value.commonBackground
  return backgroundImageEnable && backgroundType === 'innerImage' && typeof innerImage === 'string'
})

/**
 * 获取公共背景内置 SVG 的资源名称
 */
const commonBackgroundSvgInner = computed(() => {
  if (svgInnerEnable.value) {
    return config.value.commonBackground.innerImage.replace('board/', '').replace('.svg', '')
  } else {
    return null
  }
})

/**
 * 计算 3D 旋转效果需要的插槽补偿样式
 */
const slotStyle = computed(() => {
  // 3d效果支持
  if (config.value['multiDimensional'] && config.value['multiDimensional']?.enable) {
    const width = config.value.style.width // 原始元素宽度
    const height = config.value.style.height // 原始元素高度
    const rotateX = config.value['multiDimensional'].x // 旋转X角度
    const rotateY = config.value['multiDimensional'].y // 旋转Y角度

    // 将角度转换为弧度
    const radX = (rotateX * Math.PI) / 180
    const radY = (rotateY * Math.PI) / 180

    // 计算旋转后新宽度和高度
    const newWidth = Math.abs(width * Math.cos(radY)) + Math.abs(height * Math.sin(radX))
    const newHeight = Math.abs(height * Math.cos(radX)) + Math.abs(width * Math.sin(radY))

    // 计算需要的 padding
    const paddingX = (newWidth - width) / 2
    const paddingY = (newHeight - height) / 2
    return {
      padding: `${paddingY}px ${paddingX}px`,
      transform: `rotateX(${config.value['multiDimensional'].x}deg) rotateY(${config.value['multiDimensional'].y}deg) rotateZ(${config.value['multiDimensional'].z}deg)`
    }
  } else {
    return {}
  }
})

/**
 * 向外转发组件内部点位点击事件
 */
const onPointClick = param => {
  emits('onPointClick', param)
}

/**
 * 判断当前组件在预览态是否允许触发事件
 */
const eventEnable = computed(
  () =>
    showPositionActive.value.includes('preview') &&
    (['Picture', 'CanvasIcon', 'CircleShape', 'SvgTriangle', 'RectShape', 'ScrollText'].includes(
      config.value.component
    ) ||
      ['indicator', 'rich-text'].includes(config.value.innerType)) &&
    config.value.events &&
    config.value.events.checked &&
    showPositionActive.value !== 'canvas-multiplexing'
)

/**
 * 处理包装器点击事件，指标卡点击由内部组件接管
 */
const onWrapperClickCur = e => {
  // 指标卡为内部触发
  if (['indicator'].includes(config.value.innerType)) {
    e.preventDefault()
    e.stopPropagation()
    return
  }
  onWrapperClick(e)
}

/**
 * 根据组件事件配置执行弹框、跳转、刷新、全屏或下载动作
 */
const onWrapperClick = e => {
  if (eventEnable.value && !['edit-preview'].includes(showPositionActive.value)) {
    if (config.value.events.type === 'showHidden') {
      // 打开弹框区域
      nextTick(() => {
        dvMainStore.popAreaActiveSwitch()
      })
    } else if (config.value.events.type === 'jump') {
      const url = config.value.events.jump.value
      const jumpType = config.value.events.jump.type
      try {
        if ('newPop' === jumpType) {
          crestPreviewPopDialogRef.value.previewInit({ url, size: 'middle' })
        } else if ('_blank' === jumpType) {
          if (window['originOpen']) {
            window['originOpen'](url, '_blank')
          } else {
            window.open(url, '_blank')
          }
        } else {
          initOpenHandler(window.open(url, jumpType))
        }
      } catch (e) {
        console.warn('url 格式错误:' + url)
      }
    } else if (config.value.events.type === 'refreshDataV') {
      useEmitt().emitter.emit('componentRefresh')
    } else if (config.value.events.type === 'fullScreen') {
      useEmitt().emitter.emit('canvasFullscreen')
    } else if (config.value.events.type === 'download') {
      useEmitt().emitter.emit('canvasDownload')
    }
    e?.preventDefault()
    e?.stopPropagation()
  }
}

/**
 * 嵌入式窗口调用句柄
 */
const openHandler = ref(null)
/**
 * 将新窗口句柄同步给嵌入式宿主
 */
const initOpenHandler = newWindow => {
  if (openHandler?.value) {
    const pm = {
      methodName: 'initOpenHandler',
      args: newWindow
    }
    openHandler.value.invokeMethod(pm)
  }
}
/**
 * 组件深层缩放比例
 */
const deepScale = computed(() => scale.value / 100)
//const showActive = computed(() => props.popActive || (dvMainStore.mobileInPc && props.active))
const showActive = false

/**
 * 判断主画布滚动时组件是否进入冻结状态
 */
const freezeFlag = computed(() => {
  return (
    isMainCanvas(props.canvasId) &&
    config.value.freeze &&
    !isMobile() &&
    scrollMain.value - config.value.style?.top > 0
  )
})

/**
 * 传递给内部组件的公共事件参数
 */
const commonParams = computed(() => {
  return {
    eventEnable: eventEnable.value,
    eventType: config.value.events.type
  }
})

/**
 * 判断移动端编辑态是否展示同步入口
 */
const showCheck = computed(() => {
  return dvMainStore.mobileInPc && showPositionActive.value === 'edit'
})

/**
 * 触发移动端状态同步到编辑器
 */
const updateFromMobile = (e, type) => {
  if (type === 'syncPcDesign') {
    e.preventDefault()
    e.stopPropagation()
  }
  useEmitt().emitter.emit('onMobileStatusChange', {
    type: type,
    value: config.value.id
  })
}

/**
 * 将编辑预览态映射为内部组件使用的预览态
 */
const showPositionActive = computed(() =>
  showPosition.value === 'edit-preview' ? 'preview' : showPosition.value
)
/**
 * 当前包装器是否已经进入可视区域
 */
const isIntersecting = ref(false)
/**
 * 移动端懒加载可视区域监听器
 */
const observer = ref<IntersectionObserver | null>(null)
// 移动端懒加载开关
const isMobileLazyLoadEnabled = computed(() => {
  return isMobile() || dvMainStore.inMobile || dvMainStore.mobileInPc
})
// 初始化IntersectionObserver
onMounted(() => {
  if (isMobileLazyLoadEnabled.value) {
    const wrapperInner = componentWrapperInnerRef.value
    if (wrapperInner) {
      observer.value = new IntersectionObserver(
        entries => {
          entries.forEach(entry => {
            if (entry.isIntersecting) {
              isIntersecting.value = true
              // 一旦加载完成，不再监听
              if (observer.value) {
                observer.value.unobserve(entry.target)
              }
            }
          })
        },
        {
          rootMargin: '200px 0px', // 提前200px开始加载
          threshold: 0.1
        }
      )
      observer.value.observe(wrapperInner)
    }
  }
})

// 清理Observer
onBeforeUnmount(() => {
  if (observer.value) {
    observer.value.disconnect()
  }
})
</script>

<template>
  <div
    class="wrapper-outer"
    :class="[
      showPositionActive + '-' + config.component,
      {
        'freeze-component': freezeFlag
      }
    ]"
    :id="wrapperId"
    @mousedown="handleInnerMouseDown"
    @mouseenter="onMouseEnter"
    v-loading="downLoading"
    :element-loading-text="$t('visualization.export_loading')"
    element-loading-background="rgba(255, 255, 255, 1)"
  >
    <div
      :title="$t('visualization.sync_pc_design')"
      v-if="showCheck"
      class="refresh-from-pc"
      @click="updateFromMobile($event, 'syncPcDesign')"
    >
      <el-icon>
        <Icon name="icon_replace_outlined"><replaceOutlined class="svg-icon" /></Icon>
      </el-icon>
    </div>
    <component-edit-bar
      v-if="!showPositionActive.includes('canvas') && !props.isSelector"
      class="wrapper-edit-bar"
      ref="componentEditBarRef"
      :canvas-id="canvasId"
      :index="index"
      :element="config"
      :show-position="showPositionActive"
      :class="{ 'wrapper-edit-bar-active': active }"
      @componentImageDownload="htmlToImage"
      @userViewEnlargeOpen="opt => emits('userViewEnlargeOpen', opt)"
      @datasetParamsInit="() => emits('datasetParamsInit')"
    ></component-edit-bar>
    <component-selector
      v-if="
        props.isSelector &&
        config.component === 'UserView' &&
        config.propValue?.innerType !== 'rich-text'
      "
      :resource-id="config.id"
    />
    <div
      class="wrapper-inner"
      ref="componentWrapperInnerRef"
      :id="viewDemoInnerId"
      :style="componentBackgroundStyle"
    >
      <div v-if="blurBgEnable" class="blur-bg" :style="blurBgStyle"></div>
      <div
        class="wrapper-inner-adaptor"
        :style="slotStyle"
        :class="{ 'pop-wrapper-inner': showActive, 'event-active': eventEnable }"
        @mousedown="onWrapperClickCur"
      >
        <component
          v-if="isIntersecting || !isMobileLazyLoadEnabled"
          :is="findComponent(config['component'])"
          :view="viewInfo"
          ref="component"
          class="component"
          :canvas-style-data="canvasStyleData"
          :opt-type="optType"
          :dv-info="dvInfo"
          :dv-type="dvInfo.type"
          :canvas-view-info="canvasViewInfo"
          :style="getComponentStyleDefault(config?.style)"
          :curStyle="curStyle"
          :prop-value="config?.propValue"
          :element="config"
          :request="config?.request"
          :linkage="config?.linkage"
          :show-position="showPositionActive"
          :search-count="searchCount"
          :scale="deepScale"
          :disabled="true"
          :is-edit="false"
          :suffix-id="suffixId"
          :font-family="fontFamily"
          :active="active"
          :common-params="commonParams"
          @onPointClick="onPointClick"
          @onComponentEvent="onWrapperClick"
        />
      </div>
      <!--边框背景-->
      <Board
        v-if="svgInnerEnable"
        :style="{ color: config.commonBackground.innerImageColor, pointerEvents: 'none' }"
        :name="commonBackgroundSvgInner"
      ></Board>
    </div>
    <CrestPreviewPopDialog ref="crestPreviewPopDialogRef"></CrestPreviewPopDialog>
  </div>
</template>

<style lang="less" scoped>
.pop-wrapper-inner {
  overflow: hidden;
  outline: 1px solid var(--ed-color-primary) !important;
}
.wrapper-outer {
  position: absolute;
  .refresh-from-pc {
    position: absolute;
    right: 38px;
    top: 12px;
    z-index: 2;
    font-size: 16px;
    cursor: pointer;
    color: var(--ed-color-primary);
  }
}
.wrapper-inner {
  width: 100%;
  height: 100%;
  position: relative;
  background-size: 100% 100% !important;
  .wrapper-inner-adaptor {
    position: relative;
    transform-style: preserve-3d;
    width: 100%;
    height: 100%;
  }
}

.blur-bg {
  width: 100%;
  height: 100%;
  background-size: 100% 100% !important;
}

.wrapper-edit-bar-active {
  display: inherit !important;
}
.preview-UserView {
  .wrapper-edit-bar {
    display: none;
  }
  &:hover .wrapper-edit-bar {
    display: inherit !important;
  }
}

.multiplexing {
  .wrapper-edit-bar {
    display: inherit;
  }
}

.component {
  width: 100% !important;
  height: 100% !important;
  overflow: hidden;
}

.svg-background {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  width: 100% !important;
  height: 100% !important;
}
.event-active {
  cursor: pointer;
}

.freeze-component {
  position: fixed;
  z-index: 1;
  top: var(--top-show-offset) px !important;
  left: var(--left-show-offset) px !important;
}
</style>
