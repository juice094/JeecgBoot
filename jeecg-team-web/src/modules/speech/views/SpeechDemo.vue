<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  PhMicrophone,
  PhStop,
  PhUploadSimple,
  PhDownloadSimple,
  PhTrash,
  PhClockCounterClockwise,
  PhMagnifyingGlass,
  PhArrowClockwise,
  PhFileText,
  PhPlay,
} from '@phosphor-icons/vue';
import {
  createSpeechTask,
  deleteSpeechTask,
  getSpeechTaskDetail,
  listSpeechTasks,
  transcribeSync,
  uploadAudio,
  type SpeechSummary,
  type SpeechTaskDetail,
  type SpeechTaskItem,
} from '@/api/speech';
import http from '@/utils/http';

const MAX_BYTES = 10 * 1024 * 1024;
const canRecord = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
const SEGMENT_MS = 7000;

type Tab = 'record' | 'upload' | 'history';
type Phase = 'idle' | 'uploading' | 'queued' | 'transcribing' | 'done' | 'error';

const tabs: Array<{ key: Tab; label: string }> = [
  { key: 'record', label: '录音转写' },
  { key: 'upload', label: '文件上传' },
  { key: 'history', label: '历史记录' },
];

const tab = ref<Tab>('record');

/* ---------------- 录音 ---------------- */
const recording = ref(false);
const recordSeconds = ref(0);
const bars = ref<number[]>(new Array(16).fill(0));
const realtimeOn = ref(false);
const realtimeText = ref('');
const realtimeBusy = ref(false);

/** 主录制：从开始到停止一路不停，专门负责落盘完整音频 */
let recorder: MediaRecorder | null = null;
/** 实时预览用的独立分段录制器，与主录制互不干扰 */
let segRecorder: MediaRecorder | null = null;
/** 主录制的累积数据，整段录制期间绝不清空 */
let chunks: Blob[] = [];
let stream: MediaStream | null = null;
let secTimer: number | null = null;
let segTimer: number | null = null;
let audioCtx: AudioContext | null = null;
let analyser: AnalyserNode | null = null;
let rafId = 0;
/** 本次 stop 是否为"整段结束"（false 表示分片，stop 后立刻续录） */
let finalStop = false;

/* ---------------- 任务状态 ---------------- */
const phase = ref<Phase>('idle');
const errMsg = ref('');
const audioBytes = ref(0);

const PHASE_TEXT: Record<Phase, string> = {
  idle: '',
  uploading: '上传音频…',
  queued: '任务已创建，等待转写…',
  transcribing: 'Whisper 转写中…',
  done: '',
  error: '',
};
const phaseText = computed(() => PHASE_TEXT[phase.value]);

/* ---------------- 上传 ---------------- */
const pickedFile = ref<File | null>(null);
const pickedName = ref('');
const dragOver = ref(false);

/* ---------------- 详情 ---------------- */
const detail = ref<SpeechTaskDetail | null>(null);
const detailOpen = ref(false);
const detailLoading = ref(false);
const audioSrc = ref('');
const summary = computed<SpeechSummary | null>(() => {
  const s = detail.value?.summaryJson;
  return s && typeof s === 'object' ? (s as SpeechSummary) : null;
});
const keyPoints = computed(() => summary.value?.keyPoints ?? []);
const todoList = computed(() => summary.value?.todoList ?? []);

/* ---------------- 历史 ---------------- */
const records = ref<SpeechTaskItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const historyLoading = ref(false);
const keyword = ref('');
const statusFilter = ref('');
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

watch(tab, (t) => {
  if (t === 'history' && records.value.length === 0) void loadHistory();
});

/* ================= 录音相关 ================= */

function startLevelMeter(s: MediaStream) {
  try {
    audioCtx = new AudioContext();
    const src = audioCtx.createMediaStreamSource(s);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 256;
    src.connect(analyser);
    const buf = new Uint8Array(analyser.frequencyBinCount);
    const loop = () => {
      if (!analyser) return;
      analyser.getByteTimeDomainData(buf);
      const n = bars.value.length;
      const step = Math.max(1, Math.floor(buf.length / n));
      for (let i = 0; i < n; i++) {
        let sum = 0;
        for (let j = 0; j < step; j++) {
          const v = (buf[i * step + j] - 128) / 128;
          sum += v * v;
        }
        bars.value[i] = Math.min(1, Math.sqrt(sum / step) * 3.2);
      }
      rafId = requestAnimationFrame(loop);
    };
    loop();
  } catch {
    /* 电平可视化失败不影响录音 */
  }
}

