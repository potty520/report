package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.model.RptDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RptDatasourceService {

    @Autowired
    private RptDatasourceMapper rptDatasourceMapper;

    public PageResult<RptDatasource> listPage(int pageNum, int pageSize, String dsName, String dsType) {
        Page<RptDatasource> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptDatasource> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(dsName)) wrapper.like(RptDatasource::getDsName, dsName);
        if (StrUtil.isNotBlank(dsType)) wrapper.eq(RptDatasource::getDsType, dsType);
        wrapper.orderByDesc(RptDatasource::getCreateTime);
        return PageResult.of(rptDatasourceMapper.selectPage(page, wrapper));
    }

    public List<RptDatasource> listAll() {
        return rptDatasourceMapper.selectList(
                Wrappers.<RptDatasource>lambdaQuery().eq(RptDatasource::getStatus, 1));
    }

    @Transactional
    public void add(RptDatasource ds) {
        ds.setCreateTime(LocalDateTime.now());
        rptDatasourceMapper.insert(ds);
    }

    @Transactional
    public void update(RptDatasource ds) {
        if (ds.getId() == null) throw new RuntimeException("数据源ID不能为空");
        ds.setUpdateTime(LocalDateTime.now());
        rptDatasourceMapper.updateById(ds);
    }

    @Transactional
    public void deleteById(Long id) {
        rptDatasourceMapper.deleteById(id);
    }

    public RptDatasource getById(Long id) {
        return rptDatasourceMapper.selectById(id);
    }

    /**
     * 测试数据源连接
     */
    public boolean testConnection(RptDatasource ds) {
        String url;
        String driverClass;
        if ("ORACLE".equalsIgnoreCase(ds.getDsType())) {
            url = "jdbc:oracle:thin:@" + ds.getIp() + ":" + ds.getPort() + ":" + ds.getDbName();
            driverClass = "oracle.jdbc.driver.OracleDriver";
        } else {
            url = "jdbc:mysql://" + ds.getIp() + ":" + ds.getPort() + "/" + ds.getDbName()
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
            driverClass = "com.mysql.cj.jdbc.Driver";
        }

        try {
            Class.forName(driverClass);
            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            throw new RuntimeException("连接测试失败: " + e.getMessage());
        }
    }
}
