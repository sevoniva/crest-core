<script lang="ts" setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { FormRules, FormInstance } from 'element-plus-secondary'
import { loginApi, queryPublicKey, ssoStatusApi, ssoTokenApi } from '@/api/login'
import { useCache } from '@/hooks/web/useCache'
import { useAppStoreWithOut } from '@/store/modules/app'
import { CustomPassword } from '@/components/custom-password'
import { useUserStoreWithOut } from '@/store/modules/user'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { rsaEncryp } from '@/utils/encryption'
import router from '@/router'
import { ElMessage } from 'element-plus-secondary'
import { logoutHandler } from '@/utils/logout'
import { PATH_URL } from '@/config/axios/service'
import LoginHeroImage from '@/assets/crest-login-hero.png'
import crestLogo from '@/assets/img/crest-logo-horizontal-192h.png'
import elementResizeDetectorMaker from 'element-resize-detector'
import { cleanPlatformFlag } from '@/utils/utils'
import {
  parseLoginRedirect,
  resolveDefaultLoginPath,
  shouldShowLocalLogin,
  shouldUseEmergencyLogin
} from './loginEntryPolicy.mjs'
import xss from 'xss'
const { wsCache } = useCache()
const appStore = useAppStoreWithOut()
const userStore = useUserStoreWithOut()
const appearanceStore = useAppearanceStoreWithOut()
const { t } = useI18n()
// 控制登录主体在预热页之外的可见性，避免初始化阶段出现表单闪烁
const contentShow = ref(true)
// 登录表单局部加载态，独立于全页登录流程锁定
const loading = ref(false)
// 标识外观资源是否已准备完成，用于控制品牌图和背景图渲染
const axiosFinished = ref(true)
// 控制登录页底部自定义内容区域是否展示
const showFoot = ref(false)
// 控制欢迎标语是否展示，受外观配置覆盖
const showSlogan = ref(true)
// 登录页品牌标识地址，未配置时回退到内置标识
const loginLogoUrl = ref(null)
// 表单下方的轻量提示文本，保留给登录流程反馈
const msg = ref(null)
// 登录页背景图地址，未配置时使用内置背景
const loginImageUrl = ref(null)
// 欢迎标语内容，优先使用外观配置
const slogan = ref(null)
// 已经过白名单过滤的底部富文本内容
const footContent = ref(null)
// 网关或回调带回的登录错误原文，统一在页面初始化时翻译为用户提示
const loginErrorMsg = ref('')
// 控制演示环境提示是否展示
const showDempTips = ref(false)
// 演示提示文案只在开关启用时从外观配置读取
const demoTips = computed(() => {
  if (!showDempTips.value) {
    return ''
  }
  return appearanceStore.getDemoTipsContent || ''
})
// 登录表单模型集中存放账号和密码，便于表单校验和加密提交共享
const state = reactive({
  loginForm: {
    username: '',
    password: ''
  },
  footContent: ''
})
// 单点登录状态由后端下发，失败时回退到本地登录可用
const ssoStatus = reactive({
  enabled: false,
  providerName: '统一身份认证',
  loginButtonText: '单点登录',
  allowLocalLogin: true
})

// 登录表单只校验必填，账号类型差异交给后端认证策略处理
const rules = reactive<FormRules>({
  username: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  password: [{ required: true, message: t('common.required'), trigger: 'blur' }]
})

// 当前本地登录方式，simple 和 ldap 共用同一套表单提交链路
const activeName = ref('simple')
// 本地登录入口是否展示由 SSO 配置和当前路径共同决定
const showLocalLogin = computed(() => {
  return shouldShowLocalLogin(ssoStatus, router.currentRoute.value.path)
})

