// 系统监控 API —— jeecg 原生 /sys/**（只读口径，来源：jeecgboot-vue3 src/views/monitor/*/ *.api.ts，docs/24 A/B 档）
import http from '@/utils/http';
import type { PageResult } from '@/api/team';

// ---- 日志管理（/sys/log，SysLog 实体字段口径） ----
export interface SysLogRow {
  id: string;
  createTime?: string;
  username?: string;
  logContent?: string;
  logType?: number; // 1=登录日志 2=操作日志
  operateType?: number;
  requestUrl?: string;
  requestType?: string;
  ip?: string;
  costTime?: number;
}

export function listLogs(params: { pageNo?: number; pageSize?: number; username?: string; logType?: number }) {
  return http.get<any, { result: PageResult<SysLogRow> }>('/sys/log/list', { params });
}

// ---- 定时任务（/sys/quartzJob） ----
export interface QuartzJobRow {
  id: string;
  jobClassName?: string;
  cronExpression?: string;
  parameter?: string;
  description?: string;
  status?: number; // 0=停止 1=运行中
}

export function listQuartzJobs(params: { pageNo?: number; pageSize?: number; jobClassName?: string }) {
  return http.get<any, { result: PageResult<QuartzJobRow> }>('/sys/quartzJob/list', { params });
}

// ---- 在线用户（/sys/online，SysUserOnlineVO 口径） ----
export interface OnlineUserRow {
  id: string;
  username?: string;
  realname?: string;
  phone?: string;
  sex?: number; // 1=男 2=女
  /** 会话标识（强退按此字段），注意：id 是用户主键，同用户恒相同，不能当会话ID展示 */
  token?: string;
}

export function listOnlineUsers(params: { pageNo?: number; pageSize?: number; username?: string }) {
  return http.get<any, { result: PageResult<OnlineUserRow> }>('/sys/online/list', { params });
}

// ---- Redis 监控（/sys/actuator/redis，注意：keysSize/memoryInfo 为裸 Map 返回，非 Result 包装） ----
export interface RedisInfoItem {
  key?: string;
  value?: string;
  description?: string;
}

/** key 数量（裸 Map：{ create_time, dbSize }） */
export function fetchRedisKeysSize() {
  return http.get<any, { create_time?: number; dbSize?: number }>('/sys/actuator/redis/keysSize');
}

/** 占用内存字节数（裸 Map：{ used_memory, create_time }；可能为 null） */
export function fetchRedisMemoryInfo() {
  return http.get<any, { used_memory?: string; create_time?: number } | null>('/sys/actuator/redis/memoryInfo');
}

/** Redis 信息明细（Result 包装，List<RedisInfo>） */
export function fetchRedisInfo() {
  return http.get<any, { result: RedisInfoItem[] }>('/sys/actuator/redis/info');
}
