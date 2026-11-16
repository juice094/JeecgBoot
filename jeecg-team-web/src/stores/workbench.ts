import { defineStore } from 'pinia';
import { WORKBENCH_LAST_PATH_KEY } from '@/constants/app';

export type WorkbenchMode = 'tool' | 'chat';

/**
 * 工作台模式与侧栏状态（双模式设计：工具工作台 ⇄ 会话工作台）。
 * 侧栏底部提供模式切换与收起/展开（原型设计：切换按钮在收起按钮边上）。
 *
 * 模式与路由单向派生（AppLayout 里的 route watcher 负责同步）：
 * - 路由 = /message → chat；其他工具页 → tool，并记为"上次打开的页面"
 * - 从会话切回工具时回到 lastToolPath（localStorage 持久化，跨会话保留）
 */
export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    mode: 'tool' as WorkbenchMode,
    sidebarCollapsed: false,
    lastToolPath: localStorage.getItem(WORKBENCH_LAST_PATH_KEY) || '/dashboard',
  }),
  actions: {
    setMode(mode: WorkbenchMode) {
      this.mode = mode;
    },
    /** 路由到工具页时调用：记录"上次打开的页面" */
    rememberToolPath(path: string) {
      this.lastToolPath = path;
      localStorage.setItem(WORKBENCH_LAST_PATH_KEY, path);
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },
  },
});
