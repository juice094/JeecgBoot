<script setup lang="ts">
// 角色管理（管理组）—— 蒸馏自 jeecgboot-vue3/src/views/system/role/
// 规格来源：role.data.ts（列/表单/校验）、index.vue（交互流）、RolePermissionDrawer.vue（授权树）。
// 取舍：导出/导入 Excel、角色首页配置、数据规则（datarule）、角色用户管理不做（P0 只取 CRUD+授权）；
// 授权树默认层级关联（勾选级联子孙），与旧版默认 checkStrictly=false 一致；「层级独立」开关不做。
import { computed, onMounted, ref } from 'vue';
import { PhPlus, PhMagnifyingGlass, PhX } from '@phosphor-icons/vue';
import {
  listRoles,
  saveOrUpdateRole,
  deleteRole,
  isRoleExist,
  queryPermissionTree,
  queryRolePermission,
  saveRolePermission,
  type SysRole,
  type PermissionNode,
} from '@/api/sys';
import DataTable, { type DataTableColumn } from '@/components/DataTable.vue';
import FormModal, { type FormField } from '@/components/FormModal.vue';
import PermissionTreeNode from '../components/PermissionTreeNode.vue';
import { usePermission } from '@/utils/permission';

const { hasPermission } = usePermission();

/** jeecg Result 包装在 HTTP 200 里也可能 success:false，写操作必须显式判定 */
function checkOk(res: any) {
  if (res && res.success === false) throw new Error(res.message || '操作失败');
}

/* ---------------- 列表 ---------------- */
const roles = ref<SysRole[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const errorMsg = ref('');

const filters = ref<{ roleName: string; roleCode: string }>({ roleName: '', roleCode: '' });

const columns: DataTableColumn[] = [
  { title: '角色名称', dataIndex: 'roleName', width: 140 },
  { title: '角色编码', dataIndex: 'roleCode', width: 140 },
  { title: '备注', dataIndex: 'description' },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', dataIndex: '_ops', width: 150, align: 'right' },
];

async function loadRoles() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: Record<string, unknown> = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      column: 'id',
      order: 'desc',
    };
    if (filters.value.roleName) params.roleName = filters.value.roleName;
    if (filters.value.roleCode) params.roleCode = filters.value.roleCode;
    const data = await listRoles(params);
    roles.value = data.result.records;
    total.value = data.result.total;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

function onPageChange(p: number) {
  pageNo.value = p;
  loadRoles();
}

function applyFilters() {
  pageNo.value = 1;
  loadRoles();
}

function resetFilters() {
  filters.value = { roleName: '', roleCode: '' };
  pageNo.value = 1;
  loadRoles();
}

/* ---------------- 新增/编辑 ---------------- */
const modalOpen = ref(false);
const formLoading = ref(false);
const editingRole = ref<SysRole | null>(null);

const roleFields = computed<FormField[]>(() => [
  { field: 'roleName', label: '角色名称', required: true },
  {
    field: 'roleCode',
    label: '角色编码',
    required: true,
    disabled: !!editingRole.value,
    placeholder: editingRole.value ? '' : '角色编码创建后不可修改',
  },
  { field: 'description', label: '备注', type: 'textarea', span: 2 },
]);

const roleInitialValues = computed<Record<string, any>>(() =>
  editingRole.value ? { ...editingRole.value } : {},
);

function openCreate() {
  editingRole.value = null;
  modalOpen.value = true;
}

function openEdit(role: SysRole) {
  editingRole.value = role;
  modalOpen.value = true;
}

async function submitRole(values: Record<string, any>) {
  formLoading.value = true;
  try {
    const isUpdate = !!editingRole.value;
    // roleCode 唯一性校验（旧版在表单内异步校验，FormModal 校验为同步，这里提交前预检，语义等价）
    if (!isUpdate) {
      const check = await isRoleExist({ roleCode: String(values.roleCode ?? '') });
      if (!check.success) {
        alert(check.message || '角色编码已存在');
        return;
      }
    }
    const payload: Record<string, any> = { ...values };
    if (isUpdate) payload.id = editingRole.value!.id;
    checkOk(await saveOrUpdateRole(payload, isUpdate));
    modalOpen.value = false;
    await loadRoles();
  } catch (e: any) {
    alert('保存失败：' + (e.message || '未知错误'));
  } finally {
    formLoading.value = false;
  }
}