function stopLevelMeter() {
  if (rafId) cancelAnimationFrame(rafId);
  rafId = 0;
  analyser = null;
  if (audioCtx) {
    void audioCtx.close();
    audioCtx = null;
  }
  bars.value = new Array(bars.value.length).fill(0);
}

function stopTracks() {
  stream?.getTracks().forEach((t) => t.stop());
  stream = null;
}

async function startRecord() {
  if (recording.value) return;
  if (!canRecord) {
    errMsg.value = '当前页面不是 localhost / https，浏览器禁用麦克风，请用 http://127.0.0.1:3200 打开';
    return;
  }
  errMsg.value = '';
  phase.value = 'idle';
  realtimeText.value = '';
  audioBytes.value = 0;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (e: any) {
    errMsg.value = '麦克风授权失败：' + (e?.message || String(e));
    return;
  }
  startLevelMeter(stream);
  const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
    ? 'audio/webm;codecs=opus'
    : 'audio/webm';
  recorder = new MediaRecorder(stream, { mimeType: mime });
  chunks = [];
  finalStop = false;

  recorder.ondataavailable = (ev: BlobEvent) => {
    if (ev.data && ev.data.size > 0) {
      chunks.push(ev.data);
      audioBytes.value += ev.data.size;
    }
  };
  recorder.onstop = () => {
    if (!finalStop) return;
    // 只有整段真正结束时才落盘：此时 chunks 里是从第一秒到停止的全部数据
    const blob = new Blob(chunks, { type: 'audio/webm' });
    chunks = [];
    stopTracks();
    stopLevelMeter();
    if (blob.size > 0) {
      const f = new File([blob], `rec_${Date.now()}.webm`, { type: 'audio/webm' });
      void submit(f);
    }
  };
  // 关键：主录制不按 7 秒切片，全程连续录，停止时一次落盘
  recorder.start();
  recording.value = true;
  recordSeconds.value = 0;
  secTimer = window.setInterval(() => {
    recordSeconds.value += 1;
  }, 1000);
  if (realtimeOn.value) startSegmentTimer();
}

function startSegmentTimer() {
  stopSegmentTimer();
  segTimer = window.setInterval(() => {
    void recordOneSegment();
  }, SEGMENT_MS);
}

/** 实时预览用独立录音器：每 7 秒单独录一小段合法的 webm，送后端转写 */
async function recordOneSegment() {
  if (!realtimeOn.value || finalStop || !recording.value || !stream) return;
  if (realtimeBusy.value) return; // 上一段还没转完就跳过，避免请求堆积导致 Request failed
  const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
    ? 'audio/webm;codecs=opus'
    : 'audio/webm';
  try {
    const seg = new MediaRecorder(stream, { mimeType: mime });
    segRecorder = seg;
    const segChunks: Blob[] = [];
    seg.ondataavailable = (ev: BlobEvent) => {
      if (ev.data && ev.data.size > 0) segChunks.push(ev.data);
    };
    seg.onstop = () => {
      const blob = new Blob(segChunks, { type: 'audio/webm' });
      if (segRecorder === seg) segRecorder = null;
      if (blob.size > 0) void pushRealtime(blob);
    };
    seg.start();
    window.setTimeout(() => {
      try {
        if (seg.state !== 'inactive') seg.stop();
      } catch {
        /* 忽略 */
      }
    }, SEGMENT_MS);
  } catch {
    /* 忽略 */
  }
}

function stopSegmentTimer() {
  if (segTimer !== null) {
    window.clearInterval(segTimer);
    segTimer = null;
  }
}

function stopRecord() {
  stopSegmentTimer();
  if (secTimer !== null) {
    window.clearInterval(secTimer);
    secTimer = null;
  }
  recording.value = false;
  finalStop = true;
  try {
    if (segRecorder && segRecorder.state !== 'inactive') segRecorder.stop();
  } catch {
    /* 忽略 */
  }
  segRecorder = null;
  try {
    recorder?.stop();
  } catch {
    /* 忽略 */
  }
  recorder = null;
}

