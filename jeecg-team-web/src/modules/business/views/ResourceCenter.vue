<script setup lang="ts">
// 资料中心 / 共享文档（工具组）—— owner: juice094
// Word 文档同步业务已作为 Jeecg 模块提供，前端页面通过 Jeecg 8081 同源 iframe 嵌入，避免在本模块重复实现
// OnlyOffice、版本、审计、WebSocket、WebDAV 等能力。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { currentTheme, THEME_CHANGE_EVENT, type Theme } from '@/utils/theme';

const WORD_SYNC_PATH = '/word-sync/index.html';

const theme = ref<Theme>(currentTheme());
// 首次加载通过 query 带主题，避免 iframe 先按深色渲染再闪一下
const wordSyncUrl = `${WORD_SYNC_PATH}?embed=1&theme=${theme.value}`;
const externalUrl = computed(() => new URL(`${WORD_SYNC_PATH}?embed=1&theme=${theme.value}`, window.location.origin).href);
const iframeRef = ref<HTMLIFrameElement | null>(null);

function syncThemeToIframe() {
  iframeRef.value?.contentWindow?.postMessage(
    { type: 'team-theme', theme: theme.value },
    window.location.origin
  );
}

function onThemeChange(event: Event) {
  const next = (event as CustomEvent<{ theme?: Theme }>).detail?.theme;
  if (next === 'light' || next === 'dark') {
    theme.value = next;
    syncThemeToIframe();
  }
}

onMounted(() => window.addEventListener(THEME_CHANGE_EVENT, onThemeChange));
onBeforeUnmount(() => window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange));
</script>

<template>
  <div class="card resource-embed-card">
    <div class="card-header resource-embed-header">
      <span class="card-title">资料中心 / 共享文档</span>
      <div class="resource-embed-actions">
        <span class="resource-embed-badge">Word 文档同步服务</span>
        <a class="btn-text" :href="externalUrl" target="_blank" rel="noopener">新窗口打开</a>
      </div>
    </div>
    <iframe
      ref="iframeRef"
      class="resource-embed-frame"
      :src="wordSyncUrl"
      title="Word 文档同步工作台"
      allow="clipboard-read; clipboard-write"
      @load="syncThemeToIframe"
    />
  </div>
</template>

<style scoped>
.resource-embed-card {
  padding: 0;
  overflow: hidden;
}

.resource-embed-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  margin: 0;
  border-bottom: 1px solid var(--border-color);
}

.resource-embed-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.resource-embed-badge {
  font-size: 12px;
  color: var(--primary);
  background: var(--primary-light);
  border: 1px solid var(--primary-border);
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.resource-embed-frame {
  display: block;
  width: 100%;
  height: calc(100vh - 190px);
  min-height: 720px;
  border: 0;
  /* 跟随主题令牌的衬底色——禁止硬编码浅色值（深色基线下会露出亮块） */
  background: var(--bg-muted);
}
</style>
