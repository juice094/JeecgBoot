# STATUS — 团队协作效率工具现状（2026-11-16 收尾快照）

> 任何会话窗口重开时先读本文件 + `docs/11`（方向裁决）+ `ROADMAP.md`。

## 方向（已定案，勿回头）

正向道路的团队协作效率工具（基础功能优先）。账号池/浏览器池/AI采集/Agent 调度**整体封存研究线**，未经技术总监重新拍板不得重启（docs/11）。

## 运行基线（全部已验证）

| 端 | 服务 | 入口 |
|---|---|---|
| 本机 rog-x | jeecg 后端 | :8081（/jeecg-boot，关验证码） |
| 本机 | jeecg 原生前端 | :3100 |
| 本机 | **团队新前端（主战场）** | **:3200（admin/123456）** |
| 本机 | Mattermost IM | :8065（admin/Admin@123456） |
| 本机 | OnlyOffice | :8090 |
| 云 VM | 接口文档实例 | 100.78.65.37:8080/jeecg-boot/doc.html（basic auth 口令在 VM /root/jeecg-doc/jeecg-doc.env） |
| 云 VM | claw + jeecg 微服务栈 | 维护报告 /root/maintenance-report-2026-11-16.md |

## 分工（模块目录=职责边界）

- qingyanjunzi → `jeecg-team-web/src/modules/message/`（issue #1）
- **juice094 → `src/modules/business/`（issue #2 任务看板 / #3 表单与审批）**
- Ym3699 → `src/modules/admin/`（issue #4/#5）
- 开发分支从 `pilot/w1-trim` 切；设计文档在 `pilot/design` 分支（worktree: C:\dev\agent-collab-pilot）

## 编排结论（jeecg 能力复用）

通用工具中心 = Online表单（全员填报，管理员配置模板）+ 我的消息/公告/通讯录 + 积木报表；管理面（用户/角色/菜单/监控）按角色授权可见。裁剪：大屏/AI应用/演示页。**裁剪清单已在 ROADMAP 修订（19b602f9）。**

## 待办（按优先级）

1. 对侧磁盘清理（已批准）：`docker image prune -f` + `docker builder prune -f`（约 1.2G+）——**tailnet DERP 中继恢复后 SSH 执行**（17:05 起 22 端口超时中，属校园网换网常见抖动，重试即恢复）
2. juice094：issue #2 任务表 DDL + 后端模块（前端看板页骨架已就绪）
3. 成员已可访问：tailnet 已有 qingyanjunzi 设备（pc-20261030bbye）；按 docs/12 分发文档口令
4. 里程碑 M2：Mattermost↔OnlyOffice 打通 + SSO（Ym3699 协作通路）

## 已知坑（别再踩）

- systemctl --user 查 root 服务必须 `export XDG_RUNTIME_DIR=/run/user/0`
- 本机 Docker Desktop 的端口转发 netstat 不显示 LISTENING
- vite/Node24 默认只绑 IPv6 → jeecg-team-web 已显式 host 127.0.0.1
- 对侧 cron 排期必须是合法日期（"2月31日"曾致 claw 瘫痪）
- github.com 直连偶发 SSL 失败，git push 重试即可