async function pushRealtime(blob: Blob) {
  if (realtimeBusy.value) return;
  realtimeBusy.value = true;
  try {
    const file = new File([blob], `seg_${Date.now()}.webm`, { type: 'audio/webm' });
    const r = await transcribeSync(file);
    if (r.text) {
      const cur = realtimeText.value;
      const sep = cur && !cur.endsWith('\n') ? '\n' : '';
      realtimeText.value = cur + sep + r.text;
    }
  } catch (e: any) {
    const msg = e?.message || '未知错误';
    realtimeText.value += `\n[分段失败] ${msg}\n`;
  } finally {
    realtimeBusy.value = false;
  }
}

/* ================= 提交链路 ================= */

function startPoll(taskId: string) {
  phase.value = 'transcribing';
  let timer: number | null = null;
  const stop = () => {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  };
  const tick = async () => {
    try {
      const d = await getSpeechTaskDetail(taskId);
      if (d.taskStatus === 'success') {
        stop();
        phase.value = 'done';
        detail.value = d;
        detailOpen.value = true;
        await loadHistory();
      } else if (d.taskStatus === 'fail') {
        stop();
        phase.value = 'error';
        errMsg.value = d.failReason || '转写失败';
      }
    } catch (e: any) {
      stop();
      phase.value = 'error';
      errMsg.value = e?.message || '查询任务失败';
    }
  };
  void tick();
  timer = window.setInterval(() => void tick(), 2000);
}

async function submit(file: File) {
  if (file.size > MAX_BYTES) {
    phase.value = 'error';
    errMsg.value = `音频 ${(file.size / 1024 / 1024).toFixed(2)}MB 超过 10MB 上限`;
    return;
  }
  errMsg.value = '';
  phase.value = 'uploading';
  try {
    const up = await uploadAudio(file);
    phase.value = 'queued';
    const task = await createSpeechTask(up.audioFilePath, up.fileId);
    startPoll(task.taskId);
  } catch (e: any) {
    phase.value = 'error';
    errMsg.value = e?.message || '提交失败';
  }
}

/* ================= 上传 ================= */

function onPickFile(ev: Event) {
  const f = (ev.target as HTMLInputElement).files?.[0] ?? null;
  pickedFile.value = f;
  pickedName.value = f ? f.name : '';
  errMsg.value = '';
}

function onDrop(ev: DragEvent) {
  dragOver.value = false;
  const f = ev.dataTransfer?.files?.[0] ?? null;
  if (!f) return;
  pickedFile.value = f;
  pickedName.value = f.name;
  errMsg.value = '';
}

function submitPicked() {
  if (!pickedFile.value) {
    errMsg.value = '请先选择一个音频文件';
    return;
  }
  void submit(pickedFile.value);
}

/* ================= 历史 ================= */

async function loadHistory() {
  historyLoading.value = true;
  try {
    const r = await listSpeechTasks({
      pageNo: pageNo.value,
      pageSize,
      status: statusFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
    });
    records.value = r.records;
    total.value = r.total;
  } catch (e: any) {
    errMsg.value = e?.message || '历史查询失败';
  } finally {
    historyLoading.value = false;
  }
}

function gotoPage(p: number) {
  if (p < 1 || p > totalPages.value) return;
  pageNo.value = p;
  void loadHistory();
}

async function openDetail(taskId: string) {
  detailLoading.value = true;
  detailOpen.value = true;
  if (audioSrc.value) {
    URL.revokeObjectURL(audioSrc.value);
    audioSrc.value = '';
  }
  try {
    detail.value = await getSpeechTaskDetail(taskId);
  } catch (e: any) {
    errMsg.value = e?.message || '详情查询失败';
  } finally {
    detailLoading.value = false;
  }
}

async function removeTask(item: SpeechTaskItem) {
  if (!window.confirm(`确认删除该记录？\n${item.createTime || ''}  ${taskLabel(item)}`)) return;
  try {
    await deleteSpeechTask(item.id);
    await loadHistory();
  } catch (e: any) {
    errMsg.value = e?.message || '删除失败';
  }
}

/* ================= 音频下载 / 试听 ================= */

