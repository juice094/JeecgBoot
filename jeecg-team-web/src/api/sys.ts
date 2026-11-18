// 系统管理域 API —— 蒸馏自 jeecgboot-vue3/src/views/system/{user,depart,role}
// 约定（docs/ADMIN-MIGRATION-EXECUTION-PLAN-2026-11-18.md §二）：
//   1. URL 照抄旧 `X.api.ts` 的 `enum Api`，一字不改；
//   2. `defHttp.get({url, params})` → `http.get(url, { params })`；
//   3. `defHttp.post/put({url, params})` → `http.post/put(url, params)`（params 即 body）；
//   4. `defHttp.delete({url, params}, {joinParamsToUrl:true})` → `http.delete(url, { params })`；
//   5. 多租户头不由 http 层带（http.ts 仅带 X-Access-Token），tenant 相关参数按旧签名透传。
import http from '@/utils/http';

/* ============================== 类型 ============================== */

export interface DepartNode {
  id: string;
  parentId?: string;
  departName?: string;
  departNameAbbr?: string;
  orgCode?: string;
  orgCategory?: string;
  departOrder?: number;
  mobile?: string;
  fax?: string;
  address?: string;
  memo?: string;
  leaderName?: string;
  children?: DepartNode[];
}

export interface SysUser {
  id: string;
  username: string;
  realname?: string;
  phone?: string;
  email?: string;
  sex?: number;
  status?: number;
  orgCode?: string;
  workNo?: string;
  post?: string;
  telephone?: string;
  birthday?: string;
  avatar?: string;
  userIdentity?: number;
  departIds?: string;
  roleId?: string;
  roleName?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SysRole {
  id: string;
  roleName: string;
  roleCode: string;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/** 通用分页返回（jeecg IPage 结构：records/total/current/size） */
export interface PageResult<T> {
  result: { records: T[]; total: number; current?: number; size?: number };
}

/* ============================== 用户 user ============================== */

/** 列表（租户隔离）GET /sys/user/list */
export function listUsers(
  params: Record<string, unknown> = {}
): Promise<PageResult<SysUser>> {
  return http.get<any, PageResult<SysUser>>('/sys/user/list', { params });
}

/** 列表（不隔离租户）GET /sys/user/listAll */
export function listUsersNoCareTenant(
  params: Record<string, unknown> = {}
): Promise<PageResult<SysUser>> {
  return http.get<any, PageResult<SysUser>>('/sys/user/listAll', { params });
}

/** 新增/编辑用户 POST /sys/user/add | /sys/user/edit（旧 saveOrUpdateUser 同为 POST） */
export function saveOrUpdateUser(
  params: Record<string, unknown>,
  isUpdate: boolean
): Promise<any> {
  const url = isUpdate ? '/sys/user/edit' : '/sys/user/add';
  return http.post(url, params);
}

/** 删除用户 DELETE /sys/user/delete?id= */
export function deleteUser(id: string): Promise<any> {
  return http.delete('/sys/user/delete', { params: { id } });
}

/** 批量删除 DELETE /sys/user/deleteBatch?ids= */
export function batchDeleteUser(ids: string): Promise<any> {
  return http.delete('/sys/user/deleteBatch', { params: { ids } });
}

/** 重置密码 PUT /sys/user/resetPassword?usernames=（后端只收 usernames，重置为系统默认密码） */
export function resetUserPassword(params: { usernames: string }): Promise<any> {
  return http.put('/sys/user/resetPassword', null, { params });
}

/** 修改密码 PUT /sys/user/changePassword */
export function changeUserPassword(params: Record<string, unknown>): Promise<any> {
  return http.put('/sys/user/changePassword', params);
}

/** 冻结/解冻 PUT /sys/user/frozenBatch */
export function frozenBatch(params: Record<string, unknown>): Promise<any> {
  return http.put('/sys/user/frozenBatch', params);
}

/** 查询用户已分配角色 GET /sys/user/queryUserRole?userid= */
export function queryUserRole(userid: string): Promise<{ result: string[]; success: boolean }> {
  return http.get<any, { result: string[]; success: boolean }>('/sys/user/queryUserRole', {
    params: { userid },
  });
}

/** 唯一校验 GET /sys/duplicate/check */
export function duplicateCheck(params: {
  tableName: string;
  fieldName: string;
  fieldVal: string;
  dataId?: string;
}): Promise<{ success: boolean; message?: string }> {
  return http.get<any, { success: boolean; message?: string }>('/sys/duplicate/check', {
    params,
  });
}

/* ============================== 部门 depart ============================== */

/** 部门树（全部）GET /sys/sysDepart/queryTreeList */
export async function listDepartTree(params: Record<string, unknown> = {}): Promise<DepartNode[]> {
  const data: any = await http.get('/sys/sysDepart/queryTreeList', { params });
  const result = data?.result;
  const rows: DepartNode[] = Array.isArray(result) ? result : result?.records ?? result?.list ?? [];
  // queryTreeList 可能包一层 { departInfoList, ... } 结构，做兜底
  return rows.length ? rows : result?.departInfoList ?? [];
}

/** 部门树（同步版，部门管理页用）GET /sys/sysDepart/queryDepartTreeSync */
export function queryDepartTreeSync(
  params: Record<string, unknown> = {}
): Promise<{ result: DepartNode[]; success: boolean }> {
  return http.get<any, { result: DepartNode[]; success: boolean }>(
    '/sys/sysDepart/queryDepartTreeSync',
    { params }
  );
}

/** 部门管理页专用搜索 GET /sys/sysDepart/searchByDepartManage */
export function searchByDepartManage(
  params: Record<string, unknown>
): Promise<{ result: DepartNode[]; success: boolean }> {
  return http.get<any, { result: DepartNode[]; success: boolean }>(
    '/sys/sysDepart/searchByDepartManage',
    { params }
  );
}

/** 新增部门 POST /sys/sysDepart/add；编辑部门 PUT /sys/sysDepart/edit（旧 saveOrUpdateDepart） */
export function saveOrUpdateDepart(
  params: Record<string, unknown>,
  isUpdate: boolean
): Promise<any> {
  return isUpdate
    ? http.put('/sys/sysDepart/edit', params)
    : http.post('/sys/sysDepart/add', params);
}

/** 删除部门 DELETE /sys/sysDepart/delete?id= */
export function deleteDepart(id: string): Promise<any> {
  return http.delete('/sys/sysDepart/delete', { params: { id } });
}

/** 批量删除部门 DELETE /sys/sysDepart/deleteBatch?ids= */
export function batchDeleteDepart(ids: string): Promise<any> {
  return http.delete('/sys/sysDepart/deleteBatch', { params: { ids } });
}

/* ============================== 角色 role ============================== */

/** 角色列表 GET /sys/role/list */
export function listRoles(
  params: Record<string, unknown> = {}
): Promise<PageResult<SysRole>> {
  return http.get<any, PageResult<SysRole>>('/sys/role/list', { params });
}

/** 全部角色（下拉用）GET /sys/role/queryall */
export function listAllRoles(params: Record<string, unknown> = {}): Promise<SysRole[]> {
  return http.get<any, SysRole[]>('/sys/role/queryall', { params });
}

/** 新增/编辑角色 POST /sys/role/add | /sys/role/edit（旧 saveOrUpdateRole 同为 POST） */
export function saveOrUpdateRole(
  params: Record<string, unknown>,
  isUpdate: boolean
): Promise<any> {
  const url = isUpdate ? '/sys/role/edit' : '/sys/role/add';
  return http.post(url, params);
}

/** 删除角色 DELETE /sys/role/delete?id= */
export function deleteRole(id: string): Promise<any> {
  return http.delete('/sys/role/delete', { params: { id } });
}

/** 批量删除角色 DELETE /sys/role/deleteBatch?ids= */
export function batchDeleteRole(ids: string): Promise<any> {
  return http.delete('/sys/role/deleteBatch', { params: { ids } });
}

/** 角色编码唯一校验 GET /sys/role/checkRoleCode */
export function isRoleExist(params: { id?: string; roleCode: string }): Promise<{
  success: boolean;
  message?: string;
}> {
  return http.get<any, { success: boolean; message?: string }>('/sys/role/checkRoleCode', {
    params,
  });
}

/* -------------------------- 角色权限树 -------------------------- */

/** 权限树（菜单/按钮全量）GET /sys/role/queryTreeList */
/** 权限树节点：旧 RolePermissionDrawer 用 fieldNames { key, title: 'slotTitle' } */
export interface PermissionNode {
  id?: string;
  key: string;
  parentId?: string;
  title?: string;
  slotTitle?: string;
  label?: string;
  isLeaf?: boolean;
  children?: PermissionNode[];
}

/** GET /sys/role/queryTreeList → result = { treeList, ids }（ids=全部节点 key，供全选/展开用） */
export function queryPermissionTree(): Promise<{
  result: { treeList: PermissionNode[]; ids: string[] };
  success: boolean;
}> {
  return http.get<
    any,
    { result: { treeList: PermissionNode[]; ids: string[] }; success: boolean }
  >('/sys/role/queryTreeList');
}

/** 角色已勾选权限 GET /sys/permission/queryRolePermission?roleId= */
export function queryRolePermission(
  roleId: string
): Promise<{ result: string[]; success: boolean }> {
  return http.get<any, { result: string[]; success: boolean }>(
    '/sys/permission/queryRolePermission',
    { params: { roleId } }
  );
}

/** 保存角色权限 POST /sys/permission/saveRolePermission（后端契约：permissionIds 驼峰、lastpermissionids 全小写，已实证） */
export function saveRolePermission(params: {
  roleId: string;
  permissionIds: string;
  /** 后端读 json.getString("lastpermissionids")，全小写；传错键会导致取消勾选不生效 */
  lastpermissionids?: string;
}): Promise<any> {
  return http.post('/sys/permission/saveRolePermission', params);
}

/* -------------------------- 角色下用户 -------------------------- */

/** 角色下用户列表 GET /sys/user/userRoleList */
export function listRoleUsers(
  params: Record<string, unknown>
): Promise<PageResult<SysUser>> {
  return http.get<any, PageResult<SysUser>>('/sys/user/userRoleList', { params });
}

/** 向角色添加已有用户 POST /sys/user/addSysUserRole（userIdList 须为数组，后端反序列化 ArrayList<String>） */
export function addUserRole(params: {
  roleId: string;
  userIdList: string[];
}): Promise<any> {
  return http.post('/sys/user/addSysUserRole', params);
}

/** 从角色移除用户 DELETE /sys/user/deleteUserRole?roleId=&userId= */
export function deleteUserRole(params: { roleId: string; userId: string }): Promise<any> {
  return http.delete('/sys/user/deleteUserRole', { params });
}
