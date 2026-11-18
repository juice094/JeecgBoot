<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import SidebarMenu from './SidebarMenu.vue';
import TopHeader from './TopHeader.vue';
import ChatWorkbench from '@/modules/message/views/ChatWorkbench.vue';
import { useWorkbenchStore } from '@/stores/workbench';

const route = useRoute();
const workbench = useWorkbenchStore();

// 模式与路由单向派生：/message 永远渲染为会话布局（chat 分支），
// 工具页渲染为 page-fade 分支——聊天只有一种布局，不存在第二条渲染路径
watch(
  () => route.path,
  (path) => {
    if (path === '/message') {
      workbench.setMode('chat');
    } else {
      workbench.setMode('tool');
      workbench.rememberToolPath(path);
    }
  },
  { immediate: true }
);

const isChatMode = computed(() => workbench.mode === 'chat');

// 上下定格页（route.meta.fullHeight）：容器吃满窗口，页面内部自管滚动（如工作邮箱三栏）。
// 与 --chat 同思路，但保留内容区内边距与宽度约束。
const isFullHeight = computed(() => route.meta.fullHeight === true);
</script>

<template>
  <div class="app-layout">
    <SidebarMenu />
    <div class="app-layout__main">
      <TopHeader />
      <main class="app-layout__content" :class="{ 'app-layout__content--chat': isChatMode, 'app-layout__content--pinned': isFullHeight }">
        <ChatWorkbench v-if="isChatMode" />
        <div v-else class="app-layout__inner">
          <router-view v-slot="{ Component }">
            <component :is="Component" class="page-fade" />
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-body);
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

@media (max-width: 768px) {
  .app-layout__content {
    padding: 16px;
  }
}

/* 内容宽度约束：超宽屏下避免信息被拉散，居中留白 */
.app-layout__inner {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

/* 会话模式：会话工作台占满主区域，不再套内容内边距 */
.app-layout__content--chat {
  padding: 0;
  overflow: hidden;
  display: flex;
}

/* 上下定格模式：视口高度内闭环，滚动交给页面内部（dsh 号池同款口径） */
.app-layout__content--pinned {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.app-layout__content--pinned .app-layout__inner {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
}

/* 页面组件根节点撑满定格容器（router-view 直接子级） */
.app-layout__content--pinned .app-layout__inner > * {
  flex: 1;
  min-height: 0;
}

.page-fade {
  animation: fadeIn var(--duration-slow) var(--ease-out);
}
</style>
