<script lang="ts" setup>
import userImg from '@/assets/svg/user-img.svg'
import icon_expandDown_filled from '@/assets/svg/icon_expand-down_filled.svg'
import { computed, ref, unref } from 'vue'
import { ElMessage } from 'element-plus-secondary'
import { Icon } from '@/components/icon-custom'
import { useUserStoreWithOut } from '@/store/modules/user'
import { logoutApi } from '@/api/login'
import { querySystemAbout } from '@/api/about'
import type { SystemAboutInfo } from '@/api/about'
import { logoutHandler } from '@/utils/logout'
import { useI18n } from '@/hooks/web/useI18n'
import LangSelector from './LangSelector.vue'
import router from '@/router'
import { useCache } from '@/hooks/web/useCache'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
const appearanceStore = useAppearanceStoreWithOut()
// 当前导航栏背景模式
const navigateBg = computed(() => appearanceStore.getNavigateBg)
const { wsCache } = useCache()
const userStore = useUserStoreWithOut()
const { t } = useI18n()
const unknownText = 'unknown'
const frontendAbout: SystemAboutInfo = {
  version: __CREST_FRONTEND_VERSION__ || unknownText,
  commitId: __CREST_FRONTEND_COMMIT_ID__ || unknownText
}
const backendAbout = ref<SystemAboutInfo>({
  version: unknownText,
  commitId: unknownText
})
const aboutVisible = ref(false)
const aboutLoading = ref(false)

interface LinkItem {
  id: number
  label: string
  link?: string
  method?: string
}
// 用户菜单链接列表
const linkList = ref([] as LinkItem[])

// 判断是否处于平台客户端内
const inPlatformClient = computed(() => !!wsCache.get('crest-platform-client'))

// 执行退出登录
const logout = async () => {
  await logoutApi()
  logoutHandler()
}

// 加载并排序用户菜单链接
const linkLoaded = items => {
  items.forEach(item => linkList.value.push(item))
  linkList.value.sort(compare('id'))
}

// 生成按指定属性升序排序的比较器
const compare = (property: string) => {
  return (a, b) => a[property] - b[property]
}

// 执行用户菜单项动作
const executeMethod = (item: LinkItem) => {
  if (item.link) {
    router.push(item.link)
  }
}

// 当前用户名称
const name = computed(() => userStore.getName)
// 当前用户 ID
const uid = computed(() => userStore.getUid)

// 用户菜单按钮引用
const buttonRef = ref()
// 用户菜单弹层引用
const popoverRef = ref()

// 语言菜单触发元素引用
const divLanguageRef = ref()
// 语言菜单弹层引用
const popoverLanguageRef = ref()

// 保持语言弹层显示
const openLanguage = () => {
  unref(popoverLanguageRef).popperRef?.delayHide?.()
}

// 保持用户菜单弹层显示
const openPopover = () => {
  unref(popoverRef).popperRef?.delayHide?.()
}

const normalizeAbout = (about?: Partial<SystemAboutInfo>): SystemAboutInfo => ({
  version: about?.version || unknownText,
  commitId: about?.commitId || unknownText
})

// 打开系统版本信息弹窗
const openAboutDialog = async () => {
  unref(popoverRef)?.hide?.()
  aboutVisible.value = true
  aboutLoading.value = true
  try {
    backendAbout.value = normalizeAbout(await querySystemAbout())
  } catch {
    backendAbout.value = normalizeAbout()
    ElMessage.error(t('common.version_info_read_failed'))
  } finally {
    aboutLoading.value = false
  }
}

if (uid.value === '1') {
  linkLoaded([{ id: 4, link: '/sys-setting/parameter', label: t('commons.system_setting') }])
  const desktop = wsCache.get('app.desktop')
  if (!desktop) {
    linkLoaded([{ id: 2, link: '/modify-pwd/index', label: t('user.change_password') }])
  }
}
</script>

