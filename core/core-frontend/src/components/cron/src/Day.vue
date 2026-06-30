<script lang="ts" setup>
import { ref, reactive, computed, watch, onBeforeMount } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { useI18n } from '@/hooks/web/useI18n'
// 定义日期 Cron 片段的双向绑定值
const props = defineProps({
  modelValue: propTypes.string.def('?')
})
const { t } = useI18n()

// 保存当前日期规则类型
const type = ref('5')
// 保存工作日规则的日期值
const work = ref(0)
// 保存最后一天规则的偏移值
const last = ref(0)
// 保存不同日期规则的输入状态
const state = reactive({
  cycle: {
    // 周期
    start: 0,
    end: 0
  },
  loop: {
    // 循环
    start: 0,
    end: 0
  },
  week: {
    // 指定周
    start: 0,
    end: 0
  },
  appoint: []
})

// 根据当前规则状态生成日期 Cron 表达式片段
const resultValue = computed(() => {
  const result = []
  switch (type.value) {
    case '1': // 每天。
      result.push('*')
      break
    case '2': // 周期范围。
      result.push(`${state.cycle.start}-${state.cycle.end}`)
      break
    case '3': // 循环间隔。
      result.push(`${state.loop.start}/${state.loop.end}`)
      break
    case '4': // 指定日期。
      result.push(state.appoint.join(','))
      break
    case '6': // 最后一天。
      result.push(`${last.value === 0 ? '' : last.value}L`)
      break
    case '7': // 指定周。
      result.push(`${state.week.start}#${state.week.end}`)
      break
    case '8': // 工作日。
      result.push(`${work.value}W`)
      break
    default: // 不指定。
      result.push('?')
      break
  }
  return result.join('')
})

onBeforeMount(() => {
  updateVal()
})

// 监听外部绑定值变化并回填本地规则状态
watch(
  () => props.modelValue,
  () => {
    updateVal()
  }
)

// 监听本地规则结果变化并同步给父组件
watch(
  () => resultValue.value,
  () => {
    emits('update:modelValue', resultValue.value)
  }
)

// 将外部 Cron 日期片段解析为本地规则状态
const updateVal = () => {
  if (!props.modelValue) {
    return
  }
  // 将输入片段转换为数字，非法值按 0 处理
  const toNumber = (value: string) => Number(value) || 0
  if (props.modelValue === '?') {
    type.value = '5'
  } else if (props.modelValue.indexOf('-') !== -1) {
    // 周期范围
    if (props.modelValue.split('-').length === 2) {
      type.value = '2'
      state.cycle.start = toNumber(props.modelValue.split('-')[0])
      state.cycle.end = toNumber(props.modelValue.split('-')[1])
    }
  } else if (props.modelValue.indexOf('/') !== -1) {
    // 循环间隔
    if (props.modelValue.split('/').length === 2) {
      type.value = '3'
      state.loop.start = toNumber(props.modelValue.split('/')[0])
      state.loop.end = toNumber(props.modelValue.split('/')[1])
    }
  } else if (props.modelValue.indexOf('*') !== -1) {
    // 每天
    type.value = '1'
  } else if (props.modelValue.indexOf('L') !== -1) {
    // 最后一天
    type.value = '6'
    last.value = toNumber(props.modelValue.replace('L', ''))
  } else if (props.modelValue.indexOf('#') !== -1) {
    // 指定周
    if (props.modelValue.split('#').length === 2) {
      type.value = '7'
      state.week.start = toNumber(props.modelValue.split('#')[0])
      state.week.end = toNumber(props.modelValue.split('#')[1])
    }
  } else if (props.modelValue.indexOf('W') !== -1) {
    // 工作日
    type.value = '8'
    work.value = toNumber(props.modelValue.replace('W', ''))
  } else {
    // 指定日期
    type.value = '4'
    state.appoint = props.modelValue.split(',')
  }
}

// 声明日期 Cron 片段更新事件
const emits = defineEmits(['update:modelValue'])
</script>

<template>
  <div>
    <div>
      <el-radio v-model="type" label="1" size="small" border>{{ t('cron.every_day') }}</el-radio>
    </div>
    <div>
      <el-radio v-model="type" label="5" size="small" border>{{ t('cron.not_set') }}</el-radio>
    </div>
    <div>
      <el-radio v-model="type" label="2" size="small" border>{{ t('cron.cycle') }}</el-radio>
      <span style="margin-right: 5px; margin-left: 10px">{{ t('cron.from') }}</span>
      <el-input-number
        v-model="state.cycle.start"
        :min="1"
        :max="31"
        size="small"
        style="width: 100px"
        @change="type = '2'"
      />
      <span style="margin-right: 5px; margin-left: 5px">{{ t('cron.to') }}</span>
      <el-input-number
        v-model="state.cycle.end"
        :min="2"
        :max="31"
        size="small"
        style="width: 100px"
        @change="type = '2'"
      />
      {{ t('cron.day') }}
    </div>
    <div>
      <el-radio v-model="type" label="3" size="small" border>{{ t('cron.repeat') }}</el-radio>
      <span style="margin-right: 5px; margin-left: 10px">{{ t('cron.from') }}</span>
      <el-input-number
        v-model="state.loop.start"
        :min="1"
        :max="31"
        size="small"
        style="width: 100px"
        @change="type = '3'"
      />
      <span style="margin-right: 5px; margin-left: 5px">{{ t('cron.day_begin') }}</span>
      <el-input-number
        v-model="state.loop.end"
        :min="1"
        :max="31"
        size="small"
        style="width: 100px"
        @change="type = '3'"
      />
      {{ t('cron.day_exec') }}
    </div>
    <div>
      <el-radio v-model="type" label="8" size="small" border>{{ t('cron.work_day') }}</el-radio>
      <span style="margin-right: 5px; margin-left: 10px">{{ t('cron.this_month') }}</span>
      <el-input-number
        v-model="work"
        :min="1"
        :max="31"
        size="small"
        style="width: 100px"
        @change="type = '8'"
      />
      {{ t('cron.day_near_work_day') }}
    </div>
    <div>
      <el-radio v-model="type" label="6" size="small" border>{{
        t('cron.this_week_last_day')
      }}</el-radio>
    </div>
    <div>
      <el-radio v-model="type" label="4" size="small" border>{{ t('cron.set') }}</el-radio>
      <el-checkbox-group v-model="state.appoint">
        <div v-for="i in 4" :key="i" style="margin-left: 10px; line-height: 25px">
          <template v-for="j in 10">
            <el-checkbox
              v-if="parseInt(i - 1 + '' + (j - 1), 10) < 32 && !(i === 1 && j === 1)"
              :key="j"
              :label="i - 1 + '' + (j - 1)"
              @change="type = '4'"
            />
          </template>
        </div>
      </el-checkbox-group>
    </div>
  </div>
</template>

<style lang="less" scoped></style>
