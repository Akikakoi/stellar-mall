/**
 * C 端（商城用户）API 接口模块。
 * 所有请求通过 userRequest 实例发送，自动携带用户 token 和 userId。
 */
import { userRequest } from './request'

// =========================== 用户认证 ===========================

/** 用户登录 */
export function loginUser(data) {
  return userRequest({
    url: '/user/user/login',
    method: 'post',
    data
  })
}

/**
 * 获取图形验证码（E3，公开接口，无需登录）
 * @returns {Promise<{captchaId: string, imageBase64: string}>}
 */
export function getCaptcha() {
  return userRequest({
    url: '/captcha/image',
    method: 'get'
  })
}

/** 获取当前登录用户信息 */
export function getCurrentUser() {
  return userRequest({
    url: '/user/user/me',
    method: 'get'
  })
}

/** 获取用户个人资料 */
export function getUserProfile() {
  return userRequest({
    url: '/user/user/profile',
    method: 'get'
  })
}

/** 更新用户个人资料 */
export function updateUserProfile(data) {
  return userRequest({
    url: '/user/user/profile',
    method: 'put',
    data
  })
}

/** 注销当前账号 */
export function deactivateAccount() {
  return userRequest({
    url: '/user/user/deactivate',
    method: 'post'
  })
}

// =========================== 商品 (SPU) ===========================

/** 分页查询商品列表，支持按名称/分类/状态/价格区间筛选 */
export function listSpu(params) {
  return userRequest({
    url: '/user/spu/page',
    method: 'get',
    params
  })
}

/** 查询商品详情（含 SKU 列表） */
export function getSpu(id) {
  return userRequest({
    url: `/user/spu/${id}`,
    method: 'get'
  })
}

/** 搜索建议（输入框自动补全） */
export function suggestSpu(prefix) {
  return userRequest({
    url: '/user/spu/suggest',
    method: 'get',
    params: { prefix }
  })
}

/** 获取分类列表 */
export function listCategory() {
  return userRequest({
    url: '/user/category/list',
    method: 'get'
  })
}

// =========================== 购物车 ===========================

/** 查询当前用户购物车 */
export function listCart() {
  return userRequest({
    url: '/user/cart',
    method: 'get'
  })
}

/** 加入购物车 */
export function addCart(data) {
  return userRequest({
    url: '/user/cart',
    method: 'post',
    data
  })
}

/** 更新购物车商品数量 */
export function updateCartQty(data) {
  return userRequest({
    url: '/user/cart',
    method: 'put',
    data
  })
}

/** 删除购物车中指定商品 */
export function deleteCart(id) {
  return userRequest({
    url: `/user/cart/${id}`,
    method: 'delete'
  })
}

/** 清空购物车 */
export function clearCart() {
  return userRequest({
    url: '/user/cart/clear',
    method: 'delete'
  })
}

// =========================== 订单 ===========================

/** 提交订单 */
export function submitOrder(data) {
  return userRequest({
    url: '/user/order/submit',
    method: 'post',
    data
  })
}

/** 分页查询当前用户订单列表 */
export function listOrder(params) {
  return userRequest({
    url: '/user/order/list',
    method: 'get',
    params
  })
}

/** 查询订单详情 */
export function getOrder(id) {
  return userRequest({
    url: `/user/order/${id}`,
    method: 'get'
  })
}

/**
 * 支付订单
 * @param {number} id - 订单 ID
 * @param {number} payMethod - 支付方式：1-微信 2-支付宝 4-钱包
 */
export function payOrder(id, payMethod) {
  return userRequest({
    url: `/user/order/${id}/pay`,
    method: 'post',
    data: { payMethod }
  })
}

/** 取消订单 */
export function cancelOrder(id, reason) {
  return userRequest({
    url: `/user/order/${id}/cancel`,
    method: 'post',
    data: { reason }
  })
}

/** 确认收货 */
export function confirmOrder(id) {
  return userRequest({
    url: `/user/order/${id}/confirm`,
    method: 'post'
  })
}

/** 删除订单（软删除，仅已取消/已完成/已退款状态可删除） */
export function deleteUserOrder(id) {
  return userRequest({
    url: `/user/order/${id}`,
    method: 'delete'
  })
}

// =========================== 收藏夹 ===========================

/** 添加收藏 */
export function addFavorite(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'post'
  })
}

/** 取消收藏 */
export function removeFavorite(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'delete'
  })
}

/** 查询单个商品是否已收藏 */
export function isFavorited(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'get'
  })
}

/** 获取收藏列表 */
export function listFavorites() {
  return userRequest({
    url: '/user/favorite',
    method: 'get'
  })
}

/** 批量查询收藏状态 */
export function batchCheckFavorites(spuIds) {
  return userRequest({
    url: '/user/favorite/batch-check',
    method: 'post',
    data: { spuIds }
  })
}

// =========================== 评价 ===========================

