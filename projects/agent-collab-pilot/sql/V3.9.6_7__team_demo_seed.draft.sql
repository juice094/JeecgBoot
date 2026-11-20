-- =============================================================================
-- 【草案 · 不执行】团队协作 - 演示数据种子（V3.9.6_7）
-- 2026-11-17 showcase-dev 起草，供宿审定后手动执行（dev 库）。
-- 设计文档：projects/agent-collab-pilot/docs/25-demo-pack-design.md §二
--
-- 定位：仅 dev/演示环境。让 5 分钟演示路线（docs/25 §三）开箱即演：
--   任务三列分布 + 临期/过期样本；审批全状态链（含 admin 可批的待办）；
--   Bug 全链路四态；审计追溯开箱可见；公告 3 条（admin 视角 2 未读）。
--
-- 执行方式：试点 Flyway 自动装配被排除（同 V3.9.6_6 头注），本脚本转正后
--   移入 jeecg-system-start/src/main/resources/flyway/sql/mysql/ 并手动执行 +
--   登记 flyway_schema_history；草案阶段留在 agent-collab-pilot/sql/ 不入库目录。
--
-- 幂等：所有行使用 demo- 固定主键，重跑先删后插；非 demo 数据不动。
-- 用户解析：不写死 sys_user.id，按 username/realname 动态解析；
--   若试点库无 贺致远/黄茜茜 账号，相关外键列落 NULL（不影响演示主链路）。
-- 日期：一律 CURDATE() 相对偏移，保证「临期/过期」口径随执行日漂移始终成立。
-- 假设：V3.9.6_5 逻辑删列 deleted 默认 0（插人不显式给值）。
-- =============================================================================

SET @u_admin  := (SELECT id FROM sys_user WHERE username = 'admin'      LIMIT 1);
SET @n_admin  := (SELECT realname FROM sys_user WHERE username = 'admin' LIMIT 1);
SET @u_he     := (SELECT id FROM sys_user WHERE realname = '贺致远'     LIMIT 1);
SET @u_huang  := (SELECT id FROM sys_user WHERE realname = '黄茜茜'     LIMIT 1);

SELECT @u_admin AS admin_id, @u_he AS he_id, @u_huang AS huang_id,
       IF(@u_he IS NULL OR @u_huang IS NULL,
          'WARN: 试点账号缺失，相关指派/审批人列将为 NULL（主链路不受影响）', 'OK') AS seed_check;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1. 任务看板：todo×3 / doing×3 / done×3（V3.9.6_2 表结构）
--    覆盖：三标签、三优先级、三个指派对象、临期(+1d/+2d)/远期(+5d/+7d)/过期(-1d)
-- ---------------------------------------------------------------------------
DELETE FROM team_task WHERE id LIKE 'demo-task-%';

INSERT INTO team_task (id, title, description, status, tag, priority, assignee_id, assignee_name, due_date, sort_no, create_by, create_time) VALUES
('demo-task-001', '试点演示路线文档评审',        '对照 docs/25 走查 5 分钟路线，标出卡点', 'todo',  '需求', '高', @u_he,    '贺致远', CURDATE() + INTERVAL 2 DAY, 0, 'admin', NOW() - INTERVAL 2 DAY),
('demo-task-002', '任务看板拖拽联调',            '拖拽与按钮双通道流转一致性',             'todo',  '开发', '中', @u_huang, '黄茜茜', CURDATE() + INTERVAL 5 DAY, 1, 'admin', NOW() - INTERVAL 2 DAY),
('demo-task-003', 'Knife4j 注解补缺复核',        '对照 docs/25 附录 A 缺口表逐项确认',     'todo',  '开发', '中', @u_admin, @n_admin, CURDATE() + INTERVAL 1 DAY, 2, 'admin', NOW() - INTERVAL 1 DAY),
('demo-task-004', '审批越权守卫演示用例',        '非审批人 approve 须被服务端拒绝',        'doing', '开发', '高', @u_admin, @n_admin, CURDATE() + INTERVAL 3 DAY, 0, 'admin', NOW() - INTERVAL 4 DAY),
('demo-task-005', '审计日志检索验收',            'bizType+bizId 组合过滤响应 <1s',          'doing', '需求', '中', @u_he,    '贺致远', CURDATE() - INTERVAL 1 DAY, 1, 'admin', NOW() - INTERVAL 4 DAY),
('demo-task-006', '演示种子 SQL 评审',           '本脚本转正前的宿审定',                   'doing', '缺陷', '低', @u_huang, '黄茜茜', CURDATE() + INTERVAL 7 DAY, 2, 'admin', NOW() - INTERVAL 3 DAY),
('demo-task-007', '仪表盘聚合接口联调',          '三计数 + 公告聚合一次返回',              'done',  '开发', '高', @u_admin, @n_admin, CURDATE() - INTERVAL 3 DAY, 0, 'admin', NOW() - INTERVAL 6 DAY),
('demo-task-008', '冒烟脚本 Git Bash 适配',      'curl 判定与 Windows start 打开页面',     'done',  '开发', '中', @u_he,    '贺致远', CURDATE() - INTERVAL 2 DAY, 1, 'admin', NOW() - INTERVAL 5 DAY),
('demo-task-009', '公告未读口径核对',            'read_flag=0 计数与 listByUser 对齐',     'done',  '需求', '低', @u_huang, '黄茜茜', CURDATE() - INTERVAL 4 DAY, 2, 'admin', NOW() - INTERVAL 7 DAY);

