<template>
  <div class="anomaly-container">
    <el-card class="filter-card">
      <template #header>
        <span>异常检测结果</span>
      </template>
      
      <div class="filter-content">
        <el-radio-group v-model="selectedType" @change="loadAnomalousArticles">
          <el-radio-button label="">全部异常</el-radio-button>
          <el-radio-button label="GOOD_ANOMALY">异常好</el-radio-button>
          <el-radio-button label="BAD_ANOMALY">异常差</el-radio-button>
        </el-radio-group>
        
        <div class="filter-stats">
          <el-tag type="success" size="large">异常好: {{ goodCount }}</el-tag>
          <el-tag type="danger" size="large">异常差: {{ badCount }}</el-tag>
        </div>
      </div>
    </el-card>

    <!-- 异常文章列表 -->
    <el-card class="articles-card">
      <div v-loading="loading">
        <el-row :gutter="20">
          <el-col 
            :span="12" 
            v-for="article in filteredArticles" 
            :key="article.id"
            class="article-col"
          >
            <el-card 
              class="article-card" 
              :class="article.anomalyStatus === 'GOOD_ANOMALY' ? 'good-anomaly' : 'bad-anomaly'"
              shadow="hover"
            >
              <template #header>
                <div class="article-header">
                  <el-tag 
                    :type="article.anomalyStatus === 'GOOD_ANOMALY' ? 'success' : 'danger'"
                    size="small"
                  >
                    {{ article.anomalyStatus === 'GOOD_ANOMALY' ? '异常好' : '异常差' }}
                  </el-tag>
                  <span class="article-brand">{{ article.brand }}</span>
                </div>
              </template>
              
              <div class="article-content">
                <h3 class="article-title" @click="viewDetail(article)">
                  {{ article.title }}
                </h3>
                
                <div class="article-metrics">
                  <div class="metric-item">
                    <span class="metric-label">7天阅读:</span>
                    <span class="metric-value">{{ formatNumber(article.readCount7d) }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">7天互动:</span>
                    <span class="metric-value">{{ formatNumber(article.interactionCount7d) }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">7天分享:</span>
                    <span class="metric-value">{{ formatNumber(article.shareCount7d) }}</span>
                  </div>
                </div>
                
                <div class="article-suggestions" v-if="article.optimizationSuggestions">
                  <h4>优化建议摘要:</h4>
                  <p class="suggestions-preview">
                    {{ getSuggestionsPreview(article.optimizationSuggestions) }}
                  </p>
                </div>
                
                <div class="article-actions">
                  <el-button type="primary" size="small" @click="viewDetail(article)">
                    查看详情
                  </el-button>
                  <el-button 
                    type="success" 
                    size="small" 
                    v-if="article.articleLink"
                    @click="openLink(article.articleLink)"
                  >
                    查看原文
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        
        <div v-if="filteredArticles.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无异常文章数据" />
        </div>
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog 
      v-model="detailVisible" 
      :title="selectedArticle?.title" 
      width="90%"
      top="5vh"
    >
      <div v-if="selectedArticle" class="detail-content">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="文章ID">{{ selectedArticle.dataId }}</el-descriptions-item>
              <el-descriptions-item label="品牌">{{ selectedArticle.brand }}</el-descriptions-item>
              <el-descriptions-item label="发布时间">{{ formatDate(selectedArticle.publishTime) }}</el-descriptions-item>
              <el-descriptions-item label="内容类型">{{ selectedArticle.contentType }}</el-descriptions-item>
              <el-descriptions-item label="发文类型">{{ selectedArticle.postType }}</el-descriptions-item>
              <el-descriptions-item label="异常状态">
                <el-tag :type="selectedArticle.anomalyStatus === 'GOOD_ANOMALY' ? 'success' : 'danger'">
                  {{ selectedArticle.anomalyStatus === 'GOOD_ANOMALY' ? '异常好' : '异常差' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
          
          <el-tab-pane label="流量数据" name="metrics">
            <div class="metrics-grid">
              <div class="metric-card">
                <h4>7天数据</h4>
                <div class="metric-row">
                  <span>阅读量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.readCount7d) }}</span>
                </div>
                <div class="metric-row">
                  <span>互动量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.interactionCount7d) }}</span>
                </div>
                <div class="metric-row">
                  <span>分享量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.shareCount7d) }}</span>
                </div>
              </div>
              
              <div class="metric-card">
                <h4>14天数据</h4>
                <div class="metric-row">
                  <span>阅读量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.readCount14d) }}</span>
                </div>
                <div class="metric-row">
                  <span>互动量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.interactionCount14d) }}</span>
                </div>
                <div class="metric-row">
                  <span>分享量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.shareCount14d) }}</span>
                </div>
              </div>
              
              <div class="metric-card">
                <h4>其他数据</h4>
                <div class="metric-row">
                  <span>好物访问量:</span>
                  <span class="metric-number">{{ formatNumber(selectedArticle.productVisitCount) }}</span>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="文章内容" name="content" v-if="selectedArticle.content">
            <div class="content-display">
              <p>{{ selectedArticle.content }}</p>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="优化建议" name="suggestions" v-if="selectedArticle.optimizationSuggestions">
            <div class="suggestions-display" v-html="formatSuggestions(selectedArticle.optimizationSuggestions)">
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { analysisApi, type ArticleData } from '../api'

