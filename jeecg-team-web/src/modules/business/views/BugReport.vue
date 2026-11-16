<script setup lang="ts">
// Bug 反馈（工具组）—— owner: juice094
// 数据：/team/bug/add 真实接口
import { reactive, ref } from 'vue';
import { addBug } from '@/api/team';

const form = reactive({ desc: '' });
const submitting = ref(false);
const feedback = ref('');

async function submit() {
  if (!form.desc.trim() || submitting.value) return;
  submitting.value = true;
  feedback.value = '';
  try {
    await addBug(form.desc.trim());
    feedback.value = '反馈已提交，状态 open，处理进度可在后续版本跟踪';
    form.desc = '';
  } catch (e: any) {
    feedback.value = e.message || '提交失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="card" style="max-width: 800px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">系统 Bug 反馈</span>
    </div>
    <div class="form-group">
      <div class="form-label">问题描述 <span class="req">*</span></div>
      <textarea v-model="form.desc" class="form-control" placeholder="请详细描述您遇到的问题或功能异常..."></textarea>
    </div>
    <div class="form-group">
      <div class="form-label">截图附件</div>
      <div class="upload-box" style="padding: 15px">
        <p>点击上传截图（文件存储 MinIO 接入后启用，M2）</p>
      </div>
    </div>
    <button class="btn btn-primary" style="width: 100%" :disabled="submitting" @click="submit">
      {{ submitting ? '提交中…' : '提交反馈' }}
    </button>
    <p v-if="feedback" class="feedback">{{ feedback }}</p>
  </div>
</template>

<style scoped>
.feedback {
  color: var(--success);
  font-size: 13px;
  margin-top: 12px;
}
</style>
