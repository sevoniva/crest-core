<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { EditPen, Refresh, Search, View, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus-secondary'
import GridTable from '@/components/grid-table/src/GridTable.vue'
import {
  dataAssetDetailApi,
  dataAssetImpactApi,
  dataAssetOwnersApi,
  dataAssetPageApi,
  saveDataAssetProfileApi,
  type DataAsset,
  type DataAssetDetail,
  type DataAssetImpactItem,
  type DataAssetOwner
} from '@/api/dataAsset'
import {
  assetGovernanceBadges,
  assetTypeOptions,
  canEditGovernance,
  canEditProfile,
  editableGovernanceStatus,
  governanceFilterOptions,
  governanceFilterToRequest,
  normalizeGovernanceStatus,
  updateGovernanceStatus
} from './dataAssetPolicy.mjs'

// 数据资产列表的筛选、分页和表格数据状态
const state = reactive({
  loading: false,
  keyword: '',
  assetType: 'all',
  governanceStatus: 'all',
  ownerId: '' as string | number | '',
  page: 1,
  pageSize: 20,
  total: 0,
  records: [] as DataAsset[]
})

// 资产详情抽屉显示状态
const detailDrawerVisible = ref(false)
// 资产详情加载状态
const detailLoading = ref(false)
// 当前详情抽屉选中的页签
const activeDetailTab = ref('basic')
// 当前抽屉展示的资产详情
const detail = ref<DataAssetDetail | null>(null)
// 资产信息维护弹窗显示状态
const profileDialogVisible = ref(false)
// 资产信息保存状态
const profileSaving = ref(false)
// 负责人列表加载状态
const ownerLoading = ref(false)
// 数据集废弃影响范围加载状态
const profileImpactLoading = ref(false)
// 可选负责人列表
const owners = ref<DataAssetOwner[]>([])
// 当前资产的下游影响范围
const profileImpact = ref<DataAssetImpactItem[]>([])
// 资产信息维护表单
const profileForm = reactive({
  assetType: '',
  assetId: '',
  name: '',
  description: '',
  tags: '',
  ownerId: '' as string | number | '',
  certified: false,
  recommended: false,
  deprecated: false
})

// 资产类型到展示文案的映射
const typeLabelMap = computed(() =>
  assetTypeOptions.reduce<Record<string, string>>((acc, item) => {
    if (item.value !== 'all') {
      acc[item.value] = item.label
    }
    return acc
  }, {})
)

// 当前资产下游影响按类型汇总后的展示数据
const impactSummary = computed(() => {
  const summary = detail.value?.impact?.summary || {}
  return Object.keys(summary).map(key => ({
    type: key,
    label: typeLabelMap.value[key] || key,
    count: Number(summary[key] || 0)
  }))
})

// 当前资产影响范围明细
const impactItems = computed(() => detail.value?.impact?.items || [])

// 当前详情抽屉中的资产主体信息
const currentAsset = computed(() => detail.value?.asset)
// 当前资产上下游关系列表，统一补充关系方向
const lineageRows = computed(() => [
  ...relationList(detail.value?.upstream).map(item => ({ ...item, direction: '上游' })),
  ...relationList(detail.value?.downstream).map(item => ({ ...item, direction: '下游' }))
])
// 详情抽屉页签配置和数量徽标
const detailTabOptions = computed(() => [
  { label: '基础信息', value: 'basic', count: null },
  {
    label: '上下游',
    value: 'lineage',
    count:
      Number(currentAsset.value?.upstreamCount || 0) +
      Number(currentAsset.value?.downstreamCount || 0)
  },
  { label: '影响范围', value: 'impact', count: impactItems.value.length }
])
// 当前维护对象是否为数据集，数据集才允许编辑治理状态
const profileIsDataset = computed(() => profileForm.assetType === 'dataset')
// 废弃数据集弹窗中优先展示的影响范围列表
const visibleProfileImpact = computed(() => profileImpact.value.slice(0, 5))
// 废弃数据集弹窗中未展开展示的影响范围数量
const remainingProfileImpactCount = computed(() =>
  Math.max(profileImpact.value.length - visibleProfileImpact.value.length, 0)
)

// 表格分页组件配置
const paginationConfig = reactive({
  currentPage: state.page,
  pageSize: state.pageSize,
  pageSizes: [20, 50, 100],
  pagerCount: 7,
  layout: 'total, prev, pager, next, sizes, jumper',
  total: state.total
})

// 将当前筛选条件转换为资产列表接口参数
const requestParams = () => ({
  keyword: state.keyword || undefined,
  assetType: state.assetType === 'all' ? undefined : state.assetType,
  ownerId: state.ownerId || undefined,
  ...governanceFilterToRequest(state.governanceStatus)
})

// 格式化资产时间戳
const formatTime = (time?: number) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-')

// 加载数据资产列表并同步分页状态
const loadAssets = async () => {
  state.loading = true
  try {
    const res = await dataAssetPageApi(state.page, state.pageSize, requestParams())
    const data = res?.data || {}
    state.records = data.records || []
    state.total = Number(data.total || 0)
    paginationConfig.currentPage = state.page
    paginationConfig.pageSize = state.pageSize
    paginationConfig.total = state.total
  } finally {
    state.loading = false
  }
}

// 重置到第一页并按当前筛选条件查询
const search = () => {
  state.page = 1
  loadAssets()
}

// 清空所有筛选条件并重新查询
const resetFilters = () => {
  state.keyword = ''
  state.assetType = 'all'
  state.governanceStatus = 'all'
  state.ownerId = ''
  search()
}

// 切换页码后加载对应页数据
const pageChange = (page: number) => {
  state.page = page
  paginationConfig.currentPage = page
  loadAssets()
}

// 切换每页条数后回到第一页
const sizeChange = (pageSize: number) => {
  state.pageSize = pageSize
  state.page = 1
  paginationConfig.pageSize = pageSize
  paginationConfig.currentPage = 1
  loadAssets()
}

// 加载资产详情并打开指定详情页签
const loadDetail = async (asset: DataAsset, tab = 'basic') => {
  detailDrawerVisible.value = true
  activeDetailTab.value = tab
  detailLoading.value = true
  try {
    const res = await dataAssetDetailApi(asset.assetType, asset.assetId)
    detail.value = res?.data || null
  } finally {
    detailLoading.value = false
  }
}

// 懒加载负责人列表，供筛选和资产维护使用
const loadOwners = async () => {
  if (owners.value.length) {
    return
  }
  ownerLoading.value = true
  try {
    const res = await dataAssetOwnersApi()
    owners.value = res?.data || []
  } finally {
    ownerLoading.value = false
  }
}

// 打开资产信息维护弹窗，并按权限和资产类型初始化表单
const openProfileDialog = async (asset: DataAsset) => {
  if (!canEditProfile(asset)) {
    ElMessage.warning(profileTooltip(asset))
    return
  }
  await loadOwners()
  profileImpact.value = []
  const governanceStatus = editableGovernanceStatus(asset)
  Object.assign(profileForm, {
    assetType: asset.assetType,
    assetId: asset.assetId,
    name: asset.name,
    description: asset.description || '',
    tags: asset.tags || '',
    ownerId: asset.ownerId || '',
    certified: governanceStatus.certified,
    recommended: governanceStatus.recommended,
    deprecated: governanceStatus.deprecated
  })
  profileDialogVisible.value = true
  if (asset.assetType === 'dataset') {
    loadProfileImpact(asset)
  }
}

// 加载资产维护弹窗中的下游影响范围
const loadProfileImpact = async (asset: DataAsset) => {
  profileImpactLoading.value = true
  try {
    const res = await dataAssetImpactApi(asset.assetType, asset.assetId)
    profileImpact.value = res?.data?.items || []
  } finally {
    profileImpactLoading.value = false
  }
}

// 切换认证状态，并按互斥规则同步治理状态
const changeCertified = (value: boolean) => {
  Object.assign(profileForm, updateGovernanceStatus(profileForm, 'certified', value))
}

// 切换推荐状态，并按互斥规则同步治理状态
const changeRecommended = (value: boolean) => {
  Object.assign(profileForm, updateGovernanceStatus(profileForm, 'recommended', value))
}

// 切换废弃状态，并按互斥规则同步治理状态
const changeDeprecated = (value: boolean) => {
  Object.assign(profileForm, updateGovernanceStatus(profileForm, 'deprecated', value))
}

// 保存资产负责人、标签、说明和治理状态
const saveProfile = async () => {
  const status = profileIsDataset.value
    ? normalizeGovernanceStatus(profileForm)
    : { certified: false, recommended: false, deprecated: false }
  profileSaving.value = true
  try {
    const res = await saveDataAssetProfileApi({
      assetType: profileForm.assetType,
      assetId: profileForm.assetId,
      description: profileForm.description,
      tags: profileForm.tags,
      ownerId: profileForm.ownerId || null,
      certified: status.certified,
      recommended: status.recommended,
      deprecated: status.deprecated
    })
    profileDialogVisible.value = false
    ElMessage.success('保存成功')
    await loadAssets()
    if (
      detailDrawerVisible.value &&
      currentAsset.value?.assetType === profileForm.assetType &&
      currentAsset.value?.assetId === profileForm.assetId
    ) {
      detail.value = res?.data || detail.value
    }
  } finally {
    profileSaving.value = false
  }
}

// 将可空关系数组规范化为空数组
const relationList = (items?: DataAssetImpactItem[]) => items || []

// 根据关系方向返回空状态文案
const relationEmptyText = (type: 'upstream' | 'downstream') =>
  type === 'upstream' ? '暂无上游资产' : '暂无下游资产'

// 根据维护权限生成编辑按钮提示文案
const profileTooltip = (asset: DataAsset) => {
  return canEditProfile(asset) ? '编辑资产信息' : '无维护权限'
}

// 页面初始化时加载资产列表
onMounted(loadAssets)
</script>

<template>
  <div class="asset-manage">
    <div class="asset-content">
      <div class="asset-table-info">
        <!-- 筛选区组合关键字、资产类型、治理状态和负责人，查询时统一转换为接口参数。 -->
        <div class="search-operate">
          <el-input
            v-model.trim="state.keyword"
            clearable
            class="asset-search"
            placeholder="搜索资产名称、ID 或标签"
            @clear="search"
            @keyup.enter="search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="state.assetType" class="asset-filter" @change="search">
            <el-option
              v-for="item in assetTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="state.governanceStatus" class="asset-filter" @change="search">
            <el-option
              v-for="item in governanceFilterOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select
            v-model="state.ownerId"
            clearable
            filterable
            class="asset-owner-filter"
            :loading="ownerLoading"
            placeholder="全部负责人"
            @change="search"
            @visible-change="visible => visible && loadOwners()"
          >
            <el-option
              v-for="owner in owners"
              :key="owner.id"
              :label="owner.account ? `${owner.name}（${owner.account}）` : owner.name"
              :value="owner.id"
            />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadAssets">刷新</el-button>
        </div>

        <!-- 资产列表展示治理标识、负责人、上下游数量和维护入口。 -->
        <div class="info-table">
          <grid-table
            class="asset-grid-table"
            :pagination="paginationConfig"
            :table-data="state.records"
            :is-search="!!state.keyword"
            :data-loading="state.loading"
            :row-key="row => `${row.assetType}-${row.assetId}`"
            @size-change="sizeChange"
            @current-change="pageChange"
          >
            <el-table-column label="资产" min-width="260">
              <template #default="{ row }">
                <div class="asset-name-cell">
                  <div class="asset-name-main">
                    <span class="asset-name">{{ row.name || '-' }}</span>
                    <span class="asset-id">{{ row.assetId }}</span>
                  </div>
                  <div v-if="assetGovernanceBadges(row).length" class="badge-row">
                    <span
                      v-for="badge in assetGovernanceBadges(row)"
                      :key="badge.label"
                      class="governance-badge"
                      :class="badge.className"
                    >
                      {{ badge.label }}
                    </span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag effect="plain">
                  {{ row.assetTypeLabel || typeLabelMap[row.assetType] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="负责人" min-width="150">
              <template #default="{ row }">
                <div class="muted-main">{{ row.ownerName || row.creatorName || '-' }}</div>
                <div class="muted-sub">{{ row.orgName || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.description || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="上下游" width="120">
              <template #default="{ row }">
                <span class="lineage-count">上游 {{ row.upstreamCount || 0 }}</span>
                <span class="lineage-count">下游 {{ row.downstreamCount || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-tooltip content="查看详情" placement="top">
                  <el-button text :icon="View" @click.stop="loadDetail(row, 'basic')" />
                </el-tooltip>
                <el-tooltip content="影响范围" placement="top">
                  <el-button text :icon="WarningFilled" @click.stop="loadDetail(row, 'impact')" />
                </el-tooltip>
                <el-tooltip :content="profileTooltip(row)" placement="top">
                  <el-button
                    text
                    :icon="EditPen"
                    :disabled="!canEditProfile(row)"
                    @click.stop="openProfileDialog(row)"
                  />
                </el-tooltip>
              </template>
            </el-table-column>
          </grid-table>
        </div>
      </div>
    </div>

    <!-- 详情抽屉按基础信息、上下游和影响范围分区，避免列表页承载过多关系数据。 -->
    <el-drawer
      v-model="detailDrawerVisible"
      size="560px"
      :with-header="false"
      class="asset-detail-drawer"
    >
      <div v-loading="detailLoading" class="asset-detail">
        <template v-if="currentAsset">
          <div class="drawer-head">
            <div>
              <div class="drawer-title">{{ currentAsset.name }}</div>
              <div class="drawer-sub">
                {{ currentAsset.assetTypeLabel || typeLabelMap[currentAsset.assetType] }} /
                {{ currentAsset.assetId }}
              </div>
            </div>
            <el-button
              v-if="canEditProfile(currentAsset)"
              type="primary"
              plain
              @click="openProfileDialog(currentAsset)"
            >
              编辑资产信息
            </el-button>
          </div>

          <div v-if="assetGovernanceBadges(currentAsset).length" class="badge-row drawer-badges">
            <span
              v-for="badge in assetGovernanceBadges(currentAsset)"
              :key="badge.label"
              class="governance-badge"
              :class="badge.className"
            >
              {{ badge.label }}
            </span>
          </div>

          <!-- 页签数量徽标来自当前详情数据，切换只改变本地展示状态。 -->
          <div class="detail-tabs" role="tablist" aria-label="资产详情">
            <button
              v-for="tab in detailTabOptions"
              :key="tab.value"
              type="button"
              :class="['detail-tab', { active: activeDetailTab === tab.value }]"
              role="tab"
              :aria-selected="activeDetailTab === tab.value"
              @click="activeDetailTab = tab.value"
            >
              <span>{{ tab.label }}</span>
              <span v-if="tab.count !== null" class="detail-tab-count">{{ tab.count }}</span>
            </button>
          </div>

          <div class="detail-panel">
            <div v-if="activeDetailTab === 'basic'" class="detail-table-wrap">
              <table class="detail-table detail-field-table">
                <tbody>
                  <tr>
                    <th>负责人</th>
                    <td>{{ currentAsset.ownerName || '-' }}</td>
                  </tr>
                  <tr>
                    <th>创建人</th>
                    <td>{{ currentAsset.creatorName || '-' }}</td>
                  </tr>
                  <tr>
                    <th>所属组织</th>
                    <td>{{ currentAsset.orgName || '-' }}</td>
                  </tr>
                  <tr>
                    <th>创建时间</th>
                    <td>{{ formatTime(currentAsset.createTime) }}</td>
                  </tr>
                  <tr>
                    <th>更新时间</th>
                    <td>{{ formatTime(currentAsset.updateTime) }}</td>
                  </tr>
                  <tr>
                    <th>标签</th>
                    <td>{{ currentAsset.tags || '-' }}</td>
                  </tr>
                  <tr>
                    <th>资产说明</th>
                    <td class="pre-line">{{ currentAsset.description || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else-if="activeDetailTab === 'lineage'" class="detail-table-wrap">
              <table class="detail-table">
                <thead>
                  <tr>
                    <th class="w-direction">方向</th>
                    <th class="w-type">类型</th>
                    <th>名称</th>
                    <th>关系</th>
                    <th class="w-time">更新时间</th>
                  </tr>
                </thead>
                <tbody v-if="lineageRows.length">
                  <tr
                    v-for="item in lineageRows"
                    :key="`${item.direction}-${item.assetType}-${item.assetId}`"
                  >
                    <td>{{ item.direction }}</td>
                    <td>
                      <span class="relation-type">{{ item.assetTypeLabel }}</span>
                    </td>
                    <td class="strong-cell">{{ item.name }}</td>
                    <td>{{ item.relation }}</td>
                    <td>{{ formatTime(item.updateTime) }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!lineageRows.length" class="table-empty">暂无上下游资产</div>
            </div>
            <div v-else class="detail-table-wrap">
              <div class="impact-summary">
                <div v-if="!impactSummary.length" class="relation-empty">暂无下游影响</div>
                <div v-for="item in impactSummary" :key="item.type" class="impact-stat">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
              <table class="detail-table">
                <thead>
                  <tr>
                    <th class="w-type">类型</th>
                    <th>名称</th>
                    <th>关系</th>
                    <th class="w-time">更新时间</th>
                  </tr>
                </thead>
                <tbody v-if="impactItems.length">
                  <tr v-for="item in impactItems" :key="`${item.assetType}-${item.assetId}`">
                    <td>
                      <span class="relation-type">{{ item.assetTypeLabel }}</span>
                    </td>
                    <td class="strong-cell">{{ item.name }}</td>
                    <td>{{ item.relation }}</td>
                    <td>{{ formatTime(item.updateTime) }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!impactItems.length" class="table-empty">暂无下游影响</div>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 资产维护弹窗同时保存负责人、说明、标签和数据集治理状态。 -->
    <el-dialog v-model="profileDialogVisible" title="资产信息" width="560px">
      <el-form label-width="96px" class="profile-form">
        <el-form-item label="资产名称">
          <el-input v-model="profileForm.name" disabled />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="profileForm.ownerId"
            clearable
            filterable
            class="full-width"
            :loading="ownerLoading"
            placeholder="选择负责人"
          >
            <el-option
              v-for="owner in owners"
              :key="owner.id"
              :label="owner.account ? `${owner.name}（${owner.account}）` : owner.name"
              :value="owner.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="profileIsDataset" label="治理状态">
          <div class="status-checks">
            <el-checkbox
              v-model="profileForm.certified"
              :disabled="profileForm.deprecated"
              @change="changeCertified"
            >
              已认证
            </el-checkbox>
            <el-checkbox
              v-model="profileForm.recommended"
              :disabled="profileForm.deprecated"
              @change="changeRecommended"
            >
              推荐使用
            </el-checkbox>
            <el-checkbox v-model="profileForm.deprecated" @change="changeDeprecated">
              已废弃
            </el-checkbox>
          </div>
        </el-form-item>
        <!-- 废弃数据集前展示下游引用，帮助维护人评估影响范围。 -->
        <el-form-item v-if="profileIsDataset && profileForm.deprecated" label="影响范围">
          <div v-loading="profileImpactLoading" class="profile-impact">
            <div v-if="!profileImpact.length" class="profile-impact-empty">暂无下游影响</div>
            <template v-else>
              <div class="profile-impact-title">该数据集仍被以下资源使用</div>
              <div
                v-for="item in visibleProfileImpact"
                :key="`${item.assetType}-${item.assetId}`"
                class="profile-impact-item"
              >
                <span class="relation-type">{{ item.assetTypeLabel }}</span>
                <span class="relation-name">{{ item.name }}</span>
                <span class="relation-reason">{{ item.relation }}</span>
              </div>
              <div v-if="remainingProfileImpactCount" class="profile-impact-more">
                另有 {{ remainingProfileImpactCount }} 项
              </div>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="标签">
          <el-input
            v-model.trim="profileForm.tags"
            maxlength="200"
            placeholder="多个标签用逗号分隔"
          />
        </el-form-item>
        <el-form-item label="资产说明">
          <el-input
            v-model.trim="profileForm.description"
            type="textarea"
            maxlength="500"
            show-word-limit
            :rows="4"
            placeholder="填写口径、适用场景或维护要求"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
.asset-manage {
  width: 100%;
  height: 100%;
  background: #fff;
}

.asset-content {
  height: 100%;
  overflow: auto;
  background: #fff;
}

.asset-table-info {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 20px 24px 16px;
  background: #fff;
}

.search-operate {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.asset-search {
  width: 320px;
}

.asset-filter {
  width: 150px;
}

.asset-owner-filter {
  width: 180px;
}

.info-table {
  flex: 1;
  min-height: 0;
}

.asset-grid-table {
  :deep(.ed-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.ed-table) {
    --ed-table-header-bg-color: #ffffff;
    --ed-table-tr-bg-color: #ffffff;
    color: #334155;
    font-family: var(--crest-font-sans);
    font-size: 13.5px;
  }

  :deep(.ed-table th.ed-table__cell) {
    padding: 10px 0;
    color: #64748b;
    font-size: 12px;
    font-weight: 500;
    letter-spacing: 0;
    background: #ffffff;
    border-bottom: 1px solid #edf1f5;
  }

  :deep(.ed-table td.ed-table__cell) {
    padding: 12px 0;
    border-bottom: 1px solid #f1f5f9;
  }

  :deep(.ed-table__row:hover > td.ed-table__cell) {
    background: #fafbfc;
  }

  :deep(.pagination-cont) {
    margin-top: 16px;
  }
}

.asset-name-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.asset-name-main {
  display: flex;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.asset-name {
  overflow: hidden;
  color: #0f172a;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-id {
  flex: none;
  color: #94a3b8;
  font-family: var(--crest-font-mono);
  font-size: 12px;
}

.badge-row {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 6px;
}

.governance-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 5px;
}

.badge-certified {
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.badge-recommended {
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.badge-deprecated {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.muted-main {
  color: #334155;
}

.muted-sub {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 12px;
}

.lineage-count {
  display: block;
  color: #475569;
  line-height: 20px;
}

.asset-detail {
  min-height: 100%;
  padding: 22px 24px;
}

.drawer-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.drawer-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
}

.drawer-sub {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.drawer-badges {
  margin-top: 12px;
}

.detail-tabs {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf1f5;
}

.detail-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  color: #64748b;
  font-size: 13px;
  line-height: 28px;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;

  &:hover {
    color: #2563eb;
    border-color: #bfdbfe;
  }

  &.active {
    color: #2563eb;
    font-weight: 600;
    background: #eff6ff;
    border-color: #93c5fd;
  }
}

.detail-tab-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  background: #f1f5f9;
  border-radius: 9px;
}

.detail-tab.active .detail-tab-count {
  color: #1d4ed8;
  background: #dbeafe;
}

.detail-panel {
  margin-top: 12px;
}

.detail-table-wrap {
  overflow-x: auto;
  border-top: 1px solid #edf1f5;
}

.detail-table {
  width: 100%;
  min-width: 500px;
  color: #334155;
  font-size: 13px;
  table-layout: fixed;
  border-collapse: collapse;

  th,
  td {
    padding: 11px 10px;
    text-align: left;
    vertical-align: top;
    border-bottom: 1px solid #f1f5f9;
  }

  th {
    color: #64748b;
    font-size: 12px;
    font-weight: 500;
    background: #fff;
  }

  tbody tr:hover td {
    background: #fafbfc;
  }
}

.detail-field-table {
  min-width: 0;

  th {
    width: 96px;
    background: #fafbfc;
  }
}

.w-direction {
  width: 58px;
}

.w-type {
  width: 76px;
}

.w-time {
  width: 138px;
}

.strong-cell {
  overflow: hidden;
  color: #0f172a;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pre-line {
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}

.relation-item {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 4px 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.relation-type {
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

.relation-name {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-reason {
  grid-column: 2;
  color: #64748b;
  font-size: 12px;
}

.relation-empty {
  padding: 18px 0;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}

.table-empty {
  padding: 24px 0;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
  border-bottom: 1px solid #f1f5f9;
}

.impact-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 12px;
}

.impact-stat {
  padding: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;

  span,
  strong {
    display: block;
  }

  span {
    color: #64748b;
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    color: #0f172a;
    font-size: 20px;
    line-height: 24px;
  }
}

.profile-form {
  padding-right: 8px;
}

.full-width {
  width: 100%;
}

.status-checks {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
}

.profile-impact {
  width: 100%;
  min-height: 36px;
}

.profile-impact-title {
  margin-bottom: 6px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.profile-impact-item {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 2px 8px;
  padding: 7px 0;
  border-bottom: 1px solid #f1f5f9;
}

.profile-impact-more,
.profile-impact-empty {
  color: #94a3b8;
  font-size: 13px;
}

.profile-impact-more {
  padding-top: 8px;
}

@media (max-width: 900px) {
  .asset-table-info {
    height: auto;
    min-height: calc(100vh - 80px);
    padding: 16px;
  }

  .asset-search,
  .asset-filter {
    width: 100%;
  }

  .asset-owner-filter {
    width: 100%;
  }

  .search-operate {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
