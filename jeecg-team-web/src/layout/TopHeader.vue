<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const title = computed(() => (route.meta.title as string) || '');
const owner = computed(() => (route.meta.owner as string) || '');

function onLogout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <header class="top-header">
    <div class="top-header__title">{{ title }}</div>
    <div class="top-header__right">
      <span v-if="owner" class="tag tag--info">模块负责人：{{ owner }}</span>
      <span class="top-header__user">{{ auth.displayName }}</span>
      <button class="top-header__logout" @click="onLogout">退出</button>
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
  padding: 0 20px;
  flex-shrink: 0;
}

.top-header__title {
  font-size: 16px;
  font-weight: 600;
}

.top-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-header__user {
  font-size: 14px;
  color: var(--text-regular);
}

.top-header__logout {
  border: 1px solid var(--border-color);
  background: #fff;
  color: var(--text-regular);
  font-size: 13px;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.top-header__logout:hover {
  color: var(--danger);
  border-color: var(--danger);
}
</style>
