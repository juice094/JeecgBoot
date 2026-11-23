# 管理域前端迁移计划书（2026-11-18）

> 目标：把 jeecgboot-vue3（旧前端）**系统管理域**蒸馏进 jeecg-team-web（新前端）。
> 执行方式：其他会话窗口 + 子代理，按本计划书与操作范围执行。
> 本文锁定范围，执行中不得擅自扩。

## 0. 兼容性定性（先对齐认知）

| 层 | 兼容度 | 说明 |
|---|---|---|
| 接口契约层 | **≈95%** | 管理域 247 个 `/sys/*` 端点由 jeecg-boot 后端在役提供（8081），请求/响应契约与旧前端共用；迁移 = 把旧 `*.api.ts` 的 enum 端点 + 函数签名蒸馏进 team-web，换 http 封装即可 |
| 交互规格层 | **高价值参考** | 旧页面 `index.vue + X.data.ts（列/表单 schema/校验规则/权限码）` 是完整规格书；不搬代码，照着复刻，设计成本趋零 |
| UI 组件层 | **0%** | 旧 UI 长在 Vben + AntD Vue 上；新前端禁引入这两个，一律用 team-web 自有体系重写 |

一句话：**搬的是契约和规格，不是组件。**「管理部分兼容性高」准确含义=这层。

## 1. 范围锁定（三期，不得越界）

### P0 — RBAC 三件套（第一波）

| 模块 | 旧前端端点数 | team-web 现状 | 产出 |
|---|---|---|---|
| 用户管理 user | 28 | HrManage 已有部门树+用户列表（只读为主） | 用户 CRUD + 角色分配 + 重置密码，嵌进 HrManage |
| 部门管理 depart | 25 | HrManage 有树（只读） | 部门树 CRUD（增删改、拖拽不做） |
| 角色管理 role | 23 | **空白** | 角色列表/新增/编辑 + 权限勾选（sys_permission 树） |

### P1 — 支撑域（第二波）

| 模块 | 端点数 | 产出 |
|---|---|---|
| 字典管理 dict | 20 | 字典/字典项两级 CRUD（team 模块 category 后续可挂字典，先有这个能力） |
| 岗位管理 position | 8 | 岗位 CRUD |
| 通告管理 notice | 13 | 通告发布/列表（团队工作台公告栏的供给侧） |

### P2 — 租户（第三波，按需触发）

| 模块 | 端点数 | 触发条件 |
|---|---|---|
| 租户管理 tenant | 27 | 蓝鸥多租户模式真实启用后再做，不提前 |

### 明确不做（no-go）

menu（菜单管理 10）、ugroup（用户分组 11）、category（分类 10）、onlineuser、message（消息模板）、appconfig、address、fillRule、checkRule、appVersion、tableWhiteList、homeConfig、loginmini、examples。需要时另立项。

## 2. 技术约定（红线）

1. **禁新增依赖**：不得改 package.json（antd / vben / @vueuse / dayjs 一律不进）。确有需要先回主会话拍板。
2. **UI 只用 team-web 自有体系**：`src/styles/global.css` 的 card/button/input 类 + `src/components/` 现有组件。P0 第一波先投入两个通用件：`DataTable.vue`（列配置驱动 + 分页）与 `FormModal.vue`（字段 schema 驱动）——一次投入，P0-P2 全部复用（字段模板 schema 驱动的思路直接沿用）。
3. **API 层**：蒸馏进 `src/api/sys.ts`（超 400 行则拆 `src/api/sys/` 目录），函数签名照抄旧 `*.api.ts`，body 内 `defHttp.xxx(url, params)` → `http.xxx(url, params)`；URL 以旧 enum 为准一字不改。
4. **权限码**：照抄旧 `X.data.ts` 的权限码（如 `system:user:add`）；按钮显隐用 team-web 的权限工具（没有就补一个 `v-if` + store 判断的 20 行小工具，禁引入 v-auth 体系）。
5. **多租户/签名头**：team-web http 已带 token；多租户头（`X-Tenant-Id`）按登录态自动带，蒸馏时保留旧 api 的 tenant 参数透传。

## 3. 单模块蒸馏流程（每个模块一遍）

1. **读规格**（只读）：旧 `jeecgboot-vue3/src/views/system/<m>/` 全部文件——`index.vue`（交互流）、`<m>.data.ts`（列定义/表单 schema/校验/权限码）、`<m>.api.ts`（端点+参数）。
2. **写 api**：端点 + 类型进 team-web `src/api/sys.ts`，编译过。
3. **写页面**：team-web `src/modules/admin/views/<M>Manage.vue`，复刻交互（列/筛选/弹窗/校验），用 DataTable/FormModal。
4. **挂路由**：`src/router/` 加路由 + 侧栏入口（管理组），照 HrManage 现有挂法。
5. **验收门（每页必过）**：`pnpm typecheck` exit 0；浏览器走查实拍（登录 admin 操作一遍增删改查，网络面板确认 200 非 401/403）；完成后截图 + 端点清单回报。

## 4. Agent 操作范围（授权边界）

### 可写

- `jeecg-team-web/src/modules/admin/**`（管理页）
- `jeecg-team-web/src/api/**`（sys.ts 或 sys/ 目录）
- `jeecg-team-web/src/components/**`（仅限 DataTable.vue / FormModal.vue 两个新通用件）
- `jeecg-team-web/src/router/**`、`src/stores/**`（仅挂路由/权限工具所需最小改动）
- `jeecg-team-web/docs/**`（过程报告）

### 只读（参考规格，一行不改）

- `jeecgboot-vue3/**`（旧前端全部）
- `jeecg-boot/**`（后端全部）

### 禁止

- 改 `package.json` / `pnpm-lock.yaml` / `vite.config.ts`
- 动三体系业务页（`src/modules/business/**`、`WorkEmail` / `ApprovalCenter` / `RequestForm` / `TaskBoard` / `IssueList` 等）——它们在役，动了会影响今天刚闭环的走查
- 改后端 Java / yml / SQL（权限缺口登记上报，不自己补）
- 任何 `rm -rf`；删除一律 `git rm` 或移隔离目录
- git push（提交由主会话统一做）

### 异常上报而非自作主张

- 端点 403/不存在 → 登记「后端权限缺口清单」交主会话（可配在 sys_role_permission，属后端范畴）
- 旧规格与新前端设计语言冲突（如旧页面强依赖字典组件）→ 按新前端惯例简化，报告中注明取舍

## 5. 波次与成本

| 波 | 内容 | 规模估计 | 子代理 |
|---|---|---|---|
| 1 | DataTable/FormModal 通用件 + 用户/部门/角色 | 3 页 + 2 通用件，~1.5k 行 | 1 个 coder（如页面间无依赖可拆 2 个并行：先通用件，后三页并行） |
| 2 | 字典/岗位/通告 | 3 页，~600 行 | 1 个 coder，或 3 小任务并行 |
| 3 | 租户（触发后） | 1 大页 ~500 行 | 1 个 coder |

验证分级：编译/typecheck = L1；浏览器实拍走查 = L2；报告须注明每页到了哪级。

## 6. 验收标准（整体）

- P0 三页全部 L2（浏览器实拍增删改查实证）
- typecheck/build 全程 exit 0
- 在役三体系页面零回归（走查一遍 mail/approval/task 入口未坏）
- 产出：每页一份蒸馏记录（旧规格 → 新实现的取舍点）落 `jeecg-team-web/docs/`
