import { adminRequest } from './request'

export function loginAdmin(data) {
  return adminRequest({
    url: '/admin/employee/login',
    method: 'post',
    data
  })
}

export function getAdminProfile() {
  return adminRequest({
    url: '/admin/employee/me',
    method: 'get'
  })
}

export function pageSpu(params) {
  return adminRequest({
    url: '/admin/spu/page',
    method: 'get',
    params
  })
}

export function getAdminSpu(id) {
  return adminRequest({
    url: `/admin/spu/${id}`,
    method: 'get'
  })
}

export function saveSpu(data) {
  return adminRequest({
    url: '/admin/spu',
    method: 'post',
    data
  })
}

export function updateSpu(data) {
  return adminRequest({
    url: '/admin/spu',
    method: 'put',
    data
  })
}

export function deleteSpu(id) {
  return adminRequest({
    url: `/admin/spu/${id}`,
    method: 'delete'
  })
}

export function setSpuStatus(id, status) {
  return adminRequest({
    url: `/admin/spu/status/${status}`,
    method: 'post',
    params: { id }
  })
}

export function batchSetSpuStatus(ids, status) {
  return adminRequest({
    url: `/admin/spu/batch-status/${status}`,
    method: 'post',
    data: ids
  })
}

export function pageCategory(params) {
  return adminRequest({
    url: '/admin/category/page',
    method: 'get',
    params
  })
}

export function listAdminCategory(type) {
  return adminRequest({
    url: '/admin/category/list',
    method: 'get',
    params: type ? { type } : undefined
  })
}

export function saveCategory(data) {
  return adminRequest({
    url: '/admin/category',
    method: 'post',
    data
  })
}

export function updateCategory(data) {
  return adminRequest({
    url: '/admin/category',
    method: 'put',
    data
  })
}

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

export function setCategoryStatus(id, status) {
  return adminRequest({
    url: `/admin/category/status/${status}`,
    method: 'post',
    params: { id }
  })
}

export function listRagSyncPending(params) {
  return adminRequest({
    url: '/admin/rag-sync/pending',
    method: 'get',
    params
  })
}

export function listRagSyncAll(params) {
  return adminRequest({
    url: '/admin/rag-sync/list',
    method: 'get',
    params
  })
}

export function retryRagSyncOne(id) {
  return adminRequest({
    url: `/admin/rag-sync/retry/${id}`,
    method: 'post'
  })
}

export function processAllRagSync() {
  return adminRequest({
    url: '/admin/rag-sync/process-all',
    method: 'post'
  })
}

export function getRagSyncStats() {
  return adminRequest({
    url: '/admin/rag-sync/stats',
    method: 'get'
  })
}

export function pageEmployee(params) {
  return adminRequest({
    url: '/admin/employee/page',
    method: 'get',
    params
  })
}

export function saveEmployee(data) {
  return adminRequest({
    url: '/admin/employee',
    method: 'post',
    data
  })
}

export function updateEmployee(data) {
  return adminRequest({
    url: '/admin/employee',
    method: 'put',
    data
  })
}

export function getEmployee(id) {
  return adminRequest({
    url: `/admin/employee/${id}`,
    method: 'get'
  })
}

export function setEmployeeStatus(id, status) {
  return adminRequest({
    url: `/admin/employee/status/${status}`,
    method: 'post',
    params: { id }
  })
}

export function getDashboardStats() {
  return adminRequest({
    url: '/admin/dashboard/stats',
    method: 'get'
  })
}

export function pageOrder(params) {
  return adminRequest({
    url: '/admin/order/page',
    method: 'get',
    params
  })
}

export function shipOrder(id, data = {}) {
  return adminRequest({
    url: `/admin/order/${id}/ship`,
    method: 'post',
    data
  })
}

export function deleteOrder(id) {
  return adminRequest({
    url: `/admin/order/${id}`,
    method: 'delete'
  })
}

// -------- 售后管理 --------
export function pageAfterSale(params) {
  return adminRequest({
    url: '/admin/aftersale/page',
    method: 'get',
    params
  })
}

export function getAdminAfterSale(id) {
  return adminRequest({
    url: `/admin/aftersale/${id}`,
    method: 'get'
  })
}

export function auditAfterSale(data) {
  return adminRequest({
    url: '/admin/aftersale/audit',
    method: 'post',
    data
  })
}

export function confirmRefund(id) {
  return adminRequest({
    url: `/admin/aftersale/${id}/confirm-refund`,
    method: 'post'
  })
}

// ========== 积分管理 ==========
export function listPointsRules() {
  return adminRequest({
    url: '/admin/points/rules',
    method: 'get'
  })
}

export function savePointsRule(data) {
  return adminRequest({
    url: '/admin/points/rules',
    method: 'post',
    data
  })
}

export function deletePointsRule(id) {
  return adminRequest({
    url: `/admin/points/rules/${id}`,
    method: 'delete'
  })
}

export function adjustPoints(data) {
  return adminRequest({
    url: '/admin/points/adjust',
    method: 'post',
    data
  })
}

export function pagePointsProducts(params) {
  return adminRequest({
    url: '/admin/points/products',
    method: 'get',
    params
  })
}

export function getAdminPointsProduct(id) {
  return adminRequest({
    url: `/admin/points/products/${id}`,
    method: 'get'
  })
}

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

export function deletePointsProduct(id) {
  return adminRequest({
    url: `/admin/points/products/${id}`,
    method: 'delete'
  })
}

// ========== 文件上传 ==========
/**
 * 上传图片文件到 OSS。
 * @param {File|File[]} files - 单个 File 或 File 数组
 * @param {string} module - 业务模块名，默认 "spu"
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