async function fetchAudioBlob(path: string): Promise<Blob> {
  return await http.get<any, Blob>('/speech/audio/raw', {
    params: { path, inline: true },
    responseType: 'blob',
    timeout: 60000,
  });
}

async function downloadAudio(item: { audioFilePath?: string | null }) {
  if (!item.audioFilePath) {
    errMsg.value = '该任务没有记录音频路径';
    return;
  }
  try {
    const blob = await fetchAudioBlob(item.audioFilePath);
    const name = item.audioFilePath.split(/[\\/]/).pop() || 'audio.webm';
    triggerDownload(blob, name);
  } catch (e: any) {
    errMsg.value = e?.message || '下载失败';
  }
}

async function playAudio(item: { audioFilePath?: string | null }) {
  if (!item.audioFilePath) {
    errMsg.value = '该任务没有记录音频路径';
    return;
  }
  try {
    const blob = await fetchAudioBlob(item.audioFilePath);
    if (audioSrc.value) URL.revokeObjectURL(audioSrc.value);
    audioSrc.value = URL.createObjectURL(blob);
  } catch (e: any) {
    errMsg.value = e?.message || '加载音频失败';
  }
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/* ================= 导出 ================= */

function resolveSummary(d: SpeechTaskDetail): SpeechSummary | null {
  const s = d.summaryJson;
  return s && typeof s === 'object' ? (s as SpeechSummary) : null;
}

function buildMarkdown(d: SpeechTaskDetail): string {
  const s = resolveSummary(d);
  const lines: string[] = [
    `# ${s?.meetingTopic || '会议纪要'}`,
    '',
    `- 任务ID：${d.taskId}`,
    `- 创建时间：${d.createTime || '-'}`,
    `- 完成时间：${d.finishTime || '-'}`,
    `- 原音频：${d.audioFilePath || '-'}`,
    '',
    '## 转写全文',
    '',
    d.transcribeText || '（无）',
    '',
    '## 会议纪要',
    '',
  ];
  (s?.keyPoints ?? []).forEach((p) => lines.push(`- ${p}`));
  if (s?.todoList?.length) {
    lines.push('', '### 待办', '');
    s.todoList.forEach((t) => lines.push(`- [ ] ${t}`));
  }
  return lines.join('\n');
}

function buildPlainText(d: SpeechTaskDetail): string {
  const s = resolveSummary(d);
  const lines: string[] = [
    '会议主题：' + (s?.meetingTopic || '-'),
    '创建时间：' + (d.createTime || '-'),
    '完成时间：' + (d.finishTime || '-'),
    '原音频：' + (d.audioFilePath || '-'),
    '',
    '【转写全文】',
    d.transcribeText || '（无）',
    '',
    '【会议纪要】',
  ];
  (s?.keyPoints ?? []).forEach((p, i) => lines.push(`${i + 1}. ${p}`));
  if (s?.todoList?.length) {
    lines.push('', '【待办】');
    s.todoList.forEach((t, i) => lines.push(`${i + 1}. ${t}`));
  }
  return lines.join('\n');
}

function exportTask(format: 'md' | 'txt' | 'json') {
  const d = detail.value;
  if (!d) return;
  const base = `纪要_${d.taskId.slice(0, 8)}_${(d.createTime || '').replace(/[: ]/g, '').slice(0, 12)}`;
  if (format === 'json') {
    triggerDownload(
      new Blob([JSON.stringify(d, null, 2)], { type: 'application/json;charset=utf-8' }),
      `${base}.json`
    );
    return;
  }
  const content = format === 'md' ? buildMarkdown(d) : buildPlainText(d);
  const mime = format === 'md' ? 'text/markdown' : 'text/plain';
  triggerDownload(new Blob([content], { type: `${mime};charset=utf-8` }), `${base}.${format}`);
}

/* ================= 展示辅助 ================= */

const STATUS_META: Record<string, { label: string; cls: string }> = {
  pending: { label: '排队中', cls: 'wait' },
  processing: { label: '转写中', cls: 'wait' },
  success: { label: '已完成', cls: 'ok' },
  fail: { label: '失败', cls: 'bad' },
};

function statusOf(s?: string) {
  return STATUS_META[s || ''] ?? { label: s || '未知', cls: 'wait' };
}

function taskLabel(item: SpeechTaskItem) {
  return (item.transcribeText || '').slice(0, 40) || (item.audioFilePath?.split(/[\\/]/).pop() ?? '未命名音频');
}

function summaryTopic(item: SpeechTaskItem) {
  if (!item.summaryJson) return '';
  try {
    const o = JSON.parse(item.summaryJson);
    return typeof o?.meetingTopic === 'string' ? o.meetingTopic : '';
  } catch {
    return '';
  }
}

function mmss(s: number) {
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function closeDetail() {
  detailOpen.value = false;
  if (audioSrc.value) {
    URL.revokeObjectURL(audioSrc.value);
    audioSrc.value = '';
  }
}

onBeforeUnmount(() => {
  stopSegmentTimer();
  if (secTimer !== null) window.clearInterval(secTimer);
  stopLevelMeter();
  stopTracks();
  try {
    recorder?.stop();
  } catch {
    /* 忽略 */
  }
  if (audioSrc.value) URL.revokeObjectURL(audioSrc.value);
});
</script>

<template>
  <div class="sp-page">
    <header class="sp-head">
      <div class="sp-title">
        <h1>录音转写与会议纪要</h1>
        <p>录制或上传音频 → faster-whisper 转写 → Ollama 生成纪要，结果存入 speech_meeting_task</p>
      </div>
      <div class="sp-tabs">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="sp-tab"
          :class="{ active: tab === t.key }"
          @click="tab = t.key"
        >
          {{ t.label }}
        </button>
      </div>
    </header>

    <!-- ========== 录音 ========== -->
    <section v-if="tab === 'record'" class="sp-card">
      <div class="rec-stage">
        <button class="rec-btn" :class="{ on: recording }" @click="recording ? stopRecord() : startRecord()">
          <PhStop v-if="recording" :size="26" weight="fill" />
          <PhMicrophone v-else :size="28" />
        </button>
        <div class="rec-info">
          <p class="rec-time">{{ mmss(recordSeconds) }}</p>
          <p class="rec-hint">
            {{ recording ? '正在录音，点击方块结束并生成纪要' : '点击麦克风开始录音' }}
          </p>
          <p v-if="audioBytes" class="rec-size">已录 {{ (audioBytes / 1024).toFixed(0) }} KB</p>
        </div>
      </div>

      <div class="meter">
        <span
          v-for="(b, i) in bars"
          :key="i"
          class="meter-bar"
          :style="{ height: 6 + b * 44 + 'px', opacity: recording ? 1 : 0.22 }"
        ></span>
      </div>

      <label class="rt-switch">
        <input v-model="realtimeOn" type="checkbox" :disabled="recording" />
        <span>实时转写（每 7 秒出一段预览文字，不落库）</span>
      </label>

      <div v-if="realtimeOn && (recording || realtimeText)" class="rt-box">
        <p class="rt-title">
          实时预览
          <span v-if="realtimeBusy" class="rt-badge">识别中…</span>
        </p>
        <p class="rt-text">{{ realtimeText || '（等待第一段结果）' }}</p>
        <p class="rt-note">实时文字仅作预览；停止录音后会走完整转写并生成纪要。</p>
      </div>

      <p v-if="phaseText" class="sp-status sp-status--busy">{{ phaseText }}</p>
      <p v-if="errMsg" class="sp-status sp-status--error">{{ errMsg }}</p>
    </section>

    <!-- ========== 上传 ========== -->
    <section v-if="tab === 'upload'" class="sp-card">
      <div
        class="drop-zone"
        :class="{ over: dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <PhUploadSimple :size="30" class="drop-icon" />
        <p class="drop-title">把音频拖到这里，或点击选择</p>
        <p class="drop-note">支持 wav / mp3 / webm，单个文件不超过 10MB</p>
        <input
          type="file"
          class="drop-input"
          accept=".wav,.mp3,.webm,audio/wav,audio/mpeg,audio/webm"
          @change="onPickFile"
        />
      </div>

      <div v-if="pickedName" class="picked">
        <PhFileText :size="18" />
        <span class="picked-name">{{ pickedName }}</span>
        <span class="picked-size">{{ pickedFile ? (pickedFile.size / 1024).toFixed(0) : 0 }} KB</span>
        <button
          class="btn btn--primary"
          :disabled="phase === 'uploading' || phase === 'queued' || phase === 'transcribing'"
          @click="submitPicked"
        >
          上传并转写
        </button>
      </div>

      <p v-if="phaseText" class="sp-status sp-status--busy">{{ phaseText }}</p>
      <p v-if="errMsg" class="sp-status sp-status--error">{{ errMsg }}</p>
    </section>

    <!-- ========== 历史 ========== -->
    <section v-if="tab === 'history'" class="sp-card">
      <div class="his-toolbar">
        <div class="his-search">
          <PhMagnifyingGlass :size="16" />
          <input v-model="keyword" placeholder="搜索转写文本关键字" @keyup.enter="pageNo = 1; loadHistory()" />
        </div>
        <select v-model="statusFilter" class="his-select" @change="pageNo = 1; loadHistory()">
          <option value="">全部状态</option>
          <option value="success">已完成</option>
          <option value="processing">转写中</option>
          <option value="pending">排队中</option>
          <option value="fail">失败</option>
        </select>
        <button class="btn" :disabled="historyLoading" @click="loadHistory()">
          <PhArrowClockwise :size="16" /> 刷新
        </button>
        <span class="his-total">共 {{ total }} 条</span>
      </div>

      <div v-if="historyLoading" class="his-empty">加载中…</div>
      <div v-else-if="records.length === 0" class="his-empty">
        <PhClockCounterClockwise :size="28" />
        <p>还没有记录，去录一段或上传一个音频</p>
      </div>
      <table v-else class="his-table">
        <thead>
          <tr>
            <th style="width: 152px">创建时间</th>
            <th style="width: 84px">状态</th>
            <th>内容</th>
            <th style="width: 70px">字数</th>
            <th style="width: 214px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.id">
            <td class="mono">{{ item.createTime || '-' }}</td>
            <td>
              <span class="badge" :class="'badge--' + statusOf(item.taskStatus).cls">
                {{ statusOf(item.taskStatus).label }}
              </span>
            </td>
            <td>
              <div class="cell-main">{{ taskLabel(item) }}</div>
              <div v-if="summaryTopic(item)" class="cell-sub">主题：{{ summaryTopic(item) }}</div>
            </td>
            <td class="mono">{{ (item.transcribeText || '').length }}</td>
            <td>
              <div class="ops">
                <button class="op" @click="openDetail(item.id)">详情</button>
                <button class="op" @click="playAudio(item)"><PhPlay :size="14" /> 播放</button>
                <button class="op" @click="downloadAudio(item)"><PhDownloadSimple :size="14" /> 原音频</button>
                <button class="op op--danger" title="删除" @click="removeTask(item)"><PhTrash :size="14" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="total > pageSize" class="pager">
        <button class="btn" :disabled="pageNo <= 1" @click="gotoPage(pageNo - 1)">上一页</button>
        <span class="pager-info">{{ pageNo }} / {{ totalPages }}</span>
        <button class="btn" :disabled="pageNo >= totalPages" @click="gotoPage(pageNo + 1)">下一页</button>
      </div>

      <p v-if="errMsg" class="sp-status sp-status--error">{{ errMsg }}</p>
    </section>

    <!-- ========== 详情 ========== -->
    <div v-if="detailOpen" class="mask" @click.self="closeDetail">
      <div class="drawer">
        <header class="drawer-head">
          <div>
            <h2>{{ summary?.meetingTopic || '转写详情' }}</h2>
            <p class="drawer-sub">
              {{ detail?.createTime || '' }}
              <span v-if="detail?.taskStatus" class="badge" :class="'badge--' + statusOf(detail.taskStatus).cls">
                {{ statusOf(detail.taskStatus).label }}
              </span>
            </p>
          </div>
          <div class="drawer-actions">
            <button class="btn" :disabled="detailLoading || !detail" @click="exportTask('md')">导出 MD</button>
            <button class="btn" :disabled="detailLoading || !detail" @click="exportTask('txt')">导出 TXT</button>
            <button class="btn" :disabled="detailLoading || !detail" @click="exportTask('json')">导出 JSON</button>
            <button class="btn" @click="closeDetail">关闭</button>
          </div>
        </header>

        <div v-if="detailLoading" class="drawer-loading">加载中…</div>

        <div v-else-if="detail" class="drawer-body">
          <div class="audio-row">
            <button class="btn" @click="playAudio(detail)"><PhPlay :size="14" /> 加载音频</button>
            <button class="btn" @click="downloadAudio(detail)">
              <PhDownloadSimple :size="14" /> 下载原音频
            </button>
            <span class="audio-path" :title="detail.audioFilePath || ''">
              {{ detail.audioFilePath || '无音频路径' }}
            </span>
          </div>
          <audio v-if="audioSrc" :src="audioSrc" controls class="audio-player"></audio>

          <h3 class="drawer-h3">转写全文</h3>
          <div class="transcript">{{ detail.transcribeText || '（无内容）' }}</div>

          <h3 class="drawer-h3">会议纪要</h3>
          <p class="topic">主题：{{ summary?.meetingTopic || '未提取到主题' }}</p>
          <ul v-if="keyPoints.length" class="points">
            <li v-for="(p, i) in keyPoints" :key="`k${i}`">{{ p }}</li>
          </ul>
          <p v-else class="muted">未提取到要点</p>
          <p v-if="todoList.length" class="topic">待办：</p>
          <ul v-if="todoList.length" class="points">
            <li v-for="(t, i) in todoList" :key="`t${i}`">{{ t }}</li>
          </ul>
          <details v-if="detail.summaryJson" class="raw">
            <summary>原始 JSON</summary>
            <pre>{{ JSON.stringify(detail.summaryJson, null, 2) }}</pre>
          </details>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sp-page {
  padding: 22px 26px 40px;
  max-width: 1080px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sp-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.sp-title h1 {
  font-size: var(--font-size-2xl);
  color: var(--text-main);
  margin: 0 0 6px;
}

.sp-title p {
  margin: 0;
  font-size: var(--font-size-md);
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 620px;
}

.sp-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: var(--bg-muted);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
}

.sp-tab {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--font-size-md);
  padding: 7px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.sp-tab.active {
  background: var(--primary);
  color: #fff;
}

.sp-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 22px;
  box-shadow: var(--shadow-xs);
}

.rec-stage {
  display: flex;
  align-items: center;
  gap: 26px;
}

.rec-btn {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  border: 2px solid var(--primary);
  background: var(--primary-50);
  color: var(--primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  flex: none;
}

.rec-btn:hover {
  background: var(--primary-100);
}

.rec-btn.on {
  background: var(--danger);
  border-color: var(--danger);
  color: #fff;
  animation: pulse 1.4s infinite;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(248, 81, 73, 0.5); }
  70% { box-shadow: 0 0 0 16px rgba(248, 81, 73, 0); }
  100% { box-shadow: 0 0 0 0 rgba(248, 81, 73, 0); }
}

.rec-info {
  flex: 1;
}

.rec-time {
  margin: 0;
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  color: var(--text-main);
  letter-spacing: 1px;
}

.rec-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-md);
  color: var(--text-muted);
}

