<template>
  <div class="mgmt-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">商品管理</span>
        <div class="panel-actions">
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="handleExport" :loading="exporting">导出 Excel</el-button>
          <el-button type="primary" @click="openImport">导入 Excel</el-button>
          <el-button type="primary" @click="openDialog('create')">+ 新增商品</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <!-- 输入即查（400ms 防抖）；回车立即查。不能绑 :disabled="loading"，
             否则每敲一个字触发加载就会失焦、打不了下一个字 -->
        <el-input v-model="query.name" placeholder="名称搜索，输入即查" style="width: 240px" clearable @keyup.enter="onQueryClick" />
        <el-select v-model="query.categoryId" placeholder="分类" clearable style="width: 160px;" :disabled="loading">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px;" :disabled="loading">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <!-- ===== 排序控件 ===== -->
        <span style="color: var(--text-secondary); font-size:13px; white-space:nowrap;">排序：</span>
        <el-select v-model="query.sortBy" placeholder="排序字段" clearable style="width:130px;" :disabled="loading" @change="onSortByChange">
          <el-option label="创建时间" value="createTime" />
          <el-option label="商品名称" value="name" />
        </el-select>
        <el-select v-model="query.sortOrder" placeholder="排序方向" clearable style="width:110px;" :disabled="loading || !query.sortBy" @change="onSortChange">
          <el-option label="升序  ↑" value="asc" />
          <el-option label="降序  ↓" value="desc" />
        </el-select>

        <el-button type="primary" :disabled="loading" @click="onQueryClick">查询</el-button>
      </div>

      <!-- 批量操作工具栏 -->
      <Transition name="toolbar-slide">
        <div v-if="selectedRows.length > 0" class="batch-toolbar">
          <span class="batch-tip">已选择 {{ selectedRows.length }} 件商品</span>
          <el-button type="warning" size="small" @click="handleBatchShelfOff">批量下架</el-button>
          <el-button type="success" size="small" @click="handleBatchShelfOn">批量上架</el-button>
        </div>
      </Transition>

      <el-table :data="records" v-loading="loading" empty-text="暂无数据" style="width: 100%;"
        @selection-change="handleSelectionChange" :row-class-name="tableRowClassName">
        
        <el-table-column type="selection" width="45" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image :src="row.mainImage || row.image || row.pic" :preview-src-list="[row.mainImage || row.image || row.pic]" preview-teleported hide-on-click-modal style="width: 48px; height: 48px; border-radius: var(--radius-sm);" fit="cover">
              <template #error><div class="img-placeholder">N/A</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" min-width="200">
          <template #header>
            <span class="col-header-sort">
              商品名称
              <span v-if="query.sortBy === 'name'" class="sort-arrow" :class="query.sortOrder">
                {{ query.sortOrder === 'asc' ? '▲' : '▼' }}
              </span>
            </span>
          </template>
          <template #default="{ row }">
            <div class="spu-name-cell">
              <div class="spu-name-text" :title="row.name">{{ row.name }}</div>
              <button
                type="button"
                class="spu-review-entry"
                :title="`查看「${row.name}」的评价`"
                @click.stop="openReviewDrawer(row)"
              >
                <el-icon :size="12"><ChatLineSquare /></el-icon>
                评价 {{ row.commentCount || 0 }}
              </button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ Number(row.minPrice ?? row.price ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联文档" width="118">
          <template #default="{ row }">
            <el-tag
              v-if="kbDocOfRow(row)"
              size="small"
              :title="kbDocOfRow(row)!.filename"
              :type="kbDocStatusTag(kbDocOfRow(row)!.status)"
            >{{ kbDocStatusText(kbDocOfRow(row)!.status) }}</el-tag>
            <span v-else class="kb-none-text">未关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              type="warning" link size="small"
              @click="handleShelfOff(row)"
            >下架</el-button>
            <el-button
              v-else
              type="success" link size="small"
              @click="handleShelfOn(row)"
            >上架</el-button>
            <el-button type="primary" link size="small" @click="openDialog('edit', row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="openStockDialog(row)">库存管理</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next, sizes"
          :page-sizes="[10, 20, 50, 100]"
          background
          @current-change="loadPage"
          @size-change="loadPage"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增商品' : '编辑商品'" width="1000px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%;">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <!-- ===== 商品图片 ===== -->
        <el-form-item label="商品图片">
          <div class="image-upload-section">
            <!-- 已上传图片预览 -->
            <div class="image-preview-grid" v-if="uploadedImages.length > 0">
              <div
                v-for="(img, idx) in uploadedImages"
                :key="idx"
                class="image-preview-item"
                :class="{ 'is-main': idx === 0 }"
              >
                <el-image :src="img.url" fit="cover" class="image-preview-img" :preview-src-list="uploadedImages.map(i => i.url)" preview-teleported hide-on-click-modal />
                <div class="image-preview-label">{{ idx === 0 ? '主图' : `副图${idx}` }}</div>
                <div class="image-preview-remove" @click="removeUploadedImage(idx)">
                  <el-icon><Close /></el-icon>
                </div>
              </div>
            </div>
            <!-- 上传按钮 -->
            <div class="image-upload-actions">
              <el-upload
                :show-file-list="false"
                :http-request="doImageUpload"
                accept="image/*"
                drag
              >
                <div class="upload-trigger">
                  <el-icon style="font-size:24px;color:var(--brand-primary)"><Upload /></el-icon>
                  <span>拖拽图片或<em>点击上传</em></span>
                  <span class="upload-hint">支持 JPG / PNG / WebP，单张不超过 10MB</span>
                </div>
              </el-upload>
              <span v-if="imageUploading" style="color:var(--brand-primary);font-size:13px;">上传中...</span>
              <span v-if="uploadedImages.length > 0" style="color:var(--text-muted);font-size:13px;">
                共 {{ uploadedImages.length }} 张（第1张为主图，其余为轮播副图）
              </span>
            </div>
            <!-- 手动输入URL（兜底） -->
            <el-collapse style="margin-top: 8px;">
              <el-collapse-item title="手动输入 URL（可选）" name="urls">
                <el-input v-model="form.mainImage" placeholder="主图 URL" style="margin-bottom: 8px;" @change="initUploadedImages" />
                <el-input v-model="form.subImages" placeholder="副图 URL（分号分隔，如 url1;url2;url3）" @change="initUploadedImages" />
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>

        <!-- ===== 知识库文档上传（创建时必填） ===== -->
        <el-form-item>
          <template #label>
            <span style="color: var(--status-error)">*</span>
            <span>关联文档</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>上传商品参数/说明文档到知识库，AI 助手将基于此文档回答用户咨询。<br/>创建商品时<b>必须上传</b>，支持 .md / .txt / .pdf / .docx。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <!-- 状态同步中 -->
          <div v-if="kbDocLoading" class="kb-doc-state is-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>正在同步知识库文档状态…</span>
          </div>
          <!-- 已有文档预览 -->
          <div v-else-if="kbExistingDoc" class="kb-existing-doc">
            <div class="kb-existing-doc-info">
              <el-icon style="color:var(--brand-primary);font-size:22px;flex-shrink:0;"><Document /></el-icon>
              <div class="kb-existing-doc-detail">
                <div class="kb-existing-doc-name">{{ kbExistingDoc.filename }}</div>
                <div class="kb-existing-doc-meta">
                  <el-tag size="small" :type="kbDocStatusTag(kbExistingDoc.status)">{{ kbDocStatusText(kbExistingDoc.status) }}</el-tag>
                  <el-tag v-if="kbDocSource === 'name'" size="small" type="info" effect="plain">按商品名匹配</el-tag>
                  <template v-if="kbExistingDoc.file_ext">
                    <span class="kb-existing-doc-meta-item">{{ kbExistingDoc.file_ext.toUpperCase() }}</span>
                  </template>
                  <template v-if="kbExistingDoc.file_size">
                    <span class="kb-existing-doc-meta-item">{{ humanFileSize(kbExistingDoc.file_size) }}</span>
                  </template>
                  <template v-if="kbExistingDoc.chunk_count != null">
                    <span class="kb-existing-doc-meta-item">{{ kbExistingDoc.chunk_count }} 个片段</span>
                  </template>
                  <template v-if="kbExistingDoc.created_at">
                    <span class="kb-existing-doc-meta-item">上传于 {{ fmtTime(kbExistingDoc.created_at) }}</span>
                  </template>
                </div>
                <div v-if="kbExistingDoc.status === 'error' && kbExistingDoc.error_msg" class="kb-existing-doc-error">
                  解析失败：{{ kbExistingDoc.error_msg }}
                </div>
              </div>
            </div>
            <el-button type="danger" plain size="small" @click="removeExistingDoc">移除文档</el-button>
          </div>
          <!-- 未关联 -->
          <div v-else class="kb-doc-state is-empty">
            <el-icon><Document /></el-icon>
            <span>{{ dialogMode === 'edit' ? '该商品暂未关联知识库文档，AI 助手无法回答它的咨询' : '新建商品需上传一份产品参数/说明文档' }}</span>
          </div>
          <!-- 分隔线 -->
          <el-divider v-if="kbExistingDoc" content-position="left" style="margin: 12px 0;">
            <span style="font-size:13px;color:var(--text-muted);">上传新文档替换</span>
          </el-divider>
          <div class="kb-upload-row">
            <el-upload
              ref="kbUploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="onKbFileChange"
              :on-remove="onKbFileRemove"
              :file-list="kbFileList"
              accept=".md,.txt,.pdf,.docx"
              drag
              style="flex:1"
            >
              <el-icon style="font-size:24px;color:var(--brand-primary)"><Upload /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 .md / .txt / .pdf / .docx，最大 20MB</div>
              </template>
            </el-upload>
            <div class="kb-params">
              <div class="kb-param-item">
                <span class="kb-param-label">切分长度</span>
                <el-input-number v-model="kbChunkSize" :min="128" :max="4096" :step="64" size="small" style="width:120px" />
              </div>
              <div class="kb-param-item">
                <span class="kb-param-label">重叠字符</span>
                <el-input-number v-model="kbChunkOverlap" :min="0" :max="1024" :step="16" size="small" style="width:120px" />
              </div>
              <div class="kb-param-item">
                <span class="kb-param-label">标签</span>
                <el-input v-model="kbTags" size="small" placeholder="逗号分隔，如：手机,旗舰" style="width:180px" />
              </div>
              <el-button size="small" :disabled="!kbFile" :loading="kbPreviewing" @click="doKbPreview">
                <el-icon v-if="!kbPreviewing"><Search /></el-icon> 预览切分
              </el-button>
            </div>
          </div>
          <div v-if="kbPreviewResult != null" class="kb-preview-result">
            <div class="kb-preview-head">
              <span>共 {{ kbPreviewResult.chunk_count }} 个片段</span>
              <span style="font-size:12px;color:var(--text-muted)">（切分长度={{ kbChunkSize }}，重叠={{ kbChunkOverlap }}）</span>
            </div>
            <div v-for="(chunk, ci) in kbPreviewResult.chunks" :key="ci" class="kb-preview-chunk">
              <div class="kb-preview-chunk-idx">#{{ (ci as number) + 1 }}</div>
              <div class="kb-preview-chunk-text">{{ chunk }}</div>
            </div>
          </div>
          <div v-if="kbUploadProgress > 0 && kbUploadProgress < 100" style="margin-top:8px">
            <el-progress :percentage="kbUploadProgress" :stroke-width="6" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input-number v-model="form.perUserLimit" :min="0" :max="9999" />
          <span class="form-hint" style="margin-left: 8px; color: var(--text-muted, #999); font-size: 12px;">0 表示不限购；按用户对同一商品的累计购买件数（含未完成退款订单）校验</span>
        </el-form-item>
        <el-form-item label="保障服务">
          <el-select v-model="form.serviceIds" multiple collapse-tags collapse-tags-tooltip clearable
                     placeholder="选择该商品可选购的保障服务（不选=不提供）" style="width: 100%;">
            <el-option v-for="s in serviceOptions" :key="s.id" :value="s.id"
                       :label="`${s.title}（¥${Number(s.price || 0).toFixed(2)}）`" />
          </el-select>
          <div class="form-hint" style="width: 100%; color: var(--text-muted, #999); font-size: 12px;">
            服务名称与价格在「保障服务管理」维护；结算时后端按服务表价格权威重算
          </div>
        </el-form-item>

        <!-- ===== 规格组定义 ===== -->
        <el-form-item>
          <template #label>
            <span>规格组定义</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>规格组代表商品的可选维度，如「颜色」「存储容量」。<br/>添加多个规格组后，点击「生成规格组合」会自动生成所有可能的规格组合。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <div class="spec-groups-panel">
            <div v-for="(group, gi) in specGroups" :key="gi" class="spec-group-row">
              <el-select v-model="group.name" filterable allow-create placeholder="规格名" style="width: 120px;" class="spec-group-select">
                <el-option v-for="opt in presetSpecNames" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <div class="spec-group-values">
                <el-tag
                  v-for="(v, vi) in group.values"
                  :key="vi"
                  closable
                  size="small"
                  type="info"
                  @close="group.values.splice(vi, 1)"
                >{{ v }}</el-tag>
                <el-input
                  v-model="group.newValue"
                  placeholder="输入值，回车添加"
                  size="small"
                  style="width: 140px;"
                  @keyup.enter="addSpecValue(gi)"
                  @blur="addSpecValue(gi)"
                />
              </div>
              <el-button type="danger" size="small" plain @click="specGroups.splice(gi, 1)" :disabled="specGroups.length <= 1">删除</el-button>
            </div>
            <div class="spec-group-actions">
              <el-button size="small" @click="addSpecGroup">+ 添加规格组</el-button>
              <el-tooltip placement="top" effect="dark">
                <template #content>根据规格组的所有组合自动生成规格列表。<br/>例如：2 种颜色 × 3 种存储 = 6 个规格</template>
                <el-button type="primary" size="small" @click="generateSkus" :disabled="!canGenerateSkus">生成规格组合</el-button>
              </el-tooltip>
              <span v-if="generatedCount" class="gen-hint">已生成 {{ generatedCount }} 个规格</span>
            </div>
          </div>
        </el-form-item>

        <!-- ===== SKU 规格列表 ===== -->
        <el-form-item>
          <template #label>
            <span>规格列表</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>每个规格代表一种具体的组合。<br/>可通过上方「规格组定义」批量生成，也可手动添加。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <div class="sku-list">
            <div v-for="(sku, index) in form.skuList" :key="sku._uid || index" class="sku-item">
              <div class="sku-header">
                <span class="sku-index">规格 {{ (index as number) + 1 }}：{{ sku.name || '未命名' }}</span>
                <el-button type="danger" size="small" @click="removeSku(index)" v-if="form.skuList.length > 1">删除</el-button>
              </div>
              <div class="sku-fields">
                <el-tooltip placement="top" effect="dark" content="自动生成：商品名 · 规格值1 · 规格值2。可手动修改">
                  <el-input v-model="sku.name" placeholder="规格名称" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="自动生成：规格1:值1;规格2:值2。可手动修改">
                  <el-input v-model="sku.specs" placeholder="规格描述（如：颜色:红色;存储:256GB）" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="设置该规格组合的售价">
                  <el-input-number v-model="sku.price" :min="0" :precision="2" placeholder="价格" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="设置该规格组合的初始库存数量">
                  <el-input-number v-model="sku.stock" :min="0" placeholder="库存" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="数值越小越靠前，默认 0">
                  <el-input-number v-model="sku.sort" :min="0" placeholder="排序" class="sku-field" />
                </el-tooltip>
                <el-switch v-model="sku.status" :active-value="1" :inactive-value="0" active-text="在售" inactive-text="停售" />
              </div>
            </div>
            <el-button type="primary" size="small" plain @click="addManualSku">+ 手动添加规格</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 库存管理弹窗 -->
    <el-dialog v-model="stockDialogVisible" title="库存管理" width="700px" destroy-on-close>
      <template #header>
        <span>库存管理<template v-if="stockSpuName"> — {{ stockSpuName }}</template></span>
      </template>
      <el-table :data="stockSkuList" v-loading="stockLoading" size="small">
        <el-table-column label="规格名称" min-width="180">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="规格描述" width="140" prop="specs" />
        <el-table-column label="当前库存" width="100">
          <template #default="{ row }">
            <span :class="{ 'low-stock': (row.stock || 0) <= (row.warnStock || 10) }">
              {{ row.stock || 0 }}
            </span>
            <el-tag v-if="(row.stock || 0) <= (row.warnStock || 10)" type="danger" size="small" style="margin-left: 4px;">低</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warnStock" label="预警值" width="80" />
        <el-table-column prop="price" label="售价" width="80">
          <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openStockAdjust(row)">调库存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!stockSkuList.length && !stockLoading" style="text-align: center; padding: 24px; color: var(--text-muted);">
        该商品暂无规格数据
      </div>
    </el-dialog>

    <!-- 调整库存弹窗 -->
    <el-dialog v-model="adjustDialogVisible" title="调整库存" width="420px" destroy-on-close>
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="规格名称">
          <span>{{ adjustForm.skuName }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ adjustForm.currentStock }}</span>
        </el-form-item>
        <el-form-item label="调整数量">
          <el-input-number v-model="adjustForm.delta" :min="-9999" :max="9999" />
          <span style="margin-left: 8px; color: var(--text-muted); font-size: 13px;">正数入库，负数出库</span>
        </el-form-item>
        <el-form-item label="预警库存">
          <el-input-number v-model="adjustForm.warnStock" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" placeholder="调整原因（选填）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustSubmitting" @click="handleStockAdjust">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog v-model="importVisible" title="批量导入商品" width="600px" destroy-on-close>
      <div style="margin-bottom: 16px;">
        <p style="color: var(--text-secondary); margin-bottom: 12px;">请先下载模板，按格式填写后上传。同名商品将复用，只追加规格。</p>
        <el-button @click="downloadTemplate" size="small">下载模板</el-button>
      </div>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        drag
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将 Excel 文件拖到此处，或<em>点击上传</em></div>
      </el-upload>
      <div v-if="importResult" style="margin-top: 16px;">
        <el-alert
          :title="importResult.success ? '导入完成' : '导入失败'"
          :type="importResult.success ? 'success' : 'error'"
          :closable="false"
        >
          <template v-if="importResult.success">
            新建商品: {{ importResult.newSpuCount }} 个，新建规格: {{ importResult.newSkuCount }} 个<br/>
            跳过行数: {{ importResult.skippedRows }}
          </template>
        </el-alert>
        <div v-if="importResult.errors" style="margin-top: 8px; max-height: 200px; overflow-y: auto;">
          <p v-for="(e, i) in importResult.errors" :key="i" style="color: var(--status-danger); font-size: 13px; margin: 4px 0;">{{ e }}</p>
          <p v-if="importResult.errorCount > importResult.errors.length" style="color: var(--text-muted); font-size: 12px;">... 还有 {{ importResult.errorCount - importResult.errors.length }} 条错误</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport" :disabled="!importFile">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 商品评价抽屉：商品视角查看/回复/隐藏该商品的评价 -->
    <SpuReviewDrawer
      v-model="reviewDrawerVisible"
      :spu="reviewSpu"
      @changed="onReviewChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { pageSpu, getAdminSpu, saveSpu, updateSpu, deleteSpu, setSpuStatus, batchSetSpuStatus, listAdminCategory, uploadImages, listEnabledServices } from '@/api/admin'
