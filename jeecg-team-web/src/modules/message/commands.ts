// 会话工作台斜杠命令注册表（owner: qingyanjunzi）
// 设计目标：命令快速唤起工具工作台能力——不用离开聊天就能发起流程/填表/跳转。
// 两种动作：
//   navigate — 切到工具工作台并跳转对应页面（路由 watcher 自动切模式）
//   form     — 在会话内插入一张快捷表单卡（提交后走对应 API，见 QuickFormCard）
import type { Component } from 'vue';
import {
  PhNotePencil,
  PhCheckSquareOffset,
  PhBug,
  PhChartBar,
  PhKanban,
  PhFolderOpen,
} from '@phosphor-icons/vue';

export type CommandAction =
  | { type: 'navigate'; path: string }
  | { type: 'form'; form: 'approval' | 'bug' };

export interface ChatCommand {
  /** 命令名（不带斜杠，如 apply） */
  name: string;
  /** 展示名（如 日常申请） */
  title: string;
  description: string;
  icon: Component;
  action: CommandAction;
  /** 命令面板的示例语 */
  example?: string;
}

export const CHAT_COMMANDS: ChatCommand[] = [
  {
    name: 'apply',
    title: '发起申请',
    description: '在会话内填写并提交日常申请（加班/请假/报销…）',
    icon: PhNotePencil,
    action: { type: 'form', form: 'approval' },
    example: '/apply 加班申请 关于迎新筹备加班',
  },
  {
    name: 'approve',
    title: '审批中心',
    description: '跳转到工具工作台 · 待我审批列表',
    icon: PhCheckSquareOffset,
    action: { type: 'navigate', path: '/tools/approval' },
  },
  {
    name: 'bug',
    title: '反馈 Bug',
    description: '在会话内快速提交 Bug 反馈',
    icon: PhBug,
    action: { type: 'form', form: 'bug' },
    example: '/bug 看板页面 500',
  },
  {
    name: 'report',
    title: '个人报表',
    description: '跳转到工具工作台 · 积木报表',
    icon: PhChartBar,
    action: { type: 'navigate', path: '/tools/report' },
  },
  {
    name: 'task',
    title: '任务看板',
    description: '跳转到工具工作台 · 任务协作（M3）',
    icon: PhKanban,
    action: { type: 'navigate', path: '/dashboard' },
  },
  {
    name: 'doc',
    title: '资料中心',
    description: '跳转到工具工作台 · 共享文档',
    icon: PhFolderOpen,
    action: { type: 'navigate', path: '/tools/resource' },
  },
];

/** 输入框以 '/' 开头时匹配命令（大小写不敏感） */
export function matchCommands(input: string): ChatCommand[] {
  const kw = input.slice(1).trim().toLowerCase();
  if (!kw) return CHAT_COMMANDS;
  return CHAT_COMMANDS.filter(
    (c) => c.name.includes(kw) || c.title.toLowerCase().includes(kw) || c.description.toLowerCase().includes(kw)
  );
}
