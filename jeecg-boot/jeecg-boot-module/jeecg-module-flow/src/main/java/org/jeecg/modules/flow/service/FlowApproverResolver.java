package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批办理人组织链解析（W3）。
 *
 * 解析口径（注释即契约，改动需同步本注释）：
 * <ul>
 *   <li>部门主管：申请人所属部门（sys_user_depart，同学部）内职位为「部长」
 *       （sys_position.post_level=4）或「副部长」（post_level=5）的用户，取职级最高者
 *       （部长优先于副部长）。本检出的 sys_depart 无「负责人」持久化列
 *       （directorUserIds 仅为上游瞬态字段），故「部门负责人」语义落地为
 *       「同学部管理职级用户」，与本库 sys_position 实际数据（部长/副部长）对齐。</li>
 *   <li>副总：角色解析——按候选 role_code 集合查 sys_role/sys_user_role/sys_user。
 *       当前库角色模板（标配/业务成员/业务主管/系统管理员）不含副总角色，
 *       预期解析不到，由调用方走显式参数兜底（W2 既有口径）。</li>
 * </ul>
 * 兜底纪律：两个方法解析不到一律返回 null，绝不抛异常；调用方负责回退到
 * 显式传入的办理人（team 侧为审批链步骤审批人，W2 侧为 start 参数）。
 */
@Slf4j
@Service
public class FlowApproverResolver {

    /** 管理职级：部长（优先） */
    private static final int POST_LEVEL_MINISTER = 4;
    /** 管理职级：副部长（次选） */
    private static final int POST_LEVEL_DEPUTY_MINISTER = 5;
    /** 副总候选角色编码（当前库无命中属预期，走显式兜底） */
    private static final List<String> VP_ROLE_CODES = List.of("vp", "vice_president", "deputy_general_manager");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 解析申请人的部门主管（username）。
     *
     * @param applicantUserId 申请人 sys_user.id
     * @return 主管 username；无管理职级用户 / 申请人无部门 / 入参为空时返回 null
     */
    public String resolveDeptManagerUsername(String applicantUserId) {
        if (StringUtils.isBlank(applicantUserId)) {
            return null;
        }
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT u.username FROM sys_user_depart ud "
                        + "JOIN sys_user_depart ud2 ON ud2.dep_id = ud.dep_id "
                        + "JOIN sys_user u ON u.id = ud2.user_id AND u.del_flag = '0' AND u.status = 1 "
                        + "JOIN sys_user_position up ON up.user_id = u.id "
                        + "JOIN sys_position p ON p.id = up.position_id "
                        + "WHERE ud.user_id = ? AND p.post_level IN (?, ?) "
                        + "ORDER BY p.post_level ASC, u.create_time ASC LIMIT 1",
                String.class, applicantUserId, POST_LEVEL_MINISTER, POST_LEVEL_DEPUTY_MINISTER);
        if (usernames.isEmpty()) {
            log.info("dept manager unresolved by position, applicantUserId={}", applicantUserId);
            return null;
        }
        return usernames.get(0);
    }

    /**
     * 按角色解析副总（username）。
     *
     * @param applicantUserId 申请人 sys_user.id（预留：后续可按申请人公司域缩小范围；当前全库解析）
     * @return 副总 username；无候选角色 / 角色无在用在户时返回 null
     */
    public String resolveVpUsername(String applicantUserId) {
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT u.username FROM sys_role r "
                        + "JOIN sys_user_role ur ON ur.role_id = r.id "
                        + "JOIN sys_user u ON u.id = ur.user_id AND u.del_flag = '0' AND u.status = 1 "
                        + "WHERE r.role_code IN (?, ?, ?) "
                        + "ORDER BY u.create_time ASC LIMIT 1",
                String.class, VP_ROLE_CODES.get(0), VP_ROLE_CODES.get(1), VP_ROLE_CODES.get(2));
        if (usernames.isEmpty()) {
            log.info("vp unresolved by role, applicantUserId={}, candidateCodes={}", applicantUserId, VP_ROLE_CODES);
            return null;
        }
        return usernames.get(0);
    }

    /**
     * 按 sys_user.id 反查 username（team 审批链步骤兜底用：解析不到组织角色时，
     * 引擎办理人回退到步骤上已显式指定的审批人）。
     *
     * @param userId sys_user.id
     * @return username；查不到返回 null
     */
    public String resolveUsernameById(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT username FROM sys_user WHERE id = ? AND del_flag = '0'",
                String.class, userId);
        return usernames.isEmpty() ? null : usernames.get(0);
    }
}
