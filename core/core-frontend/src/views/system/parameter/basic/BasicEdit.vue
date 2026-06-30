<script lang="ts" setup>
import { ref, reactive, PropType } from 'vue'
import { ElMessage, ElLoading } from 'element-plus-secondary'
import { useI18n } from '@/hooks/web/useI18n'
import type { FormInstance, FormRules } from 'element-plus-secondary'
import request from '@/config/axios'
import dvInfo from '@/assets/svg/dv-info.svg'
const { t } = useI18n()

// 接收基础参数标签提示配置
const props = defineProps({
  labelTooltips: {
    type: Array as PropType<any[]>,
    default: () => []
  }
})

// 控制基础参数抽屉显隐
const dialogVisible = ref(false)
// 保存全屏加载实例
const loadingInstance = ref(null)
// 基础参数表单实例
const basicForm = ref<FormInstance>()
const options = [
  { value: 'minute', label: t('system.time_0_seconds') },
  { value: 'hour', label: t('system.hour_execution_default') }
]
// 平台有效期选项和后端参数值保持字符串枚举，避免保存时出现类型差异。
const pvpOptions = [
  { value: '0', label: t('commons.date.permanent') },
  { value: '1', label: t('commons.date.one_year') },
  { value: '2', label: t('commons.date.six_months') },
  { value: '3', label: t('commons.date.three_months') },
  { value: '4', label: t('commons.date.one_month') }
]
const requireKeys = [
  'logLiveTime',
  'thresholdLogLiveTime',
  'exportFileLiveTime',
  'dataFillingLogLiveTime',
  'initialPassword',
  'frontTimeOut',
  'loginLimitTime',
  'loginLimitRate',
  'thresholdLimit'
]
// 维护基础参数表单和下拉选项
const state = reactive({
  form: reactive({
    dsIntervalTime: '30',
    dsExecuteTime: 'minute',
    frontTimeOut: '30',
    thresholdLimit: 10000
  }),
  settingList: [],
  orgOptions: [],
  roleOptions: [],
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

// 参数提示文案映射
const tooltipItem = ref({})
// 初始化参数提示文案映射
const formatLabel = () => {
  props.labelTooltips?.length &&
    props.labelTooltips.forEach(tooltip => {
      tooltipItem.value[tooltip.key] = tooltip.val
    })
}

// 基础参数表单校验规则
const rule = reactive<FormRules>({
  dsIntervalTime: [
    {
      required: true,
      message: t('common.require'),
      trigger: 'blur'
    }
  ]
})

// 将表单值转换为后端参数保存结构
const buildSettingList = () => {
  return state.settingList.map(item => {
    const pkey = item.pkey.startsWith('basic.') ? item.pkey : `basic.${item.pkey}`
    const sort = item.sort
    const type = item.type
    let pval = state.form[item.pkey]
    if (Array.isArray(pval)) {
      pval = pval.join(',')
    }
    return { pkey, pval, type, sort }
  })
}
// 定义保存成功事件
const emits = defineEmits(['saved'])
// 校验并提交基础参数表单
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl.validate(valid => {
    if (valid) {
      if (
        state.form.dsExecuteTime === 'minute' &&
        (Number(state.form.dsIntervalTime) < 1 || Number(state.form.dsIntervalTime) > 59)
      ) {
        ElMessage.error(t('commons.date.of_range_1_59'))
        return
      }
      if (
        state.form.dsExecuteTime === 'hour' &&
        (Number(state.form.dsIntervalTime) < 1 || Number(state.form.dsIntervalTime) > 23)
      ) {
        ElMessage.error(t('commons.date.of_range_1_23'))
        return
      }
      const param = buildSettingList()
      if (param.length < 2) {
        return
      }
      showLoading()
      request
        .post({ url: '/sys-parameter/basic/record', data: param })
        .then(res => {
          if (!res.msg) {
            ElMessage.success(t('common.save_success'))
            emits('saved')
            reset()
          }
          closeLoading()
        })
        .catch(() => {
          closeLoading()
        })
    }
  })
}

// 重置基础参数表单和抽屉状态
const resetForm = (formEl: FormInstance | undefined) => {
  state.settingList = []
  settingList.value = []
  if (!formEl) return
  formEl.resetFields()
  dialogVisible.value = false
}

// 使用当前表单实例执行重置
const reset = () => {
  resetForm(basicForm.value)
}

// 显示基础参数保存加载态
const showLoading = () => {
  loadingInstance.value = ElLoading.service({ target: '.basic-param-drawer' })
}
// 关闭基础参数保存加载态
const closeLoading = () => {
  loadingInstance.value?.close()
}
// 当前抽屉标题
const title = ref()
// 当前抽屉显示的参数项
const settingList = ref([])
// 打开基础参数编辑抽屉并填充表单
const edit = (
  list,
  orgOptions,
  roleOptions,
  loginOptions,
  sortOptions,
  openOptions,
  titleVal,
  settingListVal
) => {
  title.value = titleVal
  state.orgOptions = orgOptions || []
  state.roleOptions = roleOptions || []
  state.loginOptions = loginOptions || []
  state.sortOptions = sortOptions || []
  state.openOptions = openOptions || []
  state.settingList = list.map(item => {
    const pkey = item.pkey
    if (requireKeys.some(requireKey => `basic.${requireKey}` === pkey)) {
      // 后端返回的参数集合是动态的，必填规则需要按本次抽屉展示项实时补齐。
      rule[pkey.split('.')[1]] = [
        {
          required: true,
          message: t('common.require'),
          trigger: ['blur', 'change']
        }
      ]
    }

    item['label'] = `setting_${pkey}`
    item['pkey'] = pkey.split('.')[1]
    let pval = item.pval
    if (item.pkey.includes('platformRid') && pval?.length) {
      pval = pval.split(',')
      if (!rule['platformRid']) {
        // 默认角色支持多选保存，编辑时从逗号分隔值还原为数组并补齐必填校验。
        rule['platformRid'] = [
          {
            required: true,
            message: t('common.require'),
            trigger: ['blur', 'change']
          }
        ]
      }
    }
    if (item.pkey.includes('platformOid')) {
      if (!rule['platformOid']) {
        // 平台组织是默认角色的上级约束，展示组织字段时同步纳入必填校验。
        rule['platformOid'] = [
          {
            required: true,
            message: t('common.require'),
            trigger: ['blur', 'change']
          }
        ]
      }
    }
    state.form[item['pkey']] = pval || state.form[item['pkey']]
    return item
  })

  settingList.value = state.settingList.filter(ele => settingListVal.includes(ele.pkey))
  dialogVisible.value = true
}
// 根据平台组织加载角色选项
const loadRoleOptions = async () => {
  const oid = state.form['platformOid']
  if (!oid) {
    return
  }
  const res = await request.get({ url: `/role/organization/${oid}` })
  const data = res.data
  const map = groupBy(data)
  state.roleOptions[0].children = map.get(false)
  state.roleOptions[1].children = map.get(true)
}
// 按只读状态分组角色选项
const groupBy = list => {
  const map = new Map()
  list.forEach(item => {
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
// 组织切换时清空角色并重新加载
const oidChange = () => {
  state.form['platformRid'] = []
  loadRoleOptions()
}
formatLabel()
defineExpose({
  edit
})
</script>

<template>
  <el-drawer
    :title="title"
    v-model="dialogVisible"
    modal-class="basic-param-drawer"
    size="600px"
    direction="rtl"
  >
    <!-- 基础参数表单按后端返回的参数列表动态渲染，不同 pkey 对应不同输入控件。 -->
    <el-form
      ref="basicForm"
      require-asterisk-position="right"
      :model="state.form"
      :rules="rule"
      label-width="80px"
      label-position="top"
    >
      <el-form-item
        v-for="item in settingList"
        :key="item.pkey"
        :prop="item.pkey"
        :class="{ 'setting-hidden-item': item.pkey === 'dsExecuteTime' }"
      >
        <template v-slot:label>
          <div class="basic-form-info-tips">
            <span class="custom-form-item__label">{{ t(item.label) }}</span>
            <el-tooltip
              v-if="tooltipItem[`setting_basic.${item.pkey}`]"
              effect="dark"
              :content="tooltipItem[`setting_basic.${item.pkey}`]"
              placement="top"
            >
              <el-icon
                ><Icon name="dv-info"><dvInfo class="svg-icon" /></Icon
              ></el-icon>
            </el-tooltip>
          </div>
        </template>
        <el-switch
          class="crest-basic-switch"
          v-if="
            item.pkey === 'autoCreateUser' ||
            item.pkey === 'pwdStrategy' ||
            item.pkey === 'dip' ||
            item.pkey === 'loginLimit'
          "
          active-value="true"
          inactive-value="false"
          v-model="state.form[item.pkey]"
        />
        <div v-else-if="item.pkey === 'dsIntervalTime'" class="ds-task-form-inline">
          <span>{{ t('cron.every') }}</span>
          <el-input-number
            v-model="state.form.dsIntervalTime"
            autocomplete="off"
            step-strictly
            class="text-left"
            :min="1"
            :placeholder="t('common.inputText')"
            controls-position="right"
            type="number"
          />
          <el-select v-model="state.form.dsExecuteTime">
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <span class="ds-span">{{ t('cron.every_exec') }}</span>
        </div>
        <div v-else-if="item.pkey === 'frontTimeOut'">
          <el-input-number
            v-model="state.form.frontTimeOut"
            autocomplete="off"
            step-strictly
            class="text-left edit-all-line"
            :min="1"
            :placeholder="t('common.inputText')"
            controls-position="right"
            type="number"
          />
        </div>
        <div v-else-if="item.pkey === 'thresholdLimit'">
          <el-input-number
            v-model="state.form.thresholdLimit"
            autocomplete="off"
            step-strictly
            class="text-left edit-all-line"
            :min="1"
            :max="50"
            :placeholder="t('common.inputText')"
            controls-position="right"
            type="number"
          />
        </div>
        <div
          v-else-if="
            item.pkey === 'logLiveTime' ||
            item.pkey === 'thresholdLogLiveTime' ||
            item.pkey === 'dataFillingLogLiveTime' ||
            item.pkey === 'loginLimitRate' ||
            item.pkey === 'loginLimitTime'
          "
        >
          <el-input-number
            v-model="state.form[item.pkey]"
            autocomplete="off"
            step-strictly
            class="text-left edit-all-line"
            :min="1"
            :max="4000"
            :placeholder="t('common.inputText')"
            controls-position="right"
            type="number"
          />
        </div>
        <div v-else-if="item.pkey === 'platformOid'">
          <el-tree-select
            class="edit-all-line"
            v-model="state.form[item.pkey]"
            :data="state.orgOptions"
            check-strictly
            :render-after-expand="false"
            @change="oidChange"
          />
        </div>
        <div v-else-if="item.pkey === 'platformRid'">
          <el-tree-select
            class="edit-all-line"
            v-model="state.form[item.pkey]"
            :data="state.roleOptions"
            :highlight-current="true"
            multiple
            :render-after-expand="false"
            :placeholder="$t('common.please_select') + $t('user.role')"
            show-checkbox
            check-on-click-node
          />
        </div>
        <div v-else-if="item.pkey === 'pvp'">
          <el-select v-model="state.form[item.pkey]" class="edit-all-line">
            <el-option
              v-for="item in pvpOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
        <div v-else-if="item.pkey === 'exportFileLiveTime'">
          <el-input-number
            v-model="state.form[item.pkey]"
            autocomplete="off"
            step-strictly
            class="text-left edit-all-line"
            :min="1"
            :max="4000"
            :placeholder="t('common.inputText')"
            controls-position="right"
            type="number"
          />
        </div>
        <div v-else-if="item.pkey === 'initialPassword'">
          <el-input
            v-model.trim="state.form[item.pkey]"
            class="edit-all-line"
            type="password"
            show-password
            autocomplete="new-password"
            maxlength="100"
            :placeholder="t('common.inputText')"
          />
        </div>
        <div v-else-if="item.pkey === 'defaultLogin'">
          <el-radio-group v-model="state.form[item.pkey]">
            <el-radio v-for="item in state.loginOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </div>
        <div v-else-if="item.pkey === 'defaultSort'">
          <el-radio-group v-model="state.form[item.pkey]">
            <el-radio v-for="item in state.sortOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </div>
        <div v-else-if="item.pkey === 'defaultOpen'">
          <el-radio-group v-model="state.form[item.pkey]">
            <el-radio v-for="item in state.openOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </div>
        <v-else />
      </el-form-item>
    </el-form>
    <template #footer>
      <!-- 抽屉底部只提交当前分组参数，保存成功后由父页面刷新参数列表。 -->
      <span class="dialog-footer">
        <el-button secondary @click="resetForm(basicForm)">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm(basicForm)">
          {{ t('commons.save') }}
        </el-button>
      </span>
    </template>
  </el-drawer>
</template>
<style lang="less">
.basic-param-drawer {
  .ed-drawer__footer {
    box-shadow: 0 -1px 4px #1f232926 !important;
    height: 64px !important;
    padding: 16px 24px !important;
    .dialog-footer {
      height: 32px;
      line-height: 32px;
    }
  }
  .ed-form-item__label {
    line-height: 22px !important;
    height: 22px !important;

    .basic-form-info-tips {
      width: fit-content;
      display: inline-flex;
      align-items: center;
      column-gap: 4px;
    }
  }

  .ed-form-item {
    &.is-required.asterisk-right {
      .ed-form-item__label:after {
        display: none;
      }
      .basic-form-info-tips {
        .custom-form-item__label:after {
          content: '*';
          color: var(--ed-color-danger);
          margin-left: 2px;
          font-family: var(--crest-custom_font, 'PingFang');
          font-size: 14px;
          font-style: normal;
          font-weight: 400;
        }
      }
    }
  }
  .ed-radio__label {
    font-weight: 400;
  }
}
</style>
<style scoped lang="less">
.basic-param-drawer {
  .ed-form-item {
    margin-bottom: 16px;
  }
  .is-error {
    margin-bottom: 40px !important;
  }
  .edit-all-line {
    width: 552px !important;
  }
}
.setting-hidden-item {
  display: none !important;
}
.ds-task-form-inline {
  width: 100%;
  display: flex;
  .ed-input-number {
    width: 140px;
    margin: 0 6px;
  }
  .ed-select {
    width: 240px;
    :deep(.ed-input) {
      width: 100% !important;
    }
  }
  span.ds-span {
    margin-left: 6px;
  }
}
.crest-basic-switch {
  height: 22px;
}
</style>