.rec-size {
  margin: 4px 0 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.meter {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 52px;
  margin: 18px 0 6px;
}

.meter-bar {
  flex: 1;
  background: linear-gradient(180deg, var(--primary-600), var(--primary));
  border-radius: var(--radius-xs);
  transition: height 0.08s linear, opacity var(--duration-normal);
}

.rt-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-md);
  color: var(--text-regular);
  margin-top: 8px;
  cursor: pointer;
}

.rt-box {
  margin-top: 14px;
  background: var(--bg-muted);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  padding: 14px;
}

.rt-title {
  margin: 0 0 8px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 8px;
}

.rt-badge {
  font-size: var(--font-size-2xs);
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--primary-50);
  color: var(--primary-700);
  border: 1px solid var(--primary-border);
}

.rt-text {
  margin: 0;
  color: var(--text-main);
  font-size: var(--font-size-base);
  line-height: 1.8;
  min-height: 44px;
  white-space: pre-wrap;
}

.rt-note {
  margin: 8px 0 0;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.drop-zone {
  position: relative;
  border: 1.5px dashed var(--border-strong);
  border-radius: var(--radius-lg);
  padding: 34px 20px;
  text-align: center;
  transition: all var(--duration-fast) var(--ease-out);
  background: var(--bg-muted);
}

.drop-zone.over {
  border-color: var(--primary);
  background: var(--primary-50);
}

.drop-icon {
  color: var(--text-muted);
}

.drop-title {
  margin: 10px 0 4px;
  color: var(--text-main);
  font-size: var(--font-size-base);
}

.drop-note {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.drop-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.picked {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 14px;
  background: var(--bg-muted);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-regular);
  font-size: var(--font-size-md);
}

.picked-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-size {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.his-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.his-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-muted);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0 10px;
  height: 34px;
  flex: 1;
  min-width: 200px;
  color: var(--text-muted);
}

