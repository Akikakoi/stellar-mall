/**
 * 管理后台 API 接口模块。
 * 所有请求通过 adminRequest 实例发送，自动携带管理员 token 和 empId。
 */
import { adminRequest } from './request'

// =========================== 管理员认证 ===========================

/** 管理员登录 */
export function loginAdmin(data) {
  return adminRequest({
    url: '/admin/employee/login',
    method: 'post',
    data
  })
}

/** 获取当前管理员信息 */
export function getAdminProfile() {
  return adminRequest({
    url: '/admin/employee/me',
    method: 'get'
  })
}

// =========================== 商品管理 (SPU) ===========================

/** 分页查询商品列表（支持 ES 中文分词搜索） */
export function pageSpu(params) {
  return adminRequest({
    url: '/admin/spu/page',
    method: 'get',
    params
  })
}

/** 查询商品详情（含 SKU） */
export function getAdminSpu(id) {
  return adminRequest({
    url: `/admin/spu/${id}`,
    method: 'get'
  })
}

/** 新增商品（含嵌套 SKU） */
export function saveSpu(data) {
  return adminRequest({
    url: '/admin/spu',
    method: 'post',
    data
  })
}

/** 更新商品（传 SKU 则覆盖，不传则保留） */
export function updateSpu(data) {
  return adminRequest({
    url: '/admin/spu',
    method: 'put',
    data
  })
}

/** 删除商品（同步删除关联 SKU） */
export function deleteSpu(id) {
  return adminRequest({
    url: `/admin/spu/${id}`,
    method: 'delete'
  })
}

/** 单个商品上下架：status=1 上架 / 0 下架 */
export function setSpuStatus(id, status) {
  return adminRequest({
    url: `/admin/spu/status/${status}`,
    method: 'post',
    params: { id }
  })
}

/** 批量上下架 */
export function batchSetSpuStatus(ids, status) {
  return adminRequest({
    url: `/admin/spu/batch-status/${status}`,
    method: 'post',
    data: ids
  })
}

// =========================== 分类管理 ===========================

/** 分页查询分类 */
export function pageCategory(params) {
  return adminRequest({
    url: '/admin/category/page',
    method: 'get',
    params
  })
}

/** 获取分类列表（可按 type 筛选） */
export function listAdminCategory(type) {
  return adminRequest({
    url: '/admin/category/list',
    method: 'get',
    params: type ? { type } : undefined
  })
}

/** 新增分类 */
export function saveCategory(data) {
  return adminRequest({
    url: '/admin/category',
    method: 'post',
    data
  })
}

/** 更新分类 */
export function updateCategory(data) {
  return adminRequest({
    url: '/admin/category',
    method: 'put',
    data
  })
}

/** 删除分类 */
export function deleteCategory(id) {
  return adminRequest({
    url: `/admin/category/${id}`,
    method: 'delete'
  })
}

/**
 * 删除分类前的预校验接口。
 * 返回体 { data: { deletable, linkedSpuCount, childCount, reason } }：
 *   deletable=false 时直接禁止删除，reason 会携带「该分类下还有 N 个商品，禁止删除...」等含具体数量的提示文案；
 *   deletable=true 时再进入二次确认框，调用 deleteCategory。
 */
export function checkCategoryDeletable(id) {
  return adminRequest({
    url: `/admin/category/${id}/deletable`,
    method: 'get'
  })
}

/** 启用/禁用分类 */
export function setCategoryStatus(id, status) {
  return adminRequest({
    url: `/admin/category/status/${status}`,
    method: 'post',
    params: { id }
  })
}

// =========================== RAG 同步 ===========================

/** 查询待同步记录 */
export function listRagSyncPending(params) {
  return adminRequest({
    url: '/admin/rag-sync/pending',
    method: 'get',
    params
  })
}

/** 查询全部同步记录 */
export function listRagSyncAll(params) {
  return adminRequest({
    url: '/admin/rag-sync/list',
    method: 'get',
    params
  })
}

/** 重试单条同步 */
export function retryRagSyncOne(id) {
  return adminRequest({
    url: `/admin/rag-sync/retry/${id}`,
    method: 'post'
  })
}

/** 批量处理全部待同步记录 */
export function processAllRagSync() {
  return adminRequest({
    url: '/admin/rag-sync/process-all',
    method: 'post'
  })
}

/** 获取同步统计信息 */
export function getRagSyncStats() {
  return adminRequest({
    url: '/admin/rag-sync/stats',
    method: 'get'
  })
}

// =========================== 员工管理 ===========================

