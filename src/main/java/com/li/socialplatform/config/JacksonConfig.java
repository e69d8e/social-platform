package com.li.socialplatform.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化配置：所有 Long 字段以字符串形式输出。
 *
 * 原因：MyBatis-Plus 雪花 ID 为 19 位整数，超出了前端 JS Number 的
 * 安全整数范围（2^53 - 1 = 9007199254740991），直接以 JSON number 返回
 * 会导致前端解析时精度丢失（如 198000109764477004 被舍入为
 * 198000109764477000），从而引发帖子去重错杀、详情/点赞 404 等问题。
 *
 * 以字符串输出后前端无需任何解析处理，HTTP 参数回传 Long 时
 * Spring 也能自动完成字符串到 Long 的转换。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}