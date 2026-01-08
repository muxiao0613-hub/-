<template>
  <div class="detail-page">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <el-button @click="goBack" :icon="ArrowLeft" circle />
      <h2 class="page-title">{{ article?.title || '文章详情' }}</h2>
      <div class="nav-actions">
        <el-button type="primary" @click="recrawlContent" :loading="recrawling">
          重新爬取
        </el-button>
      </div>
    </div>

    <div class="detail-layout" v-loading="loading">
      <!-- 左侧：顶部空白区域 + 抓取的图文 + 数据显示 -->
      <div class="left-column">
        <!-- 顶部空白区域（预留） -->
        <div class="top-blank-area">
          <div class="article-meta">
            <el-tag>{{ article?.brand }}</el-tag>
            <el-tag type="info">{{ article?.postType }}</el-tag>
            <el-tag type="warning">{{ article?.contentType }}</el-tag>
            <span class="publish-time">{{ formatDate(article?.publishTime) }}</span>
          </div>
        </div>

        <!-- 抓取的图文（能看到图片和文字） -->
        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>📷 抓取的图文（能看到图片和文字）</span>
              <el-tag :type="article?.crawlStatus === 'SUCCESS' ? 'success' : 'warning'" size="small">
                {{ article?.crawlStatus === 'SUCCESS' ? '爬取成功' : '待爬取' }}
              </el-tag>
            </div>
          </template>
          
          <!-- 图片展示区 -->
          <div v-if="parsedImages.length > 0" class="images-grid">
            <div v-for="(img, idx) in parsedImages" :key="idx" class="image-item">
              <img
                v-if="img.localPath"
                :src="getImageUrl(img.localPath)"
                :alt="img.description"
                @error="handleImageError"
              />
              <div v-else class="image-placeholder">
                <el-icon><Picture /></el-icon>
              </div>
              <div class="image-info">
                <span class="image-type">{{ img.type || '内容图' }}</span>
                <span class="image-size" v-if="img.fileSize">{{ formatFileSize(img.fileSize) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无图片内容" :image-size="80" />
          
          <!-- 文字内容 -->
          <div class="text-content" v-if="article?.content">
            <h4>文字内容</h4>
            <div class="content-text">{{ article.content }}</div>
          </div>
        </el-card>

        <!-- 数据显示 -->
        <el-card class="data-card">
          <template #header>
            <span>📊 数据显示</span>
          </template>
          
          <div class="data-grid">
            <div class="data-section">
              <h4>7天数据</h4>
              <div class="metrics">
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.readCount7d) }}</span>
                  <span class="metric-label">阅读/播放</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.interactionCount7d) }}</span>
                  <span class="metric-label">互动</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productVisit7d) }}</span>
                  <span class="metric-label">好物访问</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productWant7d) }}</span>
                  <span class="metric-label">好物想要</span>
                </div>
              </div>
            </div>
            
            <div class="data-section">
              <h4>14天数据</h4>
              <div class="metrics">
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.readCount14d) }}</span>
                  <span class="metric-label">阅读/播放</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.interactionCount14d) }}</span>
                  <span class="metric-label">互动</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productVisitCount) }}</span>
                  <span class="metric-label">好物访问</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productWant14d) }}</span>
                  <span class="metric-label">好物想要</span>
                </div>
              </div>
            </div>
            
            <div class="data-section rates">
              <h4>关键指标</h4>
              <div class="rate-items">
                <div class="rate-item">
                  <span class="rate-label">互动率</span>
                  <span class="rate-value" :class="getInteractionRateClass()">{{ interactionRate }}%</span>
                </div>
                <div class="rate-item">
                  <span class="rate-label">转化率</span>
                  <span class="rate-value">{{ conversionRate }}%</span>
                </div>
                <div class="rate-item">
                  <span class="rate-label">异常评分</span>
                  <span class="rate-value" :class="getScoreClass(article?.anomalyScore)">
                    {{ article?.anomalyScore?.toFixed(1) || '-' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </div>
      
      <!-- 右侧：AI建议 + 优化建议 -->
      <div class="right-column">
        <!-- AI建议（跳转ai界面） -->
        <el-card class="ai-card">
          <template #header>
            <div class="card-header">
              <span>🤖 AI建议（跳转AI界面）</span>
              <div class="header-actions">
                <el-tag :type="aiAvailable ? 'success' : 'warning'" size="small">
                  {{ aiAvailable ? 'AI已连接' : '本地模式' }}
                </el-tag>
                <el-button type="primary" size="small" @click="generateAI" :loading="generatingAI">
                  {{ article?.aiSuggestions ? '重新生成' : '生成建议' }}
                </el-button>
              </div>
            </div>
          </template>
          
          <div v-if="article?.aiSuggestions" class="ai-content">
            <pre class="ai-text">{{ article.aiSuggestions }}</pre>
          </div>
          <div v-else class="ai-empty">
            <el-empty description="点击上方按钮生成AI智能建议">
              <template #image>
                <div class="ai-icon-large">🤖</div>
              </template>
            </el-empty>
          </div>
        </el-card>

        <!-- 优化建议 -->
        <el-card class="optimization-card">
          <template #header>
            <span>💡 优化建议</span>
          </template>
          
          <div v-if="article?.optimizationSuggestions" class="optimization-content">
            <pre class="optimization-text">{{ article.optimizationSuggestions }}</pre>
          </div>
          <el-empty v-else description="暂无优化建议" :image-size="60" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Picture } from '@element-plus/icons-vue'
import { analysisApi, type ArticleData } from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const recrawling = ref(false)
const generatingAI = ref(false)
const article = ref<ArticleData | null>(null)
const aiAvailable = ref(false)

const parsedImages = computed(() => {
  if (!article.value?.imagesInfo) return []
  try {
    return JSON.parse(article.value.imagesInfo)
  } catch {
    return []
  }
})

const interactionRate = computed(() => {
  if (!article.value?.readCount7d || article.value.readCount7d === 0) return '0.00'
  return ((article.value.interactionCount7d || 0) / article.value.readCount7d * 100).toFixed(2)
})

const conversionRate = computed(() => {
  if (!article.value?.readCount7d || article.value.readCount7d === 0) return '0.00'
  return ((article.value.productVisit7d || 0) / article.value.readCount7d * 100).toFixed(2)
})

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    await loadArticle(parseInt(id))
    await checkAIStatus()
  }
})

