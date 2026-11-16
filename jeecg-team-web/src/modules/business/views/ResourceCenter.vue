<script setup lang="ts">
// 资料中心 / 共享文档（工具组）—— owner: juice094
// 后端：MinIO + KKFileView 为 M2 目标（ROADMAP）；当前列表为 mock
import { PhUpload, PhFilePdf, PhFileDoc } from '@phosphor-icons/vue';

const files = [
  { name: '2026年招生简章最终版.pdf', type: 'pdf', uploader: '李国成', dir: '市场部共享', size: '12.5 MB', time: '11-12 14:00' },
  { name: '教师绩效考核制度_V2.docx', type: 'doc', uploader: '黄茜茜', dir: '人事管理制度', size: '345 KB', time: '11-10 09:30' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">资料中心 / 共享文档</span>
      <button class="btn btn-primary"><PhUpload :size="16" /> 上传文件</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>文件名</th><th>上传人</th><th>所属目录</th><th>文件大小</th><th>更新时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="f in files" :key="f.name">
            <td>
              <PhFilePdf v-if="f.type === 'pdf'" :size="18" style="color: var(--danger); vertical-align: -4px" />
              <PhFileDoc v-else :size="18" style="color: #2b579a; vertical-align: -4px" />
              {{ f.name }}
            </td>
            <td>{{ f.uploader }}</td>
            <td>{{ f.dir }}</td>
            <td>{{ f.size }}</td>
            <td>{{ f.time }}</td>
            <td><a class="btn-text">下载</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
