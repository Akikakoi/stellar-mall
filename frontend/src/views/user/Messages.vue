<template>
  <div class="messages-page container">
    <div class="page-header">
      <h2>我的消息</h2>
      <div class="header-actions">
        <el-button v-if="hasRead" type="danger" link @click="clearRead" style="margin-right: 12px;">清空已读</el-button>
        <el-button v-if="hasUnread" type="primary" link @click="readAll">全部已读</el-button>
      </div>
    </div>

    <div class="msg-list" v-if="list.length > 0">
      <div
        v-for="m in list"
        :key="m.id"
        class="msg-item"
        :class="{ unread: m.isRead === 0 }"
      >
        <div class="msg-header">
          <el-tag size="small" :type="tagType(m.type)">{{ typeLabel(m.type) }}</el-tag>
          <span class="msg-time">{{ m.createTime?.substring(0, 16) }}</span>
        </div>
        <div class="msg-title">{{ m.title }}</div>
        <div class="msg-body">{{ m.content }}</div>
        <div class="msg-foot">
          <template v-if="m.isRead === 0">
            <el-button size="small" type="primary" link @click="acknowledge(m)">我知道了</el-button>
          </template>
          <span v-else class="read-label">已读</span>
        </div>
      </div>
    </div>
    <el-empty v-else class="glass-empty" description="暂无消息" />

    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listMessages, markMessageRead, markAllMessagesRead, clearReadMessages } from '@/api/mall'
import { useUnreadBadge } from '@/composables/useUnreadBadge'
import { ElMessage } from 'element-plus'

const { dec: decBadge } = useUnreadBadge()

const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const hasUnread = computed(() => list.value.some((m: any) => m.isRead === 0))
const hasRead = computed(() => list.value.some((m: any) => m.isRead === 1))

/**
 * 根据消息类型返回对应的 Element Plus Tag 样式
 * @param {string} type - 消息类型
 * @returns {string} Tag 类型
 */
function tagType(type: any) {
  if (type === 'ORDER_SHIPPED' || type === 'AFTER_SALE_APPROVED' || type === 'AFTER_SALE_COMPLETED') return 'success'
  if (type === 'ORDER_CANCELLED' || type === 'AFTER_SALE_REJECTED') return 'danger'
  if (type === 'COUPON') return 'warning'
  return 'info'
}

/**
 * 根据消息类型返回对应的中文标签
 * @param {string} type - 消息类型
 * @returns {string} 中文标签
 */
function typeLabel(type: any) {
  const map: Record<string, any> = {
    ORDER_SHIPPED: '发货通知',
    ORDER_CANCELLED: '取消通知',
    AFTER_SALE_APPROVED: '售后通过',
    AFTER_SALE_REJECTED: '售后拒绝',
    AFTER_SALE_COMPLETED: '退款完成',
    COUPON: '优惠券',
    SYSTEM: '系统消息'
  }
  return map[type] || type || '系统消息'
}

/**
 * 加载消息列表，分页查询
 */
async function load() {
  try {
    const res: any = await listMessages({ page: pageNum.value, pageSize: pageSize.value })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) { /* */ }
}

/**
 * 标记单条消息为已读，并更新未读角标
 * @param {Object} m - 消息对象
 */
async function acknowledge(m: any) {
  if (m.isRead === 1) return
  try {
    await markMessageRead(m.id)
    m.isRead = 1
    decBadge()
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '操作失败'
    ElMessage.error(msg)
  }
}

/**
 * 将所有未读消息标记为已读，并更新未读角标
 */
async function readAll() {
  try {
    await markAllMessagesRead()
    let unreadCount = 0
    list.value.forEach((m: any) => {
      if (m.isRead === 0) {
        m.isRead = 1
        unreadCount++
      }
    })
    decBadge(unreadCount)
    ElMessage.success('已全部标记为已读')
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '操作失败'
    ElMessage.error(msg)
  }
	}

	/**
	 * 清空所有已读消息
	 */
	async function clearRead() {
	  try {
	    await clearReadMessages()
	    // 从列表中移除已读消息
	    list.value = list.value.filter((m: any) => m.isRead !== 1)
	    total.value = list.value.length
	    ElMessage.success('已清空已读消息')
	  } catch (e: any) {
	    const msg = e?.response?.data?.msg || e?.message || '操作失败'
	    ElMessage.error(msg)
	  }
	}

	onMounted(load)
</script>

<style scoped>
.messages-page { max-width: 720px; margin: 0 auto; padding: 24px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 600; color: var(--text-primary); margin: 0; }

.msg-list { display: flex; flex-direction: column; gap: 10px; }

.msg-item {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  transition: background var(--transition-base);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  box-shadow: var(--glass-shadow);
}
.msg-item.unread {
  border-left: 3px solid var(--brand-primary);
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
}

.msg-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.msg-time { color: var(--text-muted); font-size: 12px; }

.msg-title { font-weight: 600; color: var(--text-primary); font-size: 15px; margin-bottom: 6px; }
.msg-body { color: var(--text-secondary); font-size: 14px; line-height: 1.6; margin-bottom: 12px; }

.msg-foot {
  display: flex; justify-content: flex-end;
  padding-top: 10px; border-top: 1px solid var(--border-subtle);
}
.read-label { color: var(--text-muted); font-size: 13px; }

.pagination-wrap { margin-top: 24px; display: flex; justify-content: center; }
</style>