// 解析登录成功后的落点，确保 redirect 缺失时仍回到当前用户默认入口
const getCurLocation = () => {
  const queryRedirectPath =
    (router.currentRoute.value.query.redirect as string) ||
    resolveDefaultLoginPath(userStore.getBackendAccess)
  return parseLoginRedirect(queryRedirectPath)
}
// 回车提交前先移除输入焦点，避免密码框内部事件继续冒泡触发重复提交
const enterHandler = e => {
  e.target.blur()
  e.stopPropagation()
  handleLogin()
}
// Element Plus 表单实例，登录提交前必须通过它执行同步校验
const formRef = ref<FormInstance | undefined>()
// 全页登录流程锁，覆盖普通登录、SSO 回调和初始化阶段
const duringLogin = ref(true)
// 每次提交前刷新公钥，保证账号密码使用当前服务端密钥加密
const refreshDekey = async () => {
  const res = await queryPublicKey()
  if (res?.data) {
    wsCache.set(appStore.getClientKey, res.data)
  }
}
// 本地账号登录主流程，负责校验、加密、登录态保存和成功后跳转
const handleLogin = () => {
  if (!formRef.value) return
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      const name = state.loginForm.username.trim()
      const pwd = state.loginForm.password
      await refreshDekey()
      const param = {
        name: rsaEncryp(name),
        pwd: rsaEncryp(pwd),
        emergency: shouldUseEmergencyLogin(router.currentRoute.value.path)
      }
      const isLdap = activeName.value === 'ldap'
      if (isLdap) {
        param['origin'] = 1
      }
      duringLogin.value = true
      cleanPlatformFlag()
      loginApi(param)
        .then(async res => {
          const { token, exp, mfa } = res.data
          if (!isLdap && mfa?.enabled) {
            duringLogin.value = false
            return
          }
          saveLoginToken({ token, exp })
          await userStore.setUser()
          router.push(getCurLocation())
        })
        .catch(() => {
          duringLogin.value = false
        })
    }
  })
}
// 保存认证令牌和过期时间，同时记录登录时间用于后续会话刷新判断
const saveLoginToken = (data: any) => {
  const { token, exp } = data || {}
  userStore.setToken(token)
  userStore.setExp(exp)
  userStore.setTime(Date.now())
}
// 判断当前路由是否携带 SSO 回调信息，避免普通登录页重复触发票据消费
const hasSsoCallbackQuery = () => {
  const query = router.currentRoute.value.query
  return !!(query.ssoTicket || query.ssoError)
}
// 加载 SSO 开关和按钮配置，接口失败时保持本地登录兜底
const loadSsoStatus = async () => {
  try {
    const res = await ssoStatusApi()
    Object.assign(ssoStatus, res.data || {})
  } catch {
    Object.assign(ssoStatus, { enabled: false, allowLocalLogin: true })
  }
}
// 发起 SSO 登录跳转时保留原始 redirect，认证完成后继续回到目标页面
const handleSsoLogin = () => {
  cleanPlatformFlag()
  const redirect = (router.currentRoute.value.query.redirect as string) || '/portal'
  const apiBase = PATH_URL.startsWith('./') ? PATH_URL.substring(1) : PATH_URL
  window.location.href = `${apiBase}/sso/login?redirect=${encodeURIComponent(redirect)}`
}
// 消费 SSO 回调票据并转换为本系统登录态，失败时释放页面登录锁
const consumeSsoTicket = async () => {
  const query = router.currentRoute.value.query
  if (query.ssoError) {
    ElMessage.error(decodeURIComponent(query.ssoError as string))
    return false
  }
  if (!query.ssoTicket) {
    return false
  }
  duringLogin.value = true
  try {
    const res = await ssoTokenApi(query.ssoTicket as string)
    saveLoginToken(res.data)
    await userStore.setUser()
    router.push(getCurLocation())
    return true
  } catch {
    duringLogin.value = false
    return false
  }
}
// 预热页加载文案，可由外部回调覆盖
const loadingText = ref('加载中...')
// 登录容器引用用于监听宽度变化，决定背景图是否展示
const loginContainer = ref()
// 登录容器实时宽度，避免小屏下背景图挤压表单
const loginContainerWidth = ref(0)
// 背景图在窄屏下隐藏，保证账号登录区域有足够可用宽度
const showLoginImage = computed<boolean>(() => {
  return !(loginContainerWidth.value < 889)
})

