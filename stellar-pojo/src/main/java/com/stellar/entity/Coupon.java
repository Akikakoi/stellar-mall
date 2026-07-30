package com.stellar.entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon implements Serializable {
    private Long id;
    private String name;          // 优惠券名称
    private Integer type;         // 1=满减券 2=折扣券
    private BigDecimal conditionAmount;  // 满减条件金额
    private BigDecimal discountAmount;   // 满减金额 / 折扣比例(0.85=85折)
    private Integer totalCount;   // 发放总量
    private Integer receivedCount;// 已领取量
    private Integer usedCount;    // 已使用量
    private Integer perUserLimit; // 每人限领
    private LocalDateTime startTime; // 有效期开始
    private LocalDateTime endTime;   // 有效期结束
    private Integer status;       // 1=启用 0=禁用
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}