package org.jeecg.modules.speech.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.speech.entity.SpeechTask;

//语音转写任务 Service


public interface ISpeechTaskService extends IService<SpeechTask> {

    //创建任务并提交异步处理
    SpeechTask createTask(String audioFilePath, String audioFileId);

    //查询任务详情（含数据权限校验）
    SpeechTask getDetail(String taskId);

    //分页查询（不是管理员只能查询到本人的数据）
    IPage<SpeechTask> pageList(String status, String keyword, String startTime, String endTime,
                               Integer pageNo, Integer pageSize);

    //删除任务（软删除）
    void deleteTask(String taskId);
}