import { adminRequest } from '@/api/request'
import { apiKbUploadWithChunks, apiKbPreview, apiKbGet, apiKbMatch, apiKbMatchBatch, apiKbDelete } from '@/api/rag'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, Document, Loading, ChatLineSquare } from '@element-plus/icons-vue'
import SpuReviewDrawer from '@/components/admin/SpuReviewDrawer.vue'
import { storage } from '@/utils/storage'

const SORT_STORAGE_KEY = 'stellar:admin:spu-mgmt:sort:v1'
const SORT_BY_ALLOWED = new Set(['createTime', 'name'])
const SORT_ORDER_ALLOWED = new Set(['asc', 'desc'])

const loading = ref(false)
const submitting = ref(false)
const records = ref<any[]>([])
const selectedRows = ref<any[]>([])
const total = ref(0)
const categories = ref<any[]>([])

const query = reactive<any>({ page: 1, pageSize: 10, name: '', categoryId: null, status: null, sortBy: null, sortOrder: null })

/** 从 localStorage 恢复上次选择的排序维度与方向 */
function restoreSort() {
  const obj: any = storage.local.getObject(SORT_STORAGE_KEY)
  if (obj && SORT_BY_ALLOWED.has(obj.sortBy)) {
    query.sortBy = obj.sortBy
    if (SORT_ORDER_ALLOWED.has(obj.sortOrder)) query.sortOrder = obj.sortOrder
    else query.sortOrder = null
  }
}

