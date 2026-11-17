<script setup lang="ts">
// 资料中心 / 共享文档（工具组）—— owner: juice094
// 上传：/sys/common/upload（jeecg 原生，真实可用）
// 文件列表：jeecg 无原生"文件中心"列表 API，MinIO 接入（M2）前先记录本次上传，历史列表待 MinIO。
import { ref } from 'vue';
import { PhUpload, PhFilePdf, PhFileDoc } from '@phosphor-icons/vue';
import http from '@/utils/http';

interface UploadedFile {
  name: string;
  uploader: string;
  dir: string;
  size: string;
  time: string;
  path?: string;
}

const files = ref<UploadedFile[]>([]);
const uploading = ref(false);
const uploadMsg = ref('');
const fileInput = ref<HTMLInputElement | null>(null);

async function onFilePicked(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  uploading.value = true;
  uploadMsg.value = '';
  try {
    const formData = new FormData();
    formData.append('file', file);
    const data: any = await http.post('/sys/common/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const path = data?.result?.path || data?.message || '';
    files.value.unshift({
      name: file.name,
      uploader: '我',
      dir: '本次会话上传',
      size: `${(file.size / 1024).toFixed(1)} KB`,
      time: new Date().toLocaleString('zh-CN', { hour12: false }),
      path,
    });
    uploadMsg.value = '上传成功（存储于 jeecg 本地/OSS，M2 迁 MinIO）';
  } catch (e: any) {
    uploadMsg.value = e.message || '上传失败';
  } finally {
    uploading.value = false;
    input.value = '';
  }
}
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">资料中心 / 共享文档</span>
      <div style="display: flex; align-items: center; gap: 10px">
        <span v-if="uploadMsg" style="font-size: 12px; color: var(--text-muted)">{{ uploadMsg }}</span>
        <button class="btn btn-primary" :disabled="uploading" @click="fileInput?.click()">
          <PhUpload :size="16" /> {{ uploading ? '上传中…' : '上传文件' }}
        </button>
        <input ref="fileInput" type="file" style="display: none" @change="onFilePicked" />
      </div>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>文件名</th><th>上传人</th><th>所属目录</th><th>文件大小</th><th>更新时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="!files.length">
            <td colspan="6" style="text-align: center; color: var(--text-muted)">
              暂无文件（历史文件列表随 MinIO 接入启用，M2）
            </td>
          </tr>
          <tr v-for="f in files" :key="f.name + f.time">
            <td>
              <PhFilePdf v-if="f.name.endsWith('.pdf')" :size="18" style="color: var(--danger); vertical-align: -4px" />
              <PhFileDoc v-else :size="18" style="color: #2b579a; vertical-align: -4px" />
              {{ f.name }}
            </td>
            <td>{{ f.uploader }}</td>
            <td>{{ f.dir }}</td>
            <td>{{ f.size }}</td>
            <td>{{ f.time }}</td>
            <td><a class="btn-text" :href="f.path" target="_blank">下载</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
