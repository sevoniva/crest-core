<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import request from '@/config/axios'
import { ElMessage } from 'element-plus-secondary'
import { Icon } from '@/components/icon-custom'
import iconRight from '@/assets/svg/icon_right_outlined.svg'

// 页面级加载态，覆盖角色、菜单树、资源树和权限回显
const loading = ref(false)
// 保存按钮加载态，菜单权限和资源权限共用
const saving = ref(false)
// 当前组织下可配置的角色列表
const roles = ref<any[]>([])
// 菜单权限树
const menuTree = ref<any[]>([])
// 业务资源权限树，随 resourceType 切换
const resourceTree = ref<any[]>([])
// 当前正在配置的角色编号
const activeRoleId = ref<any>()
// 当前资源权限类型，默认展示数据大屏资源
const resourceType = ref('screen')
// 当前权限页签，menu 表示菜单权限，resource 表示业务资源权限
const activeTab = ref('menu')
// 菜单权限树实例引用，用于回显、过滤和批量操作
const menuTreeRef = ref()
// 资源权限树实例引用，用于回显、过滤和批量操作
const resourceTreeRef = ref()
// 菜单树搜索关键字
const menuKeyword = ref('')
// 资源树搜索关键字
const resourceKeyword = ref('')
// 菜单树当前是否展开
const menuExpanded = ref(true)
// 资源树当前是否展开
const resourceExpanded = ref(true)
// 菜单路由名到中文显示名的映射，避免权限树直接暴露内部路由标识
const menuLabelMap: Record<string, string> = {
  workbranch: '工作台',
  panel: '仪表盘',
  screen: '数据大屏',
  data: '数据准备',
  dataset: '数据集',
  datasource: '数据源',
  'data-asset': '数据资产',
  'cache-task': '缓存任务',
  'sys-setting': '系统设置',
  parameter: '系统参数',
  'share-management': '分享管理',
  'site-setting': '站点设置',
  'user-management': '用户管理',
  'single-sign-on': '单点登录',
  'audit-log': '审计日志',
  'org-management': '组织管理',
  'role-management': '角色管理',
  'permission-management': '权限管理',
  association: '数据血缘',
  msg: '消息中心'
}
// 权限树节点显示名称，优先使用本地映射
const nodeLabel = (node: any) => menuLabelMap[node?.name] || node?.name || '-'

// 当前角色名称，标题区使用
const roleName = computed(
  () => roles.value.find(role => String(role.id) === String(activeRoleId.value))?.name || ''
)
// 当前角色完整对象，用于展示只读或系统角色提示
const activeRole = computed(() =>
  roles.value.find(role => String(role.id) === String(activeRoleId.value))
)
// Element Plus 树字段映射，label 使用本地化后的节点名称
const treeProps = {
  children: 'children',
  label: (node: any) => nodeLabel(node)
}

// 加载当前组织下角色，并在当前角色失效时自动选中首个角色
const loadRoles = async () => {
  const res = await request.post({ url: '/role/by-current-org', data: {} })
  roles.value = res.data || []
  if (!roles.value.some(role => String(role.id) === String(activeRoleId.value))) {
    activeRoleId.value = roles.value[0]?.id
  }
}

// 加载系统菜单资源树
const loadMenuTree = async () => {
  const res = await request.get({ url: '/auth/menu-resources' })
  menuTree.value = res.data || []
}

// 加载当前资源类型的业务资源树
const loadResourceTree = async () => {
  const res = await request.get({ url: `/auth/business-resources/${resourceType.value}` })
  resourceTree.value = res.data || []
}

// 加载当前角色的菜单和业务资源授权，并回显到两棵树
const loadPermissions = async () => {
  if (!activeRoleId.value) return
  loading.value = true
  try {
    const [menuRes, resourceRes]: any[] = await Promise.all([
      request.post({ url: '/auth/menu-permissions', data: { id: activeRoleId.value } }),
      request.post({
        url: '/auth/business-permissions',
        data: { id: activeRoleId.value, type: 2, flag: resourceType.value }
      })
    ])
    const checkedMenus = (menuRes.data?.permissions || []).map(item => item.id)
    const checkedResources = (resourceRes.data?.permissions || []).map(item => item.id)
    setTimeout(() => {
      menuTreeRef.value?.setCheckedKeys(checkedMenus)
      resourceTreeRef.value?.setCheckedKeys(checkedResources)
    })
  } finally {
    loading.value = false
  }
}

