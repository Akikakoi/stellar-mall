/**
 * 前后端共享的业务模型类型。
 *
 * 字段以 Java 端 VO/Entity 为 ground truth(stellar-pojo 模块),
 * 前端归一化后的字段(如购物车 CartItem)标注在对应接口注释里。
 * Phase 1 采用"严格字段 + 宽松兜底"策略:
 *   - 视图直接消费的模型(Spu/Sku/CartItem/Order...)尽量严格;
 *   - 后端返回字段多且前端只取少数键的模型(登录响应/知识库文档...)加
 *     [key: string]: any 索引签名,避免过度约束导致误报。
 * 后续可改用 openapi-typescript 从 Knife4j /v3/api-docs 自动生成,替换手写部分。
 */

// =========================== 通用 ===========================

/** 后端统一分页结构(对齐 sky PageResult) */
export interface PageResult<T> {
  total: number
  records: T[]
}

/** 通用分页查询参数 */
export interface PageParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  [key: string]: any
}

// =========================== 商品 SPU / SKU ===========================

/** SKU 库存单元(对齐 Sku.java) */
export interface Sku {
  id: number
  spuId: number
  /** SKU 全名:SPU名 · 规格1 · 规格2 */
  name: string
  /** 规格文本(分号分隔) */
  specs: string
  specsJson?: string
  price: number
  originalPrice?: number
  costPrice?: number
  stock: number
  version?: number
  warnStock?: number
  weightG?: number
  barcode?: string
  image?: string
  sort?: number
  /** 1 在售 0 停售 */
  status?: number
  createTime?: string
  updateTime?: string
}

/** SPU 标准产品单元(对齐 Spu.java,含非 DB 组装字段) */
export interface Spu {
  id: number
  name: string
  subTitle?: string
  brandId?: number
  categoryId?: number
  category2Id?: number
  description?: string
  descriptionMd?: string
  mainImage?: string
  subImages?: string
  saleCount?: number
  commentCount?: number
  totalStock?: number
  skuCount?: number
  minPrice?: number
  maxPrice?: number
  isNew?: number
  isHot?: number
  sort?: number
  /** 1 上架 0 下架 */
  status?: number
  onShelfTime?: string
  offShelfTime?: string
  createTime?: string
  updateTime?: string
  /** 详情/列表接口返回时可选填充 */
  skuList?: Sku[]
  /** skuList 的别名(前端常用 skus) */
  skus?: Sku[]
  /** 品牌名(列表联查返回) */
  brandName?: string
  /** 分类名(列表联查返回) */
  categoryName?: string
}

/** 搜索建议(对齐 SearchSuggestVO) */
export interface SearchSuggest {
  completions: string[]
  correction?: string
}

/** 搜索聚合桶(对齐 BucketVO) */
export interface Bucket {
  key?: string
  docCount?: number
}

/** 搜索聚合(对齐 AggregationVO) */
export interface Aggregation {
  categories?: Bucket[]
  priceRanges?: Bucket[]
}

/** 搜索分页结果(对齐 SearchResultVO,records 为商品列表) */
export interface SearchResult {
  total: number
  records: Spu[]
  /** spuId → 高亮片段列表(含 <em> 标签的 HTML 片段) */
  highlights: Record<number, string[]>
  aggregations?: Aggregation
}

// =========================== 分类 ===========================

/** 商品分类(对齐 CategoryVO) */
export interface Category {
  id: number
  name: string
  /** 1 商品 2 售后 */
  type?: number
  sort?: number
  /** 1 启用 0 禁用 */
  status?: number
  createTime?: string
  updateTime?: string
  /** 该分类下的关联商品数(0 表示无商品) */
  spuCount?: number
  /** 子分类列表(树形结构) */
  children?: Category[]
}

/** 删除分类前预校验结果 */
export interface CategoryDeletable {
  deletable: boolean
  linkedSpuCount?: number
  childCount?: number
  reason?: string
}

// =========================== 用户 / 登录 ===========================

/** 登录请求体 */
export interface LoginPayload {
  username?: string
  password?: string
  email?: string
  captchaId?: string
  captchaCode?: string
}

/**
 * 登录响应(对齐 MallUserLoginVO,兼容后端历史字段)。
 * 保留索引签名:store 里有多字段兜底读取(res.userId || res.USER_ID || res.id)。
 */
