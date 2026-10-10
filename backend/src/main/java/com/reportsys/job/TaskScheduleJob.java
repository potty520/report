package com.reportsys.job;

import com.reportsys.service.RptTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 周期任务自动生成（替代原版 job/TaskJob + CronUtil）。
 *
 * 默认每天 00:05 执行：为每个"已发布的周期主任务"按当前属期生成各部门子任务
 * （子任务名 = 主任务名-部门编码-序号，自动算填报/审核时限，并复制填报分派）。
 *
 * cron 可通过 report.schedule.periodic-task-cron 覆盖；置空字符串可关闭。
 */
@Component
public class TaskScheduleJob {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduleJob.class);

    @Autowired
    private RptTaskService rptTaskService;

    @Autowired
    private com.reportsys.service.TodoService todoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${report.schedule.periodic-task-cron:0 5 0 * * ?}")
    private String cron;

    @Scheduled(cron = "${report.schedule.periodic-task-cron:0 5 0 * * ?}")
    public void generatePeriodicTasks() {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = rptTaskService.generatePeriodicTasks(null);
            int created = ((Number) result.getOrDefault("createdCount", 0)).intValue();
            log.info("周期任务自动生成完成: created={} detail={}", created, result.get("detail"));
            record("PERIODIC_TASK", String.valueOf(result.get("batch")), created,
                    String.valueOf(result.get("detail")), 1, System.currentTimeMillis() - start);
            // P1-F：顺带扫描超期未提交任务并发出站内通知（替代原版短信催办）
            try {
                Map<String, Object> scan = todoService.scanOverdue(null);
                log.info("超期扫描: 任务 {} 个, 通知 {} 人", scan.get("overdueTasks"), scan.get("notified"));
            } catch (Exception e) {
                log.warn("超期扫描失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("周期任务自动生成失败", e);
            record("PERIODIC_TASK", "", 0, e.getMessage(), 0, System.currentTimeMillis() - start);
        }
    }

    private void record(String jobName, String batch, int created, String detail, int status, long cost) {
        try {
            jdbcTemplate.update("INSERT INTO rpt_schedule_log (job_name, batch, created_count, detail, status, cost_ms, create_time) "
                            + "VALUES (?,?,?,?,?,?,NOW())",
                    jobName, batch, created, detail == null ? "" : detail.substring(0, Math.min(detail.length(), 1900)),
                    status, cost);
        } catch (Exception ignored) {
        }
    }
}