// 预热态用于承接外部自动登录流程，完成前只展示加载容器
const preheat = ref(false)
// 将网关错误码转换成明确的登录失败提示
const showLoginErrorMsg = () => {
  if (!loginErrorMsg.value) {
    return
  }
  if (loginErrorMsg.value.includes('token is empty')) {
    ElMessage.error('token为空！')
    return
  }
  if (loginErrorMsg.value.includes('token is Expired')) {
    ElMessage.error('登录信息已过期，请重新登录！')
    return
  }
  if (loginErrorMsg.value.includes('token is destroyed')) {
    ElMessage.error('登录信息已销毁，请重新登录！')
    return
  }
  if (loginErrorMsg.value.startsWith('user_disable')) {
    ElMessage.error('用户已被禁用，无法登录！')
    return
  }
  if (loginErrorMsg.value.startsWith('permission has been changed')) {
    ElMessage.error('默认组织已发生变更，请重新登录！')
    return
  }
  ElMessage.error(loginErrorMsg.value)
}

// 加载外观配置并过滤底部富文本，避免登录页渲染未受控样式或脚本
const loadArrearance = () => {
  showDempTips.value = appearanceStore.getShowDemoTips
  if (appearanceStore.getBg) {
    loginImageUrl.value = appearanceStore.getBg
  }
  if (appearanceStore.getLogin) {
    loginLogoUrl.value = appearanceStore.getLogin
  }
  if (appearanceStore.getShowSlogan) {
    showSlogan.value = appearanceStore.getShowSlogan === 'true'
  }
  if (appearanceStore.getSlogan) {
    slogan.value = appearanceStore.getSlogan
  }
  if (appearanceStore.getFoot) {
    showFoot.value = appearanceStore.getFoot === 'true'
    if (showFoot.value) {
      const content = appearanceStore.getFootContent
      const myXss = new xss['FilterXSS']({
        css: {
          whiteList: {
            'background-color': true,
            'text-align': true,
            color: true,
            'margin-top': true,
            'margin-bottom': true,
            'line-height': true,
            'box-sizing': true,
            'padding-top': true,
            'padding-bottom': true,
            'font-size': true
          }
        },
        whiteList: {
          ...xss['whiteList'],
          p: ['style'],
          span: ['style']
        }
      })
      footContent.value = myXss.process(content)
    }
  }
}
// 切换本地登录方式，空值统一回退到普通账号登录
const switchTab = (name: string) => {
  activeName.value = name || 'simple'
}
// 外部自动登录流程回填登录方式、预热状态和加载文案
const autoCallback = (param: any) => {
  activeName.value = param.activeName || 'simple'
  preheat.value = param.preheat
  if (param.loadingText) {
    loadingText.value = param.loadingText
  }
}
// 自动登录失败时恢复普通账号登录入口
const handlerFail = () => {
  const param = {
    activeName: 'simple',
    preheat: false
  }
  autoCallback(param)
}
// 页面初始化依次处理外观、SSO 回调、本地登录开关、网关错误和公钥预取
onMounted(async () => {
  loadArrearance()
  if (hasSsoCallbackQuery() && (await consumeSsoTicket())) {
    return
  }
  await loadSsoStatus()
  duringLogin.value = false
  if (localStorage.getItem('CREST-GATEWAY-FLAG')) {
    const msg = localStorage.getItem('CREST-GATEWAY-FLAG')
    loginErrorMsg.value = decodeURIComponent(msg)
    showLoginErrorMsg()
    localStorage.removeItem('CREST-GATEWAY-FLAG')
    logoutHandler(true)
  }
  refreshDekey()
  const erd = elementResizeDetectorMaker()
  erd.listenTo(loginContainer.value, () => {
    nextTick(() => {
      loginContainerWidth.value = loginContainer.value?.offsetWidth
    })
  })
})
</script>

