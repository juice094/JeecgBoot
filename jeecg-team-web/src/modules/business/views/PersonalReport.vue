<script setup lang="ts">
// 个人报表（工具组）—— owner: juice094
// 积木报表（JimuReport）嵌入：设计器/列表由 jeecg-system-biz 自带（jimureport-spring-boot4-starter）
// 入口 /jeecg-boot/jmreport/list?token=<token>（jeecg jmreport 集成约定）
import { computed } from 'vue';
import { TOKEN_STORAGE_KEY } from '@/constants/app';

const reportUrl = computed(() => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY) || '';
  return `/jeecg-boot/jmreport/list?token=${encodeURIComponent(token)}`;
});
</script>

<template>
  <div class="report-wrap">
    <iframe :src="reportUrl" class="report-frame" title="个人报表"></iframe>
  </div>
</template>

<style scoped>
.report-wrap {
  height: calc(100vh - 120px);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  background: var(--bg-card);
}
.report-frame {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
</style>