-- ---------------------------------------------------------------------------
-- 2. 审批全状态链：pending(step1) / pending(step2) / approved / rejected / pending(试点链)
--    注意：步骤 approver_name 与 service 守卫按 realname 比对；
--    管理员 realname 通过 @n_admin 解析，保证 admin 登录即有可批待办。
-- ---------------------------------------------------------------------------
DELETE FROM team_approval_step WHERE approval_id LIKE 'demo-appr-%';
DELETE FROM team_approval       WHERE id LIKE 'demo-appr-%';

INSERT INTO team_approval (id, code, category, title, reason, status, applicant_id, applicant_name, start_date, end_date, current_step, create_by, create_time) VALUES
('demo-appr-001', 'OA-DEMO-001', '请假申请', '年假 2 天',             '家中事务，已交接演示准备工作', 'pending',  @u_he,    '贺致远', CURDATE() + INTERVAL 10 DAY, CURDATE() + INTERVAL 11 DAY, 1, 'admin', NOW() - INTERVAL 3 HOUR),
('demo-appr-002', 'OA-DEMO-002', '加班申请', '版本冲刺加班',         'M3 收尾，申请本周六加班 1 天', 'pending',  @u_admin, @n_admin, CURDATE(),                   CURDATE(),                   2, 'admin', NOW() - INTERVAL 26 HOUR),
('demo-appr-003', 'OA-DEMO-003', '费用报销', '客户现场差旅报销',     '高铁+住宿，票据齐全',          'approved', @u_he,    '贺致远', CURDATE() - INTERVAL 12 DAY, CURDATE() - INTERVAL 10 DAY, 2, 'admin', NOW() - INTERVAL 3 DAY),
('demo-appr-004', 'OA-DEMO-004', '出差申请', '客户现场支持 3 天',    '试点环境部署跟进',             'rejected', @u_admin, @n_admin, CURDATE() + INTERVAL 5 DAY,  CURDATE() + INTERVAL 7 DAY,  1, 'admin', NOW() - INTERVAL 2 DAY),
('demo-appr-005', 'OA-DEMO-005', '加班申请', '周末值班',             '值班响应演示环境故障',         'pending',  @u_admin, @n_admin, CURDATE() + INTERVAL 3 DAY,  CURDATE() + INTERVAL 3 DAY,  1, 'admin', NOW() - INTERVAL 1 HOUR);

-- 步骤链（与主单 current_step / status 严格一致，审计追溯演示的数据基础）
INSERT INTO team_approval_step (id, approval_id, step_no, approver_name, approver_role, action, comment, acted_at, create_time) VALUES
('demo-step-001a', 'demo-appr-001', 1, @n_admin,  '部门主管', 'wait',     NULL,                 NULL,                             NOW() - INTERVAL 3 HOUR),
('demo-step-001b', 'demo-appr-001', 2, '黄茜茜',  '副总经理', 'wait',     NULL,                 NULL,                             NOW() - INTERVAL 3 HOUR),
('demo-step-002a', 'demo-appr-002', 1, @n_admin,  '部门主管', 'approved', '同意，注意调休',     NOW() - INTERVAL 20 HOUR,         NOW() - INTERVAL 26 HOUR),
('demo-step-002b', 'demo-appr-002', 2, '黄茜茜',  '副总经理', 'wait',     NULL,                 NULL,                             NOW() - INTERVAL 26 HOUR),
('demo-step-003a', 'demo-appr-003', 1, @n_admin,  '部门主管', 'approved', '票据齐全，同意',     NOW() - INTERVAL 2 DAY,           NOW() - INTERVAL 3 DAY),
('demo-step-003b', 'demo-appr-003', 2, '黄茜茜',  '副总经理', 'approved', '同意，按财务流程打款', NOW() - INTERVAL 1 DAY,         NOW() - INTERVAL 3 DAY),
('demo-step-004a', 'demo-appr-004', 1, @n_admin,  '部门主管', 'rejected', '预算不足，请调整后重报', NOW() - INTERVAL 1 DAY,       NOW() - INTERVAL 2 DAY),
('demo-step-005a', 'demo-appr-005', 1, '贺致远',  '部门主管', 'wait',     NULL,                 NULL,                             NOW() - INTERVAL 1 HOUR),
('demo-step-005b', 'demo-appr-005', 2, '黄茜茜',  '副总经理', 'wait',     NULL,                 NULL,                             NOW() - INTERVAL 1 HOUR);

