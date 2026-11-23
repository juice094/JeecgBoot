# 外部参考评估：inspector-mvp（宿问，2026-11-17）

> 性质：一次性评估留档（lead 口径：wrapup 已封口防 churn，本评估随施工记录链自存）。
> 结论：inspector-mvp（`C:\Users\22414\dev\projects\baoxian\inspector-mvp`）是"演示/编排/观察"三合一素材库，
> **对 mail/team OA 业务模块无直接参考价值**；两个间接参考点见下。

## 逐项判断

- 与 mail 设计哲学相反处（不搬）：RecordStore 内存环"重启即失" vs 发信日志持久化 first（重启补偿是硬验收）；
  EventBus 进程内 pub/sub vs 合同口径 pilot 无 MQ、DB 轮询+有界池。
- 同名异物：其 `team/` 包是 dsh agent-team 编制层复刻（名册/任务板 DAG/inbox/事件流/DemoRunner 替身），
  与 bizflow-dev 的 OA 团队业务模块零重叠。
- 间接参考点 1（Phase 2 候选排障手段）：JDI attach——不加日志/不改代码/不重启观察运行中 JVM；
  注册后可用于确认补偿任务/调度器实跑。环形 store 本身不用，思路可用。
- 间接参考点 2（非本仓模块）：agent 协作编排层的现成对照物（roster/任务板依赖边/inbox/工具面-策略面分离，
  含"实验性"叙事红线口径）。
- SSE 事件流（SseEmitter）范式：sync/status 将来若做实时感（B-3 解挂后）可作候选；当前不值得引入。

## 启用条件

真到 Phase 2 排障或二期可观测性（D-2）评审时，由 lead 决定是否把"JDI attach 思路"列入候选手段。