// 保存当前角色的菜单权限，勾选节点统一写入管理权限权重
const saveMenu = async () => {
  saving.value = true
  try {
    const ids = menuTreeRef.value?.getCheckedKeys(false) || []
    await request.put({
      url: '/auth/menu-permissions',
      data: { id: activeRoleId.value, permissions: ids.map(id => ({ id, weight: 7 })) }
    })
    ElMessage.success('菜单权限已保存')
  } finally {
    saving.value = false
  }
}

// 保存当前角色在当前资源类型下的业务资源权限
const saveResource = async () => {
  saving.value = true
  try {
    const ids = resourceTreeRef.value?.getCheckedKeys(false) || []
    await request.put({
      url: '/auth/business-permissions',
      data: {
        id: activeRoleId.value,
        type: 2,
        flag: resourceType.value,
        permissions: ids.map(id => ({ id, weight: 7 }))
      }
    })
    ElMessage.success('资源权限已保存')
  } finally {
    saving.value = false
  }
}

// 切换业务资源类型后重新加载资源树和权限回显
const changeResourceType = async () => {
  await loadResourceTree()
  await loadPermissions()
}

// 选择角色后重新加载该角色权限
const selectRole = async (role: any) => {
  activeRoleId.value = role.id
  await loadPermissions()
}

// 收集树中所有节点编号，用于全选操作
const allKeys = (nodes: any[], acc: any[] = []): any[] => {
  for (const node of nodes || []) {
    acc.push(node.id)
    allKeys(node.children || [], acc)
  }
  return acc
}
// 勾选指定权限树的全部节点
const checkAll = (which: 'menu' | 'resource') => {
  const treeRef = which === 'menu' ? menuTreeRef : resourceTreeRef
  const data = which === 'menu' ? menuTree.value : resourceTree.value
  treeRef.value?.setCheckedKeys(allKeys(data))
}
// 清空指定权限树的全部勾选
const clearAll = (which: 'menu' | 'resource') => {
  const treeRef = which === 'menu' ? menuTreeRef : resourceTreeRef
  treeRef.value?.setCheckedKeys([])
}
// 批量切换指定权限树的展开状态
const toggleExpand = (which: 'menu' | 'resource') => {
  const treeRef = which === 'menu' ? menuTreeRef : resourceTreeRef
  const flag =
    which === 'menu'
      ? (menuExpanded.value = !menuExpanded.value)
      : (resourceExpanded.value = !resourceExpanded.value)
  const nodesMap = treeRef.value?.store?.nodesMap || {}
  Object.keys(nodesMap).forEach(key => (nodesMap[key].expanded = flag))
}
// 权限树过滤函数，按展示名称匹配
const filterNode = (value: string, data: any) => !value || nodeLabel(data).includes(value)
// 菜单搜索关键字变化时刷新树过滤
watch(menuKeyword, value => menuTreeRef.value?.filter(value))
// 资源搜索关键字变化时刷新树过滤
watch(resourceKeyword, value => resourceTreeRef.value?.filter(value))

// 页面初始化时并行加载基础数据，再加载当前角色权限
const init = async () => {
  await Promise.all([loadRoles(), loadMenuTree(), loadResourceTree()])
  await loadPermissions()
}

// 页面挂载后启动权限管理数据初始化
onMounted(init)
</script>

