<template>
  <div class="profile-page">

    <main class="container main-content" v-loading="loading">
      <div class="card user-card">
        <div class="avatar">
          {{ (userStore.nickname || 'U').charAt(0).toUpperCase() }}
        </div>
        <div class="user-info">
          <h2 class="nickname">{{ userStore.nickname || '用户' + (userStore.userId || '') }}</h2>
          <div class="meta">
            <span>手机号：{{ userStore.phone || '未绑定' }}</span>
            <span style="margin-left: 24px;">用户ID：{{ userStore.userId || '-' }}</span>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="profile-tabs">
        <el-tab-pane label="基本信息" name="info">
          <div class="card">
            <el-form :model="form" label-width="100px" style="max-width: 500px;">
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="form.phone" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="选填" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleUpdate">保存修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的订单" name="orders">
          <div class="card quick-links">
            <div class="link-item" @click="router.push('/order/list')">
              <el-icon :size="32" color="var(--text-secondary)"><List /></el-icon>
              <span>全部订单</span>
            </div>
            <div class="link-item" @click="router.push('/coupons')">
              <el-icon :size="32" color="var(--text-secondary)"><Ticket /></el-icon>
              <span>优惠券</span>
            </div>
            <div class="link-item" @click="router.push('/points')">
              <el-icon :size="32" color="#f0a040"><StarFilled /></el-icon>
              <span>积分商城</span>
            </div>
            <div class="link-item" @click="router.push('/order/list')">
              <el-icon :size="32" color="var(--text-secondary)"><Wallet /></el-icon>
              <span>待付款</span>
            </div>
            <div class="link-item" @click="router.push('/order/list')">
              <el-icon :size="32" color="var(--text-secondary)"><Van /></el-icon>
              <span>待收货</span>
            </div>
            <div class="link-item" @click="router.push('/rag')">
              <el-icon :size="32" color="var(--text-secondary)"><ChatDotRound /></el-icon>
              <span>AI助手</span>
            </div>
            <div class="link-item" @click="router.push('/aftersale/list')">
              <el-icon :size="32" color="var(--text-secondary)"><Warning /></el-icon>
              <span>我的售后</span>
            </div>
            <div class="link-item" @click="router.push('/wallet')">
              <el-icon :size="32" color="var(--text-secondary)"><Wallet /></el-icon>
              <span>我的钱包</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="安全设置" name="security">
          <div class="card">
            <el-form label-width="120px" style="max-width: 500px;">
              <el-form-item label="原密码">
                <el-input v-model="pwdForm.oldPwd" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPwd" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="pwdForm.confirmPwd" type="password" show-password />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleChangePwd">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { updateUserProfile } from '@/api/mall'
import { ElMessage } from 'element-plus'
import { List, Wallet, Van, ChatDotRound, Ticket, Warning, StarFilled } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('info')

const form = reactive({
  nickname: userStore.nickname || '',
  phone: userStore.phone || '',
  email: ''
})

const pwdForm = reactive({
  oldPwd: '',
  newPwd: '',
  confirmPwd: ''
})

async function loadProfile() {
  loading.value = true
  try {
    const data = await userStore.fetchProfile()
    if (data) {
      form.nickname = data.nickname || userStore.nickname || ''
      form.phone = data.phone || userStore.phone || ''
      form.email = data.email || ''
    }
  } finally {
    loading.value = false
  }
}

async function handleUpdate() {
  try {
    await updateUserProfile({ ...form })
    userStore.setUserInfo({ nickname: form.nickname, phone: form.phone })
    ElMessage.success('保存成功')
  } catch (e) {}
}

async function handleChangePwd() {
  if (pwdForm.newPwd !== pwdForm.confirmPwd) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    ElMessage.success('密码修改成功（演示接口）')
    pwdForm.oldPwd = ''
    pwdForm.newPwd = ''
    pwdForm.confirmPwd = ''
  } catch (e) {}
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { min-height: 100vh; }
.container { max-width: 1080px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 24px 20px 60px; }

.card { background: var(--bg-card); border: 1px solid var(--border-base); border-radius: var(--radius-lg); padding: 24px; margin-bottom: 16px; box-shadow: var(--shadow-sm); }

.user-card { display: flex; align-items: center; gap: 24px; }
.avatar {
  width: 80px; height: 80px;
  border-radius: 50%;
  background: var(--brand-primary);
  color: var(--text-on-primary);
  font-size: 32px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}
.nickname { font-size: 22px; color: var(--text-primary); margin: 0 0 8px; }
.meta { color: var(--text-muted); font-size: 14px; }

.profile-tabs { background: var(--bg-card); border: 1px solid var(--border-base); border-radius: var(--radius-lg); padding: 8px 24px 24px; }

.quick-links {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  padding: 24px;
}
.link-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}
.link-item:hover { background: var(--brand-primary-soft); border-color: var(--brand-primary-border); transform: var(--hover-lift); }
.link-item span { color: var(--text-secondary); font-size: 14px; }
</style>
