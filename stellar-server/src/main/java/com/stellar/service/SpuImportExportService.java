package com.stellar.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface SpuImportExportService {

    /** 导出全部商品（SPU + SKU）为 Excel 字节数组 */
    byte[] exportAll();

    /** 生成空白导入模板 */
    byte[] generateTemplate();

    /** 从 Excel 文件批量导入 */
    Map<String, Object> importFromExcel(MultipartFile file);
}
