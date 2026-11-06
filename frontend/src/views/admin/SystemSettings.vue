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
        <el-form-item label="AI 模型">
          <div class="model-field">
            <el-select
              v-model="form.llm_model_name"
              style="width:100%"
              filterable
              allow-create
              default-first-option
              placeholder="选择或直接输入模型名"
            >
              <el-option-group v-for="g in modelGroups" :key="g.label" :label="g.label">
                <el-option v-for="m in g.options" :key="m.value" :label="m.label" :value="m.value" />
              </el-option-group>
            </el-select>
            <div class="field-hint">
              <template v-if="form.llm_base_url">当前端点：{{ form.llm_base_url }}。模型名必须与该端点一致，填错会直接导致对话接口返回 4xx。</template>
              <template v-else>模型名必须与后端 .env 里 LLM_BASE_URL 指向的服务一致，填错会直接导致对话接口返回 4xx。</template>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="回答随机性（0-2）"><el-slider v-model="form.llm_temperature" :min="0" :max="2" :step="0.1" show-input /></el-form-item>
        <el-form-item label="单次回答最大长度"><el-input-number v-model="form.llm_max_tokens" :min="256" :max="8192" :step="256" /></el-form-item>
        <el-form-item label="初筛候选条数"><el-input-number v-model="form.retriever_top_k" :min="5" :max="100" /></el-form-item>
        <el-form-item label="最终引用条数"><el-input-number v-model="form.rerank_top_k" :min="1" :max="20" /></el-form-item>
        <el-form-item label="相似度过滤阈值"><el-slider v-model="form.similarity_threshold" :min="0" :max="1" :step="0.05" show-input /></el-form-item>
        <el-form-item label="文档切分长度"><el-input-number v-model="form.chunk_size" :min="256" :max="2048" :step="64" /></el-form-item>
        <el-form-item label="切分重叠字符"><el-input-number v-model="form.chunk_overlap" :min="0" :max="512" :step="16" /></el-form-item>
        <el-form-item label="自动补全提问上下文"><el-switch v-model="form.query_rewrite_enabled" /></el-form-item>
        <el-form-item label="复杂问题多步推理"><el-switch v-model="form.agent_enabled" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSave" :loading="saving">保存并应用（立即生效）</el-button>
          <el-button @click="reload">重置为当前值</el-button>
        </el-form-item>
      </el-form>
    </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAdminSettingsGet, apiAdminSettingsUpdate } from '@/api/rag'
import { useSiteTitle } from '@/composables/useSiteTitle'

const form = reactive<any>({})
const saving = ref(false)
const { siteTitle, setSiteTitle } = useSiteTitle()
const localSiteTitle = ref(siteTitle.value)

/* ===== AI 模型候选 =====
 * 候选必须跟着「当前端点」走：端点决定哪家模型名才有效。
 * 之前这里固定写死 qwen-turbo/plus/max，但 .env 早已换成 DeepSeek
 * （LLM_BASE_URL=https://api.deepseek.com/v1），选任何一个都会把
 * qwen-* 发给 DeepSeek，直接 400 Model Not Exist，且当前值 deepseek-chat
 * 不在候选里只能裸显示。 */
const MODEL_CATALOG = {
  deepseek: {
    label: 'DeepSeek',
    options: [
      { value: 'deepseek-chat', label: 'deepseek-chat（通用对话，默认）' },
      { value: 'deepseek-reasoner', label: 'deepseek-reasoner（推理增强，更慢更贵）' },
    ],
  },
  dashscope: {
    label: '通义千问（DashScope 端点）',
    options: [
      { value: 'qwen-turbo', label: 'qwen-turbo（性价比）' },
      { value: 'qwen-plus', label: 'qwen-plus（默认推荐）' },
      { value: 'qwen-max', label: 'qwen-max（高智能）' },
    ],
  },
} as const

/** 从端点推断厂商；无法识别时两家都列，交给用户自己判断 */
const modelGroups = computed(() => {
  const url = String(form.llm_base_url || '').toLowerCase()
  if (url.includes('deepseek')) return [MODEL_CATALOG.deepseek]
  if (url.includes('dashscope') || url.includes('aliyuncs')) return [MODEL_CATALOG.dashscope]
  return [MODEL_CATALOG.deepseek, MODEL_CATALOG.dashscope]
})

onMounted(() => reload())

async function reload() {
  const r: any = await apiAdminSettingsGet()
  Object.keys(r.data || {}).forEach((k: any) => { form[k] = r.data[k] })
}
async function onSave() {
  saving.value = true
  try {
    // llm_base_url 是只读回显字段，不属于可改配置，提交前剔除
    const { llm_base_url, ...payload } = form
    await apiAdminSettingsUpdate(payload)
    setSiteTitle(localSiteTitle.value)
    ElMessage.success('已更新运行时配置及站点标题')
  } finally { saving.value = false }
}
</script>

<style scoped>
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
.model-field { width: 100%; }
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted);
}
</style>
