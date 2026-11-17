// 系统组织 API —— jeecg 原生（sys/sysDepart、sys/user）
import http from '@/utils/http';

export interface DepartNode {
  id: string;
  parentId?: string;
  departName?: string;
  orgCode?: string;
  children?: DepartNode[];
}

export interface SysUser {
  id: string;
  username: string;
  realname?: string;
  phone?: string;
  status?: number;
  orgCode?: string;
}

/** 部门树（全部） */
export async function listDepartTree(): Promise<DepartNode[]> {
  const data: any = await http.get('/sys/sysDepart/queryTreeList');
  const result = data?.result;
  const rows: DepartNode[] = Array.isArray(result) ? result : result?.records ?? result?.list ?? [];
  // queryTreeList 可能包一层 { departInfoList, ... } 结构，做兜底
  return rows.length ? rows : result?.departInfoList ?? [];
}

/** 用户分页列表 */
export function listUsers(params: { pageNo?: number; pageSize?: number; [k: string]: unknown } = {}) {
  return http.get<any, { result: { records: SysUser[]; total: number } }>('/sys/user/list', { params });
}
