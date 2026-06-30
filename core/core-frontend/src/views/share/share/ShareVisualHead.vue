<template>
  <el-popover
    :visible="popoverVisible"
    title=""
    width="480"
    placement="bottom-end"
    :show-arrow="false"
    popper-class="share-popover"
    @show="share"
  >
    <template #reference>
      <el-button
        secondary
        v-if="props.weight >= 7"
        :disabled="disabled"
        @click="openPopover"
        v-click-outside="clickOutPopover"
      >
        <template #icon>
          <icon name="icon_share-label_outlined"
            ><icon_shareLabel_outlined class="svg-icon"
          /></icon>
        </template>
        {{ t('visualization.share') }}
      </el-button>
    </template>
    <div v-if="!shareDisable" class="share-container">
      <div class="share-title share-padding">{{ t('work_branch.public_link_share') }}</div>
      <div class="open-share flex-align-center share-padding">
        <el-switch size="small" v-model="shareEnable" @change="enableSwitcher" />
        {{ shareTips }}
      </div>
      <div v-if="shareEnable" class="custom-link-line share-padding">
        <el-input
          ref="linkUuidRef"
          placeholder=""
          :class="!linkCustom ? 'link-input-readlonly' : ''"
          v-model="state.detailInfo.uuid"
          :readonly="!linkCustom"
          @blur="validateUuid"
        >
          <template v-if="!linkCustom" #prefix>
            {{ formatLinkBase() }}
          </template>

          <template #suffix>
            <div class="share-input-suffix">
              <span class="suffix-split" />
              <div
                class="input-suffix-btn edit-uuid-icon"
                v-if="!linkCustom"
                @click.stop="editUuid"
              >
                <el-tooltip
                  class="item"
                  effect="dark"
                  :content="t('commons.edit') + t('chart.indicator_suffix')"
                  placement="top"
                >
                  <el-icon class="edit-uuid-icon" style="cursor: pointer">
                    <Icon class="edit-uuid-icon">
                      <icon_edit_outlined class="svg-icon edit-uuid-icon" />
                    </Icon>
                  </el-icon>
                </el-tooltip>
              </div>
              <div class="input-suffix-btn" v-if="linkCustom" @click.stop="resetUuid">
                <el-tooltip
                  class="item"
                  effect="dark"
                  :content="t('commons.cancel')"
                  placement="top"
                >
                  <el-icon style="cursor: pointer">
                    <Icon><icon_close_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </el-tooltip>
              </div>
              <div
                class="input-suffix-btn done-finish"
                v-if="linkCustom"
                @click.stop="finishEditUuid"
              >
                <el-tooltip class="item" effect="dark" :content="t('commons.save')" placement="top">
                  <el-icon style="cursor: pointer">
                    <Icon><icon_done_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </el-tooltip>
              </div>
            </div>
          </template>
        </el-input>
      </div>
      <div v-if="shareEnable" class="exp-container share-padding">
        <el-checkbox
          ref="expCheckbox"
          :disabled="!shareEnable"
          v-model="overTimeEnable"
          @change="expEnableSwitcher"
        >
          <div class="checkbox-span">
            <span>{{ t('visualization.over_time') }}</span>
            <span class="pe-require" :class="{ 'pe-tips-hidden': !sharePeRequire }">
              <span>*</span>
            </span>
          </div>
        </el-checkbox>
        <div class="inline-share-item-picker">
          <el-date-picker
            :clearable="false"
            class="share-exp-picker"
            v-if="state.detailInfo.exp"
            v-model="state.detailInfo.exp"
            type="datetime"
            :teleported="false"
            placeholder=""
            :shortcuts="shortcuts"
            @change="expChangeHandler"
            :disabled-date="disabledDate"
            value-format="x"
          />
          <span v-if="expError" class="exp-error">{{ t('work_branch.share_time_limit') }}</span>
        </div>
      </div>
      <div v-if="shareEnable" class="pwd-container share-padding">
        <el-checkbox
          ref="pwdCheckbox"
          :disabled="!shareEnable"
          v-model="passwdEnable"
          @change="pwdEnableSwitcher"
        >
          <div class="checkbox-span">
            <span>{{ t('visualization.passwd_protect') }}</span>
            <span class="pe-require" :class="{ 'pe-tips-hidden': !sharePeRequire }">
              <span>*</span>
            </span>
          </div>
        </el-checkbox>
        <div class="auto-pwd-container" v-if="passwdEnable">
          <el-checkbox
            v-show="false"
            :disabled="!shareEnable"
            v-model="state.detailInfo.autoPwd"
            @change="autoEnableSwitcher"
            :label="t('visualization.auto_pwd')"
          />
        </div>
        <div class="inline-share-item" v-if="passwdEnable">
          <el-input
            ref="pwdRef"
            style="flex: 1"
            class="link-input-readlonly"
            v-model="state.detailInfo.pwd"
            :readonly="state.detailInfo.autoPwd"
          >
            <template #suffix>
              <div class="share-input-suffix">
                <span class="suffix-split" />
                <div class="input-suffix-btn" @click="copyPwd">
                  <el-tooltip
                    class="item"
                    effect="dark"
                    :content="t('commons.copy')"
                    placement="top"
                  >
                    <el-icon style="cursor: pointer">
                      <Icon><CopyIcon class="svg-icon" /></Icon>
                    </el-icon>
                  </el-tooltip>
                </div>
                <div class="input-suffix-btn" @click="resetPwd">
                  <el-tooltip
                    class="item"
                    effect="dark"
                    :content="t('commons.reset')"
                    placement="top"
                  >
                    <el-icon style="cursor: pointer">
                      <Icon><icon_refresh_outlined class="svg-icon" /></Icon>
                    </el-icon>
                  </el-tooltip>
                </div>
              </div>
            </template>
          </el-input>

          <el-button secondary @click="openPwdDialog">{{ t('user.change_password') }}</el-button>
        </div>
      </div>

      <el-divider v-if="shareEnable" class="share-divider" />
      <div v-if="shareEnable" class="share-foot share-padding">
        <el-button secondary @click="openTicket">{{ t('work_branch.ticket_setting') }}</el-button>
        <el-button :disabled="!shareEnable || expError" type="primary" @click="copyInfo">
          {{ passwdEnable ? t('visualization.copy_link_passwd') : t('visualization.copy_link') }}
        </el-button>
      </div>
    </div>
    <div v-else class="share-container">
      <div class="share-title share-padding">{{ t('work_branch.public_link_share') }}</div>
      <div class="open-share flex-align-center share-padding">
        <span>{{ t('work_branch.cannot_share_link') }}</span>
      </div>
    </div>
  </el-popover>
  <custom-link-pwd ref="customPwdRef" @pwd-change="customPwdChange" />
  <ticket-dialog v-if="showTicket" ref="ticketDialogRef">
    <div v-if="!shareDisable && shareEnable && showTicket">
      <share-ticket
        :uuid="state.detailInfo.uuid"
        :resource-id="props.resourceId"
        :ticket-require="state.detailInfo.ticketRequire"
        @require-change="updateRequireTicket"
        @close="closeTicket"
      />
    </div>
  </ticket-dialog>
