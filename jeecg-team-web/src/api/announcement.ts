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

/**
 * 当前用户的有效公告列表。
 * ⚠ 真实返回（代码级合同，doc.html 未覆盖）：result 是 Map——
 *    anntMsgList/anntMsgTotal = 公告；sysMsgList/sysMsgTotal = 通知；数据范围近一月（需求审计 F-01/F-08）。
 *    不是数组、不是 records 结构。
 */
export async function listAnnouncements(): Promise<Announcement[]> {
  const data: any = await http.get('/sys/annountCement/listByUser', { params: { pageSize: 50 } });
  const result = data?.result;
  return result?.anntMsgList ?? (Array.isArray(result) ? result : result?.records ?? []);
}
