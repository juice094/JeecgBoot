/**
 * 语音转写 API —— 对接 jeecg 后端 /speech（SpeechTaskController）
 *
 * 异步链路：/audio/upload → /task/create → 轮询 /task/{id}
 * 同步链路：/audio/transcribe（不落库，供实时转写、任意输入框语音按钮）
 * 原音频：  /audio/raw（按任务的 audioFilePath 回源，支持下载与试听）
 *
 * 响应统一为 Result<T>：{ code, message, result }，成功码 200。
 */
import http from '@/utils/http';

export interface SpeechUploadResult {
  fileId: string;
  audioFilePath: string;
  fileSize: number;
  fileFormat: string;
}

export interface SpeechSummary {
  meetingTopic?: string;
  keyPoints?: string[];
  todoList?: string[];
}

export interface SpeechTaskDetail {
  taskId: string;
  taskStatus: string;
  transcribeText?: string | null;
  summaryJson?: SpeechSummary | Record<string, unknown> | null;
  failReason?: string | null;
  createTime?: string;
  finishTime?: string;
  audioFilePath?: string;
}

/** 历史列表项：SpeechTask 实体投影，注意 summaryJson 在列表里是字符串 */
export interface SpeechTaskItem {
  id: string;
  audioFileId?: string;
  audioFilePath?: string | null;
  taskStatus?: string;
  transcribeText?: string | null;
  summaryJson?: string | null;
  failReason?: string | null;
  createTime?: string;
  finishTime?: string | null;
}

/** 上传音频（≤10MB，wav/mp3/webm） */
export async function uploadAudio(file: File): Promise<SpeechUploadResult> {
  const fd = new FormData();
  fd.append('file', file);
  const data: any = await http.post('/speech/audio/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
  if (data?.code !== 200) throw new Error(data?.message || '上传失败');
  return data.result as SpeechUploadResult;
}

/** 创建转写任务 */
export async function createSpeechTask(audioFilePath: string, fileId: string) {
  const data: any = await http.post('/speech/task/create', { audioFilePath, fileId });
  if (data?.code !== 200) throw new Error(data?.message || '创建任务失败');
  return data.result as { taskId: string; taskStatus: string };
}

/** 查询任务详情 */
export async function getSpeechTaskDetail(taskId: string): Promise<SpeechTaskDetail> {
  const data: any = await http.get(`/speech/task/${taskId}`);
  if (data?.code !== 200) throw new Error(data?.message || '查询任务失败');
  return data.result as SpeechTaskDetail;
}

/** 分页查询历史任务 */
export async function listSpeechTasks(
  params: {
    pageNo?: number;
    pageSize?: number;
    status?: string;
    keyword?: string;
    startTime?: string;
    endTime?: string;
  } = {}
): Promise<{ records: SpeechTaskItem[]; total: number }> {
  const data: any = await http.get('/speech/task/list', { params });
  if (data?.code !== 200) throw new Error(data?.message || '查询历史失败');
  const result = data.result ?? {};
  return {
    records: Array.isArray(result.records) ? result.records : [],
    total: Number(result.total ?? 0),
  };
}

/** 软删除任务 */
export async function deleteSpeechTask(taskId: string) {
  const data: any = await http.delete(`/speech/task/${taskId}`);
  if (data?.code !== 200) throw new Error(data?.message || '删除失败');
}

/** 同步转写：不落库，直接返回文本（实时转写分段调用） */
export async function transcribeSync(file: File): Promise<{ text: string; elapsedMs: number }> {
  const attempt = async () => {
    const fd = new FormData();
    fd.append('file', file);
    const data: any = await http.post('/speech/audio/transcribe', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    });
    if (data?.code !== 200) throw new Error(data?.message || '转写失败');
    return data.result as { text: string; elapsedMs: number };
  };
  try {
    return await attempt();
  } catch (e: any) {
    // 本地 ASR 会把空闲的 keep-alive 连接提前关掉（默认 5 秒），后端复用这条
    // 死连接时必然报 10053，表现为「转写失败：Request failed」。重发一次走新连接即可恢复。
    const msg = String(e?.message || '');
    if (/Request failed|10053|中止|Network Error/i.test(msg)) {
      await new Promise((r) => setTimeout(r, 200));
      return await attempt();
    }
    throw e;
  }
}

/** 原音频地址：inline=true 用于试听，false 用于下载 */
export function rawAudioUrl(path: string, inline = false) {
  return `/jeecg-boot/speech/audio/raw?path=${encodeURIComponent(path)}&inline=${inline}`;
}
