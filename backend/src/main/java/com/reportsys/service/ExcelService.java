package com.reportsys.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 读写工具类（自包含，只依赖 POI，不依赖任何强类型模型）。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>读取统一走 POI {@link WorkbookFactory#create(InputStream)}，因此 {@code .xlsx} 与 {@code .xls} 都能读。</li>
 *   <li>列名规则：表头单元格为空时用 {@code 列N}（N 为 1 基列号，例如第 3 列空表头为 {@code 列3}）；
 *       同名表头按出现顺序追加 {@code _2}、{@code _3}（例如 {@code 金额}、{@code 金额_2}）。
 *       表头行中间的空列保留占位名，以保证 {@link #parseHeader} 与 {@link #parseRows} 的列是一一对应的；
 *       表头右侧完全为空的尾部列不予输出。</li>
 *   <li>单元格取值：字符串原样；数字整数值输出 {@code 1} 而不是 {@code 1.0}；布尔输出 {@code true}/{@code false}；
 *       日期用 {@link DateUtil#isCellDateFormatted(Cell)} 判定，输出 {@code yyyy-MM-dd}（无时分秒）或
 *       {@code yyyy-MM-dd HH:mm:ss}（有时分秒）；公式单元格用 {@link DataFormatter} + {@link FormulaEvaluator}
 *       取显示值（求值失败时退回公式缓存结果）；错误单元格输出 Excel 的错误串（如 {@code #DIV/0!}）。</li>
 *   <li>导出统一用 POI {@code XSSFWorkbook} 生成 xlsx 字节数组，表头行加粗 + 浅灰背景，列宽按内容自适应。</li>
 * </ul>
 *
 * <p>异常：所有解析异常包装为 {@code RuntimeException("Excel 解析失败: ...", e)}，
 * 所有写出异常包装为 {@code RuntimeException("Excel 导出失败: ...", e)}，不吞异常。</p>
 */
@Service
public class ExcelService {

    /** 纯日期格式 */
    private static final String PATTERN_DATE = "yyyy-MM-dd";
    /** 日期时间格式 */
    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 列宽下限（字符数，约等于 20 左右的自适应宽度） */
    private static final int MIN_COLUMN_CHARS = 18;
    /** 列宽上限（字符数） */
    private static final int MAX_COLUMN_CHARS = 40;
    /** POI 列宽单位：1 个字符 = 256 */
    private static final int WIDTH_UNIT = 256;

    /**
     * 解析表头：读取第 headerRowIndex 行（0 基）的所有非空单元格，按列顺序返回列名。
     *
     * <p>列名规则：单元格为空 -> {@code 列N}（N 为 1 基列号）；列名重复 -> 追加 {@code _2}、{@code _3}。
     * 表头行不存在或整行为空时返回空列表。</p>
     *
     * @param in              Excel 输入流（方法返回时会关闭该流），支持 .xlsx / .xls
     * @param headerRowIndex 表头所在行下标，0 基（0 表示第一行）
     * @return 按列顺序排列的列名列表；表头为空返回空列表
     */
    public List<String> parseHeader(InputStream in, int headerRowIndex) {
        if (headerRowIndex < 0) {
            throw new RuntimeException("Excel 解析失败: headerRowIndex 不能小于 0");
        }
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = firstSheet(workbook);
            if (sheet == null) {
                return new ArrayList<String>();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(headerRowIndex);
            int width = headerSpanWidth(headerRow, formatter, evaluator);
            if (width <= 0) {
                return new ArrayList<String>();
            }
            return buildColumnNames(headerRow, width, formatter, evaluator);
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析数据行：第 headerRowIndex 行是表头，往下每个非空行返回 Map&lt;列名, 单元格字符串值&gt;；完全空行跳过。
     *
     * <p>行为说明：</p>
     * <ul>
     *   <li>列名规则与 {@link #parseHeader} 一致（空表头 -> {@code 列N}，重复 -> {@code _2}/{@code _3}），
     *       因此 Map 的 key 就是表头列名，key 顺序与列顺序一致（LinkedHashMap）。</li>
     *   <li>行内空单元格写入空字符串 {@code ""}（不是 null，方便直接入库/再导出）；只有整行所有列都为空才跳过该行。</li>
     *   <li>数据列数比表头多时，多出的列自动补 {@code 列N} 列名，不丢数据。</li>
     *   <li>只读第一个 sheet；表头行不存在或没有任何数据列时返回空列表。</li>
     * </ul>
     *
     * @param in              Excel 输入流（方法返回时会关闭该流），支持 .xlsx / .xls
     * @param headerRowIndex 表头所在行下标，0 基
     * @return 数据行列表，每个 Map 的 key 为列名、value 为单元格字符串值
     */
    public List<Map<String, String>> parseRows(InputStream in, int headerRowIndex) {
        if (headerRowIndex < 0) {
            throw new RuntimeException("Excel 解析失败: headerRowIndex 不能小于 0");
        }
        try (Workbook workbook = WorkbookFactory.create(in)) {
            List<Map<String, String>> result = new ArrayList<Map<String, String>>();
            Sheet sheet = firstSheet(workbook);
            if (sheet == null) {
                return result;
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(headerRowIndex);
            int width = Math.max(headerSpanWidth(headerRow, formatter, evaluator), maxDataWidth(sheet, headerRowIndex));
            if (width <= 0) {
                return result;
            }
            List<String> columnNames = buildColumnNames(headerRow, width, formatter, evaluator);
            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, width, formatter, evaluator)) {
                    continue;
                }
                Map<String, String> rowMap = new LinkedHashMap<String, String>();
                for (int col = 0; col < width; col++) {
                    rowMap.put(columnNames.get(col), cellString(cellAt(row, col), formatter, evaluator));
                }
                result.add(rowMap);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成模板文件（xlsx 字节流）：第一行写 headers，第二行写 exampleRow（可为 null 则只写表头）。
     *
     * <p>表头行加粗 + 浅灰背景并居中；列宽按表头/示例内容自适应（约 18~40 字符）。</p>
     *
     * @param headers    表头列名，可为 null/空（则生成空表）
     * @param exampleRow 示例数据行，元素为 null 时写空字符串；传 null 表示不写示例行
     * @return xlsx 文件字节数组
     */
    public byte[] buildTemplate(List<String> headers, List<String> exampleRow) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            CellStyle headerStyle = headerStyle(workbook);

            List<String> headerList = headers == null ? Collections.<String>emptyList() : headers;
            List<String> exampleList = exampleRow == null ? Collections.<String>emptyList() : exampleRow;
            int columns = Math.max(headerList.size(), exampleList.size());
            int[] maxTextWidth = new int[columns];

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headerList.size(); col++) {
                String value = headerList.get(col) == null ? "" : headerList.get(col);
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(value);
                cell.setCellStyle(headerStyle);
                maxTextWidth[col] = Math.max(maxTextWidth[col], displayWidth(value));
            }

            if (exampleRow != null) {
                Row example = sheet.createRow(1);
                for (int col = 0; col < exampleList.size(); col++) {
                    String value = exampleList.get(col) == null ? "" : exampleList.get(col);
                    Cell cell = example.createCell(col);
                    cell.setCellValue(value);
                    maxTextWidth[col] = Math.max(maxTextWidth[col], displayWidth(value));
                }
            }

            applyColumnWidths(sheet, maxTextWidth);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出数据（xlsx 字节流）：第一行表头 headers，之后按 rows 顺序写值（值为 null 写空）。
     *
     * <p>取值规则：只需要 {@code Map} 里以 headers 中的列名为 key 的值，其它 key 忽略；
     * Map 中缺少某个列名时该单元格写空。值按类型写入：字符串/布尔/数字/日期（{@code Date}、{@code Calendar}、
     * {@code LocalDate}、{@code LocalDateTime}）分别落到对应的 Excel 单元格类型，其余类型用 {@code String.valueOf}。
     * 表头行加粗 + 浅灰背景，列宽按内容自适应。</p>
     *
     * @param headers 表头列名，可为 null/空
     * @param rows    数据行列表，可为 null；每行是 Map&lt;列名, 值&gt;
     * @return xlsx 文件字节数组
     */
    public byte[] export(List<String> headers, List<Map<String, Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook, PATTERN_DATE);
            CellStyle dateTimeStyle = dateStyle(workbook, PATTERN_DATETIME);

            List<String> headerList = headers == null ? Collections.<String>emptyList() : headers;
            int[] maxTextWidth = new int[headerList.size()];

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headerList.size(); col++) {
                String value = headerList.get(col) == null ? "" : headerList.get(col);
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(value);
                cell.setCellStyle(headerStyle);
                maxTextWidth[col] = Math.max(maxTextWidth[col], displayWidth(value));
            }

            int rowIndex = 1;
            if (rows != null) {
                for (Map<String, Object> data : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int col = 0; col < headerList.size(); col++) {
                        Object value = data == null ? null : data.get(headerList.get(col));
                        Cell cell = row.createCell(col);
                        writeValue(cell, value, dateStyle, dateTimeStyle);
                        maxTextWidth[col] = Math.max(maxTextWidth[col], displayWidth(value));
                    }
                }
            }

            applyColumnWidths(sheet, maxTextWidth);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // 读取相关私有方法
    // ------------------------------------------------------------------

    /** 取第一个 sheet，没有则返回 null */
    private Sheet firstSheet(Workbook workbook) {
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    /** 取单元格，缺失的列按 null 处理 */
    private Cell cellAt(Row row, int col) {
        return row == null ? null : row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    /** 表头行的有效列数（最后一个非空表头单元格的下标 + 1），全空返回 0 */
    private int headerSpanWidth(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (headerRow == null) {
            return 0;
        }
        int width = 0;
        int last = headerRow.getLastCellNum();
        for (int col = 0; col < last; col++) {
            if (!cellString(cellAt(headerRow, col), formatter, evaluator).trim().isEmpty()) {
                width = col + 1;
            }
        }
        return width;
    }

    /** 表头行以下数据区的最大列数 */
    private int maxDataWidth(Sheet sheet, int headerRowIndex) {
        int width = 0;
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getLastCellNum() > width) {
                width = row.getLastCellNum();
            }
        }
        return width;
    }

    /**
     * 生成 [0, width) 每一列的列名：空表头 -> 列N（N 为 1 基列号）；重名 -> 追加 _2、_3 …
     */
    private List<String> buildColumnNames(Row headerRow, int width, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> names = new ArrayList<String>(width);
        Set<String> used = new LinkedHashSet<String>();
        for (int col = 0; col < width; col++) {
            String raw = cellString(cellAt(headerRow, col), formatter, evaluator);
            String base = raw == null ? "" : raw.trim();
            if (base.isEmpty()) {
                base = "列" + (col + 1);
            }
            String name = base;
            int sequence = 1;
            while (used.contains(name)) {
                sequence++;
                name = base + "_" + sequence;
            }
            used.add(name);
            names.add(name);
        }
        return names;
    }

    /** 整行为空（在 [0, width) 范围内所有单元格取值都为空字符串）则返回 true */
    private boolean isBlankRow(Row row, int width, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        for (int col = 0; col < width; col++) {
            if (!cellString(cellAt(row, col), formatter, evaluator).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 单元格取字符串值（类型安全）：
     * 字符串原样、数字去掉整数浮点尾巴、布尔转 true/false、日期格式化为 yyyy-MM-dd[ HH:mm:ss]、
     * 公式取 DataFormatter 的显示值（用 FormulaEvaluator 求值，取不到时退回公式缓存结果）、错误单元格取错误串。
     */
    private String cellString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            return formulaString(cell, formatter, evaluator);
        }
        switch (type) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatDate(cell);
                }
                return formatNumber(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case STRING:
                return cell.getStringCellValue();
            case BLANK:
            case _NONE:
                return "";
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            default:
                return formatter.formatCellValue(cell);
        }
    }

    /**
     * 公式单元格取值：优先用 DataFormatter + FormulaEvaluator 求值（得到 Excel 里的显示值）；
     * 公式无法求值（例如自定义函数、外部引用）时退回公式的缓存结果，保证不因个别公式导致整表解析失败。
     */
    private String formulaString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (evaluator != null) {
            try {
                return formatter.formatCellValue(cell, evaluator);
            } catch (Exception ignore) {
                // 忽略求值异常，改用缓存结果
            }
        }
        switch (cell.getCachedFormulaResultType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatDate(cell);
                }
                return formatNumber(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            case STRING:
                return cell.getStringCellValue();
            default:
                return "";
        }
    }

    /** 日期单元格：无时分秒输出 yyyy-MM-dd，否则输出 yyyy-MM-dd HH:mm:ss */
    private String formatDate(Cell cell) {
        Date date = cell.getDateCellValue();
        if (date == null) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        if (dateTime.getHour() == 0 && dateTime.getMinute() == 0 && dateTime.getSecond() == 0) {
            return dateTime.toLocalDate().format(DateTimeFormatter.ofPattern(PATTERN_DATE));
        }
        return dateTime.format(DateTimeFormatter.ofPattern(PATTERN_DATETIME));
    }

    /** 数字单元格：整数值输出 1（不是 1.0），小数去掉多余的 0 且不使用科学计数法 */
    private String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) <= 9007199254740992D) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------
    // 写出相关私有方法
    // ------------------------------------------------------------------

    /** 写入一个值：null 写空字符串；字符串/布尔/数字/日期分别写入对应单元格类型 */
    private void writeValue(Cell cell, Object value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof CharSequence) {
            cell.setCellValue(value.toString());
            return;
        }
        if (value instanceof Boolean) {
            cell.setCellValue(((Boolean) value).booleanValue());
            return;
        }
        if (value instanceof LocalDateTime) {
            cell.setCellValue(toDate((LocalDateTime) value));
            cell.setCellStyle(dateTimeStyle);
            return;
        }
        if (value instanceof LocalDate) {
            cell.setCellValue(toDate(((LocalDate) value).atStartOfDay()));
            cell.setCellStyle(dateStyle);
            return;
        }
        if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateTimeStyle);
            return;
        }
        if (value instanceof Calendar) {
            cell.setCellValue((Calendar) value);
            cell.setCellStyle(dateTimeStyle);
            return;
        }
        if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
            return;
        }
        if (value instanceof BigInteger) {
            cell.setCellValue(((BigInteger) value).doubleValue());
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    /** LocalDateTime -> java.util.Date；用 Date 而非 POI 5 的 LocalDateTime 重载，避免 POI 版本被传递依赖降级时出问题 */
    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 表头样式：加粗 + 浅灰背景 + 居中 + 细边框 */
    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** 日期单元格样式 */
    private CellStyle dateStyle(Workbook workbook, String pattern) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(pattern));
        return style;
    }

    /** 列宽自适应：取该列内容显示宽度 + 5，限制在 18~40 字符之间 */
    private void applyColumnWidths(Sheet sheet, int[] maxTextWidth) {
        for (int col = 0; col < maxTextWidth.length; col++) {
            int chars = Math.min(MAX_COLUMN_CHARS, Math.max(MIN_COLUMN_CHARS, maxTextWidth[col] + 5));
            sheet.setColumnWidth(col, chars * WIDTH_UNIT);
        }
    }

    /** 估算显示宽度：CJK 等全角字符按 2 个字符宽计算 */
    private int displayWidth(Object value) {
        if (value == null) {
            return 0;
        }
        return displayWidth(String.valueOf(value));
    }

    /** 估算显示宽度：CJK 等全角字符按 2 个字符宽计算 */
    private int displayWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) >= 0x2E80 ? 2 : 1;
        }
        return width;
    }
}
