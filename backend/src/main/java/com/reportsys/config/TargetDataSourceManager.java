package com.reportsys.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.reportsys.model.RptDatasource;
import com.reportsys.service.DialectSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标（业务）数据源连接池管理。
 *
 * 替代原版的 DynamicDatasource + ThreadLocal dbKey：按"数据源 ID"缓存小型 Druid 池，
 * 口令从 SecretCrypto 解密后使用，池有上限并在关闭时统一释放。
 */
@Component
public class TargetDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(TargetDataSourceManager.class);

    private static final int MAX_POOLS = 20;

    @Autowired
    private SecretCrypto secretCrypto;

    @Value("${report.datasource.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${report.datasource.socket-timeout-ms:30000}")
    private int socketTimeoutMs;

    @Value("${report.datasource.pool.max-active:5}")
    private int maxActive;

    private final Map<Long, DruidDataSource> pools = new ConcurrentHashMap<>();

    /**
     * 取目标库连接（调用方负责 close）
     */
    public Connection getConnection(RptDatasource ds) throws SQLException {
        if (ds == null) throw new SQLException("数据源不存在");
        return pool(ds).getConnection();
    }

    private DruidDataSource pool(RptDatasource ds) throws SQLException {
        Long key = ds.getId();
        if (key == null) {
            key = (long) (ds.getIp() + ":" + ds.getPort() + "/" + ds.getDbName()).hashCode();
        }
        DruidDataSource existing = pools.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        synchronized (this) {
            existing = pools.get(key);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            if (pools.size() >= MAX_POOLS) {
                // 简单淘汰：关闭最早创建的一个
                Long victim = pools.keySet().iterator().next();
                DruidDataSource old = pools.remove(victim);
                if (old != null) try { old.close(); } catch (Exception ignored) { }
            }
            DruidDataSource created = build(ds);
            pools.put(key, created);
            return created;
        }
    }

    private DruidDataSource build(RptDatasource ds) throws SQLException {
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        String password;
        try {
            password = secretCrypto.decrypt(ds.getPassword());
        } catch (Exception e) {
            throw new SQLException("数据源口令解密失败: " + e.getMessage());
        }
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setName("target-" + ds.getId());
        dataSource.setUrl(DialectSupport.jdbcUrl(dialect, ds, connectTimeoutMs, socketTimeoutMs));
        dataSource.setUsername(ds.getUsername());
        dataSource.setPassword(password);
        dataSource.setDriverClassName(DialectSupport.driverClass(dialect));
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(maxActive);
        dataSource.setMaxWait(connectTimeoutMs);
        dataSource.setValidationQuery(dialect == DialectSupport.Dialect.ORACLE ? "SELECT 1 FROM DUAL" : "SELECT 1");
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(true);
        dataSource.setKeepAlive(false);
        dataSource.setBreakAfterAcquireFailure(false);
        dataSource.setFailFast(false);
        try {
            dataSource.init();
        } catch (SQLException e) {
            log.warn("目标数据源初始化失败 dsId={} type={} host={}: {}", ds.getId(), ds.getDsType(), ds.getIp(), e.getMessage());
            throw e;
        }
        log.info("已创建目标数据源连接池 dsId={} type={} {}:{}/{}", ds.getId(), ds.getDsType(), ds.getIp(), ds.getPort(), ds.getDbName());
        return dataSource;
    }

    /** 数据源信息变更后清理对应池 */
    public void evict(Long datasourceId) {
        DruidDataSource ds = pools.remove(datasourceId);
        if (ds != null) {
            try { ds.close(); } catch (Exception ignored) { }
        }
    }

    public void evictAll() {
        pools.forEach((k, v) -> { try { v.close(); } catch (Exception ignored) { } });
        pools.clear();
    }

    @PreDestroy
    public void shutdown() {
        evictAll();
    }
}
