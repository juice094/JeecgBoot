# components — GitHub Issues 风格组件

从 `.refs/raw/github-dark-ui-clone.html`（DeepSeek 生成的 GitHub Dark 静态克隆）拆分而来的 Vue 组件。
**HTML 原件可删**——本目录 + `../docs` + `../.refs/extracted` 已保留全部有效信息。

## 组件清单

| 组件 | 对应 HTML 块 | 说明 |
|---|---|---|
| `LabelPill.vue` | `.label-pill` | 标签胶囊；配色走全局 `.tag--*`（类名由 `constants/task.ts` 的 TAG_CLASS/PRIORITY_CLASS 产出） |
| `IssueStateIcon.vue` | 列表行 `.issue-status` 图标 | 四态无底色符号（○◐✓✕ 的图标版）；**与实心胶囊 `StatusBadge.vue` 是两种组件** |
| `IssueItem.vue` | `.issue-item` | 列表行：状态符号 + 标题 + 标签 + 元信息 + 评论计数/指派人头像；`@open` 跳转 |
| `IssueListHeader.vue` | `.list-header` | Open/Closed 计数切换（v-model）+ `filters` 插槽放筛选/排序 |
| `CommentCard.vue` | `.comment-box` | 评论/描述卡片：作者 + 徽章 + Markdown 正文（`utils/markdown`，先转义无注入面）+ `footer` 插槽 |
| `CommentEditor.vue` | `.comment-editor` | Write/Preview 双页签 + v-model 正文 + 提交/关闭；`toolbar`/`hint` 插槽 |
| `TimelineEvent.vue` | `.timeline-event` | 紧凑事件行（2px 竖线 + 24px 徽章），供「added label / change_status」类元事件 |
| `SidebarSection.vue` | `.sidebar-section` | 详情右侧栏区块，`action` 插槽放齿轮/编辑入口 |

存量组件（不在本次拆分范围，同属该体系）：`StatusBadge.vue`（实心状态胶囊）、`TimelineItem.vue`（32px 徽章时间线项，挂 CommentCard）。

## 邮箱组件（Outlook 网页版结构，宿主 2026-11-18 裁定优先于 QQ 版）

来源：`Downloads/f8bf3abf-….htm`（收件箱）、`fafccfcc-….htm`（读信态）两个快照，蒸馏规格 [../.refs/extracted/outlook-mail-spec.md](../.refs/extracted/outlook-mail-spec.md)。

| 组件 | 对应 Outlook 块 | 说明 |
|---|---|---|
| `MailFolderNav.vue` | 文件夹窗格 | 分组头 + 文件夹项（计数/激活浅灰底，非主题色） |
| `MailListToolbar.vue` | command bar | 40px 高；primary/actions/right 插槽；`:slotted(.cmd-btn/.cmd-divider)` 提供无边框按钮与分隔线 |
| `MailListRow.vue` | 消息行 | 头像 32px（悬停/选中复选框覆盖）、两行文本、未读加粗+日期主题色、选中左侧 3px 指示条 |
| `MailReadingPane.vue` | 读信窗 | 主题/发件人行/信息条（警告底+操作按钮）/正文；回复·全部答复·转发动作 |
| `MailComposeDialog.vue` | 写信遮罩层（试点弹窗化） | 下划线字段行（收件人/主题）+ 预览切换 + 发送主按钮居头部左侧 |

消费页 `WorkEmail.vue`：三栏 220｜320｜flex，mock 线不变。不搬：重点/其他页签、发现组/撤消、广告侧栏（见规格 §四）。

## 未搬入组件的内容（及原因）

| HTML 块 | 去向 |
|---|---|
| `:root` 变量 | 已由 `styles/variables.css` 全量覆盖（GitHub Dark 实测 + 品牌青绿），组件全部使用现有令牌 |
| `.btn-primary` / `.btn-secondary` | 全局 `.btn` / `.btn-primary` / `.btn-default` 已有 |
| `.avatar` | 全局 `.avatar` / `.avatar--sm` / `.avatar--lg` 已有（内容放首字母） |
| `.top-header` / `.repo-nav` / `.footer` | 应用自有布局（`layout/TopHeader.vue`、`layout/SidebarMenu.vue`），GitHub 仓库导航语义不适用 |
| 编辑器装饰工具栏（H I <> 🔗） | 未实现功能，留 `CommentEditor` 的 `toolbar` 插槽，需要时再接 |
| 标签 HSL 亮度自适应算法 | 明确不采用（见 `.refs/extracted/design-tokens.md`） |

## 用法速览

```vue
<script setup lang="ts">
import { ref } from 'vue';
import IssueListHeader from '@/components/IssueListHeader.vue';
import IssueItem from '@/components/IssueItem.vue';
import CommentCard from '@/components/CommentCard.vue';
import CommentEditor from '@/components/CommentEditor.vue';
import TimelineEvent from '@/components/TimelineEvent.vue';
import SidebarSection from '@/components/SidebarSection.vue';

const tab = ref<'open' | 'closed'>('open');
const draft = ref('');
</script>

<template>
  <IssueListHeader v-model="tab" :open-count="9" :closed-count="4" />

  <ul class="issue-list">
    <IssueItem state="open" title="任务标题" :labels="[{ text: '开发', cls: 'tag--success' }]"
               meta="juice094#12 · opened 1d ago" :comment-count="1" assignee-name="张三"
               @open="goDetail" />
  </ul>

  <CommentCard author="juice094" subtitle="opened 1d ago" :badges="['Owner']"
               body="# 目标\n- [ ] 任务清单" />

  <TimelineEvent><strong>juice094</strong> 将状态从待办变更为进行中 · 1d ago</TimelineEvent>

  <SidebarSection title="Assignees">...</SidebarSection>

  <CommentEditor v-model="draft" submit-text="评论" @submit="post" />
</template>
```
