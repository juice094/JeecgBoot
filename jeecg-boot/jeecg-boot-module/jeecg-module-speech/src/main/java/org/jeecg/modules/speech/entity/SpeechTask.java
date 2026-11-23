package org.jeecg.modules.speech.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

//语音转写任务
@Data
@TableName("speech_meeting_task")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "语音转写任务")
public class SpeechTask extends JeecgEntity implements Serializable {

    //Java 序列化版本号，用来保证序列化、反序列化的时候，类版本一致，防止报InvalidClassException无效类异常
    private static final long serialVersionUID = 1L;

    @Schema(description = "文件记录ID")
    private String audioFileId;

    @Schema(description = "音频文件路径")
    private String audioFilePath;

    @Schema(description = "任务状态：pending/processing/success/fail")
    private String taskStatus;

    @Schema(description = "转写全文")
    private String transcribeText;

    @Schema(description = "会议纪要JSON字符串")
    private String summaryJson;

    @Schema(description = "失败原因")
    private String failReason;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date finishTime;

    @Schema(description = "租户ID")
    private Integer tenantId;

    @TableLogic
    @Schema(description = "删除标记：0正常 1已删除")
    private Integer delFlag;
}
