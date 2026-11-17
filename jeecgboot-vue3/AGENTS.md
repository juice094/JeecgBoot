# AGENTS.md

本文件为 AI 编码助手（Codex、Claude Code、Kimi Code 等）提供本仓库（`jeecgboot-vue3/`）的工作指引，假定读者对本项目一无所知。

## 沟通约定

- 除用户明确要求其他语言外，一律优先使用简体中文回答。

## 项目概述

JeecgBoot Vue3 前端（当前版本 3.9.6，见 `package.json`），企业级低代码平台的管理端前端。技术栈：

- Vue 3.5（`<script setup>` 为主）+ TypeScript 5.9 + Vite 8（Rolldown/Oxc 工具链）
- Ant Design Vue 4 + Pinia 3 + Vue Router 5 + vue-i18n 9
- Less 样式、UnoCSS、vite-plugin-svg-icons / unplugin-icons 图标方案
- 在 Vben Admin 基础上深度定制，包含 JeecgBoot 专属能力：Online 在线表单/报表低代码模块（`src/views/super/online/`）、AI 应用与 RAG（`src/views/super/airag/`）、JVxeTable 高性能表格（`vxe-table` 封装）、代码生成器、流程设计（`@logicflow`）、Electron 桌面端（`electron/`，可选）等

后端为 JeecgBoot（Spring Boot），开发期默认通过代理访问 `http://localhost:8081/jeecg-boot`（见 `.env.development` 的 `VITE_PROXY`，默认值以文件为准）。

环境要求：Node.js 20+、pnpm 10+（`package.json` engines 为 `^18 || >=20`；`pnpm-workspace.yaml` 中配置了 `shamefullyHoist: true` 及依赖构建脚本白名单，勿随意删除）。

## 常用命令

```bash
pnpm install            # 安装依赖（pnpm 10+/11+）
pnpm dev                # 开发服务器，端口 3100（.env 的 VITE_PORT），启用 mock 与后端代理
pnpm build              # 生产构建到 dist/（后置 postBuild.ts 与 copyChat.ts 脚本）
pnpm build:docker       # docker 模式构建（--mode docker）
pnpm build:dockercloud  # 微服务网关模式构建（--mode dockercloud）
pnpm build:report       # 带 rollup-plugin-visualizer 产物分析报告
pnpm preview            # 构建后本地预览
pnpm clean:cache        # 清理 Vite 缓存
pnpm gen:icon           # 重新生成图标数据
pnpm batch:prettier     # 全量 Prettier 格式化 src/（仅在需要大范围格式化时执行）
```

无统一 `lint`/`test` 脚本，按需单独执行：

```bash
npx eslint src/path/to/file.vue            # ESLint（无全量脚本）
npx stylelint "src/**/*.{vue,less,css}"    # Stylelint
npx jest                                   # 运行 Jest 测试（见“测试”一节）
```

## 路径别名

- `/@/`（或 `@/`）→ `src/`
- `/#/`（或 `#/`）→ `types/`

代码中通篇使用带斜杠的 `/@/` 前缀，请遵循该约定。

## 目录结构

