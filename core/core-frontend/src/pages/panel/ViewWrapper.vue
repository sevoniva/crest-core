<script lang="ts" setup>
import { ref, onBeforeMount, reactive, inject, nextTick, onMounted } from 'vue'
import { initCanvasData, onInitReady } from '@/utils/canvasUtils'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { useEmbedded } from '@/store/modules/embedded'
import { check } from '@/utils/CrossPermission'
import { useCache } from '@/hooks/web/useCache'
import { getOuterParamsInfo } from '@/api/visualization/outerParams'
import { ElMessage } from 'element-plus-secondary'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useI18n } from '@/hooks/web/useI18n'
import EmptyBackground from '../../components/empty-background/src/EmptyBackground.vue'
import exeRequest from '@/config/axios'
/** 本地缓存用于读取嵌入式权限缓存 */
const { wsCache } = useCache()
/** 交互仓库用于写入嵌入式资源校验上下文 */
const interactiveStore = interactiveStoreWithOut()
/** 嵌入式仓库承接外部容器传入的资源参数 */
const embeddedStore = useEmbedded()
/** div 嵌入时由上层注入的参数对象 */
const embeddedParamsDiv = inject('embeddedParams') as Record<string, any>
/** 当前要预览的组件配置 */
const config = ref<Record<string, any>>({})
/** 当前要预览的图表视图信息 */
const viewInfo = ref()
/** 图表放大弹窗实例 */
const userViewEnlargeRef = ref()
/** 主画布仓库用于写入外部参数过滤器 */
const dvMainStore = dvMainStoreWithOut()
/** 国际化翻译函数，用于外部参数解析错误提示 */
const { t } = useI18n()
/** 原生宿主的交互调用句柄 */
const openHandler = ref(null)
/** 嵌入式单图预览状态，缓存画布数据、资源信息和缩放比例 */
const state = reactive({
  canvasDataPreview: null,
  canvasStylePreview: null,
  canvasViewInfoPreview: null,
  dvInfo: null,
  dvId: null,
  suffixId: 'common',
  initState: true,
  scale: 100
})

/** 统一选择 div 注入参数或全局嵌入式仓库参数 */
const embeddedParams = embeddedParamsDiv?.chartId ? embeddedParamsDiv : embeddedStore

/** 处理外部附加参数消息，只接收目标资源和后缀匹配的消息 */
const winMsgHandle = event => {
  const msgInfo = event.data

  // 校验targetSourceId
  if (
    msgInfo &&
    msgInfo.type === 'attachParams' &&
    msgInfo.targetSourceId === state.dvId + '' &&
    (!msgInfo.suffixId || msgInfo.suffixId === state.suffixId)
  ) {
    const attachParams = msgInfo.params
    state.initState = false
    dvMainStore.addOuterParamsFilter(attachParams, state.canvasDataPreview, 'outer')
    state.initState = true
  }
}

