# 19 — 表单与审批模块需求与接口文档（issue #3 落地版）

> 2026-11-17，bizflow-dev 起草，依据：issue #3（M3 里程碑）、docs/16（页面↔后端映射）、
> docs/13（协作规矩）、缺口清单（2026-11-17 首轮）。与 issue #3 原合同差异处均已标注"口径变更"。
> 本文档随代码落地同步更新；提交分支：pilot/design（docs 归档惯例）。

## 一、需求概述

日常申请表单 + 轻量审批流（**不上 Flowable**）。覆盖 issue #3 验收口径：
"模板端到端走通；流转记录可查"。

### 1.1 合同项对照（issue #3 原文 → 落地口径）

| #3 合同项 | 落地口径 | 状态 |
|---|---|---|
| 表单模板表 + 审批实例表 DDL | 口径变更：agent_collab 线已封存（docs/11），落 `team_form_template`（V3.9.6_5）+ `team_approval`/`team_approval_step`（V3.9.6_3） | ✅ 本文档落地 |
| 内置请假/报销模板 | `team_form_template` Flyway 种子数据两个模板，步骤模板 JSON 配置化 | ✅ |
| 轻量状态机（提交→审批中→通过/驳回） | 显式状态字段 + 状态机守卫（重复操作/越权/自审拦截）+ 审计留痕 | ✅ 已落地 |
| 多级审批 | 口径变更：issue 写"后置"，实际已前置实现（步骤链 currentStep 推进） | ✅ |
| 前端三视图：表单提交页/我的申请/待我审批 | RequestForm.vue / ApprovalCenter.vue（我发起的 tab）+ 待我审批 tab | ✅ 本文档落地 |
| 人员选择器（依赖 admin #4/#5） | 未接入，硬阻塞在 admin 线；过渡期审批人按模板配置的姓名展示 | ⏸ 阻塞 |
| 约束：状态机显式状态 + 流转记录表 | `team_audit_log`（V3.9.6_4）三处埋点，`/team/audit/list` 可查 | ✅ 已落地 |

### 1.2 功能点清单

| 编号 | 功能点 | 端点 | 说明 |
|---|---|---|---|
| F-A1 | 模板列表 | GET /team/approval/template/list | 启用模板按 sort_no，供表单页左侧导航与流程预览 |
| F-A2 | 提交申请 | POST /team/approval/submit | body 含 approval + steps + templateId（可选）；templateId 给定且 steps 为空时用模板默认步骤链 |
| F-A3 | 待我审批 | GET /team/approval/todo | 两步走（step wait+姓名 → 主表 pending+currentStep 匹配）；重名互见限制见端点注解 |
| F-A4 | 我的申请 | GET /team/approval/my | applicantId=当前登录人，可按 status tab 过滤 |
| F-A5 | 审批操作 | POST /team/approval/approve · /reject | 守卫：步骤未操作/越权（姓名弱校验）/自审拦截；末步通过整单 approved |
| F-A6 | 审批流查询 | GET /team/approval/steps · /queryById | 步骤链时间线数据 |
| F-A7 | 全量列表 | GET /team/approval/list | QueryGenerator 分页，tab 过滤 |
| F-A8 | 审计追溯 | GET /team/audit/list | bizType=approval 过滤审批动作留痕 |

### 1.3 明确不做（本期）

- 撤销（cancel）/驳回后重提（resubmit）：产品语义待宿拍板（挂起项 ⑥）
- 人员选择器真实用户绑定：等 admin 线（#4/#5）导出接口，届时 `team_approval_step.approver_id` 加列升级
- 表单字段 JSON 动态渲染：`form_fields` 列已预留，前端仍为固定表单（标题/事由/日期）
- 附件上传：UI 占位，等 MinIO 拍板（docs/16 资料中心同批）

## 二、数据模型

### 2.1 team_form_template（V3.9.6_5，新增）

