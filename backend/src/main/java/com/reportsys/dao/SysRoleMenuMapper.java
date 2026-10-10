package com.reportsys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportsys.model.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(Long roleId);
}
