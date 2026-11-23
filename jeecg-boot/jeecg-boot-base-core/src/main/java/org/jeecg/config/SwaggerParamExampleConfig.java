package org.jeecg.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Knife4j 调试可用性：为接口参数注入合理示例值，使 doc.html "调试"可直接跑通。
 * 仅处理无示例的参数（不覆盖代码里已有 @Parameter(example=...)）；
 * 生产文档（knife4j.production=true）不加载。
 *
 * @author jeecg-boot
 */
@Configuration
@ConditionalOnProperty(prefix = "knife4j", name = "production", havingValue = "false", matchIfMissing = true)
public class SwaggerParamExampleConfig {

    /** 通用参数名 → 示例值（path+method 未特配时按参数名兜底） */
    private static final Map<String, String> GENERIC_PARAM_EXAMPLES = new LinkedHashMap<>();
    /** path+method → 参数名 → 示例值（精确特配优先） */
    private static final Map<String, Map<String, String>> PATH_PARAM_EXAMPLES = new LinkedHashMap<>();
    /** path+method → 请求体 JSON 示例 */
    private static final Map<String, Map<String, Object>> PATH_BODY_EXAMPLES = new LinkedHashMap<>();

    static {
        GENERIC_PARAM_EXAMPLES.put("pageNo", "1");
        GENERIC_PARAM_EXAMPLES.put("pageSize", "10");
        GENERIC_PARAM_EXAMPLES.put("username", "admin");
        GENERIC_PARAM_EXAMPLES.put("realname", "周景潇");
        GENERIC_PARAM_EXAMPLES.put("id", "e9ca23d68d884d4ebb19d07889727dae");
        GENERIC_PARAM_EXAMPLES.put("ids", "e9ca23d68d884d4ebb19d07889727dae");
        GENERIC_PARAM_EXAMPLES.put("userId", "e9ca23d68d884d4ebb19d07889727dae");
        GENERIC_PARAM_EXAMPLES.put("roleId", "f6817f48af4fb3af11b9e8bf182f618b");
        GENERIC_PARAM_EXAMPLES.put("phone", "18611111111");
        GENERIC_PARAM_EXAMPLES.put("mobile", "18611111111");
        GENERIC_PARAM_EXAMPLES.put("smscode", "123456");
        GENERIC_PARAM_EXAMPLES.put("captcha", "1234");
        GENERIC_PARAM_EXAMPLES.put("checkKey", "a1b2c3d4e5f64788a1b2c3d4e5f64788");
        GENERIC_PARAM_EXAMPLES.put("jobClassName", "org.jeecg.modules.quartz.job.SampleJob");
        GENERIC_PARAM_EXAMPLES.put("dataTable", "sys_user");
        GENERIC_PARAM_EXAMPLES.put("dataId", "e9ca23d68d884d4ebb19d07889727dae");
        GENERIC_PARAM_EXAMPLES.put("createBy", "admin");
        GENERIC_PARAM_EXAMPLES.put("logType", "1");

        // GET /sys/online/list
        PATH_PARAM_EXAMPLES.put("GET /sys/online/list", mapOf(
                "username", "admin", "realname", "周景潇", "pageNo", "1", "pageSize", "10"));
        // GET /sys/log/list
        PATH_PARAM_EXAMPLES.put("GET /sys/log/list", mapOf(
                "pageNo", "1", "pageSize", "10", "logType", "1", "username", "admin"));
        // GET /sys/dataLog/list
        PATH_PARAM_EXAMPLES.put("GET /sys/dataLog/list", mapOf(
                "pageNo", "1", "pageSize", "10", "dataTable", "sys_user"));
        // GET /sys/quartzJob/list（jobClassName 用库中真实存在的示例任务）
        PATH_PARAM_EXAMPLES.put("GET /sys/quartzJob/list", mapOf(
                "pageNo", "1", "pageSize", "10", "jobClassName", "org.jeecg.modules.quartz.job.SampleJob"));
        // 定时任务操作（id 用 SampleJob 的真实主键，调试可直接跑通）
        PATH_PARAM_EXAMPLES.put("GET /sys/quartzJob/pause", mapOf("id", "a253cdfc811d69fa0efc70d052bc8128"));
        PATH_PARAM_EXAMPLES.put("GET /sys/quartzJob/resume", mapOf("id", "a253cdfc811d69fa0efc70d052bc8128"));
        PATH_PARAM_EXAMPLES.put("GET /sys/quartzJob/execute", mapOf("id", "a253cdfc811d69fa0efc70d052bc8128"));
        PATH_PARAM_EXAMPLES.put("DELETE /sys/quartzJob/delete", mapOf("id", "a253cdfc811d69fa0efc70d052bc8128"));
        // GET /sys/user/passwordChange（短信 mock 模式下 123456 可配合 /sys/sms 调试）
        PATH_PARAM_EXAMPLES.put("GET /sys/user/passwordChange", mapOf(
                "username", "admin", "password", "123456", "smscode", "123456", "phone", "18611111111"));
        // PUT /sys/user/resetPassword（用 zhangsan 演示，避免误重置 admin）
        PATH_PARAM_EXAMPLES.put("PUT /sys/user/resetPassword", mapOf("usernames", "zhangsan"));

        // POST /sys/login
        PATH_BODY_EXAMPLES.put("POST /sys/login", bodyOf(
                "username", "admin", "password", "123456"));
        // POST /sys/sms（smsmode=2 忘记密码模板；dev 下验证码打印在后端日志）
        PATH_BODY_EXAMPLES.put("POST /sys/sms", bodyOf(
                "mobile", "18611111111", "smsmode", "2"));
        // POST /sys/user/phoneVerification
        PATH_BODY_EXAMPLES.put("POST /sys/user/phoneVerification", bodyOf(
                "phone", "18611111111", "smscode", "123456"));
    }

