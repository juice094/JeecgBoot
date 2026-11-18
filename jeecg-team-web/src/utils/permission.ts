import { useAuthStore } from '@/stores/auth';

/**
 * 权限码判断（管理域按钮显隐用）。
 * 数据源：登录用户信息里的 permissionList / permissions 数组（jeecg 若下发）。
 * 未下发时一律放行（渐进增强）：显隐只是体验，后端 sys_role_permission 仍须校验。
 * 权限码照抄旧前端 X.data.ts，如 'system:user:add'。
 */
export function hasPermission(code: string): boolean {
  const auth = useAuthStore();
  const info = auth.userInfo as Record<string, unknown>;
  const list = (info.permissionList ?? info.permissions) as unknown;
  if (!Array.isArray(list) || list.length === 0) return true;
  return list.includes(code);
}

/** 组件内便捷用法：const { hasPermission } = usePermission(); */
export function usePermission() {
  return { hasPermission };
}
