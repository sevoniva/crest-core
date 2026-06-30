<script lang="ts" setup>
import { ref, reactive, computed, watch, onBeforeMount } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { useI18n } from '@/hooks/web/useI18n'
import type { Corn } from './Hour.vue'
// 定义月份字段组件接收的 cron 表达式片段
const props = defineProps({
  modelValue: propTypes.string.def('?')
})

const { t } = useI18n()

// 记录当前选中的月份配置类型
const type = ref('1')
// 保存工作日语法解析时的占位值，保持字段结构和其他 cron 组件一致
const work = ref<string | number>(0)
// 保存最后一个月语法中的偏移值
const last = ref<string | number>(0)
// 保存周期、循环、指定周和指定值的编辑状态
const state = reactive<Corn>({
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

// 根据当前编辑状态生成月份字段对应的 cron 片段
const resultValue = computed(() => {
  const result = []
  switch (type.value) {
    case '1': // 每秒
      result.push('*')
      break
    case '2': // 年期
      result.push(`${state.cycle.start}-${state.cycle.end}`)
      break
    case '3': // 循环
      result.push(`${state.loop.start}/${state.loop.end}`)
      break
    case '4': // 指定
      result.push(state.appoint.join(','))
      break
    case '6': // 最后
      result.push(`${last.value === 0 ? '' : last.value}L`)
      break
    default: // 不指定
      result.push('?')
      break
  }
  return result.join('')
})

onBeforeMount(() => {
  updateVal()
})

// 监听外部传入值变化并同步到本地编辑状态
watch(
  () => props.modelValue,
  () => {
    updateVal()
  }
)

// 监听本地计算结果并向父组件回写月份字段
watch(
  () => resultValue.value,
  () => {
    emits('update:modelValue', resultValue.value)
  }
)

// 将外部 cron 片段解析为组件内部的编辑状态
const updateVal = () => {
  if (!props.modelValue) {
    return
  }
  if (props.modelValue === '?') {
    type.value = '5'
  } else if (props.modelValue.indexOf('-') !== -1) {
    // 2周期
    if (props.modelValue.split('-').length === 2) {
      type.value = '2'
      state.cycle.start = props.modelValue.split('-')[0] as unknown as number
      state.cycle.end = props.modelValue.split('-')[1] as unknown as number
    }
  } else if (props.modelValue.indexOf('/') !== -1) {
    // 3循环
    if (props.modelValue.split('/').length === 2) {
      type.value = '3'
      state.loop.start = props.modelValue.split('/')[0] as unknown as number
      state.loop.end = props.modelValue.split('/')[1] as unknown as number
    }
  } else if (props.modelValue.indexOf('*') !== -1) {
    // 1每
    type.value = '1'
  } else if (props.modelValue.indexOf('L') !== -1) {
    // 6最后
    type.value = '6'
    last.value = props.modelValue.replace('L', '')
  } else if (props.modelValue.indexOf('#') !== -1) {
    // 7指定周
    if (props.modelValue.split('#').length === 2) {
      type.value = '7'
      state.week.start = props.modelValue.split('#')[0]
      state.week.end = props.modelValue.split('#')[1]
    }
  } else if (props.modelValue.indexOf('W') !== -1) {
    // 8工作日
    type.value = '8'
    work.value = props.modelValue.replace('W', '')
  } else {
    // *
    type.value = '4'
    state.appoint = props.modelValue.split(',')
  }
}

// 声明月份字段变更时向父组件派发的事件
const emits = defineEmits(['update:modelValue'])
</script>

<template>
  <div>
    <div>
      <el-radio v-model="type" label="1" size="small" border>{{ t('cron.every_month') }}</el-radio>
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
        :max="12"
        size="small"
        style="width: 100px"
        @change="type = '2'"
      />
      <span style="margin-right: 5px; margin-left: 5px">{{ t('cron.to') }}</span>
      <el-input-number
        v-model="state.cycle.end"
        :min="2"
        :max="12"
        size="small"
        style="width: 100px"
        @change="type = '2'"
      />
      {{ t('cron.month') }}
    </div>
    <div>
      <el-radio v-model="type" label="3" size="small" border>{{ t('cron.repeat') }}</el-radio>
      <span style="margin-right: 5px; margin-left: 10px">{{ t('cron.from') }}</span>
      <el-input-number
        v-model="state.loop.start"
        :min="1"
        :max="12"
        size="small"
        style="width: 100px"
        @change="type = '3'"
      />
      <span style="margin-right: 5px; margin-left: 5px">{{ t('cron.month_begin') }}</span>
      <el-input-number
        v-model="state.loop.end"
        :min="1"
        :max="12"
        size="small"
        style="width: 100px"
        @change="type = '3'"
      />
      {{ t('cron.month_exec') }}
    </div>
    <div>
      <el-radio v-model="type" label="4" size="small" border>{{ t('cron.set') }}</el-radio>
      <el-checkbox-group v-model="state.appoint" style="margin-left: 0; line-height: 25px">
        <el-checkbox v-for="i in 12" :key="i" :label="i + ''" @change="type = '4'" />
      </el-checkbox-group>
    </div>
  </div>
</template>

<style lang="less" scoped></style>
