<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router_2'
import router from '@/router'
import crestMark from '@/assets/svg/crest-mark.svg?url'
import { portalResourceApi } from '@/api/dataPortal'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import PortalAccount from './PortalAccount.vue'

interface PortalResource {
  id: string
  name: string
  type: string
}

const route = useRoute()
const appearanceStore = useAppearanceStoreWithOut()
// 当前门户资源详情
const resource = ref<PortalResource | null>(null)
// 门户资源加载状态
const loading = ref(false)
// 门户资源加载错误信息
const error = ref('')
// 预览 iframe 重载键
const frameKey = ref(0)
// 预览 iframe 引用
const frameRef = ref<HTMLIFrameElement>()

// 当前路由资源 ID
const resourceId = computed(() => String(route.params.id || ''))
// 系统展示标题
const systemTitle = computed(() => appearanceStore.getSiteTitle)
// 当前资源预览类型
const dvType = computed(() => resource.value?.type || String(route.query.dvType || 'dataV'))
// 当前资源预览地址
const previewUrl = computed(
  () =>
    `/#/preview?dvId=${encodeURIComponent(resourceId.value)}&dvType=${encodeURIComponent(
      dvType.value
    )}&ignoreParams=true&portal=true`
)

// 加载门户资源详情
const loadResource = async () => {
  if (!resourceId.value) {
    error.value = '资源不存在'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await portalResourceApi(resourceId.value)
    resource.value = res?.data
  } catch {
    resource.value = null
    error.value = '资源不存在或无访问权限'
  } finally {
    loading.value = false
  }
}

// 返回门户首页
const back = () => {
  router.push('/portal')
}

// 重新加载预览 iframe
const reload = () => {
  frameKey.value += 1
}

// 进入预览 iframe 全屏模式
const fullScreen = () => {
  frameRef.value?.requestFullscreen?.()
}

// 监听资源 ID 变化并重新加载资源
watch(resourceId, loadResource)
onMounted(loadResource)
</script>

<template>
  <main class="portal-view">
    <header class="portal-header view-header">
      <button class="brand" type="button" @click="back">
        <img class="brand-mark" :src="crestMark" alt="" aria-hidden="true" />
        <span class="portal-title">{{ systemTitle }}</span>
      </button>
      <div class="resource-title">
        <strong :title="resource?.name || '数据查看'">{{ resource?.name || '数据查看' }}</strong>
      </div>
      <div class="header-actions">
        <el-tooltip effect="dark" content="返回门户" placement="bottom">
          <button class="top-action" type="button" @click="back">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M15 18 9 12l6-6M10 12h10"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </el-tooltip>
        <el-tooltip effect="dark" content="刷新" placement="bottom">
          <button class="top-action" type="button" @click="reload">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M19 8a7 7 0 0 0-12.1-3.9L5 6m0 0V2m0 4h4m-4 6a7 7 0 0 0 12.1 3.9L19 14m0 0v4m0-4h-4"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </el-tooltip>
        <el-tooltip effect="dark" content="全屏" placement="bottom">
          <button class="top-action" type="button" @click="fullScreen">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M8 4H4v4m0 8v4h4m8-16h4v4m0 8v4h-4"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </el-tooltip>
        <PortalAccount />
      </div>
    </header>

    <section class="resource-strip">
      <div class="resource-strip-inner">
        <div>
          <strong>{{ resource?.name || '数据查看' }}</strong>
        </div>
      </div>
    </section>

    <section v-loading="loading" class="view-body">
      <empty-background v-if="error" :description="error" img-type="noneWhite" />
      <iframe
        v-else
        :key="frameKey"
        ref="frameRef"
        class="preview-frame"
        :src="previewUrl"
        title="数据预览"
      />
    </section>
  </main>
</template>

<style scoped lang="less">
.portal-view {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  color: #0f172a;
  background: #f8fafc;
  font-family: var(--crest-font-sans), 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.portal-header {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  column-gap: 24px;
  align-items: center;
  height: 64px;
  padding: 0 clamp(28px, 5vw, 72px);
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  border-bottom: 1px solid #e2e8f0;

  &::before {
    position: absolute;
    inset: 0;
    pointer-events: none;
    content: '';
    background-image: radial-gradient(circle at 1px 1px, #3b6fd0 1px, transparent 0);
    background-size: 20px 20px;
    opacity: 0.035;
  }

  > * {
    position: relative;
    z-index: 1;
  }
}

.brand {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  justify-self: start;
  height: 40px;
  min-width: 0;
  width: max-content;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  top: 0.5px;
}

.brand-mark {
  flex: none;
  display: block;
  width: 34px;
  height: 32px;
  object-fit: contain;
}

.portal-title {
  display: block;
  flex: none;
  color: #0f172a;
  font-family: var(--crest-font-sans), 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 20px;
  font-weight: 700;
  line-height: 32px;
  letter-spacing: 0;
  white-space: nowrap;
}

.resource-title {
  display: flex;
  justify-content: center;
  justify-self: center;
  min-width: 0;
  max-width: min(24vw, 460px);

  strong {
    max-width: 100%;
    overflow: hidden;
    color: #0f172a;
    font-size: 18px;
    font-weight: 700;
    line-height: 26px;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.header-actions {
  flex: none;
  display: flex;
  align-items: center;
  justify-self: end;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;

  :deep(.ed-button) {
    height: 34px;
    padding: 0 12px;
    color: #64748b;
    font-family: var(--crest-font-sans);
    font-size: 14px;
    font-weight: 600;
    background: transparent;
    border: 0;
    border-radius: 8px;
    transition: color 0.14s ease, background 0.14s ease;
  }

  :deep(.ed-button:hover),
  :deep(.ed-button:focus) {
    color: #0f172a;
    background: #f1f5f9;
  }
}

.top-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: #64748b;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(226, 232, 240, 0.82);
  border-radius: 10px;
  transition: color 0.14s ease, background 0.14s ease, border-color 0.14s ease;

  &:hover {
    color: #0f172a;
    background: #ffffff;
    border-color: #cbd5e1;
  }
}

.resource-strip {
  display: none;
  padding: 12px 16px 0;
  background: #f8fafc;
}

.resource-strip-inner {
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;

  div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  strong {
    overflow: hidden;
    color: #0f172a;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #64748b;
    font-size: 12px;
  }
}

.view-body {
  min-height: 0;
  flex: 1;
  padding: 0;
}

.preview-frame {
  width: 100%;
  height: calc(100vh - 64px);
  display: block;
  border: 0;
  background: #ffffff;
}

@media (max-width: 720px) {
  .portal-view {
    overflow: auto;
  }

  .portal-header {
    height: auto;
    min-height: 64px;
    grid-template-columns: minmax(0, 1fr) auto;
    padding: 10px 16px;
  }

  .portal-title {
    max-width: calc(100vw - 168px);
    overflow: hidden;
    font-size: 17px;
    text-overflow: ellipsis;
  }

  .resource-title {
    display: none;
  }

  .header-actions {
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions :deep(.account-name) {
    display: none;
  }

  .resource-strip {
    display: block;
  }

  .preview-frame {
    height: calc(100vh - 144px);
  }
}
</style>
