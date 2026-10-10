package com.reportsys.service.quality;

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
 * 外部质检报告服务（HTTP）通道实现。
 *
 * 协议约定（可按实际服务调整）：
 *   POST {url}/submit  {taskDataId, taskId, templateId, templateCode, dataJson}  → {status, reportId, reportUrl, message}
 *   POST {url}/query   {taskDataId, reportId}                                    → {status, reportId, reportUrl, message}
 *
 * 注意：与创蓝短信通道同理，**没有真实外部质检服务时未做联调**，因此默认不启用
 * （只有 report.quality.provider=http 且配置了 url 才会被选中）。
 */
@Component
public class HttpQualityReportClient implements QualityReportClient {

    private static final Logger log = LoggerFactory.getLogger(HttpQualityReportClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${report.quality.url:}")
    private String url;

    @Value("${report.quality.token:}")
    private String token;

    @Value("${report.quality.submit-path:/submit}")
    private String submitPath;

    @Value("${report.quality.query-path:/query}")
    private String queryPath;

    @Override
    public String provider() {
        return "http";
    }

    @Override
    public boolean ready() {
        return StrUtil.isNotBlank(url);
    }

    @Override
    public QualityResult submit(Long taskDataId, Long taskId, Long templateId, String templateCode, String dataJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskDataId", taskDataId);
        body.put("taskId", taskId);
        body.put("templateId", templateId);
        body.put("templateCode", templateCode);
        body.put("dataJson", dataJson);
        return call(submitPath, body);
    }

    @Override
    public QualityResult query(Long taskDataId, String reportId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskDataId", taskDataId);
        body.put("reportId", reportId);
        return call(queryPath, body);
    }

    private QualityResult call(String path, Map<String, Object> body) {
        if (!ready()) return QualityResult.fail("质检服务地址未配置（REPORT_QUALITY_URL）");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StrUtil.isNotBlank(token)) headers.add("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(MAPPER.writeValueAsString(body), headers);

            String full = url.endsWith("/") ? url.substring(0, url.length() - 1) + path : url + path;
            String resp = restTemplate().postForObject(full, entity, String.class);
            JsonNode node = MAPPER.readTree(resp == null ? "{}" : resp);
            String status = node.path("status").asText(QualityReportClient.STATUS_PENDING).toUpperCase();
            return QualityResult.of(status, node.path("reportId").asText(null),
                    node.path("reportUrl").asText(null), node.path("message").asText(""));
        } catch (Exception e) {
            log.warn("调用外部质检服务失败: {}", e.getMessage());
            return QualityResult.fail("调用外部质检服务失败: " + e.getMessage());
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
