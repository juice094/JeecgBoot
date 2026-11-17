# 17 — 云 VM 工作区维护手册（tailnet 模式）

> 2026-11-17，Kimi Code。VM：VM-14-0-ubuntu（100.78.65.37，腾讯云 4C/8G/40G，tailnet 内）。
> 角色定位见 docs/14（共享入口机）；本文讲"怎么改、怎么维护、文档怎么看"。

## 一、维护通道：tailnet

| 项 | 说明 |
|---|---|
| 组网 | tailnet（宿为 admin）；`tailscale status` 可查全员设备（rog-x / pc-20261030bbye / win-8t86aprs7u7 / vm-14-0-ubuntu） |
| 上机 | `ssh root@100.78.65.37`；宿用 `~/.ssh/id_ed25519_teamops`；成员的密钥由宿逐人挂到 `/root/.ssh/authorized_keys`（写清注释 # who） |
| 网络坑 | 校园网经常走 DERP 中继（relay "sfo"），SSH 偶发超时——**重试即可**，勿判定 VM 宕机；大文件传输避开晚高峰 |
| 口令 | knife4j basic auth 口令在 VM `/root/jeecg-doc/jeecg-doc.env`（KIMI 可见，勿外发） |

## 二、文档合同实例（jeecg-doc）

**入口：http://100.78.65.37:8080/jeecg-boot/doc.html**（tailnet 内，basic auth）

| 件 | 位置 / 说明 |
|---|---|
| systemd 服务 | `jeecg-doc.service`：jar 绑回环 8081， knife4j basic auth 由服务参数开启，nginx 8080 对外反代 |
| 代码来源 | `/root/jeecg-doc/JeecgBoot`，**分支 `pilot/w1-trim`**（2026-11-17 由 main 切换——团队合同以开发主线为准） |
| 自动更新 | `jeecg-doc-update.timer` 每日 05:30：fetch origin/pilot/w1-trim → 有变更才 `mvn clean package`（约 5-8 分钟）并重启服务；看日志 `journalctl -u jeecg-doc-update` |
| 数据库 | VM 本机 MySQL 13306 /jeecg-boot；Flyway 随启动自动迁移（team_task/team_approval/team_bug 等） |
| 手动更新 | 参考 timer 的 ExecStart（/etc/systemd/system/jeecg-doc-update.service） |

### 接口文档的内容结构（2026-11-17 调整）

Knife4j 顶部**分组下拉**（springdoc group-configs，配置在 application-dev.yml）：

- **团队协作** = `/team/**`（自研：审批状态机 / Bug / 任务）
- **系统管理** = `/sys/**`（jeecg 原生：组织/用户/公告/字典/上传）
- **default** = 全量（含 jmreport 等未分组接口）

新增自研域的规矩：controller 标 `@Tag(name="团队协作-xxx")`，路径挂 `/team/xxx/**`，并在 application-dev.yml 的 group-configs 评估是否入组。

## 三、成员常用操作

```bash
# 看文档服务状态 / 日志
systemctl status jeecg-doc
journalctl -u jeecg-doc -n 100 --no-pager

# 手动触发文档更新（等不及 05:30 时）
systemctl start jeecg-doc-update

# 验证接口是否进文档（服务跑在回环 8081）
curl -s http://127.0.0.1:8081/jeecg-boot/v3/api-docs/团队协作 | head -c 200
```

## 四、VM 作为工作区的边界

- ✅ 跑：文档实例、（未来）团队前端常驻展示位、MySQL/Redis 底座容器、维护脚本
- ⬜ 不跑：业务联调后端（各人本机 8081，docs/13 §五）、微服务栈（docs/14 降级研究线）
- 新增常驻服务前：先问"对tailnet成员是否可读日志、可重启"，再动手
