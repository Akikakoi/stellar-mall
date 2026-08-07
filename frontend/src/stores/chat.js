/**
 * 聊天（RAG 对话）状态管理 Store。
 * 管理对话列表、当前活跃对话、消息列表和 SSE 流式响应状态。
 */
import { defineStore } from 'pinia'
import {
  apiListConversations,
  apiCreateConversation,
  apiRenameConversation,
  apiDeleteConversation,
  apiGetConversation,
} from '@/api/rag'

/** 初始状态工厂函数 */
const INITIAL_STATE = () => ({
  /** 对话列表 */
  conversations: [],
  /** 当前活跃对话 ID */
  activeId: null,
  /** 当前对话的消息列表 */
  messages: [],
  /** 加载状态 */
  loading: false,
})

export const useChatStore = defineStore('chat', {
  state: INITIAL_STATE,
  actions: {
    /** 重置为初始状态 */
    reset() {
      Object.assign(this, INITIAL_STATE())
    },

    /** 从服务端获取对话列表 */
    async fetchConversations() {
      const r = await apiListConversations()
      this.conversations = r.data || []
      // 如果当前活跃对话已不在列表中，则清除
      if (this.activeId != null && !this.conversations.some(c => String(c.id) === String(this.activeId))) {
        this.activeId = null
        this.messages = []
      }
      return this.conversations
    },

    /** 创建新对话并设为活跃 */
    async createConversation(title) {
      const r = await apiCreateConversation({ title })
      this.conversations.unshift(r.data)
      this.setActive(r.data.id)
      return r.data
    },

    /** 设置当前活跃对话 */
    setActive(id) {
      this.activeId = Number(id)
      this.messages = []
    },

    /**
     * 加载当前活跃对话的消息列表。
     * 保留本地仍在流式传输中（SSE 未完成）的消息，避免导航时丢失内容。
     */
    async loadActive() {
      if (!this.activeId) { this.messages = []; return }
      const r = await apiGetConversation(this.activeId)
      const serverMsgs = (r.data?.messages || []).map(m => ({
        ...m,
        streaming: false,
        sources: m.sources || [],
      }))

      // 保留本地仍在流式传输中的消息（导航离开时 SSE 未完成的内容）
      const localStreaming = this.messages.filter(m => m.streaming && m.content)
      this.messages = serverMsgs

      for (const sm of localStreaming) {
        const exists = serverMsgs.some(m =>
          m.role === sm.role && m.content === sm.content
        )
        if (!exists) {
          this.messages.push(sm)
        }
      }
    },

    /** 重命名对话 */
    async rename(id, title) {
      const r = await apiRenameConversation(id, title)
      const idx = this.conversations.findIndex(c => c.id === id)
      if (idx >= 0) this.conversations[idx] = { ...this.conversations[idx], ...r.data }
      return r.data
    },

    /**
     * 删除对话。
     * 若删除的是当前活跃对话，则自动切换到下一个，无对话时自动创建新对话。
     */
    async remove(id) {
      const wasActive = this.activeId === id
      await apiDeleteConversation(id)
      this.conversations = this.conversations.filter(c => c.id !== id)
      if (wasActive) {
        this.messages = []
        if (this.conversations.length > 0) {
          this.activeId = this.conversations[0].id
        } else {
          const r = await apiCreateConversation({ title: '新的对话' })
          this.conversations.unshift(r.data)
          this.activeId = r.data.id
        }
      }
    },

    /** 添加一条用户消息到当前对话 */
    pushUserMessage(content) {
      const msg = {
        id: Date.now(),
        role: 'user',
        content,
        sources: [],
        streaming: false,
      }
      this.messages.push(msg)
      return msg
    },

    /** 添加一条 AI 占位消息（用于 SSE 流式更新） */
    pushAssistantPlaceholder() {
      const msg = {
        id: Date.now() + 1,
        role: 'assistant',
        content: '',
        sources: [],
        streaming: true,
        tokens_used: 0,
        latency_ms: 0,
      }
      this.messages.push(msg)
      return msg
    },

    /** 更新 AI 消息的流式内容（SSE 逐 token 追加） */
    updateAssistant(msgId, patch) {
      const idx = this.messages.findIndex(m => m.id === msgId)
      if (idx < 0) return
      this.messages[idx] = { ...this.messages[idx], ...patch }
    },
  },
})
