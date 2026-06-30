<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import request from '@/config/axios'
import { ElMessage, ElMessageBox } from 'element-plus-secondary'
import dayjs from 'dayjs'
import PlatformOrgTree from '../common/PlatformOrgTree.vue'

// 用户列表加载状态
const loading = ref(false)
// 用户搜索关键字，匹配账号、姓名或邮箱
const keyword = ref('')
// 当前页用户列表数据
const tableData = ref<any[]>([])
// 表格中已勾选的用户行
const selectedRows = ref<any[]>([])
// 用户列表分页状态
const pager = reactive({ currentPage: 1, pageSize: 15, total: 0 })
// 用户新建或编辑弹窗显示状态
const dialogVisible = ref(false)
// 标记当前用户弹窗是否处于编辑模式
const isEdit = ref(false)
// 当前组织下可分配的角色选项
const roleOptions = ref<any[]>([])
// 用户表单可选的组织树数据
const orgOptions = ref<any[]>([])
// 左侧组织树当前选中的组织 id
const selectedOrgId = ref<any>(null)
// 当前组织名称，展示在列表标题区域
const selectedOrgName = ref('全部组织')
// 用户表单模型，覆盖本地账号和单点登录账号的公共字段
const form = reactive<any>({
  id: null,
  oid: null,
  account: '',
  name: '',
  email: '',
  phone: '',
  password: '',
  enable: true,
  roleIds: [2],
  authType: 'LOCAL'
})

// 左侧组织树组件引用，用于刷新组织树
const orgTreeRef = ref()
// 组织新建或编辑弹窗显示状态
const orgDialogVisible = ref(false)
// 标记组织弹窗是否处于编辑模式
const orgIsEdit = ref(false)
// 组织表单模型
const orgForm = reactive<any>({ id: null, pid: 0, name: '', parentName: '根目录' })

// 判断用户是否来自单点登录
const isSsoUser = row => String(row?.authType || '').toUpperCase() === 'SSO'
// 将认证类型转换为表格和弹窗中的展示文案
const authTypeLabel = row => (isSsoUser(row) ? '单点登录' : '本地账号')
// 将认证类型转换为标签样式
const authTypeTag = row => (isSsoUser(row) ? 'success' : 'info')
// 判断认证类型是否允许维护本地密码
const localAuthType = authType => String(authType || 'LOCAL').toUpperCase() !== 'SSO'

