<template>
  <InfoTemplate
    ref="infoTemplate"
    :label-tooltips="tooltips"
    setting-key="basic"
    :setting-title="t('system.basic_settings')"
    :setting-data="baseInfoSettings"
    @edit="
      edit(
        t('system.basic_settings'),
        baseInfoSettings.map(ele => ele.pkey.split('.')[1])
      )
    "
  />
  <InfoTemplate
    v-if="securityInfoSettings?.length"
    ref="securityTemplate"
    class="login-setting-template"
    :label-tooltips="tooltips"
    :copy-list="['setting_basic.initialPassword']"
    setting-key="basic"
    setting-title="账号安全"
    :setting-data="securityInfoSettings"
    @edit="
      edit(
        '账号安全',
        securityInfoSettings.map(ele => ele.pkey.split('.')[1])
      )
    "
  />
  <InfoTemplate
    v-if="loginInoSettings?.length"
    ref="loginTemplate"
    class="login-setting-template"
    :label-tooltips="tooltips"
    setting-key="basic"
    :setting-title="t('system.login_settings')"
    :setting-data="loginInoSettings"
    @edit="
      edit(
        t('system.login_settings'),
        loginInoSettings.map(ele => ele.pkey.split('.')[1])
      )
    "
  />

  <InfoTemplate
    v-if="thirdInfoSettings?.length"
    ref="thirdTemplate"
    class="login-setting-template"
    :label-tooltips="tooltips"
    setting-key="basic"
    :setting-title="t('setting_basic.third_platform_settings')"
    :setting-data="thirdInfoSettings"
    @edit="
      edit(
        t('setting_basic.third_platform_settings'),
        thirdInfoSettings.map(ele => ele.pkey.split('.')[1])
      )
    "
  />
  <basic-edit ref="editor" :label-tooltips="tooltips" @saved="refresh" />
</template>

<script lang="ts" setup>
import { ref, computed, nextTick } from 'vue'
import InfoTemplate from '../../common/InfoTemplate.vue'
import BasicEdit from './BasicEdit.vue'
import request from '@/config/axios'
import { SettingRecord } from '@/views/system/common/SettingTemplate'
import { reactive } from 'vue'
import { isDesktop } from '@/utils/ModelUtil'
import { cloneDeep } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()
// 基础设置编辑弹窗实例
const editor = ref()
// 基础设置展示模板实例
const infoTemplate = ref()
// 账号安全设置展示模板实例
const securityTemplate = ref()
// 登录设置展示模板实例
const loginTemplate = ref()
// 第三方平台设置展示模板实例
const thirdTemplate = ref()
// 是否展示默认登录方式配置
const showDefaultLogin = ref(false)
const desktop = isDesktop()
const pvpOptions = [
  { value: '0', label: t('commons.date.permanent') },
  { value: '1', label: t('commons.date.one_year') },
  { value: '2', label: t('commons.date.six_months') },
  { value: '3', label: t('commons.date.three_months') },
  { value: '4', label: t('commons.date.one_month') }
]
const tooltips = [
  {
    key: 'setting_basic.defaultOpen',
    val: t('setting_basic.default_open_tips')
  },
  {
    key: 'setting_basic.frontTimeOut',
    val: t('system.to_take_effect')
  },
  {
    key: 'setting_basic.platformOid',
    val: t('system.and_platform_docking')
  },
  {
    key: 'setting_basic.platformRid',
    val: t('system.and_platform_docking')
  }
]
const loginSettings = ['setting_basic.defaultLogin']

const securitySettings = [
  'setting_basic.initialPassword',
  'setting_basic.pwdStrategy',
  'setting_basic.dip',
  'setting_basic.pvp',
  'setting_basic.loginLimit',
  'setting_basic.loginLimitRate',
  'setting_basic.loginLimitTime'
]