/** 将当前排序维度与方向写入 localStorage，缺失任一则清除 */
function persistSort() {
  if (query.sortBy && SORT_BY_ALLOWED.has(query.sortBy) && query.sortOrder && SORT_ORDER_ALLOWED.has(query.sortOrder)) {
    storage.local.setObject(SORT_STORAGE_KEY, { sortBy: query.sortBy, sortOrder: query.sortOrder })
  } else {
    storage.local.remove(SORT_STORAGE_KEY)
  }
}

/** 切换排序字段，若未选方向则默认升序，重置到第1页并重新加载 */
function onSortByChange() {
  if (query.sortBy && !query.sortOrder) query.sortOrder = 'asc'
  query.page = 1
  persistSort()
  loadPage()
}

/** 切换排序方向，重置到第1页并重新加载 */
function onSortChange() {
  query.page = 1
  persistSort()
  loadPage()
}

/* ===== 名称搜索：输入即查 =====
 * 400ms 防抖，避免每个字符都打一次接口；回车 / 点「查询」立即触发。
 * 另外用 requestSeq 丢弃过期响应——连续输入时先发的请求可能后回，
 * 不做序号校验会把旧关键词的结果盖回表格。 */
const NAME_DEBOUNCE_MS = 400
let nameTimer: ReturnType<typeof setTimeout> | null = null
let requestSeq = 0

