<script setup lang="ts">
import SidebarMenu from './SidebarMenu.vue';
import TopHeader from './TopHeader.vue';
</script>

<template>
  <div class="app-layout">
    <SidebarMenu />
    <div class="app-layout__main">
      <TopHeader />
      <main class="app-layout__content">
        <router-view />
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
  padding: 20px;
}
</style>
