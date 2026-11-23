package org.jeecg.modules.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.system.entity.SysPermissionGrant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: 权限确权-授权关系 Mapper
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 */
public interface SysPermissionGrantMapper extends BaseMapper<SysPermissionGrant> {

    /**
     * 查询用户当前有效的授予权限串（确权读路径唯一入口）
     * <p>
     * 有效定义见 PERMISSION-GOVERNANCE.md §8.2：
     * status=active + 时间窗口内 + 未逻辑删除。
     * 到期判定发生在读路径，不依赖后台清扫任务。
     *
     * @param userId 用户ID
     * @return 权限串集合（可能为 null 元素，由调用方过滤）
     */
    @Select("SELECT DISTINCT p.perms FROM sys_permission_grant g "
            + "JOIN sys_permission p ON p.id = g.permission_id "
            + "WHERE g.deleted = 0 AND g.status = 'active' "
            + "AND p.del_flag = 0 AND p.perms IS NOT NULL AND p.perms <> '' "
            + "AND (g.valid_from IS NULL OR g.valid_from <= NOW()) "
            + "AND (g.valid_until IS NULL OR g.valid_until > NOW()) "
            + "AND ( (g.subject_type = 'USER' AND g.subject_id = #{userId}) "
            + "   OR (g.subject_type = 'ROLE' AND g.subject_id IN "
            + "       (SELECT role_id FROM sys_user_role WHERE user_id = #{userId})) "
            + "   OR (g.subject_type = 'DEPT' AND g.subject_id IN "
            + "       (SELECT dep_id FROM sys_user_depart WHERE user_id = #{userId})) )")
    List<String> selectActiveGrantedPerms(@Param("userId") String userId);

    /** 角色下的全部用户（回收角色级授权时清缓存用） */
    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<String> selectUserIdsByRoleId(@Param("roleId") String roleId);

    /** 部门下的全部用户（回收部门级授权时清缓存用） */
    @Select("SELECT user_id FROM sys_user_depart WHERE dep_id = #{deptId}")
    List<String> selectUserIdsByDeptId(@Param("deptId") String deptId);
}
