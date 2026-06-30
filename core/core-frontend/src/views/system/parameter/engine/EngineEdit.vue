<script lang="ts" setup>
import icon_down_outlined1 from '@/assets/svg/icon_down_outlined-1.svg'
import icon_down_outlined from '@/assets/svg/icon_down_outlined.svg'
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElLoading } from 'element-plus-secondary'
import { useI18n } from '@/hooks/web/useI18n'
import request from '@/config/axios'
import { dsTypes, Node } from '@/views/visualized/data/datasource/form/option'
import { cloneDeep } from 'lodash-es'
import { getEngineDatasource } from '@/api/datasource'
import { CustomPassword } from '@/components/custom-password'
import { Base64 } from 'js-base64'
import { querySymmetricKey } from '@/api/login'
import { symmetricDecrypt } from '@/utils/encryption'
const { t } = useI18n()
// 引擎编辑弹窗用于维护系统内置计算引擎的数据源连接配置。
const dialogVisible = ref(false)
// 引擎编辑加载实例需要手动关闭，避免保存和校验请求结束后遮罩残留。
const loadingInstance = ref(null)

// 基础字段校验只覆盖引擎名称，连接参数校验由 configRules 单独维护。
const defaultRule = {
  name: [
    {
      required: true,
      message: t('datasource.input_name'),
      trigger: 'blur'
    },
    {
      min: 2,
      max: 64,
      message: t('datasource.input_limit_2_25', [2, 64]),
      trigger: 'blur'
    }
  ]
}
const schemaBasedEngineTypes = ['obOracle']

// 使用 Schema 定位对象空间的引擎不要求填写数据库名称。
const validateDatabaseName = (_rule, value, callback) => {
  if (schemaBasedEngineTypes.includes(nodeInfo.type) || value) {
    callback()
    return
  }
  callback(new Error(t('datasource.please_input_data_base')))
}

// 连接参数校验规则覆盖 JDBC、账号密码、连接池和查询超时等必填项。
const configRules = {
  'configuration.dataBase': [
    {
      validator: validateDatabaseName,
      trigger: 'blur'
    }
  ],
  'configuration.username': [
    {
      required: true,
      message: t('datasource.please_input_user_name'),
      trigger: 'blur'
    }
  ],
  'configuration.password': [
    {
      required: true,
      message: t('datasource.please_input_password'),
      trigger: 'blur'
    }
  ],
  'configuration.host': [
    {
      required: true,
      message: t('datasource._ip_address'),
      trigger: 'blur'
    }
  ],
  'configuration.jdbc': [
    {
      required: true,
      message: t('datasource.please_input_jdbc_url'),
      trigger: 'blur'
    }
  ],
  'configuration.extraParams': [
    {
      required: false,
      message: t('datasource.please_input_url'),
      trigger: 'blur'
    }
  ],
  'configuration.port': [
    {
      required: true,
      message: t('datasource.please_input_port'),
      trigger: 'blur'
    }
  ],
  'configuration.initialPoolSize': [
    {
      required: true,
      message: t('common.inputText') + ' ' + t('datasource.initial_pool_size'),
      trigger: 'blur'
    }
  ],
  'configuration.minPoolSize': [
    {
      required: true,
      message: t('common.inputText') + ' ' + t('datasource.min_pool_size'),
      trigger: 'blur'
    }
  ],
  'configuration.maxPoolSize': [
    {
      required: true,
      message: t('common.inputText') + ' ' + t('datasource.max_pool_size'),
      trigger: 'blur'
    }
  ],
  'configuration.queryTimeout': [
    {
      required: true,
      message: t('common.inputText') + ' ' + t('datasource.query_timeout'),
      trigger: 'blur'
    }
  ]
}
// 合并基础规则和连接规则，保持表单使用单一 rules 对象。
const rule = { ...cloneDeep(configRules), ...cloneDeep(defaultRule) }

// 保存完成后通知父级刷新引擎配置展示。
const emits = defineEmits(['saved'])
// 重置引擎表单时恢复默认值，避免下一次打开沿用旧连接信息。
const resetForm = () => {
  dialogVisible.value = false
  Object.assign(nodeInfo, cloneDeep(defaultInfo))
}

// 关闭并重置引擎编辑弹窗。
const reset = () => {
  resetForm()
}

