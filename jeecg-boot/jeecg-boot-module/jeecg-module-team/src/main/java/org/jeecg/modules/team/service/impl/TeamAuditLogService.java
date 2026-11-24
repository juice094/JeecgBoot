package org.jeecg.modules.team.service.impl;

import java.util.Collections;
import java.util.List;

import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.jeecg.modules.team.service.ITeamAuditLogService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-审计日志 ServiceImpl（只读；写入统一走 TeamAuditLogger）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * ponytail: 事件流查询用字符串列 QueryWrapper 而非 LambdaQueryWrapper——列名已稳定（biz_type/id/
 * create_time），且 SQL 片段构造不依赖 MyBatis-Plus 的 lambda 列缓存，单测可无 Spring 直接断言。
 */
@Service
public class TeamAuditLogService extends ServiceImpl<TeamAuditLogMapper, TeamAuditLog> implements ITeamAuditLogService {

    /**
     * ponytail: 增量游标用 id 字典序比较——team_audit_log.id 为雪花 long 的十进制字符串，
     * 实际值域固定 19 位（long 正数最大 19 位），定长数字字符串字典序与数值序一致。
     * 若未来更换 id 生成策略（非定长），须改为数值比较或 create_time+id 双键游标。
     */
    @Override
    public List<TeamAuditLog> listLatestForStream(List<String> bizTypes, int limit) {
        QueryWrapper<TeamAuditLog> queryWrapper = new QueryWrapper<>();
        applyBizTypeFilter(queryWrapper, bizTypes);
        queryWrapper.orderByDesc("create_time", "id")
                .last("LIMIT " + limit);
        List<TeamAuditLog> rows = list(queryWrapper);
        Collections.reverse(rows);
        return rows;
    }

    @Override
    public List<TeamAuditLog> listSinceId(String lastId, List<String> bizTypes, int limit) {
        QueryWrapper<TeamAuditLog> queryWrapper = new QueryWrapper<>();
        if (lastId != null) {
            queryWrapper.gt("id", lastId);
        }
        applyBizTypeFilter(queryWrapper, bizTypes);
        queryWrapper.orderByAsc("id").last("LIMIT " + limit);
        return list(queryWrapper);
    }

    @Override
    public String findMaxId(List<String> bizTypes) {
        QueryWrapper<TeamAuditLog> queryWrapper = new QueryWrapper<>();
        applyBizTypeFilter(queryWrapper, bizTypes);
        queryWrapper.select("id").orderByDesc("id").last("LIMIT 1");
        TeamAuditLog row = getOne(queryWrapper, false);
        return row == null ? null : row.getId();
    }

    private void applyBizTypeFilter(QueryWrapper<TeamAuditLog> queryWrapper, List<String> bizTypes) {
        if (bizTypes != null && !bizTypes.isEmpty()) {
            queryWrapper.in("biz_type", bizTypes);
        }
    }
}
