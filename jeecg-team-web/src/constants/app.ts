/* 应用级常量区 —— 跨模块共享的魔法值统一收口（docs/13 §二.5，issue #11） */

/** localStorage 键名：登录 token（http 拦截器与 auth store 共用，禁止各处硬编码字符串） */
export const TOKEN_STORAGE_KEY = 'team_token';

/** localStorage 键名：登录用户信息缓存 */
export const USER_STORAGE_KEY = 'team_user';

/** HTTP 请求超时（毫秒） */
export const REQUEST_TIMEOUT_MS = 15000;

/** jeecg-boot 统一响应包装的成功码（与后端 Result<T> 约定一致） */
export const RESULT_SUCCESS_CODE = 200;

/** localStorage 键名：上次打开的工具模式页面（双模式"回到上次页面"用） */
export const WORKBENCH_LAST_PATH_KEY = 'team_last_tool_path';

/** localStorage 键名：亮暗主题偏好（'light' | 'dark'，缺省 dark——宿裁定深色为对比度基线） */
export const THEME_STORAGE_KEY = 'team_theme';

/** 主题缺省值：深色（GitHub Dark 令牌为基线，浅色为可选切换） */
export const THEME_DEFAULT: 'light' | 'dark' = 'dark';

/** 系统用户标配角色 id（sys_role.role_code=test，全员最小权限，新用户默认分配） */
export const DEFAULT_ROLE_ID = 'ee8626f80f7c2619917b6236f3a7f02b';

/** 业务主管角色 id（sys_role.role_code=team_manager，team 域审批/审计/邮件日志管理） */
export const MANAGER_ROLE_ID = 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2';
