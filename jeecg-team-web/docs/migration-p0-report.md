# 管理域迁移 P0 波执行报告（2026-11-19）

> 依据：`ADMIN-MIGRATION-PLAN-2026-11-18.md` + `ADMIN-MIGRATION-EXECUTION-PLAN-2026-11-18.md`。
> 范围：用户/部门/角色三模块蒸馏进 jeecg-team-web。通用件与 sys.ts 骨架由前次会话产出，本次完成 RoleManage 新页、HrManage 缺口补齐、契约修正与 L2 实拍。
> 验证账号：zhoujingxiao（admin 角色）。全程 typecheck / build exit 0。

## 1. 页面清单

### 1.1 角色管理 RoleManage（新建）

- 实现文件：
  - `src/modules/admin/views/RoleManage.vue`（484 行）
  - `src/modules/admin/components/PermissionTreeNode.vue`（96 行，授权树递归节点）
  - `src/router/index.ts`（+6 行，路由 `admin/role`，meta.group=管理 自动进侧栏）
- 蒸馏来源：`jeecgboot-vue3/src/views/system/role/`（index.vue / role.data.ts / role.api.ts / components/RolePermissionDrawer.vue）
- 用到的端点（8 个，全部浏览器实拍 200）：
  `GET /sys/role/list`、`GET /sys/role/checkRoleCode`、`POST /sys/role/add`、`POST /sys/role/edit`、`DELETE /sys/role/delete`、`GET /sys/role/queryTreeList`、`GET /sys/permission/queryRolePermission`、`POST /sys/permission/saveRolePermission`
- 验证级别：**L2**。证据：
  - 列表/筛选：`/sys/role/list?pageNo=1&pageSize=10` 200，真实数据渲染（截图 `docs/p0-shots/04-role-list-20261119.jpg`）
  - 新增：checkRoleCode 200 → role/add POST 200 → 列表自动刷新（网络面板 requestId 30296.85-87）
  - 编辑：role/edit POST 200；编辑态角色编码正确禁用（DOM 实测 `disabled:true`）
  - 授权：queryTreeList 200 + queryRolePermission 200，树渲染（截图 05），勾选 1 项后 saveRolePermission POST 200
  - 删除：role/delete DELETE 200，列表确认条目消失
- 取舍点：
  - 导出/导入 Excel、批量删除、角色用户管理、数据规则（datarule）、角色首页配置不做（P0 只取 CRUD+授权核心 8 端点）
  - 授权树只做「层级关联」（勾选级联子孙），与旧版默认 checkStrictly=false 一致；「层级独立」开关、localStorage 操作缓存不做
  - roleCode 唯一性校验：旧版为表单内异步校验，FormModal 校验规则是同步函数，改为提交前预检（语义等价）
  - 权限码照后端 `@RequiresPermissions`（旧前端角色页 data.ts 无显式权限码）：system:role:add|edit|delete、system:permission:saveRole

### 1.2 部门管理（嵌进 HrManage）

- 实现文件：`src/modules/admin/views/HrManage.vue`（676 行，本次改动约 90 行）、`src/modules/admin/components/DeptNode.vue`（111 行，行内操作按钮为前次会话所加）
- 蒸馏来源：`jeecgboot-vue3/src/views/system/depart/`（index.vue / depart.data.ts / depart.api.ts）
- 用到的端点（4 个，全部实拍 200）：
  `GET /sys/sysDepart/queryTreeList`、`POST /sys/sysDepart/add`、`PUT /sys/sysDepart/edit`、`DELETE /sys/sysDepart/delete`
- 验证级别：**L2**。证据：新增子部门 add 200 → 树刷新出现新节点 → 编辑改名 edit 200 → 删除 delete 200（requestId 30296.105/107/109）
- 取舍点：拖拽排序不做（计划书明确）；树搜索不做（P0 简化）；行内按钮走 hover 显示（新前端惯例）

### 1.3 用户管理（嵌进 HrManage）

