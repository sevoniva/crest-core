<script lang="ts" setup>
import { onBeforeMount, PropType, ref, toRefs } from 'vue'
import { cloneDeep } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'

// 接口分页配置，描述分页类型以及请求、响应字段映射
export interface PageSetting {
  pageType: string
  requestData: requestItem[]
  responseData: responseItem[]
}

// 分页请求参数行，记录内置变量和实际请求参数的绑定
export interface requestItem {
  parameterName: string
  builtInParameterName: string
  requestParameterName: string
  parameterDefaultValue: string
}

// 分页响应解析行，记录总数或游标字段的解析路径
export interface responseItem {
  parameterName: string
  resolutionPath: string
  resolutionPathType: string
}
// 分页表单直接修改传入的 page 对象以保持父级配置同步
const props = defineProps({
  page: {
    type: Object as PropType<PageSetting>,
    default: () => ({
      pageType: '',
      requestData: [],
      responseData: []
    })
  }
})
// 解构为响应式引用，避免编辑表格时丢失父级对象关联
const { page } = toRefs(props)
// 分页表单的多语言翻译入口
const { t } = useI18n()
// 分页类型下拉选项，覆盖页码、游标和无分页三种模式
const options = [
  {
    value: 'pageNumber',
    label: t('api_pagination.number__size')
  },
  {
    value: 'cursor',
    label: t('api_pagination.cursor__size')
  },
  {
    value: 'empty',
    label: t('chart.line_symbol_none')
  }
]

// 页码和游标分页共用的默认请求参数模板
const requestData = ref([
  {
    parameterName: t('api_pagination.page_number'),
    builtInParameterName: '${pageToken}',
    requestParameterName: '',
    parameterDefaultValue: ''
  },
  {
    parameterName: t('api_pagination.pagination_size'),
    builtInParameterName: '${pageSize}',
    requestParameterName: '',
    parameterDefaultValue: ''
  }
])

// 页码分页响应字段的可选解析类型
const defaultPathArr = [
  {
    value: 'totalNumber',
    label: t('api_pagination.total_count')
  },
  {
    value: 'totalPage',
    label: t('api_pagination.number_of_pages')
  }
]

// 游标分页响应字段的可选解析类型
const cursorPathArr = [
  {
    value: 'cursor',
    label: t('api_pagination.cursor')
  }
]

// 当前响应解析类型选项会随分页模式切换
const resolutionPathOptions = ref(cloneDeep(defaultPathArr))

// 默认响应解析模板，进入页面时会补齐到配置对象
const responseData = ref([
  {
    parameterName: t('api_pagination.total_number'),
    resolutionPath: '',
    resolutionPathType: 'number'
  }
])

// 初始化缺省分页配置，并同步当前分页类型对应的字段文案
onBeforeMount(() => {
  if (!page.value.requestData || page.value.requestData.length === 0) {
    page.value.requestData = requestData.value
  }
  if (!page.value.responseData || page.value.responseData.length === 0) {
    page.value.responseData = responseData.value
  }
  if (page.value.pageType === '' || !page.value.pageType) {
    page.value.pageType = 'empty'
  }
  handleNumberSizeChange()
})

// 根据分页类型切换请求变量、响应解析字段和候选路径
const handleNumberSizeChange = () => {
  if (page.value.pageType === 'pageNumber') {
    page.value.responseData[0].resolutionPathType = 'totalNumber'
    page.value.responseData[0].parameterName = t('api_pagination.total_number')
    resolutionPathOptions.value = cloneDeep(defaultPathArr)
    page.value.requestData[0].parameterName = t('api_pagination.page_number')
    page.value.requestData[0].builtInParameterName = '${pageNumber}'
  }
  if (page.value.pageType === 'cursor') {
    page.value.responseData[0].resolutionPathType = 'cursor'
    page.value.responseData[0].parameterName = t('api_pagination.cursor')
    resolutionPathOptions.value = cloneDeep(cursorPathArr)
    page.value.requestData[0].parameterName = t('api_pagination.cursor')
    page.value.requestData[0].builtInParameterName = '${pageToken}'
  }
}
</script>

<template>
  <div class="api-pagination">
    <span class="type">{{ t('api_pagination.pagination_method') }}</span>
    <el-select
      v-model="page.pageType"
      @change="handleNumberSizeChange"
      style="width: 100%; margin-top: 8px"
    >
      <el-option
        v-for="item in options"
        :key="item.value"
        :label="item.label"
        :value="item.value"
      />
    </el-select>
    <template v-if="page.pageType !== 'empty'">
      <div class="table-title request">{{ t('datasource.request') }}</div>
      <el-table
        class="crest-data-table"
        header-cell-class-name="header-cell"
        :data="page.requestData"
        style="width: 100%"
      >
        <el-table-column prop="parameterName" :label="t('api_pagination.parameter_name')" />
        <el-table-column
          prop="builtInParameterName"
          :label="t('api_pagination.built_in_parameter_name')"
        />
        <el-table-column :label="t('api_pagination.parameter_default_value')" width="220">
          <template #default="scope">
            <el-input
              v-model="scope.row.parameterDefaultValue"
              style="width: 100%"
              :placeholder="
                scope.row.builtInParameterName === '${pageNumber}'
                  ? t('api_pagination.enter_first_page')
                  : t('api_pagination.enter_default_value')
              "
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="table-title response">{{ t('api_pagination.response') }}</div>
      <el-table
        class="crest-data-table"
        header-cell-class-name="header-cell"
        :data="page.responseData"
        style="width: 100%"
      >
        <el-table-column
          prop="parameterName"
          :label="t('api_pagination.parameter_name')"
          width="160"
        />
        <el-table-column prop="resolutionPath" :label="t('api_pagination.parsing_path')">
          <template #default="scope">
            <el-input
              v-model="scope.row.resolutionPath"
              style="width: 100%"
              :placeholder="t('api_pagination.please_enter_jsonpath')"
              ><template #prepend>
                <el-select
                  class="bg-white"
                  v-model="scope.row.resolutionPathType"
                  style="width: 89px"
                >
                  <el-option
                    v-for="item in resolutionPathOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select> </template
            ></el-input>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<style lang="less" scoped>
.api-pagination {
  .type {
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
  }

  .table-title {
    width: 100%;
    height: 30px;
    padding-left: 12px;
    display: flex;
    align-items: center;

    &.request {
      background: #ebf1ff;
      margin-top: 16px;
      border-top: 1px solid #dddedf;
    }

    &.response {
      background: #e6f7f5;
    }
  }

  .bg-white {
    :deep(.ed-input__wrapper) {
      background: white;
    }
  }
}
</style>