// 打开引擎编辑加载遮罩，目标限定在抽屉区域。
const showLoading = () => {
  loadingInstance.value = ElLoading.service({ target: '.basic-param-drawer' })
}
// 关闭引擎编辑加载遮罩。
const closeLoading = () => {
  loadingInstance.value?.close()
}
// 控制连接池和查询超时等高级配置显隐。
const showPriority = ref(false)
const usesSchemaField = computed(() => schemaBasedEngineTypes.includes(nodeInfo.type))
// 默认引擎配置覆盖基本信息、连接参数和连接池参数。
const defaultInfo = {
  name: '',
  createBy: '',
  creator: '',
  createTime: '',
  description: '',
  id: 0,
  size: 0,
  nodeType: '',
  type: '',
  fileName: '',
  configuration: {
    host: '',
    jdbc: '',
    port: 8081,
    dataBase: '',
    schema: '',
    username: '',
    password: '',
    extraParams: '',
    initialPoolSize: 50,
    minPoolSize: 50,
    maxPoolSize: 100,
    queryTimeout: 30
  },
  syncSetting: null,
  apiConfiguration: [],
  weight: 0
}
// 当前引擎节点信息直接作为表单模型使用。
const nodeInfo = reactive(cloneDeep(defaultInfo))
// 打开引擎编辑弹窗时先获取对称密钥，再解密后端返回的连接配置。
const edit = () => {
  querySymmetricKey().then(response => {
    getEngineDatasource()
      .then(res => {
        let {
          name,
          createBy,
          id,
          createTime,
          creator,
          type,
          pid,
          configuration,
          syncSetting,
          fileName,
          size,
          description,
          lastSyncTime,
          enableDataFill
        } = res.data
        if (configuration) {
          // 后端保存的是加密连接配置，前端展示前需要用当前会话密钥解密。
          configuration = JSON.parse(symmetricDecrypt(configuration, response.data))
        }
        Object.assign(nodeInfo, {
          name,
          pid,
          description,
          fileName,
          size,
          createTime,
          creator,
          createBy,
          id,
          type,
          configuration,
          syncSetting,
          lastSyncTime,
          enableDataFill
        })
      })
      .finally(() => {
        // 数据加载完成后再展示抽屉，避免用户看到空表单闪烁。
        dialogVisible.value = true
      })
  })
}
// 引擎基础表单实例用于触发表单校验。
const basicForm = ref()

