package org.jeecg.modules.flow.config;

import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import java.util.Locale;

/**
 * MVC 入参转换：审批动作枚举大小写不敏感（前端传 approve/reject 与 APPROVE/REJECT 均可）。
 * Spring 默认 StringToEnum 大小写敏感（走 Enum.valueOf），不加此转换器会 500 参数转换失败。
 */
@Configuration
public class FlowWebConverterConfig {

    @Bean
    public Converter<String, FlowApproveAction> flowApproveActionConverter() {
        return source -> FlowApproveAction.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
