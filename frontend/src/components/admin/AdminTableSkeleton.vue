<template>
  <div class="admin-sk-table" aria-busy="true" aria-live="polite">
    <!-- 表头：给个位置感，避免骨架像一堆无主灰条 -->
    <div v-if="showHeader" class="admin-sk-head">
      <div v-for="(c, i) in columns" :key="'h' + i" class="admin-sk-cell" :style="{ flex: c }">
        <span class="sk-line sk-line--head"></span>
      </div>
    </div>

    <div v-for="r in rows" :key="r" class="admin-sk-row" :style="{ height: rowHeight + 'px' }">
      <div v-for="(c, i) in columns" :key="i" class="admin-sk-cell" :style="{ flex: c }">
        <!-- 第二列如果是图片列，用方块占位，和真实行更像 -->
        <span v-if="thumb && i === 1" class="sk-block admin-sk-thumb"></span>
        <span v-else class="sk-line" :class="{ 'sk-line--short': (r + i) % 3 === 0 }"></span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理端表格骨架屏
 *
 * 只做一件事：首屏加载时把「表格的形状」先摆出来，数据回来再换成真表。
 * 之所以不用 v-loading 转圈：后台列表天生是表格，转圈会让人以为页面卡住，
 * 而骨架能提前告诉用户「这里是 6 列、有缩略图」。
 *
 * 注意：切换成真表时行高会有小幅变化，属正常；这里的 rowHeight 尽量与真实行一致。
 */
withDefaults(defineProps<{
  /** 占位行数 */
  rows?: number
  /** 每列的弹性比例，例如 [0.4, 1, 0.6, 0.5] */
  columns?: number[]
  /** 每行像素高度，尽量与真实行高一致 */
  rowHeight?: number
  /** 是否显示表头占位 */
  showHeader?: boolean
  /** 第二列是否为图片列（渲染方块而不是横线） */
  thumb?: boolean
}>(), {
  rows: 8,
  columns: () => [0.5, 1.4, 0.8, 0.7, 0.6],
  rowHeight: 56,
  showHeader: true,
  thumb: false
})
</script>

<style scoped>
.admin-sk-table {
  width: 100%;
  padding: 0 12px;
  box-sizing: border-box;
}

.admin-sk-head {
  display: flex;
  gap: 16px;
  align-items: center;
  height: 40px;
  border-bottom: 1px solid var(--border-base);
}
/* 表头占位要比数据行更"实"一点，否则整块看起来像没有表头 */
.admin-sk-head .sk-line {
  height: 12px;
  width: 56%;
  margin: 0;
  opacity: 0.9;
}

.admin-sk-row {
  display: flex;
  gap: 16px;
  align-items: center;
  border-bottom: 1px solid var(--border-base);
}
.admin-sk-row:last-child { border-bottom: 0; }

.admin-sk-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}
.admin-sk-cell .sk-line { width: 100%; margin: 0; }
.admin-sk-cell .sk-line--short { width: 62%; }
.admin-sk-cell .sk-line--head { width: 72%; height: 12px; opacity: 0.7; }

.admin-sk-thumb {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
</style>
