<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import request from '@/config/axios'
import dayjs from 'dayjs'
import useClipboard from 'vue-clipboard3'
import { ElMessage, ElMessageBox } from 'element-plus-secondary'

const { toClipboard } = useClipboard()
// 分享列表加载状态
const loading = ref(false)
// 分享列表搜索关键字
const keyword = ref('')
// 分享资源类型筛选值
const type = ref('')
// 分享列表数据
const tableData = ref<any[]>([])
// 分享列表分页状态
const pager = reactive({ currentPage: 1, pageSize: 15, total: 0 })
// 分享详情弹窗显隐状态
const dialogVisible = ref(false)
// 当前正在编辑的分享记录
const current = reactive<any>({})

const typeMap = {
  dashboard: '仪表盘',
  panel: '仪表盘',
  dataV: '数据大屏',
  screen: '数据大屏'
}

// 当前分享记录的访问链接
const shareLink = computed(() => {
  if (!current.uuid) return ''
  return `${window.location.origin}${window.location.pathname}#/link/${current.uuid}`
})

// 格式化分享时间或有效期
const formatTime = val => {
  if (!val) return '长期有效'
  return dayjs(Number(val)).format('YYYY-MM-DD HH:mm:ss')
}

// 加载分享列表
const loadTable = async () => {
  loading.value = true
  try {
    const queryType = type.value === 'dashboard' ? 'panel' : type.value === 'dataV' ? 'screen' : ''
    const res = await request.post({
      url: `/share/page/${pager.currentPage}/${pager.pageSize}`,
      data: { keyword: keyword.value, type: queryType, asc: false }
    })
    tableData.value = res.data?.records || []
    pager.total = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

// 打开分享详情设置弹窗
const openDetail = async row => {
  const res = await request.get({ url: `/share/detail/${row.resourceId}` })
  Object.assign(current, row, res.data || {})
  dialogVisible.value = true
}

// 保存分享有效期
const saveExp = async () => {
  await request.post({
    url: '/share/expiration',
    data: { resourceId: current.resourceId, exp: current.exp || 0 }
  })
  ElMessage.success('有效期已更新')
  await loadTable()
}

// 保存分享访问密码
const savePwd = async () => {
  await request.post({
    url: '/share/password',
    data: { resourceId: current.resourceId, pwd: current.pwd || '', autoPwd: false }
  })
  ElMessage.success('访问密码已更新')
  await loadTable()
}

// 关闭指定资源的公开分享
const disableShare = async row => {
  await ElMessageBox.confirm(`确认关闭「${row.name}」的公开分享？`, '关闭分享', {
    confirmButtonText: '关闭',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.post({ url: `/share/switcher/${row.resourceId}` })
  ElMessage.success('分享已关闭')
  await loadTable()
}

// 复制当前分享链接和密码
const copyLink = async () => {
  await toClipboard(current.pwd ? `${shareLink.value},${current.pwd}` : shareLink.value)
  ElMessage.success('链接已复制')
}

onMounted(loadTable)
</script>

<template>
  <div class="share-manage">
    <p class="router-title">分享管理</p>
    <div class="table-wrap">
      <div class="toolbar">
        <el-select v-model="type" clearable placeholder="全部类型" @change="loadTable">
          <el-option label="仪表盘" value="dashboard" />
          <el-option label="数据大屏" value="dataV" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="搜索名称" @change="loadTable" />
        <el-button type="primary" @click="loadTable">查询</el-button>
      </div>
      <el-table
        v-loading="loading"
        class="crest-data-table"
        :data="tableData"
        max-height="calc(100vh - 300px)"
      >
        <el-table-column prop="name" label="资源名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ typeMap[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column prop="creator" label="创建人" width="130" />
        <el-table-column label="分享时间" width="180">
          <template #default="{ row }">{{ formatTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="180">
          <template #default="{ row }">{{ formatTime(row.exp) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.extFlag1 ? 'success' : 'info'">{{
              row.extFlag1 ? '可访问' : '资源异常'
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetail(row)">设置</el-button>
            <el-button text type="danger" @click="disableShare(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pager.currentPage"
          v-model:page-size="pager.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="pager.total"
          @size-change="loadTable"
          @current-change="loadTable"
        />
      </div>
    </div>
    <el-dialog v-model="dialogVisible" title="分享设置" width="560px">
      <el-form label-position="top">
        <el-form-item label="公开链接">
          <el-input :model-value="shareLink" readonly>
            <template #append>
              <el-button @click="copyLink">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="current.exp"
            type="datetime"
            value-format="x"
            clearable
            placeholder="不设置则长期有效"
          />
          <el-button class="inline-btn" @click="saveExp">保存有效期</el-button>
        </el-form-item>
        <el-form-item label="访问密码">
          <el-input v-model="current.pwd" clearable placeholder="留空表示不启用密码" />
          <el-button class="inline-btn" @click="savePwd">保存密码</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
.share-manage {
  min-height: 100%;
}
.router-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}
.toolbar {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 12px 12px 0 0;
  .ed-select {
    width: 140px;
  }
  .ed-input {
    width: 260px;
  }
}
.table-wrap {
  height: auto;
  min-height: 0;
  margin-top: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 16px;
  background: #fff;
}
.inline-btn {
  margin-left: 12px;
}
</style>
