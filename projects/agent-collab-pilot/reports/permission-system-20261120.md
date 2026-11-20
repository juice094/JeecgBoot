# 权限体系完善（范围 C：通用模板化）交付报告

> 日期：2026-11-20
> 执行：AI 编码助手（Kimi Code，主会话施工）
> 触发：zhoujingxiao 登录后大量功能/内容不可见，要求完善权限规则与管理体系
> 方案：宿拍板——范围 C（通用模板化）+ zhoujingxiao 按管理员/系统开发者赋权

## 1. 确权差异根因（事实层）

**zhoujingxiao 是零角色裸账号**：sys_user_role 无任何记录。后端 Shiro 按 sys_role_permission 授权，零角色 = 所有 `@RequiresPermissions` 403。admin 拥有"管理员"(567 项) + "Vue3全角色"(598 项)，实质全量。

三个机制事实：

1. **"系统用户标配角色"（test，描述="系统所有用户拥有的最小权限角色，默认都分配"）实际只给了 ceshi/jeecg/zhangsan**；且代码里的"全员自动叠加标配角色"（`SysBaseApiImpl.getUserPermissionSet`）**只在 `OPEN_SYSTEM_TENANT_CONTROL=true` 时生效，本部署为 false**（`MybatisPlusSaasConfig.java:48`）——设计意图落空。
2. **前端权限判断原是空数据全放行**（旧 `utils/permission.ts` 读 userInfo.permissionList，登录响应从不带该字段）——zhoujingxiao 界面上什么都能看见、一点就 403，"不可见"的真实来源是后端拒绝。
3. **team 域 47 个权限码设计良好**（team:approval/task/bug/mail/audit/dashboard 全套），挂在"蓝鸥成员"角色上一刀切，无管理层分层。

## 2. 改动清单

### 数据层（SQL：`projects/agent-collab-pilot/sql/permission-system-20261120.sql`，幂等可重跑）

| 动作 | 内容 | 结果 |
|---|---|---|
| 全员补标配角色 | 所有未持有用户补 `系统用户标配角色` | admin/zhoujingxiao/lanou_* 等全部持有 |
| zhoujingxiao 赋权 | 管理员 + Vue3全角色 + 标配（镜像 admin） | 与 admin 完全同级 |
| 角色分层 | 新建 `业务主管`(team_manager, b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2)：team:approval:approve/reject、team:audit:list、team:mail:log:stat/retry | 5 项权限 |
| 剥离 | 上述 5 项从 `蓝鸥成员` 移除（成员保留提交/查看/步骤链） | 蓝鸥成员 47→42 |
| 主管赋权 | lanou_mgr / lanou_vp 获得业务主管角色 | 与业务成员身份叠加 |

注意：zhoujingxiao 必须同时给"管理员"+"Vue3全角色"——**"管理员"角色本身不含 `system:online:list`**（实测），只给管理员角色会丢掉在线用户等监控权限。

### 前端（jeecg-team-web，typecheck 通过）

| 文件 | 改动 |
|---|---|
| `constants/app.ts` | 新增 `DEFAULT_ROLE_ID`（标配角色）、`MANAGER_ROLE_ID`（业务主管） |
| `api/sys.ts` | 新增 `getPermCode()`（GET /sys/permission/getPermCode → codeList） |
| `stores/auth.ts` | 登录后自动拉权限码存 `permissionList`（localStorage `team_perms` 持久化），logout 清理 |
| `utils/permission.ts` | hasPermission 改读 authStore.permissionList；**null=未加载兜底放行，[]=已加载但零权限拒绝** |
| `router/index.ts` | 7 条路由加 `meta.perm`（只挂后端确实 enforcement 且 admin 已持有的码）；守卫对不满足者回 /dashboard |
| `layout/SidebarMenu.vue` | 侧栏按 meta.perm 过滤，空分组不渲染 |
| `modules/admin/views/HrManage.vue` | 新增用户成功后自动分配标配角色；分配失败不阻断建号但弹窗提示人工补齐（防再出裸账号） |
| `modules/business/views/ApprovalCenter.vue` | 通过/拒绝/退回按钮按 team:approval:approve/reject 显隐（配合剥离，成员不再看到必失败的按钮） |

**路由 perm 映射**（保守原则：只挂已验证 enforcement 的码，其余路由不挂=全员可见）：
仪表盘=team:dashboard:summary｜工作项=team:task:list｜人事管理=system:user:list｜角色管理=system:role:list｜在线用户=system:online:list｜日常申请=team:approval:submit｜审批中心=team:approval:list

## 3. 现在生效的权限体系（通用模板四层）

| 层 | 角色 | 内容 |
|---|---|---|
| L0 全员标配 | 系统用户标配角色 | 基础菜单 + 个人设置等（新用户自动获得） |
| L1 业务成员 | 蓝鸥成员 | team 域业务操作（提交申请/任务/Bug/邮箱/发信，42 项） |
| L2 业务主管 | 业务主管 | 审批通过/拒绝 + 审计日志 + 邮件日志统计/重试（5 项，叠加在成员之上） |
| L3 系统管理 | 管理员(+Vue3全角色) | 用户/角色/权限/监控全量 |

## 4. 回滚

- SQL：`sql/permission-system-20261120.sql` 的 INSERT 均可按 user_id/role_id 删除；剥离的 5 项权限可重新插回蓝鸥成员；`DELETE FROM sys_role WHERE role_code='team_manager'`。
- 前端：git revert（constants/api/stores/permission/router/SidebarMenu 为基础设施改动；HrManage、ApprovalCenter 为业务模块内小改）。

## 5. 已知边界与触发条件

- **侧栏未挂 perm 的路由**（行政管理/教务/后勤/市场/Redis 监控/工作邮箱/会话等）仍全员可见——这些页面后端未确认 enforcement 或 admin 角色缺对应码，挂 perm 会误伤 admin 可见性。触发条件：逐个页面对齐后端 @RequiresPermissions 后再补挂。
- **页面级 403 无统一提示页**：守卫直接回 /dashboard，API 403 走各页错误提示。需要 403 页再补。
- **跨模块改动说明**：HrManage（Ym3699 域）与 ApprovalCenter（juice094 域）的改动属权限体系基础设施配套，已保持最小侵入，请两位 owner 知悉。
- zhoujingxiao 需**重新登录**（或后端重启后）才能拿到新角色的权限码与菜单。

## 6. 关联

- 忘记密码：`reports/forgot-password-20261120.md`；在线用户重复：`reports/online-user-dup-fix-20261120.md`
