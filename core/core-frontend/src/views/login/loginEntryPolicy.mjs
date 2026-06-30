export const NORMAL_LOGIN_PATH = '/login'
export const ADMIN_LOGIN_PATH = '/admin-login'
export const PORTAL_HOME = '/portal'
export const BACKEND_HOME = '/workbranch/index'

const loginPaths = new Set([NORMAL_LOGIN_PATH, ADMIN_LOGIN_PATH])

// 控制弹窗、面板或区域的展示状态
export const shouldShowLocalLogin = (ssoStatus, path) => {
  return !ssoStatus.enabled || ssoStatus.allowLocalLogin || loginPaths.has(path)
}

// 衔接当前组件交互和状态同步
export const shouldUseEmergencyLogin = path => {
  return loginPaths.has(path)
}

// 衔接当前组件交互和状态同步
export const resolveDefaultLoginPath = backendAccess => {
  return backendAccess ? BACKEND_HOME : PORTAL_HOME
}

// 衔接当前组件交互和状态同步
export const parseLoginRedirect = redirectPath => {
  let queryRedirectPath = redirectPath || PORTAL_HOME
  try {
    let decodedRedirectPath = queryRedirectPath
    for (let i = 0; i < 5; i++) {
      const nextPath = decodeURIComponent(decodedRedirectPath)
      if (nextPath === decodedRedirectPath) {
        break
      }
      decodedRedirectPath = nextPath
    }
    queryRedirectPath = decodedRedirectPath
  } catch {
    // Keep the original path when an invalid escape sequence is passed in.
  }
  const [path, search = ''] = queryRedirectPath.split('?')
  const query = {}
  new URLSearchParams(search).forEach((value, key) => {
    query[key] = value
  })
  return {
    path,
    query
  }
}