- 实现文件：同上 `HrManage.vue`
- 蒸馏来源：`jeecgboot-vue3/src/views/system/user/`（index.vue / user.data.ts / user.api.ts）
- 用到的端点（8 个，全部实拍 200）：
  `GET /sys/user/list`、`POST /sys/user/add`、`POST /sys/user/edit`、`DELETE /sys/user/delete`、`PUT /sys/user/frozenBatch`、`PUT /sys/user/resetPassword`、`GET /sys/user/queryUserRole`、`POST /sys/user/addSysUserRole`（+ `GET /sys/role/queryall` 下拉数据源；`DELETE /sys/user/deleteUserRole` curl 实证 200）
- 验证级别：**L2**。证据：
  - 列表/筛选/分页：user/list 200，账号筛选生效（截图 06）
  - 新增：user/add POST 200（必填校验、两次密码一致性校验前端拦截属实）
  - 编辑：user/edit POST 200，编辑态账号禁用（DOM 实测）
  - 角色分配：queryUserRole 200 → 勾选「蓝鸥成员」→ addSysUserRole POST 200
  - 重置密码：resetPassword PUT 200（`?usernames=`）
  - 冻结：frozenBatch PUT 200，行内状态变「冻结」且按钮翻转为「解冻」
  - 删除：user/delete DELETE 200，列表确认消失
- 取舍点：
  - 部门树点击过滤用户列表用 `orgCode` 精确匹配（jeecg QueryGenerator 行为），**不含子部门人员**；新增「全部成员」伪节点回到全量
  - 用户列表走 `/sys/user/list`（租户隔离版）而非旧管理页的 `/listAll`——两者对 zhoujingxiao 均 200，保守取隔离版；listAll 已蒸馏备用
  - 用户表单部门选择用下拉（部门树铺平缩进）替代旧版 JTreeSelect；离职/代理人/回收站/导入导出不做
  - 旧用户页多达 15+ 字段（生日/职位/座机等），P0 取核心 9 字段

## 2. 通用件契约（P1 波复用依赖）

### DataTable.vue（`src/components/DataTable.vue`，213 行）

```ts
props: {
  columns: { title: string; dataIndex: string; width?: string | number; align?: 'left'|'center'|'right' }[];
  rows: Record<string, unknown>[];
  rowKey?: string;            // 默认 'id'
  total?: number;             // 0 则不渲染分页
  pageNo?: number;            // v-model:page-no
  pageSize?: number;          // v-model:page-size
  loading?: boolean;
  errorMsg?: string;          // 非空显示错误行
  emptyText?: string;         // 默认 '暂无数据'
}
slots: `cell-<dataIndex>`（scope: { row, value, index }，默认渲染纯文本，杜绝 v-html）
```

### FormModal.vue（`src/components/FormModal.vue`，283 行）

```ts
props: {
  modelValue: boolean;        // v-model 显隐
  title: string;
  fields: {
    field: string; label: string;
    type?: 'input'|'textarea'|'number'|'password'|'select'|'switch'|'checkboxes';
    placeholder?: string; required?: boolean;
    options?: { label: string; value: string | number }[];   // select/checkboxes
    disabled?: boolean | ((values) => boolean);
    span?: 1 | 2;             // 2=整行
    rules?: ((value, values) => string)[];                    // 同步校验，返回错误串，空串通过
  }[];
  initialValues?: Record<string, any>;   // 打开时回填
  loading?: boolean; width?: number; submitText?: string;
}
events: update:modelValue / submit(values 已过校验) / cancel
```

注意：rules 仅支持同步函数；异步校验（如唯一性）请在 submit 处理函数内预检。

### 权限工具（`src/utils/permission.ts`，20 行）

`hasPermission(code)`：读登录用户 permissionList/permissions；后端未下发时**放行**（渐进增强，按钮显隐不是安全边界，后端 @RequiresPermissions 才是）。

## 3. 异常清单（契约偏差与修复记录）

