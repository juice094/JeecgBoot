# jeecg-team-web — 团队协作效率工作台（前端）

依据技术总监指示的正向道路，独立于 jeecgboot-vue3 的新前端工程。
技术栈与参考来源见文末。

## 三人分工（模块目录即职责边界）

| 模块 | 范围 | 路由 | 负责人 |
|---|---|---|---|
| `src/modules/message/` | 用户与消息（聊天工作台） | `/message/**` | **qingyanjunzi** |
| `src/modules/business/` | 表单与审批（业务与工具模块：任务看板/申请表单） | `/business/**` | **juice094** |
| `src/modules/admin/` | 组织租户管理（管理端模块） | `/admin/**` | **Ym3699** |

铁律：**只改自己模块目录**和路由表中自己那段；跨模块需求开 issue 协商。各模块的 README.md 写了范围、接口约定和约束。

## 开发

```bash
pnpm install
pnpm dev        # http://localhost:3200，/jeecg-boot 已代理到本机后端 8081
```

- HTTP 出口：`src/utils/http.ts`（axios 实例，token 从 localStorage 读取，X-Access-Token 头；成功码与键名约定见 `src/constants/app.ts`）
- 主题变量：`src/styles/variables.css`（青绿主色 #127a7b，提取自参考原型；令牌分语义/阶梯两层）
- 依赖版本与 jeecgboot-vue3 同栈（Vite 8 / Vue 3.5 / plugin-vue 6），本机 Node 24 + pnpm 11 已验证

## 联调基线（docs/13 §五，issue #12）

- **开发联调基准 = 本机 8081**（rog-x jeecg 后端；本机 8080 被 nacos 容器占用，故 dev 代理指向 8081，见 `vite.config.ts`）
- 云 VM `100.78.65.37:8080` **仅**是接口文档合同入口（Knife4j，nginx basic auth），不作业务联调目标
- 端口全景（3100 原生前端 / 3200 本前端 / 8065 Mattermost / 8090 OnlyOffice / 13306 VM MySQL）见 docs/13 §五

## 协作流程

1. 各自从 `pilot/w1-trim`（开发主分支）切功能分支：`feature/<模块>-<功能>`，如 `feature/message-chat-list`
2. PR 描述带「文档影响」；改接口必须改后端 Knife4j 注解
3. 联调以云 VM 常驻接口文档为合同：http://100.78.65.37:8080/jeecg-boot/doc.html（tailnet 内）

## 参考来源（本工程的设计输入）

| 文件 | 用途 |
|---|---|
| `Downloads/deepseek_html_20261116_609ed8.html` | 单文件 UI 原型（青绿主题/侧栏布局），主题变量已提取进 variables.css |
| `Downloads/70c6147d-…-副本.htm` | 同原型的完整版（含图表等重资源），页面细化时可对照 |
| `Downloads/新建 文本文档.txt` | 重构方案（组件拆分建议），views/components 结构参考 |
| 微信手绘草图 | 分工模型原始依据，已与 docs/10 分工对齐 |

## 与设计文档的关系

架构/里程碑见 pilot/design 分支：`docs/10-foundation-design.md`、`docs/11-direction-pivot-2026-11-16.md`、`ROADMAP.md`（M3 业务基础功能即本工程的主战场）
