# STATUS — 团队协作效率工具现状（2026-11-17 深夜·会话收尾快照）

> 任何会话窗口重开时先读本文件 + `docs/11`（方向裁决）+ `docs/13`（上手指南）+ `docs/16`（后端模块设计）+ `docs/17`（VM 维护手册）。
> 微服务栈为封存研究线（docs/14），**一切交付在单体形态内**，勿向 Nacos/Gateway 体系内加东西。

## 方向（已定案，勿回头）

正向道路的团队协作效率工具（基础功能优先）。账号池/浏览器池/AI采集/Agent 调度封存，未经技术总监拍板不得重启。

## 运行基线（全部已验证在役）

| 端 | 位置 | 状态 |
|---|---|---|
| 后端（单体） | 本机 8081 = pilot/w1-trim @ `7044cc24` jar | ✅ 含 team 模块 + Knife4j 分组 |
| 前端（主战场） | 本机 3200 dev（admin/123456） | ✅ 双模式 + 14 页 + 会话工作台 2.0 |
| 接口文档合同 | VM 100.78.65.37:8080/doc.html | ✅ 已切 pilot/w1-trim，团队协作 18 条 / 系统管理 88 条 |
| 自动更新 | VM 每日 05:30 timer 跟踪 origin/pilot/w1-trim | ✅ 已改轨（浅克隆需显式 refspec） |
| Tauri 打包 | pnpm tauri:build → NSIS 安装包 | ✅ 产物已出；**构建必须带 HTTPS_PROXY=http://127.0.0.1:7897** |

## 11-17 单日完成（按层）

- **仓库**：team-web 入库、默认分支=pilot/w1-trim、issue/PR 模板、分支保护命令（PR#15 内待宿执行）、#7~#12 全部闭环、#16 关闭
- **后端 jeecg-module-team**：team_task（V3.9.6_2）+ team_approval/team_approval_step 状态机 + team_bug（V3.9.6_3）；编译与运行时全链路验证（提交→逐级审批→终审守卫→拒绝路径）
- **前端**：双模式工作台（模式-路由单向派生、lastToolPath 记忆）、航城蓝鸥原型 14 页复现、Phosphor 图标、设计令牌/常量区、菜单单一来源、个人报表嵌 JimuReport、会话工作台 agent-loop 重做（ToolCallCard + 斜杠命令 + 会话内快捷表单）
- **接入**：审批中心/日常申请/Bug/仪表盘/人事/资料中心上传 全部真接口；仪表盘"考勤记录"按业务适配替换为系统公告
- **文档**：docs/13~17 全部推送

## 分工现状

- qingyanjunzi → message 模块：**Mattermost 集成待启动（M2）**，会话工作台壳已就绪
- Ym3699 → admin 模块：管理组后端未立项；**人事页已用 sys 原生接口先接入**（他可直接在此基础上增强）；固定资产/排课/报修仍 mock
- juice094 → business + 负责人 + **下一会话主线**；Kimi Code 继续执行接手清单

## 下一会话主线（juice094，按优先级）

1. **业务组三页落地**：市场活动/职业教育/学生服务——表设计 + CRUD；学生服务建议复用审批状态机（学籍异动=category）
2. **审批中心增强**：team_approval_step 加 approver_id 做真实"待我审批"归属过滤；拒绝/通过意见弹窗替换 window.prompt
3. **仪表盘聚合接口**：一次拉齐 待办数/公告/未读（替代前端 N 次往返）
4. **工具自设计**：斜杠命令参数解析（`/apply 加班申请 标题…`）+ /task 接 team_task 快捷表单；更多 navigate 命令
5. **工作邮箱立项决策**：SMTP 自建 vs 后置（docs/16 矩阵有边界说明）
6. **Tauri 版本化**：安装包版本号/签名/自动更新策略
7. （联 Ym3699）管理组三个 CRUD 立项；（联 qingyanjunzi）Mattermost 接入

## 已知坑（本会话新增，重读 docs/13 §六）

- yml 改动必须 python 结构校验 + 断言关键 key 层级（springdoc 截断 spring 块事故）
- phosphor 图标导出名带 `Ph` 前缀且无 `Slashes`（用 `PhCommand`）
- 合成 KeyboardEvent 不触发 Vue `@keydown.enter`（自动化用 Input.dispatchKeyEvent）
- git 切分支会清掉"目标分支不存在"的已跟踪目录；vite 运行中切分支会端口漂移 3200→5173
- 审批接口 comment 带中文必须 URL 编码（前端 axios 自动，Apifox 手测注意）
- tailnet 今日走 SFO 中继频繁超时：SSH 重试即恢复，勿误判 VM 宕机
- cargo/tauri 构建走 7897 代理；VM 文档验证带 knife4j basic auth

## 待宿拍板

- VM 微服务四容器停机留数据（docs/14 §五.3，连续 30 天无研究使用触发）
- 分支保护命令（PR #15 正文）是否执行
- 工作邮箱立项（见主线 5）

## 深夜增量（同日后半段，2026-11-17）

- **战略重估 docs/18**：目标场景升级 60 人+60 agent 人机混编；诚实难点清单（注意力通胀/授权边界/审批成网/agent 治理）；单体非瓶颈结论；P1~P4 路线。原型来源=某公开科技公司组织图，**呈报纪律**：总监只看桌面导出件，仓库内部文档不呈报。
- **文档族 V1→V3.2**：V3.0（对外脱敏）、V3.1（docx 增量）已归档 `deliverables/archive/`；现行是 **V3.2 md 主稿**（需求 + 接口各一份，docx 由 `build-docx.py` 导出）。相对 V3.1：需求用例化（UC + 可判定验收）、接口逐项三段（请求/响应/错误）、修正"统一 code=200"口径（原生域多为 0）、审计复核并新增 F-09（团队域零权限）/F-10（站内消息 510）。HANDOVER.md 是成员入口。
- **审计发现已处置**：F-01 公告解析恒空——已修（19a108ec）；F-02 审批不校验操作人——列 R-01 强制范围；F-03 doc.html 未覆盖在用原生接口——接口文档第 4 章 ⚠ 代码级合同兜底，排查归 Kimi Code。
- **分支基线**：pilot/w1-trim @ 19a108ec；pilot/design @ 60aeef94。两树干净。
- 在役：8081 后端 jar（pilot/w1-trim 最新）、3200 前端 dev。下个窗口开工口令=STATUS 主线 + deliverables/HANDOVER。
