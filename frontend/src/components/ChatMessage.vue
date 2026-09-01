<template>
  <div class="msg-row" :class="msg.role">
    <!-- 头像 -->
    <div class="avatar-wrap">
      <el-avatar :size="40" class="avatar" :class="msg.role">
        <template v-if="msg.role === 'user'">
          {{ userLetter }}
        </template>
        <template v-else>
          <el-icon><ChatDotRound /></el-icon>
        </template>
      </el-avatar>
      <span class="avatar-label">{{ msg.role === 'user' ? '我' : 'AI助手' }}</span>
    </div>

    <div class="bubble-wrap">
      <!-- 气泡主体 -->
      <div class="bubble" :class="{ streaming: msg.streaming, 'has-sources': msg.sources?.length }">
        <!-- ReAct 思考过程 -->
        <div v-if="msg.role === 'assistant' && msg.thinking" class="react-thinking">
          <span class="rt-spin">
            <el-icon><Loading /></el-icon>
          </span>
          <span class="rt-text">{{ msg.thinking }}</span>
        </div>
        <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderedMd"></div>
        <div v-else class="user-text">{{ msg.content }}</div>

        <!-- 流式输入光标 -->
        <span v-if="msg.streaming && !msg.content" class="cursor">
          <span class="cursor-dot"></span>
          <span class="cursor-dot"></span>
          <span class="cursor-dot"></span>
        </span>
      </div>

      <!-- 知识库来源 -->
      <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length" class="sources-wrap">
        <div class="sources-header" @click="sourcesExpanded = !sourcesExpanded">
          <div class="sources-icon">
            <el-icon><Document /></el-icon>
          </div>
          <span class="sources-count">参考了 {{ msg.sources.length }} 个知识片段</span>
          <el-icon class="sources-arrow" :class="{ expanded: sourcesExpanded }"><ArrowDown /></el-icon>
        </div>
        <transition name="sources-expand">
          <div v-show="sourcesExpanded" class="sources-body">
            <SourceCard
              v-for="s in msg.sources"
              :key="s.id || s.doc_name + s.chunk_index"
              :src="s"
              :query="currentQuery"
            />
          </div>
        </transition>
      </div>

      <!-- 工具调用 -->
      <div v-if="msg.role === 'assistant' && msg.tool_calls && msg.tool_calls.length" class="tool-calls">
        <div class="tc-header">
          <el-icon><Cpu /></el-icon>
          <span>智能体工具调用</span>
        </div>
        <div class="tc-list">
          <div
            v-for="(tc, idx) in msg.tool_calls"
            :key="idx"
            class="tc-item"
            :class="[tc.type, { success: tc.success, fail: !tc.success }]"
          >
            <div class="tc-icon-wrap">
              <el-icon v-if="tc.type === 'call'"><Setting /></el-icon>
              <el-icon v-else-if="tc.success"><CircleCheck /></el-icon>
              <el-icon v-else><CircleClose /></el-icon>
            </div>
            <div class="tc-content">
              <div class="tc-tool-name">{{ toolName(tc.tool) }}</div>
              <div v-if="tc.type === 'call' && tc.params" class="tc-params">
                <span v-for="(v, k) in tc.params" :key="k" class="tc-param">
                  <span class="tc-param-key">{{ k }}</span>
                  <span class="tc-param-val">{{ typeof v === 'string' ? (v.length > 30 ? v.slice(0, 30) + '...' : v) : JSON.stringify(v) }}</span>
                </span>
              </div>
              <div v-if="tc.type === 'result'" class="tc-result" :class="{ success: tc.success, fail: !tc.success }">
                {{ tc.success ? '执行成功' : '执行失败' }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作栏 -->
      <div class="actions" v-if="!msg.streaming && msg.role === 'assistant'">
        <span class="meta">{{ meta }}</span>
        <div class="action-btns">
          <el-tooltip content="有帮助" placement="top">
            <button class="action-btn" :class="{ active: msg.feedback === 1 }" @click="onFb(1)">
              <el-icon><Star /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="无帮助" placement="top">
            <button class="action-btn" :class="{ active: msg.feedback === -1 }" @click="onFb(-1)">
              <el-icon><StarFilled style="transform: rotate(180deg)" /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="复制回答" placement="top">
            <button class="action-btn" @click="copy">
              <el-icon><CopyDocument /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Star, StarFilled, CopyDocument, Document, Cpu, Loading, Setting, CircleCheck, CircleClose, ChatDotRound, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SourceCard from './SourceCard.vue'
import { useUserStore } from '@/stores/user'
import { apiFeedbackMessage } from '@/api/rag'
import { useChatStore } from '@/stores/chat'

const props = defineProps({
  msg: { type: Object, required: true },
  conversationId: { type: [Number, String], default: null },
  currentQuery: { type: String, default: '' },
})
const emit = defineEmits(['feedback'])

const userStore = useUserStore()
const chatStore = useChatStore()
const renderedMd = ref('')
const sourcesExpanded = ref(false)
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const userLetter = computed(() => {
  const u = userStore.userInfo
  return (u?.nickname || u?.username || '我')?.[0]
})
const meta = computed(() => {
  const parts: any[] = []
  if (props.msg.mode) {
    const label = props.msg.mode === 'agent' ? 'Agent 模式' : props.msg.mode === 'rag' ? 'RAG 模式' : '智能模式'
    parts.push(label)
  }
  if (props.msg.tokens_used) parts.push(`${props.msg.tokens_used} tokens`)
  if (props.msg.latency_ms) parts.push(`${(props.msg.latency_ms / 1000).toFixed(1)}s`)
  return parts.join(' · ')
})

watch(
  () => props.msg.content,
  (c: any) => {
    if (props.msg.streaming) {
      renderedMd.value = escapeHtml(c).replace(/\n/g, '<br/>')
    } else {
      renderedMd.value = DOMPurify.sanitize(md.render(c || ''))
    }
  },
  { immediate: true },
)

watch(
  () => props.msg.streaming,
  (streaming: any, was: any) => {
    if (was && !streaming) {
      renderedMd.value = DOMPurify.sanitize(md.render(props.msg.content || ''))
    }
  },
)

function escapeHtml(s: any) {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

async function onFb(v: any) {
  if (!props.conversationId || !props.msg.id) return
  try {
    await apiFeedbackMessage(props.conversationId as number, props.msg.id, v)
    chatStore.updateAssistant(props.msg.id, { feedback: v })
    ElMessage.success('已提交反馈，感谢！')
    emit('feedback', v)
  } catch {}
}
function copy() {
  if (!props.msg.content) return
  navigator.clipboard?.writeText(props.msg.content)
  ElMessage.success('已复制')
}

function toolName(tool: any) {
  const map: Record<string, string> = {
    kb_search: '知识库检索',
    query_order: '订单查询',
    cancel_order: '取消订单',
    confirm_receipt: '确认收货',
    query_cart: '购物车查询',
    clear_cart: '清空购物车',
    delete_cart_item: '删除购物车商品',
    update_cart_item: '修改购物车商品',
    apply_after_sales: '售后申请',
    query_favorite: '收藏夹查询',
    add_to_cart: '加入购物车',
    query_wallet: '钱包查询',
    product_search: '商品搜索',
    query_reviews: '评价查询',
  }
  return map[tool] || tool
}
</script>

<style scoped lang="scss">
/* ===== 消息行 ===== */
.msg-row {
  display: flex;
  gap: 14px;
  padding: 20px 0;
  animation: msgFadeIn 0.35s ease-out;
  max-width: 100%;

  &:last-child {
    padding-bottom: 8px;
  }
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 头像区域 ===== */
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.avatar {
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s ease;

  &.user {
    background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-active));
    color: var(--text-on-primary);
    font-weight: 600;
    border: none;
  }
  &.assistant {
    background: linear-gradient(135deg, var(--bg-card), var(--bg-hover));
    color: var(--brand-primary);
    border: 1px solid var(--brand-primary-border);
  }
}

.avatar-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
}

