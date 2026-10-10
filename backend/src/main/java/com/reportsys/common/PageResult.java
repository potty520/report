package com.reportsys.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private long pageNum;
    private long pageSize;
    private long pages;
    private List<T> list;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        result.setList(page.getRecords());
        return result;
    }

    public static <T> PageResult<T> of(List<T> list, long pageNum, long pageSize, long total) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages(total / pageSize + (total % pageSize > 0 ? 1 : 0));
        result.setList(list);
        return result;
    }
}
