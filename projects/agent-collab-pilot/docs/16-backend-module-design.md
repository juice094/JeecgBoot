# 16 — 后端业务模块设计（随原型复现落地，2026-11-17）

> 依据：原型菜单树（宿 2026-11-17 裁定完整复现）+ 分工裁决（管理组后端交 Ym3699，业务/工具组 Kimi+juice094）。
> 实现载体：`jeecg-boot-module/jeecg-module-team`（org.jeecg.modules.team），Flyway 自动建表。

## 一、页面 ↔ 后端映射总表

| 组 | 页面 | 前端 owner | 后端策略 | 状态 |
|---|---|---|---|---|
| 概览 | 仪表盘 | juice094 | **复用**：聚合 sys 公告/消息/审批/任务计数，单聚合接口待建 | 前端复现✅ / 聚合接口待建 |
| 管理 | 人事管理 | Ym3699 | **复用**：sys_user/sys_depart（唯一数据源，Ym3699 出口） | mock，后端归 Ym3699 |
| 管理 | 行政管理(固定资产) | Ym3699 | 自研 CRUD（表设计由 Ym3699 定） | mock |
| 管理 | 教务管理(排课) | Ym3699 | 自研 CRUD（学校业务，Ym3699 定优先级） | mock |
| 管理 | 后勤管理(报修) | Ym3699 | 自研 CRUD（复用审批状态机思想） | mock |
| 业务 | 市场活动 | juice094 | 自研 CRUD（表设计随需求定，暂 mock） | mock |
| 业务 | 职业教育(培训班) | juice094 | 自研 CRUD（同上） | mock |
| 业务 | 学生服务(学籍异动) | juice094 | **复用审批状态机**：异动=审批单的一种 category | 建议接入 team_approval |
| 工具 | 日常申请 | juice094 | **自研已实现**：team_approval + team_approval_step 轻量状态机 | 接口✅（`/team/approval/**`） |
| 工具 | 审批中心 | juice094 | 同上 + tab 过滤（pending/approved/rejected） | 接口✅ |
| 工具 | 工作邮箱 | juice094 | **后置**：SMTP/外部邮箱集成（M2+），暂 mock | 待立项 |
| 工具 | 资料中心 | juice094 | **复用**：MinIO（M2）+ jeecg 附件能力；先上文件元数据 CRUD | 表设计待 MinIO 拍板 |
| 工具 | Bug 反馈 | juice094 | **自研已实现**：team_bug CRUD + 状态流转 | 接口✅（`/team/bug/**`） |
| 工具 | 个人报表 | juice094 | **复用**：JimuReport（jimureport-spring-boot4-starter 已在 jeecg-system-biz） | 已接入✅（iframe `/jeecg-boot/jmreport/list?token=`） |
| （底栏） | 会话工作台 | qingyanjunzi | Mattermost 集成（docs/13 §一） | 待 M2 |

## 二、自研表（team schema，Flyway 已配）

| 表 | 用途 | 脚本 |
|---|---|---|
| team_task | 任务协作（M3，API 已可用） | V3.9.6_2 |
| team_approval | 审批单（轻量状态机主表） | V3.9.6_3 |
| team_approval_step | 审批步骤链（wait/approved/rejected） | V3.9.6_3 |
| team_bug | Bug 反馈 + 状态流转 open/processing/resolved/closed | V3.9.6_3 |

状态机口径：提交(pending, currentStep=1) → 按步骤 approve → 末步通过=approved；任一步 reject=rejected。
单号 `OA-yyyyMMdd-NNN`（试点级生成，高并发换 Redis 发号，代码已标 ponytail 注释）。

## 三、复用 jeecg 能力（不重复造轮子）

- RBAC/组织/字典/菜单：sys_* 全家桶（管理组后端=Ym3699 的唯一出口原则）
- 公告/消息：sys_notice / sys_message（仪表盘聚合与"我的消息"直接用）
- 个人报表：JimuReport 设计器+列表（前端已嵌 `/jmreport/list`，token 透传）
- 接口文档：Knife4j 自动收录 `/team/**`（改动接口必须同步注解，docs/13 §二.5）

## 四、后续路线（建议）

1. juice094：前端 mock 接真接口（审批/bug 优先；业务组三页随需求定表）
2. Ym3699：管理组四页后端立项（固定资产/排课/报修，可复用 team_approval 状态机模式）
3. 仪表盘聚合接口：一次拉齐 待办数/公告/未读/任务数（减少首页 N 次往返）
4. 工作邮箱：M2 后与 SSO 一起评估（IMAP/SMTP vs 外部服务）
