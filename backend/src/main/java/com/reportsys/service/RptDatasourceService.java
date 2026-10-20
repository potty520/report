package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.config.SecretCrypto;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.model.RptDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源管理。
 *
 * 安全加固点：
 * 1. 口令加密存储（AES/GCM），接口一律只回显掩码；
 * 2. 更新时若未重新输入口令（空或掩码）则保留原口令；
 * 3. 连接测试限制在白名单主机内，并强制连接/读取超时，避免被当成内网扫描器。
 */
@Service
public class RptDatasourceService {

    private static final Logger log = LoggerFactory.getLogger(RptDatasourceService.class);

    @Autowired
    private RptDatasourceMapper rptDatasourceMapper;
    @Autowired
    private SecretCrypto secretCrypto;
    @Autowired
    private com.reportsys.dao.RptTemplateMapper rptTemplateMapper;
    @Autowired
    private com.reportsys.dao.RptDataRangeConfigMapper rptDataRangeConfigMapper;

    /** 允许做连接测试的主机白名单，逗号分隔；留空表示不限制（仅建议测试环境） */
    @Value("${report.datasource.test.allowed-hosts:}")
    private String allowedHosts;

    @Value("${report.datasource.test.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${report.datasource.test.socket-timeout-ms:5000}")
    private int socketTimeoutMs;

    public PageResult<RptDatasource> listPage(int pageNum, int pageSize, String dsName, String dsType) {
        Page<RptDatasource> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptDatasource> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(dsName)) wrapper.like(RptDatasource::getDsName, dsName);
        if (StrUtil.isNotBlank(dsType)) wrapper.eq(RptDatasource::getDsType, dsType);
        wrapper.orderByDesc(RptDatasource::getCreateTime);
        PageResult<RptDatasource> result = PageResult.of(rptDatasourceMapper.selectPage(page, wrapper));
        result.getList().forEach(this::mask);
        return result;
    }

    public List<RptDatasource> listAll() {
        List<RptDatasource> list = rptDatasourceMapper.selectList(
                Wrappers.<RptDatasource>lambdaQuery().eq(RptDatasource::getStatus, 1));
        list.forEach(this::mask);
        return list;
    }

    @Transactional
    public void add(RptDatasource ds) {
        ds.setPassword(secretCrypto.encrypt(ds.getPassword()));
        ds.setCreateTime(LocalDateTime.now());
        rptDatasourceMapper.insert(ds);
    }

    @Transactional
    public void update(RptDatasource ds) {
        if (ds.getId() == null) throw new RuntimeException("数据源ID不能为空");
        RptDatasource exist = rptDatasourceMapper.selectById(ds.getId());
        if (exist == null) throw new RuntimeException("数据源不存在");
        // 未重新输入口令（空或掩码）时保留原口令；否则加密新口令
        String incoming = ds.getPassword();
        if (StrUtil.isBlank(incoming) || SecretCrypto.isMask(incoming)) {
            ds.setPassword(exist.getPassword());
        } else {
            ds.setPassword(secretCrypto.encrypt(incoming));
        }
        ds.setUpdateTime(LocalDateTime.now());
        rptDatasourceMapper.updateById(ds);
    }

    /**
     * 删除数据源。
     *
     * 一致性修复（审计：高）：原实现直接物理删除，不检查 rpt_template.datasource_id
     * 与 rpt_data_range_config.datasource_id 引用，删除后模板无法物化入库、数据范围配置无法预览。
     */
    @Transactional
    public void deleteById(Long id) {
        Long tplCount = rptTemplateMapper.selectCount(
                Wrappers.<com.reportsys.model.RptTemplate>lambdaQuery()
                        .eq(com.reportsys.model.RptTemplate::getDatasourceId, id));
        if (tplCount != null && tplCount > 0) {
            throw new RuntimeException("该数据源被 " + tplCount + " 个模板引用，无法删除");
        }
        Long rangeCount = rptDataRangeConfigMapper.selectCount(
                Wrappers.<com.reportsys.model.RptDataRangeConfig>lambdaQuery()
                        .eq(com.reportsys.model.RptDataRangeConfig::getDatasourceId, id));
        if (rangeCount != null && rangeCount > 0) {
            throw new RuntimeException("该数据源被 " + rangeCount + " 个数据范围配置引用，无法删除");
        }
        rptDatasourceMapper.deleteById(id);
    }

    public RptDatasource getById(Long id) {
        RptDatasource ds = rptDatasourceMapper.selectById(id);
        mask(ds);
        return ds;
    }

    /** 内部使用：取出口令已解密的实体（不对外返回） */
    public RptDatasource getForConnect(Long id) {
        RptDatasource ds = rptDatasourceMapper.selectById(id);
        if (ds != null) {
            ds.setPassword(secretCrypto.decrypt(ds.getPassword()));
        }
        return ds;
    }

    /**
     * 测试数据源连接（主机需在白名单内，且强制超时）
     */
    public boolean testConnection(RptDatasource ds) {
        assertHostAllowed(ds.getIp());

        String url;
        String driverClass;
        if ("ORACLE".equalsIgnoreCase(ds.getDsType())) {
            url = "jdbc:oracle:thin:@" + ds.getIp() + ":" + ds.getPort() + ":" + ds.getDbName()
                    + "?oracle.net.CONNECT_TIMEOUT=" + connectTimeoutMs;
            driverClass = "oracle.jdbc.driver.OracleDriver";
        } else {
            url = "jdbc:mysql://" + ds.getIp() + ":" + ds.getPort() + "/" + ds.getDbName()
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&connectTimeout=" + connectTimeoutMs + "&socketTimeout=" + socketTimeoutMs;
            driverClass = "com.mysql.cj.jdbc.Driver";
        }

        log.info("数据源连接测试: type={} host={} port={} db={} user={}",
                ds.getDsType(), ds.getIp(), ds.getPort(), ds.getDbName(), ds.getUsername());
        try {
            Class.forName(driverClass);
            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
                return conn.isValid(Math.max(1, socketTimeoutMs / 1000));
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("连接测试失败: " + e.getMessage());
        }
    }

    /** 主机白名单校验 */
    private void assertHostAllowed(String ip) {
        if (allowedHosts == null || allowedHosts.trim().isEmpty()) {
            log.warn("未配置 report.datasource.test.allowed-hosts，连接测试目标 {} 未被限制", ip);
            return;
        }
        for (String allowed : allowedHosts.split(",")) {
            if (allowed.trim().equalsIgnoreCase(ip)) return;
        }
        throw new AccessDeniedException("目标主机不在允许测试的白名单内: " + ip);
    }

    private void mask(RptDatasource ds) {
        if (ds != null && ds.getPassword() != null && !ds.getPassword().isEmpty()) {
            ds.setPassword(SecretCrypto.mask(ds.getPassword()));
        }
    }
}
