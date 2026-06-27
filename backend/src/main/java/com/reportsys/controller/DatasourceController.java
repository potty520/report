package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptDatasource;
import com.reportsys.service.RptDatasourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datasource")
public class DatasourceController {

    @Autowired
    private RptDatasourceService rptDatasourceService;

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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptDatasourceService.deleteById(id);
            return Result.success();
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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