const loadArticle = async (id: number) => {
  loading.value = true
  try {
    article.value = await analysisApi.getArticleById(id)
  } catch (error) {
    ElMessage.error('加载文章详情失败')
  } finally {
    loading.value = false
  }
}

const checkAIStatus = async () => {
  try {
    const response = await fetch('/api/enhanced/ai-status')
    const data = await response.json()
    aiAvailable.value = data.available
  } catch {
    aiAvailable.value = false
  }
}

const generateAI = async () => {
  if (!article.value?.id) return
  generatingAI.value = true
  try {
    const response = await fetch(`/api/enhanced/articles/${article.value.id}/generate-ai`, {
      method: 'POST'
    })
    const data = await response.json()
    if (data.success) {
      article.value.aiSuggestions = data.aiSuggestions
      ElMessage.success('AI建议生成成功')
    } else {
      ElMessage.error(data.error || '生成失败')
    }
  } catch (error) {
    ElMessage.error('生成AI建议失败')
  } finally {
    generatingAI.value = false
  }
}

const recrawlContent = async () => {
  if (!article.value?.id) return
  recrawling.value = true
  try {
    const response = await fetch(`/api/enhanced/articles/${article.value.id}/recrawl`, {
      method: 'POST'
    })
    const data = await response.json()
    if (data.success) {
      ElMessage.success(`爬取成功，发现 ${data.imagesCount} 张图片`)
      await loadArticle(article.value.id)
    } else {
      ElMessage.error(data.error || '爬取失败')
    }
  } catch (error) {
    ElMessage.error('重新爬取失败')
  } finally {
    recrawling.value = false
  }
}

