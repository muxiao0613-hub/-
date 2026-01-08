import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
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
  title: string
  brand: string
  publishTime: string
  articleLink: string
  contentType: string
  postType: string
  materialSource?: string
  styleInfo?: string
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
  anomalyDetails?: string
  anomalyScore?: number
  content?: string
  titleAnalysis?: string
  contentAnalysis?: string
  crawlStatus?: string
  crawlError?: string
  optimizationSuggestions?: string
  aiSuggestions?: string
  imagesInfo?: string
  imagesDownloaded?: boolean
  localImagesPath?: string
  createdAt?: string
  updatedAt?: string
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