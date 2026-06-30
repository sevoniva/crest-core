<script lang="tsx" setup>
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import { reactive, toRefs, watch, ref } from 'vue'
import { multFieldValuesForPermissions } from '@/api/dataset'

const { t } = useI18n()

// 接收图表和筛选项配置，item 会直接回写到图表字段的结果过滤配置中。
const props = defineProps({
  chart: {
    type: Object,
    required: true
  },
  item: {
    type: Object,
    required: true
  }
})

const { item } = toRefs(props)

// 标记是否需要请求枚举值，避免枚举筛选切换时重复请求同一字段。
const needRequestEnum = ref(true)

// 维护结果筛选编辑状态
const state = reactive({
  textOptions: [
    {
      label: '',
      options: [
        {
          value: 'eq',
          label: t('chart.filter_eq')
        },
        {
          value: 'not_eq',
          label: t('chart.filter_not_eq')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'like',
          label: t('chart.filter_like')
        },
        {
          value: 'not like',
          label: t('chart.filter_not_like')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'null',
          label: t('chart.filter_null')
        },
        {
          value: 'not_null',
          label: t('chart.filter_not_null')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'empty',
          label: t('chart.filter_empty')
        },
        {
          value: 'not_empty',
          label: t('chart.filter_not_empty')
        }
      ]
    }
  ],
  dateOptions: [
    {
      label: '',
      options: [
        {
          value: 'eq',
          label: t('chart.filter_eq')
        },
        {
          value: 'not_eq',
          label: t('chart.filter_not_eq')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'lt',
          label: t('chart.filter_lt')
        },
        {
          value: 'gt',
          label: t('chart.filter_gt')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'le',
          label: t('chart.filter_le')
        },
        {
          value: 'ge',
          label: t('chart.filter_ge')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'not_null',
          label: t('chart.filter_not_null')
        }
      ]
    }
  ],
  valueOptions: [
    {
      label: '',
      options: [
        {
          value: 'eq',
          label: t('chart.filter_eq')
        },
        {
          value: 'not_eq',
          label: t('chart.filter_not_eq')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'lt',
          label: t('chart.filter_lt')
        },
        {
          value: 'gt',
          label: t('chart.filter_gt')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'le',
          label: t('chart.filter_le')
        },
        {
          value: 'ge',
          label: t('chart.filter_ge')
        }
      ]
    },
    {
      label: '',
      options: [
        {
          value: 'not_null',
          label: t('chart.filter_not_null')
        }
      ]
    }
  ],
  // options 按字段类型在 text/date/value 三组操作符之间切换。
  options: [],
  logic: '',
  filterType: '',
  enumCheckField: [],
  fieldOptions: []
})

// 监听筛选项变化并刷新可选条件，字段类型切换时操作符集合需要同步更新。
watch(
  [props.item],
  () => {
    initOptions()
  },
  { deep: true }
)

// 初始化筛选条件选项
const initOptions = () => {
  if (item?.value) {
    if (item.value.fieldType === 0 || item.value.fieldType === 5) {
      // 文本和地理字段使用文本匹配操作符。
      state.options = JSON.parse(JSON.stringify(state.textOptions))
    } else if (item.value.fieldType === 1) {
      // 日期字段只开放等值和大小比较，不在结果过滤内处理日期区间控件。
      state.options = JSON.parse(JSON.stringify(state.dateOptions))
    } else {
      // 数值字段使用数值比较操作符。
      state.options = JSON.parse(JSON.stringify(state.valueOptions))
    }
  }
}
// 初始化筛选编辑状态
const init = () => {
  state.logic = item.value.logic
  state.filterType = item.value.filterType
  state.enumCheckField = item.value.enumCheckField
  if (item.value.filterType === 'enum') {
    initEnumOptions()
  }
}
// 初始化枚举筛选选项
const initEnumOptions = () => {
  // 查找枚举值，当前只对文本和地理字段请求后端枚举。
  if (item.value.fieldType === 0 || item.value.fieldType === 5) {
    multFieldValuesForPermissions({ fieldIds: [item.value.id] }).then(res => {
      state.fieldOptions = optionData(res.data)
      needRequestEnum.value = false
    })
  }
}
// 将接口返回值转换为枚举选项
const optionData = data => {
  if (!data) return null
  // 枚举选择器使用 id/text 结构，保留原始值作为提交值。
  return data
    .filter(item => !!item)
    .map(item => {
      return {
        id: item,
        text: item
      }
    })
}
// 新增筛选条件
const addFilter = () => {
  // 新增条件默认使用等值匹配，由用户继续选择操作符和值。
  item.value.filter.push({
    fieldId: item.value.id,
    term: 'eq',
    value: ''
  })
}
// 删除筛选条件
const removeFilter = index => {
  item.value.filter.splice(index, 1)
}
// 同步筛选逻辑关系
const logicChange = val => {
  item.value.logic = val
}
// 同步筛选类型变化
const filterTypeChange = val => {
  item.value.filterType = val
  // 首次切换到枚举筛选时再请求枚举值，减少普通条件筛选的接口调用。
  if (val === 'enum' && needRequestEnum.value) {
    initEnumOptions()
  }
}
// 同步枚举勾选字段
const enumChange = () => {
  item.value.enumCheckField = state.enumCheckField
}

