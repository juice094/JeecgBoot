# 24 — 原项目前端资产可用性地图（jeecgboot-vue3 → jeecg-team-web）

> 2026-11-17，showcase-dev 起草（演示力线首轮）。
> **口径变更（2026-11-17 宿指示）**：jeecgboot-vue3 旧线 UI 应归档，所需工作台为 jeecg-team-web；
> team-web 可复制复用旧前端代码的**调用逻辑**，**视觉要素一律重置**（team-web 自有设计体系）。
> 因此本分档以「复制调用逻辑的代价」为第一判据；iframe 嵌入旧站降级为短期备选通道，不再作为主方案。
> 既有评估结论（reports/mail-prototype-20261117.md、reports/local-run-and-mail-backend-20261117.md §3.1）与本表一致处直接引用，不重复论证。

## 一、分档定义

| 档 | 含义 | 出口形态 |
|---|---|---|
| **A** | 调用逻辑可复制 + 视觉重置：页面级 `*.api.ts`（URL/参数/应答处理）可直抄进 team-web `src/api/`，UI 按 team-web 规范自研 | 1 页级工作量，一个 PR 一页 |
| **B** | 接口直调 + 自研薄页面：后端端点现成且稳定，但旧页面逻辑不值得抄（过重/耦合 BasicTable/与试点无关的残部），按端点合同自研 | 薄列表页/卡片页 |
| **C** | 组件移植需立项：依赖 ant-design-vue / vben 体系，列依赖代价，走立项流程 | 立项书 + 移植评估 |
| **D** | 仅口径参考：接口路径、参数命名、业务口径可查，代码与 UI 均不搬 | 无 |

**复制纪律**：只抄 `*.api.ts` 的 URL、方法、参数组装、应答取值逻辑；不抄 template/style/BasicTable 配置；`defHttp` 调用一律改写为 team-web `utils/http.ts` 口径（先例：`src/api/sys.ts` 已把 `queryTreeList` 改写落地）。

## 二、system 域资产（src/views/system + message）

| 资产 | vue3 组件 | 调用逻辑位置 | 关键端点（前缀 /jeecg-boot） | 档 | 说明 |
|---|---|---|---|---|---|
| 部门管理 | `system/depart` | `system/depart/depart.api.ts` | `/sys/sysDepart/queryTreeList`（已落地 `src/api/sys.ts`） | **A** | team-web 已有先例：`api/sys.ts` + `modules/admin/components/DeptNode.vue`，补全增删改即可 |
| 数据字典 | `system/dict` | `system/dict/dict.api.ts` | `/sys/dict/list` `/sys/dictItem/list` `/sys/dict/getDictItems/{code}` | **A** | 纯 CRUD + 字典项子表，逻辑直白；团队后续下拉选项（任务标签/优先级字典化）的底座 |
| 职位管理 | `system/position` | `system/position/position.api.ts` | `/sys/position/list` 等标准 CRUD | **A** | 纯 CRUD，页面临排最低 |
| 在线用户 | `system/onlineuser` | `system/onlineuser/OnlineUser.api.ts` | `/sys/online/list` `/sys/online/forceLogout` | **A** | 仅两个端点，演示「RBAC 在线治理」效果立现 |
| 我的消息（收件箱） | `monitor/mynews` | `monitor/mynews/mynews.api.ts` | `/sys/sysAnnouncementSend/getMyAnnouncementSend` `readAll` `getOne` | **A** | 用户侧公告/通知收件箱；与 team-web Dashboard 公告展示天然互补 |
| 用户管理 | `system/user` | `system/user/user.api.ts` | `/sys/user/list` `add` `edit` `delete` `frozenBatch` 口径参考 | **B** | 旧页与 BasicTable/租户过滤深度耦合（300+ 行）；薄页=用户列表+启用停用+重置密码，不抄旧页 |
| 角色管理 | `system/role` | `system/role/role.api.ts` | `/sys/role/list` `saveRolePermission` `queryRolePermission` | **B** | 角色 CRUD 可薄页；权限勾选树（antd Tree 耦合）暂缓，团队用 `V3.9.6_6` 按钮权限 SQL 直授 |
| 公告管理（管理端） | `system/notice` | `system/notice/notice.api.ts` | `/sys/annountCement/list` `add` `doReleaseData` `doReovkeData` | **B** | team-web 已消费 `listByUser`（`src/api/announcement.ts`）；管理端薄页补发布/撤销 |
| 消息管理（管理端） | `system/message/manage` + `template` | 同名 `*.api.ts` | `/sys/message/sysMessage/list` `sysMessageTemplate/list` `send` | **B** | 站内信发送/模板；演示「公告→未读计数」闭环用得上 |
| OSS 文件 | `system/ossfile` | `system/ossfile/ossfile.api.ts` | `/sys/oss/file/list` `/sys/oss/file/upload` | **B** | 上传端点现成；列表薄页；附件能力（Bug 截图）依赖它 |
| 用户设置 | `system/usersetting` | `system/usersetting/UserSetting.api.ts` | 改密/换头像等 | **B（暂缓）** | 端点可查；试点优先级低 |
| 租户管理 | `system/tenant` | `system/tenant/tenant.api.ts` | `/sys/tenant/list` 等 | **D** | 试点单租户，用不上 |
| 菜单管理 | `system/menu` | `system/menu/menu.api.ts` | `/sys/permission/**` | **D** | team-web 是静态路由（docs/13 §二.5），菜单驱动体系不迁 |
| address/category/checkRule/fillRule/appVersion/homeConfig/tableWhiteList/ugroup/loginmini | 各同名目录 | 各同名 `*.api.ts` | — | **D** | 试点业务用不到 |