</template>

<script lang="ts" setup>
import icon_shareLabel_outlined from '@/assets/svg/icon_share-label_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_close_outlined from '@/assets/svg/icon_close_outlined.svg'
import icon_done_outlined from '@/assets/svg/icon_done_outlined.svg'
import CopyIcon from '@/assets/svg/copy.svg'
import icon_refresh_outlined from '@/assets/svg/icon_refresh_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import { ref, reactive, computed, nextTick, watch } from 'vue'
import request from '@/config/axios'
import { propTypes } from '@/utils/propTypes'
import { ShareInfo, SHARE_BASE, shortcuts } from './option'
import { ElMessage, ElLoading } from 'element-plus-secondary'
import useClipboard from 'vue-clipboard3'
import ShareTicket from './ShareTicket.vue'
import { useEmbedded } from '@/store/modules/embedded'
import { useShareStoreWithOut } from '@/store/modules/share'
import CustomLinkPwd from './CustomLinkPwd.vue'
import TicketDialog from './TicketDialog.vue'
const shareStore = useShareStoreWithOut()
const embeddedStore = useEmbedded()
const { toClipboard } = useClipboard()
const { t } = useI18n()
// 分享头部组件入参，描述资源身份、类型、权限权重和外部禁用状态
const props = defineProps({
  resourceId: propTypes.string.def(''),
  resourceType: propTypes.string.def(''),
  weight: propTypes.number.def(0),
  disabled: propTypes.bool.def(false)
})
// 分享弹窗显示状态
const popoverVisible = ref(false)
// 密码输入框引用，用于复制、校验和聚焦
const pwdRef = ref(null)
// 过期时间复选框引用，用于必填校验错误展示
const expCheckbox = ref()
// 密码保护复选框引用，用于必填校验错误展示
const pwdCheckbox = ref()
// 分享弹窗加载实例，避免重复创建 loading
const loadingInstance = ref<any>(null)
// 是否启用分享过期时间
const overTimeEnable = ref(false)
// 是否启用分享密码保护
const passwdEnable = ref(false)
// 是否启用公开链接分享
const shareEnable = ref(false)
// 当前可复制的完整分享链接
const linkAddr = ref('')
// 过期时间是否存在校验错误
const expError = ref(false)
// 分享链接后缀是否处于自定义编辑状态
const linkCustom = ref(false)
// 分享链接后缀输入框引用
const linkUuidRef = ref(null)
// Ticket 设置弹窗显示状态
const showTicket = ref(false)
// 原始链接后缀，取消编辑时用于回滚
const originUuid = ref('')
// 自定义密码弹窗组件引用
const customPwdRef = ref()
// Ticket 弹窗组件引用
const ticketDialogRef = ref()
// 分享详情状态，保存后端返回的 uuid、密码、过期时间和 Ticket 要求
const state = reactive({
  detailInfo: {
    id: '',
    uuid: '',
    pwd: '',
    exp: 0,
    autoPwd: true
  } as ShareInfo
})

