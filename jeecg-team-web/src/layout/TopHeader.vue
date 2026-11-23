<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhBell, PhCaretDown, PhCaretRight, PhSignOut, PhSun, PhMoon } from '@phosphor-icons/vue';
import { useAuthStore } from '@/stores/auth';
import { useWorkbenchStore } from '@/stores/workbench';
import { currentTheme, toggleTheme } from '@/utils/theme';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const workbench = useWorkbenchStore();

// 亮暗主题切换（默认深色为基线，浅色可选；持久化见 utils/theme.ts）
const theme = ref(currentTheme());
function onToggleTheme() {
  theme.value = toggleTheme();
}

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
      <button class="top-header__icon-btn" :title="theme === 'dark' ? '切换为浅色' : '切换为深色'" type="button" @click="onToggleTheme">
        <PhMoon v-if="theme === 'dark'" :size="18" />
        <PhSun v-else :size="18" />
      </button>
      <button class="top-header__icon-btn" title="搜索" type="button">
        <PhMagnifyingGlass :size="18" />
      </button>
      <button class="top-header__icon-btn top-header__icon-btn--bell" title="通知" type="button">
        <PhBell :size="18" />
        <i class="top-header__dot" />
      </button>
      <div class="top-header__profile">
        <div class="top-header__avatar">{{ avatarText }}</div>
        <div class="top-header__user">
          <div class="top-header__name">{{ auth.displayName }}</div>
          <div class="top-header__role">{{ route.meta.owner ? `模块负责人：${route.meta.owner}` : '团队成员' }}</div>
        </div>
        <PhCaretDown :size="12" class="top-header__caret" />
      </div>
      <button class="top-header__icon-btn top-header__icon-btn--logout" title="退出登录" type="button" @click="onLogout">
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
  gap: var(--spacing-md);
  padding: 0 24px;
  flex-shrink: 0;
}

.top-header__crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-md);
  color: var(--text-muted);
  min-width: 0;
  overflow: hidden;
}

.top-header__crumb span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-header__crumb-current {
  color: var(--text-main);
  font-weight: 500;
  flex-shrink: 1;
}

.top-header__crumb-sep {
  color: var(--gray-400);
  flex-shrink: 0;
}

.top-header__right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.top-header__icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-regular);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}
.top-header__icon-btn:hover {
  background: var(--gray-100);
  color: var(--text-main);
}
.top-header__icon-btn--logout:hover {
  background: var(--danger-50);
  color: var(--danger);
}

.top-header__dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 6px;
  height: 6px;
  background: var(--danger);
  border-radius: 50%;
  border: 1.5px solid var(--bg-card);
}

.top-header__profile {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px 4px 12px;
  margin: 0 8px;
  border-radius: var(--radius-sm);
  transition: background-color var(--duration-fast) var(--ease-out);
}
.top-header__profile:hover {
  background: var(--gray-100);
}

.top-header__avatar {
  width: 30px;
  height: 30px;
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-md);
  font-weight: 500;
  flex-shrink: 0;
}

.top-header__user {
  min-width: 0;
}
.top-header__name {
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--text-main);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-header__role {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-header__caret {
  color: var(--text-muted);
  margin-left: 2px;
  flex-shrink: 0;
}

/* 窄窗口：压缩两侧留白，隐藏角色行避免用户信息挤压图标区 */
@media (max-width: 768px) {
  .top-header {
    padding: 0 12px;
  }
  .top-header__role {
    display: none;
  }
  .top-header__profile {
    margin: 0 4px;
    padding: 4px 6px 4px 8px;
  }
}
</style>
