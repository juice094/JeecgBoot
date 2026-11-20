# 27 · Flowable 流程域立项草案（PROJECT-CHARTER）

> 状态：立项草案 v1（2026-11-19）· 待宿正式拍板（"大概率立项"口径）
> 上游：23 号业务契约（明确"状态机可 later 迁移 Flowable"）、26 号治理基座（身份底座唯一原则）
> 触发：宿 2026-11-19 "激进路线"裁定（越快进入工程阶段，时间成本越少）

## 一、尖刺实证（已闭合，不再重复验证）

| 问题 | 答案 | 证据 |
|---|---|---|
| fork 现状 | **零流程引擎**；上游 jeecg-boot-platform（含官方 BPM 集成）已被裁 | 全仓 pom 无 flowable 引用；在役 jar 无 flowable/activiti 类 |
| Boot 4.1 兼容性 | **Flowable 8.0.0 = Boot 4 线**（7.x 全线 Boot 3，预设"7.1.x 起"已证误） | 官方 release note"based on Boot 4, no Boot 3 support" + flowable-dependencies POM spring.boot.version=4.0.2 |
| 能否在本 fork 启动 | **能**：dev 库已自动建表 39 张 act_*（历史/通用/属性族） | information_schema 实测（agent-4 启动冒烟的免费残留证据，8083 已关停） |
| REST 端到端 | 未验（中断裁定：边际效应不足，并入 W1） | — |

尖刺产物：jeecg-module-demo 下 pom+三件套（参考实现，W1 决定移植或重建）；agent-collab-pilot/docs《Flowable 工程化实战.md》。

## 二、立项决策（显式记录，非静默替换）

**反转 23 号文档/docs-11 "不上 Flowable" 口径**——原决策目标函数=试点量级最小化学习面；当前目标函数已变（多流程类型扩展 + agent 团队线 HITL 标准件 + 宿激进倾向）。按辩证纪律：成本时间维度成立（迁移成本凸函数，数据越多越贵），故现在切。

**范围裁决**：
- 迁：审批链（team 的 TeamApproval 二级链 → 流程定义驱动）；
- 迁：agent 高敏确认门（AGENT_CONFIRM_REQUIRED → userTask + delegate，治理基座 M4 增量④的落地位）;
- **不动**：任务看板状态机（协作单据非审批流程，Flowable 无增益）——此区分在辩证重判中已拍。

## 三、波次骨架（功能代码由其他 agent 线承接，本文件是交接面）

| 波 | 内容 | 出口条件 |
|---|---|---|
| W1 引擎落地 | 模块接线（pom 注册已就位，**system-start 接线是 W1 动作**——副作用=下次启动建 39 张表，需 dev 库确认）；flowable 参数外置；BPMN 部署方式决策（classpath vs 表）；demo 尖刺三件套移植或重建 | 8083 起单体四验证（复用尖刺口径） |
| W2 流程模板 | 请假/报销两条标准流程（userTask→主管→归档）；权限码 team:flow:* 入 sys_permission | 真实走通 + 冒烟报告 |
| W3 审批链迁移 | TeamApproval 二级链改由流程定义驱动；team 表保留单据语义、流转事件双写 audit_log 保持时间线口径 | 旧链路并行期回归零差异后切流 |
| W4 agent 门 | userTask 候选组=成员池角色；agent 办理人=sys_user 特殊类型；confirm-gate 命中→人工节点 | 与治理基座契约的端到端演练 |

## 四、开放决策（立项拍板时一并裁）

1. **schema 治理**：39 张 act_* 由引擎自管（现状，升级由 flowable 负责）还是导出 DDL 进 flyway（团队可控但跟版本辛苦）？建议 W1 先自管，首次版本升级时复审——把决策挂在触发器上而非现在拍死。
2. **流程定义版本化**：BPMN 文件进 git（推荐，定义即代码）+ 部署表只存运行态。
3. **异步执行器**：默认开（定时边界/超时提醒需要）；dev 可关减噪音，写进模块独立 yml。
4. demo 模块尖刺残留（pom 依赖+三件套）的去留：W1 移植进 jeecg-module-flow 后，demo 面还原（迁移不删除，git checkout 由功能线执行）。

## 五、风险登记

- R1 引擎税：40+ 表 + 异步执行器 + 流程版本治理的学习面（尖刺驾驭面初评因中断缺正式回报，W1 第一项补）；
- R2 双写期漂移：W3 并行期 team 状态机与引擎状态可能不一致——并行期以 team 表为准、引擎侧只观测，切流前零差异；
- R3 与微服务线关系：flow 模块天然是第三个可拆服务域（mail 首拆之后），schema 按域分离时 act_* 归 flow 域——登记进微服务规划 §1 目标架构。
