package org.jeecg.modules.speech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.speech.entity.SpeechTask;
import org.jeecg.modules.speech.mapper.SpeechTaskMapper;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//语音任务异步处理器：faster-whisper 转写 + Ollama 生成纪要

@Slf4j
@Component
public class SpeechTaskProcessor {
    // 纪要抢救失败时的空结构
    private static final String EMPTY_SUMMARY =
            "{\"meetingTopic\":\"未生成\",\"keyPoints\":[],\"todoList\":[]}";

    @Resource
    SpeechTaskMapper speechTaskMapper;

    /**
     * 转写模型：由 spring-ai-starter-model-openai 按
     * spring.ai.openai.audio.transcription 配置自动装配（指向本地 faster-whisper）
     */
    @Autowired(required = false)
    TranscriptionModel transcriptionModel;

    /**
     * 对话模型：按 spring.ai.openai 配置自动装配（指向本地 Ollama）
     */
    @Autowired(required = false)
    ChatModel chatModel;

    @Value("${speech.upload-dir:D:/jeecgboot/upload/speech}")
    private String uploadDir;

    /**
     * 异步执行：转写 + 纪要
     */
    @Async("speechExecutor")// 丢入 speechExecutor 线程池异步执行
    public void process(String taskId) {
        SpeechTask task = speechTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("语音任务不存在，taskId={}", taskId);
            return;
        }
        try {
            updateStatus(taskId, "processing", null);

            if (transcriptionModel == null) {
                throw new IllegalStateException(
                        "转写模型未装配，请检查 spring.ai.openai.audio.transcription 配置");
            }

            File audio = resolveAudioFile(task.getAudioFilePath());
            if (audio == null || !audio.exists()) {
                throw new IllegalStateException("音频文件不存在：" + task.getAudioFilePath());
            }

            long start = System.currentTimeMillis();
            String transcribeText = transcriptionModel.transcribe(new FileSystemResource(audio));
            transcribeText = transcribeText == null ? "" : transcribeText.trim();
            log.info("转写完成 taskId={} 耗时={}ms 字数={}", taskId,
                    System.currentTimeMillis() - start, transcribeText.length());
            // 调大模型生成并清洗纪要
            String summaryJson = buildSummary(transcribeText);
            // 组装回写实体
            SpeechTask update = new SpeechTask();
            update.setId(taskId);
            update.setTaskStatus("success");
            update.setTranscribeText(transcribeText);
            update.setSummaryJson(summaryJson);// 写回纪要 JSON
            update.setFinishTime(new Date());
            speechTaskMapper.updateById(update);
            log.info("语音任务处理完成，taskId={}", taskId);
        } catch (Exception e) {
            log.error("语音任务处理失败，taskId={}", taskId, e);
            updateStatus(taskId, "fail", truncate(e.getMessage()));
        }
    }

    //生成纪要 buildSummary，调用本地 Ollama 生成结构化纪要，失败时回退为最小可用 JSON
    private String buildSummary(String transcribeText) {
        if (chatModel == null) {
            log.warn("对话模型未装配，跳过纪要生成");
            return EMPTY_SUMMARY;
        }
        if (!StringUtils.hasText(transcribeText)) {
            // 内容过短时不要硬生成纪要，模型会套用会议模板凭空编要点
            String compact = transcribeText.replaceAll("\\s+", "");
            if (compact.length() < 10) {
                log.warn("转写内容过短，跳过纪要生成，字数={}", compact.length());
                return "{\"meetingTopic\":\"内容过短\",\"keyPoints\":[],\"todoList\":[]}";
            }
        }

    //修改给ai的提示词和拼装方案，不一定是会议，也可能是其他类型的录音
        // qwen2.5:3b 可能会把 "todoList": 写成全角冒号 "todoList：" 或漏掉引号，
        // 导致 JSON 永远解析失败，需要对输出进行校验
        String prompt = "你是录音内容整理助手。根据下面的转写文本生成结构化摘要，不要预设它一定是会议。\n"
                + "只输出一个 JSON 对象，不要任何解释，不要 markdown 代码块，不要中文标点做分隔符。\n"
                + "必须严格使用英文半角冒号和双引号，格式为：\n"
                + "{\"meetingTopic\": \"内容主题\", \"keyPoints\": [\"要点1\", \"要点2\"], \"todoList\": [\"待办1\"]}\n"
                + "要求：meetingTopic 10 字以内；keyPoints 3 到 6 条；todoList 没有待办也必须输出空数组 []；"
                + "全部使用中文。\n"
                + "转写文本：\n" + transcribeText;


        try {
            String raw = ChatClient.create(chatModel).prompt().user(prompt).call().content();
            return cleanJson(raw);
        } catch (Exception e) {
            log.error("纪要生成失败，回退为空纪要", e);
            return EMPTY_SUMMARY;
        }
    }

   //从模型输出中截取并校验 JSON，模型偶尔会带 ```json 围栏、前后缀废话、全角冒号、漏引号
    //需要保证相对稳定的输出
    //三级兜底
    private String cleanJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return EMPTY_SUMMARY;
        }
        // 先截出第一个 { 到最后一个 } 之间的片段，剔掉围栏和前后废
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        // 第一档：原样解析
        try {
            new ObjectMapper().readTree(s);
            return s;
        } catch (Exception ignored) {
        }
        // 第二档：把 JSON 结构里的全角标点（：，｛｝［］）改成半角，再解析一次
        String normalized = normalizeJsonPunct(s);
        try {
            new ObjectMapper().readTree(normalized);
            return normalized;
        } catch (Exception e) {
            // 第三档：JSON 修不回来时按字段硬抽，别让整条任务白跑（只出个空纪要）
            String salvaged = salvageSummary(s);
            if (salvaged != null) {
                log.warn("纪要 JSON 非法，已按正则抽取抢救：{}", e.getMessage());
                return salvaged;
            }
            log.warn("纪要 JSON 仍非法，本任务按未生成处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 兜底：模型偶尔把键值分隔符写成全角、或漏掉引号，此时按字段名直接抽文本拼成合法 JSON。
     * 抽不到主题也没有要点时返回 null，交由上层按"未生成"处理。
     */
    private String salvageSummary(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String topic = null;
        Matcher mt = Pattern.compile("\"meetingTopic\"\\s*[:：]\\s*\"([^\"]{1,60})\"").matcher(raw);
        if (mt.find()) {
            topic = mt.group(1).trim();
        }
        List<String> keyPoints = extractQuotedArray(raw, "keyPoints");
        List<String> todoList = extractQuotedArray(raw, "todoList");
        if (topic == null && keyPoints.isEmpty()) {
            return null;
        }
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("meetingTopic", topic == null ? "未提取到主题" : topic);
        obj.put("keyPoints", keyPoints);
        obj.put("todoList", todoList);
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /** 定位字段名后的第一个 [ ... ] 区间，把里面每个双引号字符串抽出来 */
    private List<String> extractQuotedArray(String raw, String field) {
        List<String> out = new ArrayList<>();
        int idx = raw.indexOf(field);
        if (idx < 0) {
            return out;
        }
        int lb = raw.indexOf('[', idx);
        if (lb < 0) {
            return out;
        }
        int rb = raw.indexOf(']', lb);
        String body = rb > lb ? raw.substring(lb + 1, rb) : raw.substring(lb + 1);
        Matcher m = Pattern.compile("\"([^\"]{1,120})\"").matcher(body);
        while (m.find()) {
            String v = m.group(1).trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    /** 只替换 JSON 字符串字面量之外的全角标点，不破坏正文内容 */
    private String normalizeJsonPunct(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { sb.append(c); esc = false; continue; }
            if (c == '\\') { sb.append(c); esc = true; continue; }
            if (c == '"') { inStr = !inStr; sb.append(c); continue; }
            if (inStr) { sb.append(c); continue; }
            switch (c) {
                case '：': sb.append(':'); break;
                case '，': sb.append(','); break;
                case '｛': sb.append('{'); break;
                case '｝': sb.append('}'); break;
                case '［': sb.append('['); break;
                case '］': sb.append(']'); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }


    /**
     * 兼容两种存法：绝对路径，或相对 upload-dir 的路径
     */
    private File resolveAudioFile(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        File direct = new File(path);
        if (direct.exists()) {
            return direct;
        }
        return new File(uploadDir, path);
    }

    private void updateStatus(String taskId, String status, String failReason) {
        SpeechTask update = new SpeechTask();
        update.setId(taskId);
        update.setTaskStatus(status);
        if (failReason != null) {
            update.setFailReason(failReason);
            update.setFinishTime(new Date());
        }
        speechTaskMapper.updateById(update);
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "处理失败";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
