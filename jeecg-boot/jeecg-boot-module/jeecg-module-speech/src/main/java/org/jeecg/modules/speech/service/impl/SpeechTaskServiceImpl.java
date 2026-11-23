package org.jeecg.modules.speech.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.speech.entity.SpeechTask;
import org.jeecg.modules.speech.mapper.SpeechTaskMapper;
import org.jeecg.modules.speech.service.ISpeechTaskService;
import org.jeecg.modules.speech.service.SpeechTaskProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.UUID;

// 语音转写任务 Service 实现
@Slf4j
@Service
public class SpeechTaskServiceImpl extends ServiceImpl<SpeechTaskMapper, SpeechTask> implements ISpeechTaskService {

    private static final String STATUS_PENDING = "pending";
    private static final long IDEMPOTENT_WINDOW_MS = 60_000L;

    @Resource
    private SpeechTaskProcessor speechTaskProcessor;

    @Override
    @Transactional(rollbackFor = Exception.class)// 整方法一个事务，任何异常回滚
    public SpeechTask createTask(String audioFilePath, String audioFileId) {
        if (!StringUtils.hasText(audioFilePath)) {
            throw new JeecgBootException("音频路径不能为空");
        }
        LoginUser user = currentUser();

        Date windowStart = new Date(System.currentTimeMillis() - IDEMPOTENT_WINDOW_MS);
        SpeechTask exist = lambdaQuery()
                .eq(SpeechTask::getAudioFilePath, audioFilePath)
                .eq(SpeechTask::getCreateBy, user.getUsername())
                .ge(SpeechTask::getCreateTime, windowStart)
                .last("limit 1")
                .one();
        if (exist != null) {
            return exist;
        }

        //任务入库
        SpeechTask task = new SpeechTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setAudioFilePath(audioFilePath);
        task.setAudioFileId(audioFileId);
        task.setTaskStatus(STATUS_PENDING);
        task.setTenantId(0);
        task.setDelFlag(0);
        task.setCreateBy(user.getUsername());
        task.setCreateTime(new Date());
        save(task);

        // 异步任务必须等事务提交后再触发：@Async 线程跑在另一个事务里，
        // 若在主事务提交前就 selectById，查不到记录会直接 return，
        // 任务会永远停留在 pending。
        final String taskId = task.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {// 判断是否处在事务上下文中
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {// 主事务成功提交后才执行
                    speechTaskProcessor.process(taskId);
                }
            });
        } else {
            speechTaskProcessor.process(taskId);
        }
        return task;
    }

    @Override
    public SpeechTask getDetail(String taskId) {
        SpeechTask task = getById(taskId);
        if (task == null) {
            throw new JeecgBootException("任务不存在");
        }
        assertOwner(task);
        return task;
    }

    @Override
    public IPage<SpeechTask> pageList(String status, String keyword, String startTime, String endTime,
                                      Integer pageNo, Integer pageSize) {
        LoginUser user = currentUser();
        LambdaQueryWrapper<SpeechTask> wrapper = Wrappers.lambdaQuery();
        if (!isAdmin(user)) {
            wrapper.eq(SpeechTask::getCreateBy, user.getUsername());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SpeechTask::getTaskStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SpeechTask::getTranscribeText, keyword);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(SpeechTask::getCreateTime, startTime);
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(SpeechTask::getCreateTime, endTime);
        }
        wrapper.orderByDesc(SpeechTask::getCreateTime);

        Page<SpeechTask> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(String taskId) {
        SpeechTask task = getById(taskId);
        if (task == null) {
            throw new JeecgBootException("任务不存在");
        }
        assertOwner(task);
        removeById(taskId);
    }
    //越权校验 assertOwner
    private void assertOwner(SpeechTask task) {
        LoginUser user = currentUser();
        if (isAdmin(user)) {
            return;
        }
        if (!user.getUsername().equals(task.getCreateBy())) {
            throw new JeecgBootException("无权访问该任务");
        }
    }

    private boolean isAdmin(LoginUser user) {
        return "admin".equalsIgnoreCase(user.getUsername());
    }
    //当前登录人判断
    private LoginUser currentUser() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof LoginUser)) {
            throw new JeecgBootException("未登录或登录已过期");
        }
        return (LoginUser) principal;
    }
}
