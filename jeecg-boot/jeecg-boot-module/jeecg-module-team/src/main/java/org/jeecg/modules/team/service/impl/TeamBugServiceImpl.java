package org.jeecg.modules.team.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.mapper.TeamBugMapper;
import org.jeecg.modules.team.service.ITeamBugService;
import org.springframework.stereotype.Service;

@Service
public class TeamBugServiceImpl extends ServiceImpl<TeamBugMapper, TeamBug> implements ITeamBugService {
}
