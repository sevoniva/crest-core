import SockJS from 'sockjs-client/dist/sockjs.min.js'
import Stomp from 'stompjs'
import { useCache } from '@/hooks/web/useCache'
import { useEmitt } from '@/hooks/web/useEmitt'
// 复用应用缓存读取登录态和桌面端标识
const { wsCache } = useCache()
// 当前 STOMP 客户端实例，断线重连时会复用或重建
let stompClient: Stomp.Client

// WebSocket 插件在应用启动时安装并维护订阅连接
export default {
  install() {
    // 统一维护后端主题和前端事件名的映射
    const channels = [
      {
        topic: '/task-export-topic',
        event: 'task-export-topic-call'
      },
      {
        topic: '/report-notice',
        event: 'report-notice-call'
      }
    ]
    // 登录态包含桌面端免登录场景和普通用户令牌场景
    function isLoginStatus() {
      if (wsCache.get('app.desktop')) {
        return true
      }
      return wsCache.get('user.token') && wsCache.get('user.uid')
    }

    // 建立 STOMP 连接并订阅当前用户的通知主题
    function connection() {
      if (!isLoginStatus()) {
        return
      }
      if (stompClient && stompClient.connected) {
        return
      }
      let prefix = '/'
      if (window.CrestBi?.baseUrl) {
        prefix = window.CrestBi.baseUrl
      } else {
        const path = location.pathname.endsWith('/')
          ? location.pathname
          : location.pathname.replace(/\/[^/]*$/, '/')
        prefix = location.origin + path
      }
      if (!prefix.endsWith('/')) {
        prefix += '/'
      }
      const userId = wsCache.get('app.desktop') ? 1 : wsCache.get('user.uid')
      const socket = new SockJS(prefix + 'websocket?userId=' + userId)
      stompClient = Stomp.over(socket)
      const heads = {
        userId: userId
      }
      stompClient.connect(
        heads,
        () => {
          channels.forEach(channel => {
            stompClient.subscribe('/user/' + userId + channel.topic, res => {
              res && res.body && useEmitt().emitter.emit(channel.event, res.body)
            })
          })
        },
        error => {
          disconnect()
          console.error('连接失败: ' + error)
        }
      )
    }

    // 主动断开当前 STOMP 连接并清空客户端引用
    function disconnect() {
      if (stompClient && stompClient.connected) {
        stompClient.disconnect(
          function () {
            return undefined
          },
          function (error) {
            console.warn('断开连接失败: ' + error)
          }
        )
      }
      stompClient = null
    }

    // 初始化连接并周期性检查登录态和连接状态
    function initialize() {
      connection()
      const timeInterval = setInterval(() => {
        if (!isLoginStatus()) {
          disconnect()
          return
        }
        if (!stompClient || !stompClient.connected) {
          connection()
        }
      }, 5000)
    }
    initialize()
  }
}
