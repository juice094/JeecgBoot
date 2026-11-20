/**
 * 相对时间格式化（对齐 GitHub "opened 1d ago" 口径）
 * 输入为后端返回的日期/时间字符串（yyyy-MM-dd[ HH:mm:ss]）或 Date。
 */

const UNITS: Array<{ limit: number; divisor: number; suffix: string }> = [
  { limit: 60, divisor: 1, suffix: 's ago' },
  { limit: 3600, divisor: 60, suffix: 'm ago' },
  { limit: 86400, divisor: 3600, suffix: 'h ago' },
  { limit: 2592000, divisor: 86400, suffix: 'd ago' },
];

/** 解析失败或未来时间超过 60s 时回退绝对日期 yyyy-MM-dd */
export function timeAgo(input?: string | Date | null): string {
  if (!input) return '';
  const date = input instanceof Date ? input : new Date(String(input).replace(' ', 'T'));
  if (isNaN(date.getTime())) return String(input).slice(0, 10);
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 0) return formatDate(date);
  for (const unit of UNITS) {
    if (seconds < unit.limit) return Math.floor(seconds / unit.divisor) + unit.suffix;
  }
  return formatDate(date);
}

function formatDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}
