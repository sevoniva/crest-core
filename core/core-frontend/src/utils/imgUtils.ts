import html2canvas from 'html2canvas'
import JsPDF from 'jspdf'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useEmbedded } from '@/store/modules/embedded'
import { storeToRefs } from 'pinia'
import { resourceBase64 } from '@/api/staticResource'
import FileSaver from 'file-saver'
import { deepCopy } from '@/utils/utils'
import { domToPng } from 'modern-screenshot'
import { initCanvasDataPrepare } from '@/utils/canvasUtils'
/** 嵌入式仓库提供外部访问基础地址 */
const embeddedStore = useEmbedded()
/** 主画布仓库提供导出所需的画布、组件和视图数据 */
const dvMainStore = dvMainStoreWithOut()
/** 导出流程依赖的画布样式、组件、视图数据和资源信息 */
const { canvasStyleData, componentData, canvasViewInfo, canvasViewDataInfo, dvInfo } =
  storeToRefs(dvMainStore)
/** API 基础路径，用于拼接静态资源访问地址 */
const basePath = import.meta.env.VITE_API_BASEPATH

/** 规范化接口路径中重复的 api 前缀 */
export function formatterUrl(url: string) {
  return url.replace('//api/v1', '/api/v1')
}
/** 将静态资源路径转换为当前部署环境可访问的图片地址 */
export function imgUrlTrans(url) {
  if (url) {
    if (typeof url === 'string' && url.indexOf('static-resource') > -1) {
      const rawUrl = url
        ? (basePath.endsWith('/') ? basePath.substring(0, basePath.length - 1) : basePath) + url
        : null
      return formatterUrl(
        embeddedStore.baseUrl
          ? `${embeddedStore.baseUrl}${
              rawUrl.startsWith('/api') ? rawUrl.slice(5) : rawUrl
            }`.replace('com//', 'com/')
          : rawUrl
      )
    } else {
      return formatterUrl(url.replace('com//', 'com/'))
    }
  }
}

/** 准备模板导出的基础画布数据，满屏大屏会重新读取适配后的数据 */
function prePareTemplateBaseData(dvId, callback) {
  if (dvInfo.value.type === 'dataV' && canvasStyleData.value.screenAdaptor === 'full') {
    initCanvasDataPrepare(
      dvId,
      { busiFlag: 'dataV', resourceTable: 'core' },
      function ({ canvasDataResult, canvasStyleResult }) {
        callback({
          canvasDataResult: JSON.stringify(canvasDataResult),
          canvasStyleResult: JSON.stringify(canvasStyleResult)
        })
      }
    )
  } else {
    callback({
      canvasDataResult: JSON.stringify(componentData.value),
      canvasStyleResult: JSON.stringify(canvasStyleData.value)
    })
  }
}

/** 导出应用模板或普通模板，包含缩略图、静态资源和动态数据快照 */
export function download2AppTemplate(downloadType, canvasDom, name, attachParams, callBack?) {
  try {
    findStaticSource(function (staticResource) {
      html2canvas(canvasDom).then(canvas => {
        const canvasViewDataTemplate = deepCopy(canvasViewInfo.value)
        Object.keys(canvasViewDataTemplate).forEach(viewId => {
          canvasViewDataTemplate[viewId].data = canvasViewDataInfo.value[viewId]
        })
        const snapshot = canvas.toDataURL('image/jpeg', 0.1) // 0.1是图片质量
        const templateName = attachParams?.appName ? attachParams.appName : name
        if (snapshot !== '') {
          prePareTemplateBaseData(
            dvInfo.value.id,
            function ({ canvasDataResult, canvasStyleResult }) {
              const templateInfo = {
                name: templateName,
                templateType: 'self',
                snapshot: snapshot,
                dvType: dvInfo.value.type,
                nodeType: downloadType,
                version: 3,
                canvasStyleData: canvasStyleResult,
                componentData: canvasDataResult,
                dynamicData: JSON.stringify(canvasViewDataTemplate),
                staticResource: JSON.stringify(staticResource || {}),
                appData: attachParams ? JSON.stringify(attachParams) : null
              }
              const blob = new Blob([JSON.stringify(templateInfo)], { type: '' })
              if (downloadType === 'template') {
                FileSaver.saveAs(blob, name + '.crest')
              } else if (downloadType === 'app') {
                FileSaver.saveAs(blob, templateName + '.crest')
              }
              if (callBack) {
                callBack()
              }
            }
          )
        } else if (callBack) {
          callBack()
        }
      })
    })
  } catch (e) {
    if (callBack) {
      callBack()
    }
    console.error(e)
  }
}