const goBack = () => {
  router.back()
}

const getImageUrl = (localPath: string) => {
  // 将本地路径转换为可访问的URL
  // 处理不同的路径格式
  let imagePath = localPath
  if (imagePath.startsWith('downloads/images/')) {
    imagePath = imagePath.replace('downloads/images/', '')
  }
  if (imagePath.startsWith('downloads\\images\\')) {
    imagePath = imagePath.replace('downloads\\images\\', '').replace(/\\/g, '/')
  }
  return `/api/images/${imagePath}`
}

const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const formatNumber = (num: number | null | undefined) => {
  return num?.toLocaleString() || '0'
}

const formatDate = (dateStr: string | undefined) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const formatFileSize = (size: number) => {
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

const getInteractionRateClass = () => {
  const rate = parseFloat(interactionRate.value)
  if (rate >= 8) return 'excellent'
  if (rate >= 5) return 'good'
  if (rate >= 3) return 'normal'
  return 'low'
}

const getScoreClass = (score: number | undefined) => {
  if (!score) return ''
  if (score >= 70) return 'excellent'
  if (score >= 50) return 'good'
  if (score >= 30) return 'normal'
  return 'low'
}
</script>
<style scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 20px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.page-title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-layout {
  display: grid;
  grid-template-columns: 1fr 600px;  /* 增加右侧宽度从400px到600px */
  gap: 20px;
  align-items: start;
}

.left-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 20px;
}

/* 顶部空白区域 */
.top-blank-area {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.publish-time {
  color: #9ca3af;
  font-size: 14px;
}

/* 卡片通用样式 */
.content-card, .data-card, .ai-card, .optimization-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 图片网格 */
.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.image-item {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background: #f3f4f6;
  aspect-ratio: 1;
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 32px;
}

.image-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  color: #fff;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
}

/* 文字内容 */
.text-content h4 {
  margin: 0 0 12px 0;
  color: #374151;
  font-size: 14px;
}

.content-text {
  background: #f9fafb;
  padding: 16px;
  border-radius: 8px;
  line-height: 1.8;
  color: #4b5563;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
}

/* 数据显示 */
.data-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.data-section h4 {
  margin: 0 0 12px 0;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-item {
  text-align: center;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.metric-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
}

.metric-label {
  font-size: 12px;
  color: #9ca3af;
}

.rates {
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.rate-items {
  display: flex;
  gap: 24px;
}

.rate-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-label {
  color: #6b7280;
  font-size: 14px;
}

.rate-value {
  font-size: 18px;
  font-weight: 600;
}

.rate-value.excellent { color: #10b981; }
.rate-value.good { color: #3b82f6; }
.rate-value.normal { color: #f59e0b; }
.rate-value.low { color: #ef4444; }

/* AI建议卡片 */
.ai-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.ai-card :deep(.el-card__header) {
  background: transparent;
  border-bottom: 1px solid rgba(255,255,255,0.2);
  color: #fff;
}

.ai-card :deep(.el-card__body) {
  background: transparent;
}

.ai-content {
  background: rgba(255,255,255,0.1);
  border-radius: 8px;
  padding: 16px;
  max-height: 500px;  /* 增加高度从400px到500px */
  overflow-y: auto;
}

.ai-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #fff;
  margin: 0;
}

.ai-empty {
  text-align: center;
  padding: 40px 20px;
  color: rgba(255,255,255,0.8);
}

.ai-icon-large {
  font-size: 48px;
  margin-bottom: 16px;
}

/* 优化建议卡片 */
.optimization-content {
  max-height: 500px;  /* 增加高度从400px到500px */
  overflow-y: auto;
}

.optimization-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
  margin: 0;
}

/* 响应式 */
@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  
  .right-column {
    position: static;
  }
}
</style>