function cancelPendingNameSearch() {
  if (nameTimer) {
    clearTimeout(nameTimer)
    nameTimer = null
  }
}

watch(
  () => query.name,
  () => {
    cancelPendingNameSearch()
    nameTimer = setTimeout(() => {
      nameTimer = null
      query.page = 1
      loadPage()
    }, NAME_DEBOUNCE_MS)
  }
)

onBeforeUnmount(cancelPendingNameSearch)

/** 点击查询按钮（或回车），重置到第1页并立即加载 */
function onQueryClick() {
  cancelPendingNameSearch()
  query.page = 1
  loadPage()
}

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref<any>(null)
const form = reactive<any>({ id: null, name: '', categoryId: null, price: 0, image: '', mainImage: '', subImages: '', description: '', status: 1, perUserLimit: 0, serviceIds: [], skuList: [] })

/** 可选保障服务（启用中的服务字典，打开弹窗时加载） */
const serviceOptions = ref<any[]>([])

async function loadServiceOptions() {
  try {
    serviceOptions.value = (await listEnabledServices()) || []
  } catch {
    serviceOptions.value = []
  }
}
const formRules: Record<string, any> = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }]
}

// KB upload
const kbFile = ref<any>(null)
const kbFileList = ref<any[]>([])
const kbChunkSize = ref(512)
const kbChunkOverlap = ref(64)
const kbTags = ref('')
const kbPreviewing = ref(false)
const kbPreviewResult = ref<any>(null)
const kbUploadProgress = ref(0)
const kbExistingDoc = ref<any>(null)
const kbExistingDocId = ref<any>(null)
/** 关联文档来源：marker=商品描述里的 [KB_DOC_ID] 显式绑定，name=按商品名自动匹配 */
const kbDocSource = ref<'' | 'marker' | 'name'>('')
const kbDocLoading = ref(false)
/** 弹窗文档查询序号：快速切换商品时丢弃过期响应 */
const kbReqSeq = ref(0)
/** 列表页「关联文档」状态：商品名 -> 匹配结果 */
const kbDocStatusMap = ref<Record<string, any>>({})

// 图片上传
const imageUploading = ref(false)
const uploadedImages = ref<any[]>([])  // { url, name } 数组：第0项为主图，其余为副图

/** 移除指定索引的已上传图片，并同步到表单字段 */
function removeUploadedImage(idx: any) {
  uploadedImages.value.splice(idx, 1)
  syncImagesToForm()
}
/** 将已上传图片列表同步到表单的 mainImage 和 subImages 字段 */
function syncImagesToForm() {
  const urls = uploadedImages.value.map((i: any) => i.url)
  form.mainImage = urls[0] || ''
  form.image = urls[0] || ''
  form.subImages = urls.slice(1).join(';')
}
/** 上传单张图片到服务器，成功后添加到已上传列表 */
async function doImageUpload(options: any) {
  const { file } = options
  imageUploading.value = true
  try {
    const urls = await uploadImages([file], 'spu')
    if (urls && urls.length > 0) {
      uploadedImages.value.push({ url: urls[0], name: file.name })
      syncImagesToForm()
    }
    ElMessage.success(`${file.name} 上传成功`)
  } catch (e: any) {
    ElMessage.error(`${file.name} 上传失败：${e?.message || '未知错误'}`)
  } finally {
    imageUploading.value = false
  }
}
/** 从表单的 mainImage 和 subImages 字段初始化已上传图片预览列表 */
function initUploadedImages() {
  const images: any[] = []
  const main = form.mainImage || form.image || ''
  if (main) images.push({ url: main, name: '主图' })
  const subs = form.subImages || ''
  if (subs) {
    subs.split(';').filter(Boolean).forEach((u: any, i: any) => {
      images.push({ url: u.trim(), name: `副图${i + 1}` })
    })
  }
  uploadedImages.value = images
}

// Spec groups
const presetSpecNames = ['颜色', '存储', '内存', '尺寸', '配置', '型号', '版本', '材质', '尺码', '容量', '规格']
const specGroups = ref<{ name: string; values: string[]; newValue: string }[]>([{ name: '', values: [], newValue: '' }])
const generatedCount = ref(0)

const canGenerateSkus = computed(() => {
  const valid = specGroups.value.filter((g: any) => g.name && g.name.trim() && g.values.length > 0)
  return valid.length >= 1
})

/** 加载分类列表元数据 */
async function loadMeta() {
  try {
    const c = await listAdminCategory()
    categories.value = c || []
  } catch (e: any) {}
}

/** 加载商品分页列表，根据当前查询条件筛选
 * requestSeq 自增取号：输入即查时请求可能乱序返回，只认最后一次发起的结果 */
async function loadPage() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const res = await pageSpu({ ...query })
    if (seq !== requestSeq) return // 已有更新的请求在途，丢弃本次结果
    const d: any = res || {}
    records.value = d.records || d.list || []
    total.value = d.total || 0
  } finally {
    if (seq === requestSeq) loading.value = false
  }
  // 文档状态单独异步拉取：不阻塞表格渲染，失败也只影响这一列的显示
  loadKbDocStatus()
}

/** 批量同步当前页商品的「关联文档」状态（一次请求，避免 N+1） */
async function loadKbDocStatus() {
  const names = records.value.map((r: any) => r.name).filter(Boolean)
  if (!names.length) { kbDocStatusMap.value = {}; return }
  try {
    const res: any = await apiKbMatchBatch(names)
    const payload: any = res?.data || res
    kbDocStatusMap.value = payload?.items || {}
  } catch (e: any) {
    kbDocStatusMap.value = {}
  }
}