async function removeRole(role: SysRole) {
  if (!window.confirm('确认删除角色「' + role.roleName + '」吗？')) return;
  try {
    checkOk(await deleteRole(role.id));
    await loadRoles();
  } catch (e: any) {
    alert('删除失败：' + (e.message || '未知错误'));
  }
}

/* ---------------- 授权（权限树） ---------------- */
const permOpen = ref(false);
const permLoading = ref(false);
const permSaving = ref(false);
const permRole = ref<SysRole | null>(null);
const permTree = ref<PermissionNode[]>([]);
const allPermKeys = ref<string[]>([]);
const checkedKeys = ref<Set<string>>(new Set());
const defaultCheckedKeys = ref<string[]>([]);
const expandedKeys = ref<Set<string>>(new Set());
const permSearch = ref('');

function nodeKey(n: PermissionNode): string {
  return n.key ?? n.id ?? '';
}

function nodeTitle(n: PermissionNode): string {
  return n.slotTitle ?? n.title ?? n.label ?? nodeKey(n);
}

function collectDescendantKeys(n: PermissionNode): string[] {
  const keys = [nodeKey(n)];
  for (const c of n.children ?? []) keys.push(...collectDescendantKeys(c));
  return keys;
}

// 搜索过滤：命中节点及其祖先保留，子树全量保留（与旧版 expandOnSearch 行为近似）
const visibleTree = computed<PermissionNode[]>(() => {
  const kw = permSearch.value.trim();
  if (!kw) return permTree.value;
  function filterNode(n: PermissionNode): PermissionNode | null {
    const self = nodeTitle(n).includes(kw);
    if (self) return n;
    const children = (n.children ?? [])
      .map(filterNode)
      .filter((c): c is PermissionNode => c !== null);
    return children.length ? { ...n, children } : null;
  }
  return permTree.value
    .map(filterNode)
    .filter((n): n is PermissionNode => n !== null);
});

function toggleNode(n: PermissionNode) {
  const k = nodeKey(n);
  const keys = collectDescendantKeys(n);
  const next = new Set(checkedKeys.value);
  if (next.has(k)) {
    for (const key of keys) next.delete(key);
  } else {
    for (const key of keys) next.add(key);
  }
  checkedKeys.value = next;
}

function toggleExpand(k: string) {
  const next = new Set(expandedKeys.value);
  if (next.has(k)) next.delete(k);
  else next.add(k);
  expandedKeys.value = next;
}

function checkAll() {
  checkedKeys.value = new Set(allPermKeys.value);
}

function clearAll() {
  checkedKeys.value = new Set();
}

function expandAll() {
  expandedKeys.value = new Set(allPermKeys.value);
}

function collapseAll() {
  expandedKeys.value = new Set();
}

async function openPermission(role: SysRole) {
  permRole.value = role;
  permOpen.value = true;
  permLoading.value = true;
  permSearch.value = '';
  checkedKeys.value = new Set();
  defaultCheckedKeys.value = [];
  try {
    const treeResp = await queryPermissionTree();
    permTree.value = treeResp.result?.treeList ?? [];
    allPermKeys.value = treeResp.result?.ids ?? [];
    expandedKeys.value = new Set(allPermKeys.value); // 旧版默认全部展开
    const permResp = await queryRolePermission(role.id);
    const ids = Array.isArray(permResp.result) ? permResp.result : [];
    checkedKeys.value = new Set(ids);
    defaultCheckedKeys.value = ids;
  } catch (e: any) {
    alert('权限数据加载失败：' + (e.message || '未知错误'));
    permOpen.value = false;
  } finally {
    permLoading.value = false;
  }
}

async function submitPermission() {
  if (!permRole.value || permSaving.value) return;
  permSaving.value = true;
  try {
    checkOk(
      await saveRolePermission({
        roleId: permRole.value.id,
        permissionIds: [...checkedKeys.value].join(','),
        lastpermissionIds: defaultCheckedKeys.value.join(','),
      })
    );
    permOpen.value = false;
  } catch (e: any) {
    alert('保存失败：' + (e.message || '未知错误'));
  } finally {
    permSaving.value = false;
  }
}

onMounted(loadRoles);
</script>

