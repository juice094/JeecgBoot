// 团队业务 API —— /team/**（jeecg-module-team，自研）
import http from '@/utils/http';

export interface PageResult<T> {
  records: T[];
  total: number;
}

export interface Approval {
  id: string;
  code: string;
  category: string;
  title: string;
  reason?: string;
  status: 'pending' | 'approved' | 'rejected';
  applicantName?: string;
  createTime?: string;
}

export interface Bug {
  id: string;
  description: string;
  status: 'open' | 'processing' | 'resolved' | 'closed';
  reporterName?: string;
  createTime?: string;
}

/** 审批单分页；status 传 pending/approved/rejected，空为全部 */
export function listApprovals(params: { status?: string; applicantId?: string; pageNo?: number; pageSize?: number }) {
  return http.get<any, { result: PageResult<Approval> }>('/team/approval/list', { params });
}

export function submitApproval(body: {
  category: string;
  title: string;
  reason?: string;
  startDate?: string;
  endDate?: string;
}) {
  return http.post('/team/approval/submit', {
    approval: body,
    steps: [
      { approverName: '部门主管', approverRole: '一级审批' },
      { approverName: '分管副总', approverRole: '二级审批' },
    ],
  });
}

export function approveApproval(id: string) {
  return http.post(`/team/approval/approve?id=${id}`);
}

export function rejectApproval(id: string, comment?: string) {
  const qs = comment ? `?id=${id}&comment=${encodeURIComponent(comment)}` : `?id=${id}`;
  return http.post(`/team/approval/reject${qs}`);
}

export function addBug(description: string) {
  return http.post('/team/bug/add', { description });
}

export function listBugs(params: { pageNo?: number; pageSize?: number } = {}) {
  return http.get<any, { result: PageResult<Bug> }>('/team/bug/list', { params });
}