| 列 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(36) PK | 主键 |
| code | VARCHAR(32) UNIQUE | 模板编码（leave/expense） |
| name | VARCHAR(64) | 模板名称（页面展示） |
| group_name | VARCHAR(32) | 左侧导航分组 |
| category | VARCHAR(32) | 申请类别（落 team_approval.category） |
| form_tip | VARCHAR(256) | 表单页提示语 |
| form_fields | JSON NULL | 表单字段配置（试点预留） |
| step_template | JSON | 默认审批步骤模板：[{approverName, approverRole}] |
| enabled | TINYINT(1) DEFAULT 1 | 启用开关 |
| sort_no | INT DEFAULT 0 | 排序 |
| create_by/create_time/update_by/update_time | | jeecg 四字段 |
| sys_org_code / sys_tenant_id | | 租户/部门 |

种子模板：请假申请（部门主管 贺致远 → 副总经理 黄茜茜）、费用报销（同链）。

### 2.2 team_approval 变更（V3.9.6_5，ALTER）

- 新增 `template_id VARCHAR(36) NULL`：记录所用模板，便于追溯与统计。

## 三、接口文档

统一约定：返回 `Result<T>`（code=200 成功）；分页参数 pageNo/pageSize；登录态由 Shiro 提供。

### 3.1 GET /team/approval/template/list — 模板列表

响应 `Result<List<TeamFormTemplate>>`，仅 enabled=1，sort_no asc。

### 3.2 POST /team/approval/submit — 提交申请

请求体：
```json
{
  "templateId": "模板ID（可选）",
  "approval": { "category": "加班申请", "title": "...", "reason": "...",
                "startDate": "2026-11-20", "endDate": "2026-11-21" },
  "steps": [ { "approverName": "贺致远", "approverRole": "部门主管" } ]
}
```
规则：`steps` 非空时直接使用；为空且 `templateId` 有效时取模板默认步骤链；两者皆空报错。
服务端行为：生成单号 OA-yyyyMMdd-NNN、applicant 取登录人、校验 startDate≤endDate、步骤审批人非空；写审计 SUBMIT。

### 3.3 GET /team/approval/todo — 待我审批（分页）

参数：pageNo（默认1）、pageSize（默认10）。
响应 `Result<IPage<TeamApproval>>`。
已知限制：步骤表只冗余审批人姓名，重名者互见待办；严格身份待 approver_id 列。

### 3.4 GET /team/approval/my — 我的申请（分页）

参数：pageNo、pageSize、status（可选，pending/approved/rejected）。
applicantId 服务端强制取登录人，不可指定他人。

### 3.5 POST /team/approval/approve · /reject

参数：id（单据ID）、comment（可选）。
守卫失败返回业务错误码与上下文文案（步骤已操作/越权/自审）。
审计：APPROVE/REJECT，from/to 用 step:N 快照，末步通过 to=approved。

### 3.6 GET /team/approval/steps · /queryById

steps：步骤链（stepNo asc）；queryById：单据详情。

### 3.7 GET /team/approval/list — 全量分页

QueryGenerator 字段过滤（status/category/applicantId 等），create_time desc。

### 3.8 GET /team/audit/list — 审计追溯

QueryGenerator 分页；审批留痕过滤：`bizType=approval`；单据追溯：`bizId=<id>`。

## 四、状态机口径

pending(currentStep=N) → 当前步骤 approve → 末步 approved / 非末步 currentStep=N+1；
任一步 reject → 整单 rejected（终态）。同步骤重复操作、申请人自审、非本步骤审批人操作一律拒绝。
流转全量落 team_audit_log（只追加）。

## 五、验收要点

1. 模板卡片（请假/报销）端到端：选模板 → 提交（无 steps）→ 默认链落库 → 审批人可见待办（/todo）
2. 守卫回归：双击重复 approve 被拒；申请人自审被拒；非审批人操作被拒
3. 流转记录：/team/audit/list?bizType=approval&bizId=<id> 可查 submit→approve 全链
4. 我的申请：/my 只含本人单据，tab 过滤正确