## 三、monitor 域资产（src/views/monitor）

| 资产 | vue3 组件 | 调用逻辑位置 | 关键端点 | 档 | 说明 |
|---|---|---|---|---|---|
| 日志管理 | `monitor/log` | `monitor/log/log.api.ts` | `/sys/log/list`（+ `/exportXls`） | **A** | 单分页列表 + 导出；AutoLog 审计的展示出口，配合 team 审计页讲「全链路留痕」 |
| 定时任务 | `monitor/quartz` | `monitor/quartz/quartz.api.ts` | `/sys/quartzJob/list` `pause` `resume` `execute` | **A** | CRUD + 三个动作按钮；演示「后台服务完整性」效果最佳 |
| Redis 监控 | `monitor/redis` | `monitor/redis/redis.api.ts` | `/sys/actuator/redis/keysSize` `memoryInfo` `info` `metrics/history` | **B** | 4 个端点，视觉重置后自绘卡片/迷你图（勿引旧 ECharts 大屏） |
| 性能监控 | `monitor/server` | `monitor/server/server.api.ts` | `/sys/actuator/memory/info` | **B** | 单端点，自绘卡片 |
| 磁盘监控 | `monitor/disk` | `monitor/disk/disk.api.ts` | `/sys/actuator/redis/queryDiskInfo` | **B** | 挂在 redis actuator 下，注意路径口径 |
| 我的消息 | `monitor/mynews` | 见 §二 | 见 §二 | **A** | 用户侧，重复列出防漏 |
| 网关路由 | `monitor/route` | `monitor/route/route.api.ts` | `/sys/gatewayRoute/**` | **D** | 单体 8081 无网关 |
| 数据源/数据日志/链路追踪 | `monitor/datasource` `datalog` `trace` | 同名 `*.api.ts` | `/sys/dataSource/**` 等 | **D** | online 报表/变更比对/skywalking 配套，试点不需要 |

## 四、dashboard / super / report / demo 域

| 资产 | 位置 | 档 | 说明 |
|---|---|---|---|
| 分析首页（数据大屏） | `dashboard/Analysis` | **D** | 重型 ECharts 大屏，视觉重置≈重做；如仪表盘要图表，自研轻图或按需引 echarts（独立库，不捆绑 antd） |
| 工作台 | `dashboard/workbench` | **D** | 旧线门户，team-web 已有自有 Dashboard（接 `/team/dashboard/summary` 真接口） |
| Online 在线开发 | `super/online`（cgform/cgreport） | **D** | 低代码体系不迁；cgreport 报表若需展示可走 iframe 备选（同 jmreport 约定） |
| AI 应用群 | `super/airag/*` | **D** | 与本试点无关；`/airag/flow/**` 本 fork 404（docs/22 #9 边界声明） |
| 报表/图表示例 | `report/*`、`demo/*` | **D** | 示例代码，仅口径参考 |