// 按当前组织、关键字和分页条件加载用户列表
const loadTable = async () => {
  loading.value = true
  try {
    const res = await request.post({
      url: `/user/page/${pager.currentPage}/${pager.pageSize}`,
      data: { keyword: keyword.value, oid: selectedOrgId.value, timeDesc: true }
    })
    tableData.value = res.data?.records || []
    pager.total = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

// 加载当前组织下可分配的角色列表
const loadRoles = async () => {
  const res = selectedOrgId.value
    ? await request.get({ url: `/role/organization/${selectedOrgId.value}` })
    : await request.post({ url: '/role/by-current-org', data: {} })
  roleOptions.value = res.data || []
}

// 加载组织树选项，供用户表单选择所属组织
const loadOrgOptions = async () => {
  const res = await request.post({ url: '/org/page/tree', data: {} })
  orgOptions.value = res.data || []
}

// 从组织树中查找第一个可用组织 id，作为新建用户的默认组织
const firstOrgId = (nodes: any[]): any => {
  for (const node of nodes || []) {
    if (node?.id !== undefined && node?.id !== null && node?.id !== '') {
      return node.id
    }
    const childId = firstOrgId(node?.children || [])
    if (childId !== undefined && childId !== null && childId !== '') {
      return childId
    }
  }
  return null
}

// 左侧组织切换后刷新列表和角色选项
const onOrgChange = async node => {
  selectedOrgName.value = node?.name || '全部组织'
  pager.currentPage = 1
  await Promise.all([loadTable(), loadRoles()])
}

// 用户表单切换所属组织后刷新角色选项
const onFormOrgChange = async () => {
  const res = form.oid
    ? await request.get({ url: `/role/organization/${form.oid}` })
    : await request.post({ url: '/role/by-current-org', data: {} })
  roleOptions.value = res.data || []
}

// 重置用户表单到新建状态
const resetForm = () => {
  Object.assign(form, {
    id: null,
    oid: selectedOrgId.value || firstOrgId(orgOptions.value),
    account: '',
    name: '',
    email: '',
    phone: '',
    password: '',
    enable: true,
    roleIds: [2],
    authType: 'LOCAL'
  })
}

// 打开新建用户弹窗，并初始化默认组织和角色选项
const openCreate = async () => {
  if (!orgOptions.value.length) {
    await loadOrgOptions()
  }
  resetForm()
  await onFormOrgChange()
  isEdit.value = false
  dialogVisible.value = true
}

// 打开编辑用户弹窗，并加载用户详情与角色回显
const openEdit = async row => {
  const res = await request.get({ url: `/user/detail/${row.id}` })
  Object.assign(form, res.data || row)
  form.oid = res.data?.oid || row.oid || selectedOrgId.value
  form.roleIds = (res.data?.roleIds || row.roleItems?.map(role => String(role.id)) || ['2']).map(
    Number
  )
  await onFormOrgChange()
  isEdit.value = true
  dialogVisible.value = true
}

// 保存用户信息，并在新建本地账号且使用默认密码时提示默认密码
const save = async () => {
  if (!form.account?.trim() || !form.name?.trim()) {
    ElMessage.warning('账号和姓名不能为空')
    return
  }
  if (!form.oid) {
    ElMessage.warning('请选择所属组织')
    return
  }
  const isCreate = !isEdit.value
  const account = form.account
  const authType = form.authType
  const usesDefaultPassword =
    isCreate && localAuthType(authType) && !String(form.password || '').trim()
  try {
    const method = isEdit.value ? request.put : request.post
    await method({ url: '/user', data: form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadTable()
    if (usesDefaultPassword) {
      await showDefaultPasswordNotice('新建用户成功', `用户「${account}」已创建`)
    }
  } catch (e: any) {
    // 请求拦截器已统一提示错误信息
  }
}

// 启用或停用用户账号
const toggleEnable = async row => {
  try {
    await request.post({ url: '/user/enable', data: { id: row.id, enable: !row.enable } })
    ElMessage.success(!row.enable ? '用户已启用' : '用户已停用')
    await loadTable()
  } catch (e: any) {
    // 请求拦截器已统一提示错误信息
  }
}

// 重置本地用户密码，并在后端返回新密码时弹窗提示
const resetPwd = async row => {
  try {
    await ElMessageBox.confirm(`确认重置「${row.name}」的本地密码？`, '重置密码', {
      confirmButtonText: '重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res: any = await request.post({ url: `/user/reset-password/${row.id}` })
    const newPwd = String(res?.data || '')
    if (!newPwd) {
      ElMessage.success('密码重置完成')
      return
    }
    await showPasswordNotice('密码已重置', `用户「${row.name}」的新密码：${newPwd}`, newPwd)
  } catch (e: any) {
    // 用户取消确认框或请求拦截器已处理提示
  }
}

// 查询系统配置的本地账号默认密码
const queryDefaultPassword = async () => {
  const res = await request.get({ url: '/user/default-password' })
  return String(res.data || '')
}

// 将密码写入剪贴板并提示用户
const copyPassword = async (password: string) => {
  await navigator.clipboard.writeText(password)
  ElMessage.success('密码已复制')
}

// 展示密码提示框，用户确认后复制密码
const showPasswordNotice = async (title: string, message: string, password: string) => {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '复制密码',
      cancelButtonText: '关闭',
      type: 'success'
    })
    await copyPassword(password)
  } catch {
    // 关闭提示框不影响已完成的账号操作。
  }
}

// 新建用户后查询并展示默认密码
const showDefaultPasswordNotice = async (title: string, message: string) => {
  let password = ''
  try {
    password = await queryDefaultPassword()
  } catch {
    ElMessage.warning('默认密码未配置，请先在系统参数的账号安全中设置')
    return
  }
  if (!password) {
    ElMessage.warning('默认密码未配置，请先在系统参数的账号安全中设置')
    return
  }
  await showPasswordNotice(title, `${message}，默认密码：${password}`, password)
}

// 删除单个用户
const remove = async row => {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除用户', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete({ url: `/user/${row.id}` })
    ElMessage.success('删除成功')
    await loadTable()
  } catch (e: any) {
    // 用户取消确认框或请求拦截器已处理提示
  }
}

// 删除表格中已勾选的多个用户
const batchRemove = async () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择用户')
    return
  }
  await ElMessageBox.confirm(`确认删除已选择的 ${selectedRows.value.length} 个用户？`, '批量删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.delete({ url: '/user/batch', data: selectedRows.value.map(row => row.id) })
  ElMessage.success('删除成功')
  selectedRows.value = []
  await loadTable()
}

// 下载用户批量导入模板
const downloadTemplate = async () => {
  const res: any = await request.post({ url: '/user/excel-template', responseType: 'blob' })
  const blob = new Blob([res.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'user-import-template.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

// 上传用户导入文件并刷新列表
const importUsers = async options => {
  const data = new FormData()
  data.append('file', options.file)
  const res = await request.post({
    url: '/user/batch-import',
    headersType: 'multipart/form-data',
    data
  })
  ElMessage.success(
    `导入完成：成功 ${res.data?.successCount || 0}，失败 ${res.data?.errorCount || 0}`
  )
  await loadTable()
}

// 打开新建组织弹窗，并带入父级组织信息
const openOrgCreate = (parent: any) => {
  Object.assign(orgForm, {
    id: null,
    pid: parent?.id || 0,
    name: '',
    parentName: parent?.name || '根目录'
  })
  orgIsEdit.value = false
  orgDialogVisible.value = true
}

// 打开编辑组织弹窗
const openOrgEdit = (node: any) => {
  Object.assign(orgForm, {
    id: node.id,
    pid: node.pid || 0,
    name: node.name,
    parentName: node.name
  })
  orgIsEdit.value = true
  orgDialogVisible.value = true
}

// 保存组织信息并刷新组织树和组织选项
const saveOrg = async () => {
  if (!orgForm.name?.trim()) {
    ElMessage.warning('请输入组织名称')
    return
  }
  const method = orgIsEdit.value ? request.put : request.post
  await method({
    url: '/org/page',
    data: { id: orgForm.id, pid: orgForm.pid, name: orgForm.name }
  })
  ElMessage.success('保存成功')
  orgDialogVisible.value = false
  await Promise.all([orgTreeRef.value?.loadTree?.(), loadOrgOptions()])
}

// 删除组织，并在删除当前选中组织时重置筛选范围
const removeOrg = async (node: any) => {
  await ElMessageBox.confirm(`删除组织「${node.name}」后不可恢复，确认删除？`, '删除组织', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.delete({ url: `/org/page/${node.id}` })
  ElMessage.success('删除成功')
  if (String(node.id) === String(selectedOrgId.value)) {
    selectedOrgId.value = null
    selectedOrgName.value = '全部组织'
  }
  await Promise.all([orgTreeRef.value?.loadTree?.(), loadOrgOptions(), loadTable()])
}

// 页面初始化时加载用户、角色和组织数据
onMounted(async () => {
  await Promise.all([loadTable(), loadRoles(), loadOrgOptions()])
})
</script>

<template>
  <div class="manage-page">
    <p class="router-title">用户管理</p>
    <div class="manage-layout">
      <PlatformOrgTree
        ref="orgTreeRef"
        v-model="selectedOrgId"
        title="组织架构"
        selectable-all
        manageable
        collapsible
        @change="onOrgChange"
        @create="openOrgCreate"
        @edit="openOrgEdit"
        @delete="removeOrg"
      />
      <section class="content-card">
        <div class="card-head">
          <div class="head-main">
            <div class="head-title">{{ selectedOrgName }}</div>
            <div class="head-desc">查看并维护所选组织下的用户、角色与账号状态</div>
          </div>
          <div class="head-actions">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索账号、姓名或邮箱"
              @change="loadTable"
            />
            <el-button type="primary" @click="loadTable">查询</el-button>
            <el-button type="primary" @click="openCreate">新建用户</el-button>
            <el-button :disabled="!selectedRows.length" @click="batchRemove">批量删除</el-button>
            <el-button @click="downloadTemplate">下载模板</el-button>
            <el-upload :show-file-list="false" :http-request="importUsers" accept=".xlsx,.xls,.csv">
              <el-button>批量导入</el-button>
            </el-upload>
          </div>
        </div>
        <el-table
          v-loading="loading"
          class="manage-table crest-data-table"
          :data="tableData"
          max-height="calc(100vh - 320px)"
          @selection-change="selectedRows = $event"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="account" label="账号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="account-cell">
                <span class="account-name">{{ row.account }}</span>
                <el-tag v-if="isSsoUser(row)" size="small" type="success" disable-transitions
                  >SSO</el-tag
                >
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="姓名" min-width="110" show-overflow-tooltip />
          <el-table-column prop="orgName" label="所属组织" min-width="120" show-overflow-tooltip />
          <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
          <el-table-column label="角色" min-width="160">
            <template #default="{ row }">
              <div class="role-tags">
                <el-tag v-for="role in row.roleItems || []" :key="role.id" size="small" type="info">
                  {{ role.name }}
                </el-tag>
                <span v-if="!(row.roleItems || []).length" class="muted">-</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span class="status-dot" :class="{ on: row.enable }"></span>
              <span>{{ row.enable ? '启用' : '停用' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近登录" width="160">
            <template #default="{ row }">
              <span class="muted">{{
                row.lastLoginTime
                  ? dayjs(Number(row.lastLoginTime)).format('YYYY-MM-DD HH:mm')
                  : '从未登录'
              }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button text type="primary" @click="toggleEnable(row)">{{
                row.enable ? '停用' : '启用'
              }}</el-button>
              <el-dropdown trigger="click">
                <el-button text type="primary" class="more-btn">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="!isSsoUser(row)" @click="resetPwd(row)"
                      >重置密码</el-dropdown-item
                    >
                    <el-dropdown-item
                      v-if="String(row.id) !== '1'"
                      class="danger-item"
                      @click="remove(row)"
                      >删除</el-dropdown-item
                    >
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新建用户'" width="560px">
      <el-form label-position="top">
        <el-form-item label="所属组织" required>
          <el-tree-select
            v-model="form.oid"
            :data="orgOptions"
            node-key="id"
            check-strictly
            filterable
            default-expand-all
            :props="{ children: 'children', label: 'name' }"
            placeholder="请选择所属组织"
            @change="onFormOrgChange"
          />
        </el-form-item>
        <el-form-item label="账号" required>
          <el-input
            v-model.trim="form.account"
            :disabled="isEdit && (String(form.id) === '1' || isSsoUser(form))"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item v-if="isEdit" label="认证来源">
          <el-tag :type="authTypeTag(form)">{{ authTypeLabel(form) }}</el-tag>
        </el-form-item>
        <el-form-item v-if="localAuthType(form.authType)" label="密码" :required="!isEdit">
          <el-input
            v-model.trim="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '不填写则保持原密码' : '留空则使用系统默认密码'"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model.trim="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model.trim="form.email" maxlength="120" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model.trim="form.phone" maxlength="32" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleIds"
            multiple
            class="full-width"
            :disabled="String(form.id) === '1'"
            placeholder="请选择角色"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="Number(role.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enable" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="orgDialogVisible"
      :title="orgIsEdit ? '重命名组织' : '新建组织'"
      width="480px"
    >
      <el-form label-position="top">
        <el-form-item v-if="!orgIsEdit" label="上级组织">
          <el-input :model-value="orgForm.parentName" disabled />
        </el-form-item>
        <el-form-item label="组织名称" required>
          <el-input v-model.trim="orgForm.name" maxlength="64" placeholder="请输入组织名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveOrg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
@import '../common/manage.less';
.head-actions {
  flex: 1 1 620px;
  min-width: min(100%, 620px);
  .ed-input {
    flex: 1 1 240px;
    max-width: 320px;
  }
}
.manage-table {
  padding: 8px 8px 0;
}
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.account-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  .account-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  vertical-align: middle;
  background: #cbd5e1;
  border-radius: 50%;
  &.on {
    background: #22c55e;
  }
}
.muted {
  color: #94a3b8;
}
.more-btn {
  padding-left: 4px;
}
:deep(.danger-item) {
  color: #dc2626;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}
</style>
