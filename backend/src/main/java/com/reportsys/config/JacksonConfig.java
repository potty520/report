package com.reportsys.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式统一配置。
 *
 * 缺陷修复（业务流转测试发现）：
 *   原 application.yml 里只配了 {@code spring.jackson.date-format: yyyy-MM-dd HH:mm:ss}，
 *   但该配置只对 {@code java.util.Date} 生效。项目实体大量使用 {@code java.time.LocalDateTime}，
 *   而 Spring Boot 默认的 JavaTimeModule 只认 ISO-8601（{@code 2026-12-31T23:59:59}），
 *   于是：
 *     - 前端若按 {@code yyyy-MM-dd HH:mm:ss} 提交，直接抛
 *       HttpMessageNotReadableException: Text ... could not be parsed at index 10
 *     - 接口返回的时间是 ISO 形式（带 T），与前端展示口径也不一致
 *
 * 这里显式注册 LocalDateTime/LocalDate/LocalTime 的序列化与反序列化器，
 * 同时保留对 ISO 格式的兼容（前端 dayjs 传 ISO 也能解析）。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> {
            SimpleModule module = new JavaTimeModule();

            // 序列化：统一输出 yyyy-MM-dd HH:mm:ss
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));

            // 反序列化：输出格式 + ISO 都接受
            module.addDeserializer(LocalDateTime.class,
                    new CompatibleLocalDateTimeDeserializer(dateTimeFormatter));
            module.addDeserializer(LocalDate.class,
                    new CompatibleLocalDateDeserializer(dateFormatter));
            module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

            builder.modules(module);
            // 禁止把日期写成时间戳
            builder.featuresToDisable(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /** LocalDateTime 反序列化：优先 yyyy-MM-dd HH:mm:ss，再退到 ISO */
    static class CompatibleLocalDateTimeDeserializer extends LocalDateTimeDeserializer {
        private final DateTimeFormatter custom;

        CompatibleLocalDateTimeDeserializer(DateTimeFormatter custom) {
            super(custom);
            this.custom = custom;
        }

        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                                         com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws java.io.IOException {
            String text = p.getText();
            if (text == null) return null;
            String s = text.trim();
            if (s.isEmpty()) return null;
            try {
                // 兼容 "yyyy-MM-dd HH:mm:ss"
                return LocalDateTime.parse(s, custom);
            } catch (Exception ignored) {
                // 继续尝试 ISO
            }
            try {
                // 兼容 "yyyy-MM-dd"（纯日期）→ 当天 00:00:00
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (Exception ignored) {
                // 继续尝试完整 ISO
            }
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /** LocalDate 反序列化：兼容 "yyyy-MM-dd" 与 "yyyy-MM-dd HH:mm:ss" 前 10 位 */
    static class CompatibleLocalDateDeserializer extends LocalDateDeserializer {
        CompatibleLocalDateDeserializer(DateTimeFormatter custom) {
            super(custom);
        }

        @Override
        public LocalDate deserialize(com.fasterxml.jackson.core.JsonParser p,
                                     com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws java.io.IOException {
            String s = p.getText();
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            if (s.length() >= 10) {
                try {
                    return LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            return super.deserialize(p, ctxt);
        }
    }
}