// 资源切换时重置分享状态，避免旧资源的分享信息残留在弹窗内
watch(
  () => props.resourceId,
  () => {
    shareEnable.value = false
    state.detailInfo = {
      id: '',
      uuid: '',
      pwd: '',
      exp: 0,
      autoPwd: true
    }
    popoverVisible.value = false
  }
)
// 点击外部或关闭弹窗时校验必填项和自定义链接后缀，校验通过后收起弹窗
const hideShare = async () => {
  if (!shareEnable.value) {
    popoverVisible.value = false
    return
  }
  if (sharePeRequire.value) {
    const peRequireValid = validatePeRequire()
    if (!peRequireValid) {
      return
    }
  }
  const uuidValid = await validateUuid()
  if (uuidValid) {
    linkCustom.value = false
    popoverVisible.value = false
    return
  }
}

// 点击弹窗外部时关闭分享弹窗，分享弹窗和子弹窗内部点击不触发关闭
const clickOutPopover = e => {
  if (
    !popoverVisible.value ||
    e.target.closest('[class*="share-popover"]') ||
    e.target.closest('[class*="ed-overlay-dialog"]') ||
    e.target.classList?.toString()?.includes('edit-uuid-icon')
  ) {
    return
  }
  hideShare()
}
// 打开分享弹窗，并重置 Ticket 子弹窗状态
const openPopover = () => {
  if (!popoverVisible.value) {
    showTicket.value = false
    popoverVisible.value = true
  }
}
// 分享开关旁的提示文案，按资源类型区分仪表板和数据大屏
const shareTips = computed(
  () =>
    `${t('work_branch.open_link_hint')}${
      props.resourceType === 'dashboard'
        ? t('work_branch.dashboard')
        : t('work_branch.big_data_screen')
    }`
)
// 全局分享禁用状态，来自分享 Store
const shareDisable = computed(() => shareStore.getShareDisable)
// 是否强制要求过期时间和密码保护，来自分享 Store
const sharePeRequire = computed(() => shareStore.getSharePeRequire)

