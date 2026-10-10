package com.reportsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportsys.common.PageResult;
import com.reportsys.dao.SysLoginLogMapper;
import com.reportsys.model.SysLoginLog;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SysLoginLogService {

    @Resource
    private SysLoginLogMapper loginLogMapper;

    public PageResult<SysLoginLog> listPage(int pageNum, int pageSize, String username, String ip, Integer status) {
        Page<SysLoginLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) wrapper.like(SysLoginLog::getUsername, username);
        if (ip != null && !ip.isEmpty()) wrapper.eq(SysLoginLog::getIp, ip);
        if (status != null) wrapper.eq(SysLoginLog::getStatus, status);
        wrapper.orderByDesc(SysLoginLog::getLoginTime);
        return PageResult.of(loginLogMapper.selectPage(page, wrapper));
    }

    public SysLoginLog getById(Long id) {
        return loginLogMapper.selectById(id);
    }
}
