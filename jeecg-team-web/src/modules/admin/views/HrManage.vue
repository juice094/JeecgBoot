<script setup lang="ts">
// 人事管理 / 部门与员工（管理组）—— owner: Ym3699
// 2026-11-18 P0 迁移：在原有只读视图上补用户 CRUD（新增/编辑/删除/重置密码/分配角色）
// 与部门树 CRUD（新增子部门/编辑/删除）。数据源仍为 sys_user / sys_depart（jeecg 原生接口）。
// 权限码与后端 @RequiresPermissions 对齐：system:user:add|edit|delete|resetPassword|frozenBatch|addUserRole、
// system:depart:add|edit|delete、system:role:list|add|edit|delete、system:permission:saveRole。
import { computed, onMounted, ref } from 'vue';
import {
  PhBuildings,
  PhUsersThree,
  PhUserMinus,
  PhPlus,
  PhMagnifyingGlass,
} from '@phosphor-icons/vue';
import {
  listDepartTree,
  listUsers,
  saveOrUpdateUser,
  deleteUser,
  resetUserPassword,
  queryUserRole,
  frozenBatch,
  saveOrUpdateDepart,
  deleteDepart,
  listAllRoles,
  addUserRole,
  deleteUserRole,
  type DepartNode,
  type SysUser,
  type SysRole,
} from '@/api/sys';
import DeptNode from '../components/DeptNode.vue';
import DataTable, { type DataTableColumn } from '@/components/DataTable.vue';
import FormModal, { type FormField } from '@/components/FormModal.vue';
import { usePermission } from '@/utils/permission';

const { hasPermission } = usePermission();

/** jeecg Result 包装在 HTTP 200 里也可能 success:false，写操作必须显式判定 */
function checkOk(res: any) {
  if (res && res.success === false) throw new Error(res.message || '操作失败');
}

/* ---------------- 列表数据 ---------------- */
const tree = ref<DepartNode[]>([]);
const users = ref<SysUser[]>([]);
const totalUsers = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const errorMsg = ref('');

const activeDeptId = ref('');
const activeDeptName = ref('');

const allRoles = ref<SysRole[]>([]);

/* ---------------- 筛选 ---------------- */
const filters = ref<{ username: string; realname: string; status: string }>({
  username: '',
  realname: '',
  status: '',
});

/* ---------------- 树/统计辅助 ---------------- */
function flatten(nodes: DepartNode[]): DepartNode[] {
  return nodes.flatMap((n) => [n, ...flatten(n.children ?? [])]);
}
const flat = computed(() => flatten(tree.value));

const orgCodeMap = computed(() => {
  const m = new Map<string, string>();
  for (const d of flat.value) {
    if (d.orgCode) m.set(d.orgCode, d.departName ?? d.id);
  }
  return m;
});

const noLeader = computed(() => {
  type DepartWithLeader = DepartNode & { leaderName?: string };
  const withField = flat.value.filter((n): n is DepartWithLeader => 'leaderName' in n);
  if (!withField.length) return null;
  return withField.filter((n) => !n.leaderName).length;
});

const stats = computed(() => [
  { title: '部门总数', value: String(flat.value.length || '-'), icon: PhBuildings, bg: '', color: '' },
  { title: '员工总数', value: String(totalUsers.value || '-'), icon: PhUsersThree, bg: '#e6f7eb', color: 'var(--success)' },
  { title: '未设负责人', value: noLeader.value === null ? '-' : String(noLeader.value), icon: PhUserMinus, bg: '#fdf6ec', color: 'var(--warning)' },
]);

/* ---------------- 数据加载 ---------------- */
async function loadTree() {
  try {
    tree.value = await listDepartTree();
  } catch (e: any) {
    errorMsg.value = e.message || '部门树加载失败';
  }
}

async function loadUsers() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value };
    // 选中部门时按其 orgCode 过滤（jeecg QueryGenerator 精确匹配，不含子部门，见报告取舍点）
    if (activeDeptId.value) {
      const node = flat.value.find((d) => d.id === activeDeptId.value);
      if (node?.orgCode) params.orgCode = node.orgCode;
    }
    if (filters.value.username) params.username = filters.value.username;
    if (filters.value.realname) params.realname = filters.value.realname;
    if (filters.value.status) params.status = filters.value.status;
    const data = await listUsers(params);
    users.value = data.result.records;
    totalUsers.value = data.result.total;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadAllRoles() {
  try {
    allRoles.value = await listAllRoles();
  } catch {
    allRoles.value = [];
  }
}