<template>
  <div class="card">
    <h2 style="font-size: 20px; margin-bottom: 8px">角色管理</h2>
    <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 24px">
      系统角色与菜单权限（数据源：sys_role / sys_permission）
    </p>

    <div class="filter-row">
      <input
        v-model="filters.roleName"
        class="form-control"
        placeholder="角色名称"
        style="width: 160px"
        @keyup.enter="applyFilters"
      />
      <input
        v-model="filters.roleCode"
        class="form-control"
        placeholder="角色编码"
        style="width: 160px"
        @keyup.enter="applyFilters"
      />
      <button class="btn btn--sm btn-default" @click="applyFilters">
        <PhMagnifyingGlass :size="12" /> 查询
      </button>
      <button class="btn btn--sm btn-text" @click="resetFilters">重置</button>
      <div style="flex: 1"></div>
      <button
        v-if="hasPermission('system:role:add')"
        class="btn btn--sm btn-primary"
        @click="openCreate"
      >
        <PhPlus :size="14" /> 新增角色
      </button>
    </div>

    <DataTable
      :columns="columns"
      :rows="roles as unknown as Record<string, unknown>[]"
      :loading="loading"
      :error-msg="errorMsg"
      :total="total"
      :page-no="pageNo"
      :page-size="pageSize"
      @update:page-no="onPageChange"
    >
      <template #cell-description="{ value }">
        <span class="desc-cell">{{ value || '-' }}</span>
      </template>
      <template #cell-_ops="{ row }">
        <div class="ops">
          <button
            v-if="hasPermission('system:permission:saveRole')"
            class="btn btn--xs btn-text"
            @click="openPermission(row as unknown as SysRole)"
          >授权</button>
          <button
            v-if="hasPermission('system:role:edit')"
            class="btn btn--xs btn-text"
            @click="openEdit(row as unknown as SysRole)"
          >编辑</button>
          <button
            v-if="hasPermission('system:role:delete')"
            class="btn btn--xs btn-text ops__danger"
            @click="removeRole(row as unknown as SysRole)"
          >删除</button>
        </div>
      </template>
    </DataTable>

    <FormModal
      v-model="modalOpen"
      :title="editingRole ? '编辑角色' : '新增角色'"
      :fields="roleFields"
      :initial-values="roleInitialValues"
      :loading="formLoading"
      @submit="submitRole"
    />

    <!-- 授权弹窗：树勾选交互超 FormModal 的 schema 表达力，按旧 RolePermissionDrawer 复刻为定制弹窗 -->
    <div v-if="permOpen" class="perm__mask" @click.self="permOpen = false">
      <div class="perm">
        <div class="perm__head">
          <h3 class="perm__title">角色权限配置 · {{ permRole?.roleName }}</h3>
          <button class="icon-btn" title="关闭" @click="permOpen = false"><PhX :size="16" /></button>
        </div>

        <div class="perm__toolbar">
          <input
            v-model="permSearch"
            class="form-control"
            placeholder="按名称搜索"
            style="width: 200px"
          />
          <div style="flex: 1"></div>
          <button class="btn btn--xs btn-default" @click="checkAll">全选</button>
          <button class="btn btn--xs btn-default" @click="clearAll">清空</button>
          <button class="btn btn--xs btn-text" @click="expandAll">展开</button>
          <button class="btn btn--xs btn-text" @click="collapseAll">折叠</button>
        </div>

        <div class="perm__body">
          <div v-if="permLoading" class="perm__tip">权限树加载中…</div>
          <template v-else>
            <div v-if="!visibleTree.length" class="perm__tip">无匹配节点</div>
            <ul class="ptree">
              <PermissionTreeNode
                v-for="n in visibleTree"
                :key="nodeKey(n)"
                :node="n"
                :checked-keys="checkedKeys"
                :expanded-keys="expandedKeys"
                :title="nodeTitle(n)"
                @toggle="toggleNode"
                @toggle-expand="toggleExpand"
              />
            </ul>
          </template>
        </div>

        <div class="perm__foot">
          <button class="btn btn-default" @click="permOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="permSaving || permLoading" @click="submitPermission">
            {{ permSaving ? '提交中…' : '保存并关闭' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.desc-cell {
  color: var(--text-muted);
  font-size: 12px;
}
.ops {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
}
.ops__danger:hover {
  color: var(--danger);
}

.perm__mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.perm {
  width: 640px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}
.perm__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}
.perm__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}
.perm__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
}
.perm__body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
  min-height: 240px;
}
.perm__tip {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 24px 0;
}
.perm__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
}
.ptree {
  list-style: none;
  margin: 0;
  padding: 0;
}
</style>
