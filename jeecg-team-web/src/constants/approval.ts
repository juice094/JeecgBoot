// 审批类别元数据（前端展示层）
// key 与后端 team_approval.category 字符串严格对齐。
// 后端 v2 已支持按 category 查启用模板生成审批链；模板缺失时回退到客户端传入的 steps。
// 这里的 description / tip 只影响前端展示，不参与审批链生成。

export interface ApprovalCategoryMeta {
  key: string;                 // 传给后端的 category 值
  group: string;               // 侧栏分组
  description: string;         // 表单头部说明（随类别切换）
  tip?: string;                // 表单提示条
  requireDateRange?: boolean;  // 是否必须填起止日期，缺省为 true
}

export const APPROVAL_CATEGORIES: ApprovalCategoryMeta[] = [
  {
    key: '请假申请',
    group: '考勤与假期',
    description: '按公司假勤制度提交，需注明请假类型与时段',
    tip: '半天以上需提前 1 个工作日提交；病假需附证明（附件暂不支持）',
    requireDateRange: true,
  },
  {
    key: '出差申请',
    group: '考勤与假期',
    description: '出差前提交，便于差旅与费用报销关联',
    tip: '出差单批复后可作为报销依据',
    requireDateRange: true,
  },
  {
    key: '外出申请',
    group: '考勤与假期',
    description: '工作时间内因公外出登记',
    tip: '返回后请在考勤中补齐记录',
    requireDateRange: true,
  },
  {
    key: '补卡申请',
    group: '考勤与假期',
    description: '漏打卡后补登，需说明原因',
    tip: '每月补卡次数上限以考勤制度为准',
    requireDateRange: true,
  },
  {
    key: '加班申请',
    group: '考勤与假期',
    description: '非工作时间（含周末、节假日）加班',
    tip: '工作日 9:00-18:00 为正常上班时段；此时段以外工作请提交本申请。',
    requireDateRange: true,
  },
  {
    key: '费用报销',
    group: '费用与财务',
    description: '日常费用、差旅费等报销申请',
    tip: '需附发票（附件暂不支持），金额以财务复核为准',
    requireDateRange: false,
  },
  {
    key: '采购申请',
    group: '费用与财务',
    description: '办公用品、设备等采购申请',
    tip: '超过一定金额将按模板走多级审批',
    requireDateRange: false,
  },
];

export function getCategoryMeta(key: string): ApprovalCategoryMeta | undefined {
  return APPROVAL_CATEGORIES.find((c) => c.key === key);
}

/** 按 group 聚合，保持声明顺序 */
export function groupApprovalCategories(): { group: string; items: ApprovalCategoryMeta[] }[] {
  const map = new Map<string, ApprovalCategoryMeta[]>();
  for (const c of APPROVAL_CATEGORIES) {
    if (!map.has(c.group)) map.set(c.group, []);
    map.get(c.group)!.push(c);
  }
  return Array.from(map.entries()).map(([group, items]) => ({ group, items }));
}
