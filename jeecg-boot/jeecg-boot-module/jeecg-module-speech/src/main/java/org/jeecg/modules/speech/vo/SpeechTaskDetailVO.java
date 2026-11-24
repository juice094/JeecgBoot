package org.jeecg.modules.speech.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

//任务详情返回对象

@Data
@Schema(description = "语音转写任务详情")
public class SpeechTaskDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务状态")
    private String taskStatus;

    @Schema(description = "音频文件路径")
    private String audioFilePath;

    @Schema(description = "转写全文")
    private String transcribeText;

    @Schema(description = "会议纪要对象")
    private Object summaryJson;

    @Schema(description = "失败原因")
    private String failReason;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date finishTime;
}
