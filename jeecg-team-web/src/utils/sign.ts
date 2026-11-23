/**
 * JeecgBoot Sign 签名校验的前端实现，移植自 jeecgboot-vue3 src/utils/encryption/signMd5Utils.js，
 * 与后端 org.jeecg.config.sign.util.SignUtil 的 verifySign 逐字对齐：
 *   参数 = URL 查询参数 + 请求参数（GET:params / 非GET:data），数字与布尔转字符串，
 *   键名升序，剔除 _t，JSON.stringify 后拼签名密钥做 MD5 转大写。
 * 签名密钥与后端 jeecg.signatureSecret 一致（application-dev.yml，公开常量）。
 */
import { md5 } from './md5';

// 签名密钥串(前后端要一致，正式发布请自行修改)
const SIGNATURE_SECRET = 'dd05f1c54d63749eda95f9fa6d49v442a';

type Params = Record<string, unknown>;

/** 数值/布尔统一转字符串，保持与后端加密规则一致 */
function normalizeValue(value: unknown): unknown {
  if (typeof value === 'number' && !Number.isNaN(value)) return value.toString();
  if (typeof value === 'boolean') return value.toString();
  return value;
}

/** 将 url 中请求参数组装成对象（?后面的参数） */
function parseQueryString(url: string): Params {
  const result: Params = {};
  // 获取URL上最后带逗号的参数变量 sys/dict/getDictItems/sys_user,realname,username
  let lastPathVariable = url.substring(url.lastIndexOf('/') + 1);
  if (lastPathVariable.includes(',')) {
    if (lastPathVariable.includes('?')) {
      lastPathVariable = lastPathVariable.substring(0, lastPathVariable.indexOf('?'));
    }
    result['x-path-variable'] = decodeURIComponent(lastPathVariable);
  }
  const questionIndex = url.indexOf('?');
  if (questionIndex < 0) return result;
  const paramString = url.substring(questionIndex + 1);
  for (const pair of paramString.split('&')) {
    const equalIndex = pair.indexOf('=');
    if (equalIndex > 0) {
      result[decodeURIComponent(pair.substring(0, equalIndex))] = normalizeValue(
        decodeURIComponent(pair.substring(equalIndex + 1)),
      );
    }
  }
  return result;
}

function mergeObject(target: Params, source?: Params): Params {
  if (source && Object.keys(source).length > 0) {
    for (const key of Object.keys(source)) {
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        target[key] = normalizeValue(source[key]);
      }
    }
  }
  return target;
}

/**
 * 计算请求签名（大写 MD5）
 * @param url 请求的 url（可含查询参数）
 * @param requestParams GET 请求参数（axios config.params）
 * @param requestBodyParams 请求体参数（axios config.data，对象或已序列化的 JSON 字符串）
 */
export function getSign(url: string, requestParams?: Params, requestBodyParams?: unknown): string {
  let bodyParams: Params | undefined;
  if (typeof requestBodyParams === 'string') {
    try {
      bodyParams = JSON.parse(requestBodyParams);
    } catch {
      bodyParams = undefined;
    }
  } else if (requestBodyParams && typeof requestBodyParams === 'object') {
    bodyParams = requestBodyParams as Params;
  }
  let jsonObj = mergeObject(parseQueryString(url), requestParams);
  if (bodyParams && Object.keys(bodyParams).length > 0) {
    jsonObj = mergeObject(jsonObj, bodyParams);
  }
  // 键名升序（后端 TreeMap 语义）
  const sorted: Params = {};
  for (const key of Object.keys(jsonObj).sort()) {
    sorted[key] = jsonObj[key];
  }
  delete sorted._t;
  return md5(JSON.stringify(sorted) + SIGNATURE_SECRET).toUpperCase();
}

/** 生成 X-TIMESTAMP 头（毫秒时间戳，兼容后端新老格式） */
export function getTimestamp(): number {
  return Date.now();
}
