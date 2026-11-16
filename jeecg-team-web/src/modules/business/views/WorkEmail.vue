<script setup lang="ts">
// 工作邮箱（工具组）—— owner: juice094
// 原型复现 mock；邮箱为 SMTP/外部服务集成，属 M2+ 后置项（docs/15 边界）
import { PhPaperPlaneRight, PhEnvelopeSimple, PhEnvelopeSimpleOpen } from '@phosphor-icons/vue';

const mails = [
  { from: '王立柱', subject: '关于2026年第四季度业务目标的确认通知', date: '10:30', read: true },
  { from: 'HR 系统', subject: '[系统通知] 您的11月工资条已生成', date: '09:00', read: false },
  { from: '黄茜茜', subject: '转发：外事学院大数据实验室建设方案', date: '昨天', read: false },
];
</script>

<template>
  <div class="card" style="height: calc(100vh - 120px); display: flex; flex-direction: column">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">工作邮箱 (Inbox)</span>
      <button class="btn btn-primary"><PhPaperPlaneRight :size="16" /> 写邮件</button>
    </div>
    <div class="table-wrap" style="flex: 1">
      <table class="data-table">
        <thead>
          <tr><th width="50"></th><th>发件人</th><th>主题</th><th>日期</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in mails" :key="m.subject" :class="{ 'mail--unread': !m.read }">
            <td>
              <PhEnvelopeSimpleOpen v-if="m.read" :size="18" style="color: var(--primary)" />
              <PhEnvelopeSimple v-else :size="18" />
            </td>
            <td>{{ m.from }}</td>
            <td>{{ m.subject }}</td>
            <td>{{ m.date }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.mail--unread {
  font-weight: 600;
  background: #f9fbfc;
}
</style>
