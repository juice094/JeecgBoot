# 蓝鸥三体系动线闭环走查报告（2026-11-18）

> 执行：主会话收尾（额度用尽 agent 的续作）。
> 环境：jeecg-boot 8081（新 jar，含 mail + 审批 v2 + 字段模板）· jeecg-team-web 3200 · MySQL 3306 · 租户 1002 蓝鸥科技有限公司。

## 0. 闭环结论

| 体系 | API 层 | UI 层 | 判定 |
|---|---|---|---|
| Issues（任务/Bug） | ✅ | ✅（昨日评论实发 + 今日新 jar 列表/评论 200） | **闭环** |
| 轻量申请表 | ✅ | ✅（字段模板动态渲染实拍 + UI 提交 + UI 审批） | **闭环** |
| 邮件 | ✅ | ✅（真实 IMAP 绑定/同步/搜索实拍） | **闭环** |

## 1. 申请表 API 层证据链（单号 OA-20261118-005）

1. `GET /team/approval/template/schema?category=费用报销` → 200，fieldSchema 完整下发
2. 缺必填 amount 提交 → 500「字段「金额」必填」（校验器负路径）
3. 正常提交 → 200，amount=3800 提取入列，code=OA-20261118-005
4. 员工越权审批 → 500「越权审批: 当前步骤审批人为 王凯，登录人为 李想」
5. 王凯 approve → 已通过；陈静 approve → 已通过
6. DB 终态核验：status=approved，双步 approved，amount 列 3800.00

## 2. 申请表 UI 层证据链（单号 OA-20261118-006）

1. 李想登录 → 日常申请 → 切「费用报销」→ **schema 动态渲染实证**：金额(spinbutton)/费用类型(下拉 差旅/招待/办公/其他)/发票张数 自动出现
2. 填 5200/差旅/2 + 标题事由 → 提交 → 库中落单 pending，amount=5200.00
3. 换王凯登录 → 审批中心「待我审批」恰 1 条（006）→ 点「通过」→ 库中 step1 approved、流转 step2
4. 截图：tmp/lanou-approval-ui（如需）；陈静终审同链路 API 层已实证

## 3. 邮件证据链（周景潇 zhoujingxiao，绑定 2241470466@qq.com）

- 绑定：POST /team/mail/accounts 双测连通过落库（id 2122967323495555073）
- 同步：触发后 Inbox 索引同步（pages 23），lastUid=3240
- UI：工作邮箱三栏实拍——收件箱 39/共 50、真实邮件列表、同步状态、4 个未识别文件夹提示
- 搜索：关键词「冒烟」过滤 50→4 封（防抖生效）
- 截图：workspace tmp/lanou-mail-walkthrough.jpg

## 4. 环境变更记录（本轮）

| 项 | 变更 |
|---|---|
| 在役后端 | 8081 重启为新 jar（停旧进程→package→启新，21s 启动） |
| Flyway | V3.9.6_10/11 转正官录并手动实跑，登记 flyway_schema_history（**checksum 须与脚本一致，NULL 会触发 jeecg FlywayConfig validate 失败**——本次已回填） |
| 邮件密钥 | 旧冒烟密钥丢失 → 新 MAIL_CRYPTO_KEY 已设 Windows 用户环境变量；冒烟期密文数据已清（7 表重置），周景潇绑定为新密钥下首条 |
| 租户/账号 | 蓝鸥科技有限公司 id=1002（加入码 LANOU88）；lanou_admin/mgr(王凯)/vp(陈静)/emp(李想) + zhoujingxiao(周景潇, admin 角色)，密码统一 Lanou@2026，均挂租户 1002；模板审批人回填为王凯/陈静真实 sys_user.id |
| 角色 | 新建「蓝鸥成员」lanou_member（47 条 team 权限） |
| team-web | 3200 dev server 拉起（此前未运行） |

## 5. 遗留

- 审批单 006 停在陈静待审（UI 已通过一步，终审可顺手点掉）
- 邮件 4 个未识别文件夹需映射（B-2 提示已在 UI 显示，属已知产品行为）
- 存量测试失败 2 类（D-20261118-80）未修
- 管理端迁移 P0 波已委派子代理 agent-0（k3）执行，见 docs/ADMIN-MIGRATION-EXECUTION-PLAN-2026-11-18.md
