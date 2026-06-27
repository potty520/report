package com.reportsys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reportsys.model.RptTemplateField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface RptTemplateFieldMapper extends BaseMapper<RptTemplateField> {
    @Delete("DELETE FROM rpt_template_field WHERE template_id = #{templateId}")
    int deleteByTemplateId(Long templateId);
}
