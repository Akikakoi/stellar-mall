<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="title">星耀商城</h1>
      <p class="subtitle">{{ loginMode === 'password' ? '欢迎回来，请登录您的账号' : '输入手机号获取验证码' }}</p>

      <!-- 登录方式切换 -->
      <div class="login-tabs">
        <span class="tab" :class="{ active: loginMode === 'password' }" @click="switchMode('password')">密码登录</span>
        <span class="tab" :class="{ active: loginMode === 'sms' }" @click="switchMode('sms')">验证码登录</span>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="login-form">
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large" />
        </el-form-item>

        <!-- 密码模式 -->
        <el-form-item v-if="loginMode === 'password'" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password />
        </el-form-item>

        <!-- 验证码模式 -->
        <el-form-item v-if="loginMode === 'sms'" prop="code">
          <div class="sms-row">
            <el-input v-model="form.code" placeholder="请输入验证码" size="large" style="flex:1" />
            <el-button
              class="sms-btn"
              :disabled="smsCountdown > 0"
              :loading="sendingSms"
              @click="sendSms"
              size="large"
            >
              {{ smsCountdown > 0 ? `${smsCountdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
          {{ loginMode === 'password' ? '登 录' : '登录 / 注册' }}
        </el-button>
        <div class="links">
          <router-link to="/register">注册新账号</router-link>
          <router-link to="/admin/login">管理员登录 &rarr;</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userRequest } from '@/api/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const loginMode = ref('password')
const sendingSms = ref(false)
const smsCountdown = ref(0)
let timer = null

const form = reactive({
  phone: '',
  password: '',
  code: ''
})

const rules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

function switchMode(mode) {
  loginMode.value = mode
  form.code = ''
  form.password = ''
}

async function sendSms() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sendingSms.value = true
  try {
    await userRequest({
      url: '/user/sms/send',
      method: 'post',
      data: { phone: form.phone, type: 'LOGIN' },
      __silent: true
    })
    ElMessage.success('验证码已发送')
    smsCountdown.value = 60
    timer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error('发送失败，请重试')
  } finally {
    sendingSms.value = false
  }
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    if (loginMode.value === 'sms') {
      await userStore.smsLogin({ phone: form.phone, type: 'LOGIN', code: form.code })
    } else {
      await userStore.login({ phone: form.phone, password: form.password })
    }
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-base);
  padding: 40px 20px;
}
.login-card {
  width: 420px;
  padding: 40px 48px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  animation: fadeInUp 0.5s ease-out;
}
.title {
  text-align: center;
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 10px;
}
.subtitle {
  text-align: center;
  color: var(--text-secondary);
  font-size: 15px;
  margin-bottom: 24px;
}
.login-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 28px;
  border-bottom: 2px solid var(--border-base);
}
.tab {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 15px;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all .2s;
  user-select: none;
}
.tab:hover { color: var(--brand-primary); }
.tab.active {
  color: var(--brand-primary);
  border-bottom-color: var(--brand-primary);
  font-weight: 600;
}

.sms-row { display: flex; gap: 10px; }
.sms-btn {
  white-space: nowrap;
  min-width: 120px;
  font-size: 14px;
}

.submit-btn {
  width: 100%;
  margin-top: 16px;
  margin-bottom: 20px;
  height: 48px;
  font-size: 16px;
  border-radius: var(--radius-md);
}
.links {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}
.links a { color: var(--brand-primary); }
</style>
