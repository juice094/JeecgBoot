package org.jeecg.modules.team.service.impl;

import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.jeecg.modules.team.service.ITeamAuditLogService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-审计日志 ServiceImpl（只读；写入统一走 TeamAuditLogger）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamAuditLogService extends ServiceImpl<TeamAuditLogMapper, TeamAuditLog> implements ITeamAuditLogService {
}
