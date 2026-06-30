// 组件默认配置注册表集中定义画布组件的新建状态，字段会直接进入画布持久化数据。
import { deepCopy } from '@/utils/utils'
import { guid } from '@/views/visualized/data/dataset/form/util'
import { getViewConfig } from '@/views/chart/components/editor/util/chart'
import { useI18n } from '@/hooks/web/useI18n'
import { CommonBackground } from '@/components/visualization/component-background/Types'
import { ShorthandMode } from '@/Types'
const { t } = useI18n()

// 组件通用样式覆盖旋转、透明度和边框，具体组件可在默认项中继续扩展。
export const commonStyle = {
  rotate: 0,
  opacity: 1,
  borderActive: false,
  borderWidth: 1,
  borderRadius: 5,
  borderStyle: 'solid',
  borderColor: '#cccccc'
}

// 轮播设置用于组件自动切换展示内容，默认关闭，时间单位由渲染组件解释。
export const BASE_CAROUSEL = {
  enable: false,
  time: 10
}

// 事件配置定义组件点击后的行为集合，typeList 是属性面板可选择的动作清单。
export const BASE_EVENTS = {
  checked: false,
  showTips: false,
  type: 'jump', // openHidden  jump
  typeList: [
    { key: 'jump', label: 'jump' },
    { key: 'download', label: 'download' },
    { key: 'share', label: 'share' },
    { key: 'fullScreen', label: 'fullScreen' },
    { key: 'showHidden', label: 'showHidden' },
    { key: 'refreshDataV', label: 'refreshDataV' },
    { key: 'refreshView', label: 'refreshView' }
  ],
  jump: {
    value: 'https://',
    type: '_blank'
  },
  download: {
    value: true
  },
  share: {
    value: true
  },
  showHidden: {
    value: true
  },
  refreshDataV: {
    value: true
  },
  refreshView: {
    value: true, // 当前事件是否启用，target 决定刷新单个图表还是整屏图表。
    target: 'all'
  }
}

// 流媒体视频默认使用 flv 配置，面向监控类实时视频组件。
export const STREAMMEDIALINKS = {
  videoType: 'flv',
  flv: {
    type: 'flv',
    isLive: false,
    cors: true, // 允许跨域拉取流地址。
    loop: true,
    autoplay: false,
    url: null // 网络流媒体地址。
  }
}

// 视频组件完整配置面向用户上传或外部 Web 视频，保留 poster 与控制栏选项。
export const VIDEO_LINKS_DE2 = {
  videoType: 'web',
  poster: null,
  web: {
    src: null, // 视频源地址。
    autoplay: true, // 浏览器准备好后自动播放。
    muted: true, // 默认静音，提升自动播放成功率。
    loop: true, // 播放结束后循环展示。
    preload: 'auto', // 让浏览器按自身策略预加载视频资源。
    language: 'zh-CN',
    fluid: true, // Video.js 播放器按容器比例自适应。
    notSupportedMessage: '此视频暂无法播放，请稍后再试', // 覆盖 Video.js 无法播放媒体源时的提示。
    controls: true,
    controlBar: {
      timeDivider: true,
      remainingTimeDisplay: false,
      fullscreenToggle: true // 显示播放器全屏按钮。
    }
  },
  rtmp: {
    sources: [
      {
        type: 'rtmp/mp4'
      }
    ],
    height: 300,
    techOrder: ['flash'],
    autoplay: false,
    controls: true,
    flash: {
      hls: {
        withCredentials: false
      }
    }
  }
}

// 精简视频配置用于旧组件或嵌入式展示，默认隐藏大部分控制栏。
export const VIDEO_LINKS = {
  videoType: 'web',
  web: {
    autoplay: true,
    height: 300,
    muted: true,
    loop: true,
    controlBar: {
      timeDivider: false,
      durationDisplay: false,
      remainingTimeDisplay: false,
      currentTimeDisplay: false, // 隐藏当前时间。
      volumeControl: false, // 隐藏音量控制。
      fullscreenToggle: false,
      pause: false
    },
    sources: [{}]
  },
  rtmp: {
    sources: [
      {
        type: 'rtmp/mp4'
      }
    ],
    height: 300,
    techOrder: ['flash'],
    autoplay: false,
    controls: true,
    flash: {
      hls: {
        withCredentials: false
      }
    }
  }
}

