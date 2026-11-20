-- 组织与成员初始化：以"控股集团"（A05）多层级企业模型为组织模板材料
-- 日期：2026-11-20；目标库：jeecg-boot（本机 dev）；幂等可重复执行
-- 内容：
--   1) 成员按角色身份安置进 A05 体系部门（sys_user.org_code）
--   2) 清退 jeecg 演示组织（北京国炬 A01 / 卓尔 A02），逻辑删除（del_flag='1'，可恢复）

-- ============ 1. 成员安置（org_code = 部门节点） ============
-- 控股集团 A05
--   ├─ 投资控股集团有限公司 A05A01
--   │   ├─ 领导班子 A05A01A01
--   │   ├─ 办公室 A05A01A02
--   │   ├─ 财务管理中心 A05A01A03
--   │   └─ 投资发展部 A05A01A04
--   └─ 城市运营管理集团有限公司 A05A02
--       ├─ 领导班子 A05A02A01
--       ├─ 办公室 A05A02A04
--       └─ 信息技术发展有限公司 A05A02A03
--           ├─ 综合管理部 A05A02A03A02
--           ├─ 财务部 A05A02A03A03
--           └─ 软件研发部 A05A02A03A04
UPDATE sys_user SET org_code = 'A05A01A02' WHERE username = 'admin';            -- 管理员 → 集团办公室
UPDATE sys_user SET org_code = 'A05A02A03A02' WHERE username = 'lanou_admin';    -- 蓝鸥管理员 → 信息技术/综合管理部
UPDATE sys_user SET org_code = 'A05A02A01' WHERE username = 'lanou_vp';          -- 陈静(副总级) → 城市运营/领导班子
UPDATE sys_user SET org_code = 'A05A02A03A04' WHERE username = 'lanou_mgr';      -- 王凯(主管) → 信息技术/软件研发部
UPDATE sys_user SET org_code = 'A05A02A03A04' WHERE username = 'lanou_emp';      -- 李想(成员) → 信息技术/软件研发部
UPDATE sys_user SET org_code = 'A05A02A03A04' WHERE username = 'zhoujingxiao';   -- 周景潇(开发) → 信息技术/软件研发部
UPDATE sys_user SET org_code = 'A05A01A02' WHERE username = 'zhangsan';          -- 张三 → 集团办公室
UPDATE sys_user SET org_code = 'A05A01A03' WHERE username = 'jeecg';             -- jeecg → 集团财务管理中心
-- ceshi（测试用户）、migtest_web（迁移测试）保持游离不挂部门，避免污染模板

-- ============ 2. 清退 jeecg 演示组织（逻辑删除，迁移纪律不硬删） ============
-- 北京国炬软件 A01 及子级、北京卓尔互动 A02 及子级
UPDATE sys_depart SET del_flag = '1' WHERE del_flag = '0' AND (org_code LIKE 'A01%' OR org_code LIKE 'A02%');
