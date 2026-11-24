package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.vo.TeamTaskBoard;
import org.jeecg.modules.team.vo.TeamTaskDetail;

/**
 * @Description: 团队协作-任务 Service
 * @Author: juice094 (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public interface ITeamTaskService extends IService<TeamTask> {

    /**
     * 状态流转：按状态机校验目标状态与流转路径，非法流转抛 JeecgBootException，
     * 成功后回填 update_by 并写审计留痕
     *
     * @param id     任务主键
     * @param status 目标状态码（todo/doing/done）
     */
    void changeStatus(String id, String status);

    /**
     * 临期任务查询：截止日期在 today+days 之内且未完成的任务，
     * 按截止日期升序（逾期在前）
     *
     * @param days       临期窗口天数（0-90）
     * @param assigneeId 被指派人过滤，传 null 表示不限
     * @return 临期任务列表
     */
    List<TeamTask> dueSoon(int days, String assigneeId);

    /**
     * 任务看板：todo/doing/done 三组一次拉取，组内 sort_no asc, create_time desc
     *
     * @return 看板分组
     */
    TeamTaskBoard board();

    /**
     * 编辑（F-09 归属校验：限创建人或管理员）；状态强制回填库中原值，
     * 状态变更唯一入口为 changeStatus
     */
    void edit(TeamTask task);

    /**
     * 逻辑删除单条（F-09：限创建人或管理员；D-69a：deleted=1 不物理删）
     */
    void deleteOne(String id);

    /**
     * 逻辑删除多条（逐单归属校验，任一不过整体拒绝，同事务）
     */
    void deleteBatch(List<String> ids);

    /**
     * 任务详情聚合：任务主体 + 评论 + 时间线事件，一次拉取
     *
     * @param id 任务主键
     * @return 详情聚合 VO
     */
    TeamTaskDetail detail(String id);

    /**
     * 生成租户内下一个任务序号（当前最大值 + 1）
     * 并发下依赖 uk_team_task_no 唯一索引兜底
     *
     * @return 新序号
     */
    int nextTaskNo();

    /**
     * 自助认领：指派人置为当前登录人（F-09 语义=认领不算越权；
     * 调用方 controller 挂 team:task:edit），写审计动作 CLAIM
     *
     * @param id 任务主键
     */
    void claim(String id);

    /**
     * 改派：仅创建人或管理员（TeamOwnershipGuard），写审计动作 REASSIGN
     *
     * @param id           任务主键
     * @param assigneeId   新被指派人(sys_user.id)
     * @param assigneeName 新被指派人姓名冗余
     */
    void reassign(String id, String assigneeId, String assigneeName);
}