// 复制分享链接和可选密码，复制前会检查链接、密码和必填项校验状态
const copyInfo = async () => {
  if (shareEnable.value) {
    try {
      if (existErrorMsg('link-uuid-error-msg')) {
        ElMessage.warning(t('work_branch.error_link_hint'))
        return
      }
      if (passwdEnable.value && !state.detailInfo.autoPwd && existErrorMsg('link-pwd-error-msg')) {
        ElMessage.warning(t('work_branch.error_password_hint'))
        return
      }
      if (sharePeRequire.value) {
        const peRequireValid = validatePeRequire()
        if (!peRequireValid) {
          return
        }
      }
      formatLinkAddr()
      let info = linkAddr.value
      if (passwdEnable.value) {
        info += `,${state.detailInfo.pwd}`
      }
      await toClipboard(info)
      ElMessage.success(t('common.copy_success'))
    } catch (e) {
      ElMessage.warning(t('common.copy_unsupported'))
    }
  } else {
    ElMessage.warning(t('common.copy_unsupported'))
  }
  hideShare()
}

// 禁用过去时间，分享过期时间只能选择当前时间之后
const disabledDate = date => {
  return date.getTime() < new Date().getTime()
}

// 打开分享弹窗加载态
const showLoading = () => {
  loadingInstance.value = ElLoading.service({ target: '.share-dialog-container' })
}
// 关闭分享弹窗加载态
const closeLoading = () => {
  loadingInstance.value?.close()
}

// Popover 展示时加载分享详情，并执行必填项校验
const share = () => {
  loadShareInfo(validatePeRequire)
}

// 从后端加载当前资源分享详情，并刷新页面开关状态
const loadShareInfo = cb => {
  showLoading()
  const resourceId = props.resourceId
  const url = `/share/detail/${resourceId}`
  request
    .get({ url })
    .then(res => {
      state.detailInfo = { ...res.data }
      if (res.data?.uuid) {
        originUuid.value = res.data.uuid
      }
      setPageInfo()
    })
    .finally(() => {
      closeLoading()
      cb && cb()
    })
}

// 根据分享详情回填页面开关和链接展示状态
const setPageInfo = () => {
  if (state.detailInfo.id && state.detailInfo.uuid) {
    shareEnable.value = true
    formatLinkAddr()
    passwdEnable.value = !!state.detailInfo.pwd
    overTimeEnable.value = !!state.detailInfo.exp
  } else {
    shareEnable.value = false
    passwdEnable.value = false
    overTimeEnable.value = false
  }
}

// 切换公开链接分享开关，后端状态变更后重新加载分享详情
const enableSwitcher = () => {
  const resourceId = props.resourceId
  const url = `/share/switcher/${resourceId}`
  request.post({ url }).then(() => {
    loadShareInfo(null)
  })
}

// 生成完整分享链接并写入可复制地址
const formatLinkAddr = () => {
  linkAddr.value = formatLinkBase() + state.detailInfo.uuid
}
// 生成分享链接基础地址，兼容嵌入式部署和统一认证路径
const formatLinkBase = () => {
  let prefix = '/'
  if (embeddedStore.baseUrl) {
    prefix = embeddedStore.baseUrl + '#'
  } else {
    prefix = window.location.origin + window.location.pathname + '#'
  }
  if (prefix.includes('oidcbi/') || prefix.includes('casbi/')) {
    prefix = prefix.replace('oidcbi/', '')
    prefix = prefix.replace('casbi/', '')
  }
  return prefix + SHARE_BASE
}

// 切换过期时间开关，启用时默认设置为一小时后
const expEnableSwitcher = val => {
  let exp = 0
  if (val) {
    const now = new Date()
    now.setTime(now.getTime() + 3600 * 1000)
    exp = now.getTime()
    state.detailInfo.exp = exp
  }
  validateExpRequire()
  expChangeHandler(exp)
}

