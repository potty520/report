package com.reportsys.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 跨实例定时任务互斥锁（基于数据库，无需引入 ShedLock 等新依赖）。
 *
 * 背景（审计：严重）：{@code @EnableScheduling} 裸跑，多实例部署时每个实例每天都会
 * 各执行一遍周期任务生成，配合 check-then-insert 会造成子任务成倍冗余。
 * 虽然本次已加唯一约束 uk_periodic 兜底防重复插入，但仍会刷大量错误日志、
 * 且超期扫描/通知会被重复执行。
 *
 * 实现：{@code rpt_schedule_lock} 表以 job_name 为唯一键，
 *   - 获取：先清理超时锁（进程崩溃残留），再 INSERT 一行；插入成功即拿到锁
 *   - 释放：DELETE 该行
 * 这是"数据库唯一键抢占"的经典做法，具备跨实例互斥语义，且失败不影响业务。
 */
@Service
public class ScheduleLockService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleLockService.class);

    /** 锁的最长持有时间；超过视为进程崩溃残留，可被抢占 */
    private static final int STALE_MINUTES = 30;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 尝试获取锁。
     *
     * @param jobName  任务名（锁粒度）
     * @param instance 实例标识（主机名/PID，便于排查是谁持有）
     * @return true 表示获取成功、可以执行；false 表示已有其他实例在执行
     */
    public boolean tryLock(String jobName, String instance) {
        try {
            // 1) 清理超时残留锁
            jdbcTemplate.update(
                    "DELETE FROM rpt_schedule_lock WHERE job_name = ? AND locked_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)",
                    jobName, STALE_MINUTES);
            // 2) 抢占
            jdbcTemplate.update(
                    "INSERT INTO rpt_schedule_lock (job_name, instance, locked_at) VALUES (?, ?, NOW())",
                    jobName, instance);
            log.info("定时任务获取锁成功: job={} instance={}", jobName, instance);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            log.info("定时任务跳过（其他实例正在执行）: job={} instance={}", jobName, instance);
            return false;
        } catch (Exception e) {
            // 锁表不可用（如未迁移）时降级为"允许执行"，避免整体不可用；
            // 但仍由唯一约束 uk_periodic 兜底防重复数据。
            log.warn("定时任务加锁失败，降级为直接执行: job={} err={}", jobName, e.getMessage());
            return true;
        }
    }

    /** 释放锁（在 finally 中调用） */
    public void unlock(String jobName) {
        try {
            jdbcTemplate.update("DELETE FROM rpt_schedule_lock WHERE job_name = ?", jobName);
        } catch (Exception e) {
            log.warn("释放定时任务锁失败: job={} err={}", jobName, e.getMessage());
        }
    }

    /** 当前实例标识 */
    public String instanceId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + ":" + ProcessHandleCompat.pid();
    }

    /** 兼容 Java 8：无 ProcessHandle，退化为读环境变量/返回占位 */
    private static final class ProcessHandleCompat {
        static String pid() {
            String p = System.getProperty("pid");
            return p == null ? "-" : p;
        }
    }

    /** 便于排查：列出当前持有的锁 */
    public List<java.util.Map<String, Object>> list() {
        try {
            return jdbcTemplate.queryForList("SELECT job_name, instance, locked_at FROM rpt_schedule_lock");
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}
