# 工作邮箱原型骨架交付报告（额外工作线）

> 日期：2026-11-17  
> 执行：AI 编码助手（网关工具）  
> 定位：**额外工作线——产出原型与骨架，不可靠交付**，不计入 docs/23 主合同施工与联调基准。  
> 合同依据：`docs/OA 邮件体系需求文档.md`、`docs/OA 邮件体系接口文档.md`（v1.1.0，MAIL-REQ-001 / MAIL-API-001）

## 1. 目标与边界

**目标**：在不立项、不取 Flyway 号、不碰后端的前提下，把工作邮箱从三行 mock 升级为可评审的前端原型骨架，让接口合同有可对照的页面落点。

**本线做了**：

- 前端 API 契约占位（`/team/mail/**`）。
- 邮件枚举与展示常量。
- 工作邮箱页面原型（文件夹、索引列表、详情占位、写信抽屉）。
- 全局 `.tag--danger` 样式补齐。

**本线明确没做**：

- 不创建 `jeecg-module-mail` 后端模块。
- 不写 Flyway 脚本、不占 `V3.9.6_x` 号段。
- 不改 `docs/23`、`pending-decisions.md`、共享看板。
- 不接真实 SMTP/IMAP，不调用未立项端点。
- 不做 Agent 封存线（§5）实现。

## 2. 产出清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `jeecg-team-web/src/api/mail.ts` | 新增 | 契约占位：系统发信、员工邮箱、Agent 封存线端点与 DTO |
| `jeecg-team-web/src/constants/mail.ts` | 新增 | 发送状态、文件夹、写信模式、错误码、重试上限 |
| `jeecg-team-web/src/modules/business/views/WorkEmail.vue` | 改写 | 原型页：默认 mock，不发起真实请求 |
| `jeecg-team-web/src/styles/global.css` | 修改 | 补 `.tag--danger` |
| `projects/agent-collab-pilot/reports/mail-prototype-20261117.md` | 新增 | 本报告 |

## 3. 合同映射（页面 → 接口文档）

| 页面能力 | 合同端点 | 状态 |
|---|---|---|
| 文件夹导航 | `GET /accounts/{id}/folders` | 契约占位，页面用本地枚举 |
| 索引列表 | `GET /messages` | 契约占位，页面用 mock |
| 邮件详情 | `GET /messages/{id}` | 契约占位，正文不拉取 |
| 标记已读 | `PATCH /messages/{id}/read` | 页面本地改 `read` 标志，不发请求 |
| 写信/回复/转发 | `POST /messages/send` | 仅前端校验，提示未投递 |
| 同步 | `POST /accounts/{id}/sync` | 刷新按钮 mock 重载 |
| 系统发信/模板/日志 | `/system/**` | 仅 API 契约，无页面 |
| Agent 封存线 | `/agent/**` | 仅 API 契约，不启用 |

## 4. 验证（诚实标注层级）

- **已做**：文件级静态检查——导入路径、类型命名、样式类与全局 CSS 对齐。
- **已做**：`/team/mail/**` 端点逐条对照接口文档 §3、§4、§5 路径。
- **已做**：`pnpm build`（vite v8.3.8）通过，1684 modules transformed，BUILD_EXIT=0；产物含 `WorkEmail-*.js`（18.59 kB）与 `WorkEmail-*.css`（1.87 kB），证明新增页面与两个契约文件可被打包。
- **注意**：`vite build` 用 esbuild 转译，**不做 TypeScript 类型检查**；类型层面仍需 `vue-tsc` 单独确认。
- **未做（环境限制）**：`vue-tsc --noEmit` 无法运行——`vue-tsc@1.8.27` 与 `typescript@5.9.3`/`node v24.17.0` 不兼容，启动即报 `Search string not found: "/supportedTSExtensions = .*(?=;)/"`。这是 team-web 工具链既有问题，与本线改动无关；本线**类型层面未获验证**。
- **未做**：无后端、无集成测试、无端到端验证。
- **结论**：这是**原型骨架**，构建可过、不是可联调实现；不能据此判断邮件功能可用。

## 5. 与主线的隔离

- 路由 `tools/email` 原本已指向 `WorkEmail.vue`，未新增路由、未改菜单。
- 页面顶部有醒目横幅，声明 mock 与未立项，避免被误当联调基准。
- 发送按钮只做本地校验，不触发 `sendMailMessage`，避免对未立项端点产生请求。
- `api/mail.ts` 注释标明“真实调用必然失败”，防止后续误用。

## 6. 未完成与待验证

1. **构建验证**：✅ 已执行 `pnpm build`，通过（见 §4）。
2. **类型验证**：`vue-tsc` 跑不起来（1.8.27 × TS 5.9.3 × Node 24 不兼容，环境既有问题）。若需类型门禁，先升 `vue-tsc`（≥2.x）或降 TS，再跑 `vue-tsc --noEmit`；`@/api/mail` 的类型导入届时一并确认。
3. **样式验证**：`--danger`、`--primary-light`、`--radius-sm`、`--text-secondary` 是否在 `:root` 定义，需浏览器实看。
4. **真实合同差异**：接口文档为 v1.1.0 草案，路径与字段可能随立项调整；`api/mail.ts` 需在立项后重对。
5. **权限**：系统发信页未做，管理员权限口径未验证。

## 7. 残余风险

- **误用风险**：他人可能把 `api/mail.ts` 当成已实现接口直接调用。已用文件头注释与页面横幅降低，但不能完全消除。
- **合同漂移**：草案版本与立项后版本不一致时，骨架需重写而非修补。
- **构建风险**：本线已跑 `pnpm build` 通过（BUILD_EXIT=0），但该结果只代表本次改动时点；后续他人改动若导致构建失败，优先判断是否本线新增文件引起，不动既有业务页。类型检查仍未覆盖（`vue-tsc` 未跑）。
- **范围风险**：本线不属于 docs/23 施工项，不应占用 lead/bizflow-dev 的验收资源。

## 8. 建议下一步

1. ✅ 构建已在 `jeecg-team-web/` 跑过并回填 §4；剩余可选项是补跑 `vue-tsc --noEmit`。
2. 立项拍板后，按接口文档重对 `api/mail.ts`，再决定是否保留当前骨架。
3. 若要在评审中展示，建议只展示页面与契约映射，不宣称“邮件功能已落地”。
4. 系统发信管理页（账号/模板/日志）另起原型，不与员工邮箱页混做。
