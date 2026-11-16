# 13 — 团队协作上手指南（分工 · 权责 · GitHub 协作 · 联调基线）

> 2026-11-17，宿（juice094）审定，Kimi Code 起草。目标读者：全体成员（qingyanjunzi / Ym3699 / juice094）。
> 依据：docs/11（方向裁决）、ROADMAP、STATUS（2026-11-16 快照）、代码现状盘点（2026-11-17）。
> 与既有文档冲突时，以本文档 + docs/11 为准；本文档未覆盖处回退 ROADMAP/STATUS。

## 一、方向执行口径（先读这段，防返工）

1. **IM 路线已拍板**：MVP 阶段聊天工作台 = **Mattermost 集成**（嵌入/深链 + 未读数与通知聚合），**不自研**消息存储、收发协议、WSS 推送。
2. `conversations / conv_members / messages / inbox` 四表属于已封存的自研消息线（docs/03/schema.sql），**移出对接合同**，不作为前后端开发依据。封存 ≠ 删除，重启须经技术总监重新拍板（docs/11）。
3. "工具工作台"（通用工具中心）= 编排现有 jeecg 能力：Online 表单填报 / 我的消息 / 公告 / 通讯录 / 积木报表查看，按角色授权可见（ROADMAP M1）。任务看板/审批流属 M3，排在 M1 编排之后。
4. 账号池 / 浏览器池 / AI 会话采集 / Agent 调度整体封存（docs/11），任何个人不得把研究线内容带进排期。

## 二、三人分工与权责

| 成员 | 模块 | 方向（按优先级） | 权责边界 |
|---|---|---|---|
| **qingyanjunzi** | `jeecg-team-web/src/modules/message/`（issue #1） | ① 聊天工作台改 Mattermost 集成（入口嵌入、未读数聚合、系统消息/公告收件箱）② 通知聚合 | 消息**入口与展示**；不碰消息存储/协议。Mattermost 体验若不可接受，提请评审，不在个人层面决定 |
| **juice094** | `src/modules/business/`（issue #2/#3）+ 负责人 | ① M1 通用工具中心编排 ② 任务看板/审批（M3）③ 接口合同裁决与后端质量把关 | 合同最终裁决人；"验收三问"执行人；后端模块骨架由 Kimi Code 产出、本人 review |
| **Ym3699** | `src/modules/admin/`（issue #4/#5）+ M2 协作通路 | ① 组织/成员模块（最高优先——人员选择器唯一数据源）② Mattermost 部署验收 ③ SSO 调研落地 | 组织/人员数据的**唯一出口**，他人不私自建用户查询 |

**横切规矩（三人共同遵守）**

1. 模块目录 = 职责边界，只改自己目录与路由表中自己那段；跨模块需求开 issue。
2. 菜单/路由/owner **单一来源**：`src/router/index.ts` 为唯一权威，侧栏从 router 派生（禁止第二份硬编码，Kimi Code 收口项 #11）。
3. 业务枚举走 jeecg 字典或 `src/constants/`，禁止字面量散在组件里。
4. issue 即合同：改接口必须同步 Knife4j 注解，PR 描述带「文档影响」。
5. 环境魔法值（端口、token 键名、超时）收常量区，见 §五端口矩阵。

## 三、现状诊断摘要（为什么感觉乱、进度慢）

> 完整盘点由 Kimi Code 于 2026-11-17 完成，结论如下。

1. **三条 IM 线并存**：docs/11 裁 Mattermost / docs/03 自研 schema（已建表）/ ChatWorkbench 按封存 schema 画 UI——前端朝已封存方向开发，Mattermost 落地即返工。→ §一已收口。
2. **合同缺口**：5 个前端页面全部 mock，后端无 pilot 业务模块，联调靠 issue 但 issue 未转为后端实现。
3. **契约层自相矛盾**：`utils/http.ts` 注释"成功 code===0" vs `stores/auth.ts` 判 `code===200`；README 代理 8081 vs 云上文档 8080。
4. **令牌/常量失控**：唯一令牌层 `variables.css` 18 个变量，字号/间距无阶梯，业务枚举硬编码。
5. **部署三套布局无基准**：compose 单体 / compose 微服务 / VM systemd+jar 并存。
6. 根因：决策（pilot/design）与代码（main）分家 + 合同未兑现 + 重复造轮子（第二套 admin 框架）。