// 保存过期时间配置，并校验过期时间不能早于当前时间
const expChangeHandler = exp => {
  if (overTimeEnable.value && exp < new Date().getTime()) {
    expError.value = true
    return
  }
  expError.value = false
  const resourceId = props.resourceId
  const url = '/share/expiration'
  const data = { resourceId, exp }
  request.post({ url, data }).then(() => {
    loadShareInfo(null)
  })
}

// 切换密码保护开关，启用时生成自动密码
const pwdEnableSwitcher = val => {
  let pwd = ''
  if (val) {
    pwd = getUuid()
  }
  validatePwdRequire()
  resetPwdHandler(pwd, true)
}
// 重置自动生成的分享密码
const resetPwd = () => {
  const pwd = getUuid()
  resetPwdHandler(pwd, true)
}
// 保存分享密码配置，支持自动生成密码和手动密码两种模式
const resetPwdHandler = (pwd?: string, autoPwd?: boolean) => {
  const resourceId = props.resourceId
  const url = '/share/password'
  const data = { resourceId, pwd, autoPwd }
  request.post({ url, data }).then(() => {
    loadShareInfo(null)
  })
}

// 生成默认分享密码，包含字母、数字和至少一个特殊字符
const getUuid = () => {
  const length = 10
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+'
  let result = ''
  const specialChars = '!@#$%^&*()_+'
  let hasSpecialChar = false

  for (let i = 0; i < length; i++) {
    if (i === 0) {
      result += characters.charAt(Math.floor(Math.random() * characters.length))
    } else {
      if (!hasSpecialChar && i < length - 2) {
        result += specialChars.charAt(Math.floor(Math.random() * specialChars.length))
        hasSpecialChar = true
      } else {
        result += characters.charAt(Math.floor(Math.random() * characters.length))
      }
    }
  }
  result = result
    .split('')
    .sort(() => 0.5 - Math.random())
    .join('')
  return result
}
// 校验分享策略要求的过期时间和密码保护是否都已启用
const validatePeRequire = () => {
  if (shareEnable.value && sharePeRequire.value) {
    const expRequireValid = validateExpRequire()
    const pwdRequireValid = validatePwdRequire()
    return expRequireValid && pwdRequireValid
  }
  return true
}

// 校验过期时间必填要求，并在复选框旁展示错误信息
const validateExpRequire = () => {
  if (!sharePeRequire.value || overTimeEnable.value) {
    showCheckboxError(null, expCheckbox)
    return true
  }
  showCheckboxError(t('common.required'), expCheckbox)
  return false
}

