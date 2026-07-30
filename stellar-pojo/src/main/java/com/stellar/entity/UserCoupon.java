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
public class UserCoupon implements Serializable {
    private Long id;
    private Long userId;
    private Long couponId;
    private Integer status;       // 1=未使用 2=已使用 3=已过期
    private Long orderId;         // 使用订单ID
    private LocalDateTime usedTime;
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;

    // Non-DB fields for display
    private String couponName;
    private Integer couponType;
    private BigDecimal conditionAmount;
    private BigDecimal discountAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}