-- ---------------------------------------------------------------------------
-- 3. Bug 全链路：open / processing / resolved / closed
-- ---------------------------------------------------------------------------
DELETE FROM team_bug WHERE id LIKE 'demo-bug-%';

INSERT INTO team_bug (id, description, status, reporter_id, reporter_name, handler_name, create_by, create_time) VALUES
('demo-bug-001', '看板拖拽到 done 后偶发排序回跳，刷新恢复',     'open',       @u_he,    '贺致远', NULL,     'admin', NOW() - INTERVAL 5 HOUR),
('demo-bug-002', '登录页弱网时提交按钮无 loading 态，可重复点击', 'processing', @u_admin, @n_admin, '黄茜茜', 'admin', NOW() - INTERVAL 2 DAY),
('demo-bug-003', '审批中心切换 tab 后待办计数不刷新',             'resolved',   @u_huang, '黄茜茜', @n_admin, 'admin', NOW() - INTERVAL 3 DAY),
('demo-bug-004', '个人报表 iframe 高度在小屏下撑不满',             'closed',     @u_admin, @n_admin, '贺致远', 'admin', NOW() - INTERVAL 5 DAY);

-- ---------------------------------------------------------------------------
-- 4. 审计轨迹（只追加表）：覆盖 submit / change_status / approve / reject
--    让「审计追溯」演示不依赖当场操作也有存量可查
-- ---------------------------------------------------------------------------
DELETE FROM team_audit_log WHERE biz_id LIKE 'demo-%';

INSERT INTO team_audit_log (id, biz_type, biz_id, biz_code, action, from_value, to_value, operator_id, operator_name, detail, create_by, create_time) VALUES
('demo-audit-001', 'task',     'demo-task-007', '仪表盘聚合接口联调',   'change_status', 'todo',   'doing', @u_admin, @n_admin, '看板拖拽流转',        'admin', NOW() - INTERVAL 5 DAY),
('demo-audit-002', 'task',     'demo-task-007', '仪表盘聚合接口联调',   'change_status', 'doing',  'done',  @u_admin, @n_admin, '看板拖拽流转',        'admin', NOW() - INTERVAL 4 DAY),
('demo-audit-003', 'task',     'demo-task-004', '审批越权守卫演示用例', 'change_status', 'todo',   'doing', @u_admin, @n_admin, '按钮流转',            'admin', NOW() - INTERVAL 3 DAY),
('demo-audit-004', 'task',     'demo-task-005', '审计日志检索验收',     'change_status', 'todo',   'doing', @u_he,    '贺致远', '按钮流转',            'admin', NOW() - INTERVAL 3 DAY),
('demo-audit-010', 'approval', 'demo-appr-003', 'OA-DEMO-003',          'submit',        NULL,     'pending', @u_he,  '贺致远', '步骤快照 v1',         'admin', NOW() - INTERVAL 3 DAY),
('demo-audit-011', 'approval', 'demo-appr-003', 'OA-DEMO-003',          'approve',       'step:1', 'step:2',  @u_admin, @n_admin, '票据齐全，同意',  'admin', NOW() - INTERVAL 2 DAY),
('demo-audit-012', 'approval', 'demo-appr-003', 'OA-DEMO-003',          'approve',       'step:2', 'approved', @u_huang, '黄茜茜', '同意，按财务流程打款', 'admin', NOW() - INTERVAL 1 DAY),
('demo-audit-013', 'approval', 'demo-appr-004', 'OA-DEMO-004',          'submit',        NULL,     'pending', @u_admin, @n_admin, '步骤快照 v1',     'admin', NOW() - INTERVAL 2 DAY),
('demo-audit-014', 'approval', 'demo-appr-004', 'OA-DEMO-004',          'reject',        'step:1', 'rejected', @u_admin, @n_admin, '预算不足，请调整后重报', 'admin', NOW() - INTERVAL 1 DAY),
('demo-audit-015', 'approval', 'demo-appr-002', 'OA-DEMO-002',          'submit',        NULL,     'pending', @u_admin, @n_admin, '步骤快照 v1',     'admin', NOW() - INTERVAL 26 HOUR),
('demo-audit-016', 'approval', 'demo-appr-002', 'OA-DEMO-002',          'approve',       'step:1', 'step:2',  @u_admin, @n_admin, '同意，注意调休',  'admin', NOW() - INTERVAL 20 HOUR),
('demo-audit-020', 'bug',      'demo-bug-003',  '审批中心切换 tab 后待办计数不刷新', 'change_status', 'open',       'processing', @u_huang, '黄茜茜', '复现确认，处理中', 'admin', NOW() - INTERVAL 2 DAY),
('demo-audit-021', 'bug',      'demo-bug-003',  '审批中心切换 tab 后待办计数不刷新', 'change_status', 'processing', 'resolved',   @u_admin, @n_admin, '修复已验证',       'admin', NOW() - INTERVAL 1 DAY),
('demo-audit-022', 'bug',      'demo-bug-004',  '个人报表 iframe 高度在小屏下撑不满', 'change_status', 'open',     'closed',   @u_he, '贺致远', '样式修复，直接关闭', 'admin', NOW() - INTERVAL 4 DAY);

