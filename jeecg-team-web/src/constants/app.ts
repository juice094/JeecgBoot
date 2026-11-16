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
