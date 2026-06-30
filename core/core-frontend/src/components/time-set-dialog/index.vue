<script lang="ts" setup>
import { ref, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import dayjs from 'dayjs'

// 提供弹窗内按钮、粒度选项等文案的国际化函数
const { t } = useI18n()
// 控制时间设置弹窗的显示状态
const dialogFormVisible = ref(false)
// 保存当前选择的时间粒度和值
const form = reactive({
  type: '',
  value: ''
})
// 初始化弹窗表单，并在未传入类型时默认按年选择
const init = (type, value) => {
  dialogFormVisible.value = true
  form.type = type || 'year'
  form.value = type ? value : ''
}

// 日期选择器支持的时间粒度列表
const timeList = [
  {
    label: t('dynamic_time.year'),
    value: 'year'
  },
  {
    label: t('chart.y_M'),
    value: 'month'
  },
  {
    label: t('chart.y_M_d'),
    value: 'date'
  },
  {
    label: t('chart.y_M_d_H_m_s'),
    value: 'datetime'
  }
]

// 不同时间粒度对应的格式化模板
const formatMap = {
  datetime: 'YYYY/MM/DD HH:mm:ss',
  date: 'YYYY/MM/DD',
  month: 'YYYY/MM',
  year: 'YYYY'
}

// 关闭弹窗时重置表单，避免下次打开沿用旧状态
const beforeClose = () => {
  form.type = ''
  form.value = ''
  dialogFormVisible.value = false
}
// 向父组件抛出保存后的时间粒度和值
const emits = defineEmits(['saveTime'])
// 按当前粒度将日期值格式化为查询参数
const formatValue = (val: any) => {
  if (!val) return ''
  return dayjs(val).format(formatMap[form.type])
}
// 确认当前时间选择并关闭弹窗
const confirm = () => {
  const value = form.value
  if (value) emits('saveTime', form.type, formatValue(form.value))
  beforeClose()
}

defineExpose({
  init
})
</script>

<template>
  <el-dialog
    :before-close="beforeClose"
    v-model="dialogFormVisible"
    :title="$t('data_set.time')"
    width="600"
    append-to-body
  >
    <el-form label-position="top">
      <el-form-item :label="$t('v_query.time_granularity')">
        <el-select
          :placeholder="$t('v_query.the_time_granularity')"
          v-model="form.type"
          style="width: 58%"
        >
          <el-option
            v-for="ele in timeList"
            :key="ele.value"
            :label="ele.label"
            :value="ele.value"
          />
        </el-select>
        <el-date-picker style="margin-left: auto" v-model="form.value" :type="form.type" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="beforeClose">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirm">
          {{ t('dataset.confirm') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="less" scoped></style>