/** 分页查询员工 */
export function pageEmployee(params) {
  return adminRequest({
    url: '/admin/employee/page',
    method: 'get',
    params
  })
}

/** 新增员工 */
export function saveEmployee(data) {
  return adminRequest({
    url: '/admin/employee',
    method: 'post',
    data
  })
}

/** 更新员工 */
export function updateEmployee(data) {
  return adminRequest({
    url: '/admin/employee',
    method: 'put',
    data
  })
}

/** 查询员工详情 */
export function getEmployee(id) {
  return adminRequest({
    url: `/admin/employee/${id}`,
    method: 'get'
  })
}

/** 启用/禁用员工 */
export function setEmployeeStatus(id, status) {
  return adminRequest({
    url: `/admin/employee/status/${status}`,
    method: 'post',
    params: { id }
  })
}

// =========================== 仪表盘 ===========================

/** 获取仪表盘统计数据 */
export function getDashboardStats() {
  return adminRequest({
    url: '/admin/dashboard/stats',
    method: 'get'
  })
}

// =========================== 订单管理 ===========================

/** 分页查询订单 */
export function pageOrder(params) {
  return adminRequest({
    url: '/admin/order/page',
    method: 'get',
    params
  })
}

/** 发货（填写快递单号） */
export function shipOrder(id, data = {}) {
  return adminRequest({
    url: `/admin/order/${id}/ship`,
    method: 'post',
    data
  })
}

/** 删除订单 */
export function deleteOrder(id) {
  return adminRequest({
    url: `/admin/order/${id}`,
    method: 'delete'
  })
}

// =========================== 售后管理 ===========================

/** 分页查询售后单 */
export function pageAfterSale(params) {
  return adminRequest({
    url: '/admin/aftersale/page',
    method: 'get',
    params
  })
}

/** 查询售后详情 */
export function getAdminAfterSale(id) {
  return adminRequest({
    url: `/admin/aftersale/${id}`,
    method: 'get'
  })
}

/** 审核售后单（通过/拒绝） */
export function auditAfterSale(data) {
  return adminRequest({
    url: '/admin/aftersale/audit',
    method: 'post',
    data
  })
}

/** 确认退款 */
export function confirmRefund(id) {
  return adminRequest({
    url: `/admin/aftersale/${id}/confirm-refund`,
    method: 'post'
  })
}

// =========================== 积分管理 ===========================

/** 获取积分规则列表 */
export function listPointsRules() {
  return adminRequest({
    url: '/admin/points/rules',
    method: 'get'
  })
}

/** 新增或更新积分规则 */
export function savePointsRule(data) {
  return adminRequest({
    url: '/admin/points/rules',
    method: 'post',
    data
  })
}

/** 删除积分规则 */
export function deletePointsRule(id) {
  return adminRequest({
    url: `/admin/points/rules/${id}`,
    method: 'delete'
  })
}

/** 手动调整用户积分 */
export function adjustPoints(data) {
  return adminRequest({
    url: '/admin/points/adjust',
    method: 'post',
    data
  })
}

/** 分页查询积分商品 */
export function pagePointsProducts(params) {
  return adminRequest({
    url: '/admin/points/products',
    method: 'get',
    params
  })
}

/** 查询积分商品详情 */
export function getAdminPointsProduct(id) {
  return adminRequest({
    url: `/admin/points/products/${id}`,
    method: 'get'
  })
}

/** 新增或更新积分商品（有 id 则更新，无 id 则新增） */
export function savePointsProduct(data) {
  if (data.id) {
    return adminRequest({
      url: '/admin/points/products',
      method: 'put',
      data
    })
  }
  return adminRequest({
    url: '/admin/points/products',
    method: 'post',
    data
  })
}

/** 删除积分商品 */
export function deletePointsProduct(id) {
  return adminRequest({
    url: `/admin/points/products/${id}`,
    method: 'delete'
  })
}

// =========================== 文件上传 ===========================

/**
 * 上传图片文件到 OSS。
 * @param {File|File[]} files - 单个 File 或 File 数组
 * @param {string} [module='spu'] - 业务模块名，默认 "spu"
 * @returns {Promise<string[]>} OSS URL 列表
 */
export function uploadImages(files, module = 'spu') {
  const formData = new FormData()
  const fileArr = Array.isArray(files) ? files : [files]
  fileArr.forEach(f => formData.append('files', f))
  formData.append('module', module)
  return adminRequest({
    url: '/admin/common/upload',
    method: 'post',
    data: formData
  })
}
