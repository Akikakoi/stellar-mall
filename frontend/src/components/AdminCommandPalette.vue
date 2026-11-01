<template>
  <Teleport to="body">
    <transition name="cmdk-fade">
      <div v-if="visible" class="cmdk-overlay" @mousedown.self="close">
        <div class="cmdk-panel">
          <div class="cmdk-input-row">
            <el-icon :size="15" color="var(--text-muted)"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="cmdk-input"
              placeholder="搜索页面，回车跳转…"
              @keydown="onKeydown"
            />
            <span class="cmdk-esc">ESC</span>
          </div>
          <div class="cmdk-results">
            <div
              v-for="(item, i) in filtered"
              :key="item.path"
              class="cmdk-item"
              :class="{ 'is-active': i === activeIndex }"
              @mouseenter="activeIndex = i"
              @click="go(item.path)"
            >
              <span class="cmdk-item-title">{{ item.title }}</span>
              <span class="cmdk-item-path">{{ item.path.replace('/admin/', '') }}</span>
            </div>
            <div v-if="!filtered.length" class="cmdk-empty">没有匹配的页面</div>
          </div>
          <div class="cmdk-footer">
            <span><i class="key">↑</i><i class="key">↓</i> 选择</span>
            <span><i class="key">↵</i> 跳转</span>
            <span><i class="key">ESC</i> 关闭</span>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

interface PaletteItem {
  path: string
  title: string
}

const props = defineProps<{
  items: PaletteItem[]
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const router = useRouter()
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const visible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

/** 子串匹配标题与路径，大小写不敏感；默认展示全部（也是快速导航） */
const filtered = computed<PaletteItem[]>(() => {
  const kw = query.value.trim().toLowerCase()
  if (!kw) return props.items
  return props.items.filter(
    (it) => it.title.toLowerCase().includes(kw) || it.path.toLowerCase().includes(kw)
  )
})

function close() {
  visible.value = false
}

function go(path: string) {
  close()
  router.push(path)
}

function reset() {
  query.value = ''
  activeIndex.value = 0
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (filtered.value.length) activeIndex.value = (activeIndex.value + 1) % filtered.value.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (filtered.value.length) activeIndex.value = (activeIndex.value - 1 + filtered.value.length) % filtered.value.length
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = filtered.value[activeIndex.value]
    if (item) go(item.path)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}

/** 全局 ⌘K / Ctrl+K 唤起（input 聚焦时浏览器默认行为也一并阻止） */
function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
    e.preventDefault()
    if (visible.value) {
      close()
    } else {
      open()
    }
  }
}

function open() {
  reset()
  visible.value = true
  nextTick(() => inputRef.value?.focus())
}

watch(visible, (v) => {
  if (v) {
    reset()
    nextTick(() => inputRef.value?.focus())
  }
})

/** 输入变化时选中位归零，避免高亮停在已过滤掉的项上 */
watch(query, () => { activeIndex.value = 0 })

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onUnmounted(() => window.removeEventListener('keydown', onGlobalKeydown))

/** 供父组件模板事件绑定使用（避免未使用告警） */
defineExpose({ open, close })
</script>

<style>
/* 命令面板 teleport 到 body，必须用全局（非 scoped）样式。
 * 扁平风格：实底卡片 + 发丝线边框，仅保留一层功能性投影与弹层分离。 */
.cmdk-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(22, 22, 24, 0.32);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 0 16px;
}
.cmdk-panel {
  margin-top: 12vh;
  width: 520px;
  max-width: 100%;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-base, rgba(0, 0, 0, 0.1));
  border-radius: 10px;
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.16);
  overflow: hidden;
}
.dark .cmdk-panel {
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.5);
}
.cmdk-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 14px;
  border-bottom: 1px solid var(--border-base, rgba(0, 0, 0, 0.08));
}
.cmdk-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-primary, #1d1d1f);
}
.cmdk-input::placeholder { color: var(--text-muted, #86868b); }
.cmdk-esc {
  font-size: 11px;
  padding: 2px 6px;
  border: 1px solid var(--border-base, rgba(0, 0, 0, 0.1));
  border-radius: 4px;
  color: var(--text-muted, #86868b);
  line-height: 1.4;
}
.cmdk-results {
  max-height: 320px;
  overflow-y: auto;
  padding: 6px;
}
.cmdk-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary, #1d1d1f);
}
.cmdk-item.is-active { background: var(--bg-hover, rgba(0, 0, 0, 0.05)); }
.cmdk-item-path { font-size: 11px; color: var(--text-muted, #86868b); }
.cmdk-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted, #86868b);
}
.cmdk-footer {
  display: flex;
  gap: 16px;
  padding: 9px 14px;
  border-top: 1px solid var(--border-base, rgba(0, 0, 0, 0.08));
  font-size: 11px;
  color: var(--text-muted, #86868b);
}
.cmdk-footer .key {
  font-style: normal;
  font-size: 10px;
  padding: 1px 4px;
  border: 1px solid var(--border-base, rgba(0, 0, 0, 0.1));
  border-radius: 3px;
  margin-right: 2px;
}

.cmdk-fade-enter-active, .cmdk-fade-leave-active { transition: opacity 0.12s ease; }
.cmdk-fade-enter-from, .cmdk-fade-leave-to { opacity: 0; }
</style>