/** 列表行对应的知识库文档（未匹配返回 null） */
function kbDocOfRow(row: any) {
  const m = kbDocStatusMap.value[row?.name]
  return m && m.matched ? m.document : null
}

/** 添加一个新的规格组 */
function addSpecGroup() {
  specGroups.value.push({ name: '', values: [], newValue: '' })
}

/** 向指定规格组添加一个规格值 */
function addSpecValue(gi: any) {
  const group = specGroups.value[gi]
  if (!group) return
  const val = (group.newValue || '').trim()
  if (!val) return
  if (!group.values.includes(val)) {
    group.values.push(val)
  }
  group.newValue = ''
}

/** 根据规格组的笛卡尔积自动生成 SKU 组合列表 */
function generateSkus() {
  const valid = specGroups.value.filter((g: any) => g.name && g.name.trim() && g.values.length > 0)
  if (valid.length === 0) {
    ElMessage.warning('请至少添加一个有效的规格组（含名称和至少一个值）')
    return
  }
  const combinations = cartesianProduct(valid)
  const spuName = form.name || '商品'
  const basePrice = Number(form.price ?? 0)
  form.skuList = combinations.map((combo: any, idx: any) => ({
    _uid: Date.now() + idx + Math.random(),
    name: combo.name ? `${spuName} · ${combo.name}` : `${spuName} · 规格${idx + 1}`,
    specs: combo.specs,
    price: basePrice,
    stock: 0,
    sort: idx,
    status: 1
  }))
  generatedCount.value = form.skuList.length
  ElMessage.success(`已生成 ${form.skuList.length} 个规格组合`)
}

/** 计算多个规格组的笛卡尔积，生成所有规格组合 */
function cartesianProduct(groups: any) {
  if (groups.length === 0) return []
  let result = [{ specs: '', name: '' }]
  for (const group of groups) {
    const next: any[] = []
    for (const item of result) {
      for (const val of group.values) {
        const specs = item.specs ? `${item.specs};${group.name}:${val}` : `${group.name}:${val}`
        const name = item.name ? `${item.name} · ${val}` : val
        next.push({ specs, name })
      }
    }
    result = next
  }
  return result
}

/** 从已有的 SKU 列表中反推规格组定义，用于编辑时回填 */
function inferSpecGroups(skuList: any) {
  if (!skuList || skuList.length === 0) {
    specGroups.value = [{ name: '', values: [], newValue: '' }]
    generatedCount.value = 0
    return
  }
  const groupMap = new Map()
  let allSameStructure = true
  for (const sku of skuList) {
    const specs = sku.specs || ''
    if (!specs) { allSameStructure = false; continue }
    const pairs = specs.split(';').map((s: any) => s.trim()).filter(Boolean)
    for (const pair of pairs) {
      const colonIdx = pair.indexOf(':')
      if (colonIdx === -1) continue
      const key = pair.substring(0, colonIdx).trim()
      const val = pair.substring(colonIdx + 1).trim()
      if (!key || !val) continue
      if (!groupMap.has(key)) groupMap.set(key, new Set())
      groupMap.get(key).add(val)
    }
  }
  if (groupMap.size > 0 && allSameStructure) {
    specGroups.value = Array.from(groupMap.entries()).map(([name, values]) => ({
      name, values: Array.from(values), newValue: ''
    }))
    generatedCount.value = skuList.length
  } else {
    specGroups.value = [{ name: '', values: [], newValue: '' }]
    generatedCount.value = skuList.length
  }
}

/** 手动添加一个空 SKU 规格 */
function addManualSku() {
  form.skuList.push({
    _uid: Date.now() + Math.random(),
    name: '', specs: '',
    price: Number(form.price ?? 0), stock: 0, sort: form.skuList.length, status: 1
  })
}

/** 移除指定索引的 SKU */
function removeSku(index: any) {
  form.skuList.splice(index, 1)
}

// KB functions
/** 知识库文件变更时，保存文件引用并自动填充标签 */
function onKbFileChange(f: any) {
  const raw = f?.raw
  if (!raw) return
  kbFile.value = raw
  kbFileList.value = [f]
  kbPreviewResult.value = null
  const cat = categories.value.find((c: any) => c.id === form.categoryId)
  const parts: any[] = []
  if (cat) parts.push(cat.name)
  if (form.name) parts.push(form.name)
  kbTags.value = parts.join(',')
}

/** 知识库文件被移除时，清空相关状态 */
function onKbFileRemove() {
  kbFile.value = null
  kbFileList.value = []
  kbPreviewResult.value = null
}

/** 预览知识库文档的分块切分结果 */
async function doKbPreview() {
  if (!kbFile.value) return
  kbPreviewing.value = true
  try {
    const fd = new FormData()
    fd.append('file', kbFile.value)
    fd.append('chunk_size', String(kbChunkSize.value))
    fd.append('chunk_overlap', String(kbChunkOverlap.value))
    fd.append('limit', '30')
    const res = await apiKbPreview(fd)
    kbPreviewResult.value = res?.data || res
  } catch (e: any) {
    ElMessage.error('预览失败：' + (e?.message || '未知错误'))
  } finally {
    kbPreviewing.value = false
  }
}

/** 上传知识库文档到 RAG 服务，返回文档 ID */
async function uploadKbDoc() {
  if (!kbFile.value) return null
  kbUploadProgress.value = 10
  try {
    const res = await apiKbUploadWithChunks(
      kbFile.value, kbTags.value || '', kbChunkSize.value, kbChunkOverlap.value,
      (pct: any) => { kbUploadProgress.value = 10 + Math.round(pct * 0.7) }
    )
    kbUploadProgress.value = 100
    return res?.data?.id || null
  } catch (e: any) {
    kbUploadProgress.value = 0
    throw e
  }
}

/** 去除描述中的知识库文档标记（兼容历史脏数据：可能出现多个重复标记） */
function stripKbMarkers(text: any) {
  return String(text || '').replace(/\s*\[KB_DOC_ID:\d+\]/g, '').trim()
}