export interface LoginResult {
  userId?: number
  token?: string
  refreshToken?: string
  nickname?: string
  name?: string
  phone?: string
  role?: string
  id?: number
  [key: string]: any
}

/**
 * C 端用户信息(对齐 MallUserVO)。
 * 保留索引签名:store 里有 res.role / res.ROLE 等后端附加字段的兜底读取。
 */
export interface UserInfo {
  id?: number
  phone?: string
  email?: string
  nickname?: string
  /** 1 正常 0 锁定 */
  status?: number
  [key: string]: any
}

/** 管理员登录响应(对齐 EmployeeLoginVO) */
export interface EmployeeLoginResult {
  empId?: number
  token?: string
  refreshToken?: string
  username?: string
  name?: string
  [key: string]: any
}

/** 管理员信息 */
export interface Employee {
  id?: number
  empId?: number
  username?: string
  name?: string
  phone?: string
  /** 1 正常 0 禁用 */
  status?: number
  [key: string]: any
}

// =========================== 购物车 ===========================

/** 购物车服务端原始行(对齐 CartVO,兼容前端冗余字段) */
export interface CartRowVO {
  id?: number
  cartId?: number
  skuId?: number
  spuId?: number
  /** 数量(后端字段) */
  qty?: number
  /** 是否勾选 1/0(后端字段) */
  checked?: number | string | boolean
  spuName?: string
  spuImage?: string
  skuName?: string
  skuSpecs?: string
  skuPrice?: number | string
  skuImage?: string
  /** 以下为前端兜底读取的冗余字段 */
  name?: string
  image?: string
  pic?: string
  price?: number | string
  quantity?: number
  number?: number
  serviceFee?: number | string
  extraAmount?: number | string
  services?: unknown[]
}

/**
 * 购物车前端归一化条目(cart store load() 组装后的形态)。
 * 字段说明见 stores/cart.ts。
 */
export interface CartItem {
  id: number
  skuId: number
  spuId?: number
  name: string
  image: string
  price: number
  quantity: number
  checked: boolean
  services: unknown[]
  serviceFee: number
  /** 归一化时保留的原始 SKU 展示字段 */
  skuName?: string
  skuSpecs?: string
  skuImage?: string
}

// =========================== 订单 ===========================

/** 订单明细子项(对齐 MallOrderItemVO) */
export interface OrderItem {
  id: number
  spuId: number
  skuId: number
  spuName: string
  skuSpecs?: string
  price: number
  qty: number
  subtotal: number
  /** 额外费用(保障服务等),单位元 */
  extraAmount?: number
  /** 商品主图 */
  pic?: string
}

/** C 端订单(对齐 MallOrderVO,含明细) */
export interface Order {
  id: number
  orderNo: string
  /** PENDING / PAID / CANCELLED / COMPLETED */
  status: string
  /** 前端兼容数字状态:0已取消 1待付款 2待发货 3待收货 4待评价 5已完成 6退款中 */
  statusCode: number
  totalAmount: number
  payAmount: number
  address?: string
  /** 收货人快照 */
  consignee?: string
  /** 手机号快照 */
  phone?: string
  payMethod?: number
  remark?: string
  pointsDeducted?: number
  pointsAmount?: number
  createTime: string
  items?: OrderItem[]
}

// =========================== 收货地址 ===========================

/** 收货地址(对齐 AddressVO) */
export interface Address {
  id: number
  userId?: number
  consignee: string
  phone: string
  province?: string
  city?: string
  district?: string
  detail: string
  /** 是否默认地址 1=默认 0=非默认 */
  isDefault?: number
  createTime?: string
  updateTime?: string
}

// =========================== 钱包 ===========================

/** 钱包(对齐 WalletVO) */
export interface Wallet {
  id?: number
  /** 可用余额 */
  balance: number
  /** 累计充值 */
  totalRecharge?: number
  /** 累计消费 */
  totalSpent?: number
}

/** 钱包交易流水(对齐 WalletTransactionVO) */
export interface WalletTransaction {
  id: number
  /** 1 充值 2 消费 3 退款 */
  type: number
  typeText?: string
  amount: number
  balanceAfter?: number
  channel?: string
  remark?: string
  createTime?: string
}

