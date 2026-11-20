package org.jeecg.modules.system.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.permission.PermissionGrantEvent;
import org.jeecg.common.permission.PermissionGrantStatus;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.config.shiro.ShiroRealm;
import org.jeecg.modules.system.entity.SysPermissionGrant;
import org.jeecg.modules.system.entity.SysPermissionGrantStep;
import org.jeecg.modules.system.mapper.SysPermissionGrantMapper;
import org.jeecg.modules.system.mapper.SysPermissionGrantStepMapper;
import org.jeecg.modules.system.service.ISysPermissionGrantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * @Description: 权限确权-授权关系 Service 实现
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 */
@Slf4j
@Service
public class SysPermissionGrantServiceImpl extends ServiceImpl<SysPermissionGrantMapper, SysPermissionGrant>
        implements ISysPermissionGrantService {

    private static final String STEP_WAIT = "wait";
    private static final String STEP_APPROVE = "approve";
    private static final String STEP_REJECT = "reject";
    private static final String STEP_RETURN = "return";
    private static final String SUBJECT_USER = "USER";
    private static final String SUBJECT_ROLE = "ROLE";
    private static final String SUBJECT_DEPT = "DEPT";
    private static final String SOURCE_APPLY = "APPLY";

    @Autowired
    private SysPermissionGrantStepMapper stepMapper;

    /**
     * 回收/生效需清 Shiro 授权缓存；@Lazy 避免 Realm → commonApi → 本服务 的装配期环
     */
    @Autowired
    @Lazy
    private ShiroRealm shiroRealm;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public SysPermissionGrant apply(SysPermissionGrant grant) {
        if (grant == null) {
            throw new JeecgBootException("申请参数不能为空");
        }
        if (grant.getPermissionId() == null || grant.getPermissionId().trim().isEmpty()) {
            throw new JeecgBootException("必须指定申请权限");
        }
        if (grant.getSubjectId() == null || grant.getSubjectId().trim().isEmpty()) {
            throw new JeecgBootException("必须指定授权主体");
        }
        if (grant.getSubjectType() == null || !isValidSubjectType(grant.getSubjectType())) {
            throw new JeecgBootException("主体类型仅支持 USER/ROLE/DEPT");
        }
        if (grant.getApplyReason() == null || grant.getApplyReason().trim().isEmpty()) {
            throw new JeecgBootException("申请理由必填");
        }
        String designatedApprover = grant.getApprover();
        if (designatedApprover == null || designatedApprover.trim().isEmpty()) {
            throw new JeecgBootException("必须指定第 1 步审批人");
        }
        Date now = new Date();
        Date validFrom = grant.getValidFrom() == null ? now : grant.getValidFrom();
        if (grant.getValidUntil() != null && !grant.getValidUntil().after(validFrom)) {
            throw new JeecgBootException("生效终点必须晚于生效起点");
        }
        String username = currentUsername();
        grant.setId(null);
        grant.setGrantNo(generateGrantNo());
        grant.setStatus(PermissionGrantStatus.PENDING.getCode());
        grant.setApplicant(username);
        grant.setApprover(null);
        grant.setCurrentStep(1);
        grant.setSource(SOURCE_APPLY);
        grant.setValidFrom(validFrom);
        grant.setCreateBy(username);
        grant.setCreateTime(now);
        grant.setUpdateBy(username);
        grant.setUpdateTime(now);
        grant.setDeleted(0);
        save(grant);

        SysPermissionGrantStep step = new SysPermissionGrantStep()
                .setGrantId(grant.getId())
                .setStepNo(1)
                .setApprover(designatedApprover)
                .setAction(STEP_WAIT);
        stepMapper.insert(step);
        publish(PermissionGrantEvent.SUBMITTED, grant, username, designatedApprover);
        return grant;
    }

    @Override
    public void approve(String id, String comment) {
        SysPermissionGrant grant = requireGrant(id);
        requireStatus(grant, PermissionGrantStatus.PENDING);
        String username = currentUsername();
        SysPermissionGrantStep step = requireCurrentStep(grant);
        if (!username.equals(step.getApprover())) {
            throw new JeecgBootException("当前用户不是本单第 " + grant.getCurrentStep() + " 步审批人");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.PENDING.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.APPROVED.getCode())
                .set(SysPermissionGrant::getApprover, username)
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
        closeStep(step, STEP_APPROVE, comment, username, now);
        publish(PermissionGrantEvent.APPROVED, grant, username);

        // 生产化：已到生效时间则立即 ACTIVE（系统生效，禁止人工直写）
        if (grant.getValidFrom() == null || !grant.getValidFrom().after(now)) {
            activate(grant, username, now);
        }
    }

    @Override
    public void reject(String id, String comment) {
        SysPermissionGrant grant = requireGrant(id);
        requireStatus(grant, PermissionGrantStatus.PENDING);
        String username = currentUsername();
        SysPermissionGrantStep step = requireCurrentStep(grant);
        if (!username.equals(step.getApprover())) {
            throw new JeecgBootException("当前用户不是本单第 " + grant.getCurrentStep() + " 步审批人");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.PENDING.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.REJECTED.getCode())
                .set(SysPermissionGrant::getApprover, username)
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
        closeStep(step, STEP_REJECT, comment, username, now);
        publish(PermissionGrantEvent.REJECTED, grant, username);
    }

    @Override
    public void returnBack(String id, String comment) {
        SysPermissionGrant grant = requireGrant(id);
        requireStatus(grant, PermissionGrantStatus.PENDING);
        String username = currentUsername();
        SysPermissionGrantStep step = requireCurrentStep(grant);
        if (!username.equals(step.getApprover())) {
            throw new JeecgBootException("当前用户不是本单第 " + grant.getCurrentStep() + " 步审批人");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.PENDING.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.RETURNED.getCode())
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
        closeStep(step, STEP_RETURN, comment, username, now);
    }

    @Override
    public void resubmit(String id) {
        SysPermissionGrant grant = requireGrant(id);
        requireStatus(grant, PermissionGrantStatus.RETURNED);
        String username = currentUsername();
        if (!username.equals(grant.getApplicant())) {
            throw new JeecgBootException("仅申请人可重提");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.RETURNED.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.PENDING.getCode())
                .set(SysPermissionGrant::getCurrentStep, 1)
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
        SysPermissionGrantStep lastStep = stepMapper.selectOne(new LambdaQueryWrapper<SysPermissionGrantStep>()
                .eq(SysPermissionGrantStep::getGrantId, id)
                .orderByDesc(SysPermissionGrantStep::getStepNo)
                .last("limit 1"));
        if (lastStep == null || lastStep.getApprover() == null) {
            throw new JeecgBootException("审批步骤缺失，无法重提: id=" + id);
        }
        SysPermissionGrantStep step = new SysPermissionGrantStep()
                .setGrantId(id)
                .setStepNo(1)
                .setApprover(lastStep.getApprover())
                .setAction(STEP_WAIT);
        stepMapper.insert(step);
    }

    @Override
    public void cancel(String id) {
        SysPermissionGrant grant = requireGrant(id);
        String username = currentUsername();
        if (!username.equals(grant.getApplicant())) {
            throw new JeecgBootException("仅申请人可撤销");
        }
        PermissionGrantStatus from = PermissionGrantStatus.fromCode(grant.getStatus());
        if (from == null || !from.canTransitTo(PermissionGrantStatus.CANCELED)) {
            throw new JeecgBootException("当前状态不允许撤销: " + grant.getStatus());
        }
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, grant.getStatus())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.CANCELED.getCode())
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
    }

    @Override
    public void revoke(String id, String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw new JeecgBootException("回收必须填写理由");
        }
        SysPermissionGrant grant = requireGrant(id);
        requireStatus(grant, PermissionGrantStatus.ACTIVE);
        String username = currentUsername();
        Date now = new Date();
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, id)
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.ACTIVE.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.REVOKED.getCode())
                .set(SysPermissionGrant::getExtNote, comment)
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单状态已变化: id=" + id);
        }
        evictAuthorizationCache(grant);
        publish(PermissionGrantEvent.REVOKED, grant, username);
    }

    @Override
    public IPage<SysPermissionGrant> pageQuery(Integer pageNo, Integer pageSize, String status, String subjectType) {
        String username = currentUsername();
        LambdaQueryWrapper<SysPermissionGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), SysPermissionGrant::getStatus, status)
                .eq(subjectType != null && !subjectType.trim().isEmpty(), SysPermissionGrant::getSubjectType, subjectType)
                .and(w -> w.eq(SysPermissionGrant::getApplicant, username)
                        .or()
                        .apply("id IN (SELECT grant_id FROM sys_permission_grant_step WHERE approver = {0})", username))
                .orderByDesc(SysPermissionGrant::getCreateTime);
        return page(new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    public List<SysPermissionGrant> myGrants() {
        String username = currentUsername();
        List<SysPermissionGrant> result = new ArrayList<>();
        result.addAll(list(new LambdaQueryWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getApplicant, username)
                .orderByDesc(SysPermissionGrant::getCreateTime)));
        result.addAll(list(new LambdaQueryWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.PENDING.getCode())
                .apply("id IN (SELECT grant_id FROM sys_permission_grant_step WHERE approver = {0} AND action = 'wait')", username)));
        return result;
    }

    @Override
    public Set<String> queryActiveGrantedPerms(String userId) {
        Set<String> perms = new HashSet<>();
        if (userId == null || userId.trim().isEmpty()) {
            return perms;
        }
        List<String> list = baseMapper.selectActiveGrantedPerms(userId);
        if (list != null) {
            for (String perm : list) {
                if (perm != null && !perm.trim().isEmpty()) {
                    perms.add(perm);
                }
            }
        }
        return perms;
    }

    private void activate(SysPermissionGrant grant, String username, Date now) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getId, grant.getId())
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.APPROVED.getCode())
                .set(SysPermissionGrant::getStatus, PermissionGrantStatus.ACTIVE.getCode())
                .set(SysPermissionGrant::getUpdateBy, username)
                .set(SysPermissionGrant::getUpdateTime, now));
        if (rows == 0) {
            throw new JeecgBootException("并发冲突：授权单生效失败: id=" + grant.getId());
        }
        evictAuthorizationCache(grant);
        publish(PermissionGrantEvent.ACTIVATED, grant, username);
    }

    @Override
    public int expireDue(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 1000));
        Date now = new Date();
        List<SysPermissionGrant> due = list(new LambdaQueryWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.ACTIVE.getCode())
                .isNotNull(SysPermissionGrant::getValidUntil)
                .le(SysPermissionGrant::getValidUntil, now)
                .last("limit " + limit));
        int migrated = 0;
        for (SysPermissionGrant grant : due) {
            int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                    .eq(SysPermissionGrant::getId, grant.getId())
                    .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.ACTIVE.getCode())
                    .set(SysPermissionGrant::getStatus, PermissionGrantStatus.EXPIRED.getCode())
                    .set(SysPermissionGrant::getUpdateBy, "system")
                    .set(SysPermissionGrant::getUpdateTime, now));
            if (rows > 0) {
                migrated++;
                evictAuthorizationCache(grant);
                publish(PermissionGrantEvent.EXPIRED, grant, "system");
            }
        }
        if (migrated > 0) {
            log.info("授权到期扫描：迁移 {} 条 ACTIVE → EXPIRED", migrated);
        }
        return migrated;
    }

    @Override
    public int notifyExpiring(int days) {
        int aheadDays = days > 0 ? days : 3;
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, aheadDays);
        Date deadline = calendar.getTime();
        List<SysPermissionGrant> soon = list(new LambdaQueryWrapper<SysPermissionGrant>()
                .eq(SysPermissionGrant::getStatus, PermissionGrantStatus.ACTIVE.getCode())
                .eq(SysPermissionGrant::getExpiringNotified, 0)
                .isNotNull(SysPermissionGrant::getValidUntil)
                .gt(SysPermissionGrant::getValidUntil, now)
                .le(SysPermissionGrant::getValidUntil, deadline)
                .last("limit 500"));
        int notified = 0;
        for (SysPermissionGrant grant : soon) {
            int rows = baseMapper.update(null, new LambdaUpdateWrapper<SysPermissionGrant>()
                    .eq(SysPermissionGrant::getId, grant.getId())
                    .eq(SysPermissionGrant::getExpiringNotified, 0)
                    .set(SysPermissionGrant::getExpiringNotified, 1)
                    .set(SysPermissionGrant::getUpdateBy, "system")
                    .set(SysPermissionGrant::getUpdateTime, now));
            if (rows > 0) {
                notified++;
                publish(PermissionGrantEvent.EXPIRING, grant, "system");
            }
        }
        if (notified > 0) {
            log.info("授权到期提醒：发出 {} 条 grant.expiring（提前 {} 天）", notified, aheadDays);
        }
        return notified;
    }

    /**
     * 发布领域事件。通知失败不应回滚治理动作，因此只记录不抛出；
     * 监听器装配在 jeecg-system-start（可依赖 mail），system 层不依赖 mail。
     */
    private void publish(String eventType, SysPermissionGrant grant, String operator) {
        publish(eventType, grant, operator, grant.getApprover());
    }

    private void publish(String eventType, SysPermissionGrant grant, String operator, String approver) {
        try {
            eventPublisher.publishEvent(new PermissionGrantEvent(this, eventType, grant.getId(), grant.getGrantNo(),
                    grant.getSubjectType(), grant.getSubjectId(), grant.getApplicant(), operator, approver));
        } catch (Exception e) {
            log.warn("发布授权事件失败 type={} grantNo={}: {}", eventType, grant.getGrantNo(), e.getMessage());
        }
    }

    /**
     * 清受影响用户的 Shiro 授权缓存：回收/生效必须让下一请求立刻看到新状态。
     * <p>
     * 实测 shiro-redis 的 RedisCache.clear() 对整区清空是空操作，因此按 principal 精确移除
     * （与 Jeecg 原生 ShiroRealm.clearCache → clearCachedAuthorizationInfo 同路径）。
     */
    private void evictAuthorizationCache(SysPermissionGrant grant) {
        try {
            for (String userId : resolveAffectedUserIds(grant)) {
                LoginUser principal = new LoginUser();
                principal.setId(userId);
                shiroRealm.clearCache(new SimplePrincipalCollection(principal, shiroRealm.getName()));
            }
        } catch (Exception e) {
            log.warn("清除授权缓存失败，授权变更可能延迟生效: grantNo={}, err={}", grant.getGrantNo(), e.getMessage());
        }
    }

    private Set<String> resolveAffectedUserIds(SysPermissionGrant grant) {
        Set<String> userIds = new HashSet<>();
        String subjectType = grant.getSubjectType() == null ? "" : grant.getSubjectType().toUpperCase();
        switch (subjectType) {
            case SUBJECT_USER:
                userIds.add(grant.getSubjectId());
                break;
            case SUBJECT_ROLE:
                userIds.addAll(baseMapper.selectUserIdsByRoleId(grant.getSubjectId()));
                break;
            case SUBJECT_DEPT:
                userIds.addAll(baseMapper.selectUserIdsByDeptId(grant.getSubjectId()));
                break;
            default:
                break;
        }
        return userIds;
    }

    private SysPermissionGrant requireGrant(String id) {
        SysPermissionGrant grant = getById(id);
        if (grant == null) {
            throw new JeecgBootException("授权单不存在: " + id);
        }
        return grant;
    }

    private void requireStatus(SysPermissionGrant grant, PermissionGrantStatus expected) {
        if (!expected.getCode().equals(grant.getStatus())) {
            throw new JeecgBootException("授权单状态不允许该操作，当前: " + grant.getStatus());
        }
    }

    private SysPermissionGrantStep requireCurrentStep(SysPermissionGrant grant) {
        SysPermissionGrantStep step = stepMapper.selectOne(new LambdaQueryWrapper<SysPermissionGrantStep>()
                .eq(SysPermissionGrantStep::getGrantId, grant.getId())
                .eq(SysPermissionGrantStep::getStepNo, grant.getCurrentStep() == null ? 1 : grant.getCurrentStep())
                .eq(SysPermissionGrantStep::getAction, STEP_WAIT)
                .last("limit 1"));
        if (step == null) {
            throw new JeecgBootException("审批步骤缺失或已处理: id=" + grant.getId());
        }
        return step;
    }

    private void closeStep(SysPermissionGrantStep step, String action, String comment, String username, Date now) {
        step.setAction(action);
        step.setComment(comment);
        step.setActedAt(now);
        step.setUpdateBy(username);
        step.setUpdateTime(now);
        stepMapper.updateById(step);
    }

    private boolean isValidSubjectType(String type) {
        String upper = type.trim().toUpperCase();
        return SUBJECT_USER.equals(upper) || SUBJECT_ROLE.equals(upper) || SUBJECT_DEPT.equals(upper);
    }

    private String generateGrantNo() {
        String stamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        return "PG-" + stamp;
    }

    private String currentUsername() {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            throw new JeecgBootException("未获取到登录用户");
        }
        return loginUser.getUsername();
    }
}