## 四、Kimi Code 接手清单（已授权，2026-11-17）

以下事项不适合按模块分给个人，由 Kimi Code 执行、宿 review 后合并，完成后在 issue 打勾：

| issue | 事项 | 产物 |
|---|---|---|
| #9 | IM 路线执行口径落地 | 本文档 + message README 修订 |
| #10 | 后端 pilot 业务模块骨架：tasks DDL（Flyway）+ jeecg 规范模块 + Knife4j 注解，跑通纵线 | 可运行 PR |
| #11 | 前端公共层收口：菜单单一来源、http 契约修正、设计令牌阶梯 | 前端 commit |
| #7 | 云 VM 布局收敛：角色声明、布局取舍、端口矩阵、prune 执行 | 文档 + VM 操作 |
| #12 | 联调基准声明（dev 代理=本机 8081，VM 8080 仅文档合同） | README 段落 |
| #8 | 仓库协作规范：issue/PR 模板、分支保护建议 | .github/ 模板 |

## 五、联调基线与端口矩阵

| 端口 | 归属 | 说明 |
|---|---|---|
| 3100 | 本机 jeecgboot-vue3（原生前端） | 仅参考对照 |
| 3200 | 本机 jeecg-team-web（**开发主战场**） | dev 代理 → 本机 8081 |
| 8081 | 本机 rog-x jeecg 后端 | 开发联调基准（8080 被本机 nacos 容器占用） |
| 8080 | 云 VM 100.78.65.37 | **仅**接口文档合同入口（nginx basic auth → jar 回环 8081），不作业务联调 |
| 8065 / 8090 | 本机 Mattermost / OnlyOffice | M2 协作通路 |
| 13306 | VM MySQL | jeecg-doc 底座 |

## 六、GitHub 协作模式与命令（培训版）

### 分支模型

```
main          ← 上游 JeecgBoot 基线，只同步不开发
pilot/design  ← 设计文档/决策存档（本文档所在分支）
pilot/w1-trim ← 开发主分支，所有功能分支从这切
feature/<模块>-<功能>  ← 每人开发分支，如 feature/message-chat-list
```

### 新人第一天

```bash
git clone -b pilot/w1-trim https://github.com/juice094/JeecgBoot.git
cd JeecgBoot
git config user.name "你的ID"
git config user.email "你的邮箱"
```

### 日常循环（每个功能重复这套）

```bash
git checkout pilot/w1-trim
git pull                                   # 开工前先拉最新
git checkout -b feature/business-task-api  # 切功能分支

git add <具体文件>                         # 推荐按文件 add，少用 -A
git commit -m "feat(business): 任务表DDL+列表接口  #2"   # 带 issue 号
git push -u origin feature/business-task-api
```

然后 GitHub 网页开 **Pull Request**：base=`pilot/w1-trim`，compare=你的分支，描述写「文档影响」，@负责人 review，合并后删除功能分支。

### 三条铁律

1. 永不 `git push --force` 到共享分支（main / pilot/design / pilot/w1-trim）。
2. 冲突在自己分支上解决：`git pull origin pilot/w1-trim`（或 merge）→ 解冲突 → push。
3. 一个 PR 只做一件事：DDL 和接口分开提，review 才快。

### 高频排错

| 症状 | 命令 |
|---|---|
| 提交后发现漏文件 | `git add 漏的文件 && git commit --amend --no-edit`（仅限未 push） |
| 丢弃工作区改动 | `git restore <文件>` |
| push 被拒（远端有新提交） | 先 `git pull` 解冲突再 push，**不要强推** |
| 同步上游 JeecgBoot | `git remote add upstream https://github.com/jeecgboot/jeecg-boot.git && git fetch upstream`，再 merge 进 main |

### 验收三问（每模块完成前自问）

1. 文档齐了吗（接口注解/部署手册）？
2. 对侧 VM 能复现吗？
3. 团队成员会用吗？
