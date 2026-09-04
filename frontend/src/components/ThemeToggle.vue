<template>
  <el-button
    text
    class="theme-btn"
    @click="toggleTheme"
    :title="isDark ? '切换亮色' : '切换暗色'"
  >
    <el-icon :size="18"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { theme, toggleTheme } = useTheme()
const isDark = computed(() => theme.value === 'dark')
</script>

<style scoped lang="scss">
.theme-btn {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);

  &:hover {
    border-color: var(--brand-primary);
    color: var(--brand-primary);
  }
}
</style>
