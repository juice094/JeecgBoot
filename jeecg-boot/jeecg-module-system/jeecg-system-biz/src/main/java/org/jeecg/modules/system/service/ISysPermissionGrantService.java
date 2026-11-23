package org.jeecg.modules.system.service;

import java.util.List;
import java.util.Set;

import org.jeecg.modules.system.entity.SysPermissionGrant;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 权限确权-授权关系 Service
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 状态机唯一合法路径见 docs/permission-governance/PERMISSION-GOVERNANCE.md §6.2。
 */
public interface ISysPermissionGrantService extends IService<SysPermissionGrant> {

    /**
     * 提交授权申请：直接落 PENDING，并生成第 1 步待审步骤。
     * {@code grant.approver} 作为指定第 1 步审批人，落步骤后清空（终态回填实际审批人）。
     */
    SysPermissionGrant apply(SysPermissionGrant grant);

    /** 审批通过：末步通过后若已达生效时间则立即 ACTIVE（系统生效，禁止人工直写） */
    void approve(String id, String comment);

    /** 拒绝：终态 */
    void reject(String id, String comment);

    /** 退回：申请可修改后重提 */
    void returnBack(String id, String comment);

    /** 重提：returned → pending，步骤重置为 1 */
    void resubmit(String id);

    /** 撤销：申请人撤回未生效申请 */
    void cancel(String id);

    /** 回收已生效授权：唯一回收路径，禁止物理删除 */
    void revoke(String id, String comment);

    /** 分页查询（默认仅本人相关） */
    IPage<SysPermissionGrant> pageQuery(Integer pageNo, Integer pageSize, String status, String subjectType);

    /** 与当前用户相关的授权单：我发起的 + 待我审批的 */
    List<SysPermissionGrant> myGrants();

    /** 鉴权读路径：用户当前有效的授予权限串 */
    Set<String> queryActiveGrantedPerms(String userId);

    /**
     * 到期扫描：把已过 valid_until 的 ACTIVE 授权迁移为 EXPIRED，清缓存并发事件。
     * 仅做状态落库与通知——鉴权侧到期判定在读路径，不依赖本方法。
     *
     * @param batchSize 单次处理上限，避免长事务
     * @return 本次迁移条数
     */
    int expireDue(int batchSize);

    /**
     * 到期提醒：valid_until 在 [now, now+days) 内且未提醒过的 ACTIVE 授权，
     * 标记已提醒并发 grant.expiring（每个授权仅提醒一次）。
     *
     * @param days 提前天数
     * @return 本次提醒条数
     */
    int notifyExpiring(int days);
}
