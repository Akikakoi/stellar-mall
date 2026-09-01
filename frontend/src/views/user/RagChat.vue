<template>
  <div class="layout">

    <aside class="sidebar">
      <div class="sb-head">
        <div class="brand" @click="goHome">
          <div class="brand-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="brand-text">
            <span class="brand-title">智电 AI 助手</span>
            <span class="brand-subtitle">你的智能购物顾问</span>
          </div>
        </div>
        <el-button class="new-chat" @click="startNew" :icon="Plus">
          <span>新建对话</span>
        </el-button>
      </div>

      <div class="sb-body" v-loading="loadingList">
        <div
          v-for="c in chatStore.conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === chatStore.activeId }"
          @click="switchConv(c.id)"
        >
          <div class="conv-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <span class="ci-title" :title="c.title">{{ c.title }}</span>
          <div class="ci-ops" @click.stop>
            <el-popconfirm title="确定重命名该会话？" @confirm="onRename(c)">
              <template #reference>
                <button class="op-btn rename-btn">
                  <el-icon :size="13"><Edit /></el-icon>
                </button>
              </template>
            </el-popconfirm>
            <el-popconfirm title="删除后将无法恢复，确定？" @confirm="onDelete(c.id)">
              <template #reference>
                <button class="op-btn delete-btn">
                  <el-icon :size="13"><Delete /></el-icon>
                </button>
              </template>
            </el-popconfirm>
          </div>
        </div>
        <div v-if="!chatStore.conversations.length && !loadingList" class="empty">
          <el-icon :size="32"><ChatLineRound /></el-icon>
          <p>暂无会话，开始新对话吧～</p>
        </div>
      </div>

      <div class="sb-foot">
        <router-link to="/" class="nav-item nav-back-btn">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回商城</span>
        </router-link>
        <router-link v-if="userStore.isAdmin" to="/admin/kb" class="nav-item nav-admin-btn">
          <el-icon><Collection /></el-icon>
          <span style="flex:1">知识库管理</span>
          <el-tag size="small" type="danger" effect="dark">管理员</el-tag>
        </router-link>
        <router-link v-if="userStore.isAdmin" to="/admin/dashboard" class="nav-item">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </router-link>
        <router-link to="/me" class="nav-item">
          <el-icon><User /></el-icon>
          <span>个人中心</span>
        </router-link>
        <div class="nav-item logout" @click="onLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <header class="topbar">
        <div class="topbar-left">
          <div class="conv-title">
            <el-icon v-if="currentConvTitle"><ChatDotRound /></el-icon>
            <span v-if="currentConvTitle">{{ currentConvTitle }}</span>
            <span v-else class="muted">新的对话</span>
          </div>
        </div>
        <div class="user-zone">
          <el-tag :type="userStore.isAdmin ? 'danger' : 'success'" effect="dark" size="small" class="role-tag">
            {{ userStore.roleLabel }}
          </el-tag>
          <el-dropdown trigger="click" @command="onUserCmd">
            <span class="user-info">
              <el-avatar :size="32" class="user-avatar">
                {{ userStore.userInfo?.nickname?.[0] || userStore.userInfo?.username?.[0] || 'A' }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="userStore.isAdmin" command="kb"><el-icon><Collection /></el-icon>知识库管理</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="dash"><el-icon><DataAnalysis /></el-icon>数据概览</el-dropdown-item>
                <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="msgs" ref="msgsRef" v-loading="loadingMsgs">
        <!-- 欢迎页 -->
        <div v-if="!chatStore.messages.length" class="welcome">
          <div class="welcome-content">
            <div class="wl-icon-wrap">
              <div class="wl-icon-inner">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="wl-glow"></div>
            </div>
            <h2 class="hero-title">
              <span class="gradient-text">你好，我是智电优选 AI 助手</span>
            </h2>
            <p class="hero-desc">我能帮你解答平台商品的规格、价格、售后政策等问题</p>
            <div class="prompts">
              <div class="prompt" v-for="(p, idx) in prompts" :key="p" @click="send(p)" :style="{ animationDelay: `${idx * 0.06}s` }">
                <div class="prompt-icon">
                  <el-icon><Promotion /></el-icon>
                </div>
                <span class="prompt-text">{{ p }}</span>
                <el-icon class="prompt-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <template v-else>
          <ChatMessage
            v-for="m in chatStore.messages"
            :key="m.id"
            :msg="m"
            :conversation-id="chatStore.activeId as any"
            :current-query="currentQueryOf(m)"
          />
        </template>

        <!-- 思考状态 -->
        <div v-if="chatStore.loading && !lastAssistantStreaming" class="thinking">
          <div class="thinking-inner">
            <div class="thinking-dots">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <span class="thinking-text">正在检索知识库并生成回答...</span>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-bar" :class="{ focused: inputFocused }">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="向 AI 提问商品的任何问题，按 Enter 发送，Shift+Enter 换行..."
            @keydown.enter.exact.prevent="onSend"
            @focus="inputFocused = true"
            @blur="inputFocused = false"
            :disabled="chatStore.loading"
          />
          <div class="input-actions">
            <el-button
              type="primary"
              class="send-btn"
              :icon="chatStore.loading ? Loading : ArrowUp"
              :loading="chatStore.loading"
              @click="onSend"
              :disabled="!input.trim()"
            />
          </div>
        </div>
        <p class="tip">
          <el-icon><Warning /></el-icon>
          <span>回答以知识库内容为准，如遇不准确请联系人工客服。</span>
        </p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import {
  Plus, Edit, Delete, ChatDotRound, DataAnalysis, User, SwitchButton,
  ArrowDown, ArrowLeft, Collection, Promotion, ArrowUp, Loading, Warning,
  ArrowRight, ChatLineRound,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import ChatMessage from '@/components/ChatMessage.vue'
import { getAccessToken, ragRequest } from '@/api/request'
import { storage } from '@/utils/storage'

const userStore = useUserStore()
const chatStore = useChatStore()
const router = useRouter()
const route = useRoute()
let activeController: any = null
let leaving = false

const input = ref('')
const msgsRef = ref<any>(null)
const loadingList = ref(false)
const loadingMsgs = ref(false)
const inputFocused = ref(false)
const lastAssistantStreaming = computed(() =>
  chatStore.messages.some((m: any) => m.streaming)
)

const currentConvTitle = computed(() => {
  const c = chatStore.conversations.find((x: any) => x.id === chatStore.activeId)
  return c?.title
})

const prompts = [
  '星耀 X100 Pro 多少钱？有哪些配置版本？',
  '清逸 Book Air 14 续航和接口介绍一下',
  '极净 BCD-520W 冰箱的保修政策是什么？',
  '御风空调质保多久？有没有免费安装？',
  '逸彩 65Q75 画质参数和售后亮点',
  '平台手机 7 天无理由退货的条件？',
  '帮我查一下订单状态',
  '我要申请售后退货',
]

/**
 * 获取助手消息对应的用户问题文本
 * @param {Object} m - 消息对象
 * @returns {string} 用户问题内容
 */
function currentQueryOf(m: any) {
  if (m.role !== 'assistant') return ''
  const idx = chatStore.messages.indexOf(m)
  for (let i = idx - 1; i >= 0; i--) {
    if (chatStore.messages[i]!.role === 'user') return chatStore.messages[i]!.content
  }
  return ''
}

/**
 * 将当前活跃会话 ID 同步到 URL 路由参数
 * 地址栏无变化时跳过，避免重复 replace
 */
function syncUrlWithActiveId() {
  if (leaving) return
  const urlId = route.params.conversationId || null
  const activeId = chatStore.activeId || null
  if (String(urlId || '') === String(activeId || '')) return
  const target = activeId ? `/rag/chat/${activeId}` : '/rag/chat'
  router.replace(target).catch(() => {})
}

/**
 * 根据 URL 中的 conversationId 参数恢复活跃会话
 * 如果会话列表未加载则先拉取，存在则设置为当前活跃会话
 */
async function ensureActiveFromUrl() {
  const urlId = route.params.conversationId
  if (!urlId) return
  if (!chatStore.conversations.length) await fetchConvs()
  const exists = chatStore.conversations.some((c: any) => String(c.id) === String(urlId))
  if (exists && String(chatStore.activeId || '') !== String(urlId)) {
    chatStore.setActive(String(urlId) as any)
  }
}

/**
 * 滚动消息列表至底部
 * @param {boolean} [force=false] - 预留参数，当前未使用
 */
function scrollToBottom(force = false) {
  nextTick(() => {
    if (msgsRef.value) {
      msgsRef.value.scrollTop = msgsRef.value.scrollHeight
    }
  })
}

/**
 * 从后端拉取会话列表并写入 store
 */
async function fetchConvs() {
  loadingList.value = true
  try { await chatStore.fetchConversations() }
  catch (e: any) { /* 静默处理，不影响主流程 */ }
  finally { loadingList.value = false }
}

/**
 * 加载当前活跃会话的消息列表
 * 若加载失败则清空消息并回退到新会话状态
 */
async function loadActiveMsgs() {
  if (!chatStore.activeId) { chatStore.messages = []; return }
  loadingMsgs.value = true
  try { await chatStore.loadActive() }
  catch (e: any) {
    console.warn('[RagChat] 加载消息失败，跳转到新会话:', e)
    chatStore.messages = []
    chatStore.activeId = null
    syncUrlWithActiveId()
    if (chatStore.conversations.length > 0) {
      chatStore.setActive(chatStore.conversations[0]!.id)
    }
  }
  finally { loadingMsgs.value = false; scrollToBottom(true) }
}

// 同步设置，避免 onMounted 异步执行时 scrollbar 已参与 100vh 计算导致底部空白
document.body.style.overflow = 'hidden'

/**
 * 根据窗口高度动态计算并设置布局 CSS 变量
 * 用于响应窗口大小变化，保持聊天界面正确的高度
 */
function updateLayoutHeight() {
  const headerHeight = 64
  document.documentElement.style.setProperty('--rag-layout-height', `${window.innerHeight - headerHeight}px`)
}

onMounted(async () => {
  leaving = false
  updateLayoutHeight()
  window.addEventListener('resize', updateLayoutHeight)
  try {
    await fetchConvs()
  } catch (e: any) { /* 网络异常不影响页面加载 */ }
  const urlId = route.params.conversationId
  if (urlId) {
    await ensureActiveFromUrl()
  }
  if (!chatStore.activeId && chatStore.conversations.length) {
    chatStore.setActive(chatStore.conversations[0]!.id)
  }
  syncUrlWithActiveId()
  try {
    await loadActiveMsgs()
  } catch (e: any) { /* 加载消息失败不影响页面 */ }
})

// 路由离开前标记 leaving，不再中止 SSE 连接
// SSE 会在后台继续流式传输，确保消息不丢失
onBeforeRouteLeave((to: any, from: any, next: any) => {
  leaving = true
  document.body.style.overflow = ''
  next()
})

onUnmounted(() => {
  document.body.style.overflow = ''
})

onBeforeUnmount(() => {
  leaving = true
  window.removeEventListener('resize', updateLayoutHeight)
  // 不再中止 SSE，让后台流式传输自然完成
  document.body.style.overflow = ''
})

watch(
  () => route.params.conversationId,
  () => {
    ensureActiveFromUrl()
  },
)

const loadingMsgRef = ref(false)
watch(() => chatStore.activeId, () => {
  if (leaving) return
  syncUrlWithActiveId()
  if (!loadingMsgRef.value) {
    loadActiveMsgs()
  }
})

/**
 * 创建新的对话会话
 */
async function startNew() {
  await chatStore.createConversation('新的对话')
  input.value = ''
  scrollToBottom(true)
}

/**
 * 切换到指定会话
 * @param {string} id - 会话 ID
 */
async function switchConv(id: any) {
  if (id === chatStore.activeId) return
  chatStore.setActive(id)
  input.value = ''
}

/**
 * 弹出对话框重命名会话
 * @param {Object} c - 会话对象
 */
async function onRename(c: any) {
  try {
    const { value } = await ElMessageBox.prompt('会话标题', '重命名', {
      inputValue: c.title, inputPattern: /.+/, inputErrorMessage: '不能为空',
    })
    await chatStore.rename(c.id, value)
  } catch {}
}
/**
 * 删除指定会话
 * @param {string} id - 会话 ID
 */
async function onDelete(id: any) {
  await chatStore.remove(id)
  input.value = ''
  scrollToBottom(true)
}

/**
 * 处理用户下拉菜单命令，分发路由跳转或退出登录
 * @param {string} cmd - 命令标识 ('profile'|'kb'|'dash'|'logout')
 */
function onUserCmd(cmd: any) {
  if (cmd === 'profile') router.push('/me')
  else if (cmd === 'kb') router.push('/admin/kb')
  else if (cmd === 'dash') router.push('/admin/dashboard')
  else if (cmd === 'logout') onLogout()
}
/**
 * 退出登录，清除用户状态并跳转至登录页
 */
function onLogout() {
  userStore.logout()
  router.replace('/login')
}
/**
 * 返回首页（新建会话状态），清空活跃会话
 */
function goHome() {
  chatStore.setActive(null as any)
  router.replace('/rag/chat').catch(() => {})
}

/**
 * 发送输入框中的消息
 * 校验输入内容后调用 send 进行实际发送
 */
async function onSend() {
  const q = input.value.trim()
  if (!q || chatStore.loading) return
  await send(q)
}

/**
 * 核心发送逻辑：通过 SSE 流式与后端 RAG 服务对话
 * 自动创建会话（如无活跃会话）、发送用户消息、建立 SSE 连接、
 * 实时解析 token 流并更新助手消息，处理 sources / done 等事件
 * @param {string} text - 用户输入的问题文本
 */
async function send(text: any) {
  input.value = text
  if (chatStore.loading) return
  loadingMsgRef.value = true
  try {
    if (!chatStore.activeId) {
      await chatStore.createConversation('新的对话')
    }
  } catch (e: any) {
    loadingMsgRef.value = false
    ElMessage.error('创建会话失败，请重试')
    return
  }
  const currentActiveId = chatStore.activeId
  const query = input.value.trim()
  input.value = ''
  const userMsg = chatStore.pushUserMessage(query)
  const asstId = chatStore.pushAssistantPlaceholder().id
  chatStore.loading = true
  scrollToBottom()
  const controller = new AbortController()
  activeController = controller

  try {
    const token = getAccessToken()
    const headers = new Headers()
    headers.set('Content-Type', 'application/json')
    headers.set('Authorization', `Bearer ${token}`)

    const resp = await fetch('/ragapi/api/chat', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        conversation_id: chatStore.activeId,
        query,
        stream: true,
        use_agent: true,
      }),
      signal: controller.signal,
    })

    if (!resp.ok) {
      throw new Error('请求失败')
    }

    const reader = resp.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let fullContent = ''
    let sourcesData: any[] = []
    let tokensUsed = 0
    let latencyMs = 0
    let mode = 'smart'
    const toolCalls: any[] = []    // ReAct 工具调用链: [{type:'call'|'result', tool, params, success}]
    let reactThinking = ''         // 当前思考(agent_thought),可选展示

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''

      for (const block of parts) {
        if (!block.trim()) continue
        const ev = parseEvent(block)
        if (!ev) continue

        if (ev.event === 'token') {
          const chunk = typeof ev.data === 'string' ? ev.data : (ev.data?.content || '')
          fullContent += chunk
          chatStore.updateAssistant(asstId, {
            content: fullContent,
            streaming: true,
          })
          scrollToBottom()
        } else if (ev.event === 'sources') {
          sourcesData = Array.isArray(ev.data) ? ev.data : []
        } else if (ev.event === 'agent_thought') {
          reactThinking = typeof ev.data === 'string' ? ev.data : (ev.data?.content || '')
          chatStore.updateAssistant(asstId, { thinking: reactThinking, streaming: true })
          scrollToBottom()
        } else if (ev.event === 'tool_call') {
          toolCalls.push({
            type: 'call',
            tool: ev.data?.tool || '',
            params: ev.data?.params || {},
            success: false,
          })
          chatStore.updateAssistant(asstId, { tool_calls: toolCalls, streaming: true })
          scrollToBottom()
        } else if (ev.event === 'tool_result') {
          toolCalls.push({ type: 'result', tool: ev.data?.tool || '', success: !!ev.data?.success })
          chatStore.updateAssistant(asstId, { tool_calls: toolCalls, streaming: true })
          scrollToBottom()
        } else if (ev.event === 'done') {
          if (ev.data && typeof ev.data === 'object') {
            tokensUsed = ev.data.tokens || 0
            latencyMs = ev.data.latency_ms || 0
          }
        }
      }
    }

    chatStore.updateAssistant(asstId, {
      content: fullContent,
      sources: sourcesData,
      tool_calls: toolCalls,
      thinking: '',
      streaming: false,
      tokens_used: tokensUsed,
      latency_ms: latencyMs,
      mode,
    })
    scrollToBottom(true)
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      ElMessage.error('生成失败：' + (e?.message || '未知错误'))
      chatStore.updateAssistant(asstId, {
        content: `❌ 请求失败：${e?.message || '未知错误'}，请重试或稍后再试`,
        streaming: false,
      })
    }
  } finally {
    chatStore.loading = false
    loadingMsgRef.value = false
    if (activeController === controller) activeController = null
    fetchConvs().catch(() => {})
  }
}

