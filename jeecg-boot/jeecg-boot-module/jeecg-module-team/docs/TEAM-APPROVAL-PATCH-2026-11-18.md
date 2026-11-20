# 申请表流转体系 —— 契约修订记录（2026-11-18）

> 对应文档：申请表流转体系接口文档（APPROVAL-FLOW-API-001）、申请表流转体系需求文档（APPROVAL-FLOW-REQ-001）。
> 本文记录 v2 落地后与原文档的差异；原文档请按本记录合并后升版 v1.1.0。

## 0. 背景与定性

原需求文档 §1 明确「不上 Flowable」，§2「本期不包含」列了模板表、cancel/resubmit、approver_id 三项。

v2 实现已落地这三项。**定性：迭代轮次差异，不是路线分歧。** Flowable 决策不变，继续搁置；三项能力作为「三期提前落地」追认。

## 1. 已落地但原文档标注「不包含」的项

| 原文档表述 | 实际状态 |
|---|---|
| §2「表单模板表（JSON 配置化字段）：延后（D-20261117-68）」 | 已落地**审批链模板**（team_approval_template / _step）；**字段模板仍未做** |
| §2「撤销（cancel）/驳回后重提（resubmit）：产品语义待宿拍板」 | 已落地 /cancel、/return、/resubmit 三端点 |
| §2「审批人严格身份校验（approver_id 列）：待后续 Flyway 加列升级」 | 已落地 approver_id 列；校验与 /todo 均改为 ID 优先、姓名兜底 |

> **注意区分**：已落地的是「谁审批」（审批链模板），**不是**「申请单长什么样」（字段模板）。费用报销的金额、发票张数等结构化字段仍未做，属独立议题。

## 2. 接口契约变更

### 2.1 状态集合扩展（P0）

- 原：pending / approved / rejected（3 态）
- 现：draft / pending / returned / approved / rejected / canceled（6 态）
- 枚举：TeamApprovalStatus，含 isTerminal() / canTransitTo() / fromCode()
- 前端 api/team.ts 的 Approval['status'] 已同步；Dashboard.vue、issue.ts 的 Record 映射同步扩至 6 态

**流转口径**：

- draft → pending（submit）
- pending → approved（末步）/ rejected（reject）/ returned（return）/ canceled（cancel）
- returned → pending（resubmit）/ canceled（cancel）
- approved / rejected / canceled 为终态

### 2.2 /team/approval/submit 规则变更

- 原：「steps 非空必填（模板成分延后）」
- 现：**模板优先**。按 category 查启用的 team_approval_template 生成审批链；模板缺失时回退到 body.steps；两者皆无则报错
- 新增校验：**审批链中不得包含申请人本人**（按 ID 优先，姓名兜底）

### 2.3 新增端点

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | /team/approval/return?id=&comment= | 审批人退回至申请人，状态转 returned | team:approval:approve |
| POST | /team/approval/cancel?id= | 申请人撤回，仅 pending 且所有步骤均 wait 时允许 | team:approval:submit |
| POST | /team/approval/resubmit | 申请人重提，仅 returned；body { id, steps? } | team:approval:submit |

### 2.4 /team/approval/list 新增参数

- 新增 approverId：按审批人过滤（**只能查自己**，传他人 ID 返回「无权按他人身份查询已办单据」）
- 用途：前端「我已审批」tab。原实现是 filter(status !== 'pending')，拿的是全库数据，与「我」无关，属功能级缺陷

### 2.5 /team/approval/todo 匹配口径

- 原：仅 approver_name = 登录人姓名
- 现：approver_id = 登录人ID **OR**（approver_id IS NULL AND approver_name = 姓名）
- 原文档 §3「已知限制：重名者会互相看到对方待办」**对新建数据已消除**；历史数据（approver_id 为空）仍走姓名兜底

### 2.6 并发控制（新增）

- 步骤推进：UPDATE ... WHERE id=? AND action='wait'，影响行数 0 即抛「并发冲突」
- 主表推进：UPDATE ... WHERE id=? AND status=? AND current_step=?
- 原实现是 check-then-act，双击或并发审批可连推两步

## 3. 数据结构变更

见 docs/sql/V3.9.6_10__team_approval_v2.draft.sql（**DRAFT，待 lead 取号**）。

| 变更 | 说明 |
|---|---|
| team_approval_step + approver_id | 可空，历史行按姓名兜底 |
| team_approval_step + idx_step_approver_wait | (approver_id, action) |
| 新建 team_approval_template | 每类别至多一条启用模板，唯一键 (sys_tenant_id, category) |
| 新建 team_approval_template_step | 含 sign_type，**本期仅 SEQ 生效**，ALL/ANY 预留 |

**编号说明**：V3.9.6_7 被 team_issue_detail 占用；_8/_9 由 OA 邮件体系暂定；本脚本暂定 _10。

**落地方式**：取号后改名为 V3.9.6_<号>__team_approval_v2.sql，移入 jeecg-system-start/src/main/resources/flyway/sql/mysql/。试点环境 Flyway 自动装配被排除，需手动执行并登记 flyway_schema_history。

