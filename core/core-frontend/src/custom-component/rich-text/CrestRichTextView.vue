<template>
  <div
    class="rich-main-class"
    :class="{ 'edit-model': canEdit }"
    @keydown.stop
    @keyup.stop
    @dblclick="setEdit"
    @click="onClick"
    :style="richTextStyle"
  >
    <chart-error v-if="isError" :err-msg="errMsg" />
    <Editor
      v-if="editShow && !isError"
      v-model="myValue"
      class="custom-text-content"
      :style="wrapperStyle"
      :id="tinymceId"
      :init="init"
      :disabled="!canEdit || disabled"
    />
    <div
      class="rich-placeholder"
      :class="{ 'rich-placeholder--dark': themes === 'dark' }"
      v-if="showPlaceHolder"
    >
      {{ init.outer_placeholder }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatEmbeddedUrl } from '@/utils/url'
import tinymce from 'tinymce/tinymce' // tinymce默认hidden，不引入不显示
import Editor from '@tinymce/tinymce-vue' // 编辑器引入
import 'tinymce/themes/silver/theme' // 编辑器主题
import 'tinymce/icons/default' // 引入编辑器图标icon，不引入则不显示对应图标
// 引入编辑器插件（基本免费插件都在这儿了）
import 'tinymce/plugins/advlist' // 高级列表
import 'tinymce/plugins/autolink' // 自动链接
import 'tinymce/plugins/link' // 超链接
import 'tinymce/plugins/image' // 插入编辑图片
import 'tinymce/plugins/lists' // 列表插件
import 'tinymce/plugins/charmap' // 特殊字符
import 'tinymce/plugins/media' // 插入编辑媒体
import 'tinymce/plugins/wordcount' // 字数统计
import 'tinymce/plugins/table' // 表格
import 'tinymce/plugins/directionality'
import 'tinymce/plugins/nonbreaking'
import 'tinymce/plugins/pagebreak'
import '@npkg/tinymce-plugins/letterspacing'
import './plugins' //自定义插件
import { computed, nextTick, reactive, ref, toRefs, watch, onMounted, PropType } from 'vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import eventBus from '@/utils/eventBus'
import { guid } from '@/views/visualized/data/dataset/form/util'
import { getData } from '@/api/chart'
import { storeToRefs } from 'pinia'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import ChartError from '@/views/chart/components/views/components/ChartError.vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import { valueFormatter } from '@/views/chart/components/js/formatter'
import { parseJson } from '@/views/chart/components/js/util'
import { mappingColorCustom } from '@/views/chart/components/js/panel/common/common_table'
import { CHART_FONT_FAMILY_ORIGIN } from '@/views/chart/components/editor/util/chart'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
/** 快照状态仓库 */
const snapshotStore = snapshotStoreWithOut()
/** 图表渲染错误提示 */
const errMsg = ref('')
/** 可视化主画布状态仓库 */
const dvMainStore = dvMainStoreWithOut()
/** 画布图表缓存和移动端预览状态 */
const { canvasViewInfo, mobileInPc } = storeToRefs(dvMainStore)
/** 当前富文本是否处于渲染错误状态 */
const isError = ref(false)
/** 外观配置状态仓库 */
const appearanceStore = useAppearanceStoreWithOut()
import { useUserStoreWithOut } from '@/store/modules/user'
/** 当前用户状态仓库 */
const userStore = useUserStoreWithOut()
import { useI18n } from '@/hooks/web/useI18n'
/** 国际化文本读取方法 */
const { t } = useI18n()
/** 富文本组件输入属性 */
const props = defineProps({
  scale: {
    type: Number,
    required: false,
    default: 1
  },
  element: {
    type: Object
  },
  editMode: {
    type: String,
    require: false,
    default: 'edit'
  },
  active: {
    type: Boolean,
    require: false,
    default: false
  },
  // 是否禁用编辑器
  disabled: {
    type: Boolean,
    default: false
  },
  showPosition: {
    type: String,
    required: false,
    default: 'preview'
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  // 图表渲染 ID 后缀
  suffixId: {
    type: String,
    required: false,
    default: 'common'
  }
})

/** 从属性中解构可响应的富文本上下文 */
const { element, editMode, active, disabled, showPosition, suffixId } = toRefs(props)

/** 富文本渲染和数据绑定状态 */
const state = reactive({
  emptyValue: '-',
  data: null,
  viewDataInfo: null,
  totalItems: 0,
  firstRender: true,
  previewFirstRender: true
})
/** TinyMCE 语言包映射 */
const language_map = {
  en: 'en_US',
  tw: 'zh_TW',
  'zh-CN': 'zh_CN'
}

/** 当前 TinyMCE 使用的语言标识 */
const language = language_map[userStore.getLanguage]
/** 字段值映射，用于占位符替换 */
const dataRowSelect = ref({})
/** 字段展示名到格式化值的映射 */
const dataRowNameSelect = ref({})
/** 字段展示名到原始值的映射 */
const dataRowNameSelectSource = ref({})
/** 当前可插入字段名称列表 */
const dataRowFiledName = ref([])
/** 编辑器是否完成初始化 */
const initReady = ref(false)
/** 是否显示 TinyMCE 编辑器实例 */
const editShow = ref(true)
/** 当前富文本是否处于可编辑状态 */
const canEdit = ref(false)
// 初始化编辑器 ID
const tinymceId =
  'tinymce-view-' + element.value.id + '-' + suffixId.value + '-' + showPosition.value
/** 富文本编辑器内容 */
const myValue = ref('')

/** 系统内置字体名称列表 */
const systemFontFamily = appearanceStore.fontList.map(item => item.name)
/** 组装 TinyMCE 字体下拉配置 */
const curFontFamily = () => {
  let result = ''
  CHART_FONT_FAMILY_ORIGIN.concat(
    appearanceStore.fontList.map(ele => ({
      name: ele.name,
      value: ele.name
    }))
  ).forEach(font => {
    result = result + font.name + '=' + font.value + ';'
  })
  return result
}
/** 富文本为空时的提示文本 */
const outerPlaceholder = t('visualization.component_input_tips')
/** TinyMCE 编辑器初始化配置 */
const init = ref({
  selector: '#' + tinymceId,
  toolbar_items_size: 'small',
  language_url: formatEmbeddedUrl(`./tinymce-crest-private/langs/${language}.js`), // 汉化路径是自定义的，一般放在public或static里面
  language: language,
  skin_url: formatEmbeddedUrl('./tinymce-crest-private/skins/ui/oxide'), // 皮肤
  content_css: formatEmbeddedUrl('./tinymce-crest-private/skins/content/default/content.css'),
  plugins:
    'vertical-content advlist autolink link image lists charmap  media wordcount table contextmenu directionality pagebreak letterspacing', // 插件
  // 工具栏
  toolbar:
    'undo redo | fontselect fontsizeselect |forecolor backcolor bold italic letterspacing |underline strikethrough link lineheight| formatselect |' +
    'top-align center-align bottom-align | alignleft aligncenter alignright | bullist numlist |' +
    ' blockquote subscript superscript removeformat | table image ',
  toolbar_location: '/',
  font_formats: curFontFamily(),
  fontsize_formats:
    '12px 14px 16px 18px 20px 22px 24px 28px 32px 36px 42px 48px 56px 72px 80px 90px 100px 110px 120px 140px 150px 170px 190px 210px', // 字体大小
  menubar: false,
  placeholder: '',
  outer_placeholder: outerPlaceholder,
  inline: true, // 开启内联模式
  branding: false,
  icons: 'vertical-content',
  vertical_align: element.value.propValue.verticalAlign,
  table_default_styles: {
    width: '400px' // 或者使用 table_default_styles 设置宽度，单位为 px
  },
  setup: function (editor) {
    let cloneHandle = null // 用于存储克隆的拖拽手柄。
    let originalHandle = null // 用于存储原始拖拽手柄。
    editor.on('init', () => {
      const doc = editor.getDoc()
      // 监测 mouseup、mousedown 和 mousemove 事件
      // 1.单元格问题 因为缩放问题 导致拖拽的坐标系有偏差，此处隐藏原有拖拽定位bar,并根据
      // 缩放比例动态调整时间定位bar的位置，这会代理鼠标移速和bar移速不同步问题，不过bar定位是准确的
      // 可以解决因为缩放导致tinymce 内部坐标系出错问题
      doc.addEventListener('mousedown', event => {
        nextTick(() => {
          originalHandle = event.target.closest('.ephox-snooker-resizer-bar-dragging')
          if (originalHandle) {
            // 克隆原始手柄
            cloneHandle = originalHandle.cloneNode(true)
            cloneHandle.style.zIndex = 9999 // 提升克隆手柄的层级
            originalHandle.style.display = 'none' // 隐藏原始手柄
            // 将克隆手柄添加到原手柄的父元素中
            const parentDiv = originalHandle.parentNode // 获取原手柄的父元素
            parentDiv.appendChild(cloneHandle) // 将克隆手柄添加到父元素中
          }
        })
      })

      // 监听 mousemove 事件以更新克隆手柄位置
      doc.addEventListener('mousemove', event => {
        if (cloneHandle) {
          // // 计算鼠标移动的距离
          if (cloneHandle.offsetHeight > cloneHandle.offsetWidth) {
            // 计算鼠标移动的距离
            const offsetX = event.movementX * props.scale // 使用缩放比例进行调整
            cloneHandle.style.left = `${cloneHandle.offsetLeft + offsetX}px` // 更新克隆手柄的位置
          } else {
            // 计算鼠标移动的距离
            const offsetY = event.movementY * props.scale // 使用缩放比例进行调整
            cloneHandle.style.top = `${cloneHandle.offsetTop + offsetY}px` // 更新克隆手柄的位置
          }
        }
      })

      // 监听 mouseup 事件以结束调整
      doc.addEventListener('mouseup', () => {
        if (cloneHandle) {
          // 显示原始手柄并移除克隆手柄
          originalHandle.style.display = ''
          if (cloneHandle) {
            cloneHandle.parentNode?.removeChild(cloneHandle) // 获取原手柄的父元素
          }
          cloneHandle = null
          originalHandle = null
        }
      })

      // 函数：根据缩放比例调整 .mce-resizehandle 的位置和大小
      const adjustResizeHandles = (aLeft, aTop) => {
        nextTick(() => {
          const nodeRt = doc.getElementById('mceResizeHandlene')
          const nodeRb = doc.getElementById('mceResizeHandlese')
          const nodeLb = doc.getElementById('mceResizeHandlesw')
          if (nodeRt) {
            nodeRt.style.left = `${aLeft}px`
          }
          if (nodeRb) {
            nodeRb.style.left = `${aLeft}px`
            nodeRb.style.top = `${aTop}px`
          }
          if (nodeLb) {
            nodeLb.style.top = `${aTop}px`
          }
        })
      }

      // 监听 ObjectSelected 事件，点击表格时触发调整
      editor.on('ObjectSelected', event => {
        if (event.target.nodeName === 'TABLE') {
          adjustResizeHandles(
            event.target.offsetWidth + event.target.offsetLeft,
            event.target.offsetHeight + event.target.offsetTop
          )
        }
      })

      // 监听 ObjectResized 事件，更新调整大小句柄
      // 在表格调整大小结束时
      // 解决移动表格corner点位resize时因为缩放导致的坐标系放大问题，进而导致移动错位问题
      editor.on('ObjectResized', function (e) {
        const { target, width, height, origin } = e
        if (target.nodeName === 'TABLE' && origin.indexOf('corner') > -1) {
          // 将最终调整的宽高根据缩放比例重设
          target.style.width = `${width}px`
          target.style.height = `${height}px`
        } else if (target.nodeName === 'TABLE' && origin.indexOf('bar-col') > -1) {
          // 列拖拽场景保持 TinyMCE 默认处理
        }
      })
    })
  }
})

/** 当前是否处于编辑模式 */
const editStatus = computed(() => {
  return editMode.value === 'edit'
})

/** 选中状态失效时退出富文本编辑并同步内容 */
watch(
  () => active.value,
  val => {
    if (!val) {
      const ed = tinymce.editors[tinymceId]
      if (canEdit.value) {
        element.value.propValue.textValue = ed?.getContent()
      }
      element.value['editing'] = false
      canEdit.value = false
      reShow()
      myValue.value = assignment(element.value.propValue.textValue)
      ed.setContent(myValue.value)
    }
  }
)

/** 富文本内容变化时同步组件属性并记录快照 */
watch(
  () => myValue.value,
  () => {
    if (canEdit.value) {
      const ed = tinymce.editors[tinymceId]
      element.value.propValue.textValue = ed?.getContent()
    }
    if (initReady.value && canEdit.value) {
      snapshotStore.recordSnapshotCache('renderChart', element.value.id)
      initFontFamily(myValue.value)
    }
  }
)
/** TinyMCE 垂直对齐方式到容器样式的映射 */
const ALIGN_MAP = {
  'top-align': {},
  'center-align': {
    margin: 'auto'
  },
  'bottom-align': {
    'margin-top': 'auto'
  }
}
/** 富文本编辑器外层对齐样式 */
const wrapperStyle = computed(() => {
  const align = element.value.propValue.verticalAlign
  if (!align) {
    return {}
  }
  return ALIGN_MAP[align]
})
/** 接收垂直对齐插件事件并写回组件配置 */
useEmitt({
  name: 'vertical-change-' + tinymceId,
  callback: align => {
    element.value.propValue.verticalAlign = align
  }
})

/** 初始化富文本编辑器事件和占位符内容 */
const viewInit = () => {
  useEmitt({
    name: 'fieldSelect-' + element.value.id,
    callback: function (val) {
      fieldSelect(val)
    }
  })
  tinymce.init({})
  myValue.value = assignment(element.value.propValue.textValue)
}
/** 在非编辑状态下刷新字段占位符替换结果 */
const initCurFieldsChange = () => {
  if (!canEdit.value) {
    myValue.value = assignment(element.value.propValue.textValue)
    const ed = tinymce.editors[tinymceId]
    ed.setContent(myValue.value)
  }
}

/** 为富文本中的链接段落补充点击阻断，避免触发画布事件 */
const jumpTargetAdaptor = () => {
  setTimeout(() => {
    const paragraphs = document.querySelectorAll('p')
    paragraphs.forEach(p => {
      // 如果 p 标签已经有 onclick 且包含 event.stopPropagation，则跳过
      if (
        p.getAttribute('onclick') &&
        p.getAttribute('onclick').includes('event.stopPropagation()')
      ) {
        return // 已经有 stopPropagation，跳过。
      }
      // 否则添加 onclick 事件
      p.setAttribute('onclick', 'event.stopPropagation()')
    })
  }, 1000)
}

/** 将富文本模板中的字段占位符替换为当前图表数据 */
const assignment = content => {
  if (content) {
    const on = content?.match(/\[(.+?)\]/g) || []
    if (on) {
      const thresholdStyleInfo = conditionAdaptor(state.viewDataInfo)
      on.forEach(itm => {
        if (dataRowFiledName.value.includes(decodeHTMLEntities(itm))) {
          const ele = itm.slice(1, -1)
          let value =
            dataRowNameSelect.value[ele] !== undefined ? dataRowNameSelect.value[ele] : null
          let targetValue = !!value ? value : state.emptyValue
          if (thresholdStyleInfo && thresholdStyleInfo[ele]) {
            const thresholdStyle = thresholdStyleInfo[ele]
            targetValue = `<span style="color:${thresholdStyle.color};background-color: ${thresholdStyle.backgroundColor}">${targetValue}</span>`
          }
          if (initReady.value) {
            content = content.replace(itm, targetValue)
          } else {
            content = content.replace(itm, !!value ? targetValue : '[获取中...]')
          }
        }
      })
    }
    content = content.replace('class="base-selected"', '')
    // 兼容本地跳转在富文本中的路径写法
    content = content.replace(/href="#\//g, 'href="/#/')
    content = content.replace(/href=\\"#\//g, 'href=\\"/#/')
    content = content.replace(/href=\\"#\//g, 'href=\\"/#/')
    resetSelect()
    initFontFamily(content)
    jumpTargetAdaptor()
  }

  return content
}

/** 解码 HTML 实体，便于和字段占位符匹配 */
const decodeHTMLEntities = text => {
  if (!text) return text

  const textarea = document.createElement('textarea')
  textarea.innerHTML = text
  return textarea.value
}

/** 编码 HTML 实体，避免插入字段时破坏富文本结构 */
const encodeHTMLEntities = text => {
  if (!text) return text

  const textarea = document.createElement('textarea')
  textarea.textContent = text
  return textarea.innerHTML
}
/** 根据富文本内容中的字体样式同步当前字体 */
const initFontFamily = htmlText => {
  const regex = /font-family:\s*([^;"]+);/g
  let match
  while ((match = regex.exec(htmlText)) !== null) {
    const font = match[1].trim()
    if (systemFontFamily.includes(font)) {
      appearanceStore.setCurrentFont(font)
    }
  }
}
/** 向 TinyMCE 当前光标处插入字段占位符 */
const fieldSelect = field => {
  const ed = tinymce.editors[tinymceId]
  const fieldId = 'changeText-' + guid()
  const value =
    '<span id="' +
    fieldId +
    '"><span class="mceNonEditable" contenteditable="false" data-mce-content="[' +
    field.name +
    ']">[' +
    field.name +
    ']</span></span>'
  const attachValue = '<span id="attachValue">&nbsp;</span>'
  ed.insertContent(value)
  ed.insertContent(attachValue)
  snapshotStore.resetStyleChangeTimes()
}
/** 编辑器点击时更新字段占位符选中态 */
const onClick = () => {
  if (canEdit.value) {
    const node = tinymce.activeEditor.selection.getNode()
    resetSelect(node)
  }
}
/** 清理并设置字段占位符选中态 */
const resetSelect = (node?) => {
  const edInner = tinymce.get(tinymceId)
  if (edInner?.dom) {
    const nodeArray = edInner.dom.select('.base-selected')
    if (nodeArray) {
      nodeArray.forEach(nodeInner => {
        nodeInner.removeAttribute('class')
      })
    }
    if (node) {
      const pNode = node.parentElement
      if (pNode && pNode.id && pNode.id.indexOf('changeText') > -1) {
        const innerId = '#' + pNode.id
        const domTest = edInner.dom.select(innerId)[0]
        domTest.setAttribute('class', 'base-selected')
        edInner.selection.select(domTest)
      }
    }
  }
}

/** 当前是否允许进入富文本编辑 */
const computedCanEdit = computed<boolean>(() => {
  return (
    ['canvas', 'canvasDataV', 'edit'].includes(showPosition.value) &&
    editStatus.value &&
    canEdit.value === false &&
    !isError.value &&
    !disabled.value &&
    !mobileInPc.value
  )
})

/** 当前是否展示富文本空内容占位提示 */
const showPlaceHolder = computed<boolean>(() => {
  return (
    computedCanEdit.value && (myValue.value == undefined || myValue.value == '') && !isError.value
  )
})

/** 当前组件激活状态是否允许编辑 */
const editActive = computed<boolean>(() => {
  if (element.value.canvasId.includes('Group') && !active.value) {
    return false
  } else {
    return true
  }
})

/** 双击后进入富文本编辑态 */
const setEdit = () => {
  setTimeout(() => {
    if (computedCanEdit.value && editActive.value) {
      canEdit.value = true
      element.value['editing'] = true
      myValue.value = element.value.propValue.textValue
      const ed = tinymce.editors[tinymceId]
      ed.setContent(myValue.value)
      reShow()
    }
  })
}
/** 重新渲染 TinyMCE 实例并恢复光标 */
const reShow = () => {
  editShow.value = false
  nextTick(() => {
    editShow.value = true
    editCursor()
  })
}

/** 将编辑光标移动到富文本末尾 */
const editCursor = () => {
  setTimeout(() => {
    const myDiv = document.getElementById(tinymceId)
    // 让光标聚焦到文本末尾
    const range = document.createRange()
    const sel = window.getSelection()
    if (myDiv.childNodes) {
      range.setStart(myDiv.childNodes[myDiv.childNodes.length - 1], 1)
      range.collapse(false)
      sel.removeAllRanges()
      sel.addRange(range)
    }
    // 对于部分浏览器，需要额外聚焦到编辑器节点
    if (myDiv.focus) {
      myDiv.focus()
    }
    tinymce.init({
      selector: tinymceId,
      plugins: 'table'
    })
  }, 100)
}

/** 根据图表空值策略更新富文本字段缺省展示值 */
const updateEmptyValue = view => {
  state.emptyValue =
    view?.senior?.functionCfg?.emptyDataStrategy === 'custom'
      ? view.senior.functionCfg.emptyDataCustomValue || ''
      : '-'
}

/** 判断指标中是否包含对比计算 */
const checkCompareCalc = view => {
  let compareCount = 0
  view.yAxis?.forEach(item => {
    if (item?.compareCalc?.type !== 'none') {
      compareCount++
    }
  })
  return compareCount > 0
}

/** 拉取图表数据并刷新富文本字段占位符数据源 */
const calcData = (view: Chart, callback) => {
  isError.value = false
  updateEmptyValue(view)
  if (view.tableId || view['dataFrom'] === 'template') {
    const v = JSON.parse(JSON.stringify(view))
    if (!checkCompareCalc(view)) {
      v.resultCount = 1
      v.resultMode = 'custom'
    }
    getData(v)
      .then(res => {
        if (res.code && res.code !== 0) {
          isError.value = true
          errMsg.value = res.msg
        } else {
          state.data = res?.data
          state.viewDataInfo = res
          state.totalItems = res?.totalItems
          const curViewInfo = canvasViewInfo.value[element.value.id]
          // 此处是编辑时使用，多仪表板嵌入 canvasViewInfo 会被覆盖可能出现无法读取情况
          if (res.data && curViewInfo) {
            curViewInfo['curFields'] = res.data.fields || []
          }
          dvMainStore.setViewDataDetails(element.value.id, res)
          initReady.value = true
          initCurFields(res)
        }
        callback?.()
        nextTick(() => {
          initReady.value = true
        })
      })
      .catch(() => {
        nextTick(() => {
          initReady.value = true
        })
        callback?.()
      })
  } else if (!view.tableId) {
    state.data = []
    state.viewDataInfo = {}
    state.totalItems = 0
    const curViewInfo = canvasViewInfo.value[element.value.id]
    if (curViewInfo) {
      curViewInfo['curFields'] = []
      dvMainStore.setViewDataDetails(element.value.id, state.viewDataInfo)
      initReady.value = true
      initCurFields(curViewInfo)
    }
    initReady.value = true
    callback?.()
    nextTick(() => {
      initReady.value = true
    })
  } else {
    nextTick(() => {
      initReady.value = true
    })
    callback?.()
  }
}

/** 根据图表详情初始化可插入字段和字段值映射 */
const initCurFields = chartDetails => {
  dataRowFiledName.value = []
  dataRowSelect.value = {}
  dataRowNameSelect.value = {}
  dataRowNameSelectSource.value = {} // 记录原始值，避免格式化后的数字影响阈值颜色匹配。
  const sourceFields = Array.isArray(chartDetails.data?.sourceFields)
    ? chartDetails.data.sourceFields
    : []
  if (sourceFields.length) {
    const checkAllAxisStr =
      JSON.stringify(chartDetails.xAxis) +
      JSON.stringify(chartDetails.xAxisExt) +
      JSON.stringify(chartDetails.yAxis) +
      JSON.stringify(chartDetails.yAxisExt)
    sourceFields.forEach(field => {
      if (checkAllAxisStr.indexOf(field.id) > -1) {
        dataRowFiledName.value.push(`[${field.name}]`)
      }
    })
    if (checkAllAxisStr.indexOf('"记录数*"') > -1) {
      dataRowFiledName.value.push(`[记录数*]`)
    }
    const dataFields = Array.isArray(chartDetails.data?.fields) ? chartDetails.data.fields : []
    const rowData = chartDetails.data?.tableRow?.[0] || {}
    // 建立引擎字段名与字段 ID 的对应关系
    const nameIdMap = dataFields.reduce((pre, next) => {
      pre[next['engineFieldName']] = next['id']
      return pre
    }, {})
    const sourceFieldNameIdMap = dataFields.reduce((pre, next) => {
      pre[next['engineFieldName']] = next['name']
      return pre
    }, {})
    const yAxisItems = Array.isArray(chartDetails.yAxis) ? chartDetails.yAxis : []
    if (chartDetails.type === 'rich-text') {
      let yAxis = JSON.parse(JSON.stringify(yAxisItems))
      const yEngineFieldNames = []
      const yEngineFieldNamesCfg = []
      yAxis.forEach(yItem => {
        yEngineFieldNames.push(yItem.engineFieldName)
        yEngineFieldNamesCfg[yItem.engineFieldName] = yItem.formatterCfg
      })
    }
    const valueFieldMap: Record<string, Axis> = yAxisItems.reduce((p, n) => {
      p[n.engineFieldName] = n
      return p
    }, {})
    for (const key in rowData) {
      dataRowSelect.value[nameIdMap[key]] = rowData[key]
      let rowDataValue = rowData[key]
      const rowDataValueSource = rowData[key]
      const f = valueFieldMap[key]
      if (f && f.formatterCfg) {
        rowDataValue = valueFormatter(rowDataValue, f.formatterCfg)
      }
      dataRowNameSelect.value[sourceFieldNameIdMap[key]] = rowDataValue
      dataRowNameSelectSource.value[sourceFieldNameIdMap[key]] = rowDataValueSource
    }
  }
  element.value.propValue['innerType'] = chartDetails.type
  element.value.propValue['render'] = chartDetails.render
  nextTick(() => {
    initCurFieldsChange()
    eventBus.emit('initCurFields-' + element.value.id)
  })
}

// 初始化此处不必刷新
/** 使用已有数据刷新富文本字段占位符 */
const renderChart = viewInfo => {
  // 刷新富文本字段视图
  updateEmptyValue(viewInfo)
  initCurFieldsChange()
  eventBus.emit('initCurFields-' + element.value.id)
}

/** 将表格阈值配置转换为富文本字段的前景色和背景色 */
const conditionAdaptor = (chart: Chart) => {
  if (!chart || !chart.senior) {
    return
  }
  const { threshold } = parseJson(chart.senior)
  if (!threshold.enable) {
    return
  }
  const res = {}
  const conditions = threshold.tableThreshold ?? []
  if (conditions?.length > 0) {
    for (let i = 0; i < conditions.length; i++) {
      const field = conditions[i]
      let defaultValueColor = 'none'
      let defaultBgColor = 'none'
      res[field.field.name] = {
        color: mappingColorCustom(
          dataRowNameSelectSource.value[field.field.name],
          defaultValueColor,
          field,
          'color'
        ),
        backgroundColor: mappingColorCustom(
          dataRowNameSelectSource.value[field.field.name],
          defaultBgColor,
          field,
          'backgroundColor'
        )
      }
    }
  }
  return res
}

/** 传递给富文本编辑器的画布缩放变量 */
const richTextStyle = computed(() => [{ '--crest-canvas-scale': props.scale }])

/** 组件挂载后初始化编辑器并渲染当前图表数据 */
onMounted(() => {
  viewInit()
})

/** 暴露图表渲染和取数方法给父组件调用 */
defineExpose({
  calcData,
  renderChart
})
</script>

<style lang="less" scoped>
.rich-main-class {
  display: flex;
  font-size: initial;
  width: 100%;
  height: 100%;
  overflow-y: auto !important;
  position: relative;
  div::-webkit-scrollbar {
    width: 0px !important;
    height: 0px !important;
  }
}

:deep(.ol) {
  display: block !important;
  list-style-type: decimal;
  margin-block-start: 1em !important;
  margin-block-end: 1em !important;
  margin-inline-start: 0px !important;
  margin-inline-end: 0px !important;
  padding-inline-start: 40px !important;
}

:deep(ul) {
  display: block !important;
  list-style-type: disc;
  margin-block-start: 1em !important;
  margin-block-end: 1em !important;
  margin-inline-start: 0px !important;
  margin-inline-end: 0px !important;
  padding-inline-start: 40px !important;
}

:deep(li) {
  margin-left: 20px;
  display: list-item !important;
  text-align: -webkit-match-parent !important;
}

:deep(.base-selected) {
  background-color: #b4d7ff;
}

:deep(p) {
  margin: 0px;
  padding: 0px;
}

.edit-model {
  cursor: text;
}

.mceNonEditable {
  background: rgba(59, 130, 246, 0.4);
}

.tox-tinymce-inline {
  left: var(--drawLeft);
  right: var(--drawRight);
}
</style>

<style lang="less">
.tox {
  border-radius: 6px !important;
  border-bottom: 1px solid #ccc !important;
  z-index: 1000;
}
.tox-tbtn {
  height: auto !important;
}
.tox-collection__item-label {
  p {
    color: #1a1a1a !important;
  }
  h1 {
    color: #1a1a1a !important;
  }
  h2 {
    color: #1a1a1a !important;
  }
  h3 {
    color: #1a1a1a !important;
  }
  h4 {
    color: #1a1a1a !important;
  }
  h5 {
    color: #1a1a1a !important;
  }
  h6 {
    color: #1a1a1a !important;
  }
  pre {
    color: #1a1a1a !important;
  }
}

.rich-placeholder {
  position: absolute;
  top: 50%;
  left: 50%;

  color: #646a73;
  font-size: 16px;
  font-style: normal;
  font-weight: 400;
  line-height: 24px;

  transform: translate(-50%, -50%);

  &.rich-placeholder--dark {
    color: #fff;
  }
}

.custom-text-content {
  width: 100%;
  overflow-y: auto;
  outline: none !important;
  border: none !important;
  ol {
    list-style-type: decimal;
  }
}
</style>
