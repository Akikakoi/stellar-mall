package com.stellar.service.impl;

import com.stellar.entity.MallOrder;
import com.stellar.entity.MallUser;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.mapper.MallUserMapper;
import com.stellar.service.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportServiceImpl implements DataExportService {

    private final MallOrderMapper orderMapper;
    private final MallUserMapper userMapper;

    private static final String[] ORDER_HEADERS = {
            "订单编号", "用户手机号", "订单总额", "实付金额", "订单状态",
            "收货地址", "快递单号", "快递公司", "创建时间"
    };

    private static final String[] USER_HEADERS = {
            "用户ID", "手机号", "昵称", "邮箱", "状态", "注册时间"
    };

    private static final String[] FINANCE_HEADERS = {
            "月份", "订单数", "销售额(元)", "实收金额(元)", "退款订单数", "退款金额(元)"
    };

    private static final Map<String, String> STATUS_CN = Map.of(
            "PENDING", "待付款", "PAID", "已付款", "SHIPPED", "已发货",
            "COMPLETED", "已完成", "CANCELLED", "已取消", "REFUNDED", "已退款"
    );

    @Override
    public byte[] exportOrders(String status, String startTime, String endTime) {
        List<MallOrder> orders = orderMapper.listAllForExport(status, startTime, endTime);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("订单数据");
            createHeaderRow(sheet, ORDER_HEADERS, wb);

            CellStyle style = createDataStyle(wb);
            int rowIdx = 1;
            for (MallOrder o : orders) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, o.getOrderNo(), style);
                setCell(row, 1, o.getUserPhone(), style);
                setCell(row, 2, o.getTotalAmount(), style);
                setCell(row, 3, o.getPayAmount(), style);
                setCell(row, 4, STATUS_CN.getOrDefault(o.getStatus(), o.getStatus()), style);
                setCell(row, 5, o.getAddress(), style);
                setCell(row, 6, o.getTrackingNo(), style);
                setCell(row, 7, o.getDeliveryCompany(), style);
                setCell(row, 8, o.getCreateTime(), style);
            }

            autoSize(sheet, ORDER_HEADERS.length);
            return toBytes(wb);
        } catch (IOException e) {
            log.error("导出订单失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    @Override
    public byte[] exportUsers() {
        List<MallUser> users = userMapper.listAllForExport();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户数据");
            createHeaderRow(sheet, USER_HEADERS, wb);

            CellStyle style = createDataStyle(wb);
            int rowIdx = 1;
            for (MallUser u : users) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, u.getId(), style);
                setCell(row, 1, u.getPhone(), style);
                setCell(row, 2, u.getNickname(), style);
                setCell(row, 3, u.getEmail(), style);
                setCell(row, 4, (u.getStatus() != null && u.getStatus() == 1) ? "正常" : "禁用", style);
                setCell(row, 5, u.getCreateTime(), style);
            }

            autoSize(sheet, USER_HEADERS.length);
            return toBytes(wb);
        } catch (IOException e) {
            log.error("导出用户失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    @Override
    public byte[] exportFinanceReport(String year) {
        if (year == null || year.isEmpty()) {
            year = String.valueOf(LocalDate.now().getYear());
        }
        List<Map<String, Object>> rows = orderMapper.financeMonthlySummary(year);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("财务报表 " + year);
            createHeaderRow(sheet, FINANCE_HEADERS, wb);

            // 汇总行数据
            CellStyle style = createDataStyle(wb);
            CellStyle summaryStyle = createSummaryStyle(wb);
            int rowIdx = 1;
            long totalOrders = 0, totalRefunds = 0;
            BigDecimal totalAmount = BigDecimal.ZERO, totalPay = BigDecimal.ZERO, totalRefundAmount = BigDecimal.ZERO;

            for (Map<String, Object> rowData : rows) {
                Row row = sheet.createRow(rowIdx++);
                String month = String.valueOf(rowData.getOrDefault("month", ""));
                long orderCount = toLong(rowData.get("order_count"));
                BigDecimal amount = toBigDecimal(rowData.get("total_amount"));
                BigDecimal pay = toBigDecimal(rowData.get("pay_amount"));
                long refundCount = toLong(rowData.get("refund_count"));
                BigDecimal refundAmount = toBigDecimal(rowData.get("refund_amount"));

                setCell(row, 0, month, style);
                setCell(row, 1, orderCount, style);
                setCell(row, 2, amount, style);
                setCell(row, 3, pay, style);
                setCell(row, 4, refundCount, style);
                setCell(row, 5, refundAmount, style);

                totalOrders += orderCount;
                totalRefunds += refundCount;
                totalAmount = totalAmount.add(amount);
                totalPay = totalPay.add(pay);
                totalRefundAmount = totalRefundAmount.add(refundAmount);
            }

            // 汇总行
            if (rowIdx > 1) {
                Row sumRow = sheet.createRow(rowIdx);
                setCell(sumRow, 0, "合计", summaryStyle);
                setCell(sumRow, 1, totalOrders, summaryStyle);
                setCell(sumRow, 2, totalAmount, summaryStyle);
                setCell(sumRow, 3, totalPay, summaryStyle);
                setCell(sumRow, 4, totalRefunds, summaryStyle);
                setCell(sumRow, 5, totalRefundAmount, summaryStyle);
            }

            autoSize(sheet, FINANCE_HEADERS.length);
            return toBytes(wb);
        } catch (IOException e) {
            log.error("导出财务报表失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    // ======================== 工具方法 ========================

    private void createHeaderRow(Sheet sheet, String[] headers, Workbook wb) {
        Row header = sheet.createRow(0);
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
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
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

    private CellStyle createSummaryStyle(Workbook wb) {
        CellStyle style = createDataStyle(wb);
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return bos.toByteArray();
    }

    private long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return BigDecimal.ZERO;
    }
}
