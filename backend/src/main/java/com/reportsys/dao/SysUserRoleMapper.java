package com.reportsys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportsys.model.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
