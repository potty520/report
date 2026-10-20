package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.*;
import com.reportsys.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RptTaskDataService {

    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskAssignMapper rptTaskAssignMapper;
    @Autowired
    private AuthzService authz;
    @Autowired
    private RptTemplateMapper rptTemplateMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private MaterializeService materializeService;
    @Autowired
    private ExcelService excelService;
    @Autowired
    private ProcessLogService processLog;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private QualityReportService qualityReportService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 单批写入行数上限（原版为 10000，这里取 1000 更稳） */
    private static final int WRITE_BATCH_SIZE = 1000;

    /**
     * 填报数据(草稿)
     */
    @Transactional
    public void fillData(RptTaskData taskData) {
        if (taskData.getTaskId() == null) throw new RuntimeException("任务ID不能为空");
        if (taskData.getFillUserId() == null) throw new RuntimeException("填报人不能为空");
        // 安全加固：只能为自己、且被分派到该任务时填报
        Long currentUserId = authz.requireUserId();
        if (!currentUserId.equals(taskData.getFillUserId())) {
            throw new AccessDeniedException("只能提交本人的填报数据");
        }
        authz.assertCanFill(taskData.getTaskId(), taskData.getFillUserId());

        // 检查任务状态（注意：status 可能为 null，先判空避免自动拆箱 NPE）
        RptTask task = rptTaskMapper.selectById(taskData.getTaskId());
        if (task == null) throw new RuntimeException("任务不存在");
        if (task.getStatus() == null || task.getStatus() != 2) {
            throw new RuntimeException("任务非填报中状态，无法填报");
        }

        // 检查是否超过截止时间
        if (task.getFillDeadline() != null && LocalDateTime.now().isAfter(task.getFillDeadline())) {
            throw new RuntimeException("已超过填报截止时间");
        }

        // 查找已有填报数据
        RptTaskData existData = rptTaskDataMapper.selectOne(
                Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getTaskId, taskData.getTaskId())
                        .eq(RptTaskData::getFillUserId, taskData.getFillUserId()));

        if (existData != null) {
            // 终态守卫（审计：高）：已审核通过的数据不允许被"保存草稿"打回
            // 原实现无条件 setStatus(0)，会把已通过数据变回草稿，而 auditStatus 仍为 1，
            // 形成「草稿 + 已通过」矛盾状态，台账通过数虚高。
            if (existData.getAuditStatus() != null && existData.getAuditStatus() == 1) {
                throw new RuntimeException("该数据已审核通过，不能再修改；如需修改请由管理员发起补正");
            }
            if (existData.getDeptAuditStatus() != null && existData.getDeptAuditStatus() == 1
                    && existData.getTaskAuditStatus() != null && existData.getTaskAuditStatus() == 1) {
                throw new RuntimeException("该数据已完成双级审核，不能再修改");
            }
            // 状态守卫（并发修复）：已提交（status=1）的数据不允许被「保存草稿」无条件打回。
            // 否则并发下「提交」与「保存」互相踩踏，提交计数与实际提交数不一致，
            // 也让已提交数据可被静默改回草稿。
            if (existData.getStatus() != null && existData.getStatus() == 1) {
                throw new RuntimeException("该数据已提交，不能直接保存草稿；如需修改请等待审核驳回");
            }
            // 更新已有数据（条件更新：仅当仍为草稿时生效，防并发覆盖）
            LocalDateTime now = LocalDateTime.now();
            int claimed = rptTaskDataMapper.update(null,
                    Wrappers.<RptTaskData>lambdaUpdate()
                            .set(RptTaskData::getDataJson, taskData.getDataJson())
                            .set(RptTaskData::getFileUrl, taskData.getFileUrl())
                            .set(RptTaskData::getStatus, 0)
                            .eq(RptTaskData::getId, existData.getId())
                            .and(w -> w.isNull(RptTaskData::getStatus).or().eq(RptTaskData::getStatus, 0)));
            if (claimed == 0) {
                throw new RuntimeException("该数据状态已变更（可能刚被提交或审核），请刷新后重试");
            }
            taskData.setId(existData.getId());
        } else {
            // 新增填报数据
            taskData.setTemplateId(task.getTemplateId());
            taskData.setStatus(0); // 草稿
            taskData.setAuditStatus(0); // 待审
            taskData.setCreateTime(LocalDateTime.now());
            rptTaskDataMapper.insert(taskData);
        }
    }

    /**
     * 提交数据
     */
    @Transactional
    public void submitData(Long dataId) {
        RptTaskData taskData = rptTaskDataMapper.selectById(dataId);
        if (taskData == null) throw new RuntimeException("填报数据不存在");
        // 安全加固：只能提交本人、且被分派到该任务的数据
        Long currentUserId = authz.requireUserId();
        if (taskData.getFillUserId() != null && !currentUserId.equals(taskData.getFillUserId())) {
            throw new AccessDeniedException("只能提交本人的填报数据");
        }
        authz.assertCanFill(taskData.getTaskId(), taskData.getFillUserId());
        if (taskData.getStatus() != null && taskData.getStatus() == 1) throw new RuntimeException("数据已提交，请勿重复提交");
        if (StrUtil.isBlank(taskData.getDataJson())) throw new RuntimeException("填报数据不能为空");

        // 并发修复（审计 P0-2）：用「条件更新」原子抢占，替代 check-then-act。
        // 只在 status 仍为草稿(0) 时更新，影响行数为 0 说明已被并发请求提交过。
        LocalDateTime now = LocalDateTime.now();
        int claimed = rptTaskDataMapper.update(null,
                Wrappers.<RptTaskData>lambdaUpdate()
                        .set(RptTaskData::getStatus, 1)
                        .set(RptTaskData::getSubmitTime, now)
                        .set(RptTaskData::getAuditStatus, 0)
                        .eq(RptTaskData::getId, dataId)
                        .and(w -> w.isNull(RptTaskData::getStatus).or().eq(RptTaskData::getStatus, 0)));
        if (claimed == 0) {
            throw new RuntimeException("数据已提交或状态已变更，请刷新后重试");
        }
        // P0-C：记录流转轨迹
        processLog.log(taskData.getTaskId(), dataId, "SUBMIT", "提交填报", "SUBMIT",
                taskData.getFillUserId(), "提交填报数据");

        // 更新任务分派状态（条件更新，避免重复写）
        rptTaskAssignMapper.update(null,
                Wrappers.<RptTaskAssign>lambdaUpdate()
                        .set(RptTaskAssign::getStatus, 1)
                        .eq(RptTaskAssign::getTaskId, taskData.getTaskId())
                        .eq(RptTaskAssign::getUserId, taskData.getFillUserId())
                        .eq(RptTaskAssign::getAssignType, "FILL"));
    }

    /**
     * 获取填报列表
     */
    public PageResult<RptTaskData> getFillList(int pageNum, int pageSize, Long fillUserId, Integer status) {
        Page<RptTaskData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTaskData> wrapper = new LambdaQueryWrapper<>();
        if (fillUserId != null) wrapper.eq(RptTaskData::getFillUserId, fillUserId);
        if (status != null) wrapper.eq(RptTaskData::getStatus, status);
        wrapper.orderByDesc(RptTaskData::getCreateTime);
        PageResult<RptTaskData> result = PageResult.of(rptTaskDataMapper.selectPage(page, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 获取审核列表
     */
    public PageResult<RptTaskData> getAuditList(int pageNum, int pageSize, Long auditUserId, Integer auditStatus,
                                                 Long taskId) {
        // 查询审核人员被分派的任务
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(auditUserId != null, RptTaskAssign::getUserId, auditUserId)
                        .eq(RptTaskAssign::getAssignType, "AUDIT"));

        if (assigns.isEmpty() && auditUserId != null) {
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0);
        }

        List<Long> taskIds = null;
        if (auditUserId != null) {
            taskIds = new java.util.ArrayList<>();
            java.util.Set<Long> tidSet = new java.util.HashSet<>();
            for (RptTaskAssign a : assigns) tidSet.add(a.getTaskId());
            taskIds.addAll(tidSet);
        }

        Page<RptTaskData> pageObj = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTaskData> wrapper = new LambdaQueryWrapper<>();
        if (taskIds != null && !taskIds.isEmpty()) wrapper.in(RptTaskData::getTaskId, taskIds);
        if (auditStatus != null) wrapper.eq(RptTaskData::getAuditStatus, auditStatus);
        if (taskId != null) wrapper.eq(RptTaskData::getTaskId, taskId);
        wrapper.eq(RptTaskData::getStatus, 1);
        wrapper.orderByDesc(RptTaskData::getSubmitTime);
        PageResult<RptTaskData> result = PageResult.of(rptTaskDataMapper.selectPage(pageObj, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 审核操作
     */
    @Transactional
    public void audit(Long dataId, Integer auditStatus, String auditComment, Long auditUserId) {
        RptTaskData taskData = rptTaskDataMapper.selectById(dataId);
        if (taskData == null) throw new RuntimeException("填报数据不存在");
        if (taskData.getAuditStatus() != null && taskData.getAuditStatus() != 0) {
            throw new RuntimeException("数据已审核，请勿重复操作");
        }
        if (taskData.getStatus() == null || taskData.getStatus() != 1) {
            throw new RuntimeException("数据未提交，无法审核");
        }
        // 安全加固：禁止自审；有审核分派时必须是被分派人
        authz.assertCanAudit(taskData.getTaskId(), taskData.getFillUserId(), auditUserId);

        // 并发修复（审计 P0-2）：条件更新原子抢占「待审 → 已审」，
        // 只在 status=1 且 audit_status=0 时生效，避免两人同时审核互相覆盖。
        LocalDateTime now = LocalDateTime.now();
        int claimed = rptTaskDataMapper.update(null,
                Wrappers.<RptTaskData>lambdaUpdate()
                        .set(RptTaskData::getAuditStatus, auditStatus)
                        .set(RptTaskData::getAuditComment, auditComment)
                        .set(RptTaskData::getAuditUserId, auditUserId)
                        .set(RptTaskData::getAuditTime, now)
                        .eq(RptTaskData::getId, dataId)
                        .eq(RptTaskData::getStatus, 1)
                        .and(w -> w.isNull(RptTaskData::getAuditStatus).or().eq(RptTaskData::getAuditStatus, 0)));
        if (claimed == 0) {
            throw new RuntimeException("数据状态已变更（可能已被他人审核），请刷新后重试");
        }

        // 如果驳回，将提交状态重置为草稿（同样用条件更新）
        if (auditStatus != null && auditStatus == 2) {
            rptTaskDataMapper.update(null,
                    Wrappers.<RptTaskData>lambdaUpdate()
                            .set(RptTaskData::getStatus, 0)
                            .set(RptTaskData::getSubmitTime, null)
                            .eq(RptTaskData::getId, dataId)
                            .eq(RptTaskData::getStatus, 1));
            // 重置分派状态
            rptTaskAssignMapper.update(null,
                    Wrappers.<RptTaskAssign>lambdaUpdate()
                            .set(RptTaskAssign::getStatus, 0)
                            .eq(RptTaskAssign::getTaskId, taskData.getTaskId())
                            .eq(RptTaskAssign::getUserId, taskData.getFillUserId())
                            .eq(RptTaskAssign::getAssignType, "FILL"));
        }

        // 更新任务审核状态 (auditStatus为计算字段不存DB，此处做空保护)
        RptTask task = rptTaskMapper.selectById(taskData.getTaskId());
        if (task != null && task.getAuditStatus() != null && task.getAuditStatus() == 0) {
            task.setAuditStatus(1);
            task.setUpdateTime(LocalDateTime.now());
            rptTaskMapper.updateById(task);
        }
    }

    private void fillExtInfo(List<RptTaskData> list) {
        for (RptTaskData data : list) {
            if (data.getTaskId() != null) {
                RptTask t = rptTaskMapper.selectById(data.getTaskId());
                if (t != null) {
                    data.setTaskName(t.getTaskName());
                    data.setFillingCycle(t.getFillingCycle());
                    data.setBelongTime(t.getBelongTime());
                    if (t.getTemplateId() != null) {
                        RptTemplate tp = rptTemplateMapper.selectById(t.getTemplateId());
                        if (tp != null) data.setTemplateName(tp.getTemplateName());
                    }
                }
            }
            if (data.getFillUserId() != null) {
                SysUser u = sysUserMapper.selectById(data.getFillUserId());
                if (u != null) {
                    data.setFillUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
                    if (u.getDeptId() != null) {
                        SysDept d = sysDeptMapper.selectById(u.getDeptId());
                        if (d != null) data.setDeptName(d.getDeptName());
                    }
                }
            }
            if (data.getAuditUserId() != null) {
                SysUser u = sysUserMapper.selectById(data.getAuditUserId());
                if (u != null) data.setAuditUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
        }
    }

    public RptTaskData getById(Long id) {
        return rptTaskDataMapper.selectById(id);
    }

    // ========================================================================
    // P0-A/P0-B：模板物化 + 填报数据写回业务库 + Excel 导入/导出
    // ========================================================================

    /**
     * 把已提交的填报数据（dataJson）写回模板物化的目标业务库
     */
    @Transactional
    public Map<String, Object> commitToTarget(Long taskId, Long dataId, Long operatorId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        if (task.getTemplateId() == null) throw new RuntimeException("任务未关联模板");
        RptTemplate template = rptTemplateMapper.selectById(task.getTemplateId());
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = materializeService.fields(template.getId());
        if (fields.isEmpty()) throw new RuntimeException("模板没有字段，无法入库");

        if (template.getMaterialized() == null || template.getMaterialized() != 1) {
            materializeService.ensureTable(template, fields, false);
            template.setMaterialized(1);
            template.setMaterializedTime(LocalDateTime.now());
            rptTemplateMapper.updateById(template);
        }

        List<RptTaskData> dataList;
        if (dataId != null) {
            RptTaskData one = rptTaskDataMapper.selectById(dataId);
            if (one == null) throw new RuntimeException("填报数据不存在");
            // 安全修复（审计 H3）：原实现传入 dataId 时完全不校验归属，
            // 可把他人的数据写进目标库并回写入库状态。
            if (!authz.hasAnyRole(AuthzService.ADMIN)) {
                Long me = authz.requireUserId();
                if (one.getFillUserId() == null || !one.getFillUserId().equals(me)) {
                    throw new AccessDeniedException("无权入库他人的填报数据");
                }
            }
            // 且该数据必须属于当前任务，避免跨任务写入
            if (one.getTaskId() == null || !one.getTaskId().equals(taskId)) {
                throw new RuntimeException("该填报数据不属于当前任务");
            }
            dataList = java.util.Collections.singletonList(one);
        } else if (operatorId != null && !authz.hasAnyRole(AuthzService.ADMIN)) {
            // 普通用户：只入库自己的填报数据（含草稿）
            dataList = rptTaskDataMapper.selectList(Wrappers.<RptTaskData>lambdaQuery()
                    .eq(RptTaskData::getTaskId, taskId)
                    .eq(RptTaskData::getFillUserId, operatorId));
        } else {
            // 管理员：入库该任务下所有已提交数据
            dataList = rptTaskDataMapper.selectList(Wrappers.<RptTaskData>lambdaQuery()
                    .eq(RptTaskData::getTaskId, taskId).eq(RptTaskData::getStatus, 1));
            if (dataList.isEmpty() && operatorId != null) {
                RptTaskData mine = rptTaskDataMapper.selectOne(Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getTaskId, taskId)
                        .eq(RptTaskData::getFillUserId, operatorId));
                if (mine != null) dataList = java.util.Collections.singletonList(mine);
            }
        }
        if (dataList.isEmpty()) throw new RuntimeException("没有可入库的填报数据，请先保存或导入数据");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RptTaskData data : dataList) {
            rows.addAll(parseDataJson(data.getDataJson(), fields));
        }
        if (rows.isEmpty()) throw new RuntimeException("填报内容为空，无法入库");

        int affected;
        try {
            affected = writeInBatches(template, fields, rows);
        } catch (RuntimeException e) {
            for (RptTaskData data : dataList) {
                data.setInboundStatus(2);
                data.setErrorMsg(StrUtil.maxLength(e.getMessage(), 500));
                rptTaskDataMapper.updateById(data);
            }
            throw e;
        }

        LocalDateTime now = LocalDateTime.now();
        for (RptTaskData data : dataList) {
            data.setInboundStatus(1);
            data.setInboundTime(now);
            data.setInboundRows(rows.size());
            data.setErrorMsg("");
            data.setUpdateTime(now);
            rptTaskDataMapper.updateById(data);
        }
        String table = materializeService.resolveTable(template);
        processLog.log(taskId, dataId, "INBOUND", "数据入库", "PASS", operatorId,
                "写入 " + rows.size() + " 行到 " + table);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", table);
        result.put("dialect", materializeService.resolveDatasource(template).getDsType());
        result.put("rows", rows.size());
        result.put("affected", affected);
        result.put("dbName", materializeService.resolveDatasource(template).getDbName());
        return result;
    }

    /**
     * Excel 导入：解析 → 表头校验 → 按字段映射转换 → 分批写回目标库 → 记录为已提交数据
     *
     * 安全修复（审计：高）：原实现<b>完全绕开状态机与授权</b>——
     * 不校验用户是否被分派到该任务、不校验任务是否处于填报中、不校验截止时间，
     * 还能覆盖已审核通过的数据。任何 FILL_USER 可给任意任务导入并直接标记为"已提交"。
     * 现在补齐：分派归属、任务状态、填报截止、已审核数据保护。
     */
    @Transactional
    public Map<String, Object> importExcel(Long taskId, Long userId, byte[] content, int headerRowIndex) {
        if (content == null || content.length == 0) throw new RuntimeException("上传文件为空");
        if (taskId == null) throw new RuntimeException("任务ID不能为空");
        // ① 分派归属校验（内部会放行超管）
        authz.assertCanFill(taskId, userId);
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        // ② 任务必须处于填报中
        if (task.getStatus() == null || task.getStatus() != 2) {
            throw new RuntimeException("任务非填报中状态，无法导入");
        }
        // ③ 不得晚于填报截止时间
        if (task.getFillDeadline() != null && LocalDateTime.now().isAfter(task.getFillDeadline())) {
            throw new RuntimeException("已超过填报截止时间，无法导入");
        }
        // ④ 已审核通过的数据不允许被导入覆盖
        RptTaskData exist = rptTaskDataMapper.selectOne(
                Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getTaskId, taskId)
                        .eq(RptTaskData::getFillUserId, userId));
        if (exist != null && exist.getAuditStatus() != null && exist.getAuditStatus() == 1) {
            throw new RuntimeException("该数据已审核通过，不能用导入覆盖；如需修改请由管理员发起补正");
        }
        if (task.getTemplateId() == null) throw new RuntimeException("任务未关联模板");
        RptTemplate template = rptTemplateMapper.selectById(task.getTemplateId());
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = materializeService.fields(template.getId());
        if (fields.isEmpty()) throw new RuntimeException("模板没有字段，无法导入");

        List<String> headers = excelService.parseHeader(new ByteArrayInputStream(content), headerRowIndex);
        List<String> unmatched = new ArrayList<>();
        for (String header : headers) {
            if (matchField(fields, header) == null) unmatched.add(header);
        }
        if (!unmatched.isEmpty()) {
            throw new RuntimeException("以下列在模板字段中不存在，请下载最新模板: " + String.join("、", unmatched));
        }

        List<Map<String, String>> rawRows = excelService.parseRows(new ByteArrayInputStream(content), headerRowIndex);
        if (rawRows.isEmpty()) throw new RuntimeException("文件中没有数据行");

        List<Map<String, Object>> dbRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int rowNo = headerRowIndex + 2;
        for (Map<String, String> raw : rawRows) {
            try {
                dbRows.add(toDbRow(fields, raw));
            } catch (RuntimeException e) {
                errors.add("第 " + rowNo + " 行: " + e.getMessage());
                if (errors.size() >= 10) break;
            }
            rowNo++;
        }
        if (!errors.isEmpty()) {
            throw new RuntimeException("数据校验失败（前 " + errors.size() + " 条）: " + String.join("; ", errors));
        }

        if (template.getMaterialized() == null || template.getMaterialized() != 1) {
            materializeService.ensureTable(template, fields, false);
            template.setMaterialized(1);
            template.setMaterializedTime(LocalDateTime.now());
            rptTemplateMapper.updateById(template);
        }

        int affected;
        try {
            affected = writeInBatches(template, fields, dbRows);
        } catch (RuntimeException e) {
            recordInboundFailure(taskId, userId, dbRows, e.getMessage());
            throw e;
        }

        // 记录为一条已提交数据（dataJson 保存映射后的内容）
        RptTaskData data = rptTaskDataMapper.selectOne(Wrappers.<RptTaskData>lambdaQuery()
                .eq(RptTaskData::getTaskId, taskId)
                .eq(RptTaskData::getFillUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        if (data == null) {
            data = new RptTaskData();
            data.setTaskId(taskId);
            data.setTemplateId(template.getId());
            data.setFillUserId(userId);
            data.setCreateTime(now);
        }
        data.setDataJson(writeJson(dbRows, fields));
        data.setStatus(1);
        data.setSubmitTime(now);
        data.setAuditStatus(0);
        data.setInboundStatus(1);
        data.setInboundTime(now);
        data.setInboundRows(dbRows.size());
        data.setErrorMsg("");
        data.setUpdateTime(now);
        if (data.getId() == null) {
            rptTaskDataMapper.insert(data);
        } else {
            rptTaskDataMapper.updateById(data);
        }

        RptTaskAssign assign = rptTaskAssignMapper.selectOne(Wrappers.<RptTaskAssign>lambdaQuery()
                .eq(RptTaskAssign::getTaskId, taskId)
                .eq(RptTaskAssign::getUserId, userId)
                .eq(RptTaskAssign::getAssignType, "FILL"));
        if (assign != null) {
            assign.setStatus(1);
            rptTaskAssignMapper.updateById(assign);
        }

        processLog.log(taskId, data.getId(), "EXCEL_IMPORT", "Excel 导入入库", "PASS", userId,
                "导入 " + dbRows.size() + " 行，写入 " + affected + " 行到 " + materializeService.resolveTable(template));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", materializeService.resolveTable(template));
        result.put("dialect", materializeService.resolveDatasource(template).getDsType());
        result.put("rows", dbRows.size());
        result.put("affected", affected);
        result.put("dataId", data.getId());
        return result;
    }

    /**
     * 导出任务数据（从物化表读取），返回 {fileName, content}
     */
    public Map<String, Object> exportTaskData(Long taskId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        RptTemplate template = rptTemplateMapper.selectById(task.getTemplateId());
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = materializeService.fields(template.getId());
        if (fields.isEmpty()) throw new RuntimeException("模板没有字段");

        List<Map<String, Object>> rows = materializeService.readRows(template, fields, 0);
        List<String> headers = new ArrayList<>();
        for (RptTemplateField field : fields) headers.add(field.getFieldName());

        List<Map<String, Object>> exportRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (RptTemplateField field : fields) {
                String column = DialectSupport.columnName(field);
                Object value = column == null ? null : row.get(column);
                converted.put(field.getFieldName(), value);
            }
            exportRows.add(converted);
        }
        byte[] content = excelService.export(headers, exportRows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", task.getTaskName() + "-" + System.currentTimeMillis() + ".xlsx");
        result.put("content", content);
        result.put("rows", exportRows.size());
        result.put("tableName", materializeService.resolveTable(template));
        return result;
    }

    /** 查询目标表已入库数据（分页前的全量，供前端预览） */
    public List<Map<String, Object>> queryTargetRows(Long taskId, int limit) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        RptTemplate template = rptTemplateMapper.selectById(task.getTemplateId());
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = materializeService.fields(template.getId());
        List<Map<String, Object>> rows = materializeService.readRows(template, fields, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (RptTemplateField field : fields) {
                String column = DialectSupport.columnName(field);
                converted.put(field.getFieldName(), column == null ? null : row.get(column));
            }
            result.add(converted);
        }
        return result;
    }

    private int writeInBatches(RptTemplate template, List<RptTemplateField> fields, List<Map<String, Object>> rows) {
        int affected = 0;
        for (int i = 0; i < rows.size(); i += WRITE_BATCH_SIZE) {
            List<Map<String, Object>> batch = rows.subList(i, Math.min(i + WRITE_BATCH_SIZE, rows.size()));
            affected += materializeService.writeRows(template, fields, batch);
        }
        return affected;
    }

    private void recordInboundFailure(Long taskId, Long userId, List<Map<String, Object>> rows, String message) {
        try {
            RptTaskData data = rptTaskDataMapper.selectOne(Wrappers.<RptTaskData>lambdaQuery()
                    .eq(RptTaskData::getTaskId, taskId)
                    .eq(RptTaskData::getFillUserId, userId));
            if (data != null) {
                data.setInboundStatus(2);
                data.setErrorMsg(StrUtil.maxLength(message, 500));
                rptTaskDataMapper.updateById(data);
            }
        } catch (Exception ignored) {
        }
    }

    private List<Map<String, Object>> parseDataJson(String json, List<RptTemplateField> fields) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (StrUtil.isBlank(json)) return rows;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode child : node) rows.add(jsonNodeToRow(child, fields));
            } else if (node.isObject()) {
                rows.add(jsonNodeToRow(node, fields));
            }
        } catch (Exception e) {
            throw new RuntimeException("填报数据不是合法 JSON: " + e.getMessage());
        }
        return rows;
    }

    private Map<String, Object> jsonNodeToRow(JsonNode node, List<RptTemplateField> fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (RptTemplateField field : fields) {
            String column = DialectSupport.columnName(field);
            if (column == null) continue;
            JsonNode value = field.getFieldCode() == null ? null : node.get(field.getFieldCode());
            if (value == null || value.isNull()) {
                value = field.getFieldName() == null ? null : node.get(field.getFieldName());
            }
            String text = (value == null || value.isNull()) ? null : value.asText();
            row.put(column, MaterializeService.convertValue(field.getFieldType(), text));
        }
        return row;
    }

    private Map<String, Object> toDbRow(List<RptTemplateField> fields, Map<String, String> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (RptTemplateField field : fields) {
            String column = DialectSupport.columnName(field);
            if (column == null) continue;
            String value = null;
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                if (fieldMatches(field, entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
            if ((field.getIsRequired() != null && field.getIsRequired() == 1) && StrUtil.isBlank(value)) {
                throw new RuntimeException("必填列 [" + field.getFieldName() + "] 为空");
            }
            row.put(column, MaterializeService.convertValue(field.getFieldType(), value));
        }
        return row;
    }

    private RptTemplateField matchField(List<RptTemplateField> fields, String header) {
        if (header == null) return null;
        for (RptTemplateField field : fields) {
            if (fieldMatches(field, header)) return field;
        }
        return null;
    }

    /** 表头/JSON key 与字段是否匹配（字段名或字段编码，忽略大小写） */
    private boolean fieldMatches(RptTemplateField field, String header) {
        if (field == null || header == null) return false;
        String target = header.trim();
        return target.equalsIgnoreCase(StrUtil.trimToEmpty(field.getFieldName()))
                || target.equalsIgnoreCase(StrUtil.trimToEmpty(field.getFieldCode()));
    }

    private String writeJson(List<Map<String, Object>> dbRows, List<RptTemplateField> fields) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : dbRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (RptTemplateField field : fields) {
                String column = DialectSupport.columnName(field);
                if (column == null) continue;
                Object value = row.get(column);
                item.put(StrUtil.isBlank(field.getFieldCode()) ? field.getFieldName() : field.getFieldCode(),
                        value == null ? null : String.valueOf(value));
            }
            out.add(item);
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ========================================================================
    // P0-C：双通道审核（部门审核 → 任务方审核）与补正流程
    // ========================================================================

    /** 第一级：部门审核 */
    @Transactional
    public void deptAudit(Long dataId, boolean pass, String comment, Long auditorId) {
        RptTaskData data = rptTaskDataMapper.selectById(dataId);
        if (data == null) throw new RuntimeException("填报数据不存在");
        if (data.getStatus() == null || data.getStatus() != 1) throw new RuntimeException("数据未提交，无法审核");
        authz.assertCanAudit(data.getTaskId(), data.getFillUserId(), auditorId);
        if (data.getDeptAuditStatus() != null && data.getDeptAuditStatus() != 0) {
            throw new RuntimeException("部门已审核，请勿重复操作");
        }
        // 并发修复（审计 P0-2）：条件更新原子抢占，避免两人同时审核互相覆盖
        LocalDateTime now = LocalDateTime.now();
        int claimed = rptTaskDataMapper.update(null,
                Wrappers.<RptTaskData>lambdaUpdate()
                        .set(RptTaskData::getDeptAuditStatus, pass ? 1 : 2)
                        .set(RptTaskData::getDeptAuditUserId, auditorId)
                        .set(RptTaskData::getDeptAuditTime, now)
                        .set(RptTaskData::getDeptAuditComment,
                                StrUtil.maxLength(StrUtil.nullToEmpty(comment), 500))
                        .eq(RptTaskData::getId, dataId)
                        .eq(RptTaskData::getStatus, 1)
                        .and(w -> w.isNull(RptTaskData::getDeptAuditStatus)
                                .or().eq(RptTaskData::getDeptAuditStatus, 0)));
        if (claimed == 0) {
            throw new RuntimeException("数据状态已变更（可能已被他人审核），请刷新后重试");
        }
        if (!pass) {
            // 部门驳回：退回草稿，允许重新填报
            rptTaskDataMapper.update(null,
                    Wrappers.<RptTaskData>lambdaUpdate()
                            .set(RptTaskData::getStatus, 0)
                            .set(RptTaskData::getSubmitTime, null)
                            .set(RptTaskData::getAuditStatus, 2)
                            .eq(RptTaskData::getId, dataId));
        }
        processLog.log(data.getTaskId(), dataId, "DEPT_AUDIT", "部门审核", pass ? "PASS" : "REJECT", auditorId, comment);
    }

    /** 第二级：任务方审核（需先通过部门审核） */
    @Transactional
    public void taskAudit(Long dataId, boolean pass, String comment, Long auditorId) {
        RptTaskData data = rptTaskDataMapper.selectById(dataId);
        if (data == null) throw new RuntimeException("填报数据不存在");
        if (data.getStatus() == null || data.getStatus() != 1) throw new RuntimeException("数据未提交，无法审核");
        if (data.getDeptAuditStatus() == null || data.getDeptAuditStatus() != 1) {
            throw new RuntimeException("部门审核未通过，不能进行任务方审核");
        }
        // 质检拦截（可插拔）：report.quality.required=true 时，未通过质检不允许审核
        // （对应原版「报告未生成不可审核」）；默认 false，不影响现有流程。
        qualityReportService.assertCanAudit(dataId);
        authz.assertCanAudit(data.getTaskId(), data.getFillUserId(), auditorId);
        if (data.getTaskAuditStatus() != null && data.getTaskAuditStatus() != 0) {
            throw new RuntimeException("任务方已审核，请勿重复操作");
        }
        // 并发修复（审计 P0-2）：条件更新原子抢占
        LocalDateTime now = LocalDateTime.now();
        int claimed = rptTaskDataMapper.update(null,
                Wrappers.<RptTaskData>lambdaUpdate()
                        .set(RptTaskData::getTaskAuditStatus, pass ? 1 : 2)
                        .set(RptTaskData::getTaskAuditUserId, auditorId)
                        .set(RptTaskData::getTaskAuditTime, now)
                        .set(RptTaskData::getTaskAuditComment,
                                StrUtil.maxLength(StrUtil.nullToEmpty(comment), 500))
                        .set(RptTaskData::getAuditStatus, pass ? 1 : 2)
                        .eq(RptTaskData::getId, dataId)
                        .eq(RptTaskData::getStatus, 1)
                        .and(w -> w.isNull(RptTaskData::getTaskAuditStatus)
                                .or().eq(RptTaskData::getTaskAuditStatus, 0)));
        if (claimed == 0) {
            throw new RuntimeException("数据状态已变更（可能已被他人审核），请刷新后重试");
        }
        if (!pass) {
            rptTaskDataMapper.update(null,
                    Wrappers.<RptTaskData>lambdaUpdate()
                            .set(RptTaskData::getStatus, 0)
                            .set(RptTaskData::getSubmitTime, null)
                            .eq(RptTaskData::getId, dataId));
        }
        processLog.log(data.getTaskId(), dataId, "TASK_AUDIT", "任务方审核", pass ? "PASS" : "REJECT", auditorId, comment);
    }

    /** 流转轨迹 */
    public List<Map<String, Object>> trajectory(Long taskId) {
        return jdbcTemplate.queryForList(
                "SELECT id, node_code, node_name, action, operator_name, comment, create_time "
                        + "FROM rpt_task_process WHERE task_id = ? ORDER BY id ASC", taskId);
    }

    /** 发起补正（任务方勾选需要补正的数据） */
    @Transactional
    public Long startCorrect(Long taskId, String dataIds, String comment, Long userId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        int updated = 0;
        if (StrUtil.isNotBlank(dataIds)) {
            for (String raw : dataIds.split(",")) {
                if (StrUtil.isBlank(raw)) continue;
                Long dataId = Long.valueOf(raw.trim());
                RptTaskData data = rptTaskDataMapper.selectById(dataId);
                if (data == null) continue;
                data.setCorrectStatus(1);
                data.setStatus(0);
                data.setSubmitTime(null);
                data.setUpdateTime(LocalDateTime.now());
                rptTaskDataMapper.updateById(data);
                updated++;
            }
        }
        jdbcTemplate.update("INSERT INTO rpt_correct_task (task_id, data_ids, start_user_id, start_time, status, comment) "
                + "VALUES (?,?,?,NOW(),1,?)", taskId, StrUtil.nullToEmpty(dataIds), userId,
                StrUtil.maxLength(StrUtil.nullToEmpty(comment), 500));
        Long correctId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        task.setCorrectStatus(1);
        task.setUpdateTime(LocalDateTime.now());
        rptTaskMapper.updateById(task);
        processLog.log(taskId, null, "CORRECT", "补正流程", "CORRECT_START", userId,
                "发起补正，涉及 " + updated + " 条数据；" + StrUtil.nullToEmpty(comment));
        return correctId;
    }

    /** 关闭补正 */
    @Transactional
    public void closeCorrect(Long taskId, Long userId, String comment) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        jdbcTemplate.update("UPDATE rpt_correct_task SET status = 2, close_user_id = ?, close_time = NOW() "
                + "WHERE task_id = ? AND status = 1", userId, taskId);
        jdbcTemplate.update("UPDATE rpt_task_data SET correct_status = 2 WHERE task_id = ? AND correct_status = 1", taskId);
        task.setCorrectStatus(2);
        task.setUpdateTime(LocalDateTime.now());
        rptTaskMapper.updateById(task);
        processLog.log(taskId, null, "CORRECT", "补正流程", "CORRECT_CLOSE", userId, comment);
    }

    /** 补正批次列表 */
    public List<Map<String, Object>> correctList(Long taskId) {
        return jdbcTemplate.queryForList(
                "SELECT c.id, c.task_id, c.data_ids, c.status, c.comment, c.start_time, c.close_time, "
                        + "u.real_name AS start_user_name FROM rpt_correct_task c "
                        + "LEFT JOIN sys_user u ON u.id = c.start_user_id WHERE c.task_id = ? ORDER BY c.id DESC", taskId);
    }
}
