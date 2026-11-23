import { useAuthStore } from '@/stores/auth';

/**
 * 权限码判断（管理域按钮显隐与侧栏菜单过滤用）。
 * 数据源：authStore.permissionList（登录后拉 /sys/permission/getPermCode，见 stores/auth.ts）。
 * permissionList 为 null（未加载/加载失败）时一律放行（渐进增强）：显隐只是体验，后端 sys_role_permission 仍须校验。
 * permissionList 为 []（加载成功但无任何权限码）时一律拒绝。
 * 权限码照抄旧前端 X.data.ts，如 'system:user:add'。
 */
export function hasPermission(code: string): boolean {
  const auth = useAuthStore();
  const list = auth.permissionList;
  if (list === null) return true;
  return list.includes(code);
}

/** 组件内便捷用法：const { hasPermission } = usePermission(); */
export function usePermission() {
  return { hasPermission };
}