    @Bean
    public OperationCustomizer paramExampleCustomizer() {
        return (operation, handlerMethod) -> {
            String key = keyOf(operation, handlerMethod);
            fillParamExamples(operation, key);
            fillBodyExample(operation, key);
            return operation;
        };
    }

    /** 为查询/路径参数注入示例值：精确特配优先，其次按参数名兜底；已有示例的参数不动 */
    private void fillParamExamples(Operation operation, String key) {
        Map<String, String> pathSpecific = PATH_PARAM_EXAMPLES.get(key);
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if (parameter.getExample() != null) {
                continue;
            }
            String example = null;
            if (pathSpecific != null) {
                example = pathSpecific.get(parameter.getName());
            }
            if (example == null) {
                example = GENERIC_PARAM_EXAMPLES.get(parameter.getName());
            }
            if (example != null) {
                parameter.setExample(example);
            }
        }
    }

    /** 为关键端点注入请求体 JSON 示例（已存在示例时不覆盖） */
    private void fillBodyExample(Operation operation, String key) {
        Map<String, Object> bodyExample = PATH_BODY_EXAMPLES.get(key);
        if (bodyExample == null || operation.getRequestBody() == null) {
            return;
        }
        Content content = operation.getRequestBody().getContent();
        if (content == null) {
            return;
        }
        content.forEach((mediaType, media) -> {
            if (media.getExamples() == null || media.getExamples().isEmpty()) {
                media.addExamples("调试示例", new Example().value(bodyExample));
            }
        });
    }

    private String keyOf(Operation operation, HandlerMethod handlerMethod) {
        return methodOf(handlerMethod) + " " + getFullPath(handlerMethod);
    }

    /** HTTP 方法取自方法级 RequestMapping；未标注时按 GET 兜底（示例注入用，不影响实际路由） */
    private String methodOf(HandlerMethod handlerMethod) {
        RequestMapping methodMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
        if (methodMapping != null && methodMapping.method().length > 0) {
            return methodMapping.method()[0].name();
        }
        return "GET";
    }

    private String getFullPath(HandlerMethod handlerMethod) {
        StringBuilder fullPath = new StringBuilder();
        RequestMapping classMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            fullPath.append(classMapping.value()[0]);
        }
        RequestMapping methodMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
        if (methodMapping != null && methodMapping.value().length > 0) {
            String methodPath = methodMapping.value()[0];
            if (!fullPath.toString().endsWith("/") && !methodPath.startsWith("/")) {
                fullPath.append("/");
            }
            fullPath.append(methodPath);
        }
        return fullPath.toString();
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> bodyOf(String... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