<template>
  <div
    v-if="preheat"
    ref="loginContainer"
    class="preheat-container"
    v-loading="true"
    :element-loading-text="loadingText"
    element-loading-background="#F5F6F7"
  />
  <div v-show="contentShow" class="login-background" v-loading="duringLogin">
    <div class="login-container" ref="loginContainer">
      <div class="login-image-content" v-loading="!axiosFinished" v-if="showLoginImage">
        <el-image
          v-if="axiosFinished"
          class="login-image"
          fit="cover"
          :src="loginImageUrl || LoginHeroImage"
        />
      </div>
      <div class="login-form-content" v-loading="loading">
        <div class="login-form-center">
          <el-form
            ref="formRef"
            :model="state.loginForm"
            :rules="rules"
            size="default"
            :disabled="preheat"
          >
            <div class="login-logo">
              <img v-if="axiosFinished" :src="loginLogoUrl || crestLogo" alt="Crest" />
            </div>
            <div v-if="showSlogan" class="login-welcome">
              {{ slogan || t('system.available_to_everyone') }}
            </div>
            <div class="login-form border-radius-12">
              <div
                class="default-login-tabs"
                v-if="showLocalLogin && (activeName === 'simple' || activeName === 'ldap')"
              >
                <div class="login-form-title">
                  <span>{{
                    activeName === 'ldap' ? t('login.ldap_login') : t('login.account_login')
                  }}</span>
                </div>
                <el-form-item class="login-form-item login-input-module" prop="username">
                  <el-input
                    v-model="state.loginForm.username"
                    :placeholder="`${t('common.account')}${
                      activeName === 'simple' ? '/' + t('commons.email') : ''
                    }`"
                    autofocus
                  />
                </el-form-item>
                <el-form-item class="login-input-module" prop="password">
                  <CustomPassword
                    v-model="state.loginForm.password"
                    :placeholder="t('common.pwd')"
                    show-password
                    maxlength="30"
                    show-word-limit
                    autocomplete="new-password"
                    @keypress.enter.stop="enterHandler"
                  />
                </el-form-item>
                <div class="login-btn">
                  <el-button
                    type="primary"
                    class="submit"
                    size="default"
                    :disabled="duringLogin"
                    @click="handleLogin"
                  >
                    {{ t('login.btn') }}
                  </el-button>
                  <div v-if="showDempTips" class="demo-tips">
                    <span>{{ demoTips }}</span>
                  </div>
                </div>
              </div>
              <div
                v-if="ssoStatus.enabled"
                :class="['sso-login-block', { 'only-sso': !showLocalLogin }]"
              >
                <el-divider v-if="showLocalLogin" class="sso-divider">或</el-divider>
                <div v-if="!showLocalLogin" class="login-form-title">
                  <span>单点登录</span>
                </div>
                <el-button
                  :type="showLocalLogin ? 'default' : 'primary'"
                  class="sso-submit"
                  size="default"
                  :disabled="duringLogin"
                  @click="handleSsoLogin"
                >
                  <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path
                      d="M10 2.5 4.75 4.55v4.2c0 3.25 2.1 6.35 5.25 7.4 3.15-1.05 5.25-4.15 5.25-7.4v-4.2L10 2.5Z"
                    />
                    <path d="m7.75 10.05 1.45 1.45 3.15-3.35" />
                  </svg>
                  <span>单点登录</span>
                </el-button>
              </div>
            </div>

            <div class="login-msg">
              {{ msg }}
            </div>
          </el-form>
        </div>
        <!-- nosemgrep: javascript.vue.security.audit.xss.templates.avoid-v-html.avoid-v-html -->
        <div v-if="showFoot" class="dynamic-login-foot" v-html="footContent" />
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.preheat-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  position: absolute;
  z-index: 100;
}
.login-background {
  background-color: #f5f7fa;
  height: 100vh;
  width: 100vw;
}

.login-container {
  width: 100%;
  height: 100%;
  background-color: var(--ContentBG, #ffffff);
  display: flex;
  .login-image-content {
    overflow: hidden;
    height: 100%;
    width: 40%;
    min-width: 400px;
  }

  .login-form-content {
    position: relative;
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;

    .login-form-center {
      width: 480px;
    }
  }
  .login-logo {
    text-align: center;
    img {
      width: auto;
      max-height: 52px;
      @media only screen and (max-width: 1280px) {
        width: auto;
        max-height: 52px;
      }
    }
  }

  .login-title {
    margin-top: 50px;
    font-size: 32px;
    letter-spacing: 0;
    text-align: center;
    color: #999999;

    @media only screen and (max-width: 1280px) {
      margin-top: 20px;
    }
  }

  .login-border {
    height: 2px;
    margin: 20px auto 20px;
    position: relative;
    width: 80px;
    background: var(--ed-color-primary);
    @media only screen and (max-width: 1280px) {
      margin: 20px auto 20px;
    }
  }

  .login-welcome {
    text-align: center;
    margin-top: 8px;
    color: #646a73;
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
    line-height: 20px;
    word-wrap: break-word;
  }

  .demo-tips {
    position: absolute;
    font-size: 18px;
    color: #f56c6c;
    letter-spacing: 0;
    line-height: 18px;
    text-align: center;
    top: 120px;
    @media only screen and (max-width: 1280px) {
      margin-top: 20px;
    }
  }

  .login-form {
    margin-top: 40px;
    padding: 40px;
    padding-top: 20px;
    box-shadow: 0px 6px 24px rgba(31, 35, 41, 0.08);
    border: 1px solid #dee0e3;
    border-radius: 6px;

    .login-input-module {
      width: 100%;
      :deep(.ed-input) {
        height: 40px;
        line-height: 40px;
      }
    }

    .login-form-item {
      margin-top: 24px;
    }

    .ed-form-item--default {
      margin-bottom: 24px;
    }
    .login-form-title {
      margin-top: 0;
      color: #1f2329;
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 20px;
      font-weight: 500;
      line-height: 28px;
      text-align: left;
    }

    .sso-login-block {
      margin-top: 18px;

      &.only-sso {
        margin-top: 0;

        .sso-submit {
          margin-top: 24px;
          color: #ffffff;

          svg {
            stroke: #ffffff;
          }
        }
      }

      .sso-divider {
        margin: 20px 0 16px;
      }

      .sso-submit {
        width: 100%;
        height: 40px;
        line-height: 40px;
        color: #0f172a;
        border-color: #dbe4f0;
        background: #ffffff;
        font-weight: 500;

        svg {
          width: 16px;
          height: 16px;
          margin-right: 8px;
          stroke: #3b82f6;
          stroke-width: 1.8;
          stroke-linecap: round;
          stroke-linejoin: round;
        }

        &:hover,
        &:focus {
          color: #3b82f6;
          border-color: #3b82f6;
          background: #f8fbff;
        }
      }
    }
  }

  :deep(.ed-divider__text) {
    color: #8f959e;
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
    line-height: 20px;
    padding: 0 8px;
  }

  .login-btn {
    position: relative;
    margin-bottom: 0;
    .submit {
      width: 100%;
      height: 40px;
      line-height: 40px;
    }
  }

  .login-msg {
    margin-top: 10px;
    padding: 0 40px;
    color: #f56c6c;
    text-align: center;
  }

  .login-image {
    //object-fit: cover;
    //background: url(../../assets/login-desc-de.png);
    background-size: 100% 100%;
    width: 100%;
    height: 100%;
  }
  .login-image-de {
    background-size: cover;
    width: 100%;
    height: 520px;
    @media only screen and (max-width: 1280px) {
      height: 380px;
    }
  }
}
.dynamic-login-foot {
  visibility: visible;
  width: 100%;
  position: absolute;
  z-index: 302;
  bottom: 0;
  left: 0;
  height: auto;
  padding-top: 1px;
  zoom: 1;
  margin: 0;
}

.login-logo-icon {
  width: auto;
  height: 52px;
}
</style>
