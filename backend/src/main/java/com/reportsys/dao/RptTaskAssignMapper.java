package com.reportsys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportsys.model.RptTaskAssign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RptTaskAssignMapper extends BaseMapper<RptTaskAssign> {
    @Select("SELECT a.*, d.dept_name FROM rpt_task_assign a LEFT JOIN sys_dept d ON a.dept_id = d.id WHERE a.task_id = #{taskId}")
    List<RptTaskAssign> selectByTaskId(Long taskId);
}
