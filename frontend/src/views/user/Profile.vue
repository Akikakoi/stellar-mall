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
            <span>用户ID：{{ userStore.userId || '-' }}</span>
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
              <el-form-item label="邮箱">
                <div class="email-row">
                  <el-input v-model="form.email" placeholder="选填" disabled class="email-input" />
                  <el-button type="primary" plain @click="openEmailDialog">修改邮箱</el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleUpdate">保存修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="安全设置" name="security">
          <div class="card">
            <el-form label-width="120px" style="max-width: 500px;">
              <el-form-item v-if="!useEmailCode" label="原密码">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前登录密码" />
              </el-form-item>
              <el-form-item v-else label="邮箱验证码">
                <div class="code-row">
                  <el-input v-model="pwdForm.code" placeholder="验证码" maxlength="6" />
                  <el-button :disabled="pwdCodeCountdown > 0" :loading="sendingPwdCode" @click="handleSendPwdCode">
                    {{ pwdCodeCountdown > 0 ? pwdCodeCountdown + 's 后重发' : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="changingPwd" @click="handleChangePwd">修改密码</el-button>
                <el-button link type="primary" @click="togglePwdVerifyMode">
                  {{ useEmailCode ? '改用原密码验证' : '忘记原密码？用邮箱验证码' }}
                </el-button>
              </el-form-item>
              <p v-if="useEmailCode" class="email-tip">
                验证码将发送到当前登录邮箱 {{ form.email || '（未绑定）' }}，5 分钟内有效；适用于邮箱验证码注册、从未设置过密码的账号。
              </p>
            </el-form>
          </div>

          <div class="card danger-card">
            <h3 class="danger-title">注销账号</h3>
            <p class="danger-desc">注销后账号将无法再登录，历史订单等数据将保留但无法访问。</p>
            <el-button type="danger" plain :loading="deactivating" @click="handleDeactivate">
              注销账号
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </main>

    <!-- 修改登录邮箱弹窗：先向新邮箱发验证码，校验通过 + 新邮箱未被占用才允许提交 -->
    <el-dialog
      v-model="emailDialogVisible"
      title="修改登录邮箱"
      width="440px"
      class="glass-dialog"
      modal-class="glass-overlay"
      :close-on-click-modal="false"
      @closed="resetEmailDialog"
    >
      <el-form label-width="90px">
        <el-form-item label="当前邮箱">
          <span class="current-email">{{ form.email || '未绑定' }}</span>
        </el-form-item>
        <el-form-item label="新邮箱" required>
          <el-input v-model="emailForm.newEmail" placeholder="请输入新邮箱地址" clearable />
        </el-form-item>
        <el-form-item label="验证码" required>
          <div class="code-row">
            <el-input v-model="emailForm.code" placeholder="邮箱验证码" maxlength="6" />
            <el-button :disabled="emailCodeCountdown > 0" :loading="sendingCode" @click="handleSendEmailCode">
              {{ emailCodeCountdown > 0 ? emailCodeCountdown + 's 后重发' : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <p class="email-tip">验证码将发送到新邮箱，5 分钟内有效；需通过验证码校验且新邮箱未被注册后才能完成修改。</p>
      <template #footer>
        <el-button @click="emailDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingEmail" @click="handleChangeEmail">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { updateUserProfile, deactivateAccount, sendEmailChangeCode, changeEmail, sendPasswordChangeCode, updatePassword } from '@/api/mall'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 基本信息表单 */
interface ProfileForm {
  nickname: string
  email: string
}

/** 修改密码表单 */
interface PwdForm {
  oldPassword: string
  code: string
  newPassword: string
  confirmPassword: string
}

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const deactivating = ref(false)
const activeTab = ref('info')

const form = reactive<ProfileForm>({
  nickname: userStore.nickname || '',
  email: ''
})

// ======================== 验证码倒计时 ========================
/**
 * 验证码倒计时（发码按钮 60 秒冷却）。
 * 每个验证码流程各持有一份实例，互不干扰；组件卸载时自动清理定时器。
 */
function useCodeCountdown() {
  const countdown = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null
  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }
  function start(seconds = 60) {
    stop()
    countdown.value = seconds
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) stop()
    }, 1000)
  }
  /** 停止倒计时并归零 */
  function reset() {
    stop()
    countdown.value = 0
  }
  onUnmounted(stop)
  return { countdown, start, reset }
}

