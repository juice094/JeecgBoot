import { defineStore } from 'pinia';

export type WorkbenchMode = 'tool' | 'chat';

/**
 * 工作台模式与侧栏状态（双模式设计：工具工作台 ⇄ 会话工作台）。
 * 侧栏底部提供模式切换与收起/展开（原型设计：切换按钮在收起按钮边上）。
 */
export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    mode: 'tool' as WorkbenchMode,
    sidebarCollapsed: false,
  }),
  actions: {
    setMode(mode: WorkbenchMode) {
      this.mode = mode;
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },
  },
});