// 超链接配置供图片、按钮和 iframe 类组件复用，默认在新窗口打开。
export const HYPERLINKS = {
  openMode: '_blank',
  enable: false,
  content: 'http://'
}

// 嵌套页面配置只保存地址，尺寸和滚动行为由 Frame 组件自身控制。
export const FRAMELINKS = {
  src: ''
}

// 基础文本和标题样式作为轻量组件默认值，避免每个组件重复声明相同字段。
export const defaultStyleValue = {
  ...commonStyle,
  color: '',
  fontSize: 16,
  activeFontSize: 18,
  headHorizontalPosition: 'left',
  headFontColor: '#000000',
  headFontActiveColor: '#000000'
}

// 图表联动默认采用自定义选择模式，由组件事件面板配置具体目标。
export const ACTION_SELECTION = {
  linkageActive: 'custom'
}

// 三维位置配置预留给支持空间位移的组件，普通二维组件保持默认零值。
export const MULTI_DIMENSIONAL = {
  enable: false,
  x: 0,
  y: 0,
  z: 0
}

// 通用组件背景配置覆盖颜色、内外图片、圆角、内边距和背景模糊。
export const COMMON_COMPONENT_BACKGROUND_BASE: CommonBackground = {
  backgroundColorSelect: true,
  backdropFilterEnable: false,
  backgroundImageEnable: false,
  backgroundType: 'innerImage',
  innerImage: 'board/board_1.svg',
  outerImage: null,
  innerPadding: {
    mode: ShorthandMode.Uniform,
    top: 12
  },
  borderRadius: {
    mode: ShorthandMode.Uniform,
    topLeft: 0
  },
  backdropFilter: 4
}

// 明暗背景配置只覆盖色彩，结构字段沿用基础背景，便于主题切换时稳定合并。
export const COMMON_COMPONENT_BACKGROUND_LIGHT = {
  ...COMMON_COMPONENT_BACKGROUND_BASE,
  backgroundColor: 'rgba(255,255,255,1)',
  innerImageColor: 'rgba(16, 148, 229,1)'
}

export const COMMON_COMPONENT_BACKGROUND_DARK = {
  ...COMMON_COMPONENT_BACKGROUND_BASE,
  backgroundColor: 'rgba(19,28,66,1)',
  innerImageColor: '#1094E5'
}

export const COMMON_COMPONENT_BACKGROUND_SCREEN_DARK = {
  ...COMMON_COMPONENT_BACKGROUND_BASE,
  backgroundColorSelect: false,
  backgroundColor: '#131E42',
  innerImageColor: '#1094E5'
}

export const COMMON_COMPONENT_BACKGROUND_MAP = {
  light: COMMON_COMPONENT_BACKGROUND_LIGHT,
  dark: COMMON_COMPONENT_BACKGROUND_DARK
}

// 选项卡标题背景支持激活态和非激活态分离，默认两者复用同一背景配置。
export const COMMON_TAB_TITLE_BACKGROUND = {
  enable: false, // 是否启用标题背景。
  multiply: false, // 激活态与非激活态背景是否复用。
  active: COMMON_COMPONENT_BACKGROUND_LIGHT,
  inActive: COMMON_COMPONENT_BACKGROUND_LIGHT
}

// 组件通用属性保存交互、轮播、锁定、显隐、联动和折叠面板状态。
export const commonAttr = {
  animations: [],
  canvasId: 'canvas-main',
  events: BASE_EVENTS,
  carousel: BASE_CAROUSEL,
  multiDimensional: MULTI_DIMENSIONAL, // 三维位移设置。
  groupStyle: {}, // 组件成为分组子组件时保存分组内样式。
  isLock: false, // 是否锁定组件。
  maintainRadio: false, // 调整布局时是否保持宽高比例。
  aspectRatio: 1, // 锁定比例时使用的宽高比。
  isShow: true, // 是否在画布中显示组件。
  dashboardHidden: false, // 仪表板场景下是否隐藏组件。
  category: 'base', // 组件分类，base 为基础组件，hidden 为隐藏组件。
  // 拖拽和缩放状态只用于编辑态交互，不参与发布态展示。
  dragging: false,
  resizing: false,
  collapseName: [
    'position',
    'background',
    'style',
    'picture',
    'frameLinks',
    'videoLinks',
    'streamLinks',
    'carouselInfo',
    'events',
    'decoration_style'
  ], // 记录组件属性面板展开状态，重新选中组件时恢复上次编辑位置。
  linkage: {
    duration: 0, // 联动样式过渡持续时间。
    data: [
      // 每条联动记录描述触发事件、目标组件和需要变更的样式字段。
      {
        id: '', // 目标组件 id。
        label: '', // 目标组件名称。
        event: '', // 监听事件名称。
        style: [{ key: '', value: '' }] // 事件触发时写入目标组件的样式字段。
      }
    ]
  }
}