const {
  countdown: emailCodeCountdown,
  start: startEmailCodeCountdown,
  reset: resetEmailCodeCountdown
} = useCodeCountdown()

const {
  countdown: pwdCodeCountdown,
  start: startPwdCodeCountdown,
  reset: resetPwdCodeCountdown
} = useCodeCountdown()

// ======================== 修改登录邮箱 ========================
const emailDialogVisible = ref(false)
const sendingCode = ref(false)
const changingEmail = ref(false)

const emailForm = reactive<{ newEmail: string, code: string }>({
  newEmail: '',
  code: ''
})

/** 打开修改邮箱弹窗（表单清空与倒计时重置统一由弹窗 @closed → resetEmailDialog 负责） */
function openEmailDialog() {
  emailDialogVisible.value = true
}

/** 弹窗关闭后重置状态（倒计时/表单） */
function resetEmailDialog() {
  emailForm.newEmail = ''
  emailForm.code = ''
  resetEmailCodeCountdown()
  sendingCode.value = false
  changingEmail.value = false
}

/** 校验新邮箱格式且不与当前邮箱相同 */
function validateNewEmail(): boolean {
  const email = emailForm.newEmail.trim()
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('请输入正确的新邮箱地址')
    return false
  }
  if (email.toLowerCase() === (form.email || '').toLowerCase()) {
    ElMessage.warning('新邮箱不能与当前邮箱相同')
    return false
  }
  return true
}

/** 向新邮箱发送验证码，60 秒倒计时；开发模式自动回填验证码 */
async function handleSendEmailCode() {
  if (!validateNewEmail()) return
  sendingCode.value = true
  try {
    const res: any = await sendEmailChangeCode(emailForm.newEmail.trim())
    if (res && res.devCode) {
      // 开发模式（未配置 SMTP）：接口直接返回验证码，自动填入并提示
      emailForm.code = res.devCode
      ElMessage.success(`开发模式验证码：${res.devCode}（已自动填入）`)
    } else {
      ElMessage.success('验证码已发送，请查收新邮箱')
    }
    startEmailCodeCountdown()
  } catch (e: any) {
    // 拦截器已统一提示（如"该邮箱已被注册"）
  } finally {
    sendingCode.value = false
  }
}

/** 提交修改：后端校验验证码 + 新邮箱未被占用后才更新 */
async function handleChangeEmail() {
  if (!validateNewEmail()) return
  if (!emailForm.code.trim()) {
    ElMessage.warning('请输入邮箱验证码')
    return
  }
  changingEmail.value = true
  try {
    await changeEmail(emailForm.newEmail.trim(), emailForm.code.trim())
    ElMessage.success('登录邮箱修改成功')
    emailDialogVisible.value = false
    // 表单邮箱同步为新邮箱（昵称未变，store 无需更新）
    form.email = emailForm.newEmail.trim()
  } catch (e: any) {
    // 拦截器已统一提示（如"该邮箱已被注册"/"验证码错误或已过期"）
  } finally {
    changingEmail.value = false
  }
}

// ======================== 修改登录密码 ========================
/** 是否改用邮箱验证码验证（邮箱验证码注册、从未设置过密码的账号走这条路） */
const useEmailCode = ref(false)
const sendingPwdCode = ref(false)
const changingPwd = ref(false)