// 校验密码保护必填要求，并在复选框旁展示错误信息
const validatePwdRequire = () => {
  if (!sharePeRequire.value || passwdEnable.value) {
    showCheckboxError(null, pwdCheckbox)
    return true
  }
  showCheckboxError(t('common.required'), pwdCheckbox)
  return false
}
// 在复选框区域展示或清理校验错误
const showCheckboxError = (msg, target, className?: string) => {
  if (!target.value) {
    return
  }
  className = className || 'checkbox-span-require'
  const fullClassName = `.${className}`
  const e = target.value.$el
  if (!e) {
    return
  }
  const checkboxBorder = e?.children?.[0]?.children?.[1] as HTMLElement | undefined
  const messageContainer = e?.children?.[1]?.children?.[0] as HTMLElement | undefined
  if (!msg) {
    e.style = null
    if (checkboxBorder) checkboxBorder.style.borderColor = null
    const child = messageContainer?.querySelector(fullClassName)
    if (child) {
      messageContainer?.removeChild(child)
    }
  } else {
    e.style.color = 'red'
    if (checkboxBorder) checkboxBorder.style.borderColor = 'red'
    const child = messageContainer?.querySelector(fullClassName)
    if (!messageContainer) return
    if (!child) {
      const errorDom = document.createElement('span')
      errorDom.className = className
      errorDom.innerText = msg
      messageContainer.appendChild(errorDom)
    } else {
      const errorText = child as HTMLElement
      errorText.innerText = msg
    }
  }
}
// 在输入框区域展示或清理校验错误，用于密码和链接后缀
const showPageError = (msg, target, className?: string) => {
  className = className || 'link-pwd-error-msg'
  const fullClassName = `.${className}`
  const domRef = target || pwdRef
  if (!domRef.value) {
    return
  }
  const e = domRef.value.input
  const parentElement = e?.parentElement
  if (!e || !parentElement) {
    return
  }
  if (!msg) {
    e.style = null
    e.style.borderColor = null
    const child = parentElement.querySelector(fullClassName)
    if (child) {
      parentElement['style'] = null
      parentElement.removeChild(child)
    }
  } else {
    e.style.color = 'red'
    e.style.borderColor = 'red'
    parentElement['style']['box-shadow'] = '0 0 0 1px red inset'
    const child = parentElement.querySelector(fullClassName)
    if (!child) {
      const errorDom = document.createElement('div')
      errorDom.className = className
      errorDom.innerText = msg
      parentElement.appendChild(errorDom)
    } else {
      child.innerText = msg
    }
  }
}
// 判断页面中是否存在指定校验错误消息
const existErrorMsg = (className: string) => {
  return document.getElementsByClassName(className)?.length
}
// 切换自动密码模式，启用时重新生成密码，关闭时清空并聚焦输入框
const autoEnableSwitcher = val => {
  if (val) {
    showPageError(null, pwdRef)
    resetPwd()
  } else {
    state.detailInfo.pwd = ''
    nextTick(() => {
      pwdRef.value.input.focus()
    })
  }
}

// 复制分享密码，复制前校验手动密码是否存在错误
const copyPwd = async () => {
  if (shareEnable.value && passwdEnable.value) {
    if (!state.detailInfo.autoPwd && existErrorMsg('link-pwd-error-msg')) {
      ElMessage.warning(t('work_branch.error_password_hint'))
      return
    }
    try {
      await toClipboard(state.detailInfo.pwd)
      ElMessage.success(t('common.copy_success'))
    } catch (e) {
      ElMessage.warning(t('common.copy_unsupported'))
    }
  } else {
    ElMessage.warning(t('common.copy_unsupported'))
  }
}
// 进入分享链接后缀自定义编辑状态，并聚焦输入框
const editUuid = () => {
  linkCustom.value = true
  nextTick(() => {
    if (linkUuidRef?.value) {
      linkUuidRef.value.input.focus()
    }
  })
}
// 校验分享链接后缀格式和唯一性，并在输入框下展示错误信息
const validateUuid = async () => {
  const val = state.detailInfo.uuid
  const className = 'link-uuid-error-msg'
  if (!val) {
    showPageError(t('commons.cannot_be_null'), linkUuidRef, className)
    return false
  }
  const regex = /^[a-zA-Z0-9]{8,16}$/
  const result = regex.test(val)
  if (!result) {
    showPageError(t('work_branch.uuid_checker'), linkUuidRef, className)
  } else {
    const msg = await uuidValidateApi(val)
    showPageError(msg, linkUuidRef, className)
    return !msg
  }
  return result
}

// 调用后端校验分享链接后缀是否可用
const uuidValidateApi = async val => {
  const url = '/share/uuid'
  const data = { resourceId: props.resourceId, uuid: val }
  const res = await request.post({ url, data })
  return res.data
}
// 保存自定义链接后缀，校验未通过时保持编辑状态
const finishEditUuid = async () => {
  const uuidValid = await validateUuid()
  linkCustom.value = !uuidValid
}
// 取消链接后缀编辑并回滚到原始后缀
const resetUuid = event => {
  event.stopPropagation()
  state.detailInfo.uuid = originUuid.value
  finishEditUuid()
}
// 打开 Ticket 设置弹窗
const openTicket = () => {
  showTicket.value = true
  nextTick(() => {
    ticketDialogRef.value.open()
  })
}
// 关闭 Ticket 设置弹窗并卸载内容
const closeTicket = () => {
  ticketDialogRef.value.close()
  nextTick(() => {
    showTicket.value = false
  })
}
// 更新分享详情中的 Ticket 必填状态
const updateRequireTicket = val => {
  state.detailInfo.ticketRequire = val
}

