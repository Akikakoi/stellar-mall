<template>
  <div class="register-page">
    <div class="register-card">
      <h1 class="title">注册新账号</h1>
      <p class="subtitle">加入星耀商城，开启购物之旅</p>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="register-form">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" size="large" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称（可选）" size="large" />
        </el-form-item>
        <el-form-item prop="code">
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
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="请输入图形验证码"
              size="large"
              style="flex:1"
              autocomplete="off"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              alt="图形验证码"
              class="captcha-img"
              title="点击刷新"
              @click="refreshCaptcha"
            />
            <div v-else class="captcha-placeholder" @click="refreshCaptcha">点击加载</div>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
        <div class="links">
          <router-link to="/login">← 返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userRequest } from '@/api/request'
import { getCaptcha } from '@/api/mall'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<any>(null)
const loading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
let timer: number | null = null

// 图形验证码状态
const captchaId = ref('')
const captchaImage = ref('')

const form = reactive<any>({
  email: '',
  nickname: '',
  code: '',
  captchaCode: ''
})

const emailRule: Record<string, any> = { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }

const rules: Record<string, any> = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, emailRule],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
}

/** 拉取图形验证码图片 */
async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.captchaId
    captchaImage.value = res.imageBase64
    form.captchaCode = ''
  } catch (e: any) {
    // 拦截器已统一提示
  }
}

onMounted(() => {
  refreshCaptcha()
})

/**
 * 发送邮箱注册验证码，含 60 秒倒计时
 * 需先校验邮箱格式
 */
async function sendCode() {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sendingCode.value = true
  try {
    const res: any = await userRequest({
      url: '/user/email-code/send',
      method: 'post',
      data: { email: form.email, type: 'REGISTER' },
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
      if (codeCountdown.value <= 0) clearInterval(timer!)
    }, 1000)
  } catch (e: any) {
    ElMessage.error('发送失败，请重试')
  } finally {
    sendingCode.value = false
  }
}

/**
 * 处理注册提交：图形验证码 + 邮箱验证码校验通过后自动注册并登录，
 * 若填写了昵称则同步更新
 */
async function handleRegister() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e: any) {
    return
  }
  loading.value = true
  try {
    await userStore.emailLogin({
      email: form.email,
      type: 'REGISTER',
      code: form.code,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode
    })
    if (form.nickname) {
      try {
        await userRequest({
          url: '/user/user/profile',
          method: 'put',
          data: { nickname: form.nickname }
        })
      } catch (e: any) {
        // 昵称更新失败不阻断注册流程
      }
    }
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    // 注册失败时刷新图形验证码（一次性使用），让用户重试
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.register-card {
  width: 420px;
  padding: 56px 48px;
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
  letter-spacing: -0.02em;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.subtitle {
  text-align: center;
  color: var(--text-secondary);
  font-size: 15px;
  margin-bottom: 40px;
}

.code-row { display: flex; gap: 10px; }
.code-btn {
  white-space: nowrap;
  min-width: 120px;
  font-size: 14px;
}
.captcha-row { display: flex; gap: 10px; align-items: center; }
.captcha-img {
  height: 40px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid var(--border-base);
}
.captcha-placeholder {
  height: 40px;
  min-width: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border-base);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
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
  text-align: center;
  font-size: 14px;
}

.links a {
  color: var(--brand-primary);
}
</style>