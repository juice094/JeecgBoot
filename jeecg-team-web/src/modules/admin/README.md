# 组织租户管理（管理端模块）— Ym3699

## 范围
- 租户管理、组织架构（部门树）、团队成员维护、角色分配
- 提供**人员/部门选择器组件**给 message、business 模块复用（导出入口写在 index.ts）

## 对接
- jeecg-boot 原生接口：`/sys/user/*`、`/sys/sysDepart/*`、`/sys/tenant/*`
- 权限：admin 角色；token 由登录模块统一签发（登录页属公共需求，开 issue 协商归属）

## 约束
- 只改本目录（`src/modules/admin/`）与路由表中 `/admin/**` 段
- 部门/人员选择器组件变更需同步知会 message / business 负责人
