# 阶段性收尾报告（2026-11-20）

> 执行：AI 编码助手（Kimi Code，主会话）
> 性质：单日多线施工的总收口。前五项各有专项报告（见 §1），本报告只做汇总、验证状态、遗留清单与交接。

## 1. 今日完成事项（按时间序）

| # | 事项 | 专项报告 | 状态 |
|---|---|---|---|
| 1 | 前端资产标注：jeecgboot-vue3 标记为待迁移代码资产，jeecg-team-web 为现行默认前端（三处 AGENTS.md） | —（直接改文档） | 完成 |
| 2 | 在线用户重复显示（admin×69）排查与修复：Redis 113 个堆积 token 清理、~~is-concurrent=true→false~~（同日因测试互踢改回 true，见下注）、分页硬编码修复、会话ID 列改显 token | `reports/online-user-dup-fix-20261120.md` | 完成，后端已在新构建生效 |
| 3 | /login 忘记密码：前端 MD5 签名（md5.ts/sign.ts 零依赖实现）、后端 dev 短信 mock（smsMock）、三步流程页 | `reports/forgot-password-20261120.md` | 完成，接口验证待人工走查 |
| 4 | 权限体系通用模板化：四层角色（标配/业务成员/业务主管/系统管理员）、前端 getPermCode 权限闭环、侧栏按角色过滤、路由守卫、新用户默认角色 | `reports/permission-system-20261120.md` + `sql/permission-system-20261120.sql` | 完成，SQL 已执行实测 |
| 5 | 组织初始化：以控股集团（A05）为模型材料，8 账号按身份安置、A01/A02 演示组织逻辑清退 | `reports/org-init-20261120.md` + `sql/org-init-20261120.sql` | 完成，SQL 已执行实测 |
| 6 | 监控域确权闭环：定时任务操作化（增改/暂停/启动/执行/删除）、数据变更日志新页（列表/历史版本/对比） | `reports/monitor-governance-20261120.md` | 完成，typecheck exit 0 |

## 2. 环境/工具修复（两项，均已修）

1. **`scripts/start-team-backend.sh` 隐藏 bug**：`spring-boot:run` 与 `-am` 同用导致 run 目标执行到父 pom 必失败（"Unable to find a suitable main class"）。改为两段式：先 `mvn install -am`，再不带 -am 启动。8081 后端已用修复后脚本成功重启（第 160 秒监听）。
2. **Tauri 打包链断链**：`beforeBuildCommand` 为空导致 `pnpm tauri:build` 静默打包陈旧 dist。修正为 `pnpm build`（注意：tauri 执行该命令时 cwd 已在项目根，不能加 `cd ..`）。
3. **Knife4j 覆盖率断链**：`globalOpenApiMethodFilter` 过滤"仅 @Operation 注解方法"导致 /sys/user、/sys/sms、/sys/dataLog 等在用接口不出现在文档（219/全量）。放宽为全部方法（1373 个），并新增 `SwaggerParamExampleConfig` 统一注入调试示例值（登录体/短信体/分页/quartz 真实主键等，生产文档不加载）。

## 3. 产物

- **TeamWorkbench 0.2.0 安装包**：`jeecg-team-web/src-tauri/target/release/bundle/nsis/TeamWorkbench_0.2.0_x64-setup.exe`（2.0 MiB，已实测含忘记密码与数据日志页）
- **SQL 材料 ×2**（幂等可重跑）：`sql/permission-system-20261120.sql`、`sql/org-init-20261120.sql`
- **专项报告 ×5**（§1）+ 本报告；根 `AGENTS.md` 已建全部索引

## 4. 验证状态汇总

| 验证 | 状态 |
|---|---|
| 后端编译（mvn compile） | exit 0（两次：分页修复、SMS mock） |
| 前端 typecheck | exit 0（三次：签名/忘记密码、权限闭环、监控域） |
| MD5/签名与 node:crypto 对拍 | 6 条测试向量全过 |
| SQL 执行后回查 | 角色/权限/组织安置全部实测符合设计 |
| 8081 后端重启 | 已在新构建运行 |
| Tauri 包内容 | dist 含忘记密码/数据日志 chunk（实测） |
| **浏览器 L2 走查** | **未做，需人工**：zhoujingxiao 登录重看在线用户（应 1 行）、/forgot-password 发码看 `[SMS-MOCK]` 日志、定时任务暂停/启动、数据日志空态 |

## 5. 遗留与触发条件

- **L2 走查**（见 §4）：走挂任一步把网络面板信息带回。
- **忘记密码前置条件**：目标账号须已绑手机号（admin 已绑 18611111111）。
- **未闭环项**（详见 monitor-governance 报告 §3）：部门授权/直接授予/租户按真实需求触发；G2 时效（角色权限 end_time 到期回收）需后端改表结构+定时任务，建议单独立项（回收 Job 正好落在已操作化的定时任务页）。
- **待提交**：今日改动跨 jeecg-boot / jeecg-team-web / AGENTS.md / projects，尚未 git commit，由主会话择机统一提交。

## 6. 数据/回滚要点（速查）

- 在线用户/单点登录：`application-dev.yml` 的 `is-concurrent` ~~改回 true 即回滚~~ **已于当日改回 true**（11-20 晚：false 导致多浏览器/多人共用账号测试互踢、无法正常登录，宿裁定 dev 以测试友好为先；注释中保留 prod 收口路径）。堆积治理退化为"定期清理 token"口径
- 短信 mock：`jeecg.smsMock` 置 false；prod profile 默认 false 不受影响
- 权限/组织 SQL：两份 SQL 文件内含幂等条件与逆操作说明（各专项报告 §4/§5）
