// 公告 API —— jeecg 原生（sys/annountCement，注意上游拼写如此）
import http from '@/utils/http';

export interface Announcement {
  id: string;
  titile?: string;
  msgContent?: string;
  sender?: string;
  priority?: string;
  sendTime?: string;
}

/** 当前用户的有效公告列表 */
export async function listAnnouncements(): Promise<Announcement[]> {
  const data: any = await http.get('/sys/annountCement/listByUser');
  const result = data?.result;
  return Array.isArray(result) ? result : result?.records ?? [];
}
