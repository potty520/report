package com.reportsys;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.reportsys.dao")
public class ReportApplication extends SpringBootServletInitializer {

    /**
     * 统一 JVM 默认时区为东八区。
     *
     * 缺陷修复（时间整体慢 8 小时）：容器/宿主机默认 UTC 时，{@code LocalDateTime.now()}
     * 写出来的提交时间比真实时间早 8 小时；再叠加 MySQL 的 {@code NOW()/CURDATE()} 也走 UTC，
     * 每天 00:00-08:00 的提交会被算到前一天，超期判定与「今日」统计全部偏移。
     *
     * 放在静态块里，保证即使不是从 main 启动（测试/外部引导）也能生效；
     * 同时配合 application.yml 里的 druid {@code connection-init-sqls} 把数据库会话时区
     * 也固定为 +08:00，做到 JVM / JDBC / MySQL 三层一致。
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }

    /**
     * 外部 Tomcat 部署入口（mvn -Pwar package 打出的 war 用）。
     * 以 jar 方式运行时不会走到这里，两种打包方式可共存。
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ReportApplication.class);
    }
}