/** 打开新增/编辑商品对话框，create 模式初始化空表单，edit 模式加载详情 */
async function openDialog(mode: any, row?: any) {
  dialogMode.value = mode
  specGroups.value = [{ name: '', values: [], newValue: '' }]
  generatedCount.value = 0
  kbFile.value = null
  kbFileList.value = []
  kbPreviewResult.value = null
  kbUploadProgress.value = 0
  kbTags.value = ''
  kbExistingDoc.value = null
  kbExistingDocId.value = null
  kbDocSource.value = ''
  kbDocLoading.value = false
  kbReqSeq.value++
  if (mode === 'create') {
    Object.assign(form, { id: null, name: '', categoryId: null, price: 0, image: '', mainImage: '', subImages: '', description: '', status: 1, perUserLimit: 0, serviceIds: [], skuList: [] })
    uploadedImages.value = []
    loadServiceOptions()
    addManualSku()
    dialogVisible.value = true
  } else {
    submitting.value = true
    try {
      const detail: any = await getAdminSpu(row.id)
      const mdText = detail.descriptionMd || ''
      const kbMatch = mdText.match(/\[KB_DOC_ID:(\d+)\]/)
      if (kbMatch) {
        kbExistingDocId.value = kbMatch[1]
        try {
          const docRes = await apiKbGet(kbExistingDocId.value)
          kbExistingDoc.value = docRes?.data || docRes
          kbDocSource.value = 'marker'
        } catch (e: any) {
          // 标记指向的文档已被删除：清掉标记信息，下面走按商品名回查
          kbExistingDocId.value = null
        }
      }
      if (!kbExistingDoc.value) {
        // 从知识库管理页（或脚本）直接上传的文档不会往描述里写 [KB_DOC_ID]，
        // 这里按商品名回查真实文档，保证「关联文档」一栏有状态可看。
        // 异步执行：知识库服务慢/挂掉时不拖慢弹窗打开，用 seq 防止串商品。
        const seq = ++kbReqSeq.value
        kbDocLoading.value = true
        apiKbMatch(detail.name)
          .then((matched: any) => {
            if (seq !== kbReqSeq.value) return
            const payload: any = matched?.data || matched
            if (payload?.matched && payload.document) {
              kbExistingDoc.value = payload.document
              kbExistingDocId.value = payload.document.id
              kbDocSource.value = 'name'
            }
          })
          .catch(() => { /* 知识库不可用时静默降级，不阻断商品编辑 */ })
          .finally(() => {
            if (seq === kbReqSeq.value) kbDocLoading.value = false
          })
      }
      const cleanDescription = stripKbMarkers(mdText)
      Object.assign(form, {
        id: detail.id, name: detail.name, categoryId: detail.categoryId,
        price: Number(detail.minPrice ?? detail.price ?? 0),
        image: detail.mainImage || detail.image || detail.pic || '',
        mainImage: detail.mainImage || detail.image || detail.pic || '',
        subImages: detail.subImages || '',
        description: stripKbMarkers(detail.description) || cleanDescription,
        status: detail.status ?? 1,
        perUserLimit: Number(detail.perUserLimit ?? 0) || 0,
        serviceIds: (detail.services || []).map((s: any) => s.id),
        skuList: []
      })
      loadServiceOptions()
      initUploadedImages()
      const skus = detail.skuList || detail.skus || []
      if (skus.length) {
        form.skuList = skus.map((s: any, idx: any) => ({
          _uid: s.id || (Date.now() + idx), id: s.id,
          name: s.name || '', specs: s.specs || '',
          price: Number(s.price ?? 0), stock: Number(s.stock ?? 0),
          sort: Number(s.sort ?? 0), status: s.status ?? 1,
          originalPrice: s.originalPrice, image: s.image
        }))
        inferSpecGroups(form.skuList)
      } else {
        addManualSku()
      }
      dialogVisible.value = true
    } catch (e: any) {
      ElMessage.error('加载商品详情失败')
    } finally {
      submitting.value = false
    }
  }
}

/** 构建提交给后端的商品数据对象 */
function buildPayload() {
  const priceNum = Number(form.price ?? 0)
  const imageStr = form.mainImage || form.image || ''
  const descStr = form.description || ''
  const subImagesStr = form.subImages || ''
  const skuPayload = form.skuList && form.skuList.length
    ? form.skuList.map((s: any) => ({
        id: s.id, name: s.name || '', specs: s.specs || '',
        price: Number(s.price ?? 0) || 0, originalPrice: Number(s.price ?? 0) || 0,
        stock: Number(s.stock ?? 0) || 0, sort: Number(s.sort ?? 0) || 0,
        status: s.status ?? 1, image: s.image || imageStr
      }))
    : []
  return {
    id: form.id ?? undefined, name: form.name, categoryId: form.categoryId,
    status: form.status ?? 1, sort: form.sort ?? 0,
    perUserLimit: Number(form.perUserLimit ?? 0) || 0,
    serviceIds: (form.serviceIds || []).map(Number),
    subtitle: form.subtitle ?? '', image: imageStr, mainImage: imageStr,
    subImages: subImagesStr, description: descStr, descriptionMd: descStr,
    price: isNaN(priceNum) ? null : priceNum,
    minPrice: isNaN(priceNum) ? null : priceNum,
    maxPrice: isNaN(priceNum) ? null : priceNum,
    totalStock: form.totalStock ?? 0, skuList: skuPayload
  }
}

/** 提交商品表单：校验、上传知识库文档、保存或更新商品 */
async function submitForm() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch (e: any) { return }
  if (dialogMode.value === 'create' && !kbFile.value) {
    ElMessage.warning('请上传商品关联文档（产品参数/说明）到知识库')
    return
  }
  submitting.value = true
  try {
    let kbDocId: any = null
    if (kbFile.value) {
      try {
        kbDocId = await uploadKbDoc()
        ElMessage.success('知识库文档上传成功')
      } catch (e: any) {
        ElMessage.error('知识库文档上传失败：' + (e?.message || '未知错误'))
        return
      }
    } else if (kbExistingDocId.value) {
      kbDocId = kbExistingDocId.value
    }
    const payload = buildPayload()
    if (kbDocId) {
      payload.descriptionMd = (payload.descriptionMd || '') + `\n\n[KB_DOC_ID:${kbDocId}]`
    }
    if (dialogMode.value === 'create') await saveSpu(payload)
    else await updateSpu(payload)
    ElMessage.success(dialogMode.value === 'create' ? '新增成功' : '更新成功')
    dialogVisible.value = false
    loadPage()
  } finally {
    submitting.value = false
  }
}

function handleSelectionChange(rows: any) { selectedRows.value = rows }
function tableRowClassName({ row }: { row: any }) { return row.status === 0 ? 'row-off-shelf' : '' }

/** 下架单个商品，二次确认后执行 */
async function handleShelfOff(row: any) {
  const name = row.name || ('商品#' + row.id)
  try { await ElMessageBox.confirm(`确定下架商品「${name}」？\n下架后用户将无法搜索和购买该商品。`, '下架确认', { type: 'warning' }) } catch (e: any) { return }
  try { await setSpuStatus(row.id, 0); ElMessage.success(`「${name}」已下架`); loadPage() }
  catch (e: any) { ElMessage.error((e?.response?.data?.msg) || e?.message || '下架失败') }
}

/** 上架单个商品，二次确认后执行 */
async function handleShelfOn(row: any) {
  const name = row.name || ('商品#' + row.id)
  try { await ElMessageBox.confirm(`确定上架商品「${name}」？`, '上架确认', { type: 'warning' }) } catch (e: any) { return }
  try { await setSpuStatus(row.id, 1); ElMessage.success(`「${name}」已上架`); loadPage() }
  catch (e: any) { ElMessage.error((e?.response?.data?.msg) || e?.message || '上架失败') }
}