| 目录 | 说明 |
| --- | --- |
| `src/api/` | 按域组织的 HTTP 请求函数：`sys/`、`common/`、`demo/`、`model/` |
| `src/components/` | 通用组件（Table、Form、Modal、Drawer、Icon、Tinymce、Markdown 等）及 JeecgBoot 专属组件（`jeecg/`：JVxeTable、验证码、第三方应用等）；`registerGlobComp.ts` 全局注册 |
| `src/hooks/` | Composition API：`web/`、`core/`、`setting/`、`system/`（含 `useListPage`）、`jeecg/`、`component/`、`event/` |
| `src/layouts/` | 布局：`default/`、`iframe/`、`page/` |
| `src/router/` | 路由与守卫；后端权限驱动动态路由（`guard/`、`menus/`、`routes/`） |
| `src/store/` | Pinia store：`app`、`user`、`permission`、`multipleTab`、`locale`、`lock`、`errorLog` |
| `src/views/` | 页面：`system/`（系统管理）、`super/`（online 低代码、airag AI）、`dashboard/`、`monitor/`、`demo/`、`report/`、`openapi/`、`sys/` |
| `src/utils/` | HTTP 客户端（`http/axios/`）、鉴权/加密、字典工具等 |
| `src/settings/` | 全局项目、组件、设计设置（分页参数等见 `componentSetting.ts`） |
| `src/locales/` | i18n：`zh_CN`、`en` |
| `src/enums/`、`src/directives/`、`src/logics/`、`src/design/`、`src/qiankun/`、`src/electron/` | 常量枚举；`v-auth` 等指令；应用初始化逻辑；样式变量；qiankun 微前端接入；electron 桥接 |
| `build/` | Vite 插件（`build/vite/plugin/`）、代理（`build/vite/proxy.ts`）、构建后脚本（`build/script/`）、图标生成（`build/generate/`）。注意：是构建工具源码，不是产物目录 |
| `mock/` | vite-plugin-mock 数据（`sys/`、`demo/`），由 `.env.development` 的 `VITE_USE_MOCK` 控制 |
| `tests/` | Jest 测试与 mock |
| `electron/` | Electron 桌面端源码（打包说明见 `electron.md`，package.json 中相关脚本默认未启用） |
| `types/` | 全局类型声明（`/#/` 别名指向此处） |

## 页面开发约定

`src/views/` 下每个功能模块通常包含：

- `index.vue`：主列表页
- `*.data.ts`：表格列定义与表单 schema（`FormSchema[]`）
- `*.api.ts`：接口请求（`enum Api { ... }` 集中维护 URL）
- `*Modal.vue` / `*Drawer.vue`：详情与编辑弹窗

列表页使用 `BasicTable` + `useTable`：

```ts
import { BasicTable, useTable } from '/@/components/Table';

const [registerTable] = useTable({ api, columns, formConfig: { schemas } });
```

弹窗使用 `useModal` / `useDrawer`：

```ts
const [registerModal, { openModal }] = useModal();
openModal(true, { isUpdate: true, record });
```

标准 CRUD 页优先使用 `src/hooks/system/useListPage`。表单 schema 的 `component` 支持 Ant Design Vue 组件及自定义组件（`JDictSelectTag`、`JSearchSelect`、`JVxeTable` 等）。

## HTTP 客户端

`src/utils/http/axios/` 的 `defHttp` 基于 Axios 封装：

- 拦截器注入 Token；MD5 请求签名；多租户请求头
- 统一按 `result` 字段解包后端 `Result<T>` 响应

API 函数惯例：

```ts
enum Api {
  List = '/sys/user/list',
}

export const list = (params) => defHttp.get({ url: Api.List, params });
```

改动 `defHttp` 时不得绕过/破坏 MD5 签名与鉴权逻辑。

## 鉴权与权限

- Token 经 auth 工具存于 localStorage
- `src/router/guard/permissionGuard.ts` 拉取用户信息并注册后端动态菜单；权限模式为 `BACK`（后端驱动）
- 按钮级权限：`v-auth="'system:user:add'"` 指令或 `usePermission()` hook
- 分页参数为 `pageNo`/`pageSize`，响应取 `records`/`total`（配置在 `src/settings/componentSetting.ts`）

## 环境变量

- `.env`：端口（3100）、标题、单点登录（CAS）、qiankun 开关、文件预览地址等
- `.env.development`：mock 开关、`VITE_PROXY` 代理（后端 `/jeecgboot` → `http://localhost:8081/jeecg-boot`）、`VITE_GLOB_DOMAIN_URL`、`VITE_GLOB_API_URL`、qiankun 子应用配置、在线文档版本（wps/onlyoffice 等）、iconify 在线/本地模式
- `.env.production` / `.env.docker` / `.env.dockercloud` / `.env.prod_electron`：各构建模式对应配置
- 读取后经 `build/utils.ts` 的 `wrapperEnv` 转换类型（`"true"/"false"` 转布尔、`VITE_PROXY` 转 JSON 数组）

## 构建系统

