<script setup lang="ts">
// 工作邮箱（工具组）—— owner: juice094
// 合同依据：projects/agent-collab-pilot/docs/OA 邮件体系接口文档.md v1.1.0
// 现状：全链路真实接口——列表（分页）/文件夹/同步/账号/详情正文（B-3）/已读回写/删除（B-5）/写信（B-6）。
// UI 结构对齐 Outlook 网页版三栏（规格见 .refs/extracted/outlook-mail-spec.md）。
import { computed, onMounted, ref } from 'vue';
import { PhArrowsClockwise, PhEnvelopeSimple, PhWarningCircle } from '@phosphor-icons/vue';
import MailFolderNav from '@/components/MailFolderNav.vue';
import MailListToolbar from '@/components/MailListToolbar.vue';
import MailListRow from '@/components/MailListRow.vue';
import MailReadingPane from '@/components/MailReadingPane.vue';
import MailComposeDialog from '@/components/MailComposeDialog.vue';
import type { MailCanonicalFolder, MailMessageDetail, MailMessageIndex, MailSendMode, MailSyncStatus, MyMailAccount, MailFolder } from '@/api/mail';
import {
  deleteMailMessage,
  getMailMessage,
  getMailSyncStatus,
  listMailFolders,
  listMailMessages,
  listMyMailAccounts,
  markMailRead,
  sendMailMessage,
  triggerMailSync,
} from '@/api/mail';
import { MAIL_FOLDER_META, MAIL_SEND_MODES } from '@/constants/mail';

/** 列表分页：后端 pageMessages 按 pageNo/pageSize 返回 records/total */
const PAGE_SIZE = 20;

const activeFolder = ref<MailCanonicalFolder>('Inbox');
const mails = ref<MailMessageIndex[]>([]);
const total = ref(0);
const pageNo = ref(1);
const myAccount = ref<MyMailAccount | null>(null);
const folderStates = ref<MailFolder[]>([]);
const syncStatus = ref<MailSyncStatus | null>(null);
const detail = ref<MailMessageDetail | null>(null);
const detailLoading = ref(false);

const loading = ref(false);
const bootstrapping = ref(true);
const errorMsg = ref('');
const syncHint = ref('');
const selectedId = ref<string | null>(null);
const composerOpen = ref(false);
const composerMode = ref<MailSendMode>('new');
const composer = ref({ to: '', subject: '', html: '' });
const selectedIds = ref<string[]>([]);

// ---- 查件：主题关键字 + 未读筛选（服务端过滤，keyword 仅匹配主题）----
const keyword = ref('');
const unreadOnly = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

/** 输入防抖 300ms，避免每个字符都打一次请求 */
function onKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    selectedId.value = null;
    selectedIds.value = [];
    detail.value = null;
    pageNo.value = 1;
    void loadMessages();
  }, 300);
}

function toggleUnreadOnly() {
  unreadOnly.value = !unreadOnly.value;
  selectedId.value = null;
  selectedIds.value = [];
  detail.value = null;
  pageNo.value = 1;
  void loadMessages();
}

function clearKeyword() {
  keyword.value = '';
  pageNo.value = 1;
  void loadMessages();
}

// ---- 文件夹窗格 ----
const folderNavItems = computed(() =>
  MAIL_FOLDER_META.map((f) => ({
    key: f.key,
    label: f.label,
    count: mails.value.filter((m) => m.canonicalFolder === f.key && !m.readFlag).length || undefined,
  })),
);

const folderMails = computed(() =>
  mails.value
    .filter((m) => m.canonicalFolder === activeFolder.value)
    .slice()
    .sort((a, b) => (b.receivedTime || '').localeCompare(a.receivedTime || '')),
);
const selectedMail = computed(() => mails.value.find((m) => m.id === selectedId.value) || null);
const allChecked = computed(
  () => folderMails.value.length > 0 && selectedIds.value.length === folderMails.value.length,
);