.his-search input {
  border: none;
  background: transparent;
  outline: none;
  color: var(--text-main);
  font-size: var(--font-size-md);
  width: 100%;
}

.his-select {
  height: 34px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-strong);
  background: var(--bg-muted);
  color: var(--text-regular);
  font-size: var(--font-size-md);
  padding: 0 8px;
}

.his-total {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.his-empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
  font-size: var(--font-size-md);
}

.his-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-md);
}

.his-table th {
  text-align: left;
  color: var(--text-muted);
  font-weight: 500;
  font-size: var(--font-size-sm);
  padding: 10px;
  border-bottom: 1px solid var(--border-color);
}

.his-table td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--border-color-light);
  color: var(--text-regular);
  vertical-align: top;
}

.his-table tbody tr:hover {
  background: var(--bg-muted);
}

.mono {
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.cell-main {
  color: var(--text-main);
  line-height: 1.6;
}

.cell-sub {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.ops {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.op {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 1px solid var(--border-color);
  background: var(--bg-muted);
  color: var(--text-regular);
  font-size: var(--font-size-xs);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.op:hover {
  border-color: var(--primary);
  color: var(--primary-700);
}

.op--danger:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  border: 1px solid transparent;
  white-space: nowrap;
}

.badge--ok {
  color: var(--success-700);
  background: var(--success-50);
  border-color: var(--success-border);
}

.badge--wait {
  color: var(--warning-700);
  background: var(--warning-50);
  border-color: var(--warning-border);
}

.badge--bad {
  color: var(--danger-700);
  background: var(--danger-50);
  border-color: var(--danger-border);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 18px;
}

.pager-info {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-strong);
  background: var(--bg-muted);
  color: var(--text-regular);
  font-size: var(--font-size-md);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}

.btn:hover:not(:disabled) {
  background: var(--gray-200);
  border-color: var(--primary);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn--primary {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.sp-status {
  margin: 14px 0 0;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
}

.sp-status--busy {
  color: var(--primary-700);
  background: var(--primary-50);
  border: 1px solid var(--primary-border);
}

.sp-status--error {
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}

.mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-backdrop);
  z-index: var(--z-modal);
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: min(680px, 94vw);
  height: 100%;
  background: var(--bg-card);
  border-left: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  animation: slideIn var(--duration-normal) var(--ease-out);
}

@keyframes slideIn {
  from { transform: translateX(28px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.drawer-head {
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.drawer-head h2 {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  color: var(--text-main);
}

.drawer-sub {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.drawer-loading {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
}

.drawer-body {
  padding: 18px 22px 40px;
  overflow-y: auto;
  flex: 1;
}

.audio-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.audio-path {
  flex: 1;
  min-width: 200px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audio-player {
  width: 100%;
  margin-top: 12px;
}

.drawer-h3 {
  margin: 22px 0 10px;
  font-size: var(--font-size-base);
  color: var(--text-main);
  border-left: 3px solid var(--primary);
  padding-left: 8px;
}

.transcript {
  white-space: pre-wrap;
  line-height: 1.85;
  font-size: var(--font-size-base);
  color: var(--text-regular);
  background: var(--bg-muted);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  padding: 14px;
  max-height: 300px;
  overflow-y: auto;
}

.topic {
  margin: 0 0 8px;
  font-size: var(--font-size-md);
  color: var(--text-main);
}

.points {
  margin: 0 0 12px;
  padding-left: 20px;
  color: var(--text-regular);
  font-size: var(--font-size-base);
  line-height: 1.9;
}

.muted {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  margin: 0 0 12px;
}

.raw {
  margin-top: 16px;
}

.raw summary {
  cursor: pointer;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.raw pre {
  margin: 8px 0 0;
  padding: 12px;
  background: var(--bg-muted);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  color: var(--text-regular);
  overflow-x: auto;
}
</style>
