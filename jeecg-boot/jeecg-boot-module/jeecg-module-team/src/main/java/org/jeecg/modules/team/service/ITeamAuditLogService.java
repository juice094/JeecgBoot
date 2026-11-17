package org.jeecg.modules.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamAuditLog;

/**
 * @Description: 团队协作-审计日志 Service（只读查询；写入统一走 TeamAuditLogger 埋点）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public interface ITeamAuditLogService extends IService<TeamAuditLog> {
}
