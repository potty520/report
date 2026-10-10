package com.reportsys.service.sms;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创蓝（253）短信通道实现。
 *
 * 注意：该实现按创蓝「模板短信 JSON 接口」编写（POST {account,password,template,phone,params}），
 * 但**在没有真实账号凭据的环境下未做过联调**——因此默认不启用：
 * 只有配置 report.sms.provider=chunlan 且 account/password 齐备时才会被选中。
 */
@Component
public class ChunlanSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ChunlanSmsSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${report.sms.chunlan.url:https://smssh1.253.com/msg/v1/send/json}")
    private String url;

    @Value("${report.sms.chunlan.account:}")
    private String account;

    @Value("${report.sms.chunlan.password:}")
    private String password;

    @Override
    public String provider() {
        return "chunlan";
    }

    @Override
    public boolean ready() {
        return StrUtil.isNotBlank(account) && StrUtil.isNotBlank(password);
    }

    @Override
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        if (!ready()) {
            return SmsResult.fail("创蓝短信凭据未配置（REPORT_SMS_ACCOUNT / REPORT_SMS_PASSWORD）");
        }
        if (StrUtil.isBlank(phone)) {
            return SmsResult.fail("手机号为空");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("account", account);
            body.put("password", password);
            body.put("template", templateCode);
            body.put("phone", phone);
            body.put("params", params == null ? new LinkedHashMap<>() : params);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(MAPPER.writeValueAsString(body), headers);

            String resp = restTemplate().postForObject(url, entity, String.class);
            JsonNode node = MAPPER.readTree(resp == null ? "{}" : resp);
            String code = node.path("code").asText("");
            if ("0".equals(code) || "200".equals(code)) {
                return SmsResult.ok(node.path("msgId").asText(""));
            }
            return SmsResult.fail("创蓝返回 code=" + code + " msg=" + node.path("msg").asText(""));
        } catch (Exception e) {
            log.warn("创蓝短信发送异常: {}", e.getMessage());
            return SmsResult.fail("发送异常: " + e.getMessage());
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
