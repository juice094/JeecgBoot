# docs — jeecg-team-web 设计文档

从 `.refs/` 参考件与 jeecg-module-team 后端实际代码提取/推导的设计文档。

| 文件 | 回答什么 | 事实级别 |
|---|---|---|
| [sql-schema.md](sql-schema.md) | 有哪些表、字段、索引；多标签等演进 DDL | 现状=已落库；演进=未执行待拍 |
| [api-spec.md](api-spec.md) | /team/task/** 端点、参数、权限、错误码 | 在役契约（提取自 Controller 代码） |
| [backend-pseudocode.md](backend-pseudocode.md) | 每个操作的校验顺序/事务边界/埋点时机 | 与 Java 实现逐方法对照 |
| [business-flow.md](business-flow.md) | 状态机、时序、事件流机制、权限矩阵 | 设计事实 + 演进路径 |

外部参考（GitHub Issues 长什么样）见 [../.refs/README.md](../.refs/README.md)。

约定：文档与代码冲突时以代码为准；发现漂移请改文档而不是改记忆。
