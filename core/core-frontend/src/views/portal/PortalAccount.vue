<script setup lang="ts">
import { computed, ref, unref } from 'vue'
import router from '@/router'
import userImg from '@/assets/svg/user-img.svg'
import iconExpandDownFilled from '@/assets/svg/icon_expand-down_filled.svg'
import { Icon } from '@/components/icon-custom'
import { logoutApi } from '@/api/login'
import { useUserStoreWithOut } from '@/store/modules/user'
import { logoutHandler } from '@/utils/logout'
import { useCache } from '@/hooks/web/useCache'

const userStore = useUserStoreWithOut()
const { wsCache } = useCache()
// 持有账户入口按钮实例
const buttonRef = ref()
// 持有账户弹出层实例
const popoverRef = ref()

// 计算当前用户展示名称
const name = computed(() => userStore.getName || '用户')
// 计算当前用户编号
const uid = computed(() => userStore.getUid || '-')
// 判断当前是否运行在平台客户端中
const inPlatformClient = computed(() => !!wsCache.get('crest-platform-client'))

// 点击外部时延迟隐藏账户弹出层
const openPopover = () => {
  unref(popoverRef).popperRef?.delayHide?.()
}

// 跳转到修改密码页面
const changePassword = () => {
  router.push('/modify-pwd/index')
}

// 执行退出登录流程
const logout = async () => {
  await logoutApi()
  logoutHandler()
}
</script>

<template>
  <div ref="buttonRef" class="portal-account" v-click-outside="openPopover">
    <el-icon class="account-icon">
      <Icon name="user-img"><userImg class="svg-icon" /></Icon>
    </el-icon>
    <span class="account-name">{{ name }}</span>
    <el-icon class="account-arrow">
      <Icon name="icon_expand-down_filled"><iconExpandDownFilled class="svg-icon" /></Icon>
    </el-icon>
  </div>
  <el-popover
    ref="popoverRef"
    :virtual-ref="buttonRef"
    trigger="click"
    title=""
    virtual-triggering
    placement="bottom-start"
    popper-class="portal-account-popover"
    width="224"
  >
    <div class="account-menu">
      <div class="account-menu-header">
        <span class="account-menu-name">{{ name }}</span>
        <span class="account-menu-id">ID: {{ uid }}</span>
      </div>
      <el-divider />
      <div class="account-menu-main">
        <div class="account-menu-item" @click="changePassword">
          <span>修改密码</span>
        </div>
      </div>
      <template v-if="!inPlatformClient">
        <el-divider />
        <div class="account-menu-main">
          <div class="account-menu-item" @click="logout">
            <span>退出登录</span>
          </div>
        </div>
      </template>
    </div>
  </el-popover>
</template>

<style scoped lang="less">
.portal-account {
  display: flex;
  flex: none;
  align-items: center;
  height: 40px;
  max-width: 164px;
  padding: 5px 10px 5px 5px;
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 999px;
  transition: background 0.14s ease, border-color 0.14s ease;

  &:hover {
    background: #ffffff;
    border-color: #e2e8f0;
  }
}

.account-icon {
  width: 30px;
  height: 30px;
  color: #3b82f6;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  border-radius: 50%;
}

.account-name {
  max-width: 96px;
  margin-left: 9px;
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-arrow {
  width: 12px;
  height: 12px;
  margin-left: 6px;
  color: #64748b;
  font-size: 14px !important;
}

.svg-icon {
  width: 100%;
  height: 100%;
}
</style>

<style lang="less">
.portal-account-popover {
  padding-right: 0 !important;
  padding-bottom: 0 !important;
  padding-left: 0 !important;

  .ed-popper__arrow,
  .ed-popover__title {
    display: none;
  }
}

.account-menu {
  width: 100%;
  height: 100%;

  .ed-divider--horizontal {
    margin: 0 !important;
    color: #1f2329;
    opacity: 0.35;
  }
}

.account-menu-header {
  padding: 0 13px 10px;

  span {
    display: block;
  }
}

.account-menu-name {
  color: #1f2329;
  font-size: 14px;
  font-weight: 500;
}

.account-menu-id {
  margin-top: 5px;
  color: #646a73;
  font-size: 14px;
}

.account-menu-main {
  width: 100%;
}

.account-menu-item {
  height: 40px;
  padding: 0 13px;
  color: #1f2329;
  font-size: 14px;
  line-height: 40px;
  cursor: pointer;

  &:hover {
    background: #f2f2f2;
  }
}
</style>
