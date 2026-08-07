<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="title">星耀商城</h1>
      <p class="subtitle">{{ loginMode === 'password' ? '欢迎回来，请登录您的账号' : '输入邮箱获取验证码' }}</p>

      <!-- 登录方式切换 -->
      <div class="login-tabs">
        <span class="tab" :class="{ active: loginMode === 'password' }" @click="switchMode('password')">密码登录</span>
        <span class="tab" :class="{ active: loginMode === 'email' }" @click="switchMode('email')">验证码登录</span>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="login-form">
        <!-- 密码模式：邮箱 -->
        <el-form-item v-if="loginMode === 'password'" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" size="large" autocomplete="email" />
        </el-form-item>

        <!-- 验证码模式：邮箱 -->
        <el-form-item v-if="loginMode === 'email'" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" size="large" autocomplete="email" />
        </el-form-item>

        <!-- 密码模式 -->
        <el-form-item v-if="loginMode === 'password'" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password />
        </el-form-item>

        <!-- 验证码模式 -->
        <el-form-item v-if="loginMode === 'email'" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="请输入验证码" size="large" style="flex:1" />
            <el-button
              class="code-btn"
              :disabled="codeCountdown > 0"
              :loading="sendingCode"
              @click="sendCode"
              size="large"
            >
              {{ codeCountdown > 0 ? `${codeCountdown}s 后重发` : '获取验证码' }}
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
const sendingCode = ref(false)
const codeCountdown = ref(0)
let timer = null

const form = reactive({
  email: '',
  password: '',
  code: ''
})

const emailRule = { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }

const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, emailRule],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

/**
 * 切换登录方式（密码登录 / 验证码登录）
 * @param {'password'|'email'} mode - 登录模式
 */
function switchMode(mode) {
  loginMode.value = mode
  form.code = ''
  form.password = ''
}

/**
 * 发送邮箱验证码，含 60 秒倒计时
 * 需先校验邮箱格式
 */
async function sendCode() {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sendingCode.value = true
  try {
    const res = await userRequest({
      url: '/user/email-code/send',
      method: 'post',
      data: { email: form.email, type: 'LOGIN' },
      __silent: true
    })
    if (res && res.devCode) {
      // 开发模式（未配置 SMTP）：接口直接返回验证码，自动填入并提示
      form.code = res.devCode
      ElMessage.success(`开发模式验证码：${res.devCode}（已自动填入）`)
    } else {
      ElMessage.success('验证码已发送，请查收邮箱')
    }
    codeCountdown.value = 60
    timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    ElMessage.error('发送失败，请重试')
  } finally {
    sendingCode.value = false
  }
}

/**
 * 处理登录提交
 * 根据当前登录模式调用密码登录或邮箱验证码登录，成功后跳转至重定向地址
 */
async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    if (loginMode.value === 'email') {
      await userStore.emailLogin({ email: form.email, type: 'LOGIN', code: form.code })
    } else {
      await userStore.login({ email: form.email, password: form.password })
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

.code-row { display: flex; gap: 10px; }
.code-btn {
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