// 提交引擎配置前将连接信息 Base64 编码，由后端继续完成加密和持久化。
const submitForm = async () => {
  let data = JSON.parse(JSON.stringify(nodeInfo)) as unknown as Omit<
    Node,
    'configuration' | 'apiConfiguration'
  > & {
    configuration: string
    apiConfiguration: string
  }
  data.configuration = Base64.encode(JSON.stringify(data.configuration))
  basicForm.value.validate(result => {
    if (result) {
      // 表单校验通过后才提交保存，失败时保留当前表单供用户修正。
      showLoading()
      request
        .post({ url: '/engine/record', data: data })
        .then(res => {
          if (res !== undefined) {
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

// 校验引擎连接配置复用同一份表单数据，但只调用 validate 接口不保存。
const validate = async () => {
  let data = JSON.parse(JSON.stringify(nodeInfo)) as unknown as Omit<
    Node,
    'configuration' | 'apiConfiguration'
  > & {
    configuration: string
    apiConfiguration: string
  }
  data.configuration = Base64.encode(JSON.stringify(data.configuration))
  basicForm.value.validate(result => {
    if (result) {
      // 连接校验请求只反馈连通性，不触发父级刷新。
      showLoading()
      request
        .post({ url: '/engine/validate', data: data })
        .then(res => {
          if (res !== undefined) {
            ElMessage.success(t('datasource.validate_success'))
          }
          closeLoading()
        })
        .catch(() => {
          closeLoading()
        })
    }
  })
}

defineExpose({
  // 父组件通过 ref 主动打开编辑弹窗，避免把系统参数列表状态耦合进当前表单。
  edit
})
</script>

<template>
  <el-drawer
    :title="t('system.engine_settings')"
    v-model="dialogVisible"
    modal-class="basic-param-drawer"
    size="600px"
    direction="rtl"
  >
    <el-form
      ref="basicForm"
      require-asterisk-position="right"
      :model="nodeInfo"
      :rules="rule"
      label-width="80px"
      label-position="top"
    >
      <el-form-item :label="t('datasource.type')">
        <el-select v-model="nodeInfo.type" class="crest-select" disabled>
          <el-option
            v-for="item in dsTypes"
            :key="item.type"
            :label="item.name"
            :value="item.type"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('datasource.host')"
        prop="configuration.jdbc"
        v-if="nodeInfo.type === 'h2'"
      >
        <!-- H2 引擎直接维护 JDBC 地址，其它引擎拆分为主机和端口字段。 -->
        <el-input
          v-model="nodeInfo.configuration.jdbc"
          :placeholder="t('data_source.jdbc_connection_string')"
          autocomplete="off"
        />
      </el-form-item>
      <el-form-item
        :label="t('datasource.host')"
        prop="configuration.host"
        v-if="nodeInfo.type !== 'h2'"
      >
        <el-input
          v-model="nodeInfo.configuration.host"
          :placeholder="t('datasource._ip_address')"
          autocomplete="off"
        />
      </el-form-item>
      <el-form-item
        :label="t('datasource.port')"
        prop="configuration.port"
        v-if="nodeInfo.type !== 'h2'"
      >
        <el-input-number
          v-model="nodeInfo.configuration.port"
          autocomplete="off"
          step-strictly
          class="text-left"
          :min="0"
          :placeholder="t('common.inputText') + t('datasource.port')"
          controls-position="right"
          type="number"
        />
      </el-form-item>
      <el-form-item
        v-if="!usesSchemaField"
        :label="t('datasource.data_base')"
        prop="configuration.dataBase"
      >
        <el-input
          v-model="nodeInfo.configuration.dataBase"
          :placeholder="t('datasource.please_input_data_base')"
          autocomplete="off"
        />
      </el-form-item>
      <el-form-item v-else :label="t('datasource.schema')" prop="configuration.schema">
        <el-input
          v-model="nodeInfo.configuration.schema"
          :placeholder="`${t('common.inputText')} ${t('datasource.schema')}`"
          autocomplete="off"
        />
      </el-form-item>

      <el-form-item :label="t('datasource.user_name')">
        <el-input
          :placeholder="t('common.inputText') + t('datasource.user_name')"
          v-model="nodeInfo.configuration.username"
          autocomplete="off"
        />
      </el-form-item>
      <el-form-item :label="t('datasource.password')">
        <CustomPassword
          :placeholder="t('common.inputText') + t('datasource.password')"
          show-password
          type="password"
          v-model="nodeInfo.configuration.password"
        />
      </el-form-item>
      <el-form-item :label="t('datasource.extra_params')">
        <el-input
          :placeholder="t('common.inputText') + t('datasource.extra_params')"
          v-model="nodeInfo.configuration.extraParams"
          autocomplete="off"
        />
      </el-form-item>
      <el-form-item>
        <!-- 高级配置默认收起，减少普通连接维护时的表单噪音。 -->
        <span class="crest-expand-engine" @click="showPriority = !showPriority"
          >{{ t('datasource.priority') }}
          <el-icon>
            <Icon
              ><component
                class="svg-icon"
                :is="showPriority ? icon_down_outlined : icon_down_outlined1"
              ></component
            ></Icon>
          </el-icon>
        </span>
      </el-form-item>

      <template v-if="showPriority">
        <el-row :gutter="24" class="mb16">
          <el-col :span="12">
            <el-form-item
              :label="t('datasource.initial_pool_size')"
              prop="configuration.initialPoolSize"
            >
              <el-input-number
                v-model="nodeInfo.configuration.initialPoolSize"
                controls-position="right"
                autocomplete="off"
                class="w100-input"
                :placeholder="t('common.inputText') + t('datasource.initial_pool_size')"
                type="number"
                :min="0"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('datasource.min_pool_size')" prop="configuration.minPoolSize">
              <el-input-number
                v-model="nodeInfo.configuration.minPoolSize"
                controls-position="right"
                autocomplete="off"
                class="w100-input"
                :placeholder="t('common.inputText') + t('datasource.min_pool_size')"
                type="number"
                :min="0"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item :label="t('datasource.max_pool_size')" prop="configuration.maxPoolSize">
              <el-input-number
                v-model="nodeInfo.configuration.maxPoolSize"
                class="w100-input"
                controls-position="right"
                autocomplete="off"
                :placeholder="t('common.inputText') + t('datasource.max_pool_size')"
                type="number"
                :min="0"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="`${t('datasource.query_timeout')}(${t('common.second')})`"
              prop="configuration.queryTimeout"
            >
              <el-input-number
                class="w100-input"
                v-model="nodeInfo.configuration.queryTimeout"
                controls-position="right"
                autocomplete="off"
                :placeholder="t('common.inputText') + t('datasource.query_timeout')"
                type="number"
                :min="0"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
      <!--    数据填报      -->
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button secondary @click="resetForm()">{{ t('common.cancel') }}</el-button>
        <el-button secondary @click="validate()">{{ t('datasource.validate') }}</el-button>
        <el-button type="primary" @click="submitForm()">
          {{ t('commons.save') }}
        </el-button>
      </span>
    </template>
  </el-drawer>
</template>
<style lang="less">
.basic-param-drawer {
  // 抽屉样式限定在参数编辑容器下，避免影响其它全局抽屉。
  .crest-expand-engine {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: var(--ed-color-primary);
    cursor: pointer;
    display: inline-flex;
    align-items: center;

    .ed-icon {
      margin-left: 4px;
    }
  }

  .w100-input {
    width: 100%;
  }
  .ed-drawer__footer {
    // 底部操作区固定高度，保证校验、取消、保存按钮在不同语言下稳定对齐。
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
  }
}
</style>
<style scoped lang="less">
.basic-param-drawer {
  .ed-form-item {
    // 表单项间距与抽屉宽度匹配，错误态再额外预留提示空间。
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
</style>