/** 使用 html2canvas 导出画布图片或 PDF */
export function downloadCanvas(type, canvasDom, name, callBack?) {
  // const canvasDom = document.getElementById(canvasId)
  if (canvasDom) {
    html2canvas(canvasDom)
      .then(canvas => {
        const dom = document.body.appendChild(canvas)
        dom.style.display = 'none'
        document.body.removeChild(dom)
        const dataUrl = dom.toDataURL('image/png', 1)
        if (type === 'img') {
          const a = document.createElement('a')
          a.setAttribute('download', name)
          a.href = dataUrl
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
        } else {
          const contentWidth = canvasDom.offsetWidth
          const contentHeight = canvasDom.offsetHeight
          const lp = contentWidth > contentHeight ? 'l' : 'p'
          const PDF = new JsPDF(lp, 'pt', [contentWidth, contentHeight])
          PDF.addImage(dataUrl, 'PNG', 0, 0, contentWidth, contentHeight)
          PDF.save(name + '.pdf')
        }
        if (callBack) {
          callBack()
        }
      })
      .catch(error => {
        console.error('oops, something went wrong!', error)
        if (callBack) {
          callBack()
        }
      })
  }
}

/** 使用 modern-screenshot 导出高清画布图片或 PDF */
export function downloadCanvas2(type, canvasDom, name, callBack?) {
  domToPng(canvasDom, { scale: 3 })
    .then(dataUrl => {
      if (type === 'img') {
        const a = document.createElement('a')
        a.setAttribute('download', name + '.png')
        a.href = dataUrl
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
      } else {
        const contentWidth = canvasDom.offsetWidth
        const contentHeight = canvasDom.offsetHeight
        const lp = contentWidth > contentHeight ? 'l' : 'p'
        const PDF = new JsPDF(lp, 'pt', [contentWidth, contentHeight])
        PDF.addImage(dataUrl, 'PNG', 0, 0, contentWidth, contentHeight)
        PDF.save(name + '.pdf')
      }
      if (callBack) {
        callBack()
      }
    })
    .catch(error => {
      if (callBack) {
        callBack()
      }
      console.error('oops, something went wrong!', error)
    })
}

/** 将 dataURL 图片数据转换为 Blob 对象 */
export function dataURLToBlob(dataUrl) {
  // IE 图片转格式
  const arr = dataUrl.split(',')
  const mime = arr[0].match(/:(.*?);/)[1]
  const bStr = atob(arr[1])
  let n = bStr.length
  const u8arr = new Uint8Array(n)
  while (n--) {
    u8arr[n] = bStr.charCodeAt(n)
  }
  return new Blob([u8arr], { type: mime })
}

/** 递归收集组件、分组和 Tabs 内引用的静态资源路径 */
function findStaticSourceInner(componentDataInfo, staticResource) {
  componentDataInfo?.forEach(item => {
    if (
      typeof item.commonBackground.outerImage === 'string' &&
      item.commonBackground.outerImage.indexOf('static-resource') > -1
    ) {
      staticResource.push(item.commonBackground.outerImage)
    }
    if (
      item.component === 'Picture' &&
      item.propValue['url'] &&
      typeof item.propValue['url'] === 'string' &&
      item.propValue['url'].indexOf('static-resource') > -1
    ) {
      staticResource.push(item.propValue['url'])
    } else if (
      item.component === 'UserView' &&
      item.innerType === 'picture-group' &&
      item.propValue['urlList'] &&
      item.propValue['urlList'].length > 0
    ) {
      item.propValue['urlList'].forEach(urlInfo => {
        if (urlInfo.url.indexOf('static-resource') > -1) {
          staticResource.push(urlInfo.url)
        }
      })
    } else if (item.component === 'Group') {
      findStaticSourceInner(item.propValue, staticResource)
    } else if (item.component === 'Tabs') {
      item.propValue.forEach(tabItem => {
        findStaticSourceInner(tabItem.componentData, staticResource)
      })
    }
  })
}

// 解析静态文件
/** 解析当前画布依赖的静态资源并转换为 base64 数据 */
export function findStaticSource(callBack) {
  const staticResource = []
  // 系统背景文件
  if (
    typeof canvasStyleData.value.background === 'string' &&
    canvasStyleData.value.background.indexOf('static-resource') > -1
  ) {
    staticResource.push(canvasStyleData.value.background)
  }
  findStaticSourceInner(componentData.value, staticResource)
  if (staticResource.length > 0) {
    try {
      resourceBase64({ resourcePathList: staticResource }).then(rsp => {
        callBack(rsp.data)
      })
    } catch (e) {
      console.error('resourceBase64 error', e)
      callBack()
    }
  } else {
    setTimeout(() => {
      callBack()
    }, 0)
  }
}