<template>
  <div
    class="top-info-container"
    :class="{ 'is-light-top-info': navigateBg && navigateBg === 'light' }"
    ref="buttonRef"
    v-click-outside="openPopover"
  >
    <el-icon class="main-color">
      <Icon name="user-img"><userImg class="svg-icon" /></Icon>
    </el-icon>
    <span class="uname-span">{{ name }}</span>
    <el-icon class="el-icon-animate">
      <Icon name="icon_expand-down_filled"><icon_expandDown_filled class="svg-icon" /></Icon>
    </el-icon>
  </div>
  <el-popover
    ref="popoverRef"
    :virtual-ref="buttonRef"
    trigger="click"
    title=""
    virtual-triggering
    placement="bottom-start"
    popper-class="uinfo-popover"
    width="224"
  >
    <div class="uinfo-container">
      <div class="uinfo-header crest-container">
        <span class="uinfo-name">{{ name }}</span>
        <span class="uinfo-id">{{ `ID: ${uid}` }}</span>
      </div>
      <el-divider />
      <div class="uinfo-main">
        <div
          class="uinfo-main-item crest-container"
          v-for="link in linkList"
          :key="link.id"
          @click="executeMethod(link)"
        >
          <span>{{ link.label }}</span>
        </div>

        <div class="uinfo-main-item crest-container" @click="openAboutDialog">
          <span>{{ t('common.system_about') }}</span>
        </div>

        <div class="uinfo-main-item crest-container">
          <div class="about-parent" ref="divLanguageRef" v-click-outside="openLanguage">
            <span>{{ $t('commons.language') }}</span>
            <el-icon class="el-icon-animate">
              <ArrowRight />
            </el-icon>
          </div>
          <el-popover
            ref="popoverLanguageRef"
            :virtual-ref="divLanguageRef"
            trigger="hover"
            title=""
            virtual-triggering
            placement="left"
            width="224"
            popper-class="language-popover"
          >
            <LangSelector />
          </el-popover>
        </div>
      </div>
      <el-divider />
      <div class="uinfo-footer" v-if="!inPlatformClient">
        <div class="uinfo-main-item crest-container" @click="logout">
          <span>{{ t('common.exit_system') }}</span>
        </div>
      </div>
    </div>
  </el-popover>
  <el-dialog
    v-model="aboutVisible"
    :title="t('common.system_about')"
    width="min(520px, calc(100vw - 32px))"
    append-to-body
    class="system-about-dialog"
  >
    <div v-loading="aboutLoading" class="system-about-content">
      <div class="system-about-row">
        <span>{{ t('common.frontend_version') }}</span>
        <code>{{ frontendAbout.version }}</code>
      </div>
      <div class="system-about-row">
        <span>{{ t('common.frontend_commit') }}</span>
        <code>{{ frontendAbout.commitId }}</code>
      </div>
      <div class="system-about-row">
        <span>{{ t('common.backend_version') }}</span>
        <code>{{ backendAbout.version }}</code>
      </div>
      <div class="system-about-row">
        <span>{{ t('common.backend_commit') }}</span>
        <code>{{ backendAbout.commitId }}</code>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="less">
.el-icon-animate {
  width: 12px;
  height: 12px;
  font-size: 14px !important;
}
.is-light-top-info {
  .uname-span {
    font-family: var(--crest-font-sans);
    color: #0f172a !important;
  }
  &:hover {
    background-color: #ffffff !important;
  }
}
.top-info-container {
  height: 40px;
  display: flex;
  align-items: center;
  padding: 5px 10px 5px 5px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 999px;
  overflow: hidden;
  cursor: pointer;
  transition: background 0.14s ease, border-color 0.14s ease;
  &:hover {
    background-color: #ffffff;
    border-color: #e2e8f0;
  }
  .main-color {
    width: 30px;
    height: 30px;
    color: #3b82f6;
    background: linear-gradient(135deg, #dbeafe, #bfdbfe);
    border-radius: 50%;
  }
  .uname-span {
    margin-left: 9px;
    font-family: var(--crest-font-sans);
    font-size: 14px;
    font-weight: 500;
    color: #0f172a;
  }
  .ed-icon {
    margin: 0 0 0 6px;
    color: #64748b;
  }
}
.uinfo-container {
  width: 100%;
  height: 100%;
  .crest-container {
    padding: 0 13px 10px;
  }
  .ed-divider--horizontal {
    margin: 0 0 !important;
    color: #1f2329;
    opacity: 0.35;
  }
  .uinfo-header {
    span {
      display: block;
    }
    .uinfo-name {
      font-size: 14px;
      font-weight: 500;
      color: #1f2329;
    }
    .uinfo-id {
      font-size: 14px;
      font-weight: 400;
      color: #646a73;
      margin-top: 5px;
    }
  }
  .uinfo-main,
  .uinfo-footer {
    width: 100%;
    .uinfo-main-item {
      width: 100%;
      height: 40px;
      line-height: 40px;
      cursor: pointer;
      &:hover {
        background-color: #f2f2f2;
      }
      .about-parent {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
    }
  }
}
.uinfo-popover {
  max-height: 372px;
  .ed-popper__arrow {
    display: none;
  }
  .ed-popover__title {
    display: none;
  }
  padding-left: 0 !important;
  padding-right: 0 !important;
  padding-bottom: 0 !important;
}
.language-popover {
  // max-height: 112px;
  .ed-popper__arrow {
    display: none;
  }
  padding: var(--ed-popover-padding) 0 !important;
}
.system-about-dialog {
  .ed-dialog__body {
    padding-top: 8px;
  }
  .system-about-content {
    min-height: 168px;
  }
  .system-about-row {
    display: grid;
    grid-template-columns: 120px minmax(0, 1fr);
    gap: 16px;
    align-items: start;
    padding: 12px 0;
    border-bottom: 1px solid #edf0f5;
    color: #1f2329;
    &:last-child {
      border-bottom: 0;
    }
    span {
      font-size: 14px;
      color: #646a73;
    }
    code {
      padding: 0;
      font-family: var(--crest-font-mono);
      font-size: 13px;
      line-height: 20px;
      color: #1f2329;
      background: transparent;
      white-space: normal;
      overflow-wrap: anywhere;
    }
  }
}
</style>
