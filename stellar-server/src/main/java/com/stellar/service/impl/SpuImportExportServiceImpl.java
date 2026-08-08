package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.service.SpuImportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SPU 商品导入导出服务实现。
 *
 * <p>支持将全部商品（含 SKU）导出为 Excel、生成导入模板、
 * 以及从 Excel 批量导入商品数据。导入时按 SPU 名称分组，
 * 同名 SPU 复用，不同 SKU 规格行为独立插入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuImportExportServiceImpl implements SpuImportExportService {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;

    private static final String[] HEADERS = {
            "SPU名称", "副标题", "一级分类ID",
            "主图URL", "商品描述", "排序值", "状态",
            "SKU规格名称", "SKU规格值", "价格(元)", "库存", "条码"
    };

    private static final int COL_NAME = 0;
    private static final int COL_SUBTITLE = 1;
    private static final int COL_CATEGORY_ID = 2;
    private static final int COL_MAIN_IMAGE = 3;
    private static final int COL_DESC = 4;
    private static final int COL_SORT = 5;
    private static final int COL_STATUS = 6;
    private static final int COL_SKU_NAME = 7;
    private static final int COL_SKU_SPECS = 8;
    private static final int COL_SKU_PRICE = 9;
    private static final int COL_SKU_STOCK = 10;
    private static final int COL_SKU_BARCODE = 11;

    /**
     * 导出全部商品数据为 Excel 字节流。
     *
     * <p>每个 SPU 与其关联的 SKU 展开为多行，SPU 列重复填充，
     * 无 SKU 的商品也导出一行。</p>
     *
     * @return Excel 文件的字节数组
     */
    @Override
    public byte[] exportAll() {
        List<Spu> spuList = spuMapper.listAll();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("商品数据");
            createHeaderRow(sheet);

            int rowIdx = 1;
            CellStyle defaultStyle = createDataStyle(wb);

            for (Spu spu : spuList) {
                List<Sku> skus = skuMapper.listBySpuId(spu.getId());
                if (skus.isEmpty()) {
                    // 没有 SKU 的商品也导出一行
                    Row row = sheet.createRow(rowIdx++);
                    fillSpuColumns(row, spu, defaultStyle);
                } else {
                    for (Sku sku : skus) {
                        Row row = sheet.createRow(rowIdx++);
                        fillSpuColumns(row, spu, defaultStyle);
                        fillSkuColumns(row, sku, defaultStyle);
                    }
                }
            }

            // 自适应列宽
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("导出 Excel 失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 生成商品导入模板 Excel 文件。
     *
     * <p>包含表头、示例数据行和分类 ID 参考页。</p>
     *
     * @return 模板 Excel 文件的字节数组
     */
    @Override
    public byte[] generateTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("商品导入模板");

            // 表头
            Row header = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(wb);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 示例数据行
            CellStyle demoStyle = createDataStyle(wb);
            Object[][] examples = {
                    {"示例智能手机", "2024新款", "1", "https://example.com/img.jpg", "商品描述示例", "0", "1", "标准版 · 星空黑", "颜色:星空黑;存储:128GB", "2999.00", "100", "6901234567890"},
                    {"示例智能手机", "2024新款", "1", "https://example.com/img.jpg", "商品描述示例", "0", "1", "高配版 · 极光蓝", "颜色:极光蓝;存储:256GB", "3599.00", "50", "6901234567891"},
            };
            CellStyle wrapStyle = createDataStyle(wb);
            wrapStyle.setWrapText(true);

            for (int i = 0; i < examples.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < examples[i].length; j++) {
                    Cell cell = row.createCell(j);
                    String val = String.valueOf(examples[i][j]);
                    cell.setCellValue(val);
                    cell.setCellStyle(wrapStyle);
                }
            }

            // 第二页：分类参考
            Sheet catSheet = wb.createSheet("分类ID参考");
            Row catHeader = catSheet.createRow(0);
            catHeader.createCell(0).setCellValue("分类ID");
            catHeader.createCell(1).setCellValue("分类名称");
            catHeader.setRowStyle(headerStyle);

            // 注意：这里硬编码了几个常见的分类ID，实际可从数据库读取
            Object[][] catData = {
                    {"1", "智能手机"},
                    {"2", "家用电冰箱"},
                    {"3", "家用空调"},
                    {"5", "平板电视"},
                    {"6", "笔记本电脑"},
                    {"7", "智能影音"},
                    {"8", "智能穿戴"},
            };
            for (int i = 0; i < catData.length; i++) {
                Row r = catSheet.createRow(i + 1);
                for (int j = 0; j < catData[i].length; j++) {
                    r.createCell(j).setCellValue(String.valueOf(catData[i][j]));
                }
            }
            catSheet.autoSizeColumn(0);
            catSheet.autoSizeColumn(1);

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("生成模板失败", e);
            throw new RuntimeException("生成模板失败：" + e.getMessage());
        }
    }

    /**
     * 从 Excel 文件批量导入商品数据。
     *
     * <p>按 SPU 名称分组，同名 SPU 复用已有记录，不同 SKU 行独立插入。
     * 导入完成后反写 SPU 的聚合字段（价格区间、总库存、SKU 数量）。</p>
     *
     * @param file 上传的 Excel 文件
     * @return 包含导入结果信息的 Map（success, newSpuCount, newSkuCount, skippedRows, errors 等）
     */
    @Override
    @Transactional
    public Map<String, Object> importFromExcel(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        int successSpu = 0;
        int successSku = 0;
        int skippedRows = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                result.put("success", false);
                result.put("msg", "Excel 文件为空（至少需要一行数据）");
                return result;
            }

            // 按 SPU 名称分组
            Map<String, List<ExcelRow>> spuGroups = new LinkedHashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String spuName = getCellString(row, COL_NAME);
                if (spuName == null || spuName.trim().isEmpty()) {
                    skippedRows++;
                    continue;
                }
                spuName = spuName.trim();

                ExcelRow er = new ExcelRow();
                er.rowNum = i + 1;
                er.spuName = spuName;
                er.subTitle = getCellString(row, COL_SUBTITLE);
                er.categoryId = parseLong(row, COL_CATEGORY_ID);
                er.mainImage = getCellString(row, COL_MAIN_IMAGE);
                er.description = getCellString(row, COL_DESC);
                er.sort = parseInt(row, COL_SORT, 0);
                er.status = parseInt(row, COL_STATUS, 1);
                er.skuName = getCellString(row, COL_SKU_NAME);
                er.skuSpecs = getCellString(row, COL_SKU_SPECS);
                er.skuPrice = parseBigDecimal(row, COL_SKU_PRICE);
                er.skuStock = parseInt(row, COL_SKU_STOCK, 0);
                er.skuBarcode = getCellString(row, COL_SKU_BARCODE);

                // 校验必填
                if (er.categoryId == null) {
                    errors.add("第" + er.rowNum + "行：一级分类ID不能为空");
                    skippedRows++;
                    continue;
                }
                if (er.skuName == null || er.skuName.trim().isEmpty()) {
                    errors.add("第" + er.rowNum + "行：SKU规格名称不能为空");
                    skippedRows++;
                    continue;
                }
                if (er.skuPrice == null) {
                    errors.add("第" + er.rowNum + "行：价格不能为空");
                    skippedRows++;
                    continue;
                }

                spuGroups.computeIfAbsent(spuName, k -> new ArrayList<>()).add(er);
            }

            Long userId = BaseContext.getCurrentId();

            // 逐组创建/复用 SPU + 插入 SKU
            for (Map.Entry<String, List<ExcelRow>> entry : spuGroups.entrySet()) {
                String spuName = entry.getKey();
                List<ExcelRow> rows = entry.getValue();
                ExcelRow first = rows.get(0);

                // 先查是否已有同名 SPU
                Spu existingSpu = findSpuByName(spuName);
                Long spuId;
                if (existingSpu != null) {
                    spuId = existingSpu.getId();
                } else {
                    Spu spu = Spu.builder()
                            .name(first.spuName)
                            .subTitle(first.subTitle)
                            .categoryId(first.categoryId)
                            .mainImage(first.mainImage)
                            .description(nullToEmpty(first.description))
                            .sort(first.sort)
                            .status(first.status != null ? first.status : 1)
                            .createTime(LocalDateTime.now())
                            .createUser(userId)
                            .updateTime(LocalDateTime.now())
                            .updateUser(userId)
                            .build();
                    spuMapper.insert(spu);
                    spuId = spu.getId();
                    successSpu++;
                }

                // 插入该组所有 SKU
                BigDecimal minPrice = null;
                BigDecimal maxPrice = null;
                int totalStock = 0;

                for (ExcelRow er : rows) {
                    Sku sku = Sku.builder()
                            .spuId(spuId)
                            .name(er.skuName != null ? er.skuName.trim() : "默认规格")
                            .specs(er.skuSpecs)
                            .price(er.skuPrice)
                            .stock(er.skuStock != null ? er.skuStock : 0)
                            .barcode(er.skuBarcode)
                            .status(1)
                            .createTime(LocalDateTime.now())
                            .createUser(userId)
                            .updateTime(LocalDateTime.now())
                            .updateUser(userId)
                            .build();
                    skuMapper.insert(sku);
                    successSku++;

                    if (er.skuPrice != null) {
                        if (minPrice == null || er.skuPrice.compareTo(minPrice) < 0) minPrice = er.skuPrice;
                        if (maxPrice == null || er.skuPrice.compareTo(maxPrice) > 0) maxPrice = er.skuPrice;
                    }
                    totalStock += (er.skuStock != null ? er.skuStock : 0);
                }

                // 反写 SPU 聚合字段
                int skuCount = spuMapper.refreshAggregatesFromSku(spuId, minPrice, maxPrice, totalStock, rows.size());
            }

        } catch (IOException e) {
            log.error("读取 Excel 失败", e);
            result.put("success", false);
            result.put("msg", "读取文件失败：" + e.getMessage());
            return result;
        }

        result.put("success", true);
        result.put("newSpuCount", successSpu);
        result.put("newSkuCount", successSku);
        result.put("skippedRows", skippedRows);
        if (!errors.isEmpty()) {
            result.put("errors", errors.size() > 10 ? errors.subList(0, 10) : errors);
            result.put("errorCount", errors.size());
        }
        return result;
    }

    // ======================== 辅助方法 ========================

    private void createHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        CellStyle style = createHeaderStyle(sheet.getWorkbook());
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void fillSpuColumns(Row row, Spu spu, CellStyle style) {
        setCell(row, COL_NAME, spu.getName(), style);
        setCell(row, COL_SUBTITLE, spu.getSubTitle(), style);
        setCell(row, COL_CATEGORY_ID, spu.getCategoryId(), style);
        setCell(row, COL_MAIN_IMAGE, spu.getMainImage(), style);
        setCell(row, COL_DESC, spu.getDescription(), style);
        setCell(row, COL_SORT, spu.getSort(), style);
        setCell(row, COL_STATUS, spu.getStatus(), style);
    }

    private void fillSkuColumns(Row row, Sku sku, CellStyle style) {
        setCell(row, COL_SKU_NAME, sku.getName(), style);
        setCell(row, COL_SKU_SPECS, sku.getSpecs(), style);
        setCell(row, COL_SKU_PRICE, sku.getPrice(), style);
        setCell(row, COL_SKU_STOCK, sku.getStock(), style);
        setCell(row, COL_SKU_BARCODE, sku.getBarcode(), style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String val = cell.getStringCellValue();
        return (val == null || val.trim().isEmpty()) ? null : val.trim();
    }

    private Long parseLong(Row row, int col) {
        String val = getCellString(row, col);
        if (val == null) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(Row row, int col, int defaultVal) {
        String val = getCellString(row, col);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
    }

    private BigDecimal parseBigDecimal(Row row, int col) {
        String val = getCellString(row, col);
        if (val == null) return null;
        try { return new BigDecimal(val); } catch (NumberFormatException e) { return null; }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 按名称精确匹配查找已有 SPU（用于导入时去重）。 */
    private Spu findSpuByName(String name) {
        // 通过 page 接口查询，但这里需要一个更直接的方法
        List<Spu> list = spuMapper.page(0, 1, name, null, null, null, null, null, null, null, null);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    // ======================== 内部类 ========================

    private static class ExcelRow {
        int rowNum;
        String spuName, subTitle, mainImage, description;
        Long categoryId;
        Integer sort, status;
        String skuName, skuSpecs, skuBarcode;
        BigDecimal skuPrice;
        Integer skuStock;
    }
}
