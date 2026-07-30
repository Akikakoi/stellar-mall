// 订单状态（与后端 OrderStatus 枚举的前端 code 保持一致）
export const ORDER_STATUS = {
  CANCELLED: 0, // 已取消
  PENDING: 1,   // 待付款
  PAID: 2,      // 待发货
  SHIPPED: 3,   // 待收货
  REVIEWABLE: 4, // 待评价（前端展示态，后端对应 COMPLETED）
  COMPLETED: 5, // 已完成
  REFUNDING: 6, // 退款中
  REFUNDED: 7    // 已退款
}

export const ORDER_STATUS_TEXT = {
  [ORDER_STATUS.CANCELLED]: '已取消',
  [ORDER_STATUS.PENDING]: '待付款',
  [ORDER_STATUS.PAID]: '待发货',
  [ORDER_STATUS.SHIPPED]: '待收货',
  [ORDER_STATUS.REVIEWABLE]: '待评价',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.REFUNDING]: '退款中',
  [ORDER_STATUS.REFUNDED]: '已退款'
}

// 售后状态
export const AFTER_SALE_STATUS = {
  APPLIED: 1,    // 申请中
  AUDITING: 2,   // 商家审核中
  RETURNING: 3,  // 用户退货中
  REFUNDING: 4,  // 退款中
  COMPLETED: 5,  // 已完成
  REJECTED: 6,   // 已拒绝
  CANCELLED: 7   // 已取消
}

export const AFTER_SALE_STATUS_TEXT = {
  [AFTER_SALE_STATUS.APPLIED]: '申请中',
  [AFTER_SALE_STATUS.AUDITING]: '商家审核中',
  [AFTER_SALE_STATUS.RETURNING]: '用户退货中',
  [AFTER_SALE_STATUS.REFUNDING]: '退款中',
  [AFTER_SALE_STATUS.COMPLETED]: '已完成',
  [AFTER_SALE_STATUS.REJECTED]: '已拒绝',
  [AFTER_SALE_STATUS.CANCELLED]: '已取消'
}

export const AFTER_SALE_TYPE = {
  REFUND_ONLY: 1,    // 仅退款
  RETURN_REFUND: 2,  // 退货退款
  EXCHANGE: 3        // 换货
}

export const AFTER_SALE_TYPE_TEXT = {
  [AFTER_SALE_TYPE.REFUND_ONLY]: '仅退款',
  [AFTER_SALE_TYPE.RETURN_REFUND]: '退货退款',
  [AFTER_SALE_TYPE.EXCHANGE]: '换货'
}