<template>
  <div class="manage-page">
    <p class="router-title">权限管理</p>
    <div class="permission-layout" v-loading="loading">
      <aside class="role-panel content-card">
        <div class="role-head">
          <div class="head-title">角色</div>
          <div class="head-desc">选择角色后配置其权限</div>
        </div>
        <el-scrollbar class="role-scroll">
          <button
            v-for="role in roles"
            :key="role.id"
            class="role-item"
            :class="{ active: String(activeRoleId) === String(role.id) }"
            @click="selectRole(role)"
          >
            <span class="role-meta">
              <strong>{{ role.name }}</strong>
              <small>{{
                role.readonly ? '只读角色' : role.root ? '系统角色' : '自定义角色'
              }}</small>
            </span>
            <Icon class="role-arrow" name="icon_right_outlined"
              ><iconRight class="svg-icon"
            /></Icon>
          </button>
          <div v-if="!roles.length" class="role-empty">暂无角色</div>
        </el-scrollbar>
      </aside>

      <section class="permission-panel content-card">
        <div class="card-head">
          <div class="head-main">
            <div class="head-title">{{ roleName || '请选择角色' }}</div>
            <div class="head-desc">
              {{
                activeRole?.readonly
                  ? '只读角色仅授予查看类菜单和资源'
                  : '勾选该角色可访问的菜单和业务资源'
              }}
            </div>
          </div>
          <el-segmented
            v-model="activeTab"
            :options="[
              { label: '菜单权限', value: 'menu' },
              { label: '资源权限', value: 'resource' }
            ]"
          />
        </div>

        <div v-show="activeTab === 'menu'" class="permission-body">
          <div class="tree-toolbar">
            <el-input v-model="menuKeyword" clearable class="tree-search" placeholder="搜索菜单" />
            <div class="tree-tools">
              <el-button text @click="toggleExpand('menu')">{{
                menuExpanded ? '折叠' : '展开'
              }}</el-button>
              <el-button text @click="checkAll('menu')">全选</el-button>
              <el-button text @click="clearAll('menu')">清空</el-button>
              <el-button type="primary" :loading="saving" @click="saveMenu">保存</el-button>
            </div>
          </div>
          <el-scrollbar class="tree-scroll">
            <el-tree
              ref="menuTreeRef"
              class="permission-tree"
              :data="menuTree"
              show-checkbox
              node-key="id"
              default-expand-all
              :filter-node-method="filterNode"
              :props="treeProps"
            />
          </el-scrollbar>
        </div>

        <div v-show="activeTab === 'resource'" class="permission-body">
          <div class="tree-toolbar">
            <el-input
              v-model="resourceKeyword"
              clearable
              class="tree-search"
              placeholder="搜索资源"
            />
            <div class="tree-tools">
              <el-select
                v-model="resourceType"
                class="resource-select"
                @change="changeResourceType"
              >
                <el-option label="仪表盘" value="panel" />
                <el-option label="数据大屏" value="screen" />
                <el-option label="数据集" value="dataset" />
                <el-option label="数据源" value="datasource" />
              </el-select>
              <el-button text @click="toggleExpand('resource')">{{
                resourceExpanded ? '折叠' : '展开'
              }}</el-button>
              <el-button text @click="checkAll('resource')">全选</el-button>
              <el-button text @click="clearAll('resource')">清空</el-button>
              <el-button type="primary" :loading="saving" @click="saveResource">保存</el-button>
            </div>
          </div>
          <el-scrollbar class="tree-scroll">
            <el-tree
              ref="resourceTreeRef"
              class="permission-tree"
              :data="resourceTree"
              show-checkbox
              node-key="id"
              default-expand-all
              :filter-node-method="filterNode"
              :props="treeProps"
            />
          </el-scrollbar>
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="less" scoped>
@import '../common/manage.less';
.permission-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  height: calc(100vh - 168px);
}
.role-panel {
  padding: 16px;
}
.role-head {
  margin-bottom: 8px;
}
.head-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}
.head-desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}
.role-scroll {
  flex: 1;
  min-height: 0;
}
.role-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding: 10px 12px;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #334155;
  text-align: left;
  transition: all 0.15s;
  .role-meta {
    min-width: 0;
  }
  strong,
  small {
    display: block;
  }
  strong {
    overflow: hidden;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  small {
    margin-top: 2px;
    color: #64748b;
    font-size: 12px;
  }
  .role-arrow {
    flex: 0 0 auto;
    margin-left: 8px;
    color: #cbd5e1;
    .svg-icon {
      width: 14px;
      height: 14px;
    }
  }
  &:hover {
    border-color: #bfdbfe;
  }
  &.active {
    color: #0f172a;
    background: #eff6ff;
    border-color: #3b82f6;
    .role-arrow {
      color: #3b82f6;
    }
  }
}
.role-empty {
  padding: 32px 0;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}
.permission-panel {
  min-width: 0;
}
.permission-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 14px 16px 16px;
}
.tree-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}
.tree-search {
  flex: 1 1 200px;
  max-width: 280px;
}
.tree-tools {
  display: flex;
  gap: 4px;
  align-items: center;
}
.resource-select {
  width: 130px;
  margin-right: 4px;
}
.tree-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.permission-tree {
  padding: 10px;
  background: transparent;
}
:deep(.ed-tree) {
  background: transparent;
}
:deep(.ed-tree-node__content) {
  height: 34px;
  border-radius: 8px;
}
</style>
