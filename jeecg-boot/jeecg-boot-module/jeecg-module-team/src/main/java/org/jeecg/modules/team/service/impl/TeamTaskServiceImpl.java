package org.jeecg.modules.team.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.springframework.stereotype.Service;

/**
 * @Description: 团队协作-任务 ServiceImpl
 * @Author: Kimi Code (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamTaskServiceImpl extends ServiceImpl<TeamTaskMapper, TeamTask> implements ITeamTaskService {
}