/**
 * 解析单个 SSE 事件块，提取 event 和 data 字段
 * data 字段尝试 JSON 解析，失败则保留原始字符串
 * @param {string} block - SSE 原始文本块（event: xxx\ndata: xxx 格式）
 * @returns {{ event: string, data: * }|null} 解析后的事件对象，失败返回 null
 */
function parseEvent(block: any) {
  if (!block) return null
  let event = 'token'; let data: any = null
  const lines = block.split(/\r?\n/)
  for (const line of lines) {
    if (!line) continue
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) {
      try { data = JSON.parse(line.slice(5).trim()) }
      catch { data = line.slice(5).trim() }
    }
  }
  return { event, data }
}
</script>

<style scoped lang="scss">
/* ===== 布局 ===== */
.layout {
  width: 100%;
  height: var(--rag-layout-height, calc(100vh - 64px));
  display: grid;
  grid-template-columns: 280px 1fr;
  grid-template-rows: 1fr;
  background: var(--bg-surface);
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.sidebar {
  grid-row: 1 / -1;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-strong);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.sb-head {
  padding: 18px 16px 14px;
  border-bottom: 1px solid var(--border-strong);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  margin-bottom: 14px;

  &:hover .brand-icon {
    transform: scale(1.05) rotate(-5deg);
  }
}

.brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-active));
  color: #fff;
  font-size: 20px;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.3);
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.brand-subtitle {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.new-chat {
  width: 100%;
  height: 40px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: all 0.2s ease;

  &:hover {
    background: var(--bg-hover);
    border-color: var(--brand-primary);
    color: var(--brand-primary);
    box-shadow: 0 2px 8px rgba(0, 113, 227, 0.1);
  }
}

.sb-body {
  flex: 1;
  padding: 10px 10px;
  overflow: auto;
}

/* 会话列表项 */
.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  margin-bottom: 3px;
  cursor: pointer;
  color: var(--text-secondary);
  position: relative;
  transition: all 0.15s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);

    .ci-ops {
      opacity: 1;
      visibility: visible;
    }
  }

  &.active {
    background: var(--brand-primary-soft);
    color: var(--text-primary);
    border: 1px solid var(--brand-primary-border);

    .conv-icon {
      background: var(--brand-primary);
      color: #fff;
    }
  }
}

