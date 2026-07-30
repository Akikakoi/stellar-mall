package com.stellar.service;

public interface DataExportService {

    byte[] exportOrders(String status, String startTime, String endTime);

    byte[] exportUsers();

    byte[] exportFinanceReport(String year);
}
