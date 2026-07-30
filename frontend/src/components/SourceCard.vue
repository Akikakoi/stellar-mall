<template>
  <div class="ref-card" :class="{ expanded }">
    <div class="card-header" @click="expanded = !expanded">
      <div class="header-left">
        <div class="doc-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="doc-info">
          <div class="doc-name-row">
            <span class="num">#{{ src.id }}</span>
            <span class="name" :title="src.doc_name">{{ src.doc_name }}</span>
          </div>
          <div class="doc-meta">
            <el-tag v-if="src.page" size="small" type="info" effect="plain">第 {{ src.page }} 页</el-tag>
            <span class="score-badge" :title="'相关度：' + src.score.toFixed(3)">
              <el-icon><Star /></el-icon>
              {{ Math.round(src.score * 100) }}%
            </span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-icon class="arrow"><ArrowDown v-if="!expanded" /><ArrowUp v-else /></el-icon>
      </div>
    </div>

    <div class="tags-row" v-if="tagList.length">
      <el-tag
        v-for="t in tagList"
        :key="t"
        size="small"
        effect="plain"
        type="success"
        class="tag-item"
      >{{ t }}</el-tag>
    </div>

    <transition name="content-expand">
      <div v-show="expanded" class="card-body">
        <div class="content-text">{{ highlight(src.content) }}</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowUp, ArrowDown, Document, Star } from '@element-plus/icons-vue'

const props = defineProps({
  src: { type: Object, required: true },
  query: { type: String, default: '' },
})

const expanded = ref(false)

function fixGarbledText(str) {
  if (!str || /^[\x00-\x7F\u4e00-\u9fff\u3400-\u4dbf\s]*$/.test(str)) return str
  try {
    const decoder = new TextDecoder('gbk')
    const bytes = new Uint8Array([...str].map(c => c.charCodeAt(0)))
    const fixed = decoder.decode(bytes)
    if (/[\u4e00-\u9fff]/.test(fixed)) return fixed
  } catch {}
  return str
}

const tagList = computed(() => {
  const raw = props.src.tags
  if (!raw) return []
  const str = (Array.isArray(raw) ? raw.join(',') : String(raw)).trim()
  if (str.startsWith('[') && str.endsWith(']')) {
    try {
      const parsed = JSON.parse(str)
      if (Array.isArray(parsed)) return parsed.map(s => fixGarbledText(String(s))).filter(Boolean)
    } catch {}
  }
  return str.split(/[,，、;；|\s]+/).filter(Boolean).map(fixGarbledText)
})

function highlight(text) {
  return text || ''
}
</script>

<style scoped lang="scss">
.ref-card {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  margin-bottom: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;

  &:hover {
    border-color: var(--brand-primary-border);
    box-shadow: var(--shadow-sm);
  }

  &.expanded {
    border-color: var(--brand-primary-border);
    box-shadow: var(--shadow-md);
  }
}

/* ===== 卡片头部 ===== */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.15s ease;
  user-select: none;

  &:hover {
    background: var(--bg-hover);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.doc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  font-size: 16px;
  flex-shrink: 0;
}

.doc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.doc-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.num {
  font-size: 11px;
  font-weight: 700;
  color: var(--brand-primary);
  background: var(--brand-primary-soft);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);

  .el-icon {
    font-size: 12px;
    color: var(--brand-primary);
  }
}

.header-right {
  flex-shrink: 0;
  margin-left: 8px;
}

.arrow {
  font-size: 14px;
  color: var(--text-muted);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ===== 标签行 ===== */
.tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 14px 10px 56px;
}

.tag-item {
  font-size: 11px;
}

/* ===== 卡片内容 ===== */
.card-body {
  padding: 0 14px 14px 56px;
}

.content-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow: auto;
  padding: 10px 12px;
  background: var(--bg-hover);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--brand-primary);
}

/* 内容展开动画 */
.content-expand-enter-active,
.content-expand-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 400px;
  opacity: 1;
  overflow: hidden;
}

.content-expand-enter-from,
.content-expand-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