/** 校验嵌入式资源查看权限，非集成环境默认放行 */
const checkPer = async resourceId => {
  if (!window.CrestBi || !resourceId) {
    return true
  }
  const request = { busiFlag: embeddedParams.busiFlag, resourceTable: 'core' }
  await interactiveStore.setInteractive(request)
  const key = embeddedParams.busiFlag === 'dataV' ? 'screen-weight' : 'panel-weight'
  return check(wsCache.get(key), resourceId, 1)
}
/** 挂载前完成权限校验、外部参数解析和单图画布数据初始化 */
onBeforeMount(async () => {
  const checkResult = await checkPer(embeddedParams.dvId)
  if (!checkResult) {
    return
  }
  state.dvId = embeddedParams.dvId
  state.suffixId = embeddedParams.suffixId || 'common'
  window.addEventListener('message', winMsgHandle)

  let tokenInfo = null
  if (embeddedStore.getToken && !Object.keys((tokenInfo = embeddedStore.getTokenInfo)).length) {
    const res = await exeRequest.get({ url: '/embedded/token-args' })
    embeddedStore.setTokenInfo(res.data)
    tokenInfo = embeddedStore.getTokenInfo
  }

  // 添加外部参数
  let attachParams
  const outerParamsResp = await getOuterParamsInfo(embeddedParams.dvId)
  if (!outerParamsResp?.data) {
    return
  }
  dvMainStore.setNowPanelOuterParamsInfoV2(outerParamsResp.data, embeddedParams.dvId)

  // div嵌入
  if (embeddedParams.outerParams) {
    try {
      const outerPramsParse = JSON.parse(embeddedParams.outerParams) as Record<string, any>
      attachParams = outerPramsParse.attachParams
      dvMainStore.setEmbeddedCallBack(outerPramsParse.callBackFlag || 'no')
    } catch (e) {
      console.error(e)
      ElMessage.error(t('visualization.outer_param_decode_error'))
      return
    }
  }
  if (tokenInfo && Object.keys(tokenInfo).length) {
    attachParams = Object.assign({}, attachParams, tokenInfo)
  }
  const chartId = embeddedParams?.chartId

  initCanvasData(
    embeddedParams.dvId,
    { busiFlag: embeddedParams.busiFlag },
    function ({ canvasDataResult, canvasStyleResult, dvInfo, canvasViewInfoPreview }) {
      state.canvasDataPreview = canvasDataResult
      state.canvasStylePreview = canvasStyleResult
      state.canvasViewInfoPreview = canvasViewInfoPreview
      state.dvInfo = dvInfo
      state.initState = false
      dvMainStore.addOuterParamsFilter(attachParams, canvasDataResult)
      state.initState = true

      viewInfo.value = canvasViewInfoPreview[chartId]
      ;(
        (canvasDataResult as unknown as Array<{
          id: string
          component: string
          propValue: Array<{ id: string; componentData?: Array<{ id: string }> }>
        }>) || []
      ).some(ele => {
        if (ele.id === chartId) {
          config.value = ele
          return true
        } else if (ele.component === 'Group') {
          return (ele.propValue || []).some(itx => {
            if (itx.id === chartId) {
              config.value = itx
              return true
            }
            return false
          })
        } else if (ele.component === 'Tabs') {
          ele.propValue.forEach(tabItem => {
            return (tabItem.componentData || []).some(itx => {
              if (itx.id === chartId) {
                config.value = itx
                return true
              }
              return false
            })
          })
        }
        return false
      })
      nextTick(() => {
        onInitReady({ resourceId: chartId })
      })
      resetLayout()
    }
  )
})
/** 打开单图放大弹窗 */
const userViewEnlargeOpen = opt => {
  userViewEnlargeRef.value.dialogInit(state.canvasStylePreview, viewInfo.value, config.value, opt)
}

/** 将图表点击参数发送给原生宿主或父窗口 */
const onPointClick = param => {
  try {
    if (window['crest-embedded-host'] && openHandler?.value) {
      const pm = {
        methodName: 'embeddedInteractive',
        args: {
          eventName: 'crest_inner_params',
          args: param
        }
      }
      openHandler.value.invokeMethod(pm)
    } else {
      const targetPm = {
        type: 'crest-embedded-interactive',
        eventName: 'crest_inner_params',
        args: param
      }
      window.parent.postMessage(targetPm, '*')
    }
  } catch (e) {
    console.warn('crest_inner_params send error')
  }
}
/** 单图预览容器引用，用于计算缩放比例 */
const previewViewCanvas = ref(null)

/** 根据容器宽度和原始组件宽度重算嵌入式单图缩放比例 */
const resetLayout = () => {
  nextTick(() => {
    if (previewViewCanvas.value) {
      // div 容器获取 tableBox.value.clientWidth
      const widthSource = state.canvasDataPreview?.find(item => item.id === embeddedParams?.chartId)
        ?.style.width
      if (widthSource) {
        let canvasWidth = previewViewCanvas.value.clientWidth
        state.scale = (canvasWidth * state.canvasStylePreview.scale) / widthSource
      }
    }
  })
}
/** 页面挂载后补算一次布局，兼容容器宽度延迟稳定的场景 */
onMounted(() => {
  resetLayout()
})
</script>

<template>
  <div class="crest-view-wrapper" ref="previewViewCanvas" v-if="!!config && state.initState">
    <ComponentWrapper
      style="width: 100%; height: 100%"
      :scale="state.scale"
      :view-info="viewInfo"
      :config="config"
      :canvas-style-data="state.canvasStylePreview"
      :dv-info="state.dvInfo"
      :canvas-view-info="state.canvasViewInfoPreview"
      @userViewEnlargeOpen="userViewEnlargeOpen"
      @onPointClick="onPointClick"
      :suffix-id="state.suffixId"
    />
    <user-view-enlarge ref="userViewEnlargeRef"></user-view-enlarge>
  </div>
  <empty-background v-if="!state.initState" description="参数不能为空" img-type="noneWhite" />
</template>

<style lang="less" scoped>
.crest-view-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>
