import { defineStore } from 'pinia';
import http from '@/utils/http';
import { getPermCode } from '@/api/sys';
import { RESULT_SUCCESS_CODE, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/constants/app';

interface UserInfo {
  id?: string;
  username?: string;
  realname?: string;
  avatar?: string;
}

/** localStorage 键名：权限码列表缓存 */
const PERM_STORAGE_KEY = 'team_perms';

/**
 * 登录态。token 存 localStorage(TOKEN_STORAGE_KEY)，http 拦截器自动带 X-Access-Token。
 * 成功码约定见 constants/app.ts（RESULT_SUCCESS_CODE），token 在 result.token。
 * 登录后拉取 /sys/permission/getPermCode 存入 permissionList（按钮显隐与侧栏过滤的数据源）；
 * permissionList 为 null 表示未加载/加载失败，hasPermission 对此兜底放行（渐进增强）。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_STORAGE_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_STORAGE_KEY) || '{}') as UserInfo,
    permissionList: JSON.parse(localStorage.getItem(PERM_STORAGE_KEY) || 'null') as string[] | null,
  }),
  getters: {
    isLogin: (s) => !!s.token,
    displayName: (s) => s.userInfo.realname || s.userInfo.username || '未登录',
  },
  actions: {
    async login(username: string, password: string) {
      const data: any = await http.post('/sys/login', { username, password });
      if (data.code !== RESULT_SUCCESS_CODE || !data.result?.token) {
        throw new Error(data.message || '登录失败');
      }
      this.token = data.result.token;
      this.userInfo = data.result.userInfo || { username };
      localStorage.setItem(TOKEN_STORAGE_KEY, this.token);
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(this.userInfo));
      await this.loadPermissions();
    },
    /** 拉取当前用户按钮权限码；失败时 permissionList 置 null（hasPermission 兜底放行，不阻断使用） */
    async loadPermissions() {
      try {
        this.permissionList = await getPermCode();
      } catch {
        this.permissionList = null;
      }
      localStorage.setItem(PERM_STORAGE_KEY, JSON.stringify(this.permissionList));
    },
    logout() {
      this.token = '';
      this.userInfo = {};
      this.permissionList = null;
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      localStorage.removeItem(PERM_STORAGE_KEY);
    },
  },
});
