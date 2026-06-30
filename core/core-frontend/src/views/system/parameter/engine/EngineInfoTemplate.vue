<template>
  <div class="engine-setting">
    <InfoTemplate
      ref="infoTemplate"
      setting-key="basic"
      showValidate
      :setting-title="t('system.engine_settings')"
      :setting-data="templateList"
      @edit="edit"
      @check="validateById"
    />
    <div class="crest-expand-content">
      <div class="crest-expand-engine" @click="showPriority = !showPriority">
        {{ t('datasource.priority') }}
        <el-icon>
          <Icon
            ><component
              class="svg-icon"
              :is="showPriority ? icon_down_outlined : icon_down_outlined1"
            ></component
          ></Icon>
        </el-icon>
      </div>
    </div>
    <InfoTemplate
      v-if="showPriority"
      ref="infoTemplateTime"
      class="engine-advanced-card"
      hide-head
      :setting-data="templateListTime"
    />
  </div>
  <!--    数据填报      -->
</template>

<script lang="ts" setup>
import icon_down_outlined1 from '@/assets/svg/icon_down_outlined-1.svg'
import icon_down_outlined from '@/assets/svg/icon_down_outlined.svg'
import { ref, nextTick } from 'vue'
import { SettingRecord } from '@/views/system/common/SettingTemplate'
import { ElMessage } from 'element-plus-secondary'
import { useI18n } from '@/hooks/web/useI18n'
import InfoTemplate from '@/views/system/common/InfoTemplate.vue'
import { dsTypes } from '@/views/visualized/data/datasource/form/option'
import { getEngineDatasource } from '@/api/datasource'
import request from '@/config/axios'
import { querySymmetricKey } from '@/api/login'
import { symmetricDecrypt } from '@/utils/encryption'
const { t } = useI18n()
const typeMap = dsTypes.reduce((pre, next) => {
  pre[next.type] = next.name
  return pre
}, {})
const schemaBasedEngineTypes = ['obOracle']
const schemaValue = configuration => {
  if (configuration?.schema) return configuration.schema
  const username = configuration?.username || ''
  if (!username) return ''
  const atIndex = username.indexOf('@')
  const clusterIndex = username.indexOf('#')
  let endIndex = username.length
  if (atIndex >= 0) endIndex = Math.min(endIndex, atIndex)
  if (clusterIndex >= 0) endIndex = Math.min(endIndex, clusterIndex)
  return username.substring(0, endIndex).toUpperCase()
}
// 控制是否展示连接池和超时优先级配置
const showPriority = ref(true)
let nodeInfoId
// 持有基础配置展示组件实例
const infoTemplate = ref()
// 持有连接池配置展示组件实例
const infoTemplateTime = ref()
// 保存基础配置展示数据
const templateList = ref<SettingRecord[]>([])
// 保存连接池和超时配置展示数据
const templateListTime = ref<SettingRecord[]>([])
// 加载引擎数据源配置并填充展示模板
const getEngine = () => {
  querySymmetricKey().then(response => {
    getEngineDatasource().then(res => {
      let { id, type, configuration } = res.data
      if (configuration) {
        configuration = JSON.parse(symmetricDecrypt(configuration, response.data))
      }
      nodeInfoId = id
      templateListTime.value = [
        {
          pkey: 'datasource.initial_pool_size',
          pval: configuration?.initialPoolSize || 5,
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.min_pool_size',
          pval: configuration?.minPoolSize || 5,
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.max_pool_size',
          pval: configuration?.maxPoolSize || 5,
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.query_timeout',
          pval: `${configuration?.queryTimeout || 30}${t('common.second')}`,
          type: '',
          sort: 0
        }
      ]

      templateList.value = [
        {
          pkey: t('system.engine_type'),
          pval: typeMap[type],
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.host',
          pval: configuration?.host,
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.port',
          pval: configuration?.port,
          type: '',
          sort: 0
        },
        schemaBasedEngineTypes.includes(type)
          ? {
              pkey: 'datasource.schema',
              pval: schemaValue(configuration),
              type: '',
              sort: 0
            }
          : {
              pkey: 'datasource.data_base',
              pval: configuration?.dataBase,
              type: '',
              sort: 0
            },
        {
          pkey: 'datasource.user_name',
          pval: configuration?.username,
          type: '',
          sort: 0
        },
        {
          pkey: 'datasource.extra_params',
          pval: configuration?.extraParams,
          type: '',
          sort: 0
        }
      ]
      nextTick(() => {
        infoTemplate.value.init()
        infoTemplateTime.value.init()
      })
    })
  })
}
getEngine()

defineExpose({
  getEngine
})
// 声明编辑引擎配置时向父组件派发的事件
const emits = defineEmits(['edit'])
// 通知父组件进入引擎配置编辑流程
const edit = () => {
  emits('edit')
}

// 触发当前引擎配置连通性校验
const validateById = async () => {
  request.post({ url: '/engine/validate/' + nodeInfoId }).then(res => {
    if (res !== undefined) {
      ElMessage.success(t('datasource.validate_success'))
    }
  })
}
</script>
<style lang="less" scoped>
.engine-setting {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.crest-expand-content {
  height: 28px;
  display: inline-flex;
  align-items: center;
  margin: -2px 0 -2px 0;
  .crest-expand-engine {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
    color: var(--ed-color-primary);
    cursor: pointer;
    height: 28px;
    padding: 0 10px;
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    transition: background-color 0.16s ease, color 0.16s ease;

    .ed-icon {
      margin-left: 4px;
    }

    &:hover {
      background: #eff6ff;
    }
  }
}
.engine-advanced-card {
  margin-top: -4px;
}
</style>