.conv-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 13px;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.ci-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.ci-ops {
  display: flex;
  gap: 3px;
  flex-shrink: 0;
  opacity: 0;
  visibility: hidden;
  transition: all 0.15s ease;
}

.op-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  background: var(--bg-card);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 13px;

  &:hover {
    border-color: var(--brand-primary);
    color: var(--brand-primary);
    background: var(--brand-primary-soft);
  }

  &.delete-btn:hover {
    border-color: var(--status-danger);
    color: var(--status-danger);
    background: rgba(255, 59, 48, 0.08);
  }
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-muted);
  padding: 48px 20px;
  font-size: 13px;
  gap: 8px;
}

/* 侧边栏底部导航 */
.sb-foot {
  padding: 10px 12px;
  border-top: 1px solid var(--border-strong);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;
  font-weight: 500;
  transition: all 0.15s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.logout:hover {
    background: rgba(255, 59, 48, 0.08);
    color: var(--status-danger);
  }

  &.nav-back-btn {
    background: var(--bg-card);
    border: 1px solid var(--border-base);
    color: var(--text-primary);
    margin-bottom: 6px;

    &:hover {
      background: var(--bg-hover);
      border-color: var(--brand-primary);
      color: var(--brand-primary);
    }
  }

  &.nav-admin-btn {
    background: var(--brand-primary-soft);
    border: 1px solid var(--brand-primary-border);
    color: var(--text-primary);
    margin-bottom: 6px;

    &:hover {
      background: var(--brand-primary);
      color: var(--text-on-primary);
      box-shadow: 0 4px 12px rgba(0, 113, 227, 0.25);
    }
  }
}

