# 表单与审批（业务与工具模块）— juice094

## 范围
- 任务协作（任务/看板/指派/截止提醒）
- 日常申请表单（请假/报销模板）+ 审批流（轻量状态机，不上 Flowable）

## 对接
- HTTP 出口统一用 `@/utils/http`
- 表单组件建议 ant-design-vue ^4.2.6（与 jeecgboot-vue3 同栈，团队无需学新框架）
- 人员选择器依赖 admin 模块的组织架构数据 —— 接口约定开 issue

## 约束
- 只改本目录（`src/modules/business/`）与路由表中 `/business/**` 段