// =========================== 积分 ===========================

/** 用户积分汇总(对齐 UserPointsVO) */
export interface UserPoints {
  id?: number
  totalPoints?: number
  availablePoints?: number
  frozenPoints?: number
  totalEarned?: number
  totalSpent?: number
  /** 积分汇率:每元需要的积分数(100 积分 = 1 元) */
  exchangeRate?: number
}

/** 积分流水(对齐 PointsRecordVO) */
export interface PointsRecord {
  id: number
  /** 1获得 2消费 3过期 4管理员调整 */
  type: number
  typeText?: string
  points: number
  balanceAfter?: number
  bizType?: string
  description?: string
  expiredTime?: string
  createTime?: string
}

/** 积分兑换记录(对齐 PointsRedeemVO) */
export interface PointsRedeem {
  id?: number
  [key: string]: any
}

/** 积分商城商品(对齐 PointsProductVO) */
export interface PointsProduct {
  id: number
  name: string
  productType?: string
  productTypeText?: string
  pointsPrice: number
  stock: number
  imageUrl?: string
  description?: string
  couponId?: number
  status?: number
  sortOrder?: number
  createTime?: string
}

/** 签到结果(对齐 CheckinVO) */
export interface CheckinResult {
  success: boolean
  pointsEarned?: number
  message?: string
}

// =========================== 售后 ===========================

/** 售后单(对齐 AfterSaleVO) */
export interface AfterSale {
  id: number
  orderId: number
  orderNo?: string
  skuId?: number
  skuSpecs?: string
  spuId?: number
  spuName?: string
  spuImage?: string
  qty?: number
  userId?: number
  /** 1 仅退款 2 退货退款 3 换货 */
  type: number
  typeText?: string
  /** 1-7 */
  status: number
  statusText?: string
  reason?: string
  detail?: string
  amount?: number
  images?: string
  auditRemark?: string
  auditTime?: string
  returnTracking?: string
  refundTime?: string
  createTime?: string
}

// =========================== 收藏 ===========================

/** 收藏夹条目(对齐 FavoriteVO) */
export interface Favorite {
  id: number
  spuId: number
  spuName?: string
  spuImage?: string
  minPrice?: number
  createTime?: string
}

// =========================== 消息 ===========================

/** 站内消息(字段以实际接口返回为准,宽松兜底) */
export interface MallMessage {
  id: number
  title?: string
  content?: string
  isRead?: number
  createTime?: string
  [key: string]: any
}

// =========================== RAG ===========================

/** RAG 服务统一响应包裹(ragRequest 拦截器对 code===0 返回整个 envelope) */
export interface RagResponse<T> {
  code: number
  message: string
  data: T
}

/** RAG 对话 */
export interface Conversation {
  id: number
  title: string
  createdAt?: string
  updatedAt?: string
  messages?: ChatMessage[]
  [key: string]: any
}

/** RAG 聊天消息 */
export interface ChatMessage {
  id: number
  role: 'user' | 'assistant' | 'system'
  content: string
  sources: unknown[]
  streaming?: boolean
  tokens_used?: number
  latency_ms?: number
  [key: string]: any
}

/** 知识库文档(字段以 RAG 端为准,宽松兜底) */
export interface KbDocument {
  id: number
  filename?: string
  title?: string
  tags?: string
  status?: string
  created_at?: string
  updated_at?: string
  [key: string]: any
}

/** 知识库文档分页(字段以 RAG 端为准,宽松兜底) */
export interface KbPage {
  total?: number
  records?: KbDocument[]
  items?: KbDocument[]
  [key: string]: any
}

// =========================== 管理后台 ===========================

/** 仪表盘统计(字段多且杂,宽松兜底) */
export interface DashboardStats {
  [key: string]: any
}

/** AI 经营日报 */
export interface DailyReport {
  date: string
  report: string
  generatedAt: string
}

/** ChatBI 查询结果 */
export interface ChatBiResult {
  question: string
  sql: string
  title: string
  chartType: string
  xField: string
  yField: string
  columns: unknown[]
  rows: unknown[]
  summary: string
}

/** 积分规则(宽松兜底) */
export interface PointsRule {
  id?: number
  [key: string]: any
}
