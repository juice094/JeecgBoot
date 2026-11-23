package org.jeecg.modules.speech.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.speech.entity.SpeechTask;
import org.jeecg.modules.speech.service.ISpeechTaskService;
import org.jeecg.modules.speech.vo.SpeechTaskCreateDTO;
import org.jeecg.modules.speech.vo.SpeechTaskDetailVO;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 录音转写与会议纪要

@Slf4j // 自动注入日志对象 log
@RestController // 返回值直接序列化为 JSON写回响应
@RequestMapping("/speech")
@Tag( name = "语音转写与会议纪要")
public class SpeechTaskController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final List<String> ALLOWED_FORMATS = Arrays.asList("wav", "mp3", "webm");

    @Value("${speech.upload-dir:D:/jeecgboot/upload/speech}")
    String uploadDir;

    @Resource
    ISpeechTaskService speechTaskService;

    //转写模型依赖
    @Autowired(required = false)
    TranscriptionModel transcriptionModel;

    /**
     * 仅用于把库里存的 JSON 字符串转成对象，不依赖容器里的 ObjectMapper Bean
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    //ping检查
    @Operation(summary = "测试健康")
    @GetMapping("/task/ping")
    public Result<String> ping() {
        return Result.OK("speech module ready");
    }

    // 上传音频
    @Operation(summary = "上传音频")
    @PostMapping("/audio/upload")
    @RequiresPermissions("speech:task:add")// Shiro 接口权限校验
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择音频文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 10MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            return Result.error("文件名不合法");
        }
        String format = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FORMATS.contains(format)) {
            return Result.error("仅支持 wav、mp3、webm 格式");
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
        Path dir = Paths.get(uploadDir, dateDir);
        Path target = dir.resolve(fileId + "." + format);
        try {
            Files.createDirectories(dir);// 目录不存在则递归创建
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("音频保存失败", e);
            return Result.error("音频保存失败");
        }

        Map<String, Object> result = new HashMap<>(8);
        result.put("fileId", fileId);
        result.put("audioFilePath", target.toString().replace("\\", "/"));
        result.put("fileSize", file.getSize());
        result.put("fileFormat", format);
        return Result.OK(result);
    }

    //同步转写（不落库）：实时转写分段调用，做实时预览
    @Operation(summary = "同步转写（不落库）")
    @PostMapping("/audio/transcribe")
    @RequiresPermissions("speech:task:add")
    public Result<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择音频文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 10MB");
        }
        if (transcriptionModel == null) {
            return Result.error("转写模型未装配，请检查 spring.ai.openai.audio.transcription 配置");
        }

        String format = resolveFormat(file.getOriginalFilename());
        Path tmp = Paths.get(uploadDir, "tmp")
                .resolve(UUID.randomUUID().toString().replace("-", "") + "." + format);
        try {
            Files.createDirectories(tmp.getParent());
            file.transferTo(tmp.toFile());
            long start = System.currentTimeMillis(); // 记录开始时间用于算转写耗时
            String text = transcriptionModel.transcribe(new FileSystemResource(tmp.toFile()));//调用转写
            Map<String, Object> result = new HashMap<>(4);
            result.put("text", text == null ? "" : text.trim());
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.OK(result);
        } catch (Exception e) {
            log.error("同步转写失败", e);
            return Result.error("转写失败：" + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // 临时文件清理失败不影响返回
            }
        }
    }

    /**
     * 原音频下载 / 试听（新增）：按任务记录的 audioFilePath 返回文件流。
     * 只允许读取 speech.upload-dir 目录内的文件，防止路径穿越。
     */
    @Operation(summary = "读取原音频")
    @GetMapping("/audio/raw")
    public void raw(@RequestParam("path") String path,
                    @RequestParam(value = "inline", required = false, defaultValue = "false") boolean inline,
                    HttpServletResponse response) throws IOException {
        File file = resolveSafeFile(path);
        if (file == null || !file.exists() || !file.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "音频文件不存在");
            return;
        }
        String name = file.getName();
        String ext = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String contentType = switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";//没有点的情况，返回空串，返回二进制流类型触发下载而不是尝试播放
        };
        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                (inline ? "inline" : "attachment") + "; filename=\"" + name + "\"");
        response.setContentLengthLong(file.length());
        try (InputStream in = new FileInputStream(file);//读文件
             OutputStream out = response.getOutputStream()) {//写响应
            byte[] buf = new byte[8192];//建一个 8KB 的缓冲区
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }
    //扩展名规范化
    private String resolveFormat(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "webm";
        }
        String format = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        return ALLOWED_FORMATS.contains(format) ? format : "webm";
    }

    /**
     * 只允许读取 upload-dir 内的文件
     */
    private File resolveSafeFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!target.startsWith(root)) {
                log.warn("拒绝读取上传目录之外的文件：{}", path);
                return null;
            }
            return target.toFile();
        } catch (Exception e) {
            log.warn("音频路径解析失败：{}", path, e);
            return null;
        }
    }

    /**
     * 创建转写任务
     */
    @Operation(summary = "创建转写任务")
    @PostMapping("/task/create")
    @RequiresPermissions("speech:task:add")
    public Result<Map<String, Object>> create(@RequestBody SpeechTaskCreateDTO dto) {
        SpeechTask task = speechTaskService.createTask(dto.getAudioFilePath(), dto.getFileId());// 委托 Service 落库并触发异步
        Map<String, Object> result = new HashMap<>(4);
        result.put("taskId", task.getId());
        result.put("taskStatus", task.getTaskStatus());
        return Result.OK(result);
    }

    //查询分页列表
    @Operation(summary = "查询分页列表")
    @GetMapping("/task/list")
    @RequiresPermissions("speech:task:list")
    public Result<IPage<SpeechTask>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(defaultValue = "1") Integer pageNo,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.OK(speechTaskService.pageList(status, keyword, startTime, endTime, pageNo, pageSize));
    }

    /**
     * 查询任务详情
     */
    @Operation(summary = "查询任务详情")
    @GetMapping("/task/{taskId}")
    @RequiresPermissions("speech:task:detail")
    // 返回详情 VO，纪要 JSON 解析后给出
    public Result<SpeechTaskDetailVO> detail(@PathVariable("taskId") String taskId) {
        SpeechTask task = speechTaskService.getDetail(taskId);
        SpeechTaskDetailVO vo = new SpeechTaskDetailVO();
        vo.setTaskId(task.getId());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setAudioFilePath(task.getAudioFilePath());
        vo.setTranscribeText(task.getTranscribeText());
        vo.setFailReason(task.getFailReason());
        vo.setCreateTime(task.getCreateTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setSummaryJson(parseSummary(task.getSummaryJson()));
        return Result.OK(vo);
    }

    /**
     * 删除任务
     */
    @Operation(summary = "软删除任务")
    @DeleteMapping("/task/{taskId}")
    @RequiresPermissions("speech:task:delete")
    public Result<String> delete(@PathVariable("taskId") String taskId) {
        speechTaskService.deleteTask(taskId);
        return Result.OK("删除成功");
    }

    private Object parseSummary(String summaryJson) {
        if (summaryJson == null || summaryJson.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(summaryJson, Object.class);
        } catch (Exception e) {
            log.warn("纪要JSON解析失败", e);
            throw new JeecgBootException("纪要数据格式异常");
        }
    }
}
