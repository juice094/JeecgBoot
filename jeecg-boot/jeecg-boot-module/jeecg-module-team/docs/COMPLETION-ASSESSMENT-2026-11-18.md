# 历史任务计划完成度评估（2026-11-18 收尾复核）

> 评估对象：OA 申请表分类与流转全工作线（审批 v2 + 字段模板 + 邮件查件增强 + 前端配套）。
> 对照基准：TEAM-APPROVAL-GAP-2026-11-18（五域六方面差分，含 P0/P1/P2 清单）、
> TEAM-APPROVAL-PATCH-2026-11-18（契约修订 + 待拍板 4 项 + 下一阶段三优先）、STAGE-WRAPUP-2026-11-18。
> 评估方式：文档对照 + 代码在盘核对 + 本轮复跑验证。**不是只信前序 agent 的自述。**

## 0. 本轮收尾复核结论（新增验证）

| 项 | 命令 | 结果 |
|---|---|---|
| team 模块编译 | `mvn -o -q test-compile` | exit 0 |
| mail 模块测试 | `mvn -o test -DskipTests=false` | **97/97 通过**（复跑确认） |
| 审批状态机自测 | java -cp 直跑 | OK |
| 待办匹配自测 | 同上 | 7 断言通过 |
| 字段校验器自测 | 同上 | 13 断言通过（surefire 不收集 main 式 SelfTest，须直跑） |
| 后端全模块编译 | `mvn -o -DskipTests package -pl jeecg-system-start -am` | 全 reactor 编译通过；repackage 失败仅为**在役 jar 被 8081 进程文件锁占用**（环境性，非代码问题） |
| 前端类型 | `pnpm typecheck` | exit 0 |
| 前端构建 | `pnpm build` | exit 0，1.10s |

**存量失败（与本次未提交改动无关，不阻塞提交）**：

| 失败类 | 根因 | 判定 |
|---|---|---|
| TeamTaskControllerGuardTest（1 error） | NPE：手工 new 的 controller 未注入 auditLogger（TeamTaskController.java 与测试均未被本轮改动触碰） | 存量，M3 引入 auditLogger 时未同步该测试 |
| TeamStatusFlowTransactionTest（2 errors） | Spring TestConfig 缺 ITeamTaskCommentService bean（TeamTaskServiceImpl 依赖评论服务，测试配置未注册；TeamTaskCommentServiceImpl 本轮仅改注入 mapper，不改变此结论） | 存量，TestConfig 组件扫描不完整 |

## 1. 对最初框架的完成度（五域 + 流转六方面）

### 1.1 表结构五域

| 域 | 状态 | 依据 |
|---|---|---|
| 表单模板 | **已完成**（本轮） | form_data JSON + amount DECIMAL(12,2) + 索引；field_schema JSON；ApprovalFormValidator（后端不信任前端）；GET /template/schema；RequestForm 按 schema 动态渲染；种子 SQL 三类别 |
| 流程实例 | 已完成 | 6 态 TeamApprovalStatus（isTerminal/canTransitTo/fromCode）；模板优先生成审批链；条件更新并发控制 |
| 审批动作 | 基本完成 | approver_id（ID 优先姓名兜底）；通过/拒绝/退回/撤回/重提五动作 + 审计枚举同步 |
| 协同 | 部分完成 | 评论体系在（TeamTaskCommentServiceImpl 本轮解循环依赖改直注 mapper）；抄送（step_type）未做 |
| 通知 | 部分完成 | OA 邮件模块系统发信全量落地（97 测试）；**审批单事件 → 邮件通知的联动未落地**，GAP 报告通知域缺位只解决了一半 |

### 1.2 流转六方面

| 方面 | 状态 |
|---|---|
| 发起填单 | 完成（字段模板后含结构化字段 + 必填/范围/枚举校验） |
| 节点分配 | 完成（类别模板，至多一条启用，唯一键 (tenant, category)） |
| 多人决策 | **预留未生效**（sign_type 列已建，代码一律按 SEQ；会签/或签未做） |
| 路由分支 | 未做（条件分支表达式 P2，维持搁置） |
| 异常干预 | 完成（退回/撤回/重提 + 并发冲突防护，从 check-then-act 改条件更新） |
| 权限归档 | 部分（越权校验 approverId 只能查自己、审批人身份校验已做；单据归档未做） |

## 2. 对 GAP 报告 P0/P1/P2 逐项销账

| GAP 项 | 优先级 | 状态 |
|---|---|---|
| team_approval_template / _step | P0 | ✅ 已落地 |
| 状态枚举类 | P1 | ✅ 已落地 |
| 状态集合扩 6 态 | P0 | ✅ 已落地 |
| 审批链来源改模板优先（防伪造 steps） | P0 | ✅ 已落地（含审批链不含申请人校验） |
| approver_id 列 | P0 | ✅ 已落地（列+越权校验+todo 匹配） |
| 退回标记 | P0 | ✅ 已落地（/return + returned 态 + 重提） |
| form_fields 结构化字段 | P1 | ✅ 已落地（本轮字段模板） |
| 类别字典 | P1 | ⚠️ 半（种子数据有类别，category 仍是自由文本、无字典表约束） |
| sign_type 会签/或签 | P1 | ❌ 列已建，代码按 SEQ，未生效 |
| step_type 审批/抄送/办理 | P1 | ❌ 未做 |
| 条件分支表达式 | P2 | ❌ 未做（维持搁置合理） |

## 3. 待拍板 4 项（PATCH §4）销账

| # | 事项 | 状态 |
|---|---|---|
| 1 | 字段模板方案（JSON form_data + amount 单列） | ✅ 已按该方案落地，无需再拍 |
| 2 | DDL 取号 | 半：邮件 _8/_9 已取号并移入 flyway 官方目录；**team_approval_v2 仍 DRAFT（暂定 _10），待 lead 取号** |
| 3 | 邮件 B-3 详情（正文 FETCH） | ❌ 未做（第三优先按需，维持） |
| 4 | sign_type 生效 | ❌ 未做 |

## 4. 下一阶段三优先销账

| 优先 | 内容 | 状态 |
|---|---|---|
| 第一优先 | 字段模板 | ✅ **完成**（数据结构→校验器→submit 接入→schema 端点→前端动态渲染→种子数据→文档，全链路在盘且本轮复核通过） |
| 第二优先 | 运行时验证（DDL 实跑 / 前端起服务点页面 / approverId OR 优先级） | ❌ 未做。本轮只补到「编译+单测+构建」层；DDL 实跑受试点环境 Flyway 手动执行约束，需环境窗口 |
| 第三优先 | B-3 FETCH / 会签 / 单号并发发号 / todo 内存分页下沉 | ❌ 未做（按需，维持） |

## 5. 一句话结论

最初框架的 P0 已 100% 销账，价值最高的字段模板（P1）也已闭环；
剩余缺口集中在：**DDL 实跑与运行时验证（第二优先整块）**、多人决策（会签/或签）、通知联动、抄送/归档，以及两个存量测试失败待修。
建议下一会话直取「第二优先运行时验证」——它是当前唯一一块「代码完成但未在真实环境跑过」的风险敞口。