// 对外暴露的执行入口，用于父组件主动刷新分享详情
const execute = () => {
  share()
}

// 打开自定义密码弹窗，并传入当前密码
const openPwdDialog = () => {
  customPwdRef.value.open(state.detailInfo.pwd)
}
// 保存自定义密码弹窗返回的密码
const customPwdChange = val => {
  state.detailInfo.pwd = val
  resetPwdHandler(val, false)
}
defineExpose({
  execute
})
</script>

<style lang="less">
.share-popover {
  padding: 16px 0px !important;
}
</style>

<style lang="less" scoped>
.share-container {
  .share-title {
    font-weight: 500;
    color: #1f2329;
    padding-bottom: 5px !important;
  }
  .share-padding {
    padding: 0px 16px;
  }
  .share-divider {
    margin-bottom: 10px !important;
    border-top: 1px #1f232926 solid;
  }
  .share-foot {
    display: flex;
    justify-content: flex-end;
  }
  .open-share {
    font-size: 12px;
    color: #646a73;
    .ed-switch {
      margin-right: 8px;
    }
  }
  .text {
    padding-bottom: 5px !important;
  }
  .custom-link-line {
    display: flex;
    margin-bottom: 16px;
    align-items: center;
    .maxW380 {
      :deep(.ed-input__prefix) {
        overflow: hidden;
        max-width: 380px;
      }
    }
    button {
      width: 40px;
      min-width: 40px;
      margin-left: 8px;
      height: 100%;
    }
    :deep(.link-uuid-error-msg) {
      color: red;
      position: absolute;
      z-index: 9;
      font-size: 10px;
      height: 10px;
      top: 25px;
      width: 350px;
      left: 0px;
    }
  }
}
:deep(.checkbox-span) {
  display: flex;
  align-items: center;
  .pe-require {
    color: red;
    font-size: 10px;
    line-height: 32px;
    margin: 0 4px;
  }
  .checkbox-span-require {
    font-size: 10px;
  }
  .pe-tips-hidden {
    display: none;
  }
}

.inline-share-item-picker {
  display: flex;
  align-items: center;
  :deep(.share-exp-picker) {
    margin-left: 25px !important;
    .ed-input__wrapper {
      width: 200px !important;
    }
  }
  .exp-error {
    color: var(--ed-color-danger);
    font-size: 12px;
  }
}
.inline-share-item {
  width: 220px;
  display: inline-flex;
  column-gap: 12px;
  margin-left: 25px;
  width: 332px;

  :deep(.ed-input-group__append) {
    width: initial !important;
    background: none;
    color: #1f2329;
    padding: 0px 0px !important;
  }
  :deep(.link-pwd-error-msg) {
    color: red;
    position: absolute;
    z-index: 9;
    font-size: 10px;
    height: 10px;
    top: 21px;
    width: 350px;
    left: 0px;
  }
}

.share-input-suffix {
  display: flex;
  height: 30px;
  line-height: 30px;
  column-gap: 4px;
  align-items: center;
  .suffix-split {
    height: 30px;
    width: 1px;
    display: inline-block;
    background-color: #bbbfc4;
    margin-right: 4px;
  }
  .done-finish {
    color: var(--ed-color-primary, #3b82f6);
    &:hover {
      background-color: var(--ed-color-primary-1a, #3b82f61a) !important;
    }
  }
  .input-suffix-btn {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    &:hover {
      background-color: #1f23291a;
    }
    svg {
      width: 16px;
      height: 16px;
    }
  }
}
.link-input-readlonly {
  :deep(.ed-input__wrapper) {
    background-color: rgba(0, 0, 0, 0.1);
    color: #8f959e;
    &:hover {
      box-shadow: 0 0 0 1px var(--ed-input-border-color, var(--ed-border-color)) inset;
    }
    input {
      color: #646a73;
    }
  }
}
</style>
