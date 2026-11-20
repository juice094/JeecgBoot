# bizflow-dev 会话收尾报告（2026-11-17）

> 本会话（dsh teammate bizflow-dev）上下文已接近上限，进入休耕状态。
> **请非必要不开启本会话继续工作**；后续任务请由宿/lead 新开会话或走 inbox 派达。
> 本文是全量交接：成果清单、验证状态、未决项、环境坑、续作指引。

## 一、会话成果总览（按轮次）

| 轮次 | 交付 | 验证 |
|---|---|---|
| R1 | 缺口清单（五行字段级）；Bug 状态机（TeamBugStatus）；审批守卫（越权/自审/重复操作/日期区间） | 编译✅ 自测15断言✅ |
| R2 | 任务状态机+`/dueSoon`；update_by 回填；`/team/dashboard/summary`（team 三计数）；team_audit_log（V3.9.6_4+实体+logger+三处埋点） | 编译✅ 自测27断言✅ |
| R3 | `/approval/todo`（两步走）；`/task/board`；`/audit/list` | 编译✅ 自测44断言✅ |
| R4（方向变更） | 表单模板模块按宿裁定 D-20261117-68 整体摘除并恢复三轮基线（7 件隔离 hold/20261117-form-template-deferred/，自测隔离 hold/20261117-bizflow-quarantine/） | 编译✅ 四组自测回归✅ |
| R5 | 任务排期（共享板 task-4~14）；前端执行线全部落地：ApprovalCenter 切 /todo、RequestForm 真实审批链（PILOT_APPROVAL_STEPS）、TaskBoard.vue 新建（路由 tools/taskboard）、BugReport 列表+流转、Dashboard 三计数 | build✅ 冒烟✅ |
| R6 | task-7 Dashboard /summary；task-10 联调：六线回归全绿 + 守卫运行态实测（越权/自审/状态机边界） | 运行态✅ 20/20 测试✅ |
| R7 | 宿拍板三项：D-69a 逻辑删（V3.9.6_5+@TableLogic）、F-09（24 注解+TeamOwnershipGuard+V3.9.6_6 权限 SQL）、D-72 sys 聚合（summary 扩展未读+最新公告） | 编译✅ 20/20✅ 运行态✅ |
| R8 | 前端 UI 补全：Dashboard 公告接聚合、ApprovalCenter 步骤时间线、TaskBoard 删除+逾期高亮、RequestForm 单号回显 | build✅ 运行态✅ |
| R9 | JeecgBoot 后台服务 API 接口文档：参数注解补齐+总入口文档 | 编译✅ |

**共享板终态**：task-4~10 全绿完成；task-12（逻辑删）已落地待结板确认；task-13/14 仍 blocked。

## 二、未决项（等宿/lead，勿自行启动）

1. **task-13 resubmit/cancel**：宿未拍板语义，代码未动（拍板清单 ②）
2. **task-14 approver_id 严格化**：依赖 admin 线 issue #4/#5 人员选择器（拍板清单 ③子项）
3. **表单模板重启**：hold/20261117-form-template-deferred/ 7 件，须走 issue 立项（D-20261117-68）
4. **邮件体系立项**：docs/16 后置 M2+，设计稿已备好（OA 邮件体系两份文档 v1.1.0）
5. **8081 陈旧进程**：PID 14400（今 09:31 启动，classpath 混版，/summary /todo 报 NoClassDefFoundError）——需宿重启
6. **mail-dev 线注册**：F-09/SQL 已清障，可注册 jeecg-module-mail
7. **宿 review**：全部改动未提交（git 零操作），待统一 commit；hold/ 两目录待处置

## 三、环境坑（经验沉淀，新会话必读）

1. **mvn 必须用 JDK 17**：`mvn.cmd` 默认抓 pleiades JDK 25（`JAVA_HOME` env 被神秘覆盖，cmd set 也不生效）→ Lombok 编译挂。绕过：直接 java 启动 Maven classworlds：
   `& "$java" -cp <maven>/boot/plexus-classworlds-*.jar "-Dclassworlds.conf=...\m2.conf" "-Dmaven.home=..." "-Dmaven.multiModuleProjectDirectory=..." org.codehaus.plexus.classworlds.launcher.Launcher <goals>`
2. **spring-boot:run 不带 -am 时，模块代码从本地仓解析**：改完 team 模块必须先 `install -pl jeecg-boot-module/jeecg-module-team` 再起 system-start，否则跑旧 jar（白跑过一次完整回归）
3. **job_kill 只杀包装进程，forked JVM 成孤儿占端口**（8081/8082 都中过）：重启前 `Get-NetTCPConnection -LocalPort X` 查 PID 并杀孤儿
4. **spring-boot:run 多参数**：`spring-boot.run.arguments` 用**空格**分隔（逗号会把整串当一个值）；验证码开关 `jeecg.firewall.enableLoginCaptcha` 命令行覆盖实测不生效，联调用 Redis 读码绕过（KEYS `md5(checkKey+signatureSecret)*`，value 是 captcha 文本带 JSON 引号需剥掉）
5. **.NET `WriteAllText` 默认带 BOM** → javac 报 `非法字符 '\ufeff'`；且 pwsh 单引号串里 `` `n`` 是字面量（多次踩），写文件用 `(New-Object System.Text.UTF8Encoding($false))`
6. **edit 工具对 jeecg-team-web 目录偶发 EIO**（文件锁竞争）：改 .NET 原子写 + 读回核对
7. **dev 库联调**：MySQL80/Redis 均为 Windows 服务常开；登录 admin/123456、jeecg/123456、ceshi/123456（无 team 权限，510 验证用）；zhangsan 密码未知
8. **Flyway 试点口径**：脚本放 jeecg-module-system/.../flyway/sql/mysql/（编号顺延，_4/_5/_6 已用）；本次实例 Flyway 自动装配实际生效（3.9.6.4 自动应用），但宿要求"手动执行+登记 flyway_schema_history"的老纪律仍按派工执行

## 四、续作指引（新会话开局）

1. 读本文 + `projects/agent-collab-pilot/docs/`（13/16/19-22 + 三组体系文档 + JeecgBoot 后台服务API接口文档.md）
2. `git status` 全量变更未提交——宿 review 前不要 commit；禁区：pom.xml、SidebarMenu.vue（宿主在途）
3. 待办按第二节优先级接；任何重启后端动作先查端口孤儿
4. 团队规矩： Knife4j 注解随码、Result<T>、状态机枚举唯一来源、审计只追加、git 零操作

## 五、动过文件汇总（未提交，宿 review 用）

- 后端 jeecg-module-team：controller×5、service/impl×8、entity×4（含 TeamAuditLog/TeamFormTemplate 已在 hold）、mapper×2、enums×5、vo×2、TeamOwnershipGuard/TeamAuditLogger/ApprovalTodoMatcher、test×9（含线 A 4 JUnit 类 + 我 5 用例 + 4 纯 Java 自测）
- Flyway：V3.9.6_4/_5/_6（_5 模板版已隔离，现行 _5 为逻辑删）
- 前端 jeecg-team-web：api/team.ts、constants/bug.ts、views/RequestForm/ApprovalCenter/BugReport/TaskBoard、views/dashboard/Dashboard.vue
- docs：三组体系文档×6、JeecgBoot 后台服务API接口文档.md、OA 邮件体系×2（v1.1.0）
- 隔离：projects/agent-collab-pilot/hold/20261117-form-template-deferred/（7）、hold/20261117-bizflow-quarantine/（1）