/** 批量下架选中的商品，二次确认后执行 */
async function handleBatchShelfOff() {
  const names = selectedRows.value.map((r: any) => r.name).join('、')
  try { await ElMessageBox.confirm(`确定批量下架以下 ${selectedRows.value.length} 件商品？\n${names}\n\n下架后用户将无法搜索和购买这些商品。`, '批量下架确认', { type: 'warning', confirmButtonText: '确定下架', cancelButtonText: '取消' }) } catch (e: any) { return }
  try {
    const ids = selectedRows.value.map((r: any) => r.id)
    await batchSetSpuStatus(ids, 0)
    ElMessage.success(`已成功下架 ${selectedRows.value.length} 件商品`)
    selectedRows.value = []
    loadPage()
  } catch (e: any) { ElMessage.error((e?.response?.data?.msg) || e?.message || '批量下架失败') }
}

/** 批量上架选中的商品，二次确认后执行 */
async function handleBatchShelfOn() {
  const names = selectedRows.value.map((r: any) => r.name).join('、')
  try { await ElMessageBox.confirm(`确定批量上架以下 ${selectedRows.value.length} 件商品？\n${names}`, '批量上架确认', { type: 'warning', confirmButtonText: '确定上架', cancelButtonText: '取消' }) } catch (e: any) { return }
  try {
    const ids = selectedRows.value.map((r: any) => r.id)
    await batchSetSpuStatus(ids, 1)
    ElMessage.success(`已成功上架 ${selectedRows.value.length} 件商品`)
    selectedRows.value = []
    loadPage()
  } catch (e: any) { ElMessage.error((e?.response?.data?.msg) || e?.message || '批量上架失败') }
}

/** 删除商品，二次确认后执行 */
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm(`确定删除商品「${row.name}」？`, '提示', { type: 'warning' }); await deleteSpu(row.id); ElMessage.success('已删除'); loadPage() } catch (e: any) {}
}

/* ===== 商品评价抽屉 =====
 * 商品视角的评价入口：抽屉内只查该 spuId 的评价，全站队列仍走「评价管理」页。 */
const reviewDrawerVisible = ref(false)
const reviewSpu = ref<any>(null)
/** 抽屉里回复/隐藏过评价 → 关闭时回刷列表（隐藏会同步改 comment_count） */
let reviewDirty = false

function openReviewDrawer(row: any) {
  reviewSpu.value = row
  reviewDrawerVisible.value = true
}

function onReviewChanged() {
  reviewDirty = true
}

watch(reviewDrawerVisible, (visible) => {
  if (visible || !reviewDirty) return
  reviewDirty = false
  loadPage()
})

onMounted(() => { restoreSort(); loadMeta(); loadPage() })

// Import/Export
const exporting = ref(false); const importing = ref(false); const importVisible = ref(false)
const importFile = ref<any>(null); const importResult = ref<any>(null); const uploadRef = ref<any>(null)

function openImport() { importVisible.value = true; importResult.value = null; importFile.value = null }
function onFileChange(file: any) { importFile.value = file.raw; importResult.value = null }
function onFileRemove() { importFile.value = null }

/** 下载商品导入模板 Excel 文件 */
async function downloadTemplate() {
  try {
    const resp = await fetch('/admin/spu/import-export/template', { headers: { token: localStorage.getItem('stellar_admin_token') || '' } })
    if (!resp.ok) throw new Error('下载失败')
    const blob = await resp.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = '商品导入模板.xlsx'; a.click(); URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch (e: any) { ElMessage.error('下载模板失败') }
}

/** 导出全部商品数据为 Excel 文件 */
async function handleExport() {
  exporting.value = true
  try {
    const resp = await fetch('/admin/spu/import-export/export', { headers: { token: localStorage.getItem('stellar_admin_token') || '' } })
    if (!resp.ok) throw new Error('导出失败')
    const blob = await resp.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = '商品数据导出.xlsx'; a.click(); URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error('导出失败') } finally { exporting.value = false }
}

/** 导入 Excel 文件批量创建商品和 SKU */
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importing.value = true; importResult.value = null
  try {
    const formData = new FormData(); formData.append('file', importFile.value)
    const token = localStorage.getItem('stellar_admin_token') || ''
    const resp = await fetch('/admin/spu/import-export/import', { method: 'POST', headers: { token }, body: formData })
    const json = await resp.json(); importResult.value = json?.data || json
    if (importResult.value?.success) {
      ElMessage.success(`导入成功：${importResult.value.newSpuCount} 个商品，${importResult.value.newSkuCount} 个规格`)
      loadPage(); importFile.value = null; uploadRef.value?.clearFiles()
    }
  } catch (e: any) { ElMessage.error('导入失败') } finally { importing.value = false }
}