const pwdForm = reactive<PwdForm>({
  oldPassword: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

/** 在原密码验证 / 邮箱验证码验证之间切换 */
function togglePwdVerifyMode() {
  useEmailCode.value = !useEmailCode.value
}

/** 清空改密表单并切回原密码验证 */
function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.code = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  useEmailCode.value = false
  resetPwdCodeCountdown()
}

/** 发送修改密码验证码到当前登录邮箱，60 秒倒计时；开发模式自动回填验证码 */
async function handleSendPwdCode() {
  if (!form.email) {
    ElMessage.warning('当前账号未绑定邮箱，无法使用邮箱验证码验证')
    return
  }
  sendingPwdCode.value = true
  try {
    const res: any = await sendPasswordChangeCode()
    if (res && res.devCode) {
      // 开发模式（未配置 SMTP）：接口直接返回验证码，自动填入并提示
      pwdForm.code = res.devCode
      ElMessage.success(`开发模式验证码：${res.devCode}（已自动填入）`)
    } else {
      ElMessage.success(`验证码已发送至 ${form.email}`)
    }
    startPwdCodeCountdown()
  } catch (e: any) {
    // 拦截器已统一提示
  } finally {
    sendingPwdCode.value = false
  }
}

/** 校验并提交修改密码：原密码 / 邮箱验证码二选一，新密码 6-32 位且两次一致 */
async function handleChangePwd() {
  const newPassword = pwdForm.newPassword.trim()
  if (newPassword.length < 6 || newPassword.length > 32) {
    ElMessage.warning('密码长度需为 6-32 位')
    return
  }
  if (newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (useEmailCode.value) {
    if (!pwdForm.code.trim()) {
      ElMessage.warning('请输入邮箱验证码')
      return
    }
  } else if (!pwdForm.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }

  changingPwd.value = true
  try {
    await updatePassword(
      useEmailCode.value
        ? { code: pwdForm.code.trim(), newPassword }
        : { oldPassword: pwdForm.oldPassword, newPassword }
    )
    ElMessage.success('密码修改成功')
    resetPwdForm()
  } catch (e: any) {
    // 拦截器已统一提示（如"密码错误"/"验证码错误或已过期"）
  } finally {
    changingPwd.value = false
  }
}

/** 从服务端加载用户资料并同步到本地表单和 store */
async function loadProfile() {
  loading.value = true
  try {
    const data = await userStore.fetchProfile()
    if (data) {
      form.nickname = data.nickname || userStore.nickname || ''
      form.email = data.email || ''
    }
  } finally {
    loading.value = false
  }
}

/** 提交更新用户基本信息（昵称；邮箱走独立验证码流程，不在此提交） */
async function handleUpdate() {
  try {
    await updateUserProfile({ nickname: form.nickname })
    userStore.setUserInfo({ nickname: form.nickname })
    ElMessage.success('保存成功')
  } catch (e: any) {}
}

/** 注销账号：二次确认后调用后端接口，成功后退出登录并跳转登录页 */
async function handleDeactivate() {
  try {
    await ElMessageBox.confirm(
      '注销后账号将无法再登录，此操作不可恢复。确定要注销当前账号吗？',
      '注销账号',
      {
        type: 'warning',
        confirmButtonText: '确认注销',
        cancelButtonText: '再想想',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (e: any) {
    return // 用户取消
  }
  deactivating.value = true
  try {
    await deactivateAccount()
    userStore.logout()
    ElMessage.success('账号已注销')
    router.push('/login')
  } catch (e: any) {
    // error shown
  } finally {
    deactivating.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { min-height: 100vh; }
.container { max-width: 1080px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 24px 20px 60px; }

.card {
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--radius-lg);
  padding: 24px;
  margin-bottom: 16px;
  box-shadow:
    0 2px 12px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6));
}
:global(html.theme-dark) .card {
  background: rgba(29, 29, 31, 0.45);
  box-shadow:
    0 2px 12px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.1));
}

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

.profile-tabs {
  background: var(--glass-bg, rgba(255, 255, 255, 0.72));
  backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
  -webkit-backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--radius-lg);
  padding: 8px 24px 24px;
  box-shadow:
    var(--glass-shadow, 0 8px 32px rgba(0, 0, 0, 0.06)),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6));
}

.danger-card {
  border-color: var(--el-color-danger-light-7);
}
.danger-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--el-color-danger);
}
.danger-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-muted);
}
/* 邮箱行：只读输入框 + 修改按钮 */
.email-row { display: flex; gap: 12px; width: 100%; }
.email-row .email-input { flex: 1; }

/* 修改邮箱弹窗内布局 */
.current-email { color: var(--text-secondary); font-size: 14px; word-break: break-all; }
.code-row { display: flex; gap: 12px; width: 100%; }
.code-row .el-input { flex: 1; }
.email-tip { margin: 0; padding: 0 8px; font-size: 12px; color: var(--text-muted); line-height: 1.6; }
</style>
