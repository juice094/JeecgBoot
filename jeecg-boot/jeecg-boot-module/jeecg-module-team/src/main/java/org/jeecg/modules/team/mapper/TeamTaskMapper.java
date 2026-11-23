package org.jeecg.modules.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.team.entity.TeamTask;

/**
 * @Description: 团队协作-任务 Mapper
 * @Author: juice094 (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Mapper
public interface TeamTaskMapper extends BaseMapper<TeamTask> {

    /**
     * 全局最大任务序号（含逻辑删行；uk_team_task_no 唯一索引覆盖全表，
     * nextTaskNo 必须跨越逻辑删行取值，否则复用已删序号撞唯一键）
     */
    @Select("SELECT MAX(task_no) FROM team_task")
    Integer selectMaxTaskNo();
}
