import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 300000  // 5分钟超时
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export interface AnomalyAnalysisResult {
  metric: string
  value: number
  mean: number
  stdDev: number
  zScore: number
  percentile: number
  deviation: string
  level: 'SEVERE' | 'MODERATE' | 'MILD' | 'NORMAL'
}

export interface AnomalyAnalysisReport {
  results: AnomalyAnalysisResult[]
  overallStatus: string
  overallScore: number
}

export interface TitleAnalysis {
  length: number
  hasEmotionalWords: boolean
  hasSpecificNumber: boolean
  hasQuestion: boolean
  hasCallToAction: boolean
  keywordCount: number
  qualityScore: number
}

export interface ArticleDetailResponse {
  article: ArticleData
  anomalyReport: AnomalyAnalysisReport
  titleAnalysis: TitleAnalysis
  benchmarkArticles: ArticleData[]
  brandAverages: Record<string, number>
}

export interface ArticleData {
  id?: number
  dataId: string
  title: string | null
  brand: string
  publishTime: string
  articleLink: string
  contentType: string
  postType: string
  materialSource?: string
  styleInfo?: string
  platform?: string
  readCount7d: number
  readCount14d: number
  interactionCount7d: number
  interactionCount14d: number
  shareCount7d: number
  shareCount14d: number
  productVisit7d?: number
  productVisitCount: number
  productWant7d?: number
  productWant14d?: number
  anomalyStatus: string
  anomalyScore?: number
  content?: string
  crawlStatus?: string
  optimizationSuggestions?: string
  aiSuggestions?: string
  imagesInfo?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export interface PlatformStats {
  dewuCount: number
  xiaohongshuCount: number
  totalCount: number
}

export interface Statistics {
  totalCount: number
  normalCount: number
  goodAnomalyCount: number
  badAnomalyCount: number
  avgReadCount: number
  avgInteractionCount: number
}

// API 方法
export const analysisApi = {
  // 上传Excel文件
  uploadExcel: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/analysis/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 获取所有文章
  getAllArticles: (): Promise<ArticleData[]> => {
    return api.get('/analysis/articles')
  },

  // 分页获取文章
  getArticlesPage: (
    page: number = 0, 
    size: number = 20, 
    platform?: string, 
    status?: string
  ): Promise<PageResponse<ArticleData>> => {
    const params = new URLSearchParams()
    params.append('page', String(page))
    params.append('size', String(size))
    if (platform) params.append('platform', platform)
    if (status) params.append('status', status)
    return api.get(`/analysis/articles/page?${params.toString()}`)
  },

  // 获取平台统计
  getPlatformStats: (): Promise<PlatformStats> => {
    return api.get('/analysis/platforms/stats')
  },

  // 获取异常文章
  getAnomalousArticles: (): Promise<ArticleData[]> => {
    return api.get('/analysis/articles/anomalous')
  },

  // 根据状态获取文章
  getArticlesByStatus: (status: string): Promise<ArticleData[]> => {
    return api.get(`/analysis/articles/status/${status}`)
  },

  // 获取文章详情（含分析报告）
  getArticleDetail: (id: number): Promise<ArticleDetailResponse> => {
    return api.get(`/analysis/articles/${id}/detail`)
  },

  // 获取单个文章
  getArticleById: (id: number): Promise<ArticleData> => {
    return api.get(`/analysis/articles/${id}`)
  },

  // 获取统计信息
  getStatistics: (): Promise<Statistics> => {
    return api.get('/analysis/statistics')
  },

  // 删除所有文章
  deleteAllArticles: () => {
    return api.delete('/analysis/articles')
  }
}

export default api