<script setup lang="ts">
import { computed } from 'vue';
import SidebarMenu from './SidebarMenu.vue';
import TopHeader from './TopHeader.vue';
import ChatWorkbench from '@/modules/message/views/ChatWorkbench.vue';
import { useWorkbenchStore } from '@/stores/workbench';

const workbench = useWorkbenchStore();
const isChatMode = computed(() => workbench.mode === 'chat');
</script>

<template>
  <div class="app-layout">
    <SidebarMenu />
    <div class="app-layout__main">
      <TopHeader />
      <main class="app-layout__content" :class="{ 'app-layout__content--chat': isChatMode }">
        <ChatWorkbench v-if="isChatMode" />
        <router-view v-else v-slot="{ Component }">
          <component :is="Component" class="page-fade" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}

.app-layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-layout__content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* 会话模式：会话工作台占满主区域，不再套内容内边距 */
.app-layout__content--chat {
  padding: 0;
  overflow: hidden;
  display: flex;
}

.page-fade {
  animation: fadeIn 0.3s ease;
}
</style>