initOptions()
init()
</script>

<template>
  <div @keydown.stop @keyup.stop>
    <el-col>
      <div v-if="item.fieldType === 0 || item.fieldType === 5">
        <div>{{ t('chart.slc_logic') }}:</div>
        <el-radio-group
          v-model="state.filterType"
          size="small"
          :style="{ margin: '6px 0 10px 0' }"
          @change="filterTypeChange"
        >
          <el-radio style="min-width: 80px" value="logic">{{ t('chart.logic_exp') }}</el-radio>
          <el-radio style="min-width: 80px" value="enum">{{ t('chart.enum_exp') }}</el-radio>
        </el-radio-group>
      </div>

      <div
        v-if="
          ((item.fieldType === 0 || item.fieldType === 5) && state.filterType === 'logic') ||
          item.fieldType === 1 ||
          item.fieldType === 2 ||
          item.fieldType === 3
        "
      >
        <div
          v-show="item.filter && item.filter.length > 0"
          style="display: flex; padding: 10px 0; align-items: center"
        >
          <span>{{ t('chart.conform_below') }}</span>
          <el-select
            v-model="state.logic"
            @change="logicChange"
            size="small"
            style="width: 60px; margin: 0 6px"
          >
            <el-option style="min-width: 80px" :label="t('chart.logic_and')" value="and" />
            <el-option style="min-width: 80px" :label="t('chart.logic_or')" value="or" />
          </el-select>
          <span>{{ t('chart.addition') }}</span>
        </div>
        <div
          style="max-height: 50vh; overflow-y: auto"
          v-show="item.filter && item.filter.length > 0"
          class="addition-style"
        >
          <div class="name-title">
            <span>{{ item.name }}</span>
          </div>
          <div v-for="(f, index) in item.filter" :key="index" class="filter-item">
            <div style="width: 164px; margin-right: 8px">
              <el-select v-model="f.term">
                <el-option-group
                  v-for="(group, idx) in state.options"
                  :key="idx"
                  :label="group.label"
                >
                  <el-option
                    v-for="opt in group.options"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-option-group>
              </el-select>
            </div>
            <div style="flex: 1">
              <el-input
                v-show="!f.term.includes('null') && !f.term.includes('empty')"
                v-model="f.value"
                class="value-item"
                :placeholder="t('chart.condition')"
                clearable
              />
            </div>

            <el-button class="m-del-icon-btn" text @click="removeFilter(index)">
              <el-icon size="20px">
                <Icon name="icon_delete-trash_outlined"
                  ><icon_deleteTrash_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </el-button>
          </div>
        </div>
        <el-button
          text
          class="circle-button"
          @click="addFilter"
          :style="{ marginTop: item.filter && item.filter.length > 0 ? '10px' : 0 }"
        >
          <Icon name="icon_add_outlined"
            ><icon_add_outlined style="width: 14px" class="svg-icon"
          /></Icon>
          {{ t('chart.add_addition') }}
        </el-button>
      </div>

      <div v-if="(item.fieldType === 0 || item.fieldType === 5) && state.filterType === 'enum'">
        <span style="margin-right: 10px">{{ t('chart.filter_exp') }}</span>
        <el-select
          v-model="state.enumCheckField"
          filterable
          collapse-tags
          multiple
          :placeholder="t('chart.pls_slc')"
          @change="enumChange"
          :clearable="true"
        >
          <el-option
            v-for="field in state.fieldOptions"
            :key="field.id"
            :label="field.text"
            :value="field.id"
          />
        </el-select>
      </div>
    </el-col>
  </div>
</template>

<style lang="less" scoped>
.filter-item {
  display: flex;
  width: 100%;
  margin-bottom: 8px;
  justify-content: left;
  align-items: center;

  &:last-child {
    margin-bottom: unset;
  }

  :deep(input) {
    font-size: 14px !important;
    line-height: 22px;
  }
}
.form-item :deep(.el-form-item__label) {
  font-size: 12px;
}
span {
  font-size: 12px;
}

.value-item :deep(.el-input) {
  position: relative;
  display: inline-block;
  width: 80px !important;
}

.addition-style {
  padding: 16px;
  background: #f5f6f7;

  .name-title {
    margin-bottom: 8px;

    :deep(span) {
      color: var(--N900, #1f2329);
      /* 中文/桌面端/正文 14 22 Regular */
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 22px;

      cursor: default;
    }
  }
}
.btn-delete {
  min-width: auto !important;
}
:deep(.ed-select .ed-select-tags-wrapper.has-prefix) {
  display: flex !important;
  align-items: center;
}
:deep(.ed-select__tags .ed-tag) {
  margin: 0px 4px 0 0 !important;
}
.m-del-icon-btn {
  color: #646a73;
  margin-left: 4px;

  &:hover {
    background: rgba(31, 35, 41, 0.1) !important;
  }
  &:focus {
    background: rgba(31, 35, 41, 0.1) !important;
  }
  &:active {
    background: rgba(31, 35, 41, 0.2) !important;
  }
}
</style>
