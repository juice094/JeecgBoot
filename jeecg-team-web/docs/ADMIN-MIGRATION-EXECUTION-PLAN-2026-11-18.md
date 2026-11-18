# 管理端迁移执行任务书（2026-11-18，子代理专用）

> 上游文档：同目录 `ADMIN-MIGRATION-PLAN-2026-11-18.md`（范围/红线/操作范围/验收，**必须全文先读，本任务书不重复其禁令**）。
> 本文件只补充：环境事实、本波任务分解、回报格式。

## 0. 一句话任务

按 ADMIN-MIGRATION-PLAN 的 P0 波次，把旧前端 jeecgboot-vue3 系统管理域的「用户/部门/角色」蒸馏成 jeecg-team-web 的三个管理页，先造 DataTable/FormModal 两个通用件。

## 1. 环境事实（冷启动必读，全部实测过）

- 仓库根：`C:/Users/22414/dev/JeecgBoot`（Windows，bash 用 Git Bash 语法）
- 新前端：`jeecg-team-web/`，开发服务器 **http://127.0.0.1:3200**（已在跑，vite 热更新）；命令 `pnpm typecheck` / `pnpm build` 在该目录执行，当前均 exit 0，不得弄红
- 后端：**http://127.0.0.1:8081/jeecg-boot**（在役，勿重启勿改）；登录 `POST /sys/login {"username":"admin","password":"123456"}` 拿 X-Access-Token；前端 dev 已配置代理
- 可登录验证的账号：`admin/123456`；`zhoujingxiao/Lanou@2026`（周景潇，admin 角色）；`lanou_emp/Lanou@2026`（李想，普通成员）——浏览器走查用 zhoujingxiao 即可
- 旧前端参考：`jeecgboot-vue3/src/views/system/<module>/`（**只读**）：`user/`（9 文件）、`depart/`（12）、`role/`（11），每模块读 `index.vue` + `<module>.data.ts` + `<module>.api.ts` 三件套
- team-web 现状：管理页在 `src/modules/admin/views/`（HrManage.vue 是用户/部门的半成品参考，Academic/Asset/Repair 是 mock 占位勿参照其数据写法）；`src/api/sys.ts` 仅 33 行（部门树+用户列表两个函数）；无通用表格/弹窗组件——这是本波要补的
- 侧栏/路由：`src/router/index.ts` 集中定义，meta.group='管理' 自动进侧栏（照 HrManage 挂法）
- 权限码：旧 data.ts 里的 `system:user:add` 等照抄；后端 sys 端点权限已就位于 admin 角色，zhoujingxiao 可全程验证

## 2. 本波任务分解（按序执行，前一步验收过后再做下一步）

1. **通用件**（先行，后续全复用）：
   - `src/components/DataTable.vue`：props 列配置 `[{key,title,width,slot?}]` + 分页（pageNo/pageSize，响应 records/total）+ 行操作列插槽；样式用 global.css 既有类
   - `src/components/FormModal.vue`：props 字段 schema `[{key,label,type:input|number|select|textarea|date,required,options,placeholder}]` + v-model 显隐 + 确定/取消事件 + 必填校验；参考 RequestForm.vue 的动态渲染写法（schema 驱动已在该项目实证）
   - typecheck 过 = 本步验收
2. **角色管理 RoleManage.vue**（旧 role/ 为规格，23 端点取 CRUD+授权核心约 8 个）：角色列表（分页/按角色名筛选）+ 新增/编辑弹窗 + 删除确认 + 权限勾选树（sys/role/queryTreeList 类端点，照旧 api.ts）；这是三页中最独立的一页，先做
3. **部门管理嵌进 HrManage.vue**：部门树从只读升级为增/改/删（右键或行内按钮均可，按新前端惯例），照旧 depart/ 规格（25 端点取树+增删改约 5 个）
4. **用户管理增强 HrManage.vue**：用户列表接 DataTable（替换现有简易表格），加新增/编辑用户弹窗（FormModal）、角色分配（select 多选取角色列表）、重置密码、启用/停用；照旧 user/ 规格（28 端点取核心约 10 个）
5. 全程 `pnpm typecheck` 保持 exit 0；最后 `pnpm build` 验证

## 3. 操作范围（与计划书第 4 节一致，摘要重申）

- 可写：`jeecg-team-web/src/modules/admin/**`、`src/api/**`、`src/components/**`（限 DataTable/FormModal 两个新文件）、`src/router/index.ts`（加路由）、`src/stores/**`（最小改动）、`jeecg-team-web/docs/**`
- 只读：`jeecgboot-vue3/**`、`jeecg-boot/**`
- 禁：package.json/pnpm-lock/vite.config 改动；`src/modules/business/**` 及三体系页面；git push；`rm -rf`
- 403/缺端点/规格冲突 → 记进报告的「异常清单」，不自行绕过

## 4. 验收门（每页必过，验证分级如实标注）

- L1：`pnpm typecheck` exit 0
- L2：浏览器走查（http://127.0.0.1:3200 登录 zhoujingxiao 实拍：列表加载/新增/编辑/删除各操作一次，DevTools 网络确认 200 非 401/403）
- L1 不过 = 该页未完成；L2 因工具受限做不了时如实标注「L1 only」并说明

## 5. 回报格式（收工前写入 jeecg-team-web/docs/migration-p0-report.md）

- 每页：实现文件清单（路径+行数）、蒸馏来源（旧三件套路径）、端点清单（实际用到的 URL 数）、验证级别（L1/L2 + 证据）、取舍点（与旧规格的差异及理由）
- 异常清单：403/缺端点/规格冲突/任何偏离计划书之处
- 通用件说明：DataTable/FormModal 的 props 契约（P1 波复用方依赖此）
- 已知风险与建议（留给 P1 波）

## 6. 预算口径

单波单代理。中途发现范围外缺口（如端点 403 需配权限），停止该点并记入异常清单，不自行扩张。
