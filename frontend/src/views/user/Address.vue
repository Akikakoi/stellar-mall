<template>
  <div class="address-page">
    <div class="page-header">
      <div class="header-title">
        <h2>收货地址</h2>
        <span class="subtitle">共 {{ addresses.length }} 条地址</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增收货地址</el-button>
    </div>

    <div class="address-grid" v-loading="loading">
      <el-empty v-if="!loading && addresses.length === 0" description="暂无收货地址">
        <el-button type="primary" @click="openAdd">新增收货地址</el-button>
      </el-empty>

      <template v-else>
        <!-- 新增占位卡片 -->
        <div class="address-card add-card" @click="openAdd">
          <div class="add-icon">
            <el-icon :size="32"><Plus /></el-icon>
          </div>
          <span class="add-text">添加新地址</span>
        </div>

        <!-- 地址卡片 -->
        <div
          v-for="addr in addresses"
          :key="addr.id"
          class="address-card"
          :class="{ default: addr.isDefault === 1 }"
        >
          <div class="card-top">
            <el-icon class="location-icon"><LocationFilled /></el-icon>
            <el-tag v-if="addr.isDefault === 1" type="primary" size="small" effect="light" round>默认地址</el-tag>
          </div>
          <div class="card-body">
            <div class="contact-line">
              <span class="consignee">{{ addr.consignee }}</span>
              <span class="phone">{{ addr.phone }}</span>
            </div>
            <div class="address-tags">
              <el-tag size="small" type="info" v-if="addr.province">{{ addr.province }}</el-tag>
              <el-tag size="small" type="info" v-if="addr.city">{{ addr.city }}</el-tag>
              <el-tag size="small" type="info" v-if="addr.district">{{ addr.district }}</el-tag>
            </div>
            <p class="detail">{{ addr.detail }}</p>
          </div>
          <div class="card-footer">
            <el-button
              v-if="addr.isDefault !== 1"
              type="primary"
              link
              size="small"
              @click="handleSetDefault(addr.id)"
            >设为默认</el-button>
            <span v-else class="default-label">默认地址</span>
            <div class="footer-actions">
              <el-button type="primary" link size="small" :icon="Edit" @click="openEdit(addr)">编辑</el-button>
              <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(addr.id)">删除</el-button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 弹窗表单 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑收货地址' : '新增收货地址'"
      width="540px"
      :close-on-click-modal="false"
      destroy-on-close
      align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="收货人" prop="consignee">
          <el-input v-model="form.consignee" placeholder="请输入收货人姓名" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="20" />
        </el-form-item>
        <el-form-item label="所在地区" required>
          <el-row :gutter="8">
            <el-col :span="8"><el-input v-model="form.province" placeholder="省 / 直辖市" /></el-col>
            <el-col :span="8"><el-input v-model="form.city" placeholder="市" /></el-col>
            <el-col :span="8"><el-input v-model="form.district" placeholder="区 / 县" /></el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="form.detail" type="textarea" :rows="2" placeholder="街道、门牌号、楼栋等" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" active-text="默认地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, LocationFilled } from '@element-plus/icons-vue'
import {
  listAddresses,
  saveAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress
} from '@/api/mall'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const addresses = ref([])

const form = reactive({
  id: null,
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

const PHONE_REGEXP = /^1[3-9]\d{9}$/

const rules = {
  consignee: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value || PHONE_REGEXP.test(value)) {
          callback()
        } else {
          callback(new Error('手机号格式不正确'))
        }
      },
      trigger: 'blur'
    }
  ],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

/** 重置地址表单到初始状态 */
function resetForm() {
  form.id = null
  form.consignee = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  form.isDefault = 0
}

/** 加载用户所有收货地址 */
async function loadAddresses() {
  loading.value = true
  try {
    const res = await listAddresses()
    addresses.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e) {
    ElMessage.error(e?.message || '加载地址失败')
  } finally {
    loading.value = false
  }
}

/** 打开新增地址弹窗并重置表单 */
function openAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑地址弹窗并回填已有地址数据
 * @param {Object} addr - 要编辑的地址对象
 */
function openEdit(addr) {
  isEdit.value = true
  form.id = addr.id
  form.consignee = addr.consignee || ''
  form.phone = addr.phone || ''
  form.province = addr.province || ''
  form.city = addr.city || ''
  form.district = addr.district || ''
  form.detail = addr.detail || ''
  form.isDefault = addr.isDefault || 0
  dialogVisible.value = true
  setTimeout(() => formRef.value?.clearValidate?.(), 0)
}

/** 新增或编辑地址：校验表单后调用对应接口，完成后刷新地址列表 */
async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (submitting.value) return
  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      await updateAddress({ ...form })
      ElMessage.success('地址已更新')
    } else {
      await saveAddress({ ...form })
      ElMessage.success('地址已添加')
    }
    dialogVisible.value = false
    resetForm()
    await loadAddresses()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 确认后删除指定地址
 * @param {number} id - 地址 ID
 */
async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该地址？', '提示', { type: 'warning' })
    await deleteAddress(id)
    ElMessage.success('已删除')
    await loadAddresses()
  } catch (e) {
    // 取消删除无需提示
  }
}

/**
 * 将指定地址设为默认地址
 * @param {number} id - 地址 ID
 */
async function handleSetDefault(id) {
  const addr = addresses.value.find(a => a.id === id)
  if (addr?.isDefault === 1) return
  try {
    await setDefaultAddress(id)
    ElMessage.success('已设为默认地址')
    await loadAddresses()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(loadAddresses)
</script>

<style scoped>
.address-page {
  min-height: 100vh;
  padding: 0 20px 60px;
  background: var(--bg-page, #f5f7fa);
}

.page-header {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-primary, #1f2329);
}

.header-title .subtitle {
  font-size: 13px;
  color: var(--text-muted, #8f959e);
  margin-left: 8px;
}

.address-grid {
  max-width: 960px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.address-card {
  background: #fff;
  border: 1px solid var(--border-base, #e4e6eb);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.address-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.address-card.default {
  border-color: var(--el-color-primary);
  background: linear-gradient(180deg, rgba(64, 158, 255, 0.04) 0%, #fff 100%);
}

.add-card {
  justify-content: center;
  align-items: center;
  border-style: dashed;
  border-color: var(--border-dashed, #c0c4cc);
  cursor: pointer;
  min-height: 200px;
  gap: 12px;
}

.add-card:hover {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.04);
}

.add-icon {
  color: var(--border-dashed, #c0c4cc);
  transition: color 0.2s;
}

.add-card:hover .add-icon {
  color: var(--el-color-primary);
}

.add-text {
  color: var(--text-muted, #8f959e);
  font-size: 14px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.location-icon {
  color: var(--el-color-primary);
  font-size: 18px;
}

.card-body {
  flex: 1;
  margin-bottom: 16px;
}

.contact-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.consignee {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1f2329);
}

.phone {
  font-size: 13px;
  color: var(--text-muted, #8f959e);
}

.address-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.detail {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary, #4e5969);
  line-height: 1.6;
  word-break: break-all;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle, #f2f3f5);
}

.default-label {
  font-size: 13px;
  color: var(--el-color-primary);
  opacity: 0.6;
}

.footer-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .address-grid {
    grid-template-columns: 1fr;
  }
}
</style>