const thirdSettings = [
  'setting_basic.autoCreateUser',
  'setting_basic.platformOid',
  'setting_basic.platformRid'
]
// 基础设置页面状态，包含配置列表、组织角色选项和枚举选项
const state = reactive({
  templateList: [] as SettingRecord[],
  orgOptions: [],
  roleOptions: [
    {
      value: 'admin',
      label: t('role.org_admin'),
      children: null,
      disabled: false
    },
    {
      value: 'readonly',
      label: t('role.average_role'),
      children: null,
      disabled: false
    }
  ],
  loginOptions: [
    { value: '0', label: t('system.normal_login') },
    { value: '1', label: 'LDAP' },
    { value: '2', label: 'OIDC' },
    { value: '3', label: 'CAS' },
    { value: '9', label: 'OAuth2' },
    { value: '10', label: 'Saml2' }
  ],
  sortOptions: [
    { value: '0', label: t('resource_sort.time_asc') },
    { value: '1', label: t('resource_sort.time_desc') },
    { value: '2', label: t('resource_sort.name_asc') },
    { value: '3', label: t('resource_sort.name_desc') }
  ],
  openOptions: [
    { value: '0', label: t('open_opt.new_page') },
    { value: '1', label: t('open_opt.local_page') }
  ]
})
let originData = []
// 当前选择的默认组织 ID
const selectedOid = ref('')
// 当前选择的默认组织名称
const selectedOName = ref('')
// 当前选择的默认角色 ID 列表
const selectedRid = ref<string[]>([])
// 当前选择的默认角色名称列表
const selectedRName = ref<string[]>([])
// 当前选择的密码有效期选项
const selectedPvp = ref('0')

// 基础设置分组，排除登录、安全、第三方和桌面端不适用配置
const baseInfoSettings = computed(() =>
  state.templateList.filter(
    item =>
      !loginSettings.concat(thirdSettings).includes(item.pkey) &&
      !securitySettings.includes(item.pkey) &&
      !['setting_basic.shareDisable', 'setting_basic.sharePeRequire'].includes(item.pkey) &&
      (!desktop || item.pkey !== 'setting_basic.defaultOpen')
  )
)

// 账号安全设置分组
const securityInfoSettings = computed(() =>
  state.templateList.filter(item => securitySettings.includes(item.pkey))
)

// 第三方平台设置分组
const thirdInfoSettings = computed(() =>
  state.templateList.filter(item => thirdSettings.includes(item.pkey))
)
// 登录设置分组
const loginInoSettings = computed(() => {
  const list = state.templateList.filter(item => loginSettings.includes(item.pkey))
  return list
})

// 查询基础设置，并将存储值转换为页面展示文案
const search = cb => {
  const url = '/sys-parameter/basic/list'
  originData = []
  state.templateList = []
  const resultList = []
  request.get({ url }).then(async res => {
    originData = cloneDeep(res.data)
    const data = res.data
    for (let index = 0; index < data.length; index++) {
      const item = data[index]
      if (
        item.pkey === 'basic.autoCreateUser' ||
        item.pkey === 'basic.dip' ||
        item.pkey === 'basic.pwdStrategy' ||
        item.pkey === 'basic.loginLimit'
      ) {
        item.pval = item.pval === 'true' ? t('chart.open') : t('system.not_enabled')
      } else if (item.pkey === 'basic.platformOid') {
        selectedOid.value = item.pval
        await loadOrgOptions()
        item.pval = selectedOName.value || t('system.default_organization')
      } else if (item.pkey === 'basic.platformRid') {
        const pval = item.pval
        if (pval?.length) {
          const pvalArray = pval.split(',')
          selectedRid.value = pvalArray
          await loadRoleOptions()
          if (selectedRName.value.length) {
            item.pval = selectedRName.value.join(',')
          } else {
            item.pval = t('system.normal_role')
          }
        } else {
          selectedRid.value = []
          item.pval = t('system.normal_role')
        }
      } else if (item.pkey === 'basic.pvp') {
        selectedPvp.value = item.pval || '0'
        item.pval =
          pvpOptions.find(cur => cur.value === selectedPvp.value)?.label || pvpOptions[0].label
      } else if (item.pkey === 'basic.defaultLogin') {
        await queryCategoryStatus()
        if (showDefaultLogin.value) {
          if (item.pval) {
            const r = state.loginOptions.filter(cur => cur.value === item.pval)
            if (r?.length) {
              item.pval = r[0].label
            } else {
              item.pval = state.loginOptions[0].label
              resetDefaultLogin()
            }
          } else {
            item.pval = state.loginOptions[0].label
          }
        }
      } else if (item.pkey === 'basic.defaultSort') {
        if (item.pval) {
          const r = state.sortOptions.filter(cur => cur.value === item.pval)
          if (r?.length) {
            item.pval = r[0].label
          } else {
            item.pval = state.sortOptions[1].label
          }
        } else {
          item.pval = state.sortOptions[1].label
        }
      } else if (item.pkey === 'basic.defaultOpen') {
        if (item.pval) {
          const r = state.openOptions.filter(cur => cur.value === item.pval)
          if (r?.length) {
            item.pval = r[0].label
          } else {
            item.pval = state.openOptions[0].label
          }
        } else {
          item.pval = state.openOptions[0].label
        }
      } else {
        item.pval = item.pval
      }
      item.pkey = 'setting_' + item.pkey
      if (!item.pkey.includes('defaultLogin') || showDefaultLogin.value) {
        resultList.push(item)
      }
    }
    state.templateList.splice(0, resultList.length, ...resultList)
    cb && cb()
  })
}
// 刷新设置列表并重置各展示模板状态
const refresh = () => {
  search(() => {
    nextTick(() => {
      infoTemplate?.value?.init()
      securityTemplate?.value?.init()
      loginTemplate?.value?.init()
      thirdTemplate?.value?.init()
    })
  })
}
refresh()