-- ---------------------------------------------------------------------------
-- 5. 公告 3 条（上游表，注意 titile 为上游历史拼写）+ admin 阅读标记
--    口径（TeamDashboardController V1.1 头注）：未读 = send 表 read_flag=0；
--    最新公告 = 已发布(send_status='1') 按 send_time 倒序 5 条。
--    ⚠ listByUser 实际过滤条件以联调为准（数据范围近一月，F-01/F-08）。
-- ---------------------------------------------------------------------------
DELETE FROM sys_announcement_send WHERE annt_id LIKE 'demo-annt-%';
DELETE FROM sys_announcement       WHERE id LIKE 'demo-annt-%';

INSERT INTO sys_announcement (id, titile, msg_content, sender, priority, msg_category, msg_type, send_status, send_time, del_flag, tenant_id, iz_top, create_by, create_time) VALUES
('demo-annt-001', '演示环境维护通知', '本周五 20:00-22:00 演示库例行维护，期间请勿提交正式数据。', @n_admin, 'H', '2', 'ALL', '1', NOW() - INTERVAL 6 HOUR,  '0', 0, 0, 'admin', NOW() - INTERVAL 6 HOUR),
('demo-annt-002', '试点周会改期',     '本周试点周会由周三下午改至周四上午 10:00，会议室不变。',     @n_admin, 'M', '2', 'ALL', '1', NOW() - INTERVAL 30 HOUR, '0', 0, 0, 'admin', NOW() - INTERVAL 30 HOUR),
('demo-annt-003', '新版本功能预告',   '任务看板拖拽与审批中心即将上线，敬请期待。',                 @n_admin, 'L', '2', 'ALL', '1', NOW() - INTERVAL 3 DAY,   '0', 0, 0, 'admin', NOW() - INTERVAL 3 DAY);

-- admin 视角：2 未读 + 1 已读（dashboard 未读计数 = 2）
INSERT INTO sys_announcement_send (id, annt_id, user_id, read_flag, read_time, create_time) VALUES
('demo-send-001', 'demo-annt-001', @u_admin, 0, NULL,              NOW() - INTERVAL 6 HOUR),
('demo-send-002', 'demo-annt-002', @u_admin, 0, NULL,              NOW() - INTERVAL 30 HOUR),
('demo-send-003', 'demo-annt-003', @u_admin, 1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 3 DAY);

COMMIT;

-- ---------------------------------------------------------------------------
-- 执行后自检（期望输出见 docs/25 §二.6）
-- ---------------------------------------------------------------------------
-- SELECT status, COUNT(*) FROM team_task GROUP BY status;
-- SELECT status, COUNT(*) FROM team_approval GROUP BY status;
-- SELECT status, COUNT(*) FROM team_bug GROUP BY status;
-- SELECT biz_type, action, COUNT(*) FROM team_audit_log WHERE biz_id LIKE 'demo-%' GROUP BY biz_type, action;
-- SELECT read_flag, COUNT(*) FROM sys_announcement_send WHERE annt_id LIKE 'demo-annt-%' GROUP BY read_flag;
