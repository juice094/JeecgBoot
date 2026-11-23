package org.jeecg.modules.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.team.entity.TeamBug;

/**
 * @Description: 团队协作-Bug反馈 Mapper
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Mapper
public interface TeamBugMapper extends BaseMapper<TeamBug> {

    /**
     * 全局最大缺陷序号（含逻辑删行；uk_team_bug_no 唯一索引覆盖全表，
     * nextBugNo 必须跨越逻辑删行取值，否则复用已删序号撞唯一键）
     */
    @Select("SELECT MAX(bug_no) FROM team_bug")
    Integer selectMaxBugNo();
}
