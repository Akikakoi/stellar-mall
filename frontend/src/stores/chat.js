import { defineStore } from 'pinia'
import {
  apiListConversations,
  apiCreateConversation,
  apiRenameConversation,
  apiDeleteConversation,
  apiGetConversation,
} from '@/api/rag'
const INITIAL_STATE = () => ({
  conversations: [],
  activeId: null,
  messages: [],
  loading: false,
})

export const useChatStore = defineStore('chat', {
  state: INITIAL_STATE,
  actions: {
    reset() {
      Object.assign(this, INITIAL_STATE())
    },

    async fetchConversations() {
      const r = await apiListConversations()
      this.conversations = r.data || []
      if (this.activeId != null && !this.conversations.some(c => String(c.id) === String(this.activeId))) {
        this.activeId = null
        this.messages = []
      }
      return this.conversations
    },
    async createConversation(title) {
      const r = await apiCreateConversation({ title })
      this.conversations.unshift(r.data)
      this.setActive(r.data.id)
      return r.data
    },
    setActive(id) {
      this.activeId = Number(id)
      this.messages = []
    },
    async loadActive() {
      if (!this.activeId) { this.messages = []; return }
      const r = await apiGetConversation(this.activeId)
      this.messages = (r.data?.messages || []).map(m => ({
        ...m,
        streaming: false,
        sources: m.sources || [],
      }))
    },
    async rename(id, title) {
      const r = await apiRenameConversation(id, title)
      const idx = this.conversations.findIndex(c => c.id === id)
      if (idx >= 0) this.conversations[idx] = { ...this.conversations[idx], ...r.data }
      return r.data
    },
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
    updateAssistant(msgId, patch) {
      const idx = this.messages.findIndex(m => m.id === msgId)
      if (idx < 0) return
      this.messages[idx] = { ...this.messages[idx], ...patch }
    },
  },
})
