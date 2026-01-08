<template>
  <div class="anomaly-container">
    <!-- 筛选卡片 -->
    <el-card class="filter-card">
      <div class="filter-content">
        <div class="filter-left">
          <span class="filter-title">异常检测结果</span>
          <el-radio-group v-model="selectedType" @change="loadAnomalousArticles" size="default">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="GOOD_ANOMALY">表现优秀</el-radio-button>
            <el-radio-button label="BAD_ANOMALY">需要优化</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-stats">
          <div class="stat-item good">
            <span class="stat-value">{{ goodCount }}</span>
            <span class="stat-label">表现优秀</span>
          </div>
          <div class="stat-item bad">
            <span class="stat-value">{{ badCount }}</span>
            <span class="stat-label">需要优化</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 文章列表 -->
    <div class="articles-grid" v-loading="loading">
      <el-card 
        v-for="article in filteredArticles" 
        :key="article.id"
        class="article-card"
        :class="article.anomalyStatus === 'GOOD_ANOMALY' ? 'good-card' : 'bad-card'"
        shadow="hover"
        @click="viewDetail(article)"
      >
        <div class="card-header">
          <el-tag :type="article.anomalyStatus === 'GOOD_ANOMALY' ? 'success' : 'danger'" size="small" effect="dark">
            {{ article.anomalyStatus === 'GOOD_ANOMALY' ? '优秀' : '待优化' }}
          </el-tag>
          <span class="brand-tag">{{ article.brand }}</span>
        </div>
        <h3 class="article-title">{{ article.title }}</h3>
        <div class="metrics-row">
          <div class="metric">
            <span class="metric-value">{{ formatNumber(article.readCount7d) }}</span>
            <span class="metric-label">阅读</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{ formatNumber(article.interactionCount7d) }}</span>
            <span class="metric-label">互动</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{ calculateRate(article) }}%</span>
            <span class="metric-label">互动率</span>
          </div>
        </div>
      </el-card>
      
      <el-empty v-if="filteredArticles.length === 0 && !loading" description="暂无数据" />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog 
      v-model="detailVisible" 
      :title="selectedArticle?.title" 
      width="90%"
      top="3vh"
      class="detail-dialog"
    >
      <div v-if="selectedArticle" class="detail-content">
        <!-- 基本信息卡片 -->
        <el-card class="info-card" shadow="never">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">品牌</span>
              <span class="info-value">{{ selectedArticle.brand }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">发文类型</span>
              <span class="info-value">{{ selectedArticle.postType }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">内容形式</span>
              <span class="info-value">{{ selectedArticle.contentType }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">发布时间</span>
              <span class="info-value">{{ formatDate(selectedArticle.publishTime) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">异常评分</span>
              <span class="info-value score" :class="getScoreClass(selectedArticle.anomalyScore)">
                {{ selectedArticle.anomalyScore?.toFixed(1) || '-' }}
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">状态</span>
              <el-tag :type="selectedArticle.anomalyStatus === 'GOOD_ANOMALY' ? 'success' : 'danger'" effect="dark">
                {{ selectedArticle.anomalyStatus === 'GOOD_ANOMALY' ? '表现优秀' : '需要优化' }}
              </el-tag>
            </div>
          </div>
        </el-card>

        <!-- 分块显示的Tab页 -->
        <el-tabs v-model="activeTab" class="detail-tabs">
          <!-- 原因分析 Tab -->
          <el-tab-pane label="📊 原因分析" name="analysis">
            <div class="analysis-section">
              <h4 class="section-title">异常检测结果</h4>
              <div v-if="parsedAnomalyReport && parsedAnomalyReport.results" class="results-grid">
                <div v-for="result in parsedAnomalyReport.results" :key="result.metric" class="result-card">
                  <div class="result-header">
                    <span class="result-metric">{{ result.metric }}</span>
                    <el-tag :type="getAnomalyLevelType(result.level)" size="small" effect="plain">
                      {{ getAnomalyLevelText(result.level) }}
                    </el-tag>
                  </div>
                  <div class="result-body">
                    <div class="result-row">
                      <span>当前值</span>
                      <span class="value highlight">{{ formatNumber(result.value) }}</span>
                    </div>
                    <div class="result-row">
                      <span>平均值</span>
                      <span class="value">{{ formatNumber(result.mean) }}</span>
                    </div>
                    <div class="result-row">
                      <span>偏离度</span>
                      <span class="value" :class="result.zScore > 0 ? 'positive' : 'negative'">{{ result.deviation }}</span>
                    </div>
                    <div class="result-row">
                      <span>百分位</span>
                      <div class="percentile-bar">
                        <div class="percentile-fill" :style="{ width: result.percentile + '%' }"></div>
                        <span class="percentile-text">{{ result.percentile?.toFixed(0) }}%</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无详细分析数据" />

              <!-- 图片分析 -->
              <div v-if="parsedImagesInfo && parsedImagesInfo.length > 0" class="images-section">
                <h4 class="section-title">图片内容分析</h4>
                <div class="images-stats">
                  <el-tag type="info">共 {{ parsedImagesInfo.length }} 张图片</el-tag>
                  <el-tag type="success">已下载 {{ downloadedImagesCount }} 张</el-tag>
                </div>
                <div class="images-preview">
                  <div v-for="(img, idx) in parsedImagesInfo.slice(0, 8)" :key="idx" class="image-card">
                    <div class="image-type-badge">{{ getImageTypeText(img.type) }}</div>
                    <div class="image-desc">{{ img.description }}</div>
                    <el-tag :type="img.downloaded ? 'success' : 'info'" size="small">
                      {{ img.downloaded ? '已下载' : '未下载' }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 优化建议 Tab -->
          <el-tab-pane label="💡 优化建议" name="suggestions">
            <div class="suggestions-section">
              <div v-if="selectedArticle.optimizationSuggestions" class="suggestions-content">
                <pre class="suggestions-text">{{ selectedArticle.optimizationSuggestions }}</pre>
              </div>
              <el-empty v-else description="暂无优化建议" />
            </div>
          </el-tab-pane>

          <!-- AI建议 Tab -->
          <el-tab-pane label="🤖 AI智能建议" name="ai">
            <div class="ai-section">
              <div class="ai-header">
                <div class="ai-badge">
                  <span class="ai-icon">🤖</span>
                  <span>AI智能分析</span>
                </div>
                <el-button type="primary" size="small" @click="regenerateAI" :loading="regenerating">
                  重新生成
                </el-button>
              </div>
              <div v-if="selectedArticle.aiSuggestions" class="ai-content">
                <pre class="ai-text">{{ selectedArticle.aiSuggestions }}</pre>
              </div>
              <div v-else class="ai-empty">
                <el-empty description="暂无AI建议">
                  <el-button type="primary" @click="regenerateAI" :loading="regenerating">
                    生成AI建议
                  </el-button>
                </el-empty>
              </div>
            </div>
          </el-tab-pane>

          <!-- 文章内容 Tab -->
          <el-tab-pane label="📄 文章内容" name="content" v-if="selectedArticle.content">
            <div class="content-section">
              <pre class="content-text">{{ selectedArticle.content }}</pre>
            </div>
          </el-tab-pane>
        </el-tabs>

        <!-- 底部操作栏 -->
        <div class="dialog-footer">
          <el-button v-if="selectedArticle.articleLink" type="primary" @click="openLink(selectedArticle.articleLink)">
            查看原文
          </el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { analysisApi, type ArticleData } from '../api'

const loading = ref(false)
const regenerating = ref(false)
const selectedType = ref('')
const articles = ref<ArticleData[]>([])
const detailVisible = ref(false)
const selectedArticle = ref<ArticleData | null>(null)
const activeTab = ref('analysis')

const parsedAnomalyReport = computed(() => {
  if (!selectedArticle.value?.anomalyDetails) return null
  try {
    return JSON.parse(selectedArticle.value.anomalyDetails)
  } catch { return null }
})

const parsedImagesInfo = computed(() => {
  if (!selectedArticle.value?.imagesInfo) return []
  try {
    return JSON.parse(selectedArticle.value.imagesInfo)
  } catch { return [] }
})

const downloadedImagesCount = computed(() => 
  parsedImagesInfo.value.filter((img: any) => img.downloaded).length
)

const filteredArticles = computed(() => 
  selectedType.value ? articles.value.filter(a => a.anomalyStatus === selectedType.value) : articles.value
)

const goodCount = computed(() => articles.value.filter(a => a.anomalyStatus === 'GOOD_ANOMALY').length)
const badCount = computed(() => articles.value.filter(a => a.anomalyStatus === 'BAD_ANOMALY').length)

onMounted(() => loadAnomalousArticles())

const loadAnomalousArticles = async () => {
  loading.value = true
  try {
    articles.value = await analysisApi.getAnomalousArticles()
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const viewDetail = (article: ArticleData) => {
  selectedArticle.value = article
  detailVisible.value = true
  activeTab.value = 'analysis'
}

const regenerateAI = async () => {
  if (!selectedArticle.value?.id) return
  regenerating.value = true
  try {
    const response = await fetch(`/api/enhanced/articles/${selectedArticle.value.id}/regenerate-ai`, { method: 'POST' })
    const data = await response.json()
    if (data.success) {
      selectedArticle.value.aiSuggestions = data.aiSuggestions
      ElMessage.success('AI建议已重新生成')
    }
  } catch (error) {
    ElMessage.error('生成失败，请重试')
  } finally {
    regenerating.value = false
  }
}

const openLink = (url: string) => window.open(url, '_blank')
const formatNumber = (num: number | null | undefined) => num?.toLocaleString() || '0'
const formatDate = (dateStr: string) => dateStr ? new Date(dateStr).toLocaleString('zh-CN') : ''
const calculateRate = (article: ArticleData) => {
  if (!article.readCount7d || article.readCount7d === 0) return '0.0'
  return ((article.interactionCount7d || 0) / article.readCount7d * 100).toFixed(1)
}

const getScoreClass = (score: number | undefined) => {
  if (!score) return ''
  if (score >= 70) return 'high'
  if (score >= 40) return 'medium'
  return 'low'
}

const getAnomalyLevelType = (level: string) => {
  const map: Record<string, string> = { SEVERE: 'danger', MODERATE: 'warning', MILD: 'info', NORMAL: 'success' }
  return map[level] || 'info'
}

const getAnomalyLevelText = (level: string) => {
  const map: Record<string, string> = { SEVERE: '严重', MODERATE: '中度', MILD: '轻度', NORMAL: '正常' }
  return map[level] || '未知'
}

const getImageTypeText = (type: string) => {
  const map: Record<string, string> = { product: '商品', detail: '细节', scene: '场景', banner: '横幅', content: '内容' }
  return map[type] || '图片'
}
</script>

<style scoped>
.anomaly-container { max-width: 1400px; margin: 0 auto; padding: 20px; }

.filter-card { margin-bottom: 24px; border-radius: 12px; }
.filter-content { display: flex; justify-content: space-between; align-items: center; }
.filter-left { display: flex; align-items: center; gap: 20px; }
.filter-title { font-size: 18px; font-weight: 600; color: #1f2937; }
.filter-stats { display: flex; gap: 24px; }
.stat-item { text-align: center; padding: 8px 20px; border-radius: 8px; }
.stat-item.good { background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%); }
.stat-item.bad { background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%); }
.stat-value { display: block; font-size: 24px; font-weight: 700; }
.stat-item.good .stat-value { color: #059669; }
.stat-item.bad .stat-value { color: #dc2626; }
.stat-label { font-size: 12px; color: #6b7280; }

.articles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.article-card { cursor: pointer; border-radius: 12px; transition: all 0.3s; border-left: 4px solid transparent; }
.article-card.good-card { border-left-color: #10b981; }
.article-card.bad-card { border-left-color: #ef4444; }
.article-card:hover { transform: translateY(-4px); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.brand-tag { font-size: 12px; color: #9ca3af; }
.article-title { font-size: 15px; font-weight: 500; color: #1f2937; margin: 0 0 16px 0; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.metrics-row { display: flex; justify-content: space-between; }
.metric { text-align: center; }
.metric-value { display: block; font-size: 18px; font-weight: 600; color: #1f2937; }
.metric-label { font-size: 12px; color: #9ca3af; }

.detail-dialog :deep(.el-dialog__body) { padding: 0 24px 24px; }
.info-card { margin-bottom: 20px; border-radius: 12px; background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%); }
.info-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 20px; }
.info-item { text-align: center; }
.info-label { display: block; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.info-value { font-size: 14px; font-weight: 500; color: #1f2937; }
.info-value.score { font-size: 20px; }
.info-value.score.high { color: #10b981; }
.info-value.score.medium { color: #f59e0b; }
.info-value.score.low { color: #ef4444; }

.detail-tabs { margin-top: 16px; }
.section-title { font-size: 16px; font-weight: 600; color: #1f2937; margin: 0 0 16px 0; padding-bottom: 8px; border-bottom: 2px solid #e5e7eb; }

.results-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.result-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px; }
.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.result-metric { font-weight: 600; color: #1f2937; }
.result-body { display: flex; flex-direction: column; gap: 8px; }
.result-row { display: flex; justify-content: space-between; font-size: 13px; color: #6b7280; }
.result-row .value { font-weight: 500; color: #1f2937; }
.result-row .value.highlight { color: #3b82f6; font-size: 15px; }
.result-row .value.positive { color: #10b981; }
.result-row .value.negative { color: #ef4444; }
.percentile-bar { flex: 1; margin-left: 12px; height: 20px; background: #e5e7eb; border-radius: 10px; position: relative; overflow: hidden; }
.percentile-fill { height: 100%; background: linear-gradient(90deg, #3b82f6, #8b5cf6); border-radius: 10px; transition: width 0.5s; }
.percentile-text { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); font-size: 11px; font-weight: 600; color: #fff; }

.images-section { margin-top: 24px; }
.images-stats { display: flex; gap: 12px; margin-bottom: 16px; }
.images-preview { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.image-card { background: #f9fafb; border-radius: 8px; padding: 12px; }
.image-type-badge { display: inline-block; background: #e0e7ff; color: #4f46e5; padding: 2px 8px; border-radius: 4px; font-size: 11px; margin-bottom: 8px; }
.image-desc { font-size: 13px; color: #374151; margin-bottom: 8px; }

.suggestions-section, .content-section { background: #f9fafb; border-radius: 12px; padding: 20px; }
.suggestions-text, .content-text { white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.8; color: #374151; margin: 0; }

.ai-section { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 12px; padding: 24px; color: #fff; }
.ai-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.ai-badge { display: flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 600; }
.ai-icon { font-size: 24px; }
.ai-content { background: rgba(255,255,255,0.1); border-radius: 10px; padding: 20px; backdrop-filter: blur(10px); }
.ai-text { white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.8; color: #fff; margin: 0; }
.ai-empty { text-align: center; padding: 40px; }

.dialog-footer { margin-top: 24px; padding-top: 16px; border-top: 1px solid #e5e7eb; display: flex; justify-content: flex-end; gap: 12px; }
</style>
