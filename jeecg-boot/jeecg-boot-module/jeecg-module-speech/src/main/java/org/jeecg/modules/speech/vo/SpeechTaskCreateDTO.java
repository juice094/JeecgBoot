package org.jeecg.modules.speech.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

//创建任务入参
@Data
@Schema(description = "创建转写任务入参")
public class SpeechTaskCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上传接口返回的音频路径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String audioFilePath;

    @Schema(description = "上传接口返回的文件ID")
    private String fileId;
}

