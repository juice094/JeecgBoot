<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhBell, PhCaretDown, PhCaretRight, PhSignOut } from '@phosphor-icons/vue';
import { useAuthStore } from '@/stores/auth';
import { useWorkbenchStore } from '@/stores/workbench';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const workbench = useWorkbenchStore();

const crumbGroup = computed(() => (route.meta.group as string) || '');
const crumbTitle = computed(() => {
  if (workbench.mode === 'chat') return '会话工作台';
  return (route.meta.title as string) || '';
});
const avatarText = computed(() => auth.displayName.slice(0, 1));

function onLogout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <header class="top-header">
    <div class="top-header__crumb">
      <span class="top-header__crumb-root">首页</span>
      <PhCaretRight :size="12" class="top-header__crumb-sep" />
      <template v-if="crumbGroup">
        <span class="top-header__crumb-group">{{ crumbGroup }}</span>
        <PhCaretRight :size="12" class="top-header__crumb-sep" />
      </template>
      <span class="top-header__crumb-current">{{ crumbTitle }}</span>
    </div>
    <div class="top-header__right">
      <PhMagnifyingGlass :size="20" class="top-header__icon" />
      <span class="top-header__icon top-header__icon--bell">
        <PhBell :size="20" />
        <i class="top-header__dot" />
      </span>
      <div class="top-header__profile">
        <div class="top-header__avatar">{{ avatarText }}</div>
        <div class="top-header__user">
          <div class="top-header__name">{{ auth.displayName }}</div>
          <div class="top-header__role">{{ route.meta.owner ? `模块负责人：${route.meta.owner}` : '团队成员' }}</div>
        </div>
        <PhCaretDown :size="12" class="top-header__caret" />
      </div>
      <button class="top-header__logout" title="退出登录" @click="onLogout">
        <PhSignOut :size="18" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.top-header {
  height: var(--header-height);
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.top-header__crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-base);
  color: var(--text-muted);
}

.top-header__crumb-current {
  color: var(--text-main);
  font-weight: 500;
}

.top-header__crumb-sep {
  color: var(--text-muted);
}

.top-header__right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.top-header__icon {
  color: var(--text-regular);
  cursor: pointer;
  display: inline-flex;
  position: relative;
}

.top-header__icon:hover {
  color: var(--primary);
}

.top-header__dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background: var(--danger);
  border-radius: 50%;
}

.top-header__profile {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding-left: 20px;
  border-left: 1px solid var(--border-color);
}

.top-header__avatar {
  width: 32px;
  height: 32px;
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  font-weight: 500;
}

.top-header__name {
  font-size: var(--font-size-base);
  font-weight: 500;
}

.top-header__role {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.top-header__caret {
  color: var(--text-muted);
}

.top-header__logout {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  transition:
    background 0.2s,
    color 0.2s;
}

.top-header__logout:hover {
  background: var(--border-color-light);
  color: var(--danger);
}
</style>
