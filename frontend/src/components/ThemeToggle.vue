<template>
  <button
    type="button"
    class="theme-toggle"
    role="switch"
    :aria-checked="isDark"
    :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
    :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
    @click="toggleTheme"
  >
    <el-icon class="icon sun" :class="{ active: !isDark }"><Sunny /></el-icon>
    <span class="track">
      <span class="thumb"></span>
    </span>
    <el-icon class="icon moon" :class="{ active: isDark }"><Moon /></el-icon>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { theme, toggleTheme } = useTheme()
const isDark = computed(() => theme.value === 'dark')
</script>

<style scoped lang="scss">
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  height: 34px;
  border: 1px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-card);
  cursor: pointer;
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);

  &:hover {
    border-color: var(--brand-primary);
    color: var(--brand-primary);
  }

  .icon {
    font-size: 15px;
    opacity: 0.45;
    transition:
      opacity var(--transition-base),
      transform var(--transition-base),
      color var(--transition-base);
    &.active {
      opacity: 1;
      color: var(--brand-primary);
      transform: scale(1.05);
    }
  }

  .track {
    position: relative;
    width: 34px;
    height: 18px;
    border-radius: 8px;
    background: var(--border-base);
    transition: background var(--transition-base);
  }
  &[aria-checked="true"] .track {
    background: var(--brand-primary);
  }

  .thumb {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #fff;
    box-shadow: var(--shadow-sm);
    transition: transform var(--transition-base);
  }
  &[aria-checked="true"] .thumb {
    transform: translateX(16px);
  }
}
</style>
