import { defineStore } from 'pinia';
import http from '@/utils/http';
import { RESULT_SUCCESS_CODE, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/constants/app';

interface UserInfo {
  id?: string;
  username?: string;
  realname?: string;
  avatar?: string;
}

/**
 * 登录态。token 存 localStorage(TOKEN_STORAGE_KEY)，http 拦截器自动带 X-Access-Token。
 * 成功码约定见 constants/app.ts（RESULT_SUCCESS_CODE），token 在 result.token。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_STORAGE_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_STORAGE_KEY) || '{}') as UserInfo,
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
    },
    logout() {
      this.token = '';
      this.userInfo = {};
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
    },
  },
});