## 4. 原文档需修正的具体缺陷

| # | 位置 | 问题 | 修正 |
|---|---|---|---|
| 1 | 需求 §4.3 | 「team_approval 含 sys_org_code / sys_tenant_id」 | **保留**。此前我据 JeecgEntity 基类字段判定为错，实为误判——模块 DDL 约定所有 team 表带这两列，实体不声明是既有写法 |
| 2 | 需求 §6 | 「Spring Boot 4.1」 | 与 pom.xml 的 parent jeecg-boot-module 3.9.6 不符，需核实后修正 |
| 3 | 需求 §4.4 | 「新表走 Flyway 脚本 V3.9.6_x」 | 属实，但脚本实际存放于 jeecg-system-start（工作区外），team 模块无 resources 目录 |
| 4 | 接口 §5 | 「/team/approval/my（无此端点）」 | 接口文档不应标注不存在的端点，删除该行 |
| 5 | 需求 §6 | 「待我审批两步走均走索引」 | 实际是**内存分页**（matched.subList），文档美化现实，应改为「索引查询 + 内存分页（试点量级）」 |
| 6 | 需求 §4.3 | 审计 action 枚举 create/submit/change_status/comment/approve/reject | 已新增 return / cancel / resubmit，需同步 |
| 7 | 需求 §4.3 / 接口 §4 | 状态机仅有文字描述 | 建议补状态图（6 态后纯文字较难读） |

## 4.5 字段模板（本轮新增）

### 背景

此前的 team_approval_template 只解决「谁审批」（审批链），不解决「申请单长什么样」。用户只能在申请事由手写「报销 3800 元」，后端拿不到结构化金额。

### 数据结构

| 变更 | 说明 |
|---|---|
| team_approval + form_data JSON | 类别专属字段值 |
| team_approval + amount DECIMAL(12,2) | 从 form_data.amount 提取，唯一需范围查询/聚合的字段，单独成列并建索引 |
| team_approval_template + field_schema JSON | 字段契约数组 |

设计取舍：类别专属字段走 JSON（一次改表无限扩展，审批单是「存整条读整条」场景，性能与普通列几乎无差）；金额单独成列（JSON 里查会全表扫描）。

### field_schema 元素结构

- key：字段标识，与 form_data 键一致
- label：前端显示名
- type：text / textarea / number / date / select / multiselect
- required：是否必填
- options：select/multiselect 候选值
- min/max：number 取值范围

### 校验口径

**前端与后端各校验一次，后端不信任前端。**

ApprovalFormValidator 负责：必填、类型、数值范围、枚举合法性、日期格式、长度上限；schema 外的多余键被丢弃；amount 提取为 BigDecimal。

模板未配 field_schema 时跳过校验（向后兼容），form_data 原样透传。

### 新增端点

GET /team/approval/template/schema?category=xxx

返回启用的模板（含 fieldSchema）；无模板或未配字段时返回 null，前端回退到通用表单。权限 team:approval:submit。

### 种子数据

docs/sql/V3.9.6_10__team_approval_seed.draft.sql 提供三个示例：

| 类别 | 字段 |
|---|---|
| 费用报销 | 金额（必填, 0~1000000）、费用类型（必填, 4 选项）、发票张数 |
| 采购申请 | 金额（必填）、物品名称（必填）、供应商、数量 |
| 加班申请 | 加班类型（必填, 3 选项）、时长（必填, 0.5~24）|

**注意**：种子数据 approver_id 为 NULL，走姓名兜底；正式上线前需回填真实 sys_user.id，否则重名者仍会互串。

## 5. 验证状态

**已验证**

- 后端：mvn -o -q test-compile exit 0（模块目录内执行）
- 前端：pnpm typecheck（vue-tsc --noEmit）exit 0
- 纯 Java 自测：TeamApprovalStatusSelfTest（状态矩阵）、ApprovalTodoMatcherSelfTest（既有 7 断言）均通过
- ApprovalFormValidatorSelfTest：13 条断言通过（必填、类型、范围、枚举、日期、多余键丢弃、amount 提取、无 schema 透传）

**未覆盖**

- DDL 未在 MySQL 实跑（ALTER TABLE 与索引创建未验证）
- /list 的 approverId 分支无集成测试，SQL 层 OR 优先级未在真实库验证
- Spring 上下文未启动验证（新增 Service、两个 Mapper 按 @MapperScan 推断可注册）
- 三个新端点未跑 HTTP，鉴权与序列化未验证
- 前端未起服务点过页面（pnpm typecheck 与 pnpm build 已于 2026-11-18 收尾复核通过）

## 6. 仍挂起项

1. ~~字段模板~~：**已落地**（见 §4.5：form_data/amount 列、field_schema、ApprovalFormValidator、/template/schema、种子 SQL、前端动态渲染），此项挂起关闭
2. sign_type 仅落库未生效，代码一律按 SEQ 执行
3. 单号并发：count+1 生成，高并发必冲突（代码已标 ponytail）
4. todo 内存分页：数据量上行前需下沉到 SQL
5. Flowable：继续搁置，ITeamApprovalService 作为未来替换接口层