// 打开基础设置编辑弹窗，并传入原始配置和选项数据
const edit = (val, arr) => {
  editor?.value.edit(
    cloneDeep(originData),
    cloneDeep(state.orgOptions),
    cloneDeep(state.roleOptions),
    cloneDeep(state.loginOptions),
    cloneDeep(state.sortOptions),
    cloneDeep(state.openOptions),
    val,
    arr
  )
}
// 加载可选组织并转换为级联选择器选项
const loadOrgOptions = async () => {
  const res = await request.post({ url: '/org/mounted', data: {} })
  const data = res.data
  formatOrg(data)
  state.orgOptions = data
}
// 加载当前组织下的角色并按只读角色分组
const loadRoleOptions = async () => {
  const res = await request.get({ url: `/role/organization/${selectedOid.value}` })
  const data = res.data
  const map = groupBy(data)
  state.roleOptions[0].children = map.get(false)
  state.roleOptions[1].children = map.get(true)
}
// 将组织树节点转换为级联选择器节点，并记录当前组织名称
const formatOrg = list => {
  const stack = [...list]
  while (stack.length) {
    const item = stack.pop()
    if (item.id === selectedOid.value) {
      selectedOName.value = item.name
    }
    item.value = item.id
    item.label = item.name
    item.disabled = item.readOnly
    if (item.children?.length) {
      item.children.forEach(kid => stack.push(kid))
    }
  }
}

// 按只读标记分组角色选项，并记录当前角色名称
const groupBy = list => {
  const map = new Map()
  selectedRName.value = []
  list.forEach(item => {
    if (selectedRid.value.includes(item.id)) {
      selectedRName.value.push(item.name)
    }
    const readonly = item.readonly
    let arr = map.get(readonly)
    if (!arr) {
      arr = []
    }
    arr.push({ value: item.id, label: item.name, disabled: false })
    map.set(readonly, arr)
  })
  return map
}
// 查询已启用认证方式，并决定是否展示默认登录方式配置
const queryCategoryStatus = async () => {
  const url = `/setting/authentication/status`
  const res = await request.get({ url })
  const data = res.data
  const map = data.reduce((acc, { name, enable }) => {
    acc[name] = enable
    return acc
  }, {})
  let len = state.loginOptions.length
  while (len--) {
    const item = state.loginOptions[len]
    if (item.value !== '0' && !map[item.label.toLocaleLowerCase()]) {
      state.loginOptions.splice(len, 1)
    }
  }
  showDefaultLogin.value = state.loginOptions.length > 1
  if (!showDefaultLogin.value) {
    let len = originData.length
    while (len--) {
      const item = originData[len]
      if (item.pkey === 'basic.defaultLogin') {
        originData.splice(len, 1)
      }
    }
  }
}
// 当默认登录方式失效时重置为普通登录
const resetDefaultLogin = () => {
  let len = originData.length
  while (len--) {
    const item = originData[len]
    if (item.pkey === 'basic.defaultLogin') {
      item.pval = '0'
    }
  }
}
</script>
<style lang="less" scoped>
.login-setting-template {
  margin-top: 16px;
}
</style>
