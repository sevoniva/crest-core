<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus-secondary'
import router from '@/router'
import { ssoTokenApi } from '@/api/login'
import { useUserStoreWithOut } from '@/store/modules/user'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { cleanPlatformFlag } from '@/utils/utils'
import crestLogo from '@/assets/img/crest-logo-horizontal-192h.png'

// 单点登录成功后需要刷新用户会话状态
const userStore = useUserStoreWithOut()
// 权限缓存需要和用户会话一起清空，避免沿用旧菜单
const permissionStore = usePermissionStoreWithOut()
// 交互状态包含前端缓存的仪表板上下文，登录切换时需要重置
const interactiveStore = interactiveStoreWithOut()
// 回调页状态文案用于提示当前单点登录处理进度
const statusText = ref('正在完成单点登录')

// 统一读取路由查询参数的首个字符串值
const firstQueryValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value[0] || ''
  }
  return typeof value === 'string' ? value : ''
}

// 解析单点登录完成后的跳转地址，并阻止外部地址跳转
const parseRedirectLocation = (redirectValue: unknown) => {
  let target = firstQueryValue(redirectValue) || '/workbranch/index'
  try {
    for (let i = 0; i < 5; i++) {
      const nextTarget = decodeURIComponent(target)
      if (nextTarget === target) {
        break
      }
      target = nextTarget
    }
  } catch {
    target = '/workbranch/index'
  }
  if (/^(https?:)?\/\//i.test(target)) {
    target = '/workbranch/index'
  }
  const [path, search = ''] = target.split('?')
  const query: Record<string, string> = {}
  new URLSearchParams(search).forEach((value, key) => {
    query[key] = value
  })
  return {
    path: path.startsWith('/') ? path : `/${path}`,
    query
  }
}

// 清理本地会话和平台标识，确保新票据从干净状态写入
const resetClientSession = () => {
  userStore.clear()
  userStore.$reset()
  permissionStore.clear()
  permissionStore.$reset()
  interactiveStore.clear()
  interactiveStore.$reset()
  cleanPlatformFlag()
}

// 使用单点登录票据换取系统令牌，并跳回原始业务页面
const finishLogin = async () => {
  const query = router.currentRoute.value.query
  const error = firstQueryValue(query.error || query.ssoError)
  if (error) {
    ElMessage.error(decodeURIComponent(error))
    await router.replace({
      path: '/login',
      query: { redirect: firstQueryValue(query.redirect) || '/workbranch/index' }
    })
    return
  }

  const ticket = firstQueryValue(query.ticket || query.ssoTicket)
  if (!ticket) {
    ElMessage.error('单点登录回调缺少票据，请重新登录')
    await router.replace('/login')
    return
  }

  try {
    resetClientSession()
    const res = await ssoTokenApi(ticket)
    const { token, exp } = res.data || {}
    if (!token) {
      throw new Error('单点登录票据无效')
    }
    userStore.setToken(token)
    userStore.setExp(exp)
    userStore.setTime(Date.now())
    await userStore.setUser()
    await router.replace(parseRedirectLocation(query.redirect))
  } catch (e) {
    statusText.value = '单点登录失败'
    ElMessage.error(e instanceof Error ? e.message : '单点登录失败，请重新登录')
    await router.replace({
      path: '/login',
      query: { redirect: firstQueryValue(query.redirect) || '/workbranch/index' }
    })
  }
}

// 页面加载后立即处理身份提供方回调
onMounted(finishLogin)
</script>

<template>
  <div class="sso-callback">
    <div class="sso-panel">
      <img :src="crestLogo" alt="Crest" />
      <div class="sso-spinner" aria-hidden="true" />
      <h1>{{ statusText }}</h1>
      <p>请稍候，系统正在验证企业身份。</p>
    </div>
  </div>
</template>

<style lang="less" scoped>
.sso-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  color: #0f172a;
}

.sso-panel {
  width: min(420px, calc(100vw - 48px));
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);

  img {
    height: 38px;
    margin-bottom: 28px;
  }

  h1 {
    margin: 18px 0 8px;
    font-size: 20px;
    font-weight: 700;
    line-height: 28px;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 14px;
    line-height: 22px;
  }
}

.sso-spinner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 3px solid #dbeafe;
  border-top-color: #3b82f6;
  animation: sso-spin 0.9s linear infinite;
}

@keyframes sso-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