| # | 问题 | 处置 |
|---|---|---|
| 1 | `GET /sys/role/queryTreeList` 的 result 是 `{ treeList, ids }` 对象而非数组 | sys.ts 类型已修正，RoleManage 按实测结构取值 |
| 2 | `POST /sys/permission/saveRolePermission` 后端键名为 `lastpermissionIds`（小写 p），非 lastPermissionIds | 照抄旧前端，sys.ts 已修正 |
| 3 | `PUT /sys/user/resetPassword` 后端只收 `usernames`（复数），语义=重置为系统默认密码；前次会话按"username+password 自定密码"实现会 400 | 已改为确认即重置（与旧 index.vue 批量重置一致），实拍 200 |
| 4 | `POST /sys/user/addSysUserRole` 的 `userIdList` 必须是**数组**（后端 ArrayList<String> 反序列化），传字符串 500 | sys.ts 类型收窄为 `string[]`，实拍 200 |
| 5 | `GET /sys/user/queryUserRole` 对无角色用户返回 code 500「未找到用户相关角色信息」 | 前端按空角色处理（不弹错），属后端既定行为，未改后端 |
| 6 | 前次会话 `listAllRoles` 未解包 `result` 信封，导致角色分配弹窗选项恒空 | sys.ts 已修（解包），实拍修复后 addSysUserRole 200 |
| 7 | 无 403/缺端点：本波 21 个端点对 zhoujingxiao（admin 角色）全部 200 | 无需后端配权限 |

## 4. 回归与质量门

- `pnpm typecheck` exit 0；`pnpm build` exit 0（801ms/616ms 两次）
- 在役三体系回归（浏览器实拍）：工作邮箱 /tools/email（内容渲染 4106 字符）、审批中心 /tools/approval（正常渲染）、任务看板 /tools/taskboard（正常渲染）——零回归
- 全程 50 个请求 0 个 ≥400
- 测试数据全部清理：迁移验证角色/用户/部门均已删除，接口侧复查 total=0

## 5. 已知风险与建议（留给 P1 波）

1. **侧栏图标**：RoleManage 路由 icon='role' 未在 `src/layout/SidebarMenu.vue` 的 MENU_ICONS 注册（layout 不在授权范围），当前回退默认方块图标；建议主会话补一行映射（如 PhIdentificationCard）。
2. **部门过滤不含子部门**：orgCode 精确匹配语义；若需含子部门需后端支持下钻参数或前端按前缀展开，列入待拍。
3. **hasPermission 放行策略**：后端不下发 permissionList 时按钮全显，非管理员用户会看到点了报错的按钮；P1 可接 /sys/permission/getUserPermissionByToken 真实下发。
4. **授权树仅层级关联**：旧版「层级独立」模式未做，如有精细授权需求再补。
5. **HrManage 单页 676 行**：P1 若继续堆功能（岗位/职级）建议先拆 `admin/components/UserTable.vue`。
6. **docs/p0-shots/ 01-03.png** 为前次会话遗留证据（对应实现曾被回滚），本波以 04-06.jpg 为准；01-03 可由主会话决定保留或清理。

## 6. 改动文件总清单

| 文件 | 状态 |
|---|---|
| src/components/DataTable.vue | 前次会话新建（本波复用，未改） |
| src/components/FormModal.vue | 前次会话新建（本波复用，未改） |
| src/utils/permission.ts | 前次会话新建（未改） |
| src/api/sys.ts | 前次会话新建；本波修 3 处（queryTreeList 结构、lastpermissionIds、resetPassword usernames、listAllRoles 解包、addUserRole 数组） |
| src/modules/admin/views/RoleManage.vue | 本波新建 |
| src/modules/admin/components/PermissionTreeNode.vue | 本波新建 |
| src/modules/admin/components/DeptNode.vue | 前次会话改（行内操作），本波未改 |
| src/modules/admin/views/HrManage.vue | 本波补齐（部门过滤、冻结/解冻、部门下拉、重置密码语义修正、权限码对齐后端） |
| src/router/index.ts | 本波加 RoleManage 路由 |
| docs/p0-shots/04-06.jpg | 本波 L2 证据 |
| docs/migration-p0-report.md | 本报告 |