/* ===== 气泡包装 ===== */
.bubble-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ===== 气泡主体 ===== */
.bubble {
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.75;
  position: relative;
  word-break: break-word;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.2s ease;
  max-width: 100%;

  &:hover {
    box-shadow: var(--shadow-md);
  }

  :deep(.markdown-body) {
    font-size: 15px;
  }
}

/* 用户消息气泡 */
.msg-row.user {
  flex-direction: row-reverse;

  .avatar-wrap {
    align-items: center;
  }

  .bubble-wrap {
    align-items: flex-end;
  }

  .bubble {
    background: linear-gradient(135deg, var(--brand-primary), #0056b3);
    color: var(--text-on-primary);
    border-color: transparent;
    border-bottom-right-radius: var(--radius-sm);
    box-shadow: 0 4px 16px rgba(0, 113, 227, 0.25);

    .user-text {
      white-space: pre-wrap;
    }

    :deep(.markdown-body) {
      color: var(--text-on-primary);
    }
  }
}

/* 助手消息气泡 */
.msg-row.assistant .bubble {
  border-bottom-left-radius: var(--radius-sm);
  background: var(--bg-card);
}

/* 流式输入光标 */
.cursor {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
  vertical-align: middle;
}

.cursor-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-primary);
  animation: cursorBlink 1.4s infinite ease-in-out both;

  &:nth-child(1) { animation-delay: 0s; }
  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes cursorBlink {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

/* ===== 知识库来源 ===== */
.sources-wrap {
  margin-top: 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  overflow: hidden;
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--border-base);
  }
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
  transition: background 0.15s ease;
  user-select: none;

  &:hover {
    background: var(--bg-hover);
  }
}