/** 同步状态摘要：取各文件夹最大 lastUid */
const syncSummary = computed(() => {
  if (!syncStatus.value) return '';
  const uid = syncStatus.value.folders?.reduce((max, f) => Math.max(max, f.lastUid ?? 0), 0) ?? 0;
  const time = syncStatus.value.lastSyncTime ?? '—';
  return `lastUid=${uid} · ${time}${syncStatus.value.running ? ' · 同步中' : ''}`;
});

function switchFolder(key: string) {
  activeFolder.value = key as MailCanonicalFolder;
  selectedId.value = null;
  selectedIds.value = [];
  detail.value = null;
  pageNo.value = 1;
  void loadMessages();
}

/** 翻页（pageNo 越界由按钮 disabled 兜底） */
function gotoPage(next: number) {
  pageNo.value = next;
  selectedIds.value = [];
  void loadMessages();
}
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));

// ---- 数据加载 ----

async function bootstrap() {
  bootstrapping.value = true;
  errorMsg.value = '';
  try {
    const accounts = await listMyMailAccounts();
    const list = accounts.result ?? [];
    myAccount.value = list[0] ?? null;
    if (!myAccount.value) {
      bootstrapping.value = false;
      return;
    }
    await Promise.all([loadMessages(), loadFolders(), loadSyncStatus()]);
  } catch (e) {
    errorMsg.value = `加载邮箱失败：${(e as Error).message}`;
  } finally {
    bootstrapping.value = false;
  }
}

