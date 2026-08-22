<template>
  <div class="admin-login-page">
    <div class="admin-login-card">
      <div class="logo-area">
        <div class="logo-icon">S</div>
        <h1 class="title">星耀商城 · 管理后台</h1>
        <p class="subtitle">Stellar Mall Admin Console</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="login-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
        <div class="links">
          <router-link to="/login">← 返回 C 端登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) { return }
  loading.value = true
  try {
    await adminStore.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/admin/dashboard'
    router.push(redirect)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-base);
  background-image: url('/images/background-light.webp');
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  background-repeat: no-repeat;
  padding: 40px 20px;
  position: relative;
}

/* 与商城全站 page-bg 一致的半透明遮罩，保证卡片可读性 */
.admin-login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: var(--bg-base);
  opacity: 0.55;
}

.admin-login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 56px 48px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  animation: fadeInUp 0.5s ease-out;
}

.logo-area { text-align: center; margin-bottom: 40px; }
.logo-icon {
  width: 64px; height: 64px;
  margin: 0 auto 16px;
  background: var(--brand-primary);
  color: #fff;
  font-size: 32px;
  font-weight: 700;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.title { font-size: 24px; font-weight: 700; color: var(--text-primary); margin: 0 0 6px; }
.subtitle { color: var(--text-secondary); font-size: 14px; margin: 0; }

.submit-btn {
  width: 100%;
  margin-top: 16px;
  margin-bottom: 20px;
  height: 48px;
  font-size: 16px;
  border-radius: var(--radius-md);
}
.links { text-align: center; font-size: 14px; }
.links a { color: var(--brand-primary); }
</style>
