<script setup lang="ts">
// Bug 反馈（工具组）—— owner: juice094
// 后端：/team/bug/** CRUD 骨架（Kimi Code 随本批交付）
import { reactive, ref } from 'vue';

const form = reactive({ desc: '' });
const submitted = ref(false);

function submit() {
  if (!form.desc.trim()) return;
  submitted.value = true;
  // TODO: POST /team/bug/add
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
        <p>点击上传截图</p>
      </div>
    </div>
    <button class="btn btn-primary" style="width: 100%" @click="submit">提交反馈</button>
    <p v-if="submitted" style="color: var(--success); font-size: 13px; margin-top: 12px">已记录（mock），后端接口对接后生效。</p>
  </div>
</template>
