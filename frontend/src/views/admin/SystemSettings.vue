<template>
  <div class="panel">
      <div class="panel-head">
        <span class="panel-title">系统运行参数</span>
      </div>
      <p class="panel-subtitle">修改后仅对当前进程生效，重启后还原为 .env 配置</p>
      <el-form :model="form" label-width="200px" style="max-width:800px">
        <el-form-item label="站点标题">
          <el-input v-model="localSiteTitle" placeholder="管理后台左侧显示的名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="LLM 模型名称">
          <el-select v-model="form.llm_model_name" style="width:100%">
            <el-option label="qwen-turbo（性价比）" value="qwen-turbo" />
            <el-option label="qwen-plus（默认推荐）" value="qwen-plus" />
            <el-option label="qwen-max（高智能）" value="qwen-max" />
          </el-select>
        </el-form-item>
        <el-form-item label="Temperature (0-2)"><el-slider v-model="form.llm_temperature" :min="0" :max="2" :step="0.1" show-input /></el-form-item>
        <el-form-item label="最大生成 Token"><el-input-number v-model="form.llm_max_tokens" :min="256" :max="8192" :step="256" /></el-form-item>
        <el-form-item label="召回 Top-K"><el-input-number v-model="form.retriever_top_k" :min="5" :max="100" /></el-form-item>
        <el-form-item label="精排 Top-K"><el-input-number v-model="form.rerank_top_k" :min="1" :max="20" /></el-form-item>
        <el-form-item label="相似度过滤阈值"><el-slider v-model="form.similarity_threshold" :min="0" :max="1" :step="0.05" show-input /></el-form-item>
        <el-form-item label="文档分块 Chunk Size"><el-input-number v-model="form.chunk_size" :min="256" :max="2048" :step="64" /></el-form-item>
        <el-form-item label="分块 Overlap"><el-input-number v-model="form.chunk_overlap" :min="0" :max="512" :step="16" /></el-form-item>
        <el-form-item label="查询上下文改写"><el-switch v-model="form.query_rewrite_enabled" /></el-form-item>
        <el-form-item label="启用智能体 Agent"><el-switch v-model="form.agent_enabled" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSave" :loading="saving">保存并应用（立即生效）</el-button>
          <el-button @click="reload">重置为当前值</el-button>
        </el-form-item>
      </el-form>
    </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAdminSettingsGet, apiAdminSettingsUpdate } from '@/api/rag'
import { useSiteTitle } from '@/composables/useSiteTitle'

const form = reactive<any>({})
const saving = ref(false)
const { siteTitle, setSiteTitle } = useSiteTitle()
const localSiteTitle = ref(siteTitle.value)

onMounted(() => reload())

async function reload() {
  const r: any = await apiAdminSettingsGet()
  Object.keys(r.data || {}).forEach((k: any) => { form[k] = r.data[k] })
}
async function onSave() {
  saving.value = true
  try {
    await apiAdminSettingsUpdate({ ...form })
    setSiteTitle(localSiteTitle.value)
    ElMessage.success('已更新运行时配置及站点标题')
  } finally { saving.value = false }
}
</script>

<style scoped>
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.panel-title {
  font-size: 18px; font-weight: 600; color: var(--text-primary);
}
.panel-subtitle {
  margin: -8px 0 20px;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