// 编辑器左侧组件列表决定可拖入画布的组件类型、初始尺寸和默认属性。
const list: any[] = [
  {
    // Group 是普通分组容器，只负责聚合已有组件，不额外绑定业务属性。
    component: 'Group',
    name: t('visualization.view_group'),
    label: t('visualization.view_group'),
    propValue: '&nbsp;',
    icon: 'icon_graphical',
    innerType: 'Group',
    style: {
      width: 200,
      height: 200
    }
  },
  {
    // GroupArea 用固定 id 区分区域分组，便于画布序列化时保持兼容。
    id: 100000001,
    component: 'GroupArea',
    name: 'group_area',
    label: 'group_area',
    propValue: '&nbsp;',
    icon: 'icon_graphical',
    innerType: 'GroupArea',
    style: {
      width: 200,
      height: 200
    }
  },
  {
    // 查询组件默认挂载在画布顶部区域，freeze 控制是否固定展示。
    component: 'VQuery',
    name: t('visualization.query_component'),
    label: t('visualization.query_component'),
    propValue: '',
    icon: 'icon_search',
    innerType: 'VQuery',
    isHang: false,
    freeze: false, // 是否在主画布顶部冻结展示。
    x: 1,
    y: 1,
    sizeX: 72,
    sizeY: 4,
    style: {
      width: 400,
      height: 100
    },
    request: {
      method: 'GET',
      data: [],
      url: '',
      series: false, // 是否定时发送请求。
      time: 1000, // 定时更新时间。
      paramType: '', // 请求参数类型，支持 string、object、array。
      requestCount: 0 // 请求次数限制，0 表示不限制。
    },
    matrixStyle: {}
  },
  {
    // UserView 承载图表视图，默认尺寸按仪表板常用半屏卡片设置。
    component: 'UserView',
    name: t('visualization.view'),
    label: t('visualization.view'),
    propValue: { textValue: '', urlList: [] },
    icon: 'bar',
    innerType: 'bar',
    editing: false,
    canvasActive: false,
    actionSelection: ACTION_SELECTION,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      adaptation: 'adaptation',
      width: 600,
      height: 300
    },
    matrixStyle: {}
  },
  {
    // 导出按钮默认导出当前过滤后的图表 Excel，目标视图由属性面板绑定。
    component: 'ExportButton',
    name: '导出按钮',
    label: '导出按钮',
    propValue: '',
    icon: 'Download',
    innerType: 'ExportButton',
    x: 1,
    y: 1,
    sizeX: 12,
    sizeY: 4,
    exportButton: {
      targetViewId: '',
      scope: 'currentFiltered',
      content: 'view',
      text: '导出Excel'
    },
    style: {
      width: 120,
      height: 40,
      color: '#ffffff',
      backgroundColor: '#3B82F6',
      fontSize: 14,
      fontWeight: 400,
      borderActive: false,
      borderRadius: 4
    },
    matrixStyle: {}
  },
  {
    // 视频组件使用完整播放器配置，适合用户上传视频或外部 Web 视频。
    component: 'Video',
    name: t('visualization.video'),
    label: t('visualization.video'),
    innerType: 'Video',
    editing: false,
    canvasActive: false,
    icon: 'icon-video',
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 600,
      height: 300
    },
    videoLinks: VIDEO_LINKS_DE2,
    matrixStyle: {}
  },
  {
    // 流媒体组件面向实时监控流，默认采用 StreamMedia 独立配置。
    component: 'StreamMedia',
    name: t('visualization.stream_media'),
    label: t('visualization.stream_media'),
    innerType: 'StreamMedia',
    editing: false,
    canvasActive: false,
    icon: 'icon-stream',
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 600,
      height: 300
    },
    streamMediaLinks: STREAMMEDIALINKS,
    matrixStyle: {}
  },
  {
    // Frame 组件同时保留超链接和嵌套页面配置，兼容旧版属性面板。
    component: 'Frame',
    name: t('visualization.web'),
    label: t('visualization.web'),
    innerType: 'Frame',
    editing: false,
    canvasActive: false,
    icon: 'db-more-web',
    hyperlinks: HYPERLINKS,
    frameLinks: FRAMELINKS,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 600,
      height: 300
    },
    matrixStyle: {}
  },
  {
    // 时间组件默认展示日期和时分秒，格式化规则由 formatInfo 保存。
    component: 'TimeClock',
    name: t('visualization.time_component'),
    label: t('visualization.time_component'),
    icon: 'dv-more-time-clock',
    innerType: 'TimeClock',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 12,
    propValue: {},
    style: {
      width: 300,
      height: 100,
      fontSize: 22,
      fontWeight: 'normal',
      fontStyle: 'normal',
      textAlign: 'center',
      color: '#000000'
    },
    formatInfo: {
      openMode: '0',
      showWeek: false,
      showDate: true,
      dateFormat: 'yyyy-MM-dd',
      timeFormat: 'hh:mm:ss'
    },
    matrixStyle: {}
  },
  {
    // 图片组件默认自适应容器，并保留水平、垂直翻转状态。
    component: 'Picture',
    name: t('visualization.picture'),
    label: t('visualization.picture'),
    icon: 'dv-picture-real',
    innerType: 'Picture',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    propValue: {
      url: '',
      flip: {
        horizontal: false,
        vertical: false
      }
    },
    style: {
      adaptation: 'adaptation',
      width: 300,
      height: 200
    },
    matrixStyle: {}
  },
  {
    component: 'CanvasIcon',
    name: t('visualization.icon'),
    label: t('visualization.icon'),
    propValue: '',
    icon: 'other_material_icon',
    innerType: '',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 10,
    sizeY: 10,
    style: {
      width: 40,
      height: 40,
      color: '',
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'CanvasBoard',
    name: t('visualization.board'),
    label: t('visualization.board'),
    propValue: '',
    icon: 'other_material_board',
    innerType: '',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 30,
    sizeY: 30,
    style: {
      width: 600,
      height: 300,
      color: 'rgb(255, 255, 255,1)',
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'Decoration',
    name: t('visualization.decoration'),
    label: t('visualization.decoration'),
    propValue: '&nbsp;',
    icon: 'dv_decoration',
    style: {
      width: 400,
      height: 300,
      color0: '#298e73',
      color1: '#2862b7',
      color2: '#2862b7',
      dur: 6,
      reverse: false,
      borderActive: false,
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'DynamicBackground',
    name: t('visualization.dynamic_background'),
    label: t('visualization.dynamic_background'),
    propValue: '&nbsp;',
    icon: 'dv_dynamic_background',
    style: {
      width: 400,
      height: 300,
      backgroundColor: 'rgba(236,231,231,0.1)',
      borderActive: false,
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'RectShape',
    name: t('visualization.rect_shape'),
    label: t('visualization.rect_shape'),
    propValue: '&nbsp;',
    icon: 'icon_graphical',
    style: {
      width: 200,
      height: 200,
      backgroundColor: 'rgba(236,231,231,0.1)',
      borderActive: true,
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'CircleShape',
    name: t('visualization.circle_shape'),
    label: t('visualization.circle_shape'),
    propValue: '&nbsp;',
    icon: 'icon_graphical',
    style: {
      width: 200,
      height: 200,
      borderWidth: 1,
      borderStyle: 'solid',
      borderColor: '#cccccc',
      borderActive: true,
      backgroundColor: 'rgba(236,231,231,0.1)',
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'SvgTriangle',
    name: t('visualization.triangle'),
    label: t('visualization.triangle'),
    icon: 'icon_graphical',
    propValue: '',
    style: {
      width: 200,
      height: 200,
      borderWidth: 1,
      borderColor: '#cccccc',
      borderActive: true,
      backgroundColor: 'rgba(236,231,231,0.1)',
      backdropFilter: 'blur(0px)'
    }
  },
  {
    component: 'Tabs',
    name: t('visualization.tabs'),
    label: t('visualization.tabs'),
    propValue: [
      {
        name: 'tab',
        title: t('visualization.new_tab'),
        componentData: [],
        closable: true
      }
    ],
    icon: 'dv-tab',
    innerType: '',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 600,
      height: 300,
      fontSize: 16,
      activeFontSize: 18,
      headHorizontalPosition: 'left',
      headFontColor: '#000000',
      headFontActiveColor: '#000000',
      titleHide: false,
      showTabTitle: true,
      // #13540
      fontWeight: 'normal',
      fontStyle: 'normal',
      textDecoration: 'none'
    }
  },
  {
    component: 'ScrollText',
    name: t('visualization.scroll_text'),
    label: t('visualization.scroll_text'),
    propValue: t('visualization.component_input_tips'),
    innerType: 'ScrollText',
    icon: 'scroll-text',
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 400,
      height: 80,
      fontSize: 14,
      fontWeight: 400,
      letterSpacing: 0,
      color: '',
      padding: 4,
      verticalAlign: 'middle',
      scrollSpeed: 0
    }
  },
  {
    component: 'Screen',
    name: t('visualization.screen_page'),
    label: t('visualization.screen_page'),
    propValue: [
      {
        name: 'screen',
        title: t('visualization.new_screen_page'),
        screenId: null,
        closable: true
      }
    ],
    icon: 'tab-screen',
    innerType: '',
    editing: false,
    canvasActive: false,
    x: 1,
    y: 1,
    sizeX: 36,
    sizeY: 14,
    style: {
      width: 600,
      height: 300,
      fontSize: 16,
      activeFontSize: 18,
      headHorizontalPosition: 'left',
      headFontColor: '#000000',
      headFontActiveColor: '#000000',
      titleHide: false,
      showTabTitle: true,
      // #13540
      fontWeight: 'normal',
      fontStyle: 'normal',
      textDecoration: 'none'
    }
  }
]

for (let i = 0, len = list.length; i < len; i++) {
  const item = list[i]
  item.style = { ...commonStyle, ...item.style }
  item['commonBackground'] = deepCopy(COMMON_COMPONENT_BACKGROUND_BASE)
  item['state'] = 'prepare'
  list[i] = { ...commonAttr, ...item }
}

// 衔接当前组件交互和状态同步
export function findNewComponentFromList(
  componentName,
  innerType,
  curOriginThemes,
  staticMap?: object
) {
  const isPlugin = !!staticMap
  let newComponent
  list.forEach(comp => {
    if (comp.component === componentName) {
      newComponent = deepCopy(comp)
      newComponent['commonBackground'] = deepCopy(
        COMMON_COMPONENT_BACKGROUND_MAP[curOriginThemes.value]
      )
      newComponent.innerType = innerType
      if (['Tabs', 'Screen'].includes(comp.component)) {
        newComponent.propValue[0].name = guid()
        newComponent['titleBackground'] = deepCopy(COMMON_TAB_TITLE_BACKGROUND)
      }
    }
  })

  if (componentName === 'UserView') {
    const viewConfig = getViewConfig(innerType)
    newComponent.name = viewConfig?.title
    newComponent.label = viewConfig?.title
    newComponent.render = viewConfig?.render
    newComponent.isPlugin = !!isPlugin
    if (isPlugin) {
      newComponent.staticMap = staticMap
    }
  }
  return newComponent
}

// 衔接当前组件交互和状态同步
export function findBaseDefaultAttr(componentName) {
  let result = {}
  list.forEach(comp => {
    if (comp.component === componentName) {
      const stylePropertyInner = []
      Object.keys(comp.style).forEach(styleKey => {
        if (
          (!['width', 'height'].includes(styleKey) &&
            componentName === 'VQuery' &&
            !Object.keys(commonStyle).includes(styleKey)) ||
          componentName !== 'VQuery'
        ) {
          stylePropertyInner.push(styleKey)
        }
      })
      result = {
        properties: ['common-style', 'background-overall-component'],
        propertyInner: {
          'common-style': stylePropertyInner,
          'background-overall-component': ['all']
        },
        value: comp.name,
        componentType: componentName
      }
    }
  })
  return result
}

export default list