/** 提交商品评价 */
export function submitReview(data) {
  return userRequest({
    url: '/user/review',
    method: 'post',
    data
  })
}

/** 获取评价的评论列表 */
export function getReviewComments(reviewId) {
  return userRequest({
    url: `/user/review/${reviewId}/comments`,
    method: 'get'
  })
}

/** 对评价发表评论 */
export function submitReviewComment(reviewId, content) {
  return userRequest({
    url: `/user/review/${reviewId}/comment`,
    method: 'post',
    data: { content }
  })
}

// =========================== 用户消息 ===========================

/** 分页查询用户消息列表 */
export function listMessages(params) {
  return userRequest({
    url: '/user/message/list',
    method: 'get',
    params
  })
}

/** 获取未读消息数量 */
export function getUnreadCount() {
  return userRequest({
    url: '/user/message/unread-count',
    method: 'get'
  })
}

/** 标记单条消息为已读 */
export function markMessageRead(id) {
  return userRequest({
    url: `/user/message/${id}/read`,
    method: 'put'
  })
}

/** 标记全部消息为已读 */
export function markAllMessagesRead() {
  return userRequest({
    url: '/user/message/read-all',
    method: 'put'
  })
}

// =========================== 售后 ===========================

/** 提交售后申请 */
export function submitAfterSale(data) {
  return userRequest({
    url: '/user/aftersale',
    method: 'post',
    data
  })
}

/** 取消售后申请 */
export function cancelAfterSale(id) {
  return userRequest({
    url: `/user/aftersale/${id}/cancel`,
    method: 'post'
  })
}

/** 提交退货物流单号 */
export function submitReturnTracking(data) {
  return userRequest({
    url: '/user/aftersale/return-tracking',
    method: 'put',
    data
  })
}

/** 查询售后列表 */
export function listAfterSales(params) {
  return userRequest({
    url: '/user/aftersale',
    method: 'get',
    params
  })
}

/** 查询售后详情 */
export function getAfterSale(id) {
  return userRequest({
    url: `/user/aftersale/${id}`,
    method: 'get'
  })
}

/** 按订单 ID 查询售后单 */
export function getAfterSaleByOrder(orderId) {
  return userRequest({
    url: `/user/aftersale/by-order/${orderId}`,
    method: 'get'
  })
}

// =========================== 钱包 ===========================

/** 获取钱包信息 */
export function getWallet() {
  return userRequest({
    url: '/user/wallet',
    method: 'get'
  })
}

/** 钱包充值 */
export function rechargeWallet(data) {
  return userRequest({
    url: '/user/wallet/recharge',
    method: 'post',
    data
  })
}

/** 查询钱包交易流水 */
export function listWalletTransactions(params) {
  return userRequest({
    url: '/user/wallet/transactions',
    method: 'get',
    params
  })
}

// =========================== 收货地址 ===========================

/** 获取地址列表 */
export function listAddresses() {
  return userRequest({
    url: '/user/address/list',
    method: 'get'
  })
}

/** 查询单个地址 */
export function getAddress(id) {
  return userRequest({
    url: `/user/address/${id}`,
    method: 'get'
  })
}

/** 新增地址 */
export function saveAddress(data) {
  return userRequest({
    url: '/user/address',
    method: 'post',
    data
  })
}

/** 更新地址 */
export function updateAddress(data) {
  return userRequest({
    url: '/user/address',
    method: 'put',
    data
  })
}

/** 删除地址 */
export function deleteAddress(id) {
  return userRequest({
    url: `/user/address/${id}`,
    method: 'delete'
  })
}

/** 设为默认地址 */
export function setDefaultAddress(id) {
  return userRequest({
    url: `/user/address/${id}/default`,
    method: 'post'
  })
}

// =========================== 积分系统 ===========================

/** 获取当前用户积分 */
export function getUserPoints() {
  return userRequest({
    url: '/user/points',
    method: 'get'
  })
}

/** 分页查询积分变动记录 */
export function listPointsRecords(params) {
  return userRequest({
    url: '/user/points/records',
    method: 'get',
    params
  })
}

/** 分页查询积分兑换记录 */
export function listPointsRedemptions(params) {
  return userRequest({
    url: '/user/points/redemptions',
    method: 'get',
    params
  })
}

/** 每日签到 */
export function checkin() {
  return userRequest({
    url: '/user/points/checkin',
    method: 'post'
  })
}

/** 获取签到日期列表 */
export function getCheckinDates() {
  return userRequest({
    url: '/user/points/checkin-dates',
    method: 'get'
  })
}

/** 获取积分商城商品列表 */
export function listPointsProducts() {
  return userRequest({
    url: '/user/points/products',
    method: 'get'
  })
}

/** 积分兑换商品 */
export function redeemPoints(data) {
  return userRequest({
    url: '/user/points/redeem',
    method: 'post',
    data
  })
}