/** 从知识库删除该商品的文档（连带清空向量），并同步列表状态 */
async function removeExistingDoc() {
  const doc = kbExistingDoc.value
  if (!doc) return
  try {
    await ElMessageBox.confirm(
      `确定从知识库删除文档「${doc.filename}」？\n删除后 AI 助手将无法回答该商品的咨询，需要时可重新上传。`,
      '移除关联文档', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch (e: any) { return }
  try {
    await apiKbDelete(doc.id)
    kbExistingDoc.value = null
    kbExistingDocId.value = null
    kbDocSource.value = ''
    ElMessage.success('已从知识库移除')
    loadKbDocStatus()
  } catch (e: any) {
    ElMessage.error(e?.message || '移除失败')
  }
}
function kbDocStatusText(s: any) { return ({ uploading: '上传中', parsing: '解析中', indexing: '入库中', ready: '就绪', error: '失败' } as Record<string, string>)[s] || s || '未知' }
function kbDocStatusTag(s: any) { return ({ ready: 'success', error: 'danger', parsing: 'warning', indexing: 'primary', uploading: 'info' } as Record<string, string>)[s] || 'info' }
function humanFileSize(n: any) { if (!n) return '0 B'; const u = ['B', 'KB', 'MB', 'GB']; let i = 0; n = Number(n); while (n >= 1024 && i < u.length - 1) { n /= 1024; i++ }; return n.toFixed(i > 0 ? 1 : 0) + ' ' + u[i] }
function fmtTime(v: any) { return v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '' }

// ==================== 库存管理 ====================
const stockDialogVisible = ref(false)
const stockLoading = ref(false)
const stockSpuName = ref('')
const stockSkuList = ref<any[]>([])

const adjustDialogVisible = ref(false)
const adjustSubmitting = ref(false)
const adjustForm = reactive<any>({ skuId: null, skuName: '', currentStock: 0, delta: 0, warnStock: 10, remark: '' })

/** 打开库存管理弹窗，加载该商品的所有 SKU 库存信息 */
async function openStockDialog(row: any) {
  stockSpuName.value = row.name
  stockDialogVisible.value = true
  stockLoading.value = true
  stockSkuList.value = []
  try {
    const detail: any = await getAdminSpu(row.id)
    const skus = detail.skuList || detail.skus || []
    stockSkuList.value = skus.map((s: any) => ({
      id: s.id,
      name: s.name || '',
      specs: s.specs || '',
      stock: Number(s.stock ?? 0),
      warnStock: Number(s.warnStock ?? 10),
      price: Number(s.price ?? 0)
    }))
  } catch (e: any) {
    ElMessage.error('加载库存信息失败')
  } finally {
    stockLoading.value = false
  }
}

/** 打开调整库存弹窗 */
function openStockAdjust(sku: any) {
  adjustForm.skuId = sku.id
  adjustForm.skuName = sku.name
  adjustForm.currentStock = sku.stock || 0
  adjustForm.delta = 0
  adjustForm.warnStock = sku.warnStock || 10
  adjustForm.remark = ''
  adjustDialogVisible.value = true
}

/** 执行库存调整 */
async function handleStockAdjust() {
  adjustSubmitting.value = true
  try {
    await adminRequest({ url: '/admin/inventory/stock', method: 'put', data: { ...adjustForm } })
    ElMessage.success('库存已更新')
    adjustDialogVisible.value = false
    // 刷新 SKU 列表
    stockLoading.value = true
    try {
      const detail: any = await getAdminSpu(stockSkuList.value[0]?.spuId)
      const skus = detail.skuList || detail.skus || []
      stockSkuList.value = skus.map((s: any) => ({
        id: s.id,
        name: s.name || '',
        specs: s.specs || '',
        stock: Number(s.stock ?? 0),
        warnStock: Number(s.warnStock ?? 10),
        price: Number(s.price ?? 0)
      }))
    } catch (e: any) { /* ignore */ } finally { stockLoading.value = false }
  } catch (e: any) {
    ElMessage.error('调整失败')
  } finally {
    adjustSubmitting.value = false
  }
}
</script>

<style scoped>
.mgmt-page { display: flex; flex-direction: column; gap: 16px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-actions { display: flex; gap: 8px; align-items: center; }
.panel-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }

/* 名称列：第一行商品名（单行省略），第二行评价入口小字 */
.spu-name-cell { min-width: 0; padding: 2px 0; }
.spu-name-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.spu-review-entry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-muted);
  cursor: pointer;
  transition: color var(--transition-color);
}
.spu-review-entry:hover { color: var(--brand-primary); }
.batch-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 10px 16px; background: var(--brand-primary-soft); border: 1px solid var(--brand-primary); border-radius: var(--radius-md); overflow: hidden; }
.toolbar-slide-enter-active, .toolbar-slide-leave-active { transition: opacity 0.15s var(--ease-out), transform 0.15s var(--ease-out); }
.toolbar-slide-enter-from, .toolbar-slide-leave-to { opacity: 0; transform: translateY(-6px); }
.batch-tip { font-size: 14px; font-weight: 500; color: var(--brand-primary); flex: 1; }
.img-placeholder { width: 48px; height: 48px; background: var(--bg-hover); color: var(--text-muted); display: flex; align-items: center; justify-content: center; border-radius: var(--radius-sm); font-size: 12px; }
.col-header-sort { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; }
.sort-arrow { display: inline-block; color: var(--brand-primary); font-size: 10px; line-height: 1; padding: 2px 4px; border-radius: var(--radius-sm); background: var(--brand-primary-soft); transform: translateY(-1px); }
.sort-arrow.asc { color: var(--status-success); }
.sort-arrow.desc { color: var(--status-danger); }
.low-stock { color: var(--status-danger); font-weight: 600; }
.sku-list { margin-top: 8px; }
.sku-item { border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 12px; margin-bottom: 12px; }
.sku-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.sku-index { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.sku-fields { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.sku-field { flex: 1; min-width: 140px; max-width: 200px; }
.hint-icon { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: var(--brand-primary-soft); color: var(--brand-primary); font-size: 12px; font-weight: 700; cursor: help; margin-left: 4px; line-height: 1; }
.spec-groups-panel { background: var(--bg-hover); border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 14px; }
.spec-group-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.spec-group-select { flex-shrink: 0; }
.spec-group-values { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; flex: 1; min-width: 200px; }
.spec-group-actions { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.gen-hint { color: var(--status-success); font-size: 13px; font-weight: 500; }
:deep(.el-dialog) { border-radius: var(--radius-lg); overflow: hidden; }
.kb-upload-row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.kb-params { display: flex; flex-direction: column; gap: 8px; min-width: 200px; }
.kb-param-item { display: flex; align-items: center; gap: 8px; }
.kb-param-label { font-size: 13px; color: var(--text-secondary); white-space: nowrap; width: 80px; }
.kb-preview-result { margin-top: 10px; border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 10px; max-height: 300px; overflow-y: auto; background: var(--bg-hover); }
.kb-preview-head { font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; display: flex; gap: 8px; align-items: baseline; }
.kb-preview-chunk { display: flex; gap: 8px; margin-bottom: 6px; border-top: 1px dashed var(--border-base); padding-top: 6px; }
.kb-preview-chunk-idx { font-size: 12px; color: var(--brand-primary); font-weight: 600; min-width: 28px; }
.kb-preview-chunk-text { font-size: 12px; color: var(--text-secondary); line-height: 1.5; word-break: break-all; }
.kb-existing-doc { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 12px 16px; background: var(--bg-hover); border: 1px solid var(--border-base); border-radius: var(--radius-md); }
.kb-existing-doc-info { display: flex; align-items: flex-start; gap: 10px; flex: 1; min-width: 0; }
.kb-existing-doc-detail { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.kb-existing-doc-name { font-size: 14px; font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-existing-doc-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 2px; }
.kb-existing-doc-meta-item { font-size: 12px; color: var(--text-muted); margin-left: 8px; white-space: nowrap; }
.kb-existing-doc-error { font-size: 12px; color: var(--status-error); word-break: break-all; }
.kb-doc-state { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-radius: var(--radius-md); font-size: 13px; border: 1px dashed var(--border-base); }
.kb-doc-state.is-empty { color: var(--text-muted); background: transparent; }
.kb-doc-state.is-loading { color: var(--text-secondary); background: var(--bg-hover); }
.kb-none-text { font-size: 12px; color: var(--text-muted); }
:deep(.row-off-shelf) { --el-table-tr-bg-color: var(--bg-hover, #fafafa); color: var(--text-muted, #999); }
:deep(.row-off-shelf td) { color: var(--text-muted, #999); }
:deep(.row-off-shelf .el-image) { opacity: 0.6; }

/* ===== 图片上传 ===== */
.image-upload-section { width: 100%; }
.image-preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.image-preview-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 2px solid var(--border-base);
  transition: border-color 0.2s;
}
.image-preview-item.is-main { border-color: var(--brand-primary); }
.image-preview-item:hover { border-color: var(--brand-primary); }
.image-preview-img {
  width: 100%;
  height: 100%;
}
.image-preview-label {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  text-align: center;
  padding: 2px 0;
}
.image-preview-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}
.image-preview-item:hover .image-preview-remove { opacity: 1; }
.image-upload-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 24px;
  color: var(--text-secondary);
  font-size: 13px;
}
.upload-trigger em { color: var(--brand-primary); font-style: normal; }
.upload-hint { font-size: 11px; color: var(--text-muted); }
</style>
