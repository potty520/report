package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptDatasource;
import com.reportsys.service.RptDatasourceService;
import com.reportsys.service.TemplateDisplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DatasourceController {

    @Autowired
    private RptDatasourceService rptDatasourceService;
    @Autowired
    private TemplateDisplayService templateDisplayService;

    /**
     * 分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<RptDatasource>> listPage(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) String dsName,
                                                       @RequestParam(required = false) String dsType) {
        try {
            PageResult<RptDatasource> result = rptDatasourceService.listPage(page, pageSize, dsName, dsType);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 全部列表(下拉框用)
     */
    @GetMapping("/list")
    public Result<List<RptDatasource>> listAll() {
        try {
            List<RptDatasource> list = rptDatasourceService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增
     */
    @PostMapping
    public Result<Void> add(@RequestBody RptDatasource datasource) {
        try {
            rptDatasourceService.add(datasource);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新
     */
    @PutMapping
    public Result<Void> update(@RequestBody RptDatasource datasource) {
        try {
            rptDatasourceService.update(datasource);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除
     */
    /** 兼容旧前端：PUT /xxx/{id}（id 在路径里）；委托同类 update，保留权限与校验 */
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody RptDatasource datasource) {
        datasource.setId(id);
        return update(datasource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptDatasourceService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 测试连接
     */
    @PostMapping("/test")
    public Result<Boolean> testConnection(@RequestParam String ip,
                                           @RequestParam String port,
                                           @RequestParam String dbName,
                                           @RequestParam String username,
                                           @RequestParam String password,
                                           @RequestParam String dsType) {
        try {
            RptDatasource ds = new RptDatasource();
            ds.setIp(ip);
            ds.setPort(port != null ? Integer.parseInt(port) : 3306);
            ds.setDbName(dbName);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDsType(dsType);
            boolean success = rptDatasourceService.testConnection(ds);
            if (success) {
                return Result.success("连接成功", true);
            }
            return Result.error("连接失败");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    /** R-08 数据源下的表清单 */
    @GetMapping("/{id}/tables")
    public Result<List<String>> tables(@PathVariable Long id) {
        try {
            return Result.success(templateDisplayService.listTables(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** R-08 表的列清单 */
    @GetMapping("/{id}/tables/{table}/columns")
    public Result<List<Map<String, Object>>> columns(@PathVariable Long id, @PathVariable String table) {
        try {
            return Result.success(templateDisplayService.listColumns(id, table));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}
