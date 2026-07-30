import { userRequest } from './request'

export function loginUser(data) {
  return userRequest({
    url: '/user/user/login',
    method: 'post',
    data
  })
}

export function registerUser(data) {
  return userRequest({
    url: '/user/user/login',
    method: 'post',
    data
  })
}

export function getCurrentUser() {
  return userRequest({
    url: '/user/user/me',
    method: 'get'
  })
}

export function listSpu(params) {
  return userRequest({
    url: '/user/spu/page',
    method: 'get',
    params
  })
}

export function getSpu(id) {
  return userRequest({
    url: `/user/spu/${id}`,
    method: 'get'
  })
}

export function listCategory() {
  return userRequest({
    url: '/user/category/list',
    method: 'get'
  })
}

export function listCart() {
  return userRequest({
    url: '/user/cart',
    method: 'get'
  })
}

export function addCart(data) {
  return userRequest({
    url: '/user/cart',
    method: 'post',
    data
  })
}

export function updateCartQty(data) {
  return userRequest({
    url: '/user/cart',
    method: 'put',
    data
  })
}

export function deleteCart(id) {
  return userRequest({
    url: `/user/cart/${id}`,
    method: 'delete'
  })
}

export function clearCart() {
  return userRequest({
    url: '/user/cart/clear',
    method: 'delete'
  })
}

export function submitOrder(data) {
  console.log('[mall.js] submitOrder called, data:', JSON.stringify(data))
  return userRequest({
    url: '/user/order/submit',
    method: 'post',
    data
  })
}

export function listOrder(params) {
  return userRequest({
    url: '/user/order/list',
    method: 'get',
    params
  })
}

export function getOrder(id) {
  return userRequest({
    url: `/user/order/${id}`,
    method: 'get'
  })
}

export function payOrder(id, payMethod) {
  return userRequest({
    url: `/user/order/${id}/pay`,
    method: 'post',
    data: { payMethod }
  })
}

export function cancelOrder(id, reason) {
  return userRequest({
    url: `/user/order/${id}/cancel`,
    method: 'post',
    data: { reason }
  })
}

export function confirmOrder(id) {
  return userRequest({
    url: `/user/order/${id}/confirm`,
    method: 'post'
  })
}

export function deleteUserOrder(id) {
  return userRequest({
    url: `/user/order/${id}`,
    method: 'delete'
  })
}

export function getUserProfile() {
  return userRequest({
    url: '/user/user/profile',
    method: 'get'
  })
}

export function updateUserProfile(data) {
  return userRequest({
    url: '/user/user/profile',
    method: 'put',
    data
  })
}

// ========== 收藏夹 ==========
export function addFavorite(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'post'
  })
}

export function removeFavorite(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'delete'
  })
}

export function isFavorited(spuId) {
  return userRequest({
    url: `/user/favorite/${spuId}`,
    method: 'get'
  })
}

export function listFavorites() {
  return userRequest({
    url: '/user/favorite',
    method: 'get'
  })
}

export function batchCheckFavorites(spuIds) {
  return userRequest({
    url: '/user/favorite/batch-check',
    method: 'post',
    data: { spuIds }
  })
}

// ========== 评价 ==========
export function submitReview(data) {
  return userRequest({
    url: '/user/review',
    method: 'post',
    data
  })
}

// 评价评论
export function getReviewComments(reviewId) {
  return userRequest({
    url: `/user/review/${reviewId}/comments`,
    method: 'get'
  })
}

export function submitReviewComment(reviewId, content) {
  return userRequest({
    url: `/user/review/${reviewId}/comment`,
    method: 'post',
    data: { content }
  })
}

// -------- 用户消息 --------
export function listMessages(params) {
  return userRequest({
    url: '/user/message/list',
    method: 'get',
    params
  })
}

export function getUnreadCount() {
  return userRequest({
    url: '/user/message/unread-count',
    method: 'get'
  })
}

export function markMessageRead(id) {
  return userRequest({
    url: `/user/message/${id}/read`,
    method: 'put'
  })
}

export function markAllMessagesRead() {
  return userRequest({
    url: '/user/message/read-all',
    method: 'put'
  })
}

// -------- 售后 --------
export function submitAfterSale(data) {
  return userRequest({
    url: '/user/aftersale',
    method: 'post',
    data
  })
}

export function cancelAfterSale(id) {
  return userRequest({
    url: `/user/aftersale/${id}/cancel`,
    method: 'post'
  })
}

export function submitReturnTracking(data) {
  return userRequest({
    url: '/user/aftersale/return-tracking',
    method: 'put',
    data
  })
}

export function listAfterSales(params) {
  return userRequest({
    url: '/user/aftersale',
    method: 'get',
    params
  })
}

export function getAfterSale(id) {
  return userRequest({
    url: `/user/aftersale/${id}`,
    method: 'get'
  })
}

export function getAfterSaleByOrder(orderId) {
  return userRequest({
    url: `/user/aftersale/by-order/${orderId}`,
    method: 'get'
  })
}

// -------- 钱包 --------
export function getWallet() {
  return userRequest({
    url: '/user/wallet',
    method: 'get'
  })
}

export function rechargeWallet(data) {
  return userRequest({
    url: '/user/wallet/recharge',
    method: 'post',
    data
  })
}

export function listWalletTransactions(params) {
  return userRequest({
    url: '/user/wallet/transactions',
    method: 'get',
    params
  })
}

// ========== 收货地址 ==========
export function listAddresses() {
  return userRequest({
    url: '/user/address/list',
    method: 'get'
  })
}

export function getAddress(id) {
  return userRequest({
    url: `/user/address/${id}`,
    method: 'get'
  })
}

export function saveAddress(data) {
  return userRequest({
    url: '/user/address',
    method: 'post',
    data
  })
}

export function updateAddress(data) {
  return userRequest({
    url: '/user/address',
    method: 'put',
    data
  })
}

export function deleteAddress(id) {
  return userRequest({
    url: `/user/address/${id}`,
    method: 'delete'
  })
}

export function setDefaultAddress(id) {
  return userRequest({
    url: `/user/address/${id}/default`,
    method: 'post'
  })
}

// ========== 积分系统 ==========
export function getUserPoints() {
  return userRequest({
    url: '/user/points',
    method: 'get'
  })
}

export function listPointsRecords(params) {
  return userRequest({
    url: '/user/points/records',
    method: 'get',
    params
  })
}

export function listPointsRedemptions(params) {
  return userRequest({
    url: '/user/points/redemptions',
    method: 'get',
    params
  })
}

export function checkin() {
  return userRequest({
    url: '/user/points/checkin',
    method: 'post'
  })
}

export function getCheckinDates() {
  return userRequest({
    url: '/user/points/checkin-dates',
    method: 'get'
  })
}

export function listPointsProducts() {
  return userRequest({
    url: '/user/points/products',
    method: 'get'
  })
}

export function redeemPoints(data) {
  return userRequest({
    url: '/user/points/redeem',
    method: 'post',
    data
  })
}

// ========== 搜索建议 ==========
export function suggestSpu(prefix) {
  return userRequest({
    url: '/user/spu/suggest',
    method: 'get',
    params: { prefix }
  })
}
