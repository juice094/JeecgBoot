// 认证域 API —— 登录相关的匿名接口（忘记密码/短信验证码）
// 来源：jeecgboot-vue3/src/api/sys/user.ts + src/views/system/loginmini/MiniForgotpad.vue
// 注意：/sys/sms 在 jeecg.signUrls 名单内，走 http.ts 统一加的 X-Sign/X-TIMESTAMP 签名头
import http from '@/utils/http';
import { RESULT_SUCCESS_CODE } from '@/constants/app';

/** jeecg 统一响应包装（http 拦截器已解包外层 axios，这里再解 Result<T>） */
interface Result<T = any> {
  code?: number;
  success?: boolean;
  message?: string;
  result?: T;
}

function unwrap<T = any>(data: Result<T>, fallbackMsg: string): T {
  // 后端匿名接口有的用 code=200 有的用 success=true，两者兼容
  if (data?.code === RESULT_SUCCESS_CODE || data?.success === true) {
    return data.result as T;
  }
  throw new Error(data?.message || fallbackMsg);
}

/** 发送短信验证码（smsmode=2 忘记密码模板；dev 环境验证码打印在后端日志，见 jeecg.smsMock） */
export function sendForgotPasswordSms(mobile: string): Promise<void> {
  return http.post<any, Result>('/sys/sms', { mobile, smsmode: '2' }).then((d) => unwrap(d, '短信验证码发送失败'));
}

/** 校验短信验证码，返回绑定的用户名 */
export function verifyPhoneCode(phone: string, smscode: string): Promise<{ username: string; smscode: string }> {
  return http
    .post<any, Result<{ username: string; smscode: string }>>('/sys/user/phoneVerification', { phone, smscode })
    .then((d) => unwrap(d, '手机验证码校验失败'));
}

/** 重置密码（后端为 GET + query；验证码错 5 次作废） */
export function resetPasswordByPhone(params: { username: string; password: string; smscode: string; phone: string }): Promise<void> {
  return http.get<any, Result>('/sys/user/passwordChange', { params }).then((d) => unwrap(d, '重置密码失败'));
}
