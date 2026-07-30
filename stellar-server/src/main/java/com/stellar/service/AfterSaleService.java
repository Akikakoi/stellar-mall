package com.stellar.service;

import com.stellar.dto.AfterSaleAuditDTO;
import com.stellar.dto.AfterSaleReturnDTO;
import com.stellar.dto.AfterSaleSubmitDTO;
import com.stellar.entity.AfterSale;
import com.stellar.result.PageResult;
import com.stellar.vo.AfterSaleVO;

/**
 * 售后服务接口。
 */
public interface AfterSaleService {

    /**
     * 用户提交售后申请。
     * 1) 校验订单归属 + 已支付状态
     * 2) 校验无进行中的售后单
     * 3) 创建售后单，状态=申请中
     * 4) 订单状态标记为 REFUNDING
     */
    AfterSale submit(Long userId, AfterSaleSubmitDTO dto);

    /**
     * 用户取消售后申请（仅申请中或审核中状态可取消）。
     */
    void cancel(Long id, Long userId);

    /**
     * 用户提交退货物流单号（仅退货中状态）。
     */
    void submitReturnTracking(Long userId, AfterSaleReturnDTO dto);

    /**
     * 用户售后列表（分页）。
     */
    PageResult pageByUser(Long userId, int page, int pageSize);

    /**
     * 售后单详情。
     */
    AfterSaleVO getDetail(Long id, Long userId);

    /**
     * 管理端分页查询售后列表。
     */
    PageResult pageAll(int page, int pageSize, Integer status, Integer type);

    /**
     * 管理端查看售后单详情。
     */
    AfterSaleVO getDetailById(Long id);

    /**
     * 管理端审核售后单。
     * 通过：状态 → 审核中 → 根据类型跳转（仅退款→退款中，退货退款→用户退货中）
     * 拒绝：状态 → 已拒绝，订单恢复原状态
     */
    void audit(Long empId, AfterSaleAuditDTO dto);

    /**
     * 管理端确认退款完成。
     * 状态 → 完成，订单 → COMPLETED，回滚库存。
     */
    void confirmRefund(Long empId, Long id);

    /**
     * 根据订单ID查询用户售后单（用于订单页展示售后状态）。
     */
    AfterSaleVO getByOrderId(Long orderId, Long userId);
}
