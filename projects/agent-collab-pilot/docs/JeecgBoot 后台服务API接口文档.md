# JeecgBoot 后台服务 API 接口文档

> 2026-11-17，bizflow-dev 维护。本文是后端接口文档的总入口与维护约定；分组明细以 Knife4j 在线文档为准（随码自动生成，改接口必须同步注解，docs/13 §二.5）。

## 一、访问入口

| 环境 | 地址 |
|---|---|
| 本机 dev（联调基准） | http://127.0.0.1:8081/jeecg-boot/doc.html（未起服务时为 8080/其他端口，以启动横幅为准） |
| 通用路径 | `{base}/jeecg-boot/doc.html` |

Knife4j 分组（OpenAPI v3，api-docs 已核验）：

| 分组 | 端点数 | 说明 |
|---|---|---|
| 团队协作 | 24 | `/team/**`（jeecg-module-team，自研），注解覆盖率 100%（全部 @Operation + 参数 @Parameter + 实体 @Schema） |
| 系统管理 | 88 | `/sys/**` 等（jeecg 原生，含组织/用户/角色/字典/公告/消息），上游注解，按原生文档使用 |

## 二、团队协作分组端点索引（24）

业务语义与请求/响应示例见三组体系文档（同目录，随码维护）：

- 任务线：`任务栏与布告 Issues 体系接口文档.md` —— /team/task/list、board、dueSoon、add、edit、status、delete、deleteBatch、queryById
- Bug 线：同上 —— /team/bug/list、add、status、delete、deleteBatch、queryById
- 审批线：`申请表流转体系接口文档.md` —— /team/approval/list、todo、submit、approve、reject、steps、queryById；/team/dashboard/summary；/team/audit/list
- 邮件线：`OA 邮件体系接口文档.md` —— 立项后启用（设计稿，未实现）

横切约定（三组文档同口径）：

- 统一响应 `Result<T>`（success/code=200/result/message），业务错误 `Result.error` 文案带上下文；分页 pageNo/pageSize 返回 IPage
- 认证：`X-Access-Token`（Shiro）；权限：F-09 后全部端点挂 @RequiresPermissions（team:\<biz\>:\<action\>），超管放行，普通角色需在角色管理勾选
- 删除语义：任务/Bug 为逻辑删（D-69a，deleted=1）；状态机非法流转返回带上下文文案
- sys 聚合例外：/team/dashboard/summary 直读 sys_announcement/_send（D-72 宿裁，仅此一处）

## 三、维护约定

1. 新增/变更 /team/** 端点：@Tag/@Operation/@Parameter/@Schema 随码提交；响应结构变更需在对应体系接口文档同步（PR 描述带「文档影响」）。
2. Knife4j 注解变更需重启后端才能在 doc.html 生效（OpenAPI 为启动时生成）。
3. 系统管理分组的缺陷（如 /sys/message/sysMessage/list 510，F-10）归 jeecg 上游口径，不在本组重写；发现新缺陷登记需求审计，不在接口文档内打补丁式说明。
