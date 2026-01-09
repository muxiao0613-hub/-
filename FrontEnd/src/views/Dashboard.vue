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
          <el-statistic title="表现优秀" :value="statistics.goodAnomalyCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card bad">
          <el-statistic title="需要优化" :value="statistics.badAnomalyCount" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card>
          <template #header>异常状态分布</template>
          <div ref="pieChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>平台数据对比</template>
          <div ref="barChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 平台切换Tab -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <el-tabs v-model="activePlatform" @tab-change="handlePlatformChange">
            <el-tab-pane label="得物" name="得物">
              <template #label>
                <span>得物 ({{ platformStats.dewuCount }})</span>
              </template>
            </el-tab-pane>
            <el-tab-pane label="小红书" name="小红书">
              <template #label>
                <span>小红书 ({{ platformStats.xiaohongshuCount }})</span>
              </template>
            </el-tab-pane>
          </el-tabs>
          <div class="table-actions">
            <el-select v-model="statusFilter" placeholder="筛选状态" clearable @change="loadArticles">
              <el-option label="全部" value="" />
              <el-option label="正常" value="NORMAL" />
              <el-option label="表现优秀" value="GOOD_ANOMALY" />
              <el-option label="需要优化" value="BAD_ANOMALY" />
            </el-select>
            <el-button type="danger" @click="clearAllData" :loading="clearing">
              清除所有数据
            </el-button>
          </div>
        </div>
      </template>

      <!-- 得物数据表格 -->
      <el-table 
        v-if="activePlatform === '得物'"
        :data="articles"
        v-loading="loading"
        style="width: 100%"
        border
      >
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip fixed="left" />
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="postType" label="发文类型" width="100" />
        <el-table-column prop="styleInfo" label="款式信息" width="140" show-overflow-tooltip />
        <el-table-column label="7天数据" align="center">
          <el-table-column prop="readCount7d" label="阅读" width="80" />
          <el-table-column prop="interactionCount7d" label="互动" width="70" />
          <el-table-column prop="productVisit7d" label="好物访问" width="85" />
          <el-table-column prop="productWant7d" label="好物想要" width="85" />
        </el-table-column>
        <el-table-column label="14天数据" align="center">
          <el-table-column prop="readCount14d" label="阅读" width="80" />
          <el-table-column prop="interactionCount14d" label="互动" width="70" />
          <el-table-column prop="productVisitCount" label="好物访问" width="85" />
          <el-table-column prop="productWant14d" label="好物想要" width="85" />
        </el-table-column>
        <el-table-column prop="anomalyStatus" label="状态" width="100" fixed="right">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.anomalyStatus)" size="small">
              {{ getStatusText(row.anomalyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 小红书数据表格（无标题列） -->
      <el-table 
        v-else
        :data="articles"
        v-loading="loading"
        style="width: 100%"
        border
      >
        <el-table-column prop="dataId" label="数据ID" width="200" show-overflow-tooltip fixed="left" />
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="postType" label="发文类型" width="100" />
        <el-table-column prop="styleInfo" label="款式信息" width="140" show-overflow-tooltip />
        <el-table-column label="7天数据" align="center">
          <el-table-column prop="readCount7d" label="阅读" width="80" />
          <el-table-column prop="interactionCount7d" label="互动" width="70" />
          <el-table-column prop="productVisit7d" label="好物访问" width="85" />
          <el-table-column prop="productWant7d" label="好物想要" width="85" />
        </el-table-column>
        <el-table-column label="14天数据" align="center">
          <el-table-column prop="readCount14d" label="阅读" width="80" />
          <el-table-column prop="interactionCount14d" label="互动" width="70" />
          <el-table-column prop="productVisitCount" label="好物访问" width="85" />
          <el-table-column prop="productWant14d" label="好物想要" width="85" />
        </el-table-column>
        <el-table-column prop="anomalyStatus" label="状态" width="100" fixed="right">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.anomalyStatus)" size="small">
              {{ getStatusText(row.anomalyStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goToDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { analysisApi, type ArticleData, type Statistics, type PlatformStats } from '../api'

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
const platformStats = ref<PlatformStats>({
  dewuCount: 0,
  xiaohongshuCount: 0,
  totalCount: 0
})

const activePlatform = ref('得物')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)

const pieChartRef = ref()
const barChartRef = ref()

onMounted(() => {
  loadData()
})

const loadData = async () => {
  await Promise.all([
    loadStatistics(),
    loadPlatformStats(),
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

const loadPlatformStats = async () => {
  try {
    platformStats.value = await analysisApi.getPlatformStats()
  } catch (error) {
    console.error('Failed to load platform stats:', error)
  }
}

const loadArticles = async () => {
  loading.value = true
  try {
    const response = await analysisApi.getArticlesPage(
      currentPage.value - 1,
      pageSize.value,
      activePlatform.value,
      statusFilter.value
    )
    articles.value = response.content
    totalElements.value = response.totalElements
  } catch (error) {
    console.error('Failed to load articles:', error)
    ElMessage.error('加载文章列表失败')
  } finally {
    loading.value = false
  }
}

const handlePlatformChange = () => {
  currentPage.value = 1
  loadArticles()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadArticles()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadArticles()
}

const initCharts = () => {
  // 饼图
  const pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      name: '文章状态',
      type: 'pie',
      radius: '50%',
      data: [
        { value: statistics.value.normalCount, name: '正常', itemStyle: { color: '#909399' } },
        { value: statistics.value.goodAnomalyCount, name: '表现优秀', itemStyle: { color: '#67c23a' } },
        { value: statistics.value.badAnomalyCount, name: '需要优化', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  })
  
  // 柱状图
  const barChart = echarts.init(barChartRef.value)
  barChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['得物', '小红书'] },
    yAxis: { type: 'value' },
    series: [{
      name: '文章数量',
      type: 'bar',
      data: [platformStats.value.dewuCount, platformStats.value.xiaohongshuCount],
      itemStyle: { color: '#409EFF' }
    }]
  })
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
    case 'GOOD_ANOMALY': return '优秀'
    case 'BAD_ANOMALY': return '待优化'
    default: return '正常'
  }
}

const goToDetail = (article: ArticleData) => {
  router.push(`/article/${article.id}`)
}

const clearAllData = async () => {
  try {
    await ElMessageBox.confirm('确定要清除所有数据吗？此操作不可恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    
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
  max-width: 1400px;
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
  align-items: flex-start;
}

.table-actions {
  display: flex;
  gap: 10px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>