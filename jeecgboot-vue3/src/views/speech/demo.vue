<template>
  <div style="padding: 20px; max-width: 900px">
    <a-space>
      <!-- 录音按钮，绑定toggle启停函数 -->
      <a-button type="primary" :danger="recording" @click="toggle">
        {{ recording ? '停止录音' : '开始录音' }}
      </a-button>
      <!-- 录音计时展示 -->
      <span v-if="recording" style="color: #f5222d">● 录音中 {{ seconds }}s</span>
      <a-upload :before-upload="onFile" :show-upload-list="false" accept=".wav,.mp3,.webm">
        <a-button>上传音频文件</a-button>
      </a-upload>
      <a-tag v-if="status" :color="tagColor">{{ statusText }}</a-tag>
    </a-space>

    <a-divider />
    <a-spin :spinning="status === 'pending' || status === 'processing'">
      <h3>转写全文</h3>
      <p style="white-space: pre-wrap">{{ transcribeText || '暂无' }}</p>
      <h3>会议纪要</h3>
      <div v-if="summary">
        <p><b>主题：</b>{{ summary.meetingTopic }}</p>
        <p><b>要点：</b></p>
        <ul><li v-for="(k, i) in summary.keyPoints" :key="i">{{ k }}</li></ul>
        <p><b>待办：</b></p>
        <ul><li v-for="(t, i) in summary.todoList" :key="i">{{ t.owner }} - {{ t.task }}</li></ul>
      </div>
      <p v-else>暂无</p>
      <a-alert v-if="failReason" type="error" :message="failReason" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { defHttp } from '/@/utils/http/axios';

// 变量声明区
const recording = ref(false);    // 录音标记
const seconds = ref(0);         // 计时秒数
const status = ref('');         // 任务状态
const transcribeText = ref(''); // 转写文本
const summary = ref<any>(null); // 纪要对象
const failReason = ref('');     // 失败信息
let recorder: MediaRecorder | null = null; // 录音实例
let chunks: Blob[] = [];        // 音频分片缓存
let timer: any = null;
let tick: any = null;

const tagColor = computed(() => {
  const m: any = { success: 'green', fail: 'red', processing: 'blue', pending: 'orange', uploading: 'orange' };
  return m[status.value] || 'orange';
});
const statusText = computed(() => {
  const m: any = { uploading: '上传中', pending: '排队中', processing: '转写中', success: '完成', fail: '失败' };
  return m[status.value] || status.value;
});
// 录音启停函数，这一块就是toggle，从这里开始录音逻辑
async function toggle() {
  if (recording.value) {
    recorder?.stop();
    recording.value = false;
    clearInterval(tick);
    return;
  }
  try {
    // 麦克风权限申请
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    chunks = [];
    // 创建MediaRecorder音频采集器
    recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
    // 收集音频分片
    recorder.ondataavailable = (e) => { if (e.data.size > 0) chunks.push(e.data); };
// 录音停止回调：拼接分片、生成文件、自动提交
    recorder.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop());
      const blob = new Blob(chunks, { type: 'audio/webm' });
      await submit(new File([blob], `rec-${Date.now()}.webm`, { type: 'audio/webm' }));
    };
    recorder.start();
    recording.value = true;
    seconds.value = 0;
    tick = setInterval(() => seconds.value++, 1000);
  } catch (e: any) {
    failReason.value = '麦克风不可用：' + (e?.message || e);
  }
}
// 上传本地文件触发
async function onFile(file: File) {
  await submit(file);
  return false;
}
// 统一提交方法：上传音频 + 创建任务
async function submit(file: File) {
  status.value = 'uploading';
  transcribeText.value = '';
  summary.value = null;
  failReason.value = '';
  clearInterval(timer);

  const fd = new FormData();
  fd.append('file', file);
  const up: any = await defHttp.post({ url: '/speech/audio/upload', data: fd }, { isTransformResponse: false });
  if (!up.success) {
    status.value = 'fail';
    failReason.value = up.message || '上传失败';
    return;
  }

  const created: any = await defHttp.post(
    { url: '/speech/task/create', data: { audioFilePath: up.result.audioFilePath, fileId: up.result.fileId } },
    { isTransformResponse: false }
  );
  if (!created.success) {
    status.value = 'fail';
    failReason.value = created.message || '创建任务失败';
    return;
  }
  // 开启轮询
  poll(created.result.taskId);
}
// 轮询函数，每隔2s拉取任务结果，渲染VO返回的数据
function poll(taskId: string) {
  status.value = 'pending';
  timer = setInterval(async () => {
    const r: any = await defHttp.get({ url: `/speech/task/${taskId}` }, { isTransformResponse: false });
    if (!r.success || !r.result) return;
    const d = r.result;
    status.value = d.taskStatus;
    transcribeText.value = d.transcribeText || '';
    summary.value = d.summaryJson || null;
    failReason.value = d.failReason || '';
    // 任务结束清除轮询
    if (d.taskStatus === 'success' || d.taskStatus === 'fail') clearInterval(timer);
  }, 2000);
}
</script>