`vite.config.ts` 将插件装配委托给 `build/vite/plugin/`（html 模板、mock、gzip、SVG 雪碧、动态主题 `theme-plugin/`、qiankun 微前端、PWA、visualizer 等）。构建产物到 `dist/`（`build/constant.ts` 的 `OUTPUT_DIR`），并运行 `build/script/postBuild.ts`、`copyChat.ts` 两个后置脚本。手动分包逻辑在 `vite.config.ts` 的 `manualChunks` 函数中（Vite 8 下必须是函数形式）。

## 低代码（Online）与 AI 模块

- Online 在线表单/报表为 JeecgBoot 低代码核心：由元数据配置生成 CRUD 页面，配置存于数据库，仓库代码中读不到具体表单配置。完整 schema 参考 `src/views/super/online/cgform/online-form-schema.md`（表元数据、字段配置、控件类型、校验规则、JS 增强）
- AI 相关页面在 `src/views/super/airag/`，流程/编排能力来自 `@jeecg/aiflow`（注意该依赖被 `optimizeDeps.exclude` 显式排除，勿改动）

## 代码风格

- Prettier（`prettier.config.js`）：行宽 150、单引号、分号、ES5 trailing comma、2 空格缩进、`endOfLine: auto`
- ESLint（`.eslintrc.js`）：`vue/vue3-recommended` + `@typescript-eslint/recommended` + `plugin:prettier/recommended`（Prettier 校验规则本身已关闭，格式化交给 Prettier）
- Stylelint（`stylelint.config.js`）：`stylelint-config-standard` + `stylelint-order`，兼容 `v-deep`/`deep`/`global`
- 未使用的变量/参数加 `_` 前缀（ESLint 规则如此要求）
- 新组件优先 `<script setup>`；样式用 Less
- 提交信息遵循 commitlint conventional 规范（`commitlint.config.js`，type 枚举含 feat/fix/perf 等；`package.json` 有 `husky:install` 与 lint-staged 依赖，但当前未安装 git hooks 时不会生效）

## 测试

- Jest 29 + ts-jest + `@vue/test-utils`，配置在 `jest.config.mjs`（jsdom 环境、`/@/` 别名映射、`tests/__mocks__/` 静态资源 mock）
- `package.json` 中没有 `test` 脚本；需要时手动 `npx jest`
- `tests/test.spec.ts` 目前只是一个占位用例（Vue 组件测试尚未启用，见文件内 TODO）；新增测试放在 `tests/` 下

## 部署

- `pnpm build` 产出 `dist/`，由 nginx 托管；根目录 `Dockerfile`（单体，代理到 `jeecg-boot-system:8080`）与 `Dockerfile.cloud`（微服务，代理到网关 `jeecg-boot-gateway:9999`）均内嵌 nginx 配置，含 history 路由回退与后端反代
- PWA 支持见 `PWA-README.md`；Electron 桌面打包说明见 `electron.md`（相关脚本默认未启用）

## 安全注意事项

- 前端渲染富文本/ Markdown 须经 `xss` 库过滤
- 用户输入在表单层（schema 校验规则）与后端双重校验，前端校验不可作为安全边界
- 不绕过 `defHttp` 的签名与鉴权链路直接发请求

## 排除项

`.claudeignore` 是例行排除的权威来源。除非任务明确针对或验证需要，避免在以下路径做大范围搜索/读取/编辑：

- `.git/`、`.svn/`、`.idea/`、`.vscode/` 及 IDE 元数据（`*.iml` 等）
- `node_modules/`、`dist/`、`target/`、`docker/data/`
- `backup/`、`.history/`、`.cursor/`、`doc/`、`docs/`
- OS 文件与日志产物（`*.log`、`Thumbs.db`、`*.zip`、`*.qqy`、`代码修改.log`、`代码修改日志` 等）

注意：`.claudeignore` 把 `build/` 和 `mock/` 列为排除项，但本前端中 `build/` 是 Vite 插件源码、`mock/` 是 mock 数据——避免在其中做宽泛扫描，涉及构建工具或 mock 时可定点读写。
