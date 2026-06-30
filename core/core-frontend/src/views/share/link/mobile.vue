<template>
  <div class="mobile-link-container" v-loading="loading">
    <ErrorTemplate v-if="!loading && disableError" :msg="t('link_ticket.disable_error')" />
    <ErrorTemplate v-else-if="!loading && iframeError" :msg="t('link_ticket.iframe_error')" />
    <ErrorTemplate
      v-else-if="!loading && peRequireError"
      :msg="t('link_ticket.pe_require_error')"
    />
    <ErrorTemplate v-else-if="!loading && !linkExist" :msg="t('link_ticket.link_error')" />
    <ErrorTemplate v-else-if="!loading && linkExp" :msg="t('link_ticket.link_exp_error')" />
    <PwdTips v-else-if="!loading && !pwdValid" />
    <ErrorTemplate
      v-else-if="!loading && !state.ticketValidVO.ticketValid"
      :msg="t('link_ticket.param_error')"
    />
    <ErrorTemplate
      v-else-if="!loading && state.ticketValidVO.ticketExp"
      :msg="t('link_ticket.exp_error')"
    />
    <PreviewCanvas
      v-else
      :class="{ 'hidden-link': loading }"
      ref="pcanvas"
      public-link-status
      :ticket-args="state.ticketValidVO.args"
    />
  </div>
</template>
<script lang="ts" setup>
import { onMounted, nextTick, ref, reactive } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import PreviewCanvas from '@/views/data-visualization/PreviewCanvas.vue'
import { useI18n } from '@/hooks/web/useI18n'
import { ProxyInfo, shareProxy } from './ShareProxy'
import PwdTips from './pwd.vue'
import ErrorTemplate from './ErrorTemplate.vue'
import { useRoute } from 'vue-router_2'
// 标记分享链接是否已被禁用
const disableError = ref(true)
// 标记移动端访问是否缺少必要的嵌入参数
const peRequireError = ref(true)
// 标记分享资源是否存在
const linkExist = ref(false)
// 控制移动端分享页加载状态
const loading = ref(true)
// 标记分享链接是否已过期
const linkExp = ref(false)
// 标记当前 iframe 访问环境是否非法
const iframeError = ref(true)
// 标记分享链接密码是否校验通过
const pwdValid = ref(false)
const dvMainStore = dvMainStoreWithOut()
// 持有移动端预览画布实例
const pcanvas = ref(null)
// 保存当前分享资源类型
const curType = ref('')
const { t } = useI18n()
const route = useRoute()
// 保存分享票据校验结果和透传参数
const state = reactive({
  ticketValidVO: {
    ticketValid: false,
    ticketExp: false,
    args: ''
  }
})
// 初始化移动端分享访问状态并加载可视化资源
onMounted(async () => {
  const proxyInfo = (await shareProxy.loadProxy()) as ProxyInfo
  curType.value = route.query.targetDvType
    ? String(route.query.targetDvType)
    : proxyInfo.type || 'dashboard'
  dvMainStore.setInMobile(true)
  dvMainStore.setMobileInPc(curType.value === 'dashboard')
  if (proxyInfo?.shareDisable) {
    loading.value = false
    disableError.value = true
    return
  }
  disableError.value = false
  if (proxyInfo?.inIframeError) {
    loading.value = false
    iframeError.value = true
    return
  }
  iframeError.value = false
  if (proxyInfo && !proxyInfo.peRequireValid) {
    loading.value = false
    peRequireError.value = true
    return
  }
  peRequireError.value = false
  if (!proxyInfo?.resourceId) {
    loading.value = false
    return
  }
  linkExist.value = true
  linkExp.value = !!proxyInfo.exp
  if (!!proxyInfo.exp) {
    loading.value = false
    return
  }
  pwdValid.value = !!proxyInfo.pwdValid
  if (!proxyInfo.pwdValid) {
    loading.value = false
    return
  }
  state.ticketValidVO = proxyInfo.ticketValidVO
  nextTick(() => {
    const method = pcanvas?.value?.loadCanvasDataAsync
    if (method) {
      const targetDvId = route.query.targetDvId
        ? String(route.query.targetDvId)
        : proxyInfo.resourceId
      const targetDvType = route.query.targetDvType
        ? String(route.query.targetDvType)
        : curType.value
      method(targetDvId, targetDvType, null)
    }
    loading.value = false
  })
})
</script>
<style lang="less" scoped>
.mobile-link-container {
  width: 100vw;
  height: 100vh;
  overflow-y: auto;
  position: relative;
}
</style>
