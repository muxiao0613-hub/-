<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <el-statistic title="总文章数" :value="statistics.totalCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card normal">
          <el-statistic title="正常文章" :value="statistics.normalCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card good">
          <el-statistic title="异常好" :value="statistics.goodAnomalyCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card bad">
          <el-statistic title="异常差" :value="statistics.badAnomalyCount" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>异常状态分布</span>
          </template>
          <div ref="pieChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>平均流量指标</span>
          </template>
          <div ref="barChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 文章列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>文章列表</span>
          <div class="table-actions">
            <el-select v-model="statusFilter" placeholder="筛选状态" @change="loadArticles">
              <el-option label="全部" value="" />
              <el-option label="正常" value="NORMAL" />
              <el-option label="异常好" value="GOOD_ANOMALY" />
              <el-option label="异常差" value="BAD_ANOMALY" />
            </el-select>
            <el-button type="danger" @click="clearAllData" :loading="clearing">
              清除所有数据
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="articles" v-loading="loading" style="width: 100%" :scroll="{ x: 1500 }">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip fixed="left" />
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="contentType" label="内容形式" width="100" />
        <el-table-column prop="postType" label="发文类型" width="120" />
        <el-table-column prop="materialSource" label="素材来源" width="120" />
        <el-table-column prop="styleInfo" label="款式信息" width="150" />
        
        <!-- 7天数据组 -->
        <el-table-column label="7天数据" align="center">
          <el-table-column prop="readCount7d" label="阅读/播放" width="100" />
          <el-table-column prop="interactionCount7d" label="互动" width="80" />
          <el-table-column label="好物访问" width="90">
            <template #default="{ row }">
              {{ getProductVisit7d(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="shareCount7d" label="好物想要" width="90" />
        </el-table-column>
        
        <!-- 14天数据组 -->
        <el-table-column label="14天数据" align="center">
          <el-table-column prop="readCount14d" label="阅读/播放" width="100" />
          <el-table-column prop="interactionCount14d" label="互动" width="80" />
          <el-table-column prop="productVisitCount" label="好物访问" width="90" />
          <el-table-column prop="productWant14d" label="好物想要" width="90" />
        </el-table-column>
        
        <el-table-column prop="anomalyStatus" label="状态" width="120" fixed="right">
          <template #default="{ row }">
            <el-tag 
              :type="getStatusType(row.anomalyStatus)"
              size="small"
            >
              {{ getStatusText(row.anomalyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="goToDetail(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog 
      v-model="detailVisible" 
      :title="selectedArticle?.title" 
      width="80%"
      top="5vh"
    >
      <div v-if="selectedArticle" class="article-detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="文章ID">{{ selectedArticle.dataId }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ selectedArticle.brand }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDate(selectedArticle.publishTime) }}</el-descriptions-item>
          <el-descriptions-item label="内容形式">{{ selectedArticle.contentType }}</el-descriptions-item>
          <el-descriptions-item label="发文类型">{{ selectedArticle.postType }}</el-descriptions-item>
          <el-descriptions-item label="素材来源">{{ selectedArticle.materialSource }}</el-descriptions-item>
          <el-descriptions-item label="款式信息">{{ selectedArticle.styleInfo }}</el-descriptions-item>
          <el-descriptions-item label="异常状态" :span="2">
            <el-tag :type="getStatusType(selectedArticle.anomalyStatus)">
              {{ getStatusText(selectedArticle.anomalyStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 7天数据 -->
        <div class="data-section">
          <h3>7天数据表现</h3>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="阅读/播放量">{{ selectedArticle.readCount7d }}</el-descriptions-item>
            <el-descriptions-item label="互动量">{{ selectedArticle.interactionCount7d }}</el-descriptions-item>
            <el-descriptions-item label="好物访问">{{ getProductVisit7d(selectedArticle) }}</el-descriptions-item>
            <el-descriptions-item label="好物想要">{{ selectedArticle.shareCount7d }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 14天数据 -->
        <div class="data-section">
          <h3>14天数据表现</h3>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="阅读/播放量">{{ selectedArticle.readCount14d }}</el-descriptions-item>
            <el-descriptions-item label="互动量">{{ selectedArticle.interactionCount14d }}</el-descriptions-item>
            <el-descriptions-item label="好物访问">{{ selectedArticle.productVisitCount }}</el-descriptions-item>
            <el-descriptions-item label="好物想要">{{ selectedArticle.productWant14d }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="content-section" v-if="selectedArticle.content">
          <h3>文章内容</h3>
          <div class="content-text">{{ selectedArticle.content }}</div>
        </div>

        <div class="suggestions-section" v-if="selectedArticle.optimizationSuggestions">
          <h3>优化建议</h3>
          <div class="suggestions-text" v-html="formatSuggestions(selectedArticle.optimizationSuggestions)"></div>
        </div>

        <div class="content-analysis-section" v-if="selectedArticle.content">
          <h3>内容特征分析</h3>
          <div class="content-features">
            <div class="feature-item" v-if="hasImages(selectedArticle.content)">
              <span class="feature-icon">📷</span>
              <span class="feature-text">包含图片内容</span>
              <span class="feature-count">{{ getImageCount(selectedArticle.content) }}</span>
            </div>
            <div class="feature-item" v-if="hasVideos(selectedArticle.content)">
              <span class="feature-icon">🎥</span>
              <span class="feature-text">包含视频内容</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">📝</span>
              <span class="feature-text">内容长度</span>
              <span class="feature-count">{{ selectedArticle.content.length }} 字符</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { analysisApi, type ArticleData, type Statistics } from '../api'

const router = useRouter()

const loading = ref(false)
const clearing = ref(false)
const articles = ref<ArticleData[]>([])
const statistics = ref<Statistics>({
  totalCount: 0,
  normalCount: 0,
  goodAnomalyCount: 0,
  badAnomalyCount: 0,
  avgReadCount: 0,
  avgInteractionCount: 0
})
const statusFilter = ref('')

const pieChartRef = ref()
const barChartRef = ref()

onMounted(() => {
  loadData()
})

const loadData = async () => {
  await Promise.all([
    loadStatistics(),
    loadArticles()
  ])
  await nextTick()
  initCharts()
}

const loadStatistics = async () => {
  try {
    statistics.value = await analysisApi.getStatistics()
  } catch (error) {
    console.error('Failed to load statistics:', error)
  }
}

const loadArticles = async () => {
  loading.value = true
  try {
    if (statusFilter.value) {
      articles.value = await analysisApi.getArticlesByStatus(statusFilter.value)
    } else {
      articles.value = await analysisApi.getAllArticles()
    }
  } catch (error) {
    console.error('Failed to load articles:', error)
    ElMessage.error('加载文章列表失败')
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  initPieChart()
  initBarChart()
}

const initPieChart = () => {
  const chart = echarts.init(pieChartRef.value)
  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '文章状态',
        type: 'pie',
        radius: '50%',
        data: [
          { value: statistics.value.normalCount, name: '正常' },
          { value: statistics.value.goodAnomalyCount, name: '异常好' },
          { value: statistics.value.badAnomalyCount, name: '异常差' }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
  chart.setOption(option)
}

const initBarChart = () => {
  const chart = echarts.init(barChartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['平均阅读量', '平均互动量']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '数量',
        type: 'bar',
        data: [
          statistics.value.avgReadCount,
          statistics.value.avgInteractionCount
        ],
        itemStyle: {
          color: '#409EFF'
        }
      }
    ]
  }
  chart.setOption(option)
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'GOOD_ANOMALY': return 'success'
    case 'BAD_ANOMALY': return 'danger'
    default: return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'GOOD_ANOMALY': return '异常好'
    case 'BAD_ANOMALY': return '异常差'
    default: return '正常'
  }
}

const goToDetail = (article: ArticleData) => {
  router.push(`/article/${article.id}`)
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const formatSuggestions = (suggestions: string) => {
  if (!suggestions) return ''
  return suggestions
    .replace(/\n/g, '<br>')
    .replace(/⚠️/g, '<span class="warning-icon">⚠️</span>')
    .replace(/✅/g, '<span class="success-icon">✅</span>')
    .replace(/📷/g, '<span class="image-icon">📷</span>')
    .replace(/🎥/g, '<span class="video-icon">🎥</span>')
    .replace(/📸/g, '<span class="camera-icon">📸</span>')
}

const hasImages = (content: string) => {
  return content && (content.includes('📷 图片内容分析') || content.includes('图片'))
}

const hasVideos = (content: string) => {
  return content && (content.includes('🎥 视频内容') || content.includes('视频'))
}

const getImageCount = (content: string) => {
  if (!content) return ''
  const match = content.match(/共发现 (\d+) 张/)
  return match ? `${match[1]}张图片` : '包含图片'
}

const getProductVisit7d = (article: ArticleData) => {
  // 现在使用正确的7天好物访问字段
  return article.productVisit7d || 0
}

const clearAllData = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清除所有数据吗？此操作不可恢复。',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    clearing.value = true
    await analysisApi.deleteAllArticles()
    ElMessage.success('数据清除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('清除数据失败')
    }
  } finally {
    clearing.value = false
  }
}
</script>

<style scoped>
.dashboard-container {
  max-width: 1200px;
  margin: 0 auto;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-card.normal :deep(.el-statistic__number) {
  color: #909399;
}

.stat-card.good :deep(.el-statistic__number) {
  color: #67c23a;
}

.stat-card.bad :deep(.el-statistic__number) {
  color: #f56c6c;
}

.charts-row {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-actions {
  display: flex;
  gap: 10px;
}

.article-detail {
  max-height: 70vh;
  overflow-y: auto;
}

.content-section,
.suggestions-section,
.link-section,
.data-section {
  margin-top: 20px;
}

.content-section h3,
.suggestions-section h3,
.link-section h3,
.data-section h3 {
  color: #409EFF;
  margin-bottom: 10px;
}

.content-text,
.suggestions-text {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.suggestions-text :deep(.warning-icon) {
  color: #f59e0b;
}

.suggestions-text :deep(.success-icon) {
  color: #10b981;
}

.suggestions-text :deep(.image-icon) {
  color: #8b5cf6;
}

.suggestions-text :deep(.video-icon) {
  color: #ef4444;
}

.suggestions-text :deep(.camera-icon) {
  color: #06b6d4;
}

.content-analysis-section {
  margin-top: 20px;
}

.content-analysis-section h3 {
  color: #409EFF;
  margin-bottom: 10px;
}

.content-features {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f0f9ff;
  padding: 8px 12px;
  border-radius: 20px;
  font-size: 14px;
  border: 1px solid #e0f2fe;
}

.feature-icon {
  font-size: 16px;
}

.feature-text {
  color: #0369a1;
  font-weight: 500;
}

.feature-count {
  background: #0284c7;
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.suggestions-text {
  color: #606266;
}
</style>