function onPageChange(p: number) {
  pageNo.value = p;
  loadUsers();
}

function applyFilters() {
  pageNo.value = 1;
  loadUsers();
}

function resetFilters() {
  filters.value = { username: '', realname: '', status: '' };
  pageNo.value = 1;
  loadUsers();
}

function selectDept(node: DepartNode) {
  activeDeptId.value = node.id;
  activeDeptName.value = node.departName ?? node.id;
  pageNo.value = 1;
  loadUsers();
}

function selectAll() {
  activeDeptId.value = '';
  activeDeptName.value = '';
  pageNo.value = 1;
  loadUsers();
}

/* ---------------- 冻结/解冻 ---------------- */
async function toggleFrozen(user: SysUser) {
  const target = user.status === 1 ? 2 : 1;
  const verb = target === 2 ? '冻结' : '解冻';
  if (!window.confirm('确认' + verb + '用户「' + (user.realname || user.username) + '」吗？')) return;
  try {
    await frozenBatch({ ids: user.id, status: target }).then(checkOk);
    await loadUsers();
  } catch (e: any) {
    alert(verb + '失败：' + (e.message || '未知错误'));
  }
}

/* ---------------- 用户列定义 ---------------- */
const userColumns: DataTableColumn[] = [
  { title: '用户账号', dataIndex: 'username', width: 120 },
  { title: '用户姓名', dataIndex: 'realname', width: 100 },
  { title: '手机号', dataIndex: 'phone', width: 120 },
  { title: '部门', dataIndex: 'orgCode', width: 160 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', dataIndex: '_ops', width: 260 },
];

const statusText = (s?: number) => (s === 1 ? '在职' : s === 2 ? '冻结' : '未知');
const statusCls = (s?: number) => (s === 1 ? 'tag--success' : s === 2 ? 'tag--danger' : 'tag--neutral');

/* ---------------- 用户表单 ---------------- */
const userModalOpen = ref(false);
const userFormLoading = ref(false);
const editingUser = ref<SysUser | null>(null);

const userFields = computed<FormField[]>(() => {
  const isEdit = !!editingUser.value;
  const f: FormField[] = [
    { field: 'username', label: '用户账号', required: true, disabled: isEdit },
  ];
  if (!isEdit) {
    f.push(
      { field: 'password', label: '登录密码', type: 'password', required: true },
      {
        field: 'confirmPassword',
        label: '确认密码',
        type: 'password',
        required: true,
        rules: [(v, vals) => (v !== vals.password ? '两次输入的密码不一致' : '')],
      },
    );
  }
  f.push(
    { field: 'realname', label: '用户姓名', required: true },
    { field: 'phone', label: '手机号' },
    { field: 'email', label: '邮箱' },
    { field: 'sex', label: '性别', type: 'select', options: [{ label: '男', value: 1 }, { label: '女', value: 2 }] },
    { field: 'status', label: '状态', type: 'select', options: [{ label: '正常', value: 1 }, { label: '冻结', value: 2 }] },
    { field: 'workNo', label: '工号' },
    {
      field: 'orgCode',
      label: '所属部门',
      type: 'select',
      span: 2,
      placeholder: '留空则不指定部门',
      options: [
        { label: '（不指定）', value: '' },
        ...flat.value
          .filter((d) => d.orgCode)
          .map((d) => ({ label: '　'.repeat(depthOf(d)) + (d.departName ?? d.id), value: d.orgCode! })),
      ],
    },
  );
  return f;
});

const userInitialValues = computed<Record<string, any>>(() =>
  editingUser.value ? { ...editingUser.value } : {},
);

function openCreateUser() {
  editingUser.value = null;
  userModalOpen.value = true;
}

function openEditUser(user: SysUser) {
  editingUser.value = user;
  userModalOpen.value = true;
}

async function submitUser(values: Record<string, any>) {
  userFormLoading.value = true;
  try {
    const isUpdate = !!editingUser.value;
    const payload: Record<string, any> = { ...values };
    if (isUpdate) payload.id = editingUser.value!.id;
    delete payload.confirmPassword;
    // sys_user/add 对空串字段一律 500（实测：sex/status int 绑定、orgCode 关联校验、
    // phone/email/workNo 唯一索引均不接受 ''）；提交前剔除全部空值。
    // 取舍：因此「把已有字段改回空」在 P0 不生效（字段缺省=不动），记报告。
    for (const k of Object.keys(payload)) {
      if (payload[k] === '' || payload[k] === null || payload[k] === undefined) delete payload[k];
    }
    checkOk(await saveOrUpdateUser(payload, isUpdate));
    userModalOpen.value = false;
    await loadUsers();
    await loadTree();
  } catch (e: any) {
    alert('保存失败：' + (e.message || '未知错误'));
  } finally {
    userFormLoading.value = false;
  }
}

async function removeUser(user: SysUser) {
  if (!window.confirm('确认删除用户「' + (user.realname || user.username) + '」吗？')) return;
  try {
    checkOk(await deleteUser(user.id));
    await loadUsers();
    await loadTree();
  } catch (e: any) {
    alert('删除失败：' + (e.message || '未知错误'));
  }
}

/* ---------------- 重置密码（后端语义：重置为系统默认密码，只收 usernames） ---------------- */
async function resetPwd(user: SysUser) {
  if (!window.confirm('确认将用户「' + (user.realname || user.username) + '」的密码重置为系统默认密码吗？')) return;
  try {
    checkOk(await resetUserPassword({ usernames: user.username }));
  } catch (e: any) {
    alert('重置失败：' + (e.message || '未知错误'));
  }
}

/* ---------------- 角色分配 ---------------- */
const roleModalOpen = ref(false);
const roleLoading = ref(false);
const roleUser = ref<SysUser | null>(null);
const currentRoleIds = ref<string[]>([]);

const roleFields = computed<FormField[]>(() => [
  {
    field: 'roleIds',
    label: '所属角色',
    type: 'checkboxes',
    span: 2,
    options: allRoles.value.map((r) => ({ label: r.roleName + '（' + r.roleCode + '）', value: r.id })),
  },
]);

const roleInitialValues = computed<Record<string, any>>(() => ({ roleIds: currentRoleIds.value }));

async function openRoleAssign(user: SysUser) {
  roleUser.value = user;
  try {
    const data = await queryUserRole(user.id);
    currentRoleIds.value = Array.isArray(data.result) ? data.result : [];
  } catch {
    currentRoleIds.value = [];
  }
  roleModalOpen.value = true;
}

async function submitRoleAssign(values: Record<string, any>) {
  if (!roleUser.value) return;
  roleLoading.value = true;
  try {
    const selected: string[] = values.roleIds ?? [];
    const toAdd = selected.filter((id) => !currentRoleIds.value.includes(id));
    const toRemove = currentRoleIds.value.filter((id) => !selected.includes(id));
    for (const roleId of toAdd) {
      checkOk(await addUserRole({ roleId, userIdList: [roleUser.value.id] }));
    }
    for (const roleId of toRemove) {
      checkOk(await deleteUserRole({ roleId, userId: roleUser.value.id }));
    }
    roleModalOpen.value = false;
  } catch (e: any) {
    alert('保存失败：' + (e.message || '未知错误'));
  } finally {
    roleLoading.value = false;
  }
}

/* ---------------- 部门表单 ---------------- */
const deptModalOpen = ref(false);
const deptFormLoading = ref(false);
const editingDept = ref<DepartNode | null>(null);
const deptParentId = ref('');

function depthOf(target: DepartNode): number {
  function walk(nodes: DepartNode[], d: number): number {
    for (const n of nodes) {
      if (n.id === target.id) return d;
      const found = walk(n.children ?? [], d + 1);
      if (found >= 0) return found;
    }
    return -1;
  }
  return Math.max(0, walk(tree.value, 0));
}

const deptFields = computed<FormField[]>(() => {
  const parentOptions = [
    { label: '（无，作为根部门）', value: '' },
    ...flat.value
      .filter((d) => d.id !== editingDept.value?.id)
      .map((d) => ({ label: '　'.repeat(depthOf(d)) + (d.departName ?? d.id), value: d.id })),
  ];
  return [
    { field: 'departName', label: '机构名称', required: true },
    { field: 'departNameAbbr', label: '机构简称' },
    { field: 'parentId', label: '上级部门', type: 'select', span: 2, options: parentOptions },
    { field: 'orgCode', label: '机构编码' },
    {
      field: 'orgCategory',
      label: '机构类型',
      type: 'select',
      options: [
        { label: '公司', value: '1' },
        { label: '子公司', value: '4' },
        { label: '部门', value: '2' },
        { label: '岗位', value: '3' },
      ],
    },
    { field: 'departOrder', label: '排序', type: 'number' },
    { field: 'mobile', label: '电话' },
    { field: 'fax', label: '传真' },
    { field: 'address', label: '地址', span: 2 },
    { field: 'memo', label: '备注', type: 'textarea', span: 2 },
  ];
});

const deptInitialValues = computed<Record<string, any>>(() =>
  editingDept.value ? { ...editingDept.value } : { parentId: deptParentId.value },
);

function openCreateDept(parent?: DepartNode) {
  editingDept.value = null;
  deptParentId.value = parent?.id ?? '';
  deptModalOpen.value = true;
}

function openEditDept(node: DepartNode) {
  editingDept.value = node;
  deptParentId.value = node.parentId ?? '';
  deptModalOpen.value = true;
}

async function submitDept(values: Record<string, any>) {
  deptFormLoading.value = true;
  try {
    const isUpdate = !!editingDept.value;
    const payload: Record<string, any> = { ...values };
    if (isUpdate) payload.id = editingDept.value!.id;
    checkOk(await saveOrUpdateDepart(payload, isUpdate));
    deptModalOpen.value = false;
    await loadTree();
  } catch (e: any) {
    alert('保存失败：' + (e.message || '未知错误'));
  } finally {
    deptFormLoading.value = false;
  }
}

async function removeDept(node: DepartNode) {
  if (!window.confirm('确认删除部门「' + (node.departName ?? node.id) + '」吗？')) return;
  try {
    checkOk(await deleteDepart(node.id));
    await loadTree();
  } catch (e: any) {
    alert('删除失败：' + (e.message || '未知错误'));
  }
}

/* ---------------- 初始化 ---------------- */
onMounted(async () => {
  await Promise.all([loadTree(), loadAllRoles()]);
  await loadUsers();
});
</script>

<template>
  <div class="card">
    <h2 style="font-size: 20px; margin-bottom: 8px">人事管理 / 部门与员工</h2>
    <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 24px">
      组织架构与成员（数据源：sys_user / sys_depart，管理组唯一出口）
    </p>

    <div class="stats-grid">
      <div v-for="s in stats" :key="s.title" class="stat-card">
        <div class="stat-icon" :style="{ background: s.bg || '', color: s.color || '' }">
          <component :is="s.icon" :size="24" />
        </div>
        <div class="stat-info">
          <div class="title">{{ s.title }}</div>
          <div class="value">{{ s.value }}</div>
        </div>
      </div>
    </div>

    <div class="org-layout">
      <div class="dept-pane">
        <div class="dept-head">
          <span>部门名称</span>
          <button
            v-if="hasPermission('system:depart:add')"
            class="btn btn--xs btn-default"
            title="新增根部门"
            @click="openCreateDept()"
          >
            <PhPlus :size="12" /> 新增
          </button>
        </div>
        <div v-if="loading && !tree.length" style="color: var(--text-muted); font-size: 13px; padding: 12px 0">加载中…</div>
        <div
          class="dept-all"
          :class="{ 'dept-all--active': !activeDeptId }"
          @click="selectAll"
        >全部成员</div>
        <DeptNode
          v-for="node in tree"
          :key="node.id"
          :node="node"
          :depth="0"
          :active-id="activeDeptId"
          :path-text="node.departName ?? node.id"
          :show-actions="hasPermission('system:depart:edit')"
          @select="selectDept"
          @add-child="openCreateDept"
          @edit="openEditDept"
          @remove="removeDept"
        />
      </div>

      <div class="member-pane">
        <div class="member-pane__head">
          <div class="dept-badge"><PhBuildings :size="20" /></div>
          <div style="flex: 1">
            <div style="font-size: 16px; font-weight: 600">{{ activeDeptName || '全部成员' }}</div>
            <div style="font-size: 12px; color: var(--text-muted)">共 {{ totalUsers }} 人</div>
          </div>
          <button
            v-if="hasPermission('system:user:add')"
            class="btn btn--sm btn-primary"
            @click="openCreateUser"
          >
            <PhPlus :size="14" /> 新增用户
          </button>
        </div>

        <div class="filter-row">
          <input
            v-model="filters.username"
            class="form-control"
            placeholder="账号"
            style="width: 140px"
            @keyup.enter="applyFilters"
          />
          <input
            v-model="filters.realname"
            class="form-control"
            placeholder="姓名"
            style="width: 140px"
            @keyup.enter="applyFilters"
          />
          <select v-model="filters.status" class="form-control" style="width: 120px" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="2">冻结</option>
          </select>
          <button class="btn btn--sm btn-default" @click="applyFilters">
            <PhMagnifyingGlass :size="12" /> 查询
          </button>
          <button class="btn btn--sm btn-text" @click="resetFilters">重置</button>
        </div>

        <DataTable
          :columns="userColumns"
          :rows="users as unknown as Record<string, unknown>[]"
          :loading="loading"
          :error-msg="errorMsg"
          :total="totalUsers"
          :page-no="pageNo"
          :page-size="pageSize"
          @update:page-no="onPageChange"
        >
          <template #cell-orgCode="{ value }">
            {{ value ? (orgCodeMap.get(String(value)) ?? value) : '-' }}
          </template>
          <template #cell-status="{ value }">
            <span class="tag" :class="statusCls(Number(value))">{{ statusText(Number(value)) }}</span>
          </template>
          <template #cell-_ops="{ row }">
            <div class="ops">
              <button
                v-if="hasPermission('system:user:edit')"
                class="btn btn--xs btn-text"
                @click="openEditUser(row as unknown as SysUser)"
              >编辑</button>
              <button
                v-if="hasPermission('system:user:frozenBatch')"
                class="btn btn--xs btn-text"
                @click="toggleFrozen(row as unknown as SysUser)"
              >{{ Number((row as unknown as SysUser).status) === 1 ? '冻结' : '解冻' }}</button>
              <button
                v-if="hasPermission('system:user:resetPassword')"
                class="btn btn--xs btn-text"
                @click="resetPwd(row as unknown as SysUser)"
              >重置密码</button>
              <button
                v-if="hasPermission('system:user:addUserRole')"
                class="btn btn--xs btn-text"
                @click="openRoleAssign(row as unknown as SysUser)"
              >角色</button>
              <button
                v-if="hasPermission('system:user:delete')"
                class="btn btn--xs btn-text ops__danger"
                @click="removeUser(row as unknown as SysUser)"
              >删除</button>
            </div>
          </template>
        </DataTable>
      </div>
    </div>

    <FormModal
      v-model="userModalOpen"
      :title="editingUser ? '编辑用户' : '新增用户'"
      :fields="userFields"
      :initial-values="userInitialValues"
      :loading="userFormLoading"
      @submit="submitUser"
    />

    <FormModal
      v-model="roleModalOpen"
      title="分配角色"
      :fields="roleFields"
      :initial-values="roleInitialValues"
      :loading="roleLoading"
      submit-text="保存"
      @submit="submitRoleAssign"
    />

    <FormModal
      v-model="deptModalOpen"
      :title="editingDept ? '编辑部门' : '新增部门'"
      :fields="deptFields"
      :initial-values="deptInitialValues"
      :loading="deptFormLoading"
      @submit="submitDept"
    />
  </div>
</template>

<style scoped>
.org-layout {
  display: flex;
  border-top: 1px solid var(--border-color);
  padding-top: 24px;
}
.dept-pane {
  width: 320px;
  border-right: 1px solid var(--border-color);
  padding-right: 20px;
  max-height: 640px;
  overflow-y: auto;
}
.dept-all {
  padding: 8px 0;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-regular);
}
.dept-all:hover {
  color: var(--primary);
}
.dept-all--active {
  color: var(--primary);
  font-weight: 500;
}
.dept-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);
}
.member-pane {
  flex: 1;
  padding-left: 24px;
  min-width: 0;
}
.member-pane__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.dept-badge {
  width: 40px;
  height: 40px;
  background: var(--primary-light);
  color: var(--primary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.ops {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  flex-wrap: wrap;
}
.ops__danger:hover {
  color: var(--danger);
}
</style>
