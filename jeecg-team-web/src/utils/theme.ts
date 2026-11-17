/* 亮暗主题切换 —— 外部协作代理暗色令牌落地后由 showcase-dev 补切换模式（2026-11-17，宿指派）
 * 机制：data-theme 挂在 <html> 上，variables.css 双令牌块按属性覆盖；localStorage 持久化。
 * 默认值 dark：宿评审结论——浅色令牌对比度撑不起信息层级，深色为基线（docs 未编号，见会话记录）。
 */
import { THEME_DEFAULT, THEME_STORAGE_KEY } from '@/constants/app';

export type Theme = 'light' | 'dark';

function normalize(value: string | null): Theme {
  return value === 'light' ? 'light' : THEME_DEFAULT;
}

export function currentTheme(): Theme {
  return normalize(document.documentElement.dataset.theme ?? localStorage.getItem(THEME_STORAGE_KEY));
}

/** 应用主题到 <html>；beforeMount 前调用可防闪烁（main.ts 已接） */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(THEME_STORAGE_KEY, theme);
}

export function initTheme(): void {
  applyTheme(currentTheme());
}

export function toggleTheme(): Theme {
  const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  return next;
}