const loading = ref(false)
const selectedType = ref('')
const articles = ref<ArticleData[]>([])
const detailVisible = ref(false)
const selectedArticle = ref<ArticleData | null>(null)
const activeTab = ref('basic')

const filteredArticles = computed(() => {
  if (!selectedType.value) {
    return articles.value
  }
  return articles.value.filter(article => article.anomalyStatus === selectedType.value)
})

const goodCount = computed(() => {
  return articles.value.filter(article => article.anomalyStatus === 'GOOD_ANOMALY').length
})

const badCount = computed(() => {
  return articles.value.filter(article => article.anomalyStatus === 'BAD_ANOMALY').length
})

onMounted(() => {
  loadAnomalousArticles()
})

const loadAnomalousArticles = async () => {
  loading.value = true
  try {
    articles.value = await analysisApi.getAnomalousArticles()
  } catch (error) {
    console.error('Failed to load anomalous articles:', error)
    ElMessage.error('加载异常文章失败')
  } finally {
    loading.value = false
  }
}

const viewDetail = (article: ArticleData) => {
  selectedArticle.value = article
  detailVisible.value = true
  activeTab.value = 'basic'
}

const openLink = (url: string) => {
  window.open(url, '_blank')
}

const formatNumber = (num: number | null | undefined) => {
  if (num === null || num === undefined) return '0'
  return num.toLocaleString()
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getSuggestionsPreview = (suggestions: string) => {
  if (!suggestions) return ''
  const lines = suggestions.split('\n').filter(line => line.trim())
  return lines.slice(0, 2).join(' ').substring(0, 100) + '...'
}

const formatSuggestions = (suggestions: string) => {
  if (!suggestions) return ''
  return suggestions.replace(/\n/g, '<br>')
}
</script>

<style scoped>
.anomaly-container {
  max-width: 1200px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-stats {
  display: flex;
  gap: 15px;
}

.articles-card {
  min-height: 400px;
}

.article-col {
  margin-bottom: 20px;
}

.article-card {
  height: 100%;
}

.article-card.good-anomaly {
  border-left: 4px solid #67c23a;
}

.article-card.bad-anomaly {
  border-left: 4px solid #f56c6c;
}

.article-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-brand {
  font-size: 12px;
  color: #909399;
}

.article-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.article-title {
  margin: 0 0 15px 0;
  font-size: 16px;
  line-height: 1.4;
  cursor: pointer;
  color: #409EFF;
  transition: color 0.3s;
}

.article-title:hover {
  color: #66b1ff;
}

.article-metrics {
  margin-bottom: 15px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 14px;
}

.metric-label {
  color: #606266;
}

.metric-value {
  font-weight: bold;
  color: #303133;
}

.article-suggestions {
  flex: 1;
  margin-bottom: 15px;
}

.article-suggestions h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #409EFF;
}

.suggestions-preview {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  margin: 0;
}

.article-actions {
  display: flex;
  gap: 10px;
}

.empty-state {
  text-align: center;
  padding: 40px;
}

.detail-content {
  max-height: 70vh;
  overflow-y: auto;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.metric-card {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
}

.metric-card h4 {
  margin: 0 0 10px 0;
  color: #409EFF;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.metric-number {
  font-weight: bold;
  color: #303133;
}

.content-display,
.suggestions-display {
  background: #f5f7fa;
  padding: 20px;
  border-radius: 4px;
  line-height: 1.6;
}

.suggestions-display {
  white-space: pre-wrap;
}
</style>