## 五、组件层资产（C 档集中评估，依赖代价一览）

| 组件 | 位置 | 依赖代价 | 结论 |
|---|---|---|---|
| `JUpload` / `JUploadButton` | `components/Upload` | ant-design-vue + Form 容器 + vben hooks 全套 | **C**：立项后评估；短期用原生 `input[type=file]` + `/sys/oss/file/upload`（B 档薄方案） |
| `tinymce` / `JEditor` | `components/Tinymce` | @tinymce/tinymce-vue + 体积 + license | **C**：短期 textarea；公告/邮件正文 HTML 化时立项 |
| `BasicTable` / `useTable` / `useModal` | vben 体系 | vben + antd 整套 | **禁迁（D）**：与 team-web 极简栈冲突（既有结论） |
| `JDictSelectTag` / `JSearchSelect` / `JDictTag` | `components/Dict` 等 | antd Select/Tag + 字典 hooks | 薄替代：自研 select + `/sys/dict/getDictItems/{code}`，随 A 档字典页一并落地 |
| `UserSelect` / `UserSearchModal` / `DepartSelect` / `RoleSelect` | `components/Selector` 等 | antd Modal/Table | 薄替代：人员接口（`/sys/user/list`）+ `DeptNode.vue` 自研弹窗 |
| `@vueuse/core` / `dayjs` | npm | 轻量独立 | **例外**：可直接装（不引入体系），按需 |
| `echarts` | npm | 独立库 ~1MB 级 | B 档可视化可选项，用到再装 |

## 六、iframe 通道现状（降级为短期备选）

- 既有通路：`/jeecg-boot/jmreport/list?token=`（JimuReport，PersonalReport.vue 在用）继续有效——这是**后端服务页**透传 token，与 vue3 无关，归档后不受影响。
- 旧站页面 iframe 的障碍（诚实记录，不推荐投入）：
  1. vue3 路由为后端菜单驱动，未登录只跳登录页；
  2. token 透传仅有 `TokenLoginPage`（`loginToken` + `thirdType=email`），且强制要求 `info` 跳转参数，**不支持任意页直达**；
  3. localStorage 按源隔离，team-web(3200) 与 vue3(3100) 登录态不共享。
- 结论：演示需要 system/monitor 页面时，按 §二/§三 A/B 档复制到 team-web，不要 iframe 旧站。

## 七、归档执行建议（本轮不动手）

前置（上次归档失败原因，reports/local-run-and-mail-backend-20261117.md §3）：关闭 VSCode 中打开的 vue3 文件、停止 3100 dev server。然后 `git mv jeecgboot-vue3 .archive/jeecgboot-vue3` 保留历史。
**时序建议**：git 历史里随时可查可恢复，归档不阻断复制——建议**尽早归档**，消除「双前端并存」的认知负担；本文档 + `*.api.ts` 的 git 历史即查阅索引。归档后若发现漏拷，从 .archive 取回单文件即可（move-aside 原则，不删除）。

## 八、第二轮施工临排（按演示价值排序）

1. **A 档首批（监控四页）**：日志、定时任务、在线用户、Redis/性能监控卡片——直接回应痛点①「可展示工作成果薄弱」，且全是只读/低风险端点。
2. **A 档次批（业务配套）**：字典页（为团队下拉字典化铺路）、我的消息收件箱。
3. **B 档按需**：公告管理端、OSS 文件、用户/角色薄页。
4. 路由登记：新增页面路由一律 lead 代登记进 `src/router/index.ts`（先例 task-9），本线不动该文件。

---
*文档变更记录：2026-11-17 首版（含口径变更：iframe 主方案 → 复制调用逻辑主方案）。*