/* ===== 聊天主区域 ===== */
.chat-main {
  grid-row: 1 / -1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

/* 顶部栏 */
.topbar {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
}

.conv-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;

  .el-icon {
    font-size: 16px;
    color: var(--brand-primary);
  }

  .muted {
    color: var(--text-muted);
    font-weight: 500;
  }
}

.user-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 4px 10px;
  border-radius: var(--radius-lg);
  transition: all 0.15s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }
}

.user-avatar {
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-active)) !important;
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 消息区域 ===== */
.msgs {
  flex: 1;
  overflow: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  max-width: 100%;
}

/* ===== 欢迎页 ===== */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
  animation: fadeInUp 0.5s ease-out;
}

.welcome-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 760px;
  width: 100%;
}

.wl-icon-wrap {
  position: relative;
  margin-bottom: 20px;
}

.wl-icon-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--brand-primary), #0056b3);
  color: #fff;
  font-size: 32px;
  position: relative;
  z-index: 2;
  box-shadow: 0 8px 32px rgba(0, 113, 227, 0.3);
  animation: iconFloat 3s ease-in-out infinite;
}

@keyframes iconFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.wl-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.15) 0%, transparent 70%);
  z-index: 1;
  animation: glowPulse 3s ease-in-out infinite;
}

@keyframes glowPulse {
  0%, 100% { opacity: 0.5; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 1; transform: translate(-50%, -50%) scale(1.2); }
}

