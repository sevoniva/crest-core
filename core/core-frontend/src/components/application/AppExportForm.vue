<template>
  <el-drawer
    :title="t('visualization.app_export')"
    v-model="state.applyDownloadDrawer"
    modal-class="crest-user-drawer"
    size="600px"
    direction="rtl"
  >
    <div class="app-export">
      <el-form
        ref="applyDownloadForm"
        :model="state.form"
        :rules="state.rule"
        class="crest-form-item"
        label-width="180px"
        label-position="top"
      >
        <el-form-item :label="t('visualization.app_name')" prop="appName">
          <el-input
            v-model="state.form.appName"
            autocomplete="off"
            :placeholder="t('common.input_name')"
          />
        </el-form-item>
        <el-form-item :label="t('visualization.app_version')" prop="version">
          <el-input v-model="state.form.version" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="t('visualization.app_export')" prop="required">
          <el-input v-model="state.form.required" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="t('visualization.creator')" prop="creator">
          <el-input v-model="state.form.creator" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="t('visualization.description')" prop="description">
          <el-input
            :placeholder="t('commons.input_content')"
            show-word-limit
            v-model="state.form.description"
            type="textarea"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <div class="apply" style="width: 100%">
        <el-button secondary @click="close">{{ t('commons.cancel') }} </el-button>
        <el-button type="primary" @click="downloadApp">{{ t('chart.export') }}</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import { ElButton, ElDrawer, ElForm, ElFormItem, ElInput } from 'element-plus-secondary'
import { reactive, ref, toRefs } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { export2AppCheck } from '@/api/visualization/dataVisualization'
const { t } = useI18n()
// 声明抽屉关闭和应用下载事件
const emits = defineEmits(['closeDraw', 'downLoadApp'])
// 持有应用下载表单实例
const applyDownloadForm = ref(null)

// 定义应用导出表单所需的组件、视图和资源信息
const props = defineProps({
  componentData: {
    type: Object,
    required: true
  },
  canvasViewInfo: {
    type: Object,
    required: true
  },
  dvInfo: {
    type: Object,
    required: true
  }
})

const { componentData, canvasViewInfo, dvInfo } = toRefs(props)

// 保存应用导出抽屉状态、表单数据和校验规则
const state = reactive({
  applyDownloadDrawer: false,
  form: {
    appName: null,
    icon: null,
    version: null,
    creator: null,
    required: '2.8.0',
    description: null
  },
  rule: {
    appName: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    creator: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    required: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    version: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ]
  }
})

// 使用外部参数初始化应用导出表单
const init = params => {
  state.applyDownloadDrawer = true
  state.form = params
}

// 关闭应用导出抽屉并通知父组件
const close = () => {
  emits('closeDraw')
  state.applyDownloadDrawer = false
}

// 汇总应用依赖的视图、数据源和组件信息
const gatherAppInfo = (viewIds, dsIds, componentDataCheck) => {
  componentDataCheck.forEach(item => {
    if (item.component === 'VQuery' && item.propValue?.length) {
      item.propValue.forEach(filterItem => {
        if (filterItem.dataset?.id) {
          dsIds.push(filterItem.dataset.id)
        }
      })
    } else if (item.component === 'UserView' && canvasViewInfo.value[item.id]) {
      const viewDetails = canvasViewInfo.value[item.id]
      const { id, tableId } = viewDetails
      viewIds.push(id)
      dsIds.push(tableId)
    } else if (item.component === 'Group') {
      gatherAppInfo(viewIds, dsIds, item.propValue)
    } else if (item.component === 'Tabs') {
      item.propValue.forEach(tabItem => {
        gatherAppInfo(viewIds, dsIds, tabItem.componentData)
      })
    }
  })
}
// 校验应用导出内容并触发下载
const downloadApp = () => {
  applyDownloadForm.value?.validate(valid => {
    if (valid) {
      const viewIds = []
      const dsIds = []
      gatherAppInfo(viewIds, dsIds, componentData.value)
      export2AppCheck({ dvId: dvInfo.value.id, viewIds, dsIds }).then(rsp => {
        const params = {
          ...rsp.data,
          ...state.form,
          visualizationInfo: JSON.stringify(dvInfo.value)
        }
        emits('downLoadApp', params)
        state.applyDownloadDrawer = false
      })
    } else {
      return false
    }
  })
}

defineExpose({
  init
})
</script>
<style lang="less" scoped>
.app-export {
  width: 100%;
  height: calc(100% - 56px);
}

.app-export-bottom {
  width: 100%;
  height: 56px;
  text-align: right;
}

:deep(.ed-drawer__body) {
  padding-bottom: 0 !important;
}
</style>
