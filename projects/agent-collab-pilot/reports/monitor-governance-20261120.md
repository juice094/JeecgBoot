# 监控域确权闭环（定时任务操作 + 数据变更日志）交付报告

> 日期：2026-11-20
> 执行：AI 编码助手（Kimi Code，主会话施工）
> 触发：按"认证/授权/鉴权/确权"四层图核对 system-biz Controller 能力，team-web 监控域存在确权缺口，推进闭环
> 前置：权限体系（`permission-system-20261120.md`）、组织初始化（`org-init-20261120.md`）

## 1. 四层对号入座的缺口盘点

| 层 | Controller | team-web 现状 | 缺口处置 |
|---|---|---|---|
| 认证 | LoginController / ThirdLogin / WechatVerify / SysUserOnline | 登录+忘记密码+在线用户均已闭环 | 第三方登录属 no-go（迁移计划书 §1），不动 |
| 授权 | SysUser/SysRole/SysPermission + 部门 | HrManage + RoleManage 已闭环 | 部门授权/直接授予（DepartRole/PermissionGrant）/租户/用户组 → P2 触发后 |
| 鉴权 | （无 Controller，运行时） | getPermCode 权限闭环已接 | — |
| **确权** | SysLogController | MonitorLogs 只读列表 | 够用（审计只读口径） |
| **确权** | **SysDataLogController** | **完全缺失** | **本轮新建 MonitorDataLog.vue** |
| **确权** | **QuartzJobController（job 文件夹）** | **只读**（docs/24 备注写操作不开放） | **本轮升级为操作闭环** |
| 确权 | SysTableWhiteList / CheckRule / FillRule | 未接 | 底层安全/表单能力，不直接参与权限治理，不做 |

## 2. 本轮改动（jeecg-team-web，`pnpm typecheck` 通过）

### 定时任务操作闭环 `MonitorQuartz.vue`（重写）

- 新增/编辑：FormModal 弹窗（任务类名/Cron/参数/描述），POST `/sys/quartzJob/add|edit`
- 暂停/启动/立即执行：GET `/sys/quartzJob/pause|resume|execute?id=`（按行状态切换"暂停/启动"）
- 删除：DELETE `/sys/quartzJob/delete?id=`，二次确认
- 全部按钮按 `system:quartzJob:add|edit|delete|pause|resume|execute` 权限码显隐（权限已验证：管理员/Vue3全角色均已绑定）
- 操作列仅在持有任一操作权限时渲染

### 数据变更日志 `MonitorDataLog.vue`（新建）

- 列表：GET `/sys/dataLog/list`，支持按表名/操作人过滤 + 分页（后端强制 type=json）
- 历史版本：GET `/sys/dataLog/queryDataVerList?dataTable=&dataId=&type=json`，按版本升序展示数据快照
- 版本对比：GET `/sys/dataLog/queryCompareList?dataId1=&dataId2=`，双栏并列快照
- 路由 `/admin/monitor/datalog`，侧栏"管理"组，icon 复用 logs
- **空表口径如实展示**：sys_data_log 当前 0 行（依赖业务侧数据日志打点才有数据），空态文案已说明原因
- 权限码 `system:dataLog:list|queryCompareList|queryDataVerList` 已验证管理员/Vue3全角色均已绑定，无需补配

### API 层 `api/monitor.ts`

新增 saveQuartzJob / deleteQuartzJob / controlQuartzJob / listDataLogs / listDataLogVersions / compareDataLogs。

## 3. 未闭环项与触发条件（保留给后续波次）

| 项 | 触发条件 |
|---|---|
| 部门授权（SysDepartRole/DepartPermission） | 出现"按部门授权"真实需求 |
| 直接授权（SysPermissionGrant） | 出现"临时授予某用户单权限"治理需求 |
| 租户管理（SysTenantController） | 蓝鸥多租户模式真实启用（计划书 P2） |
| 数据日志打点 | 业务模块在写接口加数据日志注解后，本页自动有数 |
| G2 时效（角色权限 end_time 过期自动回收） | 需扩展 sys_role_permission 表结构与 entity 字段 + 定时任务，属后端改造，另行立项 |

## 4. 验证

- L1：`pnpm typecheck` exit 0
- 权限面：6 个 quartz 写操作权限码 + 3 个 dataLog 权限码，管理员/Vue3全角色均已持有（SQL 实测）
- L2 浏览器走查：需人工在 3200 端口用 zhoujingxiao 走一遍（新增任务→暂停→启动→执行→删除；数据日志页空态），本报告标注 L1 only

## 5. 关联

- 权限体系：`reports/permission-system-20261120.md`
- 组织初始化：`reports/org-init-20261120.md`