.sources-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  font-size: 13px;
}

.sources-count {
  flex: 1;
}

.sources-arrow {
  font-size: 14px;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &.expanded {
    transform: rotate(180deg);
  }
}

.sources-body {
  padding: 0 14px 14px;
}

.sources-expand-enter-active,
.sources-expand-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 600px;
  opacity: 1;
  overflow: hidden;
}

.sources-expand-enter-from,
.sources-expand-leave-to {
  max-height: 0;
  opacity: 0;
}

/* ===== ReAct 思考过程 ===== */
.react-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-hover);
  border-radius: var(--radius-sm);

  .rt-spin .el-icon {
    animation: rtSpinning 1s linear infinite;
  }
  .rt-text {
    line-height: 1.5;
  }
}

@keyframes rtSpinning {
  to { transform: rotate(360deg); }
}

/* ===== 工具调用 ===== */
.tool-calls {
  margin-top: 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  overflow: hidden;
}

.tc-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
  border-bottom: 1px solid var(--border-subtle);

  .el-icon {
    color: var(--brand-primary);
  }
}

.tc-list {
  padding: 10px 14px;
}

.tc-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  margin-bottom: 8px;
  background: var(--bg-hover);
  border: 1px solid transparent;
  transition: all 0.15s ease;

  &:last-child { margin-bottom: 0; }

  &:hover {
    border-color: var(--border-base);
    box-shadow: var(--shadow-sm);
  }

  &.call {
    .tc-icon-wrap { background: var(--brand-primary-soft); color: var(--brand-primary); }
  }
  &.result.success {
    .tc-icon-wrap { background: rgba(52, 199, 89, 0.1); color: var(--status-success); }
  }
  &.result.fail {
    .tc-icon-wrap { background: rgba(255, 59, 48, 0.1); color: var(--status-danger); }
  }
}

.tc-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  margin-top: 2px;
}

.tc-content {
  flex: 1;
  min-width: 0;
}

.tc-tool-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.tc-params {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tc-param {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 3px 8px;
  background: var(--bg-input);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
}

.tc-param-key {
  color: var(--text-muted);
  font-weight: 500;
}

.tc-param-val {
  color: var(--text-secondary);
}

.tc-result {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 500;

  &.success { color: var(--status-success); }
  &.fail { color: var(--status-danger); }
}

/* ===== 操作栏 ===== */
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 12px;
  padding: 0 4px;
}

.meta {
  flex: 1;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 2px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 15px;
  transition: all 0.15s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-secondary);
  }

  &.active {
    color: var(--brand-primary);
    background: var(--brand-primary-soft);
  }

  :deep(.el-icon) {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
