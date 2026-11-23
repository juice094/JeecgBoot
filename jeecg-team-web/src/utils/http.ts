import axios from 'axios';
import { REQUEST_TIMEOUT_MS, RESULT_SUCCESS_CODE, TOKEN_STORAGE_KEY } from '@/constants/app';
import { getSign, getTimestamp } from './sign';

/**
 * 统一 HTTP 出口。
 * baseURL 分两种运行形态：
 * - Web（vite dev/build）：同源相对路径 /jeecg-boot，由 vite 代理到本机 8081（见 vite.config.ts）
 * - Tauri 桌面壳：无代理，直连本机后端绝对地址（CORS 需在 jeecg 侧放行 tauri 源，见 docs/16 后续）
 * 后端响应统一包 Result<T>：{ code, message, result }，成功码约定见 RESULT_SUCCESS_CODE。
 * 所有请求带 X-Sign/X-TIMESTAMP 签名头（与后端 SignAuthInterceptor 对齐，见 utils/sign.ts）；
 * 后端仅对 jeecg.signUrls 名单内接口校验签名，其余接口忽略这两个头。
 */
const isTauri = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

const http = axios.create({
  baseURL: isTauri ? 'http://127.0.0.1:8081/jeecg-boot' : '/jeecg-boot',
  timeout: REQUEST_TIMEOUT_MS,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) config.headers['X-Access-Token'] = token;
  // 请求拦截器先于 transformRequest 执行，POST 的 data 此时仍是对象
  config.headers['X-TIMESTAMP'] = getTimestamp();
  config.headers['X-Sign'] = getSign(config.url || '', config.params, config.data);
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
