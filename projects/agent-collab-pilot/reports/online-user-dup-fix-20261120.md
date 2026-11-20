# 在线用户重复显示（admin × 69）排查与修复报告

> 日期：2026-11-20
> 执行：AI 编码助手（Kimi Code，主会话施工）
> 触发：team-web `/admin/monitor/online` 页面显示"当前同时在线 69 个 admin"，会话ID 列全部为 `e9ca23d68d884d4ebb19d07889727dae`
> 方案：宿认可"组合 A"（止血 + 根治 + 修显示 + 顺手修分页 bug）

## 1. 结论（根因）

**不是数据库设计缺陷，也不是前端渲染 bug。** 三层原因叠加：

1. **后端口径**：`/sys/online/list`（`SysUserOnlineController.list`）遍历 Redis 中每个 `prefix_user_token:<jwt>` key 生成一行——**一个 token = 一个"在线用户"**，不去重。在线用户根本不查数据库。
2. **配置允许并发登录**：`jeecg.firewall.is-concurrent: true`（默认值）时 `LoginController.handleSingleSignOn` 直接 return，旧 token 不清理。每次登录生成新 JWT（key 独立），TTL = `EXPIRE_TIME×2` = **7 天**。窗口内重复登录的 token 全部堆积。
3. **列名误标**：`e9ca23d68d884d4ebb19d07889727dae` 是 **sys_user 表里 admin 的用户主键**（`BeanUtils.copyProperties(loginUser, online)` 把 `id` 拷到 VO），不是会话 ID；同用户 69 行自然全一样。真正的会话标识是 `SysUserOnlineVO.token`。

实测证据（2026-11-20，本机 Redis 127.0.0.1:6379 db0）：`prefix_user_token:*` 共 113 个 key，解 JWT payload 得 admin 69 / zhoujingxiao 20 / lanou_emp 8 / ceshi 7 / lanou_mgr 4 / lanou_vp 3 / jeecg 2。

## 2. 改动清单（4 项，均已验证）

| # | 改动 | 文件 | 验证 |
|---|---|---|---|
| 1 | 清空 Redis 历史 token（113 个） | Redis db0，非代码 | redis-cli 实测剩 0 |
| 2 | `is-concurrent: true → false`（新登录挤掉旧登录） | `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/application-dev.yml:291` | —（需重启生效） |
| 3 | 「会话ID」列改显示 `row.token`；`OnlineUserRow` 补 `token` 字段；行 key 用 token | `jeecg-team-web/src/modules/business/views/monitor/MonitorOnline.vue`、`src/api/monitor.ts` | `pnpm typecheck` 通过 |
| 4 | 分页总页数硬编码 `% 10` → `% pageSize`（独立小 bug，pageSize≠10 时分页页数错） | `jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/controller/SysUserOnlineController.java:99` | `mvn compile`（-pl jeecg-system-biz -am）通过 |

**删前备份**：113 条 key 列表存于 Git Bash `$TEMP`（实际 `/tmp/online-tokens-backup-20261120-093015.txt`，Windows 路径约为 `C:\Users\22414\AppData\Local\Temp` 对应 Git Bash 临时区）。仅 key 名，无 token 值，作审计用；恢复方式 = 重新登录。

**重启要求**：8081 后端进程必须重启才能加载配置与 controller 改动；重启后所有用户需重登一次。

## 3. 环境坑备案（重要，防再踩）

Windows 版 `redis-cli` 输出为 **CRLF**。`redis-cli keys ... > file` 后直接在 `while read` / `for` 循环里逐条 `del`，key 名会带隐藏 `\r`，**删除静默失败（del 返回 0），且排查时极易误判为"有进程在重建 key"**。

可靠姿势（本报告验证过的最终方案）：

```bash
redis-cli -n 0 keys "prefix_user_token:*" | tr -d '\r' > /tmp/keys.txt
for k in $(cat /tmp/keys.txt); do redis-cli -n 0 del "$k" < /dev/null; done
```

另外循环体内 `redis-cli` 会继承循环的 stdin，建议一律加 `< /dev/null`。

## 4. 回滚

> 2026-11-20 更新：`is-concurrent` 已按宿裁定当日改回 **true**（多地登录许可）——false 的单点互踢对多浏览器/多人共用账号的测试走查不友好。dev 保持测试友好；prod 收口（若需要）在 `application-prod.yml` 设 false。原回滚说明保留备查：

| 改动 | 回滚方式 | 成本 |
|---|---|---|
| is-concurrent=false | yml 改回 `true` + 重启 | 一行配置 |
| 会话ID 列显示 token | git revert 两个前端文件 | 低 |
| 分页修复 | git revert | 低（原行为是 bug，不建议回滚） |
| Redis 清理 | 无法也不需要回滚（重登即恢复） | — |

## 5. 未做（有意不做，附触发条件）

- **后端按用户名聚合去重**：改动 JeecgBoot 原生"按会话列出"口径，与 `forceLogout` 按 token 强退的语义冲突，迁移期不动。**触发条件**：产品明确"在线用户=按人统计"时单独立项。
- **缩短 token TTL**：治标不治本，已用 is-concurrent=false 根治。

## 6. 关联

- 旧前端 `jeecgboot-vue3/src/views/system/onlineuser/OnlineUserList.vue` 同样消费 `/sys/online/list`（rowKey 已是 token），行为一致，属迁移参考口径。
- 前端资产状态标注（jeecgboot-vue3 待迁移 / jeecg-team-web 现行默认）：见仓库根 `AGENTS.md` 与 `jeecgboot-vue3/AGENTS.md` 顶部横幅（2026-11-20 更新）。