async function loadMessages() {
  if (!myAccount.value) return;
  loading.value = true;
  errorMsg.value = '';
  try {
    const resp = await listMailMessages({
      accountId: myAccount.value.id,
      canonicalFolder: activeFolder.value,
      keyword: keyword.value.trim() || undefined,
      unreadOnly: unreadOnly.value || undefined,
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
    });
    mails.value = resp.result?.records ?? [];
    total.value = resp.result?.total ?? 0;
    // 当前页被删空且非首页时回退一页
    if (!mails.value.length && pageNo.value > 1) {
      pageNo.value -= 1;
      await loadMessages();
    }
  } catch (e) {
    errorMsg.value = `加载邮件列表失败：${(e as Error).message}`;
    mails.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadFolders() {
  if (!myAccount.value) return;
  try {
    const resp = await listMailFolders(myAccount.value.id);
    folderStates.value = resp.result ?? [];
  } catch (e) {
    errorMsg.value = `加载文件夹失败：${(e as Error).message}`;
  }
}

async function loadSyncStatus() {
  if (!myAccount.value) return;
  try {
    const resp = await getMailSyncStatus(myAccount.value.id);
    syncStatus.value = resp.result ?? null;
  } catch {
    syncStatus.value = null;
  }
}

async function refresh() {
  if (!myAccount.value) {
    await bootstrap();
    return;
  }
  syncHint.value = '';
  errorMsg.value = '';
  try {
    await triggerMailSync(myAccount.value.id);
    syncHint.value = '已触发增量同步，稍后刷新查看结果。';
    await loadSyncStatus();
  } catch (e) {
    errorMsg.value = `触发同步失败：${(e as Error).message}`;
  }
}

// ---- 列表：全选 / 行交互 ----
function toggleAll(checked: boolean) {
  selectedIds.value = checked ? folderMails.value.map((m) => m.id) : [];
}

function toggleSelect(id: string, checked: boolean) {
  selectedIds.value = checked
    ? [...selectedIds.value, id]
    : selectedIds.value.filter((x) => x !== id);
}

function openDetail(mail: MailMessageIndex) {
  selectedId.value = mail.id;
  detail.value = null;
  detailLoading.value = true;
  getMailMessage(mail.id)
    .then((resp) => {
      detail.value = resp.result ?? null;
      // 打开即已读（服务端已回写 \Seen 与索引），本地同步置位
      const local = mails.value.find((m) => m.id === mail.id);
      if (local) local.readFlag = true;
    })
    .catch((e) => {
      errorMsg.value = `读取正文失败：${(e as Error).message}`;
    })
    .finally(() => {
      detailLoading.value = false;
    });
}

/** HTML 兜底去标签：正文优先 text，html-only 邮件本地剥标签后展示（不引入 v-html，防邮件 XSS） */
function stripHtml(html: string): string {
  return html
    .replace(/<(script|style)[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|tr)>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}
const readerBody = computed(() => {
  if (!detail.value) return selectedMail.value?.snippet ?? '';
  return detail.value.text || (detail.value.html ? stripHtml(detail.value.html) : '');
});
const readerMail = computed(() =>
  detail.value && detail.value.id === selectedId.value ? detail.value : selectedMail.value,
);

/** 时间列：今天显时刻，历史显日期（Outlook 口径） */
function rowTime(t: string): string {
  if (!t) return '';
  const d = new Date(t.replace(' ', 'T'));
  const stamp = (x: Date) => x.getFullYear() * 10000 + x.getMonth() * 100 + x.getDate();
  if (!isNaN(d.getTime()) && stamp(d) === stamp(new Date())) return t.slice(11, 16);
  return t.slice(5, 10);
}

// ---- 命令栏动作 ----
async function markAllRead() {
  const unread = mails.value.filter((m) => !m.readFlag);
  if (!unread.length) {
    syncHint.value = '当前页没有未读邮件。';
    return;
  }
  let marked = 0;
  for (const m of unread) {
    try {
      await markMailRead(m.id, true);
      m.readFlag = true;
      marked += 1;
    } catch (e) {
      errorMsg.value = `标记已读失败（${marked}/${unread.length}）：${(e as Error).message}`;
      return;
    }
  }
  syncHint.value = `已标记 ${marked} 封为已读（未读筛选下它们将随即从列表消失）。`;
}

async function removeSelected() {
  if (!selectedIds.value.length) return;
  const ids = [...selectedIds.value];
  let removed = 0;
  for (const id of ids) {
    try {
      await deleteMailMessage(id);
      removed += 1;
    } catch (e) {
      errorMsg.value = `删除失败（${removed}/${ids.length}）：${(e as Error).message}`;
      break;
    }
  }
  if (selectedId.value && ids.includes(selectedId.value)) {
    selectedId.value = null;
    detail.value = null;
  }
  selectedIds.value = [];
  syncHint.value = removed === ids.length
    ? `已删除 ${removed} 封（有「已删除」映射则移入其中，否则永久删除）。`
    : `已删除 ${removed}/${ids.length} 封，其余失败见上方错误。`;
  await loadMessages();
}

// ---- 写信 ----
function openComposer(mode: MailSendMode, mail?: MailMessageIndex) {
  composerMode.value = mode;
  composer.value = {
    to: '',
    subject: mode === 'new' ? '' : `Re: ${mail?.subject ?? ''}`,
    html: '',
  };
  composerOpen.value = true;
}

async function submitComposer(payload: { to: string; subject: string; html: string }) {
  errorMsg.value = '';
  composer.value = { to: payload.to, subject: payload.subject, html: payload.html };
  if (!payload.to.trim() || !payload.subject.trim()) {
    errorMsg.value = '收件人与主题为必填。';
    return;
  }
  if (!myAccount.value) return;
  try {
    await sendMailMessage({
      accountId: myAccount.value.id,
      mode: composerMode.value,
      to: payload.to.trim(),
      subject: payload.subject.trim(),
      html: payload.html,
    });
    composerOpen.value = false;
    syncHint.value = '已投递；「已发送」文件夹在下次同步后可见。';
    await loadMessages();
  } catch (e) {
    errorMsg.value = `发送失败：${(e as Error).message}`;
  }
}

onMounted(bootstrap);
</script>

<template>
  <div class="mail-page">
    <div v-if="errorMsg" class="card mail-notice mail-notice--error">
      <PhWarningCircle :size="16" />
      <span>{{ errorMsg }}</span>
    </div>

    <div v-if="bootstrapping" class="card mail-empty-state">正在加载邮箱…</div>

    <div v-else-if="!myAccount" class="card mail-empty-state">
      <PhEnvelopeSimple :size="40" />
      <p>尚未绑定企业邮箱</p>
      <p class="mail-empty-state__hint">请先通过绑定接口配置 IMAP 与 SMTP（需双测连通过）。</p>
    </div>

    <template v-else>
      <MailListToolbar>
        <template #primary>
          <button type="button" class="btn btn-primary btn--sm" @click="openComposer('new')">
            <PhEnvelopeSimple :size="14" /> 新邮件
          </button>
        </template>
        <template #actions>
          <button type="button" class="cmd-btn" :disabled="!selectedIds.length" @click="removeSelected">
            删除{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
          </button>
          <span class="cmd-divider" />
          <button type="button" class="cmd-btn" @click="markAllRead">全部标记为已读</button>
          <button type="button" class="cmd-btn" :disabled="loading" @click="refresh">
            <PhArrowsClockwise :size="14" /> 刷新
          </button>
        </template>
        <template #right>
          <span v-if="syncHint" class="mail-bar-hint">{{ syncHint }}</span>
          <button
            type="button"
            class="cmd-btn"
            :class="{ 'cmd-btn--active': unreadOnly }"
            :aria-pressed="unreadOnly"
            @click="toggleUnreadOnly"
          >
            仅未读
          </button>
          <span class="mail-search">
            <input
              v-model="keyword"
              type="search"
              class="mail-search__input"
              placeholder="搜索主题…"
              aria-label="按主题搜索邮件"
              maxlength="100"
              @input="onKeywordInput"
            />
            <button
              v-if="keyword"
              type="button"
              class="mail-search__clear"
              aria-label="清空搜索"
              @click="clearKeyword"
            >×</button>
          </span>
        </template>
      </MailListToolbar>

      <div class="mail-layout">
        <MailFolderNav v-model="activeFolder" :folders="folderNavItems" @update:model-value="switchFolder">
          <template #sections>
            <div class="mail-nav-hint">同步：{{ syncSummary || '未同步' }}</div>
            <div v-if="folderStates.some((f) => !f.canonical)" class="mail-nav-hint mail-nav-hint--warn">
              有 {{ folderStates.filter((f) => !f.canonical).length }} 个文件夹未识别，需映射
            </div>
          </template>
        </MailFolderNav>

        <section class="mail-list-pane">
          <div class="mail-list-pane__head">
            <input
              type="checkbox"
              class="mail-list-pane__check-all"
              :checked="allChecked"
              aria-label="全选"
              @change="toggleAll(($event.target as HTMLInputElement).checked)"
            />
            <span class="mail-list-pane__total">共 {{ total }} 封</span>
          </div>

          <div v-if="loading" class="mail-empty">加载中…</div>
          <ul v-else class="mail-rows">
            <MailListRow
              v-for="m in folderMails"
              :key="m.id"
              :sender="m.fromName"
              :subject="m.subject"
              :snippet="m.snippet"
              :read="m.readFlag"
              :selected="selectedIds.includes(m.id)"
              :time="rowTime(m.receivedTime)"
              :attachment="m.hasAttachment"
              @open="openDetail(m)"
              @toggle="(checked) => toggleSelect(m.id, checked)"
            />
            <li v-if="!folderMails.length" class="mail-empty">当前文件夹暂无邮件</li>
          </ul>

          <div v-if="totalPages > 1" class="mail-pager">
            <button type="button" class="cmd-btn" :disabled="pageNo <= 1 || loading" @click="gotoPage(pageNo - 1)">
              上一页
            </button>
            <span class="mail-pager__info">{{ pageNo }} / {{ totalPages }} 页</span>
            <button
              type="button"
              class="cmd-btn"
              :disabled="pageNo >= totalPages || loading"
              @click="gotoPage(pageNo + 1)"
            >
              下一页
            </button>
          </div>
        </section>

        <MailReadingPane
          v-if="selectedMail"
          :subject="readerMail?.subject"
          :sender-name="readerMail?.fromName"
          :sender-address="readerMail?.fromAddressHash"
          :recipient-line="readerMail?.toSummary || '—'"
          :time="readerMail?.receivedTime"
          :body="detailLoading ? '正在读取正文…' : readerBody"
          @reply="openComposer('reply', selectedMail)"
          @reply-all="openComposer('replyAll', selectedMail)"
          @forward="openComposer('forward', selectedMail)"
        />
        <section v-else class="mail-reader-empty">
          <PhEnvelopeSimple :size="40" />
          <p>选择一封邮件以阅读</p>
        </section>
      </div>

      <MailComposeDialog
        :open="composerOpen"
        :title="MAIL_SEND_MODES.find((m) => m.key === composerMode)?.label || '新邮件'"
        :initial="{ to: composer.to, subject: composer.subject }"
        :sender-hint="myAccount.email"
        @submit="submitComposer"
        @close="composerOpen = false"
      />
    </template>
  </div>
</template>

<style scoped>
.mail-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  min-height: 0;
}

.mail-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  color: var(--warning);
  font-size: 13px;
}

.mail-notice--error {
  color: var(--danger, #d03050);
}

.mail-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  min-height: 320px;
  color: var(--text-muted);
}

.mail-empty-state__hint {
  font-size: var(--font-size-sm);
}

.mail-layout {
  display: grid;
  grid-template-columns: 220px 320px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  gap: 0;
  align-items: stretch;
}

.mail-nav-hint {
  padding: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-nav-hint--warn {
  color: var(--warning);
}

.mail-list-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-right: none;
  border-radius: 0 0 0 var(--radius-md);
}

.mail-list-pane__head {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  height: 40px;
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
}

.mail-list-pane__check-all {
  width: 14px;
  height: 14px;
  accent-color: var(--primary);
  cursor: pointer;
}

.mail-list-pane__total {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-rows {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.mail-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: 6px var(--spacing-lg);
  border-top: 1px solid var(--border-color);
}

.mail-pager__info {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
}

.mail-bar-hint {
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 查件：主题搜索 + 未读切换 */
.cmd-btn--active {
  color: var(--primary);
  font-weight: 600;
}

.mail-search {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.mail-search__input {
  width: 200px;
  height: 28px;
  padding: 0 24px 0 10px;
  font-size: var(--font-size-sm);
  color: var(--text-regular);
  background: var(--bg-body);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 0.2s, width 0.2s;
}

.mail-search__input:focus {
  width: 260px;
  border-color: var(--primary);
}

.mail-search__clear {
  position: absolute;
  right: 4px;
  width: 18px;
  height: 18px;
  padding: 0;
  line-height: 1;
  font-size: 14px;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.mail-search__clear:hover {
  color: var(--text-regular);
  background: var(--border-color-light);
}

.mail-reader-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-lg);
  min-height: 0;
  overflow-y: auto;
  color: var(--text-placeholder);
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-left: none;
  border-radius: 0 0 var(--radius-md) 0;
  font-size: var(--font-size-base);
}

/* 阅读窗格在定格布局内去掉最小高度，正文由自身 overflow-y 接管 */
:deep(.reader) {
  min-height: 0;
}

@media (max-width: 1100px) {
  .mail-layout {
    grid-template-columns: 200px minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }
  .mail-list-pane {
    border-radius: 0 0 var(--radius-md) var(--radius-md);
    border-right: 1px solid var(--border-color-light);
  }
  .mail-page .mail-layout > :last-child {
    grid-column: 1 / -1;
  }
}

/* 文件夹多时可内部滚动（组件根为 overflow:hidden 定高行） */
:deep(.mail-folder-nav) {
  overflow-y: auto;
}
</style>