.hero-title {
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
  margin-bottom: 10px;
}

.gradient-text {
  background: linear-gradient(135deg, var(--brand-primary), #00c6ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  color: var(--text-secondary);
  font-size: 15px;
  margin: 0 0 28px;
}

/* 提示卡片 */
.prompts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  width: 100%;
}

.prompt {
  padding: 14px 16px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  color: var(--text-primary);
  cursor: pointer;
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  line-height: 1.5;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: left;
  animation: promptSlideIn 0.4s ease-out both;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--brand-primary-soft);
    border-color: var(--brand-primary-border);
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 113, 227, 0.12);

    .prompt-arrow {
      transform: translateX(4px);
      color: var(--brand-primary);
    }
  }
}

@keyframes promptSlideIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.prompt-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  font-size: 15px;
  flex-shrink: 0;
}

.prompt-text {
  flex: 1;
  font-weight: 500;
}

.prompt-arrow {
  font-size: 14px;
  color: var(--text-muted);
  transition: all 0.2s ease;
  flex-shrink: 0;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 思考状态 ===== */
.thinking {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
  animation: msgFadeIn 0.3s ease-out;
}

.thinking-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  border-bottom-left-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}

.thinking-dots {
  display: flex;
  gap: 4px;

  span {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--brand-primary);
    animation: thinkingDot 1.4s infinite ease-in-out both;

    &:nth-child(1) { animation-delay: 0s; }
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes thinkingDot {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

.thinking-text {
  font-size: 13px;
  color: var(--text-muted);
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 输入区域 ===== */
.input-area {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--divider);
  background: var(--bg-surface);
  flex-shrink: 0;
}

.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 900px;
  margin: 0 auto;
  padding: 10px 12px 10px 16px;
  background: var(--bg-input);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--border-strong);
  }

  &.focused {
    border-color: var(--brand-primary);
    box-shadow: var(--focus-ring);
  }

  :deep(.el-textarea__inner) {
    background: transparent;
    color: var(--text-primary);
    border: none !important;
    box-shadow: none !important;
    padding: 6px 0 0;
    font-size: 15px;
    resize: none;

    &::placeholder {
      color: var(--text-muted);
    }
  }
}

.input-actions {
  display: flex;
  align-items: flex-end;
  flex-shrink: 0;
}

.send-btn {
  height: 36px;
  width: 36px;
  padding: 0;
  border-radius: 50% !important;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-active));
  border: none;
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.3);

  &:hover:not(:disabled) {
    transform: scale(1.05);
    box-shadow: 0 6px 20px rgba(0, 113, 227, 0.4);
  }

  &:disabled {
    background: var(--bg-hover);
    box-shadow: none;
    opacity: 0.6;
  }
}

.tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
  margin: 8px 0 0;

  .el-icon {
    font-size: 14px;
  }
}
</style>
