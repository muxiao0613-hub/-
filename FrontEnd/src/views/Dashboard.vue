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

      <el-table :data="articles" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="readCount7d" label="7天阅读" width="100" />
        <el-table-column prop="interactionCount7d" label="7天互动" width="100" />
        <el-table-column prop="shareCount7d" label="7天分享" width="100" />
        <el-table-column prop="anomalyStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag 
              :type="getStatusType(row.anomalyStatus)"
              size="small"
            >
              {{ getStatusText(row.anomalyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="viewDetail(row)"
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
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文章ID">{{ selectedArticle.dataId }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ selectedArticle.brand }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDate(selectedArticle.publishTime) }}</el-descriptions-item>
          <el-descriptions-item label="内容类型">{{ selectedArticle.contentType }}</el-descriptions-item>
          <el-descriptions-item label="7天阅读量">{{ selectedArticle.readCount7d }}</el-descriptions-item>
          <el-descriptions-item label="14天阅读量">{{ selectedArticle.readCount14d }}</el-descriptions-item>
          <el-descriptions-item label="7天互动量">{{ selectedArticle.interactionCount7d }}</el-descriptions-item>
          <el-descriptions-item label="14天互动量">{{ selectedArticle.interactionCount14d }}</el-descriptions-item>
          <el-descriptions-item label="7天分享量">{{ selectedArticle.shareCount7d }}</el-descriptions-item>
          <el-descriptions-item label="14天分享量">{{ selectedArticle.shareCount14d }}</el-descriptions-item>
        </el-descriptions>

        <div class="content-section" v-if="selectedArticle.content">
          <h3>文章内容</h3>
          <div class="content-text">{{ selectedArticle.content }}</div>
        </div>

        <div class="suggestions-section" v-if="selectedArticle.optimizationSuggestions">
          <h3>优化建议</h3>
          <div class="suggestions-text" v-html="formatSuggestions(selectedArticle.optimizationSuggestions)"></div>
        </div>

        <div class="link-section" v-if="selectedArticle.articleLink">
          <h3>原文链接</h3>
          <el-link :href="selectedArticle.articleLink" target="_blank" type="primary">
            {{ selectedArticle.articleLink }}
          </el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { analysisApi, type ArticleData, type Statistics } from '../api'

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
const detailVisible = ref(false)
const selectedArticle = ref<ArticleData | null>(null)

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

const viewDetail = (article: ArticleData) => {
  selectedArticle.value = article
  detailVisible.value = true
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const formatSuggestions = (suggestions: string) => {
  if (!suggestions) return ''
  return suggestions.replace(/\n/g, '<br>')
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
.link-section {
  margin-top: 20px;
}

.content-section h3,
.suggestions-section h3,
.link-section h3 {
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

.suggestions-text {
  color: #606266;
}
</style>