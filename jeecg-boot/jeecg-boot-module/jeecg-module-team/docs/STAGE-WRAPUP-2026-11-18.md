# 阶段性收尾报告（2026-11-18）

> 覆盖：申请表流转 v2、审批中心前端修复、邮件查件增强。
> 关联：TEAM-APPROVAL-GAP-2026-11-18.md（差分）、TEAM-APPROVAL-PATCH-2026-11-18.md（契约修订）。

## 1. 起点与判定

最初框架分两块：

- 表结构五域：表单模板 → 流程实例 → 审批动作 → 协同 → 通知
- 流转六方面：发起填单、节点分配、多人决策、路由分支、异常干预、权限归档

初始判定：现有实现只命中四点（发起、依次审批、拒绝终止、留痕），其余为空。

**重要澄清**：已落地的 team_approval_template 解决「谁审批」（审批链），**不解决「申请单长什么样」（字段模板）**。后者至今零进展。

## 2. 已完成清单

### 2.1 审批后端 v2

| 项 | 内容 |
|---|---|
| 状态枚举 | 新增 TeamApprovalStatus：draft/pending/returned/approved/rejected/canceled |
| 审批链模板 | 新增 team_approval_template / _step 两张表 + 实体 + Mapper + Service |
| 提交规则 | 模板优先，缺失回退客户端 steps；新增「审批链不含申请人」校验 |
| approver_id | step 表补列；越权校验与 /todo 改 ID 优先、姓名兜底 |
| 三新端点 | /return（退回）、/cancel（撤回）、/resubmit（重提） |
| 并发控制 | 步骤与主表均改条件更新，影响行数 0 即抛并发冲突 |

### 2.2 审批前端

| 项 | 内容 |
|---|---|
| 白屏止血 | STATUS_TAG / STEP_ACTION 扩至六态/四态 + 兜底函数，未来加状态不会崩 |
| 我已审批 | 原为本地 filter 全库数据，改服务端 approverId 过滤（后端配套新增越权拦截） |
| 按钮矩阵 | 待我审批：通过/拒绝/退回；我发起：pending 撤回、returned 重提 |
| 日常申请页 | 类别文案随切换、原生日期控件、必填校验、附件如实置灰、成功可跳转 |
| 仪表盘 | Dashboard.vue 三处 Record 同步扩至六态（类型检查发现） |

### 2.3 邮件查件增强

| 项 | 内容 |
|---|---|
| 后端 | pageMessages 新增 keyword（主题 LIKE，上限 100 字符）+ unreadOnly |
| 控制器 | /messages 暴露两个新参数 |
| 前端 | 工具栏加搜索框（300ms 防抖、清空按钮）+「仅未读」切换 |
| 测试 | 新增 4 个用例：LIKE+read_flag 生效、无参不追加条件、超长关键字拒绝、空 accountId 拒绝 |

### 2.4 工程收口

| 项 | 内容 |
|---|---|
| DDL 归位 | docs/sql/V3.9.6_10__team_approval_v2.draft.sql，按项目 Flyway 命名与字段风格 |
| 文档 | TEAM-APPROVAL-PATCH-2026-11-18.md（契约修订 + 原文档 7 处缺陷） |
| 纠错 | 撤回上轮「删掉 sys_org_code」的误判（模块约定所有 team 表带该列，实体不声明是既有写法） |

## 3. 验证状态

### 已验证

| 项 | 命令 | 结果 |
|---|---|---|
| 审批后端编译 | mvn -o -q test-compile（team 模块目录） | exit 0 |
| 审批状态机 | TeamApprovalStatusSelfTest | 通过 |
| 审批待办匹配 | ApprovalTodoMatcherSelfTest | 7 断言通过 |
| 邮件模块测试 | mvn -o test -DskipTests=false -Dmaven.test.skip=false | **97/97 通过** |
| 前端类型 | pnpm typecheck（vue-tsc --noEmit） | exit 0 |

### 未覆盖（如实标注）

| 项 | 风险 |
|---|---|
| DDL 未在 MySQL 执行 | ALTER TABLE 与索引创建未验证，换环境部署可能失败 |
| 前端未起服务实测 | 按钮交互、搜索防抖、confirm 时机仅类型层面通过 |
| /list 的 approverId SQL OR 优先级 | 未在真实库验证，写错会导致越权或漏查 |
| Spring 上下文未启动 | 新 Service 与两个 Mapper 按 @MapperScan 推断可注册，未实测 |
| 三个新端点未跑 HTTP | 鉴权与序列化未验证 |

### 过程中排除的三个假失败

1. 仓库根执行 -pl jeecg-module-team 失败：工作区根非 Maven reactor 根，须在模块目录直接编译
2. java -cp target/classes:target/test-classes 报 NoClassDefFoundError：Windows 类路径分隔符应为分号，且需带依赖
3. mvn -q test 显示 BUILD SUCCESS 但测试未跑：环境 skipTests=true 且时间戳暴露报告是旧的；须显式 -DskipTests=false

## 4. 待拍板事项

| # | 事项 | 建议 |
|---|---|---|
| 1 | 字段模板方案 | JSON form_data + 单独 amount DECIMAL 列。理由：JSON 在「存整条读整条」场景性能几乎无差；EAV 的 JOIN 成本不划算；金额是唯一需范围查询与聚合的字段 |
| 2 | DDL 取号 | _10 为暂定，邮件占 _8/_9，需 lead 统一分配避免撞号 |
| 3 | 邮件 B-3 详情 | 查件当前只到索引级，正文 FETCH 未做 |
| 4 | sign_type 会签/或签 | 列已建，代码一律按 SEQ 执行，未生效 |

## 5. 下一阶段计划

### 第一优先：字段模板（最初框架里价值最高、仍未动的部分）

现状痛点：用户只能在「申请事由」手写「报销 3800 元」，后端拿不到结构化金额，做不了金额分级审批、统计、财务对接。

拟做：
1. team_approval 加 form_data JSON + amount DECIMAL(12,2)
2. team_approval_template 加 field_schema JSON
3. 后端 submit 按 schema 校验（不只信前端）
4. 前端 RequestForm 按 schema 动态渲染

### 第二优先：补齐运行时验证

1. DDL 在 MySQL 实跑
2. 前端起 dev server 点一遍两个页面
3. /list 的 approverId 在真实库验证 OR 优先级

### 第三优先（按需）

邮件 B-3 详情 FETCH、sign_type 会签、单号并发发号、todo 内存分页下沉。

## 6. 一句话结论

框架已立、P0 已落、邮件查件打通、97 测试全绿；但验证面仍偏窄（DDL 与页面均未运行时验证），且最初最有价值的字段模板仍是空白。建议下一步直取字段模板。
