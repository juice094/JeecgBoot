package org.jeecg.modules.mail.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description: 邮件密码编解码装配（修正 @ConditionalOnMissingBean 误用）
 * @Author: mail-dev
 * @Date: 2026-11-18
 *
 * 背景：@ConditionalOnMissingBean 只应在 @Configuration 的 @Bean 方法上使用。
 * 此前直接标在 @Component 的 AesMailPasswordCodec 上，条件评估早于组件扫描注册，
 * 导致 MailPasswordCodec 无 bean 可用、单体启动失败（NoSuchBeanDefinition）。
 *
 * 可插拔语义保留：任何模块提供 MailPasswordCodec Bean 即替换本默认实现。
 * 密钥未配置时构造 fail-fast（AesMailPasswordCodec 构造器内校验）。
 */
@Configuration
public class MailCodecConfig {

    @Bean
    @ConditionalOnMissingBean(MailPasswordCodec.class)
    public MailPasswordCodec mailPasswordCodec(
            @Value("${jeecg.mail.crypto.key:${MAIL_CRYPTO_KEY:}}") String base64Key) {
        return new AesMailPasswordCodec(base64Key);
    }
}
