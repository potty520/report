package com.reportsys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportsys.model.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    @Select("SELECT m.* FROM sys_menu m INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id WHERE ur.user_id = #{userId} " +
            "AND m.status = 1 ORDER BY m.sort")
    List<SysMenu> selectMenusByUserId(Long userId);
}
