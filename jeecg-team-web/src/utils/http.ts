import axios from 'axios';
import { REQUEST_TIMEOUT_MS, RESULT_SUCCESS_CODE, TOKEN_STORAGE_KEY } from '@/constants/app';

/**
 * 统一 HTTP 出口。
 * baseURL 指向 jeecg-boot 后端（vite 代理到 8081，见 vite.config.ts）。
 * 后端响应统一包 Result<T>：{ code, message, result }，成功码约定见 constants/app.ts 的 RESULT_SUCCESS_CODE。
 */
const http = axios.create({
  baseURL: '/jeecg-boot',
  timeout: REQUEST_TIMEOUT_MS,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) config.headers['X-Access-Token'] = token;
  return config;
});

http.interceptors.response.use(
  (resp) => resp.data,
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络异常';
    return Promise.reject(new Error(msg));
  }
);

